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
      MigrationScriptLogProvider.migrationScriptLogProvider.vend.isExecuted(name) match {
        case false =>
          val startDate = System.currentTimeMillis()
          val commitId: String = APIUtil.gitCommit
          val comment: String = "dummy comment"
          val isSuccessful = true
          val endDate = System.currentTimeMillis()
          saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
          isSuccessful
        case true =>
          true
      }
    }
    
    private def populateTableAccountAccess(): Boolean = {
      val name = nameOf(populateTableAccountAccess)
      MigrationScriptLogProvider.migrationScriptLogProvider.vend.isExecuted(name) match {
        case false =>
          val startDate = System.currentTimeMillis()
          val commitId: String = APIUtil.gitCommit
          val views = ViewImpl.findAll()
          AccountAccess.bulkDelete_!!()
          val x =
            for {
              view <- views
              permission: ViewPrivileges <- ViewPrivileges.findAll(By(ViewPrivileges.view, view.id))
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
          val endDate = System.currentTimeMillis()
          val accountAccess = AccountAccess.findAll()
          val accountAccessSize = accountAccess.size
          val viewPrivileges = ViewPrivileges.findAll()
          val viewPrivilegesSize = viewPrivileges.size
          val x1 = ViewPrivileges.findAll(ByList(ViewPrivileges.view, views.map(_.id))).map(_.view.get).distinct.sortWith(_>_)
          val x2 = viewPrivileges.map(_.view.get).distinct.sortWith(_>_)
          val deadForeignKeys = x2.diff(x1)
          val comment: String =
            s"""Account access size: $accountAccessSize. 
               |View privileges size: $viewPrivilegesSize. 
               |List of dead foreign keys at the field ViewPrivileges.view_c: ${deadForeignKeys.mkString(",")}
             """.stripMargin
          val isSuccessful = true
          saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
          isSuccessful
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
