package code.api.v2_1_0

import java.text.SimpleDateFormat
import java.util.Calendar

import code.TransactionTypes.TransactionType
import code.api.APIFailure
import code.api.util.APIUtil._
import code.api.util.ApiRole._
import code.api.util.{APIUtil, ApiRole, ErrorMessages}
import code.api.v1_2_1.OBPAPI1_2_1._
import code.api.v1_2_1.{APIMethods121, AmountOfMoneyJSON => AmountOfMoneyJSON121, JSONFactory => JSONFactory121}
import code.api.v1_4_0.JSONFactory1_4_0
import code.api.v1_4_0.JSONFactory1_4_0.{ChallengeAnswerJSON, CustomerFaceImageJson, TransactionRequestAccountJSON}
import code.entitlement.Entitlement
import code.search.{elasticsearchMetrics, elasticsearchWarehouse}
import net.liftweb.http.CurrentReq
//import code.api.v2_0_0.{CreateCustomerJson}

import code.model.dataAccess.OBPUser
import net.liftweb.mapper.By

//import code.api.v1_4_0.JSONFactory1_4_0._
import code.api.v2_0_0.JSONFactory200._
import code.bankconnectors.Connector
import code.fx.fx
import code.kycchecks.KycChecks
import code.kycdocuments.KycDocuments
import code.kycmedias.KycMedias
import code.kycstatuses.KycStatuses
import code.model._
import code.model.dataAccess.BankAccountCreation
import code.socialmedia.SocialMediaHandle
import code.transactionrequests.TransactionRequests

import code.meetings.Meeting
import code.usercustomerlinks.UserCustomerLink

import net.liftweb.common.{Full, _}
import net.liftweb.http.rest.RestHelper
import net.liftweb.http.{JsonResponse, Req}
import net.liftweb.json.JsonAST.JValue
import net.liftweb.util.Helpers._
import net.liftweb.util.Props

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
// Makes JValue assignment to Nil work
import code.customer.{MockCustomerFaceImage, Customer}
import code.util.Helper._
import net.liftweb.http.js.JE.JsRaw
import net.liftweb.json.Extraction
import net.liftweb.json.JsonDSL._

import code.api.{APIFailure, OBPRestHelper}
import code.api.util.APIUtil._
import code.sandbox.{OBPDataImport, SandboxDataImport}
import code.util.Helper
import net.liftweb.common.{Box, Full, Failure, Loggable}
import net.liftweb.http.{JsonResponse, ForbiddenResponse, S}
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
      //import data into the sandbox
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
  }
}

object APIMethods210 {
}
