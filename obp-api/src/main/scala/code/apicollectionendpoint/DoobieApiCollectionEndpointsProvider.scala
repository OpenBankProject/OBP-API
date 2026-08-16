package code.apicollectionendpoint

import java.sql.Timestamp

import code.api.util.{APIUtil, DoobieUtil}
import code.util.Helper.MdcLoggable
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

/** One api-collection-endpoint row, standing in for the Lift entity in return types. */
case class ApiCollectionEndpointRow(
  apiCollectionEndpointId: String,
  apiCollectionId: String,
  operationId: String
) extends ApiCollectionEndpointTrait

/**
 * Doobie implementation of the api-collection-endpoint store, replacing the Lift
 * ApiCollectionEndpoint entity.
 *
 * There is no update path here - the Mapper version had none either, only create/get/delete - so
 * createdAt and updatedAt are both stamped once at insert, which is all CreatedUpdated ever did
 * for a row nothing later saves again.
 *
 * Both unique indexes are load-bearing: one on the generated id, and one on
 * (apiCollectionId, operationId), which is what stops the same endpoint being added twice to the
 * same collection. createApiCollectionEndpoint relies on the database to enforce the second one -
 * it does not check first.
 */
object DoobieApiCollectionEndpointsProvider extends MdcLoggable with ApiCollectionEndpointsProvider {

  override def createApiCollectionEndpoint(
    apiCollectionId: String,
    operationId: String
  ): Box[ApiCollectionEndpointTrait] = {
    val id = APIUtil.generateUUID()
    val now = new Timestamp(System.currentTimeMillis)
    tryo {
      DoobieUtil.runUpdate(
        sql"""INSERT INTO apicollectionendpoint
                (apicollectionendpointid, apicollectionid, operationid, createdat, updatedat)
              VALUES ($id, $apiCollectionId, $operationId, $now, $now)"""
          .update.run)
      ApiCollectionEndpointRow(id, apiCollectionId, operationId)
    }
  }

  override def getApiCollectionEndpointByApiCollectionIdAndOperationId(
    apiCollectionId: String,
    operationId: String
  ): Box[ApiCollectionEndpointTrait] =
    DoobieUtil.runQuery(
      sql"""SELECT apicollectionendpointid, apicollectionid, operationid FROM apicollectionendpoint
            WHERE apicollectionid = $apiCollectionId AND operationid = $operationId LIMIT 1"""
        .query[(String, String, String)].option
    ) match {
      case Some((eid, cid, op)) => Full(ApiCollectionEndpointRow(eid, cid, op))
      case None                 => Empty
    }

  override def getApiCollectionEndpoints(apiCollectionId: String): List[ApiCollectionEndpointTrait] =
    DoobieUtil.runQuery(
      sql"""SELECT apicollectionendpointid, apicollectionid, operationid FROM apicollectionendpoint
            WHERE apicollectionid = $apiCollectionId"""
        .query[(String, String, String)].to[List]
    ).map { case (eid, cid, op) => ApiCollectionEndpointRow(eid, cid, op) }

  override def getApiCollectionEndpointById(apiCollectionEndpointId: String): Box[ApiCollectionEndpointTrait] =
    DoobieUtil.runQuery(
      sql"""SELECT apicollectionendpointid, apicollectionid, operationid FROM apicollectionendpoint
            WHERE apicollectionendpointid = $apiCollectionEndpointId LIMIT 1"""
        .query[(String, String, String)].option
    ) match {
      case Some((eid, cid, op)) => Full(ApiCollectionEndpointRow(eid, cid, op))
      case None                 => Empty
    }

  // Empty when the row is missing, not Full(false): the Mapper version was find-then-delete_!,
  // and NewStyle.deleteApiCollectionEndpointById unboxes this with unboxFullOrFail, which only
  // turns a missing row into an error when it sees Empty. Full(false) here would be swallowed as
  // "deleted, sort of" and the caller would see 200 for an id that was never there.
  override def deleteApiCollectionEndpointById(apiCollectionEndpointId: String): Box[Boolean] =
    getApiCollectionEndpointById(apiCollectionEndpointId) match {
      case Full(_) =>
        tryo {
          DoobieUtil.runUpdate(
            sql"DELETE FROM apicollectionendpoint WHERE apicollectionendpointid = $apiCollectionEndpointId".update.run)
          true
        }
      case _ => Empty
    }
}
