package com.openbankproject.commons.util

import com.openbankproject.commons.model._

import scala.reflect.runtime.universe._

/**
 * The `Type` constants `code.bankconnectors.rest.RestConnector_vMar2019.convertId` dispatches on.
 *
 * Same reason as `SwaggerTypes`: each is `typeOf[T]` for a type that lives in obp-commons or the
 * JDK, which needs the Scala 2 compiler's TypeTag synthesis. obp-commons stays on 2.13, so these
 * are computed once, here.
 */
object RestConnectorTypes {

  val tString: Type = typeOf[String]
  val tCustomerId: Type = typeOf[CustomerId]
  val tCustomer: Type = typeOf[Customer]
  val tAccountId: Type = typeOf[AccountId]
  val tCoreAccount: Type = typeOf[CoreAccount]
  val tAccountBalance: Type = typeOf[AccountBalance]
  val tAccountHeld: Type = typeOf[AccountHeld]
}
