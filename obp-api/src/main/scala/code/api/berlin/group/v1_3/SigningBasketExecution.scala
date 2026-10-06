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

package code.api.berlin.group.v1_3

import code.api.berlin.group.ConstantsBG
import code.api.util.APIUtil.getPropsAsIntValue
import code.api.util.newstyle.SigningBasketNewStyle
import code.api.util.{CallContext, Consent, NewStyle}
import code.consent.{ConsentStatus, Consents}
import code.signingbaskets.{SigningBasketMemberExecution, SigningBasketMemberState, SigningBasketX}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{AccountId, BankAccount, BankId, TransactionRequest, TransactionRequestId}
import net.liftweb.common.Full

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
 * Carries out a signing basket's authorisation once its SCA has been answered: books each payment,
 * one after another, and records what happened to each.
 *
 * What it guarantees, and what it does not.
 *
 *  - Each member is claimed with one conditional update before it is touched, so two executors (the
 *    request that answered the SCA and a later resumption) never work on the same member at once.
 *  - A payment that already carries a transaction id is never booked again. The payment id is the
 *    idempotency key: this is what makes a resumption safe. It holds for the mapped connector, which
 *    records the transaction id in the same database as the booking.
 *  - An execution that stopped part way is resumed from where it stopped. A member left EXECUTING past
 *    the lease is UNKNOWN: it is reconciled by the transaction id if it has one, and otherwise left for
 *    an operator, never retried blindly.
 *  - A member that failed is retried automatically only on the mapped connector, and only a limited
 *    number of times. On any other connector a failure is recorded as UNKNOWN, because the connector
 *    may have booked before it failed, and nothing here can ask it.
 *  - The basket reaches ACTC only when every member is DONE. Otherwise it is EXECUTION_INCOMPLETE,
 *    reported as RCVD, and the members' own results say what happened.
 *
 * It does not make several payments atomic. Each is booked by its connector on its own, so a failure
 * leaves the earlier ones booked, which is what the per-member results are there to show.
 */
object SigningBasketExecution extends MdcLoggable {

  import SigningBasketMemberState._

  private def provider = SigningBasketX.signingBasketProvider.vend

  /** How many times a member that failed is claimed again before it is left for an operator. */
  private def maxAttempts: Int = getPropsAsIntValue("signing_basket_member_max_attempts", 3)

  // The statuses a payment may have been admitted to a basket with.
  private def awaitingAuthorisation: Set[String] = SigningBasketNewStyle.awaitingScaPaymentStatuses

  /**
   * Executes the basket's members that are not yet DONE, in order, stopping at the first that does not
   * finish. Returns true when every member is DONE and the basket has become ACTC.
   */
  def execute(basketId: String, callContext: Option[CallContext]): Future[Boolean] = {
    def loop(rest: List[SigningBasketMemberExecution]): Future[Boolean] = rest match {
      case Nil => Future.successful(true)
      case member :: tail if member.state == Done => loop(tail)
      case member :: tail => executeMember(basketId, member, callContext).flatMap(done => if (done) loop(tail) else Future.successful(false))
    }
    loop(provider.getSigningBasketMemberExecutions(basketId)).flatMap(allDone => finish(basketId, allDone))
  }

  /** The PSU the basket was authorised by, bound when its authorisation was started. */
  private def basketPsu(basketId: String): Option[String] =
    provider.getSigningBasketByBasketId(basketId).toOption.flatMap(_.basket.psuUserId)

  /**
   * Picks up executions that stopped: members left EXECUTING past the lease become UNKNOWN, and every basket
   * still AUTHORISING or EXECUTION_INCOMPLETE that has not moved for the lease is executed again from where
   * it stopped. Safe to run on several nodes at once, since each member and each status change is claimed
   * with a conditional update. Returns how many baskets were looked at.
   */
  def resumePending(leaseSeconds: Long, limit: Int): Future[Int] = {
    provider.markStaleSigningBasketMembersUnknown(leaseSeconds)
    val baskets = provider.getSigningBasketsAwaitingExecution(leaseSeconds, limit)
    baskets.foldLeft(Future.successful(())) { (previous, basketId) =>
      previous.flatMap(_ => execute(basketId, None).transform {
        case Failure(error) =>
          logger.error(s"Resuming the execution of signing basket $basketId failed", error)
          Success(false)
        case ok => ok
      }.map(_ => ()))
    }.map(_ => baskets.size)
  }

