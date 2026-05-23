package code.api.util.migration

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.consent.MappedConsent
import net.liftweb.mapper.Schemifier

/**
 * Migration: switch consent_reference_id from a stringified row PK (Long) to a UUID.
 *
 * On EXISTING deploys, this:
 *   1. Backs up `mappedconsent` and `consent_item` (snapshot tables prefixed `backup_2026_05_`).
 *   2. Drops the v_consent view (its column types are about to change).
 *   3. Adds `mappedconsent.consent_reference_id VARCHAR(36)` (nullable for now).
 *   4. Backfills every existing consent row with a fresh UUID.
 *   5. Adds NOT NULL + UNIQUE on the new column.
 *   6. Widens `consent_item.consent_reference_id` from BIGINT to VARCHAR(36).
 *   7. Rewrites every consent_item row's consent_reference_id to point at its parent
 *      consent's new UUID (joining on the old Long via ::text cast).
 *
 * v_consent gets recreated by `MigrationOfConsentView.addConsentView` (already in the registry
 * with this migration's updated projection), wrapped here so it's part of the same logical step.
 *
 * On FRESH deploys, Schemifier creates everything with the correct shape directly from the
 * Scala model. The `tableExists` guards make most steps no-ops; backup CREATEs against an
 * empty table produce empty backup tables, which is harmless.
 */
object MigrationOfConsentReferenceIdUuid {

  def migrate(name: String): Boolean = {
    val dbDriver = APIUtil.getPropsValue("db.driver") openOr "org.h2.Driver"
    val isH2 = dbDriver.contains("org.h2.Driver")
    // H2 is only used in tests. Schemifier builds a fresh schema each run with the new
    // column types already in place (MappedConsent.mConsentReferenceId via MappedUUID,
    // ConsentItem.consentReferenceId via MappedString(36)), and v_consent is recreated by
    // addConsentView earlier in the migration chain. Running the Postgres/MSSQL ALTERs
    // here would only break the fresh schema — gen_random_uuid()/USING-cast/UPDATE-FROM
    // are not H2 syntax, and a failure mid-way drops v_consent without recreating it.
    if (isH2) {
      val startDate = System.currentTimeMillis()
      val commitId: String = APIUtil.gitCommit
      val endDate = System.currentTimeMillis()
      saveLog(name, commitId, true, startDate, endDate, "H2 detected — fresh schema already has the new column shape; nothing to migrate.")
      return true
    }
    DbFunction.tableExists(MappedConsent) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val isMssql = dbDriver.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver")
        var isSuccessful = false
        val sqlLog = new StringBuilder()

        try {
          // 1. Backups — table-snapshot copies so we can recover from any later failure.
          val backupMappedConsent = if (isMssql) {
            "SELECT * INTO backup_2026_05_mappedconsent FROM mappedconsent;"
          } else {
            "CREATE TABLE backup_2026_05_mappedconsent AS SELECT * FROM mappedconsent;"
          }
          val backupConsentItem = if (isMssql) {
            "SELECT * INTO backup_2026_05_consent_item FROM consent_item;"
          } else {
            "CREATE TABLE backup_2026_05_consent_item AS SELECT * FROM consent_item;"
          }
          sqlLog.append(DbFunction.maybeWrite(true, Schemifier.infoF _)(() => backupMappedConsent)).append("\n")
          sqlLog.append(DbFunction.maybeWrite(true, Schemifier.infoF _)(() => backupConsentItem)).append("\n")

          // 2. Drop the existing v_consent view; its consent_reference_id column type is changing.
          sqlLog.append(DbFunction.maybeWrite(true, Schemifier.infoF _)(() => "DROP VIEW IF EXISTS v_consent;")).append("\n")

          // 3. Add the new UUID column on mappedconsent, nullable for the backfill.
          val addColumn = if (isMssql) {
            "ALTER TABLE mappedconsent ADD consent_reference_id VARCHAR(36) NULL;"
          } else {
            "ALTER TABLE mappedconsent ADD COLUMN IF NOT EXISTS consent_reference_id VARCHAR(36);"
          }
          sqlLog.append(DbFunction.maybeWrite(true, Schemifier.infoF _)(() => addColumn)).append("\n")

          // 4. Backfill every existing consent row with a fresh UUID.
          val backfillConsents = if (isMssql) {
            "UPDATE mappedconsent SET consent_reference_id = LOWER(CONVERT(varchar(36), NEWID())) WHERE consent_reference_id IS NULL;"
          } else {
            // gen_random_uuid() requires pgcrypto on older Postgres; built-in on PG13+.
            "UPDATE mappedconsent SET consent_reference_id = gen_random_uuid()::text WHERE consent_reference_id IS NULL;"
          }
          sqlLog.append(DbFunction.maybeWrite(true, Schemifier.infoF _)(() => backfillConsents)).append("\n")

          // 5. Enforce NOT NULL + UNIQUE.
          val setNotNull = if (isMssql) {
            "ALTER TABLE mappedconsent ALTER COLUMN consent_reference_id VARCHAR(36) NOT NULL;"
          } else {
            "ALTER TABLE mappedconsent ALTER COLUMN consent_reference_id SET NOT NULL;"
          }
          sqlLog.append(DbFunction.maybeWrite(true, Schemifier.infoF _)(() => setNotNull)).append("\n")
          sqlLog.append(DbFunction.maybeWrite(true, Schemifier.infoF _)(() =>
            "ALTER TABLE mappedconsent ADD CONSTRAINT uq_mappedconsent_consent_reference_id UNIQUE (consent_reference_id);"
          )).append("\n")

          // 6. Widen consent_item.consent_reference_id from BIGINT to VARCHAR(36).
          val alterConsentItem = if (isMssql) {
            "ALTER TABLE consent_item ALTER COLUMN consent_reference_id VARCHAR(36);"
          } else {
            "ALTER TABLE consent_item ALTER COLUMN consent_reference_id TYPE VARCHAR(36) USING consent_reference_id::text;"
          }
          sqlLog.append(DbFunction.maybeWrite(true, Schemifier.infoF _)(() => alterConsentItem)).append("\n")

          // 7. Rewrite each consent_item row to point at its parent consent's new UUID.
          //    Old value is the parent's row PK (id) stringified; join on that, write the UUID.
          val backfillConsentItem = if (isMssql) {
            """UPDATE ci SET ci.consent_reference_id = c.consent_reference_id
              |FROM consent_item ci
              |JOIN mappedconsent c ON ci.consent_reference_id = CAST(c.id AS VARCHAR(36));""".stripMargin
          } else {
            """UPDATE consent_item ci
              |SET consent_reference_id = c.consent_reference_id
              |FROM mappedconsent c
              |WHERE ci.consent_reference_id = c.id::text;""".stripMargin
          }
          sqlLog.append(DbFunction.maybeWrite(true, Schemifier.infoF _)(() => backfillConsentItem)).append("\n")

          // 8. Recreate v_consent with the new column projection.
          MigrationOfConsentView.addConsentView(name + "_view_rebuild")

          isSuccessful = true
        } catch {
          case e: Exception =>
            isSuccessful = false
            sqlLog.append(s"\nException: ${e.getMessage}\n")
        }

        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""Executed SQL:
             |$sqlLog
             |""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful

      case false =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val isSuccessful = false
        val endDate = System.currentTimeMillis()
        val comment: String = s"""${MappedConsent._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}
