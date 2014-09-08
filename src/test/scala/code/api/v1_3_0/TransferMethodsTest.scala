package code.api.v1_3_0

import code.api.DefaultUsers
import code.api.test.{APIResponse, ServerSetup}
import dispatch._

class TransferMethodsTest extends ServerSetup with DefaultUsers {

  val testBankId = "test-bank"
  val testAccountId = "test-account"

  override def specificSetup() = {
    //create a account whose transfer methods we can check
    val testBank = createBank(testBankId)
    createAccountAndOwnerView(Some(obpuser1), testBank, testAccountId, "EUR")
  }

  def getTransferMethods(bankId : String, accountId: String) : APIResponse = {
    val request = baseRequest / "obp" / "v1.3.0" / "banks" / bankId / "accounts" / accountId / "transfer-methods"
    makeGetRequest(request)
  }

  feature("Methods of making bank transfers") {

    scenario("Retrieval of all transfer methods for an account") {
      val response = getTransferMethods(testBankId, testAccountId)
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


    scenario("Retrieval of all transfer methods for an account that does not exist") {
      val response = getTransferMethods(testBankId, testAccountId)
      response.code should equal(404)
    }

  }

}
