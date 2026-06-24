package code.api.util.migration

import code.api.util.APIUtil.{getPropsAsBoolValue, getPropsValue}
import code.api.util.{APIUtil, ApiPropsWithAlias}
import code.api.v4_0_0.DatabaseInfoJson
import code.consumer.Consumers
import code.context.MappedUserAuthContextUpdate
import code.customer.CustomerX
import code.migration.MigrationScriptLogProvider
import code.util.Helper.MdcLoggable
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.mapper.Schemifier.getDefaultSchemaName
import net.liftweb.mapper.{BaseMetaMapper, DB}

import java.sql.{ResultSet, SQLException}
import java.text.SimpleDateFormat
import java.util.Date
import scala.collection.immutable
import scala.collection.mutable.HashMap

/**
 * ==Schema drift & SQL views — the rule when altering a viewed column==
 *
 * Postgres refuses `ALTER COLUMN ... TYPE` on a column a view references:
 * `ERROR: cannot alter type of a column used by a view or rule`. This is the recurring "schema drift"
 * that aborts boot. It bites whenever a view (e.g. `v_account_access_with_views`, `v_consent`,
 * `v_metric`) pins a column that a migration — or Lift Schemifier matching a changed model field width —
 * wants to alter.
 *
 * DO NOT fix this by dropping all views on boot/migrate: in a multi-node deployment another node may be
 * live and querying those views, so a global drop (even transient) errors the serving node.
 * `v_account_access_with_views` and `v_consent` are read by live code.
 *
 * DO: when you add a migration that alters a column a view references, make that single `runOnce`
 * migration do, as one unit (Postgres DDL is transactional, so other nodes never see the view missing):
 *   DROP VIEW <only the dependent view(s)>  ->  ALTER COLUMN ...  ->  CREATE [OR REPLACE] VIEW ...
 * Only drop a view when you must (to alter a column it pins). For a Schemifier-driven width change on a
 * viewed column, drop the view in the pre-Schemifier pass (`startedBeforeSchemifier == true`) and
 * recreate it in the post-Schemifier pass.
 *
 * To REPAIR an already-drifted DB forward-only, do NOT edit/duplicate the original migration — `runOnce`
 * skips it once logged (`isExecuted`). Add a NEW migration (new name) doing DROP+ALTER+CREATE; it runs
 * once to fix existing DBs and is a no-op (CREATE OR REPLACE / IF EXISTS) on fresh ones. The full
 * drop-everything reset in running_tests_on_postgres.md is the local recovery path.
 */
object Migration extends MdcLoggable {
  private val migrationScriptsEnabled = ApiPropsWithAlias.migrationScriptsEnabled
  private val executeAll = getPropsAsBoolValue("migration_scripts.execute_all", false)
  private val scriptsToExecute: immutable.Seq[String] = getPropsValue("list_of_migration_scripts_to_execute").toList.map(_.split(",")).flatten

  private def executeScript(blockOfCode: => Boolean): Boolean = {
    if(migrationScriptsEnabled) blockOfCode else migrationScriptsEnabled
  }
  
  private def runOnce(name: String)(blockOfCode: => Boolean): Boolean = {
    val toExecute: Boolean = executeAll || scriptsToExecute.contains(name)
    val isExecuted = MigrationScriptLogProvider.migrationScriptLogProvider.vend.isExecuted(name)
    (toExecute, isExecuted) match {
      case (true, false) => 
        logger.warn(s"Migration.database.$name is started at this instance.")
        blockOfCode
      case _ => true
    }
  }
  
  def saveLog(name: String, commitId: String, isSuccessful: Boolean, startDate: Long, endDate: Long, comment: String) = {
    var remark = comment
    if(comment.length() > 1024) {
      val traceUUID = APIUtil.generateUUID()
      val traceText = " Trace UUID: "  + traceUUID
      remark = remark.substring(0,970) + traceText
      logger.info(traceText)
      logger.info(comment)
    }
    MigrationScriptLogProvider.migrationScriptLogProvider.vend.saveLog(name, commitId, isSuccessful, startDate, endDate, remark) match {
      case true =>
        logger.warn(s"Migration.database.$name is executed at this instance.")
      case false =>
        logger.warn(s"Migration.database.$name is executed at this instance but the corresponding log is not saved!!!!!!")
    }
  }
  
  object database {

    /**
     * Runs the migration scripts. Called twice from Boot, BOTH times AFTER `schemifyAll()`.
     *
     * `startedBeforeSchemifier` does NOT mean "this pass runs before Schemifier" — despite the name
     * and the historical Boot comments, both passes run after it. It selects which pass this is:
     *  - `true`  = the existing-DB pass (only invoked when `tableExists(ResourceUser)`): migrations
     *              that require post-Schemifier schema guard on this flag and skip themselves here.
     *  - `false` = the catch-all pass that runs for every DB; the guarded migrations run in this one.
     * `runOnce` (tracked in `MigrationScriptLog`) guarantees each named migration executes exactly
     * once across both passes.
     */
    def executeScripts(startedBeforeSchemifier: Boolean): Boolean = executeScript {
      dummyScript()
      addAccountAccessConsumerId()
      generateAndPopulateMissingCustomerUUIDs(startedBeforeSchemifier)
      generateAndPopulateMissingConsumersUUIDs(startedBeforeSchemifier)
      populateTableRateLimiting()
      updateTableViewDefinition()
      bankAccountHoldersAndOwnerViewAccessInfo(startedBeforeSchemifier)
      alterTableMappedConsent()
      alterColumnChallengeAtTableMappedConsent()
      alterTableOpenIDConnectToken()
      alterTableMappedUserAuthContext(startedBeforeSchemifier)
      alterTableMappedUserAuthContextUpdate()
      populateNameAndAppTypeFieldsAtConsumerTable()
      populateAzpAndSubFieldsAtConsumerTable()
      populateTableBankAccountRouting()
      populateSettlementBankAccounts()
      alterColumnStatusAtTableMappedConsent()
      alterColumnDetailsAtTableTransactionRequest()
      deleteDuplicatedRowsInTheTableUserAuthContext(startedBeforeSchemifier)
      populateTheFieldDeletedAtResourceUser(startedBeforeSchemifier)
      populateTheFieldIsActiveAtProductAttribute(startedBeforeSchemifier)
      alterColumnUsernameProviderFirstnameAndLastnameAtAuthUser(startedBeforeSchemifier)
      populateMissingProviderAtAuthUser(startedBeforeSchemifier)
      alterColumnEmailAtResourceUser(startedBeforeSchemifier)
      alterColumnNameAtProductFee(startedBeforeSchemifier)
      addFastFirehoseAccountsView(startedBeforeSchemifier)
      addFastFirehoseAccountsMaterializedView(startedBeforeSchemifier)
      alterUserAuthContextColumnKeyAndValueLength(startedBeforeSchemifier)
      alterMappedTransactionRequestFieldsLengthMigration(startedBeforeSchemifier)
      dropIndexAtColumnUsernameAtTableAuthUser(startedBeforeSchemifier)
      dropIndexAtUserAuthContext()
      alterWebhookColumnUrlLength()
      alterMappedCounterpartyDescriptionLength()
      dropConsentAuthContextDropIndex()
      alterMappedExpectedChallengeAnswerChallengeTypeLength()
      alterTransactionRequestChallengeChallengeTypeLength()
      alterUserAttributeNameLength()
      alterMappedCustomerAttribute(startedBeforeSchemifier)
      dropMappedBadLoginAttemptIndex()
      alterMetricColumnUrlLength()
      alterMetricArchiveColumnCorrelationidLength()
      alterCounterpartyLimitFieldType()
      changeTypeOfAudFieldAtConsumerTable()
      renameCustomerRoleNames()
      addUniqueIndexOnResourceUserUserId()
      addIndexOnMappedMetricUserId()
      alterRoleNameLength()
      alterConsentRequestColumnConsumerIdLength()
      alterMappedConsentColumnConsumerIdLength()
      alterMetricColumnConsumerIdLength()
      addAccountAccessWithViewsView(startedBeforeSchemifier)
      addMetricView(startedBeforeSchemifier)
      addConsentView(startedBeforeSchemifier)
      updateConsentViewAddJwtPayload(startedBeforeSchemifier)
      updateConsentViewAddJwtExpiresAt(startedBeforeSchemifier)
      updateAccountAccessWithViewsViewUnionAll(startedBeforeSchemifier)
      migrateChatRoomIsOpenRoom()
      migrateChatRoomCreatedByAndLastMessageSender()
      migrateConsentReferenceIdToUuid(startedBeforeSchemifier)
      migrateMetricConsentReferenceId(startedBeforeSchemifier)
      dropFastFirehoseAccountsViews(startedBeforeSchemifier)
    }

    /**
     * Remove natural-key duplicate rows so Schemifier's CREATE UNIQUE INDEX on
     * `mapperaccountholder` (user_, bank, account) and `mappedentitlement` (bank, user, role)
     * cannot abort boot on an existing DB that still holds duplicates.
     *
     * Deliberately invoked directly from `Boot` BEFORE `schemifyAll()` and NOT routed through
     * `executeScripts`/`runOnce`: those passes run AFTER Schemifier (too late — the index DDL has
     * already run) and are gated by `migration_scripts.*` props (off in tests), whereas Schemifier
     * creates the index ungated in every environment incl. H2. Keeps each table's dedup self-guarded
     * (table-existence + has-duplicates probe), so it is a cheap no-op on fresh/clean/test DBs and
     * needs no `MigrationScriptLog` entry. See the call site in `Boot.scala` for the full rationale.
     */
    def deduplicateBeforeUniqueIndexSchemify(): Unit = {
      deduplicateNaturalKeyDups(
        tableName = "mapperaccountholder",
        idCol     = "id",
        groupCols = List("user_", "accountbankpermalink", "accountpermalink")
      )
      deduplicateNaturalKeyDups(
        tableName = "mappedentitlement",
        idCol     = "id",
        groupCols = List("mbankid", "muserid", "mrolename")
      )
    }

    /**
     * Collapse natural-key duplicates in `tableName` down to one surviving row per key group.
     *
     * Survivor policy: KEEP the row with the lowest `idCol` (the oldest insert) per `groupCols`
     * group, DELETE the rest. The discarded duplicates are NOT byte-identical to the survivor —
     * only the natural key matches — so this is lossy by design:
     *  - `mappedentitlement`: each duplicate carries its own `mentitlementid` UUID (the external
     *    handle returned by the API and used by `getEntitlementById`/`deleteEntitlement`), plus
     *    `created_by_process` / `group_id` / `process` / `entitlement_request_id` / timestamps.
     *    Removing a duplicate invalidates any stale reference to *that* row's UUID. This is
     *    acceptable: the surviving row encodes the identical (bank, user, role) grant, so
     *    authorization is unaffected — only dead handles to the removed copies break.
     *  - `mapperaccountholder`: duplicates may differ in `source` (provenance metadata). The
     *    surviving row encodes the same (user, account) ownership link.
     *
     * Safe to run on every boot and under concurrent multi-node boot: the survivor set is a
     * deterministic lowest-id-per-group, the DELETE is idempotent (re-running removes 0 rows), and
     * Lift Mapper's Schemifier emits no DB-level FK constraints, so the DELETE neither cascades nor
     * aborts on referential integrity. The has-duplicates probe keeps clean/fresh/test DBs on the
     * cheap path — the heavier delete only runs when extras actually exist. The delete uses a
     * derived-table + ROW_NUMBER() form (see inline note) so it is portable across every driver OBP
     * ships, including MySQL/MariaDB, instead of the MySQL-incompatible `NOT IN (SELECT MIN ...)`.
     */
    private def deduplicateNaturalKeyDups(tableName: String, idCol: String, groupCols: List[String]): Unit = {
      if (DbFunction.tableExistsByName(tableName)) {
        val groupBy = groupCols.mkString(", ")
        val hasDups = DB.use(net.liftweb.util.DefaultConnectionIdentifier) { conn =>
          val st = conn.createStatement()
          try {
            val rs = st.executeQuery(s"SELECT 1 FROM $tableName GROUP BY $groupBy HAVING COUNT(*) > 1")
            try rs.next() finally rs.close()
          } finally st.close()
        }
        if (hasDups) {
          logger.warn(s"deduplicateBeforeUniqueIndexSchemify: duplicates found in $tableName – removing extras (keeping the lowest $idCol per [$groupBy])")
          // Delete-set shape (target only the few extras), deliberately NOT survivor-set
          // (`... NOT IN (SELECT MIN(id) FROM sameTable ...)`): the survivor-set form has the
          // subquery's FROM name the very table being deleted, which throws MySQL/MariaDB
          // ERROR 1093 ("can't specify target table for update in FROM clause") — and MySQL is a
          // first-class OBP target (driver shipped, per-vendor branches throughout this package).
          // Wrapping ROW_NUMBER() in a derived table (`(...) tmp`, no AS — Oracle-safe) is the one
          // form portable across every driver OBP ships: the derived table is materialised, which
          // sidesteps 1093, and window functions are supported by all of PostgreSQL, H2 2.x,
          // MySQL 8+/MariaDB 10.2+, SQL Server and Oracle. `ORDER BY $idCol ASC` + `rn > 1` deletes
          // all but the lowest id per group — the identical survivor the NOT IN/MIN form kept.
          val deleteSql =
            s"""DELETE FROM $tableName WHERE $idCol IN (
               |  SELECT $idCol FROM (
               |    SELECT $idCol, ROW_NUMBER() OVER (PARTITION BY $groupBy ORDER BY $idCol ASC) AS rn FROM $tableName
               |  ) tmp WHERE rn > 1
               |)""".stripMargin
          val deleted = DB.use(net.liftweb.util.DefaultConnectionIdentifier) { conn =>
            val st = conn.createStatement()
            try {
              st.executeUpdate(deleteSql)
            } finally st.close()
          }
          logger.warn(s"deduplicateBeforeUniqueIndexSchemify: removed $deleted duplicate row(s) from $tableName")
        }
      }
    }

    private def dummyScript(): Boolean = {
      val name = nameOf(dummyScript)
      runOnce(name) {
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val comment: String = "dummy comment"
        val isSuccessful = true
        val endDate = System.currentTimeMillis()
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
      }
    }
    
    
    private def generateAndPopulateMissingCustomerUUIDs(startedBeforeSchemifier: Boolean): Boolean = {
      if(startedBeforeSchemifier == true) {
        logger.warn(s"Migration.database.generateAndPopulateMissingCustomerUUIDs(true) cannot be run before Schemifier.")
        true
      } else {
        val name = nameOf(generateAndPopulateMissingCustomerUUIDs(startedBeforeSchemifier))
        runOnce(name) {
          val startDate = System.currentTimeMillis()
          val commitId: String = APIUtil.gitCommit
          val isSuccessful = CustomerX.customerProvider.vend.populateMissingUUIDs()
          val endDate = System.currentTimeMillis()

          val comment: String =
            s"""Execute `generateAndPopulateMissingCustomerUUIDs` 
               |Duration: ${endDate - startDate} ms;
             """.stripMargin
          saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
          isSuccessful
        }
      }
      
    }

    private def generateAndPopulateMissingConsumersUUIDs(startedBeforeSchemifier: Boolean): Boolean = {
      if(startedBeforeSchemifier == true) {
        logger.warn(s"Migration.database.generateAndPopulateMissingConsumersUUIDs(true) cannot be run before Schemifier.")
        true
      } else {
        val name = nameOf(generateAndPopulateMissingConsumersUUIDs(startedBeforeSchemifier))
        runOnce(name) {
          val startDate = System.currentTimeMillis()
          val commitId: String = APIUtil.gitCommit
          val isSuccessful = Consumers.consumers.vend.populateMissingUUIDs()
          val endDate = System.currentTimeMillis()
          val comment: String =
            s"""Execute `generateAndPopulateMissingConsumersUUIDs` 
               |Duration: ${endDate - startDate} ms;
             """.stripMargin
          saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
          isSuccessful
        }
      }
    }

    private def populateTableRateLimiting(): Boolean = {
      val name = nameOf(populateTableRateLimiting)
      runOnce(name) {
        TableRateLmiting.populate(name)
      }
    }
    
    private def updateTableViewDefinition(): Boolean = {
      val name = nameOf(updateTableViewDefinition)
      runOnce(name) {
        UpdateTableViewDefinition.populate(name)
      }
    }

    private def bankAccountHoldersAndOwnerViewAccessInfo(startedBeforeSchemifier: Boolean): Boolean = {
      if(startedBeforeSchemifier == true) {
        logger.warn(s"Migration.database.bankAccountHoldersAndOwnerViewAccessInfo(true) cannot be run before Schemifier.")
        true
      } else {
        val name = nameOf(bankAccountHoldersAndOwnerViewAccessInfo(startedBeforeSchemifier))
        runOnce(name) {
          BankAccountHoldersAndOwnerViewAccess.saveInfoBankAccountHoldersAndOwnerViewAccessInfo(name)
        }
      }
    }
    private def alterTableMappedConsent(): Boolean = {
      val name = nameOf(alterTableMappedConsent)
      runOnce(name) {
        MigrationOfMappedConsent.alterColumnJsonWebToken(name)
      }
    }
    private def alterColumnChallengeAtTableMappedConsent(): Boolean = {
      val name = nameOf(alterColumnChallengeAtTableMappedConsent)
      runOnce(name) {
        MigrationOfMappedConsent.alterColumnChallenge(name)
      }
    }
    private def alterTableOpenIDConnectToken(): Boolean = {
      val name = nameOf(alterTableOpenIDConnectToken)
      runOnce(name) {
        MigrationOfOpnIDConnectToken.alterColumnAccessToken(name)
        MigrationOfOpnIDConnectToken.alterColumnRefreshToken(name)
      }
    }
    private def populateNameAndAppTypeFieldsAtConsumerTable(): Boolean = {
      val name = nameOf(populateNameAndAppTypeFieldsAtConsumerTable)
      runOnce(name) {
        MigrationOfConsumer.populateNamAndAppType(name)
      }
    }
    private def populateAzpAndSubFieldsAtConsumerTable(): Boolean = {
      val name = nameOf(populateAzpAndSubFieldsAtConsumerTable)
      runOnce(name) {
        MigrationOfConsumer.populateAzpAndSub(name)
      }
    }
    private def changeTypeOfAudFieldAtConsumerTable(): Boolean = {
      val name = nameOf(changeTypeOfAudFieldAtConsumerTable)
      runOnce(name) {
        MigrationOfConsumer.alterTypeofAud(name)
      }
    }
    private def alterTableMappedUserAuthContext(startedBeforeSchemifier: Boolean): Boolean = {
      if(startedBeforeSchemifier == true) {
        logger.warn(s"Migration.database.alterTableMappedUserAuthContext(true) cannot be run before Schemifier.")
        true
      } else {
        val name = nameOf(alterTableMappedUserAuthContext(startedBeforeSchemifier))
        runOnce(name) {
          MigrationOfMappedUserAuthContext.dropUniqueIndex(name)
        }
      }
    }
    private def alterTableMappedUserAuthContextUpdate(): Boolean = {
      val name = nameOf(MappedUserAuthContextUpdate)
      runOnce(name) {
        MigrationOfMappedUserAuthContextUpdate.dropUniqueIndex(name)
      }
    }
    private def populateTableBankAccountRouting(): Boolean = {
      val name = nameOf(populateTableBankAccountRouting)
      runOnce(name) {
        MigrationOfAccountRoutings.populate(name)
      }
    }
    private def populateSettlementBankAccounts(): Boolean = {
      val name = nameOf(populateSettlementBankAccounts)
      runOnce(name) {
        MigrationOfSettlementAccounts.populate(name)
      }
    }
    private def alterColumnStatusAtTableMappedConsent(): Boolean = {
      val name = nameOf(alterColumnStatusAtTableMappedConsent)
      runOnce(name) {
        MigrationOfMappedConsent.alterColumnStatus(name)
      }
    }
    private def alterColumnDetailsAtTableTransactionRequest(): Boolean = {
      val name = nameOf(alterColumnDetailsAtTableTransactionRequest)
      runOnce(name) {
        MigrationOfTransactionRequerst.alterColumnDetails(name)
      }
    }
    private def deleteDuplicatedRowsInTheTableUserAuthContext(startedBeforeSchemifier: Boolean): Boolean = {
      if(startedBeforeSchemifier == true) {
        logger.warn(s"Migration.database.deleteDuplicatedRowsInTheTableUserAuthContext(true) cannot be run before Schemifier.")
        true
      } else {
        val name = nameOf(deleteDuplicatedRowsInTheTableUserAuthContext(startedBeforeSchemifier))
        runOnce(name) {
          MigrationOfUserAuthContext.removeDuplicates(name)
        }
      }
    }
    private def populateTheFieldDeletedAtResourceUser(startedBeforeSchemifier: Boolean): Boolean = {
      if(startedBeforeSchemifier == true) {
        logger.warn(s"Migration.database.populateTheFieldDeletedAtResourceUser(true) cannot be run before Schemifier.")
        true
      } else {
        val name = nameOf(populateTheFieldDeletedAtResourceUser(startedBeforeSchemifier))
        runOnce(name) {
          MigrationOfResourceUser.populateNewFieldIsDeleted(name)
        }
      }
    }
    private def populateTheFieldIsActiveAtProductAttribute(startedBeforeSchemifier: Boolean): Boolean = {
      if(startedBeforeSchemifier == true) {
        logger.warn(s"Migration.database.populateTheFieldIsActiveAtProductAttribute(true) cannot be run before Schemifier.")
        true
      } else {
        val name = nameOf(populateTheFieldIsActiveAtProductAttribute(startedBeforeSchemifier))
        runOnce(name) {
          MigrationOfProductAttribute.populateTheFieldIsActive(name)
        }
      }
    }
    private def alterColumnUsernameProviderFirstnameAndLastnameAtAuthUser(startedBeforeSchemifier: Boolean): Boolean = {
      if(startedBeforeSchemifier == true) {
        logger.warn(s"Migration.database.alterColumnUsernameProviderFirstnameAndLastnameAtAuthUser(true) cannot be run before Schemifier.")
        true
      } else {
        val name = nameOf(alterColumnUsernameProviderFirstnameAndLastnameAtAuthUser(startedBeforeSchemifier))
        runOnce(name) {
          MigrationOfAuthUser.alterColumnUsernameProviderEmailFirstnameAndLastname(name)
        }
      }
    }
    private def populateMissingProviderAtAuthUser(startedBeforeSchemifier: Boolean): Boolean = {
      if(startedBeforeSchemifier == true) {
        logger.warn(s"Migration.database.populateMissingProviderAtAuthUser(true) cannot be run before Schemifier.")
        true
      } else {
        val name = nameOf(populateMissingProviderAtAuthUser(startedBeforeSchemifier))
        runOnce(name) {
          MigrationOfAuthUser.populateMissingProviderWithLocalIdentity(name)
        }
      }
    }
    private def alterColumnEmailAtResourceUser(startedBeforeSchemifier: Boolean): Boolean = {
      if(startedBeforeSchemifier == true) {
        logger.warn(s"Migration.database.alterColumnEmailAtResourceUser(true) cannot be run before Schemifier.")
        true
      } else {
        val name = nameOf(alterColumnEmailAtResourceUser(startedBeforeSchemifier))
        runOnce(name) {
          MigrationOfResourceUser.alterColumnEmail(name)
        }
      }
    }
    private def alterColumnNameAtProductFee(startedBeforeSchemifier: Boolean): Boolean = {
      if(startedBeforeSchemifier == true) {
        logger.warn(s"Migration.database.alterColumnNameAtProductFee(true) cannot be run before Schemifier.")
        true
      } else {
        val name = nameOf(alterColumnNameAtProductFee(startedBeforeSchemifier))
        runOnce(name) {
          MigrationOfProductFee.alterColumnProductFeeName(name)
        }
      }
    }
    private def addFastFirehoseAccountsView(startedBeforeSchemifier: Boolean): Boolean = {
      if(startedBeforeSchemifier == true) {
        logger.warn(s"Migration.database.addfastFirehoseAccountsView(true) cannot be run before Schemifier.")
        true
      } else {
        val name = nameOf(addFastFirehoseAccountsView(startedBeforeSchemifier))
        runOnce(name) {
          MigrationOfFastFireHoseView.addFastFireHoseView(name)
        }
      }
    }
    
    private def addFastFirehoseAccountsMaterializedView(startedBeforeSchemifier: Boolean): Boolean = {
      if(startedBeforeSchemifier == true) {
        logger.warn(s"Migration.database.addfastFirehoseAccountsMaterializedView(true) cannot be run before Schemifier.")
        true
      } else {
        val name = nameOf(addFastFirehoseAccountsMaterializedView(startedBeforeSchemifier))
        runOnce(name) {
          MigrationOfFastFireHoseMaterializedView.addFastFireHoseMaterializedView(name)
        }
      }
    }

    // Retire the fast-firehose SQL views (firehose -> account directory + ABAC). Runs after the create
    // migrations above, so a fresh DB creates-then-drops them and an existing DB just drops them. See
    // MigrationOfDropFastFireHoseViews.
    private def dropFastFirehoseAccountsViews(startedBeforeSchemifier: Boolean): Boolean = {
      if(startedBeforeSchemifier == true) {
        logger.warn(s"Migration.database.dropFastFirehoseAccountsViews(true) cannot be run before Schemifier.")
        true
      } else {
        val name = nameOf(dropFastFirehoseAccountsViews(startedBeforeSchemifier))
        runOnce(name) {
          MigrationOfDropFastFireHoseViews.dropFastFireHoseViews(name)
        }
      }
    }
    
    private def alterUserAuthContextColumnKeyAndValueLength(startedBeforeSchemifier: Boolean): Boolean = {
      if(startedBeforeSchemifier == true) {
        logger.warn(s"Migration.database.alterUserAuthContextColumnKeyAndValueLength(true) cannot be run before Schemifier.")
        true
      } else {
        val name = nameOf(alterUserAuthContextColumnKeyAndValueLength(startedBeforeSchemifier))
        runOnce(name) {
          MigrationOfUserAuthContextFieldLength.alterColumnKeyAndValueLength(name)
        }
      }
    }    
    
    private def alterMappedTransactionRequestFieldsLengthMigration(startedBeforeSchemifier: Boolean): Boolean = {
      if(startedBeforeSchemifier == true) {
        logger.warn(s"Migration.database.alterMappedTransactionRequestFieldsLengthMigration(true) cannot be run before Schemifier.")
        true
      } else {
        val name = nameOf(alterMappedTransactionRequestFieldsLengthMigration(startedBeforeSchemifier))
        runOnce(name) {
          MigrationOfMappedTransactionRequestFieldsLength.alterMappedTransactionRequestFieldsLength(name)
        }
      }
    }
    
    private def dropIndexAtColumnUsernameAtTableAuthUser(startedBeforeSchemifier: Boolean): Boolean = {
      if(startedBeforeSchemifier == true) {
        logger.warn(s"Migration.database.dropIndexAtColumnUsernameAtTableAuthUser(true) cannot be run before Schemifier.")
        true
      } else {
        val name = nameOf(dropIndexAtColumnUsernameAtTableAuthUser(startedBeforeSchemifier))
        runOnce(name) {
          MigrationOfAuthUser.dropIndexAtColumnUsername(name)
        }
      }
    }

    private def dropIndexAtUserAuthContext(): Boolean = {
      val name = nameOf(dropIndexAtUserAuthContext)
      runOnce(name) {
        MigrationOfMappedUserAuthContext.dropUniqueIndex(name)
      }
    }
    
    private def addAccountAccessConsumerId(): Boolean = {
      val name = nameOf(addAccountAccessConsumerId)
      runOnce(name) {
        MigrationOfAccountAccessAddedConsumerId.addAccountAccessConsumerId(name)
      }
    }

    private def alterWebhookColumnUrlLength(): Boolean = {
      val name = nameOf(alterWebhookColumnUrlLength)
      runOnce(name) {
        MigrationOfWebhookUrlFieldLength.alterColumnUrlLength(name)
      }
    }

    private def alterMappedCounterpartyDescriptionLength(): Boolean = {
      val name = nameOf(alterMappedCounterpartyDescriptionLength)
      runOnce(name) {
        MigrationOfMappedCounterpartyDescriptionLength.alterColumnDescriptionLength(name)
      }
    }

    private def alterMetricColumnUrlLength(): Boolean = {
      val name = nameOf(alterMetricColumnUrlLength)
      runOnce(name) {
        MigrationOfMetricTable.alterColumnCorrelationidLength(name)
      }
    }

    private def alterMetricArchiveColumnCorrelationidLength(): Boolean = {
      val name = nameOf(alterMetricArchiveColumnCorrelationidLength)
      runOnce(name) {
        MigrationOfMetricArchiveTable.alterColumnCorrelationidLength(name)
      }
    }

    private def dropConsentAuthContextDropIndex(): Boolean = {
      val name = nameOf(dropConsentAuthContextDropIndex)
      runOnce(name) {
        MigrationOfConsentAuthContextDropIndex.dropUniqueIndex(name)
      }
    }
  
    private def alterMappedExpectedChallengeAnswerChallengeTypeLength(): Boolean = {
      val name = nameOf(alterMappedExpectedChallengeAnswerChallengeTypeLength)
      runOnce(name) {
        MigrationOfMappedExpectedChallengeAnswerFieldLength.alterColumnLength(name)
      }
    }
  
    private def alterTransactionRequestChallengeChallengeTypeLength(): Boolean = {
      val name = nameOf(alterTransactionRequestChallengeChallengeTypeLength)
      runOnce(name) {
        MigrationOfTransactionRequestChallengeChallengeTypeLength.alterColumnChallengeChallengeTypeLength(name)
      }
    }  
  
    private def alterUserAttributeNameLength(): Boolean = {
      val name = nameOf(alterUserAttributeNameLength)
      runOnce(name) {
        MigrationOfUserAttributeNameFieldLength.alterNameLength(name)
      }
    }  
    private def alterMappedCustomerAttribute(startedBeforeSchemifier: Boolean): Boolean = {
      if(startedBeforeSchemifier == true) {
        logger.warn(s"Migration.database.alterMappedCustomerAttribute(true) cannot be run before Schemifier.")
        true
      } else {
        val name = nameOf(alterMappedCustomerAttribute(startedBeforeSchemifier))
        runOnce(name) {
          MigrationOfCustomerAttributes.alterColumnValue(name)
        }
      }
    }

    private def dropMappedBadLoginAttemptIndex(): Boolean = {
      val name = nameOf(dropMappedBadLoginAttemptIndex)
      runOnce(name) {
        MigrationOfMappedBadLoginAttemptDropIndex.dropUniqueIndex(name)
      }
    }

    private def alterCounterpartyLimitFieldType(): Boolean = {
      val name = nameOf(alterCounterpartyLimitFieldType)
      runOnce(name) {
        MigrationOfCounterpartyLimitFieldType.alterCounterpartyLimitFieldType(name)
      }
    }

    private def renameCustomerRoleNames(): Boolean = {
      val name = nameOf(renameCustomerRoleNames)
      runOnce(name) {
        MigrationOfCustomerRoleNames.renameCustomerRoles(name)
      }
    }

    private def addUniqueIndexOnResourceUserUserId(): Boolean = {
      val name = nameOf(addUniqueIndexOnResourceUserUserId)
      runOnce(name) {
        MigrationOfUserIdIndexes.addUniqueIndexOnResourceUserUserId(name)
      }
    }

    private def addIndexOnMappedMetricUserId(): Boolean = {
      val name = nameOf(addIndexOnMappedMetricUserId)
      runOnce(name) {
        MigrationOfUserIdIndexes.addIndexOnMappedMetricUserId(name)
      }
    }
    
    private def alterRoleNameLength(): Boolean = {
      val name = nameOf(alterRoleNameLength)
      runOnce(name) {
        MigrationOfRoleNameFieldLength.alterRoleNameLength(name)
      }
    }

    private def alterConsentRequestColumnConsumerIdLength(): Boolean = {
      val name = nameOf(alterConsentRequestColumnConsumerIdLength)
      runOnce(name) {
        MigrationOfConsentRequestConsumerIdFieldLength.alterColumnConsumerIdLength(name)
      }
    }

    private def alterMappedConsentColumnConsumerIdLength(): Boolean = {
      val name = nameOf(alterMappedConsentColumnConsumerIdLength)
      runOnce(name) {
        MigrationOfMappedConsent.alterColumnConsumerIdLength(name)
      }
    }

    private def alterMetricColumnConsumerIdLength(): Boolean = {
      val name = nameOf(alterMetricColumnConsumerIdLength)
      runOnce(name) {
        MigrationOfMetricConsumerIdFieldLength.alterColumnConsumerIdLength(name)
      }
    }

    private def addMetricView(startedBeforeSchemifier: Boolean): Boolean = {
      if(startedBeforeSchemifier == true) {
        logger.warn(s"Migration.database.addMetricView(true) cannot be run before Schemifier.")
        true
      } else {
        val name = nameOf(addMetricView(startedBeforeSchemifier))
        runOnce(name) {
          MigrationOfMetricView.addMetricView(name)
        }
      }
    }

    private def addConsentView(startedBeforeSchemifier: Boolean): Boolean = {
      if(startedBeforeSchemifier == true) {
        logger.warn(s"Migration.database.addConsentView(true) cannot be run before Schemifier.")
        true
      } else {
        val name = nameOf(addConsentView(startedBeforeSchemifier))
        runOnce(name) {
          MigrationOfConsentView.addConsentView(name)
        }
      }
    }

    private def updateConsentViewAddJwtPayload(startedBeforeSchemifier: Boolean): Boolean = {
      if(startedBeforeSchemifier == true) {
        logger.warn(s"Migration.database.updateConsentViewAddJwtPayload(true) cannot be run before Schemifier.")
        true
      } else {
        val name = nameOf(updateConsentViewAddJwtPayload(startedBeforeSchemifier))
        runOnce(name) {
          val viewResult = MigrationOfConsentView.addConsentView(name)
          MigrationOfConsentJwtPayload.backfillJwtPayload(name)
          viewResult
        }
      }
    }

    private def updateConsentViewAddJwtExpiresAt(startedBeforeSchemifier: Boolean): Boolean = {
      if(startedBeforeSchemifier == true) {
        logger.warn(s"Migration.database.updateConsentViewAddJwtExpiresAt(true) cannot be run before Schemifier.")
        true
      } else {
        val name = nameOf(updateConsentViewAddJwtExpiresAt(startedBeforeSchemifier))
        runOnce(name) {
          MigrationOfConsentView.addConsentView(name)
        }
      }
    }

    private def migrateConsentReferenceIdToUuid(startedBeforeSchemifier: Boolean): Boolean = {
      if(startedBeforeSchemifier == true) {
        logger.warn(s"Migration.database.migrateConsentReferenceIdToUuid(true) cannot be run before Schemifier.")
        true
      } else {
        val name = nameOf(migrateConsentReferenceIdToUuid(startedBeforeSchemifier))
        runOnce(name) {
          MigrationOfConsentReferenceIdUuid.migrate(name)
        }
      }
    }

    private def migrateMetricConsentReferenceId(startedBeforeSchemifier: Boolean): Boolean = {
      if(startedBeforeSchemifier == true) {
        logger.warn(s"Migration.database.migrateMetricConsentReferenceId(true) cannot be run before Schemifier.")
        true
      } else {
        val name = nameOf(migrateMetricConsentReferenceId(startedBeforeSchemifier))
        runOnce(name) {
          MigrationOfMetricConsentReferenceId.migrate(name)
        }
      }
    }

    private def addAccountAccessWithViewsView(startedBeforeSchemifier: Boolean): Boolean = {
      if(startedBeforeSchemifier == true) {
        logger.warn(s"Migration.database.addAccountAccessWithViewsView(true) cannot be run before Schemifier.")
        true
      } else {
        val name = nameOf(addAccountAccessWithViewsView(startedBeforeSchemifier))
        runOnce(name) {
          MigrationOfAccountAccessWithViewsView.addAccountAccessWithViewsView(name)
        }
      }
    }

    private def updateAccountAccessWithViewsViewUnionAll(startedBeforeSchemifier: Boolean): Boolean = {
      if(startedBeforeSchemifier == true) {
        logger.warn(s"Migration.database.updateAccountAccessWithViewsViewUnionAll(true) cannot be run before Schemifier.")
        true
      } else {
        val name = nameOf(updateAccountAccessWithViewsViewUnionAll(startedBeforeSchemifier))
        runOnce(name) {
          MigrationOfAccountAccessWithViewsView.addAccountAccessWithViewsView(name)
        }
      }
    }

    private def migrateChatRoomIsOpenRoom(): Boolean = {
      val name = nameOf(migrateChatRoomIsOpenRoom)
      runOnce(name) {
        MigrationOfChatRoomIsOpenRoom.migrateColumn(name)
      }
    }

    private def migrateChatRoomCreatedByAndLastMessageSender(): Boolean = {
      val name = nameOf(migrateChatRoomCreatedByAndLastMessageSender)
      runOnce(name) {
        MigrationOfChatRoomCreatedByAndLastMessageSender.migrateColumns(name)
      }
    }
  }

  /**
    * In this object we put functions dedicated to common database tasks.
    */
  object DbFunction {
    /**
      * This function is copied from the module net.liftweb.mapper.Schemifier
      */
    private def using[RetType <: Any, VarType <: ResultSet](f: => VarType)(f2: VarType => RetType): RetType = {
      val theVar = f
      try {
        f2(theVar)
      } finally {
        theVar.close()
      }
    }
    /**
      * This function is copied from the module "net.liftweb.mapper.Schemifier".
      * The purpose is to provide answer does a table exist at a database instance.
      * For instance migration scripts needs to differentiate update of an instance from build a new one from scratch.
     *  note: 07.05.2024 now. we get the connection from HikariDatasource.ds instead of Liftweb.
      */
    def tableExists (table: BaseMetaMapper, actualTableNames: HashMap[String, String] = new HashMap[String, String]()): Boolean = {
      DB.use(net.liftweb.util.DefaultConnectionIdentifier) {
        conn =>
          val md = conn.getMetaData
          val schema =  getDefaultSchemaName(conn)
      
          using(md.getTables(null, schema, null, null)){ rs =>
            def hasTable(rs: ResultSet): Boolean =
              if (!rs.next) false
              else rs.getString(3) match {
                case s if s.toLowerCase == table._dbTableNameLC.toLowerCase => actualTableNames(table._dbTableNameLC) = s; true
                case _ => hasTable(rs)
              }
    
            hasTable(rs)
          }
      }
    }
    def tableExistsByName(tableName: String): Boolean = {
      DB.use(net.liftweb.util.DefaultConnectionIdentifier) { conn =>
        val md = conn.getMetaData
        val schema = getDefaultSchemaName(conn)
        using(md.getTables(null, schema, null, null)) { rs =>
          def check(): Boolean =
            if (!rs.next) false
            else if (rs.getString(3).toLowerCase == tableName.toLowerCase) true
            else check()
          check()
        }
      }
    }

    /**
      * Declared max length of a (var)char column, via JDBC metadata (portable across H2/Postgres/MSSQL).
      * `None` if the column is absent or has no size (e.g. not a character type). Used by `alterColumn*`
      * migrations to ALTER only when the width actually differs — re-issuing `ALTER ... TYPE` to the same
      * width is rejected by Postgres when a view references the column (the recurring schema drift).
      */
    def columnMaxLength(tableNameLC: String, columnNameLC: String): Option[Int] = {
      DB.use(net.liftweb.util.DefaultConnectionIdentifier) {
        conn =>
          val md = conn.getMetaData
          val schema = getDefaultSchemaName(conn)
          using(md.getColumns(null, schema, null, null)) { rs =>
            def find(): Option[Int] =
              if (!rs.next) None
              else if (rs.getString(3).toLowerCase == tableNameLC.toLowerCase &&
                       rs.getString(4).toLowerCase == columnNameLC.toLowerCase) {
                val size = rs.getInt(7) // COLUMN_SIZE
                if (rs.wasNull) None else Some(size)
              } else find()
            find()
          }
      }
    }

    /**
      * The purpose is to provide answer does a procedure exist at a database instance.
      */
    def procedureExists(name: String): Boolean = {
      DB.use(net.liftweb.util.DefaultConnectionIdentifier) {
        conn =>
          val md = conn.getMetaData
          val schema = getDefaultSchemaName(conn)
          using(md.getProcedures(null, schema, null)){ rs =>
            def hasProcedure(rs: ResultSet): Boolean =
              if (!rs.next) false
              else rs.getString(3) match {
                case s if s.toLowerCase == name => true
                case _ => hasProcedure(rs)
              }
            hasProcedure(rs)
          }
      }
    }


    /**
      * The purpose is to provide info about the database in mapper mode.
      */
    def mapperDatabaseInfo: DatabaseInfoJson = {
      DB.use(net.liftweb.util.DefaultConnectionIdentifier) {
        conn =>
          val md = conn.getMetaData
          val productName = md.getDatabaseProductName()
          val productVersion = md.getDatabaseProductVersion()
          DatabaseInfoJson(product_name = productName, product_version = productVersion)
      }
    }

    /**
      * This function is copied from the module "net.liftweb.mapper.Schemifier".
      * 
      * Creates an SQL command and optionally executes it.
      *
      * @param performWrite Whether the SQL command should be executed.
      * @param logFunc Logger.
      * @param connection Database connection.
      * @param makeSql Factory for SQL command.
      *
      * @return SQL command.
      */
    def maybeWrite(performWrite: Boolean, logFunc: (=> AnyRef) => Unit) (makeSql: () => String) : String ={
      DB.use(net.liftweb.util.DefaultConnectionIdentifier) {
        conn =>
          val ct = makeSql()
          logger.trace("maybeWrite DDL: " + ct)
          if (performWrite) {
            logFunc(ct)
            val st = conn.createStatement
            try {
              st.execute(ct)
            } finally {
              st.close()
            }
          }
          ct
      }
    }

    /**
      * This function makes a copy on an table
      * @param table The table we want to back up
      * @return true in case of success or false otherwise
      */
    def makeBackUpOfTable(table: BaseMetaMapper): Boolean ={
      DB.use(net.liftweb.util.DefaultConnectionIdentifier) {
        conn =>
          try {
            val tableName = table.dbTableName
            val sdf = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS")
            val resultDate = new Date(System.currentTimeMillis())
            val dbDriver = APIUtil.getPropsValue("db.driver","org.h2.Driver")
            val sqlQuery = if (dbDriver.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver")) {
              s"SELECT * INTO ${tableName}_backup_${sdf.format(resultDate)} FROM $tableName;"
            }else{
              s"CREATE TABLE ${tableName}_backup_${sdf.format(resultDate)} AS (SELECT * FROM $tableName);"
            }
            DB.prepareStatement(sqlQuery, conn){
              stmt => stmt.execute() //statement.executeQuery() expects a resultset and you don't get one.
              // Use statement.execute() for an ALTER-statement to avoid this issue.
            }
            true
          } catch {
            case e: SQLException => 
              logger.error(e)
              false
          }
      }
    }
  }
  
}
