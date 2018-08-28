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
    
    val getApiUrl: String = (getMultipleApiJValue \ "request_url").asInstanceOf[JString].values //eg: /my/book
    val getSingleApiUrl: String = (getSingleApiJValue \ "request_url").asInstanceOf[JString].values //eg: /my/book
    val createSingleApiUrl: String = (createSingleApiJValue \ "request_url").asInstanceOf[JString].values //eg: /my/book
    val deleteSingleApiUrl: String = (deleteSingleApiJValue \ "request_url").asInstanceOf[JString].values //eg: /my/book
    val getApiUrlFromJsonFile: String = "/file"+getApiUrl //eg: /my/book
    
    val getApiResponseBody: JValue= getMultipleApiJValue \\ "success_response_body"
    
    val getApiAuthenticationStatement: Term.ApplyInfix = getApiAuthentication match {
      case true => q"cc.user ?~ UserNotLoggedIn"
      case false => q"Full(1) ?~ UserNotLoggedIn" //This will not throw error, only a placeholder 
    }
    
    val getSingleApiAuthenticationStatement: Term.ApplyInfix = getSingleApiAuthentication match {
      case true => q"cc.user ?~ UserNotLoggedIn"
      case false => q"Full(1) ?~ UserNotLoggedIn" //This will not throw error, only a placeholder 
    }
    
    val getApiUrlVal = q""" "/books" """.copy(s"$getApiUrl")
    val getSingleApiUrlVal = q""" "/books" """.copy(s"$getSingleApiUrl")
    val createSingleApiUrlVal = q""" "/books" """.copy(s"$createSingleApiUrl")
    val deleteSingleApiUrlVal = q""" "/books" """.copy(s"$deleteSingleApiUrl")
    val getApiUrlFromJsonFileVal = q""" "/books" """.copy(s"$getApiUrlFromJsonFile")
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
    
    val getBookFromJsonFileResourceCode: Term.ApplyInfix = 
      q"""
        resourceDocs += ResourceDoc(
          getBooksFromJsonFile, 
          apiVersion, 
          "getBooksFromJsonFile", 
          "GET", 
          $getApiUrlFromJsonFileVal, 
          $getApiSummaryFromJsonFileVal, 
          $getApiDescriptionFromJsonFileVal, 
          emptyObjectJson, 
          rootInterface, 
          List(UnknownError), 
          Catalogs(notCore, notPSD2, notOBWG), 
          apiTagApiBuilder :: Nil
        )"""
    
    val getBooksResourceCode: Term.ApplyInfix = 
      q"""
        resourceDocs += ResourceDoc(
          getBooks,
          apiVersion,
          "getBooks",
          "GET",
          $getApiUrlVal,        
          $getApiSummaryVal,       
          $getApiDescriptionVal,
          emptyObjectJson,
          rootInterface,
          List(UnknownError),
          Catalogs(notCore, notPSD2, notOBWG),
          apiTagApiBuilder :: Nil
        )  
        """
    
    val getBookResourceCode: Term.ApplyInfix = 
    q"""
      resourceDocs += ResourceDoc(
        getBook, 
        apiVersion, 
        "getBook", 
        "GET",
        $getSingleApiUrlVal,
        $getSingleApiSummaryVal,
        $getSingleApiDescriptionVal,
        emptyObjectJson, 
        createBookJson,
        List(UnknownError),
        Catalogs(notCore, notPSD2, notOBWG), 
        apiTagApiBuilder :: Nil
      )
    """
    
    val createBookResourceCode: Term.ApplyInfix = 
    q"""
       resourceDocs += ResourceDoc(
         createBook, 
         apiVersion, 
         "createBook", 
         "POST",
         $createSingleApiUrlVal,
         $createSingleApiSummaryVal,
         $createSingleApiDescriptionVal,
         createBookJson, 
         createBookJson,
         List(UnknownError),
         Catalogs(notCore, notPSD2, notOBWG), 
         apiTagApiBuilder :: Nil
       )
    """
    
    val deleteBookResourceCode: Term.ApplyInfix = 
    q"""
     resourceDocs += ResourceDoc(
       deleteBook, 
       apiVersion, 
       "deleteBook", 
       "DELETE",
       $deleteSingleApiUrlVal,
       $createSingleApiSummaryVal,
       $deleteSingleApiDescriptionVal,
       emptyObjectJson, 
       emptyObjectJson,
       List(UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       apiTagApiBuilder :: Nil
     )
    """
    
    //TODO, escape issue:return the space, I added quotes in the end: allSourceCode.syntax.replaceAll("""  ::  """,""""  ::  """")
    //from "/my/book" --> "my  ::  book" 
    val getApiUrlLiftFormat = getApiUrl.replaceFirst("/", "").split("/").mkString("""""","""  ::  ""","""""")
    val getSingleApiUrlLiftFormat = getSingleApiUrl.replaceFirst("/", "").split("/").dropRight(1).mkString("""""","""  ::  ""","""""")
    val getApiUrlLiftweb: Lit.String = q""" "books"  """.copy(getApiUrlLiftFormat)
    val getSingleApiUrlLiftweb: Lit.String = q""" "books"  """.copy(getSingleApiUrlLiftFormat)
    
    
    val getBookFromJsonPartialFunction: Defn.Val = q"""
      lazy val getBooksFromJsonFile: OBPEndpoint = {
        case ("file" :: $getApiUrlLiftweb :: Nil) JsonGet req =>
          cc => {
            for {
              u <- $getApiAuthenticationStatement
              jsonStringFromFile = scala.io.Source.fromFile("src/main/scala/code/api/APIBuilder/apisResource.json").mkString 
              jsonJValueFromFile = json.parse(jsonStringFromFile)
              resourceDocsJObject= jsonJValueFromFile.\("resource_docs").children.asInstanceOf[List[JObject]]
              getMethodJValue = resourceDocsJObject.filter(_.\("request_verb") == JString("GET")).head
              jsonObject = getMethodJValue \\ "success_response_body"
            } yield {
              successJsonResponse(jsonObject)
            }
          }
      }"""
    val getBooksPartialFunction: Defn.Val = q"""
      lazy val getBooks: OBPEndpoint ={
        case ($getApiUrlLiftweb:: Nil) JsonGet req =>
          cc =>
          {
            for{
              u <- $getApiAuthenticationStatement 
              books <-  APIBUilder_Connector.getBooks
              booksJson = JsonFactory_APIBuilder.createBooks(books)
              jsonObject:JValue = decompose(booksJson)
            }yield{
                successJsonResponse(jsonObject)
            }
          }
      }"""
    
    val getBookPartialFunction: Defn.Val = q"""
      lazy val getBook: OBPEndpoint ={
        case ($getApiUrlLiftweb :: bookId :: Nil) JsonGet _ => {
          cc =>
          {
            for{
              u <- $getSingleApiAuthenticationStatement
              book <- APIBUilder_Connector.getBookById(bookId) ?~! BookNotFound
              bookJson = JsonFactory_APIBuilder.createBook(book)
              jsonObject:JValue = decompose(bookJson)
            }yield{
              successJsonResponse(jsonObject)
            }
          }
        }
      }"""
    
    val createBookPartialFunction: Defn.Val = q"""
      lazy val createBook: OBPEndpoint ={
        case "books" :: Nil JsonPost json -> _ => {
          cc =>
          {
            for{
              jsonBody <- tryo(json.extract[CreateBookJson]) ?~! InvalidJsonFormat
              u <- cc.user ?~ UserNotLoggedIn
              book <-  APIBUilder_Connector.createBook(jsonBody.author,jsonBody.pages, jsonBody.points)
              bookJson = JsonFactory_APIBuilder.createBook(book)
              jsonObject:JValue = decompose(bookJson)
            }yield{
              successJsonResponse(jsonObject)
            }
          }
        }
      }
      """
    
    val deleteBookPartialFunction: Defn.Val = q"""
      lazy val deleteBook: OBPEndpoint ={
        case "books" :: bookId :: Nil JsonDelete _ => {
          cc =>
          {
            for{
              u <- cc.user ?~ UserNotLoggedIn
              deleted <- APIBUilder_Connector.deleteBook(bookId)
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
package code.api.APIBuilder

import java.util.UUID
import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import net.liftweb.json
import net.liftweb.json._
import net.liftweb.http.rest.RestHelper
import net.liftweb.common.Full
import net.liftweb.util.Helpers.tryo
import net.liftweb.json.Extraction._
import code.api.util.ApiVersion
import code.api.util.ErrorMessages._
import code.api.util.APIUtil._
import code.api.APIBuilder.JsonFactory_APIBuilder._
import net.liftweb.mapper.By

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
    val BookNotFound = "OBP-31001: Book not found. Please specify a valid value for BOOK_ID."
    
    def endpointsOfBuilderAPI = getBooksFromJsonFile :: getBook :: createBook :: getBooks :: deleteBook :: Nil
    
    $getBookFromJsonFileResourceCode
    $getBookFromJsonPartialFunction
 
    $getBooksResourceCode
    $getBooksPartialFunction
    
    $getBookResourceCode                           
    $getBookPartialFunction
    
    $createBookResourceCode                           
    $createBookPartialFunction
    
    $deleteBookResourceCode                           
    $deleteBookPartialFunction
  }
}

object APIBUilder_Connector
{
  def createBook(
    author: String, 
    pages: Int, 
    points: Double
  ) =
    Full(
      MappedBook.create
        .mBookId(UUID.randomUUID().toString)
        .mAuthor(author)
        .mPages(pages)
        .mPoints(points)
        .saveMe()
    )
  
  def getBooks()= Full(MappedBook.findAll())
  
  def getBookById(bookId: String)= MappedBook.find(By(MappedBook.mBookId, bookId))
  
  def deleteBook(bookId: String)= MappedBook.find(By(MappedBook.mBookId, bookId)).map(_.delete_!)
  
}

import net.liftweb.mapper._

class MappedBook extends Book with LongKeyedMapper[MappedBook] with IdPK {
  def getSingleton = MappedBook

  object mBookId extends MappedString(this,100)
  object mAuthor extends MappedString(this,100)
  object mPages extends MappedInt(this)
  object mPoints extends MappedDouble(this)

  override def bookId: String = mBookId.get
  override def author: String = mAuthor.get
  override def pages: Int = mPages.get
  override def points: Double = mPoints.get
}

object MappedBook extends MappedBook with LongKeyedMetaMapper[MappedBook] {}
 
trait Book {
  def bookId : String
  def author : String
  def pages : Int
  def points : Double
}
"""
  
    val builderAPIMethodsFile = new File("src/main/scala/code/api/APIBuilder/APIMethods_APIBuilder.scala")
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
    
    val jsonFieldname = getApiResponseBody.children.head.asInstanceOf[JsonAST.JObject].obj.head.name.toLowerCase.capitalize
    
    val jsonFieldValue =s"List[$jsonFieldname]" // List[Books]
    val jsonFieldDefaultValue = s"List($jsonFieldname())" //List(Books())
    
    
    val secondLevelFiledNames: List[String] = getApiResponseBody.children.head.asInstanceOf[JsonAST.JObject].obj.head.value.asInstanceOf[JsonAST.JArray].children.head.asInstanceOf[JsonAST.JObject].obj.map(_.name)
    val secondLevelFiledTypes: List[String] = secondLevelFiledNames.map(key => getApiResponseBody.findField{
           case JField(n, v) => n == key
         }).map(_.get.value.getClass.getSimpleName.replaceFirst("J","")).toList
    
    
    
    val secondLevelFiledTypes2: List[Any] = secondLevelFiledNames.map(key => getApiResponseBody.findField{
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

package code.api.APIBuilder
import code.api.util.APIUtil

$SecondLevelCaseClass
$FirstLevelCaseClass
case class CreateBookJson( 
  author: String = "Chinua Achebe",
  pages: Int = 209,
  points: Double = 1.3
)

case class BookJson( 
  book_id: String = "123123213",
  author: String = "Chinua Achebe",
  pages: Int = 209,
  points: Double = 1.3
)

object JsonFactory_APIBuilder{
              
  val books = Books()
  val rootInterface = RootInterface(List(books))
  val createBookJson = CreateBookJson()
  
  def createBook(book: Book) = BookJson(book.bookId,book.author,book.pages,book.points)
  def createBooks(books: List[Book])= books.map(book => BookJson(book.bookId,book.author,book.pages,book.points))
    
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
    
    println("Congratulations! You make the new APIs. Please restart OBP-API server!")
  }
}