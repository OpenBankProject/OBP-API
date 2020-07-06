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
Osloerstrasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)
  */
package code.api.v3_1_0

import com.openbankproject.commons.model.ErrorMessage
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import com.openbankproject.commons.util.ApiVersion
import code.api.util.ErrorMessages._
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.customer.CustomerX
import code.entitlement.Entitlement
import code.usercustomerlinks.UserCustomerLink
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class CustomerTest extends V310ServerSetup {

  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    CustomerX.customerProvider.vend.bulkDeleteCustomers()
    UserCustomerLink.userCustomerLink.vend.bulkDeleteUserCustomerLinks()
  }

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v3_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations3_1_0.getCustomerByCustomerId))
  object ApiEndpoint2 extends Tag(nameOf(Implementations3_1_0.getCustomerByCustomerNumber))
  object ApiEndpoint3 extends Tag(nameOf(Implementations3_1_0.createCustomer))
  object ApiEndpoint4 extends Tag(nameOf(Implementations3_1_0.updateCustomerEmail))
  object ApiEndpoint5 extends Tag(nameOf(Implementations3_1_0.updateCustomerMobileNumber))
  object ApiEndpoint6 extends Tag(nameOf(Implementations3_1_0.updateCustomerIdentity))
  object ApiEndpoint7 extends Tag(nameOf(Implementations3_1_0.updateCustomerCreditLimit))
  object ApiEndpoint8 extends Tag(nameOf(Implementations3_1_0.updateCustomerCreditRatingAndSource))
  object ApiEndpoint9 extends Tag(nameOf(Implementations3_1_0.updateCustomerBranch))
  object ApiEndpoint10 extends Tag(nameOf(Implementations3_1_0.updateCustomerData))
  object ApiEndpoint11 extends Tag(nameOf(Implementations3_1_0.updateCustomerNumber))

  val customerNumberJson = PostCustomerNumberJsonV310(customer_number = "123")
  val postCustomerJson = SwaggerDefinitionsJSON.postCustomerJsonV310
  val putCustomerUpdateMobileJson = SwaggerDefinitionsJSON.putUpdateCustomerMobileNumberJsonV310
  val putCustomerUpdateEmailJson = SwaggerDefinitionsJSON.putUpdateCustomerEmailJsonV310
  val putCustomerUpdateNumberJson = SwaggerDefinitionsJSON.putUpdateCustomerNumberJsonV310.copy(customer_number = "new1234")
  val putCustomerUpdateGeneralDataJson = SwaggerDefinitionsJSON.putUpdateCustomerIdentityJsonV310
  val putUpdateCustomerCreditLimitJsonV310 = SwaggerDefinitionsJSON.putUpdateCustomerCreditLimitJsonV310
  val putUpdateCustomerCreditRatingAndSourceJsonV310 = SwaggerDefinitionsJSON.putUpdateCustomerCreditRatingAndSourceJsonV310
  val putUpdateCustomerBranch = SwaggerDefinitionsJSON.putCustomerBranchJsonV310
  val putUpdateCustomerData = SwaggerDefinitionsJSON.putUpdateCustomerDataJsonV310
  lazy val bankId = randomBankId

  feature("Create Customer v3.1.0 - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers").POST
      val response310 = makePostRequest(request310, write(postCustomerJson))
      Then("We should get a 401")
      response310.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }

  feature("Create Customer v3.1.0 - Authorized access") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers").POST <@(user1)
      val response310 = makePostRequest(request310, write(postCustomerJson))
      Then("We should get a 403")
      response310.code should equal(403)
      val errorMsg = UserHasMissingRoles + canCreateCustomer + " or " + canCreateCustomerAtAnyBank
      And("error should be " + errorMsg)
      response310.body.extract[ErrorMessage].message should equal (errorMsg)
    }
    scenario("We will call the endpoint with a user credentials and a proper role", ApiEndpoint3, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateCustomer.toString)
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers").POST <@(user1)
      val response310 = makePostRequest(request310, write(postCustomerJson))
      Then("We should get a 201")
      response310.code should equal(201)
      val infoPost = response310.body.extract[CustomerJsonV310]

      When("We make the request: Get Customer specified by CUSTOMER_ID")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetCustomer.toString)
      val requestGet = (v3_1_0_Request / "banks" / bankId / "customers" / infoPost.customer_id).GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      Then("We should get a 200")
      responseGet.code should equal(200)
      And("We should get the right information back")
      val infoGet = responseGet.body.extract[CustomerJsonV310]
      And("POST feedback and GET feedback must be the same")
      infoGet should equal(infoPost)
    }
  }

  feature("Get Customer by CUSTOMER_ID v3.1.0 - Authorized access")
  {
    scenario("We will call the endpoint without the proper Role " + canGetCustomer, ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0 without a Role " + canGetCustomer)
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID").GET <@(user1)
      val response310 = makeGetRequest(request310)
      Then("We should get a 403")
      response310.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanGetCustomer)
      response310.body.extract[ErrorMessage].message should equal (UserHasMissingRoles + CanGetCustomer)
    }
    scenario("We will call the endpoint with the proper Role " + canGetCustomer, ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetCustomer.toString)
      When("We make a request v3.1.0 with the Role " + canGetCustomer + " but with non existing CUSTOMER_ID")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID").GET <@(user1)
      val response310 = makeGetRequest(request310)
      Then("We should get a 404")
      response310.code should equal(404)
      And("error should be " + CustomerNotFoundByCustomerId)
      response310.body.extract[ErrorMessage].message should startWith (CustomerNotFoundByCustomerId)
    }
  }

  feature("Get Customer by customer number v3.1.0 - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "customer-number").POST
      val response310 = makePostRequest(request310, write(customerNumberJson))
      Then("We should get a 401")
      response310.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }

  feature("Get Customer by customer number v3.1.0 - Authorized access") {
    scenario("We will call the endpoint without the proper Role " + canGetCustomer, ApiEndpoint2, VersionOfApi) {
      When("We make a request v3.1.0 without a Role " + canGetCustomer)
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "customer-number").POST <@(user1)
      val response310 = makePostRequest(request310, write(customerNumberJson))
      Then("We should get a 403")
      response310.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanGetCustomer)
      response310.body.extract[ErrorMessage].message should equal (UserHasMissingRoles + CanGetCustomer)
    }
    scenario("We will call the endpoint with the proper Role " + canGetCustomer, ApiEndpoint2, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetCustomer.toString)
      When("We make a request v3.1.0 with the Role " + canGetCustomer + " but with non existing customer number")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "customer-number").POST <@(user1)
      val response310 = makePostRequest(request310, write(customerNumberJson))
      Then("We should get a 404")
      response310.code should equal(404)
      And("error should be " + CustomerNotFound)
      response310.body.extract[ErrorMessage].message should startWith (CustomerNotFound)
    }
  }

  feature("Update the email of an Customer v3.1.0 - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint3, ApiEndpoint4, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "email" ).PUT
      val response310 = makePutRequest(request310, write(putCustomerUpdateEmailJson))
      Then("We should get a 401")
      response310.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }
  feature("Update the email of an Customer v3.1.0 - Authorized access") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint3, ApiEndpoint4, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "email" ).PUT <@(user1)
      val response310 = makePutRequest(request310, write(putCustomerUpdateEmailJson))
      Then("We should get a 403")
      response310.code should equal(403)
      val errorMsg = UserHasMissingRoles + canUpdateCustomerEmail
      And("error should be " + errorMsg)
      response310.body.extract[ErrorMessage].message should equal (errorMsg)
    }
    scenario("We will call the endpoint with user credentials and the proper role", ApiEndpoint3, ApiEndpoint4, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateCustomer.toString)
      When("We make a request v3.1.0")
      val postRequest310 = (v3_1_0_Request / "banks" / bankId / "customers").POST <@(user1)
      val postResponse310 = makePostRequest(postRequest310, write(postCustomerJson))
      Then("We should get a 201")
      postResponse310.code should equal(201)
      val infoPost = postResponse310.body.extract[CustomerJsonV310]

      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanUpdateCustomerEmail.toString)
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / infoPost.customer_id / "email" ).PUT <@(user1)
      val response310 = makePutRequest(request310, write(putCustomerUpdateEmailJson))
      Then("We should get a 200")
      response310.code should equal(200)

      val infoGet = response310.body.extract[CustomerJsonV310]
      infoGet.email should equal(putCustomerUpdateEmailJson.email)
    }
  }

  feature("Update the mobile phone number of an Customer v3.1.0 - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint3, ApiEndpoint5, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "mobile-number" ).PUT
      val response310 = makePutRequest(request310, write(putCustomerUpdateMobileJson))
      Then("We should get a 401")
      response310.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }
  feature("Update the mobile phone number of an Customer v3.1.0 - Authorized access") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint3, ApiEndpoint5, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "mobile-number" ).PUT <@(user1)
      val response310 = makePutRequest(request310, write(putCustomerUpdateMobileJson))
      Then("We should get a 403")
      response310.code should equal(403)
      val errorMsg = UserHasMissingRoles + canUpdateCustomerMobilePhoneNumber
      And("error should be " + errorMsg)
      response310.body.extract[ErrorMessage].message should equal (errorMsg)
    }
    scenario("We will call the endpoint with user credentials and the proper role", ApiEndpoint5, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateCustomer.toString)
      When("We make a request v3.1.0")
      val postRequest310 = (v3_1_0_Request / "banks" / bankId / "customers").POST <@(user1)
      val postResponse310 = makePostRequest(postRequest310, write(postCustomerJson))
      Then("We should get a 201")
      postResponse310.code should equal(201)
      val infoPost = postResponse310.body.extract[CustomerJsonV310]

      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanUpdateCustomerMobilePhoneNumber.toString)
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / infoPost.customer_id / "mobile-number" ).PUT <@(user1)
      val response310 = makePutRequest(request310, write(putCustomerUpdateMobileJson))
      Then("We should get a 200")
      response310.code should equal(200)

      val infoGet = response310.body.extract[CustomerJsonV310]
      infoGet.mobile_phone_number should equal(putCustomerUpdateMobileJson.mobile_phone_number)
    }
  }


  feature("Update the general data of an Customer v3.1.0 - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint3, ApiEndpoint6, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "identity" ).PUT
      val response310 = makePutRequest(request310, write(putCustomerUpdateGeneralDataJson))
      Then("We should get a 401")
      response310.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }
  feature("Update the general data of an Customer v3.1.0 - Authorized access") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint3, ApiEndpoint6, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "identity" ).PUT <@(user1)
      val response310 = makePutRequest(request310, write(putCustomerUpdateGeneralDataJson))
      Then("We should get a 403")
      response310.code should equal(403)
      val errorMsg = UserHasMissingRoles + canUpdateCustomerIdentity
      And("error should be " + errorMsg)
      response310.body.extract[ErrorMessage].message should equal (errorMsg)
    }
    scenario("We will call the endpoint with user credentials and the proper role", ApiEndpoint6, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateCustomer.toString)
      When("We make a request v3.1.0")
      val postRequest310 = (v3_1_0_Request / "banks" / bankId / "customers").POST <@(user1)
      val postResponse310 = makePostRequest(postRequest310, write(postCustomerJson))
      Then("We should get a 201")
      postResponse310.code should equal(201)
      val infoPost = postResponse310.body.extract[CustomerJsonV310]

      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanUpdateCustomerIdentity.toString)
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / infoPost.customer_id / "identity" ).PUT <@(user1)
      val response310 = makePutRequest(request310, write(putCustomerUpdateGeneralDataJson))
      Then("We should get a 200")
      response310.code should equal(200)

      val infoGet = response310.body.extract[CustomerJsonV310]
      infoGet.legal_name should equal(putCustomerUpdateGeneralDataJson.legal_name)
      infoGet.date_of_birth should equal(putCustomerUpdateGeneralDataJson.date_of_birth)
      infoGet.title should equal(putCustomerUpdateGeneralDataJson.title)
      infoGet.name_suffix should equal(putCustomerUpdateGeneralDataJson.name_suffix)
    }
  }

  
  feature("Update the credit limit of an Customer v3.1.0 - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint3, ApiEndpoint7, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "credit-limit" ).PUT
      val response310 = makePutRequest(request310, write(putUpdateCustomerCreditLimitJsonV310))
      Then("We should get a 401")
      response310.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }
  feature("Update the credit limit of an Customer v3.1.0 - Authorized access") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint3, ApiEndpoint7, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "credit-limit" ).PUT <@(user1)
      val response310 = makePutRequest(request310, write(putUpdateCustomerCreditLimitJsonV310))
      Then("We should get a 403")
      response310.code should equal(403)
      val errorMsg = UserHasMissingRoles + canUpdateCustomerCreditLimit
      And("error should be " + errorMsg)
      response310.body.extract[ErrorMessage].message should equal (errorMsg)
    }
    scenario("We will call the endpoint with user credentials and the proper role", ApiEndpoint7, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateCustomer.toString)
      When("We make a request v3.1.0")
      val postRequest310 = (v3_1_0_Request / "banks" / bankId / "customers").POST <@(user1)
      val postResponse310 = makePostRequest(postRequest310, write(postCustomerJson))
      Then("We should get a 201")
      postResponse310.code should equal(201)
      val infoPost = postResponse310.body.extract[CustomerJsonV310]

      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanUpdateCustomerCreditLimit.toString)
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / infoPost.customer_id / "credit-limit" ).PUT <@(user1)
      val response310 = makePutRequest(request310, write(putUpdateCustomerCreditLimitJsonV310))
      Then("We should get a 200")
      response310.code should equal(200)

      val infoGet = response310.body.extract[CustomerJsonV310]
      infoGet.credit_limit.map(_.amount).getOrElse("") should equal(putUpdateCustomerCreditLimitJsonV310.credit_limit.amount)
      infoGet.credit_limit.map(_.currency).getOrElse("") should equal(putUpdateCustomerCreditLimitJsonV310.credit_limit.currency)
    }
  }


  feature("Update the credit rating and source of an Customer v3.1.0 - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint3, ApiEndpoint8, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "credit-rating-and-source" ).PUT
      val response310 = makePutRequest(request310, write(putUpdateCustomerCreditRatingAndSourceJsonV310))
      Then("We should get a 401")
      response310.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }
  feature("Update the credit rating and source of an Customer v3.1.0 - Authorized access") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint3, ApiEndpoint8, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "credit-rating-and-source" ).PUT <@(user1)
      val response310 = makePutRequest(request310, write(putUpdateCustomerCreditRatingAndSourceJsonV310))
      Then("We should get a 403")
      response310.code should equal(403)
      val errorMsg = UserHasMissingRoles + canUpdateCustomerCreditRatingAndSource
      And("error should be " + errorMsg)
      response310.body.extract[ErrorMessage].message should equal (errorMsg)
    }
    scenario("We will call the endpoint with user credentials and the proper role", ApiEndpoint8, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateCustomer.toString)
      When("We make a request v3.1.0")
      val postRequest310 = (v3_1_0_Request / "banks" / bankId / "customers").POST <@(user1)
      val postResponse310 = makePostRequest(postRequest310, write(postCustomerJson))
      Then("We should get a 201")
      postResponse310.code should equal(201)
      val infoPost = postResponse310.body.extract[CustomerJsonV310]

      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanUpdateCustomerCreditRatingAndSource.toString)
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / infoPost.customer_id / "credit-rating-and-source" ).PUT <@(user1)
      val response310 = makePutRequest(request310, write(putUpdateCustomerCreditRatingAndSourceJsonV310))
      Then("We should get a 200")
      response310.code should equal(200)

      val infoGet = response310.body.extract[CustomerJsonV310]
      infoGet.credit_rating.map(_.rating).getOrElse("") should equal(putUpdateCustomerCreditRatingAndSourceJsonV310.credit_rating)
      infoGet.credit_rating.map(_.source).getOrElse("") should equal(putUpdateCustomerCreditRatingAndSourceJsonV310.credit_source)
    }
  }


  feature("Update the Branch and source of an Customer v3.1.0 - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint3, ApiEndpoint9, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "branch" ).PUT
      val response310 = makePutRequest(request310, write(putUpdateCustomerBranch))
      Then("We should get a 401")
      response310.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }
  feature("Update the Branch and source of an Customer v3.1.0 - Authorized access") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint3, ApiEndpoint9, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "branch" ).PUT <@(user1)
      val response310 = makePutRequest(request310, write(putUpdateCustomerBranch))
      Then("We should get a 403")
      response310.code should equal(403)
      val errorMsg = UserHasMissingRoles + canUpdateCustomerBranch
      And("error should be " + errorMsg)
      response310.body.extract[ErrorMessage].message should equal (errorMsg)
    }
    scenario("We will call the endpoint with user credentials and the proper role", ApiEndpoint9, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateCustomer.toString)
      When("We make a request v3.1.0")
      val postRequest310 = (v3_1_0_Request / "banks" / bankId / "customers").POST <@(user1)
      val postResponse310 = makePostRequest(postRequest310, write(postCustomerJson))
      Then("We should get a 201")
      postResponse310.code should equal(201)
      val infoPost = postResponse310.body.extract[CustomerJsonV310]

      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanUpdateCustomerBranch.toString)
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / infoPost.customer_id / "branch" ).PUT <@(user1)
      val response310 = makePutRequest(request310, write(putUpdateCustomerBranch))
      Then("We should get a 200")
      response310.code should equal(200)

      val infoGet = response310.body.extract[CustomerJsonV310]
      infoGet.branch_id should equal(putUpdateCustomerBranch.branch_id)
    }
  }


  feature("Update the other data and source of an Customer v3.1.0 - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint3, ApiEndpoint10, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "data" ).PUT
      val response310 = makePutRequest(request310, write(putUpdateCustomerData))
      Then("We should get a 401")
      response310.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }
  feature("Update the other data and source of an Customer v3.1.0 - Authorized access") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint3, ApiEndpoint10, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "data" ).PUT <@(user1)
      val response310 = makePutRequest(request310, write(putUpdateCustomerData))
      Then("We should get a 403")
      response310.code should equal(403)
      val errorMsg = UserHasMissingRoles + canUpdateCustomerData
      And("error should be " + errorMsg)
      response310.body.extract[ErrorMessage].message should equal (errorMsg)
    }
    scenario("We will call the endpoint with user credentials and the proper role", ApiEndpoint10, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateCustomer.toString)
      When("We make a request v3.1.0")
      val postRequest310 = (v3_1_0_Request / "banks" / bankId / "customers").POST <@(user1)
      val postResponse310 = makePostRequest(postRequest310, write(postCustomerJson))
      Then("We should get a 201")
      postResponse310.code should equal(201)
      val infoPost = postResponse310.body.extract[CustomerJsonV310]

      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanUpdateCustomerData.toString)
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / infoPost.customer_id / "data" ).PUT <@(user1)
      val response310 = makePutRequest(request310, write(putUpdateCustomerData))
      Then("We should get a 200")
      response310.code should equal(200)

      val infoGet = response310.body.extract[CustomerJsonV310]
      infoGet.employment_status should equal(putUpdateCustomerData.employment_status)
      infoGet.highest_education_attained should equal(putUpdateCustomerData.highest_education_attained)
      infoGet.dependants should equal(putUpdateCustomerData.dependants)
      infoGet.relationship_status should equal(putUpdateCustomerData.relationship_status)
      infoGet.face_image should equal(putUpdateCustomerData.face_image)
    }
  }

  feature("Update the number of an Customer v3.1.0 - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint11, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "number" ).PUT
      val response310 = makePutRequest(request310, write(putCustomerUpdateNumberJson))
      Then("We should get a 401")
      response310.code should equal(401)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].message should equal (UserNotLoggedIn)
    }
  }
  
  feature("Update the number of an Customer v3.1.0 - Authorized access") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint3, ApiEndpoint11, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "number" ).PUT <@(user1)
      val response310 = makePutRequest(request310, write(putCustomerUpdateNumberJson))
      Then("We should get a 403")
      response310.code should equal(403)
      val errorMsg = UserHasMissingRoles + canUpdateCustomerNumber
      And("error should be " + errorMsg)
      response310.body.extract[ErrorMessage].message should equal (errorMsg)
    }
    scenario("We will call the endpoint with user credentials and the proper role", ApiEndpoint3, ApiEndpoint11, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateCustomer.toString)
      When("We make a request v3.1.0")
      val postRequest310 = (v3_1_0_Request / "banks" / bankId / "customers").POST <@(user1)
      val postResponse310 = makePostRequest(postRequest310, write(postCustomerJson))
      Then("We should get a 201")
      postResponse310.code should equal(201)
      val infoPost = postResponse310.body.extract[CustomerJsonV310]

      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanUpdateCustomerNumber.toString)

      When("We make a request with the exsiting customer number")
      val existingCustomerNumberJson = putCustomerUpdateNumberJson.copy(customer_number = infoPost.customer_number)
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / infoPost.customer_id / "number" ).PUT <@(user1)
      val responseExistingNumber310 = makePutRequest(request310, write(existingCustomerNumberJson))
      Then("We should get a 200")
      responseExistingNumber310.code should equal(400)
      responseExistingNumber310.body.extract[ErrorMessage].message contains (CustomerNumberAlreadyExists) should be (true)
      
      When("We make a request v3.1.0")
      val response310 = makePutRequest(request310, write(putCustomerUpdateNumberJson))
      Then("We should get a 200")
      response310.code should equal(200)

      val infoGet = response310.body.extract[CustomerJsonV310]
      infoGet.customer_number should equal(putCustomerUpdateNumberJson.customer_number)

      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetCustomer.toString)
      When("We make a request v3.1.0 with the Role " + canGetCustomer + " but with non existing CUSTOMER_ID")
      val requestGetById310 = (v3_1_0_Request / "banks" / bankId / "customers" / infoPost.customer_id).GET <@(user1)
      val responseGetByI310 = makeGetRequest(requestGetById310)
      Then("We should get a 200")
      responseGetByI310.code should equal(200)
      responseGetByI310.body.extract[CustomerJsonV310].customer_number should equal(putCustomerUpdateNumberJson.customer_number)
    }
  }

}
