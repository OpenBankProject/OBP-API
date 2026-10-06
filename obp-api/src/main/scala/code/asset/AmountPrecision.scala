/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */
package code.asset

import java.util.Locale

import org.json4s.native.JsonMethods
import org.json4s.{JArray, JDecimal, JDouble, JInt, JLong, JObject, JString, JValue}

import scala.util.Try

/**
 * This object finds an amount in a request that has more decimal places than its currency allows,
 * such as 12.345 EUR or 100.5 JPY (ideas/ASSET_REGISTRY.md, section 4).
 *
 * OBP stores an amount in the currency's smallest unit, so before this check an amount with too many
 * decimal places was silently cut off: 12.345 EUR was stored as 12.34. Refusing it instead means the
 * caller learns that the amount cannot be held exactly, and no money disappears.
 *
 * An amount is found where a request pairs it with its currency:
 *
 * - In a JSON body, an object with a field named `amount` (in any letter case, so UK Open Banking's
 *   `Amount` counts) next to the field naming its currency. That is a field named `currency`, or else
 *   the object's only field whose name holds a currency code (see [[CurrencyCodes.isCurrencyKey]]).
 *   The amount may be a string or a JSON number; numbers are read exactly, never through a double.
 * - In a query string, a parameter named `amount` next to a currency parameter, as in
 *   `funds-available?currency=EUR&amount=12.34`.
 *
 * Trailing zeros do not count, so `10.00` is a valid JPY amount. A code OBP does not know, an amount
 * that is not a number, and a crypto asset (see [[AssetLookup.enforcedDecimalPlaces]]) are left to
 * the endpoint.
 */
object AmountPrecision {

  /** An amount with more decimal places than its currency allows. */
  case class ExcessPrecision(amount: String, currency: String, decimalPlaces: Int, allowedDecimalPlaces: Int) {
    def describe: String =
      s"The amount $amount $currency has $decimalPlaces decimal place(s), but $currency allows at most $allowedDecimalPlaces."
  }

  /** This returns the number of decimal places of an amount, not counting trailing zeros. */
  def decimalPlacesOf(amount: BigDecimal): Int = math.max(0, amount.bigDecimal.stripTrailingZeros.scale)

  private def isAmountKey(name: String): Boolean = name.toLowerCase(Locale.ROOT) == "amount"

  /** This checks one amount against its currency. */
  def check(currency: String, amount: BigDecimal, amountAsSent: String): Option[ExcessPrecision] =
    AssetLookup.enforcedDecimalPlaces(currency).flatMap { allowed =>
      val places = decimalPlacesOf(amount)
      if (places > allowed) Some(ExcessPrecision(amountAsSent, CurrencyCodes.normalise(currency), places, allowed))
      else None
    }

  private def parseAmount(text: String): Option[BigDecimal] = Try(BigDecimal(text.trim)).toOption

  /**
   * This returns the currency that an object's amount is in: the field named `currency`, or else the
   * only field whose name holds a currency code. With several such fields and none named `currency`
   * the pairing is ambiguous, so there is none.
   */
  private def currencyOf(fields: List[(String, String)]): Option[String] = {
    val currencyFields = fields.filter { case (name, _) => CurrencyCodes.isCurrencyKey(name) }
    currencyFields.find { case (name, _) => name.toLowerCase(Locale.ROOT) == "currency" }
      .orElse(if (currencyFields.size == 1) currencyFields.headOption else None)
      .map(_._2)
  }

  /** This finds the first amount with too many decimal places anywhere in a JSON value. */
  def inJson(json: JValue): Option[ExcessPrecision] = json match {
    case JObject(fields) =>
      val stringFields = fields.collect { case (name, JString(value)) => (name, value) }
      val here = for {
        currency <- currencyOf(stringFields)
        (amount, amountAsSent) <- fields.collectFirst {
          case (name, JString(text)) if isAmountKey(name) => parseAmount(text).map(_ -> text)
          case (name, JDecimal(number)) if isAmountKey(name) => Some(number -> number.toString)
          case (name, JDouble(number)) if isAmountKey(name) => Some(BigDecimal(number.toString) -> number.toString)
          case (name, JInt(number)) if isAmountKey(name) => Some(BigDecimal(number) -> number.toString)
          case (name, JLong(number)) if isAmountKey(name) => Some(BigDecimal(number) -> number.toString)
        }.flatten
        found <- check(currency, amount, amountAsSent)
      } yield found
      here.orElse(fields.iterator.map { case (_, value) => inJson(value) }.collectFirst { case Some(found) => found })
    case JArray(items) =>
      items.iterator.map(inJson).collectFirst { case Some(found) => found }
    case _ => None
  }

  /**
   * This finds the first amount with too many decimal places in a JSON request body. A body that
   * does not mention an amount is not parsed, and one that is not JSON is left to the endpoint.
   */
  def inJsonBody(body: String): Option[ExcessPrecision] =
    if (body == null || !body.toLowerCase(Locale.ROOT).contains("amount")) None
    else Try(JsonMethods.parse(body, useBigDecimalForDouble = true)).toOption.flatMap(inJson)

  /** This finds an amount with too many decimal places in query parameters. */
  def inQuery(parameters: Seq[(String, Option[String])]): Option[ExcessPrecision] = {
    val values = parameters.collect { case (name, Some(value)) => (name, value) }.toList
    for {
      currency <- currencyOf(values)
      amountAsSent <- values.collectFirst { case (name, value) if isAmountKey(name) => value }
      amount <- parseAmount(amountAsSent)
      found <- check(currency, amount, amountAsSent)
    } yield found
  }
}
