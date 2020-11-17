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

object APIMethods_StandingOrdersApi extends RestHelper {
    val apiVersion = OBP_UKOpenBanking_310.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      getAccountsAccountIdStandingOrders ::
      getStandingOrders ::
      Nil

            
     resourceDocs += ResourceDoc(
       getAccountsAccountIdStandingOrders, 
       apiVersion, 
       nameOf(getAccountsAccountIdStandingOrders),
       "GET", 
       "/accounts/ACCOUNTID/standing-orders", 
       "Get Standing Orders",
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
    "StandingOrder" : [ {
      "SupplementaryData" : { },
      "CreditorAgent" : {
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification"
      },
      "AccountId" : { },
      "StandingOrderId" : "StandingOrderId",
      "Reference" : "Reference",
      "StandingOrderStatusCode" : { },
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "FirstPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "FinalPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "FinalPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "NextPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "NextPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Frequency" : "Frequency",
      "FirstPaymentDateTime" : "2000-01-23T04:56:07.000+00:00"
    }, {
      "SupplementaryData" : { },
      "CreditorAgent" : {
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification"
      },
      "AccountId" : { },
      "StandingOrderId" : "StandingOrderId",
      "Reference" : "Reference",
      "StandingOrderStatusCode" : { },
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "FirstPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "FinalPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "FinalPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "NextPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "NextPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Frequency" : "Frequency",
      "FirstPaymentDateTime" : "2000-01-23T04:56:07.000+00:00"
    } ]
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Standing Orders") :: apiTagMockedData :: Nil
     )

     lazy val getAccountsAccountIdStandingOrders : OBPEndpoint = {
       case "accounts" :: accountid:: "standing-orders" :: Nil JsonGet _ => {
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
    "StandingOrder" : [ {
      "SupplementaryData" : { },
      "CreditorAgent" : {
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification"
      },
      "AccountId" : { },
      "StandingOrderId" : "StandingOrderId",
      "Reference" : "Reference",
      "StandingOrderStatusCode" : { },
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "FirstPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "FinalPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "FinalPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "NextPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "NextPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Frequency" : "Frequency",
      "FirstPaymentDateTime" : "2000-01-23T04:56:07.000+00:00"
    }, {
      "SupplementaryData" : { },
      "CreditorAgent" : {
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification"
      },
      "AccountId" : { },
      "StandingOrderId" : "StandingOrderId",
      "Reference" : "Reference",
      "StandingOrderStatusCode" : { },
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "FirstPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "FinalPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "FinalPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "NextPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "NextPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Frequency" : "Frequency",
      "FirstPaymentDateTime" : "2000-01-23T04:56:07.000+00:00"
    } ]
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getStandingOrders, 
       apiVersion, 
       nameOf(getStandingOrders),
       "GET", 
       "/standing-orders", 
       "Get Standing Orders",
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
    "StandingOrder" : [ {
      "SupplementaryData" : { },
      "CreditorAgent" : {
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification"
      },
      "AccountId" : { },
      "StandingOrderId" : "StandingOrderId",
      "Reference" : "Reference",
      "StandingOrderStatusCode" : { },
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "FirstPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "FinalPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "FinalPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "NextPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "NextPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Frequency" : "Frequency",
      "FirstPaymentDateTime" : "2000-01-23T04:56:07.000+00:00"
    }, {
      "SupplementaryData" : { },
      "CreditorAgent" : {
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification"
      },
      "AccountId" : { },
      "StandingOrderId" : "StandingOrderId",
      "Reference" : "Reference",
      "StandingOrderStatusCode" : { },
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "FirstPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "FinalPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "FinalPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "NextPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "NextPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Frequency" : "Frequency",
      "FirstPaymentDateTime" : "2000-01-23T04:56:07.000+00:00"
    } ]
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Standing Orders") :: apiTagMockedData :: Nil
     )

     lazy val getStandingOrders : OBPEndpoint = {
       case "standing-orders" :: Nil JsonGet _ => {
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
    "StandingOrder" : [ {
      "SupplementaryData" : { },
      "CreditorAgent" : {
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification"
      },
      "AccountId" : { },
      "StandingOrderId" : "StandingOrderId",
      "Reference" : "Reference",
      "StandingOrderStatusCode" : { },
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "FirstPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "FinalPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "FinalPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "NextPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "NextPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Frequency" : "Frequency",
      "FirstPaymentDateTime" : "2000-01-23T04:56:07.000+00:00"
    }, {
      "SupplementaryData" : { },
      "CreditorAgent" : {
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification"
      },
      "AccountId" : { },
      "StandingOrderId" : "StandingOrderId",
      "Reference" : "Reference",
      "StandingOrderStatusCode" : { },
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "FirstPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "FinalPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "FinalPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "NextPaymentAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "NextPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Frequency" : "Frequency",
      "FirstPaymentDateTime" : "2000-01-23T04:56:07.000+00:00"
    } ]
  }
}"""), callContext)
           }
         }
       }

}



