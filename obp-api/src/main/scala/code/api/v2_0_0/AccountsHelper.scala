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

package code.api.v2_0_0

import com.openbankproject.commons.model.CoreAccount

/**
  * this helper is make sure some common value or function can be used by different APIMethodsXxx
  * because they are in different scope, any value defined in one trait, can't be access by others, just copy
  * pass cause duplicated code.
  */
object AccountsHelper {
  // accountTypeFilter doc part text
  def accountTypeFilterText(url: String) =
    s"""
      |optional request parameters:
      |
      |* account_type_filter: one or many accountType value, split by comma
      |* account_type_filter_operation: the filter type of account_type_filter, value must be INCLUDE or EXCLUDE
      |
      |whole url example:
      |$url?account_type_filter=330,CURRENT+PLUS&account_type_filter_operation=INCLUDE
    """.stripMargin


}
