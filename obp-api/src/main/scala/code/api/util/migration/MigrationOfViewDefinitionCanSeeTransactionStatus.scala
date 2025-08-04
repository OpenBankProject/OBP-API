//package code.api.util.migration
//
//import code.api.Constant._
//import code.api.util.APIUtil
//import code.api.util.migration.Migration.{DbFunction, saveLog}
//import code.model.Consumer
//import code.views.system.ViewDefinition
//
//import java.time.format.DateTimeFormatter
//import java.time.{ZoneId, ZonedDateTime}
//
//object MigrationOfViewDefinitionCanSeeTransactionStatus {
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
//        val view = ViewDefinition.findSystemView(SYSTEM_OWNER_VIEW_ID).map(_.canSeeTransactionStatus_(true).saveMe())
//        val view1 = ViewDefinition.findSystemView(SYSTEM_READ_TRANSACTIONS_BERLIN_GROUP_VIEW_ID).map(_.canSeeTransactionStatus_(true).saveMe())
//        val view2 = ViewDefinition.findSystemView(SYSTEM_READ_TRANSACTIONS_DETAIL_VIEW_ID).map(_.canSeeTransactionStatus_(true).saveMe())
//        val view3 = ViewDefinition.findSystemView(SYSTEM_READ_TRANSACTIONS_DEBITS_VIEW_ID).map(_.canSeeTransactionStatus_(true).saveMe())
//        val view4 = ViewDefinition.findSystemView(SYSTEM_READ_TRANSACTIONS_BASIC_VIEW_ID).map(_.canSeeTransactionStatus_(true).saveMe())
//        val view8 = ViewDefinition.findSystemView(SYSTEM_AUDITOR_VIEW_ID).map(_.canSeeTransactionStatus_(true).saveMe())
//        val view5 = ViewDefinition.findSystemView(SYSTEM_STAGE_ONE_VIEW_ID).map(_.canSeeTransactionStatus_(true).saveMe())
//        val view6 = ViewDefinition.findSystemView(SYSTEM_STANDARD_VIEW_ID).map(_.canSeeTransactionStatus_(true).saveMe())
//        val view7 = ViewDefinition.findSystemView(SYSTEM_FIREHOSE_VIEW_ID).map(_.canSeeTransactionStatus_(true).saveMe())
//        val view9 = ViewDefinition.findSystemView(SYSTEM_ACCOUNTANT_VIEW_ID).map(_.canSeeTransactionStatus_(true).saveMe())
//        val view10 = ViewDefinition.findSystemView(SYSTEM_MANAGE_CUSTOM_VIEWS_VIEW_ID).map(_.canSeeTransactionStatus_(true).saveMe())
//
//        
//        val endDate = System.currentTimeMillis()
//        val comment: String =
//          s"""set $SYSTEM_OWNER_VIEW_ID.canSeeTransactionStatus_ to {true};
//             |set $SYSTEM_READ_TRANSACTIONS_BERLIN_GROUP_VIEW_ID.canSeeTransactionStatus_ to {true}
//             |set $SYSTEM_READ_TRANSACTIONS_DETAIL_VIEW_ID.canSeeTransactionStatus_ to {true};
//             |set $SYSTEM_READ_TRANSACTIONS_DEBITS_VIEW_ID.canSeeTransactionStatus_ to {true};
//             |set $SYSTEM_READ_TRANSACTIONS_BASIC_VIEW_ID.canSeeTransactionStatus_ to {true};
//             |set $SYSTEM_AUDITOR_VIEW_ID.canSeeTransactionStatus_ to {true};
//             |set $SYSTEM_STAGE_ONE_VIEW_ID.canSeeTransactionStatus_ to {true};
//             |set $SYSTEM_STANDARD_VIEW_ID.canSeeTransactionStatus_ to {true};
//             |set $SYSTEM_FIREHOSE_VIEW_ID.canSeeTransactionStatus_ to {true};
//             |set $SYSTEM_ACCOUNTANT_VIEW_ID.canSeeTransactionStatus_ to {true};
//             |set $SYSTEM_MANAGE_CUSTOM_VIEWS_VIEW_ID.canSeeTransactionStatus_ to {true};
//             |""".stripMargin
//        val value = view.map(_.canSeeTransactionStatus_.get).getOrElse(false)
//        val value1 = view1.map(_.canSeeTransactionStatus_.get).getOrElse(false)
//        val value2 = view1.map(_.canSeeTransactionStatus_.get).getOrElse(false)
//        val value3 = view3.map(_.canSeeTransactionStatus_.get).getOrElse(false)
//        val value4 = view4.map(_.canSeeTransactionStatus_.get).getOrElse(false)
//        val value5 = view5.map(_.canSeeTransactionStatus_.get).getOrElse(false)
//        val value6 = view6.map(_.canSeeTransactionStatus_.get).getOrElse(false)
//        val value7 = view7.map(_.canSeeTransactionStatus_.get).getOrElse(false)
//        val value8 = view8.map(_.canSeeTransactionStatus_.get).getOrElse(false)
//        val value9 = view9.map(_.canSeeTransactionStatus_.get).getOrElse(false)
//        val value10 = view10.map(_.canSeeTransactionStatus_.get).getOrElse(false)
//        
//        isSuccessful = value && value1 && value2 && value3 && value4 && value5 && value6 && value7 && value8 && value9 && value10
//        
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
