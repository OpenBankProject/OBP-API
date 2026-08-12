package code.users

import java.util.Date

import code.api.util.{APIUtil, OBPQueryParam}
import code.entitlement.Entitlement
import code.model.dataAccess.ResourceUser
import com.openbankproject.commons.model.{User, UserPrimaryKey}
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.collection.immutable.List
import scala.concurrent.Future

object Users  extends SimpleInjector {

  val users = new Inject(buildOne _) {}

  def buildOne: Users = LiftUsers 
  
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
  // The synchronous form of the above, for callers already inside a Box for-comprehension. Carries
  // the same duplicate-key recovery: two concurrent first requests both find nothing and both
  // insert, so the loser re-reads instead of failing. Second element is true when the user was
  // created by this call.
  def getOrCreateUserByProviderId(provider : String, idGivenByProvider : String, consentId: Option[String], name: Option[String], email: Option[String]) : (Box[User], Boolean)

  //resourceuser has two ids: id(Long)and userid_(String), this method use userid_(String)
  def getUserByUserId(userId : String) : Box[User]
  def getUserByUserIdFuture(userId : String) : Future[Box[User]]
  def getUsersByUserIdsFuture(userIds : List[String]) : Future[List[User]]

  // find ResourceUser by Resourceuser username
  def getUserByProviderAndUsername(provider: String, userName: String) : Box[User]
  def getUserByProviderAndUsernameFuture(provider: String, username: String): Future[Box[User]]

  // Every user answering to this username, whichever provider they came from. Username is only
  // unique per provider, so this can return more than one; callers that need a single user must say
  // what they do with an ambiguous answer. Added for Berlin Group PSU-ID resolution, where the
  // header names a username and the PSU may be federated rather than local.
  def getUsersByUsername(userName: String) : List[User]

  def getUserByEmail(email: String) : Box[List[ResourceUser]]
  def getUserByEmailFuture(email: String) : Future[List[(ResourceUser, Box[List[Entitlement]])]]
  def getUsersByEmail(email: String) : Future[List[(ResourceUser, Box[List[Entitlement]], Option[List[UserAgreement]])]]

  def getAllUsers() : Box[List[ResourceUser]]

  def getAllUsersF(queryParams: List[OBPQueryParam]) : Future[List[(ResourceUser, Box[List[Entitlement]])]]

  def getUsers(queryParams: List[OBPQueryParam]): Future[List[(ResourceUser, Box[List[Entitlement]], Option[List[UserAgreement]])]]

  /**
   * Get users via a Doobie-based SQL JOIN across resourceuser, authuser and
   * mappedbadloginattempt. Returns pre-joined rows plus the user's entitlements
   * and most-recent-per-type agreements, fetched in batch.
   *
   * Supported OBPQueryParam filters: OBPProvider, OBPUsername, OBPIsDeleted,
   * OBPLockedStatus, OBPRoleName, OBPBankId, OBPLimit, OBPOffset.
   */
  def getUsersV600F(queryParams: List[OBPQueryParam])
    : Future[List[(DoobieUserQueries.UserSearchRow, List[code.entitlement.Entitlement], List[UserAgreement])]]

  def createResourceUser(provider: String,
                         providerId: Option[String],
                         createdByConsentId: Option[String],
                         name: Option[String],
                         email: Option[String],
                         userId: Option[String],
                         createdByUserInvitationId: Option[String],
                         company: Option[String],
                         lastMarketingAgreementSignedDate: Option[Date],
                         isNaturalPerson: Option[Boolean] = Some(true),
                         principalUserId: Option[String] = None) : Box[ResourceUser]

  def createUnsavedResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String], userId: Option[String]) : Box[ResourceUser]

  def saveResourceUser(resourceUser: ResourceUser) : Box[ResourceUser]

  def deleteResourceUser(userId: Long) : Box[Boolean]
  
  def scrambleDataOfResourceUser(userPrimaryKey: UserPrimaryKey) : Box[Boolean]

  def bulkDeleteAllResourceUsers() : Box[Boolean]
}
