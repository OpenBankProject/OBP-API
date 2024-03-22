package code.views

import code.api.util.{APIUtil, CallContext}
import code.model.dataAccess.{MappedBankAccount, ViewImpl, ViewPrivileges}
import code.remotedata.RemotedataViews
import code.views.MapperViews.getPrivateBankAccounts
import code.views.system.AccountAccess
import com.openbankproject.commons.model.{CreateViewJson, _}
import net.liftweb.common.Box
import net.liftweb.mapper.By
import net.liftweb.util.{Props, SimpleInjector}

import scala.collection.immutable.List
import scala.concurrent.Future
import com.openbankproject.commons.ExecutionContext.Implicits.global

import scala.concurrent.Future

object Views  extends SimpleInjector {

  val views = new Inject(buildOne _) {}
 
  //TODO Remove MapperViews when Remotedata is optimized and stable
  def buildOne: Views =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
      case false  => MapperViews
      case true => RemotedataViews     // We will use Akka as a middleware
    }
  
}

trait Views {
  
  def permissions(account : BankIdAccountId) : List[Permission]
  def permission(account : BankIdAccountId, user: User) : Box[Permission]
  def getViewBydBankIdAccountIdViewIdAndUser(bankIdAccountIdViewId : BankIdAccountIdViewId, user: User) : Box[Permission]
  def getPermissionForUser(user: User) : Box[Permission]
  /**
    * This is for @ViewPrivileges. 
    * It will first find the view object by `bankIdAccountIdViewId`
    * And then, call @getOrCreateViewPrivilege(view: View, user: User) for the view and user.
   */
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

  def customView(viewId : ViewId, bankAccountId: BankIdAccountId) : Box[View]
  def systemView(viewId : ViewId) : Box[View]
  def customViewFuture(viewId : ViewId, bankAccountId: BankIdAccountId) : Future[Box[View]]
  def systemViewFuture(viewId : ViewId) : Future[Box[View]]
  def getSystemViews(): Future[List[View]]

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
   * @param bankIdAccountId the IncomingAccount from Kafka
   * @param viewId          This field should be selected one from Owner/Public/Accountant/Auditor, only support
   *                        these four values.
   * @return This will insert a View (e.g. the owner view) for an Account (BankAccount), and return the view
   *         Note:
   *         updateUserAccountViews would call createAccountView once per View specified in the IncomingAccount from Kafka.
   *         We should cache this function because the available views on an account will change rarely.
   *
   */
  def getOrCreateSystemViewFromCbs(viewId: String): Box[View]
  
  def getOrCreateSystemView(viewId: String) : Box[View]
  def getOrCreateCustomPublicView(bankId: BankId, accountId: AccountId, description: String) : Box[View]

  def getOwners(view: View): Set[User]
  
  def removeAllPermissions(bankId: BankId, accountId: AccountId) : Boolean
  def removeAllViews(bankId: BankId, accountId: AccountId) : Boolean

  def bulkDeleteAllPermissionsAndViews() : Boolean

}


class RemotedataViewsCaseClasses {

  case class permissions(account: BankIdAccountId)
  case class getPermissionForUser(user: User)
  case class permission(account: BankIdAccountId, user: User)
  case class getViewBydBankIdAccountIdViewIdAndUser(bankIdAccountIdViewId: BankIdAccountIdViewId, user: User)
  case class addPermission(viewUID: BankIdAccountIdViewId, user: User)
  case class addSystemViewPermission(bankId: BankId, accountId: AccountId, view : View, user : User)
  case class revokeAccess(bankIdAccountIdViewId: BankIdAccountIdViewId, user : User)
  case class grantAccessToMultipleViews(views: List[BankIdAccountIdViewId], user: User, callContext: Option[CallContext])
  case class revokeAccessToMultipleViews(views: List[BankIdAccountIdViewId],  user: User)
  case class revokeSystemViewPermission(bankId: BankId, accountId: AccountId, view : View, user : User)
  case class revokeAllAccountAccess(bankId: BankId, accountId: AccountId, user: User)
  case class revokeAccountAccessByUser(bankId: BankId, accountId: AccountId, user: User, callContext: Option[CallContext])
  case class createView(bankAccountId: BankIdAccountId, view: CreateViewJson)
  case class createSystemView(view: CreateViewJson)
  case class removeCustomView(viewId: ViewId, bankAccountId: BankIdAccountId)
  case class removeSystemView(viewId: ViewId)
  case class updateCustomView(bankAccountId: BankIdAccountId, viewId: ViewId, viewUpdateJson: UpdateViewJSON)
  case class updateSystemView(viewId : ViewId, viewUpdateJson : UpdateViewJSON)
  case class assignedViewsForAccount(bankAccountId: BankIdAccountId)
  case class availableViewsForAccount(bankAccountId: BankIdAccountId)
  case class viewsUserCanAccess(user: User)
  case class privateViewsUserCanAccess(user: User)
  case class privateViewsUserCanAccessViaViewId(user: User, viewIds: List[ViewId])
  case class privateViewsUserCanAccessAtBank(user: User, bankId: BankId)
  case class privateViewsUserCanAccessForAccount(user: User, bankIdAccountId : BankIdAccountId)
  case class getAllFirehoseAccounts(bank: Bank, user : User)
  case class publicViews()
  case class publicViewsForBank(bankId: BankId)
  case class customView(pars: Any*) {
    def apply(viewId: ViewId, bankAccountId: BankIdAccountId): Box[View] = this (viewId, bankAccountId)
  }
  case class systemView(viewId : ViewId)
  case class getSystemViews()
  case class customViewFuture(viewId : ViewId, bankAccountId: BankIdAccountId)
  case class systemViewFuture(viewId : ViewId)
  case class getOrCreateSystemViewFromCbs(viewId: String)
  case class getOrCreateSystemView(viewId: String)
  case class getOrCreatePublicPublicView(bankId: BankId, accountId: AccountId, description: String)

  case class getOwners(view: View)
  
  case class removeAllPermissions(bankId: BankId, accountId: AccountId)
  case class removeAllViews(bankId: BankId, accountId: AccountId)

  case class bulkDeleteAllPermissionsAndViews()

  case class revokeAccessToSystemViewForConsumer(bankId: BankId, accountId: AccountId, view : View, consumerId : String)
  case class revokeAccessToCustomViewForConsumer(view : View, consumerId : String)

}

object RemotedataViewsCaseClasses extends RemotedataViewsCaseClasses

