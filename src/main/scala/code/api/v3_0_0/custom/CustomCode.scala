package code.api.v3_0_0.custom

import java.io.File
import java.nio.file.Files

import net.liftweb.util.Props

import scala.meta._

object CustomCode extends App
{
  
  val fixedResouceCode: Term.ApplyInfix = q"""resourceDocs += ResourceDoc(getBanks, apiVersion, "getBanks", "GET", "/banks", "Get Banks", "", emptyObjectJson, banksJSON, List(UnknownError), Catalogs(Core, notPSD2, OBWG), apiTagBank :: Nil)"""
  
  val newURLProps = Props.get("new.api.url").getOrElse("banks")
  val newURL: Lit.String = q""" "banks" """.copy(newURLProps)
  
  val addedPartialfunction: Defn.Val = q"""lazy val getBanks: OBPEndpoint = {
      case ("custom" :: $newURL :: Nil) JsonGet req =>
        cc => {
          def banksToJson(banksList: List[Bank]): JValue = {
            val banksJSON: List[BankJSON] = banksList.map(b => JSONFactory.createBankJSON(b))
            val banks = new BanksJSON(banksJSON)
            Extraction.decompose(banks)
          }
          for (banks <- Bank.all) yield successJsonResponse(banksToJson(banks))
        }
    }"""
  
  val addedEndpoitList = q"""def endpointsOfCustom3_0_0 = createTransactionRequestTransferToReferenceAccountCustom :: getBanks::Nil"""
  
  val allSourceCode: Source = source""" package code.api.v3_0_0.custom

import code.api.util.APIUtil.{ApiRelation, Catalogs, CodeContext, Core, OBPEndpoint, OBWG, ResourceDoc, notPSD2, successJsonResponse}
import code.api.util.ApiVersion
import code.api.util.ErrorMessages.UnknownError
import code.api.v1_2_1.{BankJSON, BanksJSON, JSONFactory}
import code.model.Bank
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.Extraction
import net.liftweb.json.JsonAST.JValue
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import code.api.util.APIUtil._

trait CustomAPIMethods300 {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
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
  
  val jfile = new File(
    "src/main/scala/code/api/v3_0_0/custom/APIMethodsCustom300.scala"
  )
  jfile.getParentFile.mkdirs()
  // Do scala.meta code generation here.
  Files.write(
    jfile.toPath,
    allSourceCode.syntax.getBytes("UTF-8")
  )
}
