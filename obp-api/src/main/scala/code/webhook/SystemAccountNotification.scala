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

package code.webhook

import code.api.util.OBPQueryParam
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.collection.immutable.List
import scala.concurrent.Future

object SystemAccountNotificationWebhookTrait extends SimpleInjector {
  val systemAccountNotificationWebhook = new Inject(() => buildOne) {}

  def buildOne: SystemAccountNotificationWebhookProvider = MappedSystemAccountNotificationWebhookProvider
}


trait SystemAccountNotificationWebhookProvider {
  def getSystemAccountNotificationWebhookByIdFuture(webhookId: String): Future[Box[SystemAccountNotificationWebhookTrait]]

  def getSystemAccountNotificationWebhooksByUserIdFuture(userId: String): Future[Box[List[SystemAccountNotificationWebhookTrait]]]

  def getSystemAccountNotificationWebhooksFuture(queryParams: List[OBPQueryParam]): Future[Box[List[SystemAccountNotificationWebhookTrait]]]

  def createSystemAccountNotificationWebhookFuture(
    userId: String,
    triggerName: String,
    url: String,
    httpMethod: String,
    httpProtocol: String
  ): Future[Box[SystemAccountNotificationWebhookTrait]]

  def deleteSystemAccountNotificationWebhookFuture(webhookId: String): Future[Box[Boolean]]

}

trait SystemAccountNotificationWebhookTrait {

  def webhookId: String

  def triggerName: String

  def url: String

  def httpMethod: String

  def httpProtocol: String

  def createdByUserId: String

}