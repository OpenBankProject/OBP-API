package code.api.v1_3_0

import code.api.DefaultUsers
import code.api.test.{APIResponse, ServerSetup}
import code.util.APIUtil.OAuth._
import dispatch._
import org.scalatest.Tag


class TransferMethodsTest extends ServerSetup with DefaultUsers with Tags1_3_0 {

  object GetTransferMethods extends Tag("getTransferMethods")

  type OAuthCredentials = Option[(Consumer, Token)]

  val testBankId = "test-bank"
  val testAccountId = "test-account"

  override def specificSetup() = {
    //create a account whose transfer methods we can check
    val testBank = createBank(testBankId)
    createAccountAndOwnerView(Some(obpuser1), testBank, testAccountId, "EUR")
  }

  def getTransferMethods(bankId : String, accountId: String, credentials : OAuthCredentials) : APIResponse = {
    val request = baseRequest / "obp" / "v1.3.0" / "banks" / bankId / "accounts" / accountId / "transfer-methods" <@ (credentials)
    makeGetRequest(request)
  }

  feature("Methods of making bank transfers") {

    scenario("Retrieval of all transfer methods for an account by a user with access to a view with " +
      "initiate transaction privileges", API1_3_0, GetTransferMethods) {
      Given("The call is made by an authenticated user with")
      //user1 (obpuser1) has owner view access
      val response = getTransferMethods(testBankId, testAccountId, user1)
      response.code should equal(200)

      val transferMethods = response.body.extract[TransferMethodsJSON1_3_0]

      //there should only be one transfer method: sandbox
      transferMethods.transfer_methods.size should equal(1)

      val sandbox = transferMethods.transfer_methods(0)
      sandbox.permalink should equal("sandbox")
      sandbox.resource_URL should equal(s"/obp/v1.3.0/banks/$testBankId/accounts/$testAccountId/transfer-methods/sandbox")

      val sandboxBody = sandbox.body.extract[SandboxTransferMethodBodyJSON1_3_0]
      val to = sandboxBody.to
      to.account_id.isEmpty should equal(false)
      to.bank_id.isEmpty should equal(false)
      sandboxBody.amount.isEmpty should equal(false)
    }


    scenario("Retrieval of all transfer methods for an account that does not exist",
      API1_3_0, GetTransferMethods) {
      val response = getTransferMethods(testBankId, "doesnotexist", user1)
      //400 response code is consistent with how the rest of the api works when no account is found,
      //but this should probably change to a 404...
      response.code should equal(400)
    }

    scenario("Retrieval of all transfer methods for an account by a user without access" +
      " to a view with initiate transaction privileges", API1_3_0, GetTransferMethods) {
      //user2 (obpuser2) has no access
      val response = getTransferMethods(testBankId, testAccountId, user2)
      response.code should equal(401)
    }

  }

}
