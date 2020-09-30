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

object APIMethods_ScheduledPaymentsApi extends RestHelper {
    val apiVersion = OBP_UKOpenBanking_310.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      getAccountsAccountIdScheduledPayments ::
      getScheduledPayments ::
      Nil

            
     resourceDocs += ResourceDoc(
       getAccountsAccountIdScheduledPayments, 
       apiVersion, 
       nameOf(getAccountsAccountIdScheduledPayments),
       "GET", 
       "/accounts/ACCOUNTID/scheduled-payments", 
       "Get Scheduled Payments",
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
    "ScheduledPayment" : [ {
      "CreditorAgent" : {
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification"
      },
      "AccountId" : { },
      "Reference" : "Reference",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "ScheduledPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "InstructedAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "ScheduledPaymentId" : "ScheduledPaymentId",
      "ScheduledType" : { }
    }, {
      "CreditorAgent" : {
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification"
      },
      "AccountId" : { },
      "Reference" : "Reference",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "ScheduledPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "InstructedAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "ScheduledPaymentId" : "ScheduledPaymentId",
      "ScheduledType" : { }
    } ]
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Scheduled Payments") :: apiTagMockedData :: Nil
     )

     lazy val getAccountsAccountIdScheduledPayments : OBPEndpoint = {
       case "accounts" :: accountid:: "scheduled-payments" :: Nil JsonGet _ => {
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
    "ScheduledPayment" : [ {
      "CreditorAgent" : {
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification"
      },
      "AccountId" : { },
      "Reference" : "Reference",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "ScheduledPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "InstructedAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "ScheduledPaymentId" : "ScheduledPaymentId",
      "ScheduledType" : { }
    }, {
      "CreditorAgent" : {
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification"
      },
      "AccountId" : { },
      "Reference" : "Reference",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "ScheduledPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "InstructedAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "ScheduledPaymentId" : "ScheduledPaymentId",
      "ScheduledType" : { }
    } ]
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getScheduledPayments, 
       apiVersion, 
       nameOf(getScheduledPayments),
       "GET", 
       "/scheduled-payments", 
       "Get Scheduled Payments",
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
    "ScheduledPayment" : [ {
      "CreditorAgent" : {
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification"
      },
      "AccountId" : { },
      "Reference" : "Reference",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "ScheduledPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "InstructedAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "ScheduledPaymentId" : "ScheduledPaymentId",
      "ScheduledType" : { }
    }, {
      "CreditorAgent" : {
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification"
      },
      "AccountId" : { },
      "Reference" : "Reference",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "ScheduledPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "InstructedAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "ScheduledPaymentId" : "ScheduledPaymentId",
      "ScheduledType" : { }
    } ]
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Scheduled Payments") :: apiTagMockedData :: Nil
     )

     lazy val getScheduledPayments : OBPEndpoint = {
       case "scheduled-payments" :: Nil JsonGet _ => {
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
    "ScheduledPayment" : [ {
      "CreditorAgent" : {
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification"
      },
      "AccountId" : { },
      "Reference" : "Reference",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "ScheduledPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "InstructedAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "ScheduledPaymentId" : "ScheduledPaymentId",
      "ScheduledType" : { }
    }, {
      "CreditorAgent" : {
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification"
      },
      "AccountId" : { },
      "Reference" : "Reference",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "ScheduledPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "InstructedAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "ScheduledPaymentId" : "ScheduledPaymentId",
      "ScheduledType" : { }
    } ]
  }
}"""), callContext)
           }
         }
       }

}



