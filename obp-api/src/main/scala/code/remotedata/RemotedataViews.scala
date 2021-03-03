package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.views.system.AccountAccess
import code.views.{RemotedataViewsCaseClasses, Views}
import com.openbankproject.commons.model.{UpdateViewJSON, _}
import net.liftweb.common.Box

import scala.collection.immutable.List
import scala.concurrent.Future


object RemotedataViews extends ObpActorInit with Views {

  val cc = RemotedataViewsCaseClasses

  def grantAccessToMultipleViews(views: List[ViewIdBankIdAccountId], user: User): Box[List[View]] = getValueFromFuture(
    (actor ? cc.addPermissions(views, user)).mapTo[Box[List[View]]]
  )
  
  def revokeAccessToMultipleViews(views: List[ViewIdBankIdAccountId], user: User): Box[List[View]] = getValueFromFuture(
    (actor ? cc.revokePermissions(views, user)).mapTo[Box[List[View]]]
  )

  def permission(account: BankIdAccountId, user: User): Box[Permission] = getValueFromFuture(
    (actor ? cc.permission(account, user)).mapTo[Box[Permission]]
  )
  
  def getPermissionForUser(user: User): Box[Permission] = getValueFromFuture(
    (actor ? cc.getPermissionForUser(user)).mapTo[Box[Permission]]
  )

  def grantAccessToCustomView(viewIdBankIdAccountId: ViewIdBankIdAccountId, user: User): Box[View] = getValueFromFuture(
    (actor ? cc.addPermission(viewIdBankIdAccountId, user)).mapTo[Box[View]]
  )
  
  def grantAccessToSystemView(bankId: BankId, accountId: AccountId, view: View, user: User): Box[View] = getValueFromFuture(
    (actor ? cc.addSystemViewPermission(bankId, accountId, view, user)).mapTo[Box[View]]
  )
  
  def revokeAccess(viewIdBankIdAccountId : ViewIdBankIdAccountId, user : User) : Box[Boolean] =  getValueFromFuture(
    (actor ? cc.revokePermission(viewIdBankIdAccountId, user)).mapTo[Box[Boolean]]
  ) 
  
  def revokeAccessToSystemView(bankId: BankId, accountId: AccountId, view : View, user : User) : Box[Boolean] =  getValueFromFuture(
    (actor ? cc.revokeSystemViewPermission(bankId, accountId, view, user)).mapTo[Box[Boolean]]
  )

  def revokeAllAccountAccesses(bankId : BankId, accountId: AccountId, user : User) : Box[Boolean] = getValueFromFuture(
    (actor ? cc.revokeAllAccountAccesses(bankId, accountId, user)).mapTo[Box[Boolean]]
  )
  
  def revokeAccountAccessesByUser(bankId : BankId, accountId: AccountId, user : User) : Box[Boolean] = getValueFromFuture(
    (actor ? cc.revokeAccountAccessesByUser(bankId, accountId, user)).mapTo[Box[Boolean]]
  )

  def customView(viewId : ViewId, account: BankIdAccountId) : Box[View] = getValueFromFuture(
    (actor ? cc.customView(viewId, account)).mapTo[Box[View]]
  )
  
  def systemView(viewId : ViewId) : Box[View] = getValueFromFuture(
    (actor ? cc.systemView(viewId)).mapTo[Box[View]]
  )

  def customViewFuture(viewId : ViewId, account: BankIdAccountId) : Future[Box[View]] = 
    (actor ? cc.customViewFuture(viewId, account)).mapTo[Box[View]]
  
  def systemViewFuture(viewId : ViewId) : Future[Box[View]] = 
    (actor ? cc.systemViewFuture(viewId)).mapTo[Box[View]]

  def createView(bankAccountId: BankIdAccountId, view: CreateViewJson): Box[View] = getValueFromFuture(
    (actor ? cc.createView(bankAccountId, view)).mapTo[Box[View]]
  )
  def createSystemView(view: CreateViewJson): Future[Box[View]] = 
    (actor ? cc.createSystemView(view)).mapTo[Box[View]]
  
  def removeSystemView(viewId: ViewId): Future[Box[Boolean]] = 
    (actor ? cc.removeSystemView(viewId)).mapTo[Box[Boolean]]

  def updateCustomView(bankAccountId : BankIdAccountId, viewId: ViewId, viewUpdateJson : UpdateViewJSON) : Box[View] = getValueFromFuture(
    (actor ? cc.updateCustomView(bankAccountId, viewId, viewUpdateJson)).mapTo[Box[View]]
  )
  def updateSystemView(viewId: ViewId, viewUpdateJson : UpdateViewJSON) : Future[Box[View]] =
    (actor ? cc.updateSystemView(viewId, viewUpdateJson)).mapTo[Box[View]]

  def removeCustomView(viewId: ViewId, bankAccountId: BankIdAccountId): Box[Boolean] = getValueFromFuture(
    (actor ? cc.removeCustomView(viewId, bankAccountId)).mapTo[Box[Boolean]]
  )

  def permissions(account : BankIdAccountId) : List[Permission] = getValueFromFuture(
    (actor ? cc.permissions(account)).mapTo[List[Permission]]
  )

  def assignedViewsForAccount(bankAccountId : BankIdAccountId) : List[View] = getValueFromFuture(
    (actor ? cc.assignedViewsForAccount(bankAccountId)).mapTo[List[View]]
  )
  
  def availableViewsForAccount(bankAccountId : BankIdAccountId) : List[View] = getValueFromFuture(
    (actor ? cc.availableViewsForAccount(bankAccountId)).mapTo[List[View]]
  )
  
  def privateViewsUserCanAccess(user: User): (List[View], List[AccountAccess]) = getValueFromFuture(
    (actor ? cc.privateViewsUserCanAccess(user: User)).mapTo[(List[View], List[AccountAccess])]
  )  
  
  def privateViewsUserCanAccess(user: User, viewIds: List[ViewId]): (List[View], List[AccountAccess]) = getValueFromFuture(
    (actor ? cc.privateViewsUserCanAccessViaViewId(user: User, viewIds: List[ViewId])).mapTo[(List[View], List[AccountAccess])]
  )
  
  def privateViewsUserCanAccessAtBank(user: User, bankId: BankId): (List[View], List[AccountAccess]) = getValueFromFuture(
    (actor ? cc.privateViewsUserCanAccessAtBank(user, bankId)).mapTo[(List[View], List[AccountAccess])]
  )
  
  def privateViewsUserCanAccessForAccount(user: User, bankIdAccountId : BankIdAccountId): List[View] = getValueFromFuture(
    (actor ? cc.privateViewsUserCanAccessForAccount(user: User, bankIdAccountId : BankIdAccountId)).mapTo[List[View]]
  )
  
  def publicViews : (List[View], List[AccountAccess]) = getValueFromFuture(
    (actor ? cc.publicViews()).mapTo[(List[View], List[AccountAccess])]
  )
  
  def publicViewsForBank(bankId: BankId) : (List[View], List[AccountAccess]) = getValueFromFuture(
    (actor ? cc.publicViewsForBank(bankId: BankId)).mapTo[(List[View], List[AccountAccess])]
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
  
  def getOrCreateSystemView(name: String) : Box[View] = getValueFromFuture(
    (actor ? cc.getOrCreateSystemView(name)).mapTo[Box[View]]
  )
  
  def getOrCreateFirehoseView(bankId: BankId, accountId: AccountId, description: String) : Box[View] = getValueFromFuture(
    (actor ? cc.getOrCreateFirehoseView(bankId, accountId, description)).mapTo[Box[View]]
  )
  
  def getOrCreateCustomPublicView(bankId: BankId, accountId: AccountId, description: String) : Box[View] = getValueFromFuture(
    (actor ? cc.getOrCreatePublicView(bankId, accountId, description)).mapTo[Box[View]]
  )

  def getOrCreateAccountantsView(bankId: BankId, accountId: AccountId, description: String) : Box[View] = getValueFromFuture(
   (actor ? cc.getOrCreateAccountantsView(bankId, accountId, description)).mapTo[Box[View]]
  )

  def getOrCreateAuditorsView(bankId: BankId, accountId: AccountId, description: String) : Box[View] = getValueFromFuture(
   (actor ? cc.getOrCreateAuditorsView(bankId, accountId, description)).mapTo[Box[View]]
  )

  def createCustomRandomView(bankId: BankId, accountId: AccountId) : Box[View] = getValueFromFuture(
    (actor ? cc.createRandomView(bankId, accountId)).mapTo[Box[View]]
  )

  // For tests
  def bulkDeleteAllPermissionsAndViews(): Boolean = getValueFromFuture(
    (actor ? cc.bulkDeleteAllPermissionsAndViews()).mapTo[Boolean]
  )

  def removeAllViews(bankId: BankId, accountId: AccountId): Boolean = getValueFromFuture(
    (actor ? cc.removeAllViews(bankId, accountId)).mapTo[Boolean]
  )

  def removeAllPermissions(bankId: BankId, accountId: AccountId): Boolean = getValueFromFuture(
    (actor ? cc.removeAllViews(bankId, accountId)).mapTo[Boolean]
  )


}
