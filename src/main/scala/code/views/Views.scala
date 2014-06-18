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
  
  def permissions(account : BankAccount) : Box[List[Permission]]
  def permission(account : BankAccount, user: User) : Box[Permission]
  def addPermission(bankAccountId : String, view : View, user : User) : Box[Boolean]
  def addPermissions(bankAccountId : String, views : List[View], user : User) : Box[Boolean]
  def revokePermission(bankAccountId : String, view : View, user : User) : Box[Boolean]
  def revokeAllPermission(bankAccountId : String, user : User) : Box[Boolean]

  def view(viewPermalink : String, bankAccount: BankAccount) : Box[View]
  def view(viewPermalink : String, accountId: String, bankId: String) : Box[View]

  def createView(bankAccount : BankAccount, view: ViewCreationJSON) : Box[View]
  def removeView(viewId: String, bankAccount: BankAccount): Box[Unit]
  def updateView(bankAccount : BankAccount, viewId: String, viewUpdateJson : ViewUpdateData) : Box[View]
  def views(bankAccount : BankAccount) : Box[List[View]]
  def permittedViews(user: User, bankAccount: BankAccount): List[View]
  def publicViews(bankAccount : BankAccount) : Box[List[View]]
}