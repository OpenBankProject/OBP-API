package code.actorsystem

import akka.util.Timeout
import code.api.APIFailure
import net.liftweb.common._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

trait ActorUtils {

  var ACTOR_TIMEOUT: Long = 3
  val TIMEOUT: FiniteDuration = ACTOR_TIMEOUT seconds
  implicit val timeout = Timeout(ACTOR_TIMEOUT * (1000 milliseconds))
  import scala.concurrent.ExecutionContext.Implicits.global

  def extractFuture[T](f: Future[Any]): T = {
    val r = f.map {
      case s: Set[T] => s
      case l: List[T] => l
      case t: T => t
      case _ => Empty ~> APIFailure(s"future extraction failed", 501)
    }
    Await.result(r, TIMEOUT).asInstanceOf[T]
  }

  def extractFutureToBox[T](f: Future[Any]): Box[T] = {
    val r = f.map {
      case pf: ParamFailure[_] => Empty ~> pf
      case af: APIFailure => Empty ~> af
      case f: Failure => f
      case Empty => Empty
      case t: T => Full(t)
      case _ => Empty ~> APIFailure(s"future extraction to box failed", 501)
    }
    Await.result(r, TIMEOUT)
  }

}


trait ActorHelper {

  def extractResult[T](in: T) = {
    in match {
        case pf: ParamFailure[_] =>
          pf.param match {
            case af: APIFailure => af
            case f: Failure => f
            case _ => pf
          }
        case af: APIFailure => af
        case f: Failure => f
        case l: List[T] => l
        case s: Set[T] => s
        case Full(r) => r
        case t: T => t
        case _ => APIFailure(s"result extraction failed", 501)
      }
  }
}