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

package code.consumer

import code.api.util.{APIUtil, CallContext, OBPQueryParam}
import code.model.{AppType, Consumer, MappedConsumersProvider}
import com.openbankproject.commons.model.{BankIdAccountId, User, View}
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future

object Consumers extends SimpleInjector {

  val consumers = new Inject(() => buildOne) {}

  def buildOne: ConsumersProvider = MappedConsumersProvider

}


// Question: This should never be the entry point?
trait ConsumersProvider {
  def getConsumerByPrimaryIdFuture(id: Long): Future[Box[Consumer]]
  def getConsumerByPrimaryId(id: Long): Box[Consumer]
  def getConsumerByConsumerKey(consumerKey: String): Box[Consumer]
  def getConsumerByConsumerKeyFuture(consumerKey: String): Future[Box[Consumer]]
  def getConsumerByPemCertificate(pem: String): Box[Consumer]
  def getConsumerByConsumerId(consumerId: String): Box[Consumer]
  def getConsumerByConsumerIdFuture(consumerId: String): Future[Box[Consumer]]
  def getConsumersByUserIdFuture(userId: String): Future[List[Consumer]]
  def getConsumersFuture(httpParams: List[OBPQueryParam], callContext: Option[CallContext]): Future[List[Consumer]]
  def createConsumer(
    key: Option[String],
    secret: Option[String],
    isActive: Option[Boolean], 
    name: Option[String], 
    appType: Option[AppType], 
    description: Option[String], 
    developerEmail: Option[String], 
    redirectURL: Option[String],
    createdByUserId: Option[String],
    clientCertificate: Option[String],
    company: Option[String],
    logoURL: Option[String]
  ): Box[Consumer]
  def deleteConsumer(consumer: Consumer): Boolean
  def updateConsumer(id: Long,
                     key: Option[String] = None,
                     secret: Option[String] = None,
                     isActive: Option[Boolean] = None,
                     name: Option[String] = None,
                     appType: Option[AppType] = None,
                     description: Option[String] = None,
                     developerEmail: Option[String] = None,
                     redirectURL: Option[String] = None,
                     createdByUserId: Option[String] = None,
                     LogoURL: Option[String] = None,
                     certificate: Option[String] = None,
  ): Box[Consumer]
  @deprecated("Use RateLimitingDI.rateLimiting.vend methods instead", "v5.0.0")
  def updateConsumerCallLimits(id: Long, perSecond: Option[String], perMinute: Option[String], perHour: Option[String], perDay: Option[String], perWeek: Option[String], perMonth: Option[String]): Future[Box[Consumer]]
  def getOrCreateConsumer(consumerId: Option[String], 
                          key: Option[String], 
                          secret: Option[String],
                          aud: Option[String],
                          azp: Option[String],
                          iss: Option[String],
                          sub: Option[String], 
                          isActive: Option[Boolean], 
                          name: Option[String], 
                          appType: Option[AppType], 
                          description: Option[String], 
                          developerEmail: Option[String], 
                          redirectURL: Option[String], 
                          createdByUserId: Option[String],
                          certificate: Option[String] = None,
                          logoUrl: Option[String] = None
                         ): Box[Consumer]
  def populateMissingUUIDs(): Boolean
  
}