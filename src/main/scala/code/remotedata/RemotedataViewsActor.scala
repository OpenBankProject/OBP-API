package code.remotedata

import akka.actor.Actor
import akka.event.Logging
import code.views.{MapperViews, RemotedataViewsCaseClasses}
import code.model._
import net.liftweb.common._

import scala.concurrent.duration._


class RemotedataViewsActor extends Actor with ActorHelper {

  val logger = Logging(context.system, this)

  val mapper = MapperViews
  val cc = RemotedataViewsCaseClasses

  def receive = {

    case cc.addPermissions(views : List[ViewUID], user : User) =>
      logger.info("addPermissions(" + views +"," + user +")")
      sender ! extractResult(mapper.addPermissions(views, user))

    case cc.addPermission(viewUID : ViewUID, user : User) =>
      logger.info("addPermission(" + viewUID +"," + user +")")
      sender ! extractResult(mapper.addPermission(viewUID, user))

    case cc.permission(account : BankAccountUID, user: User) =>
      logger.info("permission(" + account +"," + user +")")
      sender ! extractResult(mapper.permission(account, user))

    case cc.revokePermission(viewUID : ViewUID, user : User) =>
      logger.info("revokePermission(" + viewUID +"," + user +")")
      sender ! extractResult(mapper.revokePermission(viewUID, user))

    case cc.revokeAllPermissions(bankId : BankId, accountId : AccountId, user : User) =>
      logger.info("revokeAllPermissions(" + bankId +"," + accountId +","+ user +")")
      sender ! extractResult(mapper.revokeAllPermissions(bankId, accountId, user))

    case cc.view(viewUID : ViewUID) =>
      logger.info("view(" + viewUID +")")
      sender ! extractResult(mapper.view(viewUID))

    case cc.view(viewId: ViewId, bankAccountId: BankAccountUID) =>
      logger.info("view(" + viewId +", "+ bankAccountId + ")")
      sender ! extractResult(mapper.view(viewId, bankAccountId))

    case cc.createView(bankAccountId : BankAccountUID, view: CreateViewJSON) =>
      logger.info("createView(" + bankAccountId +","+ view +")")
      sender ! extractResult(mapper.createView(bankAccountId, view))

    case cc.updateView(bankAccountId : BankAccountUID, viewId : ViewId, viewUpdateJson : UpdateViewJSON) =>
      logger.info("updateView(" + bankAccountId +","+ viewId +","+ viewUpdateJson +")")
      sender ! extractResult(mapper.updateView(bankAccountId, viewId, viewUpdateJson))

    case cc.removeView(viewId : ViewId, bankAccountId: BankAccountUID) =>
      logger.info("removeView(" + viewId +","+ bankAccountId +")")
      sender ! extractResult(mapper.removeView(viewId, bankAccountId))

    case cc.permissions(bankAccountId : BankAccountUID) =>
      logger.info("premissions(" + bankAccountId +")")
      sender ! extractResult(mapper.permissions(bankAccountId))

    case cc.views(bankAccountId : BankAccountUID) =>
      logger.info("views(" + bankAccountId +")")
      sender ! extractResult(mapper.views(bankAccountId))

    case cc.permittedViews(user: User, bankAccountId: BankAccountUID) =>
      logger.info("permittedViews(" + user +", " + bankAccountId +")")
      sender ! extractResult(mapper.permittedViews(user, bankAccountId))

    case cc.publicViews(bankAccountId : BankAccountUID) =>
      logger.info("publicViews(" + bankAccountId +")")
      sender ! extractResult(mapper.publicViews(bankAccountId))

    case cc.getAllPublicAccounts() =>
      logger.info("getAllPublicAccounts()")
      sender ! extractResult(mapper.getAllPublicAccounts)

    case cc.getPublicBankAccounts(bank : Bank) =>
      logger.info("getPublicBankAccounts(" + bank +")")
      sender ! extractResult(mapper.getPublicBankAccounts(bank))

    case cc.getAllAccountsUserCanSee(user : Box[User]) =>
      logger.info("getAllAccountsUserCanSee(" + user +")")
      sender ! extractResult(mapper.getAllAccountsUserCanSee(user))

    case cc.getAllAccountsUserCanSee(user : User) =>
      logger.info("getAllAccountsUserCanSee(" + user +")")
      sender ! extractResult(mapper.getAllAccountsUserCanSee(Full(user)))

    case cc.getAllAccountsUserCanSee(bank: Bank, user : Box[User]) =>
      logger.info("getAllAccountsUserCanSee(" + bank +", "+ user +")")
      sender ! extractResult(mapper.getAllAccountsUserCanSee(bank, user))

    case cc.getAllAccountsUserCanSee(bank: Bank, user : User) =>
      logger.info("getAllAccountsUserCanSee(" + bank +", "+ user +")")
      sender ! extractResult(mapper.getAllAccountsUserCanSee(bank, Full(user)))

    case cc.getNonPublicBankAccounts(user: User, bankId: BankId) =>
      logger.info("getNonPublicBankAccounts(" + user +", "+ bankId +")")
      sender ! extractResult(mapper.getNonPublicBankAccounts(user, bankId))

    case cc.getNonPublicBankAccounts(user: User) =>
      logger.info("getNonPublicBankAccounts(" + user +")")
      sender ! extractResult(mapper.getNonPublicBankAccounts(user))

    case cc.createOwnerView(bankId, accountId, description) =>
      logger.info("createOwnerView(" + bankId +", "+ accountId +", "+ description +")")
      sender ! extractResult(mapper.createOwnerView(bankId, accountId, description))

    case cc.createPublicView(bankId, accountId, description) =>
      logger.info("createPublicView(" + bankId +", "+ accountId +", "+ description +")")
      sender ! extractResult(mapper.createPublicView(bankId, accountId, description))

    case cc.createAccountantsView(bankId, accountId, description) =>
      logger.info("createAccountantsView(" + bankId +", "+ accountId +", "+ description +")")
      sender ! extractResult(mapper.createAccountantsView(bankId, accountId, description))

    case cc.createAuditorsView(bankId, accountId, description) =>
      logger.info("createAuditorsView(" + bankId +", "+ accountId +", "+ description +")")
      sender ! extractResult(mapper.createAuditorsView(bankId, accountId, description))

    case cc.createRandomView(bankId, accountId) =>
      logger.info("createRandomView(" + bankId +", "+ accountId +")")
      sender ! extractResult(mapper.createRandomView(bankId, accountId))

    case cc.getOwners(view) =>
      logger.info("getOwners(" + view +")")
      sender ! extractResult(mapper.getOwners(view))

    case cc.grantAccessToView(user, view) =>
      logger.info("grantAccessToView(" + user +", "+ view +")")
      sender ! extractResult(mapper.grantAccessToView(user, view))

    case cc.grantAccessToAllExistingViews(user) =>
      logger.info("grantAccessToAllExistingViews(" + user +")")
      sender ! extractResult(mapper.grantAccessToAllExistingViews(user))

    case cc.removeAllPermissions(bankId, accountId) =>
      logger.info("removeAllPermissions(" + bankId +", "+ accountId +")")
      sender ! extractResult(mapper.removeAllPermissions(bankId, accountId))
 
    case cc.removeAllViews(bankId, accountId) =>
      logger.info("removeAllViews(" + bankId +", "+ accountId +")")
      sender ! extractResult(mapper.removeAllViews(bankId, accountId))

    case cc.bulkDeleteAllPermissionsAndViews() =>
      logger.info("bulkDeleteAllPermissionsAndViews()")
      sender ! extractResult(mapper.bulkDeleteAllPermissionsAndViews())

    case message => logger.info("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}

