package code.views

import code.model._
import com.typesafe.config.ConfigFactory
import net.liftweb.common.{Box, Empty, Full, Loggable}
import net.liftweb.db.StandardDBVendor
import net.liftweb.http.LiftRules
import net.liftweb.mapper.{DB, Schemifier}
import net.liftweb.util.Props
import akka.actor.{Actor, ActorSystem, Props => ActorProps}

import scala.concurrent.duration._
import akka.event.Logging
import akka.util.Timeout
import bootstrap.liftweb.ToSchemify
import code.users.{LiftUsers, RemoteUserCaseClasses}


class AkkaMapperViewsActor extends Actor {

  val logger = Logging(context.system, this)

  Schemifier.schemify(true, Schemifier.infoF _, ToSchemify.modelsRemotedata: _*)

  val v = MapperViews
  val r = RemoteViewCaseClasses

  val vu = LiftUsers
  val ru = RemoteUserCaseClasses

  def receive = {

    case r.addPermissions(views : List[ViewUID], user : User) =>

      logger.info("addPermissions(" + views +"," + user +")")

      {
        for {
          res <- v.addPermissions(views, user)
        } yield {
          sender ! res.asInstanceOf[List[View]]
        }
      }.getOrElse( context.stop(sender) )


    case r.addPermission(viewUID : ViewUID, user : User) =>

      logger.info("addPermission(" + viewUID +"," + user +")")

      {
        for {
          res <- v.addPermission(viewUID, user)
        } yield {
          sender ! res.asInstanceOf[View]
        }
      }.getOrElse( context.stop(sender) )


    case r.permission(account : BankAccount, user: User) =>

      logger.info("permission(" + account +"," + user +")")

      {
        for {
          res <- v.permission(account, user)
        } yield {
          sender ! res.asInstanceOf[Permission]
        }
      }.getOrElse( context.stop(sender) )


    case r.revokePermission(viewUID : ViewUID, user : User) =>

      logger.info("revokePermission(" + viewUID +"," + user +")")

      {
        for {
          res <- v.revokePermission(viewUID, user)
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse(context.stop(sender))


    case r.revokeAllPermissions(bankId : BankId, accountId : AccountId, user : User) =>

      logger.info("revokeAllPermissions(" + bankId +"," + accountId +","+ user +")")

      {
        for {
          res <- v.revokeAllPermissions(bankId, accountId, user)
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse( context.stop(sender) )


    case r.view(viewUID : ViewUID) =>

      logger.info("view(" + viewUID +")")

      {
        for {
          res <- v.view(viewUID)
        } yield {
          sender ! res.asInstanceOf[View]
        }
      }.getOrElse( context.stop(sender) )


    case r.createView(bankAccount : BankAccount, view: CreateViewJSON) =>
      logger.info("createView(" + bankAccount +","+ view +")")
      sender ! v.createView(bankAccount, view)

    case r.updateView(bankAccount : BankAccount, viewId : ViewId, viewUpdateJson : UpdateViewJSON) =>
      logger.info("updateView(" + bankAccount +","+ viewId +","+ viewUpdateJson +")")
      sender ! v.updateView(bankAccount, viewId, viewUpdateJson)

    case r.view(viewId: ViewId, bankAccount: BankAccount) =>
      logger.info("view(" + viewId +","+ bankAccount +")")
      sender ! v.view(ViewId(viewId.value), bankAccount)

    case r.removeView(viewId : ViewId, bankAccount: BankAccount) =>
      logger.info("removeView(" + viewId +","+ bankAccount +")")
      sender ! v.removeView(viewId, bankAccount)

    case r.permissions(bankAccount : BankAccount) =>
      logger.info("premissions(" + bankAccount +")")
      sender ! v.permissions(bankAccount)

    case r.views(bankAccount : BankAccount) =>
      logger.info("views(" + bankAccount +")")
      sender ! v.views(bankAccount)

    case r.permittedViews(user: User, bankAccount: BankAccount) =>
      logger.info("permittedViews(" + user +", " + bankAccount +")")
      sender ! v.permittedViews(user, bankAccount)

    case r.publicViews(bankAccount : BankAccount) =>
      logger.info("publicViews(" + bankAccount +")")
      sender ! v.publicViews(bankAccount)

    case r.getAllPublicAccounts() =>
      logger.info("getAllPublicAccounts()")
      sender ! v.getAllPublicAccounts

    case r.getPublicBankAccounts(bank : Bank) =>
      logger.info("getPublicBankAccounts(" + bank +")")
      sender ! v.getPublicBankAccounts(bank)

    case r.getAllAccountsUserCanSee(user : Box[User]) =>
      logger.info("getAllAccountsUserCanSee(" + user +")")
      sender ! v.getAllAccountsUserCanSee(user)

    case r.getAllAccountsUserCanSee(user : User) =>
      logger.info("getAllAccountsUserCanSee(" + user +")")
      sender ! v.getAllAccountsUserCanSee(Full(user))

    case r.getAllAccountsUserCanSee(bank: Bank, user : Box[User]) =>
      logger.info("getAllAccountsUserCanSee(" + bank +", "+ user +")")
      sender ! v.getAllAccountsUserCanSee(bank, user)

    case r.getAllAccountsUserCanSee(bank: Bank, user : User) =>
      logger.info("getAllAccountsUserCanSee(" + bank +", "+ user +")")
      sender ! v.getAllAccountsUserCanSee(bank, Full(user))

    case r.getNonPublicBankAccounts(user: User, bankId: BankId) =>
      logger.info("getNonPublicBankAccounts(" + user +", "+ bankId +")")
      sender ! v.getNonPublicBankAccounts(user, bankId)

    case r.getNonPublicBankAccounts(user: User) =>
      logger.info("getNonPublicBankAccounts(" + user +")")
      sender ! v.getNonPublicBankAccounts(user)

    case r.createOwnerView(bankId, accountId, description) =>
      logger.info("createOwnerView(" + bankId +", "+ accountId +", "+ description +")")
      sender ! v.createOwnerView(bankId, accountId, description).orNull

    case r.createPublicView(bankId, accountId, description) =>
      logger.info("createPublicView(" + bankId +", "+ accountId +", "+ description +")")
      sender ! v.createPublicView(bankId, accountId, description).orNull

    case r.createAccountantsView(bankId, accountId, description) =>
      logger.info("createAccountantsView(" + bankId +", "+ accountId +", "+ description +")")
      sender ! v.createAccountantsView(bankId, accountId, description).orNull

    case r.createAuditorsView(bankId, accountId, description) =>
      logger.info("createAuditorsView(" + bankId +", "+ accountId +", "+ description +")")
      sender ! v.createAuditorsView(bankId, accountId, description).orNull

    case r.createRandomView(bankId, accountId) =>
      logger.info("createRandomView(" + bankId +", "+ accountId +")")
      sender ! v.createRandomView(bankId, accountId).orNull

    case r.grantAccessToView(user, view) =>
      logger.info("grantAccessToView(" + user +", "+ view +")")
      sender ! v.grantAccessToView(user, view)

    case r.grantAccessToAllExistingViews(user) =>
      logger.info("grantAccessToAllExistingViews(" + user +")")
      sender ! v.grantAccessToAllExistingViews(user)

    case r.removeAllPermissions(bankId, accountId) =>
      logger.info("removeAllPermissions(" + bankId +", "+ accountId +")")
      sender ! v.removeAllPermissions(bankId, accountId)

    case r.removeAllViews(bankId, accountId) =>
      logger.info("removeAllViews(" + bankId +", "+ accountId +")")
      sender ! v.removeAllViews(bankId, accountId)

    // Resource User part
    case ru.getUserByApiId(id: Long) =>
      logger.info("getUserByApiId(" + id +")")

      {
        for {
          res <- vu.getUserByApiId(id)
        } yield {
          sender ! res.asInstanceOf[User]
        }
      }.getOrElse( context.stop(sender) )

    case ru.getUserByProviderId(provider : String, idGivenByProvider : String) =>
      logger.info("getUserByProviderId(" + provider +"," + idGivenByProvider +")")

      {
        for {
          res <- vu.getUserByProviderId(provider, idGivenByProvider)
        } yield {
          sender ! res.asInstanceOf[User]
        }
      }.getOrElse( context.stop(sender) )

    case ru.getUserByUserId(userId: String) =>
      logger.info("getUserByUserId(" + userId +")")

      {
        for {
          res <- vu.getUserByUserId(userId)
        } yield {
          sender ! res.asInstanceOf[User]
        }
      }.getOrElse( context.stop(sender) )

    case message => logger.info("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)
  }

}

object RemoteDataActorSystem extends Loggable {
  implicit val timeout = Timeout(1 seconds)

  def startRemoteWorkerSystem(): Unit = {
    val remote = ActorSystem("OBPDataWorkerSystem", ConfigFactory.load("obpremotedata"))
    val actor = remote.actorOf(ActorProps[AkkaMapperViewsActor], name = "OBPRemoteDataActor")
    logger.info("Started OBPDataWorkerSystem")
  }

  def startLocalWorkerSystem(): Unit = {
    val remote = ActorSystem("OBPDataWorkerSystem", ConfigFactory.load("obplocaldata"))
    val actor = remote.actorOf(ActorProps[AkkaMapperViewsActor], name = "OBPLocalDataActor")
    logger.info("Started OBPDataWorkerSystem locally")
  }

  def setupRemotedataDB(): Unit = {
    // set up the way to connect to the relational DB we're using (ok if other connector than relational)
    if (!DB.jndiJdbcConnAvailable_?) {
      val driver =
        Props.mode match {
          case Props.RunModes.Production | Props.RunModes.Staging | Props.RunModes.Development => Props.get("remotedata.db.driver") openOr "org.h2.Driver"
          case _ => "org.h2.Driver"
        }
      val vendor =
        Props.mode match {
          case Props.RunModes.Production | Props.RunModes.Staging | Props.RunModes.Development =>
            new StandardDBVendor(driver,
              Props.get("remotedata.db.url") openOr "jdbc:h2:./lift_proto.remotedata.db;AUTO_SERVER=TRUE",
              Props.get("remotedata.db.user"), Props.get("remotedata.db.password"))
          case _ =>
            new StandardDBVendor(
              driver,
              "jdbc:h2:mem:OBPData;DB_CLOSE_DELAY=-1",
              Empty, Empty)
        }

      logger.debug("Using database driver: " + driver)
      LiftRules.unloadHooks.append(vendor.closeAllConnections_! _)

      DB.defineConnectionManager(net.liftweb.util.DefaultConnectionIdentifier, vendor)
    }
  }

  // Entry point if running as standalone remote data server, without jetty
  def main (args: Array[String]): Unit = {
    if (args.length > 1 && args(0) == "standalone") {
      println("------------------------------------------------------------")
      println("-----                                                  -----")
      println("-----     STANDALONE REMOTEDATA AKKA ACTOR STARTED     -----")
      println("-----                                                  -----")
      println("------------------------------------------------------------")

      setupRemotedataDB()
      startRemoteWorkerSystem()
    }
  }

}
