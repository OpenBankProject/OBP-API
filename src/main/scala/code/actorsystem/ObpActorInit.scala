package code.actorsystem

import akka.util.Timeout
import code.api.util.APIUtil
import code.util.Helper.MdcLoggable

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

trait ObpActorInit extends MdcLoggable{
  // Default is 3 seconds, which should be more than enough for slower systems
  val ACTOR_TIMEOUT: Long = APIUtil.getPropsAsLongValue("remotedata.timeout").openOr(3)

  val actorName = CreateActorNameFromClassName(this.getClass.getName)
  val actor = ObpLookupSystem.getRemotedataActor(actorName)
  logger.debug(s"Create this Actor: $actorName: ${actor}")
  val TIMEOUT = (ACTOR_TIMEOUT seconds)
  implicit val timeout = Timeout(ACTOR_TIMEOUT * (1000 milliseconds))
  
  def getValueFromFuture[T](f: Future[T]): T = {
    Await.result(f, TIMEOUT)
  }

  def CreateActorNameFromClassName(c: String): String = {
    val n = c.replaceFirst("^.*Remotedata", "").replaceAll("\\$.*", "")
    Character.toLowerCase(n.charAt(0)) + n.substring(1)
  }

}