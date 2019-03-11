package code.api.builder.ConfirmationOfFundsServicePIISApi

import code.api.APIFailureNewStyle
import code.api.berlin.group.v1_3.{JSONFactory_BERLIN_GROUP_1_3, JvalueCaseClass, OBP_BERLIN_GROUP_1_3}
import net.liftweb.json
import net.liftweb.json._
import code.api.util.APIUtil.{defaultBankId, _}
import code.api.util.{ApiTag, ApiVersion, NewStyle}
import code.api.util.ErrorMessages._
import code.api.util.ApiTag._
import code.api.util.NewStyle.HttpCode
import code.bankconnectors.Connector
import code.model._
import code.util.Helper
import code.views.Views
import net.liftweb.common.Full
import net.liftweb.http.rest.RestHelper
import com.github.dwickern.macros.NameOf.nameOf

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object APIMethods_ConfirmationOfFundsServicePIISApi extends RestHelper {
    val apiVersion =  OBP_BERLIN_GROUP_1_3.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      checkAvailabilityOfFunds ::
      Nil

            
     resourceDocs += ResourceDoc(
       checkAvailabilityOfFunds,
       apiVersion,
       nameOf(checkAvailabilityOfFunds),
       "POST",
       "/funds-confirmations",
       "Confirmation of Funds Request",
       s"""${mockedDataText(true)}
Creates a confirmation of funds request at the ASPSP. Checks whether a specific amount is available at point of time of the request on an account linked to a given tuple card issuer(TPP)/card number, or addressed by IBAN and TPP respectively""",
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
       ApiTag("Confirmation of Funds Service (PIIS)") :: apiTagMockedData :: Nil
     )

     lazy val checkAvailabilityOfFunds : OBPEndpoint = {
       case "funds-confirmations" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc)
             } yield {
             (json.parse("""{
  "fundsAvailable" : true
}"""), callContext)
           }
         }
       }

}



