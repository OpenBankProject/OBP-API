package code.remotedata

import akka.actor.ActorKilledException
import akka.pattern.ask
import akka.util.Timeout
import code.api.APIFailure
import code.model.User
import code.model.dataAccess.ResourceUser
import code.users.{RemotedataUsersCaseClasses, Users}
import net.liftweb.common.{Full, _}

import scala.collection.immutable.List
import scala.concurrent.Await
import scala.concurrent.duration._


object RemotedataUsers extends ActorInit with Users {

  val cc = RemotedataUsersCaseClasses

  def getUserByResourceUserId(id : Long) : Box[User] =
    extractFutureToBox(actor ? cc.getUserByResourceUserId(id))

  def getResourceUserByResourceUserId(id : Long) : Box[ResourceUser] =
    extractFutureToBox(actor ? cc.getResourceUserByResourceUserId(id))

  def getResourceUserByUserId(userId : String) : Box[ResourceUser] =
    extractFutureToBox(actor ? cc.getResourceUserByUserId(userId))

  def getUserByProviderId(provider : String, idGivenByProvider : String) : Box[User] =
    extractFutureToBox(actor ? cc.getUserByProviderId(provider, idGivenByProvider))

  def getUserByUserId(userId : String) : Box[User] =
    extractFutureToBox(actor ? cc.getUserByUserId(userId))

  def getUserByUserName(userName : String) : Box[ResourceUser] =
    extractFutureToBox(actor ? cc.getUserByUserName(userName))

  def getUserByEmail(email : String) : Box[List[ResourceUser]] =
    extractFutureToBox(actor ? cc.getUserByEmail(email))

  def getAllUsers() : Box[List[ResourceUser]] =
    extractFutureToBox(actor ? cc.getAllUsers())

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
