package code.api.util

import code.api.util.APIUtil.getPropsAsBoolValue
import code.consumer.Consumers
import code.customer.Customer
import code.migration.MigrationScriptLogProvider
import code.util.Helper.MdcLoggable

object Migration extends MdcLoggable {
  
  private val execute = getPropsAsBoolValue("migration_scripts.execute", false)

  private def executeScript(blockOfCode: => Boolean): Boolean = {
    if(execute) blockOfCode else execute
  }
  
  object database {
    
    def executeScripts(): Boolean = executeScript {
      dummyScript()
    }
    
    private def dummyScript(): Boolean = {
      val name = "Dummy test script"
      MigrationScriptLogProvider.migrationScriptLogProvider.vend.isExecuted(name) match {
        case false =>
          val commitId: String = APIUtil.gitCommit
          val isExecuted = true
          val executedAt = System.currentTimeMillis()
          MigrationScriptLogProvider.migrationScriptLogProvider.vend.saveLog(name, commitId, isExecuted, executedAt) match {
            case true =>
            case false =>
              logger.warn("Migration.database.dummyScript is executed at this instance but the corresponding log is not saved!!!!!!")
          }
          isExecuted
        case true =>
          true
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
