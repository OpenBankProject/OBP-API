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

package code.api.util.newstyle

import code.api.util.APIUtil.{OBPReturnType, passesPsd2Aisp, passesPsd2Pisp, unboxFullOrFail}
import code.api.util.CallContext
import code.api.berlin.group.ConstantsBG
import code.api.berlin.group.v1_3.BerlinGroupPaymentAccess
import code.api.util.Consent
import code.consent.{ConsentStatus, Consents}
import code.api.util.ErrorMessages.{PaymentInSigningBasket, ConsentDoesNotMatchUser, SigningBasketAuthorisationNotFound, SigningBasketMemberMixInvalid, SigningBasketMemberNotFound, SigningBasketMemberStatusInvalid, SigningBasketNotFound}
import code.bankconnectors.Connector
import code.consumer.Consumers
import code.signingbaskets.SigningBasketX
import code.users.Users
import code.util.Helper.{MdcLoggable, booleanToFuture}
import com.openbankproject.commons.model.enums.{ChallengeType, TransactionRequestTypes}
import com.openbankproject.commons.model.{ChallengeTrait, SigningBasketContent, TransactionRequest, TransactionRequestId}
import net.liftweb.common.{Box, Empty, Full}

import scala.concurrent.Future

object SigningBasketNewStyle extends MdcLoggable {

  import com.openbankproject.commons.ExecutionContext.Implicits.global

  /**
   * What a caller is addressing a basket for.
   *
   * A signing basket belongs to the TPP that created it (Implementation Guidelines 4.11: "the same
   * TPP"). The one exception is the ASPSP's own SCA front end, which under Redirect drives the
   * authorisation sub-resource because that is where the PSU authenticates. It is therefore allowed on
   * the authorisation operations and nowhere else: not on reading, listing the status of, or deleting
   * the basket.
   */
  sealed trait BasketAccess
  /** Read, status, delete: the creating TPP only. */
  case object CreatorOnly extends BasketAccess
  /** The authorisation sub-resource: the creating TPP, or the declared SCA front end for the right PSU. */
  case object AuthorisationOperation extends BasketAccess

  /**
   * Decide whether a caller may address a basket, returning the reason to refuse or None.
   *
   * Built on Consent.checkBerlinGroupConsentAccess, which is the same question for a consent: a PSU
   * already bound must be the caller's PSU, and the Consumer that lodged the resource is the party it
   * belongs to. A basket created before ownership was recorded has no Consumer and so belongs to
   * nobody. The consent rule's prop that re-opens such consents is deliberately not honoured here:
   * these baskets are quarantined, and an operator who needs one back assigns it explicitly.
   */
  def accessRefusal(basketConsumerId: Option[String],
                    basketPsuUserId: Option[String],
                    callerConsumerId: Option[String],
                    callerPsuUserId: Option[String],
                    callerIsScaFrontEnd: Boolean,
                    access: BasketAccess): Option[String] =
    basketConsumerId.flatMap(Consent.present) match {
      case None => Some("The basket records no Consumer that created it")
      case Some(owner) =>
        Consent.checkBerlinGroupConsentAccess(
          basketPsuUserId.getOrElse(""), owner,
          callerPsuUserId, callerConsumerId,
          callerIsScaFrontEnd = access == AuthorisationOperation && callerIsScaFrontEnd)
    }

