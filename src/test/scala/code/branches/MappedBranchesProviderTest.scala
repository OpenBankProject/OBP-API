package code.branches

import code.api.test.ServerSetup
import code.model.BankId
import net.liftweb.mapper.By

class MappedBranchesProviderTest extends ServerSetup {

  private def delete(): Unit = {
    MappedBranch.bulkDelete_!!()
    //MappedLicense.bulkDelete_!!()
  }

  override def beforeAll() = {
    super.beforeAll()
    delete()
  }

  override def afterEach() = {
    super.afterEach()
    delete()
  }

  def defaultSetup() =
    new {
      val bankIdWithLicenseAndData = "some-bank"
      val bankIdWithNoLicense = "unlicensed-bank"

//      val license = MappedLicense.create
//        .mBankId(bankIdWithLicenseAndData)
//        .mName("some-license")
//        .mUrl("http://www.example.com/license").saveMe()

      val unlicensedBranch = MappedBranch.create
        .mBankId(bankIdWithNoLicense)
        .mName("unlicensed")
        .mBranchId("unlicensed")
        .mCountryCode("es")
        .mPostCode("4444")
        .mLine1("a4")
        .mLine2("b4")
        .mLine3("c4")
        .mLine4("d4")
        .mLine5("e4").saveMe()

      val branch1 = MappedBranch.create
        .mBankId(bankIdWithLicenseAndData)
        .mName("branch 1")
        .mBranchId("branch1")
        .mCountryCode("de")
        .mPostCode("123213213")
        .mLine1("a")
        .mLine2("b")
        .mLine3("c")
        .mLine4("d")
        .mLine5("e")
        .mLicenseName("some-license")
        .mLicenseUrl("http://www.example.com/license").saveMe()

      val branch2 = MappedBranch.create
        .mBankId(bankIdWithLicenseAndData)
        .mName("branch 2")
        .mBranchId("branch2")
        .mCountryCode("fr")
        .mPostCode("898989")
        .mLine1("a2")
        .mLine2("b2")
        .mLine3("c2")
        .mLine4("d2")
        .mLine5("e2")
        .mLicenseName("some-license")
        .mLicenseUrl("http://www.example.com/license").saveMe()
    }

  feature("FIX ME MappedBranchesProvider") {

    scenario("We try to get branch data for a bank which does not have a data license set") {
      val fixture = defaultSetup()

      Given("The bank in question has no data license")
      //MappedLicense.count(By(MappedLicense.mBankId, fixture.bankIdWithNoLicense)) should equal(0)

      And("The bank in question has branches")
      MappedBranch.find(By(MappedBranch.mBankId, fixture.bankIdWithNoLicense)).isDefined should equal(true)

      When("We try to get the branch data for that bank")
      val branchData = MappedBranchesProvider.getBranches(BankId(fixture.bankIdWithNoLicense))

      Then("We should get an empty option")
      branchData should equal(None)
    }

    scenario("FIX ME We try to get branch data for a bank which does have a data license set") {
      val fixture = defaultSetup()
      val expectedBranches = Set(fixture.branch1, fixture.branch2)
      Given("We have a data license and branches for a bank")
      //MappedLicense.count(By(MappedLicense.mBankId, fixture.bankIdWithLicenseAndData)) should equal(1)
      MappedBranch.findAll(By(MappedBranch.mBankId, fixture.bankIdWithLicenseAndData)).toSet should equal(expectedBranches)

      When("We try to get the branch data for that bank")
      val branchDataOpt = MappedBranchesProvider.getBranches(BankId(fixture.bankIdWithLicenseAndData))

      Then("We should get back the data license and the branches")
      branchDataOpt.isDefined should equal(true)
      val branches = branchDataOpt.get

      // We no longer have one license per collection so this does not make sense
      // branchData.branches. should equal(fixture.license)
      branches.toSet should equal(expectedBranches)
    }

    scenario("FIX ME We try to get branch data for a bank with a data license, but no branches") {

      Given("We have a data license for a bank, but no branches")

      val bankWithNoBranches = "bank-with-no-branches"
//      val license = MappedLicense.create
//        .mBankId(bankWithNoBranches)
//        .mName("some-license")
//        .mUrl("http://www.example.com/license").saveMe()

      MappedBranch.find(By(MappedBranch.mBankId, bankWithNoBranches)).isDefined should equal(false)

      When("We try to get the branch data for that bank")
      val branchDataOpt = MappedBranchesProvider.getBranches(BankId(bankWithNoBranches))

      Then("We should get back the data license, and a list branches of size 0")
      branchDataOpt.isDefined should equal(true)
      val branches = branchDataOpt.get

      // ditto above: branchData.license should equal(license)
      branches should equal(Nil)

    }

  }
}
