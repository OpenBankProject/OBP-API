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

import scala.meta.{Decl, Defn, Term, Type}
import APIBuilderModel._

class APIBuilderModelTest extends FlatSpec with Matchers with MdcLoggable {
  
  "getApiUrl" should "work as expected" in {
    val apiUrl: String = APIBuilderModel.getApiUrl(jsonJValueFromFile)
    apiUrl should be ("/books")
  }
  
  "getModelName" should "work as expected" in {
    val apiUrl: String = APIBuilderModel.getModelName(jsonJValueFromFile)
    apiUrl should be ("book")
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
    val modelTraitMethods: List[Decl.Def] = APIBuilderModel.getModelTraitMethods(modelFieldsNames, modelFieldTypes)
    modelTraitMethods.toString() should be ("List(def author: String, def pages: Int, def points: Double, def templateId: String)")
  }
  
  "getModelCaseClassParams" should "work as expected" in {
    val modelCaseClassParams: List[Term.Param] = APIBuilderModel.getModelCaseClassParams(modelFieldsNames, modelFieldTypes, modelFieldDefaultValues)
    modelCaseClassParams.toString() should be ("List(author: String = `Chinua Achebe`, pages: Int = 209, points: Double = 1.3)")
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
    modelClassStatements.toString() should be ("List(object mAuthor extends MappedString(this, 100), override def author: String = mAuthor.get, object mPages extends MappedInt(this), override def pages: Int = mPages.get, object mPoints extends MappedDouble(this), override def points: Double = mPoints.get)")
  }
  
  "generateCreateModelJsonMethod" should "work as expected" in {
    val createModelJsonMethod= APIBuilderModel.generateCreateModelJsonMethod(modelFieldsNames, modelMappedName)
    createModelJsonMethod.toString() contains ("def createTemplate(createTemplateJson: CreateTemplateJson) = Full(MappedBook_") should be (true)
    createModelJsonMethod.toString() contains (".create.mTemplateId(UUID.randomUUID().toString).mAuthor(createTemplateJson.author).mPages(createTemplateJson.pages).mPoints(createTemplateJson.points).saveMe())") should be (true)
  }
  
  "generateCreateTemplateJsonApply" should "work as expected" in {
    val createTemplateJsonApply= APIBuilderModel.generateCreateTemplateJsonApply(modelFieldsNames)
    createTemplateJsonApply.toString() should be ("TemplateJson(template.templateId, template.author, template.pages, template.points)") 
  }
  
  "createTemplateJsonClass" should "work as expected" in {
    val className ="Book"
    val templateIdField: Term.Param = Term.Param(Nil, Term.Name(s"book_id"), Some(Type.Name("String")), Some(Term.Name("`11231231312`")))
    val templateJsonClassParams = List(templateIdField)
    
    val templateJsonClass: Defn.Class = APIBuilderModel.createTemplateJsonClass(className, templateJsonClassParams)
    templateJsonClass.toString() should be ("case class Book(book_id: String = `11231231312`)")
  }
}