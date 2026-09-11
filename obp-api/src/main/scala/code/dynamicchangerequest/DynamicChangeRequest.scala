package code.dynamicchangerequest

import java.util.Date

import code.api.util.{APIUtil, DoobieUtil, ErrorMessages}
import com.openbankproject.commons.model.enums.DynamicChangeRequestStatus
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.util.Helpers.tryo

/**
 * One maker/checker request row.
 *
 * Upstream declares this as a Lift Mapper entity; this branch has no Lift Mapper (it does not
 * compile under Scala 3) and no Schemifier, so the row is a plain case class over a Doobie store
 * and the table comes from db.changelog-develop-merge-2.yaml.
 *
 * Every String field the store binds through Option is read back through one too: the columns are
 * nullable, and Doobie's Get for a bare String throws NonNullableColumnRead on a SQL NULL - which
 * fails the whole query rather than the one row. The trait exposes them as non-null Strings, so
 * the mapping supplies the same "" the Mapper getters did via Option(...).getOrElse("").
 */
case class DynamicChangeRequest(
  dynamicChangeRequestId: String,
  targetType: String,
  targetId: String,
  operation: String,
  requestVerb: String,
  requestPath: String,
  proposedPayload: String,
  payloadHash: String,
  currentPayloadHash: String,
  status: String,
  requestorUserId: String,
  businessJustification: String,
  checkerUserId: String,
  checkerComment: String,
  created: Date,
  updated: Date,
  actionedAt: Option[Date],
  expiresAt: Option[Date],
  /** Surrogate key. Needed by the guarded status transition, which addresses the row by id. */
  id: Long = 0L
) extends DynamicChangeRequestTrait

object DynamicChangeRequest {

  private val selectColumns =
    fr"""SELECT dynamicchangerequestid, targettype, targetid, operation, requestverb, requestpath,
                proposedpayload, payloadhash, currentpayloadhash, status, requestoruserid,
                businessjustification, checkeruserid, checkercomment, createdat, updatedat,
                actionedat, expiresat, id
         FROM dynamicchangerequest"""

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[String], Option[String],
    Option[java.sql.Timestamp], Option[java.sql.Timestamp],
    Option[java.sql.Timestamp], Option[java.sql.Timestamp], Long)

  /** java.sql.Timestamp is a java.util.Date subclass, but json4s renders it as {} - convert. */
  private def readDate(value: Option[java.sql.Timestamp]): Option[Date] =
    value.map(t => new Date(t.getTime))

  private def fromRow(row: Row): DynamicChangeRequest = row match {
    case (dynamicChangeRequestId, targetType, targetId, operation, requestVerb, requestPath,
          proposedPayload, payloadHash, currentPayloadHash, status, requestorUserId,
          businessJustification, checkerUserId, checkerComment, createdAt, updatedAt,
          actionedAt, expiresAt, id) =>
      DynamicChangeRequest(
        dynamicChangeRequestId.orNull,
        targetType.orNull,
        targetId.getOrElse(""),
        operation.orNull,
        requestVerb.getOrElse(""),
        requestPath.getOrElse(""),
        proposedPayload.getOrElse(""),
        payloadHash.getOrElse(""),
        currentPayloadHash.getOrElse(""),
        status.orNull,
        requestorUserId.orNull,
        businessJustification.getOrElse(""),
        checkerUserId.getOrElse(""),
        checkerComment.getOrElse(""),
        readDate(createdAt).orNull,
        readDate(updatedAt).orNull,
        readDate(actionedAt),
        readDate(expiresAt),
        id)
  }

  private def query(condition: Fragment): List[DynamicChangeRequest] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  def findById(dynamicChangeRequestId: String): Box[DynamicChangeRequest] =
    query(fr"WHERE dynamicchangerequestid = $dynamicChangeRequestId ORDER BY id ASC LIMIT 1")
      .headOption match {
        case Some(row) => Full(row)
        case None => Empty
      }

  def findAll(status: Option[String], targetType: Option[String], targetId: Option[String],
              requestorUserId: Option[String]): List[DynamicChangeRequest] = {
    // Each filter is bound, never spliced: these come straight off the query string.
    val conditions = List(
      status.map(v => fr"status = $v"),
      targetType.map(v => fr"targettype = $v"),
      targetId.map(v => fr"targetid = $v"),
      requestorUserId.map(v => fr"requestoruserid = $v")
    ).flatten
    val where =
      if (conditions.isEmpty) Fragment.empty
      else fr"WHERE" ++ conditions.reduce((a, b) => a ++ fr"AND" ++ b)
    query(where ++ fr"ORDER BY id DESC")
  }

  def insert(targetType: String, targetId: String, operation: String, requestVerb: String,
             requestPath: String, proposedPayload: String, payloadHash: String,
             currentPayloadHash: String, requestorUserId: String, businessJustification: String,
             expiresAt: Option[Date]): DynamicChangeRequest = {
    val dynamicChangeRequestId = APIUtil.generateUUID()
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    val expires = expiresAt.map(d => new java.sql.Timestamp(d.getTime))
    DoobieUtil.runUpdate(
      sql"""INSERT INTO dynamicchangerequest
            (dynamicchangerequestid, targettype, targetid, operation, requestverb, requestpath,
             proposedpayload, payloadhash, currentpayloadhash, status, requestoruserid,
             businessjustification, checkeruserid, checkercomment, expiresat, createdat, updatedat)
            VALUES ($dynamicChangeRequestId, ${Option(targetType)}, ${Option(targetId)},
             ${Option(operation)}, ${Option(requestVerb)}, ${Option(requestPath)},
             ${Option(proposedPayload)}, ${Option(payloadHash)}, ${Option(currentPayloadHash)},
             ${Option(DynamicChangeRequestStatus.INITIATED.toString)}, ${Option(requestorUserId)},
             ${Option(businessJustification)}, ${Option("")}, ${Option("")}, $expires, $now, $now)"""
        .update.run)
    findById(dynamicChangeRequestId)
      .openOrThrowException("the change request just inserted must be readable")
  }

  /**
   * Record which row an approved CREATE actually produced.
   *
   * Unguarded on purpose, unlike the status transition: this runs immediately after the CREATE
   * the checker approved, on a request the same code path has just moved out of INITIATED, so
   * there is no second writer to race.
   */
  def setTargetId(dynamicChangeRequestId: String, targetId: String): Unit = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""UPDATE dynamicchangerequest SET targetid = ${Option(targetId)}, updatedat = $now
            WHERE dynamicchangerequestid = $dynamicChangeRequestId""".update.run)
    ()
  }

  /** The apply step threw after the decision was recorded: keep the reason on the audit row. */
  def markFailed(dynamicChangeRequestId: String, status: String, checkerComment: String): Unit = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""UPDATE dynamicchangerequest
              SET status = ${Option(status)}, checkercomment = ${Option(checkerComment)},
                  updatedat = $now
            WHERE dynamicchangerequestid = $dynamicChangeRequestId""".update.run)
    ()
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM dynamicchangerequest".update.run)
    ()
  }
}

