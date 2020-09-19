package code.api.MxOpenFinace

import code.api.Constant
import code.api.MxOpenFinace.JSONFactory_MX_OPEN_FINANCE_0_0_1.ConsentPostBodyMXOFV001
import code.api.util.APIUtil._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.NewStyle.HttpCode
import code.api.util.{ApiTag, ConsentJWT, JwtUtil, NewStyle}
import code.consent.Consents
import code.users.Users
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.User
import net.liftweb.common.{Box, Empty, Full}
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
                "TransactionToDateTime" : "2000-01-23T06:44:05.618Z",
                "ExpirationDateTime" : "2000-01-23T06:44:05.618Z",
                "Permissions" : ["ReadAccountsBasic", "ReadAccountsDetail", "ReadBalances", "ReadTransactionsBasic", "ReadTransactionsDebits", "ReadTransactionsDetail"],
                "TransactionFromDateTime" : "2000-01-23T06:44:05.618Z"
              }
}"""),
       json.parse("""{
              "Meta" : {
                "LastAvailableDateTime" : "2000-01-23T06:44:05.618Z",
                "FirstAvailableDateTime" : "2000-01-23T06:44:05.618Z",
                "TotalPages" : 0
              },
              "Links" : {
                "Self" : "Self"
              },
              "Data" : {
                "Status" : "Authorised",
                "StatusUpdateDateTime" : "2000-01-23T06:44:05.618Z",
                "CreationDateTime" : "2000-01-23T06:44:05.618Z",
                "TransactionToDateTime" : "2000-01-23T06:44:05.618Z",
                "ExpirationDateTime" : "2000-01-23T06:44:05.618Z",
                "Permissions" : ["ReadAccountsBasic", "ReadAccountsDetail", "ReadBalances", "ReadTransactionsBasic", "ReadTransactionsDebits", "ReadTransactionsDetail"],
                "ConsentId" : "ConsentId",
                "TransactionFromDateTime" : "2000-01-23T06:44:05.618Z"
              }
        }"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("Account Access") :: apiTagMXOpenFinance :: Nil
     )

     lazy val createAccountAccessConsents : OBPEndpoint = {
       case "account-access-consents" :: Nil JsonPost postJson -> _  => {
         cc =>
           for {
             (_, callContext) <- applicationAccess(cc)
             createdByUser: Option[User] <- callContext.map(_.user).getOrElse(Empty) match {
               case Full(user) => Future(Some(user))
               case _ => Future(None)
             }
             failMsg = s"$InvalidJsonFormat The Json body should be the $ConsentPostBodyMXOFV001 "
             consentJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
               postJson.extract[ConsentPostBodyMXOFV001]
             }
             createdConsent <- Future(Consents.consentProvider.vend.saveUKConsent(
               createdByUser,
               bankId = Option(consentJson.Data.BankId),
               accountIds = None,
               consumerId = callContext.map(_.consumer.map(_.consumerId.get).getOrElse("")),
               permissions = consentJson.Data.Permissions,
               expirationDateTime = DateWithDayFormat.parse(consentJson.Data.ExpirationDateTime) ,
               transactionFromDateTime = DateWithDayFormat.parse(consentJson.Data.TransactionFromDateTime),
               transactionToDateTime= DateWithDayFormat.parse(consentJson.Data.TransactionToDateTime),
               apiStandard = Some("MXOpenFinance"), 
               apiVersion = Some("0.0.1")
             )) map {
               i => connectorEmptyResponse(i, callContext)
             }
           } yield {
            (json.parse(s"""{
        "Meta" : {
          "LastAvailableDateTime" : "2000-01-23T06:44:05.618Z",
          "FirstAvailableDateTime" : "2000-01-23T06:44:05.618Z",
          "TotalPages" : 0
        },
        "Links" : {
          "Self" : "${Constant.HostName}/mx-open-finance/v0.0.1/account-access-consents"
        },
        "Data" : {
          "Status" : "${createdConsent.status}",
          "StatusUpdateDateTime" : "${createdConsent.statusUpdateDateTime}",
          "CreationDateTime" : "${createdConsent.creationDateTime}",
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
    emptyObjectJson,
    emptyObjectJson,
    List(UserNotLoggedIn, UnknownError),
    Catalogs(notCore, notPSD2, notOBWG),
    ApiTag("Account Access") :: apiTagMXOpenFinance :: Nil
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
    s"""${mockedDataText(false)}
            Get Account Access Consents
            """,
    emptyObjectJson,
    json.parse(
      """{
          "Meta" : {
            "LastAvailableDateTime" : "2000-01-23T06:44:05.618Z",
            "FirstAvailableDateTime" : "2000-01-23T06:44:05.618Z",
            "TotalPages" : 0
          },
          "Links" : {
            "Self" : "Self",
          },
          "Data" : {
            "Status" : "AUTHORISED",
            "StatusUpdateDateTime" : "2000-01-23T06:44:05.618Z",
            "CreationDateTime" : "2000-01-23T06:44:05.618Z",
            "TransactionToDateTime" : "2000-01-23T06:44:05.618Z",
            "ExpirationDateTime" : "2000-01-23T06:44:05.618Z",
            "Permissions" : ["ReadAccountsBasic", "ReadAccountsDetail", "ReadBalances", "ReadTransactionsBasic", "ReadTransactionsDebits", "ReadTransactionsDetail"],
            "ConsentId" : "ConsentId",
            "TransactionFromDateTime" : "2000-01-23T06:44:05.618Z"
          }
        }"""),
    List(UserNotLoggedIn, UnknownError),
    Catalogs(notCore, notPSD2, notOBWG),
    ApiTag("Account Access") :: apiTagMXOpenFinance :: Nil
  )

     lazy val getAccountAccessConsentsConsentId : OBPEndpoint = {
       case "account-access-consents" :: consentId :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
               unboxFullOrFail(_, callContext, s"$ConsentNotFound ($consentId)")
             }
             consentViews <- Future(JwtUtil.getSignedPayloadAsJson(consent.jsonWebToken).map(net.liftweb.json.parse(_).extract[ConsentJWT].views.map(_.view_id))) map {
               unboxFullOrFail(_, callContext, s"$ConsentViewNotFund ($consentId)")
             }
             } yield {
             (json.parse(s"""{
                "Meta" : {
                  "LastAvailableDateTime" : "2000-01-23T06:44:05.618Z",
                  "FirstAvailableDateTime" : "2000-01-23T06:44:05.618Z",
                  "TotalPages" : 0
                },
                "Links" : {
                  "Self" : "${Constant.HostName}/mx-open-finance/v0.0.1/account-access-consents/CONSENT_ID"
                },
                "Data" : {
                  "Status" : "${consent.status}",
                  "StatusUpdateDateTime" : "${consent.statusUpdateDateTime}",
                  "CreationDateTime" : "${consent.creationDateTime}",
                  "TransactionToDateTime" : "${consent.transactionToDateTime}",
                  "ExpirationDateTime" :  "${consent.expirationDateTime}",
                  "Permissions" : ${consentViews.mkString("""["""","""","""",""""]""")},
                  "ConsentId" : "${consent.consentId}",
                  "TransactionFromDateTime" : "${consent.transactionFromDateTime}",
                }
            }"""), HttpCode.`200`(callContext))
           }
         }
       }

}



