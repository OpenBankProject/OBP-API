package code.api.ResourceDocs1_4_0

import code.api.util.APIUtil.ResourceDoc
import code.api.Constant._
import net.liftweb.json._
import net.liftweb.util.Props
import org.pegdown.PegDownProcessor

import scala.collection.immutable.ListMap

object SwaggerJSONFactory {

  case class ContactJson(
                          name: String,
                          url: String
                          )

  case class InfoJson(
                       title: String,
                       description: String,
                       contact: ContactJson,
                       version: String
                       )

  case class ResponseObjectSchemaJson(`$ref`: String)
  case class ResponseObjectJson(description: Option[String], schema: Option[ResponseObjectSchemaJson])

  case class MethodJson(tags: List[String],
                        summary: String,
                        description: String,
                        operationId: String,
                        responses: Map[String, ResponseObjectJson])

  case class PathsJson(get: MethodJson)

  case class MessageJson(`type`: String)

  case class CodeJson(`type`: String, format: String)

  case class PropertiesJson(code: CodeJson, message: MessageJson)

  case class ErrorDefinitionJson(`type`: String, required: List[String], properties: PropertiesJson)

  case class DefinitionsJson(Error: ErrorDefinitionJson)

  case class SwaggerResourceDoc(swagger: String,
                                info: InfoJson,
                                host: String,
                                basePath: String,
                                schemes: List[String],
                                paths: Map[String, Map[String, MethodJson]],
                                definitions: DefinitionsJson
                                 )

  def createSwaggerResourceDoc(resourceDocList: List[ResourceDoc], requestedApiVersion: String): SwaggerResourceDoc = {
    implicit val formats = DefaultFormats

    val pegDownProcessor : PegDownProcessor = new PegDownProcessor

    val contact = ContactJson("TESOBE Ltd. / Open Bank Project", "https://openbankproject.com")
    val apiVersion = requestedApiVersion
    val title = "Open Bank Project API"
    val description = "An Open Source API for Banks. (c) TESOBE Ltd. 2011 - 2016. Licensed under the AGPL and commercial licences."
    val info = InfoJson(title, description, contact, apiVersion)
    val host = Props.get("hostname", "unknown host").replaceFirst("http://", "").replaceFirst("https://", "")
    val basePath = s"/$ApiPathZero/" + apiVersion
    val schemas = List("http", "https")
    val paths: ListMap[String, Map[String, MethodJson]] = resourceDocList.groupBy(x => x.requestUrl).toSeq.sortBy(x => x._1).map { mrd =>
      val methods: Map[String, MethodJson] = mrd._2.map(rd =>
        (rd.requestVerb.toLowerCase,
          MethodJson(
            List(s"${rd.apiVersion.toString}"),
            rd.summary,
            description = pegDownProcessor.markdownToHtml(rd.description.stripMargin).replaceAll("\n", ""),
            s"${rd.apiVersion.toString}-${rd.apiFunction.toString}",
            Map("200" -> ResponseObjectJson(Some("Success") , None), "400" -> ResponseObjectJson(Some("Error"), Some(ResponseObjectSchemaJson("#/definitions/Error"))))))
      ).toMap
      (mrd._1, methods.toSeq.sortBy(m => m._1).toMap)
    }(collection.breakOut)

    val `type` = "object"
    val required = List("code", "message")
    val code = CodeJson("integer", "int32")
    val message = MessageJson("string")
    val properties = PropertiesJson(code, message)
    val errorDef = ErrorDefinitionJson(`type`, required, properties)
    val defs = DefinitionsJson(errorDef)

    SwaggerResourceDoc("2.0", info, host, basePath, schemas, paths, defs)
  }

}