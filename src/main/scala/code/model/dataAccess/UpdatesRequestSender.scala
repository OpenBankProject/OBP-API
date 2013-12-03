package code.model.dataAccess

import com.rabbitmq.client.{ConnectionFactory,Channel}
import net.liftmodules.amqp.{AMQPSender,StringAMQPSender,AMQPMessage}
import net.liftweb.util._
import net.liftweb.common.{Loggable, Failure, Full}
import Helpers.tryo

/**
* the message to be sent in message queue
* so that the transactions of the bank account
* get refreshed in the database
*/
case class UpdateBankAccount(
  val accountNumber : String,
  val bankNationalIdentifier : String
)

object UpdatesRequestSender extends Loggable {
  private val factory = new ConnectionFactory {
    import ConnectionFactory._
    setHost("localhost")
    setPort(DEFAULT_AMQP_PORT)
    setUsername(Props.get("rabbitmq.user", DEFAULT_USER))
    setPassword(Props.get("rabbitmq.password", DEFAULT_PASS))
    setVirtualHost(DEFAULT_VHOST)
  }

  private val amqp = tryo{
      new UpdateRequestsAMQPSender(factory, "transactions", "transactions")
  }

  def sendMessage(message: UpdateBankAccount) = {
    amqp match {
      case Full(a) => a ! AMQPMessage(message)
      case Failure(msg, _, _) => logger.warn("could not send the message: " + message + " because the the message sender was not set properly. Error: " + msg)
      case _ => logger.warn("could not send the message: " + message + " because the the message sender was not set properly.")
    }
  }
}

class UpdateRequestsAMQPSender(cf: ConnectionFactory, exchange: String, routingKey: String)
 extends AMQPSender[UpdateBankAccount](cf, exchange, routingKey) {
  override def configure(channel: Channel) = {
    val conn = cf.newConnection()
    val channel = conn.createChannel()
    channel
  }
}