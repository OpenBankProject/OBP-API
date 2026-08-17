package code.usercustomerlinks

import code.api.util.{APIUtil, DoobieUtil, ErrorMessages}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Failure, Full}

import java.util.Date
import scala.concurrent.Future
import com.openbankproject.commons.ExecutionContext.Implicits.global

/** One user-customer-link row, standing in for the Lift entity in return types. */
case class UserCustomerLinkRow(
  userCustomerLinkId: String,
  userId: String,
  customerId: String,
  dateInserted: Date,
  isActive: Boolean
) extends UserCustomerLink

/**
 * Doobie implementation of the user-customer-link store, replacing the Lift
 * MappedUserCustomerLink entity.
 *
 * Two unique indexes: one on musercustomerlinkid, one on the composite (muserid, mcustomerid) -
 * a user has at most one link per customer, matching the entity's own dbIndexes.
 *
 * getOCreateUserCustomerLink preserves the Mapper version's find-then-insert-with-retry-on-
 * conflict shape exactly: ConcurrentDuplicateCreationTest scenario L relies on the unique index
 * rejecting a concurrent duplicate insert, caught here (via scala.util.Try, matching the
 * original) and turned into a re-fetch of the winning row rather than a thrown exception.
 */
object DoobieUserCustomerLinkProvider extends UserCustomerLinkProvider {

  private def rowOf(r: (String, String, String, java.sql.Timestamp, Boolean)): UserCustomerLinkRow =
    UserCustomerLinkRow(
      userCustomerLinkId = r._1,
      userId = r._2,
      customerId = r._3,
      dateInserted = new Date(r._4.getTime),
      isActive = r._5
    )

  private val selectCols: Fragment =
    fr"SELECT musercustomerlinkid, muserid, mcustomerid, mdateinserted, misactive FROM mappedusercustomerlink"

  private def insert(userId: String, customerId: String, isActive: Boolean): UserCustomerLinkRow = {
    val id = APIUtil.generateUUID()
    val now = new java.sql.Timestamp(System.currentTimeMillis)
    DoobieUtil.runUpdate(
      sql"""INSERT INTO mappedusercustomerlink (musercustomerlinkid, muserid, mcustomerid, mdateinserted, misactive, createdat, updatedat)
            VALUES ($id, $userId, $customerId, $now, $isActive, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"""
        .update.run)
    UserCustomerLinkRow(id, userId, customerId, new Date(now.getTime), isActive)
  }

  override def createUserCustomerLink(userId: String, customerId: String, dateInserted: Date, isActive: Boolean): Box[UserCustomerLink] =
    Some(insert(userId, customerId, isActive))

  override def getOCreateUserCustomerLink(userId: String, customerId: String, dateInserted: Date, isActive: Boolean): Box[UserCustomerLink] =
    getUserCustomerLink(userId, customerId) match {
      case Empty =>
        scala.util.Try(insert(userId, customerId, isActive)) match {
          case scala.util.Success(link) => Full(link)
          case scala.util.Failure(_) =>
            getUserCustomerLink(userId, customerId)
        }
      case everythingElse => everythingElse
    }

  override def getUserCustomerLinkByCustomerId(customerId: String): Box[UserCustomerLink] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mcustomerid = $customerId LIMIT 1")
        .query[(String, String, String, java.sql.Timestamp, Boolean)].option
    ) match {
      case Some(r) => Full(rowOf(r))
      case None    => Empty
    }

  override def getUserCustomerLinksByCustomerId(customerId: String): List[UserCustomerLink] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mcustomerid = $customerId")
        .query[(String, String, String, java.sql.Timestamp, Boolean)].to[List]
    ).map(rowOf)

  override def getUserCustomerLinksByUserId(userId: String): List[UserCustomerLink] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE muserid = $userId ORDER BY id")
        .query[(String, String, String, java.sql.Timestamp, Boolean)].to[List]
    ).map(rowOf)

  override def getUserCustomerLink(userId: String, customerId: String): Box[UserCustomerLink] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE muserid = $userId AND mcustomerid = $customerId LIMIT 1")
        .query[(String, String, String, java.sql.Timestamp, Boolean)].option
    ) match {
      case Some(r) => Full(rowOf(r))
      case None    => Empty
    }

  override def getUserCustomerLinks: Box[List[UserCustomerLink]] =
    Full(DoobieUtil.runQuery(selectCols.query[(String, String, String, java.sql.Timestamp, Boolean)].to[List]).map(rowOf))

  override def bulkDeleteUserCustomerLinks(): Boolean = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedusercustomerlink".update.run)
    true
  }

  override def deleteUserCustomerLink(userCustomerLinkId: String): Future[Box[Boolean]] = Future {
    DoobieUtil.runQuery(
      sql"SELECT COUNT(*) FROM mappedusercustomerlink WHERE musercustomerlinkid = $userCustomerLinkId".query[Int].unique) match {
      case 0 => Empty ?~! ErrorMessages.UserCustomerLinkNotFound
      case _ =>
        DoobieUtil.runUpdate(sql"DELETE FROM mappedusercustomerlink WHERE musercustomerlinkid = $userCustomerLinkId".update.run)
        Full(true)
    }
  }
}
