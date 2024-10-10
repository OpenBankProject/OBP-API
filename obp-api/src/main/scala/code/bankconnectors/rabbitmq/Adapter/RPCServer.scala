package code.bankconnectors.rabbitmq.Adapter

import bootstrap.liftweb.ToSchemify
import code.api.util.{APIUtil}
import com.openbankproject.commons.dto._
import com.openbankproject.commons.model._
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client._
import net.liftweb.db.DB
import net.liftweb.json
import net.liftweb.json.Serialization.write
import net.liftweb.mapper.{Schemifier}
import scala.concurrent.Future
import com.openbankproject.commons.ExecutionContext.Implicits.global

class ServerCallback(val ch: Channel) extends DeliverCallback {

  private implicit val formats = code.api.util.CustomJsonFormats.nullTolerateFormats

  override def handle(consumerTag: String, delivery: Delivery): Unit = {
    var response: Future[String] = Future {
      ""
    }
    val obpMessageId = delivery.getProperties.getMessageId
    val replyProps = new BasicProperties.Builder()
      .correlationId(delivery.getProperties.getCorrelationId)
      .contentType("application/json")
      .messageId(obpMessageId)
      .build
    val message = new String(delivery.getBody, "UTF-8")
    println(s"Request: OutBound message from OBP: methodId($obpMessageId) : message is $message ")

    try {
      val responseToOBP = if (obpMessageId.contains("obp_get_banks")) {
        val outBound = json.parse(message).extract[OutBoundGetBanks]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getBanks(None).map(_.map(_._1).head)
        
        obpMappedResponse.map(response => InBoundGetBanks(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetBanks(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("obp_get_bank")) {
        val outBound = json.parse(message).extract[OutBoundGetBank]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getBank(outBound.bankId, None).map(_.map(_._1).head)
        
        obpMappedResponse.map(response => InBoundGetBank(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetBanks(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else {  
        Future {
          1
        }
      }
      
      response = responseToOBP.map(a => write(a)).map("" + _)
      response.map(res => println(s"Response: inBound message to OBP: process($obpMessageId) : message is $res "))
      response
    } catch {
      case e: Throwable => println(" Exception [.] " + e.toString)

    } finally {
      response.map(res => ch.basicPublish("", delivery.getProperties.getReplyTo, replyProps, res.getBytes("UTF-8")))
      ch.basicAck(delivery.getEnvelope.getDeliveryTag, false)
    }
  }

}

object RPCServer extends App {
  private val RPC_QUEUE_NAME = "obp_rpc_queue"
  // lazy initial RabbitMQ connection
  val host = APIUtil.getPropsValue("rabbitmq_connector.host").openOrThrowException("mandatory property rabbitmq_connector.host is missing!")
  val port = APIUtil.getPropsAsIntValue("rabbitmq_connector.port").openOrThrowException("mandatory property rabbitmq_connector.port is missing!")
//  val username = APIUtil.getPropsValue("rabbitmq_connector.username").openOrThrowException("mandatory property rabbitmq_connector.username is missing!")
//  val password = APIUtil.getPropsValue("rabbitmq_connector.password").openOrThrowException("mandatory property rabbitmq_connector.password is missing!")

  DB.defineConnectionManager(net.liftweb.util.DefaultConnectionIdentifier, APIUtil.vendor)
  Schemifier.schemify(true, Schemifier.infoF _, ToSchemify.models: _*)


  import com.zaxxer.hikari.HikariConfig
  import com.zaxxer.hikari.HikariDataSource

  
  
  var connection: Connection = null
  var channel: Channel = null
  try {
    val factory = new ConnectionFactory()
    factory.setHost(host)
    factory.setPort(port)
    factory.setUsername("server")
    factory.setPassword("server")
    connection = factory.newConnection()
    channel = connection.createChannel()
    channel.queueDeclare(RPC_QUEUE_NAME, false, false, false, null)
    channel.basicQos(1)
    // stop after one consumed message since this is example code
    val serverCallback = new ServerCallback(channel)
    channel basicConsume(RPC_QUEUE_NAME, false, serverCallback, _ => {})
    println("Start awaiting OBP Connector Requests:")
  } catch {
    case e: Exception => e.printStackTrace()
  } finally {
    if (connection != null) {
      try {
        //          connection.close()
      } catch {
        case e: Exception => println(s"unknown Exception:$e")
      }
    }
  }

}
