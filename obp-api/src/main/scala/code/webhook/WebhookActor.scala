package code.webhook

import akka.actor.{Actor, ActorLogging}
import code.api.util.ApiTrigger
import code.util.Helper.MdcLoggable
import code.webhook.WebhookActor.{WebhookFailure, WebhookResponse, WebhookRequest}


object WebhookActor {
  case class EventPayload(event_name: String,
                         event_id: String,
                         bank_id: String,
                         account_id: String,
                         amount: String,
                         balance: String)
  case class WebhookRequest(trigger: ApiTrigger, 
                            eventId: String, 
                            bankId: String, 
                            accountId: String, 
                            amount: String, 
                            balance: String) {
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
  case class WebhookResponse(status: String,
                             request: WebhookRequest)
  case class WebhookFailure(error: String, 
                            request: WebhookRequest)
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
      WebhookHttpClient.startEvent(request)
    case WebhookResponse(status, request) =>
      logger.info("EVENT_ID: " + request.eventId)
      logger.info("STATUS: " + status)
    case WebhookFailure(error, request) =>
      logger.info("EVENT_ID: " + request.eventId)
      logger.error("ERROR: " + error)
  }

}

