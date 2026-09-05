package code.consent

import code.api.util.DoobieUtil
import code.setup.ServerSetup
import doobie.implicits._

/**
 * The consent_item table has to exist with the right shape, and nothing in Scala reads it through
 * an entity.
 *
 * This table is unusual: the Lift ConsentItem entity had no provider and no call sites at all -
 * grep finds no create/find/findAll anywhere. Every real access goes through raw SQL
 * (DoobieConsentQueries, MappedConsent, the v5.1.0 endpoints, and the reference-id migration).
 * The entity existed only so Schemifier would create the table.
 *
 * So the migration for this table is just a change of who creates it, and the thing worth testing
 * is exactly that: the table is there and still has the columns the SQL around it selects. Those
 * queries name columns explicitly, so a missing or renamed column is a runtime failure in the
 * consent endpoints rather than a compile error.
 */
class ConsentItemSchemaTest extends ServerSetup {

  private def columnExists(column: String): Boolean =
    DoobieUtil.runQuery(
      sql"""SELECT COUNT(*) FROM information_schema.columns
            WHERE UPPER(table_name) = 'CONSENT_ITEM' AND UPPER(column_name) = UPPER($column)"""
        .query[Int].unique) > 0

  Feature("consent_item schema") {

    Scenario("the table exists and is queryable") {
      // Fails outright if the table is missing - which is what happens if the entity is deleted
      // without the changelog taking over.
      noException should be thrownBy DoobieUtil.runQuery(
        sql"SELECT COUNT(*) FROM consent_item".query[Int].unique)
    }

    Scenario("it carries every column the surrounding SQL selects") {
      // Column names are snake_case, not the field names: every field on the entity overrode
      // dbColumnName. Reading them off the field names gives a table that looks right and is not.
      List("id", "consent_item_id", "consent_reference_id", "item_type",
           "bank_id", "account_id", "view_id", "role_name").foreach { c =>
        withClue(s"column $c missing from consent_item: ") {
          columnExists(c) should equal(true)
        }
      }
    }
  }
}
