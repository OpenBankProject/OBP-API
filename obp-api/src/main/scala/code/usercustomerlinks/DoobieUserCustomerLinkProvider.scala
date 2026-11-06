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

  private def rowOf(r: (Option[String], Option[String], Option[String], Option[java.sql.Timestamp], Option[Boolean])): UserCustomerLinkRow =
    UserCustomerLinkRow(
      userCustomerLinkId = r._1.orNull,
      userId = r._2.orNull,
      customerId = r._3.orNull,
      dateInserted = r._4.map(t => new Date(t.getTime)).orNull,
      isActive = r._5.getOrElse(false)
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

  /**
   * On-behalf-of guard (attribution policy UserReference.UserCustomerLink_UserId): a User-Customer
   * link is owned by the on-behalf-of user. When the caller is a consent user the row is written
   * for the user the consent names, so the link does not strand when the consent dies. For an
   * original user this is a no-op. The resolver logs every redirect.
   *
   * The three methods keyed by a single user id -- create, get-or-create and the two-argument
   * lookup -- all resolve, and they have to move together: the lookup is used as the
   * "already linked?" pre-check immediately before a create, and the table is unique on
   * (muserid, mcustomerid). A redirected create paired with an unredirected pre-check would let
   * the check pass on the consent user, then break the index on the human.
   *
   * getUserCustomerLinksByUserId is deliberately NOT resolved: it also serves the admin lookup at
   * GET /banks/BANK_ID/user_customer_links/users/USER_ID, where the id is an explicit target and
   * rewriting it would silently answer a different question. Endpoints that mean "my links" pass
   * the resolved id themselves.
   *
   * ON_BEHALF_OF_USER_ID_PLAN.md, Phase 2 row 2.
   */
  private def linkOwnerUserId(userId: String): String =
    code.users.Users.users.vend.attributedUserId(userId, code.users.UserReference.UserCustomerLink_UserId).openOr(userId)

  override def createUserCustomerLink(userId: String, customerId: String, dateInserted: Date, isActive: Boolean): Box[UserCustomerLink] =
    Some(insert(linkOwnerUserId(userId), customerId, isActive))

  override def getOCreateUserCustomerLink(userId: String, customerId: String, dateInserted: Date, isActive: Boolean): Box[UserCustomerLink] = {
    val ownerUserId = linkOwnerUserId(userId)
    getUserCustomerLinkRow(ownerUserId, customerId) match {
      case Empty =>
        scala.util.Try(insert(ownerUserId, customerId, isActive)) match {
          case scala.util.Success(link) => Full(link)
          case scala.util.Failure(_) =>
            getUserCustomerLinkRow(ownerUserId, customerId)
        }
      case everythingElse => everythingElse
    }
  }

  override def getUserCustomerLinkByCustomerId(customerId: String): Box[UserCustomerLink] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mcustomerid = $customerId LIMIT 1")
        .query[(Option[String], Option[String], Option[String], Option[java.sql.Timestamp], Option[Boolean])].option
    ) match {
      case Some(r) => Full(rowOf(r))
      case None    => Empty
    }

  override def getUserCustomerLinksByCustomerId(customerId: String): List[UserCustomerLink] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mcustomerid = $customerId")
        .query[(Option[String], Option[String], Option[String], Option[java.sql.Timestamp], Option[Boolean])].to[List]
    ).map(rowOf)

  override def getUserCustomerLinksByUserId(userId: String): List[UserCustomerLink] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE muserid = $userId ORDER BY id")
        .query[(Option[String], Option[String], Option[String], Option[java.sql.Timestamp], Option[Boolean])].to[List]
    ).map(rowOf)

  /** Resolves the caller: see linkOwnerUserId. Callers use this as the pre-check for a create,
    * so it must ask about the same row the create would write. */
  override def getUserCustomerLink(userId: String, customerId: String): Box[UserCustomerLink] =
    getUserCustomerLinkRow(linkOwnerUserId(userId), customerId)

  /** The raw lookup, on an id that has already been resolved. */
  private def getUserCustomerLinkRow(userId: String, customerId: String): Box[UserCustomerLink] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE muserid = $userId AND mcustomerid = $customerId LIMIT 1")
        .query[(Option[String], Option[String], Option[String], Option[java.sql.Timestamp], Option[Boolean])].option
    ) match {
      case Some(r) => Full(rowOf(r))
      case None    => Empty
    }

  override def getUserCustomerLinks: Box[List[UserCustomerLink]] =
    Full(DoobieUtil.runQuery(selectCols.query[(Option[String], Option[String], Option[String], Option[java.sql.Timestamp], Option[Boolean])].to[List]).map(rowOf))

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
