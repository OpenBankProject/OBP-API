package code.bankconnectors.rabbitmq

import org.json4s._
import code.api.util.ErrorMessages.AdapterUnknownError
import code.bankconnectors.Connector
import code.util.Helper.MdcLoggable
import code.api.util.APIUtil
import com.openbankproject.commons.model.TopicTrait
import net.liftweb.common.{Box, Empty, Failure, Full}
import org.json4s.native.Serialization.write
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client._
import java.util
import java.util.UUID
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import java.io.FileInputStream
import java.security.KeyStore
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}


/**
 * RabbitMQ utils.
 * The reason of extract this util: if not call RabbitMQ connector method, the db connection of RabbitMQ will not be initialized.
 */
object RabbitMQUtils extends MdcLoggable{

  val host = APIUtil.getPropsValue("rabbitmq_connector.host").openOrThrowException("mandatory property rabbitmq_connector.host is missing!")
  val port = APIUtil.getPropsAsIntValue("rabbitmq_connector.port").openOrThrowException("mandatory property rabbitmq_connector.port is missing!")
  val username = APIUtil.getPropsValue("rabbitmq_connector.username").openOrThrowException("mandatory property rabbitmq_connector.username is missing!")
  val password = APIUtil.getPropsValue("rabbitmq_connector.password").openOrThrowException("mandatory property rabbitmq_connector.password is missing!")
  val virtualHost = APIUtil.getPropsValue("rabbitmq_connector.virtual_host").openOrThrowException("mandatory property rabbitmq_connector.virtual_host is missing!")
  
  val (keystorePath, keystorePassword, truststorePath, truststorePassword) = if (APIUtil.getPropsAsBoolValue("rabbitmq.use.ssl", false)) {
    (
      APIUtil.getPropsValue("keystore.path").getOrElse(""),
      APIUtil.getPropsValue("keystore.password").getOrElse(APIUtil.initPasswd),
      APIUtil.getPropsValue("truststore.path").openOrThrowException("mandatory property truststore.path is missing!"),
      APIUtil.getPropsValue("truststore.password").getOrElse(APIUtil.initPasswd)
    )
  }else{
    ("", APIUtil.initPasswd,"",APIUtil.initPasswd)
  }

  val rpcQueueArgs = new util.HashMap[String, AnyRef]()
  rpcQueueArgs.put("x-message-ttl", Integer.valueOf(60000))
  
  val rpcReplyToQueueArgs = new util.HashMap[String, AnyRef]()
  //60s  It sets the time (in milliseconds) after which the queue will 
  // automatically be deleted if it is not used, i.e., if no consumer is connected to it during that time.
  rpcReplyToQueueArgs.put("x-expires", Integer.valueOf(60000))
  rpcReplyToQueueArgs.put("x-message-ttl", Integer.valueOf(60000))
  
  
  private implicit val formats = code.api.util.CustomJsonFormats.nullTolerateFormats
  
  val RPC_QUEUE_NAME: String = APIUtil.getPropsValue("rabbitmq_connector.request_queue", "obp_rpc_queue")
  val RPC_REPLY_TO_QUEUE_NAME_PREFIX: String = APIUtil.getPropsValue("rabbitmq_connector.response_queue_prefix", "obp_reply_queue")

  class ResponseCallback(val rabbitCorrelationId: String, channel: Channel) extends DeliverCallback {

    val promise = Promise[String]()
    val future: Future[String] = promise.future

