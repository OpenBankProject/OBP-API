/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd

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
TESOBE Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)
  */
package code.api.v3_1_0

import code.api.ErrorMessage
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.ApiVersion
import code.api.util.ErrorMessages._
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class TaxResidenceTest extends V310ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v3_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations3_1_0.createTaxResidence))
  object ApiEndpoint2 extends Tag(nameOf(Implementations3_1_0.getTaxResidence))
  object ApiEndpoint3 extends Tag(nameOf(Implementations3_1_0.deleteTaxResidence))

  val postTaxResidenceJson = SwaggerDefinitionsJSON.postTaxResidenceJsonV310
  val postCustomerJson = SwaggerDefinitionsJSON.postCustomerJsonV310
  lazy val bankId = randomBankId

  feature("Add the Tax Residence of the Customer specified by a CUSTOMER_ID v3.1.0 - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "tax-residence").POST
      val response310 = makePostRequest(request310, write(postTaxResidenceJson))
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].error should equal (UserNotLoggedIn)
    }
  }
  feature("Get the Tax Residence of the Customer specified by  CUSTOMER_ID v3.1.0 - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "tax-residence").GET
      val response310 = makeGetRequest(request310)
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].error should equal (UserNotLoggedIn)
    }
  }
  feature("Delete the Tax Residence of the Customer specified by a TAX_RESIDENCE_ID v3.1.0 - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "tax_residencies" / "TAX_RESIDENCE_ID").DELETE
      val response310 = makeDeleteRequest(request310)
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].error should equal (UserNotLoggedIn)
    }
  }


  feature("Add the Tax Residence of the Customer specified by a CUSTOMER_ID v3.1.0 - Authorized access") {
    scenario("We will call the endpoint without the proper Role " + canCreateTaxResidence, ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0 without a Role " + canCreateTaxResidence)
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "tax-residence").POST <@(user1)
      val response310 = makePostRequest(request310, write(postTaxResidenceJson))
      Then("We should get a 403")
      response310.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanCreateTaxResidence)
      response310.body.extract[ErrorMessage].error should equal (UserHasMissingRoles + CanCreateTaxResidence)
    }
    scenario("We will call the endpoint with the proper Role " + canCreateTaxResidence, ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateTaxResidence.toString)
      When("We make a request v3.1.0 with the Role " + canCreateTaxResidence + " but with non existing CUSTOMER_ID")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "tax-residence").POST <@(user1)
      val response310 = makePostRequest(request310, write(postTaxResidenceJson))
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + CustomerNotFoundByCustomerId)
      response310.body.extract[ErrorMessage].error should startWith (CustomerNotFoundByCustomerId)
    }
    scenario("We will call the endpoint with the proper Role " + canCreateTaxResidence + " and an existing customer", ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateCustomer.toString)
      When("We make a request v3.1.0")
      val requestCustomer310 = (v3_1_0_Request / "banks" / bankId / "customers").POST <@(user1)
      val responseCustomer310 = makePostRequest(requestCustomer310, write(postCustomerJson))
      Then("We should get a 200")
      responseCustomer310.code should equal(200)
      val customerJson = responseCustomer310.body.extract[CustomerJsonV310]

      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateTaxResidence.toString)
      When("We make a request v3.1.0 with the Role " + canCreateTaxResidence)
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / customerJson.customer_id / "tax-residence").POST <@(user1)
      val response310 = makePostRequest(request310, write(postTaxResidenceJson))
      Then("We should get a 200")
      response310.code should equal(200)
      val taxResidenceJson = response310.body.extract[TaxResidenceJsonV310]
      org.scalameta.logger.elem(taxResidenceJson)

      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetTaxResidence.toString)
      When("We make a request v3.1.0 with the Role " + canGetTaxResidence)
      val requestGet310 = (v3_1_0_Request / "banks" / bankId / "customers" / customerJson.customer_id / "tax-residence").GET <@(user1)
      val responseGet310 = makeGetRequest(requestGet310)
      Then("We should get a 200")
      responseGet310.code should equal(200)
      val taxResidenceGetJson = responseGet310.body.extract[TaxResidenceJsonV310]

      val createList = taxResidenceJson.tax_residence.map(_.tax_residence_id)
      val getList = taxResidenceGetJson.tax_residence.map(_.tax_residence_id)

      getList should contain theSameElementsAs (createList)

      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanDeleteTaxResidence.toString)
      When("We make a request v3.1.0 with the Role " + canDeleteTaxResidence + " but with non existing CUSTOMER_ID")
      val requestDelete310 = (v3_1_0_Request / "banks" / bankId / "customers" / customerJson.customer_id / "tax_residencies" / createList.headOption.getOrElse("")).DELETE <@(user1)
      val responseDelete310 = makeDeleteRequest(requestDelete310)
      Then("We should get a 200")
      responseDelete310.code should equal(200)

    }
  }


  feature("Get the Tax Residence of the Customer specified by  CUSTOMER_ID v3.1.0 - Authorized access") {
    scenario("We will call the endpoint without the proper Role " + canGetTaxResidence, ApiEndpoint2, VersionOfApi) {
      When("We make a request v3.1.0 without a Role " + canGetTaxResidence)
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "tax-residence").GET <@(user1)
      val response310 = makeGetRequest(request310)
      Then("We should get a 403")
      response310.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanGetTaxResidence)
      response310.body.extract[ErrorMessage].error should equal (UserHasMissingRoles + CanGetTaxResidence)
    }
    scenario("We will call the endpoint with the proper Role " + canGetTaxResidence, ApiEndpoint2, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetTaxResidence.toString)
      When("We make a request v3.1.0 with the Role " + canGetTaxResidence + " but with non existing CUSTOMER_ID")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "tax-residence").GET <@(user1)
      val response310 = makeGetRequest(request310)
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + CustomerNotFoundByCustomerId)
      response310.body.extract[ErrorMessage].error should startWith (CustomerNotFoundByCustomerId)
    }
  }


  feature("Delete the Tax Residence of the Customer specified by CUSTOMER_ID and TAX_RESIDENCE_ID v3.1.0 - Authorized access") {
    scenario("We will call the endpoint without the proper Role " + canDeleteTaxResidence, ApiEndpoint3, VersionOfApi) {
      When("We make a request v3.1.0 without a Role " + canDeleteTaxResidence)
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "tax_residencies" / "TAX_RESIDENCE_ID").DELETE <@(user1)
      val response310 = makeDeleteRequest(request310)
      Then("We should get a 403")
      response310.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanDeleteTaxResidence)
      response310.body.extract[ErrorMessage].error should equal (UserHasMissingRoles + CanDeleteTaxResidence)
    }
    scenario("We will call the endpoint with the proper Role " + canDeleteTaxResidence, ApiEndpoint3, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanDeleteTaxResidence.toString)
      When("We make a request v3.1.0 with the Role " + canDeleteTaxResidence + " but with non existing CUSTOMER_ID")
      val request310 = (v3_1_0_Request / "banks" / bankId / "customers" / "CUSTOMER_ID" / "tax_residencies" / "TAX_RESIDENCE_ID").DELETE <@(user1)
      val response310 = makeDeleteRequest(request310)
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + CustomerNotFoundByCustomerId)
      response310.body.extract[ErrorMessage].error should startWith (CustomerNotFoundByCustomerId)
    }
  }


}
