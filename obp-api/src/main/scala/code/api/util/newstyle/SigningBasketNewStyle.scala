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
import code.api.util.ErrorMessages.{InvalidConnectorResponse, RegulatedEntityNotDeleted}
import code.bankconnectors.Connector
import code.signingbaskets.SigningBasketX
import com.openbankproject.commons.model.TransactionRequestId
import net.liftweb.common.{Box, Empty}

import scala.concurrent.Future

object SigningBasketNewStyle {

  import com.openbankproject.commons.ExecutionContext.Implicits.global

  def checkSigningBasketPayments(basketId: String,
                                 callContext: Option[CallContext]
                                ): OBPReturnType[Boolean] = {
    Future {
      val basket = SigningBasketX.signingBasketProvider.vend.getSigningBasketByBasketId(basketId)
      val existAll: Box[Boolean] =
        basket.flatMap(_.payments.map(_.forall(i => Connector.connector.vend.getTransactionRequestImpl(TransactionRequestId(i), callContext).isDefined)))
      if (existAll.getOrElse(false)) {
        Some(true)
      } else { // Fail due to nonexistent payment
        val paymentIds = basket.flatMap(_.payments).getOrElse(Nil).mkString(",")
        unboxFullOrFail(Empty, callContext, s"$InvalidConnectorResponse  Some of paymentIds [${paymentIds}] are invalid")
      }
    } map {
      (_, callContext)
    } map {
      x => (unboxFullOrFail(x._1, callContext, RegulatedEntityNotDeleted, 400), x._2)
    }
  }


}
