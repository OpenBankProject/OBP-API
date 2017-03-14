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
package code.model
import code.consumer.{Consumers, ConsumersProvider}
import code.model.dataAccess.ResourceUser
import code.users.Users
import net.liftweb.common._
import net.liftweb.http.S
import net.liftweb.mapper.{LongKeyedMetaMapper, _}
import net.liftweb.util.Helpers.{now, _}
import net.liftweb.util.{FieldError, Helpers, Props}

object AppType extends Enumeration {
  type AppType = Value
  val Web, Mobile = Value
}

object TokenType extends Enumeration {
  type TokenType=Value
  val Request, Access = Value
}


object MappedConsumersProvider extends ConsumersProvider {
  override def getConsumerByConsumerId(consumerId: Long): Box[Consumer] = {
    Consumer.find(By(Consumer.id, consumerId))
  }

  override def getConsumerByConsumerKey(consumerKey: String): Box[Consumer] = {
    Consumer.find(By(Consumer.key, consumerKey))
  }

  override def createConsumer(key: Option[String], secret: Option[String], isActive: Option[Boolean], name: Option[String], appType: Option[AppType.AppType], description: Option[String], developerEmail: Option[String], redirectURL: Option[String], createdByUserId: Option[String]): Box[Consumer] = {
    tryo {
      val c = Consumer.create
      key match {
        case Some(v) => c.key(v)
        case None =>
      }
      secret match {
        case Some(v) => c.secret(v)
        case None =>
      }
      isActive match {
        case Some(v) => c.isActive(v)
        case None =>
      }
      name match {
        case Some(v) => c.name(v)
        case None =>
      }
      appType match {
        case Some(v) => c.appType(v)
        case None =>
      }
      description match {
        case Some(v) => c.description(v)
        case None =>
      }
      developerEmail match {
        case Some(v) => c.developerEmail(v)
        case None =>
      }
      redirectURL match {
        case Some(v) => c.redirectURL(v)
        case None =>
      }
      createdByUserId match {
        case Some(v) => c.createdByUserId(v)
        case None =>
      }
      c.saveMe()
    }
  }

  override def updateConsumer(consumerId: Long, key: Option[String], secret: Option[String], isActive: Option[Boolean], name: Option[String], appType: Option[AppType.AppType], description: Option[String], developerEmail: Option[String], redirectURL: Option[String], createdByUserId: Option[String]): Box[Consumer] = {
    val consumer = Consumer.find(By(Consumer.id, consumerId))
    consumer match {
      case Full(c) => tryo {
        key match {
          case Some(v) => c.key(v)
          case None =>
        }
        secret match {
          case Some(v) => c.secret(v)
          case None =>
        }
        isActive match {
          case Some(v) => c.isActive(v)
          case None =>
        }
        name match {
          case Some(v) => c.name(v)
          case None =>
        }
        appType match {
          case Some(v) => c.appType(v)
          case None =>
        }
        description match {
          case Some(v) => c.description(v)
          case None =>
        }
        developerEmail match {
          case Some(v) => c.developerEmail(v)
          case None =>
        }
        redirectURL match {
          case Some(v) => c.redirectURL(v)
          case None =>
        }
        createdByUserId match {
          case Some(v) => c.createdByUserId(v)
          case None =>
        }
        c.saveMe()
      }
      case _ => consumer
    }
  }

}

class Consumer extends LongKeyedMapper[Consumer] with CreatedUpdated{
  def getSingleton = Consumer
  def primaryKeyField = id
  object id extends MappedLongIndex(this)

  private def minLength3(field: MappedString[Consumer])( s : String) = {
    if(s.length() < 3) List(FieldError(field, {field.displayName + " must be at least 3 characters"}))
    else Nil
  }

  private def validUrl(field: MappedString[Consumer])(s: String) = {
    import java.net.URL

    import Helpers.tryo
    if(s.isEmpty)
      Nil
    else if(tryo{new URL(s)}.isEmpty)
      List(FieldError(field, {field.displayName + " must be a valid URL"}))
    else
      Nil
  }