  /**
   * The basket, if the caller may address it for this operation.
   *
   * A basket that does not exist and one the caller may not address get the same answer (403,
   * reported as RESOURCE_UNKNOWN), so the endpoint is not a way to learn which basket ids exist. The
   * reason is logged.
   */
  def getOwnBasket(basketId: String,
                   access: BasketAccess,
                   callContext: Option[CallContext]): OBPReturnType[SigningBasketContent] = Future {
    val callerConsumerId = callContext.flatMap(_.consumer.map(_.consumerId.get))
    val refusal: Either[String, SigningBasketContent] =
      SigningBasketX.signingBasketProvider.vend.getSigningBasketByBasketId(basketId) match {
        case net.liftweb.common.Full(content) =>
          accessRefusal(
            content.basket.consumerId, content.basket.psuUserId,
            callerConsumerId, callContext.flatMap(Consent.genuinePsu(_).map(_.userId)),
            Consent.isScaFrontEnd(callerConsumerId), access) match {
            case Some(reason) => Left(reason)
            case None => Right(content)
          }
        case _ => Left("There is no such basket")
      }
    refusal.left.foreach(reason => logger.info(s"A signing basket was refused to its caller: $reason. Reported as $SigningBasketNotFound"))
    refusal
  } map {
    case Right(content) => (content, callContext)
    case Left(_) => unboxFullOrFail(Empty: Box[(SigningBasketContent, Option[CallContext])], callContext, SigningBasketNotFound, 403)
  } flatMap { case (content, cc) =>
    // Only once the caller is known to be entitled to the basket, so a role check cannot be used to
    // tell a basket that exists from one that does not.
    passesRolesOfMembers(content, access, callContext).map(_ => (content, cc))
  }

  /**
   * The PSP roles the members of a basket call for: PISP for payments, AISP for consents, both for a
   * mix. The ASPSP's own SCA front end is not a payment or account information service provider, and
   * acts on the authorisation under Redirect without a certificate of its own.
   */
  private def passesRolesOfMembers(content: SigningBasketContent,
                                   access: BasketAccess,
                                   callContext: Option[CallContext]): Future[Unit] = {
    val frontEnd = access == AuthorisationOperation &&
      Consent.isScaFrontEnd(callContext.flatMap(_.consumer.map(_.consumerId.get)))
    if (frontEnd) Future.successful(())
    else for {
      _ <- if (content.payments.exists(_.nonEmpty)) passesPsd2Pisp(callContext) else Future.successful(())
      _ <- if (content.consents.exists(_.nonEmpty)) passesPsd2Aisp(callContext) else Future.successful(())
    } yield ()
  }

  private def refuseMember(message: String, code: Int, callContext: Option[CallContext]): Future[Nothing] =
    booleanToFuture(message, failCode = code, cc = callContext)(false).map(_ => throw new IllegalStateException(message))

  /**
   * A payment may join a basket if the caller may address it, it is a single SEPA payment still
   * waiting for SCA. Whether the caller may is BerlinGroupPaymentAccess's rule, the same one the
   * payment routes apply; a payment that does not exist and one that is not the caller's are answered
   * alike, so the endpoint does not reveal which payment ids exist. Returns the PSU the payment names,
   * if it names one.
   */
  private def admitPayment(paymentId: String, cc: CallContext, callContext: Option[CallContext]): Future[Option[String]] =
    for {
      (payment, _) <- BerlinGroupPaymentAccess.getOwnPayment(paymentId, callContext)
        .recoverWith { case _ => refuseMember(SigningBasketMemberNotFound, 400, callContext) }
      // Only single SEPA credit transfers. A periodic payment cannot be told apart once stored: the
      // routes pass the service as periodic_payments and the provider compares it to periodic-payments,
      // so the recurrence of a periodic payment is never recorded (and bulk payments are not offered).
      _ <- booleanToFuture(SigningBasketMemberMixInvalid, failCode = 400, cc = callContext) {
        payment.`type` == TransactionRequestTypes.SEPA_CREDIT_TRANSFERS.toString
      }
      _ <- booleanToFuture(SigningBasketMemberStatusInvalid, failCode = 409, cc = callContext) {
        awaitingScaPaymentStatuses.contains(payment.status)
      }
    } yield paymentPsu(payment, cc.consumer.map(_.key.get))

  /**
   * Whether this user is a person rather than the TPP's own pseudo-user. A client-credentials token
   * resolves to an auto-created user keyed on the consumer's own key, and a payment lodged on one
   * records it as a user it was made by or for; it names nobody a basket could be bound to.
   */
  private def isPerson(userId: String, tppConsumerKey: Option[String]): Boolean =
    Users.users.vend.getUserByUserId(userId).toOption
      .forall(user => !tppConsumerKey.contains(user.idGivenByProvider))

