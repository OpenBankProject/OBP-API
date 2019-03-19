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

import code.util.Helper.MdcLoggable
import org.scalatest.{FlatSpec, Matchers}

import scala.meta.{Defn, Term, Type}
import APIBuilderModel.{modelMappedName, _}
import net.liftweb.json
import net.liftweb.json.JValue

class APIBuilderModelTest extends FlatSpec with Matchers with MdcLoggable {
  
  val jsonStringFromFile: String =
    """{
         "request_url": "/templates",
         "template": {
           "author": "Chinua Achebe",
           "pages": 209,
           "points": 1.3
         }
       }"""
  val jsonJValueFromFile: JValue = json.parse(jsonStringFromFile)
  
  //"template"
  val modelName = getModelName(jsonJValueFromFile)
  
  //TEMPLATE
  val modelNameUpperCase = modelName.toUpperCase
  //template
  val modelNameLowerCase = modelName.toLowerCase
  //Template
  val modelNameCapitalized = modelNameLowerCase.capitalize
  //MappedTemplate_1
  val modelMappedName = s"Mapped${modelNameCapitalized}_1"
  val modelTypeName: Type.Name = Type.Name(modelMappedName)
  val modelTermName = Term.Name(modelMappedName)
  
  val modelFieldsJValue: JValue = jsonJValueFromFile \ modelName
  
  val modelFieldsNames: List[String] = List("author", "pages", "points")
  
  val modelFieldTypes: List[String] = List("String", "Int", "Double")
  
  val modelFieldDefaultValues: List[Any] = List("Chinua Achebe", 209, 1.3)
  
  "getApiUrl" should "work as expected" in {
    val apiUrl: String = APIBuilderModel.getApiUrl(jsonJValueFromFile)
    apiUrl should be ("/templates")
    
    val jvalueMissingSlash: JValue = json.parse("""{"request_url":"templates/my"}""")
    val apiUrl2: String = APIBuilderModel.getApiUrl(jvalueMissingSlash)
    apiUrl2 should be ("/templates/my")
    
    val jvalueWrongSlash: JValue = json.parse("""{"request_url":"templates/my/"}""")
    val apiUrl3: String = APIBuilderModel.getApiUrl(jvalueMissingSlash)
    apiUrl3 should be ("/templates/my")
  }
  
  "getModelName" should "work as expected" in {
    val apiUrl: String = APIBuilderModel.getModelName(jsonJValueFromFile)
    apiUrl should be ("template")
  }
  
  "getModelFieldsNames" should "work as expected" in {
    val modelFieldsNames: List[String] = APIBuilderModel.getModelFieldsNames(modelFieldsJValue)
    modelFieldsNames should be (List("author", "pages", "points"))
  }
  
  "getModelFieldsTypes" should "work as expected" in {
    val modelFieldsTypes: List[String] = APIBuilderModel.getModelFieldsTypes(modelFieldsNames, modelFieldsJValue)
    modelFieldsTypes should be (List("String", "Int", "Double"))
  }
  
  "getModelFieldDefaultValues" should "work as expected" in {
    val modelFieldsTypes: List[Any] = APIBuilderModel.getModelFieldDefaultValues(modelFieldsNames, modelFieldsJValue)
    modelFieldsTypes should be (List("Chinua Achebe", 209, 1.3))
  }
  
  "getModelTraitMethods" should "work as expected" in {
    val modelTrait: Defn.Trait = APIBuilderModel.getModelTrait(modelFieldsNames, modelFieldTypes)
    modelTrait.toString() should be (
      "trait Template { `_` =>" +
        "\n  def author: String" +
        "\n  def pages: Int" +
        "\n  def points: Double" +
        "\n  def templateId: String" +
        "\n" +
        "}")
  }
  
  "getModelCaseClassParams" should "work as expected" in {
    val modelCaseClassParams: List[Term.Param] = APIBuilderModel.getModelCaseClassParams(modelFieldsNames, modelFieldTypes, modelFieldDefaultValues)
    modelCaseClassParams.toString() should be (
      "List(" +
        "author: String = `Chinua Achebe`, " +
        "pages: Int = 209, " +
        "points: Double = 1.3)")
  }
  
  "changeStringToMappedObject" should "work as expected" in {
    val stringObjectName = "Author"
    val stringObjectType = "String"
    val stringMappedObject= APIBuilderModel.stringToMappedObject(stringObjectName, stringObjectType)
    stringMappedObject.toString() should be ("object Author extends MappedString(this, 100)")
    
    val intObjectName = 123
    val intObjectType = "Int"
    val intMappedObject= APIBuilderModel.stringToMappedObject(stringObjectName, intObjectType)
    intMappedObject.toString() should be ("object Author extends MappedInt(this)")
    
    val doubleObjectName = 123.1231
    val doubleObjectType = "Double"
    val doubleMappedObject= APIBuilderModel.stringToMappedObject(stringObjectName, doubleObjectType)
    doubleMappedObject.toString() should be ("object Author extends MappedDouble(this)")
  }
  
  "stringToMappedMethod" should "work as expected" in {
    val methodName = "author"
    val methodReturnType = "String"
    val mappedMethod= APIBuilderModel.stringToMappedMethod(methodName, methodReturnType)
    mappedMethod.toString() should be ("override def author: String = mAuthor.get")
  }
  
  "getModelClassStatements" should "work as expected" in {
    val modelClassStatements= APIBuilderModel.getModelClassStatements(modelFieldsNames, modelFieldTypes)
    modelClassStatements.toString() should be (
      "List(" +
        "object mAuthor extends MappedString(this, 100), " +
        "override def author: String = mAuthor.get, " +
        "object mPages extends MappedInt(this), " +
        "override def pages: Int = mPages.get, " +
        "object mPoints extends MappedDouble(this), " +
        "override def points: Double = mPoints.get)" +
        "")
  }
  
  
  "getModelClass" should "work as expected" in {
    val modelClass= APIBuilderModel.getModelClass(modelTypeName, modelTermName, modelFieldsNames, modelFieldTypes)
    modelClass.toString() should be (
      "class MappedTemplate_1 extends Template with LongKeyedMapper[MappedTemplate_1] with IdPK {" +
        "\n  object mAuthor extends MappedString(this, 100)" +
        "\n  override def author: String = mAuthor.get" +
        "\n  object mPages extends MappedInt(this)" +
        "\n  override def pages: Int = mPages.get" +
        "\n  object mPoints extends MappedDouble(this)" +
        "\n  override def points: Double = mPoints.get" +
        "\n  def getSingleton = MappedTemplate_1" +
        "\n  object mTemplateId extends MappedString(this, 100)" +
        "\n  override def templateId: String = mTemplateId.get" +
        "\n" +
        "}")
  }
  
  "generateCreateModelJsonMethod" should "work as expected" in {
    val createModelJsonMethod= APIBuilderModel.generateCreateModelJsonMethod(modelFieldsNames, modelMappedName)
    createModelJsonMethod.toString() should be (
      "def createTemplate(createTemplateJson: CreateTemplateJson) = " +
        "Full(MappedTemplate_1.create" +
        ".mTemplateId(UUID.randomUUID().toString)" +
        ".mAuthor(createTemplateJson.author)" +
        ".mPages(createTemplateJson.pages)" +
        ".mPoints(createTemplateJson.points)" +
        ".saveMe())")
  }
  
  "generateCreateTemplateJsonApply" should "work as expected" in {
    val createTemplateJsonApply= APIBuilderModel.generateCreateTemplateJsonApply(modelFieldsNames)
    createTemplateJsonApply.toString() should be ("TemplateJson(template.templateId, template.author, template.pages, template.points)") 
  }
  
  "getAuthenticationStatement" should "work as expected" in {
    val needAuth= APIBuilderModel.getAuthenticationStatement(true)
    needAuth.toString() should be ("cc.user ?~ UserNotLoggedIn") 
    val notNeedAuth= APIBuilderModel.getAuthenticationStatement(false)
    notNeedAuth.toString() should be ("Full(1) ?~ UserNotLoggedIn") 
  }
  
  "createTemplateJsonClass" should "work as expected" in {
    val className ="Template"
    val templateIdField: Term.Param = Term.Param(Nil, Term.Name(s"template_id"), Some(Type.Name("String")), Some(Term.Name("`11231231312`")))
    val templateJsonClassParams = List(templateIdField)
    
    val templateJsonClass: Defn.Class = APIBuilderModel.createTemplateJsonClass(className, templateJsonClassParams)
    templateJsonClass.toString() should be ("case class Template(template_id: String = `11231231312`)")
  }
}