  object key extends MappedString(this, 250){
    override def dbIndexed_? = true
  }

  object secret extends MappedString(this, 250)
  object isActive extends MappedBoolean(this){
    override def defaultValue = Props.getBool("consumers_enabled_by_default", false)
  }
  object name extends MappedString(this, 100){
    override def validations = minLength3(this) _ :: super.validations
    override def dbIndexed_? = true
    override def displayName = "Application name:"
  }
  object appType extends MappedEnum(this,AppType) {
    override def displayName = "Application type:"
  }
  object description extends MappedText(this) {
    override def displayName = "Description:"
  }
  object developerEmail extends MappedEmail(this, 100) {
    override def displayName = "Email:"
  }
  object redirectURL extends MappedString(this, 250){
    override def displayName = "Redirect URL:"
    override def validations = validUrl(this) _ :: super.validations
  }
  //if the application needs to delegate the user authentication
  //to a third party application (probably it self) rather than using
  //the default authentication page of the API, then this URL will be used.
  object userAuthenticationURL extends MappedString(this, 250){
    override def displayName = "User authentication URL:"
    override def validations = validUrl(this) _ :: super.validations
  }
  object createdByUserId extends MappedString(this, 36)

}

/**
 * Note: CRUDify is using a KeyObfuscator to generate edit/delete/view links, which means that
 * their urls are not persistent. So if you copy paste a url and email it to someone, don't count on it
 * working for long.
 */
object Consumer extends Consumer with Loggable with LongKeyedMetaMapper[Consumer] with CRUDify[Long, Consumer]{
  //list all path : /admin/consumer/list
  override def calcPrefix = List("admin",_dbTableNameLC)

  //obscure primary key to avoid revealing information about, e.g. how many consumers are registered
  // (by incrementing ids until receiving a "log in first" page instead of 404)
  val obfuscator = new KeyObfuscator()
  override def obscurePrimaryKey(in: TheCrudType): String = obfuscator(Consumer, in.id)
  //I've disabled this method as it only looked to be called by the original implementation of obscurePrimaryKey(in: TheCrudType)
  //and I don't want it affecting anything else
  override def obscurePrimaryKey(in: String): String = ""
  //Since we override obscurePrimaryKey, we also need to override findForParam to be able to get a Consumer from its obfuscated id
  override def findForParam(in: String): Box[TheCrudType] = Consumer.find(obfuscator.recover(Consumer, in))

  //override it to list the newest ones first
  override def findForListParams: List[QueryParam[Consumer]] = List(OrderBy(primaryKeyField, Descending))

  //We won't display all the fields when we are listing Consumers (to save screen space)
  override def fieldsForList: List[FieldPointerType] = List(id, name, appType, description, developerEmail, createdAt)

  override def fieldOrder = List(name, appType, description, developerEmail)

  //show more than the default of 20
  override def rowsPerPage = 100
  
  def getRedirectURLByConsumerKey(consumerKey: String): String = {
    logger.debug("hello from getRedirectURLByConsumerKey")
    val consumer: Consumer = Consumers.consumers.vend.getConsumerByConsumerKey(consumerKey).openOrThrowException(s"OBP Consumer not found by consumerKey. You looked for $consumerKey Please check the database")
    logger.debug(s"getRedirectURLByConsumerKey found consumer with id: ${consumer.id}, name is: ${consumer.name}, isActive is ${consumer.isActive}" )
    consumer.redirectURL.toString()
  }

  //counts the number of different unique email addresses
  val numUniqueEmailsQuery = s"SELECT COUNT(DISTINCT ${Consumer.developerEmail.dbColumnName}) FROM ${Consumer.dbName};"

  val numUniqueAppNames = s"SELECT COUNT(DISTINCT ${Consumer.name.dbColumnName}) FROM ${Consumer.dbName};"

