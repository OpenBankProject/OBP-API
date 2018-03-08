/**
Open Bank Project - API
Copyright (C) 2011-2016, TESOBE Ltd

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
TESOBE Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)
  by
  Simon Redfern : simon AT tesobe DOT com
  Stefan Bethge : stefan AT tesobe DOT com
  Everett Sochowski : everett AT tesobe DOT com
  Ayoub Benali: ayoub AT tesobe DOT com

 */
package code.model.dataAccess

import code.api.util.APIUtil
import code.model.{User, UserId}
import code.util.MappedUUID
import net.liftweb.mapper._

/**
  * Refer to AuthUser, see the difference between AuthUser and ResourceUser
  */
class ResourceUser extends LongKeyedMapper[ResourceUser] with User with ManyToMany with OneToMany[Long, ResourceUser]{
  def getSingleton = ResourceUser
  def primaryKeyField = id

  object id extends MappedLongIndex(this)
  object userId_ extends MappedUUID(this)
  object email extends MappedEmail(this, 48){
    override def required_? = false
  }
  object name_ extends MappedString(this, 100){
    override def defaultValue = ""
  }
  object provider_ extends MappedString(this, 100){
    override def defaultValue = APIUtil.getPropsValue("hostname","")
  }

  /**
  * the id of the user at that provider
  */
  object providerId extends MappedString(this, 100){
    override def defaultValue = java.util.UUID.randomUUID.toString
  }

  def emailAddress = {
    val e = email.get
    if(e != null) e else ""
  }

  def idGivenByProvider = providerId.get
  def resourceUserId = UserId(id.get)

  def userId = userId_.get

  def name : String = name_.get
  def provider = provider_.get

  def toCaseClass: ResourceUserCaseClass =
    ResourceUserCaseClass(
      emailAddress = emailAddress,
      idGivenByProvider = idGivenByProvider,
      resourceUserId = resourceUserId.value,
      userId = userId,
      name = name,
      provider = provider
    )

}

object ResourceUser extends ResourceUser with LongKeyedMetaMapper[ResourceUser]{
    override def dbIndexes = UniqueIndex(provider_, providerId) ::super.dbIndexes
}

case class ResourceUserCaseClass(
                                  emailAddress: String,
                                  idGivenByProvider: String,
                                  resourceUserId: Long,
                                  userId: String,
                                  name: String,
                                  provider: String
                                )