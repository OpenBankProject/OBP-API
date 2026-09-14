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

import code.api.util.ApiTrigger
import code.util.Helper.MdcLoggable
import code.webhook.WebhookActor.{AccountNotificationWebhookRequest, WebhookRequestTrait}


object WebhookActor {
  
  trait WebhookRequestTrait{
    def trigger: ApiTrigger
    def eventId: String
    def bankId: String
    def accountId: String
    def toEventPayload: EventPayloadTrait
  }
  
  trait EventPayloadTrait{
    def event_name: String
    def event_id: String
    def bank_id: String
    def account_id: String
  }
  
  case class EventPayload(event_name: String,
                         event_id: String,
                         bank_id: String,
                         account_id: String,
                         amount: String,
                         balance: String) extends EventPayloadTrait
  
  case class WebhookRequest(trigger: ApiTrigger, 
                            eventId: String, 
                            bankId: String, 
                            accountId: String, 
                            amount: String, 
                            balance: String) extends WebhookRequestTrait{
    def toEventPayload = 
      EventPayload(
        event_name = this.trigger.toString(),
        event_id = this.eventId, 
        bank_id = this.bankId, 
        account_id = this.accountId, 
        amount = this.amount, 
        balance=this.balance
      )
  }
  
  case class RelatedEntityPayload(
    user_id: String,
    customer_ids: List[String]
  )
  
  case class AccountNotificationPayload(
    event_name: String,
    event_id: String,
    bank_id:String,
    account_id:String,
    transaction_id:String,
    related_entities: List[RelatedEntityPayload]
  ) extends EventPayloadTrait
  
  case class RelatedEntity(
    userId: String,
    customerIds: List[String]
  )
  
  case class AccountNotificationWebhookRequest(
    trigger: ApiTrigger,
    eventId: String,
    bankId: String,
    accountId: String,
    transactionId: String,
    relatedEntities: List[RelatedEntity]
  ) extends WebhookRequestTrait{
    override def toEventPayload =
      AccountNotificationPayload(
        event_name = this.trigger.toString(),
        event_id = this.eventId,
        bank_id = this.bankId,
        account_id = this.accountId,
        transaction_id = this.transactionId,
        related_entities = this.relatedEntities.map(entity =>RelatedEntityPayload(entity.userId, entity.customerIds)),
      )
  }
  
  case class WebhookResponse(status: String,
                             request: WebhookRequestTrait)
  case class WebhookFailure(error: String, 
                            request: WebhookRequestTrait)
}


object WebhookAction extends MdcLoggable {
  def webhookRequest(request: WebhookRequestTrait) = {
    logger.debug("WebhookActor.webhookRequest(request).eventId: " + request.eventId)
    WebhookHttpClient.startEvent(request)
  }
  def accountNotificationWebhookRequest(request: AccountNotificationWebhookRequest) = {
    logger.debug("WebhookActor.accountNotificationWebhookRequest(request).eventId: " + request.eventId)
    WebhookHttpClient.startEvent(request)
  }
  def webhookResponse(status: String,
                      request: WebhookRequestTrait) = {
    logger.debug("WebhookActor.webhookResponse(status, request).status: " + status)
    logger.debug("WebhookActor.webhookResponse(status, request).request.eventId: " + request.eventId)
    logger.debug("WebhookActor.webhookResponse(status, request).request.toEventPayload: " + request.toEventPayload)
  }
  def webhookFailure(error: String,
                     request: WebhookRequestTrait) = {
    logger.debug("WebhookActor.webhookFailure(error, request).error: " + error)
    logger.debug("WebhookActor.webhookFailure(error, request).request.eventId:: " + request.eventId)
    logger.debug("WebhookActor.webhookFailure(error, request).request.toEventPayload: " + request.toEventPayload)
  }
}
