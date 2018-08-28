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
import net.liftweb.json.JsonAST.JField
import net.liftweb.json.{JValue, JsonAST}

object APIBuilder
{
  def main(args: Array[String]): Unit = {
    val jsonString = scala.io.Source.fromFile("src/main/scala/code/api/APIBuilder/newAPi-GET.json").mkString 
    
    val jsonObject: JValue = json.parse(jsonString)
    val newApiSummary: String = (jsonObject \\ "summary").values.head._2.toString
    val newApiDescription: String = (jsonObject \\ "description").values.head._2.toString 
    //TODO, for now this is only in description, could be a single filed later.
    val needAuthentication:Boolean = newApiDescription.contains("Authentication is Mandatory")
    val newApiURl: String = (jsonObject \\ "request_url").values.head._2.toString //eg: /my/book
    val newApiResponseBody: JValue= jsonObject \\ "success_response_body"
    
    
    val needAuthenticationStatement: Term.ApplyInfix = needAuthentication match {
      case true => q"cc.user ?~ UserNotLoggedIn"
      case false => q"Full(1) ?~ UserNotLoggedIn" //This will not throw error, only a placeholder 
    }
    
    
    val jsonFieldname = newApiResponseBody.children.head.asInstanceOf[JsonAST.JObject].obj.head.name.toLowerCase.capitalize
    
    val jsonFieldValue =s"List[$jsonFieldname]" // List[Books]
    val jsonFieldDefaultValue = s"List($jsonFieldname())" //List(Books())
    
    
    val secondLevelFiledNames: List[String] = newApiResponseBody.children.head.asInstanceOf[JsonAST.JObject].obj.head.value.asInstanceOf[JsonAST.JArray].children.head.asInstanceOf[JsonAST.JObject].obj.map(_.name)
    val secondLevelFiledTypes: List[String] = secondLevelFiledNames.map(key => newApiResponseBody.findField{
           case JField(n, v) => n == key
         }).map(_.get.value.getClass.getSimpleName.replaceFirst("J","")).toList
    
    
    
    val secondLevelFiledTypes2: List[Any] = secondLevelFiledNames.map(key => newApiResponseBody.findField{
           case JField(n, v) => n == key
         }).map(_.get.value.values).toList
    
    val SecondLevelCaseFieldNames: List[Term.Param] = { 
      val fieldNames = for{
      a <- 0 until secondLevelFiledNames.size
        } yield Term.Param(Nil, Term.Name(secondLevelFiledNames(a).toLowerCase), Some(Type.Name(secondLevelFiledTypes(a))), Some(Term.Name(s"${secondLevelFiledTypes2(a)}")))
      fieldNames.toList
    } 
    
    
    val RootFiledName = Type.Name("RootInterface")
    val FirstLevelCaseClassFiledName = List(Term.Param(Nil, Term.Name(jsonFieldname.toLowerCase), Some(Type.Name(jsonFieldValue)), Some(Term.Name(jsonFieldDefaultValue))))
    val SecondLevelCaseClassName = Type.Name(jsonFieldname)
    
    val SecondLevelCaseClass: Defn.Class = q"""case class $SecondLevelCaseClassName(..$SecondLevelCaseFieldNames) """
    val FirstLevelCaseClass: Defn.Class = q"""case class $RootFiledName(..$FirstLevelCaseClassFiledName) """ //case class Test(banks: List[Banks])
    
    val instanceRootCaseClass: Defn.Val = q"val rootInterface = RootInterface()"
    val getForComprehensionBody: Term.ForYield = 
      q"""for {
        u <- $needAuthenticationStatement 
        jsonString = scala.io.Source.fromFile("src/main/scala/code/api/APIBuilder/newAPi-GET.json").mkString 
        jsonObject: JValue = json.parse(jsonString)\\"success_response_body"
      } yield {
        successJsonResponse(jsonObject)
      }"""
    
    
    val newUrlForResourceDoc = q""" "/books" """.copy(s"$newApiURl")
    val newUrlDescriptionForResourceDoc = q""" "" """.copy(s"$newApiDescription")
    val newUrlSummaryForResourceDoc = q""" "" """.copy(s"$newApiSummary")
    
    //val termExample: Term.Apply = q""" Test() """.copy(s"${RootFiledName.value}".parse[Term].get)
    
    val fixedResourceCode: Term.ApplyInfix = 
      q"""
        resourceDocs += ResourceDoc(
          getBooks, 
          apiVersion, 
          "getBooks", 
          "GET", 
          $newUrlForResourceDoc, 
          $newUrlSummaryForResourceDoc, 
          $newUrlDescriptionForResourceDoc, 
          emptyObjectJson, 
          rootInterface, 
          List(UnknownError), 
          Catalogs(notCore, notPSD2, notOBWG), 
          apiTagBank :: Nil
        )"""
    
    
    
    
    //TODO, escape issue:return the space, I added quotes in the end: allSourceCode.syntax.replaceAll("""  ::  """,""""  ::  """")
    //from "/my/book" --> "my  ::  book" 
    val newApiUrlLiftFormat = newApiURl.replaceFirst("/","").split("/").mkString("""""","""  ::  """, """""")
    val newURL: Lit.String = q""" "books"  """.copy(newApiUrlLiftFormat)
    val addedPartialFunction: Defn.Val = 
      q"""
        lazy val getBooks: OBPEndpoint = {
          case ($newURL :: Nil) JsonGet req =>
            cc => {
              $getForComprehensionBody
            }
         }"""
    
    val addedEndpoitList = 
      q"""
         def endpointsOfBuilderAPI = getBooks::Nil"""
    
    val apiSource: Source = 
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
        package code.api.APIBuilder
        
        import code.api.util.ApiVersion
        import code.api.util.ErrorMessages._
        import net.liftweb.http.rest.RestHelper
        import scala.collection.immutable.Nil
        import scala.collection.mutable.ArrayBuffer
        import code.api.util.APIUtil._
        import net.liftweb.json
        import net.liftweb.json.JValue
        import code.api.APIBuilder.JsonFactory_APIBuilder._
        import net.liftweb.common.Full
        
        trait APIMethods_APIBuilder
        {
          self: RestHelper =>
          
          val ImplementationsBuilderAPI = new Object()
          {
            val apiVersion: ApiVersion = ApiVersion.apiBuilder
            val resourceDocs = ArrayBuffer[ResourceDoc]()
            val apiRelations = ArrayBuffer[ApiRelation]()
            val codeContext = CodeContext(resourceDocs, apiRelations)
            $addedEndpoitList
            $fixedResourceCode
            $addedPartialFunction
          }
        }
    """
  
    val builderAPIMethodsFile = new File("src/main/scala/code/api/APIBuilder/APIMethods_APIBuilder.scala")
    builderAPIMethodsFile.getParentFile.mkdirs()
    Files.write(
      builderAPIMethodsFile.toPath,
      apiSource.syntax.replaceAll("""  ::  """,""""  ::  """").getBytes("UTF-8")
  )
    
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
        package code.api.APIBuilder
        import code.api.util.APIUtil
        
        $SecondLevelCaseClass
        $FirstLevelCaseClass
      
        object JsonFactory_APIBuilder{
              
          $instanceRootCaseClass
          
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
    val builderJsonFactoryFile = new File("src/main/scala/code/api/APIBuilder/JsonFactory_APIBuilder.scala")
    builderJsonFactoryFile.getParentFile.mkdirs()
    Files.write(
      builderJsonFactoryFile.toPath,
      jsonFactorySource.syntax.replaceAll("""`""",""""""""").getBytes("UTF-8")
    )
    
    println("Congratulations! You make the new API")
  }
}