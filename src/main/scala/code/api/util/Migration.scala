package code.api.util

import code.api.util.APIUtil.getPropsAsBoolValue
import code.customer.Customer

object Migration {
  
  private val execute = getPropsAsBoolValue("migration_scripts.execute", false)

  private def executeScript(blockOfCode: => Boolean): Boolean = {
    if(execute) blockOfCode else execute
  }
  
  object database {
    
    def generateAndPopulateMissingCustomerUUIDs(): Boolean = executeScript {
      Customer.customerProvider.vend.populateMissingUUIDs()
    }
    
  }
  
}
