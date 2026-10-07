/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.api.v2_2_0

import com.openbankproject.commons.model.ErrorMessage
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ErrorMessages.InvalidISOCurrencyCode
import code.consumer.Consumers
import code.entitlement.Entitlement
import code.scope.Scope
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag
import code.api.v2_2_0.Http4s220

class ExchangeRateTest extends V220ServerSetup with DefaultUsers {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v2_2_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Http4s220.Implementations2_2_0.getCurrentFxRate))
  object ApiEndpoint2 extends Tag(nameOf(Http4s220.Implementations2_2_0.createFx))

  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
  }
  
  feature("Assuring that Get Current FxRate works as expected - v2.2.0") {

    scenario("We Get Current FxRate", VersionOfApi, ApiEndpoint1) {
      val testBank = testBankId1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(user1.get._1.key).map(_.id.get.toString).getOrElse("")
      Scope.scope.vend.addScope(testBank.value, consumerId, ApiRole.canReadFx.toString())
      val requestGet = (v2_2Request / "banks" / testBank.value / "fx" / "EUR" / "EUR" ).GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      And("We should get a 200")
      responseGet.code should equal(200)
    }
    
    scenario("We Get Current FxRate with wrong ISO from currency code", VersionOfApi, ApiEndpoint1) {
      val testBank = testBankId1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(user1.get._1.key).map(_.id.get.toString).getOrElse("")
      Scope.scope.vend.addScope(testBank.value, consumerId, ApiRole.canReadFx.toString())
      val requestGet = (v2_2Request / "banks" / testBank.value / "fx" / "EUR1" / "EUR" ).GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      And("We should get a 400")
      responseGet.code should equal(400)
      responseGet.body.extract[ErrorMessage].message should startWith (InvalidISOCurrencyCode)
    }

    scenario("We Get Current FxRate with wrong ISO to currency code", VersionOfApi, ApiEndpoint1) {
      val testBank = testBankId1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(user1.get._1.key).map(_.id.get.toString).getOrElse("")
      Scope.scope.vend.addScope(testBank.value, consumerId, ApiRole.canReadFx.toString())
      val requestGet = (v2_2Request / "banks" / testBank.value / "fx" / "EUR" / "EUR1" ).GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      And("We should get a 400")
      responseGet.code should equal(400)
      responseGet.body.extract[ErrorMessage].message should startWith (InvalidISOCurrencyCode)
    }

    scenario("Currency codes in any letter case name the same currency", VersionOfApi, ApiEndpoint1, ApiEndpoint2) {
      val testBank = testBankId1
      val consumerId = Consumers.consumers.vend.getConsumerByConsumerKey(user1.get._1.key).map(_.id.get.toString).getOrElse("")
      Scope.scope.vend.addScope(testBank.value, consumerId, ApiRole.canReadFx.toString())
      Entitlement.entitlement.vend.addEntitlement(testBank.value, resourceUser1.userId, ApiRole.canCreateFxRate.toString())

      When("We create an FX rate with the currency codes in lower case")
      val body =
        s"""{"bank_id":"${testBank.value}","from_currency_code":"eur","to_currency_code":"usd",
           |"conversion_value":1.5,"inverse_conversion_value":0.6666666666666666,"effective_date":"2026-10-06T00:00:00Z"}""".stripMargin
      val responsePut = makePutRequest((v2_2Request / "banks" / testBank.value / "fx").PUT <@ (user1), body)
      Then("We should get a 201, and the codes are stored in upper case")
      responsePut.code should equal(201)
      (responsePut.body \ "from_currency_code").extract[String] should equal("EUR")
      (responsePut.body \ "to_currency_code").extract[String] should equal("USD")

      When("We get the rate, naming the currencies in lower case")
      val responseGet = makeGetRequest((v2_2Request / "banks" / testBank.value / "fx" / "eur" / "usd").GET <@ (user1))
      Then("We should get the rate we created")
      responseGet.code should equal(200)
      (responseGet.body \ "conversion_value").extract[Double] should equal(1.5)
    }
  }
}
