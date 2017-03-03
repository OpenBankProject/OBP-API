package code.remotedata


import akka.actor.ActorKilledException
import akka.pattern.ask
import akka.util.Timeout
import code.api.APIFailure
import code.model.{CreateViewJSON, Permission, UpdateViewJSON, _}
import code.views.{RemotedataViewsCaseClasses, Views}
import net.liftweb.common.{Full, _}

import scala.collection.immutable.List
import scala.concurrent.Await
import scala.concurrent.duration._


object RemotedataViews extends ActorInit with Views {

  val cc = RemotedataViewsCaseClasses

  def addPermissions(views: List[ViewUID], user: User): Box[List[View]] = {
    val res = try {
      Full(
        Await.result(
          (actor ? cc.addPermissions(views, user)).mapTo[List[View]],
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

  def permission(account: BankAccountUID, user: User): Box[Permission] = {
    Full(
      Await.result(
        (actor ? cc.permission(account, user)).mapTo[Permission],
        TIMEOUT
      )
    )
  }

  def addPermission(viewUID: ViewUID, user: User): Box[View] = {
    val res = try {
      Full(
        Await.result(
          (actor ? cc.addPermission(viewUID, user)).mapTo[View],
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

  //TODO Fix return values in order to better describe failures
  def revokePermission(viewUID : ViewUID, user : User) : Box[Boolean] = {
    val res = try {
      Full(
        Await.result(
          (actor ? cc.revokePermission(viewUID, user)).mapTo[Boolean],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ClassCastException => k.getMessage match {
                                      case "Cannot cast net.liftweb.common.Failure to java.lang.Boolean" =>
                                        return Empty ~> APIFailure(s"View $viewUID. not removed", 400)
                                      case "Cannot cast net.liftweb.common.ParamFailure to java.lang.Boolean" =>
                                        return Empty ~> APIFailure(s"View $viewUID. not found", 404)
                                      case _ =>
					return Empty ~> APIFailure(s"Unknown error", 406)
                                    }
      case e: Throwable => throw e
    }
    res
  }

  def revokeAllPermissions(bankId : BankId, accountId: AccountId, user : User) : Box[Boolean] = {
    val res = try{
      Full(
        Await.result(
          (actor ? cc.revokeAllPermissions(bankId, accountId, user)).mapTo[Boolean],
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
        (actor ? cc.view(viewUID)).mapTo[View],
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

  def view(viewId : ViewId, account: BankAccountUID) : Box[View] = {
    val res = try {
      Full(
      Await.result(
        (actor ? cc.view(viewId, account)).mapTo[View],
        TIMEOUT
      )
    )
  }
  catch {
      case k: ActorKilledException => Empty ~> APIFailure(s"View $viewId. not found", 404)
      case e: Throwable => throw e
    }
    res
  }


  def createView(bankAccountId: BankAccountUID, view: CreateViewJSON): Box[View] = {
    Await.result(
      (actor ? cc.createView(bankAccountId, view)).mapTo[Box[View]],
      TIMEOUT
    )
  }

  def updateView(bankAccountId : BankAccountUID, viewId: ViewId, viewUpdateJson : UpdateViewJSON) : Box[View] = {
    Await.result(
      (actor ? cc.updateView(bankAccountId, viewId, viewUpdateJson)).mapTo[Box[View]],
      TIMEOUT
    )
  }

  def removeView(viewId: ViewId, bankAccountId: BankAccountUID): Box[Unit] = {
    Await.result(
      (actor ? cc.removeView(viewId, bankAccountId)).mapTo[Box[Unit]],
      TIMEOUT
    )
  }

  def permissions(account : BankAccountUID) : List[Permission] = {
    Await.result(
      (actor ? cc.permissions(account)).mapTo[List[Permission]],
      TIMEOUT
    )
  }

  def views(bankAccountId : BankAccountUID) : List[View] = {
    Await.result(
      (actor ? cc.views(bankAccountId)).mapTo[List[View]],
      TIMEOUT
    )
  }

  def permittedViews(user: User, bankAccountId: BankAccountUID): List[View] = {
    Await.result(
      (actor ? cc.permittedViews(user, bankAccountId)).mapTo[List[View]],
      TIMEOUT
    )
  }

  def publicViews(bankAccountId : BankAccountUID) : List[View] = {
    Await.result(
      (actor ? cc.publicViews(bankAccountId)).mapTo[List[View]],
      TIMEOUT
    )
  }

  def getAllPublicAccounts() : List[BankAccountUID] = {
    Await.result(
      (actor ? cc.getAllPublicAccounts()).mapTo[List[BankAccountUID]],
      TIMEOUT
    )
  }

  def getPublicBankAccounts(bank : Bank) : List[BankAccountUID] = {
    Await.result(
      (actor ? cc.getPublicBankAccounts(bank)).mapTo[List[BankAccountUID]],
      TIMEOUT
    )
  }

  def getAllAccountsUserCanSee(user : Box[User]) : List[BankAccountUID] = {
    user match {
      case Full(theUser) => {
        Await.result (
          (actor ? cc.getAllAccountsUserCanSee(theUser)).mapTo[List[BankAccountUID]],
          TIMEOUT)
      }
      case _ => getAllPublicAccounts()
    }
  }

  def getAllAccountsUserCanSee(bank: Bank, user : Box[User]) : List[BankAccountUID] = {
    user match {
      case Full(theUser) => {
        Await.result(
          (actor ? cc.getAllAccountsUserCanSee(bank, theUser)).mapTo[List[BankAccountUID]],
          TIMEOUT
        )
      }
      case _ => getPublicBankAccounts(bank)
    }
  }

  def getNonPublicBankAccounts(user : User) :  List[BankAccountUID] = {
    Await.result(
      (actor ? cc.getNonPublicBankAccounts(user)).mapTo[List[BankAccountUID]],
      TIMEOUT
    )
  }

  def getNonPublicBankAccounts(user : User, bankId : BankId) :  List[BankAccountUID] = {
    Await.result(
      (actor ? cc.getNonPublicBankAccounts(user, bankId)).mapTo[List[BankAccountUID]],
      TIMEOUT
    )
  }

  def grantAccessToAllExistingViews(user : User) = {
    Await.result(
      (actor ? cc.grantAccessToAllExistingViews(user)).mapTo[Boolean],
      TIMEOUT
    )
  }

  def grantAccessToView(user : User, view : View) = {
    Await.result(
      (actor ? cc.grantAccessToView(user, view)).mapTo[Boolean],
      TIMEOUT
    )
  }

  def getOwners(view: View) : Set[User] = {
    Await.result(
      (actor ? cc.getOwners(view)).mapTo[Set[User]],
      TIMEOUT
    )
  }

  def createOwnerView(bankId: BankId, accountId: AccountId, description: String) : Box[View] = {
    Full(Await.result(
      (actor ? cc.createOwnerView(bankId, accountId, description)).mapTo[View],
      TIMEOUT
      )
    )
  }

  def createPublicView(bankId: BankId, accountId: AccountId, description: String) : Box[View] = {
    Full(Await.result(
      (actor ? cc.createPublicView(bankId, accountId, description)).mapTo[View],
      TIMEOUT
      )
    )
  }

  def createAccountantsView(bankId: BankId, accountId: AccountId, description: String) : Box[View] = {
    Full(Await.result(
      (actor ? cc.createAccountantsView(bankId, accountId, description)).mapTo[View],
      TIMEOUT
      )
    )
  }

  def createAuditorsView(bankId: BankId, accountId: AccountId, description: String) : Box[View] = {
    Full(Await.result(
      (actor ? cc.createAuditorsView(bankId, accountId, description)).mapTo[View],
      TIMEOUT
      )
    )
  }

  def createRandomView(bankId: BankId, accountId: AccountId) : Box[View] = {
    Full(Await.result(
      (actor ? cc.createRandomView(bankId, accountId)).mapTo[View],
      TIMEOUT
      )
    )
  }

  def viewExists(bankId: BankId, accountId: AccountId, name: String): Boolean = {
    Await.result(
      (actor ? cc.viewExists(bankId, accountId, name)).mapTo[Boolean],
      TIMEOUT
    )
  }

  def removeAllViews(bankId: BankId, accountId: AccountId): Boolean = {
    Await.result(
      (actor ? cc.removeAllViews(bankId, accountId)).mapTo[Boolean],
      TIMEOUT
    )
  }

  def removeAllPermissions(bankId: BankId, accountId: AccountId): Boolean = {
    Await.result(
      (actor ? cc.removeAllViews(bankId, accountId)).mapTo[Boolean],
      TIMEOUT
    )
  }

  // bulkDeletes for tests
  def bulkDeleteAllPermissionsAndViews(): Boolean = {
    Await.result(
      (actor ? cc.bulkDeleteAllPermissionsAndViews()).mapTo[Boolean],
      TIMEOUT
    )
  }

}
