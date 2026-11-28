package com.openbankproject.commons.util

trait JsonAble {
  def toJValue(implicit format: json.Formats): json.JValue
}
object JsonAble {
  def unapply(jsonAble: JsonAble)(implicit format: json.Formats): Option[json.JValue] = Option(jsonAble).map(_.toJValue)
}

@scala.annotation.meta.field
@scala.annotation.meta.param
class optional extends scala.annotation.StaticAnnotation
