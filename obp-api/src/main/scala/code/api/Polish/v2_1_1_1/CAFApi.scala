package code.api.Polish.v2_1_1_1

import code.api.APIFailureNewStyle
import code.api.berlin.group.v1_3.JvalueCaseClass
import net.liftweb.json
import net.liftweb.json._
import code.api.util.APIUtil.{defaultBankId, _}
import code.api.util.{ApiVersion, NewStyle}
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
import code.api.Polish.v2_1_1_1.OBP_PAPI_2_1_1_1
import code.api.util.ApiTag

object APIMethods_CAFApi extends RestHelper {
    val apiVersion =  OBP_PAPI_2_1_1_1.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      getConfirmationOfFunds ::
      Nil

            
     resourceDocs += ResourceDoc(
       getConfirmationOfFunds, 
       apiVersion, 
       nameOf(getConfirmationOfFunds),
       "POST", 
       "/confirmation/v2_1_1.1/getConfirmationOfFunds", 
       "Confirmation of the availability of funds",
       s"""${mockedDataText(true)}
Confirming the availability on the payers account of the amount necessary to execute the payment transaction, as defined in Art. 65 PSD2.""", 
       json.parse("""{
  "amount" : "amount",
  "requestHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "ipAddress" : "ipAddress",
    "tppId" : "tppId",
    "userAgent" : "userAgent"
  },
  "currency" : "currency",
  "accountNumber" : "accountNumber"
}"""),
       json.parse("""{
  "fundsAvailable" : true,
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("CAF") :: apiTagMockedData :: Nil
     )

     lazy val getConfirmationOfFunds : OBPEndpoint = {
       case "confirmation":: "v2_1_1.1":: "getConfirmationOfFunds" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc)
             } yield {
             (json.parse("""{
  "fundsAvailable" : true,
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  }
}"""), callContext)
           }
         }
       }

}



