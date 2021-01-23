package code.api.v4_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ApiRole._
import code.api.util.ErrorMessages._
import code.api.v3_0_0.OBPAPI3_0_0.Implementations2_2_0
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.entitlement.Entitlement
import code.setup.{APIResponse, PropsReset}
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.{JInt, JString, prettyRender}
import org.scalatest.Tag

class ForceErrorValidationTest extends V400ServerSetup with PropsReset {

  /**
   * Test tags
   * Example: To run tests with tag "getPermissions":
   * mvn test -D tagsToInclude
   *
   * This is made possible by the scalatest maven plugin
   */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)

  object ApiEndpoint1 extends Tag(nameOf(Implementations3_1_0.createCustomer))

  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.getCustomerAttributeById))

  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.genericEndpoint))

  object ApiEndpoint4 extends Tag(nameOf(Implementations4_0_0.dynamicEndpoint))

  object ApiEndpointCreateFx extends Tag(nameOf(Implementations2_2_0.createFx))

  lazy val bankId = randomBankId
  lazy val bankAccount = randomPrivateAccountViaEndpoint(bankId)
  lazy val view = randomOwnerViewPermalinkViaEndpoint(bankId, bankAccount)

  override def beforeEach() = {
    super.beforeEach()
    setPropsValues("enable.force_error"->"true")
  }

  feature(s"test Force-Error header - Unauthenticated access") {
    scenario(s"We will call the endpoint $ApiEndpointCreateFx without authentication", VersionOfApi) {
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "banks" / bankId / "fx").PUT
      val response = makePutRequest(request, correctFx, ("Force-Error", "OBP-20006"))

      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }

    scenario("We will call the endpoint with user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request310 = (v4_0_0_Request / "banks" / bankId / "customers").POST <@ user1
      val response310 = makePostRequest(request310, "", ("Force-Error", "OBP-20006"))
      Then("We should get a 403")
      response310.code should equal(403)
      val errorMsg = UserHasMissingRoles + canCreateCustomer + " or " + canCreateCustomerAtAnyBank
      And("error should be " + errorMsg)
      response310.body.extract[ErrorMessage].message should equal(errorMsg)
    }

    scenario("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {
      val customerId = createAndGetCustomerIdViaEndpoint(bankId, user1)

      Then("we create the Customer Attribute ")
      val customerAttributeId = createAndGetCustomerAttributeIdViaEndpoint(bankId: String, customerId: String, user1)

      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "customers" / customerId / "attributes" / customerAttributeId).GET
      val response400 = makeGetRequest(request400, List("Force-Error" -> "OBP-20006"))

      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }

    scenario(s"We will call the dynamic entity endpoint without authentication", VersionOfApi) {
      addDynamicEntity()

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "banks" / bankId / "FooBar").POST
      val response = makePostRequest(request, correctFooBar, ("Force-Error", "OBP-20006"))

      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }

    scenario("We will call the endpoint dynamic endpoints without authentication", VersionOfApi) {
      addDynamicEndpoints()

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "dynamic" / "save").POST
      val response = makePostRequest(request, correctUser, ("Force-Error", "OBP-20006"))

      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }

  // old style endpoint
  feature(s"test Force-Error header $VersionOfApi - old static endpoint, authenticated access") {
    scenario(s"We will call the endpoint $ApiEndpointCreateFx with Force-Error have wrong format header", VersionOfApi) {
      addEntitlement(canCreateFxRate, bankId)
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "banks" / bankId / "fx").PUT <@ user1
      val response = makePutRequest(request, correctFx, "Force-Error" -> "OBP-xxxx")
      Then("We should get a 400")
      response.code should equal(400)
      val validation = response.body
      val message = (validation \ "message").asInstanceOf[JString].s

      message should include(s"$ForceErrorInvalid Force-Error value not correct:")
    }

    scenario(s"We will call the endpoint $ApiEndpointCreateFx with Force-Error header value not support by current endpoint", VersionOfApi) {
      addEntitlement(canCreateFxRate, bankId)
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "banks" / bankId / "fx").PUT <@ user1
      val response = makePutRequest(request, correctFx, ("Force-Error", "OBP-20005"))
      Then("We should get a 400")
      response.code should equal(400)
      val validation = response.body
      val message = (validation \ "message").asInstanceOf[JString].s

      message should include(s"$ForceErrorInvalid Invalid Force Error Code:")
    }

    scenario(s"We will call the endpoint $ApiEndpointCreateFx with Response-Code header value is not Int", VersionOfApi) {
      addEntitlement(canCreateFxRate, bankId)
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "banks" / bankId / "fx").PUT <@ user1

      val response = makePutRequest(request, correctFx, ("Force-Error", "OBP-20006"), ("Response-Code", "not_integer"))
      Then("We should get a 400")
      response.code should equal(400)
      val validation = response.body
      val message = (validation \ "message").asInstanceOf[JString].s

      message should include(s"$ForceErrorInvalid Response-Code value not correct:")
    }

    scenario(s"We will call the endpoint $ApiEndpointCreateFx with correct Force-Error header value", VersionOfApi) {
      addEntitlement(canCreateFxRate, bankId)
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "banks" / bankId / "fx").PUT <@ user1
      val response = makePutRequest(request, correctFx, ("Force-Error", "OBP-20006"))
      Then("We should get a 400")
      response.code should equal(403)
      val validation = response.body
      val message = (validation \ "message").asInstanceOf[JString].s
      val code = (validation \ "code").asInstanceOf[JInt].num.toInt

      message should be(UserHasMissingRoles)
      code shouldEqual 403
    }

    scenario(s"We will call the endpoint $ApiEndpointCreateFx with correct Force-Error header value and Response-Code value", VersionOfApi) {
      addEntitlement(canCreateFxRate, bankId)
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "banks" / bankId / "fx").PUT <@ user1
      val response = makePutRequest(request, correctFx, ("Force-Error", "OBP-20006"), ("Response-Code", "444"))
      Then("We should get a 444")
      response.code should equal(444)
      val validation = response.body
      val message = (validation \ "message").asInstanceOf[JString].s
      val code = (validation \ "code").asInstanceOf[JInt].num.toInt

      message should be(UserHasMissingRoles)
      code shouldEqual 444
    }

    scenario(s"We will call the endpoint $ApiEndpointCreateFx with correct Force-Error header value, but 'enable.force_error=false'", VersionOfApi) {
      setPropsValues("enable.force_error"->"false")
      addEntitlement(canCreateFxRate, bankId)
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "banks" / bankId / "fx").PUT <@ user1
      val response = makePutRequest(request, correctFx, ("Force-Error", "OBP-20006"))
      Then("We should not get a 400")
      response.code should not equal(403)
      val validation = response.body

      val responseBody = prettyRender(validation)

      responseBody should not contain UserHasMissingRoles
      responseBody should not contain "403"
    }
  }

  //////// not auto validate endpoint
  feature(s"test Force-Error header $VersionOfApi - not auto validate static endpoint, authenticated access") {
    scenario(s"We will call the endpoint $ApiEndpoint1 with Force-Error have wrong format header", VersionOfApi) {
      addEntitlement(canCreateCustomer, bankId)
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "banks" / bankId / "customers").POST <@ (user1)
      val response = makePostRequest(request, "", "Force-Error" -> "OBP-xxxx")
      Then("We should get a 400")
      response.code should equal(400)
      val validation = response.body
      val message = (validation \ "message").asInstanceOf[JString].s

      message should include(s"$ForceErrorInvalid Force-Error value not correct:")
    }

    scenario(s"We will call the endpoint $ApiEndpoint1 with Force-Error header value not support by current endpoint", VersionOfApi) {
      addEntitlement(canCreateCustomer, bankId)
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "banks" / bankId / "customers").POST <@ (user1)
      val response = makePostRequest(request, "", ("Force-Error", "OBP-20009"))
      Then("We should get a 400")
      response.code should equal(400)
      val validation = response.body
      val message = (validation \ "message").asInstanceOf[JString].s

      message should include(s"$ForceErrorInvalid Invalid Force Error Code:")
    }

    scenario(s"We will call the endpoint $ApiEndpoint1 with Response-Code header value is not Int", VersionOfApi) {
      addEntitlement(canCreateCustomer, bankId)
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "banks" / bankId / "customers").POST <@ (user1)
      val response = makePostRequest(request, "", ("Force-Error", "OBP-20006"), ("Response-Code", "not_integer"))
      Then("We should get a 400")
      response.code should equal(400)
      val validation = response.body
      val message = (validation \ "message").asInstanceOf[JString].s

      message should include(s"$ForceErrorInvalid Response-Code value not correct:")
    }

    scenario(s"We will call the endpoint $ApiEndpoint1 with correct Force-Error header value", VersionOfApi) {
      addEntitlement(canCreateCustomer, bankId)
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "banks" / bankId / "customers").POST <@ (user1)
      val response = makePostRequest(request, "", ("Force-Error", "OBP-20006"))
      Then("We should get a 403")
      response.code should equal(403)
      val validation = response.body
      val message = (validation \ "message").asInstanceOf[JString].s
      val code = (validation \ "code").asInstanceOf[JInt].num.toInt

      message should be(UserHasMissingRoles)
      code shouldEqual 403
    }

    scenario(s"We will call the endpoint $ApiEndpoint1 with correct Force-Error header value and Response-Code value", VersionOfApi) {
      addEntitlement(canCreateCustomer, bankId)
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "banks" / bankId / "customers").POST <@ (user1)
      val response = makePostRequest(request, "", ("Force-Error", "OBP-20006"), ("Response-Code", "444"))
      Then("We should get a 444")
      response.code should equal(444)
      val validation = response.body
      val message = (validation \ "message").asInstanceOf[JString].s
      val code = (validation \ "code").asInstanceOf[JInt].num.toInt

      message should be(UserHasMissingRoles)
      code shouldEqual 444
    }

    scenario(s"We will call the endpoint $ApiEndpoint1 with correct Force-Error header value, but 'enable.force_error=false'", VersionOfApi) {
      setPropsValues("enable.force_error"->"false")
      addEntitlement(canCreateCustomer, bankId)
      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "banks" / bankId / "customers").POST <@ (user1)
      val response = makePostRequest(request, "", ("Force-Error", "OBP-20006"))
      Then("We should not get a 403")
      response.code should not equal(403)
      val validation = response.body
      val message = (validation \ "message").asInstanceOf[JString].s
      val code = (validation \ "code").asInstanceOf[JInt].num.toInt

      message should not be(UserHasMissingRoles)
      code should not be(403)
    }
  }
  //////// auto validate endpoint
  feature(s"test Force-Error header $VersionOfApi - auto validate static endpoint, authenticated access") {
    scenario(s"We will call the endpoint $ApiEndpoint2 with Force-Error have wrong format header", VersionOfApi) {
      val customerId = createAndGetCustomerIdViaEndpoint(bankId, user1)

      Then("we create the Customer Attribute ")
      val customerAttributeId = createAndGetCustomerAttributeIdViaEndpoint(bankId: String, customerId: String, user1)

      When("We make a request v4.0.0")
      addEntitlement(canGetCustomerAttributeAtOneBank, bankId)
      val request = (v4_0_0_Request / "banks" / bankId / "customers" / customerId / "attributes" / customerAttributeId).GET <@ (user1)
      val response = makeGetRequest(request, List("Force-Error" -> "OBP-xxxx"))

      Then("We should get a 400")
      response.code should equal(400)
      val validation = response.body
      val message = (validation \ "message").asInstanceOf[JString].s

      message should include(s"$ForceErrorInvalid Force-Error value not correct:")
    }

    scenario(s"We will call the endpoint $ApiEndpoint2 with Force-Error header value not support by current endpoint", VersionOfApi) {
      val customerId = createAndGetCustomerIdViaEndpoint(bankId, user1)

      Then("we create the Customer Attribute ")
      val customerAttributeId = createAndGetCustomerAttributeIdViaEndpoint(bankId: String, customerId: String, user1)

      When("We make a request v4.0.0")
      addEntitlement(canGetCustomerAttributeAtOneBank, bankId)
      val request = (v4_0_0_Request / "banks" / bankId / "customers" / customerId / "attributes" / customerAttributeId).GET <@ (user1)
      val response = makeGetRequest(request, List("Force-Error" -> "OBP-20009"))
      Then("We should get a 400")
      response.code should equal(400)
      val validation = response.body
      val message = (validation \ "message").asInstanceOf[JString].s

      message should include(s"$ForceErrorInvalid Invalid Force Error Code:")
    }

    scenario(s"We will call the endpoint $ApiEndpoint2 with Response-Code header value is not Int", VersionOfApi) {
      val customerId = createAndGetCustomerIdViaEndpoint(bankId, user1)

      Then("we create the Customer Attribute ")
      val customerAttributeId = createAndGetCustomerAttributeIdViaEndpoint(bankId: String, customerId: String, user1)

      When("We make a request v4.0.0")
      addEntitlement(canGetCustomerAttributeAtOneBank, bankId)
      val request = (v4_0_0_Request / "banks" / bankId / "customers" / customerId / "attributes" / customerAttributeId).GET <@ (user1)
      val response = makeGetRequest(request, List("Force-Error" -> "OBP-20006", "Response-Code" -> "not_integer"))
      Then("We should get a 400")
      response.code should equal(400)
      val validation = response.body
      val message = (validation \ "message").asInstanceOf[JString].s

      message should include(s"$ForceErrorInvalid Response-Code value not correct:")
    }

    scenario(s"We will call the endpoint $ApiEndpoint2 with correct Force-Error header value", VersionOfApi) {
      val customerId = createAndGetCustomerIdViaEndpoint(bankId, user1)

      Then("we create the Customer Attribute ")
      val customerAttributeId = createAndGetCustomerAttributeIdViaEndpoint(bankId: String, customerId: String, user1)

      When("We make a request v4.0.0")
      addEntitlement(canGetCustomerAttributeAtOneBank, bankId)
      val request = (v4_0_0_Request / "banks" / bankId / "customers" / customerId / "attributes" / customerAttributeId).GET <@ (user1)
      val response = makeGetRequest(request, List("Force-Error" -> "OBP-20006"))
      Then("We should get a 403")
      response.code should equal(403)
      val validation = response.body
      val message = (validation \ "message").asInstanceOf[JString].s
      val code = (validation \ "code").asInstanceOf[JInt].num.toInt

      message should be(UserHasMissingRoles)
      code shouldEqual 403
    }

    scenario(s"We will call the endpoint $ApiEndpoint2 with correct Force-Error header value and Response-Code value", VersionOfApi) {
      val customerId = createAndGetCustomerIdViaEndpoint(bankId, user1)

      Then("we create the Customer Attribute ")
      val customerAttributeId = createAndGetCustomerAttributeIdViaEndpoint(bankId: String, customerId: String, user1)

      When("We make a request v4.0.0")
      addEntitlement(canGetCustomerAttributeAtOneBank, bankId)
      val request = (v4_0_0_Request / "banks" / bankId / "customers" / customerId / "attributes" / customerAttributeId).GET <@ (user1)
      val response = makeGetRequest(request, List("Force-Error" -> "OBP-20006", "Response-Code" -> "444"))
      Then("We should get a 444")
      response.code should equal(444)
      val validation = response.body
      val message = (validation \ "message").asInstanceOf[JString].s
      val code = (validation \ "code").asInstanceOf[JInt].num.toInt

      message should be(UserHasMissingRoles)
      code shouldEqual 444
    }

    scenario(s"We will call the endpoint $ApiEndpoint2 with correct Force-Error header value, but 'enable.force_error=false'", VersionOfApi) {
      setPropsValues("enable.force_error"->"false")
      val customerId = createAndGetCustomerIdViaEndpoint(bankId, user1)

      Then("we create the Customer Attribute ")
      val customerAttributeId = createAndGetCustomerAttributeIdViaEndpoint(bankId: String, customerId: String, user1)

      When("We make a request v4.0.0")
      addEntitlement(canGetCustomerAttributeAtOneBank, bankId)
      val request = (v4_0_0_Request / "banks" / bankId / "customers" / customerId / "attributes" / customerAttributeId).GET <@ (user1)
      val response = makeGetRequest(request, List("Force-Error" -> "OBP-20006"))
      Then("We should not get a 403")
      response.code should not equal(403)
      val validation = response.body

      val responseBody = prettyRender(validation)

      responseBody should not contain UserHasMissingRoles
      responseBody should not contain "403"
    }
  }

  ////// dynamic entity
  feature(s"test dynamic entity endpoints Force-Error, version $VersionOfApi - authenticated access") {
    scenario(s"We will call the endpoint $ApiEndpoint3 with Force-Error have wrong format header", VersionOfApi) {
      addDynamicEntity()
      addStringEntitlement("CanCreateDynamicEntity_FooBar", bankId)

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "banks" / bankId / "FooBar").POST <@ user1
      val response = makePostRequest(request, correctFooBar, ("Force-Error" -> "OBP-xxxx"))

      Then("We should get a 400")
      response.code should equal(400)
      val validation = response.body
      val message = (validation \ "message").asInstanceOf[JString].s

      message should include(s"$ForceErrorInvalid Force-Error value not correct:")
    }

    scenario(s"We will call the endpoint $ApiEndpoint3 with Force-Error header value not support by current endpoint", VersionOfApi) {
      addDynamicEntity()
      addStringEntitlement("CanCreateDynamicEntity_FooBar", bankId)

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "banks" / bankId / "FooBar").POST <@ user1
      val response = makePostRequest(request, correctFooBar, ("Force-Error" -> "OBP-20009"))
      Then("We should get a 400")
      response.code should equal(400)
      val validation = response.body
      val message = (validation \ "message").asInstanceOf[JString].s

      message should include(s"$ForceErrorInvalid Invalid Force Error Code:")
    }

    scenario(s"We will call the endpoint $ApiEndpoint3 with Response-Code header value is not Int", VersionOfApi) {
      addDynamicEntity()
      addStringEntitlement("CanCreateDynamicEntity_FooBar", bankId)

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "banks" / bankId / "FooBar").POST <@ user1
      val response = makePostRequest(request, correctFooBar, ("Force-Error" -> "OBP-20006"), ("Response-Code" -> "not_integer"))
      Then("We should get a 400")
      response.code should equal(400)
      val validation = response.body
      val message = (validation \ "message").asInstanceOf[JString].s

      message should include(s"$ForceErrorInvalid Response-Code value not correct:")
    }

    scenario(s"We will call the endpoint $ApiEndpoint3 with correct Force-Error header value", VersionOfApi) {
      addDynamicEntity()
      addStringEntitlement("CanCreateDynamicEntity_FooBar", bankId)

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "banks" / bankId / "FooBar").POST <@ user1
      val response = makePostRequest(request, correctFooBar, ("Force-Error" -> "OBP-20006"))
      Then("We should get a 403")
      response.code should equal(403)
      val validation = response.body
      val message = (validation \ "message").asInstanceOf[JString].s
      val code = (validation \ "code").asInstanceOf[JInt].num.toInt

      message should be(UserHasMissingRoles)
      code shouldEqual 403
    }

    scenario(s"We will call the endpoint $ApiEndpoint3 with correct Force-Error header value and Response-Code value", VersionOfApi) {
      addDynamicEntity()
      addStringEntitlement("CanCreateDynamicEntity_FooBar", bankId)

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "banks" / bankId / "FooBar").POST <@ user1
      val response = makePostRequest(request, correctFooBar, ("Force-Error" -> "OBP-20006"), ("Response-Code" -> "444"))
      Then("We should get a 444")
      response.code should equal(444)
      val validation = response.body
      val message = (validation \ "message").asInstanceOf[JString].s
      val code = (validation \ "code").asInstanceOf[JInt].num.toInt

      message should be(UserHasMissingRoles)
      code shouldEqual 444
    }

    scenario(s"We will call the endpoint $ApiEndpoint3 with correct Force-Error header value, but 'enable.force_error=false'", VersionOfApi) {
      setPropsValues("enable.force_error"->"false")
      addDynamicEntity()
      addStringEntitlement("CanCreateDynamicEntity_FooBar", bankId)

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "banks" / bankId / "FooBar").POST <@ user1
      val response = makePostRequest(request, correctFooBar, ("Force-Error" -> "OBP-20006"))
      Then("We should not get a 403")
      response.code should not  equal(403)
      val validation = response.body

      val responseBody = prettyRender(validation)

      responseBody should not contain UserHasMissingRoles
      responseBody should not contain "403"
    }
  }
  /////// dynamic endpoints
  feature(s"test dynamic endpoints Force-Error, version $VersionOfApi - authenticated access") {

    scenario(s"We will call the endpoint $ApiEndpoint4 with Force-Error have wrong format header", VersionOfApi) {
      addOneValidation(jsonSchemaDynamicEndpoint, "OBPv4.0.0-dynamicEndpoint_POST_save")
      addDynamicEndpoints()
      addStringEntitlement("CanCreateDynamicEndpoint_User469")

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "dynamic" / "save").POST <@ user1
      val response = makePostRequest(request, correctUser, ("Force-Error" -> "OBP-xxxx"))

      Then("We should get a 400")
      response.code should equal(400)
      val validation = response.body
      val message = (validation \ "message").asInstanceOf[JString].s

      message should include(s"$ForceErrorInvalid Force-Error value not correct:")
    }

    scenario(s"We will call the endpoint $ApiEndpoint4 with Force-Error header value not support by current endpoint", VersionOfApi) {
      addOneValidation(jsonSchemaDynamicEndpoint, "OBPv4.0.0-dynamicEndpoint_POST_save")
      addDynamicEndpoints()
      addStringEntitlement("CanCreateDynamicEndpoint_User469")

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "dynamic" / "save").POST <@ user1
      val response = makePostRequest(request, correctUser, ("Force-Error" -> "OBP-20009"))
      Then("We should get a 400")
      response.code should equal(400)
      val validation = response.body
      val message = (validation \ "message").asInstanceOf[JString].s

      message should include(s"$ForceErrorInvalid Invalid Force Error Code:")
    }

    scenario(s"We will call the endpoint $ApiEndpoint4 with Response-Code header value is not Int", VersionOfApi) {
      addOneValidation(jsonSchemaDynamicEndpoint, "OBPv4.0.0-dynamicEndpoint_POST_save")
      addDynamicEndpoints()
      addStringEntitlement("CanCreateDynamicEndpoint_User469")

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "dynamic" / "save").POST <@ user1
      val response = makePostRequest(request, correctUser, ("Force-Error" -> "OBP-20006"), ("Response-Code" -> "not_integer"))
      Then("We should get a 400")
      response.code should equal(400)
      val validation = response.body
      val message = (validation \ "message").asInstanceOf[JString].s

      message should include(s"$ForceErrorInvalid Response-Code value not correct:")
    }

    scenario(s"We will call the endpoint $ApiEndpoint4 with correct Force-Error header value", VersionOfApi) {
      addOneValidation(jsonSchemaDynamicEndpoint, "OBPv4.0.0-dynamicEndpoint_POST_save")
      addDynamicEndpoints()
      addStringEntitlement("CanCreateDynamicEndpoint_User469")

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "dynamic" / "save").POST <@ user1
      val response = makePostRequest(request, correctUser, ("Force-Error" -> "OBP-20006"))
      Then("We should get a 403")
      response.code should equal(403)
      val validation = response.body
      val message = (validation \ "message").asInstanceOf[JString].s
      val code = (validation \ "code").asInstanceOf[JInt].num.toInt

      message should be(UserHasMissingRoles)
      code shouldEqual 403
    }

    scenario(s"We will call the endpoint $ApiEndpoint4 with correct Force-Error header value and Response-Code value", VersionOfApi) {
      addOneValidation(jsonSchemaDynamicEndpoint, "OBPv4.0.0-dynamicEndpoint_POST_save")
      addDynamicEndpoints()
      addStringEntitlement("CanCreateDynamicEndpoint_User469")

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "dynamic" / "save").POST <@ user1
      val response = makePostRequest(request, correctUser, ("Force-Error" -> "OBP-20006"), ("Response-Code" -> "444"))
      Then("We should get a 444")
      response.code should equal(444)
      val validation = response.body
      val message = (validation \ "message").asInstanceOf[JString].s
      val code = (validation \ "code").asInstanceOf[JInt].num.toInt

      message should be(UserHasMissingRoles)
      code shouldEqual 444
    }

    scenario(s"We will call the endpoint $ApiEndpoint4 with correct Force-Error header value, but 'enable.force_error=false'", VersionOfApi) {
      setPropsValues("enable.force_error"->"false")
      addOneValidation(jsonSchemaDynamicEndpoint, "OBPv4.0.0-dynamicEndpoint_POST_save")
      addDynamicEndpoints()
      addStringEntitlement("CanCreateDynamicEndpoint_User469")

      When("We make a request v4.0.0")
      val request = (v4_0_0_Request / "dynamic" / "save").POST <@ user1
      val response = makePostRequest(request, correctUser, ("Force-Error" -> "OBP-20006"))
      Then("We should not get a 403")
      response.code should not equal(403)
      val validation = response.body

      val responseBody = prettyRender(validation)

      responseBody should not contain UserHasMissingRoles
      responseBody should not contain "403"
    }
  }

  private def addEntitlement(role: ApiRole, bankId: String = "") = addStringEntitlement(role.toString, bankId)

  private def addStringEntitlement(role: String, bankId: String = "") = Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, role)

  // prepare one JSON Schema Validation for update, delete and get
  private def addOneValidation(schema: String, operationId: String): APIResponse = {
    addEntitlement(canCreateJsonSchemaValidation)
    val request = (v4_0_0_Request / "management" / "json-schema-validations" / operationId).POST <@ user1
    val response = makePostRequest(request, schema)
    response.code should equal(201)

    response
  }

  // prepare one dynamic entity FooBar
  private def addDynamicEntity(): APIResponse = {
    addEntitlement(canCreateDynamicEntity)
    val request = (v4_0_0_Request / "management" / "dynamic-entities").POST <@ user1
    val fooBar =
      s"""
         |{
         |    "bankId": "$bankId",
         |    "FooBar": {
         |        "description": "description of this entity, can be markdown text.",
         |        "required": [
         |            "name"
         |        ],
         |        "properties": {
         |            "name": {
         |                "type": "string",
         |                "minLength": 3,
         |                "maxLength": 20,
         |                "example": "James Brown",
         |                "description": "description of **name** field, can be markdown text."
         |            },
         |            "number": {
         |                "type": "integer",
         |                "example": 698761728,
         |                "description": "description of **number** field, can be markdown text."
         |            }
         |        }
         |    }
         |}""".stripMargin
    val response = makePostRequest(request, fooBar)
    response.code should equal(201)

    response
  }

  // prepare dynamic endpoints
  private def addDynamicEndpoints(): APIResponse = {
    addEntitlement(canCreateDynamicEndpoint)
    val request = (v4_0_0_Request / "management" / "dynamic-endpoints").POST <@ user1

    val response = makePostRequest(request, swagger)
    response.code should equal(201)

    response
  }

  private val correctFx =
    """
      |{
      |    "bank_id": "gh.29.uk",
      |    "from_currency_code": "EUR",
      |    "to_currency_code": "USD",
      |    "conversion_value": 1.136305,
      |    "inverse_conversion_value": 0.8800454103431737,
      |    "effective_date": "2017-09-19T00:00:00Z"
      |}
      |""".stripMargin

  private val wrongFooBar = """{  "name":"James Brown",  "number":8}"""
  private val correctFooBar = """{  "name":"James Brown",  "number":200}"""

  private val swagger =
    """
      |{
      |    "swagger": "2.0",
      |    "info": {
      |        "version": "0.0.1",
      |        "title": "User Infomation for json-schema validation",
      |        "description": "Example Description",
      |        "contact": {
      |            "name": "Example Company",
      |            "email": " simon@example.com",
      |            "url": "https://www.tesobe.com/"
      |        }
      |    },
      |    "host": "obp_mock",
      |    "basePath": "/user",
      |    "schemes": [
      |        "http"
      |    ],
      |    "consumes": [
      |        "application/json"
      |    ],
      |    "produces": [
      |        "application/json"
      |    ],
      |    "paths": {
      |        "/save": {
      |            "post": {
      |                "parameters": [
      |                    {
      |                        "name": "body",
      |                        "in": "body",
      |                        "required": true,
      |                        "schema": {
      |                            "$ref": "#/definitions/user"
      |                        }
      |                    }
      |                ],
      |                "responses": {
      |                    "201": {
      |                        "description": "create user successful and return created user object",
      |                        "schema": {
      |                            "$ref": "#/definitions/user"
      |                        }
      |                    },
      |                    "500": {
      |                        "description": "unexpected error",
      |                        "schema": {
      |                            "$ref": "#/responses/unexpectedError"
      |                        }
      |                    }
      |                }
      |            }
      |        }
      |    },
      |    "definitions": {
      |        "user": {
      |            "type": "object",
      |            "properties": {
      |                "id": {
      |                    "type": "integer",
      |                    "description": "user ID"
      |                },
      |                "first_name": {
      |                    "type": "string"
      |                },
      |                "last_name": {
      |                    "type": "string"
      |                },
      |                "age": {
      |                    "type": "integer"
      |                },
      |                "career": {
      |                    "type": "string"
      |                }
      |            },
      |            "required": [
      |                "first_name",
      |                "last_name",
      |                "age"
      |            ]
      |        },
      |        "APIError": {
      |            "description": "content any error from API",
      |            "type": "object",
      |            "properties": {
      |                "errorCode": {
      |                    "description": "content error code relate to API",
      |                    "type": "string"
      |                },
      |                "errorMessage": {
      |                    "description": "content user-friendly error message",
      |                    "type": "string"
      |                }
      |            }
      |        }
      |    },
      |    "responses": {
      |        "unexpectedError": {
      |            "description": "unexpected error",
      |            "schema": {
      |                "$ref": "#/definitions/APIError"
      |            }
      |        },
      |        "invalidRequest": {
      |            "description": "invalid request",
      |            "schema": {
      |                "$ref": "#/definitions/APIError"
      |            }
      |        }
      |    },
      |    "parameters": {
      |        "userId": {
      |            "name": "userId",
      |            "in": "path",
      |            "required": true,
      |            "type": "string",
      |            "description": "user ID"
      |        }
      |    }
      |}""".stripMargin

  private val jsonSchemaDynamicEndpoint =
    """
      |{
      |    "$schema": "http://json-schema.org/draft-07/schema",
      |    "$id": "http://example.com/example.json",
      |    "type": "object",
      |    "title": "The root schema",
      |    "description": "The root schema comprises the entire JSON document.",
      |    "examples": [
      |        {
      |            "id": 1,
      |            "first_name": "string",
      |            "last_name": "string",
      |            "age": 1
      |        }
      |    ],
      |    "required": [
      |        "id",
      |        "first_name",
      |        "last_name",
      |        "age",
      |        "career"
      |    ],
      |    "properties": {
      |        "id": {
      |            "type": "integer"
      |        },
      |        "first_name": {
      |            "pattern": "[A-Z]\\w+",
      |            "type": "string"
      |        },
      |        "last_name": {
      |            "type": "string"
      |        },
      |        "age": {
      |            "maximum": 150,
      |            "minimum": 1,
      |            "type": "integer"
      |        },
      |        "career": {
      |            "type": "string"
      |        }
      |    },
      |    "additionalProperties": true
      |}
      |""".stripMargin

  private val wrongUser =
    """
      |{
      |    "id": "wrong_id",
      |    "first_name": "xx",
      |    "last_name": "dd",
      |    "age": 200,
      |    "career": "developer"
      |}""".stripMargin

  private val correctUser =
    """
      |{
      |    "id": 111,
      |    "first_name": "Robert",
      |    "last_name": "Li",
      |    "age": 10,
      |    "career": "developer"
      |}""".stripMargin

}
