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

import code.api.util.Consent.logger

import java.util.Date
import code.api.util._
import code.entitlement.{Entitlement, MappedEntitlement}
import code.bankconnectors.DoobieBadLoginAttemptQueries
import code.loginattempts.LoginAttempt.maxBadLoginAttempts
import code.model.dataAccess.{AuthUser, ResourceUser, UserQuery}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{User, UserPrimaryKey}
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.util.Helpers

import scala.collection.immutable
import scala.collection.immutable.List
import scala.concurrent.Future

object LiftUsers extends Users with MdcLoggable{

  // ---- on-behalf-of resolution (ON_BEHALF_OF_USER_ID_PLAN.md, Phase 1) ----------------------

  /** This holds the outcome of one resolution: the person the caller acts for, the Consent that
   *  said so, and whether the answer is settled enough to be worth caching. */
  private case class Resolved(onBehalfOfUserId: Box[String], consentId: Option[String], cacheable: Boolean)

  /** This cache holds settled answers only. Once a Consent names its person that binding never
   *  changes, so it is safe to keep. The answer "this consent has no person yet", which is what a
   *  Berlin Group or UK consent gives before it is authorised, must never be cached: the consent
   *  may be authorised a moment later, and a cached answer would keep every write on the agent
   *  until the entry expired. */
  private lazy val onBehalfOfCacheTtlSeconds: Long =
    APIUtil.getPropsAsLongValue("on_behalf_of_user_id.cache_ttl_seconds", 600L)
  private lazy val onBehalfOfCache: com.google.common.cache.Cache[String, Resolved] = code.telemetry.Telemetry.monitorCache(
    com.google.common.cache.CacheBuilder.newBuilder()
      .expireAfterWrite(onBehalfOfCacheTtlSeconds, java.util.concurrent.TimeUnit.SECONDS)
      .maximumSize(100000)
      .recordStats()
      .build[String, Resolved](), "on_behalf_of")

  private def nonBlank(s: String): Boolean = s != null && s.nonEmpty

