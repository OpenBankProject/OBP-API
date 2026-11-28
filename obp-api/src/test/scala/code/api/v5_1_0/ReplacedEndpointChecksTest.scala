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
package code.api.v5_1_0

import code.accountholders.AccountHolders
import code.api.Constant._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.{APIUtil, ApiRole}
import code.api.util.ErrorMessages._
import code.api.v1_3_0.ReplacementJSON
import code.api.v2_0_0.BasicAccountsJSON
import code.api.v3_1_0.PostUserAuthContextUpdateJsonV310
import code.api.v4_0_0.ProductAttributeResponseJsonV400
import code.consumer.Consumers
import code.context.UserAuthContextUpdateProvider
import code.customer.CustomerX
import code.entitlement.Entitlement
import code.productattribute.ProductAttributeX
import code.scope.Scope
import code.setup.APIResponse
import code.usercustomerlinks.UserCustomerLink
import code.views.Views
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.{ProductAttributeType, StrongCustomerAuthentication}
import com.openbankproject.commons.util.ApiVersion
import org.json4s._
import org.json4s.native.Serialization.write
import org.scalatest.Tag

import java.util.Date
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Random

/**
 * Checks that the tests of the endpoints commented out in the versions below v5.1.0 made, ported to
 * the handlers that serve the same paths under /obp/v5.1.0/. Each feature names the old scenario it
 * comes from. Where the newer handler answers with another status code or message, the test checks
 * what it answers now.
 */
