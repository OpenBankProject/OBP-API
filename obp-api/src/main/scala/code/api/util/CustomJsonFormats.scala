package code.api.util

import code.api.ChargePolicy
import code.api.util.ApiRole.rolesMappedToClasses
import code.api.v3_1_0.ListResult
import code.consent.ConsentStatus
import code.context.UserAuthContextUpdateStatus
import code.transactionrequests.TransactionRequests.{TransactionChallengeTypes, TransactionRequestStatus, TransactionRequestTypes}
import com.openbankproject.commons.model.{AccountAttributeType, CardAttributeType, JsonFieldReName, ProductAttributeType}
import com.openbankproject.commons.util.ReflectUtils
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json._
import net.liftweb.util.StringHelpers

import scala.reflect.runtime.{universe => ru}
import scala.reflect.{ManifestFactory}

trait CustomJsonFormats {
  implicit val formats: Formats = CustomJsonFormats.formats
}

object CustomJsonFormats {
  private val customSerializers =
                          BigDecimalSerializer ::
                          FiledRenameSerializer ::
                          ListResultSerializer ::
                          IdTypeSerializer ::
                          EnumerationSerializer.enumerationSerializers ::
                          Nil

  val formats: Formats = net.liftweb.json.DefaultFormats ++ customSerializers

  val losslessFormats: Formats =  net.liftweb.json.DefaultFormats.lossless ++ customSerializers

  val emptyHintFormats = DefaultFormats.withHints(ShortTypeHints(List())) ++ customSerializers

  lazy val rolesMappedToClassesFormats: Formats = new Formats {
    val dateFormat = net.liftweb.json.DefaultFormats.dateFormat

    override val typeHints = ShortTypeHints(rolesMappedToClasses)
  } ++ customSerializers
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
          val nullFields = optionalFields.filter(pair => !children.contains(pair._1)).map(pair => JField(pair._1, pair._2)).toList
          val jObject: JValue = JObject(children ++: nullFields)
          jObject
        }
        Extraction.extract(addedNullValues,typeInfo).asInstanceOf[JsonFieldReName]
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
          JField(paramName, Extraction.decompose(paramValue))
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

/**
  * the reason of type parameter is Any instead of ListResult[_]:
  * 1. if use ListResult[_], it is impossible to add this to a List: CustomJsonFormats#customSerializers
  * 2. this type parameter not really have any function.
  */
object ListResultSerializer extends Serializer[Any] {
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

object IdTypeSerializer extends Serializer[Any] {

  def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), Any] = {
    case (typeInfo @ TypeInfo(entityType, _), json) if(isSomeIdType(entityType)) => {
      val singleParamName = ReflectUtils.getPrimaryConstructor(ReflectUtils.classToSymbol(entityType)).paramLists.head.head.name.toString
      val idObject = JObject(List(JField(singleParamName, json)))
      Extraction.extract(idObject,typeInfo)
    }
  }

  def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case x if(isSomeId(x)) => {
      val idValue = ReflectUtils.getConstructorArgs(x).head._2
      Extraction.decompose(idValue)
    }
  }


  // check given object is some Id, only type name ends with "Id" and have a single param constructor
  private def isSomeId(obj: Any) = obj match {
    case null => false
    case _ => obj.getClass.getSimpleName.endsWith("Id") && ReflectUtils.getPrimaryConstructor(obj).asMethod.paramLists.headOption.exists(_.size == 1)
  }
  private def isSomeIdType(clazz: Class[_]) = clazz.getSimpleName.endsWith("Id") && clazz.getConstructors.exists(_.getParameterTypes.size == 1)

}

/**
  *  Enumeration Serializer, remember: all the Enumeration should add to  code.api.util.EnumerationSerializer#enumerationSerializers
  * @param enums multiple Enumeration
  */
class EnumerationSerializer(enums: Enumeration*) extends Serializer[Enumeration#Value] {
  val EnumerationClass = classOf[Enumeration#Value]
  val formats = Serialization.formats(NoTypeHints)
  def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), Enumeration#Value] = {
    case (TypeInfo(EnumerationClass, _), json) => json match {
      case JString(value) => enums.find(_.values.exists(_.toString == value)).get.withName(value)
      case value          => throw new MappingException("Can't convert " + value + " to " + EnumerationClass)
    }
  }

  def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case i: Enumeration#Value => JString(i.toString)
  }
}

object EnumerationSerializer{
  val enumerationSerializers = new EnumerationSerializer(
    ConsentStatus, AccountAttributeType, ProductAttributeType, CardAttributeType,
    StrongCustomerAuthentication, UserAuthContextUpdateStatus, TransactionRequestStatus, TransactionChallengeTypes,
    TransactionRequestTypes, CryptoSystem, RateLimitPeriod, ApiStandards,
    ApiShortVersions, ChargePolicy
  )
}

@scala.annotation.meta.field
@scala.annotation.meta.param
class ignore extends scala.annotation.StaticAnnotation

@scala.annotation.meta.field
@scala.annotation.meta.param
class optional extends scala.annotation.StaticAnnotation


