package code.api.UKOpenBanking.v3_1_0

import code.api.Constant
import code.api.UKOpenBanking.v3_1_0.JSONFactory_UKOpenBanking_310.ConsentPostBodyUKV310
import code.api.berlin.group.v1_3.JvalueCaseClass
import code.api.util.APIUtil._
import code.api.util.ErrorMessages._
import code.api.util.NewStyle.HttpCode
import code.api.util.{ApiTag, ConsentJWT, JwtUtil, NewStyle}
import code.consent.Consents
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.User
import net.liftweb.common.{Empty, Full}
import net.liftweb.http.js.JE.JsRaw
import net.liftweb.http.rest.RestHelper
import net.liftweb.json
import net.liftweb.json._

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

object APIMethods_AccountAccessApi extends RestHelper {
    val apiVersion = OBP_UKOpenBanking_310.apiVersion
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
       "Create Account Access Consents",
       s"""${mockedDataText(false)}
          |Create Account Access Consents
          |""".stripMargin,
       json.parse("""{
  "Data": {
    "Permissions": [
      "ReadAccountsBasic"
    ],
    "ExpirationDateTime": "2020-10-20T08:40:47.285Z",
    "TransactionFromDateTime": "2020-10-20T08:40:47.285Z",
    "TransactionToDateTime": "2020-10-20T08:40:47.285Z"
  },
  "Risk": ""
}"""),
       json.parse(s"""{
  "Data": {
    "ConsentId": "string",
    "CreationDateTime": "2020-10-20T08:40:47.375Z",
    "Status": "Authorised",
    "StatusUpdateDateTime": "2020-10-20T08:40:47.375Z",
    "Permissions": [
      "ReadAccountsBasic"
    ],
    "ExpirationDateTime": "2020-10-20T08:40:47.375Z",
    "TransactionFromDateTime": "2020-10-20T08:40:47.375Z",
    "TransactionToDateTime": "2020-10-20T08:40:47.375Z"
  },
  "Risk": {},
  "Links": {
    "Self": "${Constant.HostName}/open-banking/v3.1/account-access-consents/CONSENT_ID",
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-10-20T08:40:47.375Z",
    "LastAvailableDateTime": "2020-10-20T08:40:47.375Z"
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Account Access") :: Nil
     )

     lazy val createAccountAccessConsents : OBPEndpoint = {
       case "account-access-consents" :: Nil JsonPost postJson -> _  => {
         cc =>
           for {
             (_, callContext) <- applicationAccess(cc)
             _ <- passesPsd2Aisp(callContext)
             createdByUser: Option[User] <- callContext.map(_.user).getOrElse(Empty) match {
               case Full(user) => Future(Some(user))
               case _ => Future(None)
             }
             failMsg = s"$InvalidJsonFormat The Json body should be the $ConsentPostBodyUKV310 "
             consentJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
               postJson.extract[ConsentPostBodyUKV310]
             }
             createdConsent <- Future(Consents.consentProvider.vend.saveUKConsent(
               createdByUser,
               bankId = None,
               accountIds = None,
               consumerId = callContext.map(_.consumer.map(_.consumerId.get).getOrElse("")),
               permissions = consentJson.Data.Permissions,
               expirationDateTime = DateWithDayFormat.parse(consentJson.Data.ExpirationDateTime) ,
               transactionFromDateTime = DateWithDayFormat.parse(consentJson.Data.TransactionFromDateTime),
               transactionToDateTime= DateWithDayFormat.parse(consentJson.Data.TransactionToDateTime),
               apiStandard = Some("UKOpenBanking"), 
               apiVersion = Some("3.1.0")
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
          "Self" : "${Constant.HostName}/open-banking/v3.1/account-access-consents"
        },
        "Risk" : "",
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
       "Delete Account Access Consents",
       s"""${mockedDataText(false)}
          |Delete Account Access Consents
          |""".stripMargin, 
       emptyObjectJson,
       emptyObjectJson,
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Account Access") :: Nil
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
       "Get Account Access Consents",
       s"""
          |${mockedDataText(false)}
          |Get Account Access Consents
          |""".stripMargin, 
       emptyObjectJson,
       json.parse(s"""{
  "Data": {
    "ConsentId": "string",
    "CreationDateTime": "2020-10-20T10:28:39.801Z",
    "Status": "Authorised",
    "StatusUpdateDateTime": "2020-10-20T10:28:39.801Z",
    "Permissions": [
      "ReadAccountsBasic"
    ],
    "ExpirationDateTime": "2020-10-20T10:28:39.801Z",
    "TransactionFromDateTime": "2020-10-20T10:28:39.801Z",
    "TransactionToDateTime": "2020-10-20T10:28:39.801Z"
  },
  "Risk": "",
  "Links": {
    "Self": "${Constant.HostName}/open-banking/v3.1/account-access-consents/CONSENT_ID",
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-10-20T10:28:39.801Z",
    "LastAvailableDateTime": "2020-10-20T10:28:39.801Z"
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Account Access") :: Nil
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
                "Risk": "",
                "Links" : {
                  "Self" : "${Constant.HostName}/open-banking/v3.1/account-access-consents/CONSENT_ID"
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



