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

object APIMethods_FutureDatedPaymentsApi extends RestHelper {
    val apiVersion =  ApiCollector.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      accountsAccountIdFutureDatedPaymentsGet ::
      futureDatedPaymentsGet ::
      Nil

            
     resourceDocs += ResourceDoc(
       accountsAccountIdFutureDatedPaymentsGet, 
       apiVersion, 
       nameOf(accountsAccountIdFutureDatedPaymentsGet),
       "GET", 
       "/accounts/ACCOUNT_ID/future-dated-payments", 
       "Get Accounts Future Dated Payments by AccountId",
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
    "FutureDatedPayment" : [ { }, { } ]
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Future Dated Payments") :: apiTagMockedData :: Nil
     )

     lazy val accountsAccountIdFutureDatedPaymentsGet : OBPEndpoint = {
       case "accounts" :: accountId:: "future-dated-payments" :: Nil JsonGet _ => {
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
    "FutureDatedPayment" : [ { }, { } ]
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       futureDatedPaymentsGet, 
       apiVersion, 
       nameOf(futureDatedPaymentsGet),
       "GET", 
       "/future-dated-payments", 
       "Get Future Dated Payments",
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
    "FutureDatedPayment" : [ { }, { } ]
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Future Dated Payments") :: apiTagMockedData :: Nil
     )

     lazy val futureDatedPaymentsGet : OBPEndpoint = {
       case "future-dated-payments" :: Nil JsonGet _ => {
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
    "FutureDatedPayment" : [ { }, { } ]
  }
}"""), callContext)
           }
         }
       }

}



