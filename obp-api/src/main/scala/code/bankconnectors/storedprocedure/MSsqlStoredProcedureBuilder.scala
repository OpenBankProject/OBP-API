package code.bankconnectors.storedprocedure

import java.io.File
import java.util.{Date, TimeZone}

import code.api.ResourceDocs1_4_0.MessageDocsSwaggerDefinitions.successStatus
import code.api.util.APIUtil.MessageDoc
import code.api.util.CustomJsonFormats.formats
import code.api.util.{APIUtil, OptionalFieldSerializer}
import code.bankconnectors.ConnectorBuilderUtil._
import com.openbankproject.commons.model.Status
import com.openbankproject.commons.util.Functions
import net.liftweb.json
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.{Formats, Serializer, TypeInfo}
import net.liftweb.util.StringHelpers
import org.apache.commons.io.FileUtils

import scala.collection.mutable.ArrayBuffer

/**
 * create ms sql server stored procedure according messageDocs.
 */
object MSsqlStoredProcedureBuilder {
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
    implicit val customFormats = formats + StatusSerializer + OptionalFieldSerializer
    val messageDocs: ArrayBuffer[MessageDoc] = StoredProcedureConnector_vDec2019.messageDocs
    def toProcedureName(processName: String) = StringHelpers.snakify(processName.replace("obp.", "obp_"))
    def toJson(any: Any) = json.prettyRender(json.Extraction.decompose(any))
    val procedureNameToInbound = messageDocs.map(doc => {
      val procedureName = toProcedureName(doc.process)
      val outBoundExample = toJson(doc.exampleOutboundMessage)
      val inBoundExample = toJson(doc.exampleInboundMessage)
      buildProcedure(procedureName, outBoundExample, inBoundExample)
    }).mkString(s"-- auto generated MS sql server procedures script, create on ${APIUtil.DateWithSecondsFormat.format(new Date())}", " \n \n", "")

    val path = new File(getClass.getResource("").toURI.toString.replaceFirst("target/.*", "").replace("file:", ""),
    "src/main/scala/code/bankconnectors/storedprocedure/MSsqlStoredProcedure.sql")
    val source = FileUtils.write(path, procedureNameToInbound, "utf-8")
  }

  def buildProcedure(processName: String, outBoundExample: String, inBoundExample: String) = {
    s"""
      |
      |-- drop procedure $processName
      |DROP PROCEDURE IF EXISTS $processName;
      |GO
      |-- create procedure $processName
      |CREATE PROCEDURE $processName
      |   @outbound_json NVARCHAR(MAX),
      |   @inbound_json NVARCHAR(MAX) OUT
      |   AS
      |	  SET nocount on
      |
      |-- replace the follow example to real logic
      |/*
      |this is example of parameter @outbound_json
      |     N'${outBoundExample.replaceAll("(?m)^", "     ").trim()}'
      |*/
      |
      |-- return example value
      |	SELECT @inbound_json = (
      |		SELECT
      |     N'${inBoundExample.replaceAll("(?m)^", "     ").trim()}'
      |	);
      |GO
      |
      |""".stripMargin
  }

}
