package code.remotedata

import akka.actor.{ActorKilledException, ActorSelection, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import code.accountholder.{AccountHolders, RemoteAccountHoldersCaseClasses}
import code.api.APIFailure
import code.metadata.counterparties.{Counterparties, CounterpartyTrait, RemoteCounterpartiesCaseClasses}
import code.model.dataAccess.ResourceUser
import code.model.{CreateViewJSON, Permission, UpdateViewJSON, _}
import code.users.{RemoteUserCaseClasses, Users}
import code.views.{RemoteViewCaseClasses, Views}
import com.typesafe.config.ConfigFactory
import net.liftweb.common.{Full, _}
import net.liftweb.util.Props

import scala.collection.immutable.List
import scala.concurrent.Await
import scala.concurrent.duration._


object Remotedata extends Views with Users with Counterparties with AccountHolders {

  val TIMEOUT = 10 seconds
  val rViews = RemoteViewCaseClasses
  val rUsers = RemoteUserCaseClasses
  val rCounterparties = RemoteCounterpartiesCaseClasses
  val rAccountHolders = RemoteAccountHoldersCaseClasses
  implicit val timeout = Timeout(10000 milliseconds)

  val remote = ActorSystem("LookupSystem", ConfigFactory.load("remotelookup"))
  val cfg = ConfigFactory.load("obplocaldata")
  val host = cfg.getString("akka.remote.netty.tcp.hostname")
  val port = cfg.getString("akka.remote.netty.tcp.port")
  var actorPath = "akka.tcp://OBPDataWorkerSystem@" + host + ":" + port + "/user/OBPLocalDataActor"
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
          (viewsActor ? rViews.addPermissions(views, user)).mapTo[List[View]],
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
        (viewsActor ? rViews.permission(account, user)).mapTo[Permission],
        TIMEOUT
      )
    )
  }

  def addPermission(viewUID: ViewUID, user: User): Box[View] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? rViews.addPermission(viewUID, user)).mapTo[View],
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
          (viewsActor ? rViews.revokePermission(viewUID, user)).mapTo[Boolean],
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
          (viewsActor ? rViews.revokeAllPermissions(bankId, accountId, user)).mapTo[Boolean],
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
        (viewsActor ? rViews.view(viewUID)).mapTo[View],
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
        (viewsActor ? rViews.view(viewId, account)).mapTo[View],
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
      (viewsActor ? rViews.createView(bankAccountId, view)).mapTo[Box[View]],
      TIMEOUT
    )
  }

  def updateView(bankAccountId : BankAccountUID, viewId: ViewId, viewUpdateJson : UpdateViewJSON) : Box[View] = {
    Await.result(
      (viewsActor ? rViews.updateView(bankAccountId, viewId, viewUpdateJson)).mapTo[Box[View]],
      TIMEOUT
    )
  }

  def removeView(viewId: ViewId, bankAccountId: BankAccountUID): Box[Unit] = {
    Await.result(
      (viewsActor ? rViews.removeView(viewId, bankAccountId)).mapTo[Box[Unit]],
      TIMEOUT
    )
  }

  def permissions(account : BankAccountUID) : List[Permission] = {
    Await.result(
      (viewsActor ? rViews.permissions(account)).mapTo[List[Permission]],
      TIMEOUT
    )
  }

  def views(bankAccountId : BankAccountUID) : List[View] = {
    Await.result(
      (viewsActor ? rViews.views(bankAccountId)).mapTo[List[View]],
      TIMEOUT
    )
  }

  def permittedViews(user: User, bankAccountId: BankAccountUID): List[View] = {
    Await.result(
      (viewsActor ? rViews.permittedViews(user, bankAccountId)).mapTo[List[View]],
      TIMEOUT
    )
  }

  def publicViews(bankAccountId : BankAccountUID) : List[View] = {
    Await.result(
      (viewsActor ? rViews.publicViews(bankAccountId)).mapTo[List[View]],
      TIMEOUT
    )
  }

  def getAllPublicAccounts() : List[BankAccountUID] = {
    Await.result(
      (viewsActor ? rViews.getAllPublicAccounts()).mapTo[List[BankAccountUID]],
      TIMEOUT
    )
  }

  def getPublicBankAccounts(bank : Bank) : List[BankAccountUID] = {
    Await.result(
      (viewsActor ? rViews.getPublicBankAccounts(bank)).mapTo[List[BankAccountUID]],
      TIMEOUT
    )
  }

  def getAllAccountsUserCanSee(user : Box[User]) : List[BankAccountUID] = {
    user match {
      case Full(theUser) => {
        Await.result (
          (viewsActor ? rViews.getAllAccountsUserCanSee(theUser)).mapTo[List[BankAccountUID]],
          TIMEOUT)
      }
      case _ => getAllPublicAccounts()
    }
  }

  def getAllAccountsUserCanSee(bank: Bank, user : Box[User]) : List[BankAccountUID] = {
    user match {
      case Full(theUser) => {
        Await.result(
          (viewsActor ? rViews.getAllAccountsUserCanSee(bank, theUser)).mapTo[List[BankAccountUID]],
          TIMEOUT
        )
      }
      case _ => getPublicBankAccounts(bank)
    }
  }

  def getNonPublicBankAccounts(user : User) :  List[BankAccountUID] = {
    Await.result(
      (viewsActor ? rViews.getNonPublicBankAccounts(user)).mapTo[List[BankAccountUID]],
      TIMEOUT
    )
  }

  def getNonPublicBankAccounts(user : User, bankId : BankId) :  List[BankAccountUID] = {
    Await.result(
      (viewsActor ? rViews.getNonPublicBankAccounts(user, bankId)).mapTo[List[BankAccountUID]],
      TIMEOUT
    )
  }

  def grantAccessToAllExistingViews(user : User) = {
    Await.result(
      (viewsActor ? rViews.grantAccessToAllExistingViews(user)).mapTo[Boolean],
      TIMEOUT
    )
  }

  def grantAccessToView(user : User, view : View) = {
    Await.result(
      (viewsActor ? rViews.grantAccessToView(user, view)).mapTo[Boolean],
      TIMEOUT
    )
  }

  def getOwners(view: View) : Set[User] = {
    Await.result(
      (viewsActor ? rViews.getOwners(view)).mapTo[Set[User]],
      TIMEOUT
    )
  }

  def createOwnerView(bankId: BankId, accountId: AccountId, description: String) : Box[View] = {
    Full(Await.result(
      (viewsActor ? rViews.createOwnerView(bankId, accountId, description)).mapTo[View],
      TIMEOUT
      )
    )
  }

  def createPublicView(bankId: BankId, accountId: AccountId, description: String) : Box[View] = {
    Full(Await.result(
      (viewsActor ? rViews.createPublicView(bankId, accountId, description)).mapTo[View],
      TIMEOUT
      )
    )
  }

  def createAccountantsView(bankId: BankId, accountId: AccountId, description: String) : Box[View] = {
    Full(Await.result(
      (viewsActor ? rViews.createAccountantsView(bankId, accountId, description)).mapTo[View],
      TIMEOUT
      )
    )
  }

  def createAuditorsView(bankId: BankId, accountId: AccountId, description: String) : Box[View] = {
    Full(Await.result(
      (viewsActor ? rViews.createAuditorsView(bankId, accountId, description)).mapTo[View],
      TIMEOUT
      )
    )
  }

  def createRandomView(bankId: BankId, accountId: AccountId) : Box[View] = {
    Full(Await.result(
      (viewsActor ? rViews.createRandomView(bankId, accountId)).mapTo[View],
      TIMEOUT
      )
    )
  }

  def viewExists(bankId: BankId, accountId: AccountId, name: String): Boolean = {
    Await.result(
      (viewsActor ? rViews.viewExists(bankId, accountId, name)).mapTo[Boolean],
      TIMEOUT
    )
  }

  def removeAllViews(bankId: BankId, accountId: AccountId): Boolean = {
    Await.result(
      (viewsActor ? rViews.removeAllViews(bankId, accountId)).mapTo[Boolean],
      TIMEOUT
    )
  }

  def removeAllPermissions(bankId: BankId, accountId: AccountId): Boolean = {
    Await.result(
      (viewsActor ? rViews.removeAllViews(bankId, accountId)).mapTo[Boolean],
      TIMEOUT
    )
  }
  // Resource user part
  def getUserByResourceUserId(id : Long) : Box[User] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? rUsers.getUserByResourceUserId(id)).mapTo[User],
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

  def getResourceUserByResourceUserId(id : Long) : Box[ResourceUser] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? rUsers.getResourceUserByResourceUserId(id)).mapTo[ResourceUser],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"ResourceUser not found", 404)
      case e: Throwable => throw e
    }
    res
  }

  def getUserByProviderId(provider : String, idGivenByProvider : String) : Box[User] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? rUsers.getUserByProviderId(provider, idGivenByProvider)).mapTo[User],
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
          (viewsActor ? rUsers.getUserByUserId(userId)).mapTo[User],
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

  def getUserByUserName(userName : String) : Box[ResourceUser] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? rUsers.getUserByUserName(userName)).mapTo[ResourceUser],
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

  def getUserByEmail(email : String) : Box[List[ResourceUser]] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? rUsers.getUserByEmail(email)).mapTo[List[ResourceUser]],
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

  def getAllUsers() : Box[List[ResourceUser]] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? rUsers.getAllUsers()).mapTo[List[ResourceUser]],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"Users not found", 404)
      case e: Throwable => throw e
    }
    res
  }

  def createResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String], userId: Option[String]) : Box[ResourceUser] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? rUsers.createResourceUser(provider, providerId, name, email, userId)).mapTo[ResourceUser],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"User not created", 404)
      case e: Throwable => throw e
    }
    res
  }

  def createUnsavedResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String], userId: Option[String]) : Box[ResourceUser] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? rUsers.createUnsavedResourceUser(provider, providerId, name, email, userId)).mapTo[ResourceUser],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"User not created", 404)
      case e: Throwable => throw e
    }
    res
  }

  def saveResourceUser(resourceUser: ResourceUser) : Box[ResourceUser] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? rUsers.saveResourceUser(resourceUser)).mapTo[ResourceUser],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"User not created", 404)
      case e: Throwable => throw e
    }
    res
  }

  override def getOrCreateMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, otherParty: Counterparty): Box[CounterpartyMetadata] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? rCounterparties.getOrCreateMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, otherParty: Counterparty)).mapTo[CounterpartyMetadata],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"Can not getOrCreateMetadata", 404)
      case e: Throwable => throw e
    }
    res
  }

  override def getMetadatas(originalPartyBankId: BankId, originalPartyAccountId: AccountId): List[CounterpartyMetadata] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? rCounterparties.getMetadatas(originalPartyBankId: BankId, originalPartyAccountId: AccountId)).mapTo[List[CounterpartyMetadata]],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"Can not getMetadatas", 404)
      case e: Throwable => throw e
    }
    res.get
  }

  override def getMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, counterpartyMetadataId: String): Box[CounterpartyMetadata] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? rCounterparties.getMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, counterpartyMetadataId: String)).mapTo[CounterpartyMetadata],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"Can not getMetadata", 404)
      case e: Throwable => throw e
    }
    res
  }

  override def getCounterparty(counterPartyId: String): Box[CounterpartyTrait] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? rCounterparties.getCounterparty(counterPartyId: String)).mapTo[CounterpartyTrait],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"Can not getCounterparty", 404)
      case e: Throwable => throw e
    }
    res
  }

  override def getCounterpartyByIban(iban: String): Box[CounterpartyTrait] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? rCounterparties.getCounterpartyByIban(iban: String)).mapTo[CounterpartyTrait],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"Can not getCounterpartyByIban", 404)
      case e: Throwable => throw e
    }
    res
  }

  override def createCounterparty(createdByUserId: String, thisBankId: String, thisAccountId: String, thisViewId: String, name: String, otherBankId: String, otherAccountId: String, otherAccountRoutingScheme: String, otherAccountRoutingAddress: String, otherBankRoutingScheme: String, otherBankRoutingAddress: String, isBeneficiary: Boolean): Box[CounterpartyTrait] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? rCounterparties.createCounterparty(createdByUserId, thisBankId, thisAccountId, thisViewId, name, otherBankId, otherAccountId,
                                                           otherAccountRoutingScheme, otherAccountRoutingAddress, otherBankRoutingScheme, otherBankRoutingAddress,
                                                           isBeneficiary)).mapTo[CounterpartyTrait],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"Can not  createCounterparty", 404)
      case e: Throwable => throw e
    }
    res
  }

  override def checkCounterpartyAvailable(name: String, thisBankId: String, thisAccountId: String, thisViewId: String): Boolean = {
    Await.result(
      (viewsActor ? rCounterparties.checkCounterpartyAvailable(name: String, thisBankId: String, thisAccountId: String, thisViewId: String)).mapTo[Boolean],
      TIMEOUT
    )
  }


  override def createAccountHolder(userId: Long, bankId: String, accountId: String, source: String = "MappedAccountHolder"): Boolean = {
    Await.result(
      (viewsActor ? rAccountHolders.createAccountHolder(userId, bankId, accountId, source)).mapTo[Boolean],
      TIMEOUT
    )
  }

  override def getAccountHolders(bankId: BankId, accountId: AccountId): Set[User] = {
    Await.result(
      (viewsActor ? rAccountHolders.getAccountHolders(bankId, accountId)).mapTo[Set[User]],
      TIMEOUT
    )
  }

}

