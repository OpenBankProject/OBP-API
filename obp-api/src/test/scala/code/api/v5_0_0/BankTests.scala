package code.api.v5_0_0

import code.api.Constant.{INCOMING_SETTLEMENT_ACCOUNT_ID, OUTGOING_SETTLEMENT_ACCOUNT_ID}
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.postBankJson500
import code.api.util.ApiRole.CanCreateBank
import code.api.util.ErrorMessages.UserHasMissingRoles
import code.api.util.{APIUtil, ApiRole, ErrorMessages, NewStyle}
import code.api.util.APIUtil.OAuth._
import code.api.v5_0_0.APIMethods500.Implementations5_0_0
import code.entitlement.Entitlement
import code.setup.{APIResponse, DefaultUsers}
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{AccountId, BankId, ErrorMessage}
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class BankTests extends V500ServerSetupAsync with DefaultUsers {

   override def beforeAll() {
     super.beforeAll()
   }

   override def afterAll() {
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
      val response = makePostRequestAsync(request, write(postBankJson500))
      Then("We should get a 401")
      And("We should get a message: " + ErrorMessages.UserNotLoggedIn)
      response map { r =>
          r.code should equal(401)
          r.body.extract[ErrorMessage].message should equal(ErrorMessages.UserNotLoggedIn)
      }
    }

    scenario("We try to consume endpoint createBank without proper role - Authorized access", ApiEndpoint1, VersionOfApi) {
      When("We make the request")
      val request = (v5_0_0_Request / "banks").POST <@ (user1)
      val response = makePostRequestAsync(request, write(postBankJson500))
      Then("We should get a 403")
      And("We should get a message: " + s"$CanCreateBank entitlement required")
      response map { r =>
          r.code should equal(403)
          r.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanCreateBank)
      }
    }

    scenario("We try to consume endpoint createBank with proper role - Authorized access", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, VersionOfApi) {
      When("We add required entitlement")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanCreateBank.toString)
      And("We make the request")
      val firstFullName = "A new full name"
      val secondFullName = "A second new full name"
      val postBank = postBankJson500.copy(id = Some(APIUtil.generateUUID()))
      val bankId = postBank.id.getOrElse("some_bank_id")
      val request = (v5_0_0_Request / "banks").POST <@ (user1)
      val requestPut = (v5_0_0_Request / "banks").PUT <@ (user1)
      val response = for {
        before <- NewStyle.function.getEntitlementsByUserId(resourceUser1.userId, None) map {
          _.exists( e => e.roleName == ApiRole.CanCreateEntitlementAtOneBank.toString && e.bankId == bankId)
        }
        response: APIResponse <- makePostRequestAsync(request, write(postBank))
        after <- NewStyle.function.getEntitlementsByUserId(resourceUser1.userId, None) map {
          _.exists( e => e.roleName == ApiRole.CanCreateEntitlementAtOneBank.toString && e.bankId == bankId)
        }
        requestGet = (v5_0_0_Request / "banks" / bankId).GET <@ (user1)
        responseGet <- makeGetRequestAsync(requestGet)
        secondResponse: APIResponse <- makePostRequestAsync(request, write(postBank))
        putResponse: APIResponse <- makePutRequestAsync(requestPut, write(postBank.copy(full_name = Some(firstFullName))))
        secondPutResponse: APIResponse <- makePutRequestAsync(requestPut, write(postBank.copy(full_name = Some(secondFullName))))
      } yield (before, after, response, responseGet, secondResponse, putResponse, secondPutResponse)
      Then("We should get a 201")
      response flatMap  { r =>
        r._1 should equal(false) // Before we create a bank there is no role CanCreateEntitlementAtOneBank
        r._2 should equal(true) // After we create a bank there is a role CanCreateEntitlementAtOneBank
        r._3.code should equal(201)
        Then("Default settlement accounts should be created")
        val defaultOutgoingAccount = NewStyle.function.checkBankAccountExists(BankId(postBank.id.getOrElse("")), AccountId(OUTGOING_SETTLEMENT_ACCOUNT_ID), None)
        val defaultIncomingAccount = NewStyle.function.checkBankAccountExists(BankId(postBank.id.getOrElse("")), AccountId(INCOMING_SETTLEMENT_ACCOUNT_ID), None)
        defaultOutgoingAccount.map(account => account._1.accountId.value should equal(OUTGOING_SETTLEMENT_ACCOUNT_ID))
        defaultIncomingAccount.map(account => account._1.accountId.value should equal(INCOMING_SETTLEMENT_ACCOUNT_ID))
        Then("We should get a 200")
        r._4.code should equal(200)
        r._4.body.extract[BankJson500].bank_code should equal(postBank.bank_code)
        r._5.code should equal(400)
        r._5.body.extract[ErrorMessage].message should equal(ErrorMessages.bankIdAlreadyExists)
        r._6.code should equal(200)
        r._6.body.extract[BankJson500].full_name should equal(firstFullName)
        r._7.code should equal(200)
        r._7.body.extract[BankJson500].full_name should equal(secondFullName)
      }
    }
  }

 }