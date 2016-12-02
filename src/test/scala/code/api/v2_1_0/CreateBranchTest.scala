package code.api.v2_1_0

import code.api.DefaultUsers
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanCreateBranch, CanCreateTransactionType}
import code.api.util.ErrorMessages
import code.branches.Branches.BranchId
import code.model.{AccountId, BankId, ViewId}
import code.sandbox._
import net.liftweb.json.JsonAST.{JField, JObject, JString}
import net.liftweb.json.Serialization.write

class CreateBranchTest extends V210ServerSetup with DefaultUsers {

  override def beforeAll() {
    super.beforeAll()
  }

  override def afterAll() {
    super.afterAll()
  }

  feature("Assuring that endpoint 'Update Branch' works as expected - v2.1.0") {

    val customerPutJSON = BranchJsonPut(
      "gh.29.fi",//bank_id",
      "OBP",//"name",
      SandboxAddressImport(
        "VALTATIE 8",
        "line_2",
        "line_3",
        "city",
        "county",
        "state",
        "123",
        "FI"),
      SandboxLocationImport(1.2, 2.1),
      SandboxMetaImport(
        SandboxLicenseImport("id","name")),
      Option(SandboxLobbyImport("hours")),
      Option(SandboxDriveUpImport("hours"))
    )

    scenario("Update branch successfully ") {

      Given("The Bank_ID and Branch_ID")
      val testBank = createBank("testBankId")
      val bankId = testBank.bankId
      val branchId = BranchId("1234")

      Then("We add entitlement to user1")
      addEntitlement(bankId.value, obpuser1.userId, CanCreateBranch.toString)
      val hasEntitlement = code.api.util.APIUtil.hasEntitlement(bankId.value, obpuser1.userId, CanCreateBranch)
      hasEntitlement should equal(true)

      When("We make the request Update Branch for an account")
      var requestPut = (v2_1Request / "banks" / bankId.value / "branches" / branchId.value ).PUT <@ (user1)
      var responsePut = makePutRequest(requestPut, write(customerPutJSON))

      Then("We should get a 201 and check all the fields")
      responsePut.code should equal(201)

      var nameResponse = (responsePut.body \ "name" ) match {
        case JString(i) => i
        case _ => ""
      }
      nameResponse should equal("OBP")
    }

    scenario("Update the same data, the data will be updated") {
      Given("The user ower access and BankAccount")
      val testBank = createBank("testBankId")
      val bankId = testBank.bankId
      val branchId = BranchId("1234")

      Then("We add entitlement to user1")
      addEntitlement(bankId.value, obpuser1.userId, CanCreateBranch.toString)
      val hasEntitlement = code.api.util.APIUtil.hasEntitlement(bankId.value, obpuser1.userId, CanCreateBranch)
      hasEntitlement should equal(true)

      When("We make the request Update Branch for an account")
      var requestPut = (v2_1Request / "banks" / bankId.value / "branches" / branchId.value ).PUT <@ (user1)
      var responsePut = makePutRequest(requestPut, write(customerPutJSON))


      val customerPutJSON2 = customerPutJSON.copy(name="OBP1")
      Then("We make the request Update Branch again ,with the same data")
      requestPut = (v2_1Request / "banks" / bankId.value / "branches" / branchId.value ).PUT <@ (user1)
      responsePut = makePutRequest(requestPut, write(customerPutJSON2))

      Then("We should get a 201 and check all the fields")
      responsePut.code should equal(201)

      var nameResponse = (responsePut.body \ "name" ) match {
        case JString(i) => i
        case _ => ""
      }
      nameResponse should equal("OBP1")
    }
  }

  feature("Assuring that endpoint 'Create Branch' works as expected - v2.1.0") {

    val customerPostJSON = BranchJsonPost(
      "15",
      "gh.29.fi",
      "OBP",
      SandboxAddressImport(
        "VALTATIE 8",
        "line_2",
        "line_3",
        "city",
        "county",
        "state",
        "123",
        "FI"),
      SandboxLocationImport(1.2, 2.1),
      SandboxMetaImport(
        SandboxLicenseImport("id","name")),
      Option(SandboxLobbyImport("hours")),
      Option(SandboxDriveUpImport("hours"))
    )

    scenario("Create branch successfully ") {

      Given("The user ower access and BankAccount")
      val testBank = createBank("testBankId")
      val bankId = testBank.bankId
      val accountId = AccountId("__acc1")
      val branchId = BranchId("1234")
      val viewId =ViewId("owner")
      val bankAccount = createAccountAndOwnerView(Some(obpuser1), bankId, accountId, "EUR")

      Then("We add entitlement to user1")
      addEntitlement(bankId.value, obpuser1.userId, CanCreateBranch.toString)
      val hasEntitlement = code.api.util.APIUtil.hasEntitlement(bankId.value, obpuser1.userId, CanCreateBranch)
      hasEntitlement should equal(true)


      When("We make the request Update Branch for an account")
      val requestPut = (v2_1Request / "banks" / bankId.value / "branches").POST <@ (user1)
      val responsePost = makePostRequest(requestPut, write(customerPostJSON))

      Then("We should get a 201 and check all the fields")
      responsePost.code should equal(201)

      var nameResponse = (responsePost.body \ "name" ) match {
        case JString(i) => i
        case _ => ""
      }
      nameResponse should equal("OBP")

    }

    scenario("Create the same data again, the data will be updated") {
      Given("The user ower access and BankAccount")
      val testBank = createBank("testBankId")
      val bankId = testBank.bankId
      val accountId = AccountId("__acc1")
      val branchId = BranchId("1234")
      val viewId =ViewId("owner")
      val bankAccount = createAccountAndOwnerView(Some(obpuser1), bankId, accountId, "EUR")


      Then("We add entitlement to user1")
      addEntitlement(bankId.value, obpuser1.userId, CanCreateBranch.toString)
      val hasEntitlement = code.api.util.APIUtil.hasEntitlement(bankId.value, obpuser1.userId, CanCreateBranch)
      hasEntitlement should equal(true)

      When("We make the request Update Branch for an account")
      var requestPost = (v2_1Request / "banks" / bankId.value / "branches").POST <@ (user1)
      var responsePost = makePostRequest(requestPost, write(customerPostJSON))

      Then("We should get a 201 and check all the fields")
      responsePost.code should equal(201)

      val customerPostJSON2 = customerPostJSON.copy(name="OBP1")
      Then("We make the request Update Branch again ,with the same data")
      requestPost = (v2_1Request / "banks" / bankId.value / "branches" / branchId.value ).PUT <@ (user1)
      responsePost = makePutRequest(requestPost, write(customerPostJSON2))

      Then("We should get a 201 and check all the fields")
      responsePost.code should equal(201)

      var nameResponse = (responsePost.body \ "name" ) match {
        case JString(i) => i
        case _ => ""
      }
      nameResponse should equal("OBP1")
    }
  }


}
