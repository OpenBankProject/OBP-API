package code.api.UKOpenBanking.v3_1_0

import code.api.berlin.group.v1_3.JvalueCaseClass
import code.api.util.APIUtil._
import code.api.util.ApiTag
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.common.Full
import code.api.RestHelperX
import net.liftweb.json
import net.liftweb.json._

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global

object APIMethods_OffersApi extends RestHelperX {
    val apiVersion = OBP_UKOpenBanking_310.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      getAccountsAccountIdOffers ::
      getOffers ::
      Nil

            
     resourceDocs += ResourceDoc(
       getAccountsAccountIdOffers, 
       apiVersion, 
       nameOf(getAccountsAccountIdOffers),
       "GET", 
       "/accounts/ACCOUNTID/offers", 
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
    "Self" : "http://example.com/aeiou",
    "First" : "http://example.com/aeiou"
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
        "Currency" : "Currency"
      },
      "Fee" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "Value" : 0,
      "Term" : "Term",
      "URL" : "URL",
      "OfferType" : { }
    }, {
      "OfferId" : "OfferId",
      "AccountId" : { },
      "Description" : "Description",
      "StartDateTime" : "2000-01-23T04:56:07.000+00:00",
      "EndDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Rate" : "Rate",
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "Fee" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "Value" : 0,
      "Term" : "Term",
      "URL" : "URL",
      "OfferType" : { }
    } ]
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("Offers") :: apiTagMockedData :: Nil
     )

     lazy val getAccountsAccountIdOffers : OBPEndpoint = {
       case "accounts" :: accountid:: "offers" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc)
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
    "Offer" : [ {
      "OfferId" : "OfferId",
      "AccountId" : { },
      "Description" : "Description",
      "StartDateTime" : "2000-01-23T04:56:07.000+00:00",
      "EndDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Rate" : "Rate",
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "Fee" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "Value" : 0,
      "Term" : "Term",
      "URL" : "URL",
      "OfferType" : { }
    }, {
      "OfferId" : "OfferId",
      "AccountId" : { },
      "Description" : "Description",
      "StartDateTime" : "2000-01-23T04:56:07.000+00:00",
      "EndDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Rate" : "Rate",
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "Fee" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "Value" : 0,
      "Term" : "Term",
      "URL" : "URL",
      "OfferType" : { }
    } ]
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getOffers, 
       apiVersion, 
       nameOf(getOffers),
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
    "Self" : "http://example.com/aeiou",
    "First" : "http://example.com/aeiou"
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
        "Currency" : "Currency"
      },
      "Fee" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "Value" : 0,
      "Term" : "Term",
      "URL" : "URL",
      "OfferType" : { }
    }, {
      "OfferId" : "OfferId",
      "AccountId" : { },
      "Description" : "Description",
      "StartDateTime" : "2000-01-23T04:56:07.000+00:00",
      "EndDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Rate" : "Rate",
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "Fee" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "Value" : 0,
      "Term" : "Term",
      "URL" : "URL",
      "OfferType" : { }
    } ]
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("Offers") :: apiTagMockedData :: Nil
     )

     lazy val getOffers : OBPEndpoint = {
       case "offers" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc)
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
    "Offer" : [ {
      "OfferId" : "OfferId",
      "AccountId" : { },
      "Description" : "Description",
      "StartDateTime" : "2000-01-23T04:56:07.000+00:00",
      "EndDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Rate" : "Rate",
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "Fee" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "Value" : 0,
      "Term" : "Term",
      "URL" : "URL",
      "OfferType" : { }
    }, {
      "OfferId" : "OfferId",
      "AccountId" : { },
      "Description" : "Description",
      "StartDateTime" : "2000-01-23T04:56:07.000+00:00",
      "EndDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Rate" : "Rate",
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "Fee" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "Value" : 0,
      "Term" : "Term",
      "URL" : "URL",
      "OfferType" : { }
    } ]
  }
}"""), callContext)
           }
         }
       }

}



