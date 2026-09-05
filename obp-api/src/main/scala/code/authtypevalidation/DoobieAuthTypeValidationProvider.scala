package code.authtypevalidation

import code.api.cache.Caching
import code.api.util.{APIUtil, DoobieUtil}
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.Props

import scala.concurrent.duration.DurationInt

/**
 * Doobie implementation of the authentication-type validation store, replacing the Lift
 * AuthenticationTypeValidation entity.
 *
 * Two things carried over from the Mapper version rather than tidied up:
 *
 *  - getByOperationId stays cached with the same TTL rule, including the zero TTL under test
 *    mode. The cache key keeps its shape because it is what lands in Redis; only the provider
 *    class name inside it changes, exactly as the class did.
 *  - update returns Empty for an unknown operation id instead of inserting. The endpoint relies
 *    on that to tell update apart from create.
 *
 * Allowed types are stored as one comma-separated string, and JsonAuthTypeValidation's companion
 * apply is what turns that back into a List[AuthenticationType]. Keeping the storage format means
 * rows written by the Mapper version still read correctly.
 *
 * Writes go through runUpdate: outside a request scope runQuery's fallback transactor is
 * Strategy.void on a pool with autoCommit off, so the write would be rolled back on return.
 */
object DoobieAuthTypeValidationProvider extends AuthenticationTypeValidationProvider {

  val getValidationByOperationIdTTL: Int = {
    if (Props.testMode) 0
    else APIUtil.getPropsValue(s"authTypeValidation.cache.ttl.seconds", "36").toInt
  }

  private def findRow(operationId: String): Option[(String, String)] =
    DoobieUtil.runQuery(
      sql"""SELECT operationid, allowedauthtypes FROM authenticationtypevalidation
            WHERE operationid = $operationId LIMIT 1"""
        .query[(String, String)].option)

  override def getByOperationId(operationId: String): Box[JsonAuthTypeValidation] = {
    val cacheKey = ("code.authtypevalidation.DoobieAuthTypeValidationProvider", "getByOperationId", List(operationId).mkString("_"))
    Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(getValidationByOperationIdTTL.second) {
      findRow(operationId) match {
        case Some((op, types)) => Full(JsonAuthTypeValidation(op, types))
        case None              => Empty
      }
    }
  }

  override def getAll(): List[JsonAuthTypeValidation] =
    DoobieUtil.runQuery(
      sql"SELECT operationid, allowedauthtypes FROM authenticationtypevalidation"
        .query[(String, String)].to[List]
    ).map { case (op, types) => JsonAuthTypeValidation(op, types) }

  override def create(jsonValidation: JsonAuthTypeValidation): Box[JsonAuthTypeValidation] = {
    val types = jsonValidation.authTypes.mkString(",")
    tryo {
      DoobieUtil.runUpdate(
        sql"""INSERT INTO authenticationtypevalidation (operationid, allowedauthtypes)
              VALUES (${jsonValidation.operationId}, $types)"""
          .update.run)
      JsonAuthTypeValidation(jsonValidation.operationId, types)
    }
  }

  override def update(jsonValidation: JsonAuthTypeValidation): Box[JsonAuthTypeValidation] = {
    val types = jsonValidation.authTypes.mkString(",")
    findRow(jsonValidation.operationId) match {
      case Some(_) =>
        tryo {
          DoobieUtil.runUpdate(
            sql"""UPDATE authenticationtypevalidation SET allowedauthtypes = $types
                  WHERE operationid = ${jsonValidation.operationId}"""
              .update.run)
          JsonAuthTypeValidation(jsonValidation.operationId, types)
        }
      // Unknown operation id is Empty, not an insert: the endpoint distinguishes update from create.
      case None => Empty
    }
  }

  override def deleteByOperationId(operationId: String): Box[Boolean] = tryo {
    DoobieUtil.runUpdate(
      sql"DELETE FROM authenticationtypevalidation WHERE operationid = $operationId".update.run)
    true
  }
}
