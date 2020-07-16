package code.api.util

import java.lang.reflect.{Constructor, Parameter, Type}
import java.util.UUID.randomUUID
import java.util.regex.Pattern

import code.api.cache.Caching
import code.api.util.ApiRole.rolesMappedToClasses
import code.api.v3_1_0.ListResult
import code.util.Helper.MdcLoggable
import code.util.JsonUtils
import com.openbankproject.commons.util.Functions.Memo
import com.openbankproject.commons.util._
import com.tesobe.CacheKeyFromArguments
import net.liftweb.common.Box
import net.liftweb.json
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.{TypeInfo, _}
import net.liftweb.mapper.Mapper
import org.apache.commons.lang3.StringUtils

import scala.collection.immutable.List
import scala.concurrent.duration._
import scala.reflect.ManifestFactory
import scala.reflect.runtime.universe
import scala.reflect.runtime.universe._

trait CustomJsonFormats {
  implicit val formats: Formats = CustomJsonFormats.formats
}

object CustomJsonFormats {

  val formats: Formats = JsonSerializers.commonFormats + ListResultSerializer + MapperSerializer

  val losslessFormats: Formats =  net.liftweb.json.DefaultFormats.lossless + ListResultSerializer ++ JsonSerializers.serializers + MapperSerializer

  val emptyHintFormats = DefaultFormats.withHints(ShortTypeHints(List())) + ListResultSerializer ++ JsonSerializers.serializers + MapperSerializer

  implicit val nullTolerateFormats = formats + JNothingSerializer

  lazy val rolesMappedToClassesFormats: Formats = new Formats {
    val dateFormat = net.liftweb.json.DefaultFormats.dateFormat

    override val typeHints = ShortTypeHints(rolesMappedToClasses)
  } + ListResultSerializer ++ JsonSerializers.serializers + MapperSerializer
}

object ListResultSerializer extends Serializer[ListResult[_]] {
  private val clazz = classOf[ListResult[_]]

