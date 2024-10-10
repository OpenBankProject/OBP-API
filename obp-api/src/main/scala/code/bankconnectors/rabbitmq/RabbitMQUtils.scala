package code.bankconnectors.rabbitmq

import code.api.util.ErrorMessages.AdapterUnknownError
import code.bankconnectors.Connector
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.TopicTrait
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.json.Serialization.write
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client._

import java.util
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}


/**
 * RabbitMQ utils.
 * The reason of extract this util: if not call RabbitMQ connector method, the db connection of RabbitMQ will not be initialized.
 */
object RabbitMQUtils extends MdcLoggable{

  private implicit val formats = code.api.util.CustomJsonFormats.nullTolerateFormats
  
  val requestQueueName: String = "obp_rpc_queue"

  class ResponseCallback(val rabbitCorrelationId: String) extends DeliverCallback {

    val promise = Promise[String]()
    val future: Future[String] = promise.future

    override def handle(consumerTag: String, message: Delivery): Unit = {
      if (message.getProperties.getCorrelationId.equals(rabbitCorrelationId)) {
        {
          promise.success(new String(message.getBody, "UTF-8"))
        }
      }
    }

    def take(): Future[String] = {
      future
    }
  }

  val cancelCallback: CancelCallback = (consumerTag: String) =>  logger.info(s"consumerTag($consumerTag) is  cancelled!!")
  
  def sendRequestUndGetResponseFromRabbitMQ[T: Manifest](messageId: String, outBound: TopicTrait): Future[Box[T]] = {

    val rabbitRequestJsonString: String = write(outBound) // convert OutBound to json string
    
    val args = new util.HashMap[String, AnyRef]()
    //60s  It sets the time (in milliseconds) after which the queue will 
    // automatically be deleted if it is not used, i.e., if no consumer is connected to it during that time.
    args.put("x-expires", Integer.valueOf(60000)) 
    

    // Create a Connection and Channel pool with max 5 connections and 10 channels per connection
//    val connectionChannelPool = new RabbitMQConnectionPool2(factory, maxConnections = 5, maxChannelsPerConnection = 1)
    val connection = RabbitMQConnectionPool2.borrowConnection()
    val channel = RabbitMQConnectionPool2.borrowChannel(connection)
//    val connection = RabbitMQConnectionPool.borrowConnection()
//    val channel = connection.createChannel()
    val replyQueueName:String = channel.queueDeclare(
      "",  // Queue name
      false,  // durable: non-persistent
      true,   // exclusive: non-exclusive
      true,   // autoDelete: delete when no consumers
      args   //  extra arguments
    ).getQueue

    val rabbitResponseJsonFuture  = {
      try {
        logger.debug(s"${RabbitMQConnector_vOct2024.toString} outBoundJson: $messageId = $rabbitRequestJsonString")
        
        val rabbitMQCorrelationId = UUID.randomUUID().toString
        val rabbitMQProps = new BasicProperties.Builder()
          .messageId(messageId)
          .contentType("application/json")
          .correlationId(rabbitMQCorrelationId)
          .replyTo(replyQueueName)
          .build()
        channel.basicPublish("", requestQueueName, rabbitMQProps, rabbitRequestJsonString.getBytes("UTF-8"))

        val responseCallback = new ResponseCallback(rabbitMQCorrelationId)
        channel.basicConsume(replyQueueName, true, responseCallback, cancelCallback)
        responseCallback.take()
        
      } catch {
        case e: Throwable =>{
          logger.debug(s"${RabbitMQConnector_vOct2024.toString} inBoundJson exception: $messageId = ${e}")
          throw new RuntimeException(s"$AdapterUnknownError Please Check Adapter Side! Details: ${e.getMessage}")//TODO error handling to API level
        }
      } 
      finally {
//        channel.close() --> this will tell rebbitMQ to delete the replyQueue.
        RabbitMQConnectionPool2.returnChannel(channel)
        RabbitMQConnectionPool2.returnConnection(connection)
      }
    }
    rabbitResponseJsonFuture.map(rabbitResponseJsonString =>logger.debug(s"${RabbitMQConnector_vOct2024.toString} inBoundJson: $messageId = $rabbitResponseJsonString" ))
    rabbitResponseJsonFuture.map(rabbitResponseJsonString =>Connector.extractAdapterResponse[T](rabbitResponseJsonString, Empty))
  }
}
