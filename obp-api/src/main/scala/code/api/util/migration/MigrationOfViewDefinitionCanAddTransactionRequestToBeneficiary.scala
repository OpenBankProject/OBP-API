//package code.api.util.migration
//
//import code.api.Constant.SYSTEM_OWNER_VIEW_ID
//
//import java.time.format.DateTimeFormatter
//import java.time.{ZoneId, ZonedDateTime}
//import code.api.util.APIUtil
//import code.api.util.migration.Migration.{DbFunction, saveLog}
//import code.model.Consumer
//import code.views.system.ViewDefinition
//
//object MigrationOfViewDefinitionCanAddTransactionRequestToBeneficiary {
//  
//  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
//  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
//  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")
//  
//  def populateTheField(name: String): Boolean = {
//    DbFunction.tableExists(ViewDefinition) match {
//      case true =>
//        val startDate = System.currentTimeMillis()
//        val commitId: String = APIUtil.gitCommit
//        var isSuccessful = false
//
//        val view = ViewDefinition.findSystemView(SYSTEM_OWNER_VIEW_ID).map(_.canAddTransactionRequestToBeneficiary_(true).saveMe())
//
//        
//        val endDate = System.currentTimeMillis()
//        val comment: String =
//          s"""set $SYSTEM_OWNER_VIEW_ID.canAddTransactionRequestToBeneficiary_ to {true}""".stripMargin
//        val value = view.map(_.canAddTransactionRequestToBeneficiary_.get).getOrElse(false)
//        isSuccessful = value
//        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
//        isSuccessful
//
//      case false =>
//        val startDate = System.currentTimeMillis()
//        val commitId: String = APIUtil.gitCommit
//        val isSuccessful = false
//        val endDate = System.currentTimeMillis()
//        val comment: String =
//          s"""${Consumer._dbTableNameLC} table does not exist""".stripMargin
//        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
//        isSuccessful
//    }
//  }
//}
