/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

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
   * Identifies one view for permission lookups. A system view is shared by every account, so it has
   * no bank or account. A custom view belongs to one account, and the same view id can exist on
   * several accounts, so the bank and account are part of its identity.
   */
  case class ViewKey(bankId: Option[String], accountId: Option[String], viewId: String)

  def viewKey(view: View): ViewKey =
    if (view.isSystem) ViewKey(None, None, view.viewId.value)
    else ViewKey(Some(view.bankId.value), Some(view.accountId.value), view.viewId.value)

  // SQL Server allows about 2,100 parameters in one statement, so long IN lists are split
  private val maxIdsPerQuery = 500

  /**
   * Finds the permissions of many views at once.
   *
   * `View.allowed_actions` runs one query per view object. A list of a user's views holds one object
   * per account access, so a user with the owner view on 200 accounts would cause 200 identical
   * queries. This function runs one query for all the system views, plus one query per bank for the
   * custom views (more only if an IN list has to be split).
   *
   * The custom query matches account ids and view ids separately, so it can return a few rows for
   * views the caller did not ask about. Those rows are dropped by matching on the full ViewKey.
   */
  def findPermissionsForViews(views: List[View]): Map[ViewKey, Set[String]] = {
    val (systemViews, customViews) = views.partition(_.isSystem)

    val systemViewIds = systemViews.map(_.viewId.value).distinct
    val systemRows = systemViewIds.grouped(maxIdsPerQuery).toList.flatMap { viewIds =>
      ViewPermission.findAll(
        NullRef(ViewPermission.bank_id),
        NullRef(ViewPermission.account_id),
        ByList(ViewPermission.view_id, viewIds)
      )
    }

    val customRows = customViews.groupBy(_.bankId.value).toList.flatMap { case (bankId, viewsAtBank) =>
      val viewIds = viewsAtBank.map(_.viewId.value).distinct
      viewsAtBank.map(_.accountId.value).distinct.grouped(maxIdsPerQuery).toList.flatMap { accountIds =>
        viewIds.grouped(maxIdsPerQuery).toList.flatMap { viewIdsChunk =>
          ViewPermission.findAll(
            By(ViewPermission.bank_id, bankId),
            ByList(ViewPermission.account_id, accountIds),
            ByList(ViewPermission.view_id, viewIdsChunk)
          )
        }
      }
    }

    val wanted = views.map(viewKey).toSet
    (systemRows ++ customRows)
      .groupBy(row => ViewKey(Option(row.bank_id.get), Option(row.account_id.get), row.view_id.get))
      .collect { case (key, rows) if wanted.contains(key) => key -> rows.map(_.permission.get).toSet }
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

      // Insert new permission; ignore constraint violation from a concurrent reset
      scala.util.Try {
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

}
