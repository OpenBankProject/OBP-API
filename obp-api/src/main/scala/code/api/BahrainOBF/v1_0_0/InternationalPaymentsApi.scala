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

object APIMethods_InternationalPaymentsApi extends RestHelper {
    val apiVersion =  ApiCollector.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      internationalPaymentsInternationalPaymentIdGet ::
      internationalPaymentsInternationalPaymentIdPaymentDetailsGet ::
      internationalPaymentsPost ::
      Nil

            
     resourceDocs += ResourceDoc(
       internationalPaymentsInternationalPaymentIdGet, 
       apiVersion, 
       nameOf(internationalPaymentsInternationalPaymentIdGet),
       "GET", 
       "/international-payments/INTERNATIONAL_PAYMENT_ID", 
       "Get International Payments by InternationalPaymentId",
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
    "Status" : "AcceptedCreditSettlementCompleted",
    "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Debtor" : {
      "Name" : "Name"
    },
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExpectedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExchangeRateInformation" : {
      "ExchangeRate" : 0.80082819046101150206595775671303272247314453125,
      "ExpirationDateTime" : "2000-01-23T04:56:07.000+00:00",
      "RateType" : "Actual",
      "ContractIdentification" : "ContractIdentification",
      "UnitCurrency" : "UnitCurrency"
    },
    "Refund" : {
      "Account" : {
        "Identification" : { },
        "SchemeName" : { },
        "Name" : "Name"
      },
      "Agent" : {
        "PostalAddress" : {
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
        "Identification" : { },
        "SchemeName" : { },
        "Name" : { }
      },
      "Creditor" : {
        "PostalAddress" : {
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
        "Name" : "Name"
      }
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
    "InternationalPaymentId" : "InternationalPaymentId",
    "ConsentId" : "ConsentId",
    "Initiation" : {
      "SupplementaryData" : { },
      "CreditorAgent" : {
        "PostalAddress" : {
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
        "Identification" : { },
        "SchemeName" : { },
        "Name" : { }
      },
      "DebtorAccount" : {
        "Name" : "Name"
      },
      "EndToEndIdentification" : "EndToEndIdentification",
      "CurrencyOfTransfer" : "CurrencyOfTransfer",
      "InstructionIdentification" : "InstructionIdentification",
      "CreditorAccount" : {
        "Identification" : { },
        "SchemeName" : { },
        "Name" : "Name"
      },
      "ChargeBearer" : { },
      "Purpose" : "Purpose",
      "ExtendedPurpose" : "ExtendedPurpose",
      "InstructionPriority" : "Normal",
      "LocalInstrument" : { },
      "RemittanceInformation" : {
        "RemittanceDescription" : "RemittanceDescription",
        "Reference" : "Reference"
      },
      "DestinationCountryCode" : "DestinationCountryCode",
      "ExchangeRateInformation" : {
        "ExchangeRate" : 6.02745618307040320615897144307382404804229736328125,
        "RateType" : "Actual",
        "ContractIdentification" : "ContractIdentification",
        "UnitCurrency" : "UnitCurrency"
      },
      "Creditor" : {
        "PostalAddress" : {
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
        "Name" : "Name"
      },
      "InstructedAmount" : {
        "Amount" : { },
        "Currency" : { }
      }
    },
    "ExpectedSettlementDateTime" : "2000-01-23T04:56:07.000+00:00"
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("International Payments") :: apiTagMockedData :: Nil
     )

     lazy val internationalPaymentsInternationalPaymentIdGet : OBPEndpoint = {
       case "international-payments" :: internationalPaymentId :: Nil JsonGet _ => {
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
    "Status" : "AcceptedCreditSettlementCompleted",
    "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Debtor" : {
      "Name" : "Name"
    },
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExpectedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExchangeRateInformation" : {
      "ExchangeRate" : 0.80082819046101150206595775671303272247314453125,
      "ExpirationDateTime" : "2000-01-23T04:56:07.000+00:00",
      "RateType" : "Actual",
      "ContractIdentification" : "ContractIdentification",
      "UnitCurrency" : "UnitCurrency"
    },
    "Refund" : {
      "Account" : {
        "Identification" : { },
        "SchemeName" : { },
        "Name" : "Name"
      },
      "Agent" : {
        "PostalAddress" : {
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
        "Identification" : { },
        "SchemeName" : { },
        "Name" : { }
      },
      "Creditor" : {
        "PostalAddress" : {
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
        "Name" : "Name"
      }
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
    "InternationalPaymentId" : "InternationalPaymentId",
    "ConsentId" : "ConsentId",
    "Initiation" : {
      "SupplementaryData" : { },
      "CreditorAgent" : {
        "PostalAddress" : {
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
        "Identification" : { },
        "SchemeName" : { },
        "Name" : { }
      },
      "DebtorAccount" : {
        "Name" : "Name"
      },
      "EndToEndIdentification" : "EndToEndIdentification",
      "CurrencyOfTransfer" : "CurrencyOfTransfer",
      "InstructionIdentification" : "InstructionIdentification",
      "CreditorAccount" : {
        "Identification" : { },
        "SchemeName" : { },
        "Name" : "Name"
      },
      "ChargeBearer" : { },
      "Purpose" : "Purpose",
      "ExtendedPurpose" : "ExtendedPurpose",
      "InstructionPriority" : "Normal",
      "LocalInstrument" : { },
      "RemittanceInformation" : {
        "RemittanceDescription" : "RemittanceDescription",
        "Reference" : "Reference"
      },
      "DestinationCountryCode" : "DestinationCountryCode",
      "ExchangeRateInformation" : {
        "ExchangeRate" : 6.02745618307040320615897144307382404804229736328125,
        "RateType" : "Actual",
        "ContractIdentification" : "ContractIdentification",
        "UnitCurrency" : "UnitCurrency"
      },
      "Creditor" : {
        "PostalAddress" : {
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
        "Name" : "Name"
      },
      "InstructedAmount" : {
        "Amount" : { },
        "Currency" : { }
      }
    },
    "ExpectedSettlementDateTime" : "2000-01-23T04:56:07.000+00:00"
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       internationalPaymentsInternationalPaymentIdPaymentDetailsGet, 
       apiVersion, 
       nameOf(internationalPaymentsInternationalPaymentIdPaymentDetailsGet),
       "GET", 
       "/international-payments/INTERNATIONAL_PAYMENT_ID/payment-details", 
       "Get International Payment Details by InternationalPaymentId",
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
       ApiTag("International Payments") :: apiTagMockedData :: Nil
     )

     lazy val internationalPaymentsInternationalPaymentIdPaymentDetailsGet : OBPEndpoint = {
       case "international-payments" :: internationalPaymentId:: "payment-details" :: Nil JsonGet _ => {
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
       internationalPaymentsPost, 
       apiVersion, 
       nameOf(internationalPaymentsPost),
       "POST", 
       "/international-payments", 
       "Create International Payments",
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
      "CreditorAgent" : {
        "PostalAddress" : {
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
        "Identification" : { },
        "SchemeName" : { },
        "Name" : { }
      },
      "DebtorAccount" : {
        "Name" : "Name"
      },
      "EndToEndIdentification" : "EndToEndIdentification",
      "CurrencyOfTransfer" : "CurrencyOfTransfer",
      "InstructionIdentification" : "InstructionIdentification",
      "CreditorAccount" : {
        "Identification" : { },
        "SchemeName" : { },
        "Name" : "Name"
      },
      "ChargeBearer" : { },
      "Purpose" : "Purpose",
      "ExtendedPurpose" : "ExtendedPurpose",
      "InstructionPriority" : "Normal",
      "LocalInstrument" : { },
      "RemittanceInformation" : {
        "RemittanceDescription" : "RemittanceDescription",
        "Reference" : "Reference"
      },
      "DestinationCountryCode" : "DestinationCountryCode",
      "ExchangeRateInformation" : {
        "ExchangeRate" : 6.02745618307040320615897144307382404804229736328125,
        "RateType" : "Actual",
        "ContractIdentification" : "ContractIdentification",
        "UnitCurrency" : "UnitCurrency"
      },
      "Creditor" : {
        "PostalAddress" : {
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
        "Name" : "Name"
      },
      "InstructedAmount" : {
        "Amount" : { },
        "Currency" : { }
      }
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
    "Status" : "AcceptedCreditSettlementCompleted",
    "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Debtor" : {
      "Name" : "Name"
    },
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExpectedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExchangeRateInformation" : {
      "ExchangeRate" : 0.80082819046101150206595775671303272247314453125,
      "ExpirationDateTime" : "2000-01-23T04:56:07.000+00:00",
      "RateType" : "Actual",
      "ContractIdentification" : "ContractIdentification",
      "UnitCurrency" : "UnitCurrency"
    },
    "Refund" : {
      "Account" : {
        "Identification" : { },
        "SchemeName" : { },
        "Name" : "Name"
      },
      "Agent" : {
        "PostalAddress" : {
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
        "Identification" : { },
        "SchemeName" : { },
        "Name" : { }
      },
      "Creditor" : {
        "PostalAddress" : {
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
        "Name" : "Name"
      }
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
    "InternationalPaymentId" : "InternationalPaymentId",
    "ConsentId" : "ConsentId",
    "Initiation" : {
      "SupplementaryData" : { },
      "CreditorAgent" : {
        "PostalAddress" : {
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
        "Identification" : { },
        "SchemeName" : { },
        "Name" : { }
      },
      "DebtorAccount" : {
        "Name" : "Name"
      },
      "EndToEndIdentification" : "EndToEndIdentification",
      "CurrencyOfTransfer" : "CurrencyOfTransfer",
      "InstructionIdentification" : "InstructionIdentification",
      "CreditorAccount" : {
        "Identification" : { },
        "SchemeName" : { },
        "Name" : "Name"
      },
      "ChargeBearer" : { },
      "Purpose" : "Purpose",
      "ExtendedPurpose" : "ExtendedPurpose",
      "InstructionPriority" : "Normal",
      "LocalInstrument" : { },
      "RemittanceInformation" : {
        "RemittanceDescription" : "RemittanceDescription",
        "Reference" : "Reference"
      },
      "DestinationCountryCode" : "DestinationCountryCode",
      "ExchangeRateInformation" : {
        "ExchangeRate" : 6.02745618307040320615897144307382404804229736328125,
        "RateType" : "Actual",
        "ContractIdentification" : "ContractIdentification",
        "UnitCurrency" : "UnitCurrency"
      },
      "Creditor" : {
        "PostalAddress" : {
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
        "Name" : "Name"
      },
      "InstructedAmount" : {
        "Amount" : { },
        "Currency" : { }
      }
    },
    "ExpectedSettlementDateTime" : "2000-01-23T04:56:07.000+00:00"
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("International Payments") :: apiTagMockedData :: Nil
     )

     lazy val internationalPaymentsPost : OBPEndpoint = {
       case "international-payments" :: Nil JsonPost _ => {
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
    "Status" : "AcceptedCreditSettlementCompleted",
    "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Debtor" : {
      "Name" : "Name"
    },
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExpectedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExchangeRateInformation" : {
      "ExchangeRate" : 0.80082819046101150206595775671303272247314453125,
      "ExpirationDateTime" : "2000-01-23T04:56:07.000+00:00",
      "RateType" : "Actual",
      "ContractIdentification" : "ContractIdentification",
      "UnitCurrency" : "UnitCurrency"
    },
    "Refund" : {
      "Account" : {
        "Identification" : { },
        "SchemeName" : { },
        "Name" : "Name"
      },
      "Agent" : {
        "PostalAddress" : {
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
        "Identification" : { },
        "SchemeName" : { },
        "Name" : { }
      },
      "Creditor" : {
        "PostalAddress" : {
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
        "Name" : "Name"
      }
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
    "InternationalPaymentId" : "InternationalPaymentId",
    "ConsentId" : "ConsentId",
    "Initiation" : {
      "SupplementaryData" : { },
      "CreditorAgent" : {
        "PostalAddress" : {
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
        "Identification" : { },
        "SchemeName" : { },
        "Name" : { }
      },
      "DebtorAccount" : {
        "Name" : "Name"
      },
      "EndToEndIdentification" : "EndToEndIdentification",
      "CurrencyOfTransfer" : "CurrencyOfTransfer",
      "InstructionIdentification" : "InstructionIdentification",
      "CreditorAccount" : {
        "Identification" : { },
        "SchemeName" : { },
        "Name" : "Name"
      },
      "ChargeBearer" : { },
      "Purpose" : "Purpose",
      "ExtendedPurpose" : "ExtendedPurpose",
      "InstructionPriority" : "Normal",
      "LocalInstrument" : { },
      "RemittanceInformation" : {
        "RemittanceDescription" : "RemittanceDescription",
        "Reference" : "Reference"
      },
      "DestinationCountryCode" : "DestinationCountryCode",
      "ExchangeRateInformation" : {
        "ExchangeRate" : 6.02745618307040320615897144307382404804229736328125,
        "RateType" : "Actual",
        "ContractIdentification" : "ContractIdentification",
        "UnitCurrency" : "UnitCurrency"
      },
      "Creditor" : {
        "PostalAddress" : {
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
        "Name" : "Name"
      },
      "InstructedAmount" : {
        "Amount" : { },
        "Currency" : { }
      }
    },
    "ExpectedSettlementDateTime" : "2000-01-23T04:56:07.000+00:00"
  }
}"""), callContext)
           }
         }
       }

}



