package com.openbankproject.commons.util

import java.lang.reflect.Modifier

import com.openbankproject.commons.model.JsonFieldReName
import com.openbankproject.commons.model.enums.{SimpleEnum, SimpleEnumCollection}
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json._
import net.liftweb.util.StringHelpers
import org.apache.commons.lang3.StringUtils

import scala.reflect.ManifestFactory
import scala.reflect.runtime.{universe => ru}

object JsonSerializers {
  val serializers = AbstractTypeDeserializer :: SimpleEnumDeserializer ::
    BigDecimalSerializer :: StringDeserializer :: FiledRenameSerializer ::
    EnumValueSerializer :: JsonAbleSerializer :: Nil

  implicit val commonFormats = net.liftweb.json.DefaultFormats ++ serializers
}

trait JsonAble {
  def toJValue(implicit format: Formats): JValue
}
object JsonAble {
  def unapply(jsonAble: JsonAble)(implicit format: Formats): Option[JValue] = Option(jsonAble).map(_.toJValue)
}

object JsonAbleSerializer extends Serializer[JsonAble] {

  override def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), JsonAble] = Functions.doNothing

  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case JsonAble(jValue) => jValue
  }
}

object EnumValueSerializer extends Serializer[EnumValue] {
  private val IntervalClass = classOf[EnumValue]

  override def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), EnumValue] = {
    case (TypeInfo(clazz, _), json) if(IntervalClass.isAssignableFrom(clazz)) => json match {
      case JString(s) => OBPEnumeration.withName(clazz.asInstanceOf[Class[EnumValue]], s)
      case x => throw new MappingException(s"Can't convert $x to $clazz")
    }
  }

  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case x: EnumValue => JString(x.toString())
  }
}

/**
 * deSerialize trait or abstract type json, this Serializer should always put at formats chain first, e.g:
 * DefaultFormats + AbstractTypeDeserializer + ...others
 */
object AbstractTypeDeserializer extends Serializer[AnyRef] {

  override def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), AnyRef] = {
    case (TypeInfo(clazz, _), json) if Modifier.isAbstract(clazz.getModifiers) && ReflectUtils.isObpClass(clazz) =>
      val Some(commonClass) = ReflectUtils.findImplementedClass(clazz)

      implicit val manifest = ManifestFactory.classType[AnyRef](commonClass)
      json.extract[AnyRef](format, manifest)
  }

  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = Functions.doNothing

}

object SimpleEnumDeserializer extends Serializer[SimpleEnum] {
  private val simpleEnumClazz = classOf[SimpleEnum]
  override def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), SimpleEnum] = {
    case (TypeInfo(clazz, _), json) if simpleEnumClazz.isAssignableFrom(clazz) =>
      val JString(enumValue) = json.asInstanceOf[JString]

      ReflectUtils.getObject(clazz.getName) // get Companion instance
        .asInstanceOf[SimpleEnumCollection[SimpleEnum]]
        .valueOf(enumValue)
  }

  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = Functions.doNothing
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

object StringDeserializer extends Serializer[String] {
  private val IntervalClass = classOf[String]

  override def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), String] = {
    case (TypeInfo(IntervalClass, _), json) if !json.isInstanceOf[JString] =>
      compactRender(json)
  }

  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = Functions.doNothing
}

/**
 * when do serialize, fields name to snakify,
 * when do deserialize, fields name to camelify
 */
object FiledRenameSerializer extends Serializer[JsonFieldReName] {
  private val clazz = classOf[JsonFieldReName]

  def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), JsonFieldReName] = {
    case (typeInfo @ TypeInfo(entityType, _), json) if(isNeedRenameFieldNames(entityType, json))=> json match {
      case JObject(fieldList) => {
        val renamedJObject = json.transformField {
          case JField(name, value) => JField(StringHelpers.camelifyMethod(name), value)
        }

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
            case JField(name, jValue) if idFieldToIdValueName.contains(name) => JField(name, JObject(JField(idFieldToIdValueName(name), jValue)))
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




@scala.annotation.meta.field
@scala.annotation.meta.param
class ignore extends scala.annotation.StaticAnnotation

@scala.annotation.meta.field
@scala.annotation.meta.param
class optional extends scala.annotation.StaticAnnotation