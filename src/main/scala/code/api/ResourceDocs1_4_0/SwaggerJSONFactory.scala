package code.api.ResourceDocs1_4_0

import java.util.Date

import code.api.Constant._
import code.api.util.APIUtil.ResourceDoc
import code.api.v1_2.{BankJSON, BanksJSON, UserJSON}
import code.model._
import net.liftweb
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
        case "allBanks" => Some(ResponseObjectSchemaJson("#/definitions/BanksJSON"))
        case "bankById" => Some(ResponseObjectSchemaJson("#/definitions/BankJSON"))
        case "getTransactionRequestTypes" => Some(ResponseObjectSchemaJson("#/definitions/TransactionReponseTypes"))
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
            Map("200" -> ResponseObjectJson(Some("Success"), getName(rd)), "400" -> ResponseObjectJson(Some("Error"), Some(ResponseObjectSchemaJson("#/definitions/Error"))))))
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

    //Get fields of runtime entities and put they into structure Map(nameOfField -> fieldAsObject)
    val r = currentMirror.reflect(entity)
    val mapOfFields = r.symbol.typeSignature.members.toStream
      .collect { case s: TermSymbol if !s.isMethod => r.reflectField(s)}
      .map(r => r.symbol.name.toString.trim -> r.get)
      .toMap

    //Iterate over Map and use pattern matching to extract type of field of runtime entity and make an appropriate swagger string for it
    val properties = for ((key, value) <- mapOfFields) yield {
      value match {
        case i: Boolean                    => s""" """" + key + """": {"type":"boolean"}"""
        case Some(i: Boolean)              => s""" """" + key + """": {"type":"boolean"}"""
        case List(i: Boolean, _*)          => s""" """" + key + """": {"type":"array", "items":{"type": "boolean"}}"""
        case Some(List(i: Boolean, _*))    => s""" """" + key + """": {"type":"array", "items":{"type": "boolean"}}"""
        case i: String                     => s""" """" + key + """": {"type":"string"}"""
        case Some(i: String)               => s""" """" + key + """": {"type":"string"}"""
        case List(i: String, _*)           => s""" """" + key + """": {"type":"array", "items":{"type": "string"}}"""
        case Some(List(i: String, _*))     => s""" """" + key + """": {"type":"array", "items":{"type": "string"}}"""
        case i: Int                        => s""" """" + key + """": {"type":"integer", "format":"int32"}"""
        case Some(i: Int)                  => s""" """" + key + """": {"type":"integer", "format":"int32"}"""
        case List(i: Long, _*)             => s""" """" + key + """": {"type":"array", "items":{"type":"integer", "format":"int32"}}"""
        case Some(List(i: Long, _*))       => s""" """" + key + """": {"type":"array", "items":{"type":"integer", "format":"int32"}}"""
        case i: Long                       => s""" """" + key + """": {"type":"integer", "format":"int64"}"""
        case Some(i: Long)                 => s""" """" + key + """": {"type":"integer", "format":"int64"}"""
        case List(i: Long, _*)             => s""" """" + key + """": {"type":"array", "items":{"type":"integer", "format":"int64"}}"""
        case Some(List(i: Long, _*))       => s""" """" + key + """": {"type":"array", "items":{"type":"integer", "format":"int64"}}"""
        case i: Float                      => s""" """" + key + """": {"type":"number", "format":"float"}"""
        case Some(i: Float)                => s""" """" + key + """": {"type":"number", "format":"float"}"""
        case List(i: Float, _*)            => s""" """" + key + """": {"type":"array", "items":{"type": "float"}}"""
        case Some(List(i: Float, _*))      => s""" """" + key + """": {"type":"array", "items":{"type": "float"}}"""
        case i: Double                     => s""" """" + key + """": {"type":"number", "format":"double"}"""
        case Some(i: Double)               => s""" """" + key + """": {"type":"number", "format":"double"}"""
        case List(i: Double, _*)           => s""" """" + key + """": {"type":"array", "items":{"type": "double"}}"""
        case Some(List(i: Double, _*))     => s""" """" + key + """": {"type":"array", "items":{"type": "double"}}"""
        case i: Date                       => s""" """" + key + """": {"type":"string", "format":"date"}"""
        case Some(i: Date)                 => s""" """" + key + """": {"type":"string", "format":"date"}"""
        case List(i: Date, _*)             => s""" """" + key + """": {"type":"array", "items":{"type":"string", "format":"date"}}"""
        case Some(List(i: Date, _*))       => s""" """" + key + """": {"type":"array", "items":{"type":"string", "format":"date"}}"""
        case obj@BankJSON(_,_,_,_,_)       => s""" """" + key + """": {"$ref": "#/definitions/BankJSON"""" +"}"
        case obj@List(BankJSON(_,_,_,_,_)) => s""" """" + key + """": {"type": "array", "items":{"$ref": "#/definitions/BankJSON"""" +"}}"
        case obj@BanksJSON(_)              => s""" """" + key + """": {"$ref":"#/definitions/BanksJSON"""" +"}"
        case obj@List(BanksJSON(_))        => s""" """" + key + """": {"type": "array", "items":{"$ref": "#/definitions/BanksJSON"""" +"}}"
        case obj@UserJSON(_,_,_)           => s""" """" + key + """": {"$ref": "#/definitions/UserJSON"""" +"}"
        case obj@List(UserJSON(_,_,_))     => s""" """" + key + """": {"type":"array", "items":{"$ref": "#/definitions/UserJSON"""" +"}}"

          //now the TransactionRequestTypeJsons changed, it feed to update, so comment
          //case obj@TransactionRequestTypeJson(_)       => s""" """" + key + """":{"$ref": "#/definitions/TransactionReponseTypes"""" +"}"
          //case obj@List(TransactionRequestTypeJsons(_)) => s""" """" + key + """": {"type":"array", "items":{"$ref": "#/definitions/Transac*/tionReponseTypes"""" +"}}"
        case _ => "unknown"
      }
    }
    //Exclude all unrecognised fields and make part of fields definition
    val fields: String = properties filter (_.contains("unknown") == false) mkString (",")
    //Collect all mandatory fields and make an appropriate string
    val required =
      for {
        f <- entity.getClass.getDeclaredFields
        if f.getType.toString.contains("Option") == false
      } yield {
        f.getName
      }
    val requiredFields = required.toList mkString("[\"", "\",\"", "\"]")
    //Make part of mandatory fields
    val requiredFieldsPart = if (required.length > 0) """"required": """ + requiredFields + "," else ""
    //Make whole swagger definition of an entity
    val definition = "\"" + entity.getClass.getSimpleName + "\":{" + requiredFieldsPart + """"properties": {""" + fields + """}}"""

    definition

  }

  def loadDefinitions(resourceDocList: List[ResourceDoc]): liftweb.json.JValue = {

    implicit val formats = DefaultFormats

    //Translate a jsonAST to an appropriate case class entity
    val successResponseBodies: List[Any] =
      for (rd <- resourceDocList)
      yield {
        rd match {
          case u if u.apiFunction.contains("allBanks") => rd.successResponseBody.extract[BanksJSON]
          case u if u.apiFunction.contains("bankById") => rd.successResponseBody.extract[BankJSON]
          //now the TransactionRequestTypeJsons changed, it feed to update, so comment
          //case u if u.apiFunction.contains("getTransactionRequestTypes") => rd.successResponseBody.extract[TransactionRequestTypesSwaggerJsons]
          case _ => "Not defined"
        }
      }

    val successResponseBodiesForProcessing = successResponseBodies filter (_.toString().contains("Not defined") == false)
    //Translate every entity in a list to appropriate swagger format
    val listOfParticularDefinition =
      for (e <- successResponseBodiesForProcessing)
      yield {
        translateEntity(e)
      }
    //Add a comma between elements of a list and make a string
    val particularDefinitionsPart = listOfParticularDefinition mkString (",")
    //Make a final string
    val definitions = "{\"definitions\":{" + particularDefinitionsPart + "}}"
    //Make a jsonAST from a string
    parse(definitions)
  }

}