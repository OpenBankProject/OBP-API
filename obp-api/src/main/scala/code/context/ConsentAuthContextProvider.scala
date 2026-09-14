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

package code.context

import code.api.util.APIUtil
import com.openbankproject.commons.model.{BasicUserAuthContext, ConsentAuthContext}
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.collection.immutable.List
import scala.concurrent.Future


object ConsentAuthContextProvider extends SimpleInjector {

  val consentAuthContextProvider = new Inject(() => buildOne) {}

  def buildOne: ConsentAuthContextProvider = MappedConsentAuthContextProvider
  
}

trait ConsentAuthContextProvider {
  def createConsentAuthContext(consentId: String, key: String, value: String): Future[Box[ConsentAuthContext]]
  def getConsentAuthContexts(consentId: String): Future[Box[List[ConsentAuthContext]]]
  def getConsentAuthContextsBox(consentId: String): Box[List[ConsentAuthContext]]
  def createOrUpdateConsentAuthContexts(consentId: String, userAuthContexts: List[BasicUserAuthContext]): Box[List[ConsentAuthContext]]
  def deleteConsentAuthContexts(consentId: String): Future[Box[Boolean]]
  def deleteConsentAuthContextById(consentAuthContextId: String): Future[Box[Boolean]]
}