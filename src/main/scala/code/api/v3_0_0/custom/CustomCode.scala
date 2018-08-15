package code.api.v3_0_0.custom

import java.io.File
import java.nio.file.Files

import scala.meta._
import net.liftweb.json
import net.liftweb.json.JValue

object CustomCode extends App
{
  val jsonString = scala.io.Source.fromFile("src/main/scala/code/api/v3_0_0/custom/newAPis.json").mkString 
  
  val jsonObject: JValue = json.parse(jsonString)
  val newApiSummary: String = (jsonObject \\ "summary").values.head._2.toString
  val newApiDescription: String = (jsonObject \\ "description").values.head._2.toString 
  val newApiURl: String = (jsonObject \\ "request_url").values.head._2.toString //eg: my/book
  val newApiResponseBody: JValue= jsonObject \\ "example_request_body"
  
  
  val getJsonBody: Term.ForYield = 
    q"""for (
      u <- cc.user ?~ UserNotLoggedIn; 
      jsonString = scala.io.Source.fromFile("src/main/scala/code/api/v3_0_0/custom/newAPis.json").mkString; 
      jsonObject: JValue = json.parse(jsonString)\\"example_request_body"
    ) yield {
      successJsonResponse(jsonObject)
    }"""
  
  
  val newUrlForResouceDoc = q""" "/books" """.copy(s"/custom/$newApiURl")
  val newUrlDescriptionForResouceDoc = q""" "" """.copy(s"$newApiDescription")
  val newUrlSummaryForResouceDoc = q""" "" """.copy(s"$newApiSummary")
  
  
  val fixedResouceCode: Term.ApplyInfix = 
    q"""
      resourceDocs += ResourceDoc(
        getBooks, 
        apiVersion, 
        "getBooks", 
        "GET", 
        $newUrlForResouceDoc, 
        $newUrlSummaryForResouceDoc, 
        $newUrlDescriptionForResouceDoc, 
        emptyObjectJson, 
        emptyObjectJson, 
        List(UnknownError), 
        Catalogs(Core, notPSD2, OBWG), 
        apiTagBank :: Nil
      )"""
  
  
  
  
  //TODO, escape issue:return the space, I added quotes in the end: allSourceCode.syntax.replaceAll("""  ::  """,""""  ::  """")
  //from "my/book" --> "my  ::  book" 
  val newApiUrlLiftFormat = newApiURl.split("/").mkString("""""","""  ::  """, """""")
  val newURL: Lit.String = q""" "books"  """.copy(newApiUrlLiftFormat)
  val addedPartialfunction: Defn.Val = 
    q"""
      lazy val getBooks: OBPEndpoint = {
        case ("custom" :: $newURL :: Nil) JsonGet req =>
          cc => {
            $getJsonBody
          }
       }"""
  
  val addedEndpoitList = 
    q"""
       def endpointsOfCustom3_0_0 = createTransactionRequestTransferToReferenceAccountCustom :: getBooks::Nil"""
  
  val allSourceCode: Source = 
    source""" 
        
      package code.api.v3_0_0.custom

      import code.api.util.ApiVersion
      import code.api.util.ErrorMessages._
      import net.liftweb.http.rest.RestHelper
      import scala.collection.immutable.Nil
      import scala.collection.mutable.ArrayBuffer
      import code.api.util.APIUtil._
      import net.liftweb.json
      import net.liftweb.json.JValue

      trait CustomAPIMethods300 { 
        self: RestHelper =>
          val ImplementationsCustom3_0_0 = new Object() {
          val apiVersion: ApiVersion = ApiVersion.v3_0_0
          val resourceDocs = ArrayBuffer[ResourceDoc]()
          val apiRelations = ArrayBuffer[ApiRelation]()
          val codeContext = CodeContext(resourceDocs, apiRelations)
          val createTransactionRequestTransferToReferenceAccountCustom = null
          $addedEndpoitList
          $fixedResouceCode
          $addedPartialfunction
        }
      }
"""
  
  val jfile = new File("src/main/scala/code/api/v3_0_0/custom/APIMethodsCustom300.scala")
  jfile.getParentFile.mkdirs()
  Files.write(
    jfile.toPath,
    allSourceCode.syntax.replaceAll("""  ::  """,""""  ::  """").getBytes("UTF-8")
  )
  
}
