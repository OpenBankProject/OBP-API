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
package code.api.APIBuilder;

import code.api.APIBuilder.APIBuilderModel._
import code.api.util.APIUtil
import scala.meta._
import net.liftweb.json.JsonAST.{JObject, JString}
import net.liftweb.json.JValue

object APIBuilder
{
  def main(args: Array[String]): Unit = overwriteApiCode(apiSource,jsonFactorySource)

  val jsonJValueFromFile: JValue = APIUtil.getJValueFromFile("src/main/scala/code/api/APIBuilder/apisResource.json")

  val resourceDocsJObject= jsonJValueFromFile.\("resource_docs").children.asInstanceOf[List[JObject]]
    
  val getMultipleApiJValue = resourceDocsJObject.filter(jObject => jObject.\("request_verb") == JString("GET")&& !jObject.\("request_url").asInstanceOf[JString].values.contains("_ID")).head
  val getSingleApiJValue = resourceDocsJObject.filter(jObject => jObject.\("request_verb") == JString("GET")&& jObject.\("request_url").asInstanceOf[JString].values.contains("_ID")).head
  val createSingleApiJValue = resourceDocsJObject.filter(_.\("request_verb") == JString("POST")).head
  val deleteSingleApiJValue = resourceDocsJObject.filter(_.\("request_verb") == JString("DELETE")).head
   
  val getSingleApiResponseBody: JValue = getSingleApiJValue \ "success_response_body"
  //"book"
  val modelName = getModelName(getSingleApiResponseBody)
  //All the fields in the book object.
  val modelFieldsJValue: JValue = getSingleApiResponseBody \ modelName

  //BOOK
  val modelNameUpperCase = modelName.toUpperCase
  //book
  val modelNameLowerCase = modelName.toLowerCase
  //Book
  val modelNameCapitalized = modelNameLowerCase.capitalize
  //MappedBook_123123
  val modelMappedName = s"Mapped${modelNameCapitalized}_"+Math.abs(scala.util.Random.nextLong())
  val modelTypeName = Type.Name(modelMappedName)
  val modelTermName = Term.Name(modelMappedName)
  val modelInit =Init.apply(Type.Name(modelMappedName), Term.Name(modelMappedName), Nil)
  
  
  val getApiSummary: String = (getMultipleApiJValue \ "summary").asInstanceOf[JString].values
  val getSingleApiSummary: String = (getSingleApiJValue \ "summary").asInstanceOf[JString].values
  val createSingleApiSummary: String = (createSingleApiJValue \ "summary").asInstanceOf[JString].values
  val deleteSingleApiSummary: String = (deleteSingleApiJValue \ "summary").asInstanceOf[JString].values
  val getApiSummaryFromJsonFile: String = getApiSummary +"(from Json File)"
  
  val getApiDescription: String = (getMultipleApiJValue \ "description").asInstanceOf[JString].values 
  val getSingleApiDescription: String = (getSingleApiJValue \ "description").asInstanceOf[JString].values 
  val createSingleApiDescription: String = (createSingleApiJValue \ "description").asInstanceOf[JString].values 
  val deleteSingleApiDescription: String = (deleteSingleApiJValue \ "description").asInstanceOf[JString].values 
  val getApiDescriptionFromJsonFile: String = getApiDescription + "(From Json File)"
  
  //TODO, for now this is only in description, could be a single filed later.
  val getApiAuthentication:Boolean = getApiDescriptionFromJsonFile.contains("Authentication is Mandatory")
  val getSingleApiAuthentication:Boolean = getSingleApiDescription.contains("Authentication is Mandatory")
  val createSingleApiAuthentication:Boolean = createSingleApiDescription.contains("Authentication is Mandatory")
  val deleteSingleApiAuthentication:Boolean = deleteSingleApiDescription.contains("Authentication is Mandatory")
  
  val getApiAuthenticationStatement: Term.ApplyInfix = getAuthenticationStatement(getApiAuthentication)
  val getSingleApiAuthenticationStatement: Term.ApplyInfix = getAuthenticationStatement(getSingleApiAuthentication)
  val createSingleApiAuthenticationStatement: Term.ApplyInfix = getAuthenticationStatement(createSingleApiAuthentication)
  val deleteSingleApiAuthenticationStatement: Term.ApplyInfix = getAuthenticationStatement(deleteSingleApiAuthentication)
  
  val getApiUrl: String = (getMultipleApiJValue \ "request_url").asInstanceOf[JString].values //eg: /my/template
  val getSingleApiUrl: String = (getSingleApiJValue \ "request_url").asInstanceOf[JString].values //eg: /my/template
  val createSingleApiUrl: String = (createSingleApiJValue \ "request_url").asInstanceOf[JString].values //eg: /my/template
  val deleteSingleApiUrl: String = (deleteSingleApiJValue \ "request_url").asInstanceOf[JString].values //eg: /my/template
  val getApiUrlFromJsonFile: String = "/file"+getApiUrl //eg: /file/my/template
 
  val getApiUrlVal = Lit.String(s"$getApiUrl")
  val getSingleApiUrlVal = Lit.String(s"$getSingleApiUrl")
  val createSingleApiUrlVal = Lit.String(s"$createSingleApiUrl")
  val deleteSingleApiUrlVal = Lit.String(s"$deleteSingleApiUrl")
  val getApiUrlFromJsonFileVal = Lit.String(s"$getApiUrlFromJsonFile")
  //TODO, escape issue:return the space, I added quotes in the end: allSourceCode.syntax.replaceAll("""  ::  """,""""  ::  """")
  //from "/my/template" --> "my  ::  template" 
  val getApiUrlLiftFormat = getApiUrl.replaceFirst("/", "").split("/").mkString("""""","""  ::  ""","""""")
  val createApiUrlLiftFormat = createSingleApiUrl.replaceFirst("/", "").split("/").mkString("""""","""  ::  ""","""""")
  val deleteApiUrlLiftFormat = deleteSingleApiUrl.replaceFirst("/", "").split("/").dropRight(1).mkString("""""","""  ::  ""","""""")
  val getSingleApiUrlLiftFormat = getSingleApiUrl.replaceFirst("/", "").split("/").dropRight(1).mkString("""""","""  ::  ""","""""")
  val getApiUrlLiftweb: Lit.String = Lit.String(getApiUrlLiftFormat)
  val createApiUrlLiftweb: Lit.String = Lit.String(createApiUrlLiftFormat)
  val deleteApiUrlLiftweb: Lit.String = Lit.String(deleteApiUrlLiftFormat)
  val getSingleApiUrlLiftweb: Lit.String = Lit.String(getSingleApiUrlLiftFormat)
  
  val getApiSummaryVal = Lit.String(s"$getApiSummary")
  val getSingleApiSummaryVal = Lit.String(s"$getSingleApiSummary")
  val createSingleApiSummaryVal = Lit.String(s"$createSingleApiSummary")
  val deleteSingleApiSummaryVal = Lit.String(s"$deleteSingleApiSummary")
  val getApiSummaryFromJsonFileVal = Lit.String(s"$getApiSummaryFromJsonFile")

  val getApiDescriptionVal = Lit.String(s"$getApiDescription")
  val getSingleApiDescriptionVal = Lit.String(s"$getSingleApiDescription")
  val createSingleApiDescriptionVal = Lit.String(s"$createSingleApiDescription")
  val deleteSingleApiDescriptionVal = Lit.String(s"$deleteSingleApiDescription")
  val getApiDescriptionFromJsonFileVal = Lit.String(s"$getApiDescriptionFromJsonFile")
  
  val getTemplateFromFileResourceCode: Term.ApplyInfix =q"""
    resourceDocs += ResourceDoc(
      getTemplatesFromFile, 
      apiVersion, 
      "getTemplatesFromFile", 
      "GET", 
      $getApiUrlFromJsonFileVal, 
      $getApiSummaryFromJsonFileVal, 
      $getApiDescriptionFromJsonFileVal, 
      emptyObjectJson, 
      templatesJson, 
      List(UserNotLoggedIn, UnknownError), 
      Catalogs(notCore, notPSD2, notOBWG), 
      apiTagApiBuilder :: Nil
    )"""
  val getTemplatesResourceCode: Term.ApplyInfix =q"""
    resourceDocs += ResourceDoc(
      getTemplates,
      apiVersion,
      "getTemplates",
      "GET",
      $getApiUrlVal,        
      $getApiSummaryVal,       
      $getApiDescriptionVal,
      emptyObjectJson,
      templatesJson,
      List(UserNotLoggedIn, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      apiTagApiBuilder :: Nil
    )"""
  val getTemplateResourceCode: Term.ApplyInfix =q"""
    resourceDocs += ResourceDoc(
      getTemplate, 
      apiVersion, 
      "getTemplate", 
      "GET",
      $getSingleApiUrlVal,
      $getSingleApiSummaryVal,
      $getSingleApiDescriptionVal,
      emptyObjectJson, 
      templateJson,
      List(UserNotLoggedIn, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG), 
      apiTagApiBuilder :: Nil
    )"""
  val createTemplateResourceCode: Term.ApplyInfix =q"""
    resourceDocs += ResourceDoc(
      createTemplate, 
      apiVersion, 
      "createTemplate", 
      "POST",
      $createSingleApiUrlVal,
      $createSingleApiSummaryVal,
      $createSingleApiDescriptionVal,
      createTemplateJson, 
      templateJson,
      List(UnknownError),
      Catalogs(notCore, notPSD2, notOBWG), 
      apiTagApiBuilder :: Nil
    )"""
  val deleteTemplateResourceCode: Term.ApplyInfix = q"""
    resourceDocs += ResourceDoc(
      deleteTemplate, 
      apiVersion, 
      "deleteTemplate", 
      "DELETE",
      $deleteSingleApiUrlVal,
      $deleteSingleApiSummaryVal,
      $deleteSingleApiDescriptionVal,
      emptyObjectJson, 
      emptyObjectJson.copy("true"),
      List(UserNotLoggedIn, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG), 
      apiTagApiBuilder :: Nil
    )"""
    
  
  val getTemplateFromFilePartialFunction: Defn.Val = q"""
    lazy val getTemplatesFromFile: OBPEndpoint = {
      case ("file" :: $getApiUrlLiftweb :: Nil) JsonGet req =>
        cc => {
          for {
            u <- $getApiAuthenticationStatement
            jsonStringFromFile = scala.io.Source.fromFile("src/main/scala/code/api/APIBuilder/apisResource.json").mkString 
            jsonJValueFromFile = json.parse(jsonStringFromFile)
            resourceDocsJObject= jsonJValueFromFile.\("resource_docs").children.asInstanceOf[List[JObject]]
            getMethodJValue = resourceDocsJObject.filter(jObject => jObject.\("request_verb") == JString("GET")&& !jObject.\("request_url").asInstanceOf[JString].values.contains("_ID")).head
            jsonObject = getMethodJValue \ "success_response_body"
          } yield {
            successJsonResponse(jsonObject)
          }
        }
    }"""
  val getTemplatesPartialFunction: Defn.Val = q"""
    lazy val getTemplates: OBPEndpoint ={
      case ($getApiUrlLiftweb:: Nil) JsonGet req =>
        cc =>
        {
          for{
            u <- $getApiAuthenticationStatement 
            templates <-  APIBuilder_Connector.getTemplates
            templatesJson = JsonFactory_APIBuilder.createTemplates(templates)
            jsonObject:JValue = decompose(templatesJson)
          }yield{
            successJsonResponse(jsonObject)
          }
        }
    }"""
  val getTemplatePartialFunction: Defn.Val = q"""
    lazy val getTemplate: OBPEndpoint ={
      case ($getSingleApiUrlLiftweb :: templateId :: Nil) JsonGet _ => {
        cc =>
        {
          for{
            u <- $getSingleApiAuthenticationStatement
            template <- APIBuilder_Connector.getTemplateById(templateId) ?~! TemplateNotFound
            templateJson = JsonFactory_APIBuilder.createTemplate(template)
            jsonObject:JValue = decompose(templateJson)
          }yield{
            successJsonResponse(jsonObject)
          }
        }
      }
    }"""
  val createTemplatePartialFunction: Defn.Val = q"""
    lazy val createTemplate: OBPEndpoint ={
      case ($createApiUrlLiftweb:: Nil) JsonPost json -> _ => {
        cc =>
        {
          for{
            createTemplateJson <- tryo(json.extract[CreateTemplateJson]) ?~! InvalidJsonFormat
            u <- $createSingleApiAuthenticationStatement
            template <- APIBuilder_Connector.createTemplate(createTemplateJson)
            templateJson = JsonFactory_APIBuilder.createTemplate(template)
            jsonObject:JValue = decompose(templateJson)
          }yield{
            successJsonResponse(jsonObject)
          }
        }
      }
    }
    """
  val deleteTemplatePartialFunction: Defn.Val = q"""
    lazy val deleteTemplate: OBPEndpoint ={
      case ($deleteApiUrlLiftweb :: templateId :: Nil) JsonDelete _ => {
        cc =>
        {
          for{
            u <- $deleteSingleApiAuthenticationStatement
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
    """
  
  //List(author, pages, points)
  val modelFieldsNames: List[String] = getModelFieldsNames(modelFieldsJValue)
  //List(String, Int, Double)
  val modelFieldsTypes: List[String] = getModelFieldsTypes(modelFieldsNames, modelFieldsJValue) 
  //List(Chinua Achebe, 209, 1.3)
  val modelFieldsDefaultValues: List[Any] = getModelFieldDefaultValues(modelFieldsNames, modelFieldsJValue) 
  
  //List(author: String = `Chinua Achebe`, tutor: String = `1123123 1312`, pages: Int = 209, points: Double = 1.3)
  val modelCaseClassParams: List[Term.Param] = getModelCaseClassParams(modelFieldsNames, modelFieldsTypes, modelFieldsDefaultValues)
  
  //def createTemplate(createTemplateJson: CreateTemplateJson) = Full(
  // MappedBook_6099750036365020434.create
  // .mTemplateId(UUID.randomUUID().toString)
  // .mAuthor(createTemplateJson.author)
  // .mPages(createTemplateJson.pages)
  // .mPoints(createTemplateJson.points)
  // .saveMe())
  val createModelJsonMethod: Defn.Def = generateCreateModelJsonMethod(modelFieldsNames, modelMappedName)
  
  //trait Template { `_` =>
  //  def author: String
  //  def tutor: String
  //  def pages: Int
  //  def points: Double
  //  def templateId: String
  //}
  val modelTrait: Defn.Trait = getModelTrait(modelFieldsNames, modelFieldsTypes)
    
  //class MappedTemplate extends Template with LongKeyedMapper[MappedTemplate] with IdPK {
  //  object mAuthor extends MappedString(this, 100)
  //  override def author: String = mAuthor.get
  //  object mPages extends MappedInt(this)
  //  override def pages: Int = mPages.get
  //  object mPoints extends MappedDouble(this)
  //  override def points: Double = mPoints.get
  //  def getSingleton = MappedTemplate
  //  object mTemplateId extends MappedString(this, 100)
  //  override def templateId: String = mTemplateId.get
  //}
  val modelClass = getModelClass(modelTypeName, modelTermName, modelFieldsNames, modelFieldsTypes) 
  
  val apiSource: Source = source""" 
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
    implicit val formats = net.liftweb.json.DefaultFormats
    val TemplateNotFound = "OBP-31001: Template not found. Please specify a valid value for TEMPLATE_ID."
    
    def endpointsOfBuilderAPI = getTemplatesFromFile :: getTemplate :: createTemplate :: getTemplates :: deleteTemplate :: Nil
    
    $getTemplateFromFileResourceCode
    $getTemplateFromFilePartialFunction
 
    $getTemplatesResourceCode
    $getTemplatesPartialFunction
    
    $getTemplateResourceCode                           
    $getTemplatePartialFunction
    
    $createTemplateResourceCode                           
    $createTemplatePartialFunction
    
    $deleteTemplateResourceCode                           
    $deleteTemplatePartialFunction
  }
}

object APIBuilder_Connector
{
  val allAPIBuilderModels = List($modelTermName)
  
  $createModelJsonMethod;
  
  def getTemplates()= Full($modelTermName.findAll())
  
  def getTemplateById(templateId: String)= $modelTermName.find(By($modelTermName.mTemplateId, templateId))
  
  def deleteTemplate(templateId: String)= $modelTermName.find(By($modelTermName.mTemplateId, templateId)).map(_.delete_!)
  
}

import net.liftweb.mapper._

$modelClass

object $modelTermName extends $modelInit with LongKeyedMetaMapper[$modelTypeName] {}
 
$modelTrait
"""
  
  /*
  * ######################################JsonFactory_APIBuilder.scala###################################################
  * */
  
  //List(templateId:String = "11231231312" ,author: String = `Chinua Achebe`, tutor: String = `11231231312`, pages: Int = 209, points: Double = 1.3)
  //Added the templatedId to `modelCaseClassParams`
  val templateJsonClassParams = List(APIBuilderModel.templateIdField)++ modelCaseClassParams
  
  //case class TemplateJson(templateId: String = """1123123 1312""", author: String = """Chinua Achebe""", tutor: String = """1123123 1312""", pages: Int = 209, points: Double = 1.3)
  val TemplateJsonClass: Defn.Class = q"""case class TemplateJson(..$templateJsonClassParams) """
  
  //case class Template(author: String = `Chinua Achebe`, pages: Int = 209, points: Double = 1.3)
  //Note: No `templateId` in this class, the bank no need provide it, obp create a uuid for it.
  val createTemplateJsonClass: Defn.Class = q"""case class CreateTemplateJson(..$modelCaseClassParams) """
  
  //TemplateJson(template.templateId, template.author, template.tutor, template.pages, template.points)
  val createTemplateJsonApply: Term.Apply = generateCreateTemplateJsonApply(modelFieldsNames)
  
  //def createTemplate(template: Template) = TemplateJson(template.templateId, template.author, template.tutor, template.pages, template.points)
  val createTemplateDef: Defn.Def =q"""def createTemplate(template: Template) = $createTemplateJsonApply"""
  
  //def createTemplates(templates: List[Template]) = templates.map(template => TemplateJson(template.templateId, template.author, template.tutor, template.pages, template.points))
  val createTemplatesDef: Defn.Def = q"""def createTemplates(templates: List[Template])= templates.map(template => $createTemplateJsonApply)"""
  
  val jsonFactorySource: Source =source"""
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
import code.api.util.APIUtil

$TemplateJsonClass
$createTemplateJsonClass

object JsonFactory_APIBuilder{
              
  val templateJson = TemplateJson()
  val templatesJson = List(templateJson)
  val createTemplateJson = CreateTemplateJson()
  
  $createTemplateDef;
  $createTemplatesDef;
    
  val allFields =
    for (
      v <- this.getClass.getDeclaredFields
      //add guard, ignore the SwaggerJSONsV220.this and allFieldsAndValues fields
      if (APIUtil.notExstingBaseClass(v.getName()))
    )
      yield {
        v.setAccessible(true)
        v.get(this)
      }
}
""" 
}