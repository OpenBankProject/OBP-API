package code.api.util

import code.api.ChargePolicy
import code.api.util.ApiRole.rolesMappedToClasses
import code.api.v3_1_0.ListResult
import code.consent.ConsentStatus
import code.context.UserAuthContextUpdateStatus
import code.transactionrequests.TransactionRequests.{TransactionChallengeTypes, TransactionRequestStatus, TransactionRequestTypes}
import com.openbankproject.commons.model.AccountAttributeType.AccountAttributeType
import com.openbankproject.commons.model.{AccountAttributeType, CardAttributeType, JsonFieldReName, ProductAttributeType}
import com.openbankproject.commons.util.ReflectUtils
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json._
import net.liftweb.util.StringHelpers

import scala.reflect.runtime.{universe => ru}
import scala.reflect.{ClassTag, ManifestFactory}

trait CustomJsonFormats {
  implicit val formats: Formats = CustomJsonFormats.formats
}

object CustomJsonFormats {

  val formats: Formats = net.liftweb.json.DefaultFormats + BigDecimalSerializer + FiledRenameSerializer + IdTypeSerializer + ListResultSerializer ++ EnumerationSerializer.enumerationSerizers

  val losslessFormats: Formats =  net.liftweb.json.DefaultFormats.lossless + BigDecimalSerializer + FiledRenameSerializer + IdTypeSerializer + ListResultSerializer ++ EnumerationSerializer.enumerationSerizers

  val emptyHintFormats = DefaultFormats.withHints(ShortTypeHints(List())) + BigDecimalSerializer + FiledRenameSerializer + IdTypeSerializer + ListResultSerializer ++ EnumerationSerializer.enumerationSerizers

  lazy val rolesMappedToClassesFormats: Formats = new Formats {
    val dateFormat = net.liftweb.json.DefaultFormats.dateFormat

    override val typeHints = ShortTypeHints(rolesMappedToClasses)
  } + BigDecimalSerializer + FiledRenameSerializer + IdTypeSerializer + ListResultSerializer ++ EnumerationSerializer.enumerationSerizers
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

/*object EnumerationSerializer extends Serializer[Enumeration] {

  override def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), Enumeration] = {
    case (typeInfo @ TypeInfo(entityType, _), json) if(isEnumeType(entityType)) => {
      val enumName = json.asInstanceOf[JString].s
      val withName = entityType.getMethod("withName", classOf[String])
      withName.invoke(null, enumName).asInstanceOf[Enumeration]
    }
  }

  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case x if(isEnume(x)) => JString(x.toString())
  }

  private def isEnume(obj: Any) = {
   obj != null &&  obj.getClass.getName == "scala.Enumeration$Val"
 }
  private def isEnumeType(clazz: Class[_]) = {
    clazz.getName == "scala.Enumeration$Value"
  }
}*/
class EnumerationSerializer[T <: Enumeration: ClassTag](enum: Enumeration ) extends Serializer[T] {

  import JsonDSL._
  // def EnumerationClass[T] = classOf[T]

  override def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), T] = {
    case (TypeInfo(clazz, _), json) => json match {
      case JObject(List(JField(name, JString(value)))) => fetchEnumValue(value)
      case JString(value) => fetchEnumValue(value)
      case value => throw new MappingException("Can't convert " + value + " to " + clazz)
    }
  }

  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case x: T#Value => x.toString
  }

  private def fetchEnumValue(value: String): T = {
    enum.values.find(_.toString == value).getOrElse(throw new Exception("Invalid enum value => " + value)).asInstanceOf[T]
  }
}

object EnumerationSerializer{
  val enumerationSerizers =  new EnumerationSerializer(ConsentStatus) ::
                                new EnumerationSerializer(AccountAttributeType) ::
                                new EnumerationSerializer(ProductAttributeType) ::
                                new EnumerationSerializer(CardAttributeType) ::
                                new EnumerationSerializer(StrongCustomerAuthentication) ::
                                new EnumerationSerializer(UserAuthContextUpdateStatus) ::
                                new EnumerationSerializer(TransactionRequestStatus) ::
                                new EnumerationSerializer(TransactionChallengeTypes) ::
                                new EnumerationSerializer(TransactionRequestTypes) ::
                                new EnumerationSerializer(CryptoSystem) ::
                                new EnumerationSerializer(RateLimitPeriod) ::
                                new EnumerationSerializer(ApiStandards) ::
                                new EnumerationSerializer(ApiShortVersions) ::
                                new EnumerationSerializer(ChargePolicy) ::
                                Nil
}

@scala.annotation.meta.field
@scala.annotation.meta.param
class ignore extends scala.annotation.StaticAnnotation

@scala.annotation.meta.field
@scala.annotation.meta.param
class optional extends scala.annotation.StaticAnnotation


