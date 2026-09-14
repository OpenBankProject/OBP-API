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

object RequestHeadersUtil {
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
