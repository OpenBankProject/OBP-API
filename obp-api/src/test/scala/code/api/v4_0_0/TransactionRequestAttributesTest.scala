package code.api.v4_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages._
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag


class TransactionRequestAttributesTest extends V400ServerSetup {

  lazy val bankId = testBankId1.value
  lazy val accountId = testAccountId1.value
  lazy val postTransactionRequestAttributeJsonV400 = SwaggerDefinitionsJSON.transactionRequestAttributeJsonV400
  lazy val putTransactionRequestAttributeJsonV400 = SwaggerDefinitionsJSON.transactionRequestAttributeJsonV400.copy(name = "test")
  lazy val view = "owner"

  /**
   * Test tags
   * Example: To run tests with tag "getPermissions":
   * mvn test -D tagsToInclude
   *
   * This is made possible by the scalatest maven plugin
   */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString())

  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.createTransactionRequestAttribute))

  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.updateTransactionRequestAttribute))

  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.getTransactionRequestAttributes))

  object ApiEndpoint4 extends Tag(nameOf(Implementations4_0_0.getTransactionRequestAttributeById))


  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    if (!APIUtil.getPropsAsBoolValue("transactionRequests_enabled", defaultValue = false)) {
      ignore("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {}
    } else {
      scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
        When("We make a request v4.0.0")
        lazy val transactionRequest = randomTransactionRequestViaEndpoint(bankId, accountId, view, user1)
        lazy val transactionRequestId = transactionRequest.id

        val request400 = (v4_0_0_Request / "banks" / bankId / "accounts" / accountId / "transaction-requests" / transactionRequestId / "attribute").POST
        val response400 = makePostRequest(request400, write(postTransactionRequestAttributeJsonV400))
        Then("We should get a 401")
        response400.code should equal(401)
        response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
      }
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - authorized access- missing role") {
    if (!APIUtil.getPropsAsBoolValue("transactionRequests_enabled", defaultValue = false)) {
      ignore("We will call the endpoint with user credentials", ApiEndpoint1, VersionOfApi) {}
    } else {
      scenario("We will call the endpoint with user credentials", ApiEndpoint1, VersionOfApi) {
        lazy val transactionRequest = randomTransactionRequestViaEndpoint(bankId, accountId, view, user1)
        lazy val transactionRequestId = transactionRequest.id

        When("We make a request v4.0.0")
        val request400 = (v4_0_0_Request / "banks" / bankId / "accounts" / accountId / "transaction-requests" / transactionRequestId / "attribute").POST <@ (user1)
        val response400 = makePostRequest(request400, write(postTransactionRequestAttributeJsonV400))
        Then("We should get a 403")
        response400.code should equal(403)
        response400.body.extract[ErrorMessage].message should include(UserHasMissingRoles)
      }
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - authorized access - with role - should be success!") {
    if (!APIUtil.getPropsAsBoolValue("transactionRequests_enabled", defaultValue = false)) {
      ignore("We will call the endpoint with user credentials", ApiEndpoint1, VersionOfApi) {}
    } else {
      scenario("We will call the endpoint with user credentials", ApiEndpoint1, VersionOfApi) {
        lazy val bankId = testBankId1.value
        lazy val bankAccount = randomPrivateAccountViaEndpoint(bankId)
        lazy val accountId = bankAccount.id
        lazy val postTransactionRequestAttributeJsonV400 = SwaggerDefinitionsJSON.transactionRequestAttributeJsonV400
        lazy val putTransactionRequestAttributeJsonV400 = SwaggerDefinitionsJSON.transactionRequestAttributeJsonV400.copy(name = "test")
        lazy val view = bankAccount.views_available.map(_.id).headOption.getOrElse("owner")
        lazy val transactionRequest = randomTransactionRequestViaEndpoint(bankId, bankAccount.id, view, user1)
        lazy val transactionRequestId = transactionRequest.id


        When("We make a request v4.0.0")
        val request400 = (v4_0_0_Request / "banks" / bankId / "accounts" / accountId / "transaction-requests" / transactionRequestId / "attribute").POST <@ (user1)
        val response400 = makePostRequest(request400, write(putTransactionRequestAttributeJsonV400))
        Then("We should get a 403")
        response400.code should equal(403)
        response400.body.extract[ErrorMessage].message should include(UserHasMissingRoles)

        Then("We grant the role to the user1")
        Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, canCreateTransactionRequestAttributeAtOneBank.toString())

        val responseWithRole = makePostRequest(request400, write(putTransactionRequestAttributeJsonV400))
        Then("We should get a 201")
        responseWithRole.code should equal(201)
        responseWithRole.body.extract[TransactionRequestAttributeResponseJson].name equals ("test") should be(true)
        responseWithRole.body.extract[TransactionRequestAttributeResponseJson].value equals (postTransactionRequestAttributeJsonV400.value) should be(true)
        responseWithRole.body.extract[TransactionRequestAttributeResponseJson].`type` equals (postTransactionRequestAttributeJsonV400.`type`) should be(true)
      }
    }
  }

  feature(s"test $ApiEndpoint2 version $VersionOfApi - Unauthorized access") {
    if (!APIUtil.getPropsAsBoolValue("transactionRequests_enabled", defaultValue = false)) {
      ignore("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {}
    } else {
      scenario("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {
        When("We make a request v4.0.0")
        lazy val transactionRequest = randomTransactionRequestViaEndpoint(bankId, accountId, view, user1)
        lazy val transactionRequestId = transactionRequest.id
        val request400 = (v4_0_0_Request / "banks" / bankId / "accounts" / accountId / "transaction-requests" / transactionRequestId / "attributes" / "transactionRequestAttributeId").PUT
        val response400 = makePutRequest(request400, write(putTransactionRequestAttributeJsonV400))
        Then("We should get a 401")
        response400.code should equal(401)
        response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
      }
    }
  }

  feature(s"test $ApiEndpoint2 version $VersionOfApi - authorized access- missing role") {
    if (!APIUtil.getPropsAsBoolValue("transactionRequests_enabled", defaultValue = false)) {
      ignore("We will call the endpoint with user credentials", ApiEndpoint2, VersionOfApi) {}
    } else {
      scenario("We will call the endpoint with user credentials", ApiEndpoint2, VersionOfApi) {
        lazy val transactionRequest = randomTransactionRequestViaEndpoint(bankId, accountId, view, user1)
        lazy val transactionRequestId = transactionRequest.id
        When("We make a request v4.0.0")
        val request400 = (v4_0_0_Request / "banks" / bankId / "accounts" / accountId / "transaction-requests" / transactionRequestId / "attributes" / "transactionRequestAttributeId").PUT <@ (user1)
        val response400 = makePutRequest(request400, write(putTransactionRequestAttributeJsonV400))
        Then("We should get a 403")
        response400.code should equal(403)
        response400.body.extract[ErrorMessage].message should include(UserHasMissingRoles)
      }
    }
  }

  feature(s"test $ApiEndpoint2 version $VersionOfApi - authorized access - with role - should be success!") {
    if (!APIUtil.getPropsAsBoolValue("transactionRequests_enabled", defaultValue = false)) {
      ignore("We will call the endpoint with user credentials", ApiEndpoint1, VersionOfApi) {}
    } else {
      scenario("We will call the endpoint with user credentials", ApiEndpoint1, VersionOfApi) {
        lazy val transactionRequest = randomTransactionRequestViaEndpoint(bankId, accountId, view, user1)
        lazy val transactionRequestId = transactionRequest.id
        When("We make a request v4.0.0")
        val request400 = (v4_0_0_Request / "banks" / bankId / "accounts" / accountId / "transaction-requests" / transactionRequestId / "attribute").POST <@ (user1)
        val response400 = makePostRequest(request400, write(putTransactionRequestAttributeJsonV400))
        Then("We should get a 403")
        response400.code should equal(403)
        response400.body.extract[ErrorMessage].message should include(UserHasMissingRoles)

        Then("We grant the role to the user1")
        Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, canCreateTransactionRequestAttributeAtOneBank.toString())

        val responseWithRole = makePostRequest(request400, write(putTransactionRequestAttributeJsonV400))
        Then("We should get a 201")
        responseWithRole.code should equal(201)
        responseWithRole.body.extract[TransactionRequestAttributeResponseJson].name equals ("test") should be(true)
        responseWithRole.body.extract[TransactionRequestAttributeResponseJson].value equals (postTransactionRequestAttributeJsonV400.value) should be(true)
        responseWithRole.body.extract[TransactionRequestAttributeResponseJson].`type` equals (postTransactionRequestAttributeJsonV400.`type`) should be(true)
      }
    }
  }

  feature(s"test $ApiEndpoint2 version $VersionOfApi - authorized access - with role - wrong transactionRequestAttributeId") {
    if (!APIUtil.getPropsAsBoolValue("transactionRequests_enabled", defaultValue = false)) {
      ignore("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {}
    } else {
      scenario("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {
        lazy val transactionRequest = randomTransactionRequestViaEndpoint(bankId, accountId, view, user1)
        lazy val transactionRequestId = transactionRequest.id

        When("We make a request v4.0.0")
        val request400 = (v4_0_0_Request / "banks" / bankId / "accounts" / accountId / "transaction-requests" / transactionRequestId / "attributes" / "transactionRequestAttributeId").PUT <@ (user1)
        val response400 = makePutRequest(request400, write(putTransactionRequestAttributeJsonV400))
        Then("We should get a 403")
        response400.code should equal(403)
        response400.body.extract[ErrorMessage].message should include(UserHasMissingRoles)

        Then("We grant the role to the user1")
        Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, canUpdateTransactionRequestAttributeAtOneBank.toString())

        val responseWithRole = makePutRequest(request400, write(putTransactionRequestAttributeJsonV400))
        Then("We should get a 400")
        responseWithRole.code should equal(400)
        responseWithRole.toString should include(TransactionRequestAttributeNotFound)
      }
    }
  }

  feature(s"test $ApiEndpoint2 version $VersionOfApi - authorized access - with role - with transactionRequestAttributeId") {
    if (!APIUtil.getPropsAsBoolValue("transactionRequests_enabled", defaultValue = false)) {
      ignore("We will call the endpoint with user credentials", ApiEndpoint2, VersionOfApi) {}
    } else {
      scenario("We will call the endpoint with user credentials", ApiEndpoint2, VersionOfApi) {

        lazy val transactionRequest = randomTransactionRequestViaEndpoint(bankId, accountId, view, user1)
        lazy val transactionRequestId = transactionRequest.id

        Then("We grant the role to the user1")
        Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, canUpdateTransactionRequestAttributeAtOneBank.toString())

        Then("we create the Transaction Request Attribute ")
        val transactionRequestAttributeId = createTransactionRequestAttributeEndpoint(bankId: String, accountId: String, transactionRequestId: String, user1)


        val requestWithId = (v4_0_0_Request / "banks" / bankId / "accounts" / accountId / "transaction-requests" / transactionRequestId / "attributes" / transactionRequestAttributeId).PUT <@ (user1)
        val responseWithId = makePutRequest(requestWithId, write(putTransactionRequestAttributeJsonV400))

        responseWithId.body.extract[TransactionRequestAttributeResponseJson].name equals ("test") should be(true)
        responseWithId.body.extract[TransactionRequestAttributeResponseJson].value equals (putTransactionRequestAttributeJsonV400.value) should be(true)
        responseWithId.body.extract[TransactionRequestAttributeResponseJson].`type` equals (putTransactionRequestAttributeJsonV400.`type`) should be(true)
      }
    }
  }

  feature(s"test $ApiEndpoint3 version $VersionOfApi - authorized access - with role - wrong transactionRequestAttributeId") {
    if (!APIUtil.getPropsAsBoolValue("transactionRequests_enabled", defaultValue = false)) {
      ignore("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {}
    } else {
      scenario("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {

        lazy val transactionRequest = randomTransactionRequestViaEndpoint(bankId, accountId, view, user1)
        lazy val transactionRequestId = transactionRequest.id

        When("We make a request v4.0.0")
        Then("we create the Transaction Request Attribute ")
        val transactionRequestAttributeId = createTransactionRequestAttributeEndpoint(bankId: String, accountId: String, transactionRequestId: String, user1)


        val request400 = (v4_0_0_Request / "banks" / bankId / "accounts" / accountId / "transaction-requests" / transactionRequestId / "attributes").GET <@ (user1)
        val response400 = makeGetRequest(request400)
        Then("We should get a 403")
        response400.code should equal(403)
        response400.body.extract[ErrorMessage].message should include(UserHasMissingRoles)

        Then("We grant the role to the user1")
        Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetTransactionRequestAttributesAtOneBank.toString)

        val responseWithRole = makeGetRequest(request400)
        Then("We should get a 200")
        responseWithRole.code should equal(200)
      }
    }
  }

  feature(s"test $ApiEndpoint4 version $VersionOfApi - authorized access - with role - with transactionRequestAttributeId") {
    if (!APIUtil.getPropsAsBoolValue("transactionRequests_enabled", defaultValue = false)) {
      ignore("We will call the endpoint with user credentials", ApiEndpoint2, VersionOfApi) {}
    } else {
      scenario("We will call the endpoint with user credentials", ApiEndpoint2, VersionOfApi) {

        lazy val transactionRequest = randomTransactionRequestViaEndpoint(bankId, accountId, view, user1)
        lazy val transactionRequestId = transactionRequest.id

        Then("we create the Transaction Request Attribute ")
        val transactionRequestAttributeId = createTransactionRequestAttributeEndpoint(bankId, accountId, transactionRequestId, user1)

        Then("We grant the role to the user1")
        Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, canGetTransactionRequestAttributeAtOneBank.toString())

        val requestWithId = (v4_0_0_Request / "banks" / bankId / "accounts" / accountId / "transaction-requests" / transactionRequestId / "attributes" / transactionRequestAttributeId).GET <@ (user1)
        val responseWithId = makeGetRequest(requestWithId)

        responseWithId.body.extract[TransactionRequestAttributeResponseJson].name should equal(postTransactionRequestAttributeJsonV400.name)
        responseWithId.body.extract[TransactionRequestAttributeResponseJson].value should equal(postTransactionRequestAttributeJsonV400.value)
        responseWithId.body.extract[TransactionRequestAttributeResponseJson].`type` should equal(postTransactionRequestAttributeJsonV400.`type`)
      }
    }
  }

}