  private def finish(basketId: String, allDone: Boolean): Future[Boolean] = Future {
    val authorising = ConstantsBG.SigningBasketsStatus.AUTHORISING_INTERNAL
    val incomplete = ConstantsBG.SigningBasketsStatus.EXECUTION_INCOMPLETE_INTERNAL
    val actc = ConstantsBG.SigningBasketsStatus.ACTC.toString
    if (allDone) {
      val completed = provider.transitionSigningBasketStatus(basketId, authorising, actc).openOr(false) ||
        provider.transitionSigningBasketStatus(basketId, incomplete, actc).openOr(false)
      // The members are free to join another basket only once the basket is final.
      if (completed) provider.releaseSigningBasketMembers(basketId)
      completed
    } else {
      provider.transitionSigningBasketStatus(basketId, authorising, incomplete)
      false
    }
  }

  private def executeMember(basketId: String, member: SigningBasketMemberExecution, callContext: Option[CallContext]): Future[Boolean] =
    member.memberType match {
      case PaymentType => executePayment(basketId, member, callContext)
      case ConsentType => executeConsent(basketId, member, callContext)
      case other => record(basketId, member, Set(Pending, Failed), Failed, s"Unknown member type $other").map(_ => false)
    }

  /**
   * Activates a consent: it becomes valid and is bound to the PSU, as if the PSU had authorised it on its
   * own. Activation is idempotent (a consent already valid and bound to this PSU is simply DONE), so unlike a
   * payment a consent may be claimed again from any state short of DONE, up to the attempts allowed.
   */
  private def executeConsent(basketId: String, member: SigningBasketMemberExecution, callContext: Option[CallContext]): Future[Boolean] = {
    def finishWith(to: String, detail: String): Future[Boolean] =
      record(basketId, member, Set(Executing, Unknown), to, detail).map(_ => to == Done)
    val claimFrom = if (member.attempts == 0) Set(Pending) else if (member.attempts < maxAttempts) Set(Pending, Failed, Unknown) else Set(Pending)
    record(basketId, member, claimFrom, Executing, "").flatMap {
      case false => Future.successful(false)
      case true =>
        basketPsu(basketId) match {
          case None => finishWith(Failed, "The basket has no PSU, so there is nobody to bind the consent to")
          case Some(psuUserId) =>
            val activation = for {
              consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(member.memberId)).map {
                case Full(found) => found
                case _ => throw new IllegalStateException("The consent cannot be read")
              }
              (psu, _) <- NewStyle.function.findByUserId(psuUserId, callContext)
              outcome <-
                if (consent.status == ConsentStatus.valid.toString && consent.userId == psuUserId)
                  Future.successful("Already valid")
                else if (!BerlinGroupConsentActivation.canActivate(consent, psuUserId))
                  Future.failed(new IllegalStateException(s"The consent is ${consent.status}, not waiting for authorisation"))
                else for {
                  // The binding point, so the holdings check is repeated here: an account can change hands
                  // between the answer and the activation.
                  _ <- Consent.assertBerlinGroupConsentAccountsHeld(psu, consent, callContext)
                  _ <- BerlinGroupConsentActivation.activate(consent, psu, callContext)
                } yield "Activated"
            } yield outcome
            activation.transform(Success(_)).flatMap {
              case Success(detail) => finishWith(Done, detail)
              case Failure(error) => finishWith(Failed, Option(error.getMessage).getOrElse(error.getClass.getSimpleName))
            }
        }
    }
  }

  private def record(basketId: String, member: SigningBasketMemberExecution, from: Set[String], to: String, detail: String): Future[Boolean] = Future {
    provider.transitionSigningBasketMemberExecution(basketId, member.memberType, member.memberId, from, to, detail).openOr(false)
  }

  /** The states a member may be claimed from: a first attempt, or a retry that is allowed. */
  private def claimableFrom(member: SigningBasketMemberExecution): Set[String] =
    if (member.attempts > 0 && member.attempts < maxAttempts) Set(Pending, Failed) else Set(Pending)

  private def bookedTransactionIds(transactionRequest: TransactionRequest): Boolean =
    Option(transactionRequest.transaction_ids).exists(_.trim.nonEmpty)

  private def executePayment(basketId: String, member: SigningBasketMemberExecution, callContext: Option[CallContext]): Future[Boolean] = {
    def finishWith(to: String, detail: String): Future[Boolean] =
      record(basketId, member, Set(Executing, Unknown), to, detail).map(_ => to == Done)

    if (member.state == Unknown) reconcile(basketId, member, callContext)
    else record(basketId, member, claimableFrom(member), Executing, "").flatMap {
      // Another executor holds it, or it is Failed past its attempts: not this one's to do.
      case false => Future.successful(false)
      case true =>
        NewStyle.function.getTransactionRequestImpl(TransactionRequestId(member.memberId), callContext).transform(Success(_)).flatMap {
          case Failure(_) => finishWith(Failed, "The payment cannot be read")
          case Success((payment, _)) if bookedTransactionIds(payment) =>
            // Already booked, by an earlier attempt that did not get to record it.
            finishWith(Done, s"Already booked: transaction ${payment.transaction_ids}")
          case Success((payment, _)) if !awaitingAuthorisation.contains(payment.status) =>
            finishWith(Failed, s"The payment is ${payment.status}, not waiting for authorisation")
          case Success((payment, _)) => book(basketId, member, payment, callContext, finishWith)
        }
    }
  }

  private def book(basketId: String,
                   member: SigningBasketMemberExecution,
                   payment: TransactionRequest,
                   callContext: Option[CallContext],
                   finishWith: (String, String) => Future[Boolean]): Future[Boolean] =
    NewStyle.function.checkBankAccountExists(BankId(payment.from.bank_id), AccountId(payment.from.account_id), callContext)
      .transform(Success(_)).flatMap {
        case Failure(_) => finishWith(Failed, "The debtor account cannot be found")
        case Success((fromAccount, _)) =>
          val mapped = isMappedConnector(fromAccount, payment, callContext)
          NewStyle.function.createTransactionAfterChallengeV210(fromAccount, payment, callContext).transform(Success(_)).flatMap {
            case Success(_) => finishWith(Done, "Booked")
            case Failure(error) =>
              // The connector failed. Whether it booked first is read from the payment: a transaction id
              // means it did. Without one, the mapped connector is treated as not having booked, so the
              // payment can be tried again; any other connector may have, so it is left UNKNOWN.
              NewStyle.function.getTransactionRequestImpl(TransactionRequestId(member.memberId), callContext).transform(Success(_)).flatMap {
                case Success((after, _)) if bookedTransactionIds(after) => finishWith(Done, s"Booked: transaction ${after.transaction_ids}")
                case _ if mapped => finishWith(Failed, s"Booking failed: ${Option(error.getMessage).getOrElse(error.getClass.getSimpleName)}")
                case _ => finishWith(Unknown, s"The connector failed and may have booked: ${Option(error.getMessage).getOrElse(error.getClass.getSimpleName)}")
              }
          }
      }

  /** A member left UNKNOWN is DONE if its payment carries a transaction id, and is otherwise left for an operator. */
  private def reconcile(basketId: String, member: SigningBasketMemberExecution, callContext: Option[CallContext]): Future[Boolean] =
    NewStyle.function.getTransactionRequestImpl(TransactionRequestId(member.memberId), callContext).transform(Success(_)).flatMap {
      case Success((payment, _)) if bookedTransactionIds(payment) =>
        record(basketId, member, Set(Unknown), Done, s"Reconciled: transaction ${payment.transaction_ids}").map(_ => true)
      case _ => Future.successful(false)
    }

  /** Whether the connector that books this payment is the mapped one. Only it is retried automatically. */
  private def isMappedConnector(fromAccount: BankAccount, payment: TransactionRequest, callContext: Option[CallContext]): Boolean =
    scala.util.Try {
      code.bankconnectors.getConnectorNameAndMethodRouting(
        "createTransactionAfterChallengeV210",
        Array("fromAccount" -> fromAccount, "transactionRequest" -> payment, "callContext" -> callContext)
      )._2 == "mapped"
    }.getOrElse(false)
}
