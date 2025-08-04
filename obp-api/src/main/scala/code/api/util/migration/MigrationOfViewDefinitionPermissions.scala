//package code.api.util.migration
//
//import code.api.Constant.{DEFAULT_CAN_GRANT_AND_REVOKE_ACCESS_TO_VIEWS, SYSTEM_OWNER_VIEW_ID, SYSTEM_STANDARD_VIEW_ID}
//import code.api.util.APIUtil
//import code.api.util.migration.Migration.{DbFunction, saveLog}
//import code.views.system.ViewDefinition
//import net.liftweb.mapper.{By, DB, NullRef}
//import net.liftweb.util.DefaultConnectionIdentifier
//
//object MigrationOfViewDefinitionPermissions {
//  def populate(name: String): Boolean = {
//    DbFunction.tableExists(ViewDefinition) match {
//      case true =>
//        val startDate = System.currentTimeMillis()
//        val commitId: String = APIUtil.gitCommit
//        val ownerView = ViewDefinition.find(
//          NullRef(ViewDefinition.bank_id),
//          NullRef(ViewDefinition.account_id),
//          By(ViewDefinition.view_id, SYSTEM_OWNER_VIEW_ID),
//          By(ViewDefinition.isSystem_,true)
//        ).map(view => 
//          view
//            .canSeeTransactionRequestTypes_(true)
//            .canSeeTransactionRequests_(true)
//            .canSeeAvailableViewsForBankAccount_(true)
//            .canUpdateBankAccountLabel_(true)
//            .canSeeViewsWithPermissionsForOneUser_(true)
//            .canSeeViewsWithPermissionsForAllUsers_(true)
//            .canCreateCustomView_(false)
//            .canDeleteCustomView_(false)
//            .canUpdateCustomView_(false)
//            .canGrantAccessToCustomViews_(false)
//            .canRevokeAccessToCustomViews_(false)
//            .canGrantAccessToViews_(DEFAULT_CAN_GRANT_AND_REVOKE_ACCESS_TO_VIEWS.mkString(","))
//            .canRevokeAccessToViews_(DEFAULT_CAN_GRANT_AND_REVOKE_ACCESS_TO_VIEWS.mkString(","))
//            .save
//        )
//        
//        val standardView = ViewDefinition.find(
//          NullRef(ViewDefinition.bank_id),
//          NullRef(ViewDefinition.account_id),
//          By(ViewDefinition.view_id, SYSTEM_STANDARD_VIEW_ID),
//          By(ViewDefinition.isSystem_,true)
//        ).map(view => 
//          view
//            .canSeeTransactionRequestTypes_(true)
//            .canSeeTransactionRequests_(true)
//            .canSeeAvailableViewsForBankAccount_(true)
//            .canUpdateBankAccountLabel_(true)
//            .canSeeViewsWithPermissionsForOneUser_(true)
//            .canSeeViewsWithPermissionsForAllUsers_(true)
//            .canCreateCustomView_(false)
//            .canDeleteCustomView_(false)
//            .canUpdateCustomView_(false)
//            .canGrantAccessToCustomViews_(false)
//            .canRevokeAccessToCustomViews_(false)
//            .canGrantAccessToViews_(DEFAULT_CAN_GRANT_AND_REVOKE_ACCESS_TO_VIEWS.mkString(","))
//            .canRevokeAccessToViews_(DEFAULT_CAN_GRANT_AND_REVOKE_ACCESS_TO_VIEWS.mkString(","))
//            .save
//        )
//
//      
//        val isSuccessful = ownerView.isDefined && standardView.isDefined
//        val endDate = System.currentTimeMillis()
//
//        val comment: String =
//          s"""ViewDefinition system $SYSTEM_OWNER_VIEW_ID and $SYSTEM_STANDARD_VIEW_ID views, update the following rows to true:
//             |${ViewDefinition.canSeeTransactionRequestTypes_.dbColumnName}
//             |${ViewDefinition.canSeeTransactionRequests_.dbColumnName}
//             |${ViewDefinition.canSeeAvailableViewsForBankAccount_.dbColumnName}
//             |${ViewDefinition.canUpdateBankAccountLabel_.dbColumnName}
//             |${ViewDefinition.canCreateCustomView_.dbColumnName}
//             |${ViewDefinition.canDeleteCustomView_.dbColumnName}
//             |${ViewDefinition.canUpdateCustomView_.dbColumnName}
//             |${ViewDefinition.canSeeViewsWithPermissionsForAllUsers_.dbColumnName}
//             |${ViewDefinition.canSeeViewsWithPermissionsForOneUser_.dbColumnName}
//             |${ViewDefinition.canGrantAccessToCustomViews_.dbColumnName}
//             |${ViewDefinition.canRevokeAccessToCustomViews_.dbColumnName}
//             |${ViewDefinition.canGrantAccessToViews_.dbColumnName}
//             |${ViewDefinition.canRevokeAccessToViews_.dbColumnName}
//             |Duration: ${endDate - startDate} ms;
//             """.stripMargin
//        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
//        isSuccessful
//        
//      case false =>
//        val startDate = System.currentTimeMillis()
//        val commitId: String = APIUtil.gitCommit
//        val isSuccessful = false
//        val endDate = System.currentTimeMillis()
//        val comment: String =
//          s"""ViewDefinition does not exist!""".stripMargin
//        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
//        isSuccessful
//    }
//  }
//}
