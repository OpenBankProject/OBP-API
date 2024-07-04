package code.remotedata

import java.util.Date

import akka.actor.Actor
import akka.pattern.pipe
import code.actorsystem.ObpActorHelper
import code.api.util.OBPQueryParam
import code.model.dataAccess.ResourceUser
import code.users.{LiftUsers, RemotedataUsersCaseClasses}
import code.util.Helper.MdcLoggable

import scala.collection.immutable.List
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.UserPrimaryKey

class RemotedataUsersActor extends Actor with ObpActorHelper with MdcLoggable  {

  val mapper = LiftUsers
  val cc = RemotedataUsersCaseClasses

  def receive: PartialFunction[Any, Unit] = {

    case cc.getUserByResourceUserId(id: Long) =>
      logger.debug("getUserByResourceUserId(" + id +")")
      sender ! (mapper.getUserByResourceUserId(id))

    case cc.getResourceUserByResourceUserId(id: Long) =>
      logger.debug("getResourceUserByResourceUserId(" + id +")")
      sender ! (mapper.getResourceUserByResourceUserId(id))

    case cc.getResourceUserByResourceUserIdFuture(id: Long) =>
      logger.debug("getResourceUserByResourceUserIdFuture(" + id +")")
      sender ! (mapper.getResourceUserByResourceUserIdF(id))

    case cc.getUserByProviderId(provider : String, idGivenByProvider : String) =>
      logger.debug("getUserByProviderId(" + provider +"," + idGivenByProvider +")")
      sender ! (mapper.getUserByProviderId(provider, idGivenByProvider))

    case cc.getUserByProviderIdFuture(provider : String, idGivenByProvider : String) =>
      logger.debug("getUserByProviderIdFuture(" + provider +"," + idGivenByProvider +")")
      sender ! (mapper.getUserByProviderId(provider, idGivenByProvider))

    case cc.getOrCreateUserByProviderIdFuture(provider : String, idGivenByProvider : String, createdByConsentId: Option[String],name: Option[String], email: Option[String]) =>
      logger.debug("getOrCreateUserByProviderIdFuture(" + provider +"," + idGivenByProvider +createdByConsentId+ name + email +")")
      (mapper.getOrCreateUserByProviderIdFuture(provider, idGivenByProvider, createdByConsentId, name, email)) pipeTo sender

    case cc.getUserByUserId(userId: String) =>
      logger.debug("getUserByUserId(" + userId +")")
      sender ! (mapper.getUserByUserId(userId))

    case cc.getUserByUserIdFuture(userId: String) =>
      logger.debug("getUserByUserIdFuture(" + userId +")")
      sender ! (mapper.getUserByUserId(userId))

    case cc.getUsersByUserIdsFuture(userIds: List[String]) =>
      logger.debug("getUsersByUserIdsFuture(" + userIds +")")
      sender ! (mapper.getUsersByUserIds(userIds))

    case cc.getUserByUserName(provider: String, userName: String) =>
      logger.debug("getUserByUserName("+provider + userName +")")
      sender ! (mapper.getUserByProviderAndUsername(provider, userName))

    case cc.getUserByUserNameFuture(provider: String, userName: String) =>
      logger.debug("getUserByUserNameFuture("+provider + userName +")")
      sender ! (mapper.getUserByProviderAndUsername(provider, userName))

    case cc.getUserByEmail(email: String) =>
      logger.debug("getUserByEmail(" + email +")")
      sender ! (mapper.getUserByEmail(email))

    case cc.getUserByEmailFuture(email: String) =>
      logger.debug("getUserByEmailFuture(" + email +")")
      sender ! (mapper.getUserByEmailF(email))
      
    case cc.getUsersByEmail(email: String) =>
      logger.debug("getUsersByEmail(" + email +")")
      mapper.getUsersByEmail(email) pipeTo sender

    case cc.getAllUsers() =>
      logger.debug("getAllUsers()")
      sender ! (mapper.getAllUsers())

    case cc.getAllUsersF(queryParams: List[OBPQueryParam]) =>
      logger.debug(s"getAllUsersF(queryParams: ($queryParams))")
      mapper.getAllUsersF(queryParams) pipeTo sender
      
    case cc.getUsers(queryParams: List[OBPQueryParam]) =>
      logger.debug(s"getUsers(queryParams: ($queryParams))")
      mapper.getUsers(queryParams) pipeTo sender

    case cc.createResourceUser(provider: String, providerId: Option[String], createdByConsentId: Option[String], name: Option[String], email: Option[String], userId: Option[String], createdByUserInvitationId: Option[String], company: Option[String], lastMarketingAgreementSignedDate: Option[Date]) =>
      logger.debug("createResourceUser(" + provider + ", " + providerId.getOrElse("None") + ", " + name.getOrElse("None") + ", " + email.getOrElse("None") + ", " + userId.getOrElse("None") + ", " + createdByUserInvitationId.getOrElse("None") + ", " + company.getOrElse("None")  + ", " + lastMarketingAgreementSignedDate.getOrElse("None") + ")")
      sender ! (mapper.createResourceUser(provider, providerId, createdByConsentId, name, email, userId, createdByUserInvitationId, company, lastMarketingAgreementSignedDate))

    case cc.createUnsavedResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String], userId: Option[String]) =>
      logger.debug("createUnsavedResourceUser(" + provider + ", " + providerId.getOrElse("None") + ", " + name.getOrElse("None") + ", " + email.getOrElse("None") + ", " + userId.getOrElse("None") + ")")
      sender ! (mapper.createUnsavedResourceUser(provider, providerId, name, email, userId))

    case cc.saveResourceUser(resourceUser: ResourceUser) =>
      logger.debug("saveResourceUser")
      sender ! (mapper.saveResourceUser(resourceUser))

    case cc.deleteResourceUser(id: Long) =>
      logger.debug("deleteResourceUser(" + id +")")
      sender ! (mapper.deleteResourceUser(id))
      
    case cc.scrambleDataOfResourceUser(userPrimaryKey: UserPrimaryKey) =>
      logger.debug("scrambleDataOfResourceUser(" + userPrimaryKey +")")
      sender ! (mapper.scrambleDataOfResourceUser(userPrimaryKey))

    case cc.bulkDeleteAllResourceUsers() =>
      logger.debug("bulkDeleteAllResourceUsers()")
      sender ! (mapper.bulkDeleteAllResourceUsers())

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}

