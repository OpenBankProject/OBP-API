package code.bankconnectors.rabbitmq.Adapter

import code.api.ResourceDocs1_4_0.MessageDocsSwaggerDefinitions.successStatus
import code.api.util.APIUtil.MessageDoc
import code.api.util.CustomJsonFormats.formats
import code.api.util.{APIUtil, OptionalFieldSerializer}
import code.bankconnectors.ConnectorBuilderUtil._
import code.bankconnectors.rabbitmq.RabbitMQConnector_vOct2024
import com.openbankproject.commons.model.Status
import com.openbankproject.commons.util.Functions
import net.liftweb.json
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.{Formats, Serializer, TypeInfo}
import net.liftweb.util.StringHelpers
import org.apache.commons.io.FileUtils

import java.io.File
import java.util.{Date, TimeZone}
import scala.collection.mutable.ArrayBuffer

/**
 * create ms sql server stored procedure according messageDocs.
 */
object AdapterStubBuilder {
  specialMethods // this line just for modify "MappedWebUiPropsProvider"
  object StatusSerializer extends Serializer[Status] {

    override def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), Status] = Functions.doNothing

    override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
      case x: Status => json.Extraction.decompose(successStatus)(formats)
    }
  }

  def main(args: Array[String]): Unit = {
    // Boot.scala set default TimeZone, So here need also fix the TimeZone to make example Date is a fix value,
    // not affect by local TimeZone.
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    val messageDocs: ArrayBuffer[MessageDoc] = RabbitMQConnector_vOct2024.messageDocs

    val codeList = messageDocs.map(doc => {
      val processName = doc.process
      val outBoundExample = doc.exampleOutboundMessage
      val inBoundExample = doc.exampleInboundMessage
      buildConnectorStub(processName, outBoundExample, inBoundExample)
    })

    
//    val source = FileUtils.write(path, procedureNameToInbound, "utf-8")

    //  private val types: Iterable[ru.Type] = symbols.map(_.typeSignature)
    //  println(symbols)
//    println("-------------------")
//    codeList.foreach(println(_))
//    println("===================")
    
    val path = new File(getClass.getResource("").toURI.toString.replaceFirst("target/.*", "").replace("file:", ""),
      "src/main/scala/code/bankconnectors/rabbitmq/Adapter/RPCServer.scala")
    val source = FileUtils.readFileToString(path, "utf-8")
    val start = "//---------------- dynamic start -------------------please don't modify this line"
    val end   = "//---------------- dynamic end ---------------------please don't modify this line"
    val placeHolderInSource = s"""(?s)$start.+$end"""
    val currentTime = APIUtil.DateWithSecondsFormat.format(new Date())
    val insertCode =
      s"""$start
         |// ---------- created on $currentTime
         |${codeList.mkString}
         |// ---------- created on $currentTime
         |$end """.stripMargin
    val newSource = source.replaceFirst(placeHolderInSource, insertCode)
    FileUtils.writeStringToFile(path, newSource, "utf-8")
    
    
  }

  def buildConnectorStub(processName: String, outBoundExample: scala.Product, inBoundExample: scala.Product) = {
    // eg: processName= obp.getAtms --> partialFunctionName = getAtms
    val partialFunctionName= processName.replace("obp.","")
    val partialFunctionNameCapitalized = partialFunctionName.capitalize
    
    //the difficult is the parameters.
    
    s"""} else if (obpMessageId.contains("${StringHelpers.snakify(partialFunctionName)}")) {
       |        val outBound = json.parse(message).extract[OutBound$partialFunctionNameCapitalized]
       |        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.$partialFunctionName(outBound.bankId, None, Nil).map(_.map(_._1).head)
       |        
       |        obpMappedResponse.map(response => InBound$partialFunctionNameCapitalized(
       |          inboundAdapterCallContext = InboundAdapterCallContext(
       |            correlationId = outBound.outboundAdapterCallContext.correlationId
       |          ),
       |          status = Status("", Nil),
       |          data = response
       |        )).recoverWith {
       |          case e: Exception => Future(InBound$partialFunctionNameCapitalized(
       |            inboundAdapterCallContext = InboundAdapterCallContext(
       |              correlationId = outBound.outboundAdapterCallContext.correlationId
       |            ),
       |            status = Status(e.getMessage, Nil),
       |            data = null
       |          ))
       |        }""".stripMargin
  }

}
