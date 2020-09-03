package code.api.MxOpenFinace

import code.api.Constant
import code.api.MxOpenFinace.JSONFactory_MX_OPEN_FINANCE_0_0_1.ConsentPostBodyMXOFV001
import code.api.berlin.group.v1_3.JvalueCaseClass
import code.api.util.APIUtil._
import code.api.util.{ApiTag, NewStyle}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.NewStyle.HttpCode
import code.consent.Consents
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import net.liftweb.common.Full
import net.liftweb.http.js.JE.JsRaw
import net.liftweb.http.rest.RestHelper
import net.liftweb.json
import net.liftweb.json._

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

object APIMethods_AccountAccessApi extends RestHelper {
    val apiVersion =  MxOpenFinanceCollector.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      createAccountAccessConsents ::
      deleteAccountAccessConsentsConsentId ::
      getAccountAccessConsentsConsentId ::
      Nil

            
     resourceDocs += ResourceDoc(
       createAccountAccessConsents, 
       apiVersion, 
       nameOf(createAccountAccessConsents),
       "POST", 
       "/account-access-consents", 
       "CreateAccountAccessConsents",
       s"""${mockedDataText(false)}
            Create Account Access Consents
            """,
       json.parse("""{
              "Data" : {
                "TransactionToDateTime" : "2000-01-23T04:56:07.000+00:00",
                "ExpirationDateTime" : "2000-01-23T04:56:07.000+00:00",
                "Permissions" : [ "ReadAccountsBasic", "ReadAccountsDetail" ],
                "TransactionFromDateTime" : "2000-01-23T04:56:07.000+00:00"
              }
}"""),
       json.parse("""{
              "Meta" : {
                "LastAvailableDateTime" : "2000-01-23T04:56:07.000+00:00",
                "FirstAvailableDateTime" : "2000-01-23T04:56:07.000+00:00",
                "TotalPages" : 0
              },
              "Links" : {
                "Last" : "Last",
                "Prev" : "Prev",
                "Next" : "Next",
                "Self" : "Self",
                "First" : "First"
              },
              "Data" : {
                "Status" : "Authorised",
                "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
                "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
                "TransactionToDateTime" : "2000-01-23T04:56:07.000+00:00",
                "ExpirationDateTime" : "2000-01-23T04:56:07.000+00:00",
                "Permissions" : [ "ReadAccountsBasic", "ReadAccountsDetail" ],
                "ConsentId" : "ConsentId",
                "TransactionFromDateTime" : "2000-01-23T04:56:07.000+00:00"
              }
        }"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("Account Access") :: apiTagMockedData :: Nil
     )

     lazy val createAccountAccessConsents : OBPEndpoint = {
       case "account-access-consents" :: Nil JsonPost postJson -> _  => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             failMsg = s"$InvalidJsonFormat The Json body should be the $ConsentPostBodyMXOFV001 "
             consentJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
               postJson.extract[ConsentPostBodyMXOFV001]
             }
             
             createdConsent <- Future(Consents.consentProvider.vend.saveUKConsent(
               u,
               bankId = None,
               accountIds = None,
               consumerId = None,
               permissions = consentJson.Data.Permissions,
               expirationDateTime = DateWithDayFormat.parse(consentJson.Data.ExpirationDateTime) ,
               transactionFromDateTime = DateWithDayFormat.parse(consentJson.Data.TransactionFromDateTime),
               transactionToDateTime= DateWithDayFormat.parse(consentJson.Data.TransactionToDateTime)
             )) map {
               i => connectorEmptyResponse(i, callContext)
             }
           } yield {
            (json.parse(s"""{
        "Meta" : {
          "LastAvailableDateTime" : "2000-01-23T04:56:07.000+00:00",
          "FirstAvailableDateTime" : "2000-01-23T04:56:07.000+00:00",
          "TotalPages" : 0
        },
        "Links" : {
          "Self" : "${Constant.HostName}/mx-open-finance/v0.0.1/account-access-consents"
        },
        "Data" : {
          "Status" : "${createdConsent.status}",
          "StatusUpdateDateTime" : "${createdConsent.statusUpdateDateTime.toString}",
          "CreationDateTime" : "${createdConsent.creationDateTime.toString}",
          "TransactionToDateTime" : "${consentJson.Data.TransactionToDateTime}",
          "ExpirationDateTime" :  "${consentJson.Data.ExpirationDateTime}",
          "Permissions" : ${consentJson.Data.Permissions.mkString("""["""","""","""",""""]""")},
          "ConsentId" : "${createdConsent.consentId}",
          "TransactionFromDateTime" : "${consentJson.Data.TransactionFromDateTime}",
        }
    }"""), HttpCode.`201`(callContext))
           }
         }
       }

  resourceDocs += ResourceDoc(
    deleteAccountAccessConsentsConsentId,
    apiVersion,
    nameOf(deleteAccountAccessConsentsConsentId),
    "DELETE",
    "/account-access-consents/CONSENT_ID",
    "DeleteAccountAccessConsentsConsentId",
    s"""${mockedDataText(false)}
            Delete Account Access Consents
            """,
    json.parse(""""""),
       json.parse(""""""),
    List(UserNotLoggedIn, UnknownError),
    Catalogs(notCore, notPSD2, notOBWG),
    ApiTag("Account Access") :: apiTagMockedData :: Nil
  )

     lazy val deleteAccountAccessConsentsConsentId : OBPEndpoint = {
       case "account-access-consents" :: consentId :: Nil JsonDelete _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             _ <- passesPsd2Aisp(callContext)
             consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
               unboxFullOrFail(_, callContext, ConsentNotFound)
             }
             consent <- Future(Consents.consentProvider.vend.revoke(consentId)) map {
               i => connectorEmptyResponse(i, callContext)
             }
           } yield {
             (JsRaw(""), HttpCode.`204`(callContext))
           }
         }
       }

  resourceDocs += ResourceDoc(
    getAccountAccessConsentsConsentId,
    apiVersion,
    nameOf(getAccountAccessConsentsConsentId),
    "GET",
    "/account-access-consents/CONSENT_ID",
    "GetAccountAccessConsentsConsentId",
    s"""${mockedDataText(true)}
            Get Account Access Consents
            """,
    json.parse(""""""),
    json.parse(
      """{
          "Meta" : {
            "LastAvailableDateTime" : "2000-01-23T04:56:07.000+00:00",
            "FirstAvailableDateTime" : "2000-01-23T04:56:07.000+00:00",
            "TotalPages" : 0
          },
          "Links" : {
            "Last" : "Last",
            "Prev" : "Prev",
            "Next" : "Next",
            "Self" : "Self",
            "First" : "First"
          },
          "Data" : {
            "Status" : { },
            "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
            "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
            "TransactionToDateTime" : "2000-01-23T04:56:07.000+00:00",
            "ExpirationDateTime" : "2000-01-23T04:56:07.000+00:00",
            "Permissions" : [ { }, { } ],
            "ConsentId" : "ConsentId",
            "TransactionFromDateTime" : "2000-01-23T04:56:07.000+00:00"
          }
        }"""),
    List(UserNotLoggedIn, UnknownError),
    Catalogs(notCore, notPSD2, notOBWG),
    ApiTag("Account Access") :: apiTagMockedData :: Nil
  )

     lazy val getAccountAccessConsentsConsentId : OBPEndpoint = {
       case "account-access-consents" :: consentId :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{
  "Meta" : {
    "LastAvailableDateTime" : "2000-01-23T04:56:07.000+00:00",
    "FirstAvailableDateTime" : "2000-01-23T04:56:07.000+00:00",
    "TotalPages" : 0
  },
  "Links" : {
    "Last" : "Last",
    "Prev" : "Prev",
    "Next" : "Next",
    "Self" : "Self",
    "First" : "First"
  },
  "Data" : {
    "Status" : { },
    "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "TransactionToDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExpirationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Permissions" : [ { }, { } ],
    "ConsentId" : "ConsentId",
    "TransactionFromDateTime" : "2000-01-23T04:56:07.000+00:00"
  }
}"""), callContext)
           }
         }
       }

}



