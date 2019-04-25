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
package code.api.APIBuilder.swagger

import code.api.APIBuilder.APIBuilderModel._
import code.api.APIBuilder.APIBuilderModel
import code.api.util.APIUtil
import net.liftweb.json.JsonAST.{JObject, JString}
import net.liftweb.json.{JArray, JValue}

import scala.meta._

object APIBuilderSwagger
{
  def main(args: Array[String]): Unit = overwriteApiCode(apiSource,jsonFactorySource)
  val jsonJValueFromFile: JValue = APIUtil.getJValueFromFile("src/main/scala/code/api/APIBuilder/swagger/swaggerResource.json")

  val getSingleApiResponseBody: JValue = jsonJValueFromFile \\("foo")\"foo"\"value"
  //"template"
  val modelName = getModelName(getSingleApiResponseBody)
  //All the fields in the template object.
  val modelFieldsJValue: JValue = (getSingleApiResponseBody \modelName).children.head

  //TEMPLATE
  val modelNameUpperCase = modelName.toUpperCase
  //template
  val modelNameLowerCase = modelName.toLowerCase
  //Template
  val modelNameCapitalized = modelNameLowerCase.capitalize
  //MappedTemplate_123123
  val modelMappedName = s"Mapped${modelNameCapitalized}_"+Math.abs(scala.util.Random.nextLong())
  val modelTypeName = Type.Name(modelMappedName)
  val modelTermName = Term.Name(modelMappedName)
  val modelInit =Init.apply(Type.Name(modelMappedName), Term.Name(modelMappedName), Nil)
  
  
  val getMultipleApiSummary: String = ((jsonJValueFromFile  \\"get"\\ "summary")\("summary")).asInstanceOf[JArray].children(0).asInstanceOf[JString].values
  val getSingleApiSummary: String = ((jsonJValueFromFile  \\"get"\\ "summary")\("summary")).asInstanceOf[JArray].children(1).asInstanceOf[JString].values
  val deleteSingleApiSummary: String = ((jsonJValueFromFile \\"delete"\\ "summary")\("summary")).asInstanceOf[JString].values
  val createSingleApiSummary: String = ((jsonJValueFromFile \\"post"\\ "summary")\("summary")).asInstanceOf[JString].values
  
  val getApiDescription: String = ((jsonJValueFromFile \\"get"\\ "description").obj.head.value).asInstanceOf[JString].values 
  val getSingleDescription: String = ((jsonJValueFromFile \\"get"\\ "description").obj(3).value).asInstanceOf[JString].values 
  val createApiDescription: String = ((jsonJValueFromFile \\"post"\\ "description").obj.head.value).asInstanceOf[JString].values 
  val deleteApiDescription: String = ((jsonJValueFromFile \\"delete"\\ "description").obj.head.value).asInstanceOf[JString].values 
  
  val getMultipleApiAuthenticationStatement: Term.ApplyInfix = getAuthenticationStatement(true)
  val getSingleApiAuthenticationStatement: Term.ApplyInfix = getAuthenticationStatement(true)
  val deleteSingleApiAuthenticationStatement: Term.ApplyInfix = getAuthenticationStatement(true)
  val createSingleApiAuthenticationStatement: Term.ApplyInfix = getAuthenticationStatement(true)
  
  val getMultipleApiUrl: String = (jsonJValueFromFile \\("paths")\"paths").asInstanceOf[JObject].obj(0).name
  val getSingleApiUrl: String = (jsonJValueFromFile \\("paths")\"paths").asInstanceOf[JObject].obj(1).name
 
  val getMultipleApiUrlVal = Lit.String(s"$getMultipleApiUrl")
  val createSingleApiUrlVal = getMultipleApiUrl
  val getApiUrlLiftFormat = getMultipleApiUrl.replaceFirst("/", "").split("/").mkString("""""","""  ::  ""","""""")
  val getApiUrlLiftweb: Lit.String = Lit.String(getApiUrlLiftFormat)
  val createApiUrlLiftweb: Lit.String = Lit.String(getApiUrlLiftFormat)
  
  val getSingleApiUrlVal = Lit.String(s"$getSingleApiUrl")
  val deleteSingleApiUrlVal = Lit.String(s"$getSingleApiUrl")
  val getSingleApiUrlLiftFormat = getSingleApiUrl.replaceFirst("/", "").split("/").dropRight(1).mkString("""""","""  ::  ""","""""")
  val getSingleApiUrlLiftweb: Lit.String = Lit.String(getSingleApiUrlLiftFormat)
  val deleteApiUrlLiftweb: Lit.String = Lit.String(getSingleApiUrlLiftFormat)
  
  val getMultipleApiSummaryVal = Lit.String(s"$getMultipleApiSummary")
  val getSingleApiSummaryVal = Lit.String(s"$getSingleApiSummary")
  val deleteSingleApiSummaryVal = Lit.String(s"$deleteSingleApiSummary")
  val createSingleApiSummaryVal = Lit.String(s"$createSingleApiSummary")
  
  val getMultipleApiDescriptionVal = Lit.String(s"$getApiDescription")
  val createSingleApiDescriptionVal = Lit.String(s"$createApiDescription")
  val getSingleApiDescriptionVal = Lit.String(s"$getSingleDescription")
  val deleteSingleApiDescriptionVal = Lit.String(s"$deleteApiDescription")
  
  val errorMessageBody: Lit.String = Lit.String(s"OBP-31001: ${modelNameCapitalized} not found. Please specify a valid value for ${modelNameUpperCase}_ID.")
  val errorMessageName: Pat.Var = Pat.Var(Term.Name(s"${modelNameCapitalized}NotFound"))
  val errorMessageVal: Defn.Val = q"""val TemplateNotFound = $errorMessageBody""".copy(pats = List(errorMessageName))
  val errorMessage: Term.Name = Term.Name(errorMessageVal.pats.head.toString())

  val getTemplatesResourceCode: Term.ApplyInfix =q"""
    resourceDocs += ResourceDoc(
      getTemplates,
      apiVersion,
      "getTemplates",
      "GET",
      $getMultipleApiUrlVal,        
      $getMultipleApiSummaryVal,       
      $getMultipleApiDescriptionVal,
      emptyObjectJson,
      templatesJson,
      List(UserNotLoggedIn, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      apiTagApiBuilder :: Nil
    )"""
  val getTemplatesPartialFunction: Defn.Val = q"""
    lazy val getTemplates: OBPEndpoint ={
      case ($getApiUrlLiftweb:: Nil) JsonGet req =>
        cc =>
        {
          for{
            u <- $getMultipleApiAuthenticationStatement 
            templates <- APIBuilder_Connector.getTemplates
            templatesJson = JsonFactory_APIBuilder.createTemplates(templates)
            jsonObject:JValue = decompose(templatesJson)
          }yield{
            successJsonResponse(jsonObject)
          }
        }
    }"""
  
  
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
  
  val getTemplatePartialFunction: Defn.Val = q"""
    lazy val getTemplate: OBPEndpoint ={
      case ($getSingleApiUrlLiftweb :: templateId :: Nil) JsonGet _ => {
        cc =>
        {
          for{
            u <- $getSingleApiAuthenticationStatement
            template <- APIBuilder_Connector.getTemplateById(templateId) ?~! $errorMessage
            templateJson = JsonFactory_APIBuilder.createTemplate(template)
            jsonObject:JValue = decompose(templateJson)
          }yield{
            successJsonResponse(jsonObject)
          }
        }
      }
    }"""
  
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
  
  val deleteTemplatePartialFunction: Defn.Val = q"""
    lazy val deleteTemplate: OBPEndpoint ={
      case ($deleteApiUrlLiftweb :: templateId :: Nil) JsonDelete _ => {
        cc =>
        {
          for{
            u <- $deleteSingleApiAuthenticationStatement
            template <- APIBuilder_Connector.getTemplateById(templateId) ?~! $errorMessage
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
  // MappedTemplate_6099750036365020434.create
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
import code.api.util.ApiTag._
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
    implicit val formats = code.api.util.CustomJsonFormats.formats

    $errorMessageVal;
    def endpointsOfBuilderAPI =  getTemplates :: createTemplate :: getTemplate :: deleteTemplate:: Nil
    
 
    $getTemplatesResourceCode
    $getTemplatesPartialFunction
        
    $createTemplateResourceCode
    $createTemplatePartialFunction
                             
    $getTemplateResourceCode
    $getTemplatePartialFunction
        
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
