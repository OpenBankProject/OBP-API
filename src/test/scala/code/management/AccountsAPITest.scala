package code.management

import code.api.util.APIUtil.OAuth._
import code.api.v1_2_1.AccountsJSON
import code.api.{PrivateUser2Accounts, DefaultUsers, User1AllPrivileges}
import code.api.test.APIResponse
import code.model.BankId
import dispatch._
import code.bankconnectors.Connector
import net.liftweb.common.Empty
import org.scalatest.Tag


/**
 * Created by stefan on 16.04.15.
 */

class AccountsAPITest extends User1AllPrivileges with DefaultUsers with PrivateUser2Accounts {

  //define Tags
  object Management extends Tag("Management")
  object DeleteBankAccount extends Tag("deleteBankAccount")

  //some helpers
  def v1_2Request = baseRequest / "obp" / "v1.2.1"
  def managementRequest = baseRequest / "obp" / "vmanagement" / "v1.0"

  def deleteBankAccount(bankId : String, accountId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (managementRequest / "banks" / bankId / "accounts" / accountId).DELETE <@ (consumerAndToken)
    makeDeleteRequest(request)
  }

  def getBankAccountsForAllBanks(consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v1_2Request / "accounts").GET <@(consumerAndToken)
    makeGetRequest(request)
  }

  def getPublicAccountsForAllBanks() : APIResponse= {
    val request = (v1_2Request / "accounts" / "public").GET
    makeGetRequest(request)
  }

  val OK: Int = 200
  val OK_NO_CONTENT: Int = 204
  val CREATED: Int = 201
  val BAD_REQUEST: Int = 400

  //Tests start here

  feature("Delete an account resource") {
    scenario("We have some accounts", Management, DeleteBankAccount) {
      accountTestsSpecificDBSetup()

      //get an account
      val reply = getPublicAccountsForAllBanks
      reply.code should equal(OK)

      //get one of those
      val account = reply.body.extract[AccountsJSON].accounts.head

      //delete it
      val response = deleteBankAccount(bankId = account.bank_id, accountId = account.id, consumerAndToken = user1)
      response.code should equal(OK_NO_CONTENT)

      //check that it's gone
      Connector.connector.vend.getBank(BankId(account.bank_id)) should equal(Empty)
    }
  }
}