package code.bankaccountcreation

import code.api.test.ServerSetup
import code.model.{Consumer => OBPConsumer, Token => OBPToken}
import org.scalatest.Tag
import com.tesobe.model.CreateBankAccount
import code.model.dataAccess.{HostedBank, APIUser, BankAccountCreationListener}
import net.liftmodules.amqp.AMQPMessage
import net.liftweb.mapper.By
import code.bankconnectors.Connector

class BankAccountCreationListenerTest extends ServerSetup {

  object AccountHolderSetup extends Tag("account_holder_setup")

  feature("The account holder gets properly set when a bank account is created"){
    scenario("a bank account is created") {

      When("We create a bank account")
      val userId = "foo"
      val userProvider = "bar"
      val accountNumber = "123456"
      val bankIdentifier = "qux"
      val bankName = "quxbank"

      //need to create the user for the bank accout creation process to work
      val user =
        APIUser.create.
          provider_(userProvider).
          providerId(userId).
          saveMe

      val msgContent = CreateBankAccount(userId, userProvider, accountNumber, bankIdentifier, bankName)


      //before the bank account is created, it should obviously have no holders
      Connector.connector.vend.getAccountHolders(bankName, accountNumber) should equal (Set.empty)

      BankAccountCreationListener.createBankAccountListener ! AMQPMessage(msgContent)

      //sleep to give the actor time to process the message
      Thread.sleep(5000)

      Then("The should be considered the account holder")
      Connector.connector.vend.getAccountHolders(bankName, accountNumber) should equal(Set(user))
    }
  }

}
