package code.api.util

import code.api.util.APIUtil.getPropsAsBoolValue
import code.consumer.Consumers
import code.customer.Customer
import code.migration.MigrationScriptLogProvider
import code.model.dataAccess.{MappedBank, MappedBankAccount, ViewImpl, ViewPrivileges}
import code.util.Helper.MdcLoggable
import code.views.system.AccountAccess
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.mapper.{By, ByList}

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
  
  private def saveLog(name: String, commitId: String, isSuccessful: Boolean, startDate: Long, endDate: Long, comment: String) = {
    MigrationScriptLogProvider.migrationScriptLogProvider.vend.saveLog(name, commitId, isSuccessful, startDate, endDate, comment) match {
      case true =>
      case false =>
        logger.warn(s"Migration.database.$name is executed at this instance but the corresponding log is not saved!!!!!!")
    }
  }
  
  object database {
    
    def executeScripts(): Boolean = executeScript {
      dummyScript()
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
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val views = ViewImpl.findAll()
        
        // Delete all rows at the table
        AccountAccess.bulkDelete_!!()
        
        // Insert rows into table "accountaccess" based on data in the tables viewimpl and viewprivileges
        val insertedRows: List[Boolean] =
          for {
            view <- views
            permission <- ViewPrivileges.findAll(By(ViewPrivileges.view, view.id))
          } yield {
            val bankPrimaryKey = MappedBank.find(By(MappedBank.permalink, view.bankPermalink.get))
            val accountPrimaryKey = MappedBankAccount.find(By(MappedBankAccount.theAccountId, view.accountPermalink.get))
            AccountAccess
              .create
              .bank(bankPrimaryKey)
              .account(accountPrimaryKey)
              .user(permission.user.get)
              .view(permission.view.get)
              .save()
          }
        val isSuccessful = insertedRows.forall(_ == true)
        val accountAccess = AccountAccess.findAll()
        val accountAccessSize = accountAccess.size
        val viewPrivileges = ViewPrivileges.findAll()
        val viewPrivilegesSize = viewPrivileges.size
        
        // We want to find foreign keys "viewprivileges.view_c" which cannot be mapped to "viewimpl.id_"
        val x1 = ViewPrivileges.findAll(ByList(ViewPrivileges.view, views.map(_.id))).map(_.view.get).distinct.sortWith(_>_)
        val x2 = viewPrivileges.map(_.view.get).distinct.sortWith(_>_)
        val deadForeignKeys = x2.diff(x1)

        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""Account access size: $accountAccessSize;
             |View privileges size: $viewPrivilegesSize;
             |List of dead foreign keys at the field ViewPrivileges.view_c: ${deadForeignKeys.mkString(",")};
             |Duration: ${endDate - startDate} ms;
             """.stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
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
