package code.api.util

import java.util.regex.Pattern

import code.api.util.ApiRole.rolesMappedToClasses
import code.api.v3_1_0.ListResult
import com.openbankproject.commons.util.ReflectUtils
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json._
import net.liftweb.util.StringHelpers

import scala.reflect.ManifestFactory

trait CustomJsonFormats {
  implicit val formats: Formats = CustomJsonFormats.formats
}

object CustomJsonFormats {

  val formats: Formats = net.liftweb.json.DefaultFormats + BigDecimalSerializer + FiledRenameSerializer + ListResultSerializer

  val losslessFormats: Formats =  net.liftweb.json.DefaultFormats.lossless + BigDecimalSerializer + FiledRenameSerializer + ListResultSerializer

  val emptyHintFormats = DefaultFormats.withHints(ShortTypeHints(List())) + BigDecimalSerializer + FiledRenameSerializer + ListResultSerializer

  lazy val rolesMappedToClassesFormats: Formats = new Formats {
    val dateFormat = net.liftweb.json.DefaultFormats.dateFormat

    override val typeHints = ShortTypeHints(rolesMappedToClasses)
  } + BigDecimalSerializer + FiledRenameSerializer + ListResultSerializer
}

object BigDecimalSerializer extends Serializer[BigDecimal] {
  private val IntervalClass = classOf[BigDecimal]

  override def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), BigDecimal] = {
    case (TypeInfo(IntervalClass, _), json) => json match {
      case JString(s) => BigDecimal(s)
      case x => throw new MappingException("Can't convert " + x + " to BigDecimal")
    }
  }

  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case x: BigDecimal => JString(x.toString())
  }
}

object FiledRenameSerializer extends Serializer[JsonFieldReName] {
  private val clazz = classOf[JsonFieldReName]

  def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), JsonFieldReName] = {
    case (typeInfo @ TypeInfo(entityType, _), json) if(isNeedRenameFieldNames(entityType, json))=> json match {
      case JObject(fieldList) => {
        val renamedJObject = APIUtil.camelifyMethod(json)
        Extraction.extract(renamedJObject,typeInfo).asInstanceOf[JsonFieldReName]
      }
      case x => throw new MappingException("Can't convert " + x + " to JsonFieldReName")
    }
  }

  def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case x: JsonFieldReName => {
      val renamedJFields = ReflectUtils.getConstructorArgs(x)
        .map(pair => {
          val paramName = StringHelpers.snakify(pair._1)
          val paramValue = pair._2
          JField(paramName, Extraction.decompose(paramValue))
        }) .toList
      JObject(renamedJFields)
    }
  }

  private val camelRegex = Pattern.compile("""[a-z0-9][A-Z]|[A-Z]{2,}[a-z]""")

  private[this] def isNeedRenameFieldNames(entityType: Class[_], jvalue: JValue): Boolean = {
    // the reason of the if else clause:
    // when entity type is not JsonFieldReName, not need check the field list, will have better performance
    if(clazz.isAssignableFrom(entityType)) {
      jvalue match {
        case JObject(fieldList) => fieldList.forall(jfield => !camelRegex.matcher(jfield.name).find())
        case _ => false
      }
    } else  {
      false
    }
  }
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

  private val camelRegex = Pattern.compile("""[a-z0-9][A-Z]|[A-Z]{2,}[a-z]""")

  private[this] def isNeedRenameFieldNames(entityType: Class[_], jvalue: JValue): Boolean = {
    // the reason of the if else clause:
    // when entity type is not JsonFieldReName, not need check the field list, will have better performance
    if(clazz.isAssignableFrom(entityType)) {
      jvalue match {
        case JObject(fieldList) => fieldList.forall(jfield => !camelRegex.matcher(jfield.name).find())
        case _ => false
      }
    } else  {
      false
    }
  }
}

/**
  * a mark trait, any type that extends this trait will rename field from Camel-Case to snakify naming
  */
trait JsonFieldReName

