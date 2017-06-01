package code.management

import code.api._
import code.api.v1_2_1._
import code.bankconnectors.Connector
import code.model.{AccountId, BankId}
import code.api.util.APIUtil.OAuth.{Consumer, Token, _}
import code.setup.{APIResponse, DefaultUsers, PrivateUser2Accounts, User1AllPrivileges}
import net.liftweb.common.Empty
import org.scalatest.Tag

class AccountsAPITest extends API1_2_1Test with User1AllPrivileges with DefaultUsers with PrivateUser2Accounts {

  //define Tags
  object Management extends Tag("Management")
  object DeleteBankAccount extends Tag("deleteBankAccount")
  
  def managementRequest = baseRequest / "internal" / "v1.0"

  def deleteBankAccount(bankId : String, accountId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (managementRequest / "banks" / bankId / "accounts" / accountId).DELETE <@ (consumerAndToken)
    makeDeleteRequest(request)
  }

  val OK: Int = 200
  val OK_NO_CONTENT: Int = 204
  val CREATED: Int = 201
  val BAD_REQUEST: Int = 400
  val SERVER_ERROR: Int = 500

  //Tests start here

  feature("Delete an account resource") {
    scenario("User deletes one of his private accounts", Management, DeleteBankAccount) {
      accountTestsSpecificDBSetup()

      //get an account
      val reply = getPrivateAccountsForAllBanks(consumerAndToken = user1)
      reply.code should equal(OK)

      //get one of those
      val account = reply.body.extract[AccountsJSON].accounts.head

      //delete the account
      val response = deleteBankAccount(bankId = account.bank_id, accountId = account.id, consumerAndToken = user1)
      response.code should equal(OK_NO_CONTENT)

      //check that it's gone
      Connector.connector.vend.getBankAccount(BankId(account.bank_id), AccountId(account.id)) should equal(Empty)
    }

    scenario("User tries to delete a private account of another user", Management, DeleteBankAccount) {
      accountTestsSpecificDBSetup()

      //get an account
      val reply = getPrivateAccountsForAllBanks(consumerAndToken = user2)
      reply.code should equal(OK)

      //get one of those
      val account = reply.body.extract[AccountsJSON].accounts.head

      When("Deleting the account with another user that does not have owner permissions")
      val response = deleteBankAccount(bankId = account.bank_id, accountId = account.id, consumerAndToken = user1)
      response.code should equal(SERVER_ERROR)

      Then("The account should still be there")
      Connector.connector.vend.getBankAccount(BankId(account.bank_id), AccountId(account.id)) should not equal(Empty)
    }
  }
}