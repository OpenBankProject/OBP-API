package code.bankconnectors

import net.liftweb.common.{Full, _}
import akka.pattern.ask
import akka.util.Timeout
import code.util.Helper.MdcLoggable
import net.liftweb.json.JValue

import scala.concurrent.Await
import scala.concurrent.duration._


object KafkaHelper extends KafkaHelper

trait KafkaHelper extends KafkaHelperActorInit with MdcLoggable {

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

  def process (request: Map[String, String]): JValue ={
     extractFuture(actor ? processRequest(request))
  }

  case class processRequest (
                            request: Map[String, String]
                            )

}