class ReplacedEndpointChecksTest extends V510ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v5_1_0.toString)

  private val bankId = testBankId1.value
  private val messageField = "message"
  private val otherBankAttributeName = "other bank attribute"
  private val otherProductAttributeName = "other product attribute"
  private val testPhoneNumber = "+49 123 456"

  private def messageOf(body: JValue): String = (body \ messageField).extract[String]

  // ─── Product attributes: from v3.1.0 ProductAttributeTest ────────────────────────────────

  feature("A product attribute is only read or changed through its own bank and product (v4.0.0 handlers)") {
    def attributeAt(bank: String, productCode: String, name: String): ProductAttribute =
      Await.result(
        ProductAttributeX.productAttributeProvider.vend.createOrUpdateProductAttribute(
          BankId(bank), ProductCode(productCode), None, name, ProductAttributeType.STRING, "12345678", None),
        Duration.Inf).openOrThrowException("could not create the test attribute")

    def attributePath(productAttributeId: String) =
      v5_1_0_Request / "banks" / bankId / "products" / "PRODUCT_CODE" / "attributes" / productAttributeId

    scenario("the attribute of another bank or another product is not found when read", VersionOfApi) {
      val otherBankAttribute = attributeAt("other-bank-for-attribute-get-v510", "PRODUCT_CODE", otherBankAttributeName)
      val otherProductAttribute = attributeAt(bankId, "OTHER_PRODUCT_CODE", otherProductAttributeName)
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.canGetProductAttribute.toString)

      val otherBank = makeGetRequest(attributePath(otherBankAttribute.productAttributeId).GET <@ (user1))
      otherBank.code should equal(404)
      messageOf(otherBank.body) should equal(ProductAttributeNotFound)

      val otherProduct = makeGetRequest(attributePath(otherProductAttribute.productAttributeId).GET <@ (user1))
      otherProduct.code should equal(404)
      messageOf(otherProduct.body) should equal(ProductAttributeNotFound)
    }

    scenario("the attribute of another bank or another product is not changed or moved", VersionOfApi) {
      val otherBankAttribute = attributeAt("other-bank-for-attribute-put-v510", "PRODUCT_CODE", otherBankAttributeName)
      val otherProductAttribute = attributeAt(bankId, "OTHER_PRODUCT_CODE", otherProductAttributeName)
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.canUpdateProductAttribute.toString)
      val body = write(SwaggerDefinitionsJSON.productAttributeJsonV400.copy(name = "taken over"))

      val otherBank = makePutRequest(attributePath(otherBankAttribute.productAttributeId).PUT <@ (user1), body)
      otherBank.code should equal(404)
      messageOf(otherBank.body) should equal(ProductAttributeNotFound)
      val storedOtherBank = Await.result(
        ProductAttributeX.productAttributeProvider.vend.getProductAttributeById(otherBankAttribute.productAttributeId),
        Duration.Inf).openOrThrowException("the test attribute is gone")
      storedOtherBank.bankId.value should equal("other-bank-for-attribute-put-v510")
      storedOtherBank.name should equal(otherBankAttributeName)

      val otherProduct = makePutRequest(attributePath(otherProductAttribute.productAttributeId).PUT <@ (user1), body)
      otherProduct.code should equal(404)
      val storedOtherProduct = Await.result(
        ProductAttributeX.productAttributeProvider.vend.getProductAttributeById(otherProductAttribute.productAttributeId),
        Duration.Inf).openOrThrowException("the test attribute is gone")
      storedOtherProduct.productCode.value should equal("OTHER_PRODUCT_CODE")
      storedOtherProduct.name should equal(otherProductAttributeName)
    }

    scenario("an attribute type that does not exist is refused", VersionOfApi) {
      val attribute = attributeAt(bankId, "PRODUCT_CODE", "typed attribute")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.canUpdateProductAttribute.toString)
      val response = makePutRequest(attributePath(attribute.productAttributeId).PUT <@ (user1),
        write(SwaggerDefinitionsJSON.productAttributeJsonV400.copy(`type` = "NO_SUCH_TYPE")))
      response.code should equal(400)
      messageOf(response.body) should startWith(InvalidJsonFormat)
    }

    scenario("an attribute read through its own bank and product is returned", VersionOfApi) {
      val attribute = attributeAt(bankId, "PRODUCT_CODE", "own attribute")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.canGetProductAttribute.toString)
      val response = makeGetRequest(attributePath(attribute.productAttributeId).GET <@ (user1))
      response.code should equal(200)
      response.body.extract[ProductAttributeResponseJsonV400].product_attribute_id should equal(attribute.productAttributeId)
    }

    // user3 is given no role in this suite, so the role check is not passed by a role another scenario granted.
    scenario("creating, reading or changing an attribute needs a login and the role for it", VersionOfApi) {
      val attribute = attributeAt(bankId, "PRODUCT_CODE", "role-checked attribute")
      val createPath = v5_1_0_Request / "banks" / bankId / "products" / "PRODUCT_CODE" / "attribute"
      val body = write(SwaggerDefinitionsJSON.productAttributeJsonV400)
      val calls: List[(String, Option[(Consumer, Token)] => APIResponse, ApiRole)] = List(
        ("create", u => makePostRequest(createPath.POST <@ (u), body), ApiRole.canCreateProductAttribute),
        ("read", u => makeGetRequest(attributePath(attribute.productAttributeId).GET <@ (u)), ApiRole.canGetProductAttribute),
        ("change", u => makePutRequest(attributePath(attribute.productAttributeId).PUT <@ (u), body), ApiRole.canUpdateProductAttribute))

      calls.foreach { case (action, call, role) =>
        Then(s"$action without a login is refused with 401")
        val anonymous = call(None)
        anonymous.code should equal(401)
        messageOf(anonymous.body) should equal(AuthenticatedUserIsRequired)

        Then(s"$action without $role is refused with 403")
        val withoutRole = call(user3)
        withoutRole.code should equal(403)
        messageOf(withoutRole.body) should startWith(UserHasMissingRoles)
        messageOf(withoutRole.body) should include(role.toString)
      }
    }
  }

  // ─── User Auth Context updates: from v3.1.0 UserAuthContextUpdateTest ───────────────────

  private def authContextUpdatesPath(bank: String) = v5_1_0_Request / "banks" / bank / "users" / "current" / "auth-context-updates"

  private def consumerPrimaryKeyOf(user: Option[(Consumer, Token)]): String =
    Consumers.consumers.vend.getConsumerByConsumerKey(user.get._1.key).map(_.id.get.toString).getOrElse("")

  private def challengeOf(userId: String, authContextUpdateId: String): String =
    UserAuthContextUpdateProvider.userAuthContextUpdateProvider.vend.getUserAuthContextUpdatesBox(userId)
      .toList.flatten.filter(_.userAuthContextUpdateId == authContextUpdateId).map(_.challenge).head

  private def requestUpdate(bank: String, user: Option[(Consumer, Token)], customerNumber: String) =
    makePostRequest((authContextUpdatesPath(bank) / StrongCustomerAuthentication.SMS.toString).POST <@ (user),
      write(SwaggerDefinitionsJSON.postUserAuthContextJson.copy(value = customerNumber)))

  private def answer(bank: String, authContextUpdateId: String, user: Option[(Consumer, Token)], challenge: String) =
    makePostRequest((authContextUpdatesPath(bank) / authContextUpdateId / "challenge").POST <@ (user),
      write(PostUserAuthContextUpdateJsonV310(answer = challenge)))

  private def isLinked(userId: String, customerId: String): Boolean =
    UserCustomerLink.userCustomerLink.vend.getUserCustomerLink(userId, customerId).isDefined

  // The users linked to a customer, read through the API (v4.0.0 getUserCustomerLinksByCustomerId).
  private def linkedUserIdsViaApi(bank: String, customerId: String): List[String] = {
    Entitlement.entitlement.vend.addEntitlement(bank, resourceUser1.userId, ApiRole.canGetUserCustomerLink.toString)
    val response = makeGetRequest((v5_1_0_Request / "banks" / bank / "user_customer_links" / "customers" / customerId).GET <@ (user1))
    response.code should equal(200)
    (response.body \ "user_customer_links").children.map(l => (l \ "user_id").extract[String])
  }

  feature("User Auth Context updates (v5.0.0 createUserAuthContextUpdateRequest and answerUserAuthContextUpdateChallenge)") {
    scenario("a request needs the App Scope or the User Role CanCreateUserAuthContextUpdate at the bank", VersionOfApi) {
      val customerNumber = createCustomerEndpointV510(bankId, "Customer for the role check", testPhoneNumber).customer_number
      def numberOfUpdatesOfUser2 = UserAuthContextUpdateProvider.userAuthContextUpdateProvider.vend
        .getUserAuthContextUpdatesBox(resourceUser2.userId).toList.flatten.size

      val updatesBefore = numberOfUpdatesOfUser2
      val refused = requestUpdate(bankId, user2, customerNumber)
      refused.code should equal(403)
      messageOf(refused.body) should include(ConsumerHasMissingRoles)
      numberOfUpdatesOfUser2 should equal(updatesBefore)

      Entitlement.entitlement.vend.addEntitlement(testBankId2.value, resourceUser2.userId, ApiRole.canCreateUserAuthContextUpdate.toString)
      requestUpdate(bankId, user2, customerNumber).code should equal(403)

      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser2.userId, ApiRole.canCreateUserAuthContextUpdate.toString)
      requestUpdate(bankId, user2, customerNumber).code should equal(201)
    }

    scenario("an update can only be answered at the bank it was requested at", VersionOfApi) {
      Scope.scope.vend.addScope(bankId, consumerPrimaryKeyOf(user1), ApiRole.canCreateUserAuthContextUpdate.toString())
      val customer = createCustomerEndpointV510(bankId, "Customer at the request bank", testPhoneNumber)
      val customerIdAtOtherBank = CustomerX.customerProvider.vend.addCustomer(
        testBankId2, customer.customer_number, "Customer At Other Bank", testPhoneNumber, "other-bank-customer@example.com",
        CustomerFaceImage(new Date(), "http://example.com/face.png"),
        new Date(), "single", 0, Nil, "Bachelor", "employed", true, new Date(),
        None, None, "", "", ""
      ).openOrThrowException("expected the customer at the other bank to be created").customerId

      val created = requestUpdate(bankId, user1, customer.customer_number)
      created.code should equal(201)
      val authContextUpdateId = (created.body \ "user_auth_context_update_id").extract[String]
      val challenge = challengeOf(resourceUser1.userId, authContextUpdateId)

      val atOtherBank = answer(testBankId2.value, authContextUpdateId, user1, challenge)
      atOtherBank.code should equal(400)
      messageOf(atOtherBank.body) should include(UserAuthContextUpdateNotFound)
      isLinked(resourceUser1.userId, customerIdAtOtherBank) should equal(false)

      val atRequestBank = answer(bankId, authContextUpdateId, user1, challenge)
      atRequestBank.code should equal(200)
      (atRequestBank.body \ "status").extract[String] should equal(UserAuthContextUpdateStatus.ACCEPTED.toString)
      isLinked(resourceUser1.userId, customer.customer_id) should equal(true)
      linkedUserIdsViaApi(bankId, customer.customer_id) should contain(resourceUser1.userId)
      isLinked(resourceUser1.userId, customerIdAtOtherBank) should equal(false)
    }

    scenario("another user cannot answer, or use up, the challenge of a user's update", VersionOfApi) {
      Scope.scope.vend.addScope(bankId, consumerPrimaryKeyOf(user1), ApiRole.canCreateUserAuthContextUpdate.toString())
      val customer = createCustomerEndpointV510(bankId, "Customer for the other user check", testPhoneNumber)
      val created = requestUpdate(bankId, user1, customer.customer_number)
      created.code should equal(201)
      val authContextUpdateId = (created.body \ "user_auth_context_update_id").extract[String]
      val challenge = challengeOf(resourceUser1.userId, authContextUpdateId)

      val byUser2 = answer(bankId, authContextUpdateId, user2, challenge)
      byUser2.code should equal(400)
      messageOf(byUser2.body) should include(UserAuthContextUpdateNotFound)
      isLinked(resourceUser2.userId, customer.customer_id) should equal(false)
      isLinked(resourceUser1.userId, customer.customer_id) should equal(false)

      val byUser1 = answer(bankId, authContextUpdateId, user1, challenge)
      byUser1.code should equal(200)
      (byUser1.body \ "status").extract[String] should equal(UserAuthContextUpdateStatus.ACCEPTED.toString)
      isLinked(resourceUser1.userId, customer.customer_id) should equal(true)
      val linked = linkedUserIdsViaApi(bankId, customer.customer_id)
      linked should contain(resourceUser1.userId)
      linked should not contain (resourceUser2.userId)
    }
  }

  // ─── Balances: from v4.0.0 AccountBalanceTest ────────────────────────────────────────────

  private def grantCustomViewToUser2(accountId: String, viewName: String, allowedActions: List[String]): View = {
    val bankIdAccountId = BankIdAccountId(BankId(bankId), AccountId(accountId))
    val view = Views.views.vend.createCustomView(bankIdAccountId, CreateViewJson(
      name = viewName, description = "Used by ReplacedEndpointChecksTest", metadata_view = viewName,
      is_public = false, which_alias_to_use = "", hide_metadata_if_alias_used = false, allowed_actions = allowedActions
    )).openOrThrowException("Could not create the custom view")
    Views.views.vend.grantAccessToCustomView(BankIdAccountIdViewId(BankId(bankId), AccountId(accountId), view.viewId), resourceUser2)
      .openOrThrowException("Could not grant the custom view")
    view
  }

  private def balanceAccountIds(body: JValue): List[String] =
    (body \ "accounts").children.map(a => (a \ "account_id").extract[String])

  feature("Balances (v5.1.0 getBankAccountsBalances and getAllBankAccountBalances)") {
    scenario("a user without access to an account sees none of its balances", VersionOfApi) {
      val account = getPrivateAccountsViaEndpoint(bankId, user1).body.extract[BasicAccountsJSON].accounts.head
      val oneAccount = makeGetRequest((v5_1_0_Request / "banks" / bankId / "accounts" / account.id / "balances").GET <@ (user2))
      oneAccount.code should equal(403)
      messageOf(oneAccount.body) should include(CAN_SEE_BANK_ACCOUNT_BALANCE)

      val atBank = makeGetRequest((v5_1_0_Request / "banks" / bankId / "balances").GET <@ (user2))
      atBank.code should equal(200)
      balanceAccountIds(atBank.body) should not contain (account.id)
    }

    scenario("a custom view without the balance permission does not show the balance, even when the same view id allows it on another account",
      VersionOfApi) {
      val accounts = getPrivateAccountsViaEndpoint(bankId, user1).body.extract[BasicAccountsJSON].accounts
      accounts.length should be >= 2
      val accountWithBalance = accounts(0)
      val accountWithoutBalance = accounts(1)
      val viewName = s"_balance${Random.alphanumeric.take(8).mkString.toLowerCase}"
      grantCustomViewToUser2(accountWithBalance.id, viewName, List(CAN_SEE_TRANSACTION_THIS_BANK_ACCOUNT, CAN_SEE_BANK_ACCOUNT_BALANCE))
      grantCustomViewToUser2(accountWithoutBalance.id, viewName, List(CAN_SEE_TRANSACTION_THIS_BANK_ACCOUNT, CAN_SEE_BANK_ACCOUNT_LABEL))

      val atBank = makeGetRequest((v5_1_0_Request / "banks" / bankId / "balances").GET <@ (user2))
      atBank.code should equal(200)
      balanceAccountIds(atBank.body) should equal(List(accountWithBalance.id))

      val oneAccount = makeGetRequest(
        (v5_1_0_Request / "banks" / bankId / "accounts" / accountWithoutBalance.id / "balances").GET <@ (user2))
      oneAccount.code should equal(403)
      messageOf(oneAccount.body) should include(CAN_SEE_BANK_ACCOUNT_BALANCE)
    }
  }

  // ─── Create view: from v1.2.1 API1_2_1Test ───────────────────────────────────────────────

  feature("POST /banks/BANK_ID/accounts/ACCOUNT_ID/views only grants the caller's actions (v3.0.0 createViewForBankAccount)") {
    scenario("a view with an action that none of the caller's views has is refused, one with the caller's actions is created", VersionOfApi) {
      val accountId = testAccountId1.value
      val viewsRequest = v5_1_0_Request / "banks" / bankId / "accounts" / accountId / "views"
      val viewName = s"_view${Random.alphanumeric.take(8).mkString.toLowerCase}"
      val ownActions = List(CAN_SEE_TRANSACTION_THIS_BANK_ACCOUNT, CAN_SEE_BANK_ACCOUNT_BALANCE)
      val view = SwaggerDefinitionsJSON.createViewJsonV300.copy(name = viewName, metadata_view = viewName, allowed_actions = ownActions)

      val tooMuch = makePostRequest(viewsRequest.POST <@ (user1),
        write(view.copy(allowed_actions = ownActions :+ "can_do_what_no_view_of_the_caller_can")))
      tooMuch.code should equal(403)
      messageOf(tooMuch.body) should startWith(SourceViewHasLessPermission)
      Views.views.vend.customView(ViewId(viewName), BankIdAccountId(testBankId1, AccountId(accountId))).isDefined should equal(false)

      val created = makePostRequest(viewsRequest.POST <@ (user1), write(view))
      created.code should equal(201)
      (created.body \ "id").extract[String] should equal(viewName)
    }
  }

  // ─── Create account: from v2.0.0 and v2.2.0 AccountTest ─────────────────────────────────

  feature("PUT /banks/BANK_ID/accounts/ACCOUNT_ID refuses an ACCOUNT_ID that already exists (v5.0.0 createAccount)") {
    scenario("a second user PUTting an existing ACCOUNT_ID is refused and does not become a holder", VersionOfApi) {
      val existingAccountId = "EXISTING_ACCOUNT_ID_V510"
      def createAs(user: Option[(Consumer, Token)], userId: String) = makePutRequest(
        (v5_1_0_Request / "banks" / bankId / "accounts" / existingAccountId).PUT <@ (user),
        write(SwaggerDefinitionsJSON.createAccountRequestJsonV500.copy(
          user_id = Some(userId), label = "duplicate check", account_routings = Some(Nil))))
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.canCreateAccount.toString)
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser2.userId, ApiRole.canCreateAccount.toString)

      createAs(user1, resourceUser1.userId).code should equal(201)

      // docs/http_status_conventions.md: a duplicate create answers 409, but the versions that still
      // answer 400 are left alone until the contract is decided. v5.0.0 is one of them.
      val attemptByUser2 = createAs(user2, resourceUser2.userId)
      attemptByUser2.code should equal(400)
      messageOf(attemptByUser2.body) should include(AccountIdAlreadyExists)
      val holderUserIds = AccountHolders.accountHolders.vend.getAccountHolders(testBankId1, AccountId(existingAccountId)).map(_.userId)
      holderUserIds should contain(resourceUser1.userId)
      holderUserIds should not contain (resourceUser2.userId)
    }

    scenario("an ACCOUNT_ID in the wrong format is refused and no account is created", VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.canCreateAccount.toString)
      val response = makePutRequest(
        (v5_1_0_Request / "banks" / bankId / "accounts" / "account%20with%20spaces").PUT <@ (user1),
        write(SwaggerDefinitionsJSON.createAccountRequestJsonV500.copy(
          user_id = Some(resourceUser1.userId), label = "format check", account_routings = Some(Nil))))
      response.code should equal(400)
      messageOf(response.body) should include(InvalidAccountIdFormat)
      AccountHolders.accountHolders.vend.getAccountHolders(testBankId1, AccountId("account with spaces")) should be(empty)
    }
  }

  // ─── Smaller cases, one old scenario each ────────────────────────────────────────────────

  feature("Not found cases") {
    scenario("a counterparty for an account that does not exist is refused (v4.0.0 createCounterparty, from v2.2.0)", VersionOfApi) {
      val response = makePostRequest(
        (v5_1_0_Request / "banks" / bankId / "accounts" / "notExistingAccountId" / SYSTEM_OWNER_VIEW_ID / "counterparties").POST <@ (user1),
        write(SwaggerDefinitionsJSON.postCounterpartyJson400))
      response.code should equal(404)
      messageOf(response.body) should startWith(BankAccountNotFound)
    }

    scenario("a consumer that does not exist is not found (v5.1.0 getConsumer, from v3.1.0)", VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canGetConsumers.toString)
      val response = makeGetRequest((v5_1_0_Request / "management" / "consumers" / "non-existing-consumer-id").GET <@ (user1))
      response.code should equal(404)
      messageOf(response.body) should startWith(ConsumerNotFoundByConsumerId)
    }

    scenario("my consents at a bank that does not exist are not found (v5.1.0 getMyConsentsByBank, from v4.0.0)", VersionOfApi) {
      val response = makeGetRequest((v5_1_0_Request / "banks" / "SOME_BANK_WHICH_SHOULD_NOT_EXIST" / "my" / "consents").GET <@ (user1))
      response.code should equal(404)
      messageOf(response.body) should startWith(BankNotFound)
    }
  }

  feature("Anonymous reads that depend on a prop") {
    scenario("a product is read without a login only when products are public (v5.0.0 getProduct, from v4.0.0)", VersionOfApi) {
      val json = SwaggerDefinitionsJSON.postPutProductJsonV310
      Await.result(
        code.api.util.NewStyle.function.createOrUpdateProduct(
          bankId = bankId, code = "ANONYMOUS_GET_CODE_V510", parentProductCode = None, name = json.name,
          category = json.category, family = json.family, superFamily = json.super_family,
          moreInfoUrl = json.more_info_url, termsAndConditionsUrl = null, details = json.details,
          description = json.description, metaLicenceId = json.meta.license.id,
          metaLicenceName = json.meta.license.name, callContext = None),
        Duration(30, "seconds"))
      val response = makeGetRequest((v5_1_0_Request / "banks" / bankId / "products" / "ANONYMOUS_GET_CODE_V510").GET)
      if (APIUtil.getProductsIsPublic) {
        response.code should equal(200)
      } else {
        response.code should equal(401)
        messageOf(response.body) should equal(AuthenticatedUserIsRequired)
      }
    }

    scenario("the ATMs of a bank are read without a login only when ATMs are public (v5.1.0 getAtms, from v4.0.0)", VersionOfApi) {
      val response = makeGetRequest((v5_1_0_Request / "banks" / bankId / "atms").GET)
      if (APIUtil.getAtmsIsPublic) {
        response.code should equal(200)
      } else {
        response.code should equal(401)
        messageOf(response.body) should equal($AuthenticatedUserIsRequired)
      }
    }
  }

  // ─── Create card: from v3.1.0 CardTest ───────────────────────────────────────────────────

  feature("POST /management/banks/BANK_ID/cards checks its input (v5.0.0 addCardForBank)") {
    scenario("a card is refused for each kind of wrong input, then created once", VersionOfApi) {
      val customerId = createCustomerEndpointV510(bankId, "Card holder", testPhoneNumber).customer_id
      val properCard = SwaggerDefinitionsJSON.createPhysicalCardJsonV500.copy(
        account_id = testAccountId1.value, issue_number = "123", customer_id = customerId,
        card_number = s"4${Random.nextInt(1000000000)}")
      val cardsRequest = v5_1_0_Request / "management" / "banks" / bankId / "cards"

      Then("anonymous access is refused")
      val anonymous = makePostRequest(cardsRequest.POST, write(properCard))
      anonymous.code should equal(401)
      messageOf(anonymous.body) should include(AuthenticatedUserIsRequired)

      Then("a user without the role is refused")
      val noRole = makePostRequest(cardsRequest.POST <@ (user1), write(properCard))
      noRole.code should equal(403)
      messageOf(noRole.body) should include(ApiRole.canCreateCardsForBank.toString)

      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.canCreateCardsForBank.toString)
      def post(json: String) = makePostRequest(cardsRequest.POST <@ (user1), json)

      Then("a body that is not a card is refused")
      val wrongJson = post(write(testBankId1))
      wrongJson.code should equal(400)
      messageOf(wrongJson.body) should startWith(InvalidJsonFormat)

      Then("an action that does not exist is refused")
      val wrongAllows = post(write(properCard.copy(allows = List("123"))))
      wrongAllows.code should equal(400)
      messageOf(wrongAllows.body) should include(AllowedValuesAre)

      Then("a replacement reason that does not exist is refused")
      val wrongReplacement = post(write(properCard.copy(replacement = Some(ReplacementJSON(new Date(), "Wrong")))))
      wrongReplacement.code should equal(400)
      messageOf(wrongReplacement.body) should include(AllowedValuesAre)

      Then("an issue number longer than 10 characters is refused")
      val longIssueNumber = post(write(properCard.copy(issue_number = "01234567891")))
      longIssueNumber.code should equal(400)
      messageOf(longIssueNumber.body) should include(maximumLimitExceeded.replace("10000", "10"))

      Then("an account that does not exist is not found")
      val wrongAccount = post(write(properCard.copy(account_id = "no-such-account")))
      wrongAccount.code should equal(404)
      messageOf(wrongAccount.body) should include(BankAccountNotFound)

      Then("a customer that does not exist is not found")
      val wrongCustomer = post(write(properCard.copy(customer_id = "wrongId")))
      wrongCustomer.code should equal(404)
      messageOf(wrongCustomer.body) should include(CustomerNotFoundByCustomerId)

      Then("the proper card is created with the fields it was sent")
      val created = post(write(properCard))
      created.code should equal(201)
      (created.body \ "card_number").extract[String] should equal(properCard.card_number)
      (created.body \ "issue_number").extract[String] should equal(properCard.issue_number)
      (created.body \ "name_on_card").extract[String] should equal(properCard.name_on_card)
      (created.body \ "account" \ "id").extract[String] should equal(properCard.account_id)
      (created.body \ "brand").extract[String] should equal(properCard.brand)

      Then("the same card number and issue number at the bank are refused")
      val duplicate = post(write(properCard))
      duplicate.code should equal(400)
      messageOf(duplicate.body) should include(CardAlreadyExists)
    }
  }
}
