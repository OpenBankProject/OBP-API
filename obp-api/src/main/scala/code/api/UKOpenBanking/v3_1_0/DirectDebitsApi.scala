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
import com.openbankproject.commons.ExecutionContext.Implicits.global

object APIMethods_DirectDebitsApi extends RestHelper {
    val apiVersion = OBP_UKOpenBanking_310.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      getAccountsAccountIdDirectDebits ::
      getDirectDebits ::
      Nil

            
     resourceDocs += ResourceDoc(
       getAccountsAccountIdDirectDebits, 
       apiVersion, 
       nameOf(getAccountsAccountIdDirectDebits),
       "GET", 
       "/accounts/ACCOUNTID/direct-debits", 
       "Get Direct Debits",
       s"""${mockedDataText(true)}
""", 
       emptyObjectJson,
       json.parse("""{
  "Meta" : {
    "FirstAvailableDateTime" : { },
    "TotalPages" : 0
  },
  "Links" : {
    "Last" : "http://example.com/aeiou",
    "Prev" : "http://example.com/aeiou",
    "Next" : "http://example.com/aeiou",
    "Self" : "http://example.com/aeiou",
    "First" : "http://example.com/aeiou"
  },
  "Data" : {
    "DirectDebit" : [ {
      "PreviousPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "AccountId" : { },
      "MandateIdentification" : "MandateIdentification",
      "DirectDebitStatusCode" : { },
      "DirectDebitId" : "DirectDebitId",
      "PreviousPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "Name" : "Name"
    }, {
      "PreviousPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "AccountId" : { },
      "MandateIdentification" : "MandateIdentification",
      "DirectDebitStatusCode" : { },
      "DirectDebitId" : "DirectDebitId",
      "PreviousPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "Name" : "Name"
    } ]
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Direct Debits") :: apiTagMockedData :: Nil
     )

     lazy val getAccountsAccountIdDirectDebits : OBPEndpoint = {
       case "accounts" :: accountid:: "direct-debits" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
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
    "Self" : "http://example.com/aeiou",
    "First" : "http://example.com/aeiou"
  },
  "Data" : {
    "DirectDebit" : [ {
      "PreviousPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "AccountId" : { },
      "MandateIdentification" : "MandateIdentification",
      "DirectDebitStatusCode" : { },
      "DirectDebitId" : "DirectDebitId",
      "PreviousPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "Name" : "Name"
    }, {
      "PreviousPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "AccountId" : { },
      "MandateIdentification" : "MandateIdentification",
      "DirectDebitStatusCode" : { },
      "DirectDebitId" : "DirectDebitId",
      "PreviousPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "Name" : "Name"
    } ]
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getDirectDebits, 
       apiVersion, 
       nameOf(getDirectDebits),
       "GET", 
       "/direct-debits", 
       "Get Direct Debits",
       s"""${mockedDataText(true)}
""", 
       emptyObjectJson,
       json.parse("""{
  "Meta" : {
    "FirstAvailableDateTime" : { },
    "TotalPages" : 0
  },
  "Links" : {
    "Last" : "http://example.com/aeiou",
    "Prev" : "http://example.com/aeiou",
    "Next" : "http://example.com/aeiou",
    "Self" : "http://example.com/aeiou",
    "First" : "http://example.com/aeiou"
  },
  "Data" : {
    "DirectDebit" : [ {
      "PreviousPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "AccountId" : { },
      "MandateIdentification" : "MandateIdentification",
      "DirectDebitStatusCode" : { },
      "DirectDebitId" : "DirectDebitId",
      "PreviousPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "Name" : "Name"
    }, {
      "PreviousPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "AccountId" : { },
      "MandateIdentification" : "MandateIdentification",
      "DirectDebitStatusCode" : { },
      "DirectDebitId" : "DirectDebitId",
      "PreviousPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "Name" : "Name"
    } ]
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Direct Debits") :: apiTagMockedData :: Nil
     )

     lazy val getDirectDebits : OBPEndpoint = {
       case "direct-debits" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
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
    "Self" : "http://example.com/aeiou",
    "First" : "http://example.com/aeiou"
  },
  "Data" : {
    "DirectDebit" : [ {
      "PreviousPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "AccountId" : { },
      "MandateIdentification" : "MandateIdentification",
      "DirectDebitStatusCode" : { },
      "DirectDebitId" : "DirectDebitId",
      "PreviousPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "Name" : "Name"
    }, {
      "PreviousPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "AccountId" : { },
      "MandateIdentification" : "MandateIdentification",
      "DirectDebitStatusCode" : { },
      "DirectDebitId" : "DirectDebitId",
      "PreviousPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "Name" : "Name"
    } ]
  }
}"""), callContext)
           }
         }
       }

}



