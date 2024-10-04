package code.bankconnectors.rabbitmq
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client._

import java.util.UUID
import java.util.concurrent.{ArrayBlockingQueue, BlockingQueue}

class ResponseCallback(val corrId: String) extends DeliverCallback {
  val response: BlockingQueue[String] = new ArrayBlockingQueue[String](1)

  override def handle(consumerTag: String, message: Delivery): Unit = {
    if (message.getProperties.getCorrelationId.equals(corrId)) {
      response.offer(new String(message.getBody, "UTF-8"))
    }
  }

  def take(): String = {
    response.take();
  }
}

class RabbitMQRPClient(host: String, port: Int, username:String, password:String) {

  val factory = new ConnectionFactory()
  factory.setHost(host)
  factory.setPort(port)
  factory.setUsername(username)
  factory.setPassword(password)

  val connection: Connection = factory.newConnection()
  val channel: Channel = connection.createChannel()
  val requestQueueName: String = "rpc_queue"
  val replyQueueName: String = channel.queueDeclare().getQueue

  def call(message: String): String = {
    val corrId = UUID.randomUUID().toString
    val props = new BasicProperties.Builder().correlationId(corrId)
      .replyTo(replyQueueName)
      .build()
    channel.basicPublish("", requestQueueName, props, message.getBytes("UTF-8"))

    val responseCallback = new ResponseCallback(corrId)
    channel.basicConsume(replyQueueName, true, responseCallback, _ => { })

    responseCallback.take()
  }

  def close() {
    connection.close()
  }
}
