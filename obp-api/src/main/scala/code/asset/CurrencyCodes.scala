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

import code.api.util.CustomJsonFormats
import org.json4s.{Extraction, JArray, JObject, JString, JValue}

import scala.collection.concurrent.TrieMap
import scala.util.Try

/**
 * This object holds OBP's rule that currency codes are case-insensitive: `eur`, `Eur` and `EUR`
 * name the same asset (ideas/ASSET_REGISTRY.md, section 4).
 *
 * The rule is applied in two ways. Codes coming in are upper-cased before any endpoint sees them:
 * ResourceDocMiddleware upper-cases the currency fields of a JSON request body and the currency query
 * parameters, so new rows are stored upper case. Codes already stored are compared ignoring case,
 * with [[same]], because a row written before this rule may hold a code in another case (`ada`,
 * `lovelace` and `wei` were accepted only in lower case). Lookups in the registry ignore case too
 * ([[AssetLookup]]).
 *
 * A field or query parameter holds a currency code when its name, ignoring case and underscores,
 * ends in `currency` or `currencycode`: `currency`, `price_currency`, `from_currency_code` and
 * UK Open Banking's `Currency` all do, `currency_status` does not.
 */
object CurrencyCodes {

  /** This returns the code in its stored form: trimmed and upper case. */
  def normalise(code: String): String =
    if (code == null) code else code.trim.toUpperCase(Locale.ROOT)

  /** This returns true when the two codes name the same asset, whatever their letter case. */
  def same(first: String, second: String): Boolean =
    first != null && second != null && normalise(first) == normalise(second)

  /** This returns true when a JSON field or query parameter with this name holds a currency code. */
  def isCurrencyKey(name: String): Boolean = {
    val squashed = name.toLowerCase(Locale.ROOT).replace("_", "")
    squashed.endsWith("currency") || squashed.endsWith("currencycode")
  }

  /**
   * A value is only upper-cased when it looks like a code, so free text that happens to sit in such
   * a field is left alone.
   */
  private val CodeLikeValue = "^[A-Za-z0-9]{2,12}$".r

  def looksLikeCode(value: String): Boolean = CodeLikeValue.findFirstIn(value.trim).isDefined

  /**
   * This upper-cases the string values of the given fields wherever they appear in a JSON body. It
   * works on the text, so amounts and every other value keep their exact form: parsing and
   * re-rendering the body could turn a decimal amount sent as a JSON number into a double.
   */
  def normaliseJsonBody(body: String, currencyFields: Set[String]): String =
    if (currencyFields.isEmpty || body == null) body
    else {
      val field = currencyFields.toList.sorted.map(java.util.regex.Pattern.quote).mkString("|")
      val pattern = ("\"(" + field + ")\"(\\s*:\\s*)\"([A-Za-z0-9]{2,12})\"").r
      pattern.replaceAllIn(body, found =>
        java.util.regex.Matcher.quoteReplacement("\"" + found.group(1) + "\"" + found.group(2) + "\"" + normalise(found.group(3)) + "\""))
    }

  /**
   * This returns the names of the fields in an endpoint's documented example body that hold a
   * currency code as a string. Only those fields are upper-cased in that endpoint's requests, so a
   * body the endpoint stores as given (a swagger document, a Dynamic Entity definition) is not
   * rewritten because it happens to contain a field called `currency`.
   */
  def currencyFieldsOf(exampleRequestBody: Any): Set[String] = {
    def collect(json: JValue): Set[String] = json match {
      case JObject(fields) => fields.flatMap {
        case (name, JString(_)) if isCurrencyKey(name) => Set(name)
        case (_, other) => collect(other)
      }.toSet
      case JArray(items) => items.flatMap(collect).toSet
      case _ => Set.empty
    }
    val json: Option[JValue] = exampleRequestBody match {
      case value: JValue => Some(value)
      case null => None
      case other => Try(Extraction.decompose(other)(CustomJsonFormats.formats)).toOption
    }
    json.map(collect).getOrElse(Set.empty)
  }

  private val currencyFieldsByOperationId = TrieMap.empty[String, Set[String]]

  /** This is [[currencyFieldsOf]], worked out once per endpoint. */
  def currencyFieldsOf(operationId: String, exampleRequestBody: => Any): Set[String] =
    currencyFieldsByOperationId.getOrElseUpdate(operationId, currencyFieldsOf(exampleRequestBody))
}