  /**
   * This walks from the authenticated caller to the person it acts for. It is exactly one hop:
   *
   *   ResourceUser(userId).isConsentUser  ->  CreatedByConsentId  ->  Consent  ->  consent.userId
   *
   * and the user that lands on must itself be an original user. There is no loop and no second
   * hop by design: a consent user cannot create a Consent (UserReference.Consent_UserId is
   * policy Reject), so a well-formed chain is always one step deep. That is what makes the
   * result checkable — see ON_BEHALF_OF_USER_ID_PLAN.md, Decision 4.
   *
   * Every branch below FAILS CLOSED, i.e. keeps the caller, except one. Keeping the caller means
   * the row is stored against the agent identity and strands when the consent dies — bad, but
   * local and visible in the WARN. Guessing a human instead would silently attribute writes to
   * someone who never authorised them, which is worse. The single exception is a consent naming
   * another consent user: that breaks the one-hop invariant outright, so it returns a Failure
   * rather than a fallback, because there is no answer that is even arguably right.
   *
   * Takes only the id on purpose (see the trait): nothing request-asserted can steer it.
   */
  private def resolveOnBehalfOf(userId: String): Resolved = {
    // An empty caller id is not an error here — anonymous and system paths reach writers too.
    // Hand it straight back so callers get "" rather than a Failure they would have to unpick.
    if (!nonBlank(userId)) return Resolved(Full(userId), None, cacheable = false)
    // Every UseOnBehalfOfUserId write costs this lookup, so consent callers would otherwise pay
    // two extra reads per row written. Only stable answers were put here — see the cache above.
    val cached = if (onBehalfOfCacheTtlSeconds > 0) Option(onBehalfOfCache.getIfPresent(userId)) else None
    if (cached.isDefined) return cached.get
    // findByUserId, not Mapper's find(By(...)): ResourceUser is a Doobie store on this branch.
    // createdByConsentId is an Option here rather than a MappedString, so the consent id comes
    // out of the Option instead of off a field accessor -- isConsentUser is exactly
    // `createdByConsentId.nonEmpty`, so the guard above already proves it is a Some.
    val resolved: Resolved = ResourceUser.findByUserId(userId) match {
      case Full(ru) if ru.isConsentUser =>
        val consentId = ru.createdByConsentId.getOrElse("")
        code.consent.Consents.consentProvider.vend.getConsentByConsentId(consentId) match {
          case Full(consent) if nonBlank(consent.userId) =>
            ResourceUser.findByUserId(consent.userId) match {
              case Full(target) if target.isOriginalUser =>
                Resolved(Full(consent.userId), Some(consentId), cacheable = true)
              case Full(_) =>
                logger.warn(s"resolveOnBehalfOfUserId: consent user $userId's consent $consentId names ${consent.userId}, which is itself a consent user — invariant broken, refusing")
                Resolved(Failure(s"${ErrorMessages.InvalidUserId} consent $consentId names a consent user as its on-behalf-of user"), Some(consentId), cacheable = false)
              case _ =>
                logger.warn(s"resolveOnBehalfOfUserId: consent user $userId's consent $consentId names unknown user ${consent.userId}; keeping $userId (fails closed)")
                Resolved(Full(userId), Some(consentId), cacheable = false)
            }
          case Full(_) =>
            logger.warn(s"resolveOnBehalfOfUserId: consent user $userId's consent $consentId has no human yet (not authorised); keeping $userId (fails closed, not cached)")
            Resolved(Full(userId), Some(consentId), cacheable = false)
          case _ =>
            logger.warn(s"resolveOnBehalfOfUserId: consent user $userId names consent $consentId, which does not exist; keeping $userId (fails closed)")
            Resolved(Full(userId), Some(consentId), cacheable = false)
        }
      // An ordinary user: acts for itself. Cacheable because a user that is not consent-minted
      // can never become one — CreatedByConsentId is written at creation and never updated.
      case Full(_) => Resolved(Full(userId), None, cacheable = true)
      case _ =>
        logger.warn(s"resolveOnBehalfOfUserId: no ResourceUser $userId; keeping it (fails closed)")
        Resolved(Full(userId), None, cacheable = false)
    }
    // Only the two settled answers are stored: "ordinary user, acts for itself" and "consent user,
    // bound to this human". Everything else is a transient or broken state that can legitimately
    // change within the TTL — most importantly a BG/UK consent authorised a moment from now, which
    // must not stay pinned to the agent for the next ten minutes.
    if (resolved.cacheable && onBehalfOfCacheTtlSeconds > 0) onBehalfOfCache.put(userId, resolved)
    resolved
  }

  override def resolveOnBehalfOfUserId(userId: String): Box[String] = resolveOnBehalfOf(userId).onBehalfOfUserId

