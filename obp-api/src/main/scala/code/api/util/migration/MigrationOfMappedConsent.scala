package code.api.util.migration

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.consent.MappedConsent
import net.liftweb.common.Full
import net.liftweb.mapper.{DB, Schemifier}
import net.liftweb.util.DefaultConnectionIdentifier

object MigrationOfMappedConsent {

  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")

  private def isMssql: Boolean =
    (APIUtil.getPropsValue("db.driver") openOr "org.h2.Driver").contains("com.microsoft.sqlserver.jdbc.SQLServerDriver")
  private def isMysql: Boolean =
    (APIUtil.getPropsValue("db.driver") openOr "org.h2.Driver").contains("com.mysql.cj.jdbc.Driver")

  /**
   * Retype a `mappedconsent` column that the `v_consent` view projects.
   *
   * The `v_consent` view (created by MigrationOfConsentView) SELECTs mjsonwebtoken, mconsumerid, mstatus, etc.
   * Postgres/MSSQL refuse an in-place `ALTER COLUMN ... TYPE` while a view depends on that column — even when
   * the target type is unchanged ("cannot alter type of a column used by a view or rule"). So we DROP v_consent
   * first, run the ALTER, then recreate the view from its canonical definition.
   *
   * The recreate happens HERE rather than relying on the later `addConsentView` migration: that one is gated by
   * runOnce and may already be marked executed, which would otherwise leave the view permanently dropped.
   * Mirrors MigrationOfConsentReferenceIdUuid's drop→alter→recreate pattern.
   */
  private def alterMappedConsentColumnUnderConsentView(name: String, alterSql: => String): Boolean = {
    DbFunction.tableExists(MappedConsent) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false
        val sqlLog = new StringBuilder()

        try {
          // 1. Drop v_consent — it projects the column, blocking the in-place retype.
          sqlLog.append(DbFunction.maybeWrite(true, Schemifier.infoF _)(() => "DROP VIEW IF EXISTS v_consent;")).append("\n")
          // 2. Run the (dialect-specific) column retype.
          sqlLog.append(DbFunction.maybeWrite(true, Schemifier.infoF _)(() => alterSql)).append("\n")
          // 3. Recreate v_consent from its canonical definition (don't depend on a later, possibly-skipped migration).
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
        val comment: String =
          s"""${MappedConsent._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }

  // Widen mappedconsent.mjsonwebtoken to TEXT (projected by v_consent — see helper).
  def alterColumnJsonWebToken(name: String): Boolean =
    alterMappedConsentColumnUnderConsentView(name,
      if (isMssql) "ALTER TABLE mappedconsent ALTER COLUMN mjsonwebtoken text;"
      else if (isMysql) "ALTER TABLE mappedconsent MODIFY COLUMN mjsonwebtoken TEXT;"
      else "ALTER TABLE mappedconsent ALTER COLUMN mjsonwebtoken type text;")

  def alterColumnChallenge(name: String): Boolean = {
    // mchallenge is NOT projected by v_consent, so this retype is not blocked by the view.
    DbFunction.tableExists(MappedConsent) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        val executedSql =
          DbFunction.maybeWrite(true, Schemifier.infoF _) {
            APIUtil.getPropsValue("db.driver") match    {
              case Full(dbDriver) if dbDriver.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver") =>
                () =>
                  """ALTER TABLE mappedconsent ALTER COLUMN mchallenge varchar(50);
                    |""".stripMargin
              case Full(dbDriver) if dbDriver.contains("com.mysql.cj.jdbc.Driver") => // MySQL
                () =>
                  """ALTER TABLE mappedconsent MODIFY COLUMN mchallenge varchar(50);
                    |""".stripMargin
              case _ =>
                () =>
                  """ALTER TABLE mappedconsent ALTER COLUMN mchallenge type varchar(50);
                    |""".stripMargin
            }

          }

        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""Executed SQL:
             |$executedSql
             |""".stripMargin
        isSuccessful = true
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful

      case false =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val isSuccessful = false
        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""${MappedConsent._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
  // The mConsumerId column was originally MappedUUID (varchar(36)), but Consumer.consumerId
  // is MappedString(250) and can hold composite IDs like "{azp_value}_UUID" generated
  // by OAuth2.getOrCreateConsumer when the azp claim is not a UUID.
  // This migration widens mConsumerId to match the Consumer model. mconsumerid is projected by v_consent.
  def alterColumnConsumerIdLength(name: String): Boolean =
    alterMappedConsentColumnUnderConsentView(name,
      if (isMssql) "ALTER TABLE mappedconsent ALTER COLUMN mconsumerid varchar(250);"
      else if (isMysql) "ALTER TABLE mappedconsent MODIFY COLUMN mconsumerid varchar(250);"
      else "ALTER TABLE mappedconsent ALTER COLUMN mconsumerid TYPE character varying(250);")

  // mstatus is projected by v_consent.
  def alterColumnStatus(name: String): Boolean =
    alterMappedConsentColumnUnderConsentView(name,
      if (isMssql) "ALTER TABLE mappedconsent ALTER COLUMN mstatus varchar(40);"
      else if (isMysql) "ALTER TABLE mappedconsent MODIFY COLUMN mstatus varchar(40);"
      else "ALTER TABLE mappedconsent ALTER COLUMN mstatus type varchar(40);")
}
