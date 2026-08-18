package code.transactionChallenge

import java.util.Date

import code.api.util.DoobieUtil
import com.openbankproject.commons.model.ChallengeTrait
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA
import com.openbankproject.commons.model.enums.StrongCustomerAuthenticationStatus.SCAStatus
import com.openbankproject.commons.model.enums.{StrongCustomerAuthentication, StrongCustomerAuthenticationStatus}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}

/**
 * The expected answer to one SCA challenge.
 *
 * The optional columns hold "" rather than NULL when absent, because Mapper wrote MappedString's
 * default. Two different readings of that emptiness are visible above the store and both are
 * preserved: consentId and basketId use a bare Option, so an absent value surfaces as Some("");
 * the three PSD2 dynamic-linking fields filter empties out and surface as None.
 *
 * scaMethod and scaStatus stay defs rather than fields. They call withName on the stored string,
 * which throws for the "" that saveChallenge writes when no method was supplied — as defs that
 * only happens if a caller asks for them, which is the existing behaviour. Evaluating them
 * eagerly at construction would instead make every read of every row throw.
 */
case class MappedExpectedChallengeAnswer(
  challengeId: String,
  challengeType: String,
  transactionRequestId: String,
  expectedAnswer: String,
  expectedUserId: String,
  salt: String,
  successful: Boolean,
  private val scaMethodRaw: String,
  private val scaStatusRaw: String,
  private val consentIdRaw: String,
  private val basketIdRaw: String,
  private val authenticationMethodIdRaw: String,
  attemptCounter: Int,
  private val challengePurposeRaw: String,
  private val challengeContextHashRaw: String,
  private val challengeContextStructureRaw: String,
  createdAt: Date
) extends ChallengeTrait {

  override def consentId: Option[String] = Option(consentIdRaw)
  override def basketId: Option[String] = Option(basketIdRaw)
  override def scaMethod: Option[SCA] = Option(StrongCustomerAuthentication.withName(scaMethodRaw))
  override def scaStatus: Option[SCAStatus] = Option(StrongCustomerAuthenticationStatus.withName(scaStatusRaw))
  override def authenticationMethodId: Option[String] = Option(authenticationMethodIdRaw)

  // PSD2 Dynamic Linking
  override def challengePurpose: Option[String] = Option(challengePurposeRaw).filter(_.nonEmpty)
  override def challengeContextHash: Option[String] = Option(challengeContextHashRaw).filter(_.nonEmpty)
  override def challengeContextStructure: Option[String] = Option(challengeContextStructureRaw).filter(_.nonEmpty)
}

object MappedExpectedChallengeAnswer {

  // successful is stored as successful_c: SUCCESSFUL collides with a SQL reserved word.
  private val selectColumns =
    fr"""SELECT challengeid, challengetype, transactionrequestid, expectedanswer, expecteduserid,
                salt, successful_c, scamethod, scastatus, consentid, basketid,
                authenticationmethodid, attemptcounter, challengepurpose, challengecontexthash,
                challengecontextstructure, createdat
         FROM expectedchallengeanswer"""

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[Boolean], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[Int], Option[String], Option[String],
    Option[String], Option[java.sql.Timestamp])

  private def fromRow(row: Row): MappedExpectedChallengeAnswer = row match {
    case (challengeId, challengeType, transactionRequestId, expectedAnswer, expectedUserId, salt,
          successful, scaMethod, scaStatus, consentId, basketId, authenticationMethodId,
          attemptCounter, challengePurpose, challengeContextHash, challengeContextStructure,
          createdAt) =>
      MappedExpectedChallengeAnswer(challengeId.orNull, challengeType.orNull,
        transactionRequestId.orNull, expectedAnswer.orNull, expectedUserId.orNull, salt.orNull,
        successful.getOrElse(false), scaMethod.orNull, scaStatus.orNull, consentId.orNull,
        basketId.orNull, authenticationMethodId.orNull, attemptCounter.getOrElse(0),
        challengePurpose.orNull, challengeContextHash.orNull, challengeContextStructure.orNull,
        createdAt.orNull)
  }

  private def query(condition: Fragment): List[MappedExpectedChallengeAnswer] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  def insert(challengeId: String, challengeType: String, transactionRequestId: String, salt: String,
             expectedAnswer: String, expectedUserId: String, scaMethod: String, scaStatus: String,
             consentId: String, basketId: String, authenticationMethodId: String,
             challengePurpose: String, challengeContextHash: String,
             challengeContextStructure: String): MappedExpectedChallengeAnswer = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO expectedchallengeanswer
            (challengeid, challengetype, transactionrequestid, expectedanswer, expecteduserid, salt,
             successful_c, scamethod, scastatus, consentid, basketid, authenticationmethodid,
             attemptcounter, challengepurpose, challengecontexthash, challengecontextstructure,
             createdat, updatedat)
            VALUES ($challengeId, $challengeType, $transactionRequestId, $expectedAnswer,
             $expectedUserId, $salt, false, $scaMethod, $scaStatus, $consentId, $basketId,
             $authenticationMethodId, 0, $challengePurpose, $challengeContextHash,
             $challengeContextStructure, $now, $now)"""
        .update.run)
    findByChallengeId(challengeId)
      .openOrThrowException("the challenge just inserted must be readable")
  }

  def findByChallengeId(challengeId: String): Box[MappedExpectedChallengeAnswer] =
    query(fr"WHERE challengeid = $challengeId ORDER BY id ASC LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def findAllByTransactionRequestId(transactionRequestId: String): List[MappedExpectedChallengeAnswer] =
    query(fr"WHERE transactionrequestid = $transactionRequestId ORDER BY id ASC")

  def findAllByConsentId(consentId: String): List[MappedExpectedChallengeAnswer] =
    query(fr"WHERE consentid = $consentId ORDER BY id ASC")

  def findAllByBasketId(basketId: String): List[MappedExpectedChallengeAnswer] =
    query(fr"WHERE basketid = $basketId ORDER BY id ASC")

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM expectedchallengeanswer".update.run)
    ()
  }
}
