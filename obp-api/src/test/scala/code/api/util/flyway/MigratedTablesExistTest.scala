package code.api.util.flyway

import code.api.util.DoobieUtil
import code.setup.ServerSetup
import doobie._
import doobie.implicits._

/**
 * Every table that has been taken off Lift Mapper must still exist.
 *
 * This exists because of a real failure mode rather than a hypothetical one. Schemifier used to
 * create these tables from the entity definitions; once the entity is deleted, the only thing that
 * creates them is a Flyway script under src/main/resources/db/migration. That directory sits under
 * a .gitignore rule which excludes all of src/main/resources, so the scripts were present on the
 * machine that wrote them and absent from the repository - ten tables' worth. Everything stayed
 * green locally and would have failed on any clean checkout, at the point where the first query
 * hits a table that was never created.
 *
 * A missing script is not a compile error and not a schema error either: Flyway simply has nothing
 * to apply, and the failure surfaces much later as an unrelated-looking SQL error inside whichever
 * endpoint touched the table first. Asserting existence directly turns that into one obvious red.
 *
 * When a table moves off Mapper, add it here in the same commit.
 */
class MigratedTablesExistTest extends ServerSetup {

  // Names as the database holds them, which is not always the entity name: several entities
  // overrode dbTableName (connector_trace, consent_item), so deriving these from Scala names
  // would give a list that looks right and tests nothing.
  private val migratedTables = List(
    "mappedatm",
    "mappednarrative",
    "mappedcomment",
    "mappedtag",
    "mappedwheretag",
    "mappedtransactionimage",
    "producttag",
    "connector_trace",
    "consent_item",
    "jsonschemavalidation",
    "mappedtransactiontype",
    "etag"
  )

  Feature("tables owned by Flyway rather than Schemifier") {

    Scenario("each migrated table exists and is queryable") {
      migratedTables.foreach { table =>
        withClue(s"table $table is missing - its Flyway migration is not on the classpath: ") {
          noException should be thrownBy DoobieUtil.runQuery(
            (fr"SELECT COUNT(*) FROM " ++ Fragment.const(table)).query[Int].unique)
        }
      }
    }
  }
}
