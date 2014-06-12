package code.views

import net.liftweb.common.Box
import code.model.BankAccount
import code.model.User
import code.model.ViewCreationJSON
import code.model.View
import code.model.Permission
import net.liftweb.util.SimpleInjector

object Views  extends SimpleInjector {

  val users = new Inject(buildOne _) {}
  
  def buildOne: Views = MapperViews
  
}

trait Views {
  
  def permissions(account : BankAccount) : Box[Iterable[Permission]]
  def permission(account : BankAccount, user: User) : Box[Permission]
  def addPermission(bankAccountId : String, view : View, user : User) : Box[Boolean]
  def addPermissions(bankAccountId : String, views : List[View], user : User) : Box[Boolean]
  def revokePermission(bankAccountId : String, view : View, user : User) : Box[Boolean]
  def revokeAllPermission(bankAccountId : String, user : User) : Box[Boolean]

  def view(viewPermalink : String, bankAccount: BankAccount) : Box[View]
  def view(viewPermalink : String, accountId: String, bankId: String) : Box[View]

  def createView(bankAccount : BankAccount, view: ViewCreationJSON) : Box[View]
  def removeView(viewId: String, bankAccount: BankAccount): Box[Unit]
  def views(bankAccount : BankAccount) : Box[List[View]]
  def permittedViews(user: User, bankAccount: BankAccount): Iterable[View]
  def publicViews(bankAccount : BankAccount) : Box[Iterable[View]]
}