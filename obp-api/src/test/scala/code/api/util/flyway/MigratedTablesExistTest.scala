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
    "etag",
    "authenticationtypevalidation",
    "userlocks",
    "connectormethod",
    "apicollectionendpoint",
    "featuredapicollection",
    "consentauthcontext",
    "mappeduserauthcontext",
    "userinitaction",
    "accountidmapping"
  )

  /**
   * Unique indexes the migrated tables must still have, as index_name per table.
   *
   * These are the ones Schemifier built from dbIndexes-declared UniqueIndex. They need their own
   * assertion because FlywayBaselineExport does not emit them: a table copied straight out of
   * that export looks complete and quietly loses its unique constraint, which does not fail -
   * inserts that should have been rejected simply start succeeding. Only tables that genuinely
   * have one are listed; most migrated tables have none.
   *
   * When a table moves off Mapper, read the truth from a booted instance before writing its
   * migration:
   *   SELECT table_name, index_name, index_type_name FROM information_schema.indexes
   *   WHERE table_name = 'YOUR_TABLE';
   * and add every UNIQUE INDEX row both to the Flyway script and to this list.
   */
  private val expectedUniqueIndexes = List(
    "PRODUCTTAG" -> "PRODUCTTAG_BANKID_PRODUCTCODE_TAG",
    "JSONSCHEMAVALIDATION" -> "JSONSCHEMAVALIDATION_OPERATIONID",
    "MAPPEDTRANSACTIONTYPE" -> "MAPPEDTRANSACTIONTYPE_MTRANSACTIONTYPEID",
    "MAPPEDTRANSACTIONTYPE" -> "MAPPEDTRANSACTIONTYPE_MBANKID_MSHORTCODE",
    "ETAG" -> "ETAG_ETAGRESOURCE",
    "AUTHENTICATIONTYPEVALIDATION" -> "AUTHENTICATIONTYPEVALIDATION_OPERATIONID",
    "USERLOCKS" -> "USERLOCKS_USERID",
    "CONNECTORMETHOD" -> "CONNECTORMETHOD_CONNECTORMETHODID",
    "CONNECTORMETHOD" -> "CONNECTORMETHOD_METHODNAME",
    "APICOLLECTIONENDPOINT" -> "APICOLLECTIONENDPOINT_APICOLLECTIONENDPOINTID",
    "APICOLLECTIONENDPOINT" -> "APICOLLECTIONENDPOINT_APICOLLECTIONID_OPERATIONID",
    "FEATUREDAPICOLLECTION" -> "FEATUREDAPICOLLECTION_FEATUREDAPICOLLECTIONID",
    "FEATUREDAPICOLLECTION" -> "FEATUREDAPICOLLECTION_APICOLLECTIONID",
    "CONSENTAUTHCONTEXT" -> "CONSENTAUTHCONTEXT_CONSENTID_KEY_C_CREATEDAT",
    "MAPPEDUSERAUTHCONTEXT" -> "MAPPEDUSERAUTHCONTEXT_MUSERID_MKEY_CREATEDAT",
    "USERINITACTION" -> "USERINITACTION_USERID_ACTIONNAME_ACTIONVALUE",
    "ACCOUNTIDMAPPING" -> "ACCOUNTIDMAPPING_MACCOUNTID",
    "ACCOUNTIDMAPPING" -> "ACCOUNTIDMAPPING_MACCOUNTID_MACCOUNTPLAINTEXTREFERENCE"
  )

  Feature("tables owned by Flyway rather than Schemifier") {

    Scenario("the unique indexes survived the move to Flyway") {
      val actual = DoobieUtil.runQuery(
        sql"""SELECT table_name, index_name FROM information_schema.indexes
              WHERE index_type_name = 'UNIQUE INDEX'"""
          .query[(String, String)].to[List]).toSet

      expectedUniqueIndexes.foreach { case (table, index) =>
        withClue(s"unique index $index on $table is missing - its Flyway script does not create it: ") {
          actual should contain(table -> index)
        }
      }
    }

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
