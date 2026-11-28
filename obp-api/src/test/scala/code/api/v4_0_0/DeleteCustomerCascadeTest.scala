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
import code.api.util.{APIUtil, DoobieUtil}
import code.api.util.APIUtil.OAuth._
import org.json4s.jvalue2extractable
import code.api.util.ApiRole
import code.api.util.ApiRole.{CanDeleteCustomerCascade, CanDeleteTransactionCascade}
import code.api.util.APIUtil.extractErrorMessageCode
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, CustomerNotFoundByCustomerId, UserHasMissingRoles}
import code.api.v4_0_0.Http4s400.Implementations4_0_0
import code.customer.{MappedCustomer, MappedCustomerMessage}
import code.crm.DoobieCrmEventProvider
import code.customerlinks.DoobieCustomerLinkProvider
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import doobie.Fragment
import doobie.implicits._
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
    MappedCustomer.findByCustomerId(customerId).openOrThrowException("expected the customer").customerPrimaryKey

  private def countRows(query: Fragment): Long = DoobieUtil.runQuery(query.query[Long].unique)

  private def crmEvent(atBank: String, number: String): Unit = {
    DoobieCrmEventProvider.createEvent(bankId = atBank, crmEventId = APIUtil.generateUUID(), category = "call",
      detail = "", channel = "", actualDate = new Date(), customerName = "name", customerNumber = number,
      userIdPrimaryKey = resourceUser1.userPrimaryKey.value)
    ()
  }

  private def crmEvents(atBank: String, number: String): Long =
    countRows(sql"SELECT COUNT(*) FROM mappedcrmevent WHERE mbankid = ${Option(atBank)} AND mcustomernumber = ${Option(number)}")

  private def messages(customerPk: Long): Long =
    countRows(sql"SELECT COUNT(*) FROM mappedcustomermessage WHERE customer = $customerPk")

  private def dependants(customerPk: Long): Long =
    countRows(sql"SELECT COUNT(*) FROM mappedcustomerdependant WHERE mcustomer = $customerPk")

  private def grantCascadeRole(): Unit =
    if (!APIUtil.hasEntitlement(bankId, resourceUser1.userId, ApiRole.canDeleteCustomerCascade))
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.canDeleteCustomerCascade.toString)

  private def cascadeDelete(customerId: String) = {
    grantCascadeRole()
    makeDeleteRequest((v4_0_0_Request / "management" / "cascading" / "banks" / bankId / "customers" / customerId).DELETE <@(user1))
  }

  Feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    Scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "cascading" / "banks" / bankId / 
        "customers" / "CUSTOMER_ID" ).DELETE
      val response400 = makeDeleteRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }
  }
  Feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access") {
    Scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
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
  Feature(s"test $ApiEndpoint1 - Authorized access") {
    Scenario("We will call the endpoint with user credentials", ApiEndpoint1, VersionOfApi) {
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
        DoobieCustomerLinkProvider.createCustomerLink(bankId, from, bankId, to, "RELATED").openOrThrowException("expected the link")
      link(first, second)  // the deleted customer is the first side
      link(third, first)   // the deleted customer is the other side
      link(second, third)  // unrelated to the deleted customer
      def linksOf(id: String) =
        countRows(sql"SELECT COUNT(*) FROM customerlink WHERE customerid = ${Option(id)}") +
          countRows(sql"SELECT COUNT(*) FROM customerlink WHERE othercustomerid = ${Option(id)}")

      linksOf(first) should equal(2)

      cascadeDelete(first).code should equal(200)

      linksOf(first) should equal(0)
      countRows(sql"SELECT COUNT(*) FROM customerlink WHERE customerid = ${Option(second)} AND othercustomerid = ${Option(third)}") should equal(1)
    }
  }

  feature(s"test $ApiEndpoint1 - the bank in the URL") {
    scenario("A customer of another bank is not found and is kept", ApiEndpoint1, VersionOfApi) {
      val customerId = createAndGetCustomerIdViaEndpoint(bankId, resourceUser1.userId)
      val otherBankId = createBank(APIUtil.generateUUID()).bankId.value
      Entitlement.entitlement.vend.addEntitlement(otherBankId, resourceUser1.userId, ApiRole.canDeleteCustomerCascade.toString)

      val wrongBank = makeDeleteRequest((v4_0_0_Request / "management" / "cascading" / "banks" / otherBankId / "customers" / customerId).DELETE <@(user1))

      wrongBank.code should equal(404)
      wrongBank.body.extract[ErrorMessage].message should include(extractErrorMessageCode(CustomerNotFoundByCustomerId))
      MappedCustomer.findByCustomerId(customerId).isDefined should equal(true)

      cascadeDelete(customerId).code should equal(200)
      MappedCustomer.findByCustomerId(customerId).isDefined should equal(false)
    }
  }

  feature(s"test $ApiEndpoint1 - rows that name the customer by its number") {
    scenario("CRM events of the deleted customer go, those of another customer stay", ApiEndpoint1, VersionOfApi) {
      val customerId = createAndGetCustomerIdViaEndpoint(bankId, resourceUser1.userId)
      val otherCustomerId = createAndGetCustomerIdViaEndpoint(bankId, resourceUser1.userId)
      def numberOf(id: String) = MappedCustomer.findByCustomerId(id).openOrThrowException("expected the customer").number
      def addEvent(number: String, atBank: String): Unit =
        crmEvent(atBank, number)
      def events(number: String, atBank: String) = crmEvents(atBank, number)
      val number = numberOf(customerId)
      val otherNumber = numberOf(otherCustomerId)
      addEvent(number, bankId)
      addEvent(otherNumber, bankId)
      events(number, bankId) should equal(1)

      cascadeDelete(customerId).code should equal(200)

      events(number, bankId) should equal(0)
      events(otherNumber, bankId) should equal(1)
    }

    scenario("A CRM event with the same customer number at another bank stays", ApiEndpoint1, VersionOfApi) {
      val customerId = createAndGetCustomerIdViaEndpoint(bankId, resourceUser1.userId)
      val number = MappedCustomer.findByCustomerId(customerId).openOrThrowException("expected the customer").number
      crmEvent("another-bank", number)

      cascadeDelete(customerId).code should equal(200)

      crmEvents("another-bank", number) should equal(1)
    }
  }

  feature(s"test $ApiEndpoint1 - rows that point at the customer") {
    scenario("Messages and dependants of the deleted customer go, those of another customer stay", ApiEndpoint1, VersionOfApi) {
      val customerId = createAndGetCustomerIdViaEndpoint(bankId, resourceUser1.userId)
      val otherCustomerId = createAndGetCustomerIdViaEndpoint(bankId, resourceUser1.userId)
      val pk = customerPrimaryKey(customerId)
      val otherPk = customerPrimaryKey(otherCustomerId)
      def addRows(customerPk: Long): Unit = {
        MappedCustomerMessage.insertForCustomer(customerPk, bankId, "sms", "hello", "department", "person")
        MappedCustomerDependant.insert(customerPk, new Date())
      }
      addRows(pk)
      addRows(otherPk)
      // Creating a customer through the endpoint already adds dependants, so count against what was there.
      val otherDependantsBefore = dependants(otherPk)
      messages(pk) should equal(1)
      dependants(pk) should be >= 1L

      cascadeDelete(customerId).code should equal(200)

      messages(pk) should equal(0)
      dependants(pk) should equal(0)
      messages(otherPk) should equal(1)
      dependants(otherPk) should equal(otherDependantsBefore)
    }
  }

}
