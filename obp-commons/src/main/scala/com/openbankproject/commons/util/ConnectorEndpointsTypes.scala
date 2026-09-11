package com.openbankproject.commons.util

import scala.reflect.runtime.universe._

/**
 * The `Type` constants `code.bankconnectors.ConnectorEndpoints` (obp-api) dispatches on.
 *
 * Same reason as `SwaggerTypes`: each is `typeOf[T]` for a stdlib type, which needs the Scala 2
 * compiler's TypeTag synthesis. obp-commons stays on 2.13, so these are computed once, here.
 */
object ConnectorEndpointsTypes {

  val tString: Type = typeOf[String]
  val tInt: Type = typeOf[Int]
  val tBigDecimal: Type = typeOf[BigDecimal]
  val tBoolean: Type = typeOf[Boolean]
  val tListWildcard: Type = typeOf[List[_]]
  val tSetWildcard: Type = typeOf[Set[_]]
  val tArrayWildcard: Type = typeOf[Array[_]]
  val tOptionWildcard: Type = typeOf[Option[_]]
}
