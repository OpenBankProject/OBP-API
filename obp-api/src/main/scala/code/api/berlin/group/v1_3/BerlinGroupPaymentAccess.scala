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

import code.api.util.APIUtil.OBPReturnType
import code.api.util.ErrorMessages.PaymentNotInitiatedByCaller
import code.api.util.{CallContext, Consent, NewStyle}
import code.transactionrequests.TransactionRequests
import code.util.Helper
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{TransactionRequest, TransactionRequestId}

/**
 * Who may address a Berlin Group payment. Shared by the payment initiation routes and by the
 * signing basket, which has to decide the same question about every payment it is asked to hold.
 */
object BerlinGroupPaymentAccess {

  /**
   * Fetch a payment the caller is entitled to address.
   *
   * Berlin Group names a payment by its id alone — there is no account in the path — so nothing in
   * the route ties the payment to whoever is calling. Fetching one must therefore also establish
   * that the caller is the party that lodged it; otherwise any authenticated TPP holding a paymentId
   * could read another TPP's payment, list or start authorisations on it, or cancel it. Under
   * NextGenPSD2 a payment initiation resource belongs to the TPP that created it, and only that TPP
   * addresses it afterwards.
   *
   * Two things have to line up, because Berlin Group binds a payment to the TPP and the ASPSP
   * separately knows which PSU it is for.
   *
   *  - The TPP. The consumer that lodged the payment is recorded on it, and a caller presenting a
   *    different one is refused even when it is acting for the same PSU: one TPP's mandate over a
   *    payment is not another's. Payments lodged before the consumer was recorded carry none, and
   *    fall back to the person check alone rather than becoming unaddressable.
   *  - The person. A payment records the principal that lodged it and, when it was lodged under a
   *    consent, the PSU it was lodged for; a caller presents the same two. Any overlap is enough, so
   *    a payment lodged on a client-credentials token can still be authorised under the PSU's token
   *    and the other way round. A payment carrying neither identity belongs to nobody.
   */
  def getOwnPayment(paymentId: String, callContext: Option[CallContext]): OBPReturnType[TransactionRequest] =
    for {
      (transactionRequest, callContext) <- NewStyle.function.getTransactionRequestImpl(TransactionRequestId(paymentId), callContext)
      initiators = Set(transactionRequest.user_id, transactionRequest.on_behalf_of_user_id).flatten.filter(_.nonEmpty)
      callers = callContext.toSet[CallContext].flatMap(cc => cc.user.toOption.map(_.userId) ++ Consent.actingPsu(cc).map(_.userId))
      callingConsumer = callContext.flatMap(_.consumer.map(_.consumerId.get))
      // Read straight off the stored row rather than through the TransactionRequest model: which
      // TPP lodged a payment is this guard's business, not something every REST connector needs on
      // the wire, and that model's shape is a frozen contract.
      lodgedByConsumer = TransactionRequests.transactionRequestProvider.vend
        .getMappedTransactionRequest(TransactionRequestId(paymentId))
        .toOption.flatMap(tr => Consent.present(tr.mConsumerId.get))
      sameTpp = lodgedByConsumer.forall(lodgedBy => callingConsumer.contains(lodgedBy))
      _ <- Helper.booleanToFuture(s"$PaymentNotInitiatedByCaller Payment id: $paymentId.", 403, callContext) {
        sameTpp && initiators.exists(callers)
      }
    } yield (transactionRequest, callContext)

}