  /**
   * The one entry point a provider calls before writing a user id into a column.
   *
   * `ref` says WHICH COLUMN is about to be written. Each UserReference value names a Mapper class
   * and one or more of its fields, and carries the policy chosen for them — UserReference.Bank_CreatedByUserId
   * names MappedBank.CreatedByUserId, and applies to that column only.
   *
   * It has to be a parameter rather than something derived from the caller, because the right id
   * depends on the column and not just on who is calling. MappedEntitlement.mUserId is the case
   * that proves it: the same consent user writing that one column gets a different answer
   * depending on which process is writing. Entitlement_UserId (UseOnBehalfOfUserId) is a role being
   * granted to somebody, so it lands on the human; Entitlement_UserId_ConsentScope (UseAuthenticatedUserId) is the
   * consent engine copying the Consent's own scope onto the agent, so it must stay on the agent.
   * Same column, opposite policies.
   *
   * Which policy applies to which column is recorded in UserReference.scala and nowhere else;
   * this method only applies it.
   *
   * ---- Callers, as worked examples (2026-09-15) ----
   *
   * A. Single-column writers. Want one id, so they use the `attributedUserId` convenience and
   *    fall back to the caller, so that a Failure can never blank the column.
   *
   *      caller                                         reference passed
   *      ---------------------------------------------  --------------------
   *      MappedUserCustomerLink.linkOwnerUserId         UserCustomerLink_UserId
   *      MapperAccountHolders.getOrCreateAccountHolder  AccountHolders_User
   *      LocalMappedConnector.bankCreatorUserId         Bank_CreatedByUserId
   *
   * B. Record-both tables. Call attributionOf directly, because they need both ids out of the
   *    one Attribution rather than just the single value to store.
   *
   *      caller                            reference           columns written
   *      --------------------------------  ------------------  -----------------------------
   *      MappedTransactionRequestProvider  TransactionRequest  mUserId + mOnBehalfOfUserId
   *      MapperCounterparties              Counterparty        mCreatedByUserId + mCreatedByOnBehalfOfUserId
   *      MapppedDynamicDataProvider        DynamicData_Created CreatedByUserId + CreatedByOnBehalfOfUserId
   *      MapppedDynamicDataProvider        DynamicData_Updated UpdatedByUserId + UpdatedByOnBehalfOfUserId
   *
   * C. One column, two policies. The reference is chosen per process, then passed in.
   *
   *      caller                             reference               chosen when
   *      ---------------------------------  ----------------------  ----------------------------
   *      MappedEntitlements.addEntitlement  Entitlement_UserId_ConsentScope  createdByProcess ==
   *                                                                 Constant.consent_user
   *      MappedEntitlements.addEntitlement  Entitlement_UserId       otherwise
   *
   * D. Reads. These resolve too, and must, or an agent cannot see back what it just wrote:
   *    personal rows are keyed by the same column on both sides, so the redirect has to be
   *    symmetric. The last two are endpoint-level rather than provider-level — where a handler
   *    decides WHOSE rows to read, it has to ask the same question the provider asks on write.
   *
   *      caller                                reference             covers
   *      ------------------------------------  --------------------  ----------------------
   *      MapppedDynamicDataProvider            DynamicData_UserId     save/update/get/delete
   *      MapppedDynamicEntityProvider          DynamicEntity_UserId   definition creator
   *      Http4sDynamicEntity.personalRowOwner  DynamicData_UserId     projection read path
   *      Http4s700.linkedCustomerOwnerId       UserCustomerLink_UserId v7 "my customers"
   *
   * This is also the audit point. A delegated write logs here and nowhere else, which is why
   * providers should call it even when they already know the on-behalf-of user from the request
   * layer (see LocalMappedConnector.bankCreatorUserId) — and why naming the reference in main is
   * what OnBehalfOfOwnershipSweepTest's ratchet counts as "wired".
   */
  override def attributionOf(userId: String, ref: UserReference): Box[Attribution] = ref.policy match {
    // Audit and authorisation-materialisation columns: the caller's own id IS the truthful value,
    // so the resolver is not consulted at all. Deliberate — it also keeps these writes free of the
    // two extra reads resolution costs on a cache miss.
    case AttributionPolicy.UseAuthenticatedUserId =>
      Full(Attribution(userId, userId, None, ref))
    // Ownership columns: store the human. Note the WARN fires only when the answer actually
    // differs from the caller, so ordinary traffic stays quiet and every line in the log is a
    // real delegated write, naming the reference and the column it landed in.
    case AttributionPolicy.UseOnBehalfOfUserId =>
      val resolved = resolveOnBehalfOf(userId)
      resolved.onBehalfOfUserId.map { onBehalfOfUserId =>
        val attribution = Attribution(userId, onBehalfOfUserId, resolved.consentId, ref)
        if (attribution.isDelegated)
          logger.warn(s"attribution ${ref.name}: user $userId is a consent user (consent ${resolved.consentId.getOrElse("?")}); writing on-behalf-of user $onBehalfOfUserId to ${ref.mapperClass}.${ref.fields.mkString("/")}")
        attribution
      }
    // Things an agent must not do at all, whoever it acts for: minting a Consent (nested
    // delegation) or an OAuth consumer/token (credentials that outlive the consent). There is no
    // redirect that would make these safe — a Consent created "for" the human would be one the
    // human never granted — so this returns a Failure and the endpoint turns it into a 400.
    // NOTE: as of 2026-09-15 nothing calls attributionOf with a Reject reference, so the refusal
    // is pinned by AgentDelegationTest but not yet reachable over HTTP. Wiring the consent-create
    // path is tracked in ON_BEHALF_OF_USER_ID_PLAN.md, Phase 2/3.
    case AttributionPolicy.Reject =>
      val resolved = resolveOnBehalfOf(userId)
      resolved.onBehalfOfUserId.flatMap { onBehalfOfUserId =>
        if (onBehalfOfUserId == userId) Full(Attribution(userId, onBehalfOfUserId, resolved.consentId, ref))
        else {
          logger.warn(s"attribution ${ref.name}: user $userId is a consent user (on behalf of $onBehalfOfUserId); a consent user must not write ${ref.mapperClass}.${ref.fields.mkString("/")} — rejected")
          Failure(s"${ErrorMessages.InvalidUserId} ${ref.name}: user $userId is a consent user; this action must be performed by the user it acts for ($onBehalfOfUserId)")
        }
      }
  }


