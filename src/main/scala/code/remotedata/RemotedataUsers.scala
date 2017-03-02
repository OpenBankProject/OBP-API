package code.remotedata

import akka.actor.ActorKilledException
import akka.pattern.ask
import akka.util.Timeout
import code.api.APIFailure
import code.model.User
import code.model.dataAccess.ResourceUser
import code.users.{RemoteUserCaseClasses, Users}
import net.liftweb.common.{Full, _}

import scala.collection.immutable.List
import scala.concurrent.Await
import scala.concurrent.duration._


object RemotedataUsers extends Users {

  implicit val timeout = Timeout(10000 milliseconds)
  val TIMEOUT = 10 seconds
  val rUsers = RemoteUserCaseClasses
  var usersActor = RemotedataActorSystem.getActor("users")

  def getUserByResourceUserId(id : Long) : Box[User] = {
    val res = try {
    Full(
    Await.result(
    (usersActor ? rUsers.getUserByResourceUserId(id)).mapTo[User],
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
    (usersActor ? rUsers.getResourceUserByResourceUserId(id)).mapTo[ResourceUser],
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
    (usersActor ? rUsers.getUserByProviderId(provider, idGivenByProvider)).mapTo[User],
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
    (usersActor ? rUsers.getUserByUserId(userId)).mapTo[User],
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
    (usersActor ? rUsers.getUserByUserName(userName)).mapTo[ResourceUser],
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
    (usersActor ? rUsers.getUserByEmail(email)).mapTo[List[ResourceUser]],
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
    (usersActor ? rUsers.getAllUsers()).mapTo[List[ResourceUser]],
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
    (usersActor ? rUsers.createResourceUser(provider, providerId, name, email, userId)).mapTo[ResourceUser],
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
    (usersActor ? rUsers.createUnsavedResourceUser(provider, providerId, name, email, userId)).mapTo[ResourceUser],
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
    (usersActor ? rUsers.saveResourceUser(resourceUser)).mapTo[ResourceUser],
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

  def deleteResourceUser(userId: Long) : Box[Boolean] = {
    val res = try{
    Full(
    Await.result(
    (usersActor ? rUsers.deleteResourceUser(userId)).mapTo[Boolean],
    TIMEOUT
    )
    )
  }
    catch {
    case k: ActorKilledException => Empty ~> APIFailure(s"User not deleted", 404)
    case e: Throwable => throw e
  }
    res
  }


  def bulkDeleteAllResourceUsers(): Box[Boolean] = {
    Full(
    Await.result(
    (usersActor ? rUsers.bulkDeleteAllResourceUsers()).mapTo[Boolean],
    TIMEOUT
    )
    )
  }

}
