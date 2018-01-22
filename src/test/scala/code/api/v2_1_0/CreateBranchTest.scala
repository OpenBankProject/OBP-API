package code.api.v2_1_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ApiRole.CanCreateBranch
import code.api.v1_4_0.JSONFactory1_4_0._
import code.branches.Branches.BranchId
import code.model.{AccountId, ViewId}
import code.setup.DefaultUsers
import net.liftweb.json.JsonAST.JString
import net.liftweb.json.Serialization.write

class CreateBranchTest extends V210ServerSetup with DefaultUsers {

  override def beforeAll() {
    super.beforeAll()
  }

  override def afterAll() {
    super.afterAll()
  }

  feature("Assuring that endpoint 'Update Branch' works as expected - v2.1.0") {

    val customerPutJSON = BranchJsonPutV210(
      "gh.29.fi", "OBP",
      AddressJsonV140("VALTATIE 8", "", "", "AKAA", "", "", "DE"),
      LocationJsonV140(1.2, 2.1),
      MetaJsonV140(LicenseJsonV140("", "")),
      LobbyStringJson(""),
      DriveUpStringJson("")
    )
    scenario("Update branch successfully ") {

      Given("The Bank_ID and Branch_ID")
      val testBank = createBank("testBankId")
      val bankId = testBank.bankId
      val branchId = BranchId("1234")

      Then("We add entitlement to user1")
      addEntitlement(bankId.value, resourceUser1.userId, CanCreateBranch.toString)
      val hasEntitlement = code.api.util.APIUtil.hasEntitlement(bankId.value, resourceUser1.userId, ApiRole.canCreateBranch)
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
      addEntitlement(bankId.value, resourceUser1.userId, CanCreateBranch.toString)
      val hasEntitlement = code.api.util.APIUtil.hasEntitlement(bankId.value, resourceUser1.userId, ApiRole.canCreateBranch)
      hasEntitlement should equal(true)

      When("We make the request Update Branch for an account")
      var requestPut = (v2_1Request / "banks" / bankId.value / "branches" / branchId.value ).PUT <@ (user1)
      var responsePut = makePutRequest(requestPut, write(customerPutJSON))


      val customerPutJSON2 = customerPutJSON.copy(name="OBP1")
      Then("We make the request Update Branch again, with the same data")
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

    val customerPostJSON = BranchJsonPostV210("123","gh.29.fi", "OBP",
      AddressJsonV140("VALTATIE 8", "", "", "AKAA", "", "", "DE"),
      LocationJsonV140(1.2, 2.1),
      MetaJsonV140(LicenseJsonV140("", "")),
      LobbyStringJson(""),
      DriveUpStringJson("")
    )

    scenario("Create branch successfully ") {

      Given("The user ower access and BankAccount")
      val testBank = createBank("testBankId")
      val bankId = testBank.bankId
      val accountId = AccountId("__acc1")
      val branchId = BranchId("1234")
      val viewId =ViewId("owner")
      val bankAccount = createAccountAndOwnerView(Some(resourceUser1), bankId, accountId, "EUR")

      Then("We add entitlement to user1")
      addEntitlement(bankId.value, resourceUser1.userId, CanCreateBranch.toString)
      val hasEntitlement = code.api.util.APIUtil.hasEntitlement(bankId.value, resourceUser1.userId, ApiRole.canCreateBranch)
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
      val bankAccount = createAccountAndOwnerView(Some(resourceUser1), bankId, accountId, "EUR")


      Then("We add entitlement to user1")
      addEntitlement(bankId.value, resourceUser1.userId, CanCreateBranch.toString)
      val hasEntitlement = code.api.util.APIUtil.hasEntitlement(bankId.value, resourceUser1.userId, ApiRole.canCreateBranch)
      hasEntitlement should equal(true)

      When("We make the request Update Branch for an account")
      var requestPost = (v2_1Request / "banks" / bankId.value / "branches").POST <@ (user1)
      var responsePost = makePostRequest(requestPost, write(customerPostJSON))

      Then("We should get a 201 and check all the fields")
      responsePost.code should equal(201)

      val customerPostJSON2 = customerPostJSON.copy(name="OBP1")
      Then("We make the request Update Branch again, with the same data")
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
