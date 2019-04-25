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
package code.api.APIBuilder

import java.io.File
import java.nio.file.Files
import code.api.util.APIUtil
import net.liftweb.json.JsonAST.{JField, JObject, JString}
import net.liftweb.json.JValue
import scala.meta._

object APIBuilderModel
{
  def main(args: Array[String]) = overwriteApiCode(apiSource, jsonFactorySource)
  
  def createTemplateJsonClass(className: String, templateJsonClassParams: List[Term.Param]) = q"""case class TemplateJson(..$templateJsonClassParams) """.copy(name = Type.Name(className))
  
  def getApiUrl(jsonJValueFromFile: JValue) = {
    val inputUrl = (jsonJValueFromFile \"request_url").asInstanceOf[JString].values
    
    // if input is `my/template` --> `/my/template`
    val checkedStartWith =  inputUrl match {
      case inputUrl if (!inputUrl.startsWith("""/""")) =>"""/"""+inputUrl 
      case _ => inputUrl
    }
    
    // if input is `/my/template/` --> `/my/template`
    checkedStartWith.endsWith("""/""") match {
      case true => checkedStartWith.dropRight(1)
      case _ => checkedStartWith
    }
    
  } //eg: /my/template
  
  def getModelName(jsonJValueFromFile: JValue) = jsonJValueFromFile.asInstanceOf[JObject].obj.map(_.name).filter(_!="request_url").head
  
  def getModelFieldsNames(modelFieldsJValue: JValue)= modelFieldsJValue.asInstanceOf[JObject].obj.map(_.name) 
  
  def getModelFieldsTypes(modelFieldsNames: List[String], modelFieldsJValue: JValue)= modelFieldsNames
    .map(key => modelFieldsJValue.findField{case JField(n, v) => n == key})
    .map(_.get.value.getClass.getSimpleName.replaceFirst("J",""))
  
  def getModelFieldDefaultValues(modelFieldsNames: List[String], modelFieldsJValue: JValue)= modelFieldsNames
    .map(key => modelFieldsJValue.findField{case JField(n, v) => n == key})
    .map(_.get.value.values)
  
  def getModelTrait(modelFieldsNames: List[String], modelFieldTypes: List[String])= {
    val methodStatements = for
      {
      i <- 0 until modelFieldsNames.size
      methodName = Term.Name(modelFieldsNames(i).toLowerCase)
      methodType = Type.Name(s"${modelFieldTypes(i)}")
    } yield 
        Decl.Def(Nil, methodName, Nil, Nil, methodType)
    
    //List(def author: String, 
    // def tutor: String, 
    // def pages: Int, 
    // def points: Double, 
    // def templateId: String)
     val modelTraitMethods = methodStatements.toList++ List(Decl.Def(Nil, Term.Name("templateId"), Nil, Nil, Type.Name("String")))
    
     val modelTraitSelf: Self = Self.apply(Name("_"), None)
   
     //{
     //  `_` => def author: String
     //    def pages: Int
     //    def points: Double
     //    def templateId: String
     //}
     val modelTraitImpl = Template.apply(Nil, Nil, modelTraitSelf, modelTraitMethods)
     
     //    trait Template { `_` =>
     //      def author: String
     //      def tutor: String
     //      def pages: Int
     //      def points: Double
     //      def templateId: String
     //    }
     q"""trait Template {}""".copy(templ = modelTraitImpl)
        
    }
  
  def getModelCaseClassParams(modelFieldsNames: List[String], modelFieldTypes: List[String], modelFieldDefaultValues: List[Any])  ={
    val fieldNames = for {
      i <- 0 until modelFieldsNames.size
      modelFieldName = Term.Name(modelFieldsNames(i).toLowerCase)
      modelFieldType = Type.Name(modelFieldTypes(i))
      modelFieldDefaultValue = modelFieldDefaultValues(i) match{
        case inputDefaultValue: String if (!inputDefaultValue.contains(" ")) => Term.Name(s"`$inputDefaultValue`")
        case inputDefaultValue => Term.Name(s"$inputDefaultValue")
      }} yield
        Term.Param(Nil, modelFieldName, Some(modelFieldType), Some(modelFieldDefaultValue))
    fieldNames.toList
  }
  
  def getAuthenticationStatement (needAuthentication: Boolean) = needAuthentication match {
    case true => q"cc.user ?~ UserNotLoggedIn"
    case false => q"Full(1) ?~ UserNotLoggedIn" //This will not throw error, only a placeholder 
  }
  
  //object mAuthor extends MappedString(this, 100)
  def stringToMappedObject(objectName: String, objectType: String): Defn.Object = {
    val objectTermName = Term.Name(objectName)
    objectType match {
      case "String" => q"""object $objectTermName extends MappedString(this,100) """
      case "Int" => q"""object $objectTermName extends MappedInt(this) """
      case "Double" => q"""object $objectTermName extends MappedDouble(this) """
    }
  }
  
  //override def author: String = mAuthor.get
  def stringToMappedMethod(methodNameString: String, methodReturnTypeString: String): Defn.Def ={
    
    val methodName = Term.Name(methodNameString)
    val methodReturnType = Type.Name(methodReturnTypeString)
    val mappedObject = Term.Name(s"m${methodNameString.capitalize}")
    
    q"""override def $methodName: $methodReturnType = $mappedObject.get"""
  } 
  
  def getModelClassStatements(modelFieldsNames: List[String], modelFieldTypes: List[String]) ={
    val fieldNames = for
      {
      i <- 0 until modelFieldsNames.size
      fieldNameString = modelFieldsNames(i)
      fieldTypeString = modelFieldTypes(i)
      mappedObject = stringToMappedObject(s"m${fieldNameString.capitalize}", fieldTypeString.capitalize)
      mappedMethod = stringToMappedMethod(fieldNameString, fieldTypeString)
    } yield 
        (mappedObject,mappedMethod)
    fieldNames.flatMap (x => List(x._1, x._2)).toList
  }
  
  def getModelClass(modelTypeName: Type.Name, modelTermName: Term.Name, modelFieldsNames: List[String], modelFieldTypes: List[String]) ={
    val modelClassStatements = getModelClassStatements(modelFieldsNames, modelFieldTypes)
    
    val modelClassExample: Defn.Class = q"""
      class $modelTypeName extends Template with LongKeyedMapper[$modelTypeName] with IdPK {
        def getSingleton = $modelTermName
        object mTemplateId extends MappedString(this,100)
        override def templateId: String = mTemplateId.get
      }"""
    
    //Template with LongKeyedMapper[MappedTemplate] with IdPK {
    //  def getSingleton = MappedTemplate
    //  object mTemplateId extends MappedString(this, 100)
    //  override def templateId: String = mTemplateId.get
    //}
    val modelClassExampleTempl= modelClassExample.templ
    
    //Template with LongKeyedMapper[MappedTemplate] with IdPK {
    //  override def author: String = mAuthor.get
    //  object mPages extends MappedInt(this)
    //  override def pages: Int = mPages.get
    //  object mPoints extends MappedDouble(this)
    //  override def points: Double = mPoints.get
    //  def getSingleton = MappedTemplate
    //  object mTemplateId extends MappedString(this, 100)
    //  override def templateId: String = mTemplateId.get
    //}
    val newTempls = modelClassExampleTempl.copy(stats = modelClassStatements++modelClassExampleTempl.stats)
     
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
    modelClassExample.copy(templ = newTempls)
  }
  
  //def createTemplate(createTemplateJson: CreateTemplateJson) = 
  // Full(MappedTemplate_2145180497484573086.create
  // .mTemplateId(UUID.randomUUID().toString)
  // .mAuthor(createTemplateJson.author)
  // .mPages(createTemplateJson.pages)
  // .mPoints(createTemplateJson.points)
  // .saveMe())"
  def generateCreateModelJsonMethod(modelFieldsNames: List[String], modelMappedName: String)= {
    val fieldNames = for {
      i <- 0 until modelFieldsNames.size
      fieldName = modelFieldsNames(i)
    } yield 
      Term.Name(s".m${fieldName.capitalize}(createTemplateJson.${fieldName})")
    
    val createModelJsonMethodFields = fieldNames.toList.mkString("")

    val createModelJsonMethodBody: Term.Apply = q"""MappedTemplate.create.saveMe()""".copy(fun = Term.Name(s"$modelMappedName.create.mTemplateId(UUID.randomUUID().toString)$createModelJsonMethodFields.saveMe"))
    
    q"""def createTemplate(createTemplateJson: CreateTemplateJson) = Full($createModelJsonMethodBody)""" 
  }
  
  // TemplateJson(template.templateId, template.author, template.tutor, template.pages, template.points)
  def generateCreateTemplateJsonApply(modelFieldsNames: List[String]): Term.Apply = {
    val fieldNames = for{
      i <- 0 until modelFieldsNames.size
    } yield 
      Term.Name("template." + modelFieldsNames(i))
    
   //List(template.templateId, template.author, template.tutor, template.pages, template.points)
    val createTemplateJsonArgs =  List(Term.Name("template.templateId")) ++ (fieldNames.toList)
    
    q"""TemplateJson()""".copy(fun = Term.Name("TemplateJson"), args = createTemplateJsonArgs) 
  }
  
  def overwriteCurrentFile(sourceCode: Source, path: String) = {
    val builderAPIMethodsFile = new File(path)
    builderAPIMethodsFile.getParentFile.mkdirs()
    if(path.contains("APIMethods_APIBuilder"))
      Files.write(
      builderAPIMethodsFile.toPath,
      sourceCode.syntax
        //TODO,maybe fix later ! in scalameta, Term.Param(Nil, modelFieldName, Some(modelFieldType), Some(modelFieldDefaultValue)) => the default value should be a string in API code.
        .replaceAll("""`""","")
        .replaceAll("trait Template \\{ _ =>","trait Template \\{ `_` =>")
        .replaceAll("""  ::  """,""""  ::  """")
        .getBytes("UTF-8")
    )
    else
      Files.write(
      builderAPIMethodsFile.toPath,
      sourceCode.syntax
        //TODO,maybe fix later ! in scalameta, Term.Param(Nil, modelFieldName, Some(modelFieldType), Some(modelFieldDefaultValue)) => the default value should be a string in API code.
        .replaceAll("""`""",""""""""")
        .getBytes("UTF-8")
    )
  }
  
  def overwriteApiCode(apiSource: Source, jsonFactorySource:Source =jsonFactorySource) = {
    //APIMethods_APIBuilder.scala
    overwriteCurrentFile(apiSource,"src/main/scala/code/api/builder/APIMethods_APIBuilder.scala")
    
    //JsonFactory_APIBuilder.scala
    overwriteCurrentFile(jsonFactorySource, "src/main/scala/code/api/builder/JsonFactory_APIBuilder.scala")
    
    println("Congratulations! You make the new APIs. Please restart OBP-API server!")
  }
  
  val jsonJValueFromFile: JValue = APIUtil.getJValueFromFile("src/main/scala/code/api/APIBuilder/APIModelSource.json")
  
  //"/templates"
  val apiUrl= getApiUrl(jsonJValueFromFile)
  //"template"
  val modelName = getModelName(jsonJValueFromFile)
  //TEMPLATE
  val modelNameUpperCase = modelName.toUpperCase
  //template
  val modelNameLowerCase = modelName.toLowerCase
  //Template
  val modelNameCapitalized = modelNameLowerCase.capitalize
  val modelFieldsJValue: JValue = jsonJValueFromFile \ modelName
  
  //MappedTemplate_6285959801482269169
  val modelMappedName = s"Mapped${modelNameCapitalized}_"+Math.abs(scala.util.Random.nextLong())
  val modelTypeName: Type.Name = Type.Name(modelMappedName)
  val modelTermName = Term.Name(modelMappedName)
  val modelInit = Init.apply(Type.Name(modelMappedName), Term.Name(modelMappedName), Nil)
  
  //getApiUrlVal: scala.meta.Lit.StrincreateModelJsonMethodField = "/templates"
  val getApiUrlVal: Lit.String = Lit.String(s"$apiUrl")
  //getSingleApiUrlVal: scala.meta.Lit.String = "/templates/TEMPLATE_ID"
  val getSingleApiUrlVal = Lit.String(s"$apiUrl/${modelNameUpperCase}_ID")
  //createSingleApiUrlVal: scala.meta.Lit.String = "/templates"
  val createSingleApiUrlVal = Lit.String(s"$apiUrl")
  //deleteSingleApiUrlVal: scala.meta.Lit.String = "/templates/TEMPLATE_ID"
  val deleteSingleApiUrlVal = Lit.String(s"$apiUrl/${modelNameUpperCase}_ID")
  //TODO, escape issue:return the space, I added quotes in the end: allSourceCode.syntax.replaceAll("""  ::  """,""""  ::  """")
  //from "/my/template" --> "my  ::  template" 
  val apiUrlLiftFormat = apiUrl.replaceFirst("/", "").split("/").mkString("""""","""  ::  ""","""""")
  val apiUrlLiftweb: Lit.String = q""" "templates"  """.copy(apiUrlLiftFormat)
  
  val getApiSummaryVal: Lit.String = Lit.String(s"Get ${modelNameCapitalized}s")
  val getSingleApiSummaryVal = Lit.String(s"Get ${modelNameCapitalized}")
  val createSingleApiSummaryVal = Lit.String(s"Create ${modelNameCapitalized}")
  val deleteSingleApiSummaryVal = Lit.String(s"Delete ${modelNameCapitalized}")

  val getApiDescriptionVal: Lit.String = Lit.String(s"Return All ${modelNameCapitalized}s")
  val getSingleApiDescriptionVal = Lit.String(s"Return One ${modelNameCapitalized} By Id")
  val createSingleApiDescriptionVal = Lit.String(s"Create One ${modelNameCapitalized}")
  val deleteSingleApiDescriptionVal = Lit.String(s"Delete One ${modelNameCapitalized}")
  
  val errorMessageBody: Lit.String = Lit.String(s"OBP-31001: ${modelNameCapitalized} not found. Please specify a valid value for ${modelNameUpperCase}_ID.")
  val errorMessageName: Pat.Var = Pat.Var(Term.Name(s"${modelNameCapitalized}NotFound"))
  val errorMessageVal: Defn.Val = q"""val TemplateNotFound = $errorMessageBody""".copy(pats = List(errorMessageName))
  val errorMessage: Term.Name = Term.Name(errorMessageVal.pats.head.toString())

  val getPartialFuncTermName = Term.Name(s"get${modelNameCapitalized}s")
  val getPartialFuncName = Pat.Var(getPartialFuncTermName)
  val getSinglePartialFuncTermName = Term.Name(s"get${modelNameCapitalized}")
  val getSinglePartialFuncName = Pat.Var(getSinglePartialFuncTermName)
  val createPartialFuncTermName = Term.Name(s"create${modelNameCapitalized}")
  val createPartialFuncName = Pat.Var(createPartialFuncTermName)
  val deletePartialFuncTermName = Term.Name(s"delete${modelNameCapitalized}")
  val deletePartialFuncName = Pat.Var(deletePartialFuncTermName)
  
  //implementedApiDefBody: scala.meta.Term.Name = `getTemplates :: getTemplate :: createTemplate :: deleteTemplate :: Nil`
  val implementedApiDefBody= Term.Name(s"${getPartialFuncTermName.value} :: ${getSinglePartialFuncTermName.value} :: ${createPartialFuncTermName.value} :: ${deletePartialFuncTermName.value} :: Nil")
  //implementedApisDef: scala.meta.Defn.Def = def endpointsOfBuilderAPI = `getTemplates :: getTemplate :: createTemplate :: deleteTemplate :: Nil`
  val implementedApisDef: Defn.Def = q"""def endpointsOfBuilderAPI = $implementedApiDefBody"""
  
  val getTemplatesResourceCode: Term.ApplyInfix =q"""
    resourceDocs += ResourceDoc(
      $getPartialFuncTermName,
      apiVersion,
      ${getPartialFuncTermName.value},
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
  val getTemplateResourceCode: Term.ApplyInfix = q"""
    resourceDocs += ResourceDoc(
      $getSinglePartialFuncTermName, 
      apiVersion, 
      ${getSinglePartialFuncTermName.value}, 
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
  val createTemplateResourceCode: Term.ApplyInfix = q"""
    resourceDocs += ResourceDoc(
      $createPartialFuncTermName, 
      apiVersion, 
      ${createPartialFuncTermName.value},  
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
      $deletePartialFuncTermName, 
      apiVersion, 
      ${deletePartialFuncTermName.value},
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
  
  val authenticationStatement: Term.ApplyInfix = getAuthenticationStatement(true) 
  
  val getTemplatesPartialFunction: Defn.Val = q"""
    lazy val $getPartialFuncName: OBPEndpoint ={
      case ($apiUrlLiftweb:: Nil) JsonGet req =>
        cc =>
        {
          for{
            u <- $authenticationStatement 
            templates <- APIBuilder_Connector.getTemplates
            templatesJson = JsonFactory_APIBuilder.createTemplates(templates)
            jsonObject:JValue = decompose(templatesJson)
          }yield{
            successJsonResponse(jsonObject)
          }
        }
    }"""
  val getTemplatePartialFunction: Defn.Val = q"""
    lazy val $getSinglePartialFuncName: OBPEndpoint ={
      case ($apiUrlLiftweb :: templateId :: Nil) JsonGet _ => {
        cc =>
        {
          for{
            u <- $authenticationStatement
            template <- APIBuilder_Connector.getTemplateById(templateId) ?~! $errorMessage
            templateJson = JsonFactory_APIBuilder.createTemplate(template)
            jsonObject:JValue = decompose(templateJson)
          }yield{
            successJsonResponse(jsonObject)
          }
        }
      }
    }"""
  val createTemplatePartialFunction: Defn.Val = q"""
    lazy val $createPartialFuncName: OBPEndpoint ={
      case ($apiUrlLiftweb:: Nil) JsonPost json -> _ => {
        cc =>
        {
          for{
            createTemplateJson <- tryo(json.extract[CreateTemplateJson]) ?~! InvalidJsonFormat
            u <- $authenticationStatement
            template <- APIBuilder_Connector.createTemplate(createTemplateJson)
            templateJson = JsonFactory_APIBuilder.createTemplate(template)
            jsonObject:JValue = decompose(templateJson)
          }yield{
            successJsonResponse(jsonObject)
          }
        }
      }
    }"""
  val deleteTemplatePartialFunction: Defn.Val = q"""
    lazy val $deletePartialFuncName: OBPEndpoint ={
      case ($apiUrlLiftweb :: templateId :: Nil) JsonDelete _ => {
        cc =>
        {
          for{
            u <- $authenticationStatement
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
    }"""
  
  //List(author, pages, points)
  val modelFieldsNames: List[String] = getModelFieldsNames(modelFieldsJValue)

  //List(String, Int, Double)
  val modelFieldsTypes: List[String] = getModelFieldsTypes(modelFieldsNames, modelFieldsJValue)
  
  //List(Chinua Achebe, 209, 1.3)
  val modelFieldsDefaultValues: List[Any] = getModelFieldDefaultValues(modelFieldsNames, modelFieldsJValue)
  
  //List(author: String = `Chinua Achebe`, tutor: String = `1123123 1312`, pages: Int = 209, points: Double = 1.3)
  val modelCaseClassParams: List[Term.Param] = getModelCaseClassParams(modelFieldsNames, modelFieldsTypes, modelFieldsDefaultValues)
  
  //    trait Template { `_` =>
  //      def author: String
  //      def tutor: String
  //      def pages: Int
  //      def points: Double
  //      def templateId: String
  //    }
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
  
  val createModelJsonMethod: Defn.Def = generateCreateModelJsonMethod(modelFieldsNames, modelMappedName)
  
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
    val apiVersion = ApiVersion.apiBuilder
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(resourceDocs, apiRelations)
    implicit val formats = code.api.util.CustomJsonFormats.formats
    
    $errorMessageVal
    $implementedApisDef
    
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
  */
  
  //List(id:String = "11231231312" ,author: String = `Chinua Achebe`, tutor: String = `11231231312`, pages: Int = 209, points: Double = 1.3)
  //Added the id to `modelCaseClassParams`
  val templateIdField: Term.Param = Term.Param(Nil, Term.Name(s"id"), Some(Type.Name("String")), Some(Term.Name("`11231231312`")))
  val templateJsonClassParams: List[Term.Param] = List(templateIdField)++ modelCaseClassParams
  
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
    
  val jsonFactorySource: Source =
source"""
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
}