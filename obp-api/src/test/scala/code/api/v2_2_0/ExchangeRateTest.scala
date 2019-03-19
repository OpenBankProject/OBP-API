package code.api.v2_2_0

import code.api.ErrorMessage
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiVersion
import code.api.util.ErrorMessages.InvalidISOCurrencyCode
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import org.scalatest.Tag

class ExchangeRateTest extends V220ServerSetup with DefaultUsers {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v2_2_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(OBPAPI2_2_0.Implementations2_2_0.getCurrentFxRate))

  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
  }
  
  feature("Assuring that Get Current FxRate works as expected - v2.2.0") {

    scenario("We Get Current FxRate", VersionOfApi, ApiEndpoint1) {
      val testBank = testBankId1
      val requestGet = (v2_2Request / "banks" / testBank.value / "fx" / "EUR" / "EUR" ).GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      And("We should get a 200")
      responseGet.code should equal(200)
    }
    
    scenario("We Get Current FxRate with wrong ISO from currency code", VersionOfApi, ApiEndpoint1) {
      val testBank = testBankId1
      val requestGet = (v2_2Request / "banks" / testBank.value / "fx" / "EUR1" / "EUR" ).GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      And("We should get a 400")
      responseGet.code should equal(400)
      responseGet.body.extract[ErrorMessage].message should equal (InvalidISOCurrencyCode)
    }

    scenario("We Get Current FxRate with wrong ISO to currency code", VersionOfApi, ApiEndpoint1) {
      val testBank = testBankId1
      val requestGet = (v2_2Request / "banks" / testBank.value / "fx" / "EUR" / "EUR1" ).GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      And("We should get a 400")
      responseGet.code should equal(400)
      responseGet.body.extract[ErrorMessage].message should equal (InvalidISOCurrencyCode)
    }
    
  }

}
