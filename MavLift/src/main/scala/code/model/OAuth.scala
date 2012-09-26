/** 
Open Bank Project

Copyright 2011,2012 TESOBE / Music Pictures Ltd.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and 
limitations under the License.  

 Open Bank Project (http://www.openbankproject.com)
      Copyright 2011,2012 TESOBE / Music Pictures Ltd

      This product includes software developed at
      TESOBE (http://www.tesobe.com/)
		by 
		Simon Redfern : simon AT tesobe DOT com
		Everett Sochowski: everett AT tesobe DOT com
		Ayoub Benali : ayoub AT tesobe Dot com    
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