package code.remotedata

import akka.actor.Actor
import akka.pattern.pipe
import code.actorsystem.ObpActorHelper
import code.api.util.CallContext
import code.util.Helper.MdcLoggable
import code.views.{MapperViews, RemotedataViewsCaseClasses}
import com.openbankproject.commons.model._
import com.openbankproject.commons.ExecutionContext.Implicits.global

import scala.collection.immutable.List

class RemotedataViewsActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MapperViews
  val cc = RemotedataViewsCaseClasses

  def receive: PartialFunction[Any, Unit] = {

    case cc.addPermission(bankIdAccountIdViewId : BankIdAccountIdViewId, user : User) =>
      logger.debug("addPermission(" + bankIdAccountIdViewId +"," + user +")")
      sender ! (mapper.grantAccessToCustomView(bankIdAccountIdViewId, user))
      
    case cc.addSystemViewPermission(bankId: BankId, accountId: AccountId, view : View, user : User) =>
      logger.debug("addSystemViewPermission(" + bankId +"," + accountId +"," + view +"," + user +")")
      sender ! (mapper.grantAccessToSystemView(bankId, accountId, view, user))

    case cc.permission(account : BankIdAccountId, user: User) =>
      logger.debug("permission(" + account +"," + user +")")
      sender ! (mapper.permission(account, user))

    case cc.getPermissionForUser(user: User) =>
      logger.debug("permission(" +user +")")
      sender ! (mapper.getPermissionForUser(user))
      
    case cc.revokeSystemViewPermission(bankId: BankId, accountId: AccountId, view : View, user : User) =>
      logger.debug("revokeSystemViewPermission(" + bankId +"," + accountId +"," + view +"," + user +")")
      sender ! (mapper.revokeAccessToSystemView(bankId, accountId, view, user))

    case cc.revokeAllAccountAccess(bankId : BankId, accountId : AccountId, user : User) =>
      logger.debug("revokeAllAccountAccess(" + bankId +"," + accountId +","+ user +")")
      sender ! (mapper.revokeAllAccountAccess(bankId, accountId, user))
      
    case cc.revokeAccountAccessByUser(bankId : BankId, accountId : AccountId, user : User, callContext: Option[CallContext]) =>
      logger.debug("revokeAccountAccessByUser(" + bankId +"," + accountId +","+ user +","+ callContext+")")
      sender ! (mapper.revokeAccountAccessByUser(bankId, accountId, user, callContext))

    case cc.customView(viewId: ViewId, bankAccountId: BankIdAccountId) =>
      logger.debug("customView(" + viewId +", "+ bankAccountId + ")")
      sender ! (mapper.customView(viewId, bankAccountId))
      
    case cc.systemView(viewId: ViewId) =>
      logger.debug("view(" + viewId  + ")")
      sender ! (mapper.systemView(viewId))
      
    case cc.getSystemViews() =>
      logger.debug("getSystemViews()")
      sender ! (mapper.getSystemViews())

    case cc.customViewFuture(viewId: ViewId, bankAccountId: BankIdAccountId) =>
      logger.debug("customViewFuture(" + viewId +", "+ bankAccountId + ")")
      sender ! (mapper.customView(viewId, bankAccountId))
      
    case cc.systemViewFuture(viewId: ViewId) =>
      logger.debug("systemViewFuture(" + viewId + ")")
      (mapper.systemViewFuture(viewId)) pipeTo sender  
      
    case cc.removeSystemView(viewId : ViewId) =>
      logger.debug("removeSystemView(" + viewId +")")
      (mapper.removeSystemView(viewId)) pipeTo sender

    case cc.createView(bankAccountId : BankIdAccountId, view: CreateViewJson) =>
      logger.debug("createView(" + bankAccountId +","+ view +")")
      sender ! (mapper.createCustomView(bankAccountId, view))
      
    case cc.createSystemView(view: CreateViewJson) =>
      logger.debug("createSystemView(" + view +")")
      mapper.createSystemView(view) pipeTo sender

    case cc.updateCustomView(bankAccountId : BankIdAccountId, viewId : ViewId, viewUpdateJson : UpdateViewJSON) =>
      logger.debug("updateCustomView(" + bankAccountId +","+ viewId +","+ viewUpdateJson +")")
      sender ! (mapper.updateCustomView(bankAccountId, viewId, viewUpdateJson))
      
    case cc.updateSystemView(viewId : ViewId, viewUpdateJson : UpdateViewJSON) =>
      logger.debug("updateSystemView(" + viewId +","+ viewUpdateJson +")")
      (mapper.updateSystemView(viewId, viewUpdateJson)) pipeTo sender

    case cc.removeCustomView(viewId : ViewId, bankAccountId: BankIdAccountId) =>
      logger.debug("removeCustomView(" + viewId +","+ bankAccountId +")")
      sender ! (mapper.removeCustomView(viewId, bankAccountId))

    case cc.permissions(bankAccountId : BankIdAccountId) =>
      logger.debug("premissions(" + bankAccountId +")")
      sender ! (mapper.permissions(bankAccountId))

    case cc.assignedViewsForAccount(bankAccountId : BankIdAccountId) =>
      logger.debug("assignedViewsForAccount(" + bankAccountId +")")
      sender ! (mapper.assignedViewsForAccount(bankAccountId))
      
    case cc.availableViewsForAccount(bankAccountId : BankIdAccountId) =>
      logger.debug("availableViewsForAccount(" + bankAccountId +")")
      sender ! (mapper.availableViewsForAccount(bankAccountId))

    case cc.privateViewsUserCanAccess(user: User) =>
      logger.debug("privateViewsUserCanAccess(" + user +")")
      sender ! (mapper.privateViewsUserCanAccess(user: User))
      
    case cc.privateViewsUserCanAccessViaViewId(user: User, viewIds: List[ViewId]) =>
      logger.debug("privateViewsUserCanAccess1(" + user + ", " +  viewIds + ")")
      sender ! (mapper.privateViewsUserCanAccess(user: User, viewIds: List[ViewId]))
      
    case cc.privateViewsUserCanAccessAtBank(user: User, bankId: BankId) =>
      logger.debug("privateViewsUserCanAccess(" + user + ", " + bankId + ")")
      sender ! (mapper.privateViewsUserCanAccessAtBank(user, bankId))

    case cc.privateViewsUserCanAccessForAccount(user: User, bankAccountId : BankIdAccountId)=>
      logger.debug("privateViewsUserCanAccessForAccount(" + user +"bankAccountId"+bankAccountId+")")
      sender ! (mapper.privateViewsUserCanAccessForAccount(user: User, bankAccountId : BankIdAccountId))
      
    case cc.publicViews() =>
      logger.debug("publicViews()")
      sender ! (mapper.publicViews)
      
    case cc.publicViewsForBank(bankId: BankId) =>
      logger.debug("publicViews()")
      sender ! (mapper.publicViewsForBank(bankId: BankId))
      
    case cc.getOrCreateSystemView(name) =>
      logger.debug("getOrCreateSystemOwnerView(" + name +")")
      sender ! (mapper.getOrCreateSystemView(name))

    case cc.getOrCreatePublicPublicView(bankId, accountId, description) =>
      logger.debug("getOrCreatePublicPublicView(" + bankId +", "+ accountId +", "+ description +")")
      sender ! (mapper.getOrCreateCustomPublicView(bankId, accountId, description))

    case cc.getOwners(view) =>
      logger.debug("getOwners(" + view +")")
      sender ! (mapper.getOwners(view))

    case cc.removeAllPermissions(bankId, accountId) =>
      logger.debug("removeAllPermissions(" + bankId +", "+ accountId +")")
      sender ! (mapper.removeAllPermissions(bankId, accountId))
 
    case cc.removeAllViews(bankId, accountId) =>
      logger.debug("removeAllViews(" + bankId +", "+ accountId +")")
      sender ! (mapper.removeAllViews(bankId, accountId))

    case cc.bulkDeleteAllPermissionsAndViews() =>
      logger.debug("bulkDeleteAllPermissionsAndViews()")
      sender ! (mapper.bulkDeleteAllPermissionsAndViews())

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}

