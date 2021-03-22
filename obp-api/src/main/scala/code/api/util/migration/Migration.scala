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
      case (true, false) => blockOfCode
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
      case false =>
        logger.warn(s"Migration.database.$name is executed at this instance but the corresponding log is not saved!!!!!!")
    }
  }
  
  object database {
    
    def executeScripts(): Boolean = executeScript {
      dummyScript()
      populateTableViewDefinition()
      populateTableAccountAccess()
      generateAndPopulateMissingCustomerUUIDs()
      generateAndPopulateMissingConsumersUUIDs()
      populateTableRateLimiting()
      updateTableViewDefinition()
      bankAccountHoldersAndOwnerViewAccessInfo()
      alterTableMappedConsent()
      alterColumnChallengeAtTableMappedConsent()
      alterTableOpenIDConnectToken()
      alterTableMappedUserAuthContext()
      alterTableMappedUserAuthContextUpdate()
      populateNameAndAppTypeFieldsAtConsumerTable()
      populateAzpAndSubFieldsAtConsumerTable()
      populateTableBankAccountRouting()
      populateSettlementBankAccounts()
      alterColumnStatusAtTableMappedConsent()
      alterColumnDetailsAtTableTransactionRequest()
      deleteDuplicatedRowsInTheTableUserAuthContext()
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
    
    private def generateAndPopulateMissingCustomerUUIDs(): Boolean = {
      val name = nameOf(generateAndPopulateMissingCustomerUUIDs)
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

    private def generateAndPopulateMissingConsumersUUIDs(): Boolean = {
      val name = nameOf(generateAndPopulateMissingConsumersUUIDs)
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
    private def alterTableMappedUserAuthContext(): Boolean = {
      val name = nameOf(alterTableMappedUserAuthContext)
      runOnce(name) {
        MigrationOfMappedUserAuthContext.dropUniqueIndex(name)
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
    private def deleteDuplicatedRowsInTheTableUserAuthContext(): Boolean = {
      val name = nameOf(deleteDuplicatedRowsInTheTableUserAuthContext)
      runOnce(name) {
        MigrationOfUserAuthContext.removeDuplicates(name)
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
