package code.remotedata

import java.util.concurrent.TimeUnit

import akka.actor.Actor
import akka.event.Logging
import akka.util.Timeout
import code.model._
import code.model.dataAccess.ResourceUser
import code.users.{LiftUsers, RemoteUserCaseClasses}
import net.liftweb.common._
import net.liftweb.util.ControlHelpers.tryo

import scala.concurrent.duration._


class RemotedataUsersActor extends Actor {

  val logger = Logging(context.system, this)

  val mUsers = LiftUsers
  val rUsers = RemoteUserCaseClasses

  def receive = {

    case rUsers.getUserByResourceUserId(id: Long) =>
      logger.info("getUserByResourceUserId(" + id +")")

      {
        for {
          res <- mUsers.getUserByResourceUserId(id)
        } yield {
          sender ! res.asInstanceOf[User]
        }
      }.getOrElse( context.stop(sender) )

    case rUsers.getResourceUserByResourceUserId(id: Long) =>
      logger.info("getResourceUserByResourceUserId(" + id +")")

      {
        for {
          res <- mUsers.getResourceUserByResourceUserId(id)
        } yield {
          sender ! res.asInstanceOf[ResourceUser]
        }
      }.getOrElse( context.stop(sender) )

    case rUsers.getUserByProviderId(provider : String, idGivenByProvider : String) =>
      logger.info("getUserByProviderId(" + provider +"," + idGivenByProvider +")")

      {
        for {
          res <- mUsers.getUserByProviderId(provider, idGivenByProvider)
        } yield {
          sender ! res.asInstanceOf[User]
        }
      }.getOrElse( context.stop(sender) )

    case rUsers.getUserByUserId(userId: String) =>
      logger.info("getUserByUserId(" + userId +")")

      {
        for {
          res <- mUsers.getUserByUserId(userId)
        } yield {
          sender ! res.asInstanceOf[User]
        }
      }.getOrElse( context.stop(sender) )

    case rUsers.getUserByUserName(userName: String) =>
      logger.info("getUserByUserName(" + userName +")")

      {
        for {
          res <- mUsers.getUserByUserName(userName)
        } yield {
          sender ! res.asInstanceOf[ResourceUser]
        }
      }.getOrElse( context.stop(sender) )

    case rUsers.getUserByEmail(email: String) =>
      logger.info("getUserByEmail(" + email +")")

      {
        for {
          res <- mUsers.getUserByEmail(email)
        } yield {
          sender ! res
        }
      }.getOrElse( context.stop(sender) )

    case rUsers.getAllUsers() =>
      logger.info("getAllUsers()")

      {
        for {
          res <- mUsers.getAllUsers()
        } yield {
          sender ! res
        }
      }.getOrElse( context.stop(sender) )

    case rUsers.createResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String], userId: Option[String]) =>
      logger.info("createResourceUser(" + provider + ", " + providerId.getOrElse("None") + ", " + name.getOrElse("None") + ", " + email.getOrElse("None") + ", " + userId.getOrElse("None") + ")")

      {
        for {
          res <- mUsers.createResourceUser(provider, providerId, name, email, userId)
        } yield {
          sender ! res.asInstanceOf[ResourceUser]
        }
      }.getOrElse( context.stop(sender) )

    case rUsers.createUnsavedResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String], userId: Option[String]) =>
      logger.info("createUnsavedResourceUser(" + provider + ", " + providerId.getOrElse("None") + ", " + name.getOrElse("None") + ", " + email.getOrElse("None") + ", " + userId.getOrElse("None") + ")")

      {
        for {
          res <- mUsers.createUnsavedResourceUser(provider, providerId, name, email, userId)
        } yield {
          sender ! res.asInstanceOf[ResourceUser]
        }
      }.getOrElse( context.stop(sender) )

    case rUsers.saveResourceUser(resourceUser: ResourceUser) =>
      logger.info("saveResourceUser")

      {
        for {
          res <- mUsers.saveResourceUser(resourceUser)
        } yield {
          sender ! res.asInstanceOf[ResourceUser]
        }
      }.getOrElse( context.stop(sender) )

    case rUsers.deleteResourceUser(id: Long) =>
      logger.info("deleteResourceUser(" + id +")")

      {
        for {
          res <- tryo{mUsers.deleteResourceUser(id)}
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse( context.stop(sender) )


    case rUsers.bulkDeleteAllResourceUsers() =>

      logger.info("bulkDeleteAllResourceUsers()")

      {
        for {
          res <- mUsers.bulkDeleteAllResourceUsers()
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse( context.stop(sender) )


    case message => logger.info("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}

