package code.payments

import net.liftweb.util.SimpleInjector
import code.model.{ModeratedTransaction, BankAccount}
import net.liftweb.common.Box

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
   * @return The TRANSACTION_ID of the sender's new transaction
   *         (note: information about the receiver's new transaction is not returned)
   */
  def makePayment(fromAccount : BankAccount, toAccount : BankAccount, amt : BigDecimal) : Box[String]
}
