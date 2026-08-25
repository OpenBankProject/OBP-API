package code.consent

import java.util.Date
import code.api.util.{APIUtil, Consent, DoobieUtil, ConsentJWT, ErrorMessages, JwtUtil, OBPBankId, OBPConsentId, OBPConsumerId, OBPLimit, OBPOffset, OBPQueryParam, OBPSortBy, OBPStatus, OBPUserId, ProviderProviderId, SecureRandomUtil}
import code.consent.ConsentStatus.ConsentStatus
import code.model.Consumer
import code.model.dataAccess.ResourceUser
import com.openbankproject.commons.model.User
import com.openbankproject.commons.util.ApiStandards
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.util.Helpers.{now, tryo}
import org.mindrot.jbcrypt.BCrypt

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import scala.collection.immutable.List

object MappedConsentProvider extends ConsentProvider with code.util.Helper.MdcLoggable {
  override def getConsentByConsentId(consentId: String): Box[MappedConsent] =
    MappedConsent.findByConsentId(consentId)

  override def getConsentByConsentRequestId(consentRequestId: String): Box[MappedConsent] =
    MappedConsent.findByConsentRequestId(consentRequestId)
  
  override def updateConsentStatus(consentId: String, status: ConsentStatus): Box[MappedConsent] = {
    MappedConsent.findByConsentId(consentId) match {
      case Full(consent) =>
        Consent.expireAllPreviousValidBerlinGroupConsents(consent, status)
        //maybe not right, but for the create we use the `now`, we need to update it later.
        tryo(MappedConsent.setStatusAndLastActionDate(consentId, status.toString, now)
          .openOrThrowException(ErrorMessages.ConsentNotFound))
      case Empty =>
        Empty ?~! ErrorMessages.ConsentNotFound
      case Failure(msg, _, _) =>
        Failure(msg)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
  }
  override def updateConsentUser(consentId: String, user: User): Box[MappedConsent] = {
    MappedConsent.findByConsentId(consentId) match {
      case Full(_) =>
        //maybe not right, but for the create we use the `now`, we need to update it later.
        tryo(MappedConsent.setUserIdAndLastActionDate(consentId, user.userId, now)
          .openOrThrowException(ErrorMessages.ConsentNotFound))
      case Empty =>
        Empty ?~! ErrorMessages.ConsentNotFound
      case Failure(msg, _, _) =>
        Failure(msg)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
  }
  override def getConsentsByUser(userId: String): List[MappedConsent] =
    MappedConsent.findAllByUserId(userId)



  private def getPagedConsents(queryParams: List[OBPQueryParam]): (List[MappedConsent], Long) = {
    // Extract pagination params
    val limit = queryParams.collectFirst { case OBPLimit(value) => value }
    val offset = queryParams.collectFirst { case OBPOffset(value) => value }

    // Extract sort params — push sorting to DB. An unknown field is not sorted on at all.
    val orderBy: Option[(String, Boolean)] = queryParams.collectFirst { case OBPSortBy(value) => value }
      .flatMap { sortSpec =>
        val parts = sortSpec.split(":").map(_.trim.toLowerCase)
        val fieldName = parts(0)
        val ascending = !parts.lift(1).contains("desc")
        fieldName match {
          case "created_date" => Some(("createdat", ascending))
          case "status"       => Some(("mstatus", ascending))
          case "consumer_id"  => Some(("mconsumerid", ascending))
          case _              => None
        }
      }

    // Extract filters
    val consumerId = queryParams.collectFirst { case OBPConsumerId(value) => value }
    val consentId = queryParams.collectFirst { case OBPConsentId(value) => value }
    val providerProviderId: Option[String] = queryParams.collectFirst {
      case ProviderProviderId(value) =>
        val (provider, providerId) = value.split("\\|") match {
          case Array(a, b) => (a, b)
          case _ => ("", "")
        }
        // Only an unambiguous match narrows the query; several users on one provider id filters
        // nothing, as it did before.
        ResourceUser.findAllByProviderAndProviderId(provider, providerId) match {
          case x :: Nil => Some(x.userId)
          case _ => None
        }
    }.flatten
    val userId = queryParams.collectFirst { case OBPUserId(value) => value }

    val status = queryParams.collectFirst {
      case OBPStatus(value) =>
        val statuses = value.split(",").toList.map(_.trim)
        // Matched case-insensitively by listing both cases rather than by lowering the column.
        statuses.distinct.flatMap(s => List(s.toLowerCase, s.toUpperCase)).distinct
    }

    val effectiveUserId = userId.orElse(providerProviderId)

    // Total count for pagination (filters only, no limit/offset/orderBy)
    val totalCount = MappedConsent.countPage(status, effectiveUserId, consentId, consumerId)
    val pageData = MappedConsent.findPage(status, effectiveUserId, consentId, consumerId, orderBy,
      limit, offset)

    (pageData, totalCount)
  }


  private def sortConsents(consents: List[MappedConsent], sortByParam: String): List[MappedConsent] = {
    // Parse sort_by param like "created_date:desc,status:asc,consumer_id:asc"
    val sortFields: List[(String, String)] = sortByParam
      .split(",")
      .toList
      .map(_.trim)
      .filter(_.nonEmpty)
      .map { fieldSpec =>
        val parts = fieldSpec.split(":").map(_.trim.toLowerCase)
        val fieldName = parts(0)
        val sortOrder = parts.lift(1).getOrElse("asc") // default to asc
        (fieldName, sortOrder)
      }

    // Apply sorting in reverse order, so first field becomes the last sort (because sortBy is stable)
    sortFields.reverse.foldLeft(consents) { case (acc, (fieldName, sortOrder)) =>
      val ascending = sortOrder != "desc"

      fieldName match {
        case "created_date" =>
          if (ascending)
            acc.sortBy(_.createdAt)
          else
            acc.sortBy(_.createdAt)(Ordering[java.util.Date].reverse)

        case "status" =>
          if (ascending)
            acc.sortBy(_.status)(Ordering[String])
          else
            acc.sortBy(_.status)(Ordering[String].reverse)

        case "consumer_id" =>
          if (ascending)
            acc.sortBy(_.consumerId)(Ordering[String])
          else
            acc.sortBy(_.consumerId)(Ordering[String].reverse)

        case _ =>
          // Unknown field → ignore
          acc
      }
    }
  }


  override def getConsents(queryParams: List[OBPQueryParam]): (List[MappedConsent], Long) = {
    val (consents, totalCount) = getPagedConsents(queryParams)
    val bankId: Option[String] = queryParams.collectFirst { case OBPBankId(value) => value }
    if(bankId.isDefined) {
      (Consent.filterStrictlyByBank(consents, bankId.get), totalCount)
    } else {
      (consents, totalCount)
    }
  }




  override def createObpConsent(user: User, challengeAnswer: String, consentRequestId:Option[String], consumer: Option[Consumer]): Box[MappedConsent] = {
    tryo {
      val salt = BCrypt.gensalt()
      val challengeAnswerHashed = BCrypt.hashpw(challengeAnswer, salt).substring(0, 44)
      MappedConsent.insert(
        userId = user.userId,
        consumerId = consumer.map(_.consumerId).getOrElse(null),
        status = ConsentStatus.INITIATED.toString,
        challenge = challengeAnswerHashed,
        salt = salt,
        consentRequestId = consentRequestId.getOrElse(null),
        recurringIndicator = true,
        frequencyPerDay = 100,
        usesSoFarTodayCounter = 0,
        usesSoFarTodayCounterUpdatedAt = new Date(),
        //maybe not right, but for the create we use the `now`, we need to update it later.
        lastActionDate = now,
        apiStandard = ApiStandards.obp.toString)
    }
  }
  override def createBerlinGroupConsent(
    user: Option[User],
    consumer: Option[Consumer],
    recurringIndicator: Boolean,
    validUntil: Date,
    frequencyPerDay: Int,
    combinedServiceIndicator: Boolean,
    apiStandard: Option[String],
    apiVersion: Option[String]): Box[MappedConsent] ={
    tryo {
      MappedConsent.insert(
        userId = user.map(_.userId).getOrElse(null),
        consumerId = consumer.map(_.consumerId).getOrElse(null),
        status = ConsentStatus.received.toString,
        recurringIndicator = recurringIndicator,
        validUntil = validUntil,
        frequencyPerDay = frequencyPerDay,
        usesSoFarTodayCounter = 0,
        usesSoFarTodayCounterUpdatedAt = new Date(),
        combinedServiceIndicator = combinedServiceIndicator,
        //maybe not right, but for the create we use the `now`, we need to update it later.
        lastActionDate = now,
        apiVersion = apiVersion.getOrElse(null),
        apiStandard = apiStandard.getOrElse(null))
    }}
  override def updateBerlinGroupConsent(consentId: String,
                                        usesSoFarTodayCounter: Int) ={
    MappedConsent.findByConsentId(consentId) match {
      case Full(_) =>
        tryo(MappedConsent.setUsesSoFarToday(consentId, usesSoFarTodayCounter, now)
          .openOrThrowException(ErrorMessages.ConsentNotFound))
      case Empty =>
        Empty ?~! ErrorMessages.ConsentNotFound
      case Failure(msg, _, _) =>
        Failure(msg)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
  }

  override def saveUKConsent(
    user: Option[User],
    bankId: Option[String],//for UK Open Banking endpoints, there is no BankId there.
    accountIds: Option[List[String]],//for UK Open Banking endpoints, there is no accountIds there.
    consumerId: Option[String],
    permissions: List[String],
    expirationDateTime: Option[Date],
    transactionFromDateTime: Option[Date],
    transactionToDateTime: Option[Date],
    apiStandard: Option[String],
    apiVersion: Option[String]
  ): net.liftweb.common.Box[code.consent.MappedConsent] ={
    tryo {
      val consent = MappedConsent.insert(
        userId = user.map(_.userId).getOrElse(null),
        consumerId = consumerId.getOrElse(null),
        status = ConsentStatus.AWAITINGAUTHORISATION.toString,
        expirationDateTime = expirationDateTime.orNull,
        transactionFromDateTime = transactionFromDateTime.orNull,
        transactionToDateTime = transactionToDateTime.orNull,
        statusUpdateDateTime = now,
        apiVersion = apiVersion.getOrElse(null),
        apiStandard = apiStandard.getOrElse(null))
      val jwt = Consent.createUKConsentJWT(
        user: Option[User],
        bankId: Option[String],
        accountIds: Option[List[String]],
        permissions: List[String],
        expirationDateTime: Option[Date],
        transactionFromDateTime: Option[Date],
        transactionToDateTime: Option[Date],
        secret = consent.secret,
        consentId = consent.consentId,
        consumerId: Option[String]
      )
      setJsonWebToken(consent.consentId, jwt).head
    }
  }
  override def setJsonWebToken(consentId: String, jwt: String): Box[MappedConsent] = {
    MappedConsent.findByConsentId(consentId) match {
      case Full(_) =>
        val payload = JwtUtil.getSignedPayloadAsJson(jwt).openOr(null)
        // Parse JWT payload to denormalise exp and consent items
        val consentJWTParsed: Option[ConsentJWT] = if (payload != null) {
          try {
            import org.json4s._
            import com.openbankproject.commons.util.JsonAliases._
            implicit val formats: DefaultFormats.type = DefaultFormats
            Some(parse(payload).extract[ConsentJWT])
          } catch {
            case e: Exception =>
              logger.error(s"setJsonWebToken says: Failed to parse JWT payload for consent $consentId: ${e.getMessage}")
              None
          }
        } else None

        // Set jwt_expires_at from the JWT exp claim
        val jwtExpiresAt = consentJWTParsed.map(parsed => new Date(parsed.exp * 1000L))

        val result = tryo(MappedConsent.setJsonWebToken(consentId, jwt, payload, jwtExpiresAt)
          .openOrThrowException(ErrorMessages.ConsentNotFound))

        // Denormalise bank_id, account_id, view_id and role_name from the JWT into consent_item
        // so that bank-scoped queries can use an indexed SQL join instead of extracting every JWT.
        result.foreach { savedConsent =>
          try {
            consentJWTParsed.foreach { consentJWT =>
              DoobieConsentQueries.insertConsentItems(savedConsent.consentReferenceId, consentJWT)
            }
          } catch {
            case e: Exception =>
              logger.error(s"setJsonWebToken says: Failed to populate consent_item for consent $consentId: ${e.getMessage}")
          }
        }
        result
      case Empty =>
        Empty ?~! ErrorMessages.ConsentNotFound
      case Failure(msg, _, _) =>
        Failure(msg)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
  }
  override def setValidUntil(consentId: String, validUntil: Date): Box[MappedConsent] = {
    MappedConsent.findByConsentId(consentId) match {
      case Full(_) =>
        tryo(MappedConsent.setValidUntil(consentId, validUntil)
          .openOrThrowException(ErrorMessages.ConsentNotFound))
      case Empty =>
        Empty ?~! ErrorMessages.ConsentNotFound
      case Failure(msg, _, _) =>
        Failure(msg)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
  }
  override def revoke(consentId: String): Box[MappedConsent] = {
    MappedConsent.findByConsentId(consentId) match {
      case Full(consent) if consent.status == ConsentStatus.REVOKED.toString =>
        Failure(ErrorMessages.ConsentAlreadyRevoked)
      case Full(consent) =>
        // Atomic guarded revoke: UPDATE ... WHERE mstatus <> 'REVOKED'. A concurrent request that
        // already revoked makes this a 0-row no-op, so we never resurrect or double-revoke.
        val rows = code.bankconnectors.DoobieConsentStatusQueries
          .conditionalRevoke(consent.consentPrimaryKey, ConsentStatus.REVOKED.toString)
        if (rows == 1) {
          // Every revoke endpoint funnels through here, so this is the one place that has to give
          // the granted access back. The status flip alone leaves the AccountAccess rows live in
          // the table for anything that reads them without asking the consent first.
          //
          // Guarded because conditionalRevoke above has already committed, so a throw here cannot
          // undo the revoke -- it can only hide that it happened. The caller got a 500 and the
          // retry got ConsentAlreadyRevoked, which leaves an AISP unable to complete the DELETE the
          // standards require of it and with no correct next step. It does not take much to get
          // there: revokeConsentAccountAccess extracts the stored JWT through Box.map, which does
          // not catch, so a payload that no longer matches ConsentJWT arrives as a MappingException.
          //
          // Logged at error, not swallowed. The consent IS revoked and the API is right to say so,
          // but rows that should have been dropped are still live, and that is the one thing about
          // a revoked consent nobody comes back for. Same shape as the sibling below.
          tryo(code.api.util.Consent.revokeConsentAccountAccess(consent)) match {
            case Failure(msg, ex, _) =>
              logger.error(
                s"revoke: consent $consentId is revoked, but its account access could not be given " +
                s"back and those rows are still live: $msg" +
                ex.map(e => s" (${e.getClass.getSimpleName}: ${e.getMessage})").getOrElse(""))
            case _ =>
          }
          MappedConsent.findByConsentId(consentId)
        }
        else Failure(ErrorMessages.ConsentAlreadyRevoked)
      case Empty =>
        Empty ?~! ErrorMessages.ConsentNotFound
      case Failure(msg, _, _) =>
        Failure(msg)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
  }
  override def revokeBerlinGroupConsent(consentId: String): Box[MappedConsent] = {
    MappedConsent.findByConsentId(consentId) match {
      case Full(consent) if consent.status == ConsentStatus.terminatedByTpp.toString =>
        Failure(ErrorMessages.ConsentAlreadyRevoked)
      case Full(consent) =>
        tryo {
          val terminated = MappedConsent
            .setStatusAndLastActionDate(consentId, ConsentStatus.terminatedByTpp.toString, now)
            .openOrThrowException(ErrorMessages.ConsentNotFound)
          code.api.util.Consent.revokeConsentAccountAccess(terminated)
          terminated
        }
      case Empty =>
        Empty ?~! ErrorMessages.ConsentNotFound
      case Failure(msg, _, _) =>
        Failure(msg)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
  }
  /**
   * Whether the SCA challenge answer must actually be verified.
   *
   * `consents.sca.enabled=false` makes checkAnswer accept any answer - which is the point of the
   * switch, for local development where nobody can receive an OTP. What it must not do is apply in
   * production: there, "SCA off" means anyone who reaches the endpoint with a consent id in
   * INITIATED state can move it to ACCEPTED with an arbitrary string, and the only thing that ever
   * said so was a boot-time warning nobody has to read.
   *
   * So the switch keeps working exactly as before outside production, and is ignored in it. Split
   * out as a pure function because run mode cannot be changed from a test, so this is the only
   * seam the decision can be asserted through - see ConsentScaEnforcementTest.
   */
  private[consent] def scaVerificationRequired(scaEnabledProp: Boolean, isProduction: Boolean): Boolean =
    isProduction || scaEnabledProp

  override def checkAnswer(consentId: String, challengeAnswer: String): Box[MappedConsent] = {
    def isAnswerCorrect(expectedAnswerHashed: String, answer: String, salt: String) = {
      val challengeAnswerHashed = BCrypt.hashpw(answer, salt).substring(0, 44)
      val scaEnabled = APIUtil.getPropsAsBoolValue("consents.sca.enabled", true)
      val isProduction = net.liftweb.util.Props.mode == net.liftweb.util.Props.RunModes.Production
      if (scaVerificationRequired(scaEnabled, isProduction)) {
        expectedAnswerHashed == challengeAnswerHashed
      } else {
        true
      }
    }
    MappedConsent.findByConsentId(consentId) match {
      case Full(consent) =>
        consent.status match {
          case value if value == ConsentStatus.INITIATED.toString =>
            val status =
              if (isAnswerCorrect(consent.challenge, challengeAnswer, consent.salt)) ConsentStatus.ACCEPTED.toString
              else ConsentStatus.REJECTED.toString
            // Atomic guarded transition: only one concurrent answer may move INITIATED -> status.
            // The loser (0 rows) gets a Failure rather than a second "success" that would let two
            // callers proceed past the SCA gate on one consent.
            val rows = code.bankconnectors.DoobieConsentStatusQueries
              .conditionalStatusTransition(consent.consentPrimaryKey, ConsentStatus.INITIATED.toString, status)
            if (rows == 1) MappedConsent.findByConsentId(consentId)
            else Failure(ErrorMessages.ConsentUpdateStatusError)
          case _ =>
            // Already left INITIATED (e.g. a concurrent answer committed before our read).
            // A late second answer must fail like the atomic-transition loser above —
            // returning Full here would let two callers pass the SCA gate on one consent.
            Failure(ErrorMessages.ConsentUpdateStatusError)
        }
      case Empty =>
        Empty ?~! ErrorMessages.ConsentNotFound
      case Failure(msg, _, _) =>
        Failure(msg)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
    
  }
}

/**
 * One consent: the authority a user granted, and the state machine it moves through.
 *
 * `consentPrimaryKey` is the surrogate key. It stays on the row because the atomic status
 * transitions (DoobieConsentStatusQueries) address a consent by it, and `consentReferenceId` is the
 * stable external id that the consent_item rows point at.
 *
 * `challenge` is the SCA answer hashed with BCrypt using `salt`, never the answer.
 */
case class MappedConsent(
  consentPrimaryKey: Long,
  consentId: String,
  userId: String,
  secret: String,
  status: String,
  challenge: String,
  salt: String,
  jsonWebToken: String,
  jsonWebTokenPayload: String,
  jwtExpiresAt: Date,
  consumerId: String,
  consentRequestId: String,
  apiStandard: String,
  apiVersion: String,
  recurringIndicator: Boolean,
  validUntil: Date,
  frequencyPerDay: Int,
  usesSoFarTodayCounter: Int,
  usesSoFarTodayCounterUpdatedAt: Date,
  combinedServiceIndicator: Boolean,
  lastActionDate: Date,
  expirationDateTime: Date,
  transactionFromDateTime: Date,
  transactionToDateTime: Date,
  statusUpdateDateTime: Date,
  note: String,
  consentReferenceId: String,
  createdAt: Date,
  updatedAt: Date
) extends ConsentTrait {
  override def creationDateTime: Date = createdAt
}

object MappedConsent {

  private val selectColumns =
    fr"""SELECT id, mconsentid, muserid, msecret, mstatus, mchallenge, msalt, mjsonwebtoken,
                mjsonwebtokenpayload, jwt_expires_at, mconsumerid, mconsentrequestid, mapistandard,
                mapiversion, mrecurringindicator, mvaliduntil, mfrequencyperday,
                musessofartodaycounter, musessofartodaycounterupdatedat, mcombinedserviceindicator,
                mlastactiondate, mexpirationdatetime, mtransactionfromdatetime,
                mtransactiontodatetime, mstatusupdatedatetime, mnote, consent_reference_id,
                createdat, updatedat
         FROM mappedconsent"""

  // 29 columns, past the 22-element tuple limit, so the row is read as two nested tuples.
  private type RowHead = (Long, Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[String], Option[java.sql.Timestamp],
    Option[String], Option[String], Option[String], Option[String], Option[Boolean])
  private type RowTail = (Option[java.sql.Date], Option[Int], Option[Int],
    Option[java.sql.Timestamp], Option[Boolean], Option[java.sql.Date], Option[java.sql.Timestamp],
    Option[java.sql.Timestamp], Option[java.sql.Timestamp], Option[java.sql.Timestamp],
    Option[String], Option[String], Option[java.sql.Timestamp], Option[java.sql.Timestamp])
  private type Row = (RowHead, RowTail)

  /** Dates come back as plain java.util.Date, which is what MappedDate and MappedDateTime gave. */
  private def readTimestamp(value: Option[java.sql.Timestamp]): Date =
    value.map(t => new Date(t.getTime)).orNull
  private def readDate(value: Option[java.sql.Date]): Date =
    value.map(d => new Date(d.getTime)).orNull

  private def fromRow(row: Row): MappedConsent = row match {
    case ((id, consentId, userId, secret, status, challenge, salt, jsonWebToken,
           jsonWebTokenPayload, jwtExpiresAt, consumerId, consentRequestId, apiStandard, apiVersion,
           recurringIndicator),
          (validUntil, frequencyPerDay, usesSoFarTodayCounter, usesSoFarTodayCounterUpdatedAt,
           combinedServiceIndicator, lastActionDate, expirationDateTime, transactionFromDateTime,
           transactionToDateTime, statusUpdateDateTime, note, consentReferenceId, createdAt,
           updatedAt)) =>
      MappedConsent(id, consentId.orNull, userId.orNull, secret.orNull, status.orNull,
        challenge.orNull, salt.orNull, jsonWebToken.orNull, jsonWebTokenPayload.orNull,
        readTimestamp(jwtExpiresAt), consumerId.orNull, consentRequestId.orNull,
        apiStandard.orNull, apiVersion.orNull,
        // A NULL flag or count reads back as the field default, which is what Mapper did.
        recurringIndicator.getOrElse(false), readDate(validUntil), frequencyPerDay.getOrElse(0),
        usesSoFarTodayCounter.getOrElse(0), readTimestamp(usesSoFarTodayCounterUpdatedAt),
        combinedServiceIndicator.getOrElse(false), readDate(lastActionDate),
        readTimestamp(expirationDateTime), readTimestamp(transactionFromDateTime),
        readTimestamp(transactionToDateTime), readTimestamp(statusUpdateDateTime), note.orNull,
        consentReferenceId.orNull, readTimestamp(createdAt), readTimestamp(updatedAt))
  }

  private def query(condition: Fragment): List[MappedConsent] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def opt(value: String): Option[String] = Option(value)

  private def timestamp(value: Date): Option[java.sql.Timestamp] =
    Option(value).map(d => new java.sql.Timestamp(d.getTime))

  private def date(value: Date): Option[java.sql.Date] =
    Option(value).map(d => new java.sql.Date(d.getTime))

  private def one(condition: Fragment): Box[MappedConsent] =
    query(condition ++ fr"ORDER BY id ASC LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def findByConsentId(consentId: String): Box[MappedConsent] =
    one(fr"WHERE mconsentid = ${opt(consentId)}")

  def findByConsentRequestId(consentRequestId: String): Box[MappedConsent] =
    one(fr"WHERE mconsentrequestid = ${opt(consentRequestId)}")

  def findByConsentReferenceId(consentReferenceId: String): Box[MappedConsent] =
    one(fr"WHERE consent_reference_id = ${opt(consentReferenceId)}")

  def findAllByUserId(userId: String): List[MappedConsent] =
    query(fr"WHERE muserid = ${opt(userId)}")

  def findAllByStatus(status: String): List[MappedConsent] =
    query(fr"WHERE mstatus = ${opt(status)}")

  def findAllByUserIdAndStatuses(userId: String, statuses: List[String]): List[MappedConsent] =
    if (statuses.isEmpty) Nil
    else {
      val in = Fragments.in(fr"mstatus", cats.data.NonEmptyList.fromListUnsafe(statuses.distinct))
      query(fr"WHERE muserid = ${opt(userId)} AND " ++ in)
    }

  /**
   * Consents that are still live but have passed their validity date, for the expiry sweep.
   *
   * `validUntil` is a calendar date, so "expired" means strictly before today rather than before
   * this instant.
   */
  def findAllExpiredWithStatuses(statuses: List[String], today: Date): List[MappedConsent] =
    if (statuses.isEmpty) Nil
    else {
      val in = Fragments.in(fr"mstatus", cats.data.NonEmptyList.fromListUnsafe(statuses.distinct))
      query(fr"WHERE " ++ in ++ fr"AND mvaliduntil IS NOT NULL AND mvaliduntil < ${date(today)}")
    }

  def findAll(): List[MappedConsent] = query(Fragment.empty)

  /**
   * The other live recurring consents one PSU granted one TPP under a standard.
   *
   * Berlin Group allows a PSU only one valid recurring consent per TPP, so granting a new one
   * terminates the rest.
   */
  def findAllRecurringValidForPsuAndTpp(apiStandard: String, status: String, userId: String,
                                        consumerId: String): List[MappedConsent] =
    query(fr"""WHERE mapistandard = ${opt(apiStandard)} AND mrecurringindicator = true
                 AND mstatus = ${opt(status)} AND muserid = ${opt(userId)}
                 AND mconsumerid = ${opt(consumerId)}""")

  /**
   * Consents in one status under one standard, optionally narrowed by a deadline column that has
   * already passed. `NULL` never matches, so a consent with no deadline is never selected - which
   * is what makes an open-ended UK consent perpetual.
   */
  def findAllByStatusAndStandard(status: String, apiStandard: String,
                                 expiredColumn: Option[String] = None,
                                 before: Option[Date] = None): List[MappedConsent] = {
    val deadline = (expiredColumn, before) match {
      case (Some(column), Some(when)) =>
        fr"AND" ++ Fragment.const(column) ++ fr"< ${timestamp(when)}"
      case _ => Fragment.empty
    }
    query(fr"WHERE mstatus = ${opt(status)} AND mapistandard = ${opt(apiStandard)}" ++ deadline)
  }

  /** Consents in one status under one standard left untouched since a cut-off. */
  def findAllByStatusAndStandardNotUpdatedSince(status: String, apiStandard: String,
                                                updatedBefore: Date): List[MappedConsent] =
    query(fr"""WHERE mstatus = ${opt(status)} AND mapistandard = ${opt(apiStandard)}
                 AND updatedat < ${timestamp(updatedBefore)}""")

  /** Consents with a JWT but no decoded payload yet - what the payload backfill repairs. */
  def findAllWithJwtButNoPayload(): List[MappedConsent] =
    query(fr"WHERE mjsonwebtokenpayload IS NULL AND mjsonwebtoken > ''")

  def setJsonWebTokenPayload(consentId: String, payload: String): Box[MappedConsent] =
    update(consentId, List(fr"mjsonwebtokenpayload = ${opt(payload)}"))

  def setConsentReferenceId(consentPrimaryKey: Long, consentReferenceId: String): Boolean =
    DoobieUtil.runUpdate(
      sql"""UPDATE mappedconsent SET consent_reference_id = ${opt(consentReferenceId)},
              updatedat = ${new java.sql.Timestamp(System.currentTimeMillis())}
            WHERE id = $consentPrimaryKey"""
        .update.run) > 0

  /** Terminates a consent and records why in its note. */
  def terminate(consentId: String, status: String, note: String,
                lastActionDate: Date): Box[MappedConsent] =
    update(consentId, List(fr"mstatus = ${opt(status)}", fr"mnote = ${opt(note)}",
      fr"mlastactiondate = ${date(lastActionDate)}"))

  def count(): Long =
    DoobieUtil.runQuery(fr"SELECT COUNT(*) FROM mappedconsent".query[Long].unique)

  /** The filters a consent listing carries, applied to both the page and its total count. */
  private def listingFilters(status: Option[List[String]], userId: Option[String],
                             consentId: Option[String],
                             consumerId: Option[String]): Fragment = {
    val filters = List(
      status.map(values =>
        // An empty status list matched nothing rather than everything, as Mapper's ByList did.
        if (values.isEmpty) fr"0 = 1"
        else Fragments.in(fr"mstatus", cats.data.NonEmptyList.fromListUnsafe(values.distinct))),
      userId.map(value => fr"muserid = ${opt(value)}"),
      consentId.map(value => fr"mconsentid = ${opt(value)}"),
      consumerId.map(value => fr"mconsumerid = ${opt(value)}")
    ).flatten
    if (filters.isEmpty) Fragment.empty
    else fr"WHERE " ++ filters.reduce((a, b) => a ++ fr"AND" ++ b)
  }

  def findPage(status: Option[List[String]], userId: Option[String], consentId: Option[String],
               consumerId: Option[String], orderBy: Option[(String, Boolean)], limit: Option[Int],
               offset: Option[Int]): List[MappedConsent] = {
    val ordering = orderBy match {
      case Some((column, ascending)) =>
        fr"ORDER BY " ++ Fragment.const(column) ++ (if (ascending) fr"ASC" else fr"DESC")
      case None => Fragment.empty
    }
    val paging =
      limit.map(value => fr"LIMIT $value").getOrElse(Fragment.empty) ++
        offset.map(value => fr"OFFSET $value").getOrElse(Fragment.empty)
    query(listingFilters(status, userId, consentId, consumerId) ++ ordering ++ paging)
  }

  def countPage(status: Option[List[String]], userId: Option[String], consentId: Option[String],
                consumerId: Option[String]): Long =
    DoobieUtil.runQuery(
      (fr"SELECT COUNT(*) FROM mappedconsent" ++
        listingFilters(status, userId, consentId, consumerId)).query[Long].unique)

  /**
   * Writes a consent.
   *
   * consentId, secret and consentReferenceId are generated here, and the challenge and salt default
   * the way the entity's fields did, so a caller that does not supply them still gets a usable
   * consent rather than empty columns.
   */
  def insert(userId: String,
             consumerId: String,
             status: String,
             challenge: String = SecureRandomUtil.csprng.nextInt(99999999).toString(),
             salt: String = BCrypt.gensalt(),
             consentRequestId: String = null,
             apiStandard: String = null,
             apiVersion: String = null,
             recurringIndicator: Boolean = false,
             validUntil: Date = null,
             frequencyPerDay: Int = 0,
             usesSoFarTodayCounter: Int = 0,
             usesSoFarTodayCounterUpdatedAt: Date = null,
             combinedServiceIndicator: Boolean = false,
             lastActionDate: Date = null,
             expirationDateTime: Date = null,
             transactionFromDateTime: Date = null,
             transactionToDateTime: Date = null,
             statusUpdateDateTime: Date = null): MappedConsent = {
    val consentId = APIUtil.generateUUID()
    val secret = APIUtil.generateUUID()
    val consentReferenceId = APIUtil.generateUUID()
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO mappedconsent
            (mconsentid, muserid, msecret, mstatus, mchallenge, msalt, mjsonwebtoken,
             mjsonwebtokenpayload, mconsumerid, mconsentrequestid, mapistandard, mapiversion,
             mrecurringindicator, mvaliduntil, mfrequencyperday, musessofartodaycounter,
             musessofartodaycounterupdatedat, mcombinedserviceindicator, mlastactiondate,
             mexpirationdatetime, mtransactionfromdatetime, mtransactiontodatetime,
             mstatusupdatedatetime, mnote, consent_reference_id, createdat, updatedat)
            VALUES ($consentId, ${opt(userId)}, $secret, ${opt(status)}, ${opt(challenge)},
             ${opt(salt)}, '', '', ${opt(consumerId)}, ${opt(consentRequestId)},
             ${opt(apiStandard)}, ${opt(apiVersion)}, $recurringIndicator, ${date(validUntil)},
             $frequencyPerDay, $usesSoFarTodayCounter, ${timestamp(usesSoFarTodayCounterUpdatedAt)},
             $combinedServiceIndicator, ${date(lastActionDate)}, ${timestamp(expirationDateTime)},
             ${timestamp(transactionFromDateTime)}, ${timestamp(transactionToDateTime)},
             ${timestamp(statusUpdateDateTime)}, '', $consentReferenceId, $now, $now)"""
        .update.run)
    findByConsentId(consentId)
      .openOrThrowException("the consent just created must be readable")
  }

  /**
   * Writes a consent under a consent id the caller chose.
   *
   * Only tests need this - every production path takes the generated id from insert - but a test
   * that has to reference the consent by a known id would otherwise have to read it back first.
   */
  def insertWithConsentId(consentId: String, userId: String = "", consumerId: String = "",
                          status: String = "", apiStandard: String = null,
                          validUntil: Date = null, statusUpdateDateTime: Date = null,
                          challenge: String = SecureRandomUtil.csprng.nextInt(99999999).toString(),
                          salt: String = BCrypt.gensalt()): MappedConsent = {
    val secret = APIUtil.generateUUID()
    val consentReferenceId = APIUtil.generateUUID()
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO mappedconsent
            (mconsentid, muserid, msecret, mstatus, mchallenge, msalt, mjsonwebtoken,
             mjsonwebtokenpayload, mconsumerid, mapistandard, mrecurringindicator, mvaliduntil,
             mfrequencyperday, musessofartodaycounter, mcombinedserviceindicator,
             mstatusupdatedatetime, mnote, consent_reference_id, createdat, updatedat)
            VALUES (${opt(consentId)}, ${opt(userId)}, $secret, ${opt(status)},
             ${opt(challenge)}, ${opt(salt)}, '', '',
             ${opt(consumerId)}, ${opt(apiStandard)}, false, ${date(validUntil)}, 0, 0, false,
             ${timestamp(statusUpdateDateTime)}, '', $consentReferenceId, $now, $now)"""
        .update.run)
    findByConsentId(consentId)
      .openOrThrowException("the consent just created must be readable")
  }

  def setStatusAndStatusUpdateDateTime(consentId: String, status: String,
                                       statusUpdateDateTime: Date): Box[MappedConsent] =
    update(consentId, List(fr"mstatus = ${opt(status)}",
      fr"mstatusupdatedatetime = ${timestamp(statusUpdateDateTime)}"))

  private def update(consentId: String, sets: List[Fragment]): Box[MappedConsent] = {
    val stamp = fr"updatedat = ${new java.sql.Timestamp(System.currentTimeMillis())}"
    val assignments = (sets :+ stamp).reduce((a, b) => a ++ fr"," ++ b)
    DoobieUtil.runUpdate(
      (fr"UPDATE mappedconsent SET" ++ assignments ++
        fr"WHERE mconsentid = ${opt(consentId)}").update.run)
    findByConsentId(consentId)
  }

  def setStatusAndLastActionDate(consentId: String, status: String,
                                 lastActionDate: Date): Box[MappedConsent] =
    update(consentId, List(fr"mstatus = ${opt(status)}",
      fr"mlastactiondate = ${date(lastActionDate)}"))

  def setUserIdAndLastActionDate(consentId: String, userId: String,
                                 lastActionDate: Date): Box[MappedConsent] =
    update(consentId, List(fr"muserid = ${opt(userId)}",
      fr"mlastactiondate = ${date(lastActionDate)}"))

  def setUsesSoFarToday(consentId: String, usesSoFarTodayCounter: Int,
                        updatedAt: Date): Box[MappedConsent] =
    update(consentId, List(fr"musessofartodaycounter = $usesSoFarTodayCounter",
      fr"musessofartodaycounterupdatedat = ${timestamp(updatedAt)}"))

  def setJsonWebToken(consentId: String, jwt: String, payload: String,
                      jwtExpiresAt: Option[Date]): Box[MappedConsent] =
    update(consentId, List(fr"mjsonwebtoken = ${opt(jwt)}",
      fr"mjsonwebtokenpayload = ${opt(payload)}") ++
      jwtExpiresAt.map(value => fr"jwt_expires_at = ${timestamp(value)}").toList)

  def setValidUntil(consentId: String, validUntil: Date): Box[MappedConsent] =
    update(consentId, List(fr"mvaliduntil = ${date(validUntil)}"))

  def setStatus(consentId: String, status: String): Box[MappedConsent] =
    update(consentId, List(fr"mstatus = ${opt(status)}"))

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedconsent".update.run)
    ()
  }
}
