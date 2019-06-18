package code.api.util

import java.util.regex.Pattern

import code.api.util.ApiRole.rolesMappedToClasses
import code.api.v3_1_0.ListResult
import com.openbankproject.commons.model.JsonFieldReName
import com.openbankproject.commons.util.ReflectUtils
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json._
import net.liftweb.util.StringHelpers

import scala.reflect.runtime.{universe => ru}

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

        val optionalFields = getAnnotedFields(entityType, ru.typeOf[optional])
          .map{
            case (name, tp) if(tp <:< ru.typeOf[Long] || tp <:< ru.typeOf[Int] || tp <:< ru.typeOf[Short] || tp <:< ru.typeOf[Byte] || tp <:< ru.typeOf[Int]) => (name, JInt(0))
            case (name, tp) if(tp <:< ru.typeOf[Double] || tp <:< ru.typeOf[Float]) => (name, JDouble(0))
            case (name, tp) if(tp <:< ru.typeOf[Boolean]) => (name, JBool(false))
            case (name, tp) => (name, JNull)
          }

        val addedNullValues: JValue = if(optionalFields.isEmpty) {
          renamedJObject
        } else {
          val children = renamedJObject.asInstanceOf[JObject].obj
          val childrenNames = children.map(_.name)
          val nullFields = optionalFields.filter(pair => !children.contains(pair._1)).map(pair => JField(pair._1, pair._2)).toList
          val jObject: JValue = JObject(children ++: nullFields)
          jObject
        }

        val idFieldToIdValueName: Map[String, String] = getSomeIdFieldInfo(entityType)
        val processedIdJObject = if(idFieldToIdValueName.isEmpty){
            addedNullValues
          } else {
            addedNullValues.mapField {
              case JField(name, jValue) if(idFieldToIdValueName.contains(name)) => JField(name, JObject(JField(idFieldToIdValueName(name), jValue)))
              case jField => jField
            }
        }
        Extraction.extract(processedIdJObject,typeInfo).asInstanceOf[JsonFieldReName]
      }
      case x => throw new MappingException("Can't convert " + x + " to JsonFieldReName")
    }
  }

  def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case x: JsonFieldReName => {
      val ignoreFieldNames = getObjAnnotedFields(x, ru.typeOf[ignore])
      val renamedJFields = ReflectUtils.getConstructorArgs(x)
        .filter(pair => !ignoreFieldNames.contains(pair._1))
        .map(pair => {
          val paramName = StringHelpers.snakify(pair._1)
          val paramValue = pair._2
          isSomeId(paramValue) match {
            case false => JField(paramName, Extraction.decompose(paramValue))
            case true => {
              val idValue = ReflectUtils.getConstructorArgs(paramValue).head._2
              JField(paramName, Extraction.decompose(idValue))
            }
           }
        }) .toList
      JObject(renamedJFields)
    }
  }

  private[this] def isNeedRenameFieldNames(entityType: Class[_], jValue: JValue): Boolean = {
    val isJsonFieldRename = clazz.isAssignableFrom(entityType)

    isJsonFieldRename  &&
      jValue.isInstanceOf[JObject] &&
      jValue.asInstanceOf[JObject].obj.exists(jField => StringHelpers.camelifyMethod(jField.name) != jField.name)
  }

  // check given object is some Id, only type name ends with "Id" and have a single param constructor
  private def isSomeId(obj: Any) = obj match {
    case null => false
    case _ => obj.getClass.getSimpleName.endsWith("Id") && ReflectUtils.getPrimaryConstructor(obj).asMethod.paramLists.headOption.exists(_.size == 1)
  }
  private def isSomeIdType(tp: ru.Type) = tp.typeSymbol.name.toString.endsWith("Id") && ReflectUtils.getConstructorParamInfo(tp).size == 1

  /**
    * extract constructor params those type is some id, and return the field name to the id constructor value name
    * for example:
    * case class Foo(name: String, bankId: BankId(value:String))
    * getSomeIdFieldInfo(typeOf[Foo]) == Map(("bankId" -> "value"))
    * @param clazz to do extract class
    * @return field name to id type single value name
    */
  private def getSomeIdFieldInfo(clazz: Class[_]) = {
    val paramNameToType: Map[String, ru.Type] = ReflectUtils.getConstructorInfo(clazz)
    paramNameToType
      .filter(nameToType => isSomeIdType(nameToType._2))
      .map(nameToType => {
          val (name, paramType) = nameToType
          val singleParamName = ReflectUtils.getConstructorParamInfo(paramType).head._1
          (name, singleParamName)
        }
      )
  }
  private def getAnnotedFields(clazz: Class[_], annotationType: ru.Type): Map[String, ru.Type] = {
    val symbol  = ReflectUtils.classToSymbol(clazz)
    ReflectUtils.getPrimaryConstructor(symbol.toType)
      .paramLists.headOption.getOrElse(Nil)
      .filter(param =>  param.annotations.exists(_.tree.tpe <:< annotationType))
      .map(it => (it.name.toString, it.info))
      .toMap
  }
  private def getObjAnnotedFields(obj: Any, annotationType: ru.Type): Map[String, ru.Type] = getAnnotedFields(obj.getClass, annotationType)
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



@scala.annotation.meta.field
@scala.annotation.meta.param
class ignore extends scala.annotation.StaticAnnotation

@scala.annotation.meta.field
@scala.annotation.meta.param
class optional extends scala.annotation.StaticAnnotation


