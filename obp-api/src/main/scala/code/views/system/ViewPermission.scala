package code.views.system

import code.util.UUIDString
import com.openbankproject.commons.model._
import net.liftweb.mapper._
class ViewPermission extends LongKeyedMapper[ViewPermission] with IdPK with CreatedUpdated {
  def getSingleton = ViewPermission
  object bank_id extends MappedString(this, 255)
  object account_id extends MappedString(this, 255)
  object view_id extends UUIDString(this)
  object permission extends MappedString(this, 255)
}
object ViewPermission extends ViewPermission with LongKeyedMetaMapper[ViewPermission] {
  override def dbIndexes: List[BaseIndex[ViewPermission]] = UniqueIndex(bank_id, account_id, view_id, permission) :: super.dbIndexes
//  "ReadAccountsBerlinGroup"
  
  
  //Work in progress
  def findCustomViewPermissions(bankId: BankId, accountId: AccountId, viewId: ViewId): List[ViewPermission] =
    ViewPermission.findAll(
      By(ViewPermission.bank_id, bankId.value),
      By(ViewPermission.account_id, accountId.value),
      By(ViewPermission.view_id, viewId.value)
    )  
    
  //Work in progress
  def findSystemViewPermissions(viewId: ViewId): List[ViewPermission] =
    ViewPermission.findAll(
      NullRef(ViewPermission.bank_id),
      NullRef(ViewPermission.account_id),
      By(ViewPermission.view_id, viewId.value)
    )
}