  private val recordsWithUniqueEmails = tryo {Consumer.countByInsecureSql(numUniqueEmailsQuery, IHaveValidatedThisSQL("everett", "2014-04-29")) }
  private val recordsWithUniqueAppNames = tryo {Consumer.countByInsecureSql(numUniqueAppNames, IHaveValidatedThisSQL("everett", "2014-04-29"))}

  //overridden to display extra stats above the table
  override def _showAllTemplate =
  <lift:crud.all>
    <p id="admin-consumer-summary">
      Total of {Consumer.count} applications from {recordsWithUniqueEmails.getOrElse("ERROR")} unique email addresses. <br />
      {recordsWithUniqueAppNames.getOrElse("ERROR")} unique app names.
    </p>
    <table id={showAllId} class={showAllClass}>
      <thead>
        <tr>
          <crud:header_item><th><crud:name/></th></crud:header_item>
          <th>&nbsp;</th>
          <th>&nbsp;</th>
          <th>&nbsp;</th>
        </tr>
      </thead>
      <tbody>
        <crud:row>
          <tr>
            <crud:row_item><td><crud:value/></td></crud:row_item>
            <td><a crud:view_href="">{S.?("View")}</a></td>
            <td><a crud:edit_href="">{S.?("Edit")}</a></td>
            <td><a crud:delete_href="">{S.?("Delete")}</a></td>
          </tr>
        </crud:row>
      </tbody>
      <tfoot>
        <tr>
          <td colspan="3"><crud:prev>{previousWord}</crud:prev></td>
          <td colspan="3"><crud:next>{nextWord}</crud:next></td>
        </tr>
      </tfoot>
    </table>
  </lift:crud.all>
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
  object userForeignKey extends MappedLongForeignKey(this, ResourceUser)
  object key extends MappedString(this,250)
  object secret extends MappedString(this,250)
  object callbackURL extends MappedString(this,250)
  object verifier extends MappedString(this,250)
  object duration extends MappedLong(this)//expressed in milliseconds
  object expirationDate extends MappedDateTime(this)
  object insertDate extends MappedDateTime(this)
  def user = Users.users.vend.getResourceUserByResourceUserId(userForeignKey.get)
  //The the consumer from Token by consumerId
  def consumer = consumerId.obj
  def isValid : Boolean = expirationDate.is after now
  def gernerateVerifier : String =
    if (verifier.isEmpty){
        def fiveRandomNumbers() : String = {
          def r() = randomInt(9).toString //from zero to 9
          (1 to 5).map(x => r()).foldLeft("")(_ + _)
        }
      val generatedVerifier = fiveRandomNumbers()
      verifier(generatedVerifier).save
      generatedVerifier
    }
    else
      verifier.is

  // in the case of user authentication in a third party application
  // (see authenticationURL in class Consumer).
  // This secret will be used between the API server and the third party application
  // It will be used during the callback (the user coming back to the login page)
  // for entering the banking details.
  object thirdPartyApplicationSecret extends MappedString(this,10){

  }

  def generateThirdPartyApplicationSecret: String = {
    if(thirdPartyApplicationSecret isEmpty){
      def r() = randomInt(9).toString //from zero to 9
      val generatedSecret = (1 to 10).map(x => r()).foldLeft("")(_ + _)
      thirdPartyApplicationSecret(generatedSecret).save
      generatedSecret
    }
    else
      thirdPartyApplicationSecret
  }
}
object Token extends Token with LongKeyedMetaMapper[Token]{
  def gernerateVerifier(key : String) : Box[String] = {
    Token.find(key) match {
      case Full(tkn) => Full(tkn.gernerateVerifier)
      case _ => Failure("Token not found",Empty, Empty)
    }
  }

  def getRequestToken(token: String): Box[Token] =
    Token.find(By(Token.key, token), By(Token.tokenType, TokenType.Request))
}
