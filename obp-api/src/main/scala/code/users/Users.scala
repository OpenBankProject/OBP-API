package code.users

import java.util.Date

import code.api.util.{APIUtil, OBPQueryParam}
import code.entitlement.Entitlement
import code.model.dataAccess.ResourceUser
import code.remotedata.RemotedataUsers
import com.openbankproject.commons.model.{User, UserPrimaryKey}
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.collection.immutable.List
import scala.concurrent.Future

object Users  extends SimpleInjector {

  val users = new Inject(buildOne _) {}

  def buildOne: Users =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
      case false  => LiftUsers
      case true => RemotedataUsers     // We will use Akka as a middleware
    }
  
}

trait Users {
  //resourceuser has two ids: id(Long)and userid_(String), this method use id(Long)
  def getUserByResourceUserId(id : Long) : Box[User]

  //resourceuser has two ids: id(Long)and userid_(String), this method use id(Long)
  def getResourceUserByResourceUserId(id : Long) : Box[ResourceUser]
  def getResourceUserByResourceUserIdFuture(id : Long) : Future[Box[User]]

  def getUserByProviderId(provider : String, idGivenByProvider : String) : Box[User]
  def getUserByProviderIdFuture(provider : String, idGivenByProvider : String) : Future[Box[User]]
  def getOrCreateUserByProviderIdFuture(provider : String, idGivenByProvider : String, consentId: Option[String], name: Option[String], email: Option[String]) : Future[(Box[User], Boolean)]

  //resourceuser has two ids: id(Long)and userid_(String), this method use userid_(String)
  def getUserByUserId(userId : String) : Box[User]
  def getUserByUserIdFuture(userId : String) : Future[Box[User]]
  def getUsersByUserIdsFuture(userIds : List[String]) : Future[List[User]]

  // find ResourceUser by Resourceuser username 
  def getUserByProviderAndUsername(provider: String, userName: String) : Box[User]
  def getUserByProviderAndUsernameFuture(provider: String, username: String): Future[Box[User]]

  def getUserByEmail(email: String) : Box[List[ResourceUser]]
  def getUserByEmailFuture(email: String) : Future[List[(ResourceUser, Box[List[Entitlement]])]]
  def getUsersByEmail(email: String) : Future[List[(ResourceUser, Box[List[Entitlement]], Option[List[UserAgreement]])]]

  def getAllUsers() : Box[List[ResourceUser]]

  def getAllUsersF(queryParams: List[OBPQueryParam]) : Future[List[(ResourceUser, Box[List[Entitlement]])]]

  def getUsers(queryParams: List[OBPQueryParam]): Future[List[(ResourceUser, Box[List[Entitlement]], Option[List[UserAgreement]])]]

  def createResourceUser(provider: String, 
                         providerId: Option[String], 
                         createdByConsentId: Option[String], 
                         name: Option[String], 
                         email: Option[String], 
                         userId: Option[String], 
                         createdByUserInvitationId: Option[String], 
                         company: Option[String],
                         lastMarketingAgreementSignedDate: Option[Date]) : Box[ResourceUser]

  def createUnsavedResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String], userId: Option[String]) : Box[ResourceUser]

  def saveResourceUser(resourceUser: ResourceUser) : Box[ResourceUser]

  def deleteResourceUser(userId: Long) : Box[Boolean]
  
  def scrambleDataOfResourceUser(userPrimaryKey: UserPrimaryKey) : Box[Boolean]

  def bulkDeleteAllResourceUsers() : Box[Boolean]
}

class RemotedataUsersCaseClasses {
  case class getUserByResourceUserId(id : Long)
  case class getResourceUserByResourceUserId(id : Long)
  case class getResourceUserByResourceUserIdFuture(id : Long)
  case class getUserByProviderId(provider : String, idGivenByProvider : String)
  case class getUserByProviderIdFuture(provider : String, idGivenByProvider : String)
  case class getOrCreateUserByProviderIdFuture(provider : String, idGivenByProvider : String, createdByConsentId: Option[String],name: Option[String], email: Option[String])
  case class getUserByUserId(userId : String)
  case class getUserByUserIdFuture(userId : String)
  case class getUsersByUserIdsFuture(userId : List[String])
  case class getUserByUserName(provider : String, userName : String)
  case class getUserByUserNameFuture(provider : String, userName : String)
  case class getUserByEmail(email : String)
  case class getUserByEmailFuture(email : String)
  case class getUsersByEmail(email : String)
  case class getAllUsers()
  case class getAllUsersF(queryParams: List[OBPQueryParam])
  case class getUsers(queryParams: List[OBPQueryParam])
  case class createResourceUser(provider: String, providerId: Option[String],createdByConsentId: Option[String], name: Option[String], email: Option[String], userId: Option[String], createdByUserInvitationId: Option[String], company: Option[String], lastMarketingAgreementSignedDate: Option[Date])
  case class createUnsavedResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String], userId: Option[String])
  case class saveResourceUser(resourceUser: ResourceUser)
  case class deleteResourceUser(userId: Long)
  case class scrambleDataOfResourceUser(userPrimaryKey: UserPrimaryKey)
  case class bulkDeleteAllResourceUsers()
}

object RemotedataUsersCaseClasses extends RemotedataUsersCaseClasses
