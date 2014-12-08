package code.payments

import code.bankconnectors.Connector
import code.model.TransactionId
import net.liftweb.common.{Loggable, Failure, Box}

//Works with any kind of Connector
trait PaymentsNotSupported extends PaymentProcessor {

  self : Connector with Loggable =>

  protected def makePaymentImpl(fromAccount : AccountType, toAccount : AccountType, amt : BigDecimal) : Box[TransactionId] = {
    logger.info("Payment attempted on an Open Bank Project instance that does not support payments.")
    Failure("Payments not supported by this Open Bank Project instance.")
  }

}
