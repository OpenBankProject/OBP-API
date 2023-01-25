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

object APIMethods_OffersApi extends RestHelper {
    val apiVersion =  ApiCollector.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      accountsAccountIdOffersGet ::
      offersGet ::
      Nil

            
     resourceDocs += ResourceDoc(
       accountsAccountIdOffersGet, 
       apiVersion, 
       nameOf(accountsAccountIdOffersGet),
       "GET", 
       "/accounts/ACCOUNT_ID/offers", 
       "Get Accounts Offers by AccountId",
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
    "Offer" : [ {
      "OfferId" : "OfferId",
      "AccountId" : { },
      "Description" : "Description",
      "StartDateTime" : "2000-01-23T04:56:07.000+00:00",
      "EndDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Rate" : "Rate",
      "Amount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "Fee" : { },
      "Value" : 0,
      "Term" : "Term",
      "URL" : "http://example.com/aeiou",
      "OfferType" : "BalanceTransfer"
    }, {
      "OfferId" : "OfferId",
      "AccountId" : { },
      "Description" : "Description",
      "StartDateTime" : "2000-01-23T04:56:07.000+00:00",
      "EndDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Rate" : "Rate",
      "Amount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "Fee" : { },
      "Value" : 0,
      "Term" : "Term",
      "URL" : "http://example.com/aeiou",
      "OfferType" : "BalanceTransfer"
    } ]
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Offers") :: apiTagMockedData :: Nil
     )

     lazy val accountsAccountIdOffersGet : OBPEndpoint = {
       case "accounts" :: accountId:: "offers" :: Nil JsonGet _ => {
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
    "Offer" : [ {
      "OfferId" : "OfferId",
      "AccountId" : { },
      "Description" : "Description",
      "StartDateTime" : "2000-01-23T04:56:07.000+00:00",
      "EndDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Rate" : "Rate",
      "Amount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "Fee" : { },
      "Value" : 0,
      "Term" : "Term",
      "URL" : "http://example.com/aeiou",
      "OfferType" : "BalanceTransfer"
    }, {
      "OfferId" : "OfferId",
      "AccountId" : { },
      "Description" : "Description",
      "StartDateTime" : "2000-01-23T04:56:07.000+00:00",
      "EndDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Rate" : "Rate",
      "Amount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "Fee" : { },
      "Value" : 0,
      "Term" : "Term",
      "URL" : "http://example.com/aeiou",
      "OfferType" : "BalanceTransfer"
    } ]
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       offersGet, 
       apiVersion, 
       nameOf(offersGet),
       "GET", 
       "/offers", 
       "Get Offers",
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
    "Offer" : [ {
      "OfferId" : "OfferId",
      "AccountId" : { },
      "Description" : "Description",
      "StartDateTime" : "2000-01-23T04:56:07.000+00:00",
      "EndDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Rate" : "Rate",
      "Amount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "Fee" : { },
      "Value" : 0,
      "Term" : "Term",
      "URL" : "http://example.com/aeiou",
      "OfferType" : "BalanceTransfer"
    }, {
      "OfferId" : "OfferId",
      "AccountId" : { },
      "Description" : "Description",
      "StartDateTime" : "2000-01-23T04:56:07.000+00:00",
      "EndDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Rate" : "Rate",
      "Amount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "Fee" : { },
      "Value" : 0,
      "Term" : "Term",
      "URL" : "http://example.com/aeiou",
      "OfferType" : "BalanceTransfer"
    } ]
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Offers") :: apiTagMockedData :: Nil
     )

     lazy val offersGet : OBPEndpoint = {
       case "offers" :: Nil JsonGet _ => {
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
    "Offer" : [ {
      "OfferId" : "OfferId",
      "AccountId" : { },
      "Description" : "Description",
      "StartDateTime" : "2000-01-23T04:56:07.000+00:00",
      "EndDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Rate" : "Rate",
      "Amount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "Fee" : { },
      "Value" : 0,
      "Term" : "Term",
      "URL" : "http://example.com/aeiou",
      "OfferType" : "BalanceTransfer"
    }, {
      "OfferId" : "OfferId",
      "AccountId" : { },
      "Description" : "Description",
      "StartDateTime" : "2000-01-23T04:56:07.000+00:00",
      "EndDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Rate" : "Rate",
      "Amount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "Fee" : { },
      "Value" : 0,
      "Term" : "Term",
      "URL" : "http://example.com/aeiou",
      "OfferType" : "BalanceTransfer"
    } ]
  }
}"""), callContext)
           }
         }
       }

}



