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

object APIMethods_InternationalPaymentConsentsApi extends RestHelper {
    val apiVersion =  ApiCollector.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      internationalPaymentConsentsConsentIdFundsConfirmationGet ::
      internationalPaymentConsentsConsentIdGet ::
      internationalPaymentConsentsPost ::
      Nil

            
     resourceDocs += ResourceDoc(
       internationalPaymentConsentsConsentIdFundsConfirmationGet, 
       apiVersion, 
       nameOf(internationalPaymentConsentsConsentIdFundsConfirmationGet),
       "GET", 
       "/international-payment-consents/CONSENT_ID/funds-confirmation", 
       "Get International Payment Consents Funds Confirmation by ConsentId",
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
    "SupplementaryData" : { },
    "FundsAvailableResult" : {
      "FundsAvailableDateTime" : "2000-01-23T04:56:07.000+00:00",
      "FundsAvailable" : true
    }
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("International Payment Consents") :: apiTagMockedData :: Nil
     )

     lazy val internationalPaymentConsentsConsentIdFundsConfirmationGet : OBPEndpoint = {
       case "international-payment-consents" :: consentId:: "funds-confirmation" :: Nil JsonGet _ => {
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
    "SupplementaryData" : { },
    "FundsAvailableResult" : {
      "FundsAvailableDateTime" : "2000-01-23T04:56:07.000+00:00",
      "FundsAvailable" : true
    }
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       internationalPaymentConsentsConsentIdGet, 
       apiVersion, 
       nameOf(internationalPaymentConsentsConsentIdGet),
       "GET", 
       "/international-payment-consents/CONSENT_ID", 
       "Get International Payment Consents by ConsentId",
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
    "Status" : "Authorised",
    "ConsentId" : "ConsentId",
    "SCASupportData" : {
      "RequestedSCAExemptionType" : "BillPayment",
      "AppliedAuthenticationApproach" : "CA",
      "ReferencePaymentOrderId" : "ReferencePaymentOrderId"
    },
    "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Debtor" : {
      "Name" : "Name"
    },
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExpectedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
    "CutOffDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExchangeRateInformation" : {
      "ExchangeRate" : 0.80082819046101150206595775671303272247314453125,
      "ExpirationDateTime" : "2000-01-23T04:56:07.000+00:00",
      "RateType" : "Actual",
      "ContractIdentification" : "ContractIdentification",
      "UnitCurrency" : "UnitCurrency"
    },
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
    "ReadRefundAccount" : "No",
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
       ApiTag("International Payment Consents") :: apiTagMockedData :: Nil
     )

     lazy val internationalPaymentConsentsConsentIdGet : OBPEndpoint = {
       case "international-payment-consents" :: consentId :: Nil JsonGet _ => {
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
    "Status" : "Authorised",
    "ConsentId" : "ConsentId",
    "SCASupportData" : {
      "RequestedSCAExemptionType" : "BillPayment",
      "AppliedAuthenticationApproach" : "CA",
      "ReferencePaymentOrderId" : "ReferencePaymentOrderId"
    },
    "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Debtor" : {
      "Name" : "Name"
    },
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExpectedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
    "CutOffDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExchangeRateInformation" : {
      "ExchangeRate" : 0.80082819046101150206595775671303272247314453125,
      "ExpirationDateTime" : "2000-01-23T04:56:07.000+00:00",
      "RateType" : "Actual",
      "ContractIdentification" : "ContractIdentification",
      "UnitCurrency" : "UnitCurrency"
    },
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
    "ReadRefundAccount" : "No",
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
       internationalPaymentConsentsPost, 
       apiVersion, 
       nameOf(internationalPaymentConsentsPost),
       "POST", 
       "/international-payment-consents", 
       "Create International Payment Consents",
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
    "SCASupportData" : {
      "RequestedSCAExemptionType" : "BillPayment",
      "AppliedAuthenticationApproach" : "CA",
      "ReferencePaymentOrderId" : "ReferencePaymentOrderId"
    },
    "Authorisation" : {
      "CompletionDateTime" : "2000-01-23T04:56:07.000+00:00",
      "AuthorisationType" : "Single"
    },
    "ReadRefundAccount" : "No",
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
    "Status" : "Authorised",
    "ConsentId" : "ConsentId",
    "SCASupportData" : {
      "RequestedSCAExemptionType" : "BillPayment",
      "AppliedAuthenticationApproach" : "CA",
      "ReferencePaymentOrderId" : "ReferencePaymentOrderId"
    },
    "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Debtor" : {
      "Name" : "Name"
    },
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExpectedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
    "CutOffDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExchangeRateInformation" : {
      "ExchangeRate" : 0.80082819046101150206595775671303272247314453125,
      "ExpirationDateTime" : "2000-01-23T04:56:07.000+00:00",
      "RateType" : "Actual",
      "ContractIdentification" : "ContractIdentification",
      "UnitCurrency" : "UnitCurrency"
    },
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
    "ReadRefundAccount" : "No",
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
       ApiTag("International Payment Consents") :: apiTagMockedData :: Nil
     )

     lazy val internationalPaymentConsentsPost : OBPEndpoint = {
       case "international-payment-consents" :: Nil JsonPost _ => {
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
    "Status" : "Authorised",
    "ConsentId" : "ConsentId",
    "SCASupportData" : {
      "RequestedSCAExemptionType" : "BillPayment",
      "AppliedAuthenticationApproach" : "CA",
      "ReferencePaymentOrderId" : "ReferencePaymentOrderId"
    },
    "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Debtor" : {
      "Name" : "Name"
    },
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExpectedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
    "CutOffDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExchangeRateInformation" : {
      "ExchangeRate" : 0.80082819046101150206595775671303272247314453125,
      "ExpirationDateTime" : "2000-01-23T04:56:07.000+00:00",
      "RateType" : "Actual",
      "ContractIdentification" : "ContractIdentification",
      "UnitCurrency" : "UnitCurrency"
    },
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
    "ReadRefundAccount" : "No",
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



