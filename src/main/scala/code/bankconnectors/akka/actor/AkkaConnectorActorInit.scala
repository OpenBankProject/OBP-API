package code.bankconnectors.akka.actor

import akka.util.Timeout
import code.api.util.APIUtil
import code.util.Helper.MdcLoggable

import scala.concurrent.duration._

trait AkkaConnectorActorInit extends MdcLoggable{
  // Default is 3 seconds, which should be more than enough for slower systems
  val ACTOR_TIMEOUT: Long = APIUtil.getPropsAsLongValue("akka_connector.timeout").openOr(3)
  implicit val timeout = Timeout(ACTOR_TIMEOUT * (1000 milliseconds))
}