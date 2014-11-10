package code.views

import net.liftweb.common.Box
import code.model._
import net.liftweb.util.SimpleInjector
import code.model.Permission
import code.model.ViewCreationJSON

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

  def createView(bankAccount : BankAccount, view: ViewCreationJSON) : Box[View]
  def removeView(viewId : ViewId, bankAccount: BankAccount): Box[Unit]
  def updateView(bankAccount : BankAccount, viewId : ViewId, viewUpdateJson : ViewUpdateData) : Box[View]
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