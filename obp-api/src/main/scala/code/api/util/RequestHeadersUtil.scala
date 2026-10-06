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

package code.api.util

import code.api.RequestHeader._
import code.api.util.APIUtil.HTTPParam

/**
 * This object is the one place OBP looks up a request header by name.
 *
 * HTTP header names are case-insensitive (RFC 9110, section 5.1), and over HTTP/2 every name arrives
 * in lower case, so a client sending `Consent-JWT` may reach OBP as `consent-jwt`. The http4s request
 * already treats names that way, but `Http4sCallContextBuilder` copies the headers into a
 * `List[HTTPParam]` with the names exactly as received, and a lookup written as `_.name == "Consent-JWT"`
 * then misses the header. Every lookup in a request header list goes through the functions below,
 * which match names ignoring case; HeaderLookupConventionsTest fails the build on one that does not.
 */
object RequestHeadersUtil {

  /** This returns true when the header has the given name, ignoring letter case. */
  def isNamed(header: HTTPParam, name: String): Boolean =
    header != null && header.name != null && header.name.equalsIgnoreCase(name)

  /** This returns true when any header has the given name. */
  def exists(requestHeaders: List[HTTPParam], name: String): Boolean =
    requestHeaders.exists(isNamed(_, name))

  /** This returns the first header with the given name. */
  def find(requestHeaders: List[HTTPParam], name: String): Option[HTTPParam] =
    requestHeaders.find(isNamed(_, name))

  /**
   * This returns the header with the given name only when exactly one distinct header has it, and
   * None when there is none or there are several that differ. Identical repeats count once. It is for
   * credentials such as Consent-JWT, where a request carrying two different values is ambiguous.
   */
  def findSingle(requestHeaders: List[HTTPParam], name: String): Option[HTTPParam] =
    requestHeaders.toSet.filter(isNamed(_, name)).toList match {
      case header :: Nil => Some(header)
      case _ => None
    }

  /** This groups the headers by name, ignoring letter case; the keys are the names in lower case. */
  def groupByName(requestHeaders: List[HTTPParam]): Map[String, List[HTTPParam]] =
    requestHeaders.groupBy(_.name.toLowerCase(java.util.Locale.ROOT))

  def checkEmptyRequestHeaderValues(requestHeaders: List[HTTPParam]): List[String] = {
    val emptyValues = requestHeaders
      .filter(header => header != null && (header.values == null || header.values.isEmpty || header.values.exists(_.trim.isEmpty)))
      .map(_.name) // Extract header names with empty values

    emptyValues
  }
  def checkEmptyRequestHeaderNames(requestHeaders: List[HTTPParam]): List[String] = {
    val emptyNames = requestHeaders
      .filter(header => header == null || header.name == null || header.name.trim.isEmpty)
      .map(_.values.mkString("'")) // List values without names

    emptyNames
  }

}
