package code.views.system

import code.api.Constant.{CAN_GRANT_ACCESS_TO_VIEWS, CAN_REVOKE_ACCESS_TO_VIEWS}
import code.api.util.DoobieUtil
import com.openbankproject.commons.model._
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.common.Box.tryo

/**
 * One (view, permission) pair.
 *
 * A SYSTEM view is identified by view_id alone and stores NULL in bank_id and account_id; a CUSTOM
 * view is identified by all three. The system-view reads use `IS NULL` on both columns rather than
 * leaving them unconstrained, so a system lookup does not match a custom view's rows — and a row
 * storing "" instead of NULL would be invisible to those reads.
 *
 * `extraData` carries the comma-joined view-id list for CAN_GRANT_ACCESS_TO_VIEWS and
 * CAN_REVOKE_ACCESS_TO_VIEWS, and is NULL for every other permission.
 */
case class ViewPermission(
  bankId: Option[String],
  accountId: Option[String],
  viewId: String,
  permission: String,
  extraData: Option[String]
)

object ViewPermission {

  private val selectColumns =
    fr"SELECT bank_id, account_id, view_id, permission, extradata FROM viewpermission"

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String])

  private def fromRow(row: Row): ViewPermission = row match {
    case (bankId, accountId, viewId, permission, extraData) =>
      ViewPermission(bankId, accountId, viewId.orNull, permission.orNull, extraData)
  }

  private def query(condition: Fragment): List[ViewPermission] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def one(condition: Fragment): Box[ViewPermission] =
    query(condition ++ fr"ORDER BY id ASC LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  /**
   * `None` means the column must be NULL — Lift's NullRef, not "do not filter".
   *
   * Some(null) is NOT None and must not be bound as a bare String: a view row loaded from the
   * database can carry BankId(null) / AccountId(null), so wrapping the value in Option again
   * collapses that to SQL NULL, which is exactly what Lift's `By(field, null)` rendered. Binding it
   * directly throws "oops, null" with no OBP frame in the trace.
   */
  private def scoped(column: Fragment, value: Option[String]): Fragment =
    value.flatMap(Option(_)) match {
      case Some(v) => column ++ fr" = $v"
      case None => column ++ fr" IS NULL"
    }

  private def viewScope(bankId: Option[String], accountId: Option[String], viewId: String): Fragment =
    fr"WHERE " ++ scoped(fr"bank_id", bankId) ++ fr"AND " ++ scoped(fr"account_id", accountId) ++
      fr"AND view_id = ${Option(viewId)}"

  def findCustomViewPermissions(bankId: BankId, accountId: AccountId, viewId: ViewId): List[ViewPermission] =
    query(viewScope(Some(bankId.value), Some(accountId.value), viewId.value) ++ fr"ORDER BY id ASC")

  def findSystemViewPermissions(viewId: ViewId): List[ViewPermission] =
    query(viewScope(None, None, viewId.value) ++ fr"ORDER BY id ASC")

  def findCustomViewPermission(bankId: BankId, accountId: AccountId, viewId: ViewId,
                               permission: String): Box[ViewPermission] =
    one(viewScope(Some(bankId.value), Some(accountId.value), viewId.value) ++
      fr"AND permission = ${Option(permission)}")

  def findSystemViewPermission(viewId: ViewId, permission: String): Box[ViewPermission] =
    one(viewScope(None, None, viewId.value) ++ fr"AND permission = ${Option(permission)}")

  private def insert(bankId: Option[String], accountId: Option[String], viewId: String,
                     permission: String, extraData: Option[String]): ViewPermission = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO viewpermission
            (bank_id, account_id, view_id, permission, extradata, createdat, updatedat)
            VALUES (${bankId.flatMap(Option(_))}, ${accountId.flatMap(Option(_))},
             ${Option(viewId)}, ${Option(permission)}, ${extraData.flatMap(Option(_))}, $now, $now)"""
        .update.run)
    ViewPermission(bankId, accountId, viewId, permission, extraData)
  }

  def createSystemViewPermission(viewId: ViewId, permissionName: String,
                                 extraData: Option[List[String]]): Box[ViewPermission] =
    tryo {
      insert(None, None, viewId.value, permissionName, extraData.map(_.mkString(",")))
    }

  private def delete(bankId: Option[String], accountId: Option[String], viewId: String): Int =
    DoobieUtil.runUpdate(
      (fr"DELETE FROM viewpermission" ++
        viewScope(bankId, accountId, viewId).stripMargin).update.run)

  private def deleteOne(bankId: Option[String], accountId: Option[String], viewId: String,
                        permission: String): Int =
    DoobieUtil.runUpdate(
      (fr"DELETE FROM viewpermission" ++ viewScope(bankId, accountId, viewId) ++
        fr"AND permission = ${Option(permission)}").update.run)

  /** Deletes exactly this row, addressed by the four columns that identify it. */
  def deleteRow(row: ViewPermission): Boolean =
    deleteOne(row.bankId, row.accountId, row.viewId, row.permission) > 0

  def count(bankId: Option[String], accountId: Option[String], viewId: String): Long =
    DoobieUtil.runQuery(
      (fr"SELECT COUNT(*) FROM viewpermission" ++ viewScope(bankId, accountId, viewId))
        .query[Long].unique)

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM viewpermission".update.run)
    ()
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

    // A system view is scoped by view_id alone, with both id columns NULL.
    val (bankId, accountId) =
      if (view.isSystem) (None, None)
      else (Some(view.bankId.value), Some(view.accountId.value))

    // Delete all existing permissions for this view
    delete(bankId, accountId, view.viewId.value)

    // Insert each new permission
    permissionNames.foreach { permissionName =>
      val extraData = permissionName match {
        case CAN_GRANT_ACCESS_TO_VIEWS  => Some(canGrantAccessToViews.mkString(","))
        case CAN_REVOKE_ACCESS_TO_VIEWS => Some(canRevokeAccessToViews.mkString(","))
        case _                          => None
      }

      // Remove existing conflicting record if any
      deleteOne(bankId, accountId, view.viewId.value, permissionName)

      // Insert new permission; ignore constraint violation from a concurrent reset
      scala.util.Try {
        insert(bankId, accountId, view.viewId.value, permissionName, extraData)
      }
    }
  }
}
