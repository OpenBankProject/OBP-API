package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.api.util.CallContext
import code.views.system.AccountAccess
import code.views.{RemotedataViewsCaseClasses, Views}
import com.openbankproject.commons.model.{UpdateViewJSON, _}
import net.liftweb.common.Box

import scala.concurrent.Future


object RemotedataViews extends ObpActorInit with Views {

  val cc = RemotedataViewsCaseClasses

  def grantAccessToMultipleViews(views: List[BankIdAccountIdViewId], user: User, callContext: Option[CallContext]): Box[List[View]] = getValueFromFuture(
    (actor ? cc.grantAccessToMultipleViews(views, user, callContext)).mapTo[Box[List[View]]]
  )
  
  def revokeAccessToMultipleViews(views: List[BankIdAccountIdViewId], user: User): Box[List[View]] = getValueFromFuture(
    (actor ? cc.revokeAccessToMultipleViews(views, user)).mapTo[Box[List[View]]]
  )

  def permission(account: BankIdAccountId, user: User): Box[Permission] = getValueFromFuture(
    (actor ? cc.permission(account, user)).mapTo[Box[Permission]]
  )

  def getViewByBankIdAccountIdViewIdUserPrimaryKey(bankIdAccountIdViewId: BankIdAccountIdViewId, userPrimaryKey: UserPrimaryKey): Box[View] = getValueFromFuture(
    (actor ? cc.getViewBydBankIdAccountIdViewIdAndUser(bankIdAccountIdViewId: BankIdAccountIdViewId, userPrimaryKey: UserPrimaryKey)).mapTo[Box[View]]
  )
  
  def getPermissionForUser(user: User): Box[Permission] = getValueFromFuture(
    (actor ? cc.getPermissionForUser(user)).mapTo[Box[Permission]]
  )

  def grantAccessToCustomView(bankIdAccountIdViewId: BankIdAccountIdViewId, user: User): Box[View] = getValueFromFuture(
    (actor ? cc.addPermission(bankIdAccountIdViewId, user)).mapTo[Box[View]]
  )
  
  def grantAccessToSystemView(bankId: BankId, accountId: AccountId, view: View, user: User): Box[View] = getValueFromFuture(
    (actor ? cc.addSystemViewPermission(bankId, accountId, view, user)).mapTo[Box[View]]
  )
  
  def revokeAccess(bankIdAccountIdViewId : BankIdAccountIdViewId, user : User) : Box[Boolean] =  getValueFromFuture(
    (actor ? cc.revokeAccess(bankIdAccountIdViewId, user)).mapTo[Box[Boolean]]
  ) 
  
  def revokeAccessToSystemView(bankId: BankId, accountId: AccountId, view : View, user : User) : Box[Boolean] =  getValueFromFuture(
    (actor ? cc.revokeSystemViewPermission(bankId, accountId, view, user)).mapTo[Box[Boolean]]
  )

  def revokeAllAccountAccess(bankId : BankId, accountId: AccountId, user : User) : Box[Boolean] = getValueFromFuture(
    (actor ? cc.revokeAllAccountAccess(bankId, accountId, user)).mapTo[Box[Boolean]]
  )
  
  def revokeAccountAccessByUser(bankId : BankId, accountId: AccountId, user : User, callContext: Option[CallContext]) : Box[Boolean] = getValueFromFuture(
    (actor ? cc.revokeAccountAccessByUser(bankId, accountId, user, callContext)).mapTo[Box[Boolean]]
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

  def getSystemViews() : Future[List[View]] =
    (actor ? cc.getSystemViews()).mapTo[List[View]]
  

  def createCustomView(bankAccountId: BankIdAccountId, view: CreateViewJson): Box[View] = getValueFromFuture(
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
  def privateViewsUserCanAccessAtBankThroughViews(user: User, bankId: BankId, viewIds: List[ViewId]): ((List[View], List[AccountAccess])) = getValueFromFuture(
    (actor ? cc.privateViewsUserCanAccessAtBankThroughViews(user, bankId, viewIds)).mapTo[(List[View], List[AccountAccess])]
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
  
  def getOrCreateSystemViewFromCbs(viewId: String): Box[View] = getValueFromFuture(
    (actor ? cc.getOrCreateSystemViewFromCbs(viewId: String)).mapTo[Box[View]]
  )
  
  def getOrCreateSystemView(viewId: String) : Box[View] = getValueFromFuture(
    (actor ? cc.getOrCreateSystemView(viewId)).mapTo[Box[View]]
  )
  
  def getOrCreateCustomPublicView(bankId: BankId, accountId: AccountId, description: String) : Box[View] = getValueFromFuture(
    (actor ? cc.getOrCreatePublicPublicView(bankId, accountId, description)).mapTo[Box[View]]
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


  def revokeAccessToSystemViewForConsumer(bankId: BankId, accountId: AccountId, view : View, consumerId : String) : Box[Boolean] = getValueFromFuture(
    (actor ? cc.revokeAccessToSystemViewForConsumer(bankId: BankId, accountId: AccountId, view : View, consumerId : String)).mapTo[Box[Boolean]]
  )
  
  def revokeAccessToCustomViewForConsumer(view : View, consumerId : String) : Box[Boolean] = getValueFromFuture(
    (actor ? cc.revokeAccessToCustomViewForConsumer(view : View, consumerId : String)).mapTo[Box[Boolean]]
  )

}
