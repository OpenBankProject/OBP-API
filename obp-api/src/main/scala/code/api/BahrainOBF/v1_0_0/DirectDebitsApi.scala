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

object APIMethods_DirectDebitsApi extends RestHelper {
    val apiVersion =  ApiCollector.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      accountsAccountIdDirectDebitsGet ::
      directDebitsGet ::
      Nil

            
     resourceDocs += ResourceDoc(
       accountsAccountIdDirectDebitsGet, 
       apiVersion, 
       nameOf(accountsAccountIdDirectDebitsGet),
       "GET", 
       "/accounts/ACCOUNT_ID/direct-debits", 
       "Get Accounts Direct Debits by AccountId",
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
    "DirectDebit" : [ {
      "SupplementaryData" : { },
      "PreviousPaymentDateTime" : { },
      "AccountId" : { },
      "MandateIdentification" : { },
      "DirectDebitStatusCode" : { },
      "Frequency" : "Frequency",
      "DirectDebitId" : { },
      "PreviousPaymentAmount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "Name" : { }
    }, {
      "SupplementaryData" : { },
      "PreviousPaymentDateTime" : { },
      "AccountId" : { },
      "MandateIdentification" : { },
      "DirectDebitStatusCode" : { },
      "Frequency" : "Frequency",
      "DirectDebitId" : { },
      "PreviousPaymentAmount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "Name" : { }
    } ]
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Direct Debits") :: apiTagMockedData :: Nil
     )

     lazy val accountsAccountIdDirectDebitsGet : OBPEndpoint = {
       case "accounts" :: accountId:: "direct-debits" :: Nil JsonGet _ => {
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
    "DirectDebit" : [ {
      "SupplementaryData" : { },
      "PreviousPaymentDateTime" : { },
      "AccountId" : { },
      "MandateIdentification" : { },
      "DirectDebitStatusCode" : { },
      "Frequency" : "Frequency",
      "DirectDebitId" : { },
      "PreviousPaymentAmount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "Name" : { }
    }, {
      "SupplementaryData" : { },
      "PreviousPaymentDateTime" : { },
      "AccountId" : { },
      "MandateIdentification" : { },
      "DirectDebitStatusCode" : { },
      "Frequency" : "Frequency",
      "DirectDebitId" : { },
      "PreviousPaymentAmount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "Name" : { }
    } ]
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       directDebitsGet, 
       apiVersion, 
       nameOf(directDebitsGet),
       "GET", 
       "/direct-debits", 
       "Get Direct Debits",
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
    "DirectDebit" : [ {
      "SupplementaryData" : { },
      "PreviousPaymentDateTime" : { },
      "AccountId" : { },
      "MandateIdentification" : { },
      "DirectDebitStatusCode" : { },
      "Frequency" : "Frequency",
      "DirectDebitId" : { },
      "PreviousPaymentAmount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "Name" : { }
    }, {
      "SupplementaryData" : { },
      "PreviousPaymentDateTime" : { },
      "AccountId" : { },
      "MandateIdentification" : { },
      "DirectDebitStatusCode" : { },
      "Frequency" : "Frequency",
      "DirectDebitId" : { },
      "PreviousPaymentAmount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "Name" : { }
    } ]
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Direct Debits") :: apiTagMockedData :: Nil
     )

     lazy val directDebitsGet : OBPEndpoint = {
       case "direct-debits" :: Nil JsonGet _ => {
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
    "DirectDebit" : [ {
      "SupplementaryData" : { },
      "PreviousPaymentDateTime" : { },
      "AccountId" : { },
      "MandateIdentification" : { },
      "DirectDebitStatusCode" : { },
      "Frequency" : "Frequency",
      "DirectDebitId" : { },
      "PreviousPaymentAmount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "Name" : { }
    }, {
      "SupplementaryData" : { },
      "PreviousPaymentDateTime" : { },
      "AccountId" : { },
      "MandateIdentification" : { },
      "DirectDebitStatusCode" : { },
      "Frequency" : "Frequency",
      "DirectDebitId" : { },
      "PreviousPaymentAmount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "Name" : { }
    } ]
  }
}"""), callContext)
           }
         }
       }

}



