package code.views

import code.api.util.APIUtil
import code.api.util.APIUtil.canUseFirehose
import code.model.dataAccess.{ViewImpl, ViewPrivileges}
import code.model.{CreateViewJson, Permission, _}
import code.remotedata.RemotedataViews
import code.views.MapperViews.getPrivateBankAccounts
import net.liftweb.common.Box
import net.liftweb.mapper.By
import net.liftweb.util.{Props, SimpleInjector}

import scala.collection.immutable.List
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
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
  def getPermissionForUser(user: User) : Box[Permission]
  /**
    * This is for @ViewPrivileges. 
    * It will first find the view object by `viewIdBankIdAccountId`
    * And then, call @getOrCreateViewPrivilege(view: View, user: User) for the view and user.
   */
  def addPermission(viewIdBankIdAccountId : ViewIdBankIdAccountId, user : User) : Box[View]
  def addPermissions(views : List[ViewIdBankIdAccountId], user : User) : Box[List[View]]
  def revokePermission(viewIdBankIdAccountId : ViewIdBankIdAccountId, user : User) : Box[Boolean]
  def revokeAllPermissions(bankId : BankId, accountId : AccountId, user : User) : Box[Boolean]

  def view(viewId : ViewId, bankAccountId: BankIdAccountId) : Box[View]
  def viewFuture(viewId : ViewId, bankAccountId: BankIdAccountId) : Future[Box[View]]

  //always return a view id String, not error here. 
  def getMetadataViewId(bankAccountId: BankIdAccountId, viewId : ViewId) = Views.views.vend.view(viewId, bankAccountId).map(_.metadataView).openOr(viewId.value)
  
  def createView(bankAccountId: BankIdAccountId, view: CreateViewJson): Box[View]
  def removeView(viewId: ViewId, bankAccountId: BankIdAccountId): Box[Unit]
  def updateView(bankAccountId : BankIdAccountId, viewId : ViewId, viewUpdateJson : UpdateViewJSON) : Box[View]
  
  /**
    * This will return all the public views, no requirements for accountId or userId.
    * Because the public views are totally open for everyone. 
    */
  def publicViews: List[View]
  def publicViewsForBank(bankId: BankId): List[View]
  def firehoseViewsForBank(bankId: BankId, user : User): List[View]
  /**
    * This will return all the views belong to the bankAccount, its own Public + Private views.
    * Do not contain any other account public views.
    */
  def viewsForAccount(bankAccountId : BankIdAccountId) : List[View]
  
  def privateViewsUserCanAccess(user: User): List[View]
  def privateViewsUserCanAccessForAccount(user: User, bankIdAccountId : BankIdAccountId) : List[View]
  
  //the following return list[BankIdAccountId], just use the list[View] method, the View object contains enough data for it.
  final def getAllFirehoseAccounts(bankId: BankId, user : User) : List[BankIdAccountId] = firehoseViewsForBank(bankId, user).map(v => BankIdAccountId(v.bankId, v.accountId)).distinct
  final def getPrivateBankAccounts(user : User) : List[BankIdAccountId] =  privateViewsUserCanAccess(user).map(v => BankIdAccountId(v.bankId, v.accountId)).distinct 
  final def getPrivateBankAccountsFuture(user : User) : Future[List[BankIdAccountId]] = Future {getPrivateBankAccounts(user)}
  final def getPrivateBankAccounts(user : User, bankId : BankId) : List[BankIdAccountId] = getPrivateBankAccounts(user).filter(_.bankId == bankId).distinct
  final def getPrivateBankAccountsFuture(user : User, bankId : BankId) : Future[List[BankIdAccountId]] = Future {getPrivateBankAccounts(user, bankId)}
  
  def getOrCreateAccountView(bankAccountUID: BankIdAccountId, viewId: String): Box[View]
  def getOrCreateFirehoseView(bankId: BankId, accountId: AccountId, description: String) : Box[View]
  def getOrCreateOwnerView(bankId: BankId, accountId: AccountId, description: String) : Box[View]
  def getOrCreatePublicView(bankId: BankId, accountId: AccountId, description: String) : Box[View]
  def getOrCreateAccountantsView(bankId: BankId, accountId: AccountId, description: String) : Box[View]
  def getOrCreateAuditorsView(bankId: BankId, accountId: AccountId, description: String) : Box[View]
  def createRandomView(bankId: BankId, accountId: AccountId) : Box[View]

  def getOwners(view: View): Set[User]

  def grantAccessToView(user : User, view : View) : Boolean
  def grantAccessToAllExistingViews(user : User) : Boolean

  def removeAllPermissions(bankId: BankId, accountId: AccountId) : Boolean
  def removeAllViews(bankId: BankId, accountId: AccountId) : Boolean

  def bulkDeleteAllPermissionsAndViews() : Boolean

}


class RemotedataViewsCaseClasses {

  case class permissions(account: BankIdAccountId)
  case class getPermissionForUser(user: User)
  case class permission(account: BankIdAccountId, user: User)
  case class addPermission(viewUID: ViewIdBankIdAccountId, user: User)
  case class addPermissions(views: List[ViewIdBankIdAccountId], user: User)
  case class revokePermission(viewUID: ViewIdBankIdAccountId, user: User)
  case class revokeAllPermissions(bankId: BankId, accountId: AccountId, user: User)
  case class createView(bankAccountId: BankIdAccountId, view: CreateViewJson)
  case class removeView(viewId: ViewId, bankAccountId: BankIdAccountId)
  case class updateView(bankAccountId: BankIdAccountId, viewId: ViewId, viewUpdateJson: UpdateViewJSON)
  case class viewsForAccount(bankAccountId: BankIdAccountId)
  case class viewsUserCanAccess(user: User)
  case class privateViewsUserCanAccess(user: User)
  case class privateViewsUserCanAccessForAccount(user: User, bankIdAccountId : BankIdAccountId)
  case class getAllFirehoseAccounts(bank: Bank, user : User)
  case class publicViews()
  case class publicViewsForBank(bankId: BankId)
  case class firehoseViewsForBank(bankId: BankId, user : User)
  case class view(pars: Any*) {
    def apply(viewId: ViewId, bankAccountId: BankIdAccountId): Box[View] = this (viewId, bankAccountId)
  }
  case class viewFuture(viewId : ViewId, bankAccountId: BankIdAccountId)
  case class getOrCreateAccountView(account: BankIdAccountId, viewName: String)
  case class getOrCreateOwnerView(bankId: BankId, accountId: AccountId, description: String)
  case class getOrCreateFirehoseView(bankId: BankId, accountId: AccountId, description: String)
  case class getOrCreatePublicView(bankId: BankId, accountId: AccountId, description: String)
  case class getOrCreateAccountantsView(bankId: BankId, accountId: AccountId, description: String)
  case class getOrCreateAuditorsView(bankId: BankId, accountId: AccountId, description: String)
  case class createRandomView(bankId: BankId, accountId: AccountId)

  case class getOwners(view: View)

  case class grantAccessToView(user : User, view : View)
  case class grantAccessToAllExistingViews(user : User)

  case class removeAllPermissions(bankId: BankId, accountId: AccountId)
  case class removeAllViews(bankId: BankId, accountId: AccountId)

  case class bulkDeleteAllPermissionsAndViews()
}

object RemotedataViewsCaseClasses extends RemotedataViewsCaseClasses

