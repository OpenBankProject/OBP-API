package code.customeraccountlinks

import code.api.util.APIUtil
import code.setup.ServerSetup
import net.liftweb.common.Full

class CustomerAccountLinkProviderTest extends ServerSetup {

  Feature("CustomerAccountLinkX provider - methods not covered by the endpoint test") {

    Scenario("getOrCreateCustomerAccountLink creates once then returns the same row") {
      val customerId = APIUtil.generateUUID()
      val bankId = APIUtil.generateUUID()
      val accountId = APIUtil.generateUUID()

      val first = CustomerAccountLinkX.customerAccountLink.vend.getOrCreateCustomerAccountLink(
        customerId, bankId, accountId, "Owner")
      val second = CustomerAccountLinkX.customerAccountLink.vend.getOrCreateCustomerAccountLink(
        customerId, bankId, accountId, "SomethingElse")

      (first, second) match {
        case (Full(a), Full(b)) =>
          a.customerAccountLinkId should equal(b.customerAccountLinkId)
          b.relationshipType should equal("Owner")
        case other => fail(s"expected (Full, Full), got $other")
      }
    }

    Scenario("getCustomerAccountLinks returns every row and bulkDeleteCustomerAccountLinks clears them") {
      CustomerAccountLinkX.customerAccountLink.vend.createCustomerAccountLink(
        APIUtil.generateUUID(), APIUtil.generateUUID(), APIUtil.generateUUID(), "Owner")
      CustomerAccountLinkX.customerAccountLink.vend.createCustomerAccountLink(
        APIUtil.generateUUID(), APIUtil.generateUUID(), APIUtil.generateUUID(), "Owner")

      val all = CustomerAccountLinkX.customerAccountLink.vend.getCustomerAccountLinks
      all match {
        case Full(links) => links.size should be >= 2
        case other => fail(s"expected Full, got $other")
      }

      val deleted = CustomerAccountLinkX.customerAccountLink.vend.bulkDeleteCustomerAccountLinks()
      deleted should equal(true)

      val afterDelete = CustomerAccountLinkX.customerAccountLink.vend.getCustomerAccountLinks
      afterDelete should equal(Full(Nil))
    }
  }
}
