package code.api.UKOpenBanking.v3_1_0

import code.api.berlin.group.v1_3.JvalueCaseClass
import code.api.util.APIUtil._
import code.api.util.ApiTag
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.common.Full
import net.liftweb.http.rest.RestHelper
import net.liftweb.json
import net.liftweb.json._

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global

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
       s"""${mockedDataText(true)}
""", 
       json.parse(""""""),
       json.parse("""{
  "Meta" : {
    "FirstAvailableDateTime" : { },
    "TotalPages" : 0
  },
  "Risk" : { },
  "Links" : {
    "Last" : "http://example.com/aeiou",
    "Prev" : "http://example.com/aeiou",
    "Next" : "http://example.com/aeiou",
    "Self" : "http://example.com/aeiou",
    "First" : "http://example.com/aeiou"
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

     lazy val createAccountAccessConsents : OBPEndpoint = {
       case "account-access-consents" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc)
             } yield {
             (json.parse("""{
  "Meta" : {
    "FirstAvailableDateTime" : { },
    "TotalPages" : 0
  },
  "Risk" : { },
  "Links" : {
    "Last" : "http://example.com/aeiou",
    "Prev" : "http://example.com/aeiou",
    "Next" : "http://example.com/aeiou",
    "Self" : "http://example.com/aeiou",
    "First" : "http://example.com/aeiou"
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
       "/account-access-consents/CONSENTID", 
       "Delete Account Access Consents",
       s"""${mockedDataText(true)}
""", 
       json.parse(""""""),
       json.parse(""""""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("Account Access") :: apiTagMockedData :: Nil
     )

     lazy val deleteAccountAccessConsentsConsentId : OBPEndpoint = {
       case "account-access-consents" :: consentid :: Nil JsonDelete _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc)
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
       "/account-access-consents/CONSENTID", 
       "Get Account Access Consents",
       s"""${mockedDataText(true)}
""", 
       json.parse(""""""),
       json.parse("""{
  "Meta" : {
    "FirstAvailableDateTime" : { },
    "TotalPages" : 0
  },
  "Risk" : { },
  "Links" : {
    "Last" : "http://example.com/aeiou",
    "Prev" : "http://example.com/aeiou",
    "Next" : "http://example.com/aeiou",
    "Self" : "http://example.com/aeiou",
    "First" : "http://example.com/aeiou"
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
       case "account-access-consents" :: consentid :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc)
             } yield {
             (json.parse("""{
  "Meta" : {
    "FirstAvailableDateTime" : { },
    "TotalPages" : 0
  },
  "Risk" : { },
  "Links" : {
    "Last" : "http://example.com/aeiou",
    "Prev" : "http://example.com/aeiou",
    "Next" : "http://example.com/aeiou",
    "Self" : "http://example.com/aeiou",
    "First" : "http://example.com/aeiou"
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



