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
import code.api.util.CommonFunctions.validUri
import code.api.util.migration.Migration.DbFunction
import code.api.util._
import code.consumer.{Consumers, ConsumersProvider}
import code.model.AppType.{Confidential, Public, Unknown}
import code.model.dataAccess.ResourceUser
import code.nonce.NoncesProvider
import code.token.TokensProvider
import code.users.Users
import code.util.Helper.MdcLoggable
import code.util.HydraUtil._
import com.github.dwickern.macros.NameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import net.liftweb.common._
import net.liftweb.mapper._
import net.liftweb.util.Helpers._
import net.liftweb.util.{FieldError, Helpers}
import org.apache.commons.lang3.StringUtils

import java.util.Date
import scala.concurrent.Future


sealed trait AppType
object AppType {
  case object Confidential extends AppType
  case object Public extends AppType
  case object Unknown extends AppType
  def valueOf(value: String): AppType = value match {
    case "Web" => Confidential
    case "Confidential" => Confidential
    case "Mobile" => Public
    case "Public" => Public
    case "Unknown" => Unknown
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

  def getConsumerByPemCertificate(pem: String): Box[Consumer] = {
    Consumer.find(By(Consumer.clientCertificate, pem))
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

  def getConsumers(queryParams: List[OBPQueryParam], callContext: Option[CallContext]): List[Consumer] = {
    val limit = queryParams.collect { case OBPLimit(value) => MaxRows[Consumer](value) }.headOption
    val offset = queryParams.collect { case OBPOffset(value) => StartAt[Consumer](value) }.headOption
    val fromDate = queryParams.collect { case OBPFromDate(date) => By_>=(Consumer.createdAt, date) }.headOption
    val toDate = queryParams.collect { case OBPToDate(date) => By_<=(Consumer.createdAt, date) }.headOption
    val azp = queryParams.collect { case OBPAzp(value) => By(Consumer.azp, value) }.headOption
    val iss = queryParams.collect { case OBPIss(value) => By(Consumer.iss, value) }.headOption
    val consumerId = queryParams.collect { case OBPConsumerId(value) => By(Consumer.consumerId, value) }.headOption
    val ordering = queryParams.collect {
      case OBPOrdering(_, direction) =>
        direction match {
          case OBPAscending => OrderBy(Consumer.createdAt, Ascending)
          case OBPDescending => OrderBy(Consumer.createdAt, Descending)
        }
    }

    val mapperParams: Seq[QueryParam[Consumer]] =
      Seq(limit.toSeq, offset.toSeq, fromDate.toSeq, toDate.toSeq, ordering, azp.toSeq, iss.toSeq, consumerId.toSeq).flatten
    
    Consumer.findAll(mapperParams: _*)
  }
  
  override def getConsumersFuture(httpParams: List[OBPQueryParam], callContext: Option[CallContext]): Future[List[Consumer]] = {
    Future(getConsumers(httpParams: List[OBPQueryParam], callContext: Option[CallContext]))
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
                              clientCertificate: Option[String] = None,
                              company: Option[String] = None,
                              logoURL: Option[String]
                             ): Box[Consumer] = {
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
          case Confidential => c.appType(Confidential.toString)
          case Public => c.appType(Public.toString)
          case Unknown => c.appType(Unknown.toString)
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
      logoURL match {
        case Some(v) => c.logoUrl(v)
        case None =>
      }
      createdByUserId match {
        case Some(v) => c.createdByUserId(v)
        case None =>
      }
      company match {
        case Some(v) => c.company(v)
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
                              createdByUserId: Option[String],
                              logoURL: Option[String],
                              certificate: Option[String],
  ): Box[Consumer] = {
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
        certificate match {
          case Some(v) => c.clientCertificate(v)
          case None =>
        }
        appType match {
          case Some(v) => v match {
            case Confidential => c.appType(Confidential.toString)
            case Public => c.appType(Public.toString)
            case Unknown => c.appType(Unknown.toString)
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
        logoURL match {
          case Some(v) => c.logoUrl(v)
          case None =>
        }
        createdByUserId match {
          case Some(v) => c.createdByUserId(v)
          case None =>
        }
        val updatedConsumer = c.saveMe()

        // In case we use Hydra ORY as Identity Provider we update corresponding client at Hydra side a well
        if(integrateWithHydra && isActive.isDefined) {
          val clientId = c.key.get
          val existsOAuth2Client = Box.tryo(hydraAdmin.getOAuth2Client(clientId))
            .filter(null.!=)
          // TODO Involve Hydra ORY version with working update mechanism
          if (isActive == Some(false) && existsOAuth2Client.isDefined) {
              existsOAuth2Client
              .map { oAuth2Client =>
                oAuth2Client.setClientSecretExpiresAt(System.currentTimeMillis())
                hydraAdmin.updateOAuth2Client(clientId, oAuth2Client)
              }
          }
          if(isActive == Some(true) && existsOAuth2Client.isDefined) {
            existsOAuth2Client
              .map { oAuth2Client =>
                oAuth2Client.setClientSecretExpiresAt(0L)
                hydraAdmin.updateOAuth2Client(clientId, oAuth2Client)
              }
          }
        }

        updatedConsumer
      }
      case _ => consumer
    }
  }

  @deprecated("Use RateLimitingDI.rateLimiting.vend methods instead", "v5.0.0")
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
                                   createdByUserId: Option[String],
                                   certificate: Option[String],
                                   logoUrl: Option[String],
                                  ): Box[Consumer] = {

    logger.info(s"getOrCreateConsumer says: BEGIN lookup. Input: consumerId=${consumerId.getOrElse("None")}, azp=${azp.getOrElse("None")}, iss=${iss.getOrElse("None")}, sub=${sub.getOrElse("None")}")

    // 1st try: find by consumerId (UUID issued by OBP-API back end)
    val byConsumerId = Consumer.find(By(Consumer.consumerId, consumerId.getOrElse("None")))
    val consumer: Box[Consumer] = if (byConsumerId.isDefined) {
      val c = byConsumerId.openOrThrowException("checked isDefined")
      logger.info(s"getOrCreateConsumer says: MATCH on lookup 1 (by consumerId). Found consumer: consumerId=${c.consumerId.get}, key=${c.key.get}, azp=${c.azp.get}, iss=${c.iss.get}")
      byConsumerId
    } else {
      logger.info(s"getOrCreateConsumer says: MISS on lookup 1 (by consumerId=${consumerId.getOrElse("None")}). Trying lookup 2 (by Consumer.key matching azp)...")

      // 2nd try: find by consumer key matching azp (pre-registered consumer whose key is the OAuth2 client_id)
      // This is checked before (azp, iss) so that a pre-registered consumer takes priority over an auto-created one
      val byKeyMatchingAzp = Consumer.find(By(Consumer.key, azp.getOrElse("None")))
      if (byKeyMatchingAzp.isDefined) {
        val c = byKeyMatchingAzp.openOrThrowException("checked isDefined")
        logger.info(s"getOrCreateConsumer says: MATCH on lookup 2 (by Consumer.key matching azp). Found pre-registered consumer: consumerId=${c.consumerId.get}, key=${c.key.get}, azp=${c.azp.get}, iss=${c.iss.get}")
        // Transitional cleanup: before the duplicate-consumer fix, OAuth2/OIDC flows could auto-create
        // consumers that now conflict with the pre-registered one we just found. Clear the stale consumer's
        // azp/iss/sub so we can populate those fields on the pre-registered consumer without a unique
        // constraint violation. This block can be removed once all environments have been cleaned up.
        val conflicting = Consumer.find(By(Consumer.azp, azp.getOrElse("None")), By(Consumer.iss, iss.getOrElse("None")))
        for (stale <- conflicting) {
          if (stale.id.get != c.id.get) {
            logger.info(s"getOrCreateConsumer says: Found CONFLICTING auto-created consumer holding the same (azp, iss). Clearing its azp/iss/sub to avoid unique constraint violation. Stale consumer: consumerId=${stale.consumerId.get}, key=${stale.key.get}, azp=${stale.azp.get}, iss=${stale.iss.get}, sub=${stale.sub.get}")
            stale.azp(APIUtil.generateUUID())
            stale.sub(APIUtil.generateUUID())
            stale.saveMe()
            logger.info(s"getOrCreateConsumer says: Cleared stale consumer. Now: consumerId=${stale.consumerId.get}, azp=${stale.azp.get}, sub=${stale.sub.get}")
          }
        }
        // End of transitional cleanup block
        logger.info(s"getOrCreateConsumer says: Updating azp/iss/sub on pre-registered consumer so future lookups also match by (azp, iss)...")
        // Populate azp, iss, sub on the existing consumer so future lookups can also find it by (azp, iss)
        for (found <- byKeyMatchingAzp) {
          azp.foreach(v => found.azp(v))
          iss.foreach(v => found.iss(v))
          sub.foreach(v => found.sub(v))
          found.saveMe()
          logger.info(s"getOrCreateConsumer says: Updated pre-registered consumer. Now: consumerId=${found.consumerId.get}, key=${found.key.get}, azp=${found.azp.get}, iss=${found.iss.get}, sub=${found.sub.get}")
        }
        byKeyMatchingAzp
      } else {
        logger.info(s"getOrCreateConsumer says: MISS on lookup 2 (no consumer has key=${azp.getOrElse("None")}). Trying lookup 3 (by azp+iss pair)...")

        // 3rd try: find by (azp, iss) pair issued by External Identity Provider
        // The azp field in a JWT represents the Authorized Party (OAuth 2.0 / OpenID Connect client application).
        // The pair (azp, iss) is a unique key in case of Client of an Identity Provider
        val byAzpIss = Consumer.find(By(Consumer.azp, azp.getOrElse("None")), By(Consumer.iss, iss.getOrElse("None")))
        if (byAzpIss.isDefined) {
          val c = byAzpIss.openOrThrowException("checked isDefined")
          logger.info(s"getOrCreateConsumer says: MATCH on lookup 3 (by azp+iss). Found auto-created consumer: consumerId=${c.consumerId.get}, key=${c.key.get}, azp=${c.azp.get}, iss=${c.iss.get}")
          byAzpIss
        } else {
          logger.info(s"getOrCreateConsumer says: MISS on all 3 lookups. Will CREATE a new consumer. Searched: consumerId=${consumerId.getOrElse("None")}, key=${azp.getOrElse("None")}, (azp=${azp.getOrElse("None")}, iss=${iss.getOrElse("None")})")
          Empty
        }
      }
    }
    consumer match {
      case Full(c) => Full(c)
      case Failure(msg, t, c) => Failure(msg, t, c)
      case ParamFailure(x,y,z,q) => ParamFailure(x,y,z,q)
      case Empty =>
        tryo {
          val c = Consumer.create
          val actualKey = key.getOrElse(Helpers.randomString(40).toLowerCase)
          val actualSecret = secret.getOrElse(Helpers.randomString(40).toLowerCase)
          val actualConsumerId = consumerId.getOrElse {
            azp match {
              case Some(value) if APIUtil.checkIfStringIsUUID(value) => value
              case Some(value) => s"${value}_${APIUtil.generateUUID()}"
              case None => APIUtil.generateUUID()
            }
          }
          c.key(actualKey)
          c.secret(actualSecret)
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
            case Some(v) =>
              val count = Consumer.findAll(By(Consumer.name, v)).size
              if (count == 0)
                c.name(v)
              else
                c.name(v + "_" + Helpers.randomString(10).toLowerCase)
            case None =>
          }
          appType match {
            case Some(v) => v match {
              case Confidential => c.appType(Confidential.toString)
              case Public => c.appType(Public.toString)
              case Unknown => c.appType(Unknown.toString)
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
          certificate match {
            case Some(v) => c.clientCertificate(v)
            case None =>
          }
          logoUrl match {
            case Some(v) => c.logoUrl(v)
            case None =>
          }
          c.consumerId(actualConsumerId)
          val createdConsumer = c.saveMe()
          if(integrateWithHydra) createHydraClient(createdConsumer)
          createdConsumer
        } match {
          case Full(c) => Full(c)
          case Failure(_, _, _) =>
            // UniqueIndex violated by concurrent insert — re-fetch using the most specific available key.
            // Searching by (azp="", sub="") when both are absent would match unrelated consumers.
            (azp, sub) match {
              case (Some(a), Some(s)) => Consumer.find(By(Consumer.azp, a), By(Consumer.sub, s))
              case _                  => key.flatMap(k => Consumer.find(By(Consumer.key, k)))
            }
          case other => other
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
      consumer.consumerId(APIUtil.generateUUID()).save
    }
  }.forall(_ == true)

}

class Consumer extends LongKeyedMapper[Consumer] with CreatedUpdated{
  def getSingleton = Consumer
  def primaryKeyField = id
  
  // Note: There are two IDs on Consumer.
  // `id` is the Long primary key (MappedLongIndex).
  // `consumerId` is the UUID-based string identifier exposed externally as consumer_id in the API.
  //
  // consumerId is 250 chars to accommodate:
  //   - Standard UUIDs (36 chars) — the default
  //   - Gateway Login external app_id values (variable length)
  //   - OAuth2 composite IDs in format "azp_UUID" created by OAuth2.getOrCreateConsumer (up to ~77 chars)
  //
  // WARNING: Do not increase this length. Other tables (e.g. MappedConsent.mConsumerId) store
  // copies of this value.
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

  private def uniqueName(field: MappedString[Consumer])(s: String): List[FieldError] = {
    val consumer = Consumer.find(By(Consumer.name, s))
    if(consumer.isDefined)
      List(FieldError(field, {field.displayName + " must be unique"}))
    else 
      Nil
  }

  object key extends MappedString(this, 250)
  object secret extends MappedString(this, 250)
  object azp extends MappedString(this, 250) {
    // because different databases treat unique indexes on NULL values differently.
    override def defaultValue = APIUtil.generateUUID() 
  }
  object aud extends MappedText(this) {
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
  
  object logoUrl extends MappedString(this, 250){
    override def displayName = "Logo URL:"
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
    override def defaultValue: Long = APIUtil.getPropsAsLongValue("rate_limiting_per_second", -1)
  }
  object perMinuteCallLimit extends MappedLong(this) {
    override def defaultValue: Long = APIUtil.getPropsAsLongValue("rate_limiting_per_minute", -1)
  }
  object perHourCallLimit extends MappedLong(this) {
    override def defaultValue: Long = APIUtil.getPropsAsLongValue("rate_limiting_per_hour", -1)
  }
  object perDayCallLimit extends MappedLong(this) {
    override def defaultValue: Long = APIUtil.getPropsAsLongValue("rate_limiting_per_day", -1)
  }
  object perWeekCallLimit extends MappedLong(this) {
    override def defaultValue : Long = APIUtil.getPropsAsLongValue("rate_limiting_per_week", -1)
  }
  object perMonthCallLimit extends MappedLong(this) {
    override def defaultValue : Long = APIUtil.getPropsAsLongValue("rate_limiting_per_month", -1)
  }
  object clientCertificate extends MappedString(this, 4000)
  object company extends MappedString(this, 100) {
    override def displayName = "Company:"
  }
}

object Consumer extends Consumer with MdcLoggable with LongKeyedMetaMapper[Consumer] {

  override def dbIndexes = UniqueIndex(key) :: UniqueIndex(azp, sub) :: super.dbIndexes

  def getRedirectURLByConsumerKey(consumerKey: String): String = {
    logger.debug("hello from getRedirectURLByConsumerKey")
    val consumer: Consumer = Consumers.consumers.vend.getConsumerByConsumerKey(consumerKey).openOrThrowException(s"OBP Consumer not found by consumerKey. You looked for $consumerKey Please check the database")
    logger.debug(s"getRedirectURLByConsumerKey found consumer with id: ${consumer.id}, name is: ${consumer.name}, isActive is ${consumer.isActive}")
    consumer.redirectURL.toString()
  }

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
        case Some(v) => n.`value`(v)
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
      By(Nonce.`value`, value),
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
  object `value` extends MappedString(this,250)

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
      case Full(t) => t.userForeignKey(userId).save
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
  def isValid : Boolean = expirationDate.get after new Date(System.currentTimeMillis())
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
    if(thirdPartyApplicationSecret.get.isEmpty){
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
