/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

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
package code.model
import java.util.{Collections, Date}

import code.api.util.APIUtil
import code.api.util.migration.Migration.DbFunction
import code.consumer.{Consumers, ConsumersProvider}
import code.model.AppType.{Mobile, Web}
import code.model.dataAccess.ResourceUser
import code.nonce.NoncesProvider
import code.token.TokensProvider
import code.users.Users
import code.util.Helper.MdcLoggable
import code.util.HydraUtil
import code.util.HydraUtil._
import com.github.dwickern.macros.NameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import net.liftweb.common._
import net.liftweb.http.S
import net.liftweb.mapper.{LongKeyedMetaMapper, _}
import net.liftweb.util.Helpers.{now, _}
import net.liftweb.util.{FieldError, Helpers}
import org.apache.commons.lang3.StringUtils

import scala.collection.immutable.List
import scala.concurrent.Future


sealed trait AppType
object AppType {
  case object Web extends AppType
  case object Mobile extends AppType
  def valueOf(value: String): AppType = value match {
    case "Web" => Web
    case "Mobile" => Mobile
  }
}

sealed trait TokenType
object TokenType {
  case object Request extends TokenType
  case object Access extends TokenType
  case object IDToken extends TokenType
  def valueOf(value: String): TokenType = value match {
    case "Request" => Request
    case "Access" => Access
    case "IDToken" => IDToken
  }
}


object MappedConsumersProvider extends ConsumersProvider with MdcLoggable {
  
  override def getConsumerByPrimaryIdFuture(id: Long): Future[Box[Consumer]] = {
    Future(
      Consumer.find(By(Consumer.id, id))
    )
  }

  override def getConsumerByPrimaryId(id: Long): Box[Consumer] = {
    Consumer.find(By(Consumer.id, id))
  }

  override def getConsumerByConsumerKey(consumerKey: String): Box[Consumer] = {
    Consumer.find(By(Consumer.key, consumerKey))
  }

  override def getConsumerByConsumerKeyFuture(consumerKey: String): Future[Box[Consumer]] = {
    Future{
      getConsumerByConsumerKey(consumerKey)
    }
  }

  def getConsumerByConsumerId(consumerId: String): Box[Consumer] = {
    Consumer.find(By(Consumer.consumerId, consumerId))
  }
  override def getConsumerByConsumerIdFuture(consumerId: String): Future[Box[Consumer]] = {
    Future{
      getConsumerByConsumerId(consumerId)
    }
  }

  def getConsumersByUserId(userId: String): List[Consumer] = {
    Consumer.findAll(By(Consumer.createdByUserId, userId))
  }
  override def getConsumersByUserIdFuture(userId: String): Future[List[Consumer]] = {
    Future(getConsumersByUserId(userId))
  }

  def getConsumers(): List[Consumer] = {
    Consumer.findAll()
  }
  override def getConsumersFuture(): Future[List[Consumer]] = {
    Future(getConsumers())
  }