  def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), ListResult[_]] = {
    case (TypeInfo(entityType, Some(parameterizedType)), json) if clazz.isAssignableFrom(entityType) => json match {
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

  private def addMissingFields(jObject: JObject, missingFieldNames: List[String]): JObject = {
    val JObject(obj) = jObject
    val newFields: List[JField] = obj ::: (addedMissingFields :: missingFieldNames).map(JField(_, JNull))
    JObject(newFields)
  }

  private def isNoMissingFields(jValue: JValue): Boolean = (jValue \ addedMissingFields) != JNothing

  override def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), Any] = {
    case JNothingSerializer(typeInfo, jValue: JObject, missingFieldNames) => {
      val newJValue =  addMissingFields(jValue, missingFieldNames)
      Extraction.extract(newJValue, typeInfo)
    }
  }

  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = Functions.doNothing


  private[this] def unapply(arg: (TypeInfo, JValue))(implicit formats: Formats): Option[(TypeInfo, JValue, List[String])] =  {
    val (TypeInfo(clazz, _), jValue) = arg
    if (! ReflectUtils.isObpClass(clazz) || jValue == JNothing || jValue == JNull || isNoMissingFields(jValue)) {
      None
    } else {
      val jsonFieldNames: Set[String] = jValue.asInstanceOf[JObject].obj.toSet[JField].collect {
        case JField(name, v) if v != JNothing => name
      }

      val missingFields: List[String] = missingFieldNames(clazz, jsonFieldNames)
      missingFields match {
        case Nil => None
        case x => Some((arg._1, arg._2, x))
      }
    }
  }

  private[this] def missingFieldNames(clazz: Class[_], jsonFieldNames: Set[String]): List[String] = {
    // cache 2 hours, the cache time can be very long, because the result will never be changed
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider (Some(cacheKey.toString())) (2 hours) {

        val constructors: Array[Constructor[_]] = clazz.getDeclaredConstructors()
        bestMatching(constructors, jsonFieldNames) match {
          case None => Nil
          case Some(array: Array[Arg]) =>
            array.toList collect {
              case arg if arg.required && !jsonFieldNames.contains(arg.path) => arg.path
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
    } else if(constructors.size == 1) {
      constructors.headOption.map(_.getParameters.map(Arg(_)))
    } else {
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

    val optional = {
      val optionClass: Class[Option[_]] = classOf[Option[_]]
      val boxClass: Class[Box[_]] = classOf[Box[_]]
      val paramType = parameter.getType()
      optionClass.isAssignableFrom(paramType) || boxClass.isAssignableFrom(paramType)
    }
    val required = !optional
  }
}

object FieldIgnoreSerializer extends Serializer[AnyRef] {
  private val memo = new Memo[universe.Type, List[String]]()
  override def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, json.JValue), AnyRef] = Functions.doNothing
  private lazy val propsConfigIgnoreFields = Array(
      APIUtil.getPropsValue("outbound.ignore.fields", "").split("""\s*,\s*"""),
      APIUtil.getPropsValue("inbound.ignore.fields", "").split("""\s*,\s*""")
    ).flatten.filterNot(StringUtils.isBlank).toList

  // keep current process InBound or OutBound instance, avoid dead loop.
  private val threadLocal = new java.lang.ThreadLocal[Any]

  override def serialize(implicit format: Formats): PartialFunction[Any, json.JValue] = {
    case x if isInOutBoundType(x) && threadLocal.get() == null =>
      threadLocal.set(x)
      try{
        toIgnoreFieldJson(x)
      } finally {
        threadLocal.remove()
      }
  }

  def toIgnoreFieldJson(x: Any)(implicit formats: Formats): JValue = toIgnoreFieldJson(x, ReflectUtils.getType(x))

  def toIgnoreFieldJson(x: Any, tp: universe.Type, ignoreFunc: List[String] => List[String] = Functions.unary)(implicit formats: Formats): JValue = {
    val ignoreFieldNames: List[String] = getIgnores(tp) ::: propsConfigIgnoreFields
    val zson = json.Extraction.decompose(x)
    ignoreFieldNames match {
      case Nil => zson
      case ignores => JsonUtils.deleteFields(zson, ignoreFunc(ignores))
    }
  }

  private def isInOutBoundType(any: Any) = {
    if(ReflectUtils.isObpObject(any)) {
      val className = any.getClass.getSimpleName
      className.startsWith("OutBound") || className.startsWith("InBound")
    } else {
      false
    }
  }

  def getIgnores(tp: universe.Type): List[String] = {
    if(!ReflectUtils.isObpType(tp)) {
      return Nil
    }
    memo.memoize(tp){
      val fields: List[universe.Symbol] = tp.decls.filter(decl => decl.isTerm && (decl.asTerm.isVal || decl.asTerm.isVar)).toList
      val (ignoreFields, notIgnoreFields) = fields.partition(_.annotations.exists(_.tree.tpe <:< typeOf[ignore]))
      val annotedFieldNames = ignoreFields.map(_.name.decodedName.toString.trim)
      val subAnnotedFieldNames = notIgnoreFields.flatMap(it => {
        val fieldName = it.name.decodedName.toString.trim
        val fieldType: universe.Type = it.info match {
          case x if x <:< typeOf[Iterable[_]] &&  !(x <:< typeOf[Map[_,_]]) =>
            x.typeArgs.head
          case x if x <:< typeOf[Array[_]] =>
            x.typeArgs.head
          case x => x
        }

        getIgnores(fieldType)
          .map(it =>  s"$fieldName.$it")
      })
      annotedFieldNames ++ subAnnotedFieldNames
    }
  }
}

/**
 * serialize DB Mapped object to JValue
 */
object MapperSerializer extends Serializer[Mapper[_]] {
  /**
   * `call by name` method names those defined in Mapper trait.
   */
  val mapperMethods: Set[String] = typeOf[Mapper[_]].decls.filter(it => it.isMethod && it.asMethod.paramLists.isEmpty).map(_.name.decodedName.toString).toSet

  private val memo = new Memo[universe.Type, Iterable[universe.MethodSymbol]]

  override def serialize(implicit format: Formats): PartialFunction[Any, json.JValue] = {
    case x: Mapper[_] =>
      val tp: universe.Type = ReflectUtils.getType(x)
      val instanceMirror: InstanceMirror = ReflectUtils.getInstanceMirror(x)
      val callByNameMethods = memo.memoize(tp) {
        tp.decls.filter(it => it.isMethod && it.overrides.nonEmpty && it.asMethod.paramLists.isEmpty && !mapperMethods.contains(it.name.decodedName.toString))
          .map(_.asMethod)
      }
      // mapper object to Map[String, _]
      val map: Map[String, Any] = callByNameMethods.map(method => {
        val methodName = method.name.decodedName.toString
        val value = instanceMirror.reflectMethod(method).apply()
        methodName -> value
      }).toMap
      json.Extraction.decompose(map)
  }


  override def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, json.JValue), Mapper[_]] = Functions.doNothing
}
