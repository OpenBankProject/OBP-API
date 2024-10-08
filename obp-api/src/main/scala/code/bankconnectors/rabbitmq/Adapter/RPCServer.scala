package code.bankconnectors.rabbitmq.Adapter

import bootstrap.liftweb.ToSchemify
import code.api.util.APIUtil
import com.openbankproject.commons.dto._
import com.openbankproject.commons.model._
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client._
import net.liftweb.db.DB
import net.liftweb.json
import net.liftweb.json.Serialization.write
import net.liftweb.mapper.{MapperRules, Schemifier}
import scala.concurrent.duration._
import java.util.concurrent.CountDownLatch
import scala.concurrent.Await

class ServerCallback(val ch: Channel, val latch: CountDownLatch) extends DeliverCallback {
  
  private implicit val formats = code.api.util.CustomJsonFormats.nullTolerateFormats
  val TIMEOUT = (3 seconds)
  override def handle(consumerTag: String, delivery: Delivery): Unit = {
    var response: String = null
    val obpMessageId = delivery.getProperties.getMessageId
    val replyProps = new BasicProperties.Builder()
      .correlationId(delivery.getProperties.getCorrelationId)
      .messageId(obpMessageId)
      .build
    val message = new String(delivery.getBody, "UTF-8")
    println(s"Request: OutBound message from OBP: process($obpMessageId) : message is $message " )
    
    try {
      
      val responseToOBP = if(obpMessageId.contains("obp_get_banks")){
        val outBound = json.parse(message).extract[OutBoundGetBanks]
        
        InBoundGetBanks(
          inboundAdapterCallContext = InboundAdapterCallContext(
             correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("",Nil),
          data = Await.result(code.bankconnectors.LocalMappedConnector.getBanks(None), TIMEOUT).map(_._1).head
        )
      } else if(obpMessageId.contains("obp_get_bank")){
        val outBound = json.parse(message).extract[OutBoundGetBank]
        InBoundGetBank(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("",Nil),
          data = Await.result(code.bankconnectors.LocalMappedConnector.getBank(outBound.bankId,None), TIMEOUT).map(_._1).head
        )
      } else {
        
      }

      val responseToOBPString = write(responseToOBP)
      println(s"Response: inBound message to OBP: process($obpMessageId) : message is $responseToOBPString " )
      response = "" + responseToOBPString 
      
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

    DB.defineConnectionManager(net.liftweb.util.DefaultConnectionIdentifier, APIUtil.vendor)
    Schemifier.schemify(true, Schemifier.infoF _, ToSchemify.models: _*)
    
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
