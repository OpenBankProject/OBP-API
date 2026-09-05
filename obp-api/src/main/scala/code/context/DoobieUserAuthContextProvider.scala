package code.context

import java.sql.Timestamp
import java.util.Date

import code.api.util.ErrorMessages.CreateUserAuthContextError
import code.api.util.{APIUtil, DoobieUtil, ErrorMessages}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{BasicUserAuthContext, UserAuthContext}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future

/** One user-auth-context row, standing in for the Lift entity in return types. */
case class UserAuthContextRow(
  userAuthContextId: String,
  userId: String,
  key: String,
  value: String,
  consumerId: String,
  timeStamp: Date
) extends UserAuthContext

/**
 * Doobie implementation of the user-auth-context store, replacing the Lift MappedUserAuthContext
 * entity. Sibling of DoobieConsentAuthContextProvider, and the same two things carry over from
 * there:
 *
 *  - createUserAuthContext always inserts with no existence check - duplicate (userId, key) pairs
 *    are intentional, callers are expected to namespace their keys. The unique index is (userId,
 *    key, createdAt), so two writes for the same key inside the same millisecond collide; that is
 *    a real, narrow race in the existing design, not something this migration changes.
 *  - createOrUpdateUserAuthContexts fixes the same shadowed-lambda bug as the consent-auth-context
 *    provider had: the Mapper version's update branch -
 *    `.map(authContext => authContext.mKey(authContext.key).mValue(authContext.value).saveMe())`
 *    - saved the found row's own existing key/value back onto itself, so an update through this
 *    path never actually changed a value once one existed for that (userId, key). This is used
 *    from the login flow in AuthUser (external/SSO auth contexts) and from ConsentUtil, so the
 *    practical effect was that auth context values set once never refreshed on a later login or
 *    consent flow. This implementation writes the incoming BasicUserAuthContext's key/value.
 *
 * createUserAuthContext keeps its consumerId requirement, including the exact Mapper wording -
 * a blank or null consumerId throws CreateUserAuthContextError, which tryo turns into a Failure
 * Box, not a thrown exception that escapes the caller.
 *
 * Writes go through runUpdate: outside a request scope runQuery's fallback transactor is
 * Strategy.void on a pool with autoCommit off, so the write would be rolled back on return.
 */
object DoobieUserAuthContextProvider extends UserAuthContextProvider with MdcLoggable {

  private def rowOf(r: (String, String, String, String, String, Timestamp)): UserAuthContextRow =
    UserAuthContextRow(r._1, r._2, r._3, r._4, r._5, new Date(r._6.getTime))

  private val selectCols =
    fr"SELECT muserauthcontextid, muserid, mkey, mvalue, mconsumerid, createdat FROM mappeduserauthcontext"

  override def createUserAuthContext(userId: String, key: String, value: String, consumerId: String): Future[Box[UserAuthContext]] =
    Future { createUserAuthContextSync(userId, key, value, consumerId) }

  private def createUserAuthContextSync(userId: String, key: String, value: String, consumerId: String): Box[UserAuthContext] =
    tryo {
      if (consumerId == null || consumerId.isEmpty) {
        throw new RuntimeException(s"$CreateUserAuthContextError current consumerId is empty here.")
      }
      val id = APIUtil.generateUUID()
      val now = new Timestamp(System.currentTimeMillis)
      DoobieUtil.runUpdate(
        sql"""INSERT INTO mappeduserauthcontext
                (muserauthcontextid, muserid, mkey, mvalue, mconsumerid, createdat, updatedat)
              VALUES ($id, $userId, $key, $value, $consumerId, $now, $now)"""
          .update.run)
      UserAuthContextRow(id, userId, key, value, consumerId, new Date(now.getTime))
    }

  override def getUserAuthContexts(userId: String): Future[Box[List[UserAuthContext]]] =
    Future { getUserAuthContextsBox(userId) }

  override def getUserAuthContextsBox(userId: String): Box[List[UserAuthContext]] =
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE muserid = $userId")
          .query[(String, String, String, String, String, Timestamp)].to[List]
      ).map(rowOf)
    }

  private def findOne(userId: String, key: String): Option[UserAuthContextRow] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE muserid = $userId AND mkey = $key LIMIT 1")
        .query[(String, String, String, String, String, Timestamp)].option
    ).map(rowOf)

  override def createOrUpdateUserAuthContexts(
    userId: String,
    userAuthContexts: List[BasicUserAuthContext]
  ): Box[List[UserAuthContext]] = tryo {
    userAuthContexts.distinct.map { incoming =>
      findOne(userId, incoming.key) match {
        case Some(existing) =>
          DoobieUtil.runUpdate(
            sql"UPDATE mappeduserauthcontext SET mvalue = ${incoming.value} WHERE muserauthcontextid = ${existing.userAuthContextId}"
              .update.run)
          existing.copy(value = incoming.value)
        case None =>
          // Deliberately not createUserAuthContextSync: that enforces a non-blank consumerId,
          // but the Mapper version's create branch here calls MappedUserAuthContext.create
          // directly - mUserId/mKey/mValue only, no .mConsumerId(...) - bypassing that check
          // entirely. createOrUpdateUserAuthContexts has no consumerId parameter to pass one
          // even if it wanted to, so a row created through this path has always had a blank one.
          val id = APIUtil.generateUUID()
          val now = new java.sql.Timestamp(System.currentTimeMillis)
          DoobieUtil.runUpdate(
            sql"""INSERT INTO mappeduserauthcontext
                    (muserauthcontextid, muserid, mkey, mvalue, mconsumerid, createdat, updatedat)
                  VALUES ($id, $userId, ${incoming.key}, ${incoming.value}, '', $now, $now)"""
              .update.run)
          UserAuthContextRow(id, userId, incoming.key, incoming.value, "", new Date(now.getTime))
      }
    }
  }

  override def deleteUserAuthContexts(userId: String): Future[Box[Boolean]] =
    Future {
      tryo {
        DoobieUtil.runUpdate(sql"DELETE FROM mappeduserauthcontext WHERE muserid = $userId".update.run)
        true
      }
    }

  override def deleteUserAuthContextById(userAuthContextId: String): Future[Box[Boolean]] =
    Future {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE muserauthcontextid = $userAuthContextId LIMIT 1")
          .query[(String, String, String, String, String, Timestamp)].option
      ) match {
        case Some(_) =>
          tryo {
            DoobieUtil.runUpdate(
              sql"DELETE FROM mappeduserauthcontext WHERE muserauthcontextid = $userAuthContextId".update.run)
            true
          }
        case None => Empty ?~! ErrorMessages.DeleteUserAuthContextNotFound
      }
    }
}
