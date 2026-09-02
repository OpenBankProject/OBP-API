package code.api.v7_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages._
import code.api.v6_0_0.{ActiveRateLimitsJsonV600, ApiProductAttributeJsonV600, PostPutApiProductJsonV600}
import code.apicollection.MappedApiCollectionsProvider
import code.apicollectionendpoint.MappedApiCollectionEndpointsProvider
import code.scope.Scope
import code.api.v7_0_0.JSONFactory700.{ApiProductSubscriptionAttributeJsonV700, ApiProductSubscriptionAttributeResponseJsonV700, ApiProductSubscriptionJsonV700, ApiProductSubscriptionsJsonV700, PostApiProductSubscriptionJsonV700, PutApiProductSubscriptionStatusJsonV700}
import code.api.v7_0_0.Http4s700.Implementations7_0_0
import code.consumer.Consumers
import code.entitlement.Entitlement
import code.setup.ServerSetupWithTestData
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s._
import org.json4s.native.Serialization.write
import org.scalatest.Tag

import java.util.UUID

/**
 * API Product Subscription (Phase 2 of API_PRODUCT_SUBSCRIPTION_PLAN.md).
 *
 * The API Product itself is a v6.0.0 resource, so products and their attributes are created through
 * v6.0.0; every subscription endpoint under test is v7.0.0.
 *
 * user1 owns consumer1 (created_by_user_id = userId1), user2 owns consumer2. Entitlements accumulate
 * on a user across scenarios, so each "without the role" check uses a role that no earlier scenario
 * granted to that user.
 */
