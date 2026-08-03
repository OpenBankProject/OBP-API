package code.users

import code.api.util.Consent.logger

import java.util.Date
import code.api.util._
import code.entitlement.{Entitlement, MappedEntitlement}
import code.loginattempts.LoginAttempt.maxBadLoginAttempts
import code.loginattempts.MappedBadLoginAttempt
import code.model.dataAccess.{AuthUser, ResourceUser}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{User, UserPrimaryKey}
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers

import scala.collection.immutable
import scala.collection.immutable.List
import scala.concurrent.Future

object LiftUsers extends Users with MdcLoggable{

  //UserId here is the resourceuser.id field
  def getUserByResourceUserId(id : Long) : Box[User] = {
    ResourceUser.find(id) ?~ { s"user $id not found"}
  }

  //UserId here is the resourceuser.id field
  def getResourceUserByResourceUserId(id : Long) : Box[ResourceUser] = {
    ResourceUser.find(id) ?~ { s"user $id not found"}
  }

  def getResourceUserByResourceUserIdF(id : Long) : Box[User] = {
    ResourceUser.find(id) ?~ { s"user $id not found"}
  }

  def getResourceUserByResourceUserIdFuture(id : Long) : Future[Box[User]] = {
    Future{getResourceUserByResourceUserIdF(id)}
  }

  def getUserByProviderId(provider : String, idGivenByProvider : String) : Box[User] = {
    // Note: providerId is generally human readable like a username. it is not a uuid like user_id.
    ResourceUser.find(By(ResourceUser.provider_, provider), By(ResourceUser.providerId, idGivenByProvider))
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
    ResourceUser.find(By(ResourceUser.userId_, userId))
  }

   def getUserByUserIdFuture(userId : String) : Future[Box[User]] = {
    Future {
      getUserByUserId(userId)
    }
  }

  def getUsersByUserIds(userIds : List[String]) : List[User] = {
    ResourceUser.findAll(ByList(ResourceUser.userId_, userIds))
  }

  def getUsersByUserIdsFuture(userIds : List[String]) : Future[List[User]] = {
    Future(getUsersByUserIds(userIds))
  }

  override def getUserByProviderAndUsername(provider : String, userName: String): Box[User] = {
    ResourceUser.find(
      By(ResourceUser.provider_, provider),
      By(ResourceUser.name_, userName)
    )
  }

  override def getUserByProviderAndUsernameFuture(provider: String, username: String): Future[Box[User]] = {
    Future {
      getUserByProviderAndUsername(provider, username)
    }
  }

  override def getUserByEmail(email: String): Box[List[ResourceUser]] = {
    Full(ResourceUser.findAll(By(ResourceUser.email, email)))
  }

  def getUserByEmailF(email: String): List[(ResourceUser, Box[List[Entitlement]])] = {
    val users = ResourceUser.findAll(By(ResourceUser.email, email))
    for {
      user <- users
    } yield {
      (user, Entitlement.entitlement.vend.getEntitlementsByUserId(user.userId).map(_.sortWith(_.roleName < _.roleName)))
    }
  }
  