  override def createConsumer(key: Option[String],
                              secret: Option[String],
                              isActive: Option[Boolean],
                              name: Option[String],
                              appType: Option[AppType],
                              description: Option[String],
                              developerEmail: Option[String],
                              redirectURL: Option[String],
                              createdByUserId: Option[String],
                              clientCertificate: Option[String] = None): Box[Consumer] = {
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
        case Some(v) =>
          val count = Consumer.findAll(By(Consumer.name, v)).size
          if(count == 0) 
            c.name(v) 
          else 
            c.name(v + "_"  + Helpers.randomString(10).toLowerCase)
        case None =>
      }
      appType match {
        case Some(v) => v match {
          case Web => c.appType(Web.toString)
          case Mobile => c.appType(Mobile.toString)
        }
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

      clientCertificate.filter(StringUtils.isNotBlank).foreach(c.clientCertificate(_))

      if(c.validate.isEmpty) {
        c.saveMe()
      }
      else
        throw new Error(c.validate.map(_.msg.toString()).mkString(";"))
    }
  }

  def deleteConsumer(consumer: Consumer): Boolean = {
    if(integrateWithHydra) hydraAdmin.deleteOAuth2Client(consumer.key.get)
    Consumer.delete_!(consumer)
  }

  override def updateConsumer(id: Long,
                              key: Option[String],
                              secret: Option[String],
                              isActive: Option[Boolean],
                              name: Option[String],
                              appType: Option[AppType],
                              description: Option[String],
                              developerEmail: Option[String],
                              redirectURL: Option[String],
                              createdByUserId: Option[String]): Box[Consumer] = {
    val consumer = Consumer.find(By(Consumer.id, id))
    consumer match {
      case Full(c) => tryo {
        val originIsActive = c.isActive.get
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
          case Some(v) => v match {
            case Web => c.appType(Web.toString)
            case Mobile => c.appType(Mobile.toString)
          }
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
        val updatedConsumer = c.saveMe()

        if(integrateWithHydra && Option(originIsActive) != isActive && isActive.isDefined) {
          val clientId = c.key.get
          val existsOAuth2Client = Box.tryo(hydraAdmin.getOAuth2Client(clientId))
            .filter(null !=)
          // if disable consumer, delete hydra client, else if enable consumer, create hydra client
          // note: hydra update client endpoint have bug, can't update any client, So here delete and create new one
          if (isActive == Some(false)) {
              existsOAuth2Client
              .map { oAuth2Client =>
                  hydraAdmin.deleteOAuth2Client(clientId)
                  // set grantTypes to empty to disable the client
                  oAuth2Client.setGrantTypes(Collections.emptyList())
                  hydraAdmin.createOAuth2Client(oAuth2Client)
              }
          } else if(isActive == Some(true) && existsOAuth2Client.isDefined) {
            existsOAuth2Client
              .map { oAuth2Client =>
                hydraAdmin.deleteOAuth2Client(clientId)
                // set grantTypes to correct value to enable the client
                oAuth2Client.setGrantTypes(HydraUtil.grantTypes)
                hydraAdmin.createOAuth2Client(oAuth2Client)
              }
          } else if(isActive == Some(true)) {
            createHydraClient(updatedConsumer)
          }
        }

        updatedConsumer
      }
      case _ => consumer
    }
  }

  override def updateConsumerCallLimits(id: Long,
                                     perSecond: Option[String],
                                     perMinute: Option[String],
                                     perHour: Option[String],
                                     perDay: Option[String],
                                     perWeek: Option[String],
                                     perMonth: Option[String]): Future[Box[Consumer]] = {
    Future{
      updateConsumerCallLimitsRemote(id, perSecond, perMinute, perHour, perDay, perWeek, perMonth)
    }
  }

  def updateConsumerCallLimitsRemote(id: Long,
                                        perSecond: Option[String],
                                        perMinute: Option[String],
                                        perHour: Option[String],
                                        perDay: Option[String],
                                        perWeek: Option[String],
                                        perMonth: Option[String]): Box[Consumer] = {
    val consumer = Consumer.find(By(Consumer.id, id))
    consumer match {
      case Full(c) => tryo {
        perSecond match {
          case Some(v) => c.perSecondCallLimit(v.toLong)
          case None =>
        }
        perMinute match {
          case Some(v) => c.perMinuteCallLimit(v.toLong)
          case None =>
        }
        perHour match {
          case Some(v) => c.perHourCallLimit(v.toLong)
          case None =>
        }
        perDay match {
          case Some(v) => c.perDayCallLimit(v.toLong)
          case None =>
        }
        perWeek match {
          case Some(v) => c.perWeekCallLimit(v.toLong)
          case None =>
        }
        perMonth match {
          case Some(v) => c.perMonthCallLimit(v.toLong)
          case None =>
        }
        c.saveMe()
      }
      case _ => consumer
    }
  }

  override def getOrCreateConsumer(consumerId: Option[String],
                                   key: Option[String],
                                   secret: Option[String],
                                   aud: Option[String],
                                   azp: Option[String],
                                   iss: Option[String],
                                   sub: Option[String],
                                   isActive: Option[Boolean],
                                   name: Option[String],
                                   appType: Option[AppType],
                                   description: Option[String],
                                   developerEmail: Option[String],
                                   redirectURL: Option[String],
                                   createdByUserId: Option[String]): Box[Consumer] = {

    val consumer: Box[Consumer] =
      // 1st try represent GatewayLogin usage of this function
      Consumer.find(By(Consumer.consumerId, consumerId.getOrElse("None"))) or {
        // 2nd try represent OAuth2 usage of this function
        Consumer.find(By(Consumer.azp, azp.getOrElse("None")), By(Consumer.sub, sub.getOrElse("None")))
      } or {
        aud.flatMap(consumerKey => Consumer.find(By(Consumer.key, consumerKey)))
      }
    consumer match {
      case Full(c) => Full(c)
      case Failure(msg, t, c) => Failure(msg, t, c)
      case ParamFailure(x,y,z,q) => ParamFailure(x,y,z,q)
      case Empty =>
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
          aud match {
            case Some(v) => c.aud(v)
            case None =>
          }
          azp match {
            case Some(v) => c.azp(v)
            case None =>
          }
          iss match {
            case Some(v) => c.iss(v)
            case None =>
          }
          sub match {
            case Some(v) => c.sub(v)
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
            case Some(v) => v match {
              case Web => c.appType(Web.toString)
              case Mobile => c.appType(Mobile.toString)
            }
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
          consumerId match {
            case Some(v) => c.consumerId(v)
            case None =>
          }
          val createdConsumer = c.saveMe()
          if(integrateWithHydra) createHydraClient(createdConsumer)
          createdConsumer
        }
    }
  }
  
  override def populateMissingUUIDs(): Boolean = {
    logger.warn("Executed script: MappedConsumersProvider." + NameOf.nameOf(populateMissingUUIDs))
    //back up consumer table
    DbFunction.makeBackUpOfTable(Consumer)

    for {
      consumer <- Consumer.findAll(NullRef(Consumer.consumerId))++ Consumer.findAll(By(Consumer.consumerId,""))
    } yield {
      consumer.consumerId(APIUtil.generateUUID()).save()
    }
  }.forall(_ == true)

}

class Consumer extends LongKeyedMapper[Consumer] with CreatedUpdated{
  def getSingleton = Consumer
  def primaryKeyField = id
  
  //Note: we have two id here for Consumer. id is the primaryKeyField, we used it as the CONSUMER_ID in api level for a long time. 
  //But from `a4222f9824fcac039e7968f4abcd009fa3918d4a` 2017-07-07 we introduced the consumerId here. It is confused now
  //For now consumerId is only used in Gateway Login, all other cases, we should use the id instead `consumerId`.
  object id extends MappedLongIndex(this)
  object consumerId extends MappedString(this, 250) { // Introduced to cover gateway login functionality
    override def defaultValue = APIUtil.generateUUID()
  }

  private def minLength3(field: MappedString[Consumer])( s : String) = {
    if(s.length() < 3) List(FieldError(field, {field.displayName + " must be at least 3 characters"}))
    else Nil
  }

  private def EmptyError(field: MappedText[Consumer])( s : String) = {
    if(s.isEmpty) List(FieldError(field, {field.displayName + "can not be empty"}))
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

  private def uniqueName(field: MappedString[Consumer])(s: String): List[FieldError] = {
    val consumer = Consumer.find(By(Consumer.name, s))
    if(consumer.isDefined)
      List(FieldError(field, {field.displayName + " must be unique"}))
    else 
      Nil
  }

  /**
    * This function is added in order to support iOS/macOS requirements for callbacks.
    * For instance next callback has to be valid: x-com.tesobe.helloobp.ios://callback
    * @param field object which has to be validated
    * @param s is a URI string
    * @return Empty list if URI is valid or FieldError otherwise
    */
  private def validUri(field: MappedString[Consumer])(s: String) = {
    import java.net.URI

    import Helpers.tryo
    if(s.isEmpty)
      Nil
    else if(tryo{new URI(s)}.isEmpty)
      List(FieldError(field, {field.displayName + " must be a valid URI"}))
    else
      Nil
  }

  object key extends MappedString(this, 250)
  object secret extends MappedString(this, 250)
  object azp extends MappedString(this, 250) {
    // because different databases treat unique indexes on NULL values differently.
    override def defaultValue = APIUtil.generateUUID() 
  }
  object aud extends MappedString(this, 250) {
    override def defaultValue = null
  }  
  object iss extends MappedString(this, 250) {
    override def defaultValue = null
  }  
  object sub extends MappedString(this, 250) {
    // because different databases treat unique indexes on NULL values differently.
    override def defaultValue = APIUtil.generateUUID()
  }
  object isActive extends MappedBoolean(this){
    override def defaultValue = APIUtil.getPropsAsBoolValue("consumers_enabled_by_default", false)
  }
  object name extends MappedString(this, 100){
    override def validations = minLength3(this) _ :: uniqueName(this) _ :: super.validations
    override def dbIndexed_? = true
    override def displayName = "Application name:"
  }
  object appType extends MappedString(this, 20) {
    override def displayName = "Application type:"
  }
  object description extends MappedText(this) {
    override def validations = EmptyError(this) _ :: super.validations
    override def displayName = "Description:"
  }
  object developerEmail extends MappedEmail(this, 100) {
    override def displayName = "Email:"
  }
  object redirectURL extends MappedString(this, 250){
    override def displayName = "Redirect URL:"
    override def validations = validUri(this) _ :: super.validations
  }
  //if the application needs to delegate the user authentication
  //to a third party application (probably it self) rather than using
  //the default authentication page of the API, then this URL will be used.
  object userAuthenticationURL extends MappedString(this, 250){
    override def displayName = "User authentication URL:"
    override def validations = validUri(this) _ :: super.validations
  }
  object createdByUserId extends MappedString(this, 36)

  object perSecondCallLimit extends MappedLong(this) {
    override def defaultValue = -1
  }
  object perMinuteCallLimit extends MappedLong(this) {
    override def defaultValue = -1
  }
  object perHourCallLimit extends MappedLong(this) {
    override def defaultValue = -1
  }
  object perDayCallLimit extends MappedLong(this) {
    override def defaultValue = -1
  }
  object perWeekCallLimit extends MappedLong(this) {
    override def defaultValue = -1
  }
  object perMonthCallLimit extends MappedLong(this) {
    override def defaultValue = -1
  }
  object clientCertificate extends MappedString(this, 4000)
}

/**
 * Note: CRUDify is using a KeyObfuscator to generate edit/delete/view links, which means that
 * their urls are not persistent. So if you copy paste a url and email it to someone, don't count on it
 * working for long.
 */
object Consumer extends Consumer with MdcLoggable with LongKeyedMetaMapper[Consumer] with CRUDify[Long, Consumer] {

