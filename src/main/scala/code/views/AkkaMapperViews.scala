package code.views

import code.model.{CreateViewJSON, Permission, UpdateViewJSON, _}
import net.liftweb.common._

import scala.collection.immutable.List
import code.model._
import com.typesafe.config.ConfigFactory
import net.liftweb.common.Full
import net.liftweb.mapper._
import net.liftweb.util._

import scala.concurrent.Await
import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout
import akka.actor.{Props => ActorProps}
import dispatch.host
import net.liftweb.http.LiftRules

object RemoteDataStandalone extends Loggable {
  implicit val timeout = Timeout(5000 milliseconds)

  def startRemoteWorkerSystem(): Unit = {
    val remote = ActorSystem("OBPDataWorkerSystem", ConfigFactory.load("obpremotedata"))
    val actor = remote.actorOf(ActorProps[AkkaMapperViewsActor], name = "OBPDataActor")
    logger.info("Started OBPDataWorkerSystem")
  }

  def startLocalWorkerSystem(): Unit = {
    val remote = ActorSystem("OBPDataWorkerSystem", ConfigFactory.load("obplocaldata"))
    val actor = remote.actorOf(ActorProps[AkkaMapperViewsActor], name = "OBPDataActor")
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
    setupRemotedataDB()
    startRemoteWorkerSystem()
  }

}
//TODO: Replace BankAccounts with bankPermalink + accountPermalink


private object AkkaMapperViews extends Views with Loggable {

  val remote = ActorSystem("LookupSystem", ConfigFactory.load("remotelookup"))
  val cfg = ConfigFactory.load("obplocaldata")
  val rhost = cfg.getString("remote.natty.tcp.hostname")
  val rport = cfg.getString("remote.natty.tcp.port")
  var remotePath = "akka.tcp://OBPDataWorkerSystem@" + rhost +":"+ rport +"/user/OBPDataActor"
  if (!Props.getBool("enable_akka_remote_data", false))
    remotePath = "akka.tcp://OBPDataWorkerSystem@localhost:2552/user/OBPDataActor"
  val viewsActor = remote.actorSelection(remotePath)
  implicit val timeout = Timeout(5000 milliseconds)
  val r = RemoteViewCases
  val TIMEOUT = 5 seconds


  def permissions(account : BankAccount) : List[Permission] = {
    Await.result(
      (viewsActor ? r.permissions(account)).mapTo[List[Permission]],
      TIMEOUT
    )
  }

  def permission(account: BankAccount, user: User): Box[Permission] = {
    Full(
      Await.result(
        (viewsActor ? r.permission(account, user)).mapTo[Permission],
        TIMEOUT
      )
    )
  }

  def addPermission(viewUID: ViewUID, user: User): Box[View] = {
    Full(
      Await.result(
        (viewsActor ? r.addPermission(viewUID, user)).mapTo[View],
        TIMEOUT
      )
    )
  }

  def addPermissions(views: List[ViewUID], user: User): Box[List[View]] = {
    Full(
      Await.result(
        (viewsActor ? r.addPermissions(views, user)).mapTo[List[View]],
        TIMEOUT
      )
    )
  }

  def revokePermission(viewUID : ViewUID, user : User) : Box[Boolean] = {
    Full(
      Await.result(
        (viewsActor ? r.revokePermission(viewUID, user)).mapTo[Boolean],
        TIMEOUT
      )
    )
  }


  /*
  This removes the link between a User and a View (View Privileges)
   */

  def revokeAllPermission(bankId : BankId, accountId: AccountId, user : User) : Box[Boolean] = {
    Full(
      Await.result(
        (viewsActor ? r.revokeAllPermission(bankId, accountId, user)).mapTo[Boolean],
        TIMEOUT
      )
    )

  }

  def view(viewId : ViewId, account: BankAccount) : Box[View] = {
    Full(
      Await.result(
        (viewsActor ? r.view(viewId, account)).mapTo[View],
        TIMEOUT
      )
    )
  }

  def view(viewUID : ViewUID) : Box[View] = {
    Full(
      Await.result(
        (viewsActor ? r.view(viewUID)).mapTo[View],
        TIMEOUT
      )
    )
  }

  /*
  Create View based on the Specification (name, alias behavior, what fields can be seen, actions are allowed etc. )
  * */
  def createView(bankAccount: BankAccount, view: CreateViewJSON): Box[View] = {
    Full(
      Await.result(
        (viewsActor ? r.createView(bankAccount, view)).mapTo[View],
        TIMEOUT
      )
    )
  }


  /* Update the specification of the view (what data/actions are allowed) */
  def updateView(bankAccount : BankAccount, viewId: ViewId, viewUpdateJson : UpdateViewJSON) : Box[View] = {
    Full(
      Await.result(
        (viewsActor ? r.updateView(bankAccount, viewId, viewUpdateJson)).mapTo[View],
        TIMEOUT
      )
    )
  }

  def removeView(viewId: ViewId, bankAccount: BankAccount): Box[Unit] = {
    Full(
      Await.result(
        (viewsActor ? r.removeView(viewId, bankAccount)).mapTo[Unit],
        TIMEOUT
      )
    )
  }

  def views(bankAccount : BankAccount) : List[View] = {
    Await.result(
      (viewsActor ? r.views(bankAccount)).mapTo[List[View]],
      TIMEOUT
    )
  }

  def permittedViews(user: User, bankAccount: BankAccount): List[View] = {
    Await.result(
      (viewsActor ? r.permittedViews(user, bankAccount)).mapTo[List[View]],
      TIMEOUT
    )
  }

  def publicViews(bankAccount : BankAccount) : List[View] = {
    Await.result(
      (viewsActor ? r.publicViews(bankAccount)).mapTo[List[View]],
      TIMEOUT
    )
  }

  def getAllPublicAccounts() : List[BankAccount] = {
    Await.result(
      (viewsActor ? r.getAllPublicAccounts()).mapTo[List[BankAccount]],
      TIMEOUT
    )
  }

  def getPublicBankAccounts(bank : Bank) : List[BankAccount] = {
    Await.result(
      (viewsActor ? r.getPublicBankAccounts(bank)).mapTo[List[BankAccount]],
      TIMEOUT
    )
  }

  /**
   * @param user
   * @return the bank accounts the @user can see (public + private if @user is Full, public if @user is Empty)
   */
  def getAllAccountsUserCanSee(user : Box[User]) : List[BankAccount] = {
    Await.result(
      (viewsActor ? r.getAllAccountsUserCanSee(user)).mapTo[List[BankAccount]],
      TIMEOUT
    )
  }

  /**
   * @param user
   * @return the bank accounts at @bank the @user can see (public + private if @user is Full, public if @user is Empty)
   */
  def getAllAccountsUserCanSee(bank: Bank, user : Box[User]) : List[BankAccount] = {
    Await.result(
      (viewsActor ? r.getAllAccountsUserCanSee(bank, user)).mapTo[List[BankAccount]],
      TIMEOUT
    )
  }

  /**
   * @return the bank accounts where the user has at least access to a non public view (is_public==false)
   */
  def getNonPublicBankAccounts(user : User) :  List[BankAccount] = {
    Await.result(
      (viewsActor ? r.getNonPublicBankAccounts(user)).mapTo[List[BankAccount]],
      TIMEOUT
    )
  }

  /**
   * @return the bank accounts where the user has at least access to a non public view (is_public==false) for a specific bank
   */
  def getNonPublicBankAccounts(user : User, bankId : BankId) :  List[BankAccount] = {
    Await.result(
      (viewsActor ? r.getNonPublicBankAccounts(user, bankId)).mapTo[List[BankAccount]],
      TIMEOUT
    )
  }

}
