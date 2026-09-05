package code.context

import java.sql.Timestamp
import java.util.Date

import code.api.util.{APIUtil, DoobieUtil, ErrorMessages}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{BasicUserAuthContext, ConsentAuthContext}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future

/** One consent-auth-context row, standing in for the Lift entity in return types. */
case class ConsentAuthContextRow(
  consentAuthContextId: String,
  consentId: String,
  key: String,
  value: String,
  timeStamp: Date
) extends ConsentAuthContext

/**
 * Doobie implementation of the consent-auth-context store, replacing the Lift
 * MappedConsentAuthContext entity.
 *
 * createConsentAuthContext always inserts with no existence check, matching the Mapper version -
 * "developers are encouraged to use name space in the key" rather than rely on one row per key.
 * The unique index is (consentId, key, createdAt): two writes for the same key inside the same
 * millisecond collide, and the second is rejected. That is a real, if narrow, race in the
 * existing design, not something this migration widens or narrows.
 *
 * createOrUpdateConsentAuthContexts fixes a bug in the Mapper version's update branch: a shadowed
 * lambda parameter there (`.map(authContext => authContext.Key(authContext.key)...)`) made the
 * "update" write the found row's own existing key/value back onto itself, so it could never
 * actually change a value once one existed. Nothing exercised that path before the
 * characterization test written for this migration, which is what caught it. This implementation
 * writes the incoming BasicUserAuthContext's key/value, matching the method's documented contract
 * ("creates or replaces").
 *
 * Writes go through runUpdate: outside a request scope runQuery's fallback transactor is
 * Strategy.void on a pool with autoCommit off, so the write would be rolled back on return.
 */
object DoobieConsentAuthContextProvider extends ConsentAuthContextProvider with MdcLoggable {

  private def rowOf(r: (String, String, String, String, Timestamp)): ConsentAuthContextRow =
    ConsentAuthContextRow(r._1, r._2, r._3, r._4, new Date(r._5.getTime))

  private val selectCols =
    fr"SELECT consentauthcontextid, consentid, key_c, value, createdat FROM consentauthcontext"

  override def createConsentAuthContext(consentId: String, key: String, value: String): Future[Box[ConsentAuthContext]] =
    Future { createConsentAuthContextSync(consentId, key, value) }

  private def createConsentAuthContextSync(consentId: String, key: String, value: String): Box[ConsentAuthContext] = {
    val id = APIUtil.generateUUID()
    val now = new Timestamp(System.currentTimeMillis)
    tryo {
      DoobieUtil.runUpdate(
        sql"""INSERT INTO consentauthcontext
                (consentauthcontextid, consentid, key_c, value, createdat, updatedat)
              VALUES ($id, $consentId, $key, $value, $now, $now)"""
          .update.run)
      ConsentAuthContextRow(id, consentId, key, value, new Date(now.getTime))
    }
  }

  override def getConsentAuthContexts(consentId: String): Future[Box[List[ConsentAuthContext]]] =
    Future { getConsentAuthContextsBox(consentId) }

  override def getConsentAuthContextsBox(consentId: String): Box[List[ConsentAuthContext]] =
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE consentid = $consentId")
          .query[(String, String, String, String, Timestamp)].to[List]
      ).map(rowOf)
    }

  private def findOne(consentId: String, key: String): Option[ConsentAuthContextRow] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE consentid = $consentId AND key_c = $key LIMIT 1")
        .query[(String, String, String, String, Timestamp)].option
    ).map(rowOf)

  override def createOrUpdateConsentAuthContexts(
    consentId: String,
    userAuthContexts: List[BasicUserAuthContext]
  ): Box[List[ConsentAuthContext]] = tryo {
    userAuthContexts.distinct.map { incoming =>
      findOne(consentId, incoming.key) match {
        case Some(existing) =>
          DoobieUtil.runUpdate(
            sql"UPDATE consentauthcontext SET value = ${incoming.value} WHERE consentauthcontextid = ${existing.consentAuthContextId}"
              .update.run)
          existing.copy(value = incoming.value)
        case None =>
          createConsentAuthContextSync(consentId, incoming.key, incoming.value)
            .openOrThrowException("createConsentAuthContextSync only fails on a database error")
      }
    }
  }

  override def deleteConsentAuthContexts(consentId: String): Future[Box[Boolean]] =
    Future {
      tryo {
        DoobieUtil.runUpdate(sql"DELETE FROM consentauthcontext WHERE consentid = $consentId".update.run)
        true
      }
    }

  override def deleteConsentAuthContextById(consentAuthContextId: String): Future[Box[Boolean]] =
    Future {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE consentauthcontextid = $consentAuthContextId LIMIT 1")
          .query[(String, String, String, String, Timestamp)].option
      ) match {
        case Some(_) =>
          tryo {
            DoobieUtil.runUpdate(
              sql"DELETE FROM consentauthcontext WHERE consentauthcontextid = $consentAuthContextId".update.run)
            true
          }
        case None => Empty ?~! ErrorMessages.DeleteUserAuthContextNotFound
      }
    }
}