  override def dbIndexes = UniqueIndex(key) :: UniqueIndex(azp, sub) :: super.dbIndexes

  //list all path : /admin/consumer/list
  override def calcPrefix = List("admin", _dbTableNameLC)

  //obscure primary key to avoid revealing information about, e.g. how many consumers are registered
  // (by incrementing ids until receiving a "log in first" page instead of 404)
  val obfuscator = new KeyObfuscator()

  override def obscurePrimaryKey(in: TheCrudType): String = obfuscator(Consumer, in.id.get)

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
    logger.debug(s"getRedirectURLByConsumerKey found consumer with id: ${consumer.id}, name is: ${consumer.name}, isActive is ${consumer.isActive}")
    consumer.redirectURL.toString()
  }

  //counts the number of different unique email addresses
  val numUniqueEmailsQuery = s"SELECT COUNT(DISTINCT ${Consumer.developerEmail.dbColumnName}) FROM ${Consumer.dbName};"

  val numUniqueAppNames = s"SELECT COUNT(DISTINCT ${Consumer.name.dbColumnName}) FROM ${Consumer.dbName};"

  private val recordsWithUniqueEmails = tryo {
    Consumer.countByInsecureSql(numUniqueEmailsQuery, IHaveValidatedThisSQL("everett", "2014-04-29"))
  }
  private val recordsWithUniqueAppNames = tryo {
    Consumer.countByInsecureSql(numUniqueAppNames, IHaveValidatedThisSQL("everett", "2014-04-29"))
  }

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
            <crud:header_item>
              <th>
                <crud:name/>
              </th>
            </crud:header_item>
            <th>
              &nbsp;
            </th>
            <th>
              &nbsp;
            </th>
            <th>
              &nbsp;
            </th>
          </tr>
        </thead>
        <tbody>
          <crud:row>
            <tr>
              <crud:row_item>
                <td>
                  <crud:value/>
                </td>
              </crud:row_item>
              <td>
                <a crud:view_href="">
                  {S.?("View")}
                </a>
              </td>
              <td>
                <a crud:edit_href="">
                  {S.?("Edit")}
                </a>
              </td>
              <td>
                <a crud:delete_href="">
                  {S.?("Delete")}
                </a>
              </td>
            </tr>
          </crud:row>
        </tbody>
        <tfoot>
          <tr>
            <td colspan="3">
              <crud:prev>
                {previousWord}
              </crud:prev>
            </td>
            <td colspan="3">
              <crud:next>
                {nextWord}
              </crud:next>
            </td>
          </tr>
        </tfoot>
      </table>
    </lift:crud.all>

