package code.model.dataAccess.internalMapping

import code.setup.ServerSetup

/**
 * Characterization of the account-id-mapping provider. Nothing in the suite exercised this table
 * before this test: it is used to translate between an OBP AccountId (UUID) and a bank's own
 * plain-text account reference, from Helper.convertToId/convertToReference and from dynamic
 * connector code via DynamicUtil's compiled-code template.
 *
 * getOrCreateAccountId is get-or-create keyed on accountPlainTextReference: a fresh reference
 * gets a newly generated accountId, and calling it again for the same reference returns the same
 * accountId rather than minting a new one. getAccountPlainTextReference is the reverse lookup.
 */
class AccountIdMappingProviderTest extends ServerSetup {

  private def provider = AccountIdMappingProvider.accountIdMappingProvider.vend

  Feature("account id mapping storage") {

    Scenario("a fresh reference gets a newly created account id") {
      val ref = "account-id-mapping-test-" + System.nanoTime()
      val created = provider.getOrCreateAccountId(ref)
      created.isDefined should equal(true)
    }

    Scenario("the same reference returns the same account id on a second call") {
      val ref = "account-id-mapping-test-" + System.nanoTime()
      val first = provider.getOrCreateAccountId(ref).openOrThrowException("just created")
      val second = provider.getOrCreateAccountId(ref).openOrThrowException("found again")

      second should equal(first)
    }

    Scenario("different references get different account ids") {
      val refA = "account-id-mapping-test-a-" + System.nanoTime()
      val refB = "account-id-mapping-test-b-" + System.nanoTime()
      val idA = provider.getOrCreateAccountId(refA).openOrThrowException("created A")
      val idB = provider.getOrCreateAccountId(refB).openOrThrowException("created B")

      idA should not equal idB
    }

    Scenario("getAccountPlainTextReference is the reverse lookup") {
      val ref = "account-id-mapping-test-" + System.nanoTime()
      val id = provider.getOrCreateAccountId(ref).openOrThrowException("just created")

      provider.getAccountPlainTextReference(id).openOrThrowException("found") should equal(ref)
    }

    Scenario("getAccountPlainTextReference on an unknown id is empty") {
      provider.getAccountPlainTextReference(com.openbankproject.commons.model.AccountId("does-not-exist")).isDefined should equal(false)
    }
  }
}
