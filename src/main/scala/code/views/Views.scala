package code.views

import net.liftweb.common.Box
import code.model._
import net.liftweb.util.SimpleInjector
import code.model.Permission
import code.model.CreateViewJSON

object Views  extends SimpleInjector {

  val views = new Inject(buildOne _) {}

  def buildOne: Views = MapperViews

}

trait Views {

  def permissions(account : BankAccount) : List[Permission]
  def permission(account : BankAccount, user: User) : Box[Permission]
  def addPermission(viewUID : ViewUID, user : User) : Box[View]
  def addPermissions(views : List[ViewUID], user : User) : Box[List[View]]
  def revokePermission(viewUID : ViewUID, user : User) : Box[Boolean]
  def revokeAllPermission(bankId : BankId, accountId : AccountId, user : User) : Box[Boolean]

  def view(viewId : ViewId, bankAccount: BankAccount) : Box[View]
  def view(viewUID : ViewUID) : Box[View]

  def createView(bankAccount : BankAccount, view: CreateViewJSON) : Box[View]
  def removeView(viewId : ViewId, bankAccount: BankAccount): Box[Unit]
  def updateView(bankAccount : BankAccount, viewId : ViewId, viewUpdateJson : UpdateViewJSON) : Box[View]
  def views(bankAccount : BankAccount) : List[View]
  def permittedViews(user: User, bankAccount: BankAccount): List[View]
  def publicViews(bankAccount : BankAccount) : List[View]

  def getAllPublicAccounts : List[BankAccount]
  def getPublicBankAccounts(bank : Bank) : List[BankAccount]
  def getAllAccountsUserCanSee(user : Box[User]) : List[BankAccount]
  def getAllAccountsUserCanSee(bank: Bank, user : Box[User]) : List[BankAccount]
  def getNonPublicBankAccounts(user : User) : List[BankAccount]
  def getNonPublicBankAccounts(user : User, bankId : BankId) : List[BankAccount]
}

trait RemoteViewCases {

  case class permissions(account: BankAccount)

  case class permission(account: BankAccount, user: User)

  case class addPermission(viewUID: ViewUID, user: User)

  case class addPermissions(views: List[ViewUID], user: User)

  case class revokePermission(viewUID: ViewUID, user: User)

  case class revokeAllPermission(bankId: BankId, accountId: AccountId, user: User)

  case class createView(bankAccount: BankAccount, view: CreateViewJSON)

  case class removeView(viewId: ViewId, bankAccount: BankAccount)

  case class updateView(bankAccount: BankAccount, viewId: ViewId, viewUpdateJson: UpdateViewJSON)

  case class views(bankAccount: BankAccount)

  case class permittedViews(user: User, bankAccount: BankAccount)

  case class publicViews(bankAccount: BankAccount)

  case class getAllPublicAccounts()

  case class getPublicBankAccounts(bank: Bank)

  case class getAllAccountsUserCanSee(pars: Any*) {
    def apply(user: Box[User]): List[BankAccount] = this (user)

    def apply(bank: Bank, user: Box[User]): List[BankAccount] = this (bank, user)
  }

  case class getNonPublicBankAccounts(pars: Any*) {
    def apply(user: User): List[BankAccount] = this (user)

    def apply(user: User, bankId: BankId): List[BankAccount] = this (user, bankId)
  }

  case class view(pars: Any*) {
    def apply(viewUID: ViewUID): Box[View] = this (viewUID)

    def apply(viewId: ViewId, bankAccount: BankAccount): Box[View] = this (viewId, bankAccount)
  }

}

object RemoteViewCases extends RemoteViewCases

