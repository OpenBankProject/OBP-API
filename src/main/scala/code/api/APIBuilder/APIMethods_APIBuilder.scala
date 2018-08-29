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
    val BookNotFound = "OBP-31001: Book not found. Please specify a valid value for BOOK_ID."
    
    implicit val formats = net.liftweb.json.DefaultFormats
    
    def endpointsOfBuilderAPI = getBooksFromJsonFile :: getBook :: createBook :: getBooks :: deleteBook :: Nil
    
    resourceDocs += ResourceDoc(
      getBooksFromJsonFile,
      apiVersion,
      "getBooksFromJsonFile",
      "GET",
      "/my/good/books",
      "Get Books From Json File",
      "Return All my books in Json ,Authentication is Mandatory",
      emptyObjectJson,
      rootInterface,
      List(UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      apiTagApiBuilder :: Nil
    )
    lazy val getBooksFromJsonFile: OBPEndpoint ={
      case ("my" :: "good" :: "books" :: Nil) JsonGet req =>
        cc =>
        {
          for{
            u <- cc.user ?~ UserNotLoggedIn;
            jsonString = scala.io.Source.fromFile("src/main/scala/code/api/APIBuilder/newAPi-GET.json").mkString;
            jsonObject: JValue = json.parse(jsonString) \\ "success_response_body"
          }yield{
              successJsonResponse(jsonObject)
          }
        }
    }

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
            books <-  APIBuilder_Connector.getBooks
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
            book <- APIBuilder_Connector.getBookById(bookId) ?~! BookNotFound
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
            book <-  APIBuilder_Connector.createBook(jsonBody.author,jsonBody.pages, jsonBody.points)
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
            deleted <- APIBuilder_Connector.deleteBook(bookId)
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


object APIBuilder_Connector
{
  val allAPIBuilderModels = List(MappedBook)
  
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

object MappedBook extends MappedBook with LongKeyedMetaMapper[MappedBook] {
}
 
trait Book {
  def bookId : String
  def author : String
  def pages : Int
  def points : Double
}