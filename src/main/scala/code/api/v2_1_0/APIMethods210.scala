package code.api.v2_1_0

import java.text.SimpleDateFormat
import code.api.util.ApiRole._
import code.api.util.ErrorMessages
import code.api.v2_1_0.JSONFactory210
import code.model._

import net.liftweb.http.Req
import net.liftweb.json.Extraction
import net.liftweb.json.JsonAST.JValue
import net.liftweb.util.Props

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
// Makes JValue assignment to Nil work
import code.util.Helper._
import net.liftweb.json.JsonDSL._

import code.api.APIFailure
import code.api.util.APIUtil._
import code.sandbox.{OBPDataImport, SandboxDataImport}
import code.util.Helper
import net.liftweb.common.Box
import net.liftweb.http.JsonResponse
import net.liftweb.http.js.JE.JsRaw
import net.liftweb.http.rest.RestHelper
import net.liftweb.util.Helpers._


trait APIMethods210 {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>

  // helper methods begin here
  // helper methods end here

  val Implementations2_1_0 = new Object() {

    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()

    val emptyObjectJson: JValue = Nil
    val apiVersion: String = "2_1_0"

    val exampleDateString: String = "22/08/2013"
    val simpleDateFormat: SimpleDateFormat = new SimpleDateFormat("dd/mm/yyyy")
    val exampleDate = simpleDateFormat.parse(exampleDateString)

    val codeContext = CodeContext(resourceDocs, apiRelations)


    resourceDocs += ResourceDoc(
      sandboxDataImport,
      apiVersion,
      "sandboxDataImport",
      "POST",
      "/sandbox/data-import",
      "Import data into the sandbox.",
      s"""Import bulk data into the sandbox (Authenticated access).
          |The user needs to have CanCreateSandbox entitlement.
          |
          |An example of an import set of data (json) can be found [here](https://raw.githubusercontent.com/OpenBankProject/OBP-API/develop/src/main/scala/code/api/sandbox/example_data/2016-04-28/example_import.json)
         |${authenticationRequiredMessage(true)}
          |""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      false,
      false,
      false,
      List(apiTagAccount, apiTagPrivateData, apiTagPublicData))


    lazy val sandboxDataImport: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      // Import data into the sandbox
      case "sandbox" :: "data-import" :: Nil JsonPost json -> _ => {
        user =>
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            allowDataImportProp <- Props.get("allow_sandbox_data_import") ~> APIFailure("Data import is disabled for this API instance.", 403)
            allowDataImport <- Helper.booleanToBox(allowDataImportProp == "true") ~> APIFailure("Data import is disabled for this API instance.", 403)
            canCreateSandbox <- booleanToBox(hasEntitlement("", u.userId, CanCreateSandbox), s"$CanCreateSandbox entitlement required")
            importData <- tryo {json.extract[SandboxDataImport]} ?~ "invalid json"
            importWorked <- OBPDataImport.importer.vend.importData(importData)
          } yield {
            successJsonResponse(JsRaw("{}"), 201)
          }
      }
    }


    val getTransactionRequestTypesIsPublic = Props.getBool("apiOptions.getTransactionRequestTypesIsPublic", true)

    resourceDocs += ResourceDoc(
      getTransactionRequestTypesSupportedByBank,
      apiVersion,
      "getTransactionRequestTypesSupportedByBank",
      "GET",
      "/banks/BANK_ID/transaction-request-types",
      "Get the Transaction Request Types supported by the bank",
      s"""Get the list of the Transaction Request Types supported by the bank.
        |
        |${authenticationRequiredMessage(!getTransactionRequestTypesIsPublic)}
        |""",
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      false,
      false,
      false,
      List(apiTagBank, apiTagTransactionRequest))


    lazy val getTransactionRequestTypesSupportedByBank: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      // Get transaction request types supported by the bank
      case "banks" :: BankId(bankId) :: "transaction-request-types" :: Nil JsonGet _ => {
        user =>
          for {
            u <- if(getTransactionRequestTypesIsPublic)
              Box(Some(1))
            else
              user ?~! ErrorMessages.UserNotLoggedIn
            bank <- Bank(bankId) ?~! {ErrorMessages.BankNotFound}
            // Get Transaction Request Types from Props "transactionRequests_supported_types". Default is empty string
            transactionRequestTypes <- tryo(Props.get("transactionRequests_supported_types", ""))
          } yield {
            // Format the data as json
            val json = JSONFactory210.createTransactionRequestTypeJSON(transactionRequestTypes.split(",").toList)
            // Return
            successJsonResponse(Extraction.decompose(json))
          }
      }
    }
  }
}

object APIMethods210 {
}
