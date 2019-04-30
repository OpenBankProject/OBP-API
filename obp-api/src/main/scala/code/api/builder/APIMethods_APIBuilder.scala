package code.api.builder
import java.util.UUID

import code.api.builder.JsonFactory_APIBuilder._
import code.api.util.APIUtil._
import code.api.util.ApiTag._
import code.api.util.{ApiVersion, CustomJsonFormats}
import code.api.util.ErrorMessages._
import net.liftweb.common.Full
import net.liftweb.http.rest.RestHelper
import net.liftweb.json
import net.liftweb.json.Extraction._
import net.liftweb.json._
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
trait APIMethods_APIBuilder { self: RestHelper =>
  val ImplementationsBuilderAPI = new Object() {
    val apiVersion = ApiVersion.apiBuilder
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(resourceDocs, apiRelations)
    implicit val formats = CustomJsonFormats.formats
    val TemplateNotFound = "OBP-31001: Template not found. Please specify a valid value for TEMPLATE_ID."
    def endpointsOfBuilderAPI = getTemplates :: getTemplate :: createTemplate :: deleteTemplate :: Nil
    resourceDocs += ResourceDoc(getTemplates, apiVersion, "getTemplates", "GET", "/templates", "Get Templates", "Return All Templates", emptyObjectJson, templatesJson, List(UserNotLoggedIn, UnknownError), Catalogs(notCore, notPSD2, notOBWG), apiTagApiBuilder :: Nil)
    lazy val getTemplates: OBPEndpoint = {
      case ("templates" :: Nil) JsonGet req =>
        cc => {
          for (u <- cc.user ?~ UserNotLoggedIn; templates <- APIBuilder_Connector.getTemplates; templatesJson = JsonFactory_APIBuilder.createTemplates(templates); jsonObject: JValue = decompose(templatesJson)) yield {
            successJsonResponse(jsonObject)
          }
        }
    }
    resourceDocs += ResourceDoc(getTemplate, apiVersion, "getTemplate", "GET", "/templates/TEMPLATE_ID", "Get Template", "Return One Template By Id", emptyObjectJson, templateJson, List(UserNotLoggedIn, UnknownError), Catalogs(notCore, notPSD2, notOBWG), apiTagApiBuilder :: Nil)
    lazy val getTemplate: OBPEndpoint = {
      case ("templates" :: templateId :: Nil) JsonGet _ =>
        cc => {
          for (u <- cc.user ?~ UserNotLoggedIn; template <- APIBuilder_Connector.getTemplateById(templateId) ?~! TemplateNotFound; templateJson = JsonFactory_APIBuilder.createTemplate(template); jsonObject: JValue = decompose(templateJson)) yield {
            successJsonResponse(jsonObject)
          }
        }
    }
    resourceDocs += ResourceDoc(createTemplate, apiVersion, "createTemplate", "POST", "/templates", "Create Template", "Create One Template", createTemplateJson, templateJson, List(UnknownError), Catalogs(notCore, notPSD2, notOBWG), apiTagApiBuilder :: Nil)
    lazy val createTemplate: OBPEndpoint = {
      case ("templates" :: Nil) JsonPost json -> _ =>
        cc => {
          for (createTemplateJson <- tryo(json.extract[CreateTemplateJson]) ?~! InvalidJsonFormat; u <- cc.user ?~ UserNotLoggedIn; template <- APIBuilder_Connector.createTemplate(createTemplateJson); templateJson = JsonFactory_APIBuilder.createTemplate(template); jsonObject: JValue = decompose(templateJson)) yield {
            successJsonResponse(jsonObject)
          }
        }
    }
    resourceDocs += ResourceDoc(deleteTemplate, apiVersion, "deleteTemplate", "DELETE", "/templates/TEMPLATE_ID", "Delete Template", "Delete One Template", emptyObjectJson, emptyObjectJson.copy("true"), List(UserNotLoggedIn, UnknownError), Catalogs(notCore, notPSD2, notOBWG), apiTagApiBuilder :: Nil)
    lazy val deleteTemplate: OBPEndpoint = {
      case ("templates" :: templateId :: Nil) JsonDelete _ =>
        cc => {
          for (
            u <- cc.user ?~ UserNotLoggedIn; 
            template <- APIBuilder_Connector.getTemplateById(templateId) ?~! TemplateNotFound;
            deleted <- APIBuilder_Connector.deleteTemplate(templateId)
          ) yield {
            if (deleted) noContentJsonResponse else errorJsonResponse("Delete not completed")
          }
        }
    }
  }
}
object APIBuilder_Connector {
  val allAPIBuilderModels = List(MappedTemplate_4824100653501473508)
  def createTemplate(createTemplateJson: CreateTemplateJson) = Full(MappedTemplate_4824100653501473508.create.mTemplateId(generateUUID()).mAuthor(createTemplateJson.author).mPages(createTemplateJson.pages).mPoints(createTemplateJson.points).saveMe())
  def getTemplates() = Full(MappedTemplate_4824100653501473508.findAll())
  def getTemplateById(templateId: String) = MappedTemplate_4824100653501473508.find(By(MappedTemplate_4824100653501473508.mTemplateId, templateId))
  def deleteTemplate(templateId: String) = MappedTemplate_4824100653501473508.find(By(MappedTemplate_4824100653501473508.mTemplateId, templateId)).map(_.delete_!)
}
import net.liftweb.mapper._
class MappedTemplate_4824100653501473508 extends Template with LongKeyedMapper[MappedTemplate_4824100653501473508] with IdPK {
  object mAuthor extends MappedString(this, 100)
  override def author: String = mAuthor.get
  object mPages extends MappedInt(this)
  override def pages: Int = mPages.get
  object mPoints extends MappedDouble(this)
  override def points: Double = mPoints.get
  def getSingleton = MappedTemplate_4824100653501473508
  object mTemplateId extends MappedString(this, 100)
  override def templateId: String = mTemplateId.get
}
object MappedTemplate_4824100653501473508 extends MappedTemplate_4824100653501473508 with LongKeyedMetaMapper[MappedTemplate_4824100653501473508]
trait Template { `_` =>
  def author: String
  def pages: Int
  def points: Double
  def templateId: String
}