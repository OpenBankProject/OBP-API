package code.customerlinks

import code.api.util.APIUtil
import code.setup.ServerSetup
import net.liftweb.common.Full

import scala.concurrent.Await
import scala.concurrent.duration._

class CustomerLinkProviderTest extends ServerSetup {

  Feature("CustomerLinkX provider - CRUD") {

    Scenario("create, read, update, delete a customer link") {
      val bankId = APIUtil.generateUUID()
      val customerId = APIUtil.generateUUID()
      val otherBankId = APIUtil.generateUUID()
      val otherCustomerId = APIUtil.generateUUID()

      val created = CustomerLinkX.customerLink.vend.createCustomerLink(
        bankId, customerId, otherBankId, otherCustomerId, "spouse")
      created match {
        case Full(link) =>
          link.relationshipTo should equal("spouse")
          link.bankId should equal(bankId)
          link.customerId should equal(customerId)

          val fetched = CustomerLinkX.customerLink.vend.getCustomerLinkById(link.customerLinkId)
          fetched.map(_.relationshipTo) should equal(Full("spouse"))

          val updated = CustomerLinkX.customerLink.vend.updateCustomerLinkById(link.customerLinkId, "parent")
          updated.map(_.relationshipTo) should equal(Full("parent"))

          val byBank = CustomerLinkX.customerLink.vend.getCustomerLinksByBankId(bankId)
          byBank.map(_.map(_.customerLinkId)) should equal(Full(List(link.customerLinkId)))

          val byCustomer = CustomerLinkX.customerLink.vend.getCustomerLinksByCustomerId(customerId)
          byCustomer.map(_.map(_.customerLinkId)) should equal(Full(List(link.customerLinkId)))

          val deleted = Await.result(CustomerLinkX.customerLink.vend.deleteCustomerLinkById(link.customerLinkId), 10.seconds)
          deleted should equal(Full(true))

          val afterDelete = CustomerLinkX.customerLink.vend.getCustomerLinkById(link.customerLinkId)
          afterDelete.isDefined should equal(false)
        case other => fail(s"expected Full, got $other")
      }
    }

    Scenario("bulkDeleteCustomerLinks removes all rows") {
      CustomerLinkX.customerLink.vend.createCustomerLink(
        APIUtil.generateUUID(), APIUtil.generateUUID(), APIUtil.generateUUID(), APIUtil.generateUUID(), "sibling")
      CustomerLinkX.customerLink.vend.createCustomerLink(
        APIUtil.generateUUID(), APIUtil.generateUUID(), APIUtil.generateUUID(), APIUtil.generateUUID(), "sibling")

      val result = CustomerLinkX.customerLink.vend.bulkDeleteCustomerLinks()
      result should equal(true)
    }
  }
}
