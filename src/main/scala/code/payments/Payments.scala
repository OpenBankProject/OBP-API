package code.payments

import net.liftweb.util.SimpleInjector
import code.model.BankAccount
import code.model.operations.{PaymentOperation}

object PaymentsInjector extends SimpleInjector {

  private val sandboxProcessor = SandboxPaymentProcessor

  def buildOne : PaymentProcessor = sandboxProcessor

  val processor = new Inject[PaymentProcessor](buildOne _) {}

}

trait PaymentProcessor {

  /**
   *
   * @param fromAccount The account sending money
   * @param toAccount The account receiving money
   * @param amt The amount of money to send ( > 0 )
   * @return The PaymentOperation representing the payment that was initiated
   */
  def makePayment(fromAccount : BankAccount, toAccount : BankAccount, amt : BigDecimal) : PaymentOperation
}
