package code.context

import java.sql.Timestamp
import java.util.Date

import code.api.util.{APIUtil, DoobieUtil, ErrorMessages, SecureRandomUtil}
import code.bankconnectors.DoobieUserAuthContextUpdateQueries
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{UserAuthContextUpdate, UserAuthContextUpdateStatus}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.util.Helpers
import net.liftweb.util.Helpers.tryo

import scala.compat.Platform
import scala.concurrent.Future

/** One user-auth-context-update row, standing in for the Lift entity in return types. */
case class UserAuthContextUpdateRow(
  primaryKey: Long,
  userAuthContextUpdateId: String,
  userId: String,
  consumerId: String,
  key: String,
  value: String,
  challenge: String,
  status: String,
  createdAt: Date
) extends UserAuthContextUpdate

/**
 * Doobie implementation of the user-auth-context-update store, replacing the Lift
 * MappedUserAuthContextUpdate entity. This is the SCA-style challenge/answer flow for updating a
 * user auth context.
 *
 * checkAnswer's status transition already went through DoobieUserAuthContextUpdateQueries
 * (conditionalStatusTransition, an atomic UPDATE ... WHERE mstatus = 'INITIATED') before the rest
 * of this table moved off Mapper - CONCURRENCY_HAZARDS.md hazard H2, exercised by
 * ConcurrentConsentStatusRaceTest. That fix is unchanged here; only the surrounding find/create/
 * delete calls move to Doobie alongside it.
 *
 * createUserAuthContextUpdates does not set challenge explicitly - the Mapper version relied on
 * mChallenge's field default (SecureRandomUtil.csprng.nextInt(99999999).toString(), an up-to-8-
 * digit numeric OTP) firing on an unset field. That default is reproduced explicitly here.
 *
 * Writes go through runUpdate: outside a request scope runQuery's fallback transactor is
 * Strategy.void on a pool with autoCommit off, so the write would be rolled back on return.
 */
object MappedUserAuthContextUpdateProvider extends UserAuthContextUpdateProvider with MdcLoggable {

  private def rowOf(r: (Long, String, String, String, String, String, String, String, Timestamp)): UserAuthContextUpdateRow =
    UserAuthContextUpdateRow(r._1, r._2, r._3, r._4, r._5, r._6, r._7, r._8, new Date(r._9.getTime))

  private val selectCols: Fragment =
    fr"""SELECT id, muserauthcontextupdateid, muserid, mconsumerid, mkey, mvalue, mchallenge, mstatus, createdat
         FROM mappeduserauthcontextupdate"""

  override def createUserAuthContextUpdates(userId: String, consumerId: String, key: String, value: String): Future[Box[UserAuthContextUpdate]] =
    Future {
      val id = APIUtil.generateUUID()
      val challenge = SecureRandomUtil.csprng.nextInt(99999999).toString()
      val status = UserAuthContextUpdateStatus.INITIATED.toString
      val now = new Timestamp(System.currentTimeMillis)
      tryo {
        DoobieUtil.runUpdate(
          sql"""INSERT INTO mappeduserauthcontextupdate
                  (muserauthcontextupdateid, muserid, mconsumerid, mkey, mvalue, mchallenge, mstatus, createdat, updatedat)
                VALUES ($id, $userId, $consumerId, $key, $value, $challenge, $status, $now, $now)"""
            .update.run)
        findByUpdateId(id).getOrElse(
          throw new RuntimeException("createUserAuthContextUpdates: row not found immediately after insert"))
      }
    }

  private def findByUpdateId(userAuthContextUpdateId: String): Option[UserAuthContextUpdateRow] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE muserauthcontextupdateid = $userAuthContextUpdateId LIMIT 1")
        .query[(Long, String, String, String, String, String, String, String, Timestamp)].option
    ).map(rowOf)

  override def getUserAuthContextUpdates(userId: String): Future[Box[List[UserAuthContextUpdate]]] =
    Future(getUserAuthContextUpdatesBox(userId))

  override def getUserAuthContextUpdatesBox(userId: String): Box[List[UserAuthContextUpdate]] =
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE muserid = $userId").query[(Long, String, String, String, String, String, String, String, Timestamp)].to[List]
      ).map(rowOf)
    }

  override def deleteUserAuthContextUpdates(userId: String): Future[Box[Boolean]] =
    Future {
      tryo {
        DoobieUtil.runUpdate(sql"DELETE FROM mappeduserauthcontextupdate WHERE muserid = $userId".update.run)
        true
      }
    }

  override def deleteUserAuthContextUpdateById(userAuthContextId: String): Future[Box[Boolean]] =
    Future {
      findByUpdateId(userAuthContextId) match {
        case Some(_) =>
          tryo {
            DoobieUtil.runUpdate(
              sql"DELETE FROM mappeduserauthcontextupdate WHERE muserauthcontextupdateid = $userAuthContextId".update.run)
            true
          }
        case None => Empty ?~! ErrorMessages.DeleteUserAuthContextNotFound
      }
    }

  override def checkAnswer(consentId: String, challenge: String): Future[Box[UserAuthContextUpdate]] = Future {
    findByUpdateId(consentId) match {
      case Some(consent) => processUacAnswer(consent, challenge, consentId)
      case None           => Empty ?~! ErrorMessages.UserAuthContextUpdateNotFound
    }
  }

  private def processUacAnswer(consent: UserAuthContextUpdateRow, challenge: String, consentId: String): Box[UserAuthContextUpdate] = {
    val expiredDateTime: Long = consent.createdAt.getTime + Helpers.seconds(APIUtil.userAuthContextUpdateRequestChallengeTtl)
    if (expiredDateTime <= Platform.currentTime) {
      Failure(s"${ErrorMessages.OneTimePasswordExpired} Current expiration time is ${APIUtil.userAuthContextUpdateRequestChallengeTtl} seconds")
    } else {
      consent.status match {
        case value if value == UserAuthContextUpdateStatus.INITIATED.toString =>
          val status = if (consent.challenge == challenge) UserAuthContextUpdateStatus.ACCEPTED.toString else UserAuthContextUpdateStatus.REJECTED.toString
          // Atomic guarded transition: only one concurrent answer may move INITIATED -> status,
          // so two correct answers cannot both be accepted (MFA double-authorisation).
          val rows = DoobieUserAuthContextUpdateQueries
            .conditionalStatusTransition(consent.primaryKey, UserAuthContextUpdateStatus.INITIATED.toString, status)
          if (rows == 1) findByUpdateId(consentId).map(r => r: UserAuthContextUpdate).fold[Box[UserAuthContextUpdate]](Empty)(Full(_))
          else Failure(ErrorMessages.UserAuthContextUpdateStatusError)
        case _ =>
          // Already left INITIATED (e.g. a concurrent answer committed before our read).
          // A late second answer must fail like the atomic-transition loser above —
          // returning Full here would allow MFA double-authorisation.
          Failure(ErrorMessages.UserAuthContextUpdateStatusError)
      }
    }
  }
}
