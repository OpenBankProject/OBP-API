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


class RemotedataUsersActor extends Actor {

  val logger = Logging(context.system, this)

  val mapper = LiftUsers
  val cc = RemotedataUsersCaseClasses

  def receive = {

    case cc.getUserByResourceUserId(id: Long) =>
      logger.info("getUserByResourceUserId(" + id +")")

      {
        for {
          res <- mapper.getUserByResourceUserId(id)
        } yield {
          sender ! res.asInstanceOf[User]
        }
      }.getOrElse( context.stop(sender) )

    case cc.getResourceUserByResourceUserId(id: Long) =>
      logger.info("getResourceUserByResourceUserId(" + id +")")

      {
        for {
          res <- mapper.getResourceUserByResourceUserId(id)
        } yield {
          sender ! res.asInstanceOf[ResourceUser]
        }
      }.getOrElse( context.stop(sender) )

    case cc.getUserByProviderId(provider : String, idGivenByProvider : String) =>
      logger.info("getUserByProviderId(" + provider +"," + idGivenByProvider +")")

      {
        for {
          res <- mapper.getUserByProviderId(provider, idGivenByProvider)
        } yield {
          sender ! res.asInstanceOf[User]
        }
      }.getOrElse( context.stop(sender) )

    case cc.getUserByUserId(userId: String) =>
      logger.info("getUserByUserId(" + userId +")")

      {
        for {
          res <- mapper.getUserByUserId(userId)
        } yield {
          sender ! res.asInstanceOf[User]
        }
      }.getOrElse( context.stop(sender) )

    case cc.getUserByUserName(userName: String) =>
      logger.info("getUserByUserName(" + userName +")")

      {
        for {
          res <- mapper.getUserByUserName(userName)
        } yield {
          sender ! res.asInstanceOf[ResourceUser]
        }
      }.getOrElse( context.stop(sender) )

    case cc.getUserByEmail(email: String) =>
      logger.info("getUserByEmail(" + email +")")

      {
        for {
          res <- mapper.getUserByEmail(email)
        } yield {
          sender ! res
        }
      }.getOrElse( context.stop(sender) )

    case cc.getAllUsers() =>
      logger.info("getAllUsers()")

      {
        for {
          res <- mapper.getAllUsers()
        } yield {
          sender ! res
        }
      }.getOrElse( context.stop(sender) )

    case cc.createResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String], userId: Option[String]) =>
      logger.info("createResourceUser(" + provider + ", " + providerId.getOrElse("None") + ", " + name.getOrElse("None") + ", " + email.getOrElse("None") + ", " + userId.getOrElse("None") + ")")

      {
        for {
          res <- mapper.createResourceUser(provider, providerId, name, email, userId)
        } yield {
          sender ! res.asInstanceOf[ResourceUser]
        }
      }.getOrElse( context.stop(sender) )

    case cc.createUnsavedResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String], userId: Option[String]) =>
      logger.info("createUnsavedResourceUser(" + provider + ", " + providerId.getOrElse("None") + ", " + name.getOrElse("None") + ", " + email.getOrElse("None") + ", " + userId.getOrElse("None") + ")")

      {
        for {
          res <- mapper.createUnsavedResourceUser(provider, providerId, name, email, userId)
        } yield {
          sender ! res.asInstanceOf[ResourceUser]
        }
      }.getOrElse( context.stop(sender) )

    case cc.saveResourceUser(resourceUser: ResourceUser) =>
      logger.info("saveResourceUser")

      {
        for {
          res <- mapper.saveResourceUser(resourceUser)
        } yield {
          sender ! res.asInstanceOf[ResourceUser]
        }
      }.getOrElse( context.stop(sender) )

    case cc.deleteResourceUser(id: Long) =>
      logger.info("deleteResourceUser(" + id +")")

      {
        for {
          res <- tryo{mapper.deleteResourceUser(id)}
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse( context.stop(sender) )


    case cc.bulkDeleteAllResourceUsers() =>

      logger.info("bulkDeleteAllResourceUsers()")

      {
        for {
          res <- mapper.bulkDeleteAllResourceUsers()
        } yield {
          sender ! res.asInstanceOf[Boolean]
        }
      }.getOrElse( context.stop(sender) )


    case message => logger.info("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}

