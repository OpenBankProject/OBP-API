package code.api.ResourceDocs1_4_0

import java.util.Date

import code.api.Constant._
import code.api.util.APIUtil.ResourceDoc
import net.liftweb
import net.liftweb.json.Extraction._
import net.liftweb.json._
import net.liftweb.util.Props
import org.pegdown.PegDownProcessor

import scala.collection.immutable.ListMap
import scala.reflect.runtime.currentMirror
import scala.reflect.runtime.universe._

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

    def getName(rd: ResourceDoc) = {
      rd.apiFunction match {
        case "allBanks" => Some(ResponseObjectSchemaJson("#/definitions/BankJSON"))
        case _ => None
      }
    }

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
            Map("200" -> ResponseObjectJson(Some("Success") , getName(rd)), "400" -> ResponseObjectJson(Some("Error"), Some(ResponseObjectSchemaJson("#/definitions/Error"))))))
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


  def translateEntity(entity: Any): String = {

    val r = currentMirror.reflect(entity)
    val ddd = r.symbol.typeSignature.members.toStream
      .collect { case s: TermSymbol if !s.isMethod => r.reflectField(s)}
      .map(r => r.symbol.name.toString.trim -> r.get)
      .toMap

    val properties = for ((key, value) <- ddd) yield {
      value match {
        case i: Boolean => "\"" + key + "\":" + """{"type":"boolean"}"""
        case Some(i: Boolean) => "\"" + key + "\":" + """{"type":"boolean"}"""
        case List(i: Boolean, _*) => "\"" + key + "\":" + """{"type":"array", "items":{"type": "boolean"}}"""
        case Some(List(i: Boolean, _*)) => "\"" + key + "\":" + """{"type":"array", "items":{"type": "boolean"}}"""
        case i: String => "\"" + key + "\":" + """{"type":"string"}"""
        case Some(i: String) => "\"" + key + "\":" + """{"type":"string"}"""
        case List(i: String, _*) => "\"" + key + "\":" + """{"type":"array", "items":{"type": "string"}}"""
        case Some(List(i: String, _*)) => "\"" + key + "\":" + """{"type":"array", "items":{"type": "string"}}"""
        case i: Int => "\"" + key + "\":" + """{"type":"integer", "format":"int32"}"""
        case Some(i: Int) => "\"" + key + "\":" + """{"type":"integer", "format":"int32"}"""
        case List(i: Long, _*) => "\"" + key + "\":" + """{"type":"array", "items":{"type":"integer", "format":"int32"}}"""
        case Some(List(i: Long, _*)) => "\"" + key + "\":" + """{"type":"array", "items":{"type":"integer", "format":"int32"}}"""
        case i: Long => "\"" + key + "\":" + """{"type":"integer", "format":"int64"}"""
        case Some(i: Long) => "\"" + key + "\":" + """{"type":"integer", "format":"int64"}"""
        case List(i: Long, _*) => "\"" + key + "\":" + """{"type":"array", "items":{"type":"integer", "format":"int64"}}"""
        case Some(List(i: Long, _*)) => "\"" + key + "\":" + """{"type":"array", "items":{"type":"integer", "format":"int64"}}"""
        case i: Float => "\"" + key + "\":" + """{"type":"number", "format":"float"}"""
        case Some(i: Float) => "\"" + key + "\":" + """{"type":"number", "format":"float"}"""
        case List(i: Float, _*) => "\"" + key + "\":" + """{"type":"array", "items":{"type": "float"}}"""
        case Some(List(i: Float, _*)) => "\"" + key + "\":" + """{"type":"array", "items":{"type": "float"}}"""
        case i: Double => "\"" + key + "\":" + """{"type":"number", "format":"double"}"""
        case Some(i: Double) => "\"" + key + "\":" + """{"type":"number", "format":"double"}"""
        case List(i: Double, _*) => "\"" + key + "\":" + """{"type":"array", "items":{"type": "double"}}"""
        case Some(List(i: Double, _*)) => "\"" + key + "\":" + """{"type":"array", "items":{"type": "double"}}"""
        case i: Date => "\"" + key + "\":" + """{"type":"string", "format":"date"}"""
        case Some(i: Date) => "\"" + key + "\":" + """{"type":"string", "format":"date"}"""
        case List(i: Date, _*) => "\"" + key + "\":" + """{"type":"array", "items":{"type":"string", "format":"date"}}"""
        case Some(List(i: Date, _*)) => "\"" + key + "\":" + """{"type":"array", "items":{"type":"string", "format":"date"}}"""
        case _ => "unknown"
      }
    }
    val fields: String = properties filter (_.contains("unknown") == false) mkString (",")

    val required = for {f <- entity.getClass.getDeclaredFields
                        if f.getType.toString.contains("Option") == false
    } yield {
      f.getName
    }
    val requiredFields = required.toList mkString("[\"", "\",\"", "\"]")

    val requiredFieldsPart = if (required.length > 0) """"required": """ + requiredFields + "," else ""

    val definition = "\"" + entity.getClass.getSimpleName + "\":{" + requiredFieldsPart + """"properties": {""" + fields + """}}"""

    definition

  }

  def loadDefinitions (resourceDocList: List[ResourceDoc]): liftweb.json.JValue = {

    import code.api.v1_2_1._

    implicit val formats = DefaultFormats
    val jsonAST1: JValue = decompose(BankJSON("1", "Name", "Name1", "None", "www.go.com"))
    val jsonCaseClass1 = jsonAST1.extract[BankJSON]

    val jsonAST2: JValue = decompose(UserJSON("1", "Name", "Name1"))
    val jsonCaseClass2 = jsonAST2.extract[UserJSON]

    val definitions = "{\"definitions\":{" + translateEntity(jsonCaseClass1) + "," + translateEntity(jsonCaseClass2) + "}}"

    parse(definitions)
  }

}