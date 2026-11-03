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

package code.api.v4_0_0

import org.json4s._
import code.DynamicData.{DynamicData, DynamicDataProvider}
import code.accountholders.MapperAccountHolders
import code.dynamicEntity.{DynamicEntity, DynamicEntityCommons, DynamicEntityProvider}
import net.liftweb.util.StringHelpers
import code.api.Constant
import java.util.concurrent.TimeUnit

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.createViewJsonV300
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanDeleteBankCascade, canGetCustomersMinimalAtAllBanks}
import code.api.util.ErrorMessages.{UserHasMissingRoles, AuthenticatedUserIsRequired}
import code.api.util.{APIUtil, ApiRole}
import code.api.v3_1_0.CreateAccountResponseJsonV310
import code.api.v4_0_0.Http4s400.Implementations4_0_0
import code.entitlement.{Entitlement, MappedEntitlement}
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{AmountOfMoneyJsonV121, ErrorMessage}
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.mapper.By
import org.json4s.native.Serialization.write
import org.scalatest.Tag

class DeleteBankCascadeTest extends V400ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * mvn test -D tagsToInclude
    *
    * This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.deleteBankCascade))
  
  lazy val addAccountJson = SwaggerDefinitionsJSON.createAccountRequestJsonV310.copy(user_id = resourceUser1.userId, balance = AmountOfMoneyJsonV121("EUR","0"))


  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      val bankId = createBank(APIUtil.generateUUID()).bankId.value
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "cascading" / "banks" / bankId ).DELETE
      val response400 = makeDeleteRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }
  }
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val bankId = createBank(APIUtil.generateUUID()).bankId.value
      val request400 = (v4_0_0_Request / "management" / "cascading" / "banks" / bankId ).DELETE <@(user1)
      val response400 = makeDeleteRequest(request400)
      Then("We should get a 403")
      response400.code should equal(403)
      val errorMessage = response400.body.extract[ErrorMessage].message
      errorMessage contains (UserHasMissingRoles) should be (true)
      errorMessage contains (CanDeleteBankCascade.toString()) should be (true)
    }
  }
  feature(s"test $ApiEndpoint1 - Authorized access") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint1, VersionOfApi) {
      When("We grant the role")
      val bankId = createBank(APIUtil.generateUUID()).bankId.value
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.canCreateAccount.toString)
      And("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "accounts" ).POST <@(user1)
      val response400 = makePostRequest(request400, write(addAccountJson))

  
      Then("We should get a 201")
      response400.code should equal(201)
      val account = response400.body.extract[CreateAccountResponseJsonV310]
      account.account_id should not be empty

      val postBodyView = createViewJsonV300.copy(name = "_cascade_delete", metadata_view = "_cascade_delete", is_public = false).toCreateViewJson
      createViewViaEndpoint(bankId, account.account_id, postBodyView, user1)
      
      createAccountAttributeViaEndpoint(
        bankId,
        account.account_id,
        "REQUIRED_CHALLENGE_ANSWERS",
        "2",
        "INTEGER",
        Some("LKJL98769F")
      )

      val customerNumber = createCustomerViaEndpointAndGetNumber(bankId, resourceUser1.userId)
      createAccountAttributeViaEndpoint(
        bankId,
        account.account_id,
        "customer_number",
        customerNumber,
        "STRING",
        None
      )

      grantUserAccessToViewViaEndpoint(
        bankId,
        account.account_id,
        resourceUser2.userId,
        user1,
        PostViewJsonV400(view_id = Constant.SYSTEM_OWNER_VIEW_ID, is_system = true)
      )

      createWebhookViaEndpoint(
        bankId,
        account.account_id,
        resourceUser1.userId,
        user1
      )

      When("We grant the role")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.canDeleteBankCascade.toString)
      And("We make a delete cascade request v4.0.0")
      val deleteRequest400 = (v4_0_0_Request / "management" / "cascading" / "banks" / bankId ).DELETE <@(user1)
      val deleteResponse400 = makeDeleteRequest(deleteRequest400)
      Then("We should get a 200")
      deleteResponse400.code should equal(200)

      When("We try to delete one more time")
      makeDeleteRequest(request400).code should equal(404)

      // Bnam customers must be deleted as well
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canGetCustomersMinimalAtAllBanks.toString)
      When(s"We make a request $VersionOfApi")
      val request = (v4_0_0_Request / "customers-minimal").GET <@(user1)
      val response = makeGetRequest(request)
      Then("We should get a 200")
      response.code should equal(200)
      val responseBody = response.body.extract[CustomersMinimalJsonV400]
      responseBody.customers.length equals 0
    }
    scenario("We delete a bank whose accounts have paid by transaction requests", ApiEndpoint1, VersionOfApi) {
      val bankId = createBank(APIUtil.generateUUID()).bankId.value
      val (fromAccountId, toAccountId, transactionRequestIds) = createPaymentsForDeleteCascade(bankId)
      doubleEntryBookTransactionsOfAccount(bankId, fromAccountId) should not be empty
      transactionRequestsAndChallenges(transactionRequestIds)._2 should not be empty

      When("We grant the role")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.canDeleteBankCascade.toString)
      And("We make a delete cascade request v4.0.0")
      val deleteRequest400 = (v4_0_0_Request / "management" / "cascading" / "banks" / bankId ).DELETE <@(user1)
      makeDeleteRequest(deleteRequest400).code should equal(200)

      Then("The double-entry book rows, transaction requests and challenges of its accounts are deleted")
      doubleEntryBookTransactionsOfAccount(bankId, fromAccountId) shouldBe empty
      doubleEntryBookTransactionsOfAccount(bankId, toAccountId) shouldBe empty
      val (transactionRequestsAfter, challengesAfter) = transactionRequestsAndChallenges(transactionRequestIds)
      transactionRequestsAfter shouldBe empty
      challengesAfter shouldBe empty
    }
    scenario("We delete a bank that has dynamic entities, their records and a ZZ_BAK_ copy", ApiEndpoint1, VersionOfApi) {
      val bankId = createBank(APIUtil.generateUUID()).bankId.value
      val otherBankId = createBank(APIUtil.generateUUID()).bankId.value
      val owner = "bank-cascade-owner"
      val suffix = APIUtil.generateUUID().take(8).replace("-", "")
      def defineWithRecord(space: String, name: String): Unit = {
        DynamicEntityProvider.connectorMethodProvider.vend.createOrUpdate(DynamicEntityCommons(
          name, s"""{"$name":{"properties":{"${StringHelpers.snakify(name)}_id":{"type":"string"},"note":{"type":"string"}}}}""",
          None, owner, Some(space), hasPersonalEntity = false)).openOrThrowException("definition")
        DynamicDataProvider.connectorMethodProvider.vend.save(Some(space), name,
          JObject(JField(StringHelpers.snakify(name) + "_id", JString(APIUtil.generateUUID())), JField("note", JString("kept?"))),
          Some(owner), false).openOrThrowException("record")
      }
      defineWithRecord(bankId, s"Echo$suffix")
      defineWithRecord(bankId, s"ZZ_BAK_Echo$suffix")
      defineWithRecord(otherBankId, s"Echo$suffix")
      def definitionsAt(space: String) = DynamicEntity.findAll(By(DynamicEntity.BankId, space))
      def recordsAt(space: String) = DynamicData.findAll(By(DynamicData.BankId, space))
      val readRole = s"CanGetDynamicEntityRecord_Echo$suffix"
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, readRole)
      def readRoleGrantsAt(space: String) = MappedEntitlement.findAll(By(MappedEntitlement.mBankId, space), By(MappedEntitlement.mRoleName, readRole))
      definitionsAt(bankId) should have size 2
      recordsAt(bankId) should have size 2
      readRoleGrantsAt(bankId) should have size 1

      When("We grant the role and delete the bank with the cascading endpoint")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.canDeleteBankCascade.toString)
      makeDeleteRequest((v4_0_0_Request / "management" / "cascading" / "banks" / bankId).DELETE <@(user1)).code should equal(200)

      Then("The bank's definitions and records are gone, and the other bank keeps its own")
      definitionsAt(bankId) shouldBe empty
      recordsAt(bankId) shouldBe empty
      readRoleGrantsAt(bankId) shouldBe empty
      definitionsAt(otherBankId) should have size 1
      recordsAt(otherBankId) should have size 1
    }

    scenario("We delete a bank whose accounts have holders", ApiEndpoint1, VersionOfApi) {
      val bankId = createBank(APIUtil.generateUUID()).bankId.value
      val accountId = createAccountViaEndpoint(bankId, addAccountJson, user1).account_id
      def accountHolders = MapperAccountHolders.findAll(
        By(MapperAccountHolders.accountBankPermalink, bankId),
        By(MapperAccountHolders.accountPermalink, accountId)
      )
      accountHolders should not be empty

      When("We grant the role")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.canDeleteBankCascade.toString)
      And("We delete the bank with the cascading endpoint")
      val deleteRequest400 = (v4_0_0_Request / "management" / "cascading" / "banks" / bankId ).DELETE <@(user1)
      makeDeleteRequest(deleteRequest400).code should equal(200)

      Then("The holders of its accounts are deleted")
      accountHolders shouldBe empty
    }
  }


}
