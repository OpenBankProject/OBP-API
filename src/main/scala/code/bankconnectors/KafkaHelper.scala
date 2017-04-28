package code.bankconnectors

import akka.actor.ActorSelection
import net.liftweb.common.{Full, _}
import akka.pattern.ask
import akka.util.Timeout
import code.api.APIFailure
import code.util.Helper.MdcLoggable
import net.liftweb.json.JValue
import net.liftweb.util.Props

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object KafkaHelper extends KafkaHelper

trait KafkaHelper extends MdcLoggable {

  // Deafult is 3 seconds, which should be more than enough for slower systems
  val ACTOR_TIMEOUT: Long = Props.getLong("connector.timeout").openOr(3)
  val TIMEOUT: FiniteDuration = ACTOR_TIMEOUT seconds


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

  val actorName: String = CreateActorNameFromClassName(this.getClass.getName)
  val actor: ActorSelection = KafkaHelperLookupSystem.getKafkaHelperActor(actorName)

  def CreateActorNameFromClassName(c: String): String = {
    val n = c.substring(c.lastIndexOf('.') + 1 ).replaceAll("\\$", "")
    val name = Character.toLowerCase(n.charAt(0)) + n.substring(1)
    name
  }

  /**
    * Have this function just to keep compatibility for KafkaMappedConnector_vMar2017 and  KafkaMappedConnector.scala
    * In KafkaMappedConnector.scala, we use Map[String, String]. Now we change to case class
    * eg: case class Company(name: String, address: String) -->
    * Company("TESOBE","Berlin")
    * Map(name->"TESOBE", address->"2")
    *
    * @param caseClassObject
    * @return Map[String, String]
    */
  def transferCaseClassToMap(caseClassObject: scala.Product) =
    caseClassObject.getClass.getDeclaredFields.map(_.getName) // all field names
    .zip(caseClassObject.productIterator.to).toMap.asInstanceOf[Map[String, String]] // zipped with all values

  def process(request: scala.Product): JValue = {
    val mapRequest:Map[String, String] = transferCaseClassToMap(request)
    process(mapRequest)
  }

  implicit val timeout: Timeout = Timeout(ACTOR_TIMEOUT * (1000 milliseconds))
  def process (request: Map[String, String]): JValue ={
    extractFuture(actor ? processRequest(request))
  }

  case class processRequest (
                            request: Map[String, String]
                            )

}
