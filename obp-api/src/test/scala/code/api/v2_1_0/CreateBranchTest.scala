package code.api.v2_1_0

import org.json4s._
import code.api.util.APIUtil.OAuth._
import code.api.util.{APIUtil, ApiRole}
import code.api.util.ApiRole.{CanCreateBranch, CanUpdateBranch}
import code.api.v1_4_0.JSONFactory1_4_0._
import code.setup.DefaultUsers
import com.openbankproject.commons.model.{AccountId, BranchId, ViewId}
import org.json4s.JsonAST.JString
import org.json4s.native.Serialization.write

class CreateBranchTest extends V210ServerSetup with DefaultUsers {

  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
  }

  Feature("Assuring that endpoint 'Update Branch' works as expected - v2.1.0") {

    Scenario("Update branch successfully ") {

      Given("The Bank_ID and Branch_ID")
      val testBank = createBank("testBankId")
      val bankId = testBank.bankId
      val branchId = BranchId("1234")

      Then("We add entitlement to user1")
      addEntitlement(bankId.value, resourceUser1.userId, CanUpdateBranch.toString)
      val hasEntitlement = APIUtil.hasEntitlement(bankId.value, resourceUser1.userId, ApiRole.canUpdateBranch)
      hasEntitlement should equal(true)

      When("We make the request Update Branch for an account")
      val customerPutJSON = BranchJsonPutV210(
        testBank.bankId.value, "OBP",
        AddressJsonV140("VALTATIE 8", "", "", "AKAA", "", "", "DE"),
        LocationJsonV140(1.2, 2.1),
        MetaJsonV140(LicenseJsonV140("", "")),
        LobbyStringJson(""),
        DriveUpStringJson("")
      )
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

    Scenario("Update the same data, the data will be updated") {
      Given("The user ower access and BankAccount")
      val testBank = createBank("testBankId")
      val bankId = testBank.bankId
      val branchId = BranchId("1234")

      Then("We add entitlement to user1")
      val customerPutJSON = BranchJsonPutV210(
        testBank.bankId.value, "OBP",
        AddressJsonV140("VALTATIE 8", "", "", "AKAA", "", "", "DE"),
        LocationJsonV140(1.2, 2.1),
        MetaJsonV140(LicenseJsonV140("", "")),
        LobbyStringJson(""),
        DriveUpStringJson("")
      )
      addEntitlement(bankId.value, resourceUser1.userId, CanUpdateBranch.toString)
      val hasEntitlement = APIUtil.hasEntitlement(bankId.value, resourceUser1.userId, ApiRole.canUpdateBranch)
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

  Feature("Assuring that endpoint 'Create Branch' works as expected - v2.1.0") {

 

    Scenario("Create branch successfully ") {

      Given("The user ower access and BankAccount")
      val testBank = createBank("testBankId")
      val bankId = testBank.bankId

      Then("We add entitlement to user1")
      val customerPostJSON = BranchJsonPostV210(
        "123",
        bankId.value,
        "OBP",
        AddressJsonV140("VALTATIE 8", "", "", "AKAA", "", "", "DE"),
        LocationJsonV140(1.2, 2.1),
        MetaJsonV140(LicenseJsonV140("", "")),
        LobbyStringJson(""),
        DriveUpStringJson("")
      )
      addEntitlement(bankId.value, resourceUser1.userId, CanCreateBranch.toString)
      val hasEntitlement = APIUtil.hasEntitlement(bankId.value, resourceUser1.userId, ApiRole.canCreateBranch)
      hasEntitlement should equal(true)


      When("We make the request Update Branch for an account")
      val requestPost = (v2_1Request / "banks" / bankId.value / "branches").POST <@ (user1)
      val responsePost = makePostRequest(requestPost, write(customerPostJSON))

      Then("We should get a 201 and check all the fields")
      responsePost.code should equal(201)

      var nameResponse = (responsePost.body \ "name" ) match {
        case JString(i) => i
        case _ => ""
      }
      nameResponse should equal("OBP")

    }

    Scenario("Create the same data again, the data will be updated") {
      Given("The user ower access and BankAccount")
      val testBank = createBank("testBankId")
      val bankId = testBank.bankId
      val accountId = AccountId("__acc1")
      val branchId = BranchId("1234")


      Then("We add `CanCreateBranch`entitlement to user1")
      val customerPostJSON = BranchJsonPostV210(
        "123",
        bankId.value,
        "OBP",
        AddressJsonV140("VALTATIE 8", "", "", "AKAA", "", "", "DE"),
        LocationJsonV140(1.2, 2.1),
        MetaJsonV140(LicenseJsonV140("", "")),
        LobbyStringJson(""),
        DriveUpStringJson("")
      )
      addEntitlement(bankId.value, resourceUser1.userId, CanCreateBranch.toString)
      val hasEntitlement = APIUtil.hasEntitlement(bankId.value, resourceUser1.userId, ApiRole.canCreateBranch)
      hasEntitlement should equal(true)

      When("We make the request Update Branch for an account")
      var requestPost = (v2_1Request / "banks" / bankId.value / "branches").POST <@ (user1)
      var responsePost = makePostRequest(requestPost, write(customerPostJSON))

      Then("We should get a 201 and check all the fields")
      responsePost.code should equal(201)

      val customerPostJSON2 = customerPostJSON.copy(name="OBP1")
      Then("We make the request Update Branch again, with the same data")
      Then("We add `CanCreateBranch`entitlement to user1")
      
      addEntitlement(bankId.value, resourceUser1.userId, CanUpdateBranch.toString)
      val hasCanUpdateBranchEntitlement = APIUtil.hasEntitlement(bankId.value, resourceUser1.userId, ApiRole.canUpdateBranch)
      hasCanUpdateBranchEntitlement should equal(true)
      
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
