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

import java.io.File
import java.nio.file.Files

import scala.meta._
import net.liftweb.json
import net.liftweb.json.JsonAST.{JField, JObject, JString}
import net.liftweb.json.{JValue, JsonAST}

object APIBuilder
{
  def main(args: Array[String]): Unit = {
    val jsonStringFromFile = scala.io.Source.fromFile("src/main/scala/code/api/APIBuilder/apisResource.json").mkString 
    val jsonJValueFromFile = json.parse(jsonStringFromFile)
    val resourceDocsJObject= jsonJValueFromFile.\("resource_docs").children.asInstanceOf[List[JObject]]
    
    val getMultipleApiJValue = resourceDocsJObject.filter(jObject => jObject.\("request_verb") == JString("GET")&& !jObject.\("request_url").asInstanceOf[JString].values.contains("_ID")).head
    val getSingleApiJValue = resourceDocsJObject.filter(jObject => jObject.\("request_verb") == JString("GET")&& jObject.\("request_url").asInstanceOf[JString].values.contains("_ID")).head
    val createSingleApiJValue = resourceDocsJObject.filter(_.\("request_verb") == JString("POST")).head
    val deleteSingleApiJValue = resourceDocsJObject.filter(_.\("request_verb") == JString("DELETE")).head
    
    
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
    
    val getApiUrl: String = (getMultipleApiJValue \ "request_url").asInstanceOf[JString].values //eg: /my/template
    val getSingleApiUrl: String = (getSingleApiJValue \ "request_url").asInstanceOf[JString].values //eg: /my/template
    val createSingleApiUrl: String = (createSingleApiJValue \ "request_url").asInstanceOf[JString].values //eg: /my/template
    val deleteSingleApiUrl: String = (deleteSingleApiJValue \ "request_url").asInstanceOf[JString].values //eg: /my/template
    val getApiUrlFromJsonFile: String = "/file"+getApiUrl //eg: /my/template
    
    val getApiResponseBody: JValue = getMultipleApiJValue \\ "success_response_body"
    
    //TODO, this is super important for all the endpoint, this will be used as Model Name Later, for now, only hard code it here.
    val modelName = "MappedTemplate_"+Math.abs(scala.util.Random.nextLong())
    val modelTypeName = Type.Name(modelName)
    val modelTermName = Term.Name(modelName)
    val modelInit =Init.apply(Type.Name(modelName),Term.Name(modelName),Nil)
    val modelNameJson = "TemplateJson"//getApiResponseBody.children.head.asInstanceOf[JsonAST.JObject].obj.head.name.toLowerCase.capitalize
    
    
    val getApiAuthenticationStatement: Term.ApplyInfix = getApiAuthentication match {
      case true => q"cc.user ?~ UserNotLoggedIn"
      case false => q"Full(1) ?~ UserNotLoggedIn" //This will not throw error, only a placeholder 
    }
    
    val getSingleApiAuthenticationStatement: Term.ApplyInfix = getSingleApiAuthentication match {
      case true => q"cc.user ?~ UserNotLoggedIn"
      case false => q"Full(1) ?~ UserNotLoggedIn" //This will not throw error, only a placeholder 
    }
    
    val createSingleApiAuthenticationStatement: Term.ApplyInfix = createSingleApiAuthentication match {
      case true => q"cc.user ?~ UserNotLoggedIn"
      case false => q"Full(1) ?~ UserNotLoggedIn" //This will not throw error, only a placeholder 
    }
    
    val deleteSingleApiAuthenticationStatement: Term.ApplyInfix = deleteSingleApiAuthentication match {
      case true => q"cc.user ?~ UserNotLoggedIn"
      case false => q"Full(1) ?~ UserNotLoggedIn" //This will not throw error, only a placeholder 
    }
    
    
    val getApiUrlVal = q""" "/templates" """.copy(s"$getApiUrl")
    val getSingleApiUrlVal = q""" "/templates" """.copy(s"$getSingleApiUrl")
    val createSingleApiUrlVal = q""" "/templates" """.copy(s"$createSingleApiUrl")
    val deleteSingleApiUrlVal = q""" "/templates" """.copy(s"$deleteSingleApiUrl")
    val getApiUrlFromJsonFileVal = q""" "/templates" """.copy(s"$getApiUrlFromJsonFile")
    val getApiSummaryVal = q""" "" """.copy(s"$getApiSummary")
    val getSingleApiSummaryVal = q""" "" """.copy(s"$getSingleApiSummary")
    val createSingleApiSummaryVal = q""" "" """.copy(s"$createSingleApiSummary")
    val deleteSingleApiSummaryVal = q""" "" """.copy(s"$deleteSingleApiSummary")
    val getApiSummaryFromJsonFileVal = q""" "" """.copy(s"$getApiSummaryFromJsonFile")
    val getApiDescriptionVal = q""" "" """.copy(s"$getApiDescription")
    val getSingleApiDescriptionVal = q""" "" """.copy(s"$getSingleApiDescription")
    val createSingleApiDescriptionVal = q""" "" """.copy(s"$createSingleApiDescription")
    val deleteSingleApiDescriptionVal = q""" "" """.copy(s"$deleteSingleApiDescription")
    val getApiDescriptionFromJsonFileVal = q""" "" """.copy(s"$getApiDescriptionFromJsonFile")
    
    val getTemplateFromFileResourceCode: Term.ApplyInfix = 
      q"""
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
    
    val getTemplatesResourceCode: Term.ApplyInfix = 
      q"""
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
        )  
        """
    
    val getTemplateResourceCode: Term.ApplyInfix = 
    q"""
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
      )
    """
    
    val createTemplateResourceCode: Term.ApplyInfix = 
    q"""
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
       )
    """
    
    val deleteTemplateResourceCode: Term.ApplyInfix = 
    q"""
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
     )
    """
    
    //TODO, escape issue:return the space, I added quotes in the end: allSourceCode.syntax.replaceAll("""  ::  """,""""  ::  """")
    //from "/my/template" --> "my  ::  template" 
    val getApiUrlLiftFormat = getApiUrl.replaceFirst("/", "").split("/").mkString("""""","""  ::  ""","""""")
    val createApiUrlLiftFormat = createSingleApiUrl.replaceFirst("/", "").split("/").mkString("""""","""  ::  ""","""""")
    val deleteApiUrlLiftFormat = deleteSingleApiUrl.replaceFirst("/", "").split("/").dropRight(1).mkString("""""","""  ::  ""","""""")
    val getSingleApiUrlLiftFormat = getSingleApiUrl.replaceFirst("/", "").split("/").dropRight(1).mkString("""""","""  ::  ""","""""")
    val getApiUrlLiftweb: Lit.String = q""" "templates"  """.copy(getApiUrlLiftFormat)
    val createApiUrlLiftweb: Lit.String = q""" "templates"  """.copy(createApiUrlLiftFormat)
    val deleteApiUrlLiftweb: Lit.String = q""" "templates"  """.copy(deleteApiUrlLiftFormat)
    val getSingleApiUrlLiftweb: Lit.String = q""" "templates"  """.copy(getSingleApiUrlLiftFormat)
    
    
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
    
    
    val jsonFieldValue =s"List[$modelNameJson]" // List[Templates]
    val jsonFieldDefaultValue = s"List($modelNameJson())" //List(Templates())
    
    //List(author, pages, points)
    val modelFieldNames: List[String] = getApiResponseBody.children.head.asInstanceOf[JsonAST.JObject].obj.head.value.asInstanceOf[JsonAST.JArray].children.head.asInstanceOf[JsonAST.JObject].obj.map(_.name)

    //List(String, Int, Double)
    val modelFieldTypes: List[String] = modelFieldNames.map(key => getApiResponseBody.findField{
           case JField(n, v) => n == key
         }).map(_.get.value.getClass.getSimpleName.replaceFirst("J","")).toList
    
    //List(Chinua Achebe, 209, 1.3)
    val modelFieldDefaultValues: List[Any] = modelFieldNames.map(key => getApiResponseBody.findField{
           case JField(n, v) => n == key
         }).map(_.get.value.values).toList
    
    
    //List(author: String = `Chinua Achebe`, tutor: String = `1123123 1312`, pages: Int = 209, points: Double = 1.3)
    val modelCaseClassParams: List[Term.Param] = { 
      val fieldNames = for{
        i <- 0 until modelFieldNames.size
        modelFieldName = Term.Name(modelFieldNames(i).toLowerCase)
        modelFieldType = Type.Name(modelFieldTypes(i))
        modelFieldDefaultValue =  modelFieldDefaultValues(i) match {
          case inputDefaultValue: String if(! inputDefaultValue.contains(" "))  => Term.Name(s"`$inputDefaultValue`")
          case inputDefaultValue => Term.Name(s"$inputDefaultValue")
        }
        
      } yield 
        Term.Param(Nil, modelFieldName, Some(modelFieldType), Some(modelFieldDefaultValue))
      fieldNames.toList
    }
    
    // List(def author: String, 
    // def tutor: String, 
    // def pages: Int, 
    // def points: Double, 
    // def templateId: String)
    val modelTraitMethods: List[Decl.Def] =
    {
      val fieldNames = for
        {
        i <- 0 until modelFieldNames.size
        methodName = Term.Name(modelFieldNames(i).toLowerCase)
        methodType = Type.Name(s"${modelFieldTypes(i)}")
      } yield 
          Decl.Def(Nil, methodName, Nil, Nil, methodType)
      
      fieldNames.toList++ List(Decl.Def(Nil,Term.Name("templateId"), Nil, Nil, Type.Name("String")))
    }

    val modelTraitSelf: Self = Self.apply(Name("_"), None)
    
  //    {
  //      `_` => def author: String
  //        def pages: Int
  //        def points: Double
  //        def templateId: String
  //    }
    val modelTraitImpl = Template.apply(Nil, Nil, modelTraitSelf, modelTraitMethods)
    
//    trait Template { `_` =>
//      def author: String
//      def tutor: String
//      def pages: Int
//      def points: Double
//      def templateId: String
//    }
    val modelTrait: Defn.Trait = q"""trait Template {}""".copy(templ = modelTraitImpl)
    
    
    def mappedString(objectName: Term.Name): Defn.Object = q"""object $objectName extends MappedString(this,100) """
    def mappedInt(objectName: Term.Name): Defn.Object = q"""object $objectName extends MappedInt(this) """
    def mappedDouble(objectName: Term.Name): Defn.Object = q"""object $objectName extends MappedDouble(this) """
    def mappedMethod(methodName: Term.Name,objectName: Term.Name, methodReturnType: Type.Name): Defn.Def = q"""override def $methodName: $methodReturnType = $objectName.get"""
    
    
//    List(
//      object mAuthor extends MappedString(this, 100), 
//      override def author: String = mAuthor.get, 
//      object mPages extends MappedInt(this), 
//      override def pages: Int = mPages.get, 
//      object mPoints extends MappedDouble(this), 
//      override def points: Double = mPoints.get
//    )
    val modelClassStatments =
    {
      val fieldNames = for
        {
        i <- 0 until modelFieldNames.size
        fieldNameString = modelFieldNames(i)
        fieldTypeString = modelFieldTypes(i)
        objectName = Term.Name(s"m${fieldNameString.capitalize}")
        methodName = Term.Name(fieldNameString)
        methodReturnType = Type.Name(fieldTypeString)
        stat = modelFieldTypes(i) match {
          case "String" => mappedString(objectName)
          case "Int" => mappedInt(objectName)
          case "Double" => mappedDouble(objectName)
          }
        methodStat = mappedMethod(methodName,objectName, methodReturnType)
      } yield 
          (stat,methodStat)
      fieldNames.flatMap (x => List(x._1, x._2)).toList
    }
    
    val modelClassExample: Defn.Class = q"""
    class $modelTypeName extends Template with LongKeyedMapper[$modelTypeName] with IdPK {
      def getSingleton = $modelTermName
      object mTemplateId extends MappedString(this,100)
      override def templateId: String = mTemplateId.get
    }"""
    
//    Template with LongKeyedMapper[MappedTemplate] with IdPK {
//      def getSingleton = MappedTemplate
//      object mTemplateId extends MappedString(this, 100)
//      override def templateId: String = mTemplateId.get
//    }
    val modelClassExampleTempl= modelClassExample.templ
    
//    Template with LongKeyedMapper[MappedTemplate] with IdPK {
//      override def author: String = mAuthor.get
//      object mPages extends MappedInt(this)
//      override def pages: Int = mPages.get
//      object mPoints extends MappedDouble(this)
//      override def points: Double = mPoints.get
//      def getSingleton = MappedTemplate
//      object mTemplateId extends MappedString(this, 100)
//      override def templateId: String = mTemplateId.get
//    }
    val newTempls = modelClassExampleTempl.copy(stats = modelClassStatments++modelClassExampleTempl.stats)
     
//    class MappedTemplate extends Template with LongKeyedMapper[MappedTemplate] with IdPK {
//      object mAuthor extends MappedString(this, 100)
//      override def author: String = mAuthor.get
//      object mPages extends MappedInt(this)
//      override def pages: Int = mPages.get
//      object mPoints extends MappedDouble(this)
//      override def points: Double = mPoints.get
//      def getSingleton = MappedTemplate
//      object mTemplateId extends MappedString(this, 100)
//      override def templateId: String = mTemplateId.get
//    }
    val modelClass = modelClassExample.copy(templ = newTempls)
    
    
/*
* ##################################################################################################
* ######################################APIBuilder_Connector###################################################
* ##################################################################################################
* */
    val createModelJsonMethodFields= {
      val fieldNames = for{
        i <- 0 until modelFieldNames.size
        fieldName = modelFieldNames(i)
      } 
        yield 
          Term.Name(s".m${fieldName.capitalize}(createTemplateJson.${fieldName})")
      fieldNames.toList.mkString("")
    }

    val createModelJsonMethodBody: Term.Apply = q"""MappedTemplate.create.saveMe()""".copy(fun = Term.Name(s"$modelName.create.mTemplateId(UUID.randomUUID().toString)$createModelJsonMethodFields.saveMe"))
    
    val createModelJsonMethod: Defn.Def = q"""def createTemplate(createTemplateJson: CreateTemplateJson) = Full($createModelJsonMethodBody)"""
    
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
  
    val builderAPIMethodsFile = new File("src/main/scala/code/api/builder/APIMethods_APIBuilder.scala")
    builderAPIMethodsFile.getParentFile.mkdirs()
    Files.write(
      builderAPIMethodsFile.toPath,
      apiSource.syntax.replaceAll("""  ::  """,""""  ::  """").getBytes("UTF-8")
    )
    
    /*
    * ##################################################################################################
    * ######################################Json_Factory###################################################
    * ##################################################################################################
    * */
   
    //List(templateId:String = "11231231312" ,author: String = `Chinua Achebe`, tutor: String = `11231231312`, pages: Int = 209, points: Double = 1.3)
    //Added the templatedId to `modelCaseClassParams`
    val templateIdField: Term.Param = Term.Param(Nil, Term.Name("templateId"), Some(Type.Name("String")), Some(Term.Name("`11231231312`")))
    val templateJsonClassParams = List(templateIdField)++ modelCaseClassParams
    
    //case class TemplateJson(templateId: String = """1123123 1312""", author: String = """Chinua Achebe""", tutor: String = """1123123 1312""", pages: Int = 209, points: Double = 1.3)
    val TemplateJsonClass: Defn.Class = q"""case class TemplateJson(..$templateJsonClassParams) """
    
    //case class Template(author: String = `Chinua Achebe`, pages: Int = 209, points: Double = 1.3)
    //Note: No `templateId` in this class, the bank no need provide it, obp create a uuid for it.
    val createTemplateJsonClass: Defn.Class = q"""case class CreateTemplateJson(..$modelCaseClassParams) """
    
    //List(template.templateId, template.author, template.tutor, template.pages, template.points)
    val createTemplateJsonArgs: List[Term.Name] = {
      val fieldNames = for{
        i <- 0 until modelFieldNames.size
      } 
        yield 
          Term.Name("template." + modelFieldNames(i))
      List(Term.Name("template.templateId")) ++ (fieldNames.toList)
    }
    
    //TemplateJson(template.templateId, template.author, template.tutor, template.pages, template.points)
    val createTemplateJsonApply: Term.Apply = q"""TemplateJson()""".copy(fun = Term.Name("TemplateJson"), args = createTemplateJsonArgs)
    
    //def createTemplate(template: Template) = TemplateJson(template.templateId, template.author, template.tutor, template.pages, template.points)
    val createTemplateDef: Defn.Def =q"""def createTemplate(template: Template) = $createTemplateJsonApply"""
    
//    def createTemplates(templates: List[Template]) = templates.map(template => TemplateJson(template.templateId, template.author, template.tutor, template.pages, template.points))
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

$createTemplateJsonClass
$TemplateJsonClass

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
    val builderJsonFactoryFile = new File("src/main/scala/code/api/builder/JsonFactory_APIBuilder.scala")
    builderJsonFactoryFile.getParentFile.mkdirs()
    Files.write(
      builderJsonFactoryFile.toPath,
      jsonFactorySource.syntax
        //TODO,maybe fix later ! in scalameta, Term.Param(Nil, modelFieldName, Some(modelFieldType), Some(modelFieldDefaultValue)) => the default value should be a string in API code.
        .replaceAll("""`""",""""""""")
        .getBytes("UTF-8")
    )
    
    println("Congratulations! You make the new APIs. Please restart OBP-API server!")
  }
}