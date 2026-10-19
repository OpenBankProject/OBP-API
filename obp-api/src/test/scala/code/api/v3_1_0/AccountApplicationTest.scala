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
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)
  */
package code.api.v3_1_0

import java.util.Date

import org.json4s._
import code.accountapplication.AccountApplicationX
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanGetAccountApplications
import code.api.util.ErrorMessages.{AccountApplicationNotFound, UserHasMissingRoles}
import code.api.v3_1_0.Http4s310.Implementations3_1_0
import code.customer.CustomerX
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{BankId, CustomerFaceImage, ErrorMessage, ProductCode}
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

import scala.concurrent.Await
import scala.concurrent.duration._

class AccountApplicationTest extends V310ServerSetup {

  override def afterAll(): Unit = {
    super.afterAll()
    CustomerX.customerProvider.vend.bulkDeleteCustomers()
  }

  object VersionOfApi extends Tag(ApiVersion.v3_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations3_1_0.getAccountApplication))

  private def createCustomerAt(bankId: String): String =
    CustomerX.customerProvider.vend.addCustomer(
      BankId(bankId), java.util.UUID.randomUUID().toString, "Test Customer", "+49 123 456", "test-customer@example.com",
      CustomerFaceImage(new Date(), "http://example.com/face.png"),
      new Date(), "single", 0, Nil, "Bachelor", "employed", true, new Date(),
      None, None, "", "", ""
    ).openOrThrowException("expected the customer to be created").customerId

  private def createApplication(userId: String, customerId: String): String =
    Await.result(
      AccountApplicationX.accountApplication.vend.createAccountApplication(ProductCode("1234BW"), Some(userId), Some(customerId)),
      10.seconds
    ).openOrThrowException("expected the account application to be created").accountApplicationId

  feature("Get Account Application by Id v3.1.0") {
    scenario("Another User without the Role cannot read a User's application", ApiEndpoint1, VersionOfApi) {
      val bankId = testBankId1.value
      val applicationId = createApplication(resourceUser1.userId, createCustomerAt(bankId))
      val request = (v3_1_0_Request / "banks" / bankId / "account-applications" / applicationId).GET

      When("The applicant reads it")
      Then("We should get a 200")
      makeGetRequest(request <@(user1)).code should equal(200)

      When("Another User without CanGetAccountApplications reads it")
      val response = makeGetRequest(request <@(user2))
      Then("We should get a 403")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should equal(s"$UserHasMissingRoles$CanGetAccountApplications")

      When("That User has CanGetAccountApplications")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser2.userId, CanGetAccountApplications.toString)
      Then("We should get a 200")
      makeGetRequest(request <@(user2)).code should equal(200)
    }

    scenario("An application whose customer is at another bank is not found at the bank in the path", ApiEndpoint1, VersionOfApi) {
      val applicationId = createApplication(resourceUser1.userId, createCustomerAt(testBankId2.value))
      When("The applicant reads it with another bank in the path")
      val response = makeGetRequest((v3_1_0_Request / "banks" / testBankId1.value / "account-applications" / applicationId).GET <@(user1))
      Then("We should get a 404")
      response.code should equal(404)
      response.body.extract[ErrorMessage].message should equal(AccountApplicationNotFound)
    }
  }

}
