package code.bankaccountcreation

import code.api.test.ServerSetup
import code.model.{Consumer => OBPConsumer, Token => OBPToken, User, BankAccount, AccountId, BankId}
import org.scalatest.Tag
import com.tesobe.model.CreateBankAccount
import code.model.dataAccess.{HostedBank, APIUser, BankAccountCreationListener}
import net.liftmodules.amqp.AMQPMessage
import net.liftweb.mapper.By
import code.bankconnectors.Connector

class BankAccountCreationListenerTest extends ServerSetup {

  object AccountHolderSetup extends Tag("account_holder_setup")

  feature("The account holder gets properly set when a bank account is created"){
    scenario("a bank account is created", AccountHolderSetup) {

      When("We create a bank account")
      val userId = "foo"
      val userProvider = "bar"
      val accountNumber = "123456"
      val bankIdentifier = "qux"
      val expectedBankId = "quxbank"

      //need to create the user for the bank account creation process to work
      val user =
        APIUser.create.
          provider_(userProvider).
          providerId(userId).
          saveMe

      val msgContent = CreateBankAccount(userId, userProvider, accountNumber, bankIdentifier, expectedBankId)

      val u = User.findByApiId(user.apiId.value)

      def accountsAtNewBank() =  BankAccount.accounts(u).filter(a => a.bankId == BankId(expectedBankId))

      //there should be no accounts at this new bank
      accountsAtNewBank().size should equal(0)

      BankAccountCreationListener.createBankAccountListener ! AMQPMessage(msgContent)

      //sleep to give the actor time to process the message
      Thread.sleep(5000)

      //Need to figure out what the generated account id is

      val accountsAtNewBankAfter = accountsAtNewBank()
      //one account should be created
      accountsAtNewBankAfter.size should equal(1)

      val createdAccountId = accountsAtNewBankAfter(0).accountId

      Then("The should be considered the account holder")
      Connector.connector.vend.getAccountHolders(BankId(expectedBankId), createdAccountId) should equal(Set(user))
    }
  }

}
