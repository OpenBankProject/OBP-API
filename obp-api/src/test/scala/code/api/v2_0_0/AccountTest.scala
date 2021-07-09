package code.api.v2_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages
import code.setup.{DefaultUsers, PrivateUser2AccountsAndSetUpWithTestData}
import com.openbankproject.commons.model.{AmountOfMoneyJsonV121 => AmountOfMoneyJSON121}
import net.liftweb.json.JsonAST._
import net.liftweb.json.Serialization.write

class AccountTest extends V200ServerSetup with DefaultUsers with PrivateUser2AccountsAndSetUpWithTestData {
  val mockAccountId1 = "NEW_ACCOUNT_ID_01"
  val mockAccountLabel1 = "NEW_ACCOUNT_LABEL_01"
  
  feature("Assuring that Get all accounts at all banks works as expected - v2.0.0") {

    scenario("We create an account and get accounts as anonymous and then as authenticated user - allAccountsAllBanks") {
      Given("The bank")
      val testBank = testBankId1
      val accountPutJSON = CreateAccountJSON(resourceUser1.userId, "CURRENT", mockAccountLabel1, AmountOfMoneyJSON121("EUR", "0"))
      val requestPut = (v2_0Request / "banks" / testBank.value / "accounts" / mockAccountId1).PUT <@ (user1)
      val responsePut = makePutRequest(requestPut, write(accountPutJSON))

      And("We should get a 200")
      responsePut.code should equal(200)
// TODO Make this work
//      When("We make the anonymous access request")
//      val requestGet = (v2_0Request / "accounts").GET
//      val responseGet = makeGetRequest(requestGet)
//
//      Then("We should get a 200")
//      responseGet.code should equal(200)
//
//      val isPublic: List[Boolean] =
//        for {
//          JObject(o) <- responseGet.body
//          JField("is_public", JBool(isPublic)) <- o
//        } yield {
//          isPublic
//        }
//      And("All received accounts have to be public")
//      isPublic.forall(_ == true) should equal(true)

      When("We make the authenticated access request")
      val requestGetAll = (v2_0Request / "accounts").GET <@ (user1)
      val responseGetAll = makeGetRequest(requestGetAll)

      Then("We should get a 200")
      responseGetAll.code should equal(200)

      val isPublicAll =
        for {
          obj@JObject(o) <- responseGetAll.body
          if (o contains JField("id", JString(mockAccountId1)))
          JBool(isPublic) <- obj \\ "is_public"
        } yield {
          isPublic
        }
      And("The new created account has to be private")
      isPublicAll.forall(_ == false) should equal(true)
    }

    scenario("We create an account and get accounts as anonymous and then as authenticated user - allAccountsAtOneBank") {
      Given("The bank")
      val testBank = testBankId1

      Then("We create an private account at the bank")
      val accountPutJSON = CreateAccountJSON(resourceUser1.userId,"CURRENT", mockAccountLabel1, AmountOfMoneyJSON121("EUR", "0"))
      val requestPut = (v2_0Request / "banks" / testBank.value / "accounts" / mockAccountId1).PUT <@ (user1)
      val responsePut = makePutRequest(requestPut, write(accountPutJSON))

      And("We should get a 200")
      responsePut.code should equal(200)
//      TODO Make this work
//      When("We make the anonymous access request")
//      val requestGet = (v2_0Request / "banks" / testBank.value / "accounts").GET
//      val responseGet = makeGetRequest(requestGet)
//
//      Then("We should get a 200")
//      responseGet.code should equal(200)
//
//      val isPublic: List[Boolean] =
//        for {
//          JObject(o) <- responseGet.body
//          JField("is_public", JBool(isPublic)) <- o
//        } yield {
//          isPublic
//        }
//      And("All received accounts have to be public")
//      isPublic.forall(_ == true) should equal(true)

      When("We make the authenticated access request")
      val requestGetAll = (v2_0Request / "banks" / testBank.value / "accounts").GET <@ (user1)
      val responseGetAll = makeGetRequest(requestGetAll)

      Then("We should get a 200")
      responseGetAll.code should equal(200)

      val isPublicAll =
        for {
          obj@JObject(o) <- responseGetAll.body
          if (o contains JField("id", JString(testAccountId1.value)))
          JBool(isPublic) <- obj \\ "is_public"
        } yield {
          isPublic
        }
      And("The new created account has to be private")
      isPublicAll.forall(_ == false) should equal(true)
    }

    scenario("We create an account, but with wrong format of account_id ") {
      Given("The bank")
      val testBank = testBankId1
      val newAccountIdWithSpaces = "account%20with%20spaces"

      Then("We create an private account at the bank")
      val accountPutJSON = CreateAccountJSON(resourceUser1.userId, "CURRENT", mockAccountLabel1, AmountOfMoneyJSON121("EUR", "0"))
      val requestPut = (v2_0Request / "banks" / testBank.value / "accounts" / newAccountIdWithSpaces).PUT <@ (user1)
      val responsePut = makePutRequest(requestPut, write(accountPutJSON))

      And("We should get a 400")
      responsePut.code should equal(400)
      And("We should have the error massage")
      val error: String = (responsePut.body \ "message") match {
        case JString(i) => i
        case _ => ""
      }
      Then("We should have the error: " + ErrorMessages.InvalidAccountIdFormat)
      error.toString contains (ErrorMessages.InvalidAccountIdFormat) should be (true)
    }
  }

  feature("Information about the public bank accounts for all banks"){
    scenario("we get the public bank accounts"){
      accountTestsSpecificDBSetup()
      Given("We will not use an access token")
      When("the request is sent")
      val reply = getPublicAccountsForAllBanks()
      Then("we should get a 200 ok code")
      reply.code should equal (200)
      val publicAccountsInfo = reply.body.extract[BasicAccountsJSON]
      And("some fields should not be empty")
      publicAccountsInfo.accounts.foreach(a => {
        a.id.nonEmpty should equal (true)
        a.views_available.nonEmpty should equal (true)
        a.views_available.foreach(
          //check that all the views are public
          v => v.is_public should equal (true)
        )
      })
    }
  }

}
