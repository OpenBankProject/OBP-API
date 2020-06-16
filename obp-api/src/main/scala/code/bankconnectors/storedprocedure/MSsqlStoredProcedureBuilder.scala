package code.bankconnectors.storedprocedure

import java.io.File
import java.util.Date

import code.api.util.APIUtil.MessageDoc
import code.bankconnectors.ConnectorBuilderUtil.{getClass, _}
import com.openbankproject.commons.model.Status
import com.openbankproject.commons.util.Functions
import net.liftweb.json
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.{Formats, JString, MappingException, Serializer, TypeInfo}
import code.api.ResourceDocs1_4_0.MessageDocsSwaggerDefinitions.successStatus
import code.api.util.APIUtil
import code.api.util.CustomJsonFormats.formats
import com.openbankproject.commons.model.Status
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
    implicit val customFormats = formats + StatusSerializer
    val messageDocs: ArrayBuffer[MessageDoc] = StoredProcedureConnector_vDec2019.messageDocs
    def toProcedureName(processName: String) = StringHelpers.snakify(processName.replace("obp.", ""))
    def inboundToJson(any: Any) = json.prettyRender(json.Extraction.decompose(any))
    val procedureNameToInbound = messageDocs.map(doc => {
      val procedureName = toProcedureName(doc.process)
      val inBoundExample = inboundToJson(doc.exampleInboundMessage)
      buildProcedure(procedureName, inBoundExample)
    }).mkString(s"-- auto generated MS sql server procedures script, create on ${APIUtil.DateWithSecondsFormat.format(new Date())}", " \n \n", "")

    val path = new File(getClass.getResource("").toURI.toString.replaceFirst("target/.*", "").replace("file:", ""),
    "src/main/scala/code/bankconnectors/storedprocedure/MSsqlStoredProcedure.sql")
    val source = FileUtils.write(path, procedureNameToInbound, "utf-8")
  }

  def buildProcedure(processName: String, inBoundExample: String) = {
    s"""
      |
      |-- drop procedure $processName
      |DROP PROCEDURE IF EXISTS $processName;
      |GO
      |-- create procedure $processName
      |CREATE PROCEDURE $processName
      |@out_bound_json NVARCHAR(MAX),
      |@in_bound_json NVARCHAR(MAX) OUT
      |AS
      |	SET nocount on
      |
      |-- replace the follow example to real logic
      |
      |	SELECT @in_bound_json = (
      |		SELECT
      |     N'${inBoundExample.replaceAll("(?m)^", "     ").trim()}'
      |	);
      |GO
      |
      |""".stripMargin
  }

}
