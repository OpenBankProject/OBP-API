package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.ratelimiting.{RateLimiting, RateLimitingProviderTrait, RemotedataRateLimitingCaseClasses}
import net.liftweb.common.Box

import scala.collection.immutable.List
import scala.concurrent.Future


object RemotedataRateLimiting extends ObpActorInit with RateLimitingProviderTrait {

  val cc = RemotedataRateLimitingCaseClasses
  
  def getAll(): Future[List[RateLimiting]] = {
    (actor ? cc.getAll()).mapTo[List[RateLimiting]]
  }

  def getByConsumerId(consumerId: String): Future[Box[RateLimiting]] = {
    (actor ? cc.getByConsumerId(consumerId)).mapTo[Box[RateLimiting]]
  }

  def createOrUpdateConsumerCallLimits(id: String,
                                       perSecond: Option[String],
                                       perMinute: Option[String],
                                       perHour: Option[String],
                                       perDay: Option[String],
                                       perWeek: Option[String],
                                       perMonth: Option[String]): Future[Box[RateLimiting]] =
    (actor ? cc.createOrUpdateConsumerCallLimits(id, perSecond, perMinute, perHour, perDay, perWeek, perMonth)).mapTo[Box[RateLimiting]]



}
