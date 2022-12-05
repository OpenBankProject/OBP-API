package code.bankaccountcreation

import code.TestServer
import code.accountholders.AccountHolders
import code.api.util.APIUtil
import code.api.util.ErrorMessages._
import code.views.Views
import net.liftweb.common.Full
import net.liftweb.mapper.By
import net.liftweb.util.Props
import org.scalatest.Tag
import com.tesobe.model.CreateBankAccount
import code.model.dataAccess.{BankAccountCreationListener, ResourceUser}
import net.liftmodules.amqp.AMQPMessage
import code.bankconnectors.Connector
import code.connector.MockedCbsConnector.{defaultProvider, resourceUser1Name, resourceUser1}
import code.model.UserX
import code.setup.{DefaultConnectorTestSetup, ServerSetup}
import code.users.Users
import com.openbankproject.commons.model.{BankId, User}

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

    val userProvider = defaultProvider
    val userProviderId = resourceUser1Name
    val userId1 = TestServer.userId1
    
    //need to create the user for the bank account creation process to work
    def getTestUser() = resourceUser1

    val expectedBankId = "quxbank"
    val accountNumber = "123456"

    def thenCheckAccountCreated(user: User) = {
      Then("An account with the proper parameters should be created")
      val userAccounts = Views.views.vend.getPrivateBankAccounts(user)
      userAccounts.size should equal(1)
      val createdAccount = userAccounts(0)

      //the account id should be randomly generated
      createdAccount.accountId.value.nonEmpty should be(true)

      createdAccount.bankId.value should equal(expectedBankId)
      createdAccount.accountId should equal(accountNumber)

      And("The account holder should be set correctly")
      AccountHolders.accountHolders.vend.getAccountHolders(BankId(expectedBankId), createdAccount.accountId) should equal(Set(user))
    }

    if (APIUtil.getPropsAsBoolValue("messageQueue.createBankAccounts", false) == false) {
      ignore("a bank account is created at a bank that does not yet exist", BankAccountCreationListenerTag) {}
      ignore("a bank account is created at a bank that already exists", BankAccountCreationListenerTag) {}
    } else {

      scenario("a bank account is created at a bank that does not yet exist", BankAccountCreationListenerTag) {
        val bankIdentifier = "qux"
        val user = getTestUser()

        Given("The account doesn't already exist")
        Views.views.vend.getPrivateBankAccounts(user).size should equal(0)

        And("The bank in question doesn't already exist")
        Connector.connector.vend.getBankLegacy(BankId(expectedBankId), None).map(_._1).isDefined should equal(false)

        When("We create a bank account")

        //using expectedBankId as the bank name should be okay as the behaviour should be to slugify the bank name to get the id
        //what to do if this slugification results in an id collision has not been determined yet
        val msgContent = CreateBankAccount(userProviderId, userProvider, accountNumber, bankIdentifier, expectedBankId)

        BankAccountCreationListener.createBankAccountListener ! AMQPMessage(msgContent)

        //sleep to give the actor time to process the message
        Thread.sleep(5000)

        thenCheckAccountCreated(user)

        And("A bank should be created")
        val createdBankBox = Connector.connector.vend.getBankLegacy(BankId(expectedBankId), None).map(_._1)
        createdBankBox.isDefined should equal(true)
        val createdBank = createdBankBox.openOrThrowException(attemptedToOpenAnEmptyBox)
        createdBank.nationalIdentifier should equal(bankIdentifier)

      }

      scenario("a bank account is created at a bank that already exists", BankAccountCreationListenerTag) {
        val user = getTestUser()
        Given("The account doesn't already exist")
        Views.views.vend.getPrivateBankAccounts(user).size should equal(0)

        And("The bank in question already exists")
        val createdBank = createBank(expectedBankId)

        When("We create a bank account")
        val msgContent = CreateBankAccount(userProviderId, userProvider, accountNumber, createdBank.nationalIdentifier, createdBank.bankId.value)

        BankAccountCreationListener.createBankAccountListener ! AMQPMessage(msgContent)

        //sleep to give the actor time to process the message
        Thread.sleep(5000)

        thenCheckAccountCreated(user)
      }
    }
  }
}
