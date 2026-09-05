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
import code.api.util.migration.Migration.DbFunction
import code.api.util._
import code.consumer.{Consumers, ConsumersProvider}
import code.model.AppType.{Confidential, Public, Unknown}
import code.model.dataAccess.ResourceUser
import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import code.nonce.NoncesProvider
import code.token.TokensProvider
import code.users.Users
import code.util.Helper.MdcLoggable
import com.github.dwickern.macros.NameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import net.liftweb.common._
import net.liftweb.util.Helpers._
import net.liftweb.util.Helpers
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
      Consumer.findByPrimaryKey(id)
    )
  }

  override def getConsumerByPrimaryId(id: Long): Box[Consumer] =
    Consumer.findByPrimaryKey(id)

  override def getConsumerByConsumerKey(consumerKey: String): Box[Consumer] =
    Consumer.findByKey(consumerKey)

  override def getConsumerByConsumerKeyFuture(consumerKey: String): Future[Box[Consumer]] = {
    Future{
      getConsumerByConsumerKey(consumerKey)
    }
  }

  def getConsumerByPemCertificate(pem: String): Box[Consumer] =
    Consumer.findByClientCertificate(pem)
  
  def getConsumerByConsumerId(consumerId: String): Box[Consumer] =
    Consumer.findByConsumerId(consumerId)
  override def getConsumerByConsumerIdFuture(consumerId: String): Future[Box[Consumer]] = {
    Future{
      getConsumerByConsumerId(consumerId)
    }
  }

  def getConsumersByUserId(userId: String): List[Consumer] =
    Consumer.findAllByCreatedByUserId(userId)
  override def getConsumersByUserIdFuture(userId: String): Future[List[Consumer]] = {
    Future(getConsumersByUserId(userId))
  }

  def getConsumers(queryParams: List[OBPQueryParam], callContext: Option[CallContext]): List[Consumer] = {
    Consumer.findAll(ConsumerQuery(
      limit = queryParams.collect { case OBPLimit(value) => value }.headOption,
      offset = queryParams.collect { case OBPOffset(value) => value }.headOption,
      fromDate = queryParams.collect { case OBPFromDate(date) => date }.headOption,
      toDate = queryParams.collect { case OBPToDate(date) => date }.headOption,
      ascending = queryParams.collect {
        case OBPOrdering(_, OBPAscending) => true
        case OBPOrdering(_, OBPDescending) => false
      }.headOption,
      azp = queryParams.collect { case OBPAzp(value) => value }.headOption,
      iss = queryParams.collect { case OBPIss(value) => value }.headOption,
      consumerId = queryParams.collect { case OBPConsumerId(value) => value }.headOption))
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
      // A name that is already taken gets a random suffix rather than failing the unique-name
      // validation below. Preserved.
      val actualName = name.map { v =>
        if (Consumer.findAllByName(v).isEmpty) v
        else v + "_" + Helpers.randomString(10).toLowerCase
      }
      val row = Consumer.defaults.copy(
        key = key.getOrElse(Consumer.defaults.key),
        secret = secret.getOrElse(Consumer.defaults.secret),
        isActive = isActive.getOrElse(Consumer.defaults.isActive),
        name = actualName.getOrElse(Consumer.defaults.name),
        appType = appType.map(_.toString).getOrElse(Consumer.defaults.appType),
        description = description.getOrElse(Consumer.defaults.description),
        // MappedEmail lowercased and trimmed on every set, so the stored address is normalised
        // before it is validated.
        developerEmail = developerEmail.map(Consumer.normalizeEmail)
          .getOrElse(Consumer.defaults.developerEmail),
        redirectURL = redirectURL.getOrElse(Consumer.defaults.redirectURL),
        logoUrl = logoURL.getOrElse(Consumer.defaults.logoUrl),
        createdByUserId = createdByUserId.getOrElse(Consumer.defaults.createdByUserId),
        company = company.getOrElse(Consumer.defaults.company),
        clientCertificate = clientCertificate.filter(StringUtils.isNotBlank)
          .getOrElse(Consumer.defaults.clientCertificate))

      val errors = Consumer.validate(row)
      if(errors.isEmpty) {
        Consumer.insert(row)
      }
      else
        throw new Error(errors.mkString(";"))
    }
  }

  def deleteConsumer(consumer: Consumer): Boolean = Consumer.delete(consumer)

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
    val consumer = Consumer.findByPrimaryKey(id)
    consumer match {
      case Full(c) => tryo {
        Consumer.update(c.copy(
          key = key.getOrElse(c.key),
          secret = secret.getOrElse(c.secret),
          isActive = isActive.getOrElse(c.isActive),
          name = name.getOrElse(c.name),
          clientCertificate = certificate.getOrElse(c.clientCertificate),
          appType = appType.map(_.toString).getOrElse(c.appType),
          description = description.getOrElse(c.description),
          developerEmail = developerEmail.map(Consumer.normalizeEmail)
            .getOrElse(c.developerEmail),
          redirectURL = redirectURL.getOrElse(c.redirectURL),
          logoUrl = logoURL.getOrElse(c.logoUrl),
          createdByUserId = createdByUserId.getOrElse(c.createdByUserId)))
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
    val consumer = Consumer.findByPrimaryKey(id)
    consumer match {
      case Full(c) => tryo {
        Consumer.update(c.copy(
          perSecondCallLimit = perSecond.map(_.toLong).getOrElse(c.perSecondCallLimit),
          perMinuteCallLimit = perMinute.map(_.toLong).getOrElse(c.perMinuteCallLimit),
          perHourCallLimit = perHour.map(_.toLong).getOrElse(c.perHourCallLimit),
          perDayCallLimit = perDay.map(_.toLong).getOrElse(c.perDayCallLimit),
          perWeekCallLimit = perWeek.map(_.toLong).getOrElse(c.perWeekCallLimit),
          perMonthCallLimit = perMonth.map(_.toLong).getOrElse(c.perMonthCallLimit)))
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
    val byConsumerId = Consumer.findByConsumerId(consumerId.getOrElse("None"))
    val consumer: Box[Consumer] = if (byConsumerId.isDefined) {
      val c = byConsumerId.openOrThrowException("checked isDefined")
      logger.info(s"getOrCreateConsumer says: MATCH on lookup 1 (by consumerId). Found consumer: consumerId=${c.consumerId}, key=${c.key}, azp=${c.azp}, iss=${c.iss}")
      byConsumerId
    } else {
      logger.info(s"getOrCreateConsumer says: MISS on lookup 1 (by consumerId=${consumerId.getOrElse("None")}). Trying lookup 2 (by Consumer.key matching azp)...")

      // 2nd try: find by consumer key matching azp (pre-registered consumer whose key is the OAuth2 client_id)
      // This is checked before (azp, iss) so that a pre-registered consumer takes priority over an auto-created one
      val byKeyMatchingAzp = Consumer.findByKey(azp.getOrElse("None"))
      if (byKeyMatchingAzp.isDefined) {
        val c = byKeyMatchingAzp.openOrThrowException("checked isDefined")
        logger.info(s"getOrCreateConsumer says: MATCH on lookup 2 (by Consumer.key matching azp). Found pre-registered consumer: consumerId=${c.consumerId}, key=${c.key}, azp=${c.azp}, iss=${c.iss}")
        // Transitional cleanup: before the duplicate-consumer fix, OAuth2/OIDC flows could auto-create
        // consumers that now conflict with the pre-registered one we just found. Clear the stale consumer's
        // azp/iss/sub so we can populate those fields on the pre-registered consumer without a unique
        // constraint violation. This block can be removed once all environments have been cleaned up.
        val conflicting = Consumer.findByAzpAndIss(azp.getOrElse("None"), iss.getOrElse("None"))
        for (stale <- conflicting) {
          if (stale.id != c.id) {
            logger.info(s"getOrCreateConsumer says: Found CONFLICTING auto-created consumer holding the same (azp, iss). Clearing its azp/iss/sub to avoid unique constraint violation. Stale consumer: consumerId=${stale.consumerId}, key=${stale.key}, azp=${stale.azp}, iss=${stale.iss}, sub=${stale.sub}")
            val cleared = Consumer.update(stale.copy(
              azp = APIUtil.generateUUID(), sub = APIUtil.generateUUID()))
            logger.info(s"getOrCreateConsumer says: Cleared stale consumer. Now: consumerId=${cleared.consumerId}, azp=${cleared.azp}, sub=${cleared.sub}")
          }
        }
        // End of transitional cleanup block
        logger.info(s"getOrCreateConsumer says: Updating azp/iss/sub on pre-registered consumer so future lookups also match by (azp, iss)...")
        // Populate azp, iss, sub on the existing consumer so future lookups can also find it by (azp, iss)
        val updatedPreRegistered = byKeyMatchingAzp.map { found =>
          val updated = Consumer.update(found.copy(
            azp = azp.getOrElse(found.azp),
            iss = iss.getOrElse(found.iss),
            sub = sub.getOrElse(found.sub)))
          logger.info(s"getOrCreateConsumer says: Updated pre-registered consumer. Now: consumerId=${updated.consumerId}, key=${updated.key}, azp=${updated.azp}, iss=${updated.iss}, sub=${updated.sub}")
          updated
        }
        updatedPreRegistered
      } else {
        logger.info(s"getOrCreateConsumer says: MISS on lookup 2 (no consumer has key=${azp.getOrElse("None")}). Trying lookup 3 (by azp+iss pair)...")

        // 3rd try: find by (azp, iss) pair issued by External Identity Provider
        // The azp field in a JWT represents the Authorized Party (OAuth 2.0 / OpenID Connect client application).
        // The pair (azp, iss) is a unique key in case of Client of an Identity Provider
        val byAzpIss = Consumer.findByAzpAndIss(azp.getOrElse("None"), iss.getOrElse("None"))
        if (byAzpIss.isDefined) {
          val c = byAzpIss.openOrThrowException("checked isDefined")
          logger.info(s"getOrCreateConsumer says: MATCH on lookup 3 (by azp+iss). Found auto-created consumer: consumerId=${c.consumerId}, key=${c.key}, azp=${c.azp}, iss=${c.iss}")
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
          val actualKey = key.getOrElse(Helpers.randomString(40).toLowerCase)
          val actualSecret = secret.getOrElse(Helpers.randomString(40).toLowerCase)
          val actualConsumerId = consumerId.getOrElse {
            azp match {
              case Some(value) if APIUtil.checkIfStringIsUUID(value) => value
              case Some(value) => s"${value}_${APIUtil.generateUUID()}"
              case None => APIUtil.generateUUID()
            }
          }
          val defaults = Consumer.defaults
          // A name already in use gets a random suffix rather than colliding. Preserved.
          val actualName = name.map { v =>
            if (Consumer.findAllByName(v).isEmpty) v
            else v + "_" + Helpers.randomString(10).toLowerCase
          }
          Consumer.insert(defaults.copy(
            key = actualKey,
            secret = actualSecret,
            aud = aud.getOrElse(defaults.aud),
            azp = azp.getOrElse(defaults.azp),
            iss = iss.getOrElse(defaults.iss),
            sub = sub.getOrElse(defaults.sub),
            isActive = isActive.getOrElse(defaults.isActive),
            name = actualName.getOrElse(defaults.name),
            appType = appType.map(_.toString).getOrElse(defaults.appType),
            description = description.getOrElse(defaults.description),
            developerEmail = developerEmail.map(Consumer.normalizeEmail)
              .getOrElse(defaults.developerEmail),
            redirectURL = redirectURL.getOrElse(defaults.redirectURL),
            createdByUserId = createdByUserId.getOrElse(defaults.createdByUserId),
            clientCertificate = certificate.getOrElse(defaults.clientCertificate),
            logoUrl = logoUrl.getOrElse(defaults.logoUrl),
            consumerId = actualConsumerId))
        } match {
          case Full(c) => Full(c)
          case Failure(_, _, _) =>
            // UniqueIndex violated by concurrent insert — re-fetch using the most specific available key.
            // Searching by (azp="", sub="") when both are absent would match unrelated consumers.
            (azp, sub) match {
              case (Some(a), Some(s)) => Consumer.findByAzpAndSub(a, s)
              case _                  => key.flatMap(k => Consumer.findByKey(k))
            }
          case other => other
        }
    }
  }
  
  override def populateMissingUUIDs(): Boolean = {
    logger.warn("Executed script: MappedConsumersProvider." + NameOf.nameOf(populateMissingUUIDs()))
    //back up consumer table
    DbFunction.makeBackUpOfTableByName("consumer")

    for {
      consumer <- Consumer.findAllWithoutConsumerId()
    } yield {
      Consumer.setConsumerId(consumer.id, APIUtil.generateUUID())
    }
  }.forall(_ == true)

}

/**
 * A registered application.
 *
 * Two ids, not interchangeable: `id` is the surrogate key that Token points at, `consumerId` is the
 * string id the API exposes and that other tables keep copies of.
 *
 * `azp` and `sub` default to fresh UUIDs rather than null on purpose - the unique index over the
 * pair de-duplicates auto-created OIDC consumers, and databases disagree about whether NULLs
 * collide, so a generated value keeps hand-registered consumers distinct without relying on NULL
 * semantics.
 */
case class Consumer(
  id: Long = 0L,
  consumerId: String = "",
  key: String = "",
  secret: String = "",
  azp: String = "",
  aud: String = null,
  iss: String = null,
  sub: String = "",
  isActive: Boolean = false,
  name: String = "",
  appType: String = "",
  description: String = "",
  developerEmail: String = "",
  redirectURL: String = "",
  logoUrl: String = "",
  userAuthenticationURL: String = "",
  createdByUserId: String = "",
  perSecondCallLimit: Long = -1,
  perMinuteCallLimit: Long = -1,
  perHourCallLimit: Long = -1,
  perDayCallLimit: Long = -1,
  perWeekCallLimit: Long = -1,
  perMonthCallLimit: Long = -1,
  clientCertificate: String = "",
  jwksUri: String = "",
  company: String = "",
  createdAt: Date = null,
  updatedAt: Date = null
)

object Consumer extends MdcLoggable {

  /**
   * match the flow style, it can be http, https, or Private-Use URI Scheme Redirection for app:
   * http://some.domain.com/path
   * https://some.domain.com/path
   * com.example.app:/oauth2redirect/example-provider
   */
  val redirectURLRegex = """^([.\w]+:|(http|https):/)/(www.)?\S+?(:\d{2,6})?\S*$""".r

  /**
   * The call limits the entity's MappedLong fields defaulted to, read from props on every access
   * as they were there.
   *
   * These are also what a NULL column reads back as: MappedLong's reader is
   * `if (isNull) defaultValue else v`, so unlike a boolean - where the getter is
   * `data openOr false` and the declared default never applies on read - a NULL number really did
   * come back as this. Rows predating the columns therefore carried the configured limit, not
   * "unlimited"; MigrationOfConsumerRateLimiting seeds the ratelimiting table from them.
   */
  def perSecondCallLimitDefault: Long = APIUtil.getPropsAsLongValue("rate_limiting_per_second", -1)
  def perMinuteCallLimitDefault: Long = APIUtil.getPropsAsLongValue("rate_limiting_per_minute", -1)
  def perHourCallLimitDefault: Long = APIUtil.getPropsAsLongValue("rate_limiting_per_hour", -1)
  def perDayCallLimitDefault: Long = APIUtil.getPropsAsLongValue("rate_limiting_per_day", -1)
  def perWeekCallLimitDefault: Long = APIUtil.getPropsAsLongValue("rate_limiting_per_week", -1)
  def perMonthCallLimitDefault: Long = APIUtil.getPropsAsLongValue("rate_limiting_per_month", -1)

  /** The defaults the entity's fields carried, several of which came from props at first use. */
  def defaults: Consumer = Consumer(
    consumerId = APIUtil.generateUUID(),
    azp = APIUtil.generateUUID(),
    sub = APIUtil.generateUUID(),
    isActive = APIUtil.getPropsAsBoolValue("consumers_enabled_by_default", false),
    perSecondCallLimit = perSecondCallLimitDefault,
    perMinuteCallLimit = perMinuteCallLimitDefault,
    perHourCallLimit = perHourCallLimitDefault,
    perDayCallLimit = perDayCallLimitDefault,
    perWeekCallLimit = perWeekCallLimitDefault,
    perMonthCallLimit = perMonthCallLimitDefault)

  /** RFC 5321's 254-character cap and the address pattern MappedEmail validated against. */
  private val maxEmailLength = 254
  private val emailPattern = java.util.regex.Pattern.compile(
    "^[a-z0-9._%\\-+]+@(?:[a-z0-9\\-]+\\.)+[a-z]{2,}$", java.util.regex.Pattern.CASE_INSENSITIVE)

  /**
   * What MappedEmail's setFilter did to a developer email on every set: null becomes "", the rest
   * is lowercased and trimmed. Callers apply it where the entity used to assign the field, so the
   * stored value and the validated value are the same one.
   */
  def normalizeEmail(value: String): String =
    (if (value == null) "" else value).toLowerCase.trim

  /**
   * The field validations Mapper ran on save, in field-declaration order and with the same
   * messages, because createConsumer throws them joined and tests assert the wording. Reproduced
   * exactly, quirks included: "Description:" runs straight into "can not be empty" with no space,
   * and an unset or malformed developer email fails with the raw i18n key MappedEmail used.
   *
   * The URI checks are CommonFunctions.validUri's: an empty value passes, anything else has to
   * parse as a java.net.URI. That is far laxer than it looks - "not a url" parses fine as a
   * relative URI - but it is what the entity enforced.
   */
  def validate(row: Consumer): List[String] = {
    val nameErrors =
      (if (row.name.length() < 3) List("Application name: must be at least 3 characters") else Nil) :::
      (if (findByName(row.name).isDefined) List("Application name: must be unique") else Nil)
    val descriptionErrors =
      if (row.description.isEmpty) List("Description:can not be empty") else Nil
    val developerEmailErrors =
      if (row.developerEmail != null && row.developerEmail.length <= maxEmailLength &&
          emailPattern.matcher(row.developerEmail).matches) Nil
      else List("invalid.email.address")
    def uriError(displayName: String, value: String): List[String] =
      if (value.isEmpty) Nil
      else if (tryo(new java.net.URI(value)).isEmpty) List(s"$displayName must be a valid URI")
      else Nil
    nameErrors ::: descriptionErrors ::: developerEmailErrors :::
      uriError("Redirect URL:", row.redirectURL) :::
      uriError("Logo URL:", row.logoUrl) :::
      uriError("User authentication URL:", row.userAuthenticationURL)
  }

  private val selectColumns =
    fr"""SELECT id, consumerid, key_c, secret, azp, aud, iss, sub, isactive, name, apptype,
                description, developeremail, redirecturl, logourl, userauthenticationurl,
                createdbyuserid, persecondcalllimit, perminutecalllimit, perhourcalllimit,
                perdaycalllimit, perweekcalllimit, permonthcalllimit, clientcertificate, jwksuri,
                company, createdat, updatedat
         FROM consumer"""

  // 28 columns, past the 22-element tuple limit, so the row is read as two nested tuples.
  private type RowHead = (Long, Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[Boolean], Option[String],
    Option[String], Option[String], Option[String], Option[String])
  private type RowTail = (Option[String], Option[String], Option[String], Option[Long],
    Option[Long], Option[Long], Option[Long], Option[Long], Option[Long], Option[String],
    Option[String], Option[String], Option[java.sql.Timestamp], Option[java.sql.Timestamp])
  private type Row = (RowHead, RowTail)

  /** Timestamps come back as plain java.util.Date, which is what CreatedUpdated gave. */
  private def readDate(value: Option[java.sql.Timestamp]): Date =
    value.map(t => new Date(t.getTime)).orNull

  private def fromRow(row: Row): Consumer = row match {
    case ((id, consumerId, key, secret, azp, aud, iss, sub, isActive, name, appType, description,
           developerEmail, redirectURL),
          (logoUrl, userAuthenticationURL, createdByUserId, perSecond, perMinute, perHour, perDay,
           perWeek, perMonth, clientCertificate, jwksUri, company, createdAt, updatedAt)) =>
      Consumer(id, consumerId.orNull, key.orNull, secret.orNull, azp.orNull, aud.orNull,
        iss.orNull, sub.orNull,
        // What a NULL column reads back as is per field type, not one rule: MappedBoolean's getter
        // is `data openOr false`, so a NULL flag is false whatever its declared default, while
        // MappedLong's reader is `if (isNull) defaultValue`, so a NULL limit is the configured one.
        isActive.getOrElse(false), name.orNull, appType.orNull, description.orNull,
        developerEmail.orNull, redirectURL.orNull, logoUrl.orNull, userAuthenticationURL.orNull,
        createdByUserId.orNull,
        perSecond.getOrElse(perSecondCallLimitDefault), perMinute.getOrElse(perMinuteCallLimitDefault),
        perHour.getOrElse(perHourCallLimitDefault), perDay.getOrElse(perDayCallLimitDefault),
        perWeek.getOrElse(perWeekCallLimitDefault), perMonth.getOrElse(perMonthCallLimitDefault),
        clientCertificate.orNull, jwksUri.orNull, company.orNull, readDate(createdAt),
        readDate(updatedAt))
  }

  private def query(condition: Fragment): List[Consumer] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def opt(value: String): Option[String] = Option(value)

  private def ts(value: Date): Option[java.sql.Timestamp] =
    Option(value).map(d => new java.sql.Timestamp(d.getTime))

  private def one(condition: Fragment): Box[Consumer] =
    query(condition ++ fr"ORDER BY id ASC LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def findByPrimaryKey(id: Long): Box[Consumer] = one(fr"WHERE id = $id")
  def findByKey(key: String): Box[Consumer] = one(fr"WHERE key_c = ${opt(key)}")
  def findByConsumerId(consumerId: String): Box[Consumer] =
    one(fr"WHERE consumerid = ${opt(consumerId)}")
  def findByName(name: String): Box[Consumer] = one(fr"WHERE name = ${opt(name)}")
  def findByClientCertificate(pem: String): Box[Consumer] =
    one(fr"WHERE clientcertificate = ${opt(pem)}")
  def findByAzpAndIss(azp: String, iss: String): Box[Consumer] =
    one(fr"WHERE azp = ${opt(azp)} AND iss = ${opt(iss)}")
  def findByAzpAndSub(azp: String, sub: String): Box[Consumer] =
    one(fr"WHERE azp = ${opt(azp)} AND sub = ${opt(sub)}")

  def findAllByCreatedByUserId(userId: String): List[Consumer] =
    query(fr"WHERE createdbyuserid = ${opt(userId)}")
  def findAllByName(name: String): List[Consumer] = query(fr"WHERE name = ${opt(name)}")
  def findAllByAzp(azp: String): List[Consumer] = query(fr"WHERE azp = ${opt(azp)}")
  def findAll(): List[Consumer] = query(Fragment.empty)

  def countByAzpAndSub(azp: String, sub: String): Long =
    DoobieUtil.runQuery(
      sql"SELECT COUNT(*) FROM consumer WHERE azp = ${opt(azp)} AND sub = ${opt(sub)}"
        .query[Long].unique)

  /** Consumers whose consumer id was never filled in - what populateMissingUUIDs repairs. */
  def findAllWithoutConsumerId(): List[Consumer] =
    query(fr"WHERE consumerid IS NULL OR consumerid = ''")

  def findAll(params: ConsumerQuery): List[Consumer] = {
    val filters = List(
      params.fromDate.map(d => fr"createdat >= ${ts(d)}"),
      params.toDate.map(d => fr"createdat <= ${ts(d)}"),
      params.azp.map(v => fr"azp = ${opt(v)}"),
      params.iss.map(v => fr"iss = ${opt(v)}"),
      params.consumerId.map(v => fr"consumerid = ${opt(v)}")
    ).flatten
    val where =
      if (filters.isEmpty) Fragment.empty
      else fr"WHERE " ++ filters.reduce((a, b) => a ++ fr"AND" ++ b)
    val ordering = params.ascending match {
      case Some(true) => fr"ORDER BY createdat ASC"
      case Some(false) => fr"ORDER BY createdat DESC"
      case None => Fragment.empty
    }
    val paging =
      params.limit.map(value => fr"LIMIT $value").getOrElse(Fragment.empty) ++
        params.offset.map(value => fr"OFFSET $value").getOrElse(Fragment.empty)
    query(where ++ ordering ++ paging)
  }

  /**
   * Writes a consumer. The unique indexes on key and on (azp, sub) are what reject a concurrent
   * duplicate; getOrCreateConsumer catches that failure and re-reads.
   */
  def insert(row: Consumer): Consumer = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    val id = DoobieUtil.runUpdate(
      sql"""INSERT INTO consumer
            (consumerid, key_c, secret, azp, aud, iss, sub, isactive, name, apptype, description,
             developeremail, redirecturl, logourl, userauthenticationurl, createdbyuserid,
             persecondcalllimit, perminutecalllimit, perhourcalllimit, perdaycalllimit,
             perweekcalllimit, permonthcalllimit, clientcertificate, jwksuri, company,
             createdat, updatedat)
            VALUES (${opt(row.consumerId)}, ${opt(row.key)}, ${opt(row.secret)}, ${opt(row.azp)},
             ${opt(row.aud)}, ${opt(row.iss)}, ${opt(row.sub)}, ${row.isActive}, ${opt(row.name)},
             ${opt(row.appType)}, ${opt(row.description)}, ${opt(row.developerEmail)},
             ${opt(row.redirectURL)}, ${opt(row.logoUrl)}, ${opt(row.userAuthenticationURL)},
             ${opt(row.createdByUserId)}, ${row.perSecondCallLimit}, ${row.perMinuteCallLimit},
             ${row.perHourCallLimit}, ${row.perDayCallLimit}, ${row.perWeekCallLimit},
             ${row.perMonthCallLimit}, ${opt(row.clientCertificate)}, ${opt(row.jwksUri)},
             ${opt(row.company)}, $now, $now)"""
        .update.withUniqueGeneratedKeys[Long]("id"))
    row.copy(id = id, createdAt = new Date(now.getTime), updatedAt = new Date(now.getTime))
  }

  /** Rewrites an existing consumer by its surrogate key. */
  def update(row: Consumer): Consumer = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""UPDATE consumer
            SET consumerid = ${opt(row.consumerId)}, key_c = ${opt(row.key)},
                secret = ${opt(row.secret)}, azp = ${opt(row.azp)}, aud = ${opt(row.aud)},
                iss = ${opt(row.iss)}, sub = ${opt(row.sub)}, isactive = ${row.isActive},
                name = ${opt(row.name)}, apptype = ${opt(row.appType)},
                description = ${opt(row.description)}, developeremail = ${opt(row.developerEmail)},
                redirecturl = ${opt(row.redirectURL)}, logourl = ${opt(row.logoUrl)},
                userauthenticationurl = ${opt(row.userAuthenticationURL)},
                createdbyuserid = ${opt(row.createdByUserId)},
                persecondcalllimit = ${row.perSecondCallLimit},
                perminutecalllimit = ${row.perMinuteCallLimit},
                perhourcalllimit = ${row.perHourCallLimit},
                perdaycalllimit = ${row.perDayCallLimit},
                perweekcalllimit = ${row.perWeekCallLimit},
                permonthcalllimit = ${row.perMonthCallLimit},
                clientcertificate = ${opt(row.clientCertificate)}, jwksuri = ${opt(row.jwksUri)},
                company = ${opt(row.company)}, updatedat = $now
            WHERE id = ${row.id}"""
        .update.run)
    row.copy(updatedAt = new Date(now.getTime))
  }

  def setConsumerId(id: Long, consumerId: String): Boolean =
    DoobieUtil.runUpdate(
      sql"""UPDATE consumer SET consumerid = ${opt(consumerId)},
              updatedat = ${new java.sql.Timestamp(System.currentTimeMillis())}
            WHERE id = $id"""
        .update.run) > 0

  def delete(row: Consumer): Boolean =
    DoobieUtil.runUpdate(sql"DELETE FROM consumer WHERE id = ${row.id}".update.run) > 0

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM consumer".update.run)
    ()
  }

  def getRedirectURLByConsumerKey(consumerKey: String): String = {
    logger.debug("hello from getRedirectURLByConsumerKey")
    val consumer: Consumer = Consumers.consumers.vend.getConsumerByConsumerKey(consumerKey).openOrThrowException(s"OBP Consumer not found by consumerKey. You looked for $consumerKey Please check the database")
    logger.debug(s"getRedirectURLByConsumerKey found consumer with id: ${consumer.id}, name is: ${consumer.name}, isActive is ${consumer.isActive}")
    consumer.redirectURL.toString()
  }
}

/** The paging, date range, ordering and filters a consumer listing carries. */
case class ConsumerQuery(
  limit: Option[Int],
  offset: Option[Int],
  fromDate: Option[Date],
  toDate: Option[Date],
  ascending: Option[Boolean],
  azp: Option[String],
  iss: Option[String],
  consumerId: Option[String]
)


object MappedNonceProvider extends NoncesProvider {
  override def createNonce(id: Option[Long],
                           consumerKey: Option[String],
                           tokenKey: Option[String],
                           timestamp: Option[Date],
                           value: Option[String]): Box[Nonce] = {
    tryo {
      // An absent field keeps the entity's default: "" for the token key, null for the rest.
      Nonce.insert(
        id = id,
        consumerkey = consumerKey.orNull,
        tokenKey = tokenKey.getOrElse(""),
        timestamp = timestamp.orNull,
        value = value.orNull)
    }
  }

  override def deleteExpiredNonces(currentDate: Date): Boolean =
    Nonce.deleteOlderThan(currentDate)

  override def countNonces(consumerKey: String,
                           tokenKey: String,
                           timestamp: Date,
                           value: String): Long =
    Nonce.count(consumerKey, tokenKey, timestamp, value)

  override def countNoncesFuture(consumerKey: String,
                                 tokenKey: String,
                                 timestamp: Date,
                                 value: String): Future[Long] = {
    Future{countNonces(consumerKey, tokenKey, timestamp, value)}
  }

}

/**
 * One OAuth 1.0a nonce.
 *
 * The consumer and the token are held as their keys rather than as foreign keys: a nonce is a
 * replay guard with its own lifetime and never needs to join to either.
 */
case class Nonce(
  id: Long,
  consumerkey: String,
  tokenKey: String,
  timestamp: Date,
  `value`: String
)

object Nonce {

  // timestamp is a reserved word, so Schemifier named the column timestamp_c.
  private val selectColumns =
    fr"SELECT id, consumerkey, tokenkey, timestamp_c, value FROM nonce"

  private type Row = (Long, Option[String], Option[String], Option[java.sql.Timestamp],
    Option[String])

  private def fromRow(row: Row): Nonce = row match {
    case (id, consumerkey, tokenKey, timestamp, value) =>
      // A timestamp comes back as a plain java.util.Date, which is what MappedDateTime gave.
      Nonce(id, consumerkey.orNull, tokenKey.orNull,
        timestamp.map(t => new Date(t.getTime)).orNull, value.orNull)
  }

  private def opt(value: String): Option[String] = Option(value)

  private def ts(value: Date): Option[java.sql.Timestamp] =
    Option(value).map(d => new java.sql.Timestamp(d.getTime))

  def findAll(): List[Nonce] =
    DoobieUtil.runQuery(selectColumns.query[Row].to[List]).map(fromRow)

  /**
   * Writes a nonce, letting the caller pin the primary key.
   *
   * Only the provider's own callers pass an id, and only ever to reproduce a specific row; every
   * real request lets the identity column allocate one.
   */
  def insert(id: Option[Long], consumerkey: String, tokenKey: String, timestamp: Date,
             value: String): Nonce = {
    val newId = id match {
      case Some(pinned) =>
        DoobieUtil.runUpdate(
          sql"""INSERT INTO nonce (id, consumerkey, tokenkey, timestamp_c, value)
                VALUES ($pinned, ${opt(consumerkey)}, ${opt(tokenKey)}, ${ts(timestamp)},
                 ${opt(value)})"""
            .update.run)
        pinned
      case None =>
        DoobieUtil.runUpdate(
          sql"""INSERT INTO nonce (consumerkey, tokenkey, timestamp_c, value)
                VALUES (${opt(consumerkey)}, ${opt(tokenKey)}, ${ts(timestamp)}, ${opt(value)})"""
            .update.withUniqueGeneratedKeys[Long]("id"))
    }
    Nonce(newId, consumerkey, tokenKey, timestamp, value)
  }

  /** The replay check: an identical nonce inside the window means the request is a replay. */
  def count(consumerKey: String, tokenKey: String, timestamp: Date, value: String): Long =
    DoobieUtil.runQuery(
      sql"""SELECT COUNT(*) FROM nonce
            WHERE value = ${opt(value)} AND tokenkey = ${opt(tokenKey)}
              AND consumerkey = ${opt(consumerKey)} AND timestamp_c = ${ts(timestamp)}"""
        .query[Long].unique)

  /** What the database cleaner sweeps. Mapper deleted row by row; one statement does the same. */
  def deleteOlderThan(currentDate: Date): Boolean = {
    DoobieUtil.runUpdate(
      sql"DELETE FROM nonce WHERE timestamp_c < ${ts(currentDate)}".update.run)
    true
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM nonce".update.run)
    ()
  }
}

object MappedTokenProvider extends TokensProvider {
  override def getTokenByKey(key: String): Box[Token] = Token.findByKey(key)

  override def getTokenByKeyFuture(key: String): Future[Box[Token]] = {
    Future{
      getTokenByKey(key)
    }
  }
  override def getTokenByKeyAndType(key: String, tokenType: TokenType): Box[Token] =
    Token.findByKeyAndType(key, tokenType.toString)

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
      // An absent field keeps the entity's default: 0 for the numbers, "" for the strings, null
      // for the dates and for the two foreign keys.
      Token.insert(
        tokenType = tokenType.toString,
        consumerId = consumerId,
        userForeignKey = userId,
        key = key.getOrElse(""),
        secret = secret.getOrElse(""),
        duration = duration.getOrElse(0L),
        expirationDate = expirationDate.orNull,
        insertDate = insertDate.orNull,
        callbackURL = callbackURL.getOrElse(""))
    }
  }

  override def updateToken(id: Long, userId: Long): Boolean =
    Token.findByPrimaryKey(id) match {
      case Full(_) => Token.setUserForeignKey(id, userId)
      case _       => false
    }

  override def gernerateVerifier(id: Long): String =
    Token.findByPrimaryKey(id).map(_.gernerateVerifier).getOrElse("")

  override def deleteToken(id: Long): Boolean =
    Token.findByPrimaryKey(id) match {
      case Full(t) => Token.deleteByPrimaryKey(t.id)
      case _       => false
    }

  override def deleteExpiredTokens(currentDate: Date): Boolean =
    Token.deleteExpiredBefore(currentDate)
}


/**
 * One OAuth 1.0a token, request or access.
 *
 * `consumerId` and `userForeignKey` are the SURROGATE keys of the consumer and the resource user,
 * not their business ids - the consumer's own string id lives in a column of the same name on its
 * own table.
 *
 * `verifier` and `thirdPartyApplicationSecret` are generated on first read rather than at creation,
 * and the generator writes them back, so both accessors have a side effect. Preserved.
 */
case class Token(
  id: Long,
  tokenType: String,
  consumerId: Option[Long],
  userForeignKey: Option[Long],
  key: String,
  secret: String,
  callbackURL: String,
  verifier: String,
  duration: Long,
  expirationDate: Date,
  insertDate: Date,
  thirdPartyApplicationSecret: String
) {
  def user = userForeignKey.map(Users.users.vend.getResourceUserByResourceUserId).getOrElse(Empty)
  //The the consumer from Token by consumerId
  def consumer = consumerId.map(Consumers.consumers.vend.getConsumerByPrimaryId).getOrElse(Empty)
  def isValid : Boolean = expirationDate after new Date(System.currentTimeMillis())

  /** Generates and stores a verifier the first time it is asked for. */
  def gernerateVerifier : String =
    if (verifier.isEmpty){
        def fiveRandomNumbers() : String = {
          def r() = randomInt(9).toString //from zero to 9
          (1 to 5).map(x => r()).foldLeft("")(_ + _)
        }
      val generatedVerifier = fiveRandomNumbers()
      Token.setVerifier(id, generatedVerifier)
      generatedVerifier
    }
    else
      verifier

  // in the case of user authentication in a third party application
  // (see authenticationURL in class Consumer).
  // This secret will be used between the API server and the third party application
  // It will be used during the callback (the user coming back to the login page)
  // for entering the banking details.
  def generateThirdPartyApplicationSecret: String = {
    if(thirdPartyApplicationSecret.isEmpty){
      def r() = randomInt(9).toString //from zero to 9
      val generatedSecret = (1 to 10).map(x => r()).foldLeft("")(_ + _)
      Token.setThirdPartyApplicationSecret(id, generatedSecret)
      generatedSecret
    }
    else
      thirdPartyApplicationSecret
  }
}

object Token {

  // key is a reserved word, so Schemifier named the column key_c.
  private val selectColumns =
    fr"""SELECT id, tokentype, consumerid, userforeignkey, key_c, secret, callbackurl, verifier,
                duration, expirationdate, insertdate, thirdpartyapplicationsecret
         FROM token"""

  private type Row = (Long, Option[String], Option[Long], Option[Long], Option[String],
    Option[String], Option[String], Option[String], Option[Long], Option[java.sql.Timestamp],
    Option[java.sql.Timestamp], Option[String])

  private def fromRow(row: Row): Token = row match {
    case (id, tokenType, consumerId, userForeignKey, key, secret, callbackURL, verifier, duration,
          expirationDate, insertDate, thirdPartyApplicationSecret) =>
      Token(id, tokenType.orNull, consumerId, userForeignKey, key.orNull, secret.orNull,
        callbackURL.orNull, verifier.orNull,
        // A NULL number reads back as 0, which is what MappedLong did.
        duration.getOrElse(0L),
        // Dates come back as plain java.util.Date, as MappedDateTime gave.
        expirationDate.map(t => new Date(t.getTime)).orNull,
        insertDate.map(t => new Date(t.getTime)).orNull,
        thirdPartyApplicationSecret.orNull)
  }

  private def query(condition: Fragment): List[Token] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def opt(value: String): Option[String] = Option(value)

  private def ts(value: Date): Option[java.sql.Timestamp] =
    Option(value).map(d => new java.sql.Timestamp(d.getTime))

  private def one(condition: Fragment): Box[Token] =
    query(condition ++ fr"ORDER BY id ASC LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def findByKey(key: String): Box[Token] = one(fr"WHERE key_c = ${opt(key)}")

  def findByKeyAndType(key: String, tokenType: String): Box[Token] =
    one(fr"WHERE key_c = ${opt(key)} AND tokentype = ${opt(tokenType)}")

  def findByPrimaryKey(id: Long): Box[Token] = one(fr"WHERE id = $id")

  def getRequestToken(token: String): Box[Token] =
    findByKeyAndType(token, TokenType.Request.toString)

  /**
   * Tokens of the same consumer and user that outlive the one given.
   *
   * DirectLogin treats only the newest token as valid, and this is how it tells: if anything of
   * the same pair expires later, the token in hand has been superseded.
   */
  def findLaterExpiringForUserAndConsumer(userForeignKey: Option[Long], consumerId: Option[Long],
                                          expirationDate: Date): List[Token] =
    query(fr"""WHERE userforeignkey = $userForeignKey AND consumerid = $consumerId
                 AND expirationdate > ${ts(expirationDate)}""")

  def insert(tokenType: String, consumerId: Option[Long], userForeignKey: Option[Long],
             key: String, secret: String, callbackURL: String, duration: Long,
             expirationDate: Date, insertDate: Date): Token = {
    val id = DoobieUtil.runUpdate(
      sql"""INSERT INTO token
            (tokentype, consumerid, userforeignkey, key_c, secret, callbackurl, verifier,
             duration, expirationdate, insertdate, thirdpartyapplicationsecret)
            VALUES (${opt(tokenType)}, $consumerId, $userForeignKey, ${opt(key)}, ${opt(secret)},
             ${opt(callbackURL)}, '', $duration, ${ts(expirationDate)}, ${ts(insertDate)}, '')"""
        .update.withUniqueGeneratedKeys[Long]("id"))
    Token(id, tokenType, consumerId, userForeignKey, key, secret, callbackURL, "", duration,
      expirationDate, insertDate, "")
  }

  def setUserForeignKey(id: Long, userForeignKey: Long): Boolean =
    DoobieUtil.runUpdate(
      sql"UPDATE token SET userforeignkey = $userForeignKey WHERE id = $id".update.run) > 0

  def setVerifier(id: Long, verifier: String): Boolean =
    DoobieUtil.runUpdate(
      sql"UPDATE token SET verifier = ${opt(verifier)} WHERE id = $id".update.run) > 0

  def setThirdPartyApplicationSecret(id: Long, secret: String): Boolean =
    DoobieUtil.runUpdate(
      sql"UPDATE token SET thirdpartyapplicationsecret = ${opt(secret)} WHERE id = $id"
        .update.run) > 0

  def deleteByPrimaryKey(id: Long): Boolean =
    DoobieUtil.runUpdate(sql"DELETE FROM token WHERE id = $id".update.run) > 0

  /** What the database cleaner sweeps. Mapper deleted row by row; one statement does the same. */
  def deleteExpiredBefore(currentDate: Date): Boolean = {
    DoobieUtil.runUpdate(
      sql"DELETE FROM token WHERE expirationdate < ${ts(currentDate)}".update.run)
    true
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM token".update.run)
    ()
  }
}
