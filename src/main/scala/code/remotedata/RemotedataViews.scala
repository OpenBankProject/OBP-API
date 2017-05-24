package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.model.{CreateViewJson, Permission, UpdateViewJSON, _}
import code.views.{RemotedataViewsCaseClasses, Views}
import net.liftweb.common.{Box, Full}
import scala.collection.immutable.List


object RemotedataViews extends ObpActorInit with Views {

  val cc = RemotedataViewsCaseClasses

  def addPermissions(views: List[ViewUID], user: User): Box[List[View]] =
    extractFutureToBox(actor ? cc.addPermissions(views, user))

  def permission(account: BankAccountUID, user: User): Box[Permission] =
    extractFutureToBox(actor ? cc.permission(account, user))

  def addPermission(viewUID: ViewUID, user: User): Box[View] =
    extractFutureToBox(actor ? cc.addPermission(viewUID, user))

  def revokePermission(viewUID : ViewUID, user : User) : Box[Boolean] =
    extractFutureToBox(actor ? cc.revokePermission(viewUID, user))

  def revokeAllPermissions(bankId : BankId, accountId: AccountId, user : User) : Box[Boolean] =
    extractFutureToBox(actor ? cc.revokeAllPermissions(bankId, accountId, user))

  def view(viewUID : ViewUID) : Box[View] = 
    extractFutureToBox(actor ? cc.view(viewUID))

  def view(viewId : ViewId, account: BankAccountUID) : Box[View] =
    extractFutureToBox(actor ? cc.view(viewId, account))

  def createView(bankAccountId: BankAccountUID, view: CreateViewJson): Box[View] =
    extractFutureToBox(actor ? cc.createView(bankAccountId, view))

  def updateView(bankAccountId : BankAccountUID, viewId: ViewId, viewUpdateJson : UpdateViewJSON) : Box[View] =
    extractFutureToBox(actor ? cc.updateView(bankAccountId, viewId, viewUpdateJson))

  def removeView(viewId: ViewId, bankAccountId: BankAccountUID): Box[Unit] =
    extractFutureToBox(actor ? cc.removeView(viewId, bankAccountId))

  def permissions(account : BankAccountUID) : List[Permission] =
    extractFuture(actor ? cc.permissions(account))

  def views(bankAccountId : BankAccountUID) : List[View] =
    extractFuture(actor ? cc.views(bankAccountId))

  def permittedViews(user: User, bankAccountId: BankAccountUID): List[View] =
    extractFuture(actor ? cc.permittedViews(user, bankAccountId))

  def publicViews(bankAccountId : BankAccountUID) : List[View] =
    extractFuture(actor ? cc.publicViews(bankAccountId))

  def getAllPublicAccounts() : List[BankAccountUID] =
    extractFuture(actor ? cc.getAllPublicAccounts())

  def getPublicBankAccounts(bank : Bank) : List[BankAccountUID] =
    extractFuture(actor ? cc.getPublicBankAccounts(bank))

  def getAllAccountsUserCanSee(user : Box[User]) : List[BankAccountUID] =
    user match {
      case Full(theUser) => {
        extractFuture(actor ? cc.getAllAccountsUserCanSee(theUser))
      }
      case _ => getAllPublicAccounts()
    }

  def getAllAccountsUserCanSee(bank: Bank, user : Box[User]) : List[BankAccountUID] =
    user match {
      case Full(theUser) => {
        extractFuture(actor ? cc.getAllAccountsUserCanSee(bank, theUser))
      }
      case _ => getPublicBankAccounts(bank)
    }

  def getNonPublicBankAccounts(user : User) :  List[BankAccountUID] =
    extractFuture(actor ? cc.getNonPublicBankAccounts(user))

  def getNonPublicBankAccounts(user : User, bankId : BankId) :  List[BankAccountUID] =
    extractFuture(actor ? cc.getNonPublicBankAccounts(user, bankId))

  def grantAccessToView(user : User, view : View): Boolean =
    extractFuture(actor ? cc.grantAccessToView(user, view))

  def getOwners(view: View) : Set[User] =
    extractFuture(actor ? cc.getOwners(view))

  def createOwnerView(bankId: BankId, accountId: AccountId, description: String) : Box[View] =
    extractFutureToBox(actor ? cc.createOwnerView(bankId, accountId, description))

  def createPublicView(bankId: BankId, accountId: AccountId, description: String) : Box[View] =
    extractFutureToBox(actor ? cc.createPublicView(bankId, accountId, description))

  def createAccountantsView(bankId: BankId, accountId: AccountId, description: String) : Box[View] =
   extractFutureToBox(actor ? cc.createAccountantsView(bankId, accountId, description))

  def createAuditorsView(bankId: BankId, accountId: AccountId, description: String) : Box[View] =
   extractFutureToBox(actor ? cc.createAuditorsView(bankId, accountId, description))

  def createRandomView(bankId: BankId, accountId: AccountId) : Box[View] =
    extractFutureToBox(actor ? cc.createRandomView(bankId, accountId))

  def viewExists(bankId: BankId, accountId: AccountId, name: String): Boolean =
    extractFuture(actor ? cc.viewExists(bankId, accountId, name))

  // For tests
  def bulkDeleteAllPermissionsAndViews(): Boolean =
    extractFuture(actor ? cc.bulkDeleteAllPermissionsAndViews())

  def grantAccessToAllExistingViews(user : User): Boolean =
    extractFuture(actor ? cc.grantAccessToAllExistingViews(user))

  def removeAllViews(bankId: BankId, accountId: AccountId): Boolean =
    extractFuture(actor ? cc.removeAllViews(bankId, accountId))

  def removeAllPermissions(bankId: BankId, accountId: AccountId): Boolean =
    extractFuture(actor ? cc.removeAllViews(bankId, accountId))


}