  /**
   * match the flow style, it can be http, https, or Private-Use URI Scheme Redirection for app:
   * http://some.domain.com/path
   * https://some.domain.com/path
   * com.example.app:/oauth2redirect/example-provider
   */
  val redirectURLRegex = """^([.\w]+:|(http|https):/)/(www.)?\S+?(:\d{2,6})?\S*$""".r
}

object MappedNonceProvider extends NoncesProvider {
  override def createNonce(id: Option[Long],
                           consumerKey: Option[String],
                           tokenKey: Option[String],
                           timestamp: Option[Date],
                           value: Option[String]): Box[Nonce] = {
    tryo {
      val n = Nonce.create
      id match {
        case Some(v) => n.id(v)
        case None =>
      }
      consumerKey match {
        case Some(v) => n.consumerkey(v)
        case None =>
      }
      tokenKey match {
        case Some(v) => n.tokenKey(v)
        case None =>
      }
      timestamp match {
        case Some(v) => n.timestamp(v)
        case None =>
      }
      value match {
        case Some(v) => n.value(v)
        case None =>
      }
      val nonce = n.saveMe()
      nonce
    }
  }

  override def deleteExpiredNonces(currentDate: Date): Boolean = {
    Nonce.findAll(By_<(Nonce.timestamp, currentDate)).forall(_.delete_!)
  }

  override def countNonces(consumerKey: String,
                           tokenKey: String,
                           timestamp: Date,
                           value: String): Long = {
    Nonce.count(
      By(Nonce.value, value),
      By(Nonce.tokenKey, tokenKey),
      By(Nonce.consumerkey, consumerKey),
      By(Nonce.timestamp, timestamp)
    )
  }

  override def countNoncesFuture(consumerKey: String,
                                 tokenKey: String,
                                 timestamp: Date,
                                 value: String): Future[Long] = {
    Future{countNonces(consumerKey, tokenKey, timestamp, value)}
  }

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

object MappedTokenProvider extends TokensProvider {
  override def getTokenByKey(key: String): Box[Token] = {
    Token.find(By(Token.key, key))
  }
  override def getTokenByKeyFuture(key: String): Future[Box[Token]] = {
    Future{
      getTokenByKey(key)
    }
  }
  override def getTokenByKeyAndType(key: String, tokenType: TokenType): Box[Token] = {
    val token = Token.find(By(Token.key, key),By(Token.tokenType,tokenType.toString))
    token
  }

