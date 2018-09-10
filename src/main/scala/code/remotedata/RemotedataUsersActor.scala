package code.remotedata

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.bankconnectors.OBPQueryParam
import code.model.dataAccess.ResourceUser
import code.users.{LiftUsers, RemotedataUsersCaseClasses}
import code.util.Helper.MdcLoggable

import scala.collection.immutable.List

class RemotedataUsersActor extends Actor with ObpActorHelper with MdcLoggable  {

  val mapper = LiftUsers
  val cc = RemotedataUsersCaseClasses

  def receive = {

    case cc.getUserByResourceUserId(id: Long) =>
      logger.debug("getUserByResourceUserId(" + id +")")
      sender ! extractResult(mapper.getUserByResourceUserId(id))

    case cc.getResourceUserByResourceUserId(id: Long) =>
      logger.debug("getResourceUserByResourceUserId(" + id +")")
      sender ! extractResult(mapper.getResourceUserByResourceUserId(id))

    case cc.getResourceUserByResourceUserIdFuture(id: Long) =>
      logger.debug("getResourceUserByResourceUserIdFuture(" + id +")")
      sender ! (mapper.getResourceUserByResourceUserIdF(id))

    case cc.getUserByProviderId(provider : String, idGivenByProvider : String) =>
      logger.debug("getUserByProviderId(" + provider +"," + idGivenByProvider +")")
      sender ! extractResult(mapper.getUserByProviderId(provider, idGivenByProvider))

    case cc.getUserByProviderIdFuture(provider : String, idGivenByProvider : String) =>
      logger.debug("getUserByProviderIdFuture(" + provider +"," + idGivenByProvider +")")
      sender ! (mapper.getUserByProviderId(provider, idGivenByProvider))

    case cc.getOrCreateUserByProviderIdFuture(provider : String, idGivenByProvider : String) =>
      logger.debug("getOrCreateUserByProviderIdFuture(" + provider +"," + idGivenByProvider +")")
      sender ! (mapper.getOrCreateUserByProviderId(provider, idGivenByProvider))

    case cc.getUserByUserId(userId: String) =>
      logger.debug("getUserByUserId(" + userId +")")
      sender ! extractResult(mapper.getUserByUserId(userId))

    case cc.getUserByUserIdFuture(userId: String) =>
      logger.debug("getUserByUserIdFuture(" + userId +")")
      sender ! (mapper.getUserByUserId(userId))

    case cc.getUsersByUserIdsFuture(userIds: List[String]) =>
      logger.debug("getUsersByUserIdsFuture(" + userIds +")")
      sender ! extractResult(mapper.getUsersByUserIds(userIds))

    case cc.getUserByUserName(userName: String) =>
      logger.debug("getUserByUserName(" + userName +")")
      sender ! extractResult(mapper.getUserByUserName(userName))

    case cc.getUserByUserNameFuture(userName: String) =>
      logger.debug("getUserByUserNameFuture(" + userName +")")
      sender ! (mapper.getUserByUserName(userName))

    case cc.getUserByEmail(email: String) =>
      logger.debug("getUserByEmail(" + email +")")
      sender ! extractResult(mapper.getUserByEmail(email))

    case cc.getUserByEmailFuture(email: String) =>
      logger.debug("getUserByEmailFuture(" + email +")")
      sender ! (mapper.getUserByEmailF(email))

    case cc.getAllUsers() =>
      logger.debug("getAllUsers()")
      sender ! extractResult(mapper.getAllUsers())

    case cc.getAllUsersF(queryParams: List[OBPQueryParam]) =>
      logger.debug(s"getAllUsersF(queryParams: ($queryParams))")
      sender ! (mapper.getAllUsersFF(queryParams))

    case cc.createResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String], userId: Option[String]) =>
      logger.debug("createResourceUser(" + provider + ", " + providerId.getOrElse("None") + ", " + name.getOrElse("None") + ", " + email.getOrElse("None") + ", " + userId.getOrElse("None") + ")")
      sender ! extractResult(mapper.createResourceUser(provider, providerId, name, email, userId))

    case cc.createUnsavedResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String], userId: Option[String]) =>
      logger.debug("createUnsavedResourceUser(" + provider + ", " + providerId.getOrElse("None") + ", " + name.getOrElse("None") + ", " + email.getOrElse("None") + ", " + userId.getOrElse("None") + ")")
      sender ! extractResult(mapper.createUnsavedResourceUser(provider, providerId, name, email, userId))

    case cc.saveResourceUser(resourceUser: ResourceUser) =>
      logger.debug("saveResourceUser")
      sender ! extractResult(mapper.saveResourceUser(resourceUser))

    case cc.deleteResourceUser(id: Long) =>
      logger.debug("deleteResourceUser(" + id +")")
      sender ! extractResult(mapper.deleteResourceUser(id))

    case cc.bulkDeleteAllResourceUsers() =>
      logger.debug("bulkDeleteAllResourceUsers()")
      sender ! extractResult(mapper.bulkDeleteAllResourceUsers())

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}

