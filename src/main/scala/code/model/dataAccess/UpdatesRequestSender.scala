package code.model.dataAccess

import com.rabbitmq.client.{ConnectionFactory,Channel}
import net.liftmodules.amqp.{AMQPSender,StringAMQPSender,AMQPMessage}
import net.liftweb.util._

/**
* the message to be sent in message queue
* so that the transactions of the bank account
* get refreshed in the database
*/
case class UpdateBankAccount(
  val accountNumber : String,
  val bankNationalIdentifier : String
)

object UpdatesRequestSender {
  private val factory = new ConnectionFactory {
    import ConnectionFactory._
    setHost("localhost")
    setPort(DEFAULT_AMQP_PORT)
    setUsername(Props.get("rabbitmq.user", DEFAULT_USER))
    setPassword(Props.get("rabbitmq.password", DEFAULT_PASS))
    setVirtualHost(DEFAULT_VHOST)
  }

  private val amqp = new UpdateRequestsAMQPSender(factory, "transactions", "transactions")

  def sendMessage(message: UpdateBankAccount) = {
     amqp ! AMQPMessage(message)
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