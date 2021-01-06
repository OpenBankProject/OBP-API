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

  def getAllByConsumerId(consumerId: String, date: Option[Date] = None): Future[List[RateLimiting]] = {
    (actor ? cc.getAllByConsumerId(consumerId, date)).mapTo[List[RateLimiting]]
  }
  
  def getByConsumerId(consumerId: String, apiVersion: String, apiName: String, date: Option[Date] = None): Future[Box[RateLimiting]] = {
    (actor ? cc.getByConsumerId(consumerId, apiVersion, apiName, date)).mapTo[Box[RateLimiting]]
  }

  def createOrUpdateConsumerCallLimits(id: String,
                                       from_date: Date,
                                       to_date: Date,
                                       apiVersion: Option[String],
                                       apiName: Option[String],
                                       bankId: Option[String],
                                       perSecond: Option[String],
                                       perMinute: Option[String],
                                       perHour: Option[String],
                                       perDay: Option[String],
                                       perWeek: Option[String],
                                       perMonth: Option[String]): Future[Box[RateLimiting]] =
    (actor ? cc.createOrUpdateConsumerCallLimits(id, from_date, to_date, apiVersion, apiName, bankId, perSecond, perMinute, perHour, perDay, perWeek, perMonth)).mapTo[Box[RateLimiting]]



}
