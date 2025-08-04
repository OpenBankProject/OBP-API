package code.views.system

import code.api.Constant.{CAN_GRANT_ACCESS_TO_VIEWS, CAN_REVOKE_ACCESS_TO_VIEWS}
import code.util.UUIDString
import com.openbankproject.commons.model._
import net.liftweb.common.Box
import net.liftweb.common.Box.tryo
import net.liftweb.mapper._


class ViewPermission extends LongKeyedMapper[ViewPermission] with IdPK with CreatedUpdated {
  def getSingleton = ViewPermission
  object bank_id extends MappedString(this, 255)
  object account_id extends MappedString(this, 255)
  object view_id extends UUIDString(this)
  object permission extends MappedString(this, 255)
  
  //this is for special permissions like CAN_REVOKE_ACCESS_TO_VIEWS and CAN_GRANT_ACCESS_TO_VIEWS, it will be a list of view ids , 
  // eg: owner,auditor,accountant,firehose,standard,StageOne,ManageCustomViews,ReadAccountsBasic
  object extraData extends MappedString(this, 1024) 
}
object ViewPermission extends ViewPermission with LongKeyedMetaMapper[ViewPermission] {
  override def dbIndexes: List[BaseIndex[ViewPermission]] = UniqueIndex(bank_id, account_id, view_id, permission) :: super.dbIndexes
  
  def findCustomViewPermissions(bankId: BankId, accountId: AccountId, viewId: ViewId): List[ViewPermission] =
    ViewPermission.findAll(
      By(ViewPermission.bank_id, bankId.value),
      By(ViewPermission.account_id, accountId.value),
      By(ViewPermission.view_id, viewId.value)
    )  
    
  def findSystemViewPermissions(viewId: ViewId): List[ViewPermission] =
    ViewPermission.findAll(
      NullRef(ViewPermission.bank_id),
      NullRef(ViewPermission.account_id),
      By(ViewPermission.view_id, viewId.value)
    )
    
  def findCustomViewPermission(bankId: BankId, accountId: AccountId, viewId: ViewId, permission: String): Box[ViewPermission] =
    ViewPermission.find(
      By(ViewPermission.bank_id, bankId.value),
      By(ViewPermission.account_id, accountId.value),
      By(ViewPermission.view_id, viewId.value),
      By(ViewPermission.permission,permission)
    )  
    
  def findSystemViewPermission(viewId: ViewId, permission: String): Box[ViewPermission] =
    ViewPermission.find(
      NullRef(ViewPermission.bank_id),
      NullRef(ViewPermission.account_id),
      By(ViewPermission.view_id, viewId.value),
      By(ViewPermission.permission,permission),
    )

  def createSystemViewPermission(viewId: ViewId, permissionName: String, extraData: Option[List[String]]): Box[ViewPermission] = {
    tryo {
      ViewPermission.create
        .bank_id(null)
        .account_id(null)
        .view_id(viewId.value)
        .permission(permissionName)
        .extraData(extraData.map(_.mkString(",")).getOrElse(null))
        .saveMe
    }
  }

  /**
   * Finds the permissions for a given view, if it is sytem view, 
   * it will search in system view permission, otherwise it will search in custom view permissions.
   * @param view
   * @return
   */
  def findViewPermissions(view: View): List[ViewPermission] =
    if(view.isSystem) {
      findSystemViewPermissions(view.viewId)
    } else {
      findCustomViewPermissions(view.bankId, view.accountId, view.viewId)
    }
    
  def findViewPermission(view: View, permission: String): Box[ViewPermission] =
    if(view.isSystem) {
      findSystemViewPermission(view.viewId, permission)
    } else {
      findCustomViewPermission(view.bankId, view.accountId, view.viewId, permission)
    }

  /**
   * This method first removes all existing permissions for the given view,
   * then creates new ones based on the provided parameters.
   *
   * This follows the original logic from ViewDefinition, where permission updates
   * were only supported in bulk (all at once). In the future, we may extend this
   * to support updating individual permissions selectively.
   */
  def resetViewPermissions(
    view: View,
    permissionNames: List[String],
    canGrantAccessToViews: List[String] = Nil,
    canRevokeAccessToViews: List[String] = Nil
  ): Unit = {

    // Delete all existing permissions for this view
    ViewPermission.findViewPermissions(view).foreach(_.delete_!)

    val (bankId, accountId) =
      if (view.isSystem)
        (null, null)
      else
        (view.bankId.value, view.accountId.value)

    // Insert each new permission
    permissionNames.foreach { permissionName =>
      val extraData = permissionName match {
        case CAN_GRANT_ACCESS_TO_VIEWS  => canGrantAccessToViews.mkString(",")
        case CAN_REVOKE_ACCESS_TO_VIEWS => canRevokeAccessToViews.mkString(",")
        case _                          => null
      }

      // Dynamically build correct query conditions with NullRef if needed
      val conditions: Seq[QueryParam[ViewPermission]] = Seq(
        if (bankId == null) NullRef(ViewPermission.bank_id) else By(ViewPermission.bank_id, bankId),
        if (accountId == null) NullRef(ViewPermission.account_id) else By(ViewPermission.account_id, accountId),
        By(ViewPermission.view_id, view.viewId.value),
        By(ViewPermission.permission, permissionName)
      )

      // Remove existing conflicting record if any
      ViewPermission.find(conditions: _*).foreach(_.delete_!)

      // Insert new permission
      ViewPermission.create
        .bank_id(bankId)
        .account_id(accountId)
        .view_id(view.viewId.value)
        .permission(permissionName)
        .extraData(extraData)
        .save
    }
  }

}
