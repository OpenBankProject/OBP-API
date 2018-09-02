/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd

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
TESOBE Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)
 */

package code.api.builder

import java.util.UUID
import code.api.builder.JsonFactory_APIBuilder._
import code.api.util.APIUtil._
import code.api.util.ApiVersion
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

trait APIMethods_APIBuilder
{
  self: RestHelper =>
  
  val ImplementationsBuilderAPI = new Object()
  {
    val apiVersion: ApiVersion = ApiVersion.apiBuilder
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(resourceDocs, apiRelations)
    val TemplateNotFound = "OBP-31001: Template not found. Please specify a valid value for TEMPLATE_ID."
    
    implicit val formats = net.liftweb.json.DefaultFormats
    
    def endpointsOfBuilderAPI = getTemplatesFromFile :: getTemplate :: createTemplate :: getTemplates :: deleteTemplate :: Nil
    
    resourceDocs += ResourceDoc(
      getTemplatesFromFile,
      apiVersion,
      "getTemplatesFromFile",
      "GET",
      "/file/templates",
      "Get Templates From File",
      "Return all templates in file, Authentication is Mandatory",
      emptyObjectJson,
      templatesJson,
      List(UserNotLoggedIn, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      apiTagApiBuilder :: Nil
    )
    lazy val getTemplatesFromFile: OBPEndpoint ={
      case ("file" :: "templates" :: Nil) JsonGet req =>
        cc =>
        {
          for{
            u <- cc.user ?~ UserNotLoggedIn;
            jsonStringFromFile = scala.io.Source.fromFile("src/main/scala/code/api/APIBuilder/apisResource.json").mkString; 
            jsonJValueFromFile = json.parse(jsonStringFromFile); 
            resourceDocsJObject = jsonJValueFromFile.\("resource_docs").children.asInstanceOf[List[JObject]]; 
            getMethodJValue = resourceDocsJObject.filter(jObject => jObject.\("request_verb") == JString("GET") && !jObject.\("request_url").asInstanceOf[JString].values.contains("_ID")).head; 
            jsonObject = getMethodJValue \ "success_response_body"
          }yield{
            successJsonResponse(jsonObject)
          }
        }
    }

    resourceDocs += ResourceDoc(
      getTemplates,
      apiVersion,
      "getTemplates",
      "GET",
      "/templates",
      "Get All Templates.",
      "Return all templates, Authentication is Mandatory",
      emptyObjectJson,
      templatesJson,
      List(UserNotLoggedIn, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      apiTagApiBuilder :: Nil
    )
    lazy val getTemplates: OBPEndpoint ={
      case ("templates" :: Nil) JsonGet req =>
        cc =>
        {
          for{
            u <- cc.user ?~ UserNotLoggedIn
            templates <-  APIBuilder_Connector.getTemplates
            templatesJson = JsonFactory_APIBuilder.createTemplates(templates)
            jsonObject:JValue = decompose(templatesJson)
          }yield{
            successJsonResponse(jsonObject)
          }
        }
    }
    
    resourceDocs += ResourceDoc(
      getTemplate,
      apiVersion,
      "getTemplate",
      "GET",
      "/templates/TEMPLATE_ID",
      "Get Template ",
      "Get a template by Id, Authentication is Mandatory",
      emptyObjectJson,
      templateJson,
      List(UserNotLoggedIn, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      apiTagApiBuilder :: Nil
    )
    lazy val getTemplate: OBPEndpoint ={
      case "templates" :: templateId :: Nil JsonGet _ => {
        cc =>
        {
          for{
            u <- cc.user ?~ UserNotLoggedIn
            template <- APIBuilder_Connector.getTemplateById(templateId) ?~! TemplateNotFound
            templateJson = JsonFactory_APIBuilder.createTemplate(template)
            jsonObject:JValue = decompose(templateJson)
          }yield{
            successJsonResponse(jsonObject)
          }
        }
      }
    }
    
    resourceDocs += ResourceDoc(
      createTemplate,
      apiVersion,
      "createTemplate",
      "POST",
      "/templates",
      "Create Template ",
      "Create one template, Authentication is Mandatory",
      createTemplateJson,
      templateJson,
      List(InvalidJsonFormat,UserNotLoggedIn, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      apiTagApiBuilder :: Nil
    )
    lazy val createTemplate: OBPEndpoint ={
      case "templates" :: Nil JsonPost json -> _ => {
        cc =>
        {
          for{
            createTemplateJson <- tryo(json.extract[CreateTemplateJson]) ?~! InvalidJsonFormat
            u <- cc.user ?~ UserNotLoggedIn
            template <-  APIBuilder_Connector.createTemplate(createTemplateJson.author, createTemplateJson.pages, createTemplateJson.points)
            templateJson = JsonFactory_APIBuilder.createTemplate(template)
            jsonObject:JValue = decompose(templateJson)
          }yield{
            successJsonResponse(jsonObject)
          }
        }
      }
    }
    
    resourceDocs += ResourceDoc(
      deleteTemplate,
      apiVersion,
      "deleteTemplate",
      "DELETE",
      "/templates/TEMPLATE_ID",
      "Delete Template ",
      "Delete a template, Authentication is Mandatory",
      emptyObjectJson,
      emptyObjectJson.copy("true"),
      List(UserNotLoggedIn, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      apiTagApiBuilder :: Nil
    )
    lazy val deleteTemplate: OBPEndpoint ={
      case "templates" :: templateId :: Nil JsonDelete _ => {
        cc =>
        {
          for{
            u <- cc.user ?~ UserNotLoggedIn
            deleted <- APIBuilder_Connector.deleteTemplate(templateId)
          }yield{
            if(deleted)
              noContentJsonResponse
            else
              errorJsonResponse("Delete not completed")
          }
        }
      }
    }
  }
}


object APIBuilder_Connector
{
  val allAPIBuilderModels = List(MappedTemplate)
  
  def createTemplate(
    author: String, 
    pages: Int, 
    points: Double
  ) =
    Full(
      MappedTemplate.create
        .mTemplateId(UUID.randomUUID().toString)
        .mAuthor(author)
        .mPages(pages)
        .mPoints(points)
        .saveMe()
    )
  
  def getTemplates()= Full(MappedTemplate.findAll())
  
  def getTemplateById(templateId: String)= MappedTemplate.find(By(MappedTemplate.mTemplateId, templateId))
  
  def deleteTemplate(templateId: String)= MappedTemplate.find(By(MappedTemplate.mTemplateId, templateId)).map(_.delete_!)
  
}

import net.liftweb.mapper._

class MappedTemplate extends Template with LongKeyedMapper[MappedTemplate] with IdPK {
  def getSingleton = MappedTemplate

  object mTemplateId extends MappedString(this,100)
  object mAuthor extends MappedString(this,100)
  object mPages extends MappedInt(this)
  object mPoints extends MappedDouble(this)

  override def templateId: String = mTemplateId.get
  override def author: String = mAuthor.get
  override def pages: Int = mPages.get
  override def points: Double = mPoints.get
}

object MappedTemplate extends MappedTemplate with LongKeyedMetaMapper[MappedTemplate] {
}
 
trait Template {
  def templateId : String
  def author : String
  def pages : Int
  def points : Double
}