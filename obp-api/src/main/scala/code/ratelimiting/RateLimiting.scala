package code.ratelimiting

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
  def createOrUpdateConsumerCallLimits(consumerId: String,
                                       perSecond: Option[String],
                                       perMinute: Option[String],
                                       perHour: Option[String],
                                       perDay: Option[String],
                                       perWeek: Option[String],
                                       perMonth: Option[String]): Future[Box[RateLimiting]]
}

trait RateLimitingTrait {
  def rateLimitingId: String
  def apiVersion: String
  def apiName: String
  def consumerId: String
  def bankId: String
  def perSecondCallLimit: Long
  def perMinuteCallLimit: Long
  def perHourCallLimit: Long
  def perDayCallLimit: Long
  def perWeekCallLimit: Long
  def perMonthCallLimit: Long
}


class RemotedataRateLimitingCaseClasses {
  case class getAll()
  case class createOrUpdateConsumerCallLimits(consumerId: String,
                                              perSecond: Option[String],
                                              perMinute: Option[String],
                                              perHour: Option[String],
                                              perDay: Option[String],
                                              perWeek: Option[String],
                                              perMonth: Option[String])
}

object RemotedataRateLimitingCaseClasses extends RemotedataRateLimitingCaseClasses
