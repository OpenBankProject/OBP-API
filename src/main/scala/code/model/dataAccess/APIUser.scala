/**
Open Bank Project - API
Copyright (C) 2011, 2013, TESOBE / Music Pictures Ltd

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

import net.liftweb.mapper._
import net.liftweb.util.Props

import code.model.{User, View}

class APIUser extends LongKeyedMapper[APIUser] with User with ManyToMany with OneToMany[Long, APIUser]{
  def getSingleton = APIUser
  def primaryKeyField = id

  object id extends MappedLongIndex(this)
  object email extends MappedEmail(this, 48){
    override def required_? = false
  }
  object firstName extends MappedString(this, 100){
    override def required_? = false
  }
  object lastName extends MappedString(this, 100){
    override def required_? = false
  }
  object provider_ extends MappedString(this, 100){
    override def defaultValue = Props.get("hostname","")
  }
  object providerId extends MappedString(this, 100){
    override def defaultValue = java.util.UUID.randomUUID.toString
  }

  object views_ extends MappedManyToMany(ViewPrivileges, ViewPrivileges.user, ViewPrivileges.view, ViewImpl)

  def emailAddress = email.get
  def id_ = providerId.get
  def theFirstName : String = firstName.get
  def theLastName : String = lastName.get
  def provider = provider_.get
  def views: List[View] = views_.toList

}

object APIUser extends APIUser with LongKeyedMetaMapper[APIUser]{}