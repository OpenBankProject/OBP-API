package code.bankconnectors.opencorridor

import code.amqpbroker.AmqpBankBroker
import code.api.util.APIUtil
import code.api.util.ErrorMessages._
import code.bankconnectors.rabbitmq.ResponseCallback
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.dto._
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.{CancelCallback, Connection, ConnectionFactory}
import net.liftweb.common.{Box, Failure, Full}
import org.json4s.native.Serialization.write
import org.json4s.jvalue2extractable

import java.util
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Open Corridor Interface C publisher: server-initiated publish-and-await-reply to a
 * BANK's own RabbitMQ vhost, keyed by bank_id through the AmqpBankBroker
 * registry.
 *
 * Structurally the same RPC shape as `RabbitMQUtils.sendRequestUndGetResponseFromRabbitMQ`
 * (publish to `obp_rpc_queue`, await on a per-request `replyTo` queue, correlate by
 * `correlationId`) but deliberately self-contained: `RabbitMQUtils`' object init hard-requires
 * the global `rabbitmq_connector.*` props, which describe the OBP adapter broker, not the
 * per-bank brokers this publisher talks to. The wire bodies are the flat lower_snake_case
 * DTOs from `com.openbankproject.commons.dto` (locked contract — see the MessageDocs in
 * `RabbitMQConnector_vOct2024`).
 */
object OpenCorridorPublisher extends MdcLoggable {

  private implicit val formats: org.json4s.Formats = code.api.util.CustomJsonFormats.nullTolerateFormats

  val RPC_QUEUE_NAME = "obp_rpc_queue"
  val REPLY_QUEUE_NAME_PREFIX = "obp_reply_queue"

  /** Application-level cap on how long to wait for the bank's reply; kept below the
    * 60s reply-queue TTL, mirroring the RabbitMQUtils setup. */
  val PUBLISH_RESPONSE_TIMEOUT_IN_MILLIS: Long =
    APIUtil.getPropsAsLongValue("open_corridor.publish_response_timeout", 30000L)

  private val rpcQueueArgs = new util.HashMap[String, AnyRef]()
  rpcQueueArgs.put("x-message-ttl", Integer.valueOf(60000))

  private val replyQueueArgs = new util.HashMap[String, AnyRef]()
  replyQueueArgs.put("x-expires", Integer.valueOf(60000))
  replyQueueArgs.put("x-message-ttl", Integer.valueOf(60000))

  private val cancelCallback: CancelCallback =
    (consumerTag: String) => logger.info(s"Open Corridor consumerTag($consumerTag) cancelled")

  /** One cached connection per bank, created lazily and replaced on disconnect.
    * Channels are NOT cached — they are not thread-safe; one per publish, as in
    * RabbitMQUtils. */
  private val connections = new ConcurrentHashMap[String, Connection]()

  private def connectionFor(broker: AmqpBankBroker): Connection = {
    connections.compute(broker.bankId, (_, existing) => {
      if (existing != null && existing.isOpen) existing
      else {
        if (existing != null) {
          try existing.close() catch { case _: Throwable => () }
        }
        val factory = new ConnectionFactory()
        factory.setHost(broker.host)
        factory.setPort(broker.port)
        factory.setVirtualHost(broker.virtualHost)
        factory.setUsername(broker.username)
        factory.setPassword(broker.password)
        // Recover the connection + topology automatically after a broker restart.
        factory.setAutomaticRecoveryEnabled(true)
        // Local SSL-context construction: calling RabbitMQUtils.createSSLContext would
        // initialize that object, which hard-requires the global rabbitmq_connector.* props.
        if (broker.useSsl) factory.useSslProtocol(createSslContext())
        logger.info(s"Open Corridor: connecting to bank ${broker.bankId} broker ${broker.host}:${broker.port}${broker.virtualHost}")
        factory.newConnection(s"obp-open-corridor-${broker.bankId}")
      }
    })
  }

  /**
   * Publish one Interface C message to the bank's vhost and await the Bank Node's
   * reply envelope. `messageId` is the operation (`obp_credit_notification` /
   * `obp_settlement_instruction` / ...); the body is the flat wire DTO.
   *
   * The returned envelope's `status.errorCode` is NOT interpreted here beyond
   * parsing — callers must handle non-empty error codes explicitly (they must
   * never be swallowed).
   */
  def publishAndAwaitReply(bankId: String, messageId: String, wireBody: AnyRef): Future[Box[InBoundOpenCorridorReply]] =
    publishRawAndAwaitReply(bankId, messageId, write(wireBody))

  /** Same, but with an already-serialized wire body (the outbox stores payloads as JSON). */
  def publishRawAndAwaitReply(bankId: String, messageId: String, bodyJson: String): Future[Box[InBoundOpenCorridorReply]] = {
    AmqpBankBroker.findByBankId(bankId) match {
      case Full(broker) => publishToBroker(broker, messageId, bodyJson)
      case _ => Future.successful(Failure(s"$AmqpBankBrokerNotConfigured BANK_ID: $bankId"))
    }
  }

  private def publishToBroker(broker: AmqpBankBroker, messageId: String, bodyJson: String): Future[Box[InBoundOpenCorridorReply]] = {
    val replyJsonFuture: Future[String] =
      try {
        val connection = connectionFor(broker)
        val channel = connection.createChannel()

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
        if (!queueExists) {
          channel.queueDeclare(RPC_QUEUE_NAME, true, false, false, rpcQueueArgs)
        }

        val replyQueueName = channel.queueDeclare(
          s"${REPLY_QUEUE_NAME_PREFIX}_${messageId.replace("obp_", "")}_${UUID.randomUUID.toString}",
          false, true, true, replyQueueArgs
        ).getQueue

        val correlationId = UUID.randomUUID().toString
        val props = new BasicProperties.Builder()
          .messageId(messageId)
          .contentType("application/json")
          .correlationId(correlationId)
          .replyTo(replyQueueName)
          .build()

        logger.info(s"Open Corridor publish: bank=${broker.bankId} messageId=$messageId correlationId=$correlationId replyTo=$replyQueueName")
        // Body content is never logged: credit notifications carry the commit–reveal
        // evidence (promise_salt / promise_preimage, with the payment instruction
        // embedded in the preimage) plus originator PII, none of which
        // SecureLogging.maskSensitive knows how to mask.
        logger.debug(s"Open Corridor publish body: ${bodyJson.length} chars (content not logged)")
        channel.basicPublish("", RPC_QUEUE_NAME, props, bodyJson.getBytes("UTF-8"))

        val responseCallback = new ResponseCallback(correlationId, channel)
        channel.basicConsume(replyQueueName, true, responseCallback, cancelCallback)
        code.api.util.FutureUtil.futureWithTimeout(responseCallback.take())(
          code.api.util.FutureUtil.EndpointTimeout(PUBLISH_RESPONSE_TIMEOUT_IN_MILLIS),
          code.api.util.FutureUtil.EndpointContext(None),
          global
        ).andThen {
          // On timeout the ResponseCallback never ran, so the per-publish channel is still open.
          case scala.util.Failure(_) =>
            try { if (channel.isOpen) channel.close() }
            catch { case e: Throwable => logger.debug(s"Open Corridor timeout channel cleanup failed: $e") }
        }
      } catch {
        case e: Throwable =>
          logger.warn(s"Open Corridor publish failed: bank=${broker.bankId} messageId=$messageId", e)
          Future.failed(e)
      }

    replyJsonFuture.map { replyJson =>
      logger.debug(s"Open Corridor reply: bank=${broker.bankId} messageId=$messageId body=${replyJson.length} chars (content not logged)")
      net.liftweb.util.Helpers.tryo(
        org.json4s.native.JsonMethods.parse(replyJson).extract[InBoundOpenCorridorReply]
      ) match {
        case Full(reply) =>
          logger.debug(s"Open Corridor reply parsed: bank=${broker.bankId} messageId=$messageId errorCode='${reply.status.errorCode}'")
          Full(reply)
        case _ =>
          // The raw body stays out of the Failure message — it propagates to callers
          // and can surface in API error responses. Log it at debug only, truncated,
          // where the SecureLogging funnel at least applies.
          logger.debug(s"Open Corridor reply did not parse as the inbound envelope: bank=${broker.bankId} messageId=$messageId body=${replyJson.take(500)}")
          Failure(s"$InvalidConnectorResponse Open Corridor reply did not parse as the inbound envelope. bank=${broker.bankId} messageId=$messageId")
      }
    }.recover {
      case e: Throwable => Failure(s"$OpenCorridorPublishFailed bank=${broker.bankId} messageId=$messageId Details: ${e.getMessage}")
    }
  }

  /** TLS 1.3 context from the instance keystore/truststore props. Local copy of
    * `RabbitMQUtils.createSSLContext` — referencing that object would force its init,
    * which hard-requires the global `rabbitmq_connector.*` props this publisher
    * deliberately does not depend on. */
  private def createSslContext(): javax.net.ssl.SSLContext = {
    import java.io.FileInputStream
    import java.security.KeyStore
    import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

    val keystorePath = APIUtil.getPropsValue("keystore.path").getOrElse("")
    val keystorePassword = APIUtil.getPropsValue("keystore.password").getOrElse(APIUtil.initPasswd)
    val truststorePath = APIUtil.getPropsValue("truststore.path").getOrElse("")
    val truststorePassword = APIUtil.getPropsValue("truststore.password").getOrElse(APIUtil.initPasswd)

    val keyManagers = if (keystorePath.nonEmpty) {
      val keyStore = KeyStore.getInstance(KeyStore.getDefaultType)
      val keystoreFile = new FileInputStream(keystorePath)
      try keyStore.load(keystoreFile, keystorePassword.toCharArray) finally keystoreFile.close()
      val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
      kmf.init(keyStore, keystorePassword.toCharArray)
      kmf.getKeyManagers
    } else null

    val trustManagers = if (truststorePath.nonEmpty) {
      val trustStore = KeyStore.getInstance(KeyStore.getDefaultType)
      val truststoreFile = new FileInputStream(truststorePath)
      try trustStore.load(truststoreFile, truststorePassword.toCharArray) finally truststoreFile.close()
      val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
      tmf.init(trustStore)
      tmf.getTrustManagers
    } else null

    val sslContext = SSLContext.getInstance("TLSv1.3")
    sslContext.init(keyManagers, trustManagers, null)
    sslContext
  }

  /** Publish `obp_credit_notification` to the CREDITOR (beneficiary) bank's vhost. */
  def sendCreditNotification(
    creditorBankId: String,
    message: OutBoundOpenCorridorCreditNotification
  ): Future[Box[InBoundOpenCorridorReply]] =
    publishAndAwaitReply(creditorBankId, "obp_credit_notification", message)

  /** Publish `obp_settlement_instruction` to the DEBTOR bank's vhost. */
  def sendSettlementInstruction(
    debtorBankId: String,
    message: OutBoundOpenCorridorSettlementInstruction
  ): Future[Box[InBoundOpenCorridorReply]] =
    publishAndAwaitReply(debtorBankId, "obp_settlement_instruction", message)
}
