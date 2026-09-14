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

package code.users

/* For UserAttribute */

import code.api.util.APIUtil
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.enums.UserAttributeType
import net.liftweb.common.{Box, Logger}
import net.liftweb.util.SimpleInjector

import scala.collection.immutable.List
import scala.concurrent.Future

object UserAttributeProvider extends SimpleInjector {

  val userAttributeProvider = new Inject(() => buildOne) {}

  def buildOne: UserAttributeProvider = MappedUserAttributeProvider

  // Helper to get the count out of an option
  def countOfUserAttribute(listOpt: Option[List[UserAttribute]]): Int = {
    val count = listOpt match {
      case Some(list) => list.size
      case None => 0
    }
    count
  }

}

trait UserAttributeProvider extends MdcLoggable {

  def getUserAttributesByUser(userId: String): Future[Box[List[UserAttribute]]]
  def getPersonalUserAttributes(userId: String): Future[Box[List[UserAttribute]]]
  def getNonPersonalUserAttributes(userId: String): Future[Box[List[UserAttribute]]]
  def getUserAttributesByUsers(userIds: List[String]): Future[Box[List[UserAttribute]]]
  def deleteUserAttribute(userAttributeId: String): Future[Box[Boolean]]
  def createOrUpdateUserAttribute(userId: String,
                                  userAttributeId: Option[String],
                                  name: String,
                                  attributeType: UserAttributeType.Value,
                                  value: String,
                                  isPersonal: Boolean): Future[Box[UserAttribute]]
  // End of Trait
}
