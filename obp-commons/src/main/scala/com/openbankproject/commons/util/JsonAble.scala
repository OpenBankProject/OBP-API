package com.openbankproject.commons.util

import net.liftweb.json._

trait JsonAble {
  def toJValue: JValue
}
object JsonAble {
  def unapply(jsonAble: JsonAble): Option[JValue] = Option(jsonAble).map(_.toJValue)
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