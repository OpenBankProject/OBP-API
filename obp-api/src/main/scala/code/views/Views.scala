package code.views

import code.api.util.CallContext
import code.model.dataAccess.MappedBankAccount
import code.views.system.AccountAccess
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model._
import net.liftweb.common.Box
import net.liftweb.mapper.By
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future

object Views  extends SimpleInjector {

  val views = new Inject(buildOne _) {}
 
  def buildOne: Views = MapperViews
 
}

trait Views {
  
  def permissions(account : BankIdAccountId) : List[Permission]
  def permission(account : BankIdAccountId, user: User) : Box[Permission]
  def getPermissionForUser(user: User) : Box[Permission]
  def grantAccessToCustomView(bankIdAccountIdViewId : BankIdAccountIdViewId, user : User) : Box[View]
  def grantAccessToSystemView(bankId: BankId, accountId: AccountId, view : View, user : User) : Box[View]
  def grantAccessToMultipleViews(views : List[BankIdAccountIdViewId], user : User, callContext: Option[CallContext]) : Box[List[View]]
  def revokeAccessToMultipleViews(views : List[BankIdAccountIdViewId], user : User) : Box[List[View]]
  def revokeAccess(bankIdAccountIdViewId : BankIdAccountIdViewId, user : User) : Box[Boolean]
  def revokeAccessToSystemView(bankId: BankId, accountId: AccountId, view : View, user : User) : Box[Boolean]
  def revokeAllAccountAccess(bankId : BankId, accountId : AccountId, user : User) : Box[Boolean]
  def revokeAccountAccessByUser(bankId : BankId, accountId : AccountId, user : User, callContext: Option[CallContext]) : Box[Boolean]

  def revokeAccessToSystemViewForConsumer(bankId: BankId, accountId: AccountId, view : View, consumerId : String) : Box[Boolean]
  def revokeAccessToCustomViewForConsumer(view : View, consumerId : String) : Box[Boolean]

  // Revoke and enumerate one application's access, rather than access shared by every application.
  // The consent flows use these against Constant.ALL_CONSUMERS, which is the literal every consent
  // grant is written under -- not a wildcard, and every lookup matches it by equality.
  //
  // There is deliberately no grant...ForConsumer counterpart. Two existed and had no callers, and
  // could not have gained one safely: a row written under a real consumer id is invisible to
  // revokeConsentAccountAccess, which sweeps by asking for exactly ALL_CONSUMERS, so the access
  // would outlive the consent that granted it. Grants go through grantAccessToSystemView /
  // grantAccessToCustomView, which write ALL_CONSUMERS and are therefore revocable.
  def revokeAccessToViewForUserAndConsumer(bankIdAccountIdViewId : BankIdAccountIdViewId, user : User, consumerId : String) : Box[Boolean]
  // Everything a user currently holds under one application's scope. The consent flows reconcile
  // against this: a consent's granted views are whatever its JWT says, so the rows that back it are
  // brought to match rather than deleted and rewritten.
  def accessGrantedToUserForConsumer(user : User, consumerId : String) : List[BankIdAccountIdViewId]

  def customView(viewId : ViewId, bankAccountId: BankIdAccountId) : Box[View]
  def systemView(viewId : ViewId) : Box[View]
  def customViewFuture(viewId : ViewId, bankAccountId: BankIdAccountId) : Future[Box[View]]
  def systemViewFuture(viewId : ViewId) : Future[Box[View]]
  def getSystemViews(): Future[List[View]]
  def getViewByBankIdAccountIdViewIdUserPrimaryKey(bankIdAccountIdViewId : BankIdAccountIdViewId, userPrimaryKey: UserPrimaryKey) : Box[View]

  //always return a view id String, not error here. 
  def getMetadataViewId(bankAccountId: BankIdAccountId, viewId : ViewId) = Views.views.vend.customView(viewId, bankAccountId).map(_.metadataView).openOr(viewId.value)
  
  def createCustomView(bankAccountId: BankIdAccountId, view: CreateViewJson): Box[View]
  def createSystemView(view: CreateViewJson): Future[Box[View]]
  def removeCustomView(viewId: ViewId, bankAccountId: BankIdAccountId): Box[Boolean]
  def removeSystemView(viewId: ViewId): Future[Box[Boolean]]
  def updateCustomView(bankAccountId : BankIdAccountId, viewId : ViewId, viewUpdateJson : UpdateViewJSON) : Box[View]
  def updateSystemView(viewId : ViewId, viewUpdateJson : UpdateViewJSON): Future[Box[View]]
  
  /**
    * This will return all the public views, no requirements for accountId or userId.
    * Because the public views are totally open for everyone. 
    */
  def publicViews: (List[View], List[AccountAccess])
  def publicViewsForBank(bankId: BankId): (List[View], List[AccountAccess])
  /**
    * This will return all the views belong to the bankAccount, its own Public + Private views.
    * Do not contain any other account public views.
    */
  def assignedViewsForAccount(bankAccountId : BankIdAccountId) : List[View]
  def availableViewsForAccount(bankAccountId : BankIdAccountId) : List[View]
  
  def privateViewsUserCanAccess(user: User): (List[View], List[AccountAccess])
  def privateViewsUserCanAccess(user: User, viewIds: List[ViewId]): (List[View], List[AccountAccess])
  def privateViewsUserCanAccessAtBank(user: User, bankId: BankId): (List[View], List[AccountAccess])
  def getAccountAccessAtBankThroughView(user: User, bankId: BankId, viewId: ViewId): (List[View], List[AccountAccess])
  def privateViewsUserCanAccessForAccount(user: User, bankIdAccountId : BankIdAccountId) : List[View]
  
  //the following return list[BankIdAccountId], just use the list[View] method, the View object contains enough data for it.
  final def getAllFirehoseAccounts(bankId: BankId)= {
    MappedBankAccount.findAll(
      By(MappedBankAccount.bank, bankId.value)
    )
  }
  final def getPrivateBankAccounts(user : User) : List[BankIdAccountId] =  privateViewsUserCanAccess(user)._2.map(a => BankIdAccountId(BankId(a.bank_id.get), AccountId(a.account_id.get))).distinct 
  final def getPrivateBankAccounts(user : User, viewIds: List[ViewId]) : List[BankIdAccountId] =  privateViewsUserCanAccess(user, viewIds)._2.map(a => BankIdAccountId(BankId(a.bank_id.get), AccountId(a.account_id.get))).distinct 
  final def getPrivateBankAccountsFuture(user : User) : Future[List[BankIdAccountId]] = Future {getPrivateBankAccounts(user)}
  final def getPrivateBankAccountsFuture(user : User, viewIds: List[ViewId]) : Future[List[BankIdAccountId]] = Future {getPrivateBankAccounts(user, viewIds)}
  final def getPrivateBankAccounts(user : User, bankId : BankId) : List[BankIdAccountId] = getPrivateBankAccounts(user).filter(_.bankId == bankId).distinct
  final def getPrivateBankAccountsFuture(user : User, bankId : BankId) : Future[List[BankIdAccountId]] = Future {getPrivateBankAccounts(user, bankId)}

  /**
   * @param bankIdAccountId the IncomingAccount from CBS
   * @param viewId          This field should be selected one from Owner/Public/Accountant/Auditor, only support
   *                        these four values.
   * @return This will insert a View (e.g. the owner view) for an Account (BankAccount), and return the view
   *         Note:
   *         updateUserAccountViews would call createAccountView once per View specified in the IncomingAccount from CBS.
   *         We should cache this function because the available views on an account will change rarely.
   *
   */
  def getOrCreateSystemViewFromCbs(viewId: String): Box[View]
  
  def getOrCreateSystemView(viewId: String) : Box[View]
  def getOrCreateCustomPublicView(bankId: BankId, accountId: AccountId, description: String) : Box[View]

  /**
   * Reset an existing system view's permissions and view-level flags back to
   * the code-defined defaults for that view id. Preserves the row itself
   * (so any AccountAccess bindings keep working). Returns Empty if no such
   * system view exists.
   */
  def factoryResetSystemView(viewId: ViewId) : Box[View]

  /**
   * Get or create a system view AND bring its permissions into line with the code.
   *
   * getOrCreateSystemView returns an existing row untouched, so a view created by an older
   * version keeps whatever permission set that version gave it -- a change to a view's can_*
   * set in code therefore reaches new installations only. This is what boot calls instead, so
   * the code stays the source of truth for the views it defines on every installation.
   *
   * Only the permission set is reconciled. Unlike factoryResetSystemView this leaves the row's
   * name, description and flags alone, so an operator's edits to those survive.
   */
  def ensureSystemViewUpToDate(viewId: String) : Box[View]

  def getOwners(view: View): Set[User]
  
  def removeAllAccountAccess(bankId: BankId, accountId: AccountId) : Boolean
  def removeAllViewsAndVierPermissions(bankId: BankId, accountId: AccountId) : Boolean

  def bulkDeleteAllViewsAndAccountAccessAndViewPermission() : Boolean

}