class ApiProductSubscriptionTest extends ServerSetupWithTestData {

  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations7_0_0.createApiProductSubscription))
  object ApiEndpoint2 extends Tag(nameOf(Implementations7_0_0.getMyApiProductSubscriptions))
  object ApiEndpoint3 extends Tag(nameOf(Implementations7_0_0.getMyApiProductSubscription))
  object ApiEndpoint4 extends Tag(nameOf(Implementations7_0_0.updateMyApiProductSubscriptionStatus))
  object ApiEndpoint5 extends Tag(nameOf(Implementations7_0_0.getApiProductSubscriptionsByProduct))
  object ApiEndpoint6 extends Tag(nameOf(Implementations7_0_0.getConsumerApiProductSubscriptions))
  object ApiEndpoint7 extends Tag(nameOf(Implementations7_0_0.getApiProductSubscription))
  object ApiEndpoint8 extends Tag(nameOf(Implementations7_0_0.updateApiProductSubscriptionStatus))
  object ApiEndpoint9 extends Tag(nameOf(Implementations7_0_0.deleteApiProductSubscription))
  object ApiEndpoint10 extends Tag(nameOf(Implementations7_0_0.createApiProductSubscriptionAttribute))
  object ApiEndpoint11 extends Tag(nameOf(Implementations7_0_0.updateApiProductSubscriptionAttribute))
  object ApiEndpoint12 extends Tag(nameOf(Implementations7_0_0.getApiProductSubscriptionAttributes))
  object ApiEndpoint13 extends Tag(nameOf(Implementations7_0_0.deleteApiProductSubscriptionAttribute))

  def v6 = baseRequest / "obp" / "v6.0.0"
  def v7 = baseRequest / "obp" / "v7.0.0"

  lazy val bankId: String = testBankId1.value

  // Ownership means Consumer.createdByUserId == caller.userId. The harness's default consumers are
  // stamped with TestServer.userIdN, which need not equal resourceUserN.userId when the resource user
  // already existed, so create consumers whose owner is exactly the resource user we call as.
  def createOwnedConsumer(ownerUserId: String, label: String): String = Consumers.consumers.vend.createConsumer(
    key = Some(UUID.randomUUID().toString.replace("-", "")),
    secret = Some(UUID.randomUUID().toString.replace("-", "")),
    isActive = Some(true),
    name = Some(s"api-product-subscription-test-$label"),
    appType = None,
    description = Some("created by ApiProductSubscriptionTest"),
    developerEmail = Some(s"$label@example.com"),
    redirectURL = None,
    createdByUserId = Some(ownerUserId),
    None, None, None
  ).map(_.consumerId.get).openOrThrowException("could not create test consumer")
  lazy val consumerId1: String = createOwnedConsumer(resourceUser1.userId, "user1")
  lazy val consumerId2: String = createOwnedConsumer(resourceUser2.userId, "user2")

  def newProductCode(): String = "sub-test-" + UUID.randomUUID().toString.take(8)

  def grant(userId: String, bank: String, role: String): Unit =
    Entitlement.entitlement.vend.addEntitlement(bank, userId, role)

  def createProduct(code: String, collectionId: Option[String] = None): Unit = {
    grant(resourceUser1.userId, bankId, CanCreateApiProduct.toString)
    val json = PostPutApiProductJsonV600(
      parent_api_product_code = None, name = s"Subscription test $code", category = None,
      more_info_url = None, terms_and_conditions_url = None, description = None, collection_id = collectionId,
      monthly_subscription_currency = None, monthly_subscription_amount = None,
      per_second_call_limit = Some(10L), per_minute_call_limit = Some(100L), per_hour_call_limit = Some(-1L),
      per_day_call_limit = Some(-1L), per_week_call_limit = Some(-1L), per_month_call_limit = Some(-1L), tags = None)
    val response = makePostRequest((v6 / "banks" / bankId / "api-products" / code).POST <@ (user1), write(json))
    response.code should equal(201)
  }

  def setProductAttribute(code: String, name: String, value: String): Unit = {
    grant(resourceUser1.userId, bankId, CanCreateApiProductAttribute.toString)
    val json = ApiProductAttributeJsonV600(name = name, `type` = "STRING", value = value, is_active = Some(true))
    val response = makePostRequest((v6 / "banks" / bankId / "api-products" / code / "attribute").POST <@ (user1), write(json))
    response.code should equal(201)
  }

  def subscribe(code: String, consumerId: String, as: Option[(Consumer, Token)]) =
    makePostRequest((v7 / "banks" / bankId / "api-products" / code / "subscriptions").POST <@ (as),
      write(PostApiProductSubscriptionJsonV700(consumer_id = consumerId, start_date = None, end_date = None)))

  def subscribed(code: String, consumerId: String, as: Option[(Consumer, Token)]): ApiProductSubscriptionJsonV700 = {
    val response = subscribe(code, consumerId, as)
    response.code should equal(201)
    response.body.extract[ApiProductSubscriptionJsonV700]
  }

  def putMyStatus(id: String, status: String, as: Option[(Consumer, Token)]) =
    makePutRequest((v7 / "my" / "api-product-subscriptions" / id / "status").PUT <@ (as),
      write(PutApiProductSubscriptionStatusJsonV700(status = status, end_date = None)))

  def putStatus(id: String, status: String, as: Option[(Consumer, Token)]) =
    makePutRequest((v7 / "management" / "api-product-subscriptions" / id / "status").PUT <@ (as),
      write(PutApiProductSubscriptionStatusJsonV700(status = status, end_date = None)))

  def errorOf(response: code.setup.APIResponse): String = response.body.extract[ErrorMessage].message

  feature("Create subscription: developer self-service, /my endpoints, cancel") {
    scenario("Own consumer, open product: active at once, then list, get, cancel, re-subscribe", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, ApiEndpoint4, VersionOfApi) {
      val code = newProductCode()
      createProduct(code)

      When("user1 subscribes their own consumer without any role")
      val created = subscribed(code, consumerId1, user1)
      Then("the subscription is active at once, BILLING_SYSTEM being absent")
      created.status should equal("active")
      created.consumer_id should equal(consumerId1)
      created.bank_id should equal(bankId)
      created.api_product_code should equal(code)
      created.created_by_user_id should equal(resourceUser1.userId)

      And("a second subscription to the same product is refused with 409")
      val duplicate = subscribe(code, consumerId1, user1)
      duplicate.code should equal(409)
      errorOf(duplicate) should startWith(ApiProductSubscriptionAlreadyExists)

      And("it is listed under /my")
      val mine = makeGetRequest((v7 / "my" / "api-product-subscriptions").GET <@ (user1))
      mine.code should equal(200)
      mine.body.extract[ApiProductSubscriptionsJsonV700].api_product_subscriptions.map(_.api_product_subscription_id) should contain(created.api_product_subscription_id)

      And("the owner can read it, another developer gets 404")
      makeGetRequest((v7 / "my" / "api-product-subscriptions" / created.api_product_subscription_id).GET <@ (user1)).code should equal(200)
      val other = makeGetRequest((v7 / "my" / "api-product-subscriptions" / created.api_product_subscription_id).GET <@ (user2))
      other.code should equal(404)
      errorOf(other) should startWith(ApiProductSubscriptionNotFound)

      And("the owner may not set any status but cancelled")
      val activate = putMyStatus(created.api_product_subscription_id, "active", user1)
      activate.code should equal(400)
      errorOf(activate) should startWith(InvalidApiProductSubscriptionStatusTransition)

      And("another developer may not cancel it")
      val foreignCancel = putMyStatus(created.api_product_subscription_id, "cancelled", user2)
      foreignCancel.code should equal(403)
      errorOf(foreignCancel) should startWith(ConsumerNotOwnedByUser)

      And("the owner cancels it")
      val cancelled = putMyStatus(created.api_product_subscription_id, "cancelled", user1)
      cancelled.code should equal(200)
      cancelled.body.extract[ApiProductSubscriptionJsonV700].status should equal("cancelled")

      And("after cancelling, a new subscription can be created")
      val again = subscribed(code, consumerId1, user1)
      again.api_product_subscription_id should not equal created.api_product_subscription_id
    }

    scenario("consumer_id is required and must exist; anonymous is 401", ApiEndpoint1, VersionOfApi) {
      val code = newProductCode()
      createProduct(code)
      subscribe(code, "", user1).code should equal(400)
      val unknown = subscribe(code, "no-such-consumer-" + UUID.randomUUID(), user1)
      unknown.code should equal(404)
      errorOf(unknown) should startWith(ConsumerNotFoundByConsumerId)
      subscribe(code, consumerId1, None).code should equal(401)
      // The v6.0.0 API Product lookup reports a missing product with 400 (existing convention).
      val noProduct = subscribe("no-such-product", consumerId1, user1)
      noProduct.code should equal(400)
      errorOf(noProduct) should startWith(ApiProductNotFound)
    }

    scenario("Someone else's consumer needs the create role; the bank enrols a partner's consumer", ApiEndpoint1, VersionOfApi) {
      val code = newProductCode()
      createProduct(code)
      When("user1 tries to subscribe consumer2, which user2 created, without the role")
      val refused = subscribe(code, consumerId2, user1)
      Then("403")
      refused.code should equal(403)
      errorOf(refused) should startWith(UserHasMissingRoles)
      When("user1 holds CanCreateApiProductSubscriptionAtOneBank at the product's bank")
      grant(resourceUser1.userId, bankId, CanCreateApiProductSubscriptionAtOneBank.toString)
      Then("the enrolment succeeds")
      val enrolled = subscribed(code, consumerId2, user1)
      enrolled.consumer_id should equal(consumerId2)
      enrolled.created_by_user_id should equal(resourceUser1.userId)
    }

    scenario("SELF_SUBSCRIBE=false closes a product to self-service; the role at the product's bank reopens it", ApiEndpoint1, VersionOfApi) {
      val code = newProductCode()
      createProduct(code)
      setProductAttribute(code, "SELF_SUBSCRIBE", "false")
      When("user2 subscribes their own consumer without a role")
      val refused = subscribe(code, consumerId2, user2)
      Then("403")
      refused.code should equal(403)
      errorOf(refused) should startWith(UserHasMissingRoles)
      When("user2 holds CanCreateApiProductSubscriptionAtOneBank at the product's bank")
      grant(resourceUser2.userId, bankId, CanCreateApiProductSubscriptionAtOneBank.toString)
      Then("201")
      subscribed(code, consumerId2, user2).status should equal("active")
    }
  }

  feature("Status machine via /management, BILLING_SYSTEM=manual") {
    scenario("requested until a bank admin activates; every transition; invalid ones refused", ApiEndpoint8, VersionOfApi) {
      val code = newProductCode()
      createProduct(code)
      setProductAttribute(code, "BILLING_SYSTEM", "manual")
      val created = subscribed(code, consumerId1, user1)
      created.status should equal("requested")
      val id = created.api_product_subscription_id

      When("user2 tries to activate without the role")
      val noRole = putStatus(id, "active", user2)
      noRole.code should equal(403)
      errorOf(noRole) should startWith(UserHasMissingRoles)

      When("user2 holds the AtOneBank role at the wrong bank")
      grant(resourceUser2.userId, "some-other-bank", CanUpdateApiProductSubscriptionStatusAtOneBank.toString)
      Then("still 403")
      putStatus(id, "active", user2).code should equal(403)

      When("user2 holds the AtOneBank role at the product's bank")
      grant(resourceUser2.userId, bankId, CanUpdateApiProductSubscriptionStatusAtOneBank.toString)
      Then("requested -> active")
      val activated = putStatus(id, "active", user2)
      activated.code should equal(200)
      activated.body.extract[ApiProductSubscriptionJsonV700].status should equal("active")

      And("an unknown status is refused")
      val bogus = putStatus(id, "bogus", user2)
      bogus.code should equal(400)
      errorOf(bogus) should startWith(InvalidApiProductSubscriptionStatus)

      And("active -> requested is refused")
      val backwards = putStatus(id, "requested", user2)
      backwards.code should equal(400)
      errorOf(backwards) should startWith(InvalidApiProductSubscriptionStatusTransition)

      And("active -> past_due -> suspended -> active -> cancelled")
      putStatus(id, "past_due", user2).body.extract[ApiProductSubscriptionJsonV700].status should equal("past_due")
      putStatus(id, "suspended", user2).body.extract[ApiProductSubscriptionJsonV700].status should equal("suspended")
      putStatus(id, "active", user2).body.extract[ApiProductSubscriptionJsonV700].status should equal("active")
      putStatus(id, "cancelled", user2).body.extract[ApiProductSubscriptionJsonV700].status should equal("cancelled")

      And("cancelled is terminal")
      val revive = putStatus(id, "active", user2)
      revive.code should equal(400)
      errorOf(revive) should startWith(InvalidApiProductSubscriptionStatusTransition)
    }
  }

  feature("Management reads") {
    scenario("by product, by id, and by consumer filtered to the banks where the role is held", ApiEndpoint5, ApiEndpoint6, ApiEndpoint7, VersionOfApi) {
      val code = newProductCode()
      createProduct(code)
      val created = subscribed(code, consumerId1, user1)

      When("user2 lists subscribers of the product without the role")
      val noRole = makeGetRequest((v7 / "banks" / bankId / "api-products" / code / "subscriptions").GET <@ (user2))
      Then("403")
      noRole.code should equal(403)

      And("by consumer is refused too: user2 neither created the consumer nor holds the role anywhere")
      val byConsumerNoRole = makeGetRequest((v7 / "management" / "consumers" / consumerId1 / "api-product-subscriptions").GET <@ (user2))
      byConsumerNoRole.code should equal(403)
      errorOf(byConsumerNoRole) should startWith(UserHasMissingRoles)

      And("with the role at another bank only, by consumer answers 200 but hides this bank's subscription")
      grant(resourceUser2.userId, "some-other-bank", CanGetApiProductSubscriptionAtOneBank.toString)
      val byConsumerOtherBank = makeGetRequest((v7 / "management" / "consumers" / consumerId1 / "api-product-subscriptions").GET <@ (user2))
      byConsumerOtherBank.code should equal(200)
      byConsumerOtherBank.body.extract[ApiProductSubscriptionsJsonV700].api_product_subscriptions.map(_.api_product_subscription_id) should not contain created.api_product_subscription_id

      When("user2 holds CanGetApiProductSubscriptionAtOneBank at the product's bank")
      grant(resourceUser2.userId, bankId, CanGetApiProductSubscriptionAtOneBank.toString)
      val byProduct = makeGetRequest((v7 / "banks" / bankId / "api-products" / code / "subscriptions").GET <@ (user2))
      byProduct.code should equal(200)
      byProduct.body.extract[ApiProductSubscriptionsJsonV700].api_product_subscriptions.map(_.api_product_subscription_id) should contain(created.api_product_subscription_id)

      And("by id works with the same role")
      val byId = makeGetRequest((v7 / "management" / "api-product-subscriptions" / created.api_product_subscription_id).GET <@ (user2))
      byId.code should equal(200)
      byId.body.extract[ApiProductSubscriptionJsonV700].api_product_subscription_id should equal(created.api_product_subscription_id)

      And("by consumer now shows the subscription at the bank where the role is held")
      val byConsumer = makeGetRequest((v7 / "management" / "consumers" / consumerId1 / "api-product-subscriptions").GET <@ (user2))
      byConsumer.code should equal(200)
      byConsumer.body.extract[ApiProductSubscriptionsJsonV700].api_product_subscriptions.map(_.api_product_subscription_id) should contain(created.api_product_subscription_id)

      And("the consumer's creator sees them all without any role")
      val byConsumerOwner = makeGetRequest((v7 / "management" / "consumers" / consumerId1 / "api-product-subscriptions").GET <@ (user1))
      byConsumerOwner.code should equal(200)
      byConsumerOwner.body.extract[ApiProductSubscriptionsJsonV700].api_product_subscriptions.map(_.api_product_subscription_id) should contain(created.api_product_subscription_id)
    }
  }

  feature("Attributes and delete") {
    scenario("create, list, update, delete attributes; the owner sees them; delete the subscription", ApiEndpoint9, ApiEndpoint10, ApiEndpoint11, ApiEndpoint12, ApiEndpoint13, VersionOfApi) {
      val code = newProductCode()
      createProduct(code)
      val created = subscribed(code, consumerId1, user1)
      val id = created.api_product_subscription_id
      val attributeJson = write(ApiProductSubscriptionAttributeJsonV700(name = "STRIPE_SUBSCRIPTION_ID", `type` = "STRING", value = "sub_1", is_active = Some(true)))

      When("user2 creates an attribute without the role")
      makePostRequest((v7 / "management" / "api-product-subscriptions" / id / "attribute").POST <@ (user2), attributeJson).code should equal(403)
      When("user2 holds CanCreateApiProductSubscriptionAttributeAtOneBank at the subscription's bank")
      grant(resourceUser2.userId, bankId, CanCreateApiProductSubscriptionAttributeAtOneBank.toString)
      val createdAttribute = makePostRequest((v7 / "management" / "api-product-subscriptions" / id / "attribute").POST <@ (user2), attributeJson)
      createdAttribute.code should equal(201)
      val attribute = createdAttribute.body.extract[ApiProductSubscriptionAttributeResponseJsonV700]
      attribute.api_product_subscription_id should equal(id)
      attribute.value should equal("sub_1")

      And("the attributes can be listed with the get role (granted earlier at this bank)")
      grant(resourceUser2.userId, bankId, CanGetApiProductSubscriptionAtOneBank.toString)
      val listed = makeGetRequest((v7 / "management" / "api-product-subscriptions" / id / "attributes").GET <@ (user2))
      listed.code should equal(200)
      listed.body.extract[List[ApiProductSubscriptionAttributeResponseJsonV700]].map(_.api_product_subscription_attribute_id) should contain(attribute.api_product_subscription_attribute_id)

      And("the owner sees the attribute on their subscription without any role")
      val mine = makeGetRequest((v7 / "my" / "api-product-subscriptions" / id).GET <@ (user1))
      mine.code should equal(200)
      mine.body.extract[ApiProductSubscriptionJsonV700].attributes.getOrElse(Nil).map(_.value) should contain("sub_1")

      And("the attribute can be updated with the update role")
      val updateJson = write(ApiProductSubscriptionAttributeJsonV700(name = "STRIPE_SUBSCRIPTION_ID", `type` = "STRING", value = "sub_2", is_active = Some(true)))
      makePutRequest((v7 / "management" / "api-product-subscriptions" / id / "attributes" / attribute.api_product_subscription_attribute_id).PUT <@ (user2), updateJson).code should equal(403)
      grant(resourceUser2.userId, bankId, CanUpdateApiProductSubscriptionAttributeAtOneBank.toString)
      val updated = makePutRequest((v7 / "management" / "api-product-subscriptions" / id / "attributes" / attribute.api_product_subscription_attribute_id).PUT <@ (user2), updateJson)
      updated.code should equal(200)
      updated.body.extract[ApiProductSubscriptionAttributeResponseJsonV700].value should equal("sub_2")

      And("an attribute of another subscription is not reachable through this one")
      val otherCode = newProductCode()
      createProduct(otherCode)
      val otherId = subscribed(otherCode, consumerId1, user1).api_product_subscription_id
      makePutRequest((v7 / "management" / "api-product-subscriptions" / otherId / "attributes" / attribute.api_product_subscription_attribute_id).PUT <@ (user2), updateJson).code should equal(404)

      And("the attribute can be deleted with the delete role")
      makeDeleteRequest((v7 / "management" / "api-product-subscriptions" / id / "attributes" / attribute.api_product_subscription_attribute_id).DELETE <@ (user2)).code should equal(403)
      grant(resourceUser2.userId, bankId, CanDeleteApiProductSubscriptionAttributeAtOneBank.toString)
      makeDeleteRequest((v7 / "management" / "api-product-subscriptions" / id / "attributes" / attribute.api_product_subscription_attribute_id).DELETE <@ (user2)).code should equal(204)
      makeGetRequest((v7 / "management" / "api-product-subscriptions" / id / "attributes").GET <@ (user2)).body.extract[List[ApiProductSubscriptionAttributeResponseJsonV700]] shouldBe empty

      And("the subscription can be deleted with the delete role, and is then gone")
      makeDeleteRequest((v7 / "management" / "api-product-subscriptions" / id).DELETE <@ (user2)).code should equal(403)
      grant(resourceUser2.userId, bankId, CanDeleteApiProductSubscriptionAtOneBank.toString)
      makeDeleteRequest((v7 / "management" / "api-product-subscriptions" / id).DELETE <@ (user2)).code should equal(204)
      val gone = makeGetRequest((v7 / "management" / "api-product-subscriptions" / id).GET <@ (user2))
      gone.code should equal(404)
      errorOf(gone) should startWith(ApiProductSubscriptionNotFound)
    }
  }

  // ─── Phase 3: enforcement ─────────────────────────────────────────────────────────────────────

  /** An API Collection holding one endpoint that requires CanCreateApiProduct (a bank-scoped role). */
  def createCollectionRequiring(operationId: String): String = {
    val collection = MappedApiCollectionsProvider.createApiCollection(resourceUser1.userId, "sub-test-" + UUID.randomUUID().toString.take(8), true, "ApiProductSubscriptionTest")
      .openOrThrowException("could not create test collection")
    MappedApiCollectionEndpointsProvider.createApiCollectionEndpoint(collection.apiCollectionId, operationId).openOrThrowException("could not add endpoint")
    collection.apiCollectionId
  }

  def activeLimits(consumerId: String): ActiveRateLimitsJsonV600 = {
    grant(resourceUser2.userId, "", CanGetRateLimits.toString)
    val response = makeGetRequest((v6 / "management" / "consumers" / consumerId / "active-rate-limits").GET <@ (user2))
    response.code should equal(200)
    response.body.extract[ActiveRateLimitsJsonV600]
  }

  def scopesOf(consumerId: String): Set[(String, String)] =
    Scope.scope.vend.getScopesByConsumerId(consumerId).getOrElse(Nil).map(s => (s.bankId, s.roleName)).toSet

  def callAsUser3() = makeGetRequest((v6 / "users" / "current").GET <@ (user3))

  feature("Enforcement: active applies limits and scopes, suspended blocks, cancelled releases") {
    scenario("Full life cycle on a partner consumer enrolled by the bank", ApiEndpoint1, ApiEndpoint8, VersionOfApi) {
      Given("A product with limits 10/100 and a collection whose endpoint requires CanCreateApiProduct, BILLING_SYSTEM=manual")
      val code = newProductCode()
      createProduct(code, Some(createCollectionRequiring("OBPv6.0.0-createApiProduct")))
      setProductAttribute(code, "BILLING_SYSTEM", "manual")
      val consumerId3 = Consumers.consumers.vend.getConsumerByConsumerKey(consumer3.key).map(_.consumerId.get).getOrElse("")
      And("a scope granted by hand to that consumer beforehand")
      Scope.scope.vend.addScope("", consumerId3, CanGetAnyUser.toString)
      And("user2 is the bank admin")
      grant(resourceUser2.userId, bankId, CanCreateApiProductSubscriptionAtOneBank.toString)
      grant(resourceUser2.userId, bankId, CanUpdateApiProductSubscriptionStatusAtOneBank.toString)

      When("the bank enrols user3's consumer")
      val created = subscribed(code, consumerId3, user2)
      val id = created.api_product_subscription_id
      Then("it is requested and nothing has been granted")
      created.status should equal("requested")
      created.rate_limiting_id shouldBe None
      activeLimits(consumerId3).considered_rate_limit_ids shouldBe empty
      scopesOf(consumerId3) should equal(Set(("", CanGetAnyUser.toString)))
      callAsUser3().code should equal(200)

      When("the admin activates it")
      val activated = putStatus(id, "active", user2).body.extract[ApiProductSubscriptionJsonV700]
      Then("the consumer has the product's limits and the derived scope")
      activated.status should equal("active")
      activated.rate_limiting_id should not be None
      val limits = activeLimits(consumerId3)
      limits.considered_rate_limit_ids should equal(List(activated.rate_limiting_id.get))
      limits.active_per_second_rate_limit should equal(10L)
      limits.active_per_minute_rate_limit should equal(100L)
      limits.active_per_hour_rate_limit should equal(-1L)
      scopesOf(consumerId3) should equal(Set(("", CanGetAnyUser.toString), (bankId, CanCreateApiProduct.toString)))
      callAsUser3().code should equal(200)

      When("the admin suspends it")
      val suspended = putStatus(id, "suspended", user2).body.extract[ApiProductSubscriptionJsonV700]
      Then("the same row is now all zeros, the consumer is blocked, scopes are kept")
      suspended.rate_limiting_id should equal(activated.rate_limiting_id)
      val blockedLimits = activeLimits(consumerId3)
      blockedLimits.active_per_second_rate_limit should equal(0L)
      blockedLimits.active_per_month_rate_limit should equal(0L)
      val blocked = callAsUser3()
      blocked.code should equal(429)
      errorOf(blocked) should include("blocked")
      scopesOf(consumerId3) should contain((bankId, CanCreateApiProduct.toString))

      When("the admin reinstates it")
      val reinstated = putStatus(id, "active", user2).body.extract[ApiProductSubscriptionJsonV700]
      Then("the product limits are back on the same row and the consumer can call again")
      reinstated.rate_limiting_id should equal(activated.rate_limiting_id)
      activeLimits(consumerId3).active_per_second_rate_limit should equal(10L)
      callAsUser3().code should equal(200)
      scopesOf(consumerId3) should equal(Set(("", CanGetAnyUser.toString), (bankId, CanCreateApiProduct.toString)))

      When("an admin deletes the subscription's rate limit row by hand and then suspends")
      grant(resourceUser2.userId, "", CanDeleteRateLimits.toString)
      makeDeleteRequest((v6 / "management" / "consumers" / consumerId3 / "consumer" / "rate-limits" / activated.rate_limiting_id.get).DELETE <@ (user2)).code should equal(204)
      activeLimits(consumerId3).considered_rate_limit_ids shouldBe empty
      val resuspended = putStatus(id, "suspended", user2).body.extract[ApiProductSubscriptionJsonV700]
      Then("a fresh zero row is created and its id stored")
      resuspended.rate_limiting_id should not be None
      resuspended.rate_limiting_id should not equal activated.rate_limiting_id
      activeLimits(consumerId3).considered_rate_limit_ids should equal(List(resuspended.rate_limiting_id.get))
      callAsUser3().code should equal(429)

      When("the admin cancels it")
      val cancelled = putStatus(id, "cancelled", user2).body.extract[ApiProductSubscriptionJsonV700]
      Then("the row and the derived scope are gone; the hand-granted scope survives")
      cancelled.status should equal("cancelled")
      cancelled.rate_limiting_id shouldBe None
      activeLimits(consumerId3).considered_rate_limit_ids shouldBe empty
      scopesOf(consumerId3) should equal(Set(("", CanGetAnyUser.toString)))
      callAsUser3().code should equal(200)
    }

    scenario("Self-service activation applies limits at once; a product without limits or collection grants nothing; manual limits are summed and survive cancel", ApiEndpoint1, ApiEndpoint4, VersionOfApi) {
      Given("A consumer of user1 that already has a manual rate limit row of 5 per second")
      grant(resourceUser2.userId, "", CanCreateRateLimits.toString)
      val manual = makePostRequest((v6 / "management" / "consumers" / consumerId1 / "consumer" / "rate-limits").POST <@ (user2),
        write(code.api.v6_0_0.CallLimitPostJsonV600(
          from_date = new java.util.Date(System.currentTimeMillis() - 3600000L), to_date = new java.util.Date(System.currentTimeMillis() + 86400000L),
          api_version = None, api_name = None, bank_id = None,
          per_second_call_limit = "5", per_minute_call_limit = "-1", per_hour_call_limit = "-1",
          per_day_call_limit = "-1", per_week_call_limit = "-1", per_month_call_limit = "-1")))
      manual.code should equal(201)
      val manualId = manual.body.extract[code.api.v6_0_0.CallLimitJsonV600].rate_limiting_id

      When("user1 subscribes to an open product with limits 10/100 (no collection)")
      val code1 = newProductCode()
      createProduct(code1)
      val active = subscribed(code1, consumerId1, user1)
      Then("it is active with a row, and the limits are the sum of both rows")
      active.status should equal("active")
      active.rate_limiting_id should not be None
      val summed = activeLimits(consumerId1)
      summed.considered_rate_limit_ids.toSet should equal(Set(manualId, active.rate_limiting_id.get))
      summed.active_per_second_rate_limit should equal(15L)
      summed.active_per_minute_rate_limit should equal(100L)
      scopesOf(consumerId1) shouldBe empty

      When("user1 cancels it")
      putMyStatus(active.api_product_subscription_id, "cancelled", user1).code should equal(200)
      Then("only the manual row remains")
      val remaining = activeLimits(consumerId1)
      remaining.considered_rate_limit_ids should equal(List(manualId))
      remaining.active_per_second_rate_limit should equal(5L)

      When("user1 subscribes to a product with no limits at all")
      val code2 = newProductCode()
      grant(resourceUser1.userId, bankId, CanCreateApiProduct.toString)
      val noLimits = PostPutApiProductJsonV600(
        parent_api_product_code = None, name = "no limits", category = None, more_info_url = None, terms_and_conditions_url = None,
        description = None, collection_id = None, monthly_subscription_currency = None, monthly_subscription_amount = None,
        per_second_call_limit = None, per_minute_call_limit = None, per_hour_call_limit = None,
        per_day_call_limit = None, per_week_call_limit = None, per_month_call_limit = None, tags = None)
      makePostRequest((v6 / "banks" / bankId / "api-products" / code2).POST <@ (user1), write(noLimits)).code should equal(201)
      val unlimited = subscribed(code2, consumerId1, user1)
      Then("it is active but no row was created")
      unlimited.status should equal("active")
      unlimited.rate_limiting_id shouldBe None
      activeLimits(consumerId1).considered_rate_limit_ids should equal(List(manualId))
    }
  }
}
