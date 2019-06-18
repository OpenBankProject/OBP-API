package code.api.util.migration

import java.sql.{ResultSet, SQLException}
import java.text.SimpleDateFormat
import java.util.Date

import code.api.util.APIUtil
import code.api.util.APIUtil.{getPropsAsBoolValue, getPropsValue}
import code.consumer.Consumers
import code.customer.CustomerX
import code.migration.MigrationScriptLogProvider
import code.util.Helper.MdcLoggable
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.mapper.Schemifier.getDefaultSchemaName
import net.liftweb.mapper.{BaseMetaMapper, DB, SuperConnection}

import scala.collection.immutable
import scala.collection.mutable.HashMap

object Migration extends MdcLoggable {
  
  private val execute = getPropsAsBoolValue("migration_scripts.execute", false)
  private val scriptsToExecute: immutable.Seq[String] = getPropsValue("list_of_migration_scripts_to_execute").toList.map(_.split(",")).flatten

  private def executeScript(blockOfCode: => Boolean): Boolean = {
    if(execute) blockOfCode else execute
  }
  
  private def runOnce(name: String)(blockOfCode: => Boolean): Boolean = {
    val toExecute = scriptsToExecute.contains(name)
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
      CustomerX.customerProvider.vend.populateMissingUUIDs()
    }

    private def generateAndPopulateMissingConsumersUUIDs(): Boolean = {
      Consumers.consumers.vend.populateMissingUUIDs()
    }
    
  }

  /**
    * In this object we put functions dedcated to common database tasks.
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
