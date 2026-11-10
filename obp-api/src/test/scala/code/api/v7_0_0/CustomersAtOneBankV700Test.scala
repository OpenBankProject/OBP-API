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
package code.api.v7_0_0

import java.util.Date

import code.api.util.APIUtil.OAuth._
import code.api.util.APIUtil.generateUUID
import code.api.util.ApiRole.CanGetCustomersAtOneBank
import code.api.util.ErrorMessages._
import code.api.v6_0_0.V600ServerSetup
import code.customer.CustomerX
import code.customerattribute.CustomerAttributeX
import code.entitlement.Entitlement
import com.openbankproject.commons.model.enums.CustomerAttributeType
import com.openbankproject.commons.model.{BankId, CustomerFaceImage, CustomerId, ErrorMessage}
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

import scala.concurrent.Await
import scala.concurrent.duration._

class CustomersAtOneBankV700Test extends V600ServerSetup {

  def v7 = baseRequest / "obp" / "v7.0.0"
  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object CustomersAtOneBank extends Tag("getCustomersAtOneBank")

  private lazy val bankId = testBankId1.value

  private def withRole[T](f: => T): T = {
    val grant = Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetCustomersAtOneBank.toString)
    try f finally Entitlement.entitlement.vend.deleteEntitlement(grant)
  }

  /** A fresh Customer of the test bank with the given attributes; returns its id. */
  private def customer(attributes: (String, String)*): String = customerAt(bankId, attributes: _*)

  private def customerAt(bank: String, attributes: (String, String)*): String = {
    val created = CustomerX.customerProvider.vend.addCustomer(
      BankId(bank), generateUUID().take(12), s"Organisation ${generateUUID().take(6)}", "+968 1234 5678", "org@example.com",
      CustomerFaceImage(new Date(), "http://example.com/face.png"),
      new Date(), "single", 0, Nil, "Bachelor", "employed", false, new Date(),
      None, None, "", "", "", "CORPORATE", ""
    ).openOrThrowException("expected the customer to be created")
    attributes.foreach { case (name, value) =>
      Await.result(CustomerAttributeX.customerAttributeProvider.vend.createOrUpdateCustomerAttribute(
        BankId(bank), CustomerId(created.customerId), None, name, CustomerAttributeType.STRING, value), 10.seconds)
        .openOrThrowException("expected the attribute to be created")
    }
    created.customerId
  }

  /** Query parameters go into the URL; a name may repeat. */
  private def get(query: (String, String)*) = {
    val request = query.foldLeft((v7 / "banks" / bankId / "customers").GET) { case (r, (k, v)) => r.addQueryParameter(k, v) }
    makeGetRequest(request <@ (user1))
  }

  private def ids(query: (String, String)*): List[String] = {
    val response = withRole { get(query: _*) }
    withClue(response.body) { response.code should equal(200) }
    response.body.extract[CustomersWithAttributesJsonV700].customers.map(_.customer_id)
  }

  feature(s"Get Customers at Bank, filtered by Customer Attributes - GET /banks/BANK_ID/customers - $VersionOfApi") {

    scenario("Customers of another bank are never listed, and the Role is needed at this bank", CustomersAtOneBank, VersionOfApi) {
      val tag = generateUUID().take(6)
      val here = customer("organisation_kind" -> s"university-$tag")
      customerAt(testBankId2.value, "organisation_kind" -> s"university-$tag")
      ids("organisation_kind" -> s"university-$tag") should equal(List(here))

      val grantAtOtherBank = Entitlement.entitlement.vend.addEntitlement(testBankId2.value, resourceUser1.userId, CanGetCustomersAtOneBank.toString)
      val response = try get("organisation_kind" -> s"university-$tag") finally Entitlement.entitlement.vend.deleteEntitlement(grantAtOtherBank)
      response.code should equal(403)
    }

    scenario("An attribute parameter without a value is refused", CustomersAtOneBank, VersionOfApi) {
      Seq("", ",", " , ").foreach { value =>
        val response = withRole { get("organisation_kind" -> value) }
        withClue(s"value '$value': ${response.body}") { response.code should equal(400) }
        response.body.extract[ErrorMessage].message should include(InvalidUrlParameters)
      }
    }

    scenario("Other OBP query parameters are not taken for attribute names", CustomersAtOneBank, VersionOfApi) {
      val tag = generateUUID().take(6)
      val matching = customer("organisation_kind" -> s"bank-$tag")
      ids("organisation_kind" -> s"bank-$tag", "from_date" -> "2020-01-01T00:00:00.000Z", "to_date" -> "2099-01-01T00:00:00.000Z",
        "sort_by" -> "name", "obp_limit" -> "10", "_" -> "123") should equal(List(matching))
    }

    scenario("sort_direction orders the matching Customers by customer_id", CustomersAtOneBank, VersionOfApi) {
      val tag = generateUUID().take(6)
      val created = (1 to 3).map(_ => customer("cohort" -> s"s-$tag")).toList
      ids("cohort" -> s"s-$tag", "sort_direction" -> "ASC") should equal(created.sorted)
      ids("cohort" -> s"s-$tag", "sort_direction" -> "DESC") should equal(created.sorted.reverse)
    }

    scenario("Without attribute parameters limit and offset still page the list", CustomersAtOneBank, VersionOfApi) {
      customer("cohort" -> s"p-${generateUUID().take(6)}")
      customer("cohort" -> s"p-${generateUUID().take(6)}")
      ids("limit" -> "1") should have size 1
      ids("limit" -> "1", "offset" -> "1") should have size 1
    }

    scenario("It needs the Role", CustomersAtOneBank, VersionOfApi) {
      val response = get()
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should include(UserHasMissingRoles)
    }

    scenario("Without attribute parameters it lists the bank's Customers with their attributes", CustomersAtOneBank, VersionOfApi) {
      val kind = s"university-${generateUUID().take(6)}"
      val customerId = customer("organisation_kind" -> kind)
      val response = withRole { get("limit" -> "1000") }
      withClue(response.body) { response.code should equal(200) }
      And("it is served by v7.0.0 itself, not passed on to v6.0.0")
      response.headers.flatMap(h => Option(h.get("X-OBP-Version-Served"))) should equal(None)
      val listed = response.body.extract[CustomersWithAttributesJsonV700].customers.find(_.customer_id == customerId)
      listed.map(_.customer_attributes.map(a => a.name -> a.value)) should equal(Some(List("organisation_kind" -> kind)))
    }

    scenario("An attribute parameter returns only the Customers that have that value", CustomersAtOneBank, VersionOfApi) {
      val tag = generateUUID().take(6)
      val university = customer("organisation_kind" -> s"university-$tag")
      customer("organisation_kind" -> s"regulator-$tag")
      customer()
      ids("organisation_kind" -> s"university-$tag") should equal(List(university))
      ids("organisation_kind" -> s"nothing-$tag") should equal(Nil)
    }

    scenario("Comma-separated values match any of them, different names must all match", CustomersAtOneBank, VersionOfApi) {
      val tag = generateUUID().take(6)
      val university = customer("organisation_kind" -> s"university-$tag", "city" -> s"muscat-$tag")
      val regulator = customer("organisation_kind" -> s"regulator-$tag", "city" -> s"sohar-$tag")
      customer("organisation_kind" -> s"partner-$tag", "city" -> s"muscat-$tag")

      ids("organisation_kind" -> s"university-$tag,regulator-$tag").toSet should equal(Set(university, regulator))
      ids("organisation_kind" -> s"university-$tag", "city" -> s"muscat-$tag") should equal(List(university))
      ids("organisation_kind" -> s"regulator-$tag", "city" -> s"muscat-$tag") should equal(Nil)
    }

    scenario("A query parameter name given twice is refused, as on every endpoint", CustomersAtOneBank, VersionOfApi) {
      val response = withRole { get("organisation_kind" -> "university", "organisation_kind" -> "regulator") }
      response.code should equal(400)
      response.body.extract[ErrorMessage].message should include(DuplicateQueryParameters)
    }

    scenario("limit and offset page through the matching Customers only", CustomersAtOneBank, VersionOfApi) {
      val tag = generateUUID().take(6)
      val matching = Set(customer("cohort" -> s"c-$tag"), customer("cohort" -> s"c-$tag"))
      customer("cohort" -> s"other-$tag")
      val first = ids("cohort" -> s"c-$tag", "limit" -> "1", "offset" -> "0")
      val second = ids("cohort" -> s"c-$tag", "limit" -> "1", "offset" -> "1")
      first.size should equal(1)
      second.size should equal(1)
      (first ++ second).toSet should equal(matching)
      ids("cohort" -> s"c-$tag", "limit" -> "1", "offset" -> "2") should equal(Nil)
    }
  }
}
