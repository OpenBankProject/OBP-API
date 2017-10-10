package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.entitlement.Entitlement
import code.model.User
import code.model.dataAccess.{ResourceUser, ResourceUserCaseClass}
import code.users.{RemotedataUsersCaseClasses, Users}
import net.liftweb.common.Box

import scala.collection.immutable.List
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object RemotedataUsers extends ObpActorInit with Users {

  val cc = RemotedataUsersCaseClasses

  def getUserByResourceUserId(id : Long) : Box[User] =
    extractFutureToBox(actor ? cc.getUserByResourceUserId(id))

  def getResourceUserByResourceUserId(id : Long) : Box[ResourceUser] =
    extractFutureToBox(actor ? cc.getResourceUserByResourceUserId(id))

  def getResourceUserByResourceUserIdFuture(id : Long) : Future[Box[User]] = {
    (actor ? cc.getResourceUserByResourceUserIdFuture(id)).mapTo[Box[User]]
  }

  def getUserByProviderId(provider : String, idGivenByProvider : String) : Box[User] =
    extractFutureToBox(actor ? cc.getUserByProviderId(provider, idGivenByProvider))

  def getUserByProviderIdFuture(provider : String, idGivenByProvider : String) : Future[Box[User]] =
    (actor ? cc.getUserByProviderIdFuture(provider, idGivenByProvider)).mapTo[Box[User]]

  def getOrCreateUserByProviderIdFuture(provider : String, idGivenByProvider : String) : Future[Box[User]] =
    (actor ? cc.getOrCreateUserByProviderIdFuture(provider, idGivenByProvider)).mapTo[Box[User]]

  def getUserByUserId(userId : String) : Box[User] =
    extractFutureToBox(actor ? cc.getUserByUserId(userId))

  def getUserByUserName(userName : String) : Box[ResourceUser] =
    extractFutureToBox(actor ? cc.getUserByUserName(userName))

  def getUserByEmail(email : String) : Box[List[ResourceUser]] =
    extractFutureToBox(actor ? cc.getUserByEmail(email))

  def getAllUsers() : Box[List[ResourceUser]] =
    extractFutureToBox(actor ? cc.getAllUsers())

  def getAllUsersF() : Future[List[(ResourceUser, Box[List[Entitlement]])]] = {
    val res = (actor ? cc.getAllUsersF())
    res.mapTo[List[(ResourceUser, Box[List[Entitlement]])]]
  }

  def createResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String], userId: Option[String]) : Box[ResourceUser] =
    extractFutureToBox(actor ? cc.createResourceUser(provider, providerId, name, email, userId))

  def createUnsavedResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String], userId: Option[String]) : Box[ResourceUser] =
    extractFutureToBox(actor ? cc.createUnsavedResourceUser(provider, providerId, name, email, userId))

  def saveResourceUser(resourceUser: ResourceUser) : Box[ResourceUser] =
    extractFutureToBox(actor ? cc.saveResourceUser(resourceUser))

  def deleteResourceUser(userId: Long) : Box[Boolean] =
    extractFutureToBox(actor ? cc.deleteResourceUser(userId))

  def bulkDeleteAllResourceUsers(): Box[Boolean] =
    extractFutureToBox(actor ? cc.bulkDeleteAllResourceUsers())


}
