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

object APIMethods_FundsConfirmationsApi extends RestHelperX {
    val apiVersion = OBP_UKOpenBanking_310.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      createFundsConfirmationConsents ::
      createFundsConfirmations ::
      deleteFundsConfirmationConsentsConsentId ::
      getFundsConfirmationConsentsConsentId ::
      Nil

            
     resourceDocs += ResourceDoc(
       createFundsConfirmationConsents, 
       apiVersion, 
       nameOf(createFundsConfirmationConsents),
       "POST", 
       "/funds-confirmation-consents", 
       "Create Funds Confirmation Consent",
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
    "Status" : { },
    "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
    "DebtorAccount" : {
      "SecondaryIdentification" : "SecondaryIdentification",
      "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
      "Identification" : "Identification",
      "Name" : "Name"
    },
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExpirationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ConsentId" : "ConsentId"
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("Funds Confirmations") :: apiTagMockedData :: Nil
     )

     lazy val createFundsConfirmationConsents : OBPEndpoint = {
       case "funds-confirmation-consents" :: Nil JsonPost _ => {
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
    "Status" : { },
    "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
    "DebtorAccount" : {
      "SecondaryIdentification" : "SecondaryIdentification",
      "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
      "Identification" : "Identification",
      "Name" : "Name"
    },
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExpirationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ConsentId" : "ConsentId"
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       createFundsConfirmations, 
       apiVersion, 
       nameOf(createFundsConfirmations),
       "POST", 
       "/funds-confirmations", 
       "Create Funds Confirmation",
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
    "FundsConfirmationId" : "FundsConfirmationId",
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Reference" : "Reference",
    "FundsAvailable" : true,
    "ConsentId" : "ConsentId",
    "InstructedAmount" : {
      "Amount" : { },
      "Currency" : "Currency"
    }
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("Funds Confirmations") :: apiTagMockedData :: Nil
     )

     lazy val createFundsConfirmations : OBPEndpoint = {
       case "funds-confirmations" :: Nil JsonPost _ => {
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
    "FundsConfirmationId" : "FundsConfirmationId",
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Reference" : "Reference",
    "FundsAvailable" : true,
    "ConsentId" : "ConsentId",
    "InstructedAmount" : {
      "Amount" : { },
      "Currency" : "Currency"
    }
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       deleteFundsConfirmationConsentsConsentId, 
       apiVersion, 
       nameOf(deleteFundsConfirmationConsentsConsentId),
       "DELETE", 
       "/funds-confirmation-consents/CONSENTID", 
       "Delete Funds Confirmation Consent",
       s"""${mockedDataText(true)}
""", 
       json.parse(""""""),
       json.parse(""""""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("Funds Confirmations") :: apiTagMockedData :: Nil
     )

     lazy val deleteFundsConfirmationConsentsConsentId : OBPEndpoint = {
       case "funds-confirmation-consents" :: consentid :: Nil JsonDelete _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getFundsConfirmationConsentsConsentId, 
       apiVersion, 
       nameOf(getFundsConfirmationConsentsConsentId),
       "GET", 
       "/funds-confirmation-consents/CONSENTID", 
       "Get Funds Confirmation Consent",
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
    "Status" : { },
    "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
    "DebtorAccount" : {
      "SecondaryIdentification" : "SecondaryIdentification",
      "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
      "Identification" : "Identification",
      "Name" : "Name"
    },
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExpirationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ConsentId" : "ConsentId"
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("Funds Confirmations") :: apiTagMockedData :: Nil
     )

     lazy val getFundsConfirmationConsentsConsentId : OBPEndpoint = {
       case "funds-confirmation-consents" :: consentid :: Nil JsonGet _ => {
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
    "Status" : { },
    "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
    "DebtorAccount" : {
      "SecondaryIdentification" : "SecondaryIdentification",
      "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
      "Identification" : "Identification",
      "Name" : "Name"
    },
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExpirationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ConsentId" : "ConsentId"
  }
}"""), callContext)
           }
         }
       }

}



