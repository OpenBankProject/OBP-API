package code.validation

import code.setup.ServerSetup

/**
 * Characterization of the JSON-schema-validation provider, written before the implementation
 * moves to Doobie.
 *
 * There are endpoint tests for this feature but nothing at the provider level, so nothing would
 * say whether a replacement keeps the storage contract. Pinned here:
 *
 *  - lookup by operation id, and that a missing one is an empty Box rather than an exception;
 *  - create then read back with the schema text intact - the schema is a MappedText, so it has to
 *    survive being longer than a normal column;
 *  - update replaces the schema for an existing operation id rather than adding a second row,
 *    checked by deleting once and finding nothing left;
 *  - deleteByOperationId is scoped to one operation.
 */
class JsonSchemaValidationProviderTest extends ServerSetup {

  // Through the vend, so this keeps testing whichever implementation buildOne returns.
  private def provider = JsonSchemaValidationProvider.validationProvider.vend

  private val opA = "OBPv4.0.0-jsonSchemaProviderTest-A"
  private val opB = "OBPv4.0.0-jsonSchemaProviderTest-B"

  private val smallSchema = """{"type":"object"}"""
  private val bigSchema = """{"type":"object","properties":{""" +
    (1 to 200).map(i => s""""field$i":{"type":"string"}""").mkString(",") + "}}"

  override def beforeEach() = {
    super.beforeEach()
    provider.deleteByOperationId(opA)
    provider.deleteByOperationId(opB)
  }

  Feature("json schema validation storage") {

    Scenario("looking up an operation with no schema gives an empty box") {
      provider.getByOperationId(opA).isDefined should equal(false)
    }

    Scenario("a validation can be created and read back") {
      provider.create(JsonValidation(opA, smallSchema)).isDefined should equal(true)

      val found = provider.getByOperationId(opA)
      found.isDefined should equal(true)
      found.openOrThrowException("just asserted").jsonSchema should equal(smallSchema)
    }

    Scenario("a long schema survives the round trip") {
      // JsonSchema is a MappedText, not a bounded string: a rewrite that gives it a VARCHAR(255)
      // would pass every other scenario here and truncate real schemas.
      provider.create(JsonValidation(opA, bigSchema))

      provider.getByOperationId(opA).openOrThrowException("created").jsonSchema should equal(bigSchema)
    }

    Scenario("update replaces the schema instead of adding a second row") {
      provider.create(JsonValidation(opA, smallSchema))
      provider.update(JsonValidation(opA, """{"type":"array"}"""))

      provider.getByOperationId(opA).openOrThrowException("updated").jsonSchema should
        equal("""{"type":"array"}""")

      And("deleting once leaves nothing, i.e. there was only ever one row")
      provider.deleteByOperationId(opA)
      provider.getByOperationId(opA).isDefined should equal(false)
    }

    Scenario("delete is scoped to one operation id") {
      provider.create(JsonValidation(opA, smallSchema))
      provider.create(JsonValidation(opB, smallSchema))

      provider.deleteByOperationId(opA)

      provider.getByOperationId(opA).isDefined should equal(false)
      provider.getByOperationId(opB).isDefined should equal(true)
    }

    Scenario("getAll returns the stored validations") {
      provider.create(JsonValidation(opA, smallSchema))
      provider.create(JsonValidation(opB, smallSchema))

      val ids = provider.getAll().map(_.operationId)
      ids should contain(opA)
      ids should contain(opB)
    }
  }
}
