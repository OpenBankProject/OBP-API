package code.payments

import net.liftweb.common.{Failure, Box}
import net.liftweb.util.SimpleInjector
import code.model.{View, User, BankAccount}
import code.model.operations.{PaymentOperation}

object Payments {

  def payment(view : View, fromAccount : BankAccount, toAccount : BankAccount, amt : BigDecimal) : Box[PaymentOperation] = {
    //TODO check if view has a make payments permissions
    //for now we just check it's the owner view
    if(view.permalink == "owner") PaymentsInjector.processor.vend.makePayment(fromAccount, toAccount, amt)
    else Failure("payments must currently be made via the owner view")
  }
}

private object PaymentsInjector extends SimpleInjector {

  private val sandboxProcessor = SandboxPaymentProcessor

  def buildOne : PaymentProcessor = sandboxProcessor

  val processor = new Inject[PaymentProcessor](buildOne _) {}
}

trait PaymentProcessor {

  /**
   * @param fromAccount The account sending money
   * @param toAccount The account receiving money
   * @param amt The amount of money to send ( > 0 )
   * @return The PaymentOperation representing the payment that was initiated
   */
  def makePayment(fromAccount : BankAccount, toAccount : BankAccount, amt : BigDecimal) : Box[PaymentOperation]

}