  //UserId here is the resourceuser.id field
  def getUserByResourceUserId(id : Long) : Box[User] = {
    ResourceUser.findByPrimaryKey(id) ?~ { s"user $id not found"}
  }

  //UserId here is the resourceuser.id field
  def getResourceUserByResourceUserId(id : Long) : Box[ResourceUser] = {
    ResourceUser.findByPrimaryKey(id) ?~ { s"user $id not found"}
  }

  def getResourceUserByResourceUserIdF(id : Long) : Box[User] = {
    ResourceUser.findByPrimaryKey(id) ?~ { s"user $id not found"}
  }

  def getResourceUserByResourceUserIdFuture(id : Long) : Future[Box[User]] = {
    Future{getResourceUserByResourceUserIdF(id)}
  }

  def getUserByProviderId(provider : String, idGivenByProvider : String) : Box[User] = {
    // Note: providerId is generally human readable like a username. it is not a uuid like user_id.
    ResourceUser.findByProviderAndProviderId(provider, idGivenByProvider)
  }
  def getUserByProviderIdFuture(provider : String, idGivenByProvider : String) : Future[Box[User]] = {
    Future {
      getUserByProviderId(provider, idGivenByProvider)
    }
  }

  def getOrCreateUserByProviderId(provider : String, idGivenByProvider : String, consentId: Option[String], name: Option[String], email: Option[String]) : (Box[User], Boolean) = {
    val existingUser = Users.users.vend.getUserByProviderId(provider = provider, idGivenByProvider = idGivenByProvider)
    existingUser match {
      case Full(_) =>
        (existingUser, false)
      case _ =>
        scala.util.Try(Users.users.vend.createResourceUser(
          provider = provider,
          providerId = Some(idGivenByProvider),
          createdByConsentId = consentId,
          name = name,
          email = email,
          userId = None,
          createdByUserInvitationId = None,
          company = None,
          lastMarketingAgreementSignedDate = None
        )) match {
          case scala.util.Success(box) => (box, true)
          case scala.util.Failure(_) =>
            (Users.users.vend.getUserByProviderId(provider, idGivenByProvider), false)
        }
    }
  }
  def getOrCreateUserByProviderIdFuture(provider : String, idGivenByProvider : String, consentId: Option[String], name: Option[String], email: Option[String]) : Future[(Box[User], Boolean)] = {
    Future {
      val result = getOrCreateUserByProviderId(provider, idGivenByProvider, consentId, name, email)
      logger.debug(s"getOrCreateUserByProviderId.result ($result)")
      result
    }
  }

  def getUserByUserId(userId : String) : Box[User] = {
    ResourceUser.findByUserId(userId)
  }

   def getUserByUserIdFuture(userId : String) : Future[Box[User]] = {
    Future {
      getUserByUserId(userId)
    }
  }

  def getUsersByUserIds(userIds : List[String]) : List[User] = {
    ResourceUser.findAllByUserIds(userIds)
  }

  def getUsersByUserIdsFuture(userIds : List[String]) : Future[List[User]] = {
    Future(getUsersByUserIds(userIds))
  }

  override def getUserByProviderAndUsername(provider : String, userName: String): Box[User] = {
    ResourceUser.findByProviderAndName(provider, userName)
  }

  override def getUserByProviderAndUsernameFuture(provider: String, username: String): Future[Box[User]] = {
    Future {
      getUserByProviderAndUsername(provider, username)
    }
  }

  override def getUsersByUsername(userName: String): List[User] = {
    ResourceUser.findAllByName(userName)
  }

  override def getUserByEmail(email: String): Box[List[ResourceUser]] = {
    Full(ResourceUser.findAllByEmail(email))
  }

