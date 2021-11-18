package code.api.util

import java.util.Date

import code.api.util.ErrorMessages.{UserIsDeleted, UsernameHasBeenLocked}
import code.api.util.RateLimitingJson.CallLimit
import code.loginattempts.LoginAttempt
import code.ratelimiting.{RateLimiting, RateLimitingDI}
import com.openbankproject.commons.model.User
import net.liftweb.common.{Box, Empty, Failure, Full}
import com.openbankproject.commons.ExecutionContext.Implicits.global

import scala.concurrent.Future


object ApiAuth {
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
