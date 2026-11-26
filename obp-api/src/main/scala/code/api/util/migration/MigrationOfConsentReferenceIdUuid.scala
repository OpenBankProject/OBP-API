/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.api.util.migration

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.consent.MappedConsent
import code.util.Helper.MdcLoggable
import net.liftweb.db.DB
import net.liftweb.mapper.Schemifier
import net.liftweb.util.DefaultConnectionIdentifier

import java.sql.SQLException

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
 * On a database written before the column existed, prepareBeforeSchemifier has already done
 * steps 1, 3 and 4 before Schemifier ran, so here they find nothing left to do.
 *
 * On FRESH deploys, Schemifier creates everything with the correct shape directly from the
 * Scala model. The `tableExists` guards make most steps no-ops; backup CREATEs against an
 * empty table produce empty backup tables, which is harmless.
 */
object MigrationOfConsentReferenceIdUuid extends MdcLoggable {

  private val consentTable = "mappedconsent"
  private val consentItemTable = "consent_item"
  private val referenceColumn = "consent_reference_id"
  private val consentBackup = "backup_2026_05_mappedconsent"
  private val consentItemBackup = "backup_2026_05_consent_item"

  private def isMssqlDriver(dbDriver: String): Boolean = dbDriver.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver")

  /**
   * Copy `source` into `backup`, unless an earlier step has already done so. The backup is taken
   * by whichever step changes the consent tables first, and that first copy is the one kept.
   */
  private def backupOnce(source: String, backup: String, isMssql: Boolean): String =
    if (isMssql) s"IF OBJECT_ID('$backup', 'U') IS NULL SELECT * INTO $backup FROM $source;"
    else s"CREATE TABLE IF NOT EXISTS $backup AS SELECT * FROM $source;"

  /** The SQL that adds the column and fills it, for the databases this step supports. */
  private case class ReferenceIdSql(addColumn: String, fillNulls: String)

  private def referenceIdSql(dbDriver: String): Option[ReferenceIdSql] =
    if (isMssqlDriver(dbDriver)) Some(ReferenceIdSql(
      s"ALTER TABLE $consentTable ADD $referenceColumn VARCHAR(36) NULL",
      s"UPDATE $consentTable SET $referenceColumn = LOWER(CONVERT(varchar(36), NEWID())) WHERE $referenceColumn IS NULL"))
    else if (dbDriver.contains("org.postgresql.Driver")) Some(ReferenceIdSql(
      s"ALTER TABLE $consentTable ADD COLUMN $referenceColumn VARCHAR(36)",
      // md5 over random input, cast to uuid, works on every Postgres version. gen_random_uuid() needs
      // Postgres 13 or the pgcrypto extension. The row id in the input keeps two rows apart even if
      // random() and the clock were to repeat.
      s"UPDATE $consentTable SET $referenceColumn = CAST(CAST(md5(random()::text || clock_timestamp()::text || id::text) AS uuid) AS VARCHAR(36)) WHERE $referenceColumn IS NULL"))
    else if (dbDriver.contains("org.h2.Driver")) Some(ReferenceIdSql(
      s"ALTER TABLE $consentTable ADD COLUMN $referenceColumn VARCHAR(36)",
      s"UPDATE $consentTable SET $referenceColumn = LOWER(CAST(RANDOM_UUID() AS VARCHAR(36))) WHERE $referenceColumn IS NULL"))
    else None

  private def hasConsentWithoutReferenceId(): Boolean =
    DB.use(DefaultConnectionIdentifier) { conn =>
      val st = conn.createStatement()
      try {
        st.setMaxRows(1)
        val rs = st.executeQuery(s"SELECT id FROM $consentTable WHERE $referenceColumn IS NULL")
        try rs.next() finally rs.close()
      } finally st.close()
    }

  private def execute(sql: String): Int =
    DB.use(DefaultConnectionIdentifier) { conn =>
      val st = conn.createStatement()
      try {
        st.execute(sql)
        st.getUpdateCount
      } finally st.close()
    }

  /**
   * Give every existing consent a reference id before Schemifier creates the unique index on it.
   *
   * A build from before May 2026 had no consent_reference_id column: a consent's reference was its
   * row id. The current model declares the column with a unique index, so on the first boot of a
   * current build Schemifier adds the column, NULL in every existing row, and then creates the
   * index. SQL Server allows only one NULL in a unique index, so with two or more consents that
   * boot stopped with a duplicate key error. Postgres, H2, MySQL and Oracle accept many NULLs, so
   * they booted, and migrate below filled the column later, if migration scripts were enabled.
   *
   * This step runs before Schemifier on every boot, ungated, for the reasons given on
   * Migration.database.prepareSchemaBeforeSchemifier. On a database with no consent table, or one
   * whose consents all have a reference id, it does one or two cheap checks and nothing else.
   * Otherwise it backs up the consent tables under the names migrate uses, adds the column if it
   * is missing and gives each consent without a reference id a new UUID. The backups are then
   * taken before anything changes, and migrate keeps them rather than copying again. migrate
   * still does the rest: it makes the column NOT NULL, widens consent_item.consent_reference_id
   * and points each consent item at its consent's new reference id, which it can do because the
   * row ids it joins on are unchanged.
   *
   * A failure is logged and does not stop the boot here. On SQL Server, Schemifier then stops it
   * with the error this step exists to prevent, which an operator can act on.
   */
  def prepareBeforeSchemifier(): Unit = {
    val name = "prepareConsentReferenceIdBeforeSchemifier"
    if (DbFunction.tableExistsByName(consentTable)) {
      val dbDriver = APIUtil.getPropsValue("db.driver") openOr "org.h2.Driver"
      val hasColumn = DbFunction.columnExistsByName(consentTable, referenceColumn)
      if (!hasColumn || hasConsentWithoutReferenceId()) {
        referenceIdSql(dbDriver) match {
          case None =>
            logger.info(s"$name: no SQL for driver $dbDriver; this database accepts more than one NULL in a unique index, so Schemifier can create the index and migrate fills the column")
          case Some(sql) =>
            val isMssql = isMssqlDriver(dbDriver)
            val startDate = System.currentTimeMillis()
            val done = new StringBuilder()
            val isSuccessful =
              try {
                execute(backupOnce(consentTable, consentBackup, isMssql))
                done.append(s"Backed up $consentTable to $consentBackup (if not already backed up). ")
                if (DbFunction.tableExistsByName(consentItemTable)) {
                  execute(backupOnce(consentItemTable, consentItemBackup, isMssql))
                  done.append(s"Backed up $consentItemTable to $consentItemBackup (if not already backed up). ")
                }
                if (!hasColumn) {
                  execute(sql.addColumn)
                  done.append(s"Added $consentTable.$referenceColumn. ")
                }
                val filled = execute(sql.fillNulls)
                done.append(s"Gave $filled consent(s) a new reference id.")
                logger.warn(s"$name: ${done.toString}")
                true
              } catch {
                case e: SQLException =>
                  logger.error(s"$name failed after: ${done.toString}", e)
                  done.append(s" FAILED: ${e.getMessage}")
                  false
              }
            if (DbFunction.tableExistsByName("migrationscriptlog")) {
              saveLog(name, APIUtil.gitCommit, isSuccessful, startDate, System.currentTimeMillis(), done.toString)
            }
        }
      }
    }
  }

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
        val isMssql = isMssqlDriver(dbDriver)
        var isSuccessful = false
        val sqlLog = new StringBuilder()

        try {
          // 1. Backups — table-snapshot copies so we can recover from any later failure. When
          //    prepareBeforeSchemifier has already changed the consent table, it took these copies
          //    first, before its change, and they are kept.
          val backupMappedConsent = backupOnce(consentTable, consentBackup, isMssql)
          val backupConsentItem = backupOnce(consentItemTable, consentItemBackup, isMssql)
          sqlLog.append(DbFunction.maybeWrite(true, Schemifier.infoF _)(() => backupMappedConsent)).append("\n")
          sqlLog.append(DbFunction.maybeWrite(true, Schemifier.infoF _)(() => backupConsentItem)).append("\n")

          // 2. Drop the existing v_consent view; its consent_reference_id column type is changing.
          sqlLog.append(DbFunction.maybeWrite(true, Schemifier.infoF _)(() => "DROP VIEW IF EXISTS v_consent;")).append("\n")

          // 3. Add the new UUID column on mappedconsent, nullable for the backfill.
          val addColumn = if (isMssql) {
            """IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('mappedconsent') AND name = 'consent_reference_id')
              |  ALTER TABLE mappedconsent ADD consent_reference_id VARCHAR(36) NULL;""".stripMargin
          } else {
            "ALTER TABLE mappedconsent ADD COLUMN IF NOT EXISTS consent_reference_id VARCHAR(36);"
          }
          sqlLog.append(DbFunction.maybeWrite(true, Schemifier.infoF _)(() => addColumn)).append("\n")

          // 4. Backfill every existing consent row with a fresh UUID. This uses the same statement as
          //    prepareBeforeSchemifier, so on Postgres it does not need gen_random_uuid(). That
          //    function exists only from Postgres 13, or with the pgcrypto extension. Postgres looks
          //    it up when it plans the statement, so it would fail even with no rows to fill.
          val backfillConsents = referenceIdSql(dbDriver).map(_.fillNulls).getOrElse(
            "UPDATE mappedconsent SET consent_reference_id = gen_random_uuid()::text WHERE consent_reference_id IS NULL;")
          sqlLog.append(DbFunction.maybeWrite(true, Schemifier.infoF _)(() => backfillConsents)).append("\n")

          // 5. Enforce NOT NULL + UNIQUE.
          val setNotNull = if (isMssql) {
            """IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('mappedconsent') AND name = 'consent_reference_id' AND is_nullable = 1)
              |BEGIN
              |  IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('mappedconsent') AND name = 'mappedconsent_consent_reference_id')
              |    DROP INDEX mappedconsent_consent_reference_id ON mappedconsent;
              |  IF EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = 'uq_mappedconsent_consent_reference_id' AND parent_object_id = OBJECT_ID('mappedconsent'))
              |    ALTER TABLE mappedconsent DROP CONSTRAINT uq_mappedconsent_consent_reference_id;
              |  ALTER TABLE mappedconsent ALTER COLUMN consent_reference_id VARCHAR(36) NOT NULL;
              |END""".stripMargin
          } else {
            "ALTER TABLE mappedconsent ALTER COLUMN consent_reference_id SET NOT NULL;"
          }
          sqlLog.append(DbFunction.maybeWrite(true, Schemifier.infoF _)(() => setNotNull)).append("\n")
          val addConstraint = if (isMssql) {
            """IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('mappedconsent') AND name = 'mappedconsent_consent_reference_id')
              |   AND NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = 'uq_mappedconsent_consent_reference_id' AND parent_object_id = OBJECT_ID('mappedconsent'))
              |  ALTER TABLE mappedconsent ADD CONSTRAINT uq_mappedconsent_consent_reference_id UNIQUE (consent_reference_id);""".stripMargin
          } else {
            "ALTER TABLE mappedconsent ADD CONSTRAINT uq_mappedconsent_consent_reference_id UNIQUE (consent_reference_id);"
          }
          sqlLog.append(DbFunction.maybeWrite(true, Schemifier.infoF _)(() => addConstraint)).append("\n")

          // 6. Widen consent_item.consent_reference_id from BIGINT to VARCHAR(36).
          val alterConsentItem = if (isMssql) {
            """IF EXISTS (SELECT 1 FROM sys.columns c JOIN sys.types t ON c.user_type_id = t.user_type_id WHERE c.object_id = OBJECT_ID('consent_item') AND c.name = 'consent_reference_id' AND t.name NOT IN ('varchar', 'nvarchar'))
              |  ALTER TABLE consent_item ALTER COLUMN consent_reference_id VARCHAR(36);""".stripMargin
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

          // 8. v_consent is recreated in the finally block below, with the new column projection.
          isSuccessful = true
        } catch {
          case e: Exception =>
            isSuccessful = false
            sqlLog.append(s"\nException: ${e.getMessage}\n")
        } finally {
          // Runs on success and on failure: step 2 dropped v_consent, so a failure part-way must not
          // leave it missing.
          MigrationOfConsentView.addConsentView(name + "_view_rebuild")
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
