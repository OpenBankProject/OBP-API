package code.views.system

import code.api.Constant.ALL_CONSUMERS
import code.model.dataAccess.ResourceUser
import code.util.UUIDString
import com.openbankproject.commons.model.{AccountId, BankId, UserPrimaryKey, View, ViewId}
import net.liftweb.mapper._
/*
This stores the link between A User and a View
A User can't use a View unless it is listed here.
 */
class AccountAccess extends LongKeyedMapper[AccountAccess] with IdPK with CreatedUpdated {
  def getSingleton = AccountAccess
  object user_fk extends MappedLongForeignKey(this, ResourceUser)
  object bank_id extends MappedString(this, 255)
  object account_id extends MappedString(this, 255)
  object view_id extends UUIDString(this)

  //If consumer_id is `ALL-CONSUMERS`, any consumers can use this record
  //If consumer_id is consumerId (obp UUID), only same consumer can use this record
  object consumer_id extends MappedString(this, 255) {
    override def defaultValue = ALL_CONSUMERS
  }
  
  
  @deprecated("we should use bank_id, account_id and view_id instead of the view_fk","07-03-2023")
  object view_fk extends MappedLongForeignKey(this, ViewDefinition)
}
object AccountAccess extends AccountAccess with LongKeyedMetaMapper[AccountAccess] {
  override def dbIndexes: List[BaseIndex[AccountAccess]] = UniqueIndex(bank_id, account_id, view_id, user_fk, consumer_id) :: super.dbIndexes
  
  def findByUniqueIndex(bankId: BankId, accountId: AccountId, viewId: ViewId, userPrimaryKey: UserPrimaryKey, consumerId: String) =
    AccountAccess.find(
      By(AccountAccess.bank_id, bankId.value),
      By(AccountAccess.account_id, accountId.value),
      By(AccountAccess.view_id, viewId.value),
      By(AccountAccess.user_fk, userPrimaryKey.value),
      By(AccountAccess.consumer_id, consumerId),
    )
  
  def findAllBySystemViewId(systemViewId:ViewId)= AccountAccess.findAll(
    By(AccountAccess.view_id, systemViewId.value)
  ) 
  def findAllByView(view: View)=
    if(view.isSystem) {
      findAllBySystemViewId(view.viewId)
    }else{
      AccountAccess.findAllByBankIdAccountIdViewId(view.bankId, view.accountId, view.viewId)
    }
  def findAllByUserPrimaryKey(userPrimaryKey:UserPrimaryKey)= AccountAccess.findAll(
    By(AccountAccess.user_fk, userPrimaryKey.value)
  )
  def findAllByBankIdAccountId(bankId:BankId, accountId:AccountId) = AccountAccess.findAll(
    By(AccountAccess.bank_id, bankId.value),
    By(AccountAccess.account_id, accountId.value)
  )
  def findAllByBankIdAccountIdViewId(bankId:BankId, accountId:AccountId, viewId:ViewId)= AccountAccess.findAll(
    By(AccountAccess.bank_id, bankId.value),
    By(AccountAccess.account_id, accountId.value),
    By(AccountAccess.view_id, viewId.value)
  )

  def findByBankIdAccountIdUserPrimaryKey(bankId: BankId, accountId: AccountId, userPrimaryKey: UserPrimaryKey) = AccountAccess.findAll(
    By(AccountAccess.bank_id, bankId.value),
    By(AccountAccess.account_id, accountId.value),
    By(AccountAccess.user_fk, userPrimaryKey.value)
  )

  def findByBankIdAccountIdViewIdUserPrimaryKey(bankId: BankId, accountId: AccountId, viewId: ViewId, userPrimaryKey: UserPrimaryKey) = AccountAccess.find(
    By(AccountAccess.bank_id, bankId.value),
    By(AccountAccess.account_id, accountId.value),
    By(AccountAccess.view_id, viewId.value),
    By(AccountAccess.user_fk, userPrimaryKey.value)
  )

  def findByBankIdAccountIdViewIdConsumerId(bankId: BankId, accountId: AccountId, viewId: ViewId, consumerId:String ) = AccountAccess.find(
    By(AccountAccess.bank_id, bankId.value),
    By(AccountAccess.account_id, accountId.value),
    By(AccountAccess.view_id, viewId.value),
    By(AccountAccess.consumer_id, consumerId)
  )
}
