package code.bankconnectors.rabbitmq.Adapter

import code.api.ResourceDocs1_4_0.MessageDocsSwaggerDefinitions.{outboundAdapterAuthInfo, successStatus}
import code.api.util.APIUtil.MessageDoc
import code.api.util.CustomJsonFormats.formats
import code.api.util.{APIUtil, NewStyle, OptionalFieldSerializer}
import code.bankconnectors.{Connector, ConnectorBuilderUtil}
import code.bankconnectors.Connector.connector
import code.bankconnectors.ConnectorBuilderUtil._
import code.bankconnectors.rabbitmq.RabbitMQConnector_vOct2024
import com.openbankproject.commons.model.{Status, TopicTrait}
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

    val codeList = messageDocs
      //these are only for debugging.
//      .filterNot(_.process.equals("obp.getBankAccountOld"))//getBanks is the template code, already in the code.
//      .filter(_.process.equals("obp.getCustomers"))//getBanks is the template code, already in the code.
//      .take(80)
//      .slice(91,1000)
      .map(
        doc => {
          val processName = doc.process
          val outBoundExample = doc.exampleOutboundMessage
          val inBoundExample = doc.exampleInboundMessage
          buildConnectorStub(processName, outBoundExample, inBoundExample)
        })


    println("-------------------")
    codeList.foreach(println(_))
    println("===================")

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
    println("finished")

    sys.exit(0)  // Pass 0 for a normal termination, other codes for abnormal termination
  }

  def buildConnectorStub(processName: String, outBoundExample: scala.Product, inBoundExample: scala.Product) = {
    val allParameters: List[(String, Any)] = outBoundExample.asInstanceOf[TopicTrait].nameToValue
    
    val paginationParameters = List("limit","offset","fromDate","toDate")
    
    val parametersRemovedPagination = allParameters.filterNot(parameter => paginationParameters.contains(parameter._1))
      
    val parameters = 
      //do not need the 1st parameter "outboundAdapterCallContext" so remove it.
      if(allParameters.nonEmpty && allParameters.head._1.equals("outboundAdapterCallContext"))
        parametersRemovedPagination.drop(1)
      else
        parametersRemovedPagination
    
    val parameterString = 
      if(parameters.isEmpty) 
        "" 
      else
        parameters.map(_._1).mkString("outBound.",",outBound.",",")
          .replace(".type",".`type`") //type is the keyword in scala. so must be use `type` instead
    
    // eg: processName= obp.getAtms --> connectorMethodName = getAtms
    val connectorMethodName= processName.replace("obp.","") //eg: getBank
    val connectorMethodNameCapitalized = connectorMethodName.capitalize // eg: GetBank 

    //the connector method return types are different, eg: OBPReturnType[Box[T]], Future[Box[(T, Option[CallContext])]],,,
    // we need to prepare different code for them.
    val methodSymbol = NewStyle.function.getConnectorMethod("mapped", connectorMethodName)
    val returnType = methodSymbol.map(_.returnType)
    val returnTypeString = returnType.map(_.toString).getOrElse("")
    
    val obpMappedResponse = if (connectorMethodName == "dynamicEntityProcess") 
      s"code.bankconnectors.LocalMappedConnector.$connectorMethodName(${parameterString}None,None,None,false,None).map(_._1.head)" 
    else if (returnTypeString.contains("OBPReturnType") && allParameters.exists(_._1 == "limit") && connectorMethodName !="getTransactions") 
      s"code.bankconnectors.LocalMappedConnector.$connectorMethodName(${parameterString}Nil,None).map(_._1.head)" 
    else if(returnTypeString.contains("OBPReturnType")&&allParameters.head._1.equals("outboundAdapterCallContext"))
      s"code.bankconnectors.LocalMappedConnector.$connectorMethodName(${parameterString}None).map(_._1.head)" 
    else if(!returnTypeString.contains("OBPReturnType") && !returnTypeString.contains("Future") && returnTypeString.contains("Box[(") && allParameters.head._1.equals("outboundAdapterCallContext"))
      s"Future{code.bankconnectors.LocalMappedConnector.$connectorMethodName(${parameterString}None).map(_._1).head}"
    else if(!returnTypeString.contains("OBPReturnType") && !returnTypeString.contains("Future") && returnTypeString.contains("Box[(") && !allParameters.head._1.equals("outboundAdapterCallContext"))
      s"Future{code.bankconnectors.LocalMappedConnector.$connectorMethodName(${parameterString.dropRight(1)}).map(_._1).head}"
    else if(!returnTypeString.contains("OBPReturnType") && !returnTypeString.contains("Future") && returnTypeString.contains("Box[") && !allParameters.head._1.equals("outboundAdapterCallContext"))
      s"Future{code.bankconnectors.LocalMappedConnector.$connectorMethodName(${parameterString.dropRight(1)}).head}"
    else if(!returnTypeString.contains("OBPReturnType") && returnTypeString.contains("scala.concurrent.Future[net.liftweb.common.Box[") && !returnTypeString.contains("scala.concurrent.Future[net.liftweb.common.Box[("))
      s"code.bankconnectors.LocalMappedConnector.$connectorMethodName(${parameterString}None).map(_.head)"
    else 
      s"code.bankconnectors.LocalMappedConnector.$connectorMethodName(${parameterString}None).map(_.map(_._1).head)"

    val isDataFieldBoolean = inBoundExample.toString.endsWith(",true)") || inBoundExample.toString.endsWith(",false)")
    
    
    //the InBound.data field sometimes can not be null .we need to prepare the proper value for it. this is only use for errro handling.
    // eg: inBoundExample.toString = InBoundValidateChallengeAnswer(InboundAdapterCallContext(....,true)
    val data = if(isDataFieldBoolean) 
      false 
    else 
      null
    
    val inboundAdapterCallContext = if(ConnectorBuilderUtil.specialMethods.contains(connectorMethodName) ||
      connectorMethodName == "getPhysicalCardsForUser" // this need to be check, InBoundGetPhysicalCardsForUser is missing inboundAdapterCallContext field.
    )
      ""
    else
      """          
        |          
        |          inboundAdapterCallContext = InboundAdapterCallContext(
        |            correlationId = outBound.outboundAdapterCallContext.correlationId
        |          ),""".stripMargin
    
    s"""
       |      } else if (obpMessageId.contains("${StringHelpers.snakify(connectorMethodName)}")) {
       |        val outBound = json.parse(message).extract[OutBound$connectorMethodNameCapitalized]
       |        val obpMappedResponse = $obpMappedResponse
       |        
       |        obpMappedResponse.map(response => InBound$connectorMethodNameCapitalized($inboundAdapterCallContext
       |          status = Status("", Nil),
       |          data = response
       |        )).recoverWith {
       |          case e: Exception => Future(InBound$connectorMethodNameCapitalized($inboundAdapterCallContext
       |            status = Status(e.getMessage, Nil),
       |            data = $data
       |          ))
       |        }""".stripMargin
  }

}
