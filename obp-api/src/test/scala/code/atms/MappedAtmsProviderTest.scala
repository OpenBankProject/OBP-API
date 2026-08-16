package code.atms

import code.api.util.OBPLimit
import code.setup.ServerSetup
import com.openbankproject.commons.model.{AtmT, BankId}
import net.liftweb.mapper.By

class MappedAtmsProviderTest extends ServerSetup {

  private def delete(): Unit = {
    MappedAtm.bulkDelete_!!()
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
  
  class DefaultSetup {
      val bankIdX = "some-bank-x"
      val bankIdY = "some-bank-y"

      // 3 atms for bank X (one atm does not have a license)

      val unlicensedAtm = MappedAtm.create
        .mBankId(bankIdX)
        .mName("unlicensed")
        .mAtmId("unlicensed")
        .mCountryCode("es")
        .mPostCode("4444")
        .mLine1("line 1  1 1")
        .mLine2("line 2 2 2 2")
        .mLine3("c4")
        .mCity("d4")
        .mState("e4")
        .mlocationLatitude(4.44)
        .mlocationLongitude(5.55)
        .saveMe()
        // Note: The license is not set


      val atm1 = MappedAtm.create
        .mBankId(bankIdX)
        .mName("atm 1")
        .mAtmId("atm1")
        .mCountryCode("de")
        .mPostCode("123213213")
        .mLine1("a")
        .mLine2("b")
        .mLine3("c")
        .mCity("d")
        .mState("e")
        .mLicenseId("some-license")
        .mLicenseName("Some License")
        .mlocationLatitude(2.22)
        .mlocationLongitude(3.33).saveMe()

      val atm2 = MappedAtm.create
        .mBankId(bankIdX)
        .mName("atm 2")
        .mAtmId("atm2")
        .mCountryCode("fr")
        .mPostCode("898989")
        .mLine1("a2")
        .mLine2("b2")
        .mLine3("c2")
        .mCity("d2")
        .mState("e2")
        .mLicenseId("some-license")
        .mLicenseName("Some License")
        .mlocationLatitude(4.4444)
        .mlocationLongitude(5.5555).saveMe()

    }


  Feature("MappedAtmsProvider") {

    Scenario("We try to get atms") {

      val fixture = defaultSetup()

      // Only these have license set
      val expectedAtms =  List(fixture.atm1, fixture.atm2, fixture.unlicensedAtm)


      Given("the bank in question has atms")
      MappedAtm.find(By(MappedAtm.mBankId, fixture.bankIdX)).isDefined should equal(true)

      When("we try to get the atms for that bank")
      val atmsOpt: Option[List[AtmT]] = Atms.atmsProvider.vend.getAtms(BankId(fixture.bankIdX),List(OBPLimit(1000))) //OBPLimit(1000) is just a place holder

      Then("We should get a atms list")
      atmsOpt.isDefined should equal (true)
      val atms = atmsOpt.get

      And("it should contain 3 atms")
      atms.size should equal(3)

      And("they should be the licensed ones")
      // Compared field-by-field rather than object-to-object: the provider answers with the
      // commons Atm type while the fixture rows are MappedAtm entities, so `equal` on the whole
      // object compares two different classes and can never hold. The projection keeps every
      // field the old assertion actually depended on.
      def key(a: AtmT) =
        (a.atmId.value, a.bankId.value, a.name, a.address.line1, a.address.postCode,
         a.address.countryCode, a.location.latitude, a.location.longitude,
         a.meta.license.id, a.meta.license.name)
      atms.map(key).sortBy(_._1) should equal (expectedAtms.map(key).sortBy(_._1))
    }

    Scenario("We try to get atms for a bank that doesn't have any") {

      val fixture = defaultSetup()

      Given("we don't have any atms")

      MappedAtm.find(By(MappedAtm.mBankId, fixture.bankIdY)).isDefined should equal(false)

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
