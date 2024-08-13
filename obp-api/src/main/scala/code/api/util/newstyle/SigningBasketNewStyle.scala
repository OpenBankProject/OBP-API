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
