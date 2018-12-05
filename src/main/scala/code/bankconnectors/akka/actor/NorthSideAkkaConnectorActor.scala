package code.bankconnectors.akka.actor

import akka.actor.{Actor, ActorLogging}
import code.util.Helper.MdcLoggable
import code.webhook.WebhookActor.Request


/**
  * This Actor acts in next way:
  */
class AkkaConnectorActor extends Actor with ActorLogging with MdcLoggable {

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

