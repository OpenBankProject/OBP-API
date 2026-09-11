package code.customer.internalMapping

import code.setup.ServerSetup

/**
 * Characterization of the customer-id-mapping provider. Third of the id-mapping triplet
 * (AccountIdMapping, TransactionIdMapping, this one) - same table shape, same provider shape.
 * Nothing in the suite exercised this table before this test; it translates between an OBP
 * CustomerId (UUID) and a bank's own plain-text customer reference, called from
 * Helper.convertToId/convertToReference and from dynamic connector code via DynamicUtil's
 * compiled-code template.
 *
 * getOrCreateCustomerId is get-or-create keyed on customerPlainTextReference: a fresh reference
 * gets a newly generated customerId, and calling it again for the same reference returns the
 * same customerId rather than minting a new one. getCustomerPlainTextReference is the reverse
 * lookup.
 */
class CustomerIdMappingProviderTest extends ServerSetup {

  private def provider = CustomerIdMappingProvider.customerIdMappingProvider.vend

  Feature("customer id mapping storage") {

    Scenario("a fresh reference gets a newly created customer id") {
      val ref = "customer-id-mapping-test-" + System.nanoTime()
      val created = provider.getOrCreateCustomerId(ref)
      created.isDefined should equal(true)
    }

    Scenario("the same reference returns the same customer id on a second call") {
      val ref = "customer-id-mapping-test-" + System.nanoTime()
      val first = provider.getOrCreateCustomerId(ref).openOrThrowException("just created")
      val second = provider.getOrCreateCustomerId(ref).openOrThrowException("found again")

      second should equal(first)
    }

    Scenario("different references get different customer ids") {
      val refA = "customer-id-mapping-test-a-" + System.nanoTime()
      val refB = "customer-id-mapping-test-b-" + System.nanoTime()
      val idA = provider.getOrCreateCustomerId(refA).openOrThrowException("created A")
      val idB = provider.getOrCreateCustomerId(refB).openOrThrowException("created B")

      idA should not equal idB
    }

    Scenario("getCustomerPlainTextReference is the reverse lookup") {
      val ref = "customer-id-mapping-test-" + System.nanoTime()
      val id = provider.getOrCreateCustomerId(ref).openOrThrowException("just created")

      provider.getCustomerPlainTextReference(id).openOrThrowException("found") should equal(ref)
    }

    Scenario("getCustomerPlainTextReference on an unknown id is empty") {
      provider.getCustomerPlainTextReference(com.openbankproject.commons.model.CustomerId("does-not-exist")).isDefined should equal(false)
    }
  }
}
