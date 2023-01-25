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

object APIMethods_DomesticFutureDatedPaymentsApi extends RestHelper {
    val apiVersion =  ApiCollector.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      domesticFutureDatedPaymentsDomesticFutureDatedPaymentIdGet ::
      domesticFutureDatedPaymentsDomesticFutureDatedPaymentIdPatch ::
      domesticFutureDatedPaymentsDomesticFutureDatedPaymentIdPaymentDetailsGet ::
      domesticFutureDatedPaymentsPost ::
      Nil

            
     resourceDocs += ResourceDoc(
       domesticFutureDatedPaymentsDomesticFutureDatedPaymentIdGet, 
       apiVersion, 
       nameOf(domesticFutureDatedPaymentsDomesticFutureDatedPaymentIdGet),
       "GET", 
       "/domestic-future-dated-payments/DOMESTIC_FUTURE_DATED_PAYMENT_ID", 
       "Get Domestic Future Dated Payments by DomesticFutureDatedPaymentId",
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
    "Status" : "Cancelled",
    "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Debtor" : {
      "Name" : "Name"
    },
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExpectedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Refund" : {
      "Account" : {
        "Identification" : { },
        "SchemeName" : { },
        "Name" : "Name"
      }
    },
    "DomesticFutureDatedPaymentId" : "DomesticFutureDatedPaymentId",
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
      "SupplementaryData" : { },
      "LocalInstrument" : { },
      "DebtorAccount" : {
        "Name" : "Name"
      },
      "RemittanceInformation" : {
        "RemittanceDescription" : "RemittanceDescription",
        "Reference" : "Reference"
      },
      "EndToEndIdentification" : "EndToEndIdentification",
      "InstructionIdentification" : "InstructionIdentification",
      "CreditorAccount" : {
        "Identification" : { },
        "SchemeName" : { },
        "Name" : "Name"
      },
      "CreditorPostalAddress" : {
        "CountrySubDivision" : { },
        "StreetName" : { },
        "Department" : { },
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : { },
        "TownName" : { },
        "Country" : { },
        "SubDepartment" : { },
        "AddressType" : { },
        "PostCode" : { }
      },
      "InstructedAmount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "RequestedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00"
    },
    "ExpectedSettlementDateTime" : "2000-01-23T04:56:07.000+00:00"
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Domestic Future Dated Payments") :: apiTagMockedData :: Nil
     )

     lazy val domesticFutureDatedPaymentsDomesticFutureDatedPaymentIdGet : OBPEndpoint = {
       case "domestic-future-dated-payments" :: domesticFutureDatedPaymentId :: Nil JsonGet _ => {
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
    "Status" : "Cancelled",
    "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Debtor" : {
      "Name" : "Name"
    },
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExpectedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Refund" : {
      "Account" : {
        "Identification" : { },
        "SchemeName" : { },
        "Name" : "Name"
      }
    },
    "DomesticFutureDatedPaymentId" : "DomesticFutureDatedPaymentId",
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
      "SupplementaryData" : { },
      "LocalInstrument" : { },
      "DebtorAccount" : {
        "Name" : "Name"
      },
      "RemittanceInformation" : {
        "RemittanceDescription" : "RemittanceDescription",
        "Reference" : "Reference"
      },
      "EndToEndIdentification" : "EndToEndIdentification",
      "InstructionIdentification" : "InstructionIdentification",
      "CreditorAccount" : {
        "Identification" : { },
        "SchemeName" : { },
        "Name" : "Name"
      },
      "CreditorPostalAddress" : {
        "CountrySubDivision" : { },
        "StreetName" : { },
        "Department" : { },
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : { },
        "TownName" : { },
        "Country" : { },
        "SubDepartment" : { },
        "AddressType" : { },
        "PostCode" : { }
      },
      "InstructedAmount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "RequestedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00"
    },
    "ExpectedSettlementDateTime" : "2000-01-23T04:56:07.000+00:00"
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       domesticFutureDatedPaymentsDomesticFutureDatedPaymentIdPatch, 
       apiVersion, 
       nameOf(domesticFutureDatedPaymentsDomesticFutureDatedPaymentIdPatch),
       "PATCH", 
       "/domestic-future-dated-payments/DOMESTIC_FUTURE_DATED_PAYMENT_ID", 
       "Patch Domestic Future Dated Payments by DomesticFutureDatedPaymentId",
       s"""${mockedDataText(true)}
            
            """,
       json.parse("""{
  "Data" : {
    "Status" : "RejectedCancellationRequest",
    "ConsentId" : "ConsentId"
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
    "Status" : "Cancelled",
    "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Debtor" : {
      "Name" : "Name"
    },
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExpectedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Refund" : {
      "Account" : {
        "Identification" : { },
        "SchemeName" : { },
        "Name" : "Name"
      }
    },
    "DomesticFutureDatedPaymentId" : "DomesticFutureDatedPaymentId",
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
      "SupplementaryData" : { },
      "LocalInstrument" : { },
      "DebtorAccount" : {
        "Name" : "Name"
      },
      "RemittanceInformation" : {
        "RemittanceDescription" : "RemittanceDescription",
        "Reference" : "Reference"
      },
      "EndToEndIdentification" : "EndToEndIdentification",
      "InstructionIdentification" : "InstructionIdentification",
      "CreditorAccount" : {
        "Identification" : { },
        "SchemeName" : { },
        "Name" : "Name"
      },
      "CreditorPostalAddress" : {
        "CountrySubDivision" : { },
        "StreetName" : { },
        "Department" : { },
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : { },
        "TownName" : { },
        "Country" : { },
        "SubDepartment" : { },
        "AddressType" : { },
        "PostCode" : { }
      },
      "InstructedAmount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "RequestedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00"
    },
    "ExpectedSettlementDateTime" : "2000-01-23T04:56:07.000+00:00"
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Domestic Future Dated Payments") :: apiTagMockedData :: Nil
     )

     lazy val domesticFutureDatedPaymentsDomesticFutureDatedPaymentIdPatch : OBPEndpoint = {
       case "domestic-future-dated-payments" :: domesticFutureDatedPaymentId :: Nil JsonPatch _ => {
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
    "Status" : "Cancelled",
    "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Debtor" : {
      "Name" : "Name"
    },
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExpectedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Refund" : {
      "Account" : {
        "Identification" : { },
        "SchemeName" : { },
        "Name" : "Name"
      }
    },
    "DomesticFutureDatedPaymentId" : "DomesticFutureDatedPaymentId",
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
      "SupplementaryData" : { },
      "LocalInstrument" : { },
      "DebtorAccount" : {
        "Name" : "Name"
      },
      "RemittanceInformation" : {
        "RemittanceDescription" : "RemittanceDescription",
        "Reference" : "Reference"
      },
      "EndToEndIdentification" : "EndToEndIdentification",
      "InstructionIdentification" : "InstructionIdentification",
      "CreditorAccount" : {
        "Identification" : { },
        "SchemeName" : { },
        "Name" : "Name"
      },
      "CreditorPostalAddress" : {
        "CountrySubDivision" : { },
        "StreetName" : { },
        "Department" : { },
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : { },
        "TownName" : { },
        "Country" : { },
        "SubDepartment" : { },
        "AddressType" : { },
        "PostCode" : { }
      },
      "InstructedAmount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "RequestedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00"
    },
    "ExpectedSettlementDateTime" : "2000-01-23T04:56:07.000+00:00"
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       domesticFutureDatedPaymentsDomesticFutureDatedPaymentIdPaymentDetailsGet, 
       apiVersion, 
       nameOf(domesticFutureDatedPaymentsDomesticFutureDatedPaymentIdPaymentDetailsGet),
       "GET", 
       "/domestic-future-dated-payments/DOMESTIC_FUTURE_DATED_PAYMENT_ID/payment-details", 
       "Get Domestic Future Dated Payment Details by DomesticFutureDatedPaymentId",
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
       ApiTag("Domestic Future Dated Payments") :: apiTagMockedData :: Nil
     )

     lazy val domesticFutureDatedPaymentsDomesticFutureDatedPaymentIdPaymentDetailsGet : OBPEndpoint = {
       case "domestic-future-dated-payments" :: domesticFutureDatedPaymentId:: "payment-details" :: Nil JsonGet _ => {
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
       domesticFutureDatedPaymentsPost, 
       apiVersion, 
       nameOf(domesticFutureDatedPaymentsPost),
       "POST", 
       "/domestic-future-dated-payments", 
       "Create Domestic Future Dated Payments",
       s"""${mockedDataText(true)}
            
            """,
       json.parse("""{
  "Risk" : {
    "DeliveryAddress" : {
      "CountrySubDivision" : [ "CountrySubDivision", "CountrySubDivision" ],
      "AddressLine" : [ "AddressLine", "AddressLine" ],
      "Country" : "Country"
    },
    "PaymentContextCode" : "BillPayment",
    "MerchantCategoryCode" : "MerchantCategoryCode",
    "MerchantCustomerIdentification" : "MerchantCustomerIdentification"
  },
  "Data" : {
    "ConsentId" : "ConsentId",
    "Initiation" : {
      "SupplementaryData" : { },
      "LocalInstrument" : { },
      "DebtorAccount" : {
        "Name" : "Name"
      },
      "RemittanceInformation" : {
        "RemittanceDescription" : "RemittanceDescription",
        "Reference" : "Reference"
      },
      "EndToEndIdentification" : "EndToEndIdentification",
      "InstructionIdentification" : "InstructionIdentification",
      "CreditorAccount" : {
        "Identification" : { },
        "SchemeName" : { },
        "Name" : "Name"
      },
      "CreditorPostalAddress" : {
        "CountrySubDivision" : { },
        "StreetName" : { },
        "Department" : { },
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : { },
        "TownName" : { },
        "Country" : { },
        "SubDepartment" : { },
        "AddressType" : { },
        "PostCode" : { }
      },
      "InstructedAmount" : {
        "Amount" : { },
        "Currency" : { }
      },
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
    "Status" : "Cancelled",
    "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Debtor" : {
      "Name" : "Name"
    },
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExpectedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Refund" : {
      "Account" : {
        "Identification" : { },
        "SchemeName" : { },
        "Name" : "Name"
      }
    },
    "DomesticFutureDatedPaymentId" : "DomesticFutureDatedPaymentId",
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
      "SupplementaryData" : { },
      "LocalInstrument" : { },
      "DebtorAccount" : {
        "Name" : "Name"
      },
      "RemittanceInformation" : {
        "RemittanceDescription" : "RemittanceDescription",
        "Reference" : "Reference"
      },
      "EndToEndIdentification" : "EndToEndIdentification",
      "InstructionIdentification" : "InstructionIdentification",
      "CreditorAccount" : {
        "Identification" : { },
        "SchemeName" : { },
        "Name" : "Name"
      },
      "CreditorPostalAddress" : {
        "CountrySubDivision" : { },
        "StreetName" : { },
        "Department" : { },
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : { },
        "TownName" : { },
        "Country" : { },
        "SubDepartment" : { },
        "AddressType" : { },
        "PostCode" : { }
      },
      "InstructedAmount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "RequestedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00"
    },
    "ExpectedSettlementDateTime" : "2000-01-23T04:56:07.000+00:00"
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Domestic Future Dated Payments") :: apiTagMockedData :: Nil
     )

     lazy val domesticFutureDatedPaymentsPost : OBPEndpoint = {
       case "domestic-future-dated-payments" :: Nil JsonPost _ => {
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
    "Status" : "Cancelled",
    "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Debtor" : {
      "Name" : "Name"
    },
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExpectedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Refund" : {
      "Account" : {
        "Identification" : { },
        "SchemeName" : { },
        "Name" : "Name"
      }
    },
    "DomesticFutureDatedPaymentId" : "DomesticFutureDatedPaymentId",
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
      "SupplementaryData" : { },
      "LocalInstrument" : { },
      "DebtorAccount" : {
        "Name" : "Name"
      },
      "RemittanceInformation" : {
        "RemittanceDescription" : "RemittanceDescription",
        "Reference" : "Reference"
      },
      "EndToEndIdentification" : "EndToEndIdentification",
      "InstructionIdentification" : "InstructionIdentification",
      "CreditorAccount" : {
        "Identification" : { },
        "SchemeName" : { },
        "Name" : "Name"
      },
      "CreditorPostalAddress" : {
        "CountrySubDivision" : { },
        "StreetName" : { },
        "Department" : { },
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : { },
        "TownName" : { },
        "Country" : { },
        "SubDepartment" : { },
        "AddressType" : { },
        "PostCode" : { }
      },
      "InstructedAmount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "RequestedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00"
    },
    "ExpectedSettlementDateTime" : "2000-01-23T04:56:07.000+00:00"
  }
}"""), callContext)
           }
         }
       }

}



