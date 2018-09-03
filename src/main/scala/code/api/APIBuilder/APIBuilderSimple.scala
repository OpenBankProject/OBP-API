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

import code.api.APIBuilder.APIBuilderSimple.modelFieldsJValue
import net.liftweb.json
import net.liftweb.json.JsonAST.{JField, JObject, JString}
import net.liftweb.json.{JValue, JsonAST}

import scala.meta._

object APIBuilderSimple
{
  
  val jsonStringFromFile: String = scala.io.Source.fromFile("src/main/scala/code/api/APIBuilder/modelSource.json").mkString 
  val jsonJValueFromFile: JValue = json.parse(jsonStringFromFile)
  
  //"/books"
  val apiUrl= getApiUrl(jsonJValueFromFile)
  //"book"
  val modelName = getModelName(jsonJValueFromFile)
  
  val modelFieldsJValue: JValue = jsonJValueFromFile \ modelName

  //BOOK
  val modelNameUpperCase = modelName.toUpperCase
  //book
  val modelNameLowerCase = modelName.toLowerCase
  //Book
  val modelNameCapitalized = modelNameLowerCase.capitalize
  
  //MappedBook_6285959801482269169
  val modelMappedName = s"Mapped${modelNameCapitalized}_"+Math.abs(scala.util.Random.nextLong())
  val modelTypeName = Type.Name(modelMappedName)
  val modelTermName = Term.Name(modelMappedName)
  val modelInit = Init.apply(Type.Name(modelMappedName), Term.Name(modelMappedName), Nil)
  
  //BookJson
  val modelJsonClassName = s"${modelNameCapitalized}Json"
  
  //getApiUrlVal: scala.meta.Lit.String = "/books"
  val getApiUrlVal: Lit.String = Lit.String(s"$apiUrl")
  //getSingleApiUrlVal: scala.meta.Lit.String = "/books/BOOK_ID"
  val getSingleApiUrlVal = Lit.String(s"$apiUrl/${modelNameUpperCase}_ID")
  //createSingleApiUrlVal: scala.meta.Lit.String = "/books"
  val createSingleApiUrlVal = Lit.String(s"$apiUrl")
  //deleteSingleApiUrlVal: scala.meta.Lit.String = "/books/BOOK_ID"
  val deleteSingleApiUrlVal = Lit.String(s"$apiUrl/${modelNameUpperCase}_ID")
  
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
  
  //implementedApiDefBody: scala.meta.Term.Name = `getBooks :: getBook :: createBook :: deleteBook :: Nil`
  val implementedApiDefBody= Term.Name(s"${getPartialFuncTermName.value} :: ${getSinglePartialFuncTermName.value} :: ${createPartialFuncTermName.value} :: ${deletePartialFuncTermName.value} :: Nil")
  //implementedApisDef: scala.meta.Defn.Def = def endpointsOfBuilderAPI = `getBooks :: getBook :: createBook :: deleteBook :: Nil`
  val implementedApisDef: Defn.Def = q"""def endpointsOfBuilderAPI = $implementedApiDefBody"""
  
  
  val getTemplatesResourceCode: Term.ApplyInfix = 
    q"""
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
      )  
      """
  
  val getTemplateResourceCode: Term.ApplyInfix = 
  q"""
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
    )
  """
  
  val createTemplateResourceCode: Term.ApplyInfix = 
  q"""
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
     )
  """
  
  val deleteTemplateResourceCode: Term.ApplyInfix = 
  q"""
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
   )
  """
  
  //TODO, escape issue:return the space, I added quotes in the end: allSourceCode.syntax.replaceAll("""  ::  """,""""  ::  """")
  //from "/my/template" --> "my  ::  template" 
  val apiUrlLiftFormat = apiUrl.replaceFirst("/", "").split("/").mkString("""""","""  ::  ""","""""")
  val apiUrlLiftweb: Lit.String = q""" "templates"  """.copy(apiUrlLiftFormat)
  
  val authenticationStatement: Term.ApplyInfix = true match {
    case true => q"cc.user ?~ UserNotLoggedIn"
    case false => q"Full(1) ?~ UserNotLoggedIn" //This will not throw error, only a placeholder 
  }
  
  
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
    }
    """
  
  val deleteTemplatePartialFunction: Defn.Val = q"""
    lazy val $deletePartialFuncName: OBPEndpoint ={
      case ($apiUrlLiftweb :: templateId :: Nil) JsonDelete _ => {
        cc =>
        {
          for{
            u <- $authenticationStatement
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
  
  def createTemplateJsonClass(className: String, templateJsonClassParams: List[Term.Param]) = q"""case class TemplateJson(..$templateJsonClassParams) """.copy(name = Type.Name(className))
  
  def getApiUrl(jsonJValueFromFile: JValue) = (jsonJValueFromFile \"request_url").asInstanceOf[JString].values //eg: /my/template
  
  def getModelName(jsonJValueFromFile: JValue) = jsonJValueFromFile.asInstanceOf[JObject].obj.map(_.name).filter(_!="request_url").head
  
  def getModelFieldsNames(modelFieldsJValue: JValue)= modelFieldsJValue.asInstanceOf[JObject].obj.map(_.name) 
  
  def getModelFieldsTypes(modelFieldsNames: List[String], modelFieldsJValue: JValue)= modelFieldsNames
    .map(key => modelFieldsJValue.findField{case JField(n, v) => n == key})
    .map(_.get.value.getClass.getSimpleName.replaceFirst("J",""))
  
  def getModelFieldDefaultValues(modelFieldsNames: List[String], modelFieldsJValue: JValue)= modelFieldsNames
    .map(key => modelFieldsJValue.findField{case JField(n, v) => n == key})
    .map(_.get.value.values)
  
  //List(author, pages, points)
  val modelFieldsNames: List[String] = getModelFieldsNames(modelFieldsJValue)

  //List(String, Int, Double)
  val modelFieldTypes: List[String] = getModelFieldsTypes(modelFieldsNames, modelFieldsJValue)
  
  //List(Chinua Achebe, 209, 1.3)
  val modelFieldDefaultValues: List[Any] = getModelFieldDefaultValues(modelFieldsNames, modelFieldsJValue)
  
  //List(author: String = `Chinua Achebe`, tutor: String = `1123123 1312`, pages: Int = 209, points: Double = 1.3)
  val modelCaseClassParams: List[Term.Param] = { 
    val fieldNames = for{
      i <- 0 until modelFieldsNames.size
      modelFieldName = Term.Name(modelFieldsNames(i).toLowerCase)
      modelFieldType = Type.Name(modelFieldTypes(i))
      modelFieldDefaultValue =  modelFieldDefaultValues(i) match {
        case inputDefaultValue: String if(! inputDefaultValue.contains(" "))  => Term.Name(s"`$inputDefaultValue`")
        case inputDefaultValue => Term.Name(s"$inputDefaultValue")
      }
    } yield 
      Term.Param(Nil, modelFieldName, Some(modelFieldType), Some(modelFieldDefaultValue))
    fieldNames.toList
  }
  
  def main(args: Array[String]): Unit = {
    // List(def author: String, 
    // def tutor: String, 
    // def pages: Int, 
    // def points: Double, 
    // def templateId: String)
    val modelTraitMethods: List[Decl.Def] =
    {
      val fieldNames = for
        {
        i <- 0 until modelFieldsNames.size
        methodName = Term.Name(modelFieldsNames(i).toLowerCase)
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
        i <- 0 until modelFieldsNames.size
        fieldNameString = modelFieldsNames(i)
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
        i <- 0 until modelFieldsNames.size
        fieldName = modelFieldsNames(i)
      } 
        yield 
          Term.Name(s".m${fieldName.capitalize}(createTemplateJson.${fieldName})")
      fieldNames.toList.mkString("")
    }

    val createModelJsonMethodBody: Term.Apply = q"""MappedTemplate.create.saveMe()""".copy(fun = Term.Name(s"$modelMappedName.create.mTemplateId(UUID.randomUUID().toString)$createModelJsonMethodFields.saveMe"))
    
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
    
    $errorMessageVal;
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
  
    val builderAPIMethodsFile = new File("src/main/scala/code/api/builder/APIMethods_APIBuilder.scala")
    builderAPIMethodsFile.getParentFile.mkdirs()
    Files.write(
      builderAPIMethodsFile.toPath,
      apiSource.syntax
        //TODO,maybe fix later ! in scalameta, Term.Param(Nil, modelFieldName, Some(modelFieldType), Some(modelFieldDefaultValue)) => the default value should be a string in API code.
        .replaceAll("""`""","")
        .replaceAll("trait Template \\{ _ =>","trait Template \\{ `_` =>")
        .replaceAll("""  ::  """,""""  ::  """")
        .getBytes("UTF-8")
    )
    
    /*
    * ##################################################################################################
    * ######################################Json_Factory###################################################
    * ##################################################################################################
    * */
    //List(templateId:String = "11231231312" ,author: String = `Chinua Achebe`, tutor: String = `11231231312`, pages: Int = 209, points: Double = 1.3)
    //Added the templatedId to `modelCaseClassParams`
    val templateIdField: Term.Param = Term.Param(Nil, Term.Name(s"${modelNameLowerCase}_id"), Some(Type.Name("String")), Some(Term.Name("`11231231312`")))
    val templateJsonClassParams: List[Term.Param] = List(templateIdField)++ modelCaseClassParams
    
    //case class TemplateJson(templateId: String = """1123123 1312""", author: String = """Chinua Achebe""", tutor: String = """1123123 1312""", pages: Int = 209, points: Double = 1.3)
    val TemplateJsonClass: Defn.Class = q"""case class TemplateJson(..$templateJsonClassParams) """
    
    //case class Template(author: String = `Chinua Achebe`, pages: Int = 209, points: Double = 1.3)
    //Note: No `templateId` in this class, the bank no need provide it, obp create a uuid for it.
    val createTemplateJsonClass: Defn.Class = q"""case class CreateTemplateJson(..$modelCaseClassParams) """
    
    //List(template.templateId, template.author, template.tutor, template.pages, template.points)
    val createTemplateJsonArgs: List[Term.Name] = {
      val fieldNames = for{
        i <- 0 until modelFieldsNames.size
      } 
        yield 
          Term.Name("template." + modelFieldsNames(i))
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