package code.apicollection

import java.sql.Timestamp

import code.api.util.{APIUtil, DoobieUtil}
import code.util.Helper.MdcLoggable
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

/** One api-collection row, standing in for the Lift entity in return types. */
case class ApiCollectionRow(
  apiCollectionId: String,
  userId: String,
  apiCollectionName: String,
  isSharable: Boolean,
  description: String
) extends ApiCollectionTrait

/**
 * Doobie implementation of the api-collection store, replacing the Lift ApiCollection entity.
 *
 * Both unique indexes are load-bearing: one on the generated id, and one on
 * (userId, apiCollectionName), which is what stops one user creating two collections with the
 * same name - createApiCollection does not check first, it relies on the database rejecting the
 * duplicate.
 *
 * updateApiCollectionById and deleteApiCollectionById stay find-then-write/find-then-delete and
 * Empty on a missing id, matching the Mapper version: NewStyle's
 * updateApiCollection/deleteApiCollectionById both unbox the result with unboxFullOrFail, which
 * only turns a missing row into an error on Empty - Full(false) would have read as success.
 *
 * Writes go through runUpdate: outside a request scope runQuery's fallback transactor is
 * Strategy.void on a pool with autoCommit off, so the write would be rolled back on return.
 */
object DoobieApiCollectionsProvider extends MdcLoggable with ApiCollectionsProvider {

  // Only `id` is NOT NULL on this table. `description` in particular was added to the model months
  // after the table existed, and Schemifier added it with no backfill, so collections created in
  // that window hold SQL NULL there. Binding bare made doobie raise NonNullableColumnRead and fail
  // the whole listing; each column is collapsed the way its Mapper field read a NULL
  // (MappedString -> null, MappedBoolean -> false).
  private type Row = (Option[String], Option[String], Option[String], Option[Boolean], Option[String])

  private def rowOf(r: Row): ApiCollectionRow =
    ApiCollectionRow(r._1.orNull, r._2.orNull, r._3.orNull, r._4.getOrElse(false), r._5.orNull)

  private val selectCols: Fragment =
    fr"SELECT apicollectionid, userid, apicollectionname, issharable, description FROM apicollection"

  override def createApiCollection(
    userId: String,
    apiCollectionName: String,
    isSharable: Boolean,
    description: String
  ): Box[ApiCollectionTrait] = {
    val id = APIUtil.generateUUID()
    val now = new Timestamp(System.currentTimeMillis)
    tryo {
      DoobieUtil.runUpdate(
        sql"""INSERT INTO apicollection
                (apicollectionid, userid, apicollectionname, issharable, description, createdat, updatedat)
              VALUES ($id, $userId, $apiCollectionName, $isSharable, $description, $now, $now)"""
          .update.run)
      ApiCollectionRow(id, userId, apiCollectionName, isSharable, description)
    }
  }

  override def getApiCollectionById(apiCollectionId: String): Box[ApiCollectionTrait] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE apicollectionid = $apiCollectionId LIMIT 1")
        .query[Row].option
    ) match {
      case Some(r) => Full(rowOf(r))
      case None    => Empty
    }

  override def updateApiCollectionById(
    apiCollectionId: String,
    name: String,
    description: String,
    isSharable: Boolean
  ): Box[ApiCollectionTrait] =
    getApiCollectionById(apiCollectionId) match {
      case Full(existing: ApiCollectionRow) =>
        tryo {
          DoobieUtil.runUpdate(
            sql"""UPDATE apicollection SET apicollectionname = $name, description = $description, issharable = $isSharable
                  WHERE apicollectionid = $apiCollectionId"""
              .update.run)
          existing.copy(apiCollectionName = name, description = description, isSharable = isSharable)
        }
      case _ => Empty
    }

  override def getApiCollectionByUserIdAndCollectionName(
    userId: String,
    apiCollectionName: String
  ): Box[ApiCollectionTrait] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE userid = $userId AND apicollectionname = $apiCollectionName LIMIT 1")
        .query[Row].option
    ) match {
      case Some(r) => Full(rowOf(r))
      case None    => Empty
    }

  override def getAllApiCollections(): List[ApiCollectionTrait] =
    DoobieUtil.runQuery(selectCols.query[Row].to[List]).map(rowOf)

  override def deleteApiCollectionById(apiCollectionId: String): Box[Boolean] =
    getApiCollectionById(apiCollectionId) match {
      case Full(_) =>
        tryo {
          DoobieUtil.runUpdate(sql"DELETE FROM apicollection WHERE apicollectionid = $apiCollectionId".update.run)
          true
        }
      case _ => Empty
    }

  override def getApiCollectionsByUserId(userId: String): List[ApiCollectionTrait] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE userid = $userId").query[Row].to[List]
    ).map(rowOf)
}
