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

import code.api.APIFailureNewStyle
import code.api.util.APIUtil.fullBoxOrException
import com.openbankproject.commons.model.User
import net.liftweb.common.{Box, Empty, Failure}
import org.json4s._
import com.openbankproject.commons.util.JsonAliases._


object ErrorUtil {
  def apiFailure(errorMessage: String, httpCode: Int)(forwardResult: (Box[User], Option[CallContext])): (Box[User], Option[CallContext]) = {
    val (_, second) = forwardResult
    val apiFailure = APIFailureNewStyle(
      failMsg = errorMessage,
      failCode = httpCode,
      callContextLight = second.map(_.toLight)
    )
    val failureBox = Empty ~> apiFailure
    (
      fullBoxOrException(failureBox),
      second
    )
  }

  def apiFailureToBox[T](errorMessage: String, httpCode: Int)(cc: Option[CallContext]): Box[T] = {
    val apiFailure = APIFailureNewStyle(
      failMsg = errorMessage,
      failCode = httpCode,
      callContextLight = cc.map(_.toLight)
    )
    val failureBox: Box[T] = Empty ~> apiFailure
    fullBoxOrException(failureBox)
  }



  implicit val formats: Formats = DefaultFormats
  def extractFailureMessage(e: Throwable): String = {
    parse(e.getMessage)
      .extractOpt[APIFailureNewStyle] // Extract message from APIFailureNewStyle
      .map(_.failMsg) // or provide a original one
      .getOrElse(e.getMessage)
  }


}
