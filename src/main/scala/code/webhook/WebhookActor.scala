package code.webhook

import akka.actor.{Actor, ActorLogging}
import code.api.util.ApiTrigger
import code.util.Helper.MdcLoggable
import code.webhook.WebhookActor.Request


object WebhookActor {
  case class Request(trigger: ApiTrigger , eventId: String, bankId: String, accountId: String, amount: String, balance: String)
}


/**
  * This Actor acts in next way:
  */
class WebhookActor extends Actor with ActorLogging with MdcLoggable {

  def receive = waitingForRequest

  private def waitingForRequest: Receive = {
    case Request(trigger, eventId, bankId, accountId, amount, balance) =>
      implicit val ec = context.dispatcher
      logger.debug("TRIGGER: " + trigger.toString())
      logger.debug("EVENT_ID: " + eventId)
      logger.debug("BANK_ID: " + bankId)
      logger.debug("ACCOUNT_ID: " + accountId)
      logger.debug("AMOUNT: " + amount)
      logger.debug("BALANCE: " + balance)
  }

}

