package com.openbankproject.commons.util

import scala.reflect.runtime.universe._

/**
 * The `Type` constants `code.api.util.JsonSchemaGenerator` (obp-api) dispatches on.
 *
 * Same reason as `SwaggerTypes`: each is `typeOf[T]` for a JDK/stdlib type, which needs the
 * Scala 2 compiler's TypeTag synthesis. obp-commons stays on 2.13, so these are computed once,
 * here.
 */
object JsonSchemaGeneratorTypes {

  val tString: Type = typeOf[String]
  val tInt: Type = typeOf[Int]
  val tLong: Type = typeOf[Long]
  val tDouble: Type = typeOf[Double]
  val tFloat: Type = typeOf[Float]
  val tBigDecimal: Type = typeOf[BigDecimal]
  val tBoolean: Type = typeOf[Boolean]
  val tJavaUtilDate: Type = typeOf[java.util.Date]
  val tOptionWildcard: Type = typeOf[Option[_]]
  val tListWildcard: Type = typeOf[List[_]]
  val tSeqWildcard: Type = typeOf[Seq[_]]
  val tMapWildcardWildcard: Type = typeOf[Map[_, _]]
  val tAny: Type = typeOf[Any]
}
