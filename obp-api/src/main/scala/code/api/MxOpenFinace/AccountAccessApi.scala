package code.api.MxOpenFinace

import code.api.berlin.group.v1_3.JvalueCaseClass
import code.api.util.APIUtil._
import code.api.util.ApiTag
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import net.liftweb.common.Full
import net.liftweb.http.rest.RestHelper
import net.liftweb.json
import net.liftweb.json._

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer

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
       s"""${mockedDataText(true)}
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
       case "account-access-consents" :: Nil JsonPost _ => {
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

  resourceDocs += ResourceDoc(
    deleteAccountAccessConsentsConsentId,
    apiVersion,
    nameOf(deleteAccountAccessConsentsConsentId),
    "DELETE",
    "/account-access-consents/CONSENT_ID",
    "DeleteAccountAccessConsentsConsentId",
    s"""${mockedDataText(true)}
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
             } yield {
             (NotImplemented, callContext)
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



