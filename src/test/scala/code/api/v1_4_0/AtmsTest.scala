package code.api.v1_4_0

import code.api.DefaultUsers
import code.api.v1_4_0.JSONFactory1_4_0.{AtmJson, AtmsJson}
import code.api.util.APIUtil.OAuth._

import code.atms.Atms.{Atm, AtmId}
import code.atms.{Atms, AtmsProvider}
import code.common.{Address, License, Location, Meta}
import code.model.BankId

class AtmsTest extends V140ServerSetup with DefaultUsers {

  val BankWithLicense = BankId("testBank1")
  val BankWithoutLicense = BankId("testBank2")

  // Have to repeat the constructor parameters from the trait
  case class AtmImpl(atmId : AtmId,
                        name : String,
                        address : Address,
                        location : Location,
                        meta : Meta) extends Atm

  case class AddressImpl(line1 : String, line2 : String, line3 : String, city : String, county : String,
                         state : String, postCode : String, countryCode : String) extends Address


  val fakeAddress1 = AddressImpl("Duckerstrasse 86", "Prenzlauerberg", "lala", "Berlin", "a county", "Berlin", "10437", "DE")
  val fakeAddress2 = fakeAddress1.copy(line1 = "00000")

  val fakeMeta = new Meta {
    val license = new License {
      override def id: String = "sample-license"
      override def name: String = "Sample License"
    }
  }

  val fakeMetaNoLicense = new Meta {
    val license = new License {
      override def id: String = ""
      override def name: String = ""
    }
  }

  val fakeLocation = new Location {
   override def latitude: Double = 11.11
   override def longitude: Double = 22.22
  }


  val fakeLocation2 = new Location {
    override def latitude: Double = 11.1111
    override def longitude: Double = 22.2222
  }




  val fakeAtm1 = AtmImpl(AtmId("atm1"), "Atm 1", fakeAddress1, fakeLocation, fakeMeta)
  val fakeAtm2 = AtmImpl(AtmId("atm2"), "Atm 2", fakeAddress2, fakeLocation2, fakeMeta)
  val fakeAtm3 = AtmImpl(AtmId("atm3"), "Atm 3", fakeAddress2, fakeLocation, fakeMetaNoLicense) // Should not be returned

  // This mock provider is returning same branches for the fake banks
  val mockConnector = new AtmsProvider {
    override protected def getAtmsFromProvider(bank: BankId): Option[List[Atm]] = {
      bank match {
        // have it return branches even for the bank without a license so we can test the API does not return them
        case BankWithLicense | BankWithoutLicense=> Some(List(fakeAtm1, fakeAtm2, fakeAtm3))
        case _ => None
      }
    }

    // Mock a badly behaving connector that returns data that doesn't have license.
    override protected def getAtmFromProvider(AtmId: AtmId): Option[Atm] = {
      AtmId match {
         case BankWithLicense => Some(fakeAtm1)
         case BankWithoutLicense=> Some(fakeAtm3) // In case the connector returns, the API should guard
        case _ => None
      }
    }

  }

  def verifySameData(atm: Atm, atmJson : AtmJson) = {
    atm.name should equal (atmJson.name)
    atm.atmId should equal(AtmId(atmJson.id))
    atm.address.line1 should equal(atmJson.address.line_1)
    atm.address.line2 should equal(atmJson.address.line_2)
    atm.address.line3 should equal(atmJson.address.line_3)
    atm.address.city should equal(atmJson.address.city)
    atm.address.state should equal(atmJson.address.state)
    atm.address.countryCode should equal(atmJson.address.country)
    atm.address.postCode should equal(atmJson.address.postcode)
    atm.location.latitude should equal(atmJson.location.latitude)
    atm.location.longitude should equal(atmJson.location.longitude)
  }

  /*
  So we can test the API layer, rather than the connector, use a mock connector.
   */
  override def beforeAll() {
    super.beforeAll()
    //use the mock connector
    Atms.atmsProvider.default.set(mockConnector)
  }

  override def afterAll() {
    super.afterAll()
    //reset the default connector
    Atms.atmsProvider.default.set(Atms.buildOne)
  }

  feature("Getting bank ATMs") {

    scenario("We try to get ATMs for a bank without a data license for ATM information") {

      When("We make a request")
      val request = (v1_4Request / "banks" / BankWithoutLicense.value / "atms").GET <@ user1
      val response = makeGetRequest(request)

      Then("We should get a 200")
      response.code should equal(200)

    }

    scenario("We try to get ATMs for a bank with a data license for ATM information") {
      When("We make a request")
      val request = (v1_4Request / "banks" / BankWithLicense.value / "atms").GET <@ user1
      val response = makeGetRequest(request)

      Then("We should get a 200")
      response.code should equal(200)

      And("We should get the right json format containing a list of ATMs")
      val wholeResponseBody = response.body
      val responseBodyOpt = wholeResponseBody.extractOpt[AtmsJson]
      responseBodyOpt.isDefined should equal(true)

      val responseBody = responseBodyOpt.get

      And("We should get the right atms")
      val atms = responseBody.atms

      // Order of ATMs in the list is arbitrary
      atms.size should equal(2)
      val first = atms(0)
      if(first.id == fakeAtm1.atmId.value) {
        verifySameData(fakeAtm1, first)
        verifySameData(fakeAtm2, atms(1))
      } else if (first.id == fakeAtm2.atmId.value) {
        verifySameData(fakeAtm2, first)
        verifySameData(fakeAtm1, atms(1))
      } else {
        fail("Incorrect ATMs")
      }

    }
  }

}
