package code.ratelimiting

import java.util.Date

import code.api.util.APIUtil
import net.liftweb.util.SimpleInjector
import code.remotedata.RemotedataRateLimiting
import net.liftweb.common.Box

import scala.concurrent.Future

object RateLimitingDI extends SimpleInjector {
  val rateLimiting = new Inject(buildOne _) {}
  def buildOne: RateLimitingProviderTrait = APIUtil.getPropsAsBoolValue("use_akka", false) match {
    case false  => MappedRateLimitingProvider
    case true => RemotedataRateLimiting   // We will use Akka as a middleware
  }
}

trait RateLimitingProviderTrait {
  def getAll(): Future[List[RateLimiting]]
  def getAllByConsumerId(consumerId: String, date: Option[Date] = None): Future[List[RateLimiting]]
  def getByConsumerId(consumerId: String, apiVersion: String, apiName: String, date: Option[Date] = None): Future[Box[RateLimiting]]
  def createOrUpdateConsumerCallLimits(consumerId: String,
                                       fromDate: Date,
                                       toDate: Date,
                                       apiVersion: Option[String],
                                       apiName: Option[String],
                                       bankId: Option[String],
                                       perSecond: Option[String],
                                       perMinute: Option[String],
                                       perHour: Option[String],
                                       perDay: Option[String],
                                       perWeek: Option[String],
                                       perMonth: Option[String]): Future[Box[RateLimiting]]
}

trait RateLimitingTrait {
  def rateLimitingId: String
  def apiVersion: Option[String]
  def apiName: Option[String]
  def consumerId: String
  def bankId: Option[String]
  def perSecondCallLimit: Long
  def perMinuteCallLimit: Long
  def perHourCallLimit: Long
  def perDayCallLimit: Long
  def perWeekCallLimit: Long
  def perMonthCallLimit: Long
  def fromDate: Date
  def toDate: Date
}


class RemotedataRateLimitingCaseClasses {
  case class getAll()
  case class getAllByConsumerId(consumerId: String, date: Option[Date] = None)
  case class getByConsumerId(consumerId: String, apiVersion: String, apiName: String, date: Option[Date] = None)
  case class createOrUpdateConsumerCallLimits(consumerId: String,
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
                                              perMonth: Option[String])
}

object RemotedataRateLimitingCaseClasses extends RemotedataRateLimitingCaseClasses
