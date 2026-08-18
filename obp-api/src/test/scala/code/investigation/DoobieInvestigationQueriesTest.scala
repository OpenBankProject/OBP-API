package code.investigation

import code.customerlinks.CustomerLinkX
import code.setup.ServerSetup
import net.liftweb.util.Helpers

/**
 * The investigation report's queries are hand-written SQL, so nothing but running them checks that
 * the tables and columns they name exist.
 *
 * getCustomerLinks named a table that does not exist - mappedcustomerlink, with an m-prefix on
 * every column - while the entity had overridden dbTableName to CustomerLink and its columns carry
 * no prefix. The endpoint that calls it (GET .../customers/CUSTOMER_ID/investigation-report) failed
 * on the first row it tried to read, and no test noticed because none of them ran the query.
 */
class DoobieInvestigationQueriesTest extends ServerSetup {

  feature("the investigation report's customer-link query") {

    scenario("runs against the real schema and returns the linked customer") {
      val customerId = "inv-" + Helpers.randomString(10).toLowerCase
      val otherCustomerId = "inv-other-" + Helpers.randomString(10).toLowerCase
      val bankId = "inv-bank-" + Helpers.randomString(6).toLowerCase

      // (bankId, customerId, otherBankId, otherCustomerId, relationshipTo)
      CustomerLinkX.customerLink.vend.createCustomerLink(
        bankId, customerId, bankId, otherCustomerId, "SPOUSE")
        .isDefined should equal(true)

      val links = DoobieInvestigationQueries.getCustomerLinks(customerId)

      links.map(_.otherCustomerId) should contain(otherCustomerId)
      val link = links.find(_.otherCustomerId == otherCustomerId).get
      link.otherBankId should equal(bankId)
      link.relationship should equal("SPOUSE")
      link.customerLinkId should not be empty
    }

    scenario("returns nothing for a customer with no links, rather than failing") {
      DoobieInvestigationQueries.getCustomerLinks("inv-no-links-" + Helpers.randomString(8)) should be(empty)
    }
  }
}
