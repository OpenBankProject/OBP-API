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
    val newUrlForResourceDoc = q""" "/books" """.copy(s"$newApiURl")
    val newUrlDescriptionForResourceDoc = q""" "" """.copy(s"$newApiDescription")
    val newUrlSummaryForResourceDoc = q""" "" """.copy(s"$newApiSummary")
    
    val getBookFromJsonFileResourceCode: Term.ApplyInfix = 
      q"""
        resourceDocs += ResourceDoc(
          getBooksFromJsonFile, 
          apiVersion, 
          "getBooksFromJsonFile", 
          "GET", 
          $newUrlForResourceDoc, 
          $newUrlSummaryForResourceDoc, 
          $newUrlDescriptionForResourceDoc, 
          emptyObjectJson, 
          rootInterface, 
          List(UnknownError), 
          Catalogs(notCore, notPSD2, notOBWG), 
          apiTagApiBuilder :: Nil
        )"""
    
    
    //TODO, escape issue:return the space, I added quotes in the end: allSourceCode.syntax.replaceAll("""  ::  """,""""  ::  """")
    //from "/my/book" --> "my  ::  book" 
    val newApiUrlLiftFormat = newApiURl.replaceFirst("/","").split("/").mkString("""""","""  ::  """, """""")
    val newURL: Lit.String = q""" "books"  """.copy(newApiUrlLiftFormat)
    val getBookFromJsonPartialFunction: Defn.Val = 
      q"""
        lazy val getBooksFromJsonFile: OBPEndpoint = {
          case ($newURL :: Nil) JsonGet req =>
            cc => {
              for {
                u <- $needAuthenticationStatement 
                jsonString = scala.io.Source.fromFile("src/main/scala/code/api/APIBuilder/newAPi-GET.json").mkString 
                jsonObject: JValue = json.parse(jsonString)\\"success_response_body"
              } yield {
                successJsonResponse(jsonObject)
              }
            }
         }"""
    
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
 
    resourceDocs += ResourceDoc(
      getBooks,
      apiVersion,
      "getBooks",
      "GET",
      "/books",
      "Get All Books.",
      "Return All my books, Authentication is Mandatory",
      emptyObjectJson,
      rootInterface,
      List(UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      apiTagApiBuilder :: Nil
    )
    lazy val getBooks: OBPEndpoint ={
      case ("books" :: Nil) JsonGet req =>
        cc =>
        {
          for{
            u <- cc.user ?~ UserNotLoggedIn
            books <-  APIBUilder_Connector.getBooks
            booksJson = JsonFactory_APIBuilder.createBooks(books)
            jsonObject:JValue = decompose(booksJson)
          }yield{
              successJsonResponse(jsonObject)
          }
        }
    }
    
    resourceDocs += ResourceDoc(
      getBook, 
      apiVersion, 
      "getBook", 
      "GET",
      "/books/BOOK_ID",
      "Get Book ",
      "Get a book by Id, Authentication is Mandatory",
      createBookJson, 
      rootInterface,
      List(UnknownError),
      Catalogs(notCore, notPSD2, notOBWG), 
      apiTagApiBuilder :: Nil
    )
    lazy val getBook: OBPEndpoint ={
      case "books" :: bookId :: Nil JsonGet _ => {
        cc =>
        {
          for{
            u <- cc.user ?~ UserNotLoggedIn
            book <- APIBUilder_Connector.getBookById(bookId) ?~! BookNotFound
            bookJson = JsonFactory_APIBuilder.createBook(book)
            jsonObject:JValue = decompose(bookJson)
          }yield{
            successJsonResponse(jsonObject)
          }
        }
      }
    }
    
    resourceDocs += ResourceDoc(
      createBook, 
      apiVersion, 
      "createBook", 
      "POST",
      "/books",
      "Create Book ",
      "Create one book, Authentication is Mandatory",
      createBookJson, 
      rootInterface,
      List(UnknownError),
      Catalogs(notCore, notPSD2, notOBWG), 
      apiTagApiBuilder :: Nil
    )
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
    
    resourceDocs += ResourceDoc(
      deleteBook, 
      apiVersion, 
      "deleteBook", 
      "DELETE",
      "/books/BOOK_ID",
      "Delete Book ",
      "Delete a book, Authentication is Mandatory",
      createBookJson, 
      rootInterface,
      List(UnknownError),
      Catalogs(notCore, notPSD2, notOBWG), 
      apiTagApiBuilder :: Nil
    )
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