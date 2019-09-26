package code.remotedata

import java.util.Date

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
                                       from_date: Date,
                                       to_date: Date,
                                       perSecond: Option[String],
                                       perMinute: Option[String],
                                       perHour: Option[String],
                                       perDay: Option[String],
                                       perWeek: Option[String],
                                       perMonth: Option[String]): Future[Box[RateLimiting]] =
    (actor ? cc.createOrUpdateConsumerCallLimits(id, from_date, to_date, perSecond, perMinute, perHour, perDay, perWeek, perMonth)).mapTo[Box[RateLimiting]]



}
