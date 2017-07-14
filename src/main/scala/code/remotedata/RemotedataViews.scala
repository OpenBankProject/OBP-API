package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.model.{CreateViewJson, Permission, UpdateViewJSON, _}
import code.views.{RemotedataViewsCaseClasses, Views}
import net.liftweb.common.{Box, Full}
import scala.collection.immutable.List


object RemotedataViews extends ObpActorInit with Views {

  val cc = RemotedataViewsCaseClasses

  def addPermissions(views: List[ViewIdBankIdAccountId], user: User): Box[List[View]] =
    extractFutureToBox(actor ? cc.addPermissions(views, user))

  def permission(account: BankIdAccountId, user: User): Box[Permission] =
    extractFutureToBox(actor ? cc.permission(account, user))

  def addPermission(viewIdBankIdAccountId: ViewIdBankIdAccountId, user: User): Box[View] =
    extractFutureToBox(actor ? cc.addPermission(viewIdBankIdAccountId, user))
  
  def getOrCreateViewPrivilege(bankIdAccountId: BankIdAccountId, viewIdBankIdAccountId: ViewIdBankIdAccountId, user: User): Box[View] =
    extractFutureToBox(actor ? cc.getOrCreateViewPrivilege(bankIdAccountId: BankIdAccountId,
      viewIdBankIdAccountId: ViewIdBankIdAccountId, user: User))

  def revokePermission(viewIdBankIdAccountId : ViewIdBankIdAccountId, user : User) : Box[Boolean] =
    extractFutureToBox(actor ? cc.revokePermission(viewIdBankIdAccountId, user))

  def revokeAllPermissions(bankId : BankId, accountId: AccountId, user : User) : Box[Boolean] =
    extractFutureToBox(actor ? cc.revokeAllPermissions(bankId, accountId, user))

  def view(viewUID : ViewIdBankIdAccountId) : Box[View] =
    extractFutureToBox(actor ? cc.view(viewUID))

  def view(viewId : ViewId, account: BankIdAccountId) : Box[View] =
    extractFutureToBox(actor ? cc.view(viewId, account))

  def createView(bankAccountId: BankIdAccountId, view: CreateViewJson): Box[View] =
    extractFutureToBox(actor ? cc.createView(bankAccountId, view))

  def updateView(bankAccountId : BankIdAccountId, viewId: ViewId, viewUpdateJson : UpdateViewJSON) : Box[View] =
    extractFutureToBox(actor ? cc.updateView(bankAccountId, viewId, viewUpdateJson))

  def removeView(viewId: ViewId, bankAccountId: BankIdAccountId): Box[Unit] =
    extractFutureToBox(actor ? cc.removeView(viewId, bankAccountId))

  def permissions(account : BankIdAccountId) : List[Permission] =
    extractFuture(actor ? cc.permissions(account))

  def views(bankAccountId : BankIdAccountId) : List[View] =
    extractFuture(actor ? cc.views(bankAccountId))

  def permittedViews(user: User, bankAccountId: BankIdAccountId): List[View] =
    extractFuture(actor ? cc.permittedViews(user, bankAccountId))

  def publicViews(bankAccountId : BankIdAccountId) : List[View] =
    extractFuture(actor ? cc.publicViews(bankAccountId))

  def getAllPublicAccounts() : List[BankIdAccountId] =
    extractFuture(actor ? cc.getAllPublicAccounts())

  def getPublicBankAccounts(bank : Bank) : List[BankIdAccountId] =
    extractFuture(actor ? cc.getPublicBankAccounts(bank))

  def getAllAccountsUserCanSee(user : Box[User]) : List[BankIdAccountId] =
    user match {
      case Full(theUser) => {
        extractFuture(actor ? cc.getAllAccountsUserCanSee(theUser))
      }
      case _ => getAllPublicAccounts()
    }

  def getAllAccountsUserCanSee(bank: Bank, user : Box[User]) : List[BankIdAccountId] =
    user match {
      case Full(theUser) => {
        extractFuture(actor ? cc.getAllAccountsUserCanSee(bank, theUser))
      }
      case _ => getPublicBankAccounts(bank)
    }

  def getNonPublicBankAccounts(user : User) :  List[BankIdAccountId] =
    extractFuture(actor ? cc.getNonPublicBankAccounts(user))

  def getNonPublicBankAccounts(user : User, bankId : BankId) :  List[BankIdAccountId] =
    extractFuture(actor ? cc.getNonPublicBankAccounts(user, bankId))

  def grantAccessToView(user : User, view : View): Boolean =
    extractFuture(actor ? cc.grantAccessToView(user, view))

  def getOwners(view: View) : Set[User] =
    extractFuture(actor ? cc.getOwners(view))
  
  def getOrCreateAccountView(bankAccountUID: BankIdAccountId, viewId: String): Box[View] =
    extractFutureToBox(actor ? cc.getOrCreateAccountView(bankAccountUID: BankIdAccountId, viewId: String))
  
  def getOrCreateOwnerView(bankId: BankId, accountId: AccountId, description: String) : Box[View] =
    extractFutureToBox(actor ? cc.getOrCreateOwnerView(bankId, accountId, description))

  def getOrCreatePublicView(bankId: BankId, accountId: AccountId, description: String) : Box[View] =
    extractFutureToBox(actor ? cc.getOrCreatePublicView(bankId, accountId, description))

  def getOrCreateAccountantsView(bankId: BankId, accountId: AccountId, description: String) : Box[View] =
   extractFutureToBox(actor ? cc.getOrCreateAccountantsView(bankId, accountId, description))

  def getOrCreateAuditorsView(bankId: BankId, accountId: AccountId, description: String) : Box[View] =
   extractFutureToBox(actor ? cc.getOrCreateAuditorsView(bankId, accountId, description))

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
