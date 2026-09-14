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

import code.model.Consumer
import code.model.dataAccess.AuthUser
import code.util.Helper.MdcLoggable
import net.liftweb.mapper.MappedString
import net.liftweb.util.{FieldError, Helpers}

import scala.collection.immutable.List

object CommonFunctions extends MdcLoggable {
  /**
   * This function is added in order to support iOS/macOS requirements for callbacks.
   * For instance next callback has to be valid: x-com.tesobe.helloobp.ios://callback
   * @param field object which has to be validated
   * @param s is a URI string
   * @return Empty list if URI is valid or FieldError otherwise
   */
  def validUri[T <: MappedString[_]](field: T)(s: String): List[FieldError] = {
    import java.net.URI
    import Helpers.tryo
    if(s.isEmpty)
      Nil
    else if(tryo{new URI(s)}.isEmpty)
      List(FieldError(field, {field.displayName + " must be a valid URI"}))
    else
      Nil
  }

  private def validUrl[T <: MappedString[_]](field: T)(s: String): List[FieldError] = {
    import java.net.URL

    import Helpers.tryo
    if(s.isEmpty)
      Nil
    else if(tryo{new URL(s)}.isEmpty)
      List(FieldError(field, {field.displayName + " must be a valid URL"}))
    else
      Nil
  }
  
}
