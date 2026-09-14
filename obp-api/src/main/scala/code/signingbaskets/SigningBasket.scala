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

package code.signingbaskets


import com.openbankproject.commons.model.{SigningBasketContent, SigningBasketTrait}
import net.liftweb.common.{Box, Logger}
import net.liftweb.util.SimpleInjector
import code.util.Helper.MdcLoggable

object SigningBasketX extends SimpleInjector {
  val signingBasketProvider: SigningBasketX.Inject[SigningBasketProvider] = new Inject(() => buildOne) {}
  private def buildOne: SigningBasketProvider = MappedSigningBasketProvider
}

trait SigningBasketProvider extends MdcLoggable {

  def getSigningBaskets(): List[SigningBasketTrait]

  def getSigningBasketByBasketId(entityId: String): Box[SigningBasketContent]
  def saveSigningBasketStatus(entityId: String, status: String): Box[SigningBasketContent]

  def createSigningBasket(paymentIds: Option[List[String]],
                          consentIds: Option[List[String]],
                         ): Box[SigningBasketTrait]

  def deleteSigningBasket(id: String): Box[Boolean]

}
