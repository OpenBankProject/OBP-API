package code.branches

import code.api.test.ServerSetup
import code.model.BankId
import net.liftweb.mapper.By
import code.branches.Branches.Branch

class MappedBranchesProviderTest extends ServerSetup {

  private def delete(): Unit = {
    MappedBranch.bulkDelete_!!()
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
      val bankIdX = "some-bank-x"
      val bankIdY = "some-bank-y"

      // 3 branches for bank X (one branch does not have a license)

      val unlicensedBranch = MappedBranch.create
        .mBankId(bankIdX)
        .mName("unlicensed")
        .mBranchId("unlicensed")
        .mCountryCode("es")
        .mPostCode("4444")
        .mLine1("a4")
        .mLine2("b4")
        .mLine3("c4")
        .mLine4("d4")
        .mLine5("e4").saveMe()
        // Note: The license is not set

      val branch1 = MappedBranch.create
        .mBankId(bankIdX)
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
        .mBankId(bankIdX)
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


  feature("MappedBranchesProvider") {

    scenario("We try to get branches") {

      val fixture = defaultSetup()

      // Only these have license set
      val expectedBranches =  Set(fixture.branch1 :: fixture.branch2 :: Nil)


      Given("the bank in question has branches")
      MappedBranch.find(By(MappedBranch.mBankId, fixture.bankIdX)).isDefined should equal(true)

      When("we try to get the branches for that bank")
      val branchesOpt: Option[List[Branch]] = MappedBranchesProvider.getBranches(BankId(fixture.bankIdX))

      Then("We should get a branches list")
      branchesOpt.isDefined should equal (true)
      val branches = branchesOpt.get

      And("it should contain two branches")
      branches.size should equal(2)

      //And("they should be the licensed ones")
      //branches.toSet should equal (expectedBranches)
    }

    scenario("We try to get branches for a bank that doesn't have any") {

      val fixture = defaultSetup()

      Given("we don't have any branches")

      MappedBranch.find(By(MappedBranch.mBankId, fixture.bankIdY)).isDefined should equal(false)

      When("we try to get the branches for that bank")
      val branchDataOpt = MappedBranchesProvider.getBranches(BankId(fixture.bankIdY))

      Then("we should get back an empty list")
      branchDataOpt.isDefined should equal(true)
      val branches = branchDataOpt.get

      branches.size should equal(0)

    }

  }
}
