package code.api.util.migration

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.model.Consumer
import code.productAttributeattribute.MappedProductAttribute
import net.liftweb.mapper.DB
import net.liftweb.util.DefaultConnectionIdentifier

object MigrationOfProductAttribute {
  
  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")
  
  def populateTheFieldIsActive(name: String): Boolean = {
    DbFunction.tableExists(MappedProductAttribute) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        // Make back up
        DbFunction.makeBackUpOfTable(MappedProductAttribute)

        val emptyDeletedField = 
          for {
            attribute <- MappedProductAttribute.findAll() if attribute.isActive.isEmpty == true
          } yield {
            attribute.IsActive(true).saveMe()
          }
        
        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""Updated number of rows: 
             |${emptyDeletedField.size}
             |""".stripMargin
        isSuccessful = true
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful

      case false =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val isSuccessful = false
        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""${Consumer._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}
