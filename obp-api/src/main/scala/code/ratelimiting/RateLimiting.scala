/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.ratelimiting

import java.util.Date

import code.api.util.APIUtil
import net.liftweb.util.SimpleInjector
import net.liftweb.common.Box

import scala.concurrent.Future

object RateLimitingDI extends SimpleInjector {
  val rateLimiting = new Inject(() => buildOne) {}
  def buildOne: RateLimitingProviderTrait = MappedRateLimitingProvider
}

trait RateLimitingProviderTrait {
  def getAll(): Future[List[RateLimiting]]
  def getAllByConsumerId(consumerId: String, date: Option[Date] = None): Future[List[RateLimiting]]
  def getByConsumerId(consumerId: String, apiVersion: String, apiName: String, date: Option[Date] = None): Future[Box[RateLimiting]]
  def findMostRecentRateLimit(consumerId: String, bankId: Option[String], apiVersion: Option[String], apiName: Option[String]): Future[Option[RateLimiting]]
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
    def updateConsumerCallLimits(rateLimitingId: String,
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
  def createConsumerCallLimits(consumerId: String,
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
  def deleteByRateLimitingId(rateLimitingId: String): Future[Box[Boolean]]
  def getByRateLimitingId(rateLimitingId: String): Future[Box[RateLimiting]]
  def getActiveCallLimitsByConsumerIdAtDate(consumerId: String, dateUtc: Date): Future[List[RateLimiting]]
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