  override def getUsersByEmail(email: String): Future[List[(ResourceUser, Box[List[Entitlement]], Option[List[UserAgreement]])]] = Future {
    val users = ResourceUser.findAll(By(ResourceUser.email, email))
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
    val limit = queryParams.collect { case OBPLimit(value) => MaxRows[ResourceUser](value) }.headOption
    val offset: Option[StartAt[ResourceUser]] = queryParams.collect { case OBPOffset(value) => StartAt[ResourceUser](value) }.headOption
    val locked: Option[String] = queryParams.collect { case OBPLockedStatus(value) => value }.headOption
    val deleted = queryParams.collect {
      case OBPIsDeleted(value) if value == true => // ?is_deleted=true
        By(ResourceUser.IsDeleted, true)
      case OBPIsDeleted(value) if value == false => // ?is_deleted=false
        By(ResourceUser.IsDeleted, false)
    }.headOption.orElse(
      Some(By(ResourceUser.IsDeleted, false)) // There is no query parameter "is_deleted"
    )

    // Users a consent minted for itself are not people and do not belong in a list of people: they
    // have no username and no email, there is one of them for every consent ever granted, and they
    // outnumber real users by orders of magnitude on any busy instance. They stay reachable by id
    // and through the account-access data; they just do not pad out this list.
    //
    // Filtered in SQL rather than after the fact, so it composes with the limit/offset above: a
    // filter applied to an already-paginated result returns short pages, which is exactly the
    // defect the ?locked= path below has.
    //
    // The v6.0.0 search path applies the same predicate -- see DoobieUserQueries.getUsers.
    val notMintedByAConsent = BySql[ResourceUser](
      "(createdbyconsentid IS NULL OR createdbyconsentid = '')",
      IHaveValidatedThisSQL("hongwei", "2026-08-01"))

    val optionalParams: Seq[QueryParam[ResourceUser]] =
      Seq(limit.toSeq, offset.toSeq, deleted.toSeq, Seq(notMintedByAConsent)).flatten

    def getAllResourceUsers(): List[ResourceUser] = ResourceUser.findAll(optionalParams: _*)

    val showUsers: List[ResourceUser] = locked.map(_.toLowerCase()) match {
      case Some("active") =>
        val lockedUsers: immutable.Seq[MappedBadLoginAttempt] =
          MappedBadLoginAttempt.findAll(
            By_>(MappedBadLoginAttempt.mBadAttemptsSinceLastSuccessOrReset, maxBadLoginAttempts.toInt)
          )
        val exclude: immutable.Seq[ResourceUser] = ResourceUser.findAll(ByList(ResourceUser.name_, lockedUsers.map(_.username)))
        getAllResourceUsers() diff exclude
      case Some("locked") =>
        val lockedUsers: immutable.Seq[MappedBadLoginAttempt] =
          MappedBadLoginAttempt.findAll(
            By_>(MappedBadLoginAttempt.mBadAttemptsSinceLastSuccessOrReset, maxBadLoginAttempts.toInt)
          )
        val exclude: immutable.Seq[ResourceUser] = ResourceUser.findAll(ByList(ResourceUser.name_, lockedUsers.map(_.username)))
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
        MappedEntitlement.findAll(ByList(MappedEntitlement.mUserId, userIds))
          .groupBy(_.userId)
          .map { case (uid, ents) => uid -> ents.sortBy(_.roleName).toList }

      // Batch-fetch agreements, then reduce to most-recent per (userId, agreementType).
      val agreementsByUserId: Map[String, List[UserAgreement]] =
        UserAgreement.findAll(ByList(UserAgreement.UserId, userIds))
          .groupBy(_.userId)
          .map { case (uid, all) =>
            uid -> all.groupBy(_.agreementType)
              .values
              .flatMap(_.sortBy(_.Date.get)(Ordering[Date].reverse).headOption)
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
    val ru = ResourceUser.create
    ru.provider_(provider)
    providerId match {
      case Some(v) => ru.providerId(v)
      case None    =>
    }
    createdByConsentId match {
      case Some(consentId) => ru.CreatedByConsentId(consentId)
      case None    => ru.CreatedByConsentId(null)
    }
    createdByUserInvitationId match {
      case Some(invitationId) => ru.CreatedByUserInvitationId(invitationId)
      case None    => ru.CreatedByUserInvitationId(null)
    }
    name match {
      case Some(v) => ru.name_(v)
      case None    =>
    }
    email match {
      case Some(v) => ru.email(v)
      case None    =>
    }
    userId match {
      case Some(v) => ru.userId_(v)
      case None    =>
    }
    company match {
      case Some(v) => ru.Company(v)
      case None    =>
    }
    lastMarketingAgreementSignedDate match {
      case Some(v) => ru.LastMarketingAgreementSignedDate(v)
      case None    =>
    }
    isNaturalPerson match {
      case Some(v) => ru.IsNaturalPerson(v)
      case None    =>
    }
    principalUserId match {
      case Some(v) => ru.PrincipalUserId(v)
      case None    =>
    }
    Full(ru.saveMe())
  }

  override def createUnsavedResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String], userId: Option[String]): Box[ResourceUser] = {
    val ru = ResourceUser.create
    ru.provider_(provider)
    providerId match {
      case Some(v) => ru.providerId(v)
      case None    =>
    }
    name match {
      case Some(v) => ru.name_(v)
      case None    =>
    }
    email match {
      case Some(v) => ru.email(v)
      case None    =>
    }
    userId match {
      case Some(v) => ru.userId_(v)
      case None    =>
    }
    Full(ru)
  }

  override def saveResourceUser(ru: ResourceUser): Box[ResourceUser] = {
    val r = Full(ru.saveMe())
    r
  }

  override def bulkDeleteAllResourceUsers(): Box[Boolean] = {
    Full( ResourceUser.bulkDelete_!!() )
  }

  override def deleteResourceUser(userId: Long): Box[Boolean] = {
    for {
      u <- ResourceUser.find(By(ResourceUser.id, userId))
    } yield {
      u.delete_!
    }
  }
  override def scrambleDataOfResourceUser(userPrimaryKey: UserPrimaryKey): Box[Boolean] = {
    for {
      u <- ResourceUser.find(By(ResourceUser.id, userPrimaryKey.value))
    } yield {
      AuthUser.find(By(AuthUser.user, userPrimaryKey.value)) match {
        case Empty =>
          u
            .Company(Helpers.randomString(16))
            .IsDeleted(true)
            .name_("DELETED-" + Helpers.randomString(16))
            .email(Helpers.randomString(10) + "@example.com")
            .providerId(Helpers.randomString(16))
            .save
        case _ =>
          u
            .Company(Helpers.randomString(16))
            .IsDeleted(true)
            .save
      }
    }
  }
  
}