object MappedDynamicChangeRequestProvider extends DynamicChangeRequestProvider {

  override def create(
    targetType: String,
    targetId: String,
    operation: String,
    requestVerb: String,
    requestPath: String,
    proposedPayload: String,
    payloadHash: String,
    currentPayloadHash: String,
    requestorUserId: String,
    businessJustification: String,
    expiresAt: Option[Date]
  ): Box[DynamicChangeRequestTrait] = tryo {
    DynamicChangeRequest.insert(targetType, targetId, operation, requestVerb, requestPath,
      proposedPayload, payloadHash, currentPayloadHash, requestorUserId, businessJustification,
      expiresAt)
  }

  override def getById(dynamicChangeRequestId: String): Box[DynamicChangeRequestTrait] =
    DynamicChangeRequest.findById(dynamicChangeRequestId)

  override def getAll(
    status: Option[String],
    targetType: Option[String],
    targetId: Option[String],
    requestorUserId: Option[String]
  ): List[DynamicChangeRequestTrait] =
    DynamicChangeRequest.findAll(status, targetType, targetId, requestorUserId)

  override def getByRequestorUserId(requestorUserId: String): List[DynamicChangeRequestTrait] =
    DynamicChangeRequest.findAll(None, None, None, Some(requestorUserId))

  override def updateStatus(
    dynamicChangeRequestId: String,
    status: String,
    checkerUserId: String,
    checkerComment: String
  ): Box[DynamicChangeRequestTrait] =
    DynamicChangeRequest.findById(dynamicChangeRequestId).flatMap { request =>
      // Atomic guarded transition: a request is actioned once, from INITIATED. The loser of a
      // concurrent approve/reject gets 0 rows -> Failure, instead of silently overwriting the decision.
      val rows = code.bankconnectors.DoobieBusinessStatusQueries.conditionalDynamicChangeRequestStatus(
        request.id,
        DynamicChangeRequestStatus.INITIATED.toString, status, checkerUserId, checkerComment)
      if (rows == 1) DynamicChangeRequest.findById(dynamicChangeRequestId)
      else Failure(ErrorMessages.DynamicChangeRequestNotInitiated)
    }
}
