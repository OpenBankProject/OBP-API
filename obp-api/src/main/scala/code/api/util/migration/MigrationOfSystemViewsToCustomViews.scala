package code.api.util.migration

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.ratelimiting.RateLimiting
import code.views.system.ViewDefinition
import net.liftweb.mapper.{By, DB, NotNullRef}
import net.liftweb.util.DefaultConnectionIdentifier

object UpdateTableViewDefinition {
  
  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")
  
  def populate(name: String): Boolean = {
    DbFunction.tableExists(RateLimiting, (DB.use(DefaultConnectionIdentifier){ conn => conn})) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val views = ViewDefinition.findAll(
          NotNullRef(ViewDefinition.bank_id),
          NotNullRef(ViewDefinition.account_id),
          NotNullRef(ViewDefinition.view_id)
        )

        // Make back up
        DbFunction.makeBackUpOfTable(ViewDefinition)
    
        // Update rows into table "viewdefinition"
        val updatedRows: List[Boolean] =
          for {
            view <- views
            (name, viewId) = (view.name, view.viewId.value)
          } yield {
            view
              .name_(if (name.startsWith("_")) name else "_" + name)
              .view_id(if (viewId.startsWith("_")) viewId else "_" + viewId)
              .isSystem_(false)
              .save
          }
        val isSuccessful = views.forall(_.name.startsWith("_"))
        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""Number of updated rows at table ViewDefinition: ${updatedRows.size}""".stripMargin
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
