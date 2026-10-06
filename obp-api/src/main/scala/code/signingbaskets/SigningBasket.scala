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

package code.signingbaskets


import com.openbankproject.commons.model.{SigningBasketContent, SigningBasketTrait}
import net.liftweb.common.{Box, Logger}
import net.liftweb.util.SimpleInjector
import code.util.Helper.MdcLoggable

object SigningBasketX extends SimpleInjector {
  val signingBasketProvider: SigningBasketX.Inject[SigningBasketProvider] = new Inject(() => buildOne) {}
  private def buildOne: SigningBasketProvider = MappedSigningBasketProvider
}

/**
 * What happened to one member of a basket when the basket's authorisation was executed.
 *
 * `state` is one of SigningBasketMemberState. `detail` says why for FAILED and UNKNOWN. The state is
 * the member's own and is never folded into the basket's status: a basket can be RCVD while its
 * first payment is DONE and its second FAILED, and the TPP reads that here.
 */
case class SigningBasketMemberExecution(
  memberType: String,
  memberId: String,
  position: Int,
  state: String,
  detail: String,
  attempts: Int
)

object SigningBasketMemberState {
  /** Not started. */
  val Pending = "PENDING"
  /** Claimed by one executor; the outcome is not known yet. */
  val Executing = "EXECUTING"
  /** Booked (a payment) or activated (a consent), and recorded as such. */
  val Done = "DONE"
  /** Refused or failed before it took effect. Safe to try again. */
  val Failed = "FAILED"
  /** The executor stopped without recording an outcome. Whether it took effect is not known. */
  val Unknown = "UNKNOWN"

  val PaymentType = "payment"
  val ConsentType = "consent"
}

trait SigningBasketProvider extends MdcLoggable {

  def getSigningBaskets(): List[SigningBasketTrait]

  def getSigningBasketByBasketId(entityId: String): Box[SigningBasketContent]

  /**
   * Creates the basket and its members together, owned by the consumer that creates it. A failure
   * part way leaves nothing behind. `psuUserId` is the PSU the request already names, if any.
   *
   * A payment or consent may be held by one active basket at a time. Creating a basket that names one
   * already held fails with SigningBasketMemberStatusInvalid and leaves nothing behind.
   */
  def createSigningBasket(paymentIds: Option[List[String]],
                          consentIds: Option[List[String]],
                          consumerId: String,
                          psuUserId: Option[String]
                         ): Box[SigningBasketTrait]

  /** Records each member as PENDING, in the order given. Members already recorded are left as they are. */
  def createSigningBasketMemberExecutions(basketId: String, members: List[(String, String)]): Box[Boolean]

  /** The members of a basket with their execution state, in the order they were recorded. */
  def getSigningBasketMemberExecutions(basketId: String): List[SigningBasketMemberExecution]

  /**
   * Moves one member from one of the given states to another, only if it is still in one of them.
   * One conditional update, so two executors reaching for the same member have exactly one winner.
   * `attempts` goes up each time a member is claimed (moved to EXECUTING).
   */
  def transitionSigningBasketMemberExecution(basketId: String,
                                             memberType: String,
                                             memberId: String,
                                             from: Set[String],
                                             to: String,
                                             detail: String): Box[Boolean]

  /**
   * Members still EXECUTING after `olderThanSeconds` belong to an executor that stopped. They become
   * UNKNOWN, because nothing records whether they took effect. Returns how many were moved.
   */
  def markStaleSigningBasketMembersUnknown(olderThanSeconds: Long): Box[Int]

  /** Baskets whose execution has not finished, oldest first: AUTHORISING or EXECUTION_INCOMPLETE. */
  def getSigningBasketsAwaitingExecution(olderThanSeconds: Long, limit: Int): List[String]

  /**
   * Frees the payments and consents a basket was holding, so they can join another basket. Called when
   * a basket reaches a final status.
   */
  def releaseSigningBasketMembers(basketId: String): Box[Boolean]

  /**
   * Moves a basket from one status to another only if it still has the status the caller read.
   * One conditional update, so two callers racing for the same transition have exactly one winner.
   * Returns whether this call made the move.
   */
  def transitionSigningBasketStatus(basketId: String, from: String, to: String): Box[Boolean]

  /**
   * Binds the PSU to the basket if none is bound yet. Returns whether the basket is now bound to
   * this PSU, which is also true when it already was.
   */
  def bindSigningBasketPsu(basketId: String, psuUserId: String): Box[Boolean]

}
