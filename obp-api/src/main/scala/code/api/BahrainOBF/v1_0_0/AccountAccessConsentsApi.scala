package code.api.BahrainOBF.v1_0_0

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

object APIMethods_AccountAccessConsentsApi extends RestHelper {
    val apiVersion =  ApiCollector.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      accountAccessConsentsConsentIdGet ::
      accountAccessConsentsConsentIdPatch ::
      accountAccessConsentsPost ::
      Nil

            
     resourceDocs += ResourceDoc(
       accountAccessConsentsConsentIdGet, 
       apiVersion, 
       nameOf(accountAccessConsentsConsentIdGet),
       "GET", 
       "/account-access-consents/CONSENT_ID", 
       "Get Account Access Consents by ConsentId",
       s"""${mockedDataText(true)}
            
            """,
       json.parse(""""""),
       json.parse("""{
  "Meta" : {
    "FirstAvailableDateTime" : { },
    "TotalPages" : 0
  },
  "Links" : {
    "Last" : "http://example.com/aeiou",
    "Prev" : "http://example.com/aeiou",
    "Next" : "http://example.com/aeiou",
    "First" : "http://example.com/aeiou",
    "Self" : "http://example.com/aeiou"
  },
  "Data" : {
    "Status" : "Authorised",
    "StatusUpdateDateTime" : { },
    "CreationDateTime" : { },
    "TransactionToDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Permissions" : [ "ReadAccountsBasic", "ReadAccountsBasic" ],
    "ConsentId" : "ConsentId",
    "TransactionFromDateTime" : "2000-01-23T04:56:07.000+00:00"
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Account Access Consents") :: apiTagMockedData :: Nil
     )

     lazy val accountAccessConsentsConsentIdGet : OBPEndpoint = {
       case "account-access-consents" :: consentId :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{
  "Meta" : {
    "FirstAvailableDateTime" : { },
    "TotalPages" : 0
  },
  "Links" : {
    "Last" : "http://example.com/aeiou",
    "Prev" : "http://example.com/aeiou",
    "Next" : "http://example.com/aeiou",
    "First" : "http://example.com/aeiou",
    "Self" : "http://example.com/aeiou"
  },
  "Data" : {
    "Status" : "Authorised",
    "StatusUpdateDateTime" : { },
    "CreationDateTime" : { },
    "TransactionToDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Permissions" : [ "ReadAccountsBasic", "ReadAccountsBasic" ],
    "ConsentId" : "ConsentId",
    "TransactionFromDateTime" : "2000-01-23T04:56:07.000+00:00"
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       accountAccessConsentsConsentIdPatch, 
       apiVersion, 
       nameOf(accountAccessConsentsConsentIdPatch),
       "PATCH", 
       "/account-access-consents/CONSENT_ID", 
       "Update Account Access Consent Status by ConsentId",
       s"""${mockedDataText(true)}
            
            """,
       json.parse("""{
  "Data" : {
    "Status" : "Revoked"
  }
}"""),
       json.parse("""{
  "Meta" : {
    "FirstAvailableDateTime" : { },
    "TotalPages" : 0
  },
  "Links" : {
    "Last" : "http://example.com/aeiou",
    "Prev" : "http://example.com/aeiou",
    "Next" : "http://example.com/aeiou",
    "First" : "http://example.com/aeiou",
    "Self" : "http://example.com/aeiou"
  },
  "Data" : {
    "Status" : "Authorised",
    "StatusUpdateDateTime" : { },
    "CreationDateTime" : { },
    "TransactionToDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Permissions" : [ "ReadAccountsBasic", "ReadAccountsBasic" ],
    "ConsentId" : "ConsentId",
    "TransactionFromDateTime" : "2000-01-23T04:56:07.000+00:00"
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Account Access Consents") :: apiTagMockedData :: Nil
     )

     lazy val accountAccessConsentsConsentIdPatch : OBPEndpoint = {
       case "account-access-consents" :: consentId :: Nil JsonPatch _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{
  "Meta" : {
    "FirstAvailableDateTime" : { },
    "TotalPages" : 0
  },
  "Links" : {
    "Last" : "http://example.com/aeiou",
    "Prev" : "http://example.com/aeiou",
    "Next" : "http://example.com/aeiou",
    "First" : "http://example.com/aeiou",
    "Self" : "http://example.com/aeiou"
  },
  "Data" : {
    "Status" : "Authorised",
    "StatusUpdateDateTime" : { },
    "CreationDateTime" : { },
    "TransactionToDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Permissions" : [ "ReadAccountsBasic", "ReadAccountsBasic" ],
    "ConsentId" : "ConsentId",
    "TransactionFromDateTime" : "2000-01-23T04:56:07.000+00:00"
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       accountAccessConsentsPost, 
       apiVersion, 
       nameOf(accountAccessConsentsPost),
       "POST", 
       "/account-access-consents", 
       "Create Account Access Consents",
       s"""${mockedDataText(true)}
            
            """,
       json.parse("""{
  "Data" : {
    "TransactionToDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Permissions" : [ "ReadAccountsBasic", "ReadAccountsBasic" ],
    "TransactionFromDateTime" : "2000-01-23T04:56:07.000+00:00"
  }
}"""),
       json.parse("""{
  "Meta" : {
    "FirstAvailableDateTime" : { },
    "TotalPages" : 0
  },
  "Links" : {
    "Last" : "http://example.com/aeiou",
    "Prev" : "http://example.com/aeiou",
    "Next" : "http://example.com/aeiou",
    "First" : "http://example.com/aeiou",
    "Self" : "http://example.com/aeiou"
  },
  "Data" : {
    "Status" : "Authorised",
    "StatusUpdateDateTime" : { },
    "CreationDateTime" : { },
    "TransactionToDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Permissions" : [ "ReadAccountsBasic", "ReadAccountsBasic" ],
    "ConsentId" : "ConsentId",
    "TransactionFromDateTime" : "2000-01-23T04:56:07.000+00:00"
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Account Access Consents") :: apiTagMockedData :: Nil
     )

     lazy val accountAccessConsentsPost : OBPEndpoint = {
       case "account-access-consents" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{
  "Meta" : {
    "FirstAvailableDateTime" : { },
    "TotalPages" : 0
  },
  "Links" : {
    "Last" : "http://example.com/aeiou",
    "Prev" : "http://example.com/aeiou",
    "Next" : "http://example.com/aeiou",
    "First" : "http://example.com/aeiou",
    "Self" : "http://example.com/aeiou"
  },
  "Data" : {
    "Status" : "Authorised",
    "StatusUpdateDateTime" : { },
    "CreationDateTime" : { },
    "TransactionToDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Permissions" : [ "ReadAccountsBasic", "ReadAccountsBasic" ],
    "ConsentId" : "ConsentId",
    "TransactionFromDateTime" : "2000-01-23T04:56:07.000+00:00"
  }
}"""), callContext)
           }
         }
       }

}



