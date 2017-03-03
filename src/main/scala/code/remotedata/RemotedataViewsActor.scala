package code.remotedata

import java.util.concurrent.TimeUnit

import akka.actor.Actor
import akka.event.Logging
import akka.util.Timeout
import code.views.{MapperViews, RemotedataViewsCaseClasses}
import code.model._
import net.liftweb.common._
import net.liftweb.util.ControlHelpers.tryo

import scala.concurrent.duration._


class RemotedataViewsActor extends Actor {

  val logger = Logging(context.system, this)

  val mapper = MapperViews
  val cc = RemotedataViewsCaseClasses

  def receive = {

    case cc.addPermissions(views : List[ViewUID], user : User) =>

      logger.info("addPermissions(" + views +"," + user +")")

      {
        for {
          res <- mapper.addPermissions(views, user)
        } yield {
          sender ! res.asInstanceOf[List[View]]
        }
      }.getOrElse( context.stop(sender) )


    case cc.addPermission(viewUID : ViewUID, user : User) =>

      logger.info("addPermission(" + viewUID +"," + user +")")

      {
        for {
          res <- mapper.addPermission(viewUID, user)
        } yield {
          sender ! res.asInstanceOf[View]
        }
      }.getOrElse( context.stop(sender) )


    case cc.permission(account : BankAccountUID, user: User) =>

      logger.info("permission(" + account +"," + user +")")

      {
        for {
          res <- mapper.permission(account, user)
        } yield {
          sender ! res.asInstanceOf[Permission]
        }
      }.getOrElse( context.stop(sender) )


    //TODO Fix return values in order to better describe failures
    case cc.revokePermission(viewUID : ViewUID, user : User) =>

      logger.info("revokePermission(" + viewUID +"," + user +")")


      val res = mapper.revokePermission(viewUID, user)
      res match {
        case Full(r) => sender ! r
        case f => sender ! f
      }

    case cc.revokeAllPermissions(bankId : BankId, accountId : AccountId, user : User) =>

      logger.info("revokeAllPermissions(" + bankId +"," + accountId +","+ user +")")

      {
        for {
          res <- mapper.revokeAllPermissions(bankId, accountId, user)
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse( context.stop(sender) )


    case cc.view(viewUID : ViewUID) =>

      logger.info("view(" + viewUID +")")

      {
        for {
          res <- mapper.view(viewUID)
        } yield {
          sender ! res.asInstanceOf[View]
        }
      }.getOrElse( context.stop(sender) )


    case cc.view(viewId: ViewId, bankAccountId: BankAccountUID) =>

      logger.info("view(" + viewId +", "+ bankAccountId + ")")

      {
        for {
          res <- mapper.view(viewId, bankAccountId)
        } yield {
          sender ! res.asInstanceOf[View]
        }
      }.getOrElse( context.stop(sender) )

    case cc.createView(bankAccountId : BankAccountUID, view: CreateViewJSON) =>
      logger.info("createView(" + bankAccountId +","+ view +")")
      sender ! mapper.createView(bankAccountId, view)

    case cc.updateView(bankAccountId : BankAccountUID, viewId : ViewId, viewUpdateJson : UpdateViewJSON) =>
      logger.info("updateView(" + bankAccountId +","+ viewId +","+ viewUpdateJson +")")
      sender ! mapper.updateView(bankAccountId, viewId, viewUpdateJson)

    //case r.view(viewId: ViewId, bankAccountId: BankAccountUID) =>
    //  logger.info("view(" + viewId +","+ bankAccountId +")")
    //  sender ! v.view(ViewId(viewId.value), bankAccountId)

    case cc.removeView(viewId : ViewId, bankAccountId: BankAccountUID) =>
      logger.info("removeView(" + viewId +","+ bankAccountId +")")
      sender ! mapper.removeView(viewId, bankAccountId)

    case cc.permissions(bankAccountId : BankAccountUID) =>
      logger.info("premissions(" + bankAccountId +")")
      sender ! mapper.permissions(bankAccountId)

    case cc.views(bankAccountId : BankAccountUID) =>
      logger.info("views(" + bankAccountId +")")
      sender ! mapper.views(bankAccountId)

    case cc.permittedViews(user: User, bankAccountId: BankAccountUID) =>
      logger.info("permittedViews(" + user +", " + bankAccountId +")")
      sender ! mapper.permittedViews(user, bankAccountId)

    case cc.publicViews(bankAccountId : BankAccountUID) =>
      logger.info("publicViews(" + bankAccountId +")")
      sender ! mapper.publicViews(bankAccountId)

    case cc.getAllPublicAccounts() =>
      logger.info("getAllPublicAccounts()")
      sender ! mapper.getAllPublicAccounts

    case cc.getPublicBankAccounts(bank : Bank) =>
      logger.info("getPublicBankAccounts(" + bank +")")
      sender ! mapper.getPublicBankAccounts(bank)

    case cc.getAllAccountsUserCanSee(user : Box[User]) =>
      logger.info("getAllAccountsUserCanSee(" + user +")")
      sender ! mapper.getAllAccountsUserCanSee(user)

    case cc.getAllAccountsUserCanSee(user : User) =>
      logger.info("getAllAccountsUserCanSee(" + user +")")
      sender ! mapper.getAllAccountsUserCanSee(Full(user))

    case cc.getAllAccountsUserCanSee(bank: Bank, user : Box[User]) =>
      logger.info("getAllAccountsUserCanSee(" + bank +", "+ user +")")
      sender ! mapper.getAllAccountsUserCanSee(bank, user)

    case cc.getAllAccountsUserCanSee(bank: Bank, user : User) =>
      logger.info("getAllAccountsUserCanSee(" + bank +", "+ user +")")
      sender ! mapper.getAllAccountsUserCanSee(bank, Full(user))

    case cc.getNonPublicBankAccounts(user: User, bankId: BankId) =>
      logger.info("getNonPublicBankAccounts(" + user +", "+ bankId +")")
      sender ! mapper.getNonPublicBankAccounts(user, bankId)

    case cc.getNonPublicBankAccounts(user: User) =>
      logger.info("getNonPublicBankAccounts(" + user +")")
      sender ! mapper.getNonPublicBankAccounts(user)

    case cc.createOwnerView(bankId, accountId, description) =>
      logger.info("createOwnerView(" + bankId +", "+ accountId +", "+ description +")")
      sender ! mapper.createOwnerView(bankId, accountId, description).orNull

    case cc.createPublicView(bankId, accountId, description) =>
      logger.info("createPublicView(" + bankId +", "+ accountId +", "+ description +")")
      sender ! mapper.createPublicView(bankId, accountId, description).orNull

    case cc.createAccountantsView(bankId, accountId, description) =>
      logger.info("createAccountantsView(" + bankId +", "+ accountId +", "+ description +")")
      sender ! mapper.createAccountantsView(bankId, accountId, description).orNull

    case cc.createAuditorsView(bankId, accountId, description) =>
      logger.info("createAuditorsView(" + bankId +", "+ accountId +", "+ description +")")
      sender ! mapper.createAuditorsView(bankId, accountId, description).orNull

    case cc.createRandomView(bankId, accountId) =>
      logger.info("createRandomView(" + bankId +", "+ accountId +")")
      sender ! mapper.createRandomView(bankId, accountId).orNull

    case cc.getOwners(view) =>
      logger.info("getOwners(" + view +")")
     sender ! mapper.getOwners(view)

    case cc.grantAccessToView(user, view) =>
      logger.info("grantAccessToView(" + user +", "+ view +")")
      sender ! mapper.grantAccessToView(user, view)

    case cc.grantAccessToAllExistingViews(user) =>
      logger.info("grantAccessToAllExistingViews(" + user +")")
      sender ! mapper.grantAccessToAllExistingViews(user)

    case cc.removeAllPermissions(bankId, accountId) =>
      logger.info("removeAllPermissions(" + bankId +", "+ accountId +")")
      sender ! mapper.removeAllPermissions(bankId, accountId)

    case cc.removeAllViews(bankId, accountId) =>
      logger.info("removeAllViews(" + bankId +", "+ accountId +")")
      sender ! mapper.removeAllViews(bankId, accountId)


    case cc.bulkDeleteAllPermissionsAndViews() =>

      logger.info("bulkDeleteAllPermissionsAndViews()")

      {
        for {
          res <- tryo{mapper.bulkDeleteAllPermissionsAndViews()}
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse( context.stop(sender) )


    case message => logger.info("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}

