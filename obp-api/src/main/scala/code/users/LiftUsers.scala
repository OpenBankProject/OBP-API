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
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers

import scala.collection.immutable
import scala.collection.immutable.List
import scala.concurrent.Future

object LiftUsers extends Users with MdcLoggable{

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

      rows.map { r =>
        (r, entitlementsByUserId.getOrElse(r.userId, Nil), agreementsByUserId.getOrElse(r.userId, Nil))
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
                                  lastMarketingAgreementSignedDate: Option[Date],
                                  isNaturalPerson: Option[Boolean] = Some(true),
                                  principalUserId: Option[String] = None): Box[ResourceUser] = {
    Full(ResourceUser.insert(
      buildResourceUser(provider, providerId, name, email, userId).copy(
        createdByConsentId = createdByConsentId,
        createdByUserInvitationId = createdByUserInvitationId,
        company = company.getOrElse(""),
        lastMarketingAgreementSignedDate = lastMarketingAgreementSignedDate,
        isNaturalPerson = isNaturalPerson.getOrElse(true),
        principalUserIdOption = principalUserId)))
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
