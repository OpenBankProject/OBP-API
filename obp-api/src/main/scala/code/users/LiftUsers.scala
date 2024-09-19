package code.users

import code.api.util.Consent.logger

import java.util.Date
import code.api.util._
import code.entitlement.Entitlement
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
    val existingUser = Users.users.vend.getUserByProviderId(provider = provider, idGivenByProvider = idGivenByProvider) // Find a user
    existingUser match {
      case Full(_) => // Existing user
        (existingUser, false)
      case _ => // Otherwise create a new one
        val newUser = Users.users.vend.createResourceUser( 
          provider = provider,
          providerId = Some(idGivenByProvider),
          createdByConsentId = consentId,
          name = name,
          email = email,
          userId = None,
          createdByUserInvitationId = None,
          company = None,
          lastMarketingAgreementSignedDate = None
        )
        (newUser, true)
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

    val optionalParams: Seq[QueryParam[ResourceUser]] = Seq(limit.toSeq, offset.toSeq, deleted.toSeq).flatten

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
      for {
        user <- getUsersCommon(queryParams)
      } yield {
        val entitlements = Entitlement.entitlement.vend.getEntitlementsByUserId(user.userId).map(_.sortWith(_.roleName < _.roleName))
        // val agreements = getUserAgreements(user)
        (user, entitlements, None)
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
      case None    => ru.CreatedByConsentId(null)
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