  /**
   * The PSU a payment is for. A payment records two identities, the principal that lodged it and, when it
   * was lodged for somebody, the one it was lodged for, and the rule that lets a caller address it
   * accepts either (BerlinGroupPaymentAccess). So the PSU is whichever of the two is a person: the one it
   * was lodged for if that is one, otherwise the one that lodged it. Reading only the first lets a payment
   * a PSU lodged themselves join a basket that names nobody, to be bound to somebody else.
   */
  private def paymentPsu(payment: TransactionRequest, tppConsumerKey: Option[String]): Option[String] =
    List(payment.on_behalf_of_user_id, payment.user_id).flatten.flatMap(Consent.present).find(isPerson(_, tppConsumerKey))

  /**
   * The PSUs the members of a basket name. Empty when none names anyone, one when they agree. Read off the
   * members as they are now, because they can change between creating the basket and authorising it.
   */
  private def knownMemberPsus(basket: SigningBasketContent, callContext: Option[CallContext]): Future[Set[String]] = Future {
    val tppKey = basket.basket.consumerId.flatMap(id => Consumers.consumers.vend.getConsumerByConsumerId(id).toOption.map(_.key.get))
    val payments = basket.payments.getOrElse(Nil).flatMap { id =>
      Connector.connector.vend.getTransactionRequestImpl(TransactionRequestId(id), callContext).toOption.flatMap(r => paymentPsu(r._1, tppKey))
    }
    val consents = basket.consents.getOrElse(Nil).flatMap { id =>
      Consents.consentProvider.vend.getConsentByConsentId(id).toOption.flatMap(c => Consent.present(c.userId))
    }
    (payments ++ consents).toSet
  }

  /**
   * Refuse a PSU the members do not all name. The PSU named when an authorisation is started, and the one
   * the answer is checked as, must be the one every member that names a PSU is for; otherwise someone else
   * could authorise a member that is not theirs. Answered like any other refusal to address the basket.
   */
  def requireMembersForPsu(basket: SigningBasketContent, psuUserId: String, callContext: Option[CallContext]): Future[Unit] =
    knownMemberPsus(basket, callContext).flatMap { named =>
      booleanToFuture(failMsg = SigningBasketNotFound, failCode = 403, cc = callContext)(named.forall(_ == psuUserId)).map(_ => ())
    }

  /**
   * A payment that an active basket holds is authorised through the basket. Authorising it on its own as well
   * would book it twice, once by each. Only the check is shared with the basket: the two authorisations are
   * answered with different one-time passwords, in separate requests.
   */
  def requirePaymentOutsideBaskets(paymentId: String, callContext: Option[CallContext]): Future[Unit] =
    booleanToFuture(failMsg = PaymentInSigningBasket, failCode = 409, cc = callContext) {
      !SigningBasketX.signingBasketProvider.vend.memberHeldByBasket(s"payment:$paymentId")
    }.map(_ => ())

  // A payment lodged for SCA is stored RCVD (BG initiation) or INITIATED; anything else has been booked,
  // rejected or cancelled, or is being authorised some other way. The executor accepts the same set.
  val awaitingScaPaymentStatuses = Set("RCVD", "INITIATED")

  /**
   * A consent may join a basket if the caller may address it under the rule consents use, it was
   * created through the Berlin Group API, and it has not been authorised or ended. Returns the PSU the
   * consent is bound to, if it is.
   */
  private def admitConsent(consentId: String, cc: CallContext, callContext: Option[CallContext]): Future[Option[String]] =
    for {
      consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)).flatMap {
        case Full(found) => Future.successful(found)
        case _ => refuseMember(SigningBasketMemberNotFound, 400, callContext)
      }
      refusal = Consent.checkBerlinGroupConsentAccess(
        consent.userId, consent.consumerId,
        Consent.genuinePsu(cc).map(_.userId), cc.consumer.map(_.consumerId.get),
        callerIsScaFrontEnd = false)
      _ <- booleanToFuture(SigningBasketMemberNotFound, failCode = 400, cc = callContext) {
        refusal.isEmpty && consent.apiStandard == ConstantsBG.berlinGroupVersion1.apiStandard
      }
      _ <- booleanToFuture(SigningBasketMemberStatusInvalid, failCode = 409, cc = callContext) {
        consent.status == ConsentStatus.received.toString
      }
    } yield Consent.present(consent.userId)

  /**
   * Admit the members of a new basket, and say whom the basket is for.
   *
   * Every member must be one the caller may address, in a state SCA can still authorise. All members
   * must be for the same PSU where they name one, and that must be the PSU the request names (a genuine
   * PSU in the session, or PSU-ID) where it names one. Members that name nobody leave the PSU to be bound
   * when an authorisation is started.
   */
  def admitMembers(paymentIds: List[String],
                   consentIds: List[String],
                   cc: CallContext,
                   callContext: Option[CallContext]): Future[Option[String]] =
    for {
      paymentPsus <- paymentIds.foldLeft(Future.successful(List.empty[Option[String]])) { (acc, id) =>
        acc.flatMap(done => admitPayment(id, cc, callContext).map(done :+ _))
      }
      consentPsus <- consentIds.foldLeft(Future.successful(List.empty[Option[String]])) { (acc, id) =>
        acc.flatMap(done => admitConsent(id, cc, callContext).map(done :+ _))
      }
      namedPsu <- Consent.resolvePsuIdHeader(cc, callContext).map(_.orElse(Consent.genuinePsu(cc).map(_.userId)))
      memberPsus = (paymentPsus ++ consentPsus).flatten.toSet
      _ <- booleanToFuture(SigningBasketMemberMixInvalid, failCode = 400, cc = callContext) {
        memberPsus.size <= 1 && namedPsu.forall(named => memberPsus.forall(_ == named))
      }
    } yield namedPsu.orElse(memberPsus.headOption)

  /**
   * The PSU an authorisation on this basket is for, bound to the basket.
   *
   * It decides whose challenge this is, which is also where the one-time password is sent, so it is
   * not read off the session: under Berlin Group the caller is the TPP, and a client-credentials TPP
   * resolves to a pseudo-user of its own. The order is Consent.resolveBerlinGroupPsu's: the PSU the
   * basket already names, a genuine PSU in the session (Redirect), then the PSU-ID header (Embedded).
   * A header that contradicts the basket's PSU gets the same answer as any other refusal to address
   * the basket; with none of the three there is nobody to authorise for, which is the standard's
   * PSU_CREDENTIALS_INVALID (401).
   */
  def bindAuthorisingPsu(basket: SigningBasketContent,
                         cc: CallContext,
                         callContext: Option[CallContext]): Future[String] =
    for {
      headerPsuUserId <- Consent.resolvePsuIdHeader(cc, callContext)
      psuUserId <- Consent.resolveBerlinGroupPsu(
        basket.basket.psuUserId.getOrElse(""), Consent.genuinePsu(cc).map(_.userId), headerPsuUserId) match {
        case Right(userId) => Future.successful(userId)
        case Left(reason) =>
          val (failMsg, failCode) =
            if (reason == ConsentDoesNotMatchUser) (SigningBasketNotFound, 403) else (reason, 401)
          booleanToFuture(failMsg = failMsg, failCode = failCode, cc = callContext)(false).map(_ => "")
      }
      _ <- requireMembersForPsu(basket, psuUserId, callContext)
      bound <- Future(SigningBasketX.signingBasketProvider.vend.bindSigningBasketPsu(basket.basket.basketId, psuUserId))
      _ <- booleanToFuture(failMsg = SigningBasketNotFound, failCode = 403, cc = callContext)(bound.openOr(false))
    } yield psuUserId

  /**
   * An authorisation of this basket, by id. One issued for another basket, or for something that is
   * not a signing basket, is not found: the connector's challenge lookup goes by challenge id alone.
   */
  def getBasketAuthorisation(basketId: String,
                             authorisationId: String,
                             callContext: Option[CallContext]): OBPReturnType[ChallengeTrait] =
    Connector.connector.vend.getChallenge(authorisationId, callContext) map { case (challenge, cc) =>
      val ofThisBasket = challenge.toOption.filter(c =>
        c.basketId.contains(basketId) && c.challengeType == ChallengeType.BERLIN_GROUP_SIGNING_BASKETS_CHALLENGE.toString)
      (unboxFullOrFail(Box(ofThisBasket), callContext, SigningBasketAuthorisationNotFound, 404), cc)
    }
}
