/** 
Open Bank Project - Transparency / Social Finance Web Application
Copyright (C) 2011, 2012, TESOBE / Music Pictures Ltd

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
package code.model
import net.liftweb.mapper._
import java.util.Date
import scala.compat.Platform
import code.model.dataAccess.OBPUser

object AppType extends Enumeration("web", "mobile") 
{
	type AppType = Value
	val Web, Mobile = Value
}

object TokenType extends Enumeration("request", "access")
{
	type TokenType=Value
	val Request, Access = Value
}

class Consumer extends LongKeyedMapper[Consumer]{
	def getSingleton = Consumer
	def primaryKeyField = id
	object id extends MappedLongIndex(this) 
	object key extends MappedString(this, 250){
		override def dbIndexed_? = true
	} 
	object secret extends MappedString(this, 250)
	object isActive extends MappedBoolean(this)
	object name extends MappedString(this, 100){
		override def dbIndexed_? = true
	} 
	object appType extends MappedEnum(this,AppType)
	object description extends MappedText(this)
	object developerEmail extends MappedString(this, 100)
	object insertDate extends MappedDateTime(this){
	  override def defaultValue = new Date(Platform.currentTime)
	}
	object updateDate extends MappedDateTime(this)
}

object Consumer extends Consumer with LongKeyedMetaMapper[Consumer]{} 


class Nonce extends LongKeyedMapper[Nonce] {
  
	def getSingleton = Nonce
	def primaryKeyField = id
	object id extends MappedLongIndex(this) 
	object consumerkey extends MappedString(this, 250) //we store the consumer Key and we don't need to keep a reference to the token consumer as foreign key
	object tokenKey extends MappedString(this, 250){ //we store the token Key and we don't need to keep a reference to the token object as foreign key
		override def defaultValue = ""
	}
	object timestamp extends MappedDateTime(this){
	override  def toString = {
	    //returns as a string the time in milliseconds
	    timestamp.get.getTime().toString()
	  }
	}
	object value extends MappedString(this,250)
	
}
object Nonce extends Nonce with LongKeyedMetaMapper[Nonce]{}


class Token extends LongKeyedMapper[Token]{
	def getSingleton = Token 
	def primaryKeyField = id
	object id extends MappedLongIndex(this) //TODO : auto increment
	object tokenType extends MappedEnum(this, TokenType)
	object consumerId extends MappedLongForeignKey(this, Consumer)
	object userId extends MappedLongForeignKey(this, OBPUser)
	object key extends MappedString(this,250)
	object secret extends MappedString(this,250)
	object callbackURL extends MappedString(this,250)
	object verifier extends MappedString(this,250)
	object duration extends MappedLong(this)//expressed in milliseconds 
	object expirationDate extends MappedDateTime(this)
	object insertDate extends MappedDateTime(this)
}
object Token extends Token with LongKeyedMetaMapper[Token]{}