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
import com.openbankproject.commons.model.{BasicUserAuthContext, UserAuthContext}
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.collection.immutable.List
import scala.concurrent.Future


object UserAuthContextProvider extends SimpleInjector {

  val userAuthContextProvider = new Inject(() => buildOne) {}

  def buildOne: UserAuthContextProvider = MappedUserAuthContextProvider
  
}

trait UserAuthContextProvider {
  def createUserAuthContext(userId: String, key: String, value: String, consumerId: String): Future[Box[UserAuthContext]]
  def getUserAuthContexts(userId: String): Future[Box[List[UserAuthContext]]]
  def getUserAuthContextsBox(userId: String): Box[List[UserAuthContext]]
  def createOrUpdateUserAuthContexts(userId: String, userAuthContexts: List[BasicUserAuthContext]): Box[List[UserAuthContext]]
  def deleteUserAuthContexts(userId: String): Future[Box[Boolean]]
  def deleteUserAuthContextById(userAuthContextId: String): Future[Box[Boolean]]
}