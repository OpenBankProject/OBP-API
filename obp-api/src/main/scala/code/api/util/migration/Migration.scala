package code.api.util.migration

import code.api.util.APIUtil
import code.api.util.APIUtil.getPropsAsBoolValue
import code.consumer.Consumers
import code.customer.Customer
import code.migration.MigrationScriptLogProvider
import code.util.Helper.MdcLoggable
import com.github.dwickern.macros.NameOf.nameOf

object Migration extends MdcLoggable {
  
  private val execute = getPropsAsBoolValue("migration_scripts.execute", false)

  private def executeScript(blockOfCode: => Boolean): Boolean = {
    if(execute) blockOfCode else execute
  }
  
  private def runOnce(name: String)(blockOfCode: => Boolean): Boolean = {
    MigrationScriptLogProvider.migrationScriptLogProvider.vend.isExecuted(name) match {
      case false => blockOfCode
      case true => true
    }
  }
  
  def saveLog(name: String, commitId: String, isSuccessful: Boolean, startDate: Long, endDate: Long, comment: String) = {
    MigrationScriptLogProvider.migrationScriptLogProvider.vend.saveLog(name, commitId, isSuccessful, startDate, endDate, comment) match {
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
      val name = "Dummy test script"
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
      Customer.customerProvider.vend.populateMissingUUIDs()
    }

    private def generateAndPopulateMissingConsumersUUIDs(): Boolean = {
      Consumers.consumers.vend.populateMissingUUIDs()
    }
    
  }
  
}
