package code.remotedata

import akka.actor.Actor
import akka.pattern.pipe
import code.actorsystem.ObpActorHelper
import code.util.Helper.MdcLoggable
import code.views.{MapperViews, RemotedataViewsCaseClasses}
import com.openbankproject.commons.model._

import scala.concurrent.ExecutionContext.Implicits.global

class RemotedataViewsActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MapperViews
  val cc = RemotedataViewsCaseClasses

  def receive: PartialFunction[Any, Unit] = {

    case cc.addPermissions(views : List[ViewIdBankIdAccountId], user : User) =>
      logger.debug("addPermissions(" + views +"," + user +")")
      sender ! (mapper.addPermissions(views, user))

    case cc.addPermission(viewIdBankIdAccountId : ViewIdBankIdAccountId, user : User) =>
      logger.debug("addPermission(" + viewIdBankIdAccountId +"," + user +")")
      sender ! (mapper.addPermission(viewIdBankIdAccountId, user))

    case cc.permission(account : BankIdAccountId, user: User) =>
      logger.debug("permission(" + account +"," + user +")")
      sender ! (mapper.permission(account, user))

    case cc.getPermissionForUser(user: User) =>
      logger.debug("permission(" +user +")")
      sender ! (mapper.getPermissionForUser(user))
      
    case cc.revokePermission(viewIdBankIdAccountId : ViewIdBankIdAccountId, user : User) =>
      logger.debug("revokePermission(" + viewIdBankIdAccountId +"," + user +")")
      sender ! (mapper.revokePermission(viewIdBankIdAccountId, user))

    case cc.revokeAllPermissions(bankId : BankId, accountId : AccountId, user : User) =>
      logger.debug("revokeAllPermissions(" + bankId +"," + accountId +","+ user +")")
      sender ! (mapper.revokeAllPermissions(bankId, accountId, user))

    case cc.view(viewId: ViewId, bankAccountId: BankIdAccountId) =>
      logger.debug("view(" + viewId +", "+ bankAccountId + ")")
      sender ! (mapper.view(viewId, bankAccountId))

    case cc.viewFuture(viewId: ViewId, bankAccountId: BankIdAccountId) =>
      logger.debug("vieFuture(" + viewId +", "+ bankAccountId + ")")
      sender ! (mapper.view(viewId, bankAccountId))
      
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

    case cc.updateView(bankAccountId : BankIdAccountId, viewId : ViewId, viewUpdateJson : UpdateViewJSON) =>
      logger.debug("updateView(" + bankAccountId +","+ viewId +","+ viewUpdateJson +")")
      sender ! (mapper.updateView(bankAccountId, viewId, viewUpdateJson))
      
    case cc.updateSystemView(viewId : ViewId, viewUpdateJson : UpdateViewJSON) =>
      logger.debug("updateSystemView(" + viewId +","+ viewUpdateJson +")")
      (mapper.updateSystemView(viewId, viewUpdateJson)) pipeTo sender

    case cc.removeView(viewId : ViewId, bankAccountId: BankIdAccountId) =>
      logger.debug("removeView(" + viewId +","+ bankAccountId +")")
      sender ! (mapper.removeView(viewId, bankAccountId))

    case cc.permissions(bankAccountId : BankIdAccountId) =>
      logger.debug("premissions(" + bankAccountId +")")
      sender ! (mapper.permissions(bankAccountId))

    case cc.viewsForAccount(bankAccountId : BankIdAccountId) =>
      logger.debug("viewsForAccount(" + bankAccountId +")")
      sender ! (mapper.viewsForAccount(bankAccountId))

    case cc.privateViewsUserCanAccess(user: User) =>
      logger.debug("privateViewsUserCanAccess(" + user +")")
      sender ! (mapper.privateViewsUserCanAccess(user: User))

    case cc.privateViewsUserCanAccessForAccount(user: User, bankAccountId : BankIdAccountId)=>
      logger.debug("privateViewsUserCanAccessForAccount(" + user +"bankAccountId"+bankAccountId+")")
      sender ! (mapper.privateViewsUserCanAccessForAccount(user: User, bankAccountId : BankIdAccountId))
      
      
    case cc.publicViews() =>
      logger.debug("publicViews()")
      sender ! (mapper.publicViews)

    case cc.firehoseViewsForBank(bankId: BankId, user : User) =>
      logger.debug(s"firehoseViewsForBank($bankId, $user)")
      sender ! (mapper.firehoseViewsForBank(bankId: BankId, user : User))
      
    case cc.publicViewsForBank(bankId: BankId) =>
      logger.debug("publicViews()")
      sender ! (mapper.publicViewsForBank(bankId: BankId))

    case cc.getOrCreateAccountView(bankAccountUID: BankIdAccountId, viewId: String) =>
      logger.debug("getOrCreateAccountView(" + BankIdAccountId +", "+ viewId +")")
      sender ! (mapper.getOrCreateAccountView(bankAccountUID: BankIdAccountId, viewId: String))
    
    case cc.getOrCreateOwnerView(bankId, accountId, description) =>
      logger.debug("getOrCreateOwnerView(" + bankId +", "+ accountId +", "+ description +")")
      sender ! (mapper.getOrCreateOwnerView(bankId, accountId, description))

    case cc.getOrCreateFirehoseView(bankId, accountId, description) =>
      logger.debug("getOrCreateFirehoseView(" + bankId +", "+ accountId +", "+ description +")")
      sender ! (mapper.getOrCreateFirehoseView(bankId, accountId, description))

    case cc.getOrCreatePublicView(bankId, accountId, description) =>
      logger.debug("getOrCreatePublicView(" + bankId +", "+ accountId +", "+ description +")")
      sender ! (mapper.getOrCreatePublicView(bankId, accountId, description))

    case cc.getOrCreateAccountantsView(bankId, accountId, description) =>
      logger.debug("getOrCreateAccountantsView(" + bankId +", "+ accountId +", "+ description +")")
      sender ! (mapper.getOrCreateAccountantsView(bankId, accountId, description))

    case cc.getOrCreateAuditorsView(bankId, accountId, description) =>
      logger.debug("getOrCreateAuditorsView(" + bankId +", "+ accountId +", "+ description +")")
      sender ! (mapper.getOrCreateAuditorsView(bankId, accountId, description))

    case cc.createRandomView(bankId, accountId) =>
      logger.debug("createRandomView(" + bankId +", "+ accountId +")")
      sender ! (mapper.createRandomView(bankId, accountId))

    case cc.getOwners(view) =>
      logger.debug("getOwners(" + view +")")
      sender ! (mapper.getOwners(view))

    case cc.grantAccessToAllExistingViews(user) =>
      logger.debug("grantAccessToAllExistingViews(" + user +")")
      sender ! (mapper.grantAccessToAllExistingViews(user))

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

