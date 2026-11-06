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
package code.api.v7_0_0

import code.api.Constant.{CAN_ADD_TRANSACTION_REQUEST_TO_ANY_ACCOUNT, SYSTEM_MANAGE_CUSTOM_VIEWS_VIEW_ID, SYSTEM_OWNER_VIEW_ID}
import code.api.util.APIUtil
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages.{CannotGrantConsumerAccountAccessToView, ConsumerAccountAccessAlreadyExists, ConsumerAccountAccessNotFound, ConsumerNotFoundByConsumerId}
import code.api.v6_0_0.V600ServerSetup
import code.consumeraccountaccess.ConsumerAccountAccesses
import code.transactionrequests.MappedTransactionRequest
import code.views.Views
import com.openbankproject.commons.model.{BankIdAccountId, CreateViewJson, ErrorMessage, ViewId}
import com.openbankproject.commons.util.ApiVersion
import org.json4s.JsonDSL._
import org.json4s.{jvalue2extractable, jvalue2monadic}
import org.json4s.native.JsonMethods.{compact, render}
import org.scalatest.Tag

/** Account access granted to a Consumer, used by the application acting as itself (client credentials). */
class ConsumerAccountAccessTest extends V600ServerSetup {

  def v7 = baseRequest / "obp" / "v7.0.0"

  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object ApiEndpoint1 extends Tag("grantConsumerAccessToView")
  object ApiEndpoint2 extends Tag("getConsumerAccountAccess")
  object ApiEndpoint3 extends Tag("revokeConsumerAccessToView")
  object ApiEndpoint4 extends Tag("createTransactionRequestOpenCorridor")

  private val bankId = testBankId1
  private val accountId = testAccountId0

  private def access(viewId: String) =
    v7 / "banks" / bankId.value / "accounts" / accountId.value / "views" / viewId / "consumer-account-access"
  private def grantBody(consumerId: String, viewId: String) =
    compact(render(("consumer_id" -> consumerId) ~ ("view_id" -> viewId)))
  private def promise(viewId: String) =
    v7 / "banks" / bankId.value / "accounts" / accountId.value / viewId / "transaction-request-types" /
      "OPEN_CORRIDOR_PROMISE" / "transaction-requests"
  private def message(r: code.setup.APIResponse) = r.body.extract[ErrorMessage].message

  private def accountCurrency: String =
    code.bankconnectors.Connector.connector.vend.getBankAccountLegacy(bankId, accountId, None)
      .map(_._1.currency).openOrThrowException("test account")

  private def promiseBody: String =
    s"""{
       |  "to": {
       |    "name": "Beneficiary ${APIUtil.generateUUID().take(8)}",
       |    "description": "Beneficiary at receiving institution",
       |    "other_bank_routing_scheme": "OBP",
       |    "other_bank_routing_address": "${testBankId2.value}",
       |    "other_branch_routing_scheme": "",
       |    "other_branch_routing_address": "",
       |    "other_account_routing_scheme": "OBP",
       |    "other_account_routing_address": "${testAccountId1.value}",
       |    "other_account_secondary_routing_scheme": "",
       |    "other_account_secondary_routing_address": ""
       |  },
       |  "value": {"currency": "$accountCurrency", "amount": "2.00"},
       |  "description": "Consumer account access test payment",
       |  "charge_policy": "SHARED",
       |  "originator": {
       |    "name": "Alice Sender",
       |    "address": "1 Sender Street, London, UK",
       |    "account_routing": {"scheme": "IBAN", "address": "GB29 NWBK 6016 1331 9268 19"}
       |  }
       |}""".stripMargin

  /** user1 holds owner on the test accounts; custom views are granted through the ManageCustomViews view. */
  private val Manage = SYSTEM_MANAGE_CUSTOM_VIEWS_VIEW_ID
  private def user1ManagesCustomViews(): Unit =
    Views.views.vend.grantAccessToSystemView(bankId, accountId,
      Views.views.vend.systemView(ViewId(Manage)).openOrThrowException("ManageCustomViews"), resourceUser1)

  /** A custom view of the test account with only the permission to create Transaction Requests. */
  private def createPaymentInitiationView(): String = {
    user1ManagesCustomViews()
    val name = "_pay" + APIUtil.generateUUID().take(8)
    Views.views.vend.createCustomView(
      BankIdAccountId(bankId, accountId),
      CreateViewJson(
        name = name,
        description = "payment initiation only",
        metadata_view = name,
        is_public = false,
        which_alias_to_use = "",
        hide_metadata_if_alias_used = false,
        allowed_actions = List(CAN_ADD_TRANSACTION_REQUEST_TO_ANY_ACCOUNT)
      )
    ).openOrThrowException("custom view").viewId.value
  }

  private def revokeAll(): Unit =
    ConsumerAccountAccesses.consumerAccountAccessProvider.vend.getByAccount(bankId, accountId).openOr(Nil)
      .foreach(a => ConsumerAccountAccesses.consumerAccountAccessProvider.vend.revoke(a.consumerAccountAccessId))

  feature("Consumer account access") {

    scenario("an application acting as itself initiates a payment through a view granted to its Consumer, until it is revoked",
      ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, VersionOfApi) {
      setPropsValues("open_corridor_enabled" -> "true")
      revokeAll()
      val viewId = createPaymentInitiationView()
      val consumerId = testConsumer.consumerId

      When("the application creates a promise through the view before its Consumer has access")
      val before = makePostRequest(promise(viewId).POST <@ (application1), promiseBody)
      Then("it is refused")
      before.code should equal(403)

      When("the account holder gives the Consumer access to the view")
      val granted = makePostRequest(access(Manage).POST <@ (user1), grantBody(consumerId, viewId))
      granted.code should equal(201)
      (granted.body \ "consumer_id").extract[String] should equal(consumerId)
      (granted.body \ "view_id").extract[String] should equal(viewId)
      (granted.body \ "granted_by_user_id").extract[String] should equal(resourceUser1.userId)
      val grantId = (granted.body \ "consumer_account_access_id").extract[String]

      And("giving it again is refused")
      val again = makePostRequest(access(Manage).POST <@ (user1), grantBody(consumerId, viewId))
      again.code should equal(409)
      message(again) should include(ConsumerAccountAccessAlreadyExists)

      When("the application creates a promise through the view")
      val created = makePostRequest(promise(viewId).POST <@ (application1), promiseBody)
      Then("it is created, held at PENDING, and records the Consumer")
      created.code should equal(201)
      (created.body \ "status").extract[String] should equal("PENDING")
      val trId = (created.body \ "id").extract[String]
      MappedTransactionRequest.findByTransactionRequestId(trId)
        .map(_.consumerId).openOr("") should equal(consumerId)

      And("the account holder sees the grant")
      val listed = makeGetRequest(access(SYSTEM_OWNER_VIEW_ID).GET <@ (user1))
      listed.code should equal(200)
      (listed.body \ "consumer_account_access").children.map(a => (a \ "consumer_account_access_id").extract[String]) should contain(grantId)

      When("the account holder revokes it")
      makeDeleteRequest(access(Manage) / grantId <@ (user1)).code should equal(204)
      And("revoking it again finds nothing")
      val gone = makeDeleteRequest(access(Manage) / grantId <@ (user1))
      gone.code should equal(404)
      message(gone) should include(ConsumerAccountAccessNotFound)

      Then("the application can no longer create a promise through the view")
      makePostRequest(promise(viewId).POST <@ (application1), promiseBody).code should equal(403)
    }

    scenario("a person calling through the Consumer does not get the Consumer's access", ApiEndpoint4, VersionOfApi) {
      setPropsValues("open_corridor_enabled" -> "true")
      revokeAll()
      val viewId = createPaymentInitiationView()
      Given("the view is granted to testConsumer2, the Consumer user2 calls through")
      makePostRequest(access(Manage).POST <@ (user1), grantBody(testConsumer2.consumerId, viewId)).code should equal(201)
      try {
        When("user2, a person, creates a promise through the view")
        val response = makePostRequest(promise(viewId).POST <@ (user2), promiseBody)
        Then("it is refused: the grant is the application's, not its Users'")
        response.code should equal(403)
      } finally revokeAll()
    }

    scenario("the grant does not count on an endpoint that does not admit an application", ApiEndpoint2, VersionOfApi) {
      revokeAll()
      val viewId = createPaymentInitiationView()
      makePostRequest(access(Manage).POST <@ (user1), grantBody(testConsumer.consumerId, viewId)).code should equal(201)
      try {
        When("the application calls a User-only endpoint of the same view")
        val response = makeGetRequest(access(viewId).GET <@ (application1))
        Then("its Consumer's access does not let it through")
        response.code should equal(403)
      } finally revokeAll()
    }

    scenario("the owner view, an unknown Consumer, a caller without access and an anonymous caller are refused",
      ApiEndpoint1, VersionOfApi) {
      revokeAll()
      val viewId = createPaymentInitiationView()

      val owner = makePostRequest(access(SYSTEM_OWNER_VIEW_ID).POST <@ (user1), grantBody(testConsumer.consumerId, SYSTEM_OWNER_VIEW_ID))
      owner.code should equal(400)
      message(owner) should include(CannotGrantConsumerAccountAccessToView)

      val unknown = makePostRequest(access(Manage).POST <@ (user1), grantBody("no-such-consumer", viewId))
      unknown.code should equal(404)
      message(unknown) should include(ConsumerNotFoundByConsumerId)

      Then("user2, who has no access to the account, can't grant")
      makePostRequest(access(Manage).POST <@ (user2), grantBody(testConsumer.consumerId, viewId)).code should equal(403)

      Then("nobody can grant anonymously")
      makePostRequest(access(Manage).POST, grantBody(testConsumer.consumerId, viewId)).code should equal(401)

      ConsumerAccountAccesses.consumerAccountAccessProvider.vend
        .find(testConsumer.consumerId, bankId, accountId, ViewId(viewId)).isEmpty should equal(true)
    }
  }
}
