package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.views.{RemotedataViewsCaseClasses, Views}
import com.openbankproject.commons.model.{UpdateViewJSON, _}
import net.liftweb.common.Box

import scala.collection.immutable.List
import scala.concurrent.Future


object RemotedataViews extends ObpActorInit with Views {

  val cc = RemotedataViewsCaseClasses

  def addPermissions(views: List[ViewIdBankIdAccountId], user: User): Box[List[View]] = getValueFromFuture(
    (actor ? cc.addPermissions(views, user)).mapTo[Box[List[View]]]
  )

  def permission(account: BankIdAccountId, user: User): Box[Permission] = getValueFromFuture(
    (actor ? cc.permission(account, user)).mapTo[Box[Permission]]
  )
  
  def getPermissionForUser(user: User): Box[Permission] = getValueFromFuture(
    (actor ? cc.getPermissionForUser(user)).mapTo[Box[Permission]]
  )

  def addPermission(viewIdBankIdAccountId: ViewIdBankIdAccountId, user: User): Box[View] = getValueFromFuture(
    (actor ? cc.addPermission(viewIdBankIdAccountId, user)).mapTo[Box[View]]
  )
  
  def revokePermission(viewIdBankIdAccountId : ViewIdBankIdAccountId, user : User) : Box[Boolean] =  getValueFromFuture(
    (actor ? cc.revokePermission(viewIdBankIdAccountId, user)).mapTo[Box[Boolean]]
  )

  def revokeAllPermissions(bankId : BankId, accountId: AccountId, user : User) : Box[Boolean] = getValueFromFuture(
    (actor ? cc.revokeAllPermissions(bankId, accountId, user)).mapTo[Box[Boolean]]
  )

  def view(viewId : ViewId, account: BankIdAccountId) : Box[View] = getValueFromFuture(
    (actor ? cc.view(viewId, account)).mapTo[Box[View]]
  )

  def viewFuture(viewId : ViewId, account: BankIdAccountId) : Future[Box[View]] = 
    (actor ? cc.viewFuture(viewId, account)).mapTo[Box[View]]
  
  def systemViewFuture(viewId : ViewId) : Future[Box[View]] = 
    (actor ? cc.systemViewFuture(viewId)).mapTo[Box[View]]

  def createView(bankAccountId: BankIdAccountId, view: CreateViewJson): Box[View] = getValueFromFuture(
    (actor ? cc.createView(bankAccountId, view)).mapTo[Box[View]]
  )
  def createSystemView(view: CreateViewJson): Future[Box[View]] = 
    (actor ? cc.createSystemView(view)).mapTo[Box[View]]
  
  def removeSystemView(viewId: ViewId): Future[Box[Boolean]] = 
    (actor ? cc.removeSystemView(viewId)).mapTo[Box[Boolean]]

  def updateView(bankAccountId : BankIdAccountId, viewId: ViewId, viewUpdateJson : UpdateViewJSON) : Box[View] = getValueFromFuture(
    (actor ? cc.updateView(bankAccountId, viewId, viewUpdateJson)).mapTo[Box[View]]
  )
  def updateSystemView(viewId: ViewId, viewUpdateJson : UpdateViewJSON) : Future[Box[View]] =
    (actor ? cc.updateSystemView(viewId, viewUpdateJson)).mapTo[Box[View]]

  def removeView(viewId: ViewId, bankAccountId: BankIdAccountId): Box[Unit] = getValueFromFuture(
    (actor ? cc.removeView(viewId, bankAccountId)).mapTo[Box[Unit]]
  )

  def permissions(account : BankIdAccountId) : List[Permission] = getValueFromFuture(
    (actor ? cc.permissions(account)).mapTo[List[Permission]]
  )

  def viewsForAccount(bankAccountId : BankIdAccountId) : List[View] = getValueFromFuture(
    (actor ? cc.viewsForAccount(bankAccountId)).mapTo[List[View]]
  )
  
  def privateViewsUserCanAccess(user: User): List[View] = getValueFromFuture(
    (actor ? cc.privateViewsUserCanAccess(user: User)).mapTo[List[View]]
  )
  
  def privateViewsUserCanAccessForAccount(user: User, bankIdAccountId : BankIdAccountId): List[View] = getValueFromFuture(
    (actor ? cc.privateViewsUserCanAccessForAccount(user: User, bankIdAccountId : BankIdAccountId)).mapTo[List[View]]
  )
  
  def publicViews : List[View] = getValueFromFuture(
    (actor ? cc.publicViews()).mapTo[List[View]]
  )
  
  def publicViewsForBank(bankId: BankId) : List[View] = getValueFromFuture(
    (actor ? cc.publicViewsForBank(bankId: BankId)).mapTo[List[View]]
  )
  
  def firehoseViewsForBank(bankId: BankId, user : User): List[View] = getValueFromFuture(
    (actor ? cc.firehoseViewsForBank(bankId: BankId, user : User)).mapTo[List[View]]
  )

  def getOwners(view: View) : Set[User] = getValueFromFuture(
    (actor ? cc.getOwners(view)).mapTo[Set[User]]
  )
  
  def getOrCreateAccountView(bankAccountUID: BankIdAccountId, viewId: String): Box[View] = getValueFromFuture(
    (actor ? cc.getOrCreateAccountView(bankAccountUID: BankIdAccountId, viewId: String)).mapTo[Box[View]]
  )
  
  def getOrCreateOwnerView(bankId: BankId, accountId: AccountId, description: String) : Box[View] = getValueFromFuture(
    (actor ? cc.getOrCreateOwnerView(bankId, accountId, description)).mapTo[Box[View]]
  )
  
  def getOrCreateFirehoseView(bankId: BankId, accountId: AccountId, description: String) : Box[View] = getValueFromFuture(
    (actor ? cc.getOrCreateFirehoseView(bankId, accountId, description)).mapTo[Box[View]]
  )
  
  def getOrCreatePublicView(bankId: BankId, accountId: AccountId, description: String) : Box[View] = getValueFromFuture(
    (actor ? cc.getOrCreatePublicView(bankId, accountId, description)).mapTo[Box[View]]
  )

  def getOrCreateAccountantsView(bankId: BankId, accountId: AccountId, description: String) : Box[View] = getValueFromFuture(
   (actor ? cc.getOrCreateAccountantsView(bankId, accountId, description)).mapTo[Box[View]]
  )

  def getOrCreateAuditorsView(bankId: BankId, accountId: AccountId, description: String) : Box[View] = getValueFromFuture(
   (actor ? cc.getOrCreateAuditorsView(bankId, accountId, description)).mapTo[Box[View]]
  )

  def createRandomView(bankId: BankId, accountId: AccountId) : Box[View] = getValueFromFuture(
    (actor ? cc.createRandomView(bankId, accountId)).mapTo[Box[View]]
  )

  // For tests
  def bulkDeleteAllPermissionsAndViews(): Boolean = getValueFromFuture(
    (actor ? cc.bulkDeleteAllPermissionsAndViews()).mapTo[Boolean]
  )

  def grantAccessToAllExistingViews(user : User): Boolean = getValueFromFuture(
    (actor ? cc.grantAccessToAllExistingViews(user)).mapTo[Boolean]
  )

  def removeAllViews(bankId: BankId, accountId: AccountId): Boolean = getValueFromFuture(
    (actor ? cc.removeAllViews(bankId, accountId)).mapTo[Boolean]
  )

  def removeAllPermissions(bankId: BankId, accountId: AccountId): Boolean = getValueFromFuture(
    (actor ? cc.removeAllViews(bankId, accountId)).mapTo[Boolean]
  )


}
