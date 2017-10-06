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

    // TODO Remove duplicate bankId accountId inputs here.
    case cc.getOrCreateViewPrivilege(view: View, user: User) =>
      logger.debug("getOrCreateViewPrivilege(" + view +"," + user +")")
      sender ! extractResult(mapper.getOrCreateViewPrivilege(view: View, user: User))
  
    case cc.permission(account : BankIdAccountId, user: User) =>
      logger.debug("permission(" + account +"," + user +")")
      sender ! extractResult(mapper.permission(account, user))

    case cc.revokePermission(viewIdBankIdAccountId : ViewIdBankIdAccountId, user : User) =>
      logger.debug("revokePermission(" + viewIdBankIdAccountId +"," + user +")")
      sender ! extractResult(mapper.revokePermission(viewIdBankIdAccountId, user))

    case cc.revokeAllPermissions(bankId : BankId, accountId : AccountId, user : User) =>
      logger.debug("revokeAllPermissions(" + bankId +"," + accountId +","+ user +")")
      sender ! extractResult(mapper.revokeAllPermissions(bankId, accountId, user))

    case cc.view(viewIdBankIdAccountId : ViewIdBankIdAccountId) =>
      logger.debug("view(" + viewIdBankIdAccountId +")")
      sender ! extractResult(mapper.view(viewIdBankIdAccountId))

    case cc.view(viewId: ViewId, bankAccountId: BankIdAccountId) =>
      logger.debug("view(" + viewId +", "+ bankAccountId + ")")
      sender ! extractResult(mapper.view(viewId, bankAccountId))

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

    case cc.views(bankAccountId : BankIdAccountId) =>
      logger.debug("views(" + bankAccountId +")")
      sender ! extractResult(mapper.views(bankAccountId))

    case cc.permittedViews(user: User, bankAccountId: BankIdAccountId) =>
      logger.debug("permittedViews(" + user +", " + bankAccountId +")")
      sender ! extractResult(mapper.permittedViews(user, bankAccountId))

    case cc.publicViews(bankAccountId : BankIdAccountId) =>
      logger.debug("publicViews(" + bankAccountId +")")
      sender ! extractResult(mapper.publicViews(bankAccountId))

    case cc.getAllPublicAccounts() =>
      logger.debug("getAllPublicAccounts()")
      sender ! extractResult(mapper.getAllPublicAccounts)

    case cc.getPublicBankAccounts(bank : Bank) =>
      logger.debug("getPublicBankAccounts(" + bank +")")
      sender ! extractResult(mapper.getPublicBankAccounts(bank))

    case cc.getAllAccountsUserCanSee(user : Box[User]) =>
      logger.debug("getAllAccountsUserCanSee(" + user +")")
      sender ! extractResult(mapper.getAllAccountsUserCanSee(user))

    case cc.getAllAccountsUserCanSee(user : User) =>
      logger.debug("getAllAccountsUserCanSee(" + user +")")
      sender ! extractResult(mapper.getAllAccountsUserCanSee(Full(user)))

    case cc.getAllAccountsUserCanSee(bank: Bank, user : Box[User]) =>
      logger.debug("getAllAccountsUserCanSee(" + bank +", "+ user +")")
      sender ! extractResult(mapper.getAllAccountsUserCanSee(bank, user))

    case cc.getAllAccountsUserCanSee(bank: Bank, user : User) =>
      logger.debug("getAllAccountsUserCanSee(" + bank +", "+ user +")")
      sender ! extractResult(mapper.getAllAccountsUserCanSee(bank, Full(user)))

    case cc.getNonPublicBankAccounts(user: User, bankId: BankId) =>
      logger.debug("getNonPublicBankAccounts(" + user +", "+ bankId +")")
      sender ! extractResult(mapper.getNonPublicBankAccounts(user, bankId))

    case cc.getNonPublicBankAccounts(user: User) =>
      logger.debug("getNonPublicBankAccounts(" + user +")")
      sender ! extractResult(mapper.getNonPublicBankAccounts(user))

    case cc.getOrCreateAccountView(bankAccountUID: BankIdAccountId, viewId: String) =>
      logger.debug("getOrCreateAccountView(" + BankIdAccountId +", "+ viewId +")")
      sender ! extractResult(mapper.getOrCreateAccountView(bankAccountUID: BankIdAccountId, viewId: String))
    
    case cc.getOrCreateOwnerView(bankId, accountId, description) =>
      logger.debug("getOrCreateOwnerView(" + bankId +", "+ accountId +", "+ description +")")
      sender ! extractResult(mapper.getOrCreateOwnerView(bankId, accountId, description))

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

