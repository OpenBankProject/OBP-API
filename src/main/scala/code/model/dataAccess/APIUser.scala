/**
Open Bank Project - API
Copyright (C) 2011-2015, TESOBE / Music Pictures Ltd

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
TESOBE / Music Pictures Ltd
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

import code.util.MappedUUID
import net.liftweb.mapper._
import net.liftweb.util.Props

import code.model.{UserId, User, View}

class APIUser extends LongKeyedMapper[APIUser] with User with ManyToMany with OneToMany[Long, APIUser]{
  def getSingleton = APIUser
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
    override def defaultValue = Props.get("hostname","")
  }

  /**
  * the id of the user at that provider
  */
  object providerId extends MappedString(this, 100){
    override def defaultValue = java.util.UUID.randomUUID.toString
  }

  object views_ extends MappedManyToMany(ViewPrivileges, ViewPrivileges.user, ViewPrivileges.view, ViewImpl)

  // Roles
 // THESE ARE NO LONGER USED!!------------------------
  object hasCrmAdminRole extends MappedBoolean(this)
  object hasCrmReaderRole extends MappedBoolean(this)
  object hasCustomerMessageAdminRole extends MappedBoolean(this)

  object hasBranchReaderRole extends MappedBoolean(this)
  object hasAtmReaderRole extends MappedBoolean(this)
  object hasProductReaderRole extends MappedBoolean(this)
  // END of no longer used. TODO remove. --------------

  def emailAddress = {
    val e = email.get
    if(e != null) e else ""
  }

  def idGivenByProvider = providerId.get
  def apiId = UserId(id.get)

  def userId = userId_.get

  def name : String = name_.get
  def provider = provider_.get
  def views: List[View] = views_.toList

  // Depreciated. Do not use.///////////////
  def isCrmAdmin : Boolean = hasCrmAdminRole
  def isCrmReader : Boolean = hasCrmReaderRole
  def isCustomerMessageAdmin : Boolean = hasCustomerMessageAdminRole

  def isBranchReader : Boolean = hasBranchReaderRole
  def isAtmReader : Boolean = hasAtmReaderRole
  def isProductReader : Boolean = hasProductReaderRole
  ////////////////////////////////////////////////////
}

object APIUser extends APIUser with LongKeyedMetaMapper[APIUser]{
    override def dbIndexes = UniqueIndex(provider_, providerId) ::super.dbIndexes
}