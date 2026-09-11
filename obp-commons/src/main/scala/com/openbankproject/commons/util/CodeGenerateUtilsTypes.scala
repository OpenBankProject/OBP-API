package com.openbankproject.commons.util

import com.openbankproject.commons.dto.CustomerAndAttribute
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication
import com.openbankproject.commons.model.{CardAction, CardReplacementReason, InboundAdapterCallContext, OutboundAdapterCallContext, PinResetReason, Status}

import java.util.Date
import scala.reflect.runtime.universe._

/**
 * The `Type` constants `code.api.util.CodeGenerateUtils` (obp-api) dispatches on.
 *
 * Same reason as `SwaggerTypes`: each is `typeOf[T]` for a type that lives in obp-commons or the
 * JDK, which needs the Scala 2 compiler's TypeTag synthesis. obp-commons stays on 2.13, so these
 * are computed once, here.
 */
object CodeGenerateUtilsTypes {

  val tOutboundAdapterCallContext: Type = typeOf[OutboundAdapterCallContext]
  val tInboundAdapterCallContext: Type = typeOf[InboundAdapterCallContext]
  val tStatus: Type = typeOf[Status]
  val tString: Type = typeOf[String]
  val tListCustomerAndAttribute: Type = typeOf[List[CustomerAndAttribute]]
  val tCardAction: Type = typeOf[CardAction]
  val tCardReplacementReason: Type = typeOf[CardReplacementReason]
  val tPinResetReason: Type = typeOf[PinResetReason]
  val tStrongCustomerAuthenticationValue: Type = typeOf[StrongCustomerAuthentication.Value]
  val tEnumValue: Type = typeOf[EnumValue]
  val tDate: Type = typeOf[Date]
  val tBigDecimal: Type = typeOf[BigDecimal]
  val tBigInt: Type = typeOf[BigInt]
  val tInt: Type = typeOf[Int]
  val tJavaInteger: Type = typeOf[java.lang.Integer]
  val tLong: Type = typeOf[Long]
  val tJavaLong: Type = typeOf[java.lang.Long]
  val tFloat: Type = typeOf[Float]
  val tJavaFloat: Type = typeOf[java.lang.Float]
  val tDouble: Type = typeOf[Double]
  val tJavaDouble: Type = typeOf[java.lang.Double]
  val tBoolean: Type = typeOf[Boolean]
  val tJavaBoolean: Type = typeOf[java.lang.Boolean]
  val tOptionWildcard: Type = typeOf[Option[_]]
  val tMapStringListString: Type = typeOf[Map[String, List[String]]]
}
