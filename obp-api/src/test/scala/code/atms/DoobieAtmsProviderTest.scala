package code.atms

import code.api.util.{DoobieUtil, OBPLimit}
import code.setup.ServerSetup
import com.openbankproject.commons.model.{Address, AtmId, AtmT, BankId, License, Location, Meta}
import doobie.implicits._

class DoobieAtmsProviderTest extends ServerSetup {

  private def delete(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedatm".update.run)
  }

  override def beforeAll() = {
    super.beforeAll()
    delete()
  }

  override def afterEach() = {
    super.afterEach()
    delete()
  }

  // Build the minimal commons Atm used by these tests (all optional schedule/feature fields empty).
  private def mkAtm(bankId: String, atmId: String, name: String, countryCode: String, postCode: String,
                    line1: String, line2: String, line3: String, city: String, state: String,
                    latitude: Double, longitude: Double,
                    licenseId: String = "", licenseName: String = ""): AtmT =
    Atms.Atm(
      atmId  = AtmId(atmId),
      bankId = BankId(bankId),
      name   = name,
      address = Address(line1 = line1, line2 = line2, line3 = line3, city = city,
        county = None, state = state, postCode = postCode, countryCode = countryCode),
      location = Location(latitude, longitude, None, None),
      meta     = Meta(License(id = licenseId, name = licenseName)),
      OpeningTimeOnMonday = None, ClosingTimeOnMonday = None,
      OpeningTimeOnTuesday = None, ClosingTimeOnTuesday = None,
      OpeningTimeOnWednesday = None, ClosingTimeOnWednesday = None,
      OpeningTimeOnThursday = None, ClosingTimeOnThursday = None,
      OpeningTimeOnFriday = None, ClosingTimeOnFriday = None,
      OpeningTimeOnSaturday = None, ClosingTimeOnSaturday = None,
      OpeningTimeOnSunday = None, ClosingTimeOnSunday = None,
      isAccessible = None, locatedAt = None, moreInfo = None, hasDepositCapability = None
    )

  def defaultSetup() = new DefaultSetup()

  class DefaultSetup {
      val bankIdX = "some-bank-x"
      val bankIdY = "some-bank-y"

      // 3 atms for bank X (one atm does not have a license).
      // createOrUpdateAtm returns the persisted (re-read) row, so the captured values match what
      // getAtms returns field-for-field — both come from DoobieAtmsProvider.rowToAtm on the same rows.

      val unlicensedAtm: AtmT = Atms.atmsProvider.vend.createOrUpdateAtm(
        mkAtm(bankIdX, "unlicensed", "unlicensed", "es", "4444", "line 1  1 1", "line 2 2 2 2", "c4", "d4", "e4", 4.44, 5.55)
      ).openOrThrowException("Failed to create unlicensedAtm")

      val atm1: AtmT = Atms.atmsProvider.vend.createOrUpdateAtm(
        mkAtm(bankIdX, "atm1", "atm 1", "de", "123213213", "a", "b", "c", "d", "e", 2.22, 3.33, "some-license", "Some License")
      ).openOrThrowException("Failed to create atm1")

      val atm2: AtmT = Atms.atmsProvider.vend.createOrUpdateAtm(
        mkAtm(bankIdX, "atm2", "atm 2", "fr", "898989", "a2", "b2", "c2", "d2", "e2", 4.4444, 5.5555, "some-license", "Some License")
      ).openOrThrowException("Failed to create atm2")
    }


  feature("DoobieAtmsProvider") {

    scenario("We try to get atms") {

      val fixture = defaultSetup()

      val expectedAtms = List(fixture.atm1, fixture.atm2, fixture.unlicensedAtm)


      Given("the bank in question has atms")
      Atms.atmsProvider.vend.getAtms(BankId(fixture.bankIdX), List(OBPLimit(1000))).get.nonEmpty should equal(true)

      When("we try to get the atms for that bank")
      val atmsOpt: Option[List[AtmT]] = Atms.atmsProvider.vend.getAtms(BankId(fixture.bankIdX),List(OBPLimit(1000))) //OBPLimit(1000) is just a place holder

      Then("We should get a atms list")
      atmsOpt.isDefined should equal (true)
      val atms = atmsOpt.get

      And("it should contain 3 atms")
      atms.size should equal(3)

      And("they should match the persisted ones")
      atms.sortBy(_.atmId.value) should equal (expectedAtms.sortBy(_.atmId.value))
    }

    scenario("We try to get atms for a bank that doesn't have any") {

      val fixture = defaultSetup()

      Given("we don't have any atms")

      Atms.atmsProvider.vend.getAtms(BankId(fixture.bankIdY), List(OBPLimit(1000))).get.isEmpty should equal(true)

      When("we try to get the atms for that bank")
      val atmDataOpt = Atms.atmsProvider.vend.getAtms(BankId(fixture.bankIdY), List(OBPLimit(1000))) //OBPLimit(1000) is just a place holder

      Then("we should get back an empty list")
      atmDataOpt.isDefined should equal(true)
      val atms = atmDataOpt.get

      atms.size should equal(0)

    }


    // TODO add test for individual items

  }
}
