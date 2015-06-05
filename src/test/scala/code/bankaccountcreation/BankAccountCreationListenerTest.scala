package code.bankaccountcreation

import code.api.DefaultConnectorTestSetup
import code.api.test.ServerSetup
import code.model.{User, BankId}
import code.views.Views
import net.liftweb.common.Full
import net.liftweb.mapper.By
import net.liftweb.util.Props
import org.scalatest.Tag
import com.tesobe.model.CreateBankAccount
import code.model.dataAccess.{APIUser, BankAccountCreationListener}
import net.liftmodules.amqp.AMQPMessage
import code.bankconnectors.Connector

class BankAccountCreationListenerTest extends ServerSetup with DefaultConnectorTestSetup {

  object BankAccountCreationListenerTag extends Tag("bank_account_creation_listener")

  override def beforeEach() = {
    super.beforeEach()
    wipeTestData()
  }

  override def afterEach() = {
    super.afterEach()
    wipeTestData()
  }

  feature("Bank account creation via AMQP messages") {

    val userId = "foo"
    val userProvider = "bar"

    //need to create the user for the bank accout creation process to work
    def getTestUser() =
      APIUser.find(By(APIUser.provider_, userProvider), By(APIUser.providerId, userId)).getOrElse {
        APIUser.create.
          provider_(userProvider).
          providerId(userId).
          saveMe
      }

    val expectedBankId = "quxbank"
    val accountNumber = "123456"

    def thenCheckAccountCreated(user: User) = {
      Then("An account with the proper parameters should be created")
      val userAccounts = Views.views.vend.getAllAccountsUserCanSee(Full(user))
      userAccounts.size should equal(1)
      val createdAccount = userAccounts(0)

      //the account id should be randomly generated
      createdAccount.accountId.value.nonEmpty should be(true)

      createdAccount.bankId.value should equal(expectedBankId)
      createdAccount.number should equal(accountNumber)

      And("The account holder should be set correctly")
      Connector.connector.vend.getAccountHolders(BankId(expectedBankId), createdAccount.accountId) should equal(Set(user))
    }

    if (Props.getBool("bank_account_creation_listener", true) == false) {
      ignore("a bank account is created at a bank that does not yet exist", BankAccountCreationListenerTag) {}
      ignore("a bank account is created at a bank that already exists", BankAccountCreationListenerTag) {}
    } else {

      scenario("a bank account is created at a bank that does not yet exist", BankAccountCreationListenerTag) {
        val bankIdentifier = "qux"
        val user = getTestUser()

        Given("The account doesn't already exist")
        Views.views.vend.getAllAccountsUserCanSee(Full(user)).size should equal(0)

        And("The bank in question doesn't already exist")
        Connector.connector.vend.getBank(BankId(expectedBankId)).isDefined should equal(false)

        When("We create a bank account")

        //using expectedBankId as the bank name should be okay as the behaviour should be to slugify the bank name to get the id
        //what to do if this slugification results in an id collision has not been determined yet
        val msgContent = CreateBankAccount(userId, userProvider, accountNumber, bankIdentifier, expectedBankId)

        BankAccountCreationListener.createBankAccountListener ! AMQPMessage(msgContent)

        //sleep to give the actor time to process the message
        Thread.sleep(5000)

        thenCheckAccountCreated(user)

        And("A bank should be created")
        val createdBankBox = Connector.connector.vend.getBank(BankId(expectedBankId))
        createdBankBox.isDefined should equal(true)
        val createdBank = createdBankBox.get
        createdBank.nationalIdentifier should equal(bankIdentifier)

      }

      scenario("a bank account is created at a bank that already exists", BankAccountCreationListenerTag) {
        val user = getTestUser()
        Given("The account doesn't already exist")
        Views.views.vend.getAllAccountsUserCanSee(Full(user)).size should equal(0)

        And("The bank in question already exists")
        val createdBank = createBank(expectedBankId)

        When("We create a bank account")
        val msgContent = CreateBankAccount(userId, userProvider, accountNumber, createdBank.nationalIdentifier, createdBank.bankId.value)

        BankAccountCreationListener.createBankAccountListener ! AMQPMessage(msgContent)

        //sleep to give the actor time to process the message
        Thread.sleep(5000)

        thenCheckAccountCreated(user)
      }
    }
  }
}
