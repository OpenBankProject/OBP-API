package code.api.builder.ConfirmationOfFundsServicePIISApi
import java.util.UUID

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
       "", 
       emptyObjectJson, 
       emptyObjectJson,
       List(UserNotLoggedIn, UnknownError), 
       Catalogs(notCore, notPSD2, notOBWG), 
       ConfirmationOfFundsServicePIISApi :: Nil
     )

     lazy val checkAvailabilityOfFunds : OBPEndpoint = {
       case "v1":: "funds-confirmations" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }

  }
}



