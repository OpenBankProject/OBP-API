package code.apiproductattribute

import code.setup.ServerSetup

/**
 * Characterization of the api-product-attribute provider, written before the implementation
 * moves to Doobie. Nothing in the suite exercised this table before this test.
 *
 * createOrUpdateApiProductAttribute is a real update when apiProductAttributeId is supplied and
 * a row already exists for it, otherwise a create - and looking it up by id (not by
 * (bankId, apiProductCode)) is what lets a bank/code get more than one attribute with the same
 * name at once.
 */
class ApiProductAttributesProviderTest extends ServerSetup {

  private def provider = DoobieApiProductAttributesProvider

  private val bankId = "api-product-attribute-test-bank"
  private val productCode = "CURRENT"

  Feature("api product attribute storage") {

    Scenario("a fresh attribute is created when no id is given") {
      val created = provider.createOrUpdateApiProductAttribute(
        bankId, productCode, None, "maxBalance", "STRING", "1000", Some(true))
      created.isDefined should equal(true)
      created.openOrThrowException("just created").name should equal("maxBalance")
    }

    Scenario("supplying an existing id updates that row in place") {
      val created = provider.createOrUpdateApiProductAttribute(
        bankId, productCode, None, "maxBalance", "STRING", "1000", Some(true))
        .openOrThrowException("just created")

      provider.createOrUpdateApiProductAttribute(
        bankId, productCode, Some(created.apiProductAttributeId), "maxBalance", "STRING", "2000", Some(false))

      val all = provider.getApiProductAttributesByBankIdAndCode(bankId, productCode)
        .openOrThrowException("listed")
      all.count(_.apiProductAttributeId == created.apiProductAttributeId) should equal(1)
      all.find(_.apiProductAttributeId == created.apiProductAttributeId).get.value should equal("2000")
    }

    Scenario("an id with no matching row falls back to create") {
      val result = provider.createOrUpdateApiProductAttribute(
        bankId, productCode, Some("does-not-exist"), "minBalance", "STRING", "0", Some(true))
      result.isDefined should equal(true)
      result.openOrThrowException("created").apiProductAttributeId should not equal "does-not-exist"
    }

    Scenario("getApiProductAttributeById finds a single attribute") {
      val created = provider.createOrUpdateApiProductAttribute(
        bankId, productCode, None, "currency", "STRING", "EUR", Some(true))
        .openOrThrowException("just created")

      provider.getApiProductAttributeById(created.apiProductAttributeId)
        .openOrThrowException("found").name should equal("currency")
    }

    Scenario("deleteApiProductAttribute removes just that attribute") {
      val created = provider.createOrUpdateApiProductAttribute(
        bankId, productCode, None, "toDelete", "STRING", "x", Some(true))
        .openOrThrowException("just created")

      provider.deleteApiProductAttribute(created.apiProductAttributeId)

      provider.getApiProductAttributeById(created.apiProductAttributeId).isDefined should equal(false)
    }

    Scenario("deleteApiProductAttributesByBankIdAndCode removes every attribute for that product") {
      val otherBankId = "api-product-attribute-test-other-bank"
      provider.createOrUpdateApiProductAttribute(otherBankId, productCode, None, "a", "STRING", "1", Some(true))
      provider.createOrUpdateApiProductAttribute(otherBankId, productCode, None, "b", "STRING", "2", Some(true))

      provider.deleteApiProductAttributesByBankIdAndCode(otherBankId, productCode)

      provider.getApiProductAttributesByBankIdAndCode(otherBankId, productCode)
        .openOrThrowException("listed") should equal(Nil)
    }
  }
}
