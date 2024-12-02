package code.bankconnectors.rabbitmq

import code.api.util.ErrorMessages.AdapterUnknownError
import code.bankconnectors.Connector
import code.util.Helper.MdcLoggable
import code.api.util.APIUtil
import com.openbankproject.commons.model.TopicTrait
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.json.Serialization.write
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
  
  val keystorePath = APIUtil.getPropsValue("keystore.path").getOrElse("")
  val keystorePassword = APIUtil.getPropsValue("keystore.password").getOrElse(APIUtil.initPasswd)
  val truststorePath = APIUtil.getPropsValue("truststore.path").getOrElse("")
  val truststorePassword = APIUtil.getPropsValue("keystore.password").getOrElse(APIUtil.initPasswd)

  val args = new util.HashMap[String, AnyRef]()
  //60s  It sets the time (in milliseconds) after which the queue will 
  // automatically be deleted if it is not used, i.e., if no consumer is connected to it during that time.
  args.put("x-expires", Integer.valueOf(60000))
  args.put("x-message-ttl", Integer.valueOf(60000))
  //args.put("x-queue-type", "quorum") // use the classic first.
  
  
  private implicit val formats = code.api.util.CustomJsonFormats.nullTolerateFormats
  
  val RPC_QUEUE_NAME: String = "obp_rpc_queue"
  val RPC_REPLY_TO_QUEUE_NAME_PREFIX: String = "obp_reply_queue"

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
    
    val connection = RabbitMQConnectionPool.borrowConnection()
    val channel = connection.createChannel() // channel is not thread safe, so we always create new channel for each message.
    channel.queueDeclare(
      RPC_QUEUE_NAME,  // Queue name
      false,           // durable: non-persis, here set durable = true
      false,           // exclusive: non-excl4, here set exclusive = false
      false,           // autoDelete: delete, here set autoDelete = false 
      args             // extra arguments,
    )

    val replyQueueName:String = channel.queueDeclare(
      s"${RPC_REPLY_TO_QUEUE_NAME_PREFIX}_${messageId.replace("obp_","")}_${UUID.randomUUID.toString}",  // Queue name, it will be a unique name for each queue
      false,            // durable: non-persis, here set durable = false
      true,           // exclusive: non-excl4, here set exclusive = true
      true,           // autoDelete: delete, here set autoDelete = true 
      args             // extra arguments,
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
        channel.basicPublish("", RPC_QUEUE_NAME, rabbitMQProps, rabbitRequestJsonString.getBytes("UTF-8"))

        val responseCallback = new ResponseCallback(rabbitMQCorrelationId, channel)
        channel.basicConsume(replyQueueName, true, responseCallback, cancelCallback)
        responseCallback.take()
      } catch {
        case e: Throwable =>{
          logger.debug(s"${RabbitMQConnector_vOct2024.toString} inBoundJson exception: $messageId = ${e}")
          throw new RuntimeException(s"$AdapterUnknownError Please Check Adapter Side! Details: ${e.getMessage}")//TODO error handling to API level
        }
      } 
      finally {
        RabbitMQConnectionPool.returnConnection(connection)
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
    // Load client keystore
    val keyStore = KeyStore.getInstance("jks")
    val keystoreFile = new FileInputStream(keystorePath)
    keyStore.load(keystoreFile, keystorePassword.toCharArray)
    keystoreFile.close()
    // Set up KeyManagerFactory for client certificates
    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    kmf.init(keyStore, keystorePassword.toCharArray)

    // Load truststore for CA certificates
    val trustStore = KeyStore.getInstance("jks")
    val truststoreFile = new FileInputStream(truststorePath)
    trustStore.load(truststoreFile, truststorePassword.toCharArray)
    truststoreFile.close()

    // Set up TrustManagerFactory for CA certificates
    val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    tmf.init(trustStore)

    // Initialize SSLContext
    val sslContext = SSLContext.getInstance("TLSv1.3")
    sslContext.init(kmf.getKeyManagers, tmf.getTrustManagers, null)
    sslContext
  }
  
}
