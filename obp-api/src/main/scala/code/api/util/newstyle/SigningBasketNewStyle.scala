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

import code.api.util.APIUtil.{OBPReturnType, unboxFullOrFail}
import code.api.util.CallContext
import code.api.util.Consent
import code.api.util.ErrorMessages.{ConsentDoesNotMatchUser, SigningBasketAuthorisationNotFound, SigningBasketNotFound}
import code.bankconnectors.Connector
import code.signingbaskets.SigningBasketX
import code.util.Helper.{MdcLoggable, booleanToFuture}
import com.openbankproject.commons.model.enums.ChallengeType
import com.openbankproject.commons.model.{ChallengeTrait, SigningBasketContent}
import net.liftweb.common.{Box, Empty}

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
  }

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
