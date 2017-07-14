package code.views

import code.model.{CreateViewJson, Permission, _}
import code.remotedata.RemotedataViews
import net.liftweb.common.Box
import net.liftweb.util.{Props, SimpleInjector}

object Views  extends SimpleInjector {

  val views = new Inject(buildOne _) {}
 
  //TODO Remove MapperViews when Remotedata is optimized and stable
  def buildOne: Views =
    Props.getBool("use_akka", false) match {
      case false  => MapperViews
      case true => RemotedataViews     // We will use Akka as a middleware
    }
  
}

trait Views {
  
  def permissions(account : BankIdAccountId) : List[Permission]
  def permission(account : BankIdAccountId, user: User) : Box[Permission]
  def getOrCreateViewPrivilege(bankIdAccountId: BankIdAccountId, viewIdBankIdAccountId: ViewIdBankIdAccountId, user: User): Box[View]
  def addPermission(viewIdBankIdAccountId : ViewIdBankIdAccountId, user : User) : Box[View]
  def addPermissions(views : List[ViewIdBankIdAccountId], user : User) : Box[List[View]]
  def revokePermission(viewIdBankIdAccountId : ViewIdBankIdAccountId, user : User) : Box[Boolean]
  def revokeAllPermissions(bankId : BankId, accountId : AccountId, user : User) : Box[Boolean]

  def view(viewId : ViewId, bankAccountId: BankIdAccountId) : Box[View]
  def view(viewUID : ViewIdBankIdAccountId) : Box[View]

  def createView(bankAccountId: BankIdAccountId, view: CreateViewJson): Box[View]
  def removeView(viewId: ViewId, bankAccountId: BankIdAccountId): Box[Unit]
  def updateView(bankAccountId : BankIdAccountId, viewId : ViewId, viewUpdateJson : UpdateViewJSON) : Box[View]
  def views(bankAccountId : BankIdAccountId) : List[View]
  def permittedViews(user: User, bankAccountId: BankIdAccountId): List[View]
  def publicViews(bankAccountId : BankIdAccountId) : List[View]

  def getAllPublicAccounts : List[BankIdAccountId]
  def getPublicBankAccounts(bank : Bank) : List[BankIdAccountId]
  def getAllAccountsUserCanSee(user : Box[User]) : List[BankIdAccountId]
  def getAllAccountsUserCanSee(bank: Bank, user : Box[User]) : List[BankIdAccountId]
  def getNonPublicBankAccounts(user : User) : List[BankIdAccountId]
  def getNonPublicBankAccounts(user : User, bankId : BankId) : List[BankIdAccountId]

  def getOrCreateAccountView(bankAccountUID: BankIdAccountId, viewId: String): Box[View]
  def getOrCreateOwnerView(bankId: BankId, accountId: AccountId, description: String) : Box[View]
  def getOrCreatePublicView(bankId: BankId, accountId: AccountId, description: String) : Box[View]
  def getOrCreateAccountantsView(bankId: BankId, accountId: AccountId, description: String) : Box[View]
  def getOrCreateAuditorsView(bankId: BankId, accountId: AccountId, description: String) : Box[View]
  def createRandomView(bankId: BankId, accountId: AccountId) : Box[View]

  def getOwners(view: View): Set[User]

  def grantAccessToView(user : User, view : View) : Boolean
  def grantAccessToAllExistingViews(user : User) : Boolean

  def viewExists(bank: BankId, accountId: AccountId, name: String): Boolean
  def removeAllPermissions(bankId: BankId, accountId: AccountId) : Boolean
  def removeAllViews(bankId: BankId, accountId: AccountId) : Boolean

  def bulkDeleteAllPermissionsAndViews() : Boolean

}


class RemotedataViewsCaseClasses {

  case class permissions(account: BankIdAccountId)
  case class permission(account: BankIdAccountId, user: User)
  case class getOrCreateViewPrivilege(bankIdAccountId: BankIdAccountId, viewIdBankIdAccountId: ViewIdBankIdAccountId, user: User)
  case class addPermission(viewUID: ViewIdBankIdAccountId, user: User)
  case class addPermissions(views: List[ViewIdBankIdAccountId], user: User)
  case class revokePermission(viewUID: ViewIdBankIdAccountId, user: User)
  case class revokeAllPermissions(bankId: BankId, accountId: AccountId, user: User)
  case class createView(bankAccountId: BankIdAccountId, view: CreateViewJson)
  case class removeView(viewId: ViewId, bankAccountId: BankIdAccountId)
  case class updateView(bankAccountId: BankIdAccountId, viewId: ViewId, viewUpdateJson: UpdateViewJSON)
  case class views(bankAccountId: BankIdAccountId)
  case class permittedViews(user: User, bankAccountId: BankIdAccountId)
  case class publicViews(bankAccountId: BankIdAccountId)
  case class getAllPublicAccounts()
  case class getPublicBankAccounts(bank: Bank)
  case class getAllAccountsUserCanSee(pars: Any*) {
    def apply(user: Box[User]): List[(BankId, AccountId)] = this (user)
    def apply(bankId: BankId, user: Box[User]): List[(BankId, AccountId)] = this (bankId, user)
  }
  case class getNonPublicBankAccounts(pars: Any*) {
    def apply(user: User): List[(BankId, AccountId)] = this (user)
    def apply(user: User, bankId: BankId): List[(BankId, AccountId)] = this (user, bankId)
  }
  case class view(pars: Any*) {
    def apply(viewIdBankIdAccountId: ViewIdBankIdAccountId): Box[View] = this (viewIdBankIdAccountId)
    def apply(viewId: ViewId, bankAccountId: BankIdAccountId): Box[View] = this (viewId, bankAccountId)
  }
  case class getOrCreateAccountView(account: BankIdAccountId, viewName: String)
  case class getOrCreateOwnerView(bankId: BankId, accountId: AccountId, description: String)
  case class getOrCreatePublicView(bankId: BankId, accountId: AccountId, description: String)
  case class getOrCreateAccountantsView(bankId: BankId, accountId: AccountId, description: String)
  case class getOrCreateAuditorsView(bankId: BankId, accountId: AccountId, description: String)
  case class createRandomView(bankId: BankId, accountId: AccountId)

  case class getOwners(view: View)

  case class grantAccessToView(user : User, view : View)
  case class grantAccessToAllExistingViews(user : User)

  case class viewExists(bank: BankId, accountId: AccountId, name: String)
  case class removeAllPermissions(bankId: BankId, accountId: AccountId)
  case class removeAllViews(bankId: BankId, accountId: AccountId)

  case class bulkDeleteAllPermissionsAndViews()
}

object RemotedataViewsCaseClasses extends RemotedataViewsCaseClasses

