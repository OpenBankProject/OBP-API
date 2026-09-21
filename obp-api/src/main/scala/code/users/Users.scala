/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

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

  val users = new Inject(() => buildOne) {}

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
   * This searches for users with a single SQL join, written in Doobie, across the resourceuser,
   * authuser and mappedbadloginattempt tables. It returns the joined rows, and with each row the
   * user's entitlements and their most recent agreement of each type, all fetched in batches
   * rather than one query per user.
   *
   * It understands these OBPQueryParam filters: OBPProvider, OBPUsername, OBPIsDeleted,
   * OBPLockedStatus, OBPRoleName, OBPBankId, OBPLimit and OBPOffset.
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
                         lastMarketingAgreementSignedDate: Option[Date]) : Box[ResourceUser]

  def createUnsavedResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String], userId: Option[String]) : Box[ResourceUser]

  // ---- on-behalf-of resolution (ON_BEHALF_OF_USER_ID_PLAN.md, Phase 1) ----------------------

  /** This resolves the caller's user id to the user id of the person they are acting for.
   *
   *  When the caller is a consent user, the answer is the user named by its Consent, looked up at
   *  the moment of the call rather than copied when the consent user was made: a Berlin Group or UK
   *  consent does not know its person until the person authorises it. When the caller is an ordinary
   *  user, the answer is the id it was given.
   *
   *  It fails closed, meaning that where it cannot find an answer it keeps the caller's own id and
   *  logs a warning. That covers an unknown user, a consent id pointing at nothing, and a consent
   *  with no person attached yet. There is one case it will not fall back on: a Consent whose user
   *  is itself a consent user breaks the rule that resolution is a single hop, so rather than guess
   *  it warns and returns a Failure.
   *
   *  It takes the id alone, and deliberately so. Nothing the caller asserts in the request — a body,
   *  a header, a query parameter — can influence whom the write is attributed to. */
  def resolveOnBehalfOfUserId(userId: String): Box[String]

  /** This is true when the given user acts only for itself, and may therefore own rows that
   *  outlive any Consent. It is false for an agent acting for somebody else. */
  def actsForSelf(userId: String): Boolean = resolveOnBehalfOfUserId(userId).exists(_ == userId)

  /** This works out whose user id to write into the column that `ref` names, for a call made by
   *  `userId`, and returns both candidate ids in an Attribution.
   *
   *  What it does depends on the reference's policy. Under UseAuthenticatedUserId it returns the
   *  caller as both ids without resolving anything. Under UseOnBehalfOfUserId it resolves the
   *  person the caller acts for, and logs a warning naming the reference whenever the two differ,
   *  so every delegated write leaves a trace. Under Reject it succeeds only if the caller acts for
   *  itself, and otherwise returns a Failure that the endpoint turns into a 400. */
  def attributionOf(userId: String, ref: UserReference): Box[Attribution]

  /** This is a shortcut for code filling a single column: it asks attributionOf the same question
   *  and hands back just the one id to store, rather than the whole Attribution. */
  def attributedUserId(userId: String, ref: UserReference): Box[String] = attributionOf(userId, ref).map(_.userIdToStore)

  def saveResourceUser(resourceUser: ResourceUser) : Box[ResourceUser]

  def deleteResourceUser(userId: Long) : Box[Boolean]
  
  def scrambleDataOfResourceUser(userPrimaryKey: UserPrimaryKey) : Box[Boolean]

  def bulkDeleteAllResourceUsers() : Box[Boolean]
}
