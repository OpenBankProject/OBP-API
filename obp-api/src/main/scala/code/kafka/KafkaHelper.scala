package code.kafka

import akka.pattern.{AskTimeoutException, ask}
import code.actorsystem.{ObpActorInit, ObpLookupSystem}
import code.api.APIFailureNewStyle
import code.api.util.APIUtil.{fullBoxOrException, gitCommit, unboxFull, unboxFullOrFail}
import code.api.util.{APIUtil, CallContext, CustomJsonFormats}
import code.api.util.ErrorMessages._
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.{ObpApiLoopback, TopicTrait}
import net.liftweb
import net.liftweb.common._
import net.liftweb.json
import net.liftweb.json.JsonAST.JNull
import net.liftweb.json.{Extraction, JValue, MappingException}

import scala.concurrent.Future
import net.liftweb.json.JsonParser.ParseException
import net.liftweb.util.Helpers
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.errors._

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

  /**
    * This function is used for Old Style Endpoints.
    * It processes Kafka's Outbound message to JValue.
    * @param request The request we send to Kafka
    * @return Kafka's Inbound message as JValue
    */
  def process (request: Map[String, String]): JValue = {
    val boxedJValue = processToBox(request)
    fullBoxOrException(boxedJValue)
    // fullBoxOrException(boxedJValue) already process Empty and Failure, So the follow throw exception message just a stub.
    boxedJValue.openOrThrowException("future extraction to box failed")
  }

  /**
    * This function is used for Old Style Endpoints at Kafka connector.
    * It processes Kafka's Outbound message to JValue wrapped into Box.
    * @param request The request we send to Kafka
    * @return Kafka's Inbound message as JValue wrapped into Box
    */
  def processToBox(request: Any): Box[JValue] = {
    extractFutureToBox[JValue](actor ? request)
  }

  /**
    * This function is used for Old Style Endpoints at Kafka connector.
    * It processes Kafka's Outbound message to JValue wrapped into Box.
    * @param request The request we send to Kafka
    * @tparam T the type of the Outbound message
    * @return Kafka's Inbound message as JValue wrapped into Future
    */
  def processToFuture[T](request: T): Future[JValue] = {
    (actor ? request).mapTo[JValue]
  }
  /**
    * This function is used for send request to kafka, and get the result extract to Box result.
    * It processes Kafka's Outbound message to JValue wrapped into Box.
    * @param request The request we send to Kafka
    * @tparam T the type of the Inbound message
    * @return Kafka's Inbound message into Future
    */
  def processRequest[T: Manifest](request: TopicTrait): Future[Box[T]] = {
    import com.openbankproject.commons.ExecutionContext.Implicits.global
    import liftweb.json.compactRender
    implicit val formats = CustomJsonFormats.nullTolerateFormats
    val tp = manifest[T].runtimeClass

    (actor ? request)
      .mapTo[JValue]
      .map {jvalue =>
        try {
          if (jvalue == JNull)
            throw new Exception("Adapter can not return `null` value to OBP-API!")
          else 
            Full(jvalue.extract[T])
        } catch {
          case e: Exception => {
            val errorMsg = s"${InvalidConnectorResponse} extract response payload to type ${tp} fail. the payload content: ${compactRender(jvalue)}. $e"
            sendOutboundAdapterError(errorMsg, request)

            Failure(errorMsg, Full(e), Empty)
          }
        }
      }
      .recoverWith {
        case e: ParseException => {
          val errorMsg = s"${InvalidConnectorResponse} parse response payload to JValue fail. ${e.getMessage}"
          sendOutboundAdapterError(errorMsg, request)

          Future(Failure(errorMsg, Box !! (e.getCause) or Full(e), Empty))
        }
        case e: AskTimeoutException => {
          echoKafkaServer
            .map { _ => {
                val errorMsg = s"${AdapterUnknownError} Timeout error, because Adapter do not return proper message to Kafka. ${e.getMessage}"
                sendOutboundAdapterError(errorMsg, request)
                Failure(errorMsg, Full(e), Empty)
              }
            }
            .recover{
              case e: Throwable => Failure(s"${KafkaServerUnavailable} Timeout error, because kafka do not return message to OBP-API. ${e.getMessage}", Full(e), Empty)
            }
        }
        case e @ (_:AuthenticationException| _:AuthorizationException|
                  _:IllegalStateException| _:InterruptException|
                  _:SerializationException| _:TimeoutException|
                  _:KafkaException| _:ApiException)
          => Future(Failure(s"${KafkaUnknownError} OBP-API send message to kafka server failed. ${e.getMessage}", Full(e), Empty))
      }
  }

  def sendOutboundAdapterError(error: String): Unit = actor ! OutboundAdapterError(error)

  def sendOutboundAdapterError(error: String, request: TopicTrait): Unit = {
    implicit val formats = CustomJsonFormats.formats
    val requestJson =json.compactRender(Extraction.decompose(request))
    s"""$error
       |The request is: ${requestJson}
     """.stripMargin
  }

  /**
    * check Kafka server, where send and request success
    * @return ObpApiLoopback with duration
    */
  def echoKafkaServer: Future[ObpApiLoopback] = {
    import com.openbankproject.commons.ExecutionContext.Implicits.global
    implicit val formats = CustomJsonFormats.formats
    for{
      connectorVersion <- Future {APIUtil.getPropsValue("connector").openOrThrowException("connector props field `connector` not set")}
      startTime = Helpers.now
      req = ObpApiLoopback(connectorVersion, gitCommit, "")
      obpApiLoopbackRespons <- (actor ? req)
        .map(_.asInstanceOf[JValue].extract[ObpApiLoopback])
        .map(_.copy(durationTime = (Helpers.now.getTime - startTime.getTime).toString))
    } yield {
      obpApiLoopbackRespons
    }
  }
}