    override def handle(consumerTag: String, message: Delivery): Unit = {
      if (message.getProperties.getCorrelationId.equals(rabbitCorrelationId)) {
        {
          promise.success {
            val response =new String(message.getBody, "UTF-8");
            try {
              if (channel.isOpen) channel.close();
            } catch {
              case e: Throwable =>{
                logger.debug(s"$AdapterUnknownError Can not close the channel properly! Details:$e")
                throw new RuntimeException(s"$AdapterUnknownError Can not close the channel properly! Details:$e")
              }
            }
            response
          }
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

    var connectionOpt: Option[com.rabbitmq.client.Connection] = None

    val rabbitResponseJsonFuture  = {
      try {
        val connection = RabbitMQConnectionPool.borrowConnection()
        connectionOpt = Some(connection)
        // Check if queue already exists using a temporary channel (passive declare closes channel on failure)
        val queueExists = try {
          val tempChannel = connection.createChannel()
          try {
            tempChannel.queueDeclarePassive(RPC_QUEUE_NAME)
            true
          } finally {
            if (tempChannel.isOpen) tempChannel.close()
          }
        } catch {
          case _: java.io.IOException => false
        }

        val channel = connection.createChannel() // channel is not thread safe, so we always create new channel for each message.
        // Only declare queue if it doesn't already exist (avoids argument conflicts with external adapters)
        if (!queueExists) {
          channel.queueDeclare(
            RPC_QUEUE_NAME,  // Queue name
            true,            // durable: non-persis, here set durable = true
            false,           // exclusive: non-excl4, here set exclusive = false
            false,           // autoDelete: delete, here set autoDelete = false
            rpcQueueArgs     // extra arguments,
          )
        }

        val replyQueueName:String = channel.queueDeclare(
          s"${RPC_REPLY_TO_QUEUE_NAME_PREFIX}_${messageId.replace("obp_","")}_${UUID.randomUUID.toString}",  // Queue name, it will be a unique name for each queue
          false,            // durable: non-persis, here set durable = false
          true,           // exclusive: non-excl4, here set exclusive = true
          true,           // autoDelete: delete, here set autoDelete = true
          rpcReplyToQueueArgs // extra arguments,
        ).getQueue

        logger.debug(s"${RabbitMQConnector_vOct2024.toString} outBoundJson: $messageId = $rabbitRequestJsonString")
        logger.info(s"[RabbitMQ] Sending message to queue: $RPC_QUEUE_NAME, messageId: $messageId, replyTo: $replyQueueName")
        
        val rabbitMQCorrelationId = UUID.randomUUID().toString
        val rabbitMQProps = new BasicProperties.Builder()
          .messageId(messageId)
          .contentType("application/json")
          .correlationId(rabbitMQCorrelationId)
          .replyTo(replyQueueName)
          .build()
        channel.basicPublish("", RPC_QUEUE_NAME, rabbitMQProps, rabbitRequestJsonString.getBytes("UTF-8"))
        logger.info(s"[RabbitMQ] Message published, correlationId: $rabbitMQCorrelationId, waiting for response on: $replyQueueName")

        val responseCallback = new ResponseCallback(rabbitMQCorrelationId, channel)
        channel.basicConsume(replyQueueName, true, responseCallback, cancelCallback)
        responseCallback.take()
      } catch {
        case e: RuntimeException if e.getMessage != null && e.getMessage.startsWith("OBP-") =>
          logger.debug(s"${RabbitMQConnector_vOct2024.toString} inBoundJson exception: $messageId = ${e}")
          throw e
        case e: Throwable =>
          logger.debug(s"${RabbitMQConnector_vOct2024.toString} inBoundJson exception: $messageId = ${e}")
          throw new RuntimeException(s"$AdapterUnknownError Please Check Adapter Side! Details: ${e.getMessage}")//TODO error handling to API level
      }
      finally {
        connectionOpt.foreach(RabbitMQConnectionPool.returnConnection)
      }
    }
    rabbitResponseJsonFuture.map(rabbitResponseJsonString =>logger.debug(s"${RabbitMQConnector_vOct2024.toString} inBoundJson: $messageId = $rabbitResponseJsonString" ))
    rabbitResponseJsonFuture.map(rabbitResponseJsonString =>Connector.extractAdapterResponse[T](rabbitResponseJsonString, Empty))
  }

  def createSSLContext(
    keystorePath: String, 
    keystorePassword: String,
    truststorePath: String, 
    truststorePassword: String
  ): SSLContext = {
    // Load client keystore (optional for standard TLS)
    val keyManagers = if (keystorePath.nonEmpty) {
      val keyStore = KeyStore.getInstance(KeyStore.getDefaultType)
      val keystoreFile = new FileInputStream(keystorePath)
      try {
        keyStore.load(keystoreFile, keystorePassword.toCharArray)
      } finally {
        keystoreFile.close()
      }
      val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
      kmf.init(keyStore, keystorePassword.toCharArray)
      kmf.getKeyManagers
    } else {
      null
    }

    // Load truststore for CA certificates (required)
    val trustManagers = if (truststorePath.nonEmpty) {
      val trustStore = KeyStore.getInstance(KeyStore.getDefaultType)
      val truststoreFile = new FileInputStream(truststorePath)
      try {
        trustStore.load(truststoreFile, truststorePassword.toCharArray)
      } finally {
        truststoreFile.close()
      }
      val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
      tmf.init(trustStore)
      tmf.getTrustManagers
    } else {
      null
    }

    // Initialize SSLContext with optional client authentication
    val sslContext = SSLContext.getInstance("TLSv1.3")
    sslContext.init(keyManagers, trustManagers, null)
    sslContext
  }
  
}
