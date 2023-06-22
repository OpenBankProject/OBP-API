package code.api.util.migration

import code.api.Constant.SYSTEM_OWNER_VIEW_ID
import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.views.system.ViewDefinition
import net.liftweb.mapper.{By, DB, NullRef}
import net.liftweb.util.DefaultConnectionIdentifier

object MigrationOfViewDefinitionPermissions {
  def populate(name: String): Boolean = {
    DbFunction.tableExists(ViewDefinition, (DB.use(DefaultConnectionIdentifier){ conn => conn})) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val ownerView = ViewDefinition.find(
          NullRef(ViewDefinition.bank_id),
          NullRef(ViewDefinition.account_id),
          By(ViewDefinition.view_id, SYSTEM_OWNER_VIEW_ID),
          By(ViewDefinition.isSystem_,true)
        ).map(view => 
          view
            .canSeeTransactionRequestTypes_(true)
            .canSeeTransactionRequests_(true)
            .canSeeAvailableViewsForBankAccount_(true)
            .save
        ).head

      
        val isSuccessful = ownerView
        val endDate = System.currentTimeMillis()

        val comment: String =
          s"""ViewDefinition system owner view, update the following rows to true:
             |${ViewDefinition.canSeeTransactionRequestTypes_.dbColumnName}
             |${ViewDefinition.canSeeTransactionRequests_.dbColumnName}
             |${ViewDefinition.canSeeAvailableViewsForBankAccount_.dbColumnName}
             |Duration: ${endDate - startDate} ms;
             """.stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
      case false =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val isSuccessful = false
        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""ViewDefinition does not exist!""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}
