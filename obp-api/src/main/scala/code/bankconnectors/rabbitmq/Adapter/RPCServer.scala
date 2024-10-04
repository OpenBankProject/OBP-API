package code.bankconnectors.rabbitmq.Adapter

import com.openbankproject.commons.dto.{InBoundGetBanks, OutBoundGetBanks}
import com.openbankproject.commons.model.{BankCommons, BankId, InboundAdapterCallContext, Status}
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client._
import net.liftweb.json
import net.liftweb.json.Serialization.write

import java.util.concurrent.CountDownLatch

class ServerCallback(val ch: Channel, val latch: CountDownLatch) extends DeliverCallback {
  
  private implicit val formats = code.api.util.CustomJsonFormats.nullTolerateFormats
  
  override def handle(consumerTag: String, delivery: Delivery): Unit = {
    var response: String = null
    val replyProps = new BasicProperties.Builder()
      .correlationId(delivery.getProperties.getCorrelationId)
      .build

    try {
      val message = new String(delivery.getBody, "UTF-8")
      println(s"Request: OutBound message from OBP: $message " )
      val outBoundGetBanks = json.parse(message).extract[OutBoundGetBanks]
      val inBoundGetBanks = InBoundGetBanks(
        inboundAdapterCallContext = InboundAdapterCallContext(
           correlationId = outBoundGetBanks.outboundAdapterCallContext.correlationId
        ),
        status = Status("",Nil),
        data = List(BankCommons(
          bankId = BankId("bankIdExample.value"),
          shortName = "bankShortNameExample.value",
          fullName = "bankFullNameExample.value",
          logoUrl = "bankLogoUrlExample.value",
          websiteUrl = "bankWebsiteUrlExample.value",
          bankRoutingScheme = "bankRoutingSchemeExample.value",
          bankRoutingAddress = "bankRoutingAddressExample.value",
          swiftBic = "bankSwiftBicExample.value",
          nationalIdentifier = "bankNationalIdentifierExample.valu"
        )))
      val responseToOBP = write(inBoundGetBanks)
      println(s"Response: inBound message to OBP: $responseToOBP " )
      response = "" + responseToOBP // convert OutBound to json string
      
    } catch {
      case e: Exception => {
        println(" [.] " + e.toString)
        response = ""
      }
    } finally {
      ch.basicPublish("", delivery.getProperties.getReplyTo, replyProps, response.getBytes("UTF-8"))
      ch.basicAck(delivery.getEnvelope.getDeliveryTag, false)
      latch.countDown()
    }

  }

}

object RPCServer {
  private val RPC_QUEUE_NAME = "obp_rpc_queue"

  def main(argv: Array[String]) {
    var connection: Connection = null
    var channel: Channel = null
    try {
      val factory = new ConnectionFactory()
      factory.setHost("localhost")
      connection = factory.newConnection()
      channel = connection.createChannel()
      channel.queueDeclare(RPC_QUEUE_NAME, false, false, false, null)
      channel.basicQos(1)
      // stop after one consumed message since this is example code
      val latch = new CountDownLatch(1)
      val serverCallback = new ServerCallback(channel, latch)
      channel.basicConsume(RPC_QUEUE_NAME, false, serverCallback, _ => { })
      println("Start awaiting OBP Connector Requests:")
      latch.await()
    } catch {
      case e: Exception => e.printStackTrace()
    } finally {
      if (connection != null) {
        try {
//          connection.close()
        } catch {
          case ignore: Exception =>
        }
      }
    }
  }
}
