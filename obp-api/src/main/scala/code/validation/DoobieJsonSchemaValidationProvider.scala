package code.validation

import code.api.cache.Caching
import code.api.util.{APIUtil, DoobieUtil}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import scala.concurrent.duration._

/**
 * Doobie implementation of the JSON-schema-validation store, replacing the Lift
 * JsonSchemaValidation entity.
 *
 * Written rather than ported - the reference branch never migrated this table.
 *
 * Two things carried over deliberately from the Mapper version:
 *
 *  - getByOperationId stays cached, with the same TTL prop and the same cache key shape. The key
 *    string is what lands in Redis, so changing it would silently orphan live entries; only the
 *    provider class name inside it changes, exactly as the class did.
 *  - update returns Empty when the operation id is not present, rather than inserting. The Mapper
 *    version did a find-then-save and fell through to Empty, and the endpoint relies on that to
 *    distinguish update from create.
 *
 * Writes go through runUpdate: outside a request scope runQuery's fallback transactor is
 * Strategy.void on an autoCommit=false pool, so the write would be rolled back on return.
 */
object DoobieJsonSchemaValidationProvider extends JsonSchemaValidationProvider {

  private val getValidationByOperationIdTTL =
    APIUtil.getPropsValue(s"MappedJsonSchemaValidationProvider.cache.ttl.seconds.getByOperationId", "0").toInt

  private def findRow(operationId: String): Option[JsonValidation] =
    DoobieUtil.runQuery(
      sql"""SELECT operationid, jsonschema FROM jsonschemavalidation
            WHERE operationid = $operationId LIMIT 1"""
        .query[(String, String)].option
    ).map { case (op, schema) => JsonValidation(op, schema) }

  override def getByOperationId(operationId: String): Box[JsonValidation] = {
    val cacheKey = ("code.validation.DoobieJsonSchemaValidationProvider", "getByOperationId", List(operationId).mkString("_"))
    Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(getValidationByOperationIdTTL.second) {
      findRow(operationId) match {
        case Some(v) => Full(v)
        case None    => Empty
      }
    }
  }

  override def getAll(): List[JsonValidation] =
    DoobieUtil.runQuery(
      sql"SELECT operationid, jsonschema FROM jsonschemavalidation".query[(String, String)].to[List]
    ).map { case (op, schema) => JsonValidation(op, schema) }

  override def create(jsonValidation: JsonValidation): Box[JsonValidation] = tryo {
    DoobieUtil.runUpdate(
      sql"""INSERT INTO jsonschemavalidation (operationid, jsonschema)
            VALUES (${jsonValidation.operationId}, ${jsonValidation.jsonSchema})"""
        .update.run)
    jsonValidation
  }

  override def update(jsonValidation: JsonValidation): Box[JsonValidation] =
    findRow(jsonValidation.operationId) match {
      case Some(_) =>
        tryo {
          DoobieUtil.runUpdate(
            sql"""UPDATE jsonschemavalidation SET jsonschema = ${jsonValidation.jsonSchema}
                  WHERE operationid = ${jsonValidation.operationId}"""
              .update.run)
          jsonValidation
        }
      // Not found is Empty, not an insert: the endpoint tells update and create apart by this.
      case None => Empty
    }

  override def deleteByOperationId(operationId: String): Box[Boolean] = tryo {
    DoobieUtil.runUpdate(
      sql"DELETE FROM jsonschemavalidation WHERE operationid = $operationId".update.run)
    true
  }
}
