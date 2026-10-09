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

import code.api.berlin.group.v1_3.Http4sBGv13PIS
import code.api.util.APIUtil.unboxFullOrFail
import code.api.util.CallContext
import code.api.util.ErrorMessages.{SigningBasketMemberNotFound, SigningBasketMemberStatusInvalid, SigningBasketNotFound}
import code.signingbaskets.SigningBasketX
import code.util.Helper.booleanToFuture
import com.openbankproject.commons.model.SigningBasketContent
import net.liftweb.common.{Box, Empty}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object SigningBasketNewStyle {

  import com.openbankproject.commons.ExecutionContext.Implicits.global

  /**
   * The basket, if the caller may address it: only the consumer (TPP) that created it. A basket that does
   * not exist, one another TPP created, and one created before ownership was recorded all answer the same
   * way, so the endpoint is not a way to learn which basket ids exist.
   */
  def getOwnBasket(basketId: String, callContext: Option[CallContext]): Future[SigningBasketContent] = {
    val callingConsumer = callContext.flatMap(_.consumer.toOption).map(_.consumerId.get)
    Future(SigningBasketX.signingBasketProvider.vend.getSigningBasketByBasketId(basketId).toOption).flatMap { found =>
      booleanToFuture(SigningBasketNotFound, failCode = 403, cc = callContext) {
        found.exists(content => content.basket.consumerId.isDefined && content.basket.consumerId == callingConsumer)
      }.map(_ => found.get)
    }
  }

  // A payment lodged for SCA is stored RCVD (BG initiation) or INITIATED; anything else has been booked,
  // rejected or cancelled, or is being authorised some other way.
  val awaitingScaPaymentStatuses = Set("RCVD", "INITIATED")

  /**
   * A payment may join a basket if the caller lodged it (the rule the payment routes use) and it is still
   * waiting for SCA. One that does not exist and one that is somebody else's answer alike.
   */
  def admitPayments(paymentIds: List[String], callContext: Option[CallContext]): Future[Unit] =
    paymentIds.foldLeft(Future.successful(())) { (previous, paymentId) =>
      previous.flatMap(_ => admitPayment(paymentId, callContext))
    }

  private def admitPayment(paymentId: String, callContext: Option[CallContext]): Future[Unit] =
    Http4sBGv13PIS.getOwnPaymentImpl(paymentId, callContext).transformWith {
      case Success((payment, _)) =>
        booleanToFuture(SigningBasketMemberStatusInvalid, failCode = 409, cc = callContext) {
          awaitingScaPaymentStatuses.contains(payment.status)
        }.map(_ => ())
      case Failure(_) =>
        Future(unboxFullOrFail(Empty: Box[Unit], callContext, SigningBasketMemberNotFound, 400))
    }
}
