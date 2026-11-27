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

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, BranchNotFoundByBranchId, UserHasMissingRoles}
import code.api.v2_0_0.BasicAccountsJSON
import code.api.v2_1_0.PutEnabledJSON
import code.api.v3_0_0.BranchJsonV300
import code.api.v3_1_0.{EntitlementJSonsV310, PostPutProductJsonV310}
import code.api.v4_0_0.{ProductAttributeResponseJsonV400, UpdateAccountJsonV400}
import code.consumer.Consumers
import code.entitlement.Entitlement
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.util.Helpers.randomString
import org.json4s._
import org.json4s.native.Serialization.write
import org.scalatest.Tag

/**
 * When an endpoint of a version below v5.1.0 was commented out, the newer handler that serves
 * the same path under /obp/v5.1.0/ took over. Some of those handlers are themselves in an older
 * version file (v2.0.0 to v4.0.0) and had no test of their own. This suite calls each of them
 * through /obp/v5.1.0/, the prefix clients use: the authentication and role checks, then a
 * successful call with its response checked.
 */
class OlderHandlersServedByV510Test extends V510ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v5_1_0.toString)
  object PublicAccountsAtOneBank extends Tag("publicAccountsAtOneBank")
  object GetPrivateAccountsAllBanks extends Tag("getPrivateAccountsAllBanks")
  object CreateBranch extends Tag("createBranch")
  object GetBranch extends Tag("getBranch")
  object UpdateBranch extends Tag("updateBranch")
  object Config extends Tag("config")
  object EnableDisableConsumers extends Tag("enableDisableConsumers")
  object GetAllEntitlements extends Tag("getAllEntitlements")
  object CreateProductAttribute extends Tag("createProductAttribute")
  object GetProductAttribute extends Tag("getProductAttribute")
  object UpdateProductAttribute extends Tag("updateProductAttribute")
  object UpdateAccountLabel extends Tag("updateAccountLabel")

  private val bankId = testBankId1.value
  private val renamedBranch = "Renamed branch"
  private val updatedValue = "updated value"

  private def errorMessage(body: JValue): String = body.extract[ErrorMessage].message

  feature("GET /banks/BANK_ID/accounts/public (v2.0.0 publicAccountsAtOneBank)") {
    scenario("anyone gets the public accounts of a bank, each listed with its public views only",
      PublicAccountsAtOneBank, VersionOfApi) {
      val response = makeGetRequest(v5_1_0_Request / "banks" / bankId / "accounts" / "public")
      response.code should equal(200)
      val accounts = response.body.extract[BasicAccountsJSON].accounts
      accounts.foreach { account =>
        account.bank_id should equal(bankId)
        account.views_available.foreach(_.is_public should equal(true))
      }
    }
  }

  feature("GET /accounts (v2.0.0 getPrivateAccountsAllBanks)") {
    scenario("the private accounts at all banks need a login and list private views only",
      GetPrivateAccountsAllBanks, VersionOfApi) {
      val request = v5_1_0_Request / "accounts"
      makeGetRequest(request.GET).code should equal(401)

      val response = makeGetRequest(request.GET <@ (user1))
      response.code should equal(200)
      val accounts = response.body.extract[BasicAccountsJSON].accounts
      accounts.map(_.id) should contain(testAccountId1.value)
      accounts.foreach(_.views_available.foreach(_.is_public should equal(false)))
    }
  }

  feature("Branches (v3.0.0 createBranch, getBranch and updateBranch)") {
    def branchBody(branchId: String): BranchJsonV300 =
      SwaggerDefinitionsJSON.branchJsonV300.copy(id = branchId, bank_id = bankId)

    scenario("creating a branch needs a login and a branch role", CreateBranch, VersionOfApi) {
      val request = v5_1_0_Request / "banks" / bankId / "branches"
      val anonymous = makePostRequest(request.POST, write(branchBody("BRANCH_ANON")))
      anonymous.code should equal(401)
      errorMessage(anonymous.body) should startWith(AuthenticatedUserIsRequired)

      val noRole = makePostRequest(request.POST <@ (user1), write(branchBody("BRANCH_NO_ROLE")))
      noRole.code should equal(403)
    }

    scenario("we create, read and update a branch", CreateBranch, GetBranch, UpdateBranch, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.canCreateBranch.toString)
      val created = makePostRequest((v5_1_0_Request / "banks" / bankId / "branches").POST <@ (user1),
        write(branchBody("BRANCH_V510_TEST")))
      created.code should equal(201)
      created.body.extract[BranchJsonV300].id should equal("BRANCH_V510_TEST")
      created.body.extract[BranchJsonV300].name should equal(SwaggerDefinitionsJSON.branchJsonV300.name)

      val branchRequest = v5_1_0_Request / "banks" / bankId / "branches" / "BRANCH_V510_TEST"
      val read = makeGetRequest(branchRequest.GET <@ (user1))
      read.code should equal(200)
      read.body.extract[BranchJsonV300].bank_id should equal(bankId)
      read.body.extract[BranchJsonV300].id should equal("BRANCH_V510_TEST")

      val missing = makeGetRequest((v5_1_0_Request / "banks" / bankId / "branches" / "NO_SUCH_BRANCH").GET <@ (user1))
      missing.code should equal(400)
      errorMessage(missing.body) should startWith(BranchNotFoundByBranchId)

      val updateBody = SwaggerDefinitionsJSON.postBranchJsonV300.copy(bank_id = bankId, name = renamedBranch)
      val noUpdateRole = makePutRequest(branchRequest.PUT <@ (user1), write(updateBody))
      noUpdateRole.code should equal(403)
      errorMessage(noUpdateRole.body) should include(ApiRole.canUpdateBranch.toString)

      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.canUpdateBranch.toString)
      val updated = makePutRequest(branchRequest.PUT <@ (user1), write(updateBody))
      updated.code should equal(201)
      updated.body.extract[BranchJsonV300].name should equal(renamedBranch)
      makeGetRequest(branchRequest.GET <@ (user1)).body.extract[BranchJsonV300].name should equal(renamedBranch)
    }
  }

  feature("GET /config (v3.1.0 config)") {
    scenario("the configuration needs a login and the CanGetConfig role", Config, VersionOfApi) {
      val request = v5_1_0_Request / "config"
      makeGetRequest(request.GET).code should equal(401)

      val noRole = makeGetRequest(request.GET <@ (user1))
      noRole.code should equal(403)
      errorMessage(noRole.body) should equal(UserHasMissingRoles + ApiRole.canGetConfig)

      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canGetConfig.toString)
      val response = makeGetRequest(request.GET <@ (user1))
      response.code should equal(200)
      (response.body \ "default_bank_id").extract[String] should not be empty
    }
  }

  feature("PUT /management/consumers/CONSUMER_ID (v3.1.0 enableDisableConsumers)") {
    scenario("we disable and enable a consumer, with the role for each", EnableDisableConsumers, VersionOfApi) {
      // A consumer of its own, so disabling it does not lock out the users this suite calls with.
      val target = Consumers.consumers.vend.createConsumer(
        Some(randomString(40).toLowerCase), Some(randomString(40).toLowerCase), Some(true),
        Some("enable-disable-test"), None, Some("enable/disable test"), Some("test@example.com"),
        None, Some(resourceUser1.userId), None, None, None
      ).openOrThrowException("Could not create the test consumer")
      val request = v5_1_0_Request / "management" / "consumers" / target.consumerId.get

      makePutRequest(request.PUT, write(PutEnabledJSON(false))).code should equal(401)
      val noRole = makePutRequest(request.PUT <@ (user1), write(PutEnabledJSON(false)))
      noRole.code should equal(403)

      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canDisableConsumers.toString)
      val disabled = makePutRequest(request.PUT <@ (user1), write(PutEnabledJSON(false)))
      disabled.code should equal(200)
      disabled.body.extract[PutEnabledJSON].enabled should equal(false)
      isActive(target.consumerId.get) should equal(false)

      Then("enabling needs its own role")
      val enableWithoutRole = makePutRequest(request.PUT <@ (user1), write(PutEnabledJSON(true)))
      enableWithoutRole.code should equal(403)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canEnableConsumers.toString)
      val enabled = makePutRequest(request.PUT <@ (user1), write(PutEnabledJSON(true)))
      enabled.code should equal(200)
      enabled.body.extract[PutEnabledJSON].enabled should equal(true)
      isActive(target.consumerId.get) should equal(true)
    }
  }

  private def isActive(consumerId: String): Boolean =
    Consumers.consumers.vend.getConsumerByConsumerId(consumerId).map(_.isActive.get).openOrThrowException("consumer not found")

  feature("GET /entitlements (v3.1.0 getAllEntitlements)") {
    scenario("listing every entitlement needs a login and the role for any user at any bank", GetAllEntitlements, VersionOfApi) {
      val request = v5_1_0_Request / "entitlements"
      makeGetRequest(request.GET).code should equal(401)

      val noRole = makeGetRequest(request.GET <@ (user1))
      noRole.code should equal(403)
      errorMessage(noRole.body) should include(ApiRole.canGetEntitlementsForAnyUserAtAnyBank.toString)

      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canGetEntitlementsForAnyUserAtAnyBank.toString)
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser2.userId, ApiRole.canGetCustomersAtOneBank.toString)
      val filtered = makeGetRequest(request.GET <@ (user1) <<? List(("role", ApiRole.canGetCustomersAtOneBank.toString)))
      filtered.code should equal(200)
      val entitlements = filtered.body.extract[EntitlementJSonsV310].list
      entitlements should not be empty
      entitlements.foreach(_.role_name should equal(ApiRole.canGetCustomersAtOneBank.toString))
      entitlements.map(_.user_id) should contain(resourceUser2.userId)
    }
  }

  feature("Product attributes (v4.0.0 createProductAttribute, getProductAttribute and updateProductAttribute)") {
    val productCode = "PRODUCT_ATTRIBUTE_V510_TEST"

    def createProduct(): Unit = {
      val json: PostPutProductJsonV310 = SwaggerDefinitionsJSON.postPutProductJsonV310.copy(parent_product_code = "")
      scala.concurrent.Await.result(
        code.api.util.NewStyle.function.createOrUpdateProduct(
          bankId = bankId, code = productCode, parentProductCode = None, name = json.name,
          category = json.category, family = json.family, superFamily = json.super_family,
          moreInfoUrl = json.more_info_url, termsAndConditionsUrl = null, details = json.details,
          description = json.description, metaLicenceId = json.meta.license.id,
          metaLicenceName = json.meta.license.name, callContext = None),
        scala.concurrent.duration.Duration(30, "seconds"))
    }

    scenario("creating a product attribute needs a login and the role", CreateProductAttribute, VersionOfApi) {
      val request = v5_1_0_Request / "banks" / bankId / "products" / productCode / "attribute"
      val body = write(SwaggerDefinitionsJSON.productAttributeJsonV400)
      makePostRequest(request.POST, body).code should equal(401)
      val noRole = makePostRequest(request.POST <@ (user1), body)
      noRole.code should equal(403)
      errorMessage(noRole.body) should include(ApiRole.canCreateProductAttribute.toString)
    }

    scenario("we create, read and update a product attribute",
      CreateProductAttribute, GetProductAttribute, UpdateProductAttribute, VersionOfApi) {
      createProduct()
      val attributeJson = SwaggerDefinitionsJSON.productAttributeJsonV400

      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.canCreateProductAttribute.toString)
      val created = makePostRequest(
        (v5_1_0_Request / "banks" / bankId / "products" / productCode / "attribute").POST <@ (user1), write(attributeJson))
      created.code should equal(201)
      val createdAttribute = created.body.extract[ProductAttributeResponseJsonV400]
      createdAttribute.bank_id should equal(bankId)
      createdAttribute.product_code should equal(productCode)
      createdAttribute.name should equal(attributeJson.name)
      createdAttribute.value should equal(attributeJson.value)

      val attributeRequest = v5_1_0_Request / "banks" / bankId / "products" / productCode / "attributes" /
        createdAttribute.product_attribute_id
      val readWithoutRole = makeGetRequest(attributeRequest.GET <@ (user1))
      readWithoutRole.code should equal(403)
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.canGetProductAttribute.toString)
      val read = makeGetRequest(attributeRequest.GET <@ (user1))
      read.code should equal(200)
      read.body.extract[ProductAttributeResponseJsonV400] should equal(createdAttribute)

      val updatedJson = attributeJson.copy(value = updatedValue)
      val updateWithoutRole = makePutRequest(attributeRequest.PUT <@ (user1), write(updatedJson))
      updateWithoutRole.code should equal(403)
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.canUpdateProductAttribute.toString)
      val updated = makePutRequest(attributeRequest.PUT <@ (user1), write(updatedJson))
      updated.code should equal(200)
      updated.body.extract[ProductAttributeResponseJsonV400].value should equal(updatedValue)
      makeGetRequest(attributeRequest.GET <@ (user1)).body.extract[ProductAttributeResponseJsonV400].value should equal(updatedValue)
    }
  }

  feature("POST /banks/BANK_ID/accounts/ACCOUNT_ID (v4.0.0 updateAccountLabel)") {
    scenario("the account holder changes the label, other users cannot", UpdateAccountLabel, VersionOfApi) {
      val accountId = testAccountId1.value
      val request = v5_1_0_Request / "banks" / bankId / "accounts" / accountId
      val body = write(UpdateAccountJsonV400(label = "Label set through v5.1.0"))

      makePostRequest(request.POST, body).code should equal(401)

      val otherUser = makePostRequest(request.POST <@ (user3), body)
      otherUser.code should equal(400)
      errorMessage(otherUser.body) should include("can_update_bank_account_label")

      val holder = makePostRequest(request.POST <@ (user1), body)
      holder.code should equal(200)
      val account = makeGetRequest((v5_1_0_Request / "banks" / bankId / "accounts" / accountId / "owner" / "account").GET <@ (user1))
      account.code should equal(200)
      (account.body \ "label").extract[String] should equal("Label set through v5.1.0")
    }
  }
}
