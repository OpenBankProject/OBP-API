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
