package com.openbankproject.commons.util

import net.liftweb.json.{Formats, JValue, Serializer, TypeInfo}

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