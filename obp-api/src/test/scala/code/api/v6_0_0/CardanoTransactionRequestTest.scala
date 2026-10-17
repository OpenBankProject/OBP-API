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
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)
*/
package code.api.v6_0_0

import org.json4s._
import code.api.Constant
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages._
import code.api.v4_0_0.TransactionRequestWithChargeJSON400
import code.entitlement.Entitlement
import code.methodrouting.MethodRoutingCommons
import code.api.v6_0_0.Http4s600.Implementations6_0_0
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{AccountId, AmountOfMoneyJsonV121, ErrorMessage}
import com.openbankproject.commons.util.ApiVersion
import org.json4s.native.Serialization.write
import org.scalatest.Tag

/**
  * This suite checks the v6.0.0 CARDANO transaction request endpoint.
  *
  * Most scenarios are rejected by OBP before any payment is attempted (not logged in, no access to the view,
  * invalid body), so they need no Cardano infrastructure and run in every build.
  *
  * The scenarios tagged RequiresCardanoWallet send a real payment: they route makePaymentv210 to the
  * cardano_vJun2025 connector, which posts to a cardano-wallet server at http://localhost:8090. They need that
  * server, connected to a synced Cardano test network, with the wallet `testAccountId` restored with
  * `passphrase` and funded. The tag is excluded by default in obp-api/pom.xml; to run them, pass
  * -DtagsToInclude=RequiresCardanoWallet -Dtest.tagsToExclude= on a machine that has such a wallet.
  */
class CardanoTransactionRequestTest extends V600ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object CreateTransactionRequestCardano extends Tag(nameOf(Implementations6_0_0.createTransactionRequestCardano))
  object RequiresCardanoWallet extends Tag("RequiresCardanoWallet")

  val testBankId = testBankId1.value

  // The account id is the cardano-wallet walletId, and passphrase is that wallet's passphrase.
  val testAccountId = "62b27359c25d4f2a5f97acee521ac1df7ac5a606"
  val passphrase = "StrongPassword123!"

  val testAddress = "addr_test1qpv3se9ghq87ud29l0a8asy8nlqwd765e5zt4rc2z4mktqulwagn832cuzcjknfyxwzxz2p2kumx6n58tskugny6mrqs7fd23z"

  private def cardanoRequest = (v6_0_0_Request / "banks" / testBankId / "accounts" / testAccountId /
    Constant.SYSTEM_OWNER_VIEW_ID / "transaction-request-types" / "CARDANO" / "transaction-requests").POST

  private def basicTransferBody(description: String = "Basic ADA transfer") = TransactionRequestBodyCardanoJsonV600(
    to = CardanoPaymentJsonV600(
      address = testAddress,
      amount = CardanoAmountJsonV600(quantity = 1000000, unit = "lovelace")
    ),
    value = AmountOfMoneyJsonV121("lovelace", "1000000"),
    passphrase = passphrase,
    description = description
  )

  // Test data is wiped before every scenario, so each scenario that reaches the account creates it here,
  // with resourceUser1 (user1) as its owner.
  private def createWalletAccountAndEnableCardano(): Unit = {
    setPropsValues("transactionRequests_enabled" -> "true")
    setPropsValues("transactionRequests_supported_types" -> "CARDANO")
    createAccountRelevantResource(Some(resourceUser1), testBankId1, AccountId(testAccountId), "lovelace")
  }

  private def routePaymentsToCardanoWallet(): Unit = {
    Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateMethodRouting.toString)
    val cardanoMethodRouting = MethodRoutingCommons(
      methodName = "makePaymentv210",
      connectorName = "cardano_vJun2025",
      isBankIdExactMatch = false,
      bankIdPattern = Some("*"),
      parameters = List()
    )
    val response = makePostRequest((v6_0_0_Request / "management" / "method_routings").POST <@ (user1), write(cardanoMethodRouting))
    response.code should equal(201)
  }

  private def postExpecting400(body: String, expectedMessage: String) = {
    createWalletAccountAndEnableCardano()
    val response = makePostRequest(cardanoRequest <@ (user1), body)
    Then("We should get a 400")
    response.code should equal(400)
    And("error should include " + expectedMessage)
    response.body.extract[ErrorMessage].message should include(expectedMessage)
  }

  feature("Create Cardano Transaction Request - v6.0.0") {

    scenario("We will create Cardano transaction request - user is NOT logged in", CreateTransactionRequestCardano, VersionOfApi) {
      When("We make a request v6.0.0")
      val response600 = makePostRequest(cardanoRequest, write(basicTransferBody()))
      Then("We should get a 401")
      response600.code should equal(401)
      And("error should be " + AuthenticatedUserIsRequired)
      response600.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }

    scenario("We will try to create Cardano transaction request for someone else account - user is logged in", CreateTransactionRequestCardano, VersionOfApi) {
      createWalletAccountAndEnableCardano()
      When("user2, who has no view on the account, makes a request v6.0.0")
      val response600 = makePostRequest(cardanoRequest <@ (user2), write(basicTransferBody()))
      Then("We should get a 403")
      response600.code should equal(403)
      And("error should be " + UserNoPermissionAccessView)
      response600.body.extract[ErrorMessage].message should include(UserNoPermissionAccessView)
    }

    scenario("We will try to create Cardano transaction request with invalid address format", CreateTransactionRequestCardano, VersionOfApi) {
      When("We make a request v6.0.0 with invalid address")
      val body = basicTransferBody().copy(to = basicTransferBody().to.copy(address = "invalid_address_format"))
      postExpecting400(write(body), "Cardano address format is invalid")
    }

    scenario("We will try to create Cardano transaction request with missing amount", CreateTransactionRequestCardano, VersionOfApi) {
      When("We make a request v6.0.0 with missing amount")
      val invalidJson =
        s"""
          {
            "to": {
              "address": "$testAddress"
            },
            "value": {
              "currency": "lovelace",
              "amount": "1000000"
            },
            "passphrase": "$passphrase",
            "description": "Basic ADA transfer"
          }
        """
      postExpecting400(invalidJson, InvalidJsonFormat)
    }

    scenario("We will try to create Cardano transaction request with negative amount", CreateTransactionRequestCardano, VersionOfApi) {
      When("We make a request v6.0.0 with negative amount")
      val body = basicTransferBody().copy(to = basicTransferBody().to.copy(amount = CardanoAmountJsonV600(quantity = -1000000, unit = "lovelace")))
      postExpecting400(write(body), "Cardano amount quantity must be non-negative")
    }

    scenario("We will try to create Cardano transaction request with invalid amount unit", CreateTransactionRequestCardano, VersionOfApi) {
      When("We make a request v6.0.0 with invalid amount unit")
      val body = basicTransferBody().copy(to = basicTransferBody().to.copy(amount = CardanoAmountJsonV600(quantity = 1000000, unit = "abc")))
      postExpecting400(write(body), "Cardano amount unit must be 'lovelace'")
    }

    scenario("We will try to create Cardano transaction request with zero amount but no assets", CreateTransactionRequestCardano, VersionOfApi) {
      When("We make a request v6.0.0 with zero amount but no assets")
      val body = basicTransferBody("Zero amount without assets").copy(
        to = basicTransferBody().to.copy(amount = CardanoAmountJsonV600(quantity = 0, unit = "lovelace")),
        value = AmountOfMoneyJsonV121("lovelace", "0.0")
      )
      // The check that value.amount is positive, common to every transaction request type, runs before the
      // CARDANO-specific checks, so it is the one that rejects this request.
      postExpecting400(write(body), NotPositiveAmount)
    }

    scenario("We will try to create Cardano transaction request with invalid assets", CreateTransactionRequestCardano, VersionOfApi) {
      When("We make a request v6.0.0 with invalid assets")
      val body = basicTransferBody("Invalid assets").copy(
        to = basicTransferBody().to.copy(assets = Some(List(CardanoAssetJsonV600(policy_id = "", asset_name = "", quantity = 0))))
      )
      postExpecting400(write(body), "Cardano assets must have valid policy_id and asset_name")
    }

    scenario("We will try to create Cardano transaction request with invalid metadata", CreateTransactionRequestCardano, VersionOfApi) {
      When("We make a request v6.0.0 with invalid metadata")
      val body = basicTransferBody("ADA transfer with invalid metadata").copy(
        metadata = Some(Map("" -> CardanoMetadataStringJsonV600("")))
      )
      postExpecting400(write(body), "Cardano metadata must have valid structure")
    }

    // The scenarios below send a real payment through cardano-wallet; see the class comment.

    scenario("We will create Cardano transaction request - user is logged in", CreateTransactionRequestCardano, VersionOfApi, RequiresCardanoWallet) {
      createWalletAccountAndEnableCardano()
      routePaymentsToCardanoWallet()
      When("We make a request v6.0.0")
      val response600 = makePostRequest(cardanoRequest <@ (user1), write(basicTransferBody()))
      Then("We should get a 201")
      response600.code should equal(201)
      And("response should contain transaction request")
      response600.body.extract[TransactionRequestWithChargeJSON400].status should not be empty
    }

    scenario("We will create Cardano transaction request with metadata - user is logged in", CreateTransactionRequestCardano, VersionOfApi, RequiresCardanoWallet) {
      createWalletAccountAndEnableCardano()
      routePaymentsToCardanoWallet()
      When("We make a request v6.0.0 with metadata")
      val body = basicTransferBody("ADA transfer with metadata").copy(
        metadata = Some(Map("202507022319" -> CardanoMetadataStringJsonV600("Hello Cardano")))
      )
      val response600 = makePostRequest(cardanoRequest <@ (user1), write(body))
      Then("We should get a 201")
      response600.code should equal(201)
      And("response should contain transaction request")
      response600.body.extract[TransactionRequestWithChargeJSON400].status should not be empty
    }

    scenario("We will create Cardano transaction request with token - user is logged in", CreateTransactionRequestCardano, VersionOfApi, RequiresCardanoWallet) {
      createWalletAccountAndEnableCardano()
      routePaymentsToCardanoWallet()
      When("We make a request v6.0.0 with token")
      val body = basicTransferBody("Token-only transfer").copy(
        to = basicTransferBody().to.copy(assets = Some(List(CardanoAssetJsonV600(
          policy_id = "ef1954d3a058a96d89d959939aeb5b2948a3df2eb40f9a78d61e3d4f",
          asset_name = "OGCRA",
          quantity = 10
        ))))
      )
      val response600 = makePostRequest(cardanoRequest <@ (user1), write(body))
      Then("We should get a 201")
      response600.code should equal(201)
      And("response should contain transaction request")
      response600.body.extract[TransactionRequestWithChargeJSON400].status should not be empty
    }

    scenario("We will create Cardano transaction request with token and metadata - user is logged in", CreateTransactionRequestCardano, VersionOfApi, RequiresCardanoWallet) {
      createWalletAccountAndEnableCardano()
      routePaymentsToCardanoWallet()
      When("We make a request v6.0.0 with token and metadata")
      val body = basicTransferBody("ADA transfer with token and metadata").copy(
        to = CardanoPaymentJsonV600(
          address = testAddress,
          amount = CardanoAmountJsonV600(quantity = 5000000, unit = "lovelace"),
          assets = Some(List(CardanoAssetJsonV600(
            policy_id = "ef1954d3a058a96d89d959939aeb5b2948a3df2eb40f9a78d61e3d4f",
            asset_name = "OGCRA",
            quantity = 10
          )))
        ),
        metadata = Some(Map("202507022319" -> CardanoMetadataStringJsonV600("Hello Cardano with Token")))
      )
      val response600 = makePostRequest(cardanoRequest <@ (user1), write(body))
      Then("We should get a 201")
      response600.code should equal(201)
      And("response should contain transaction request")
      response600.body.extract[TransactionRequestWithChargeJSON400].status should not be empty
    }
  }
}
