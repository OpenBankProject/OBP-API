package code.api.v3_0_0.custom

import java.io.File
import java.nio.file.Files
import net.liftweb.util.Props
import scala.meta._

object CustomCode extends App
{
  val getJsonBody: Term.ForYield = 
    q"""for (
      u <- cc.user ?~ UserNotLoggedIn; 
      jsonString = scala.io.Source.fromFile("src/main/scala/code/api/v3_0_0/custom/newAPis.json").mkString; 
      jsonObject: JValue = json.parse(jsonString)
    ) yield {
      successJsonResponse(jsonObject)
    }"""
  
  
  val fixedResouceCode: Term.ApplyInfix = 
    q"""
      resourceDocs += ResourceDoc(
        getBooks, 
        apiVersion, 
        "getBooks", 
        "GET", 
        "/books", 
        "Get Books", 
        "", 
        emptyObjectJson, 
        emptyObjectJson, 
        List(UnknownError), 
        Catalogs(Core, notPSD2, OBWG), 
        apiTagBank :: Nil
      )"""
  
  val newURLProps = Props.get("new.api.url").getOrElse("books")
  val newURL: Lit.String = q""" "books" """.copy(newURLProps)
  
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
    allSourceCode.syntax.getBytes("UTF-8")
  )
  
}
