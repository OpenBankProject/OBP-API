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
import net.liftweb.http.SHtml
import net.liftweb.util.FieldError
import net.liftweb.util.FieldIdentifier
import net.liftweb.common.{Full,Failure,Box,Empty}
import code.model.dataAccess.Admin
import net.liftweb.util.Helpers
import Helpers.now

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

class Consumer extends LongKeyedMapper[Consumer] with CreatedUpdated{
	def getSingleton = Consumer
	def primaryKeyField = id
	object id extends MappedLongIndex(this)

	def minLength3(field: MappedString[Consumer])( s : String) = {
	  if(s.length() < 3) List(FieldError(field, {field.displayName + " must be at least 3 characters"}))
	  else Nil
	}

	object key extends MappedString(this, 250){
      override def dbIndexed_? = true
	}
	object secret extends MappedString(this, 250)
	object isActive extends MappedBoolean(this)
	object name extends MappedString(this, 100){
		override def validations = minLength3(this) _ :: super.validations
		override def dbIndexed_? = true
		override def displayName = "App name:"
	}
	object appType extends MappedEnum(this,AppType) {
	  override def displayName = "App type:"
	}
	object description extends MappedText(this) {
	  override def displayName = "Description:"
	}
	object developerEmail extends MappedEmail(this, 100) {
	  override def displayName = "Email:"
	}

}

object Consumer extends Consumer with LongKeyedMetaMapper[Consumer] with CRUDify[Long, Consumer]{
	//list all path : /admin/consumer/list
	override def calcPrefix = List("admin",_dbTableNameLC)

	override def editMenuLocParams = List(Admin.testLogginIn)
	override def showAllMenuLocParams = List(Admin.testLogginIn)
	override def deleteMenuLocParams = List(Admin.testLogginIn)
	override def createMenuLocParams = List(Admin.testLogginIn)
	override def viewMenuLocParams = List(Admin.testLogginIn)

	override def fieldOrder = List(name, appType, description, developerEmail)
}


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
	object id extends MappedLongIndex(this)
	object tokenType extends MappedEnum(this, TokenType)
	object consumerId extends MappedLongForeignKey(this, Consumer)
	object userId extends MappedString(this,255)
	object key extends MappedString(this,250)
	object secret extends MappedString(this,250)
	object callbackURL extends MappedString(this,250)
	object verifier extends MappedString(this,250)
	object duration extends MappedLong(this)//expressed in milliseconds
	object expirationDate extends MappedDateTime(this)
	object insertDate extends MappedDateTime(this)
	def user = User.findById(userId.get)
	def isValid : Boolean = expirationDate.is after now
	private def fiveRandomNumbers() : String = {
	  def r() = Helpers.randomInt(9).toString //from zero to 9
	  (1 to 5).map(x => r()).foldLeft("")(_ + _)
	}
	def gernerateVerifier : String =
		if (verifier.isEmpty)
		{
			val generatedVerifier = fiveRandomNumbers()
			verifier(generatedVerifier).save
			generatedVerifier
		}
		else
			verifier.is
}
object Token extends Token with LongKeyedMetaMapper[Token]{
	def gernerateVerifier(key : String) : Box[String] = {
		Token.find(key) match {
			case Full(tkn) => Full(tkn.gernerateVerifier)
			case _ => Failure("Token not found",Empty, Empty)
		}
	}
}