  override def getTokenByKeyAndTypeFuture(key: String, tokenType: TokenType): Future[Box[Token]] = {
    Future{
      getTokenByKeyAndType(key, tokenType)
    }
  }

  override def createToken(tokenType: TokenType,
                           consumerId: Option[Long],
                           userId: Option[Long],
                           key: Option[String],
                           secret: Option[String],
                           duration: Option[Long],
                           expirationDate: Option[Date],
                           insertDate: Option[Date],
                           callbackURL: Option[String]): Box[Token] = {
    tryo {
      val t = Token.create
      t.tokenType(tokenType.toString)
      consumerId match {
        case Some(v) => t.consumerId(v)
        case None =>
      }
      userId match {
        case Some(v) => t.userForeignKey(v)
        case None =>
      }
      key match {
        case Some(v) => t.key(v)
        case None =>
      }
      secret match {
        case Some(v) => t.secret(v)
        case None =>
      }
      duration match {
        case Some(v) => t.duration(v)
        case None =>
      }
      expirationDate match {
        case Some(v) => t.expirationDate(v)
        case None =>
      }
      insertDate match {
        case Some(v) => t.insertDate(v)
        case None =>
      }
      callbackURL match {
        case Some(v) => t.callbackURL(v)
        case None =>
      }
      val token = t.saveMe()
      token
    }
  }

  override def updateToken(id: Long, userId: Long): Boolean = {
    Token.find(By(Token.id, id)) match {
      case Full(t) => t.userForeignKey(userId).save()
      case _       => false
    }
  }

  override def gernerateVerifier(id: Long): String = {
    Token.find(By(Token.id, id)).map(_.gernerateVerifier).getOrElse("")
  }

  override def deleteToken(id: Long): Boolean = {
    Token.find(By(Token.id, id)) match {
      case Full(t) => t.delete_!
      case _       => false
    }
  }

  override def deleteExpiredTokens(currentDate: Date): Boolean = {
    Token.findAll(By_<(Token.expirationDate, currentDate)).forall(_.delete_!)
  }
}


class Token extends LongKeyedMapper[Token]{
  def getSingleton = Token
  def primaryKeyField = id
  object id extends MappedLongIndex(this)
  object tokenType extends MappedString(this,10)
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
  def consumer = Consumers.consumers.vend.getConsumerByPrimaryId(consumerId.get)
  def isValid : Boolean = expirationDate.get after now
  def gernerateVerifier : String =
    if (verifier.get.isEmpty){
        def fiveRandomNumbers() : String = {
          def r() = randomInt(9).toString //from zero to 9
          (1 to 5).map(x => r()).foldLeft("")(_ + _)
        }
      val generatedVerifier = fiveRandomNumbers()
      verifier(generatedVerifier).save
      generatedVerifier
    }
    else
      verifier.get

  // in the case of user authentication in a third party application
  // (see authenticationURL in class Consumer).
  // This secret will be used between the API server and the third party application
  // It will be used during the callback (the user coming back to the login page)
  // for entering the banking details.
  object thirdPartyApplicationSecret extends MappedString(this,10){

  }

  def generateThirdPartyApplicationSecret: String = {
    if(thirdPartyApplicationSecret.get isEmpty){
      def r() = randomInt(9).toString //from zero to 9
      val generatedSecret = (1 to 10).map(x => r()).foldLeft("")(_ + _)
      thirdPartyApplicationSecret(generatedSecret).save
      generatedSecret
    }
    else
      thirdPartyApplicationSecret.get
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
    Token.find(By(Token.key, token), By(Token.tokenType, TokenType.Request.toString))
}
