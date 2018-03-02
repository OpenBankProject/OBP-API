package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.model.{CreateViewJson, Permission, UpdateViewJSON, _}
import code.views.{RemotedataViewsCaseClasses, Views}
import net.liftweb.common.{Box, Full}
import scala.collection.immutable.List
import scala.concurrent.Future


object RemotedataViews extends ObpActorInit with Views {

  val cc = RemotedataViewsCaseClasses

  def addPermissions(views: List[ViewIdBankIdAccountId], user: User): Box[List[View]] =
    extractFutureToBox(actor ? cc.addPermissions(views, user))

  def permission(account: BankIdAccountId, user: User): Box[Permission] =
    extractFutureToBox(actor ? cc.permission(account, user))

  def addPermission(viewIdBankIdAccountId: ViewIdBankIdAccountId, user: User): Box[View] =
    extractFutureToBox(actor ? cc.addPermission(viewIdBankIdAccountId, user))
  
  def getOrCreateViewPrivilege(view: View, user: User): Box[View] =
    extractFutureToBox(actor ? cc.getOrCreateViewPrivilege(view: View, user: User))

  def revokePermission(viewIdBankIdAccountId : ViewIdBankIdAccountId, user : User) : Box[Boolean] =
    extractFutureToBox(actor ? cc.revokePermission(viewIdBankIdAccountId, user))

  def revokeAllPermissions(bankId : BankId, accountId: AccountId, user : User) : Box[Boolean] =
    extractFutureToBox(actor ? cc.revokeAllPermissions(bankId, accountId, user))

  def view(viewId : ViewId, account: BankIdAccountId) : Box[View] =
    extractFutureToBox(actor ? cc.view(viewId, account))

  def viewFuture(viewId : ViewId, account: BankIdAccountId) : Future[Box[View]] =
    (actor ? cc.viewFuture(viewId, account)).mapTo[Box[View]]

  def createView(bankAccountId: BankIdAccountId, view: CreateViewJson): Box[View] =
    extractFutureToBox(actor ? cc.createView(bankAccountId, view))

  def updateView(bankAccountId : BankIdAccountId, viewId: ViewId, viewUpdateJson : UpdateViewJSON) : Box[View] =
    extractFutureToBox(actor ? cc.updateView(bankAccountId, viewId, viewUpdateJson))

  def removeView(viewId: ViewId, bankAccountId: BankIdAccountId): Box[Unit] =
    extractFutureToBox(actor ? cc.removeView(viewId, bankAccountId))

  def permissions(account : BankIdAccountId) : List[Permission] =
    extractFuture(actor ? cc.permissions(account))

  def viewsForAccount(bankAccountId : BankIdAccountId) : List[View] =
    extractFuture(actor ? cc.viewsForAccount(bankAccountId))
  
  def privateViewsUserCanAccess(user: User): List[View] =
    extractFuture(actor ? cc.privateViewsUserCanAccess(user: User))
  
  def privateViewsUserCanAccessForAccount(user: User, bankIdAccountId : BankIdAccountId): List[View] =
    extractFuture(actor ? cc.privateViewsUserCanAccessForAccount(user: User, bankIdAccountId : BankIdAccountId))
  
  def getAllFirehoseAccounts(bank: Bank, user : User) : List[BankIdAccountId] =
    extractFuture(actor ? cc.getAllFirehoseAccounts(bank: Bank, user : User))
  
  def publicViews : List[View] =
    extractFuture(actor ? cc.publicViews())
  
  def publicViewsForBank(bankId: BankId) : List[View] =
    extractFuture(actor ? cc.publicViewsForBank(bankId: BankId))
  
  def getPrivateBankAccounts(user : User) :  List[BankIdAccountId] =
    extractFuture(actor ? cc.getPrivateBankAccounts(user))

  def getPrivateBankAccountsFuture(user : User) :  Future[List[BankIdAccountId]] =
    (actor ? cc.getPrivateBankAccounts(user)).mapTo[List[BankIdAccountId]]

  def getPrivateBankAccountsFuture(user : User, bankId : BankId) :  Future[List[BankIdAccountId]] =
    (actor ? cc.getPrivateBankAccounts(user, bankId)).mapTo[List[BankIdAccountId]]

  def getPrivateBankAccounts(user : User, bankId : BankId) :  List[BankIdAccountId] =
    extractFuture(actor ? cc.getPrivateBankAccounts(user, bankId))

  def grantAccessToView(user : User, view : View): Boolean =
    extractFuture(actor ? cc.grantAccessToView(user, view))

  def getOwners(view: View) : Set[User] =
    extractFuture(actor ? cc.getOwners(view))
  
  def getOrCreateAccountView(bankAccountUID: BankIdAccountId, viewId: String): Box[View] =
    extractFutureToBox(actor ? cc.getOrCreateAccountView(bankAccountUID: BankIdAccountId, viewId: String))
  
  def getOrCreateOwnerView(bankId: BankId, accountId: AccountId, description: String) : Box[View] =
    extractFutureToBox(actor ? cc.getOrCreateOwnerView(bankId, accountId, description))
  
  def getOrCreateFirehoseView(bankId: BankId, accountId: AccountId, description: String) : Box[View] =
    extractFutureToBox(actor ? cc.getOrCreateFirehoseView(bankId, accountId, description))
  
  def getOrCreatePublicView(bankId: BankId, accountId: AccountId, description: String) : Box[View] =
    extractFutureToBox(actor ? cc.getOrCreatePublicView(bankId, accountId, description))

  def getOrCreateAccountantsView(bankId: BankId, accountId: AccountId, description: String) : Box[View] =
   extractFutureToBox(actor ? cc.getOrCreateAccountantsView(bankId, accountId, description))

  def getOrCreateAuditorsView(bankId: BankId, accountId: AccountId, description: String) : Box[View] =
   extractFutureToBox(actor ? cc.getOrCreateAuditorsView(bankId, accountId, description))

  def createRandomView(bankId: BankId, accountId: AccountId) : Box[View] =
    extractFutureToBox(actor ? cc.createRandomView(bankId, accountId))

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
