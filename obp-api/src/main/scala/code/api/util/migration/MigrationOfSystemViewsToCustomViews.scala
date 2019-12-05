package code.api.util.migration

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.views.system.{AccountAccess, ViewDefinition}
import net.liftweb.mapper.{By, DB, NotNullRef, NullRef}
import net.liftweb.util.DefaultConnectionIdentifier

object UpdateTableViewDefinition {
  
  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")
  
  def populate(name: String): Boolean = {
    DbFunction.tableExists(ViewDefinition, (DB.use(DefaultConnectionIdentifier){ conn => conn})) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val views = ViewDefinition.findAll(
          NotNullRef(ViewDefinition.bank_id),
          NotNullRef(ViewDefinition.account_id),
          NotNullRef(ViewDefinition.view_id)
        )
        val instanceSpecificSystemViews = ViewDefinition.findAll(
          NullRef(ViewDefinition.bank_id),
          NullRef(ViewDefinition.account_id),
          By(ViewDefinition.isSystem_, true)
        )
        val bankSpecificSystemViews = ViewDefinition.findAll(
          NotNullRef(ViewDefinition.bank_id),
          NullRef(ViewDefinition.account_id),
          By(ViewDefinition.isSystem_, true)
        )

        // Make back up
        DbFunction.makeBackUpOfTable(ViewDefinition)
    
        // Update rows into table "viewdefinition"
        val updatedRows: List[Boolean] =
          for {
            view <- views
          } yield {
            view
              .isSystem_(false)
              .save
          }

        // Make back up
        DbFunction.makeBackUpOfTable(AccountAccess)

        // Update rows into table "AccountAccess"
        val updatedAccountAccessRows =
          for {
            view <- views
            accountAccess <- AccountAccess.find(By(AccountAccess.view_fk, view.id)).toList
          } yield {
            accountAccess.view_id(view.viewId.value).save()
          }
        
        val isSuccessful = views.forall(_.isSystem == false)
        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""Number of updated rows at table ViewDefinition: ${updatedRows.size}
             |Number of instance specific system views: ${instanceSpecificSystemViews.size}
             |Number of bank specific system views: ${bankSpecificSystemViews.size}
             |Number of updated rows at table AccountAccess: ${updatedAccountAccessRows.size}
             |""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
        
      case false =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val isSuccessful = false
        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""ViewDefinition table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}
