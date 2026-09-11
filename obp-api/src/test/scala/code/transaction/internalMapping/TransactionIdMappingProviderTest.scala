package code.transaction.internalMapping

import code.setup.ServerSetup

/**
 * Characterization of the transaction-id-mapping provider. Sibling of AccountIdMappingProviderTest
 * - same table shape, same provider shape. Nothing in the suite exercised this table before this
 * test; it translates between an OBP TransactionId (UUID) and a bank's own plain-text transaction
 * reference, called from Helper.convertToId/convertToReference.
 *
 * getOrCreateTransactionId is get-or-create keyed on transactionPlainTextReference: a fresh
 * reference gets a newly generated transactionId, and calling it again for the same reference
 * returns the same transactionId rather than minting a new one. getTransactionPlainTextReference
 * is the reverse lookup.
 */
class TransactionIdMappingProviderTest extends ServerSetup {

  private def provider = TransactionIdMappingProvider.transactionIdMappingProvider.vend

  Feature("transaction id mapping storage") {

    Scenario("a fresh reference gets a newly created transaction id") {
      val ref = "transaction-id-mapping-test-" + System.nanoTime()
      val created = provider.getOrCreateTransactionId(ref)
      created.isDefined should equal(true)
    }

    Scenario("the same reference returns the same transaction id on a second call") {
      val ref = "transaction-id-mapping-test-" + System.nanoTime()
      val first = provider.getOrCreateTransactionId(ref).openOrThrowException("just created")
      val second = provider.getOrCreateTransactionId(ref).openOrThrowException("found again")

      second should equal(first)
    }

    Scenario("different references get different transaction ids") {
      val refA = "transaction-id-mapping-test-a-" + System.nanoTime()
      val refB = "transaction-id-mapping-test-b-" + System.nanoTime()
      val idA = provider.getOrCreateTransactionId(refA).openOrThrowException("created A")
      val idB = provider.getOrCreateTransactionId(refB).openOrThrowException("created B")

      idA should not equal idB
    }

    Scenario("getTransactionPlainTextReference is the reverse lookup") {
      val ref = "transaction-id-mapping-test-" + System.nanoTime()
      val id = provider.getOrCreateTransactionId(ref).openOrThrowException("just created")

      provider.getTransactionPlainTextReference(id).openOrThrowException("found") should equal(ref)
    }

    Scenario("getTransactionPlainTextReference on an unknown id is empty") {
      provider.getTransactionPlainTextReference(com.openbankproject.commons.model.TransactionId("does-not-exist")).isDefined should equal(false)
    }
  }
}
