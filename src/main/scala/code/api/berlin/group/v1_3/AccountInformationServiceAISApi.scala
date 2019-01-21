package code.api.builder.AccountInformationServiceAISApi
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

trait APIMethods_AccountInformationServiceAISApi { self: RestHelper =>
  val ImplementationsAccountInformationServiceAISApi = new Object() {
    val apiVersion: ApiVersion = ApiVersion.berlinGroupV1_3
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(resourceDocs, apiRelations)
    implicit val formats = net.liftweb.json.DefaultFormats
    val endpoints =
      createConsent ::
      deleteConsent ::
      getAccountList ::
      getBalances ::
      getCardAccount ::
      getCardAccountBalances ::
      getCardAccountTransactionList ::
      getConsentAuthorisation ::
      getConsentInformation ::
      getConsentScaStatus ::
      getConsentStatus ::
      getTransactionDetails ::
      getTransactionList ::
      readAccountDetails ::
      readCardAccount ::
      startConsentAuthorisation ::
      updateConsentsPsuData ::
      Nil

            
     resourceDocs += ResourceDoc(
       createConsent, 
       apiVersion, 
       "createConsent",
       "POST", 
       "/v1/consents", 
       "Create consent",
       "", 
       emptyObjectJson, 
       emptyObjectJson,
       List(UserNotLoggedIn, UnknownError), 
       Catalogs(notCore, notPSD2, notOBWG), 
       AccountInformationServiceAISApi :: Nil
     )

     lazy val createConsent : OBPEndpoint = {
       case "v1":: "consents" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       deleteConsent, 
       apiVersion, 
       "deleteConsent",
       "DELETE", 
       "/v1/consents/CONSENTID", 
       "Delete Consent",
       "", 
       emptyObjectJson, 
       emptyObjectJson,
       List(UserNotLoggedIn, UnknownError), 
       Catalogs(notCore, notPSD2, notOBWG), 
       AccountInformationServiceAISApi :: Nil
     )

     lazy val deleteConsent : OBPEndpoint = {
       case "v1":: "consents" :: consentid :: Nil JsonDelete _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getAccountList, 
       apiVersion, 
       "getAccountList",
       "GET", 
       "/v1/accounts", 
       "Read Account List",
       "", 
       emptyObjectJson, 
       emptyObjectJson,
       List(UserNotLoggedIn, UnknownError), 
       Catalogs(notCore, notPSD2, notOBWG), 
       AccountInformationServiceAISApi :: Nil
     )

     lazy val getAccountList : OBPEndpoint = {
       case "v1":: "accounts" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getBalances, 
       apiVersion, 
       "getBalances",
       "GET", 
       "/v1/accounts/ACCOUNT_ID/balances", 
       "Read Balance",
       "", 
       emptyObjectJson, 
       emptyObjectJson,
       List(UserNotLoggedIn, UnknownError), 
       Catalogs(notCore, notPSD2, notOBWG), 
       AccountInformationServiceAISApi :: Nil
     )

     lazy val getBalances : OBPEndpoint = {
       case "v1":: "accounts" :: account_id:: "balances" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getCardAccount, 
       apiVersion, 
       "getCardAccount",
       "GET", 
       "/card-accounts", 
       "Reads a list of card accounts",
       "", 
       emptyObjectJson, 
       emptyObjectJson,
       List(UserNotLoggedIn, UnknownError), 
       Catalogs(notCore, notPSD2, notOBWG), 
       AccountInformationServiceAISApi :: Nil
     )

     lazy val getCardAccount : OBPEndpoint = {
       case "card-accounts" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getCardAccountBalances, 
       apiVersion, 
       "getCardAccountBalances",
       "GET", 
       "/card-accounts/ACCOUNT_ID/balances", 
       "Read card account balances",
       "", 
       emptyObjectJson, 
       emptyObjectJson,
       List(UserNotLoggedIn, UnknownError), 
       Catalogs(notCore, notPSD2, notOBWG), 
       AccountInformationServiceAISApi :: Nil
     )

     lazy val getCardAccountBalances : OBPEndpoint = {
       case "card-accounts" :: account_id:: "balances" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getCardAccountTransactionList, 
       apiVersion, 
       "getCardAccountTransactionList",
       "GET", 
       "/card-accounts/ACCOUNT_ID/transactions", 
       "Read transaction list of an account",
       "", 
       emptyObjectJson, 
       emptyObjectJson,
       List(UserNotLoggedIn, UnknownError), 
       Catalogs(notCore, notPSD2, notOBWG), 
       AccountInformationServiceAISApi :: Nil
     )

     lazy val getCardAccountTransactionList : OBPEndpoint = {
       case "card-accounts" :: account_id:: "transactions" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getConsentAuthorisation, 
       apiVersion, 
       "getConsentAuthorisation",
       "GET", 
       "/v1/consents/CONSENTID/authorisations", 
       "Get Consent Authorisation Sub-Resources Request",
       "", 
       emptyObjectJson, 
       emptyObjectJson,
       List(UserNotLoggedIn, UnknownError), 
       Catalogs(notCore, notPSD2, notOBWG), 
       AccountInformationServiceAISApi :: Nil
     )

     lazy val getConsentAuthorisation : OBPEndpoint = {
       case "v1":: "consents" :: consentid:: "authorisations" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getConsentInformation, 
       apiVersion, 
       "getConsentInformation",
       "GET", 
       "/v1/consents/CONSENTID", 
       "Get Consent Request",
       "", 
       emptyObjectJson, 
       emptyObjectJson,
       List(UserNotLoggedIn, UnknownError), 
       Catalogs(notCore, notPSD2, notOBWG), 
       AccountInformationServiceAISApi :: Nil
     )

     lazy val getConsentInformation : OBPEndpoint = {
       case "v1":: "consents" :: consentid :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getConsentScaStatus, 
       apiVersion, 
       "getConsentScaStatus",
       "GET", 
       "/v1/consents/CONSENTID/authorisations/AUTHORISATIONID", 
       "Read the SCA status of the consent authorisation.",
       "", 
       emptyObjectJson, 
       emptyObjectJson,
       List(UserNotLoggedIn, UnknownError), 
       Catalogs(notCore, notPSD2, notOBWG), 
       AccountInformationServiceAISApi :: Nil
     )

     lazy val getConsentScaStatus : OBPEndpoint = {
       case "v1":: "consents" :: consentid:: "authorisations" :: authorisationid :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getConsentStatus, 
       apiVersion, 
       "getConsentStatus",
       "GET", 
       "/v1/consents/CONSENTID/status", 
       "Consent status request",
       "", 
       emptyObjectJson, 
       emptyObjectJson,
       List(UserNotLoggedIn, UnknownError), 
       Catalogs(notCore, notPSD2, notOBWG), 
       AccountInformationServiceAISApi :: Nil
     )

     lazy val getConsentStatus : OBPEndpoint = {
       case "v1":: "consents" :: consentid:: "status" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getTransactionDetails, 
       apiVersion, 
       "getTransactionDetails",
       "GET", 
       "/v1/accounts/ACCOUNT_ID/transactions/RESOURCEID", 
       "Read Transaction Details",
       "", 
       emptyObjectJson, 
       emptyObjectJson,
       List(UserNotLoggedIn, UnknownError), 
       Catalogs(notCore, notPSD2, notOBWG), 
       AccountInformationServiceAISApi :: Nil
     )

     lazy val getTransactionDetails : OBPEndpoint = {
       case "v1":: "accounts" :: account_id:: "transactions" :: resourceid :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getTransactionList, 
       apiVersion, 
       "getTransactionList",
       "GET", 
       "/v1/accounts/ACCOUNT_ID/transactions/", 
       "Read transaction list of an account",
       "", 
       emptyObjectJson, 
       emptyObjectJson,
       List(UserNotLoggedIn, UnknownError), 
       Catalogs(notCore, notPSD2, notOBWG), 
       AccountInformationServiceAISApi :: Nil
     )

     lazy val getTransactionList : OBPEndpoint = {
       case "v1":: "accounts" :: account_id:: "transactions" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       readAccountDetails, 
       apiVersion, 
       "readAccountDetails",
       "GET", 
       "/v1/accounts/ACCOUNT_ID", 
       "Read Account Details",
       "", 
       emptyObjectJson, 
       emptyObjectJson,
       List(UserNotLoggedIn, UnknownError), 
       Catalogs(notCore, notPSD2, notOBWG), 
       AccountInformationServiceAISApi :: Nil
     )

     lazy val readAccountDetails : OBPEndpoint = {
       case "v1":: "accounts" :: account_id :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       readCardAccount, 
       apiVersion, 
       "readCardAccount",
       "GET", 
       "/card-accounts/ACCOUNT_ID", 
       "Reads details about a card account",
       "", 
       emptyObjectJson, 
       emptyObjectJson,
       List(UserNotLoggedIn, UnknownError), 
       Catalogs(notCore, notPSD2, notOBWG), 
       AccountInformationServiceAISApi :: Nil
     )

     lazy val readCardAccount : OBPEndpoint = {
       case "card-accounts" :: account_id :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       startConsentAuthorisation, 
       apiVersion, 
       "startConsentAuthorisation",
       "POST", 
       "/v1/consents/CONSENTID/authorisations", 
       "Start the authorisation process for a consent",
       "", 
       emptyObjectJson, 
       emptyObjectJson,
       List(UserNotLoggedIn, UnknownError), 
       Catalogs(notCore, notPSD2, notOBWG), 
       AccountInformationServiceAISApi :: Nil
     )

     lazy val startConsentAuthorisation : OBPEndpoint = {
       case "v1":: "consents" :: consentid:: "authorisations" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       updateConsentsPsuData, 
       apiVersion, 
       "updateConsentsPsuData",
       "PUT", 
       "/v1/consents/CONSENTID/authorisations/AUTHORISATIONID", 
       "Update PSU Data for consents",
       "", 
       emptyObjectJson, 
       emptyObjectJson,
       List(UserNotLoggedIn, UnknownError), 
       Catalogs(notCore, notPSD2, notOBWG), 
       AccountInformationServiceAISApi :: Nil
     )

     lazy val updateConsentsPsuData : OBPEndpoint = {
       case "v1":: "consents" :: consentid:: "authorisations" :: authorisationid :: Nil JsonPut _ => {
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



