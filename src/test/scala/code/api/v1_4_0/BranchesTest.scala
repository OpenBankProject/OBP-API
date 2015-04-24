package code.api.v1_4_0

import code.api.v1_4_0.JSONFactory1_4_0.{BranchJson, BranchDataJson}
import dispatch._
import code.branches.Branches.{Address, BranchId, Branch, DataLicense}
import code.branches.{Branches, BranchesProvider}
import code.model.BankId

class BranchesTest extends V140ServerSetup {

  val BankWithLicense = BankId("bank-with-license")
  val BankWithoutLicense = BankId("bank-without-license")

  case class BranchImpl(branchId : BranchId, name : String, address : Address) extends Branch
  case class AddressImpl(line1 : String, line2 : String, line3 : String, line4 : String,
                         line5 : String, postCode : String, countryCode : String) extends Address

  val fakeAddress1 = AddressImpl("134", "32432", "fff", "fsfsfs", "mvmvmv", "C4SF5", "DE")
  val fakeAddress2 = fakeAddress1.copy(line1 = "00000")

  val fakeBranch1 = BranchImpl(BranchId("branch1"), "Branch 1", fakeAddress1)
  val fakeBranch2 = BranchImpl(BranchId("branch2"), "Branch 2", fakeAddress2)

  val fakeLicense = new DataLicense {
    override def name: String = "sample-license"
    override def url: String = "http://example.com/license"
  }

  val mockConnector = new BranchesProvider {
    override protected def branchesData(bank: BankId): List[Branch] = {
      bank match {
        // have it return branches even for the bank without a license so we can test the connector does not return them
        case BankWithLicense | BankWithoutLicense=> List(fakeBranch1, fakeBranch2)
        case _ => Nil
      }
    }

    override protected def branchData(branchId: BranchId): Option[Branch] = {
      branchId match {
        // have it return a branch even for the bank without a license so we can test the connector does not return them
        case BankWithLicense | BankWithoutLicense=> Some(fakeBranch1)
        case _ => None
      }
    }






    override protected def branchDataLicense(bank: BankId): Option[DataLicense] = {
      bank match {
        case BankWithLicense => Some(fakeLicense)
        case _ => None
      }
    }
  }

  def verifySameData(branch: Branch, branchJson : BranchJson) = {
    branch.name should equal (branchJson.name)
    branch.branchId should equal(BranchId(branchJson.id))
    branch.address.line1 should equal(branchJson.address.line_1)
    branch.address.line2 should equal(branchJson.address.line_2)
    branch.address.line3 should equal(branchJson.address.line_3)
    branch.address.line4 should equal(branchJson.address.line_4)
    branch.address.line5 should equal(branchJson.address.line_5)
    branch.address.countryCode should equal(branchJson.address.country)
    branch.address.postCode should equal(branchJson.address.postcode_zip)
  }

  override def beforeAll() {
    super.beforeAll()
    //use the mock connector
    Branches.branchesProvider.default.set(mockConnector)
  }

  override def afterAll() {
    super.afterAll()
    //reset the default connector
    Branches.branchesProvider.default.set(Branches.buildOne)
  }

  feature("Getting bank branches") {

    scenario("We try to get bank branches for a bank without a data license for branch information") {

      When("We make a request")
      val request = (v1_4Request / "banks" / BankWithoutLicense.value / "branches").GET
      val response = makeGetRequest(request)

      Then("We should get a 404")
      response.code should equal(404)
    }

    scenario("We try to get bank branches for a bank with a data license for branch information") {
      When("We make a request")
      val request = (v1_4Request / "banks" / BankWithLicense.value / "branches").GET
      val response = makeGetRequest(request)

      Then("We should get a 200")
      response.code should equal(200)

      And("We should get the right json format")
      val responseBodyOpt = response.body.extractOpt[BranchDataJson]
      responseBodyOpt.isDefined should equal(true)
      val responseBody = responseBodyOpt.get

      And("We should get the right license")
      val license = responseBody.license
      license.name should equal(fakeLicense.name)
      license.url should equal(fakeLicense.url)

      And("We should get the right branches")
      val branches = responseBody.branches
      branches.size should equal(2)
      val first = branches(0)
      if(first.id == fakeBranch1.branchId.value) {
        verifySameData(fakeBranch1, first)
        verifySameData(fakeBranch2, branches(1))
      } else if (first.id == fakeBranch2.branchId.value) {
        verifySameData(fakeBranch2, first)
        verifySameData(fakeBranch1, branches(1))
      } else {
        fail("incorrect branches")
      }

    }
  }

}
