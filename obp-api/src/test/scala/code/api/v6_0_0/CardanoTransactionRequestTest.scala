/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH

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
TESOBE GmbH
Osloerstrasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)
*/
package code.api.v6_0_0

import code.api.Constant
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.ApiRole
import code.api.util.ApiRole._
import code.api.util.ErrorMessages._
import code.api.v4_0_0.TransactionRequestWithChargeJSON400
import code.entitlement.Entitlement
import code.methodrouting.MethodRoutingCommons
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{AmountOfMoneyJsonV121, ErrorMessage}
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.v6_0_0.OBPAPI6_0_0.Implementations6_0_0
import code.entitlement.Entitlement



class CardanoTransactionRequestTest extends V600ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object CreateTransactionRequestCardano extends Tag(nameOf(Implementations6_0_0.createTransactionRequestCardano))


  val testBankId = testBankId1.value

  // This is a test account for Cardano transaction request tests, testAccountId0 is the walletId, passphrase is the passphrase for the wallet
  val testAccountId = "62b27359c25d4f2a5f97acee521ac1df7ac5a606"
  val passphrase = "StrongPassword123!"


  val putCreateAccountJSONV310 = SwaggerDefinitionsJSON.createAccountRequestJsonV310.copy(
    user_id = resourceUser1.userId,
    balance = AmountOfMoneyJsonV121("lovelace", "0"),
  )

  
  feature("Create Cardano Transaction Request - v6.0.0") {
   
    scenario("We will create Cardano transaction request - user is NOT logged in", CreateTransactionRequestCardano, VersionOfApi) {
      When("We make a request v6.0.0")
      val request600 = (v6_0_0_Request / "banks" / testBankId / "accounts" / testAccountId / Constant.SYSTEM_OWNER_VIEW_ID / "transaction-request-types" / "CARDANO" / "transaction-requests").POST
      val cardanoTransactionRequestBody = TransactionRequestBodyCardanoJsonV600(
        to = CardanoPaymentJsonV600(
          address = "addr_test1qpv3se9ghq87ud29l0a8asy8nlqwd765e5zt4rc2z4mktqulwagn832cuzcjknfyxwzxz2p2kumx6n58tskugny6mrqs7fd23z",
          amount = CardanoAmountJsonV600(
            quantity = 1000000,
            unit = "lovelace"
          )
        ),
        value = AmountOfMoneyJsonV121("lovelace", "1000000"),
        passphrase = passphrase,
        description = "Basic ADA transfer"
      )
      val response600 = makePostRequest(request600, write(cardanoTransactionRequestBody))
      Then("We should get a 401")
      response600.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response600.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }

//    scenario("We will create Cardano transaction request - user is logged in", CreateTransactionRequestCardano, VersionOfApi) {
//      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, ApiRole.canCreateAccount.toString())
//      val request = (v6_0_0_Request / "banks" / testBankId / "accounts" / testAccountId ).PUT <@(user1)
//      val response = makePutRequest(request, write(putCreateAccountJSONV310))
//      Then("We should get a 201")
//      response.code should equal(201)
//
//      When("We create a method routing for makePaymentv210 to cardano_vJun2025")
//      val cardanoMethodRouting = MethodRoutingCommons(
//        methodName = "makePaymentv210",
//        connectorName = "cardano_vJun2025",
//        isBankIdExactMatch = false,
//        bankIdPattern = Some("*"),
//        parameters = List()
//      )
//      val request310 = (v6_0_0_Request / "management" / "method_routings").POST <@(user1)
//      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateMethodRouting.toString)
//      val response310 = makePostRequest(request310, write(cardanoMethodRouting))
//      response310.code should equal(201)
//
//      When("We make a request v6.0.0")
//      val request600 = (v6_0_0_Request / "banks" / testBankId / "accounts" / testAccountId / Constant.SYSTEM_OWNER_VIEW_ID / "transaction-request-types" / "CARDANO" / "transaction-requests").POST <@(user1)
//      val cardanoTransactionRequestBody = TransactionRequestBodyCardanoJsonV600(
//        to = CardanoPaymentJsonV600(
//          address = "addr_test1qpv3se9ghq87ud29l0a8asy8nlqwd765e5zt4rc2z4mktqulwagn832cuzcjknfyxwzxz2p2kumx6n58tskugny6mrqs7fd23z",
//          amount = CardanoAmountJsonV600(
//            quantity = 1000000,
//            unit = "lovelace"
//          )
//        ),
//        value = AmountOfMoneyJsonV121("lovelace", "1000000"),
//        passphrase = passphrase,
//        description = "Basic ADA transfer"
//      )
//      val response600 = makePostRequest(request600, write(cardanoTransactionRequestBody))
//      Then("We should get a 201")
//      response600.code should equal(201)
//      And("response should contain transaction request")
//      val transactionRequest = response600.body.extract[TransactionRequestWithChargeJSON400]
//      transactionRequest.status should not be empty
//    }
//
//    scenario("We will create Cardano transaction request with metadata - user is logged in", CreateTransactionRequestCardano, VersionOfApi) {
//      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, ApiRole.canCreateAccount.toString())
//      val request = (v6_0_0_Request / "banks" / testBankId / "accounts" / testAccountId ).PUT <@(user1)
//      val response = makePutRequest(request, write(putCreateAccountJSONV310))
//      Then("We should get a 201")
//      response.code should equal(201)
//
//      When("We create a method routing for makePaymentv210 to cardano_vJun2025")
//      val cardanoMethodRouting = MethodRoutingCommons(
//        methodName = "makePaymentv210",
//        connectorName = "cardano_vJun2025",
//        isBankIdExactMatch = false,
//        bankIdPattern = Some("*"),
//        parameters = List()
//      )
//      val request310 = (v6_0_0_Request / "management" / "method_routings").POST <@(user1)
//      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateMethodRouting.toString)
//      val response310 = makePostRequest(request310, write(cardanoMethodRouting))
//      response310.code should equal(201)
//      
//      When("We make a request v6.0.0 with metadata")
//      val request600 = (v6_0_0_Request / "banks" / testBankId / "accounts" / testAccountId / Constant.SYSTEM_OWNER_VIEW_ID / "transaction-request-types" / "CARDANO" / "transaction-requests").POST <@(user1)
//      val cardanoTransactionRequestBody = TransactionRequestBodyCardanoJsonV600(
//        to = CardanoPaymentJsonV600(
//          address = "addr_test1qpv3se9ghq87ud29l0a8asy8nlqwd765e5zt4rc2z4mktqulwagn832cuzcjknfyxwzxz2p2kumx6n58tskugny6mrqs7fd23z",
//          amount = CardanoAmountJsonV600(
//            quantity = 1000000,
//            unit = "lovelace"
//          )
//        ),
//        value = AmountOfMoneyJsonV121("lovelace", "1000000"),
//        passphrase = passphrase,
//        description = "ADA transfer with metadata",
//        metadata = Some(Map("202507022319" -> CardanoMetadataStringJsonV600("Hello Cardano")))
//      )
//      val response600 = makePostRequest(request600, write(cardanoTransactionRequestBody))
//      Then("We should get a 201")
//      response600.code should equal(201)
//      And("response should contain transaction request")
//       val transactionRequest = response600.body.extract[TransactionRequestWithChargeJSON400]
//      transactionRequest.status should not be empty
//    }
//
//    scenario("We will create Cardano transaction request with token - user is logged in", CreateTransactionRequestCardano, VersionOfApi) {
//      Entitlement.entitlement.vend.addEntitlement(testBankId, resourceUser1.userId, ApiRole.canCreateAccount.toString())
//      val request = (v6_0_0_Request / "banks" / testBankId / "accounts" / testAccountId ).PUT <@(user1)
//      val response = makePutRequest(request, write(putCreateAccountJSONV310))
//      Then("We should get a 201")
//      response.code should equal(201)
//
//      When("We create a method routing for makePaymentv210 to cardano_vJun2025")
//      val cardanoMethodRouting = MethodRoutingCommons(
//        methodName = "makePaymentv210",
//        connectorName = "cardano_vJun2025",
//        isBankIdExactMatch = false,
//        bankIdPattern = Some("*"),
//        parameters = List()
//      )
//      val request310 = (v6_0_0_Request / "management" / "method_routings").POST <@(user1)
//      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateMethodRouting.toString)
//      val response310 = makePostRequest(request310, write(cardanoMethodRouting))
//      response310.code should equal(201)
//      
//      When("We make a request v6.0.0 with token")
//      val request600 = (v6_0_0_Request / "banks" / testBankId / "accounts" / testAccountId / Constant.SYSTEM_OWNER_VIEW_ID / "transaction-request-types" / "CARDANO" / "transaction-requests").POST <@(user1)
//      val cardanoTransactionRequestBody = TransactionRequestBodyCardanoJsonV600(
//        to = CardanoPaymentJsonV600(
//          address = "addr_test1qpv3se9ghq87ud29l0a8asy8nlqwd765e5zt4rc2z4mktqulwagn832cuzcjknfyxwzxz2p2kumx6n58tskugny6mrqs7fd23z",
//          amount = CardanoAmountJsonV600(
//            quantity = 1000000,
//            unit = "lovelace"
//          ),
//          assets = Some(List(CardanoAssetJsonV600(
//            policy_id = "ef1954d3a058a96d89d959939aeb5b2948a3df2eb40f9a78d61e3d4f",
//            asset_name = "OGCRA",
//            quantity = 10
//          )))
//        ),
//        value = AmountOfMoneyJsonV121("lovelace", "1000000"),
//        passphrase = passphrase,
//        description = "Token-only transfer"
//      )
//      val response600 = makePostRequest(request600, write(cardanoTransactionRequestBody))
//      Then("We should get a 201")
//      response600.code should equal(201)
//      And("response should contain transaction request")
//       val transactionRequest = response600.body.extract[TransactionRequestWithChargeJSON400]
//      transactionRequest.status should not be empty
//    }
//
//    scenario("We will create Cardano transaction request with token and metadata - user is logged in", CreateTransactionRequestCardano, VersionOfApi) {
//      When("We make a request v6.0.0 with token and metadata")
//      val request600 = (v6_0_0_Request / "banks" / testBankId / "accounts" / testAccountId / Constant.SYSTEM_OWNER_VIEW_ID / "transaction-request-types" / "CARDANO" / "transaction-requests").POST <@(user1)
//      val cardanoTransactionRequestBody = TransactionRequestBodyCardanoJsonV600(
//        to = CardanoPaymentJsonV600(
//          address = "addr_test1qpv3se9ghq87ud29l0a8asy8nlqwd765e5zt4rc2z4mktqulwagn832cuzcjknfyxwzxz2p2kumx6n58tskugny6mrqs7fd23z",
//          amount = CardanoAmountJsonV600(
//            quantity = 5000000,
//            unit = "lovelace"
//          ),
//          assets = Some(List(CardanoAssetJsonV600(
//            policy_id = "ef1954d3a058a96d89d959939aeb5b2948a3df2eb40f9a78d61e3d4f",
//            asset_name = "OGCRA",
//            quantity = 10
//          )))
//        ),
//        value = AmountOfMoneyJsonV121("lovelace", "1000000"),
//        passphrase = passphrase,
//        description = "ADA transfer with token and metadata",
//        metadata = Some(Map("202507022319" -> CardanoMetadataStringJsonV600("Hello Cardano with Token")))
//      )
//      val response600 = makePostRequest(request600, write(cardanoTransactionRequestBody))
//      Then("We should get a 201")
//      response600.code should equal(201)
//      And("response should contain transaction request")
//       val transactionRequest = response600.body.extract[TransactionRequestWithChargeJSON400]
//      transactionRequest.status should not be empty
//    }
//
//    scenario("We will try to create Cardano transaction request for someone else account - user is logged in", CreateTransactionRequestCardano, VersionOfApi) {
//      When("We make a request v6.0.0")
//      val request600 = (v6_0_0_Request / "banks" / testBankId / "accounts" / testAccountId / Constant.SYSTEM_OWNER_VIEW_ID / "transaction-request-types" / "CARDANO" / "transaction-requests").POST <@(user2)
//      val cardanoTransactionRequestBody = TransactionRequestBodyCardanoJsonV600(
//        to = CardanoPaymentJsonV600(
//          address = "addr_test1qpv3se9ghq87ud29l0a8asy8nlqwd765e5zt4rc2z4mktqulwagn832cuzcjknfyxwzxz2p2kumx6n58tskugny6mrqs7fd23z",
//          amount = CardanoAmountJsonV600(
//            quantity = 1000000,
//            unit = "lovelace"
//          )
//        ),
//        value = AmountOfMoneyJsonV121("lovelace", "1000000"),
//        passphrase = passphrase,
//        description = "Basic ADA transfer"
//      )
//      val response600 = makePostRequest(request600, write(cardanoTransactionRequestBody))
//      Then("We should get a 403")
//      response600.code should equal(403)
//      And("error should be " + UserNoPermissionAccessView)
//      response600.body.extract[ErrorMessage].message contains (UserNoPermissionAccessView) shouldBe (true)
//    }
//
//    scenario("We will try to create Cardano transaction request with invalid address format", CreateTransactionRequestCardano, VersionOfApi) {
//      When("We make a request v6.0.0 with invalid address")
//      val request600 = (v6_0_0_Request / "banks" / testBankId / "accounts" / testAccountId / Constant.SYSTEM_OWNER_VIEW_ID / "transaction-request-types" / "CARDANO" / "transaction-requests").POST <@(user1)
//      val cardanoTransactionRequestBody = TransactionRequestBodyCardanoJsonV600(
//        to = CardanoPaymentJsonV600(
//          address = "invalid_address_format",
//          amount = CardanoAmountJsonV600(
//            quantity = 1000000,
//            unit = "lovelace"
//          )
//        ),
//        value = AmountOfMoneyJsonV121("lovelace", "1000000"),
//        passphrase = passphrase,
//        description = "Basic ADA transfer"
//      )
//      val response600 = makePostRequest(request600, write(cardanoTransactionRequestBody))
//      Then("We should get a 400")
//      response600.code should equal(400)
//      And("error should contain invalid address message")
//      response600.body.extract[ErrorMessage].message should include("Cardano address format is invalid")
//    }
//
//    scenario("We will try to create Cardano transaction request with missing amount", CreateTransactionRequestCardano, VersionOfApi) {
//      When("We make a request v6.0.0 with missing amount")
//      val request600 = (v6_0_0_Request / "banks" / testBankId / "accounts" / testAccountId / Constant.SYSTEM_OWNER_VIEW_ID / "transaction-request-types" / "CARDANO" / "transaction-requests").POST <@(user1)
//      val invalidJson = """
//        {
//          "to": {
//            "address": "addr_test1qpv3se9ghq87ud29l0a8asy8nlqwd765e5zt4rc2z4mktqulwagn832cuzcjknfyxwzxz2p2kumx6n58tskugny6mrqs7fd23z"
//          },
//          "value": {
//            "currency": "lovelace",
//            "amount": "1000000"
//          },
//          "passphrase": "StrongPassword123!",
//          "description": "Basic ADA transfer"
//        }
//      """
//      val response600 = makePostRequest(request600, invalidJson)
//      Then("We should get a 400")
//      response600.code should equal(400)
//      And("error should contain invalid json format message")
//      response600.body.extract[ErrorMessage].message should include("InvalidJsonFormat")
//    }
//
//    scenario("We will try to create Cardano transaction request with negative amount", CreateTransactionRequestCardano, VersionOfApi) {
//      When("We make a request v6.0.0 with negative amount")
//      val request600 = (v6_0_0_Request / "banks" / testBankId / "accounts" / testAccountId / Constant.SYSTEM_OWNER_VIEW_ID / "transaction-request-types" / "CARDANO" / "transaction-requests").POST <@(user1)
//      val cardanoTransactionRequestBody = TransactionRequestBodyCardanoJsonV600(
//        to = CardanoPaymentJsonV600(
//          address = "addr_test1qpv3se9ghq87ud29l0a8asy8nlqwd765e5zt4rc2z4mktqulwagn832cuzcjknfyxwzxz2p2kumx6n58tskugny6mrqs7fd23z",
//          amount = CardanoAmountJsonV600(
//            quantity = -1000000,
//            unit = "lovelace"
//          )
//        ),
//        value = AmountOfMoneyJsonV121("lovelace", "1000000"),
//        passphrase = passphrase,
//        description = "Basic ADA transfer"
//      )
//      val response600 = makePostRequest(request600, write(cardanoTransactionRequestBody))
//      Then("We should get a 400")
//      response600.code should equal(400)
//      And("error should contain invalid amount message")
//      response600.body.extract[ErrorMessage].message should include("Cardano amount quantity must be non-negative")
//    }
//
//    scenario("We will try to create Cardano transaction request with invalid amount unit", CreateTransactionRequestCardano, VersionOfApi) {
//      When("We make a request v6.0.0 with invalid amount unit")
//      val request600 = (v6_0_0_Request / "banks" / testBankId / "accounts" / testAccountId / Constant.SYSTEM_OWNER_VIEW_ID / "transaction-request-types" / "CARDANO" / "transaction-requests").POST <@(user1)
//      val cardanoTransactionRequestBody = TransactionRequestBodyCardanoJsonV600(
//        to = CardanoPaymentJsonV600(
//          address = "addr_test1qpv3se9ghq87ud29l0a8asy8nlqwd765e5zt4rc2z4mktqulwagn832cuzcjknfyxwzxz2p2kumx6n58tskugny6mrqs7fd23z",
//          amount = CardanoAmountJsonV600(
//            quantity = 1000000,
//            unit = "abc" // Invalid unit, should be "lovelace"
//          )
//        ),
//        value = AmountOfMoneyJsonV121("lovelace", "1000000"),
//        passphrase = passphrase,
//        description = "Basic ADA transfer"
//      )
//      val response600 = makePostRequest(request600, write(cardanoTransactionRequestBody))
//      Then("We should get a 400")
//      response600.code should equal(400)
//      And("error should contain invalid unit message")
//      response600.body.extract[ErrorMessage].message should include("Cardano amount unit must be 'lovelace'")
//    }
//
//    scenario("We will try to create Cardano transaction request with zero amount but no assets", CreateTransactionRequestCardano, VersionOfApi) {
//      When("We make a request v6.0.0 with zero amount but no assets")
//      val request600 = (v6_0_0_Request / "banks" / testBankId / "accounts" / testAccountId / Constant.SYSTEM_OWNER_VIEW_ID / "transaction-request-types" / "CARDANO" / "transaction-requests").POST <@(user1)
//      val cardanoTransactionRequestBody = TransactionRequestBodyCardanoJsonV600(
//        to = CardanoPaymentJsonV600(
//          address = "addr_test1qpv3se9ghq87ud29l0a8asy8nlqwd765e5zt4rc2z4mktqulwagn832cuzcjknfyxwzxz2p2kumx6n58tskugny6mrqs7fd23z",
//          amount = CardanoAmountJsonV600(
//            quantity = 0,
//            unit = "lovelace"
//          )
//        ),
//        value = AmountOfMoneyJsonV121("lovelace", "0.0"),
//        passphrase = passphrase,
//        description = "Zero amount without assets"
//      )
//      val response600 = makePostRequest(request600, write(cardanoTransactionRequestBody))
//      Then("We should get a 400")
//      response600.code should equal(400)
//      And("error should contain invalid amount message")
//      response600.body.extract[ErrorMessage].message should include("Cardano transfer with zero amount must include assets")
//    }
//
//    scenario("We will try to create Cardano transaction request with invalid assets", CreateTransactionRequestCardano, VersionOfApi) {
//      When("We make a request v6.0.0 with invalid assets")
//      val request600 = (v6_0_0_Request / "banks" / testBankId / "accounts" / testAccountId / Constant.SYSTEM_OWNER_VIEW_ID / "transaction-request-types" / "CARDANO" / "transaction-requests").POST <@(user1)
//      val cardanoTransactionRequestBody = TransactionRequestBodyCardanoJsonV600(
//        to = CardanoPaymentJsonV600(
//          address = "addr_test1qpv3se9ghq87ud29l0a8asy8nlqwd765e5zt4rc2z4mktqulwagn832cuzcjknfyxwzxz2p2kumx6n58tskugny6mrqs7fd23z",
//          amount = CardanoAmountJsonV600(
//            quantity = 0,
//            unit = "lovelace"
//          ),
//          assets = Some(List(CardanoAssetJsonV600(
//            policy_id = "",
//            asset_name = "",
//            quantity = 0
//          )))
//        ),
//        value = AmountOfMoneyJsonV121("lovelace", "0.0"),
//        passphrase = passphrase,
//        description = "Invalid assets"
//      )
//      val response600 = makePostRequest(request600, write(cardanoTransactionRequestBody))
//      Then("We should get a 400")
//      response600.code should equal(400)
//      And("error should contain invalid assets message")
//      response600.body.extract[ErrorMessage].message should include("Cardano assets must have valid policy_id and asset_name")
//    }
//
//    scenario("We will try to create Cardano transaction request with invalid metadata", CreateTransactionRequestCardano, VersionOfApi) {
//      When("We make a request v6.0.0 with invalid metadata")
//      val request600 = (v6_0_0_Request / "banks" / testBankId / "accounts" / testAccountId / Constant.SYSTEM_OWNER_VIEW_ID / "transaction-request-types" / "CARDANO" / "transaction-requests").POST <@(user1)
//      val cardanoTransactionRequestBody = TransactionRequestBodyCardanoJsonV600(
//        to = CardanoPaymentJsonV600(
//          address = "addr_test1qpv3se9ghq87ud29l0a8asy8nlqwd765e5zt4rc2z4mktqulwagn832cuzcjknfyxwzxz2p2kumx6n58tskugny6mrqs7fd23z",
//          amount = CardanoAmountJsonV600(
//            quantity = 1000000,
//            unit = "lovelace"
//          )
//        ),
//        value = AmountOfMoneyJsonV121("lovelace", "1000000"),
//        passphrase = passphrase,
//        description = "ADA transfer with invalid metadata",
//        metadata = Some(Map("" -> CardanoMetadataStringJsonV600("")))
//      )
//      val response600 = makePostRequest(request600, write(cardanoTransactionRequestBody))
//      Then("We should get a 400")
//      response600.code should equal(400)
//      And("error should contain invalid metadata message")
//      response600.body.extract[ErrorMessage].message should include("Cardano metadata must have valid structure")
//    }
  }
} 