/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.bankconnectors.storedprocedure

import org.json4s._
import java.io.File
import java.util.{Date, TimeZone}

import code.api.ResourceDocs1_4_0.MessageDocsSwaggerDefinitions.successStatus
import code.api.util.APIUtil.MessageDoc
import code.api.util.CustomJsonFormats.formats
import code.api.util.{APIUtil, OptionalFieldSerializer}
import code.bankconnectors.generator.ConnectorBuilderUtil._
import com.openbankproject.commons.model.Status
import com.openbankproject.commons.util.Functions
import com.openbankproject.commons.util.json
import org.json4s.JsonAST.JValue
import org.json4s.{Formats, Serializer, TypeInfo}
import net.liftweb.util.StringHelpers
import org.apache.commons.io.FileUtils

import scala.collection.mutable.ArrayBuffer

/**
 * create ms sql server stored procedure according messageDocs.
 */
object MSsqlStoredProcedureBuilder {
  object StatusSerializer extends Serializer[Status] {

    override def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), Status] = Functions.doNothing

    override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
      case x: Status => json.Extraction.decompose(successStatus)(formats)
    }
  }

  def main(args: Array[String]): Unit = {
    commonMethodNames// do not delete this line, it is to modify "MappedWebUiPropsProvider", to avoid access DB cause dataSource not found exception
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


    // After generatin the code, then exit 
    sys.exit(0)
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
