package code.remotedata

import java.util.concurrent.TimeUnit

import akka.actor.Actor
import akka.event.Logging
import akka.util.Timeout
import code.model._
import code.model.dataAccess.ResourceUser
import code.users.{LiftUsers, RemotedataUsersCaseClasses}
import net.liftweb.common._
import net.liftweb.util.ControlHelpers.tryo

import scala.concurrent.duration._


class RemotedataUsersActor extends Actor with ActorHelper {

  val logger = Logging(context.system, this)

  val mapper = LiftUsers
  val cc = RemotedataUsersCaseClasses

  def receive = {

    case cc.getUserByResourceUserId(id: Long) =>
      logger.info("getUserByResourceUserId(" + id +")")
      sender ! extractResult(mapper.getUserByResourceUserId(id))

    case cc.getResourceUserByResourceUserId(id: Long) =>
      logger.info("getResourceUserByResourceUserId(" + id +")")
      sender ! extractResult(mapper.getResourceUserByResourceUserId(id))

    case cc.getUserByProviderId(provider : String, idGivenByProvider : String) =>
      logger.info("getUserByProviderId(" + provider +"," + idGivenByProvider +")")
      sender ! extractResult(mapper.getUserByProviderId(provider, idGivenByProvider))

    case cc.getUserByUserId(userId: String) =>
      logger.info("getUserByUserId(" + userId +")")
      sender ! extractResult(mapper.getUserByUserId(userId))

    case cc.getUserByUserName(userName: String) =>
      logger.info("getUserByUserName(" + userName +")")
      sender ! extractResult(mapper.getUserByUserName(userName))

    case cc.getUserByEmail(email: String) =>
      logger.info("getUserByEmail(" + email +")")
      sender ! extractResult(mapper.getUserByEmail(email))

    case cc.getAllUsers() =>
      logger.info("getAllUsers()")
      sender ! extractResult(mapper.getAllUsers())

    case cc.createResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String], userId: Option[String]) =>
      logger.info("createResourceUser(" + provider + ", " + providerId.getOrElse("None") + ", " + name.getOrElse("None") + ", " + email.getOrElse("None") + ", " + userId.getOrElse("None") + ")")
      sender ! extractResult(mapper.createResourceUser(provider, providerId, name, email, userId))

    case cc.createUnsavedResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String], userId: Option[String]) =>
      logger.info("createUnsavedResourceUser(" + provider + ", " + providerId.getOrElse("None") + ", " + name.getOrElse("None") + ", " + email.getOrElse("None") + ", " + userId.getOrElse("None") + ")")
      sender ! extractResult(mapper.createUnsavedResourceUser(provider, providerId, name, email, userId))

    case cc.saveResourceUser(resourceUser: ResourceUser) =>
      logger.info("saveResourceUser")
      sender ! extractResult(mapper.saveResourceUser(resourceUser))

    case cc.deleteResourceUser(id: Long) =>
      logger.info("deleteResourceUser(" + id +")")
      sender ! extractResult(mapper.deleteResourceUser(id))

    case cc.bulkDeleteAllResourceUsers() =>
      logger.info("bulkDeleteAllResourceUsers()")
      sender ! extractResult(mapper.bulkDeleteAllResourceUsers())

    case message => logger.info("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}

