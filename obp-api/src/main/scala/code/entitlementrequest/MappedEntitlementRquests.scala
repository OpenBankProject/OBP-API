package code.entitlementrequest

import java.util.Date

import code.api.util._
import code.users.Users
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.User
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}

import scala.concurrent.Future

/**
 * A user's outstanding request for a role.
 *
 * Nothing constrains (mbankid, muserid, mrolename) even though getEntitlementRequest looks a row up
 * by exactly that triple, so the same request can be made twice and a lookup sees one of them.
 * Pre-existing; the lookup pins id ASC so which one is deterministic.
 */
case class MappedEntitlementRequest(
  entitlementRequestId: String,
  bankId: String,
  userId: String,
  roleName: String,
  created: Date
) extends EntitlementRequest {
  override def user: Box[User] = Users.users.vend.getUserByUserId(userId)
}

object MappedEntitlementRequest {

  private val selectColumns =
    fr"SELECT mentitlementrequestid, mbankid, muserid, mrolename, createdat FROM mappedentitlementrequest"

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[java.sql.Timestamp])

  private def fromRow(row: Row): MappedEntitlementRequest = row match {
    case (entitlementRequestId, bankId, userId, roleName, createdAt) =>
      MappedEntitlementRequest(entitlementRequestId.orNull, bankId.orNull, userId.orNull,
        roleName.orNull, createdAt.orNull)
  }

  private def query(condition: Fragment): List[MappedEntitlementRequest] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  def insert(bankId: String, userId: String, roleName: String): MappedEntitlementRequest = {
    val entitlementRequestId = APIUtil.generateUUID()
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO mappedentitlementrequest
            (mentitlementrequestid, mbankid, muserid, mrolename, createdat, updatedat)
            VALUES ($entitlementRequestId, $bankId, $userId, $roleName, $now, $now)"""
        .update.run)
    MappedEntitlementRequest(entitlementRequestId, bankId, userId, roleName, now)
  }

  def find(bankId: String, userId: String, roleName: String): Box[MappedEntitlementRequest] =
    query(fr"""WHERE mbankid = $bankId AND muserid = $userId AND mrolename = $roleName
               ORDER BY id ASC LIMIT 1""").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def findById(entitlementRequestId: String): Box[MappedEntitlementRequest] =
    query(fr"WHERE mentitlementrequestid = $entitlementRequestId ORDER BY id ASC LIMIT 1")
      .headOption match {
        case Some(row) => Full(row)
        case None => Empty
      }

  def findAll(): List[MappedEntitlementRequest] = query(fr"ORDER BY id ASC")

  def findAllByUserId(userId: String): List[MappedEntitlementRequest] =
    query(fr"WHERE muserid = $userId ORDER BY id ASC")

  /**
   * Date window, ordering, limit and offset are applied only when supplied, matching the Mapper
   * QueryParam list. When no ordering is requested the id order stands in for the database's scan
   * order, which is what Mapper returned and what makes LIMIT/OFFSET deterministic.
   */
  def findAllFiltered(userId: Option[String],
                      queryParams: List[OBPQueryParam]): List[MappedEntitlementRequest] = {
    val conditions = List(
      userId.map(v => fr"muserid = $v"),
      queryParams.collectFirst { case OBPFromDate(date) =>
        fr"createdat >= ${new java.sql.Timestamp(date.getTime)}" },
      queryParams.collectFirst { case OBPToDate(date) =>
        fr"createdat <= ${new java.sql.Timestamp(date.getTime)}" }
    ).flatten
    val where =
      if (conditions.isEmpty) Fragment.empty
      else fr"WHERE " ++ conditions.reduce((a, b) => a ++ fr"AND" ++ b)
    val ordering = queryParams.collectFirst {
      case OBPOrdering(_, OBPAscending) => fr"ORDER BY createdat ASC, id ASC"
      case OBPOrdering(_, OBPDescending) => fr"ORDER BY createdat DESC, id DESC"
    }.getOrElse(fr"ORDER BY id ASC")
    val limit = queryParams.collectFirst { case OBPLimit(value) => fr"LIMIT $value" }.getOrElse(Fragment.empty)
    val offset = queryParams.collectFirst { case OBPOffset(value) => fr"OFFSET $value" }.getOrElse(Fragment.empty)
    query(where ++ ordering ++ limit ++ offset)
  }

  def delete(entitlementRequestId: String): Boolean =
    DoobieUtil.runUpdate(
      sql"DELETE FROM mappedentitlementrequest WHERE mentitlementrequestid = $entitlementRequestId"
        .update.run) > 0

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedentitlementrequest".update.run)
    ()
  }
}

object MappedEntitlementRequestsProvider extends EntitlementRequestProvider {

  override def addEntitlementRequest(bankId: String, userId: String, roleName: String): Box[EntitlementRequest] =
    Some(MappedEntitlementRequest.insert(bankId, userId, roleName))

  override def addEntitlementRequestFuture(bankId: String, userId: String, roleName: String): Future[Box[EntitlementRequest]] =
    Future(addEntitlementRequest(bankId, userId, roleName))

  override def getEntitlementRequest(bankId: String, userId: String, roleName: String): Box[MappedEntitlementRequest] =
    MappedEntitlementRequest.find(bankId, userId, roleName)

  override def getEntitlementRequestFuture(entitlementRequestId: String): Future[Box[EntitlementRequest]] =
    Future(MappedEntitlementRequest.findById(entitlementRequestId))

  override def getEntitlementRequestFuture(bankId: String, userId: String, roleName: String): Future[Box[EntitlementRequest]] =
    Future(getEntitlementRequest(bankId, userId, roleName))

  override def getEntitlementRequestsFuture(): Future[Box[List[EntitlementRequest]]] =
    Future(Full(MappedEntitlementRequest.findAll()))

  override def getEntitlementRequestsFuture(userId: String): Future[Box[List[EntitlementRequest]]] =
    Future(Full(MappedEntitlementRequest.findAllByUserId(userId)))

  override def getEntitlementRequestsFuture(queryParams: List[OBPQueryParam]): Future[Box[List[EntitlementRequest]]] =
    Future(Full(MappedEntitlementRequest.findAllFiltered(None, queryParams)))

  override def getEntitlementRequestsFuture(userId: String, queryParams: List[OBPQueryParam]): Future[Box[List[EntitlementRequest]]] =
    Future(Full(MappedEntitlementRequest.findAllFiltered(Some(userId), queryParams)))

  override def deleteEntitlementRequestFuture(entitlementRequestId: String): Future[Box[Boolean]] =
    Future {
      MappedEntitlementRequest.findById(entitlementRequestId) match {
        case Full(_) => Full(MappedEntitlementRequest.delete(entitlementRequestId))
        case Empty   => Empty ?~! ErrorMessages.EntitlementRequestNotFound
        case _       => Full(false)
      }
    }
}
