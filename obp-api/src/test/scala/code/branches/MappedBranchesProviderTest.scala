package code.branches

import code.api.util.OBPLimit
import code.setup.ServerSetup
import com.openbankproject.commons.model.{BankId, BranchT}

class MappedBranchesProviderTest extends ServerSetup {

  private def delete(): Unit = {
    MappedBranch.deleteAll()
  }

  override def beforeAll() = {
    super.beforeAll()
    delete()
  }

  override def afterEach() = {
    super.afterEach()
    delete()
  }

  def defaultSetup() = new DefaultSetup()
  
  // The Mapper fixtures set only a handful of fields and relied on MappedString's "" default for
  // the rest; the store takes every column explicitly, so the unset ones are passed as "" here.
  private def branch(bankId: String, branchId: String, name: String, countryCode: String,
                     postCode: String, line1: String, line2: String, line3: String, city: String,
                     state: String, licenseId: String, licenseName: String) =
    MappedBranch.createOrUpdate(
      branchIdRaw = branchId, bankIdRaw = bankId, nameRaw = name,
      line1 = line1, line2 = line2, line3 = line3, city = city, county = "", state = state,
      postCode = postCode, countryCode = countryCode, latitude = 2.22, longitude = 3.33,
      licenseId = licenseId, licenseName = licenseName, lobbyHours = "", driveUpHours = "",
      branchRoutingSchemeRaw = "", branchRoutingAddressRaw = "",
      lobbyOpenMonday = "", lobbyCloseMonday = "", lobbyOpenTuesday = "", lobbyCloseTuesday = "",
      lobbyOpenWednesday = "", lobbyCloseWednesday = "", lobbyOpenThursday = "",
      lobbyCloseThursday = "", lobbyOpenFriday = "", lobbyCloseFriday = "",
      lobbyOpenSaturday = "", lobbyCloseSaturday = "", lobbyOpenSunday = "", lobbyCloseSunday = "",
      driveUpOpenMonday = "", driveUpCloseMonday = "", driveUpOpenTuesday = "",
      driveUpCloseTuesday = "", driveUpOpenWednesday = "", driveUpCloseWednesday = "",
      driveUpOpenThursday = "", driveUpCloseThursday = "", driveUpOpenFriday = "",
      driveUpCloseFriday = "", driveUpOpenSaturday = "", driveUpCloseSaturday = "",
      driveUpOpenSunday = "", driveUpCloseSunday = "",
      isAccessibleRaw = "", accessibleFeaturesRaw = "", branchTypeRaw = "", moreInfoRaw = "",
      phoneNumberRaw = "", isDeletedRaw = false)

  class DefaultSetup {
      val bankIdX = "some-bank-x"
      val bankIdY = "some-bank-y"

      // 3 branches for bank X (one branch does not have a license)

      // Note: The license is not set
      val unlicensedBranch =
        branch(bankIdX, "unlicensed", "unlicensed", "es", "4444", "a4", "b4", "c4", "d4", "e4", "", "")


      val branch1 =
        branch(bankIdX, "branch1", "branch 1", "de", "123213213", "a", "b", "c", "d", "e", "some-license", "Some License")

      val branch2 =
        branch(bankIdX, "branch2", "branch 2", "fr", "898989", "a2", "b2", "c2", "d2", "e2", "some-license", "Some License")

    }


  Feature("MappedBranchesProvider") {

    Scenario("We try to get branches") {

      val fixture = defaultSetup()

      // Only these have license set
      val expectedBranches =  List(fixture.branch1, fixture.branch2, fixture.unlicensedBranch)

      Given("the bank in question has branches")
      MappedBranch.findAllByBankId(fixture.bankIdX).nonEmpty should equal(true)

      When("we try to get the branches for that bank")
      val branchesOpt: Option[List[BranchT]] = MappedBranchesProvider.getBranches(BankId(fixture.bankIdX),List(OBPLimit(1000))) //OBPLimit(1000) is placeholder here.

      Then("We should get a branches list")
      branchesOpt.isDefined should equal (true)
      val branches = branchesOpt.get

      And("it should contain 3 branches")
      branches.size should equal(3)

      And("they should be the licensed ones")
      branches.sortBy(_.branchId.value) should equal (expectedBranches.sortBy(_.branchId.value))
    }

    Scenario("We try to get branches for a bank that doesn't have any") {

      val fixture = defaultSetup()

      Given("we don't have any branches")

      MappedBranch.findAllByBankId(fixture.bankIdY).nonEmpty should equal(false)

      When("we try to get the branches for that bank")
      val branchDataOpt = MappedBranchesProvider.getBranches(BankId(fixture.bankIdY),List(OBPLimit(1000))) //OBPLimit(1000) is placeholder here.

      Then("we should get back an empty list")
      branchDataOpt.isDefined should equal(true)
      val branches = branchDataOpt.get

      branches.size should equal(0)

    }


    // TODO add test for individual items

  }
}
