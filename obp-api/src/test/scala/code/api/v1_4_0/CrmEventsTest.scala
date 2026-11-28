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

package code.api.v1_4_0

import java.util.Date

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanGetCrmEvents
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, UserHasMissingRoles}
import code.crm.DoobieCrmEventProvider
import code.entitlement.Entitlement
import code.setup.{APIResponse, DefaultUsers}
import org.json4s._

/**
 * This suite checks that getCrmEvents, which returns every CRM event at a bank for all customers, needs an
 * authenticated User with the Role CanGetCrmEvents at that bank, and returns only that bank's events.
 */
class CrmEventsTest extends V140ServerSetup with DefaultUsers {

  override def beforeEach(): Unit = {
    super.beforeEach()
    DoobieCrmEventProvider.bulkDelete()
  }

  override def afterEach(): Unit = {
    super.afterEach()
    DoobieCrmEventProvider.bulkDelete()
  }

  private def crmEventsAt(bankId: String) = (v1_4Request / "banks" / bankId / "crm-events").GET

  private def messageOf(response: APIResponse): String = (response.body \ "message").extractOpt[String].getOrElse("")

  private def createCrmEvent(crmEventId: String, bankId: String, customerName: String) =
    DoobieCrmEventProvider.createEvent(
      bankId = bankId,
      crmEventId = crmEventId,
      category = "Category X",
      detail = "Call about mortgage",
      channel = "PHONE",
      actualDate = new Date(12340000),
      customerName = customerName,
      customerNumber = "1234",
      userIdPrimaryKey = resourceUser2.id,
      scheduledDate = new Date(12340000),
      result = "No answer")

  feature("getCrmEvents needs authentication and the Role CanGetCrmEvents") {

    scenario("an anonymous caller is refused with 401") {
      createCrmEvent("CRM-EVENT-1", testBankId1.value, "Customer One")
      val response = makeGetRequest(crmEventsAt(testBankId1.value))
      response.code should equal(401)
      messageOf(response) should include(AuthenticatedUserIsRequired)
    }

    scenario("a User without the Role is refused with 403") {
      createCrmEvent("CRM-EVENT-1", testBankId1.value, "Customer One")
      val response = makeGetRequest(crmEventsAt(testBankId1.value) <@ (user1))
      response.code should equal(403)
      messageOf(response) should include(UserHasMissingRoles)
      messageOf(response) should include(CanGetCrmEvents.toString)
    }

    scenario("a User with the Role at another bank is refused with 403") {
      Entitlement.entitlement.vend.addEntitlement(testBankId2.value, resourceUser1.userId, CanGetCrmEvents.toString)
      val response = makeGetRequest(crmEventsAt(testBankId1.value) <@ (user1))
      response.code should equal(403)
    }

    scenario("a User with the Role at the bank gets that bank's events only") {
      createCrmEvent("CRM-EVENT-1", testBankId1.value, "Customer One")
      createCrmEvent("CRM-EVENT-2", testBankId2.value, "Customer Two")
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, CanGetCrmEvents.toString)
      val response = makeGetRequest(crmEventsAt(testBankId1.value) <@ (user1))
      response.code should equal(200)
      val events = (response.body \ "crm_events").children
      events.map(event => (event \ "id").extract[String]) should equal(List("CRM-EVENT-1"))
      (events.head \ "customer_name").extract[String] should equal("Customer One")
    }
  }
}
