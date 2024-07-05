package code.remotedata

import java.util.Date

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.api.util.OBPQueryParam
import code.entitlement.Entitlement
import code.model.dataAccess.ResourceUser
import code.users.{RemotedataUsersCaseClasses, UserAgreement, Users}
import com.openbankproject.commons.model.{User, UserPrimaryKey}
import net.liftweb.common.Box

import scala.collection.immutable.List
import scala.concurrent.Future

object RemotedataUsers extends ObpActorInit with Users {

  val cc = RemotedataUsersCaseClasses

  def getUserByResourceUserId(id : Long) : Box[User] = getValueFromFuture(
    (actor ? cc.getUserByResourceUserId(id)).mapTo[Box[User]]
  )

  def getResourceUserByResourceUserId(id : Long) : Box[ResourceUser] = getValueFromFuture(
    (actor ? cc.getResourceUserByResourceUserId(id)).mapTo[Box[ResourceUser]]
  )

  def getResourceUserByResourceUserIdFuture(id : Long) : Future[Box[User]] = {
    (actor ? cc.getResourceUserByResourceUserIdFuture(id)).mapTo[Box[User]]
  }

  def getUserByProviderId(provider : String, idGivenByProvider : String) : Box[User] = getValueFromFuture(
    (actor ? cc.getUserByProviderId(provider, idGivenByProvider)).mapTo[Box[User]]
  )

  def getUserByProviderIdFuture(provider : String, idGivenByProvider : String) : Future[Box[User]] =
    (actor ? cc.getUserByProviderIdFuture(provider, idGivenByProvider)).mapTo[Box[User]]

  def getOrCreateUserByProviderIdFuture(provider : String, idGivenByProvider : String, createdByConsentId: Option[String], name: Option[String], email: Option[String]) : Future[(Box[User], Boolean)] =
    (actor ? cc.getOrCreateUserByProviderIdFuture(provider, idGivenByProvider, createdByConsentId, name, email)).mapTo[(Box[User], Boolean)]

  def getUserByUserId(userId : String) : Box[User] = getValueFromFuture(
    (actor ? cc.getUserByUserId(userId)).mapTo[Box[User]]
  )

  def getUserByUserIdFuture(userId : String) : Future[Box[User]] =
    (actor ? cc.getUserByUserIdFuture(userId)).mapTo[Box[User]]

  def getUsersByUserIdsFuture(userIds : List[String]) : Future[List[User]] =
    (actor ? cc.getUsersByUserIdsFuture(userIds)).mapTo[List[User]]

  def getUserByProviderAndUsername(provider : String, userName : String) : Box[ResourceUser] = getValueFromFuture(
    (actor ? cc.getUserByUserName(provider, userName)).mapTo[Box[ResourceUser]]
  )

  def getUserByProviderAndUsernameFuture(provider: String, username: String): Future[Box[User]] =
    (actor ? cc.getUserByUserNameFuture(provider, username)).mapTo[Box[User]]

  def getUserByEmail(email : String) : Box[List[ResourceUser]] = getValueFromFuture(
    (actor ? cc.getUserByEmail(email)).mapTo[Box[List[ResourceUser]]]
  )

  def getUserByEmailFuture(email : String) : Future[List[(ResourceUser, Box[List[Entitlement]])]] =
    (actor ? cc.getUserByEmailFuture(email)).mapTo[List[(ResourceUser, Box[List[Entitlement]])]]
  
  def getUsersByEmail(email : String) : Future[List[(ResourceUser, Box[List[Entitlement]], Option[List[UserAgreement]])]] =
    (actor ? cc.getUsersByEmail(email)).mapTo[List[(ResourceUser, Box[List[Entitlement]], Option[List[UserAgreement]])]]

  def getAllUsers() : Box[List[ResourceUser]] = getValueFromFuture(
    (actor ? cc.getAllUsers()).mapTo[Box[List[ResourceUser]]]
  )

  def getAllUsersF(queryParams: List[OBPQueryParam]) : Future[List[(ResourceUser, Box[List[Entitlement]])]] = {
    val res = (actor ? cc.getAllUsersF(queryParams))
    res.mapTo[List[(ResourceUser, Box[List[Entitlement]])]]
  }
  
  def getUsers(queryParams: List[OBPQueryParam]): Future[List[(ResourceUser, Box[List[Entitlement]], Option[List[UserAgreement]])]]  = {
    val res = (actor ? cc.getUsers(queryParams))
    res.mapTo[List[(ResourceUser, Box[List[Entitlement]], Option[List[UserAgreement]])]]
  }

  def createResourceUser(provider: String, providerId: Option[String], createdByConsentId: Option[String], name: Option[String], email: Option[String], userId: Option[String], createdByUserInvitationId: Option[String], company: Option[String], lastMarketingAgreementSignedDate: Option[Date]) : Box[ResourceUser] = getValueFromFuture(
    (actor ? cc.createResourceUser(provider, providerId, createdByConsentId, name, email, userId, createdByUserInvitationId, company, lastMarketingAgreementSignedDate)).mapTo[Box[ResourceUser]]
  )

  def createUnsavedResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String], userId: Option[String]) : Box[ResourceUser] = getValueFromFuture(
    (actor ? cc.createUnsavedResourceUser(provider, providerId, name, email, userId)).mapTo[Box[ResourceUser]]
  )

  def saveResourceUser(resourceUser: ResourceUser) : Box[ResourceUser] = getValueFromFuture(
    (actor ? cc.saveResourceUser(resourceUser)).mapTo[Box[ResourceUser]]
  )

  def deleteResourceUser(userId: Long) : Box[Boolean] = getValueFromFuture(
    (actor ? cc.deleteResourceUser(userId)).mapTo[Box[Boolean]]
  )
  
  def scrambleDataOfResourceUser(userPrimaryKey: UserPrimaryKey) : Box[Boolean] = getValueFromFuture(
    (actor ? cc.scrambleDataOfResourceUser(userPrimaryKey)).mapTo[Box[Boolean]]
  )

  def bulkDeleteAllResourceUsers(): Box[Boolean] = getValueFromFuture(
    (actor ? cc.bulkDeleteAllResourceUsers()).mapTo[Box[Boolean]]
  )


}
