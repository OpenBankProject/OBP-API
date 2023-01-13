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

object APIMethods_FilePaymentConsentsApi extends RestHelper {
    val apiVersion =  ApiCollector.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      filePaymentConsentsConsentIdFileGet ::
      filePaymentConsentsConsentIdFilePost ::
      filePaymentConsentsConsentIdGet ::
      filePaymentConsentsPost ::
      Nil

            
     resourceDocs += ResourceDoc(
       filePaymentConsentsConsentIdFileGet, 
       apiVersion, 
       nameOf(filePaymentConsentsConsentIdFileGet),
       "GET", 
       "/file-payment-consents/CONSENT_ID/file", 
       "Get File Payment Consents File by ConsentId",
       s"""${mockedDataText(true)}
            
            """,
       json.parse(""""""),
       json.parse("""{ }"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("File Payment Consents") :: apiTagMockedData :: Nil
     )

     lazy val filePaymentConsentsConsentIdFileGet : OBPEndpoint = {
       case "file-payment-consents" :: consentId:: "file" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{ }"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       filePaymentConsentsConsentIdFilePost, 
       apiVersion, 
       nameOf(filePaymentConsentsConsentIdFilePost),
       "POST", 
       "/file-payment-consents/CONSENT_ID/file", 
       "Create File Payment Consents File by ConsentId",
       s"""${mockedDataText(true)}
            
            """,
       json.parse("""{ }"""),
       json.parse(""""""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("File Payment Consents") :: apiTagMockedData :: Nil
     )

     lazy val filePaymentConsentsConsentIdFilePost : OBPEndpoint = {
       case "file-payment-consents" :: consentId:: "file" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse(""""""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       filePaymentConsentsConsentIdGet, 
       apiVersion, 
       nameOf(filePaymentConsentsConsentIdGet),
       "GET", 
       "/file-payment-consents/CONSENT_ID", 
       "Get File Payment Consents by ConsentId",
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
    "SCASupportData" : {
      "RequestedSCAExemptionType" : "BillPayment",
      "AppliedAuthenticationApproach" : "CA",
      "ReferencePaymentOrderId" : "ReferencePaymentOrderId"
    },
    "Status" : "Authorised",
    "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Debtor" : {
      "Name" : "Name"
    },
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "CutOffDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Authorisation" : {
      "CompletionDateTime" : "2000-01-23T04:56:07.000+00:00",
      "AuthorisationType" : "Single"
    },
    "Charges" : [ {
      "Type" : { },
      "Amount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "ChargeBearer" : { }
    }, {
      "Type" : { },
      "Amount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "ChargeBearer" : { }
    } ],
    "ConsentId" : "ConsentId",
    "Initiation" : {
      "FileContextFormat" : "FileContextFormat",
      "SupplementaryData" : { },
      "ControlSum" : 0.80082819046101150206595775671303272247314453125,
      "LocalInstrument" : { },
      "DebtorAccount" : {
        "Name" : "Name"
      },
      "RemittanceInformation" : {
        "RemittanceDescription" : "RemittanceDescription",
        "Reference" : "Reference"
      },
      "FileHash" : "FileHash",
      "NumberOfTransactions" : "NumberOfTransactions",
      "FileReference" : "FileReference",
      "RequestedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00"
    }
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("File Payment Consents") :: apiTagMockedData :: Nil
     )

     lazy val filePaymentConsentsConsentIdGet : OBPEndpoint = {
       case "file-payment-consents" :: consentId :: Nil JsonGet _ => {
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
    "SCASupportData" : {
      "RequestedSCAExemptionType" : "BillPayment",
      "AppliedAuthenticationApproach" : "CA",
      "ReferencePaymentOrderId" : "ReferencePaymentOrderId"
    },
    "Status" : "Authorised",
    "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Debtor" : {
      "Name" : "Name"
    },
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "CutOffDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Authorisation" : {
      "CompletionDateTime" : "2000-01-23T04:56:07.000+00:00",
      "AuthorisationType" : "Single"
    },
    "Charges" : [ {
      "Type" : { },
      "Amount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "ChargeBearer" : { }
    }, {
      "Type" : { },
      "Amount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "ChargeBearer" : { }
    } ],
    "ConsentId" : "ConsentId",
    "Initiation" : {
      "FileContextFormat" : "FileContextFormat",
      "SupplementaryData" : { },
      "ControlSum" : 0.80082819046101150206595775671303272247314453125,
      "LocalInstrument" : { },
      "DebtorAccount" : {
        "Name" : "Name"
      },
      "RemittanceInformation" : {
        "RemittanceDescription" : "RemittanceDescription",
        "Reference" : "Reference"
      },
      "FileHash" : "FileHash",
      "NumberOfTransactions" : "NumberOfTransactions",
      "FileReference" : "FileReference",
      "RequestedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00"
    }
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       filePaymentConsentsPost, 
       apiVersion, 
       nameOf(filePaymentConsentsPost),
       "POST", 
       "/file-payment-consents", 
       "Create File Payment Consents",
       s"""${mockedDataText(true)}
            
            """,
       json.parse("""{
  "Data" : {
    "SCASupportData" : {
      "RequestedSCAExemptionType" : "BillPayment",
      "AppliedAuthenticationApproach" : "CA",
      "ReferencePaymentOrderId" : "ReferencePaymentOrderId"
    },
    "Authorisation" : {
      "CompletionDateTime" : "2000-01-23T04:56:07.000+00:00",
      "AuthorisationType" : "Single"
    },
    "Initiation" : {
      "FileContextFormat" : "FileContextFormat",
      "SupplementaryData" : { },
      "ControlSum" : 0.80082819046101150206595775671303272247314453125,
      "LocalInstrument" : { },
      "DebtorAccount" : {
        "Name" : "Name"
      },
      "RemittanceInformation" : {
        "RemittanceDescription" : "RemittanceDescription",
        "Reference" : "Reference"
      },
      "FileHash" : "FileHash",
      "NumberOfTransactions" : "NumberOfTransactions",
      "FileReference" : "FileReference",
      "RequestedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00"
    }
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
    "SCASupportData" : {
      "RequestedSCAExemptionType" : "BillPayment",
      "AppliedAuthenticationApproach" : "CA",
      "ReferencePaymentOrderId" : "ReferencePaymentOrderId"
    },
    "Status" : "Authorised",
    "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Debtor" : {
      "Name" : "Name"
    },
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "CutOffDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Authorisation" : {
      "CompletionDateTime" : "2000-01-23T04:56:07.000+00:00",
      "AuthorisationType" : "Single"
    },
    "Charges" : [ {
      "Type" : { },
      "Amount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "ChargeBearer" : { }
    }, {
      "Type" : { },
      "Amount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "ChargeBearer" : { }
    } ],
    "ConsentId" : "ConsentId",
    "Initiation" : {
      "FileContextFormat" : "FileContextFormat",
      "SupplementaryData" : { },
      "ControlSum" : 0.80082819046101150206595775671303272247314453125,
      "LocalInstrument" : { },
      "DebtorAccount" : {
        "Name" : "Name"
      },
      "RemittanceInformation" : {
        "RemittanceDescription" : "RemittanceDescription",
        "Reference" : "Reference"
      },
      "FileHash" : "FileHash",
      "NumberOfTransactions" : "NumberOfTransactions",
      "FileReference" : "FileReference",
      "RequestedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00"
    }
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("File Payment Consents") :: apiTagMockedData :: Nil
     )

     lazy val filePaymentConsentsPost : OBPEndpoint = {
       case "file-payment-consents" :: Nil JsonPost _ => {
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
    "SCASupportData" : {
      "RequestedSCAExemptionType" : "BillPayment",
      "AppliedAuthenticationApproach" : "CA",
      "ReferencePaymentOrderId" : "ReferencePaymentOrderId"
    },
    "Status" : "Authorised",
    "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Debtor" : {
      "Name" : "Name"
    },
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "CutOffDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Authorisation" : {
      "CompletionDateTime" : "2000-01-23T04:56:07.000+00:00",
      "AuthorisationType" : "Single"
    },
    "Charges" : [ {
      "Type" : { },
      "Amount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "ChargeBearer" : { }
    }, {
      "Type" : { },
      "Amount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "ChargeBearer" : { }
    } ],
    "ConsentId" : "ConsentId",
    "Initiation" : {
      "FileContextFormat" : "FileContextFormat",
      "SupplementaryData" : { },
      "ControlSum" : 0.80082819046101150206595775671303272247314453125,
      "LocalInstrument" : { },
      "DebtorAccount" : {
        "Name" : "Name"
      },
      "RemittanceInformation" : {
        "RemittanceDescription" : "RemittanceDescription",
        "Reference" : "Reference"
      },
      "FileHash" : "FileHash",
      "NumberOfTransactions" : "NumberOfTransactions",
      "FileReference" : "FileReference",
      "RequestedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00"
    }
  }
}"""), callContext)
           }
         }
       }

}