  def getUserByEmailF(email: String): List[(ResourceUser, Box[List[Entitlement]])] = {
    val users = ResourceUser.findAllByEmail(email)
    for {
      user <- users
    } yield {
      (user, Entitlement.entitlement.vend.getEntitlementsByUserId(user.userId).map(_.sortWith(_.roleName < _.roleName)))
    }
  }
  
  override def getUsersByEmail(email: String): Future[List[(ResourceUser, Box[List[Entitlement]], Option[List[UserAgreement]])]] = Future {
    val users = ResourceUser.findAllByEmail(email)
    for {
      user <- users
    } yield {
      val entitlements = Entitlement.entitlement.vend.getEntitlementsByUserId(user.userId).map(_.sortWith(_.roleName < _.roleName))
      // val agreements = getUserAgreements(user)
      (user, entitlements, None)
    }
  }

  private def getUserAgreements(user: ResourceUser) = {
    val acceptMarketingInfo = UserAgreementProvider.userAgreementProvider.vend.getLastUserAgreement(user.userId, "accept_marketing_info")
    val termsAndConditions = UserAgreementProvider.userAgreementProvider.vend.getLastUserAgreement(user.userId, "terms_and_conditions")
    val privacyConditions = UserAgreementProvider.userAgreementProvider.vend.getLastUserAgreement(user.userId, "privacy_conditions")
    val agreements = acceptMarketingInfo.toList ::: termsAndConditions.toList ::: privacyConditions.toList
    agreements
  }

  override def getUserByEmailFuture(email: String): Future[List[(ResourceUser, Box[List[Entitlement]])]] = {
    Future {
      getUserByEmailF(email)
    }
  }

  override def getAllUsers(): Box[List[ResourceUser]] = {
    Full(ResourceUser.findAll())
  }

  override def getAllUsersF(queryParams: List[OBPQueryParam]): Future[List[(ResourceUser, Box[List[Entitlement]])]] = {
    Future {
      for {
        user <- getUsersCommon(queryParams)
      } yield {
        (user, Entitlement.entitlement.vend.getEntitlementsByUserId(user.userId).map(_.sortWith(_.roleName < _.roleName)))
      }
    }
  }


  private def getUsersCommon(queryParams: List[OBPQueryParam]) = {
    val limit = queryParams.collect { case OBPLimit(value) => value }.headOption
    val offset: Option[Int] = queryParams.collect { case OBPOffset(value) => value }.headOption
    val locked: Option[String] = queryParams.collect { case OBPLockedStatus(value) => value }.headOption
    // No ?is_deleted means is_deleted = false rather than "no filter", as it always has.
    val deleted: Option[Boolean] = queryParams.collect { case OBPIsDeleted(value) => value }.headOption

    // Users a consent minted for itself are not people and do not belong in a list of people: they
    // have no username and no email, there is one of them for every consent ever granted, and they
    // outnumber real users by orders of magnitude on any busy instance. They stay reachable by id
    // and through the account-access data; they just do not pad out this list. That predicate lives
    // in ResourceUser.findAll(UserQuery) now, applied in SQL rather than after the fact so it
    // composes with the limit/offset above: a filter applied to an already-paginated result returns
    // short pages, which is exactly the defect the ?locked= path below has.
    //
    // The v6.0.0 search path applies the same predicate -- see DoobieUserQueries.getUsers.
    def getAllResourceUsers(): List[ResourceUser] =
      ResourceUser.findAll(UserQuery(limit = limit, offset = offset, isDeleted = deleted))

    val showUsers: List[ResourceUser] = locked.map(_.toLowerCase()) match {
      case Some("active") =>
        val lockedUsernames: List[String] = DoobieBadLoginAttemptQueries.usernamesOverThreshold(maxBadLoginAttempts.toInt)
        val exclude: immutable.Seq[ResourceUser] = ResourceUser.findAllByNames(lockedUsernames)
        getAllResourceUsers() diff exclude
      case Some("locked") =>
        val lockedUsernames: List[String] = DoobieBadLoginAttemptQueries.usernamesOverThreshold(maxBadLoginAttempts.toInt)
        val exclude: immutable.Seq[ResourceUser] = ResourceUser.findAllByNames(lockedUsernames)
        getAllResourceUsers() intersect exclude.toList
      case _ =>
        getAllResourceUsers()
    }
    showUsers
  }

  override def getUsers(queryParams: List[OBPQueryParam]): Future[List[(ResourceUser, Box[List[Entitlement]], Option[List[UserAgreement]])]] = {
    Future {
      val roleName: Option[String] = queryParams.collect { case OBPRoleName(value) => value }.headOption
      val bankId: Option[String] = queryParams.collect { case OBPBankId(value) => value }.headOption
      val roleUserIds: Option[Set[String]] = roleName.map { rn =>
        val entitlements = Entitlement.entitlement.vend.getEntitlementsByRole(rn)
          .getOrElse(Nil)
        val filtered = bankId match {
          case Some(bid) => entitlements.filter(_.bankId == bid)
          case None => entitlements
        }
        filtered.map(_.userId).toSet
      }
      for {
        user <- getUsersCommon(queryParams)
        if roleUserIds.forall(_.contains(user.userId))
      } yield {
        val entitlements = Entitlement.entitlement.vend.getEntitlementsByUserId(user.userId).map(_.sortWith(_.roleName < _.roleName))
        (user, entitlements, None)
      }
    }
  }

  override def getUsersV600F(queryParams: List[OBPQueryParam])
    : Future[List[(DoobieUserQueries.UserSearchRow, List[Entitlement], List[UserAgreement])]] = Future {

    val provider:   Option[String]  = queryParams.collectFirst { case OBPProvider(v) => v }
    val username:   Option[String]  = queryParams.collectFirst { case OBPUsername(v) => v }
    val email:      Option[String]  = queryParams.collectFirst { case OBPEmail(v) => v }
    val userId:     Option[String]  = queryParams.collectFirst { case OBPUserId(v) => v }
    val isDeleted:  Option[Boolean] = queryParams.collectFirst { case OBPIsDeleted(v) => v }
    val lockedStat: Option[String]  = queryParams.collectFirst { case OBPLockedStatus(v) => v }
    val roleName:   Option[String]  = queryParams.collectFirst { case OBPRoleName(v) => v }
    val bankId:     Option[String]  = queryParams.collectFirst { case OBPBankId(v) => v }
    val ordering:   Option[OBPOrdering] = queryParams.collectFirst { case o: OBPOrdering => o }
    val sortBy:     Option[String]  = ordering.flatMap(_.field)
    // When no sort_by is supplied we fall back to `ru.id ASC` for stable pagination.
    // When sort_by IS supplied we honour sort_direction, which defaults to DESC per OBP convention.
    val sortAsc:    Boolean         =
      if (sortBy.isEmpty) true
      else ordering.exists(_.order == OBPAscending)
    val limit:  Int = queryParams.collectFirst { case OBPLimit(v) => v }.getOrElse(100)
    val offset: Int = queryParams.collectFirst { case OBPOffset(v) => v }.getOrElse(0)

    logger.info(
      s"getUsersV600F says: filters provider=$provider username=$username email=$email userId=$userId " +
      s"isDeleted=$isDeleted lockedStatus=$lockedStat roleName=$roleName bankId=$bankId " +
      s"sortBy=$sortBy sortAsc=$sortAsc limit=$limit offset=$offset"
    )

    val started = System.currentTimeMillis()
    val rows = DoobieUserQueries.getUsers(provider, username, email, userId, isDeleted, lockedStat, roleName, bankId, sortBy, sortAsc, limit, offset)
    logger.info(s"getUsersV600F says: DoobieUserQueries.getUsers returned ${rows.size} row(s) in ${System.currentTimeMillis() - started}ms")

    if (rows.isEmpty) Nil
    else {
      val userIds = rows.map(_.userId)

      // Batch-fetch entitlements for all returned users (single IN query).
      val entitlementsByUserId: Map[String, List[Entitlement]] =
        MappedEntitlement.findAllByUserIds(userIds)
          .groupBy(_.userId)
          .map { case (uid, ents) => uid -> ents.sortBy(_.roleName).toList }

      // Batch-fetch agreements, then reduce to most-recent per (userId, agreementType).
      val agreementsByUserId: Map[String, List[UserAgreement]] =
        UserAgreement.findAllByUserIds(userIds)
          .groupBy(_.userId)
          .map { case (uid, all) =>
            uid -> all.groupBy(_.agreementType)
              .values
              .flatMap(_.sortBy(_.date)(Ordering[Date].reverse).headOption)
              .toList
          }

      val totalEntitlements = entitlementsByUserId.values.map(_.size).sum
      val totalAgreements = agreementsByUserId.values.map(_.size).sum
      logger.info(
        s"getUsersV600F says: batched $totalEntitlements entitlement(s) and $totalAgreements agreement(s) across ${userIds.size} user(s)"
      )

      rows.map { user =>
        (user, entitlementsByUserId.getOrElse(user.userId, Nil), agreementsByUserId.getOrElse(user.userId, Nil))
      }
    }
  }



  override def createResourceUser(provider: String,
                                  providerId: Option[String],
                                  createdByConsentId: Option[String],
                                  name: Option[String],
                                  email: Option[String],
                                  userId: Option[String],
                                  createdByUserInvitationId: Option[String],
                                  company: Option[String],
                                  lastMarketingAgreementSignedDate: Option[Date]): Box[ResourceUser] = {
    Full(ResourceUser.insert(
      buildResourceUser(provider, providerId, name, email, userId).copy(
        createdByConsentId = createdByConsentId,
        createdByUserInvitationId = createdByUserInvitationId,
        company = company.getOrElse(""),
        lastMarketingAgreementSignedDate = lastMarketingAgreementSignedDate)))
  }

  override def createUnsavedResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String], userId: Option[String]): Box[ResourceUser] = {
    Full(buildResourceUser(provider, providerId, name, email, userId))
  }

  /**
   * The five fields both create paths share.
   *
   * providerId falls back to the name because the entity's default for that column was `name_.get`,
   * evaluated lazily at save time - so a caller that supplied a name but no provider id got the
   * name written into providerid. Anything the caller leaves out keeps the row's own default.
   */
  private def buildResourceUser(provider: String,
                                providerId: Option[String],
                                name: Option[String],
                                email: Option[String],
                                userId: Option[String]): ResourceUser = {
    val defaults = ResourceUser.defaults
    val theName = name.getOrElse(defaults.name)
    defaults.copy(
      provider = provider,
      name = theName,
      idGivenByProvider = providerId.getOrElse(theName),
      // MappedEmail lowercased and trimmed on every set.
      emailAddress = email.map(ResourceUser.normalizeEmail).getOrElse(defaults.emailAddress),
      userId = userId.getOrElse(defaults.userId))
  }

  override def saveResourceUser(ru: ResourceUser): Box[ResourceUser] = {
    // saveMe() inserted a transient row and updated a persisted one; id == 0 is what tells them
    // apart, exactly as Mapper's saved_? did.
    Full(if (ru.id == 0L) ResourceUser.insert(ru) else ResourceUser.update(ru))
  }

  override def bulkDeleteAllResourceUsers(): Box[Boolean] = {
    ResourceUser.deleteAll()
    Full(true)
  }

  override def deleteResourceUser(userId: Long): Box[Boolean] = {
    for {
      u <- ResourceUser.findByPrimaryKey(userId)
    } yield {
      ResourceUser.delete(u.id)
    }
  }
  override def scrambleDataOfResourceUser(userPrimaryKey: UserPrimaryKey): Box[Boolean] = {
    for {
      u <- ResourceUser.findByPrimaryKey(userPrimaryKey.value)
    } yield {
      // A user who never had an AuthUser has no login to keep working, so their username, email and
      // provider id are scrambled too; one who does keeps them, and only the company is scrambled.
      val scrambled = AuthUser.findByResourceUserPrimaryKey(userPrimaryKey.value) match {
        case Empty =>
          u.copy(
            company = Helpers.randomString(16),
            isDeleted = Some(true),
            name = "DELETED-" + Helpers.randomString(16),
            emailAddress = ResourceUser.normalizeEmail(Helpers.randomString(10) + "@example.com"),
            idGivenByProvider = Helpers.randomString(16))
        case _ =>
          u.copy(company = Helpers.randomString(16), isDeleted = Some(true))
      }
      ResourceUser.update(scrambled)
      true
    }
  }
  
}
