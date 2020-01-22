package code.actorsystem

import akka.util.Timeout
import code.api.APIFailure
import code.api.util.APIUtil
import code.util.Helper.MdcLoggable
import net.liftweb.common._

import com.openbankproject.commons.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.reflect.ClassTag

trait ObpActorInit extends MdcLoggable{
  // Default is 3 seconds, which should be more than enough for slower systems
  val ACTOR_TIMEOUT: Long = APIUtil.getPropsAsLongValue("remotedata.timeout").openOr(3)

  val actorName = CreateActorNameFromClassName(this.getClass.getName)
  val actor = ObpLookupSystem.getRemotedataActor(actorName)
  logger.debug(s"Create this Actor: $actorName: ${actor}")
  val TIMEOUT = (ACTOR_TIMEOUT seconds)
  implicit val timeout = Timeout(ACTOR_TIMEOUT * (1000 milliseconds))

  /**
    * This function extracts the payload from Future and wraps it to Box.
    * It is used for Old Style Endpoints at Kafka connector.
    * @param f The payload wrapped into Future
    * @tparam T The type of the payload
    * @return The payload wrapped into Box
    */
  def extractFutureToBox[T: ClassTag](f: Future[Any]): Box[T] = {
    val r: Future[Box[T]] = f.map {
      case f@ (_: ParamFailure[_] | _: APIFailure) => Empty ~> f
      case f: Failure => f
      case Empty => Empty
      case t: T => Full(t)
      case _ => Empty ~> APIFailure("future extraction to box failed", 501)
    }
    
    Await.result(r, TIMEOUT)
    
  }

  def getValueFromFuture[T](f: Future[T]): T = {
    Await.result(f, TIMEOUT)
  }

  def CreateActorNameFromClassName(c: String): String = {
    val n = c.replaceFirst("^.*Remotedata", "").replaceAll("\\$.*", "")
    Character.toLowerCase(n.charAt(0)) + n.substring(1)
  }

}