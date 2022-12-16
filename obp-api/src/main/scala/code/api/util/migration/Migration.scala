package code.api.util.migration

import java.sql.{ResultSet, SQLException}
import java.text.SimpleDateFormat
import java.util.Date

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
import net.liftweb.mapper.{BaseMetaMapper, DB, SuperConnection}
import net.liftweb.util.DefaultConnectionIdentifier

import scala.collection.immutable
import scala.collection.mutable.HashMap

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
    
    def executeScripts(startedBeforeSchemifier: Boolean): Boolean = executeScript {
      dummyScript()
      addAccountAccessConsumerId()
      populateTableViewDefinition()
      populateTableAccountAccess()
      generateAndPopulateMissingCustomerUUIDs(startedBeforeSchemifier)
      generateAndPopulateMissingConsumersUUIDs(startedBeforeSchemifier)
      populateTableRateLimiting()
      updateTableViewDefinition()
      bankAccountHoldersAndOwnerViewAccessInfo()
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
      alterColumnEmailAtResourceUser(startedBeforeSchemifier)
      alterColumnNameAtProductFee(startedBeforeSchemifier)
      addFastFirehoseAccountsView(startedBeforeSchemifier)
      addFastFirehoseAccountsMaterializedView(startedBeforeSchemifier)
      alterUserAuthContextColumnKeyAndValueLength(startedBeforeSchemifier)
      dropIndexAtColumnUsernameAtTableAuthUser(startedBeforeSchemifier)
      dropIndexAtUserAuthContext()
      alterWebhookColumnUrlLength()
      dropConsentAuthContextDropIndex()
      alterMappedExpectedChallengeAnswerChallengeTypeLength()
      alterTransactionRequestChallengeChallengeTypeLength()
      alterMappedCustomerAttribute(startedBeforeSchemifier)
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
    
    private def populateTableAccountAccess(): Boolean = {
      val name = nameOf(populateTableAccountAccess)
      runOnce(name) {
        TableAccountAccess.populate(name)
      }
    }

    private def populateTableViewDefinition(): Boolean = {
      val name = nameOf(populateTableViewDefinition)
      runOnce(name) {
        TableViewDefinition.populate(name)
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

    private def bankAccountHoldersAndOwnerViewAccessInfo(): Boolean = {
      val name = nameOf(bankAccountHoldersAndOwnerViewAccessInfo)
      runOnce(name) {
        BankAccountHoldersAndOwnerViewAccess.saveInfoBankAccountHoldersAndOwnerViewAccessInfo(name)
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
      */
    def tableExists (table: BaseMetaMapper, connection: SuperConnection, actualTableNames: HashMap[String, String] = new HashMap[String, String]()): Boolean = {
      val md = connection.getMetaData
      using(md.getTables(null, getDefaultSchemaName(connection), null, null)){ rs =>
        def hasTable(rs: ResultSet): Boolean =
          if (!rs.next) false
          else rs.getString(3) match {
            case s if s.toLowerCase == table._dbTableNameLC.toLowerCase => actualTableNames(table._dbTableNameLC) = s; true
            case _ => hasTable(rs)
          }

        hasTable(rs)
      }
    }
    /**
      * The purpose is to provide answer does a procedure exist at a database instance.
      */
    def procedureExists(name: String, connection: SuperConnection): Boolean = {
      val md = connection.getMetaData
      using(md.getProcedures(null, getDefaultSchemaName(connection), null)){ rs =>
        def hasProcedure(rs: ResultSet): Boolean =
          if (!rs.next) false
          else rs.getString(3) match {
            case s if s.toLowerCase == name => true
            case _ => hasProcedure(rs)
          }
        hasProcedure(rs)
      }
    }


    /**
      * The purpose is to provide info about the database in mapper mode.
      */
    def mapperDatabaseInfo(): DatabaseInfoJson = {
      val connection = DB.use(DefaultConnectionIdentifier){ conn => conn}
      val md = connection.getMetaData
      val productName = md.getDatabaseProductName()
      val productVersion = md.getDatabaseProductVersion()
      DatabaseInfoJson(product_name = productName, product_version = productVersion)
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
    def maybeWrite(performWrite: Boolean, logFunc: (=> AnyRef) => Unit, connection: SuperConnection) (makeSql: () => String) : String ={
      val ct = makeSql()
      logger.trace("maybeWrite DDL: "+ct)
      if (performWrite) {
        logFunc(ct)
        val st = connection.createStatement
        st.execute(ct)
        st.close
      }
      ct
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
            DB.prepareStatement(s"CREATE TABLE ${tableName}_backup_${sdf.format(resultDate)} AS (SELECT * FROM $tableName); ", conn){
              stmt => stmt.executeQuery()
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
