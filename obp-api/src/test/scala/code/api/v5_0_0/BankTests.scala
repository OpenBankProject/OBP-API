package code.api.v5_0_0

import org.json4s._
import code.api.Constant.{INCOMING_SETTLEMENT_ACCOUNT_ID, OUTGOING_SETTLEMENT_ACCOUNT_ID}
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.postBankJson500
import code.api.util.ApiRole.CanCreateBank
import code.api.util.ErrorMessages.UserHasMissingRoles
import code.api.util.{APIUtil, ApiRole, ErrorMessages, NewStyle}
import code.api.util.APIUtil.OAuth._
import code.api.v5_0_0.Http4s500.Implementations5_0_0
import code.entitlement.Entitlement
import code.setup.{APIResponse, DefaultUsers}
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{AccountId, BankId, ErrorMessage}
import com.openbankproject.commons.util.ApiVersion
import org.json4s.native.Serialization.write
import org.scalatest.Tag

import scala.concurrent.Await
import scala.concurrent.duration._

class BankTests extends V500ServerSetup with DefaultUsers {

   override def beforeAll(): Unit = {
     super.beforeAll()
   }

   override def afterAll(): Unit = {
     super.afterAll()
   }

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v5_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations5_0_0.createBank))
  object ApiEndpoint2 extends Tag(nameOf(Implementations5_0_0.getBank))
  object ApiEndpoint3 extends Tag(nameOf(Implementations5_0_0.updateBank))

  feature(s"Assuring that endpoint createBank works as expected - $VersionOfApi") {

    scenario("We try to consume endpoint createBank - Anonymous access", ApiEndpoint1, VersionOfApi) {
      When("We make the request")
      val request = (v5_0_0_Request / "banks").POST
      val response = makePostRequest(request, write(postBankJson500))
      Then("We should get a 401")
      And("We should get a message: " + ErrorMessages.AuthenticatedUserIsRequired)
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(ErrorMessages.AuthenticatedUserIsRequired)
    }

    scenario("We try to consume endpoint createBank without proper role - Authorized access", ApiEndpoint1, VersionOfApi) {
      When("We make the request")
      val request = (v5_0_0_Request / "banks").POST <@ (user1)
      val response = makePostRequest(request, write(postBankJson500))
      Then("We should get a 403")
      And("We should get a message: " + s"$CanCreateBank entitlement required")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanCreateBank)
    }

    scenario("We try to consume endpoint createBank with proper role - Authorized access", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, VersionOfApi) {
      When("We add required entitlement")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanCreateBank.toString)
      And("We make the request")
      val firstFullName = "A new full name"
      val secondFullName = "A second new full name"
      val postBank = postBankJson500.copy(id = Some(APIUtil.generateUUID().substring(25)))
      val bankId = postBank.id.getOrElse("some_bank_id")
      val request = (v5_0_0_Request / "banks").POST <@ (user1)
      val requestPut = (v5_0_0_Request / "banks").PUT <@ (user1)
      val before = Await.result(
        NewStyle.function.getEntitlementsByUserId(resourceUser1.userId, None), 10.seconds
      ).exists(e => e.roleName == ApiRole.CanCreateEntitlementAtOneBank.toString && e.bankId == bankId)
      val response = makePostRequest(request, write(postBank))
      val after = Await.result(
        NewStyle.function.getEntitlementsByUserId(resourceUser1.userId, None), 10.seconds
      ).exists(e => e.roleName == ApiRole.CanCreateEntitlementAtOneBank.toString && e.bankId == bankId)
      val requestGet = (v5_0_0_Request / "banks" / bankId).GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      val secondResponse = makePostRequest(request, write(postBank))
      val putResponse = makePutRequest(requestPut, write(postBank.copy(full_name = Some(firstFullName))))
      val secondPutResponse = makePutRequest(requestPut, write(postBank.copy(full_name = Some(secondFullName))))
      Then("We should get a 201")
      before should equal(false) // Before we create a bank there is no role CanCreateEntitlementAtOneBank
      after should equal(true) // After we create a bank there is a role CanCreateEntitlementAtOneBank
      response.code should equal(201)
      Then("Default settlement accounts should be created")
      val defaultOutgoingAccount = Await.result(
        NewStyle.function.checkBankAccountExists(BankId(postBank.id.getOrElse("")), AccountId(OUTGOING_SETTLEMENT_ACCOUNT_ID), None), 10.seconds)
      val defaultIncomingAccount = Await.result(
        NewStyle.function.checkBankAccountExists(BankId(postBank.id.getOrElse("")), AccountId(INCOMING_SETTLEMENT_ACCOUNT_ID), None), 10.seconds)
      defaultOutgoingAccount._1.accountId.value should equal(OUTGOING_SETTLEMENT_ACCOUNT_ID)
      defaultIncomingAccount._1.accountId.value should equal(INCOMING_SETTLEMENT_ACCOUNT_ID)
      Then("We should get a 200")
      responseGet.code should equal(200)
      responseGet.body.extract[BankJson500].bank_code should equal(postBank.bank_code)
      secondResponse.code should equal(400)
      secondResponse.body.extract[ErrorMessage].message should equal(ErrorMessages.bankIdAlreadyExists)
      putResponse.code should equal(200)
      putResponse.body.extract[BankJson500].full_name should equal(firstFullName)
      secondPutResponse.code should equal(200)
      secondPutResponse.body.extract[BankJson500].full_name should equal(secondFullName)
    }
  }

 }