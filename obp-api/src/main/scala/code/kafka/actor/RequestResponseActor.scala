package code.kafka.actor

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, PoisonPill}
import code.util.Helper.MdcLoggable
import shapeless.ops.zipper.Down

import scala.concurrent.duration.DurationInt


object RequestResponseActor {
  case class Request(backendRequestId: String, payload: String)
  case class Response(backendRequestId: String, payload: String)
}

/**
  * This Actor acts in next way:
  * 1. Someone sends a message to it, i.e. "thisActor ? Request(backendRequestId, requestMessage)"
  * 2. The actor log the request
  * 3. The actor immediately start to listen to a Response(backendRequestId, responseMessage)
  *    without returning answer to "thisActor ? Request(backendRequestId, requestMessage)"
  * 4. The actor receives the Response(backendRequestId, responseMessage
  * 5. The actor sends answer to "thisActor ? Request(backendRequestId, requestMessage)"
  * 6. The actor destroy itself
  *
  * Please note that this type of Actor during its life cycle:
  *  - leaves up to 60 seconds
  *  - serves only one Kafka message
  */
class RequestResponseActor extends Actor with ActorLogging with MdcLoggable {
  import RequestResponseActor._

  def receive = waitingForRequest

  private def waitingForRequest: Receive = {
    case Request(backendRequestId, payload) =>
      implicit val ec = context.dispatcher
      val timeout = context.system.scheduler.scheduleOnce(60.second, self, Down)
      context become waitingForResponse(sender, timeout)
      logger.info(s"Request (backendRequestId, payload) = ($backendRequestId, $payload) was sent.")
  }

  private def waitingForResponse(origin: ActorRef, timeout: Cancellable): Receive = {
    case Response(backendRequestId, payload) =>
      timeout.cancel()
      origin ! payload
      self ! PoisonPill
      logger.info(s"Response (backendRequestId, payload) = ($backendRequestId, $payload) was processed.")
    case Down =>
      self ! PoisonPill
      logger.info(s"Actor $self was destroyed by the scheduler.")
  }

}