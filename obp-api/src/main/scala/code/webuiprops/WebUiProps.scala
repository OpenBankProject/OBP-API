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

package code.webuiprops

/* For Connector method routing, star connector use this provider to find proxy connector name */

import com.openbankproject.commons.model.{Converter, JsonFieldReName}
import net.liftweb.common.Box

trait WebUiPropsT {
  def webUiPropsId: Option[String]
  def name: String
  def value: String
  def source: Option[String]
}

case class WebUiPropsCommons(name: String,
                             value: String, 
                             webUiPropsId: Option[String] = None,
                             source: Option[String] = None) extends WebUiPropsT with JsonFieldReName

object WebUiPropsCommons extends Converter[WebUiPropsT, WebUiPropsCommons]

case class WebUiPropsPutJsonV600(value: String) extends JsonFieldReName

trait WebUiPropsProvider {
  def getAll(): List[WebUiPropsT]

  def getByName(name: String): Box[WebUiPropsT]

  def createOrUpdate(webUiProps: WebUiPropsT): Box[WebUiPropsT]

  def delete(webUiPropsId: String):Box[Boolean]

  def getWebUiPropsValue(nameOfProperty: String, defaultValue: String, language: String): String
}






