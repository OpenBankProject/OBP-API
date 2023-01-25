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

object APIMethods_FilePaymentsApi extends RestHelper {
    val apiVersion =  ApiCollector.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      filePaymentsFilePaymentIdGet ::
      filePaymentsFilePaymentIdPaymentDetailsGet ::
      filePaymentsFilePaymentIdReportFileGet ::
      filePaymentsPost ::
      Nil

            
     resourceDocs += ResourceDoc(
       filePaymentsFilePaymentIdGet, 
       apiVersion, 
       nameOf(filePaymentsFilePaymentIdGet),
       "GET", 
       "/file-payments/FILE_PAYMENT_ID", 
       "Get File Payments by FilePaymentId",
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
    "Status" : "InitiationCompleted",
    "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Debtor" : {
      "Name" : "Name"
    },
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
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
    "FilePaymentId" : "FilePaymentId",
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
       ApiTag("File Payments") :: apiTagMockedData :: Nil
     )

     lazy val filePaymentsFilePaymentIdGet : OBPEndpoint = {
       case "file-payments" :: filePaymentId :: Nil JsonGet _ => {
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
    "Status" : "InitiationCompleted",
    "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Debtor" : {
      "Name" : "Name"
    },
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
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
    "FilePaymentId" : "FilePaymentId",
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
       filePaymentsFilePaymentIdPaymentDetailsGet, 
       apiVersion, 
       nameOf(filePaymentsFilePaymentIdPaymentDetailsGet),
       "GET", 
       "/file-payments/FILE_PAYMENT_ID/payment-details", 
       "Get File Payment Details by FilePaymentId",
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
    "PaymentStatus" : [ {
      "PaymentTransactionId" : "PaymentTransactionId",
      "Status" : "Accepted",
      "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
      "StatusDetail" : {
        "Status" : "Status",
        "LocalInstrument" : { },
        "StatusReason" : "Cancelled",
        "StatusReasonDescription" : "StatusReasonDescription"
      }
    }, {
      "PaymentTransactionId" : "PaymentTransactionId",
      "Status" : "Accepted",
      "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
      "StatusDetail" : {
        "Status" : "Status",
        "LocalInstrument" : { },
        "StatusReason" : "Cancelled",
        "StatusReasonDescription" : "StatusReasonDescription"
      }
    } ]
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("File Payments") :: apiTagMockedData :: Nil
     )

     lazy val filePaymentsFilePaymentIdPaymentDetailsGet : OBPEndpoint = {
       case "file-payments" :: filePaymentId:: "payment-details" :: Nil JsonGet _ => {
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
    "PaymentStatus" : [ {
      "PaymentTransactionId" : "PaymentTransactionId",
      "Status" : "Accepted",
      "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
      "StatusDetail" : {
        "Status" : "Status",
        "LocalInstrument" : { },
        "StatusReason" : "Cancelled",
        "StatusReasonDescription" : "StatusReasonDescription"
      }
    }, {
      "PaymentTransactionId" : "PaymentTransactionId",
      "Status" : "Accepted",
      "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
      "StatusDetail" : {
        "Status" : "Status",
        "LocalInstrument" : { },
        "StatusReason" : "Cancelled",
        "StatusReasonDescription" : "StatusReasonDescription"
      }
    } ]
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       filePaymentsFilePaymentIdReportFileGet, 
       apiVersion, 
       nameOf(filePaymentsFilePaymentIdReportFileGet),
       "GET", 
       "/file-payments/FILE_PAYMENT_ID/report-file", 
       "Get File Payments Report File by FilePaymentId",
       s"""${mockedDataText(true)}
            
            """,
       json.parse(""""""),
       json.parse("""{ }"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("File Payments") :: apiTagMockedData :: Nil
     )

     lazy val filePaymentsFilePaymentIdReportFileGet : OBPEndpoint = {
       case "file-payments" :: filePaymentId:: "report-file" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{ }"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       filePaymentsPost, 
       apiVersion, 
       nameOf(filePaymentsPost),
       "POST", 
       "/file-payments", 
       "Create File Payments",
       s"""${mockedDataText(true)}
            
            """,
       json.parse("""{
  "Data" : {
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
    "Status" : "InitiationCompleted",
    "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Debtor" : {
      "Name" : "Name"
    },
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
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
    "FilePaymentId" : "FilePaymentId",
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
       ApiTag("File Payments") :: apiTagMockedData :: Nil
     )

     lazy val filePaymentsPost : OBPEndpoint = {
       case "file-payments" :: Nil JsonPost _ => {
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
    "Status" : "InitiationCompleted",
    "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Debtor" : {
      "Name" : "Name"
    },
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
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
    "FilePaymentId" : "FilePaymentId",
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



