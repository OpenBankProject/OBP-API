package code.remotedata

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.views.{MapperViews, RemotedataViewsCaseClasses}
import code.model._
import code.util.Helper.MdcLoggable
import net.liftweb.common._

class RemotedataViewsActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MapperViews
  val cc = RemotedataViewsCaseClasses

  def receive = {

    case cc.addPermissions(views : List[ViewIdBankIdAccountId], user : User) =>
      logger.debug("addPermissions(" + views +"," + user +")")
      sender ! extractResult(mapper.addPermissions(views, user))

    case cc.addPermission(viewIdBankIdAccountId : ViewIdBankIdAccountId, user : User) =>
      logger.debug("addPermission(" + viewIdBankIdAccountId +"," + user +")")
      sender ! extractResult(mapper.addPermission(viewIdBankIdAccountId, user))

    case cc.permission(account : BankIdAccountId, user: User) =>
      logger.debug("permission(" + account +"," + user +")")
      sender ! extractResult(mapper.permission(account, user))

    case cc.getPermissionForUser(user: User) =>
      logger.debug("permission(" +user +")")
      sender ! extractResult(mapper.getPermissionForUser(user))
      
    case cc.revokePermission(viewIdBankIdAccountId : ViewIdBankIdAccountId, user : User) =>
      logger.debug("revokePermission(" + viewIdBankIdAccountId +"," + user +")")
      sender ! extractResult(mapper.revokePermission(viewIdBankIdAccountId, user))

    case cc.revokeAllPermissions(bankId : BankId, accountId : AccountId, user : User) =>
      logger.debug("revokeAllPermissions(" + bankId +"," + accountId +","+ user +")")
      sender ! extractResult(mapper.revokeAllPermissions(bankId, accountId, user))

    case cc.view(viewId: ViewId, bankAccountId: BankIdAccountId) =>
      logger.debug("view(" + viewId +", "+ bankAccountId + ")")
      sender ! extractResult(mapper.view(viewId, bankAccountId))

    case cc.viewFuture(viewId: ViewId, bankAccountId: BankIdAccountId) =>
      logger.debug("vieFuture(" + viewId +", "+ bankAccountId + ")")
      sender ! (mapper.view(viewId, bankAccountId))

    case cc.createView(bankAccountId : BankIdAccountId, view: CreateViewJson) =>
      logger.debug("createView(" + bankAccountId +","+ view +")")
      sender ! extractResult(mapper.createView(bankAccountId, view))

    case cc.updateView(bankAccountId : BankIdAccountId, viewId : ViewId, viewUpdateJson : UpdateViewJSON) =>
      logger.debug("updateView(" + bankAccountId +","+ viewId +","+ viewUpdateJson +")")
      sender ! extractResult(mapper.updateView(bankAccountId, viewId, viewUpdateJson))

    case cc.removeView(viewId : ViewId, bankAccountId: BankIdAccountId) =>
      logger.debug("removeView(" + viewId +","+ bankAccountId +")")
      sender ! extractResult(mapper.removeView(viewId, bankAccountId))

    case cc.permissions(bankAccountId : BankIdAccountId) =>
      logger.debug("premissions(" + bankAccountId +")")
      sender ! extractResult(mapper.permissions(bankAccountId))

    case cc.viewsForAccount(bankAccountId : BankIdAccountId) =>
      logger.debug("viewsForAccount(" + bankAccountId +")")
      sender ! extractResult(mapper.viewsForAccount(bankAccountId))

    case cc.privateViewsUserCanAccess(user: User) =>
      logger.debug("privateViewsUserCanAccess(" + user +")")
      sender ! extractResult(mapper.privateViewsUserCanAccess(user: User))

    case cc.privateViewsUserCanAccessForAccount(user: User, bankAccountId : BankIdAccountId)=>
      logger.debug("privateViewsUserCanAccessForAccount(" + user +"bankAccountId"+bankAccountId+")")
      sender ! extractResult(mapper.privateViewsUserCanAccessForAccount(user: User, bankAccountId : BankIdAccountId))
      
      
    case cc.publicViews() =>
      logger.debug("publicViews()")
      sender ! extractResult(mapper.publicViews)

    case cc.firehoseViewsForBank(bankId: BankId, user : User) =>
      logger.debug(s"firehoseViewsForBank($bankId, $user)")
      sender ! extractResult(mapper.firehoseViewsForBank(bankId: BankId, user : User))
      
    case cc.publicViewsForBank(bankId: BankId) =>
      logger.debug("publicViews()")
      sender ! extractResult(mapper.publicViewsForBank(bankId: BankId))

    case cc.getOrCreateAccountView(bankAccountUID: BankIdAccountId, viewId: String) =>
      logger.debug("getOrCreateAccountView(" + BankIdAccountId +", "+ viewId +")")
      sender ! extractResult(mapper.getOrCreateAccountView(bankAccountUID: BankIdAccountId, viewId: String))
    
    case cc.getOrCreateOwnerView(bankId, accountId, description) =>
      logger.debug("getOrCreateOwnerView(" + bankId +", "+ accountId +", "+ description +")")
      sender ! extractResult(mapper.getOrCreateOwnerView(bankId, accountId, description))

    case cc.getOrCreateFirehoseView(bankId, accountId, description) =>
      logger.debug("getOrCreateFirehoseView(" + bankId +", "+ accountId +", "+ description +")")
      sender ! extractResult(mapper.getOrCreateFirehoseView(bankId, accountId, description))

    case cc.getOrCreatePublicView(bankId, accountId, description) =>
      logger.debug("getOrCreatePublicView(" + bankId +", "+ accountId +", "+ description +")")
      sender ! extractResult(mapper.getOrCreatePublicView(bankId, accountId, description))

    case cc.getOrCreateAccountantsView(bankId, accountId, description) =>
      logger.debug("getOrCreateAccountantsView(" + bankId +", "+ accountId +", "+ description +")")
      sender ! extractResult(mapper.getOrCreateAccountantsView(bankId, accountId, description))

    case cc.getOrCreateAuditorsView(bankId, accountId, description) =>
      logger.debug("getOrCreateAuditorsView(" + bankId +", "+ accountId +", "+ description +")")
      sender ! extractResult(mapper.getOrCreateAuditorsView(bankId, accountId, description))

    case cc.createRandomView(bankId, accountId) =>
      logger.debug("createRandomView(" + bankId +", "+ accountId +")")
      sender ! extractResult(mapper.createRandomView(bankId, accountId))

    case cc.getOwners(view) =>
      logger.debug("getOwners(" + view +")")
      sender ! extractResult(mapper.getOwners(view))

    case cc.grantAccessToView(user, view) =>
      logger.debug("grantAccessToView(" + user +", "+ view +")")
      sender ! extractResult(mapper.grantAccessToView(user, view))

    case cc.grantAccessToAllExistingViews(user) =>
      logger.debug("grantAccessToAllExistingViews(" + user +")")
      sender ! extractResult(mapper.grantAccessToAllExistingViews(user))

    case cc.removeAllPermissions(bankId, accountId) =>
      logger.debug("removeAllPermissions(" + bankId +", "+ accountId +")")
      sender ! extractResult(mapper.removeAllPermissions(bankId, accountId))
 
    case cc.removeAllViews(bankId, accountId) =>
      logger.debug("removeAllViews(" + bankId +", "+ accountId +")")
      sender ! extractResult(mapper.removeAllViews(bankId, accountId))

    case cc.bulkDeleteAllPermissionsAndViews() =>
      logger.debug("bulkDeleteAllPermissionsAndViews()")
      sender ! extractResult(mapper.bulkDeleteAllPermissionsAndViews())

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}

