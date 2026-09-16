/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.usercustomerlinks

import java.util.Date

import code.api.util.ErrorMessages
import code.users.{UserReference, Users}
import code.util.{MappedUUID, UUIDString}
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.mapper._

import scala.concurrent.Future
import com.openbankproject.commons.ExecutionContext.Implicits.global

object MappedUserCustomerLinkProvider extends UserCustomerLinkProvider {

  /**
   * On-behalf-of guard (attribution policy UserReference.UserCustomerLinkUserId): a User-Customer
   * link is owned by the on-behalf-of user. When the caller is a consent user the row is written
   * for the user the consent names, so the link does not strand when the consent dies. For an
   * original user this is a no-op. The resolver logs every redirect.
   *
   * The three methods keyed by a single user id -- create, get-or-create and the two-argument
   * lookup -- all resolve, and they have to move together: the lookup is used as the
   * "already linked?" pre-check immediately before a create, and MappedUserCustomerLink carries
   * UniqueIndex(mUserId, mCustomerId). A redirected create paired with an unredirected pre-check
   * would let the check pass on the consent user, then break the index on the human.
   *
   * getUserCustomerLinksByUserId is deliberately NOT resolved: it also serves the admin lookup at
   * GET /banks/BANK_ID/user_customer_links/users/USER_ID, where the id is an explicit target and
   * rewriting it would silently answer a different question. Endpoints that mean "my links" pass
   * the resolved id themselves.
   *
   * ON_BEHALF_OF_USER_ID_PLAN.md, Phase 2 row 2.
   */
  private def linkOwnerUserId(userId: String): String =
    Users.users.vend.attributedUserId(userId, UserReference.UserCustomerLinkUserId).openOr(userId)

  def createUserCustomerLink(userId: String, customerId: String, dateInserted: Date, isActive: Boolean): Box[UserCustomerLink] = {
    val ownerUserId = linkOwnerUserId(userId)

    val createUserCustomerLink = MappedUserCustomerLink.create
      .mUserId(ownerUserId)
      .mCustomerId(customerId)
      .mDateInserted(new Date())
      .mIsActive(isActive)
      .saveMe()

    Some(createUserCustomerLink)
  }
  def getOCreateUserCustomerLink(userId: String, customerId: String, dateInserted: Date, isActive: Boolean): Box[UserCustomerLink] = {
    val ownerUserId = linkOwnerUserId(userId)
    getUserCustomerLinkRow(ownerUserId, customerId) match {
      case Empty =>
        scala.util.Try {
          MappedUserCustomerLink.create
            .mUserId(ownerUserId)
            .mCustomerId(customerId)
            .mDateInserted(new Date())
            .mIsActive(isActive)
            .saveMe()
        } match {
          case scala.util.Success(link) => Full(link)
          case scala.util.Failure(_) =>
            getUserCustomerLinkRow(ownerUserId, customerId)
        }
      case everythingElse => everythingElse
    }
  }

  def getUserCustomerLinkByCustomerId(customerId: String): Box[UserCustomerLink] = {
    MappedUserCustomerLink.find(
      By(MappedUserCustomerLink.mCustomerId, customerId))
  }
  def getUserCustomerLinksByCustomerId(customerId: String): List[UserCustomerLink] = {
    MappedUserCustomerLink.findAll(
      By(MappedUserCustomerLink.mCustomerId, customerId))
  }

  def getUserCustomerLinksByUserId(userId: String): List[UserCustomerLink] = {
    val userCustomerLinks : List[UserCustomerLink] = MappedUserCustomerLink.findAll(
      By(MappedUserCustomerLink.mUserId, userId)).sortWith(_.id.get < _.id.get)
    userCustomerLinks
  }

  /** Resolves the caller: see linkOwnerUserId. Callers use this as the pre-check for a create,
    * so it must ask about the same row the create would write. */
  def getUserCustomerLink(userId : String, customerId: String): Box[UserCustomerLink] =
    getUserCustomerLinkRow(linkOwnerUserId(userId), customerId)

  /** The raw lookup, on an id that has already been resolved. */
  private def getUserCustomerLinkRow(userId : String, customerId: String): Box[UserCustomerLink] = {
    MappedUserCustomerLink.find(
      By(MappedUserCustomerLink.mUserId, userId),
      By(MappedUserCustomerLink.mCustomerId, customerId))
  }

  def getUserCustomerLinks: Box[List[UserCustomerLink]] = {
    Full(MappedUserCustomerLink.findAll())
  }

  def bulkDeleteUserCustomerLinks(): Boolean = {
    MappedUserCustomerLink.bulkDelete_!!()
  }

  def deleteUserCustomerLink(userCustomerLinkId: String): Future[Box[Boolean]] = {
    Future {
      MappedUserCustomerLink.find(By(MappedUserCustomerLink.mUserCustomerLinkId, userCustomerLinkId)) match {
        case Full(t) => Full(t.delete_!)
        case Empty => Empty ?~! ErrorMessages.UserCustomerLinkNotFound
        case Failure(msg, exception, chain) => Failure(msg, exception, chain)
      }
    }
  }
}

class MappedUserCustomerLink extends UserCustomerLink with LongKeyedMapper[MappedUserCustomerLink] with IdPK with CreatedUpdated {

  def getSingleton = MappedUserCustomerLink

  // Name the objects m* so that we can give the overridden methods nice names.
  // Assume we'll have to override all fields so name them all m*
  object mUserCustomerLinkId extends MappedUUID(this)
  object mCustomerId extends UUIDString(this)
  object mUserId extends UUIDString(this)
  object mDateInserted extends MappedDateTime(this)
  object mIsActive extends MappedBoolean(this)

  override def userCustomerLinkId: String = mUserCustomerLinkId.get
  override def customerId: String = mCustomerId.get // id.toString
  override def userId: String = mUserId.get
  override def dateInserted: Date = mDateInserted.get
  override def isActive: Boolean = mIsActive.get
}

object MappedUserCustomerLink extends MappedUserCustomerLink with LongKeyedMetaMapper[MappedUserCustomerLink] {
  override def dbIndexes = UniqueIndex(mUserCustomerLinkId) :: UniqueIndex(mUserId, mCustomerId) :: super.dbIndexes

}
