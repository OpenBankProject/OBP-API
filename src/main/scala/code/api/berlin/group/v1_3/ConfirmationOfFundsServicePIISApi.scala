package code.api.builder.ConfirmationOfFundsServicePIISApi
import java.util.UUID

import code.api.berlin.group.v1_3.JvalueCaseClass
import code.api.builder.{APIBuilder_Connector, CreateTemplateJson, JsonFactory_APIBuilder}
import code.api.builder.JsonFactory_APIBuilder._
import code.api.util.APIUtil._
import code.api.util.ApiTag._
import code.api.util.ApiVersion
import code.api.util.ErrorMessages._
import net.liftweb.common.Full
import net.liftweb.http.rest.RestHelper
import net.liftweb.json
import net.liftweb.json.Extraction._
import net.liftweb.json._
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait APIMethods_ConfirmationOfFundsServicePIISApi { self: RestHelper =>
  val ImplementationsConfirmationOfFundsServicePIISApi = new Object() {
    val apiVersion: ApiVersion = ApiVersion.berlinGroupV1_3
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(resourceDocs, apiRelations)
    implicit val formats = net.liftweb.json.DefaultFormats
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints =
      checkAvailabilityOfFunds ::
      Nil

            
     resourceDocs += ResourceDoc(
       checkAvailabilityOfFunds, 
       apiVersion, 
       "checkAvailabilityOfFunds",
       "POST", 
       "/v1/funds-confirmations", 
       "Confirmation of Funds Request",
       """Creates a confirmation of funds request at the ASPSP. Checks whether a specific amount is available at point of time of the request on an account linked to a given tuple card issuer(TPP)/card number, or addressed by IBAN and TPP respectively""", 
       json.parse("""{
  "payee" : "payee",
  "instructedAmount" : {
    "amount" : "123",
    "currency" : "EUR"
  },
  "account" : {
    "bban" : "BARC12345612345678",
    "maskedPan" : "123456xxxxxx1234",
    "iban" : "FR7612345987650123456789014",
    "currency" : "EUR",
    "msisdn" : "+49 170 1234567",
    "pan" : "5409050000000000"
  },
  "cardNumber" : "cardNumber"
}"""),
       json.parse("""{
  "fundsAvailable" : true
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ConfirmationOfFundsServicePIISApi :: apiTagDummyData :: Nil
     )

     lazy val checkAvailabilityOfFunds : OBPEndpoint = {
       case "v1":: "funds-confirmations" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (json.parse("""{
  "fundsAvailable" : true
}"""), callContext)
           }
         }
       }

  }
}



