package code.api.builder.SigningBasketsApi
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

trait APIMethods_SigningBasketsApi { self: RestHelper =>
  val ImplementationsSigningBasketsApi = new Object() {
    val apiVersion: ApiVersion = ApiVersion.berlinGroupV1_3
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(resourceDocs, apiRelations)
    implicit val formats = net.liftweb.json.DefaultFormats
    val endpoints =
      createSigningBasket ::
      deleteSigningBasket ::
      getSigningBasket ::
      getSigningBasketAuthorisation ::
      getSigningBasketScaStatus ::
      getSigningBasketStatus ::
      startSigningBasketAuthorisation ::
      updateSigningBasketPsuData ::
      Nil

            
     resourceDocs += ResourceDoc(
       createSigningBasket, 
       apiVersion, 
       "createSigningBasket",
       "POST", 
       "/v1/signing-baskets", 
       "Create a signing basket resource",
       "", 
       emptyObjectJson, 
       emptyObjectJson,
       List(UserNotLoggedIn, UnknownError), 
       Catalogs(notCore, notPSD2, notOBWG), 
       SigningBasketsApi :: Nil
     )

     lazy val createSigningBasket : OBPEndpoint = {
       case "v1":: "signing-baskets" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       deleteSigningBasket, 
       apiVersion, 
       "deleteSigningBasket",
       "DELETE", 
       "/v1/signing-baskets/BASKETID", 
       "Delete the signing basket",
       "", 
       emptyObjectJson, 
       emptyObjectJson,
       List(UserNotLoggedIn, UnknownError), 
       Catalogs(notCore, notPSD2, notOBWG), 
       SigningBasketsApi :: Nil
     )

     lazy val deleteSigningBasket : OBPEndpoint = {
       case "v1":: "signing-baskets" :: basketid :: Nil JsonDelete _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getSigningBasket, 
       apiVersion, 
       "getSigningBasket",
       "GET", 
       "/v1/signing-baskets/BASKETID", 
       "Returns the content of an signing basket object.",
       "", 
       emptyObjectJson, 
       emptyObjectJson,
       List(UserNotLoggedIn, UnknownError), 
       Catalogs(notCore, notPSD2, notOBWG), 
       SigningBasketsApi :: Nil
     )

     lazy val getSigningBasket : OBPEndpoint = {
       case "v1":: "signing-baskets" :: basketid :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getSigningBasketAuthorisation, 
       apiVersion, 
       "getSigningBasketAuthorisation",
       "GET", 
       "/v1/signing-baskets/BASKETID/authorisations", 
       "Get Signing Basket Authorisation Sub-Resources Request",
       "", 
       emptyObjectJson, 
       emptyObjectJson,
       List(UserNotLoggedIn, UnknownError), 
       Catalogs(notCore, notPSD2, notOBWG), 
       SigningBasketsApi :: Nil
     )

     lazy val getSigningBasketAuthorisation : OBPEndpoint = {
       case "v1":: "signing-baskets" :: basketid:: "authorisations" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getSigningBasketScaStatus, 
       apiVersion, 
       "getSigningBasketScaStatus",
       "GET", 
       "/v1/signing-baskets/BASKETID/authorisations/AUTHORISATIONID", 
       "Read the SCA status of the signing basket authorisation",
       "", 
       emptyObjectJson, 
       emptyObjectJson,
       List(UserNotLoggedIn, UnknownError), 
       Catalogs(notCore, notPSD2, notOBWG), 
       SigningBasketsApi :: Nil
     )

     lazy val getSigningBasketScaStatus : OBPEndpoint = {
       case "v1":: "signing-baskets" :: basketid:: "authorisations" :: authorisationid :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getSigningBasketStatus, 
       apiVersion, 
       "getSigningBasketStatus",
       "GET", 
       "/v1/signing-baskets/BASKETID/status", 
       "Read the status of the signing basket",
       "", 
       emptyObjectJson, 
       emptyObjectJson,
       List(UserNotLoggedIn, UnknownError), 
       Catalogs(notCore, notPSD2, notOBWG), 
       SigningBasketsApi :: Nil
     )

     lazy val getSigningBasketStatus : OBPEndpoint = {
       case "v1":: "signing-baskets" :: basketid:: "status" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       startSigningBasketAuthorisation, 
       apiVersion, 
       "startSigningBasketAuthorisation",
       "POST", 
       "/v1/signing-baskets/BASKETID/authorisations", 
       "Start the authorisation process for a signing basket",
       "", 
       emptyObjectJson, 
       emptyObjectJson,
       List(UserNotLoggedIn, UnknownError), 
       Catalogs(notCore, notPSD2, notOBWG), 
       SigningBasketsApi :: Nil
     )

     lazy val startSigningBasketAuthorisation : OBPEndpoint = {
       case "v1":: "signing-baskets" :: basketid:: "authorisations" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       updateSigningBasketPsuData, 
       apiVersion, 
       "updateSigningBasketPsuData",
       "PUT", 
       "/v1/signing-baskets/BASKETID/authorisations/AUTHORISATIONID", 
       "Update PSU Data for signing basket",
       "", 
       emptyObjectJson, 
       emptyObjectJson,
       List(UserNotLoggedIn, UnknownError), 
       Catalogs(notCore, notPSD2, notOBWG), 
       SigningBasketsApi :: Nil
     )

     lazy val updateSigningBasketPsuData : OBPEndpoint = {
       case "v1":: "signing-baskets" :: basketid:: "authorisations" :: authorisationid :: Nil JsonPut _ => {
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



