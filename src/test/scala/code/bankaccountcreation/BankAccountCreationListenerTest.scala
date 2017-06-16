package code.bankaccountcreation

import code.model.{BankId, User}
import code.views.Views
import net.liftweb.common.Full
import net.liftweb.mapper.By
import net.liftweb.util.Props
import org.scalatest.Tag
import com.tesobe.model.CreateBankAccount
import code.model.dataAccess.{BankAccountCreationListener, ResourceUser}
import net.liftmodules.amqp.AMQPMessage
import code.bankconnectors.Connector
import code.setup.{DefaultConnectorTestSetup, ServerSetup}
import code.users.Users

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
      Users.users.vend.getUserByProviderId(userProvider, userId).getOrElse {
        Users.users.vend.createResourceUser(userProvider, Some(userId), None, None, None) match {
          case Full(u) => u
        }
      }

    val expectedBankId = "quxbank"
    val accountNumber = "123456"

    def thenCheckAccountCreated(user: User) = {
      Then("An account with the proper parameters should be created")
      val userAccounts = Views.views.vend.getAllAccountsUserCanSee(Full(user))
      userAccounts.size must equal(1)
      val createdAccount = userAccounts(0)

      //the account id must be randomly generated
      createdAccount.accountId.value.nonEmpty must be(true)

      createdAccount.bankId.value must equal(expectedBankId)
      createdAccount.accountId must equal(accountNumber)

      And("The account holder must be set correctly")
      Connector.connector.vend.getAccountHolders(BankId(expectedBankId), createdAccount.accountId) must equal(Set(user))
    }

    if (Props.getBool("messageQueue.createBankAccounts", false) == false) {
      ignore("a bank account is created at a bank that does not yet exist", BankAccountCreationListenerTag) {}
      ignore("a bank account is created at a bank that already exists", BankAccountCreationListenerTag) {}
    } else {

      scenario("a bank account is created at a bank that does not yet exist", BankAccountCreationListenerTag) {
        val bankIdentifier = "qux"
        val user = getTestUser()

        Given("The account doesn't already exist")
        Views.views.vend.getAllAccountsUserCanSee(Full(user)).size must equal(0)

        And("The bank in question doesn't already exist")
        Connector.connector.vend.getBank(BankId(expectedBankId)).isDefined must equal(false)

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
        createdBankBox.isDefined must equal(true)
        val createdBank = createdBankBox match {
          case Full(cb) => cb
        }
        createdBank.nationalIdentifier must equal(bankIdentifier)

      }

      scenario("a bank account is created at a bank that already exists", BankAccountCreationListenerTag) {
        val user = getTestUser()
        Given("The account doesn't already exist")
        Views.views.vend.getAllAccountsUserCanSee(Full(user)).size must equal(0)

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
