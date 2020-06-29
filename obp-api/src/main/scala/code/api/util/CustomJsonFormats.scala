package code.api.util

import java.lang.reflect.{Constructor, Parameter, Type}
import java.util.UUID.randomUUID
import java.util.regex.Pattern

import code.api.cache.Caching
import code.api.util.ApiRole.rolesMappedToClasses
import code.api.v3_1_0.ListResult
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util._
import com.tesobe.CacheKeyFromArguments
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.{TypeInfo, _}

import scala.concurrent.duration._
import scala.reflect.ManifestFactory

trait CustomJsonFormats {
  implicit val formats: Formats = CustomJsonFormats.formats
}

object CustomJsonFormats {

  val formats: Formats = JsonSerializers.commonFormats + ListResultSerializer

  val losslessFormats: Formats =  net.liftweb.json.DefaultFormats.lossless + ListResultSerializer ++ JsonSerializers.serializers

  val emptyHintFormats = DefaultFormats.withHints(ShortTypeHints(List())) + ListResultSerializer ++ JsonSerializers.serializers

  implicit val nullTolerateFormats = formats + JNothingSerializer

  lazy val rolesMappedToClassesFormats: Formats = new Formats {
    val dateFormat = net.liftweb.json.DefaultFormats.dateFormat

    override val typeHints = ShortTypeHints(rolesMappedToClasses)
  } + ListResultSerializer ++ JsonSerializers.serializers
}

object ListResultSerializer extends Serializer[ListResult[_]] {
  private val clazz = classOf[ListResult[_]]

  def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), ListResult[_]] = {
    case (TypeInfo(entityType, Some(parameterizedType)), json) if(clazz.isAssignableFrom(entityType))=> json match {
      case JObject(singleField::Nil) => {
        val resultsItemType = parameterizedType.getActualTypeArguments.apply(1)
        assume(resultsItemType != classOf[Object], "when do deserialize to type ListResult, should supply exactly type parameter, should not give wildcard like this: jValue.extract[ListResult[List[_]]]")

        val name = singleField.name
        val manifest: Manifest[Any] = ManifestFactory.classType(resultsItemType.asInstanceOf[Class[Any]])
        val results: List[Any] = singleField.value.asInstanceOf[JArray].children.map(_.extract(format, manifest))
        ListResult(name, results)
      }
      case x => throw new MappingException("Can't convert " + x + " to ListResult")
    }
  }

  def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case x: ListResult[_] => {
      val singleField = JField(x.name, Extraction.decompose(x.results))
      JObject(singleField)
    }
  }

}


/**
 * make tolerate for missing required constructor parameters
 */
object JNothingSerializer extends Serializer[Any] with MdcLoggable {
  // This field is just a tag to declare all the missing fields are added, to avoid check missing field repeatedly
  val addedMissingFields = "addedMissingFieldsThisFieldIsJustBeTag"

  override def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), Any] = {
    case JNothingSerializer(typeInfo, jValue: JObject, missingFieldNames) if missingFieldNames.nonEmpty  => {
      val newFields: List[JField] = jValue.obj ::: (addedMissingFields :: missingFieldNames).map(JField(_, JNull))
      val newJValue =  JObject(newFields)
      Extraction.extract(newJValue,typeInfo)
    }
  }

  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case null => JNull
  }

  private[this] def unapply(arg: (TypeInfo, JValue))(implicit formats: Formats): Option[(TypeInfo, JValue, List[String])] =  {
    val (TypeInfo(clazz, _), jValue) = arg
    if (!jValue.isInstanceOf[JObject]) {
      None
    } else if(jValue \ addedMissingFields != JNothing) {
      None
    } else if (clazz == classOf[Option[_]] || clazz == classOf[List[_]] || clazz == classOf[Set[_]] || clazz.isArray) {
      None
    } else if (clazz == classOf[Map[_, _]]) {
      None
    } else if (tuple_?(clazz) && formats.tuplesAsArrays) {
      None
    } else {
      val jsonFieldNames: Set[String] = jValue.asInstanceOf[JObject].obj.map(_.name).toSet
      val missingFields: Option[List[String]] = missingFieldNames(clazz, jsonFieldNames)

      missingFields.map(it => (arg._1, arg._2, it))
    }
  }

  private[this] val TUPLE_PATTERN = Pattern.compile("scala.Tuple([1-9]|1\\d|2[0-2])")

  private[this] def tuple_?(t: Type) = t match {
    case clazz: Class[_] =>
      TUPLE_PATTERN.matcher(clazz.getName()).matches()
    case _ =>
      false
  }

  private[this] def missingFieldNames(clazz: Class[_], jsonFieldNames: Set[String]): Option[List[String]] = {
    // cache 2 hours, the cache time can be very long, because the result will never be changed
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider (Some(cacheKey.toString())) (2 hours) {

        val constructors: Array[Constructor[_]] = clazz.getDeclaredConstructors()
        bestMatching(constructors, jsonFieldNames) match {
          case None => None
          case Some(array: Array[Arg]) => {
            val missingFields: Array[String] = array.map(_.path).filterNot(jsonFieldNames.contains(_))
            if(missingFields.isEmpty) None else Some(missingFields.toList)
          }
        }

      }
    }
  }

  /**
   * absolutely simulate net.liftweb.json.Meta.Constructor#bestMatching,
   * to find beast matching constructor parameters
   * @param constructors
   * @param names json object Field Names
   * @return beast matching constructor parameters according json field names.
   */
  private[this] def bestMatching(constructors: Array[Constructor[_]], names: Set[String]): Option[Array[Arg]] = {

    def countOptionals(args: Array[Arg]) =
      args.foldLeft(0)((n, x) => if (x.optional) n+1 else n)
    def score(args: Array[Arg]) =
      args.foldLeft(0)((s, arg) => if (names.contains(arg.path)) s+1 else -100)


    val maybeObject: Option[Array[Arg]] = if (constructors.isEmpty) {
      None
    }
    else if(constructors.size == 1) {
      constructors.headOption.map(_.getParameters.map(Arg(_)))
    }
    else {
      val choices: Array[Array[Arg]] = constructors.map(_.getParameters())
        .map(_.map(Arg(_)))

      val best: (Array[Arg], Int) = choices.tail.foldLeft((choices.head, score(choices.head))) { (best, c) =>
        val newScore = score(c)
        if (newScore == best._2) {
          if (countOptionals(c) < countOptionals(best._1))
            (c, newScore) else best
        } else if (newScore > best._2) (c, newScore) else best
      }
      Some(best._1)
    }

    maybeObject
  }

  private case class Arg(private val parameter: Parameter) {
    if (!parameter.isNamePresent) {
      throw new IllegalArgumentException(
        s"""Parameter names are not present!
           |The constructor [${parameter.getDeclaringExecutable.toGenericString}] parameter names are missing.
           |Please check the compiler parameter '-parameters'.
           |""".stripMargin
      )
    }
    val path = parameter.getName()
    val optional = classOf[Option[_]].isAssignableFrom(parameter.getType())
  }
}



