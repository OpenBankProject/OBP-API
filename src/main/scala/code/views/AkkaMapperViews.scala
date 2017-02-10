package code.views

import code.model.{CreateViewJSON, Permission, UpdateViewJSON, _}
import net.liftweb.common._

import scala.collection.immutable.List
import code.model._
import com.typesafe.config.ConfigFactory
import net.liftweb.common.Full
import net.liftweb.util.Props

import scala.concurrent.Await
import scala.concurrent.duration._
import akka.pattern.ask
import akka.actor.{ActorKilledException, ActorSelection, ActorSystem}
import akka.util.Timeout
import code.api.APIFailure
import code.users.{RemoteUserCaseClasses, Users}


object AkkaMapperViews extends Views with Users  {

  val TIMEOUT = 10 seconds
  val r = RemoteViewCaseClasses
  val ru = RemoteUserCaseClasses
  implicit val timeout = Timeout(10000 milliseconds)

  val remote = ActorSystem("LookupSystem", ConfigFactory.load("remotelookup"))
  var actorPath = "akka.tcp://OBPDataWorkerSystem@127.0.0.1:5050/user/OBPLocalDataActor"
  if (Props.getBool("enable_remotedata", false)) {
    val cfg = ConfigFactory.load("obpremotedata")
    val rhost = cfg.getString("akka.remote.netty.tcp.hostname")
    val rport = cfg.getString("akka.remote.netty.tcp.port")
    actorPath = "akka.tcp://OBPDataWorkerSystem@" + rhost + ":" + rport + "/user/OBPRemoteDataActor"
  }

  var viewsActor: ActorSelection = remote.actorSelection(actorPath)

  def addPermissions(views: List[ViewUID], user: User): Box[List[View]] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? r.addPermissions(views, user)).mapTo[List[View]],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"One or more views not found", 404)
      case e: Throwable => throw e
    }
    res
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
    val res = try {
      Full(
        Await.result(
          (viewsActor ? r.addPermission(viewUID, user)).mapTo[View],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"View $viewUID. not found", 404)
      case e: Throwable => throw e
    }
    res

  }

  def revokePermission(viewUID : ViewUID, user : User) : Box[Boolean] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? r.revokePermission(viewUID, user)).mapTo[Boolean],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException => return Empty ~> APIFailure(s"View $viewUID. not found", 404)
      case e: Throwable => throw e
    }

    if ( res.getOrElse(false) ) {
      res
    }
    else
      Empty ~> Failure("access cannot be revoked")
  }

  def revokeAllPermissions(bankId : BankId, accountId: AccountId, user : User) : Box[Boolean] = {
    val res = try{
      Full(
        Await.result(
          (viewsActor ? r.revokeAllPermissions(bankId, accountId, user)).mapTo[Boolean],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException => Empty ~> Failure("One of the views this user has access to is the owner view, and there would be no one with access" +
          " to this owner view if access to the user was revoked. No permissions to any views on the account have been revoked.")

      case e: Throwable => throw e
    }
    res
  }

  def view(viewUID : ViewUID) : Box[View] = {
    val res = try {
      Full(
      Await.result(
        (viewsActor ? r.view(viewUID)).mapTo[View],
        TIMEOUT
      )
    )
  }
  catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"View $viewUID. not found", 404)
      case e: Throwable => throw e
    }
    res
  }

  def view(viewId : ViewId, account: BankAccount) : Box[View] = {
    Await.result(
      (viewsActor ? r.view(viewId, account)).mapTo[Box[View]],
      TIMEOUT
    )
  }

  def createView(bankAccount: BankAccount, view: CreateViewJSON): Box[View] = {
    Await.result(
      (viewsActor ? r.createView(bankAccount, view)).mapTo[Box[View]],
      TIMEOUT
    )
  }

  def updateView(bankAccount : BankAccount, viewId: ViewId, viewUpdateJson : UpdateViewJSON) : Box[View] = {
    Await.result(
      (viewsActor ? r.updateView(bankAccount, viewId, viewUpdateJson)).mapTo[Box[View]],
      TIMEOUT
    )
  }

  def removeView(viewId: ViewId, bankAccount: BankAccount): Box[Unit] = {
    Await.result(
      (viewsActor ? r.removeView(viewId, bankAccount)).mapTo[Box[Unit]],
      TIMEOUT
    )
  }

  def permissions(account : BankAccount) : List[Permission] = {
    Await.result(
      (viewsActor ? r.permissions(account)).mapTo[List[Permission]],
      TIMEOUT
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

  def getAllAccountsUserCanSee(user : Box[User]) : List[BankAccount] = {
    user match {
      case Full(theUser) => {
        Await.result (
          (viewsActor ? r.getAllAccountsUserCanSee(theUser)).mapTo[List[BankAccount]],
          TIMEOUT)
      }
      case _ => getAllPublicAccounts()
    }
  }

  def getAllAccountsUserCanSee(bank: Bank, user : Box[User]) : List[BankAccount] = {
    user match {
      case Full(theUser) => {
        Await.result(
          (viewsActor ? r.getAllAccountsUserCanSee(bank, theUser)).mapTo[List[BankAccount]],
          TIMEOUT
        )
      }
      case _ => getPublicBankAccounts(bank)
    }
  }

  def getNonPublicBankAccounts(user : User) :  List[BankAccount] = {
    Await.result(
      (viewsActor ? r.getNonPublicBankAccounts(user)).mapTo[List[BankAccount]],
      TIMEOUT
    )
  }

  def getNonPublicBankAccounts(user : User, bankId : BankId) :  List[BankAccount] = {
    Await.result(
      (viewsActor ? r.getNonPublicBankAccounts(user, bankId)).mapTo[List[BankAccount]],
      TIMEOUT
    )
  }

  def grantAccessToAllExistingViews(user : User) = {
    Await.result(
      (viewsActor ? r.grantAccessToAllExistingViews(user)).mapTo[Boolean],
      TIMEOUT
    )
  }

  def grantAccessToView(user : User, view : View) = {
    Await.result(
      (viewsActor ? r.grantAccessToView(user, view)).mapTo[Boolean],
      TIMEOUT
    )
  }

  def createOwnerView(bankId: BankId, accountId: AccountId, description: String) : Box[View] = {
    Full(Await.result(
      (viewsActor ? r.createOwnerView(bankId, accountId, description)).mapTo[View],
      TIMEOUT
      )
    )
  }

  def createPublicView(bankId: BankId, accountId: AccountId, description: String) : Box[View] = {
    Full(Await.result(
      (viewsActor ? r.createPublicView(bankId, accountId, description)).mapTo[View],
      TIMEOUT
      )
    )
  }

  def createAccountantsView(bankId: BankId, accountId: AccountId, description: String) : Box[View] = {
    Full(Await.result(
      (viewsActor ? r.createAccountantsView(bankId, accountId, description)).mapTo[View],
      TIMEOUT
      )
    )
  }

  def createAuditorsView(bankId: BankId, accountId: AccountId, description: String) : Box[View] = {
    Full(Await.result(
      (viewsActor ? r.createAuditorsView(bankId, accountId, description)).mapTo[View],
      TIMEOUT
      )
    )
  }

  def createRandomView(bankId: BankId, accountId: AccountId) : Box[View] = {
    Full(Await.result(
      (viewsActor ? r.createRandomView(bankId, accountId)).mapTo[View],
      TIMEOUT
      )
    )
  }

  def viewExists(bankId: BankId, accountId: AccountId, name: String): Boolean = {
    Await.result(
      (viewsActor ? r.viewExists(bankId, accountId, name)).mapTo[Boolean],
      TIMEOUT
    )
  }

  def removeAllViews(bankId: BankId, accountId: AccountId): Boolean = {
    Await.result(
      (viewsActor ? r.removeAllViews(bankId, accountId)).mapTo[Boolean],
      TIMEOUT
    )
  }

  def removeAllPermissions(bankId: BankId, accountId: AccountId): Boolean = {
    Await.result(
      (viewsActor ? r.removeAllViews(bankId, accountId)).mapTo[Boolean],
      TIMEOUT
    )
  }
  // Resource user part
  def getUserByApiId(id : Long) : Box[User] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? ru.getUserByApiId(id)).mapTo[User],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"User not found", 404)
      case e: Throwable => throw e
    }
    res
  }

  def getUserByProviderId(provider : String, idGivenByProvider : String) : Box[User] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? ru.getUserByProviderId(provider, idGivenByProvider)).mapTo[User],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"User not found", 404)
      case e: Throwable => throw e
    }
    res
  }

  def getUserByUserId(userId : String) : Box[User] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? ru.getUserByUserId(userId)).mapTo[User],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"User not found", 404)
      case e: Throwable => throw e
    }
    res
  }
}

