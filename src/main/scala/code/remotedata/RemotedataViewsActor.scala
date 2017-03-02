package code.remotedata

import java.util.concurrent.TimeUnit

import akka.actor.Actor
import akka.event.Logging
import akka.util.Timeout
import code.views.{MapperViews, RemoteViewCaseClasses}
import code.model._
import net.liftweb.common._
import net.liftweb.util.ControlHelpers.tryo

import scala.concurrent.duration._


class RemotedataViewsActor extends Actor {

  val logger = Logging(context.system, this)

  val mViews = MapperViews
  val rViews = RemoteViewCaseClasses

  def receive = {

    case rViews.addPermissions(views : List[ViewUID], user : User) =>

      logger.info("addPermissions(" + views +"," + user +")")

      {
        for {
          res <- mViews.addPermissions(views, user)
        } yield {
          sender ! res.asInstanceOf[List[View]]
        }
      }.getOrElse( context.stop(sender) )


    case rViews.addPermission(viewUID : ViewUID, user : User) =>

      logger.info("addPermission(" + viewUID +"," + user +")")

      {
        for {
          res <- mViews.addPermission(viewUID, user)
        } yield {
          sender ! res.asInstanceOf[View]
        }
      }.getOrElse( context.stop(sender) )


    case rViews.permission(account : BankAccountUID, user: User) =>

      logger.info("permission(" + account +"," + user +")")

      {
        for {
          res <- mViews.permission(account, user)
        } yield {
          sender ! res.asInstanceOf[Permission]
        }
      }.getOrElse( context.stop(sender) )


    //TODO Fix return values in order to better describe failures
    case rViews.revokePermission(viewUID : ViewUID, user : User) =>

      logger.info("revokePermission(" + viewUID +"," + user +")")


      val res = mViews.revokePermission(viewUID, user)
      res match {
        case Full(r) => sender ! r
        case f => sender ! f
      }

    case rViews.revokeAllPermissions(bankId : BankId, accountId : AccountId, user : User) =>

      logger.info("revokeAllPermissions(" + bankId +"," + accountId +","+ user +")")

      {
        for {
          res <- mViews.revokeAllPermissions(bankId, accountId, user)
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse( context.stop(sender) )


    case rViews.view(viewUID : ViewUID) =>

      logger.info("view(" + viewUID +")")

      {
        for {
          res <- mViews.view(viewUID)
        } yield {
          sender ! res.asInstanceOf[View]
        }
      }.getOrElse( context.stop(sender) )


    case rViews.view(viewId: ViewId, bankAccountId: BankAccountUID) =>

      logger.info("view(" + viewId +", "+ bankAccountId + ")")

      {
        for {
          res <- mViews.view(viewId, bankAccountId)
        } yield {
          sender ! res.asInstanceOf[View]
        }
      }.getOrElse( context.stop(sender) )

    case rViews.createView(bankAccountId : BankAccountUID, view: CreateViewJSON) =>
      logger.info("createView(" + bankAccountId +","+ view +")")
      sender ! mViews.createView(bankAccountId, view)

    case rViews.updateView(bankAccountId : BankAccountUID, viewId : ViewId, viewUpdateJson : UpdateViewJSON) =>
      logger.info("updateView(" + bankAccountId +","+ viewId +","+ viewUpdateJson +")")
      sender ! mViews.updateView(bankAccountId, viewId, viewUpdateJson)

    //case r.view(viewId: ViewId, bankAccountId: BankAccountUID) =>
    //  logger.info("view(" + viewId +","+ bankAccountId +")")
    //  sender ! v.view(ViewId(viewId.value), bankAccountId)

    case rViews.removeView(viewId : ViewId, bankAccountId: BankAccountUID) =>
      logger.info("removeView(" + viewId +","+ bankAccountId +")")
      sender ! mViews.removeView(viewId, bankAccountId)

    case rViews.permissions(bankAccountId : BankAccountUID) =>
      logger.info("premissions(" + bankAccountId +")")
      sender ! mViews.permissions(bankAccountId)

    case rViews.views(bankAccountId : BankAccountUID) =>
      logger.info("views(" + bankAccountId +")")
      sender ! mViews.views(bankAccountId)

    case rViews.permittedViews(user: User, bankAccountId: BankAccountUID) =>
      logger.info("permittedViews(" + user +", " + bankAccountId +")")
      sender ! mViews.permittedViews(user, bankAccountId)

    case rViews.publicViews(bankAccountId : BankAccountUID) =>
      logger.info("publicViews(" + bankAccountId +")")
      sender ! mViews.publicViews(bankAccountId)

    case rViews.getAllPublicAccounts() =>
      logger.info("getAllPublicAccounts()")
      sender ! mViews.getAllPublicAccounts

    case rViews.getPublicBankAccounts(bank : Bank) =>
      logger.info("getPublicBankAccounts(" + bank +")")
      sender ! mViews.getPublicBankAccounts(bank)

    case rViews.getAllAccountsUserCanSee(user : Box[User]) =>
      logger.info("getAllAccountsUserCanSee(" + user +")")
      sender ! mViews.getAllAccountsUserCanSee(user)

    case rViews.getAllAccountsUserCanSee(user : User) =>
      logger.info("getAllAccountsUserCanSee(" + user +")")
      sender ! mViews.getAllAccountsUserCanSee(Full(user))

    case rViews.getAllAccountsUserCanSee(bank: Bank, user : Box[User]) =>
      logger.info("getAllAccountsUserCanSee(" + bank +", "+ user +")")
      sender ! mViews.getAllAccountsUserCanSee(bank, user)

    case rViews.getAllAccountsUserCanSee(bank: Bank, user : User) =>
      logger.info("getAllAccountsUserCanSee(" + bank +", "+ user +")")
      sender ! mViews.getAllAccountsUserCanSee(bank, Full(user))

    case rViews.getNonPublicBankAccounts(user: User, bankId: BankId) =>
      logger.info("getNonPublicBankAccounts(" + user +", "+ bankId +")")
      sender ! mViews.getNonPublicBankAccounts(user, bankId)

    case rViews.getNonPublicBankAccounts(user: User) =>
      logger.info("getNonPublicBankAccounts(" + user +")")
      sender ! mViews.getNonPublicBankAccounts(user)

    case rViews.createOwnerView(bankId, accountId, description) =>
      logger.info("createOwnerView(" + bankId +", "+ accountId +", "+ description +")")
      sender ! mViews.createOwnerView(bankId, accountId, description).orNull

    case rViews.createPublicView(bankId, accountId, description) =>
      logger.info("createPublicView(" + bankId +", "+ accountId +", "+ description +")")
      sender ! mViews.createPublicView(bankId, accountId, description).orNull

    case rViews.createAccountantsView(bankId, accountId, description) =>
      logger.info("createAccountantsView(" + bankId +", "+ accountId +", "+ description +")")
      sender ! mViews.createAccountantsView(bankId, accountId, description).orNull

    case rViews.createAuditorsView(bankId, accountId, description) =>
      logger.info("createAuditorsView(" + bankId +", "+ accountId +", "+ description +")")
      sender ! mViews.createAuditorsView(bankId, accountId, description).orNull

    case rViews.createRandomView(bankId, accountId) =>
      logger.info("createRandomView(" + bankId +", "+ accountId +")")
      sender ! mViews.createRandomView(bankId, accountId).orNull

    case rViews.getOwners(view) =>
      logger.info("getOwners(" + view +")")
     sender ! mViews.getOwners(view)

    case rViews.grantAccessToView(user, view) =>
      logger.info("grantAccessToView(" + user +", "+ view +")")
      sender ! mViews.grantAccessToView(user, view)

    case rViews.grantAccessToAllExistingViews(user) =>
      logger.info("grantAccessToAllExistingViews(" + user +")")
      sender ! mViews.grantAccessToAllExistingViews(user)

    case rViews.removeAllPermissions(bankId, accountId) =>
      logger.info("removeAllPermissions(" + bankId +", "+ accountId +")")
      sender ! mViews.removeAllPermissions(bankId, accountId)

    case rViews.removeAllViews(bankId, accountId) =>
      logger.info("removeAllViews(" + bankId +", "+ accountId +")")
      sender ! mViews.removeAllViews(bankId, accountId)


    case rViews.bulkDeleteAllPermissionsAndViews() =>

      logger.info("bulkDeleteAllPermissionsAndViews()")

      {
        for {
          res <- tryo{mViews.bulkDeleteAllPermissionsAndViews()}
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse( context.stop(sender) )


    case message => logger.info("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}

