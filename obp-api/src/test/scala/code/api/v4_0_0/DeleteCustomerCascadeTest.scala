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

import code.CustomerDependants.MappedCustomerDependant
import code.api.util.APIUtil
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ApiRole.{CanDeleteCustomerCascade, CanDeleteTransactionCascade}
import code.api.util.ErrorMessages.{UserHasMissingRoles, AuthenticatedUserIsRequired}
import code.api.v4_0_0.Http4s400.Implementations4_0_0
import code.customer.{MappedCustomer, MappedCustomerMessage}
import code.crm.MappedCrmEvent
import code.customerlinks.{CustomerLink, MappedCustomerLinkProvider}
import code.socialmedia.MappedSocialMedia
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.mapper.By
import org.scalatest.Tag

import java.util.Date

class DeleteCustomerCascadeTest extends V400ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * mvn test -D tagsToInclude
    *
    * This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)

  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.deleteCustomerCascade))

  lazy val bankId = randomBankId
  lazy val bankAccount = randomPrivateAccountViaEndpoint(bankId)

  private def customerPrimaryKey(customerId: String): Long =
    MappedCustomer.find(By(MappedCustomer.mCustomerId, customerId)).openOrThrowException("expected the customer").id.get

  private def grantCascadeRole(): Unit =
    if (!APIUtil.hasEntitlement(bankId, resourceUser1.userId, ApiRole.canDeleteCustomerCascade))
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.canDeleteCustomerCascade.toString)

  private def cascadeDelete(customerId: String) = {
    grantCascadeRole()
    makeDeleteRequest((v4_0_0_Request / "management" / "cascading" / "banks" / bankId / "customers" / customerId).DELETE <@(user1))
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "cascading" / "banks" / bankId / 
        "customers" / "CUSTOMER_ID" ).DELETE
      val response400 = makeDeleteRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }
  }
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "cascading" / "banks" / bankId /
        "customers" / "CUSTOMER_ID" ).DELETE <@(user1)
      val response400 = makeDeleteRequest(request400)
      Then("We should get a 403")
      response400.code should equal(403)
      val errorMessage = response400.body.extract[ErrorMessage].message
      errorMessage contains (UserHasMissingRoles) should be (true) 
      errorMessage contains (CanDeleteCustomerCascade.toString()) should be (true) 
    }
  }
  feature(s"test $ApiEndpoint1 - Authorized access") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint1, VersionOfApi) {
      val customerId = createAndGetCustomerIdViaEndpoint(bankId, resourceUser1.userId)

      Then("we create the Customer Attribute")
      createAndGetCustomerAttributeIdViaEndpoint(bankId:String, customerId:String, user1)

      When("We make a request v4.0.0")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.canDeleteCustomerCascade.toString)
      val request400 = (v4_0_0_Request / "management" / "cascading" / "banks" / bankId /
        "customers" / customerId ).DELETE <@(user1)
      val response400 = makeDeleteRequest(request400)
      Then("We should get a 200")
      response400.code should equal(200)

      When("We try to delete one more time we should get 404")
      makeDeleteRequest(request400).code should equal(404)
    }
  }  

  feature(s"test $ApiEndpoint1 - rows that name the customer by its id") {
    scenario("A link between customers goes when either customer is deleted, links of others stay", ApiEndpoint1, VersionOfApi) {
      val first = createAndGetCustomerIdViaEndpoint(bankId, resourceUser1.userId)
      val second = createAndGetCustomerIdViaEndpoint(bankId, resourceUser1.userId)
      val third = createAndGetCustomerIdViaEndpoint(bankId, resourceUser1.userId)
      def link(from: String, to: String) =
        MappedCustomerLinkProvider.createCustomerLink(bankId, from, bankId, to, "RELATED").openOrThrowException("expected the link")
      link(first, second)  // the deleted customer is the first side
      link(third, first)   // the deleted customer is the other side
      link(second, third)  // unrelated to the deleted customer
      def linksOf(id: String) =
        CustomerLink.count(By(CustomerLink.CustomerId, id)) + CustomerLink.count(By(CustomerLink.OtherCustomerId, id))

      linksOf(first) should equal(2)

      cascadeDelete(first).code should equal(200)

      linksOf(first) should equal(0)
      CustomerLink.count(By(CustomerLink.CustomerId, second), By(CustomerLink.OtherCustomerId, third)) should equal(1)
    }
  }

  feature(s"test $ApiEndpoint1 - rows that name the customer by its number") {
    scenario("Social media and CRM events of the deleted customer go, those of another customer stay", ApiEndpoint1, VersionOfApi) {
      val customerId = createAndGetCustomerIdViaEndpoint(bankId, resourceUser1.userId)
      val otherCustomerId = createAndGetCustomerIdViaEndpoint(bankId, resourceUser1.userId)
      def numberOf(id: String) = MappedCustomer.find(By(MappedCustomer.mCustomerId, id)).openOrThrowException("expected the customer").mNumber.get
      def addRows(number: String): Unit = {
        MappedSocialMedia.create.mCustomerNumber(number).mType("twitter").mHandle("@" + number).saveMe()
        MappedCrmEvent.create.mBankId(bankId).mCustomerNumber(number).mCustomerName("name").mCategory("call")
          .mUserId(resourceUser1).saveMe()
      }
      def rows(number: String) = (
        MappedSocialMedia.count(By(MappedSocialMedia.mCustomerNumber, number)),
        MappedCrmEvent.count(By(MappedCrmEvent.mBankId, bankId), By(MappedCrmEvent.mCustomerNumber, number)))
      val number = numberOf(customerId)
      val otherNumber = numberOf(otherCustomerId)
      addRows(number)
      addRows(otherNumber)
      rows(number) should equal((1, 1))

      cascadeDelete(customerId).code should equal(200)

      rows(number) should equal((0, 0))
      rows(otherNumber) should equal((1, 1))
    }

    scenario("Social media is kept when a customer of another bank has the same number", ApiEndpoint1, VersionOfApi) {
      val customerId = createAndGetCustomerIdViaEndpoint(bankId, resourceUser1.userId)
      val number = MappedCustomer.find(By(MappedCustomer.mCustomerId, customerId)).openOrThrowException("expected the customer").mNumber.get
      // The same number at another bank: social media does not record the bank, so the rows cannot be told apart.
      MappedCustomer.create.mBank("another-bank").mNumber(number).mCustomerId(APIUtil.generateUUID()).saveMe()
      MappedSocialMedia.create.mCustomerNumber(number).mType("twitter").mHandle("@shared").saveMe()
      MappedCrmEvent.create.mBankId(bankId).mCustomerNumber(number).mCustomerName("name").mCategory("call")
        .mUserId(resourceUser1).saveMe()

      cascadeDelete(customerId).code should equal(200)

      MappedSocialMedia.count(By(MappedSocialMedia.mCustomerNumber, number)) should equal(1)
      MappedCrmEvent.count(By(MappedCrmEvent.mBankId, bankId), By(MappedCrmEvent.mCustomerNumber, number)) should equal(0)
    }
  }

  feature(s"test $ApiEndpoint1 - rows that point at the customer") {
    scenario("Messages and dependants of the deleted customer go, those of another customer stay", ApiEndpoint1, VersionOfApi) {
      val customerId = createAndGetCustomerIdViaEndpoint(bankId, resourceUser1.userId)
      val otherCustomerId = createAndGetCustomerIdViaEndpoint(bankId, resourceUser1.userId)
      val pk = customerPrimaryKey(customerId)
      val otherPk = customerPrimaryKey(otherCustomerId)
      def addRows(customerPk: Long): Unit = {
        MappedCustomerMessage.create.customer(customerPk).bank(bankId).mMessage("hello")
          .mFromPerson("person").mFromDepartment("department").mTransport("sms").saveMe()
        MappedCustomerDependant.create.mCustomer(customerPk).mDateOfBirth(new Date()).saveMe()
      }
      addRows(pk)
      addRows(otherPk)
      // Creating a customer through the endpoint already adds dependants, so count against what was there.
      val otherDependantsBefore = MappedCustomerDependant.count(By(MappedCustomerDependant.mCustomer, otherPk))
      MappedCustomerMessage.count(By(MappedCustomerMessage.customer, pk)) should equal(1)
      MappedCustomerDependant.count(By(MappedCustomerDependant.mCustomer, pk)) should be >= 1L

      cascadeDelete(customerId).code should equal(200)

      MappedCustomerMessage.count(By(MappedCustomerMessage.customer, pk)) should equal(0)
      MappedCustomerDependant.count(By(MappedCustomerDependant.mCustomer, pk)) should equal(0)
      MappedCustomerMessage.count(By(MappedCustomerMessage.customer, otherPk)) should equal(1)
      MappedCustomerDependant.count(By(MappedCustomerDependant.mCustomer, otherPk)) should equal(otherDependantsBefore)
    }
  }

}
