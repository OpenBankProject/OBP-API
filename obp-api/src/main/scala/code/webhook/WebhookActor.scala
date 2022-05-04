package code.webhook

import akka.actor.{Actor, ActorLogging}
import code.api.util.ApiTrigger
import code.util.Helper.MdcLoggable
import code.webhook.WebhookActor.{AccountNotificationWebhookRequest, WebhookFailure, WebhookRequest, WebhookResponse}


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


/**
  * This Actor process all request/response messages related to Webhooks.
  * It's accessible at the North side all over the code. 
  * Example:
  * {
  *   val actor: ActorSelection = ObpLookupSystem.getWebhookActor()
  * }
  * We use fire and forget scenario in case of this Actor.
  * I.e. we trigger some webhook's event with:
  * 1. actor ! Request
  * and then send result of event to the Actor:
  * 2. actor ! Response
  * 
  */
class WebhookActor extends Actor with ActorLogging with MdcLoggable {

  def receive: Receive = waitingForRequest

  private def waitingForRequest: Receive = {
    case request@WebhookRequest(trigger, eventId, bankId, accountId, amount, balance) =>
      implicit val ec = context.dispatcher
      logger.debug("WebhookActor.waitingForRequest.WebhookRequest(request).eventId: " + eventId)
      WebhookHttpClient.startEvent(request)
    case request@AccountNotificationWebhookRequest(trigger, eventId, bankId, accountId, transactionId, relatedEntities) =>
      implicit val ec = context.dispatcher
      logger.debug("WebhookActor.waitingForRequest.AccountNotificationWebhookRequest(request).eventId: " + eventId)
      WebhookHttpClient.startEvent(request)
    case WebhookResponse(status, request) =>
      logger.debug("WebhookActor.waitingForRequest.WebhookResponse(status, request).status: " + status)
      logger.debug("WebhookActor.waitingForRequest.WebhookResponse(status, request).request.eventId: " + request.eventId)
      logger.debug("WebhookActor.waitingForRequest.WebhookResponse(status, request).request.toEventPayload: " + request.toEventPayload)
    case WebhookFailure(error, request) =>
      logger.debug("WebhookActor.waitingForRequest.WebhookFailure(error, request).error: " + error)
      logger.debug("WebhookActor.waitingForRequest.WebhookFailure(error, request).request.eventId:: " + request.eventId)
      logger.debug("WebhookActor.waitingForRequest.WebhookFailure(error, request).request.toEventPayload: " + request.toEventPayload)
  }

}

