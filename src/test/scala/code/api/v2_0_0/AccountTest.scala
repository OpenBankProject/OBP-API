package code.api.v2_0_0

import java.text.SimpleDateFormat

import code.api.DefaultUsers
import code.api.util.APIUtil.OAuth._
import code.api.v1_2_1.{AmountOfMoneyJSON => AmountOfMoneyJSON121}
import code.model.BankId
import code.model.dataAccess.MappedBankAccount
import net.liftweb.json.Serialization.write
import net.liftweb.json.JsonAST._

class AccountTest extends V200ServerSetup with DefaultUsers {

  val exampleDateString: String = "22/08/2013"
  val simpleDateFormat: SimpleDateFormat = new SimpleDateFormat("dd/mm/yyyy")
  val exampleDate = simpleDateFormat.parse(exampleDateString)

  val mockBankId = BankId("testBank1")
  val newAccountId1 = "NEW_ACCOUNT_ID_01"
  val newAccountLabel1 = "NEW_ACCOUNT_LABEL_01"


  override def beforeAll() {
    super.beforeAll()
  }

  override def afterAll() {
    super.afterAll()
    MappedBankAccount.bulkDelete_!!()
  }

  feature("Assuring that Get all accounts at all banks works as expected - v2.0.0") {

    scenario("We create an account and get accounts as anonymous and then as authenticated user - allAccountsAllBanks") {
      Given("The bank")
      val testBank = mockBankId

      Then("We create an private account at the bank")
      val accountPutJSON = CreateAccountJSON(obpuser1.userId, "CURRENT", newAccountLabel1, AmountOfMoneyJSON121("EUR", "0"))
      val requestPut = (v2_0Request / "banks" / testBank.value / "accounts" / newAccountId1).PUT <@ (user1)
      val responsePut = makePutRequest(requestPut, write(accountPutJSON))

      And("We should get a 200")
      responsePut.code should equal(200)

      When("We make the anonymous access request")
      val requestGet = (v2_0Request / "accounts").GET
      val responseGet = makeGetRequest(requestGet)

      Then("We should get a 200")
      responseGet.code should equal(200)

      val isPublic: List[Boolean] =
        for {
          JObject(o) <- responseGet.body
          JField("is_public", JBool(isPublic)) <- o
        } yield {
          isPublic
        }
      And("All received accounts have to be public")
      isPublic.forall(_ == true) should equal(true)

      When("We make the authenticated access request")
      val requestGetAll = (v2_0Request / "accounts").GET <@ (user1)
      val responseGetAll = makeGetRequest(requestGetAll)

      Then("We should get a 200")
      responseGetAll.code should equal(200)

      val isPublicAll =
        for {
          obj@JObject(o) <- responseGetAll.body
          if (o contains JField("id", JString(newAccountId1)))
          JBool(isPublic) <- obj \\ "is_public"
        } yield {
          isPublic
        }
      And("The new created account has to be private")
      isPublicAll.forall(_ == false) should equal(true)
    }

    scenario("We create an account and get accounts as anonymous and then as authenticated user - allAccountsAtOneBank") {
      Given("The bank")
      val testBank = mockBankId

      Then("We create an private account at the bank")
      val accountPutJSON = CreateAccountJSON(obpuser1.userId,"CURRENT", newAccountLabel1, AmountOfMoneyJSON121("EUR", "0"))
      val requestPut = (v2_0Request / "banks" / testBank.value / "accounts" / newAccountId1).PUT <@ (user1)
      val responsePut = makePutRequest(requestPut, write(accountPutJSON))

      And("We should get a 200")
      responsePut.code should equal(200)

      When("We make the anonymous access request")
      val requestGet = (v2_0Request / "banks" / testBank.value / "accounts").GET
      val responseGet = makeGetRequest(requestGet)

      Then("We should get a 200")
      responseGet.code should equal(200)

      val isPublic: List[Boolean] =
        for {
          JObject(o) <- responseGet.body
          JField("is_public", JBool(isPublic)) <- o
        } yield {
          isPublic
        }
      And("All received accounts have to be public")
      isPublic.forall(_ == true) should equal(true)

      When("We make the authenticated access request")
      val requestGetAll = (v2_0Request / "banks" / testBank.value / "accounts").GET <@ (user1)
      val responseGetAll = makeGetRequest(requestGetAll)

      Then("We should get a 200")
      responseGetAll.code should equal(200)

      val isPublicAll =
        for {
          obj@JObject(o) <- responseGetAll.body
          if (o contains JField("id", JString(newAccountId1)))
          JBool(isPublic) <- obj \\ "is_public"
        } yield {
          isPublic
        }
      And("The new created account has to be private")
      isPublicAll.forall(_ == false) should equal(true)
    }
  }

}
