package code.kafka

import akka.pattern.ask
import code.actorsystem.{ObpActorInit, ObpLookupSystem}
import code.util.Helper.MdcLoggable
import net.liftweb.common.Box
import net.liftweb.json.JValue

import scala.concurrent.Future

object KafkaHelper extends KafkaHelper

trait KafkaHelper extends ObpActorInit with MdcLoggable {

  override val actorName = "KafkaStreamsHelperActor" //CreateActorNameFromClassName(this.getClass.getName)
  override val actor = ObpLookupSystem.getKafkaActor(actorName)
  
  
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
    extractFuture(actor ? request)
  }

  def processToBox[T](request: T): Box[JValue] = {
    extractFutureToBox(actor ? request)
  }

  def processToFuture[T](request: T): Future[JValue] = {
    (actor ? request).mapTo[JValue]
  }

}
