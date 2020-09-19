package code.remotedata

import akka.actor.Actor
import akka.pattern.pipe
import code.actorsystem.ObpActorHelper
import code.util.Helper.MdcLoggable
import code.views.{MapperViews, RemotedataViewsCaseClasses}
import com.openbankproject.commons.model._

import com.openbankproject.commons.ExecutionContext.Implicits.global

class RemotedataViewsActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MapperViews
  val cc = RemotedataViewsCaseClasses

  def receive: PartialFunction[Any, Unit] = {

    case cc.addPermissions(views : List[ViewIdBankIdAccountId], user : User) =>
      logger.debug("addPermissions(" + views +"," + user +")")
      sender ! (mapper.grantAccessToMultipleViews(views, user))
      
    case cc.revokePermissions(views : List[ViewIdBankIdAccountId], user : User) =>
      logger.debug("revokePermissions(" + views +"," + user +")")
      sender ! (mapper.revokeAccessToMultipleViews(views, user))

    case cc.addPermission(viewIdBankIdAccountId : ViewIdBankIdAccountId, user : User) =>
      logger.debug("addPermission(" + viewIdBankIdAccountId +"," + user +")")
      sender ! (mapper.grantAccessToCustomView(viewIdBankIdAccountId, user))
      
    case cc.addSystemViewPermission(bankId: BankId, accountId: AccountId, view : View, user : User) =>
      logger.debug("addSystemViewPermission(" + bankId +"," + accountId +"," + view +"," + user +")")
      sender ! (mapper.grantAccessToSystemView(bankId, accountId, view, user))

    case cc.permission(account : BankIdAccountId, user: User) =>
      logger.debug("permission(" + account +"," + user +")")
      sender ! (mapper.permission(account, user))

    case cc.getPermissionForUser(user: User) =>
      logger.debug("permission(" +user +")")
      sender ! (mapper.getPermissionForUser(user))
      
    case cc.revokePermission(viewIdBankIdAccountId : ViewIdBankIdAccountId, user : User) =>
      logger.debug("revokePermission(" + viewIdBankIdAccountId +"," + user +")")
      sender ! (mapper.revokeAccess(viewIdBankIdAccountId, user)) 
      
    case cc.revokeSystemViewPermission(bankId: BankId, accountId: AccountId, view : View, user : User) =>
      logger.debug("revokeSystemViewPermission(" + bankId +"," + accountId +"," + view +"," + user +")")
      sender ! (mapper.revokeAccessToSystemView(bankId, accountId, view, user))

    case cc.revokeAllAccountAccesses(bankId : BankId, accountId : AccountId, user : User) =>
      logger.debug("revokeAllAccountAccesses(" + bankId +"," + accountId +","+ user +")")
      sender ! (mapper.revokeAllAccountAccesses(bankId, accountId, user))
      
    case cc.revokeAccountAccessesByUser(bankId : BankId, accountId : AccountId, user : User) =>
      logger.debug("revokeAccountAccessesByUser(" + bankId +"," + accountId +","+ user +")")
      sender ! (mapper.revokeAccountAccessesByUser(bankId, accountId, user))

    case cc.customView(viewId: ViewId, bankAccountId: BankIdAccountId) =>
      logger.debug("customView(" + viewId +", "+ bankAccountId + ")")
      sender ! (mapper.customView(viewId, bankAccountId))
      
    case cc.systemView(viewId: ViewId) =>
      logger.debug("view(" + viewId  + ")")
      sender ! (mapper.systemView(viewId))

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
      sender ! (mapper.createView(bankAccountId, view))
      
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

    case cc.getOrCreateAccountView(bankAccountUID: BankIdAccountId, viewId: String) =>
      logger.debug("getOrCreateAccountView(" + BankIdAccountId +", "+ viewId +")")
      sender ! (mapper.getOrCreateAccountView(bankAccountUID: BankIdAccountId, viewId: String))
    
    case cc.getOrCreateOwnerView(bankId, accountId, description) =>
      logger.debug("getOrCreateOwnerView(" + bankId +", "+ accountId +", "+ description +")")
      sender ! (mapper.getOrCreateOwnerView(bankId, accountId, description))  
      
    case cc.getOrCreateSystemView(name) =>
      logger.debug("getOrCreateSystemOwnerView(" + name +")")
      sender ! (mapper.getOrCreateSystemView(name))

    case cc.getOrCreateFirehoseView(bankId, accountId, description) =>
      logger.debug("getOrCreateFirehoseView(" + bankId +", "+ accountId +", "+ description +")")
      sender ! (mapper.getOrCreateFirehoseView(bankId, accountId, description))

    case cc.getOrCreatePublicView(bankId, accountId, description) =>
      logger.debug("getOrCreatePublicView(" + bankId +", "+ accountId +", "+ description +")")
      sender ! (mapper.getOrCreateCustomPublicView(bankId, accountId, description))

    case cc.getOrCreateAccountantsView(bankId, accountId, description) =>
      logger.debug("getOrCreateAccountantsView(" + bankId +", "+ accountId +", "+ description +")")
      sender ! (mapper.getOrCreateAccountantsView(bankId, accountId, description))

    case cc.getOrCreateAuditorsView(bankId, accountId, description) =>
      logger.debug("getOrCreateAuditorsView(" + bankId +", "+ accountId +", "+ description +")")
      sender ! (mapper.getOrCreateAuditorsView(bankId, accountId, description))

    case cc.createRandomView(bankId, accountId) =>
      logger.debug("createRandomView(" + bankId +", "+ accountId +")")
      sender ! (mapper.createCustomRandomView(bankId, accountId))

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

