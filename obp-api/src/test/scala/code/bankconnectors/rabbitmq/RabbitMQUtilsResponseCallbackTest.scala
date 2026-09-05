package code.bankconnectors.rabbitmq

import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.{Channel, Delivery, Envelope}

import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * O3: ResponseCallback.handle must complete the promise even when closing the channel fails.
 *
 * THE HAZARD (before the fix): `promise.success { ...; throw new RuntimeException(...) }` — the
 * throw happens while evaluating the argument to `success`, so a channel-close failure meant the
 * promise was never completed at all, and the exception escaped onto the RabbitMQ client dispatch
 * thread. The fix completes the promise (`trySuccess`) BEFORE attempting to close the channel, so
 * a close failure is merely logged and never blocks the waiting caller.
 *
 * No broker needed: `channel` is a dynamic proxy whose `close()` always throws, verifying the
 * promise still resolves with the delivered message body.
 */
class RabbitMQUtilsResponseCallbackTest extends AnyFlatSpec with Matchers {

  private def channelWithFailingClose(): Channel = {
    val handler = new InvocationHandler {
      override def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef = {
        method.getName match {
          case "isOpen"   => Boolean.box(true)
          case "close"    => throw new java.io.IOException("simulated channel close failure")
          case "hashCode" => Int.box(System.identityHashCode(proxy))
          case "equals"   => Boolean.box(proxy eq (if (args != null) args(0) else null))
          case "toString" => "FakeChannel"
          case _          => null
        }
      }
    }
    Proxy.newProxyInstance(
      classOf[Channel].getClassLoader,
      Array(classOf[Channel]),
      handler
    ).asInstanceOf[Channel]
  }

  "ResponseCallback.handle" should "complete the promise with the message body even when channel.close() throws" in {
    val correlationId = UUID.randomUUID().toString
    val channel = channelWithFailingClose()
    val callback = new ResponseCallback(correlationId, channel)

    val properties = new BasicProperties.Builder().correlationId(correlationId).build()
    val envelope = new Envelope(1L, false, "", "")
    val body = "hello from adapter".getBytes("UTF-8")
    val delivery = new Delivery(envelope, properties, body)

    // Must not throw, even though channel.close() will fail internally.
    callback.handle("consumer-tag", delivery)

    val result = Await.result(callback.take(), 5.seconds)
    result shouldBe "hello from adapter"
  }

  it should "ignore deliveries whose correlationId does not match (promise stays incomplete)" in {
    val correlationId = UUID.randomUUID().toString
    val otherCorrelationId = UUID.randomUUID().toString
    val channel = channelWithFailingClose()
    val callback = new ResponseCallback(correlationId, channel)

    val properties = new BasicProperties.Builder().correlationId(otherCorrelationId).build()
    val envelope = new Envelope(1L, false, "", "")
    val delivery = new Delivery(envelope, properties, "irrelevant".getBytes("UTF-8"))

    callback.handle("consumer-tag", delivery)

    callback.promise.isCompleted shouldBe false
  }
}
