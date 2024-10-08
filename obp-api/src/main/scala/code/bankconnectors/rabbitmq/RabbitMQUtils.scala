package code.bankconnectors.rabbitmq

import code.api.util.APIUtil
import code.api.util.ErrorMessages.AdapterUnknownError
import code.bankconnectors.Connector
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.TopicTrait
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.json.Serialization.write
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client._

import java.util.UUID
import java.util.concurrent.{ArrayBlockingQueue, BlockingQueue}


/**
 * RabbitMQ utils.
 * The reason of extract this util: if not call RabbitMQ connector method, the db connection of RabbitMQ will not be initialized.
 */
object RabbitMQUtils extends MdcLoggable{

  private implicit val formats = code.api.util.CustomJsonFormats.nullTolerateFormats

  // lazy initial RabbitMQ connection
  val host = APIUtil.getPropsValue("rabbitmq_connector.host").openOrThrowException("mandatory property rabbitmq_connector.host is missing!")
  val port = APIUtil.getPropsAsIntValue("rabbitmq_connector.port").openOrThrowException("mandatory property rabbitmq_connector.port is missing!")
  val username = APIUtil.getPropsValue("rabbitmq_connector.username").openOrThrowException("mandatory property rabbitmq_connector.username is missing!")
  val password = APIUtil.getPropsValue("rabbitmq_connector.password").openOrThrowException("mandatory property rabbitmq_connector.password is missing!") 
  

  class ResponseCallback(val rabbitCorrelationId: String) extends DeliverCallback {
    val response: BlockingQueue[String] = new ArrayBlockingQueue[String](1)

    override def handle(consumerTag: String, message: Delivery): Unit = {
      if (message.getProperties.getCorrelationId.equals(rabbitCorrelationId)) {
        response.offer(new String(message.getBody, "UTF-8"))
      }
    }

    def take(): String = {
      response.take();
    }
  }

  
  def sendRequestUndGetResponseFromRabbitMQ[T: Manifest](messageId: String, outBound: TopicTrait): Box[T] = {
    val rabbitRequestJsonString: String = write(outBound) // convert OutBound to json string
    val rabbitResponseJsonString: String = {
      var connection: Connection = null
      try {
        logger.debug(s"${RabbitMQConnector_vOct2024.toString} outBoundJson: $messageId = $rabbitRequestJsonString")
        
        val factory = new ConnectionFactory()
        factory.setHost(host)
        factory.setPort(port)
        factory.setUsername(username)
        factory.setPassword(password)

        connection = factory.newConnection()
        val channel: Channel = connection.createChannel()
        val requestQueueName: String = "obp_rpc_queue"
        val replyQueueName: String = channel.queueDeclare().getQueue
        
        val rabbitMQCorrelationId = UUID.randomUUID().toString
        val rabbitMQprops = new BasicProperties.Builder()
          .messageId(messageId)
          .correlationId(rabbitMQCorrelationId)
          .replyTo(replyQueueName)
          .build()
        channel.basicPublish("", requestQueueName, rabbitMQprops, rabbitRequestJsonString.getBytes("UTF-8"))

        val responseCallback = new ResponseCallback(rabbitMQCorrelationId)
        channel.basicConsume(replyQueueName, true, responseCallback, _ => { })

        responseCallback.take()
        
      } catch {
        case e: Throwable =>{
          logger.debug(s"${RabbitMQConnector_vOct2024.toString} inBoundJson exception: $messageId = ${e}")
          throw new RuntimeException(s"$AdapterUnknownError Please Check Adapter Side! Details: ${e.getMessage}")//TODO error handling to API level
        }
      } finally {
        if (connection != null) {
          try {
            connection.close()
          } catch {
            case e: Throwable =>{
              logger.debug(s"${RabbitMQConnector_vOct2024.toString} inBoundJson exception: $messageId = ${e}")
              throw new RuntimeException(s"$AdapterUnknownError Please Check Adapter Side! Details: ${e.getMessage}")//TODO error handling to API Level
            }
          }
        }
      }
    }
    logger.debug(s"${RabbitMQConnector_vOct2024.toString} inBoundJson: $messageId = $rabbitResponseJsonString" )
    Connector.extractAdapterResponse[T](rabbitResponseJsonString, Empty)
  }
}