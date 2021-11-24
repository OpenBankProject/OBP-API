package code.api.util

import java.util.Date

import code.api.util.ErrorMessages.{UserIsDeleted, UsernameHasBeenLocked}
import code.api.util.RateLimitingJson.CallLimit
import code.loginattempts.LoginAttempt
import code.model.dataAccess.AuthUser
import code.ratelimiting.{RateLimiting, RateLimitingDI}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.User
import net.liftweb.common.{Box, Empty, Failure, Full}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import net.liftweb.mapper.By

import scala.concurrent.Future


object AfterApiAuth extends MdcLoggable{
  /**
   * This function is used to execute actions after an user is authenticated via GUI
   * Types of authentication: GUI logon(OpenID Connect and OAuth1.0a)
   * @param user the authenticated user
   */
  def userGuiLogonInitAction(user: Box[AuthUser]) = {
    user.map { u => // Init actions
      logger.info("AfterApiAuth.userGuiLogonInitAction started successfully")
      
    } match {
        case Full(_) => logger.warn("AfterApiAuth.userGuiLogonInitAction completed successfully")
        case userInitActionFailure => logger.warn("AfterApiAuth.userGuiLogonInitAction: " + userInitActionFailure)
    }
  }
  /**
   * This function is used to execute actions after an user is authenticated via API
   * Types of authentication: Direct Login, OpenID Connect, OAuth1.0a, Direct Login, DAuth and Gateway Login
   */
  def userApiLogonInitAction(result: Future[(Box[User], Option[CallContext])]): Future[(Box[User], Option[CallContext])] = {
    logger.info("AfterApiAuth.userApiLogonInitAction started successfully")
    for {
      (user: Box[User], cc) <- result
    } yield {
      user match {
        case Full(u) => // There is a user. Apply init actions
          val authUser: Box[AuthUser] = AuthUser.find(By(AuthUser.user, u.userPrimaryKey.value))
          userGuiLogonInitAction(authUser)
          (user, cc)
        case userInitActionFailure => // There is no user. Just forward the result.
          logger.warn("AfterApiAuth.userApiLogonInitAction: " + userInitActionFailure)
          (user, cc)
      }
    }
  }  
  def checkUserIsDeletedOrLocked(res: Future[(Box[User], Option[CallContext])]): Future[(Box[User], Option[CallContext])] = {
    for {
      (user: Box[User], cc) <- res
    } yield {
      user match {
        case Full(u) => // There is a user. Check it.
          if (u.isDeleted.getOrElse(false)) {
            (Failure(UserIsDeleted), cc) // The user is DELETED.
          } else {
            LoginAttempt.userIsLocked(u.name) match {
              case true => (Failure(UsernameHasBeenLocked), cc) // The user is LOCKED.
              case false => (user, cc) // All good
            }
          }
        case _ => // There is no user. Just forward the result.
          (user, cc)
      }
    }
  }

  /**
   * This block of code needs to update Call Context with Rate Limiting
   * Please note that first source is the table RateLimiting and second is the table Consumer
   */
  def checkRateLimiting(userIsLockedOrDeleted: Future[(Box[User], Option[CallContext])]): Future[(Box[User], Option[CallContext])] = {
    def getRateLimiting(consumerId: String, version: String, name: String): Future[Box[RateLimiting]] = {
      RateLimitingUtil.useConsumerLimits match {
        case true => RateLimitingDI.rateLimiting.vend.getByConsumerId(consumerId, version, name, Some(new Date()))
        case false => Future(Empty)
      }
    }
    for {
      (user, cc) <- userIsLockedOrDeleted
      consumer = cc.flatMap(_.consumer)
      version = cc.map(_.implementedInVersion).getOrElse("None") // Calculate apiVersion  in case of Rate Limiting
      operationId = cc.flatMap(_.operationId) // Unique Identifier of Dynamic Endpoints
      // Calculate apiName in case of Rate Limiting
      name = cc.flatMap(_.resourceDocument.map(_.partialFunctionName)) // 1st try: function name at resource doc
        .orElse(operationId) // 2nd try: In case of Dynamic Endpoint we can only use operationId
        .getOrElse("None") // Not found any unique identifier
      rateLimiting <- getRateLimiting(consumer.map(_.consumerId.get).getOrElse(""), version, name)
    } yield {
      val limit: Option[CallLimit] = rateLimiting match {
        case Full(rl) => Some(CallLimit(
          rl.consumerId,
          rl.apiName,
          rl.apiVersion,
          rl.bankId,
          rl.perSecondCallLimit,
          rl.perMinuteCallLimit,
          rl.perHourCallLimit,
          rl.perDayCallLimit,
          rl.perWeekCallLimit,
          rl.perMonthCallLimit))
        case Empty =>
          Some(CallLimit(
            consumer.map(_.consumerId.get).getOrElse(""),
            None,
            None,
            None,
            consumer.map(_.perSecondCallLimit.get).getOrElse(-1),
            consumer.map(_.perMinuteCallLimit.get).getOrElse(-1),
            consumer.map(_.perHourCallLimit.get).getOrElse(-1),
            consumer.map(_.perDayCallLimit.get).getOrElse(-1),
            consumer.map(_.perWeekCallLimit.get).getOrElse(-1),
            consumer.map(_.perMonthCallLimit.get).getOrElse(-1)
          ))
        case _ => None
      }
      (user, cc.map(_.copy(rateLimiting = limit)))
    }
  }
  
}
