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

object APIMethods_DomesticScheduledPaymentsApi extends RestHelper {
    val apiVersion = OBP_UKOpenBanking_310.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      createDomesticScheduledPaymentConsents ::
      createDomesticScheduledPayments ::
      getDomesticScheduledPaymentConsentsConsentId ::
      getDomesticScheduledPaymentsDomesticScheduledPaymentId ::
      Nil

            
     resourceDocs += ResourceDoc(
       createDomesticScheduledPaymentConsents, 
       apiVersion, 
       nameOf(createDomesticScheduledPaymentConsents),
       "POST", 
       "/domestic-scheduled-payment-consents", 
       "Create Domestic Scheduled Payment Consents",
       s"""${mockedDataText(true)}
""", 
       emptyObjectJson,
       json.parse("""{
  "Meta" : {
    "FirstAvailableDateTime" : { },
    "TotalPages" : 0
  },
  "Risk" : {
    "PaymentContextCode" : { },
    "DeliveryAddress" : {
      "StreetName" : "StreetName",
      "CountrySubDivision" : [ "CountrySubDivision", "CountrySubDivision" ],
      "AddressLine" : [ "AddressLine", "AddressLine" ],
      "BuildingNumber" : "BuildingNumber",
      "TownName" : "TownName",
      "Country" : "Country",
      "PostCode" : "PostCode"
    },
    "MerchantCategoryCode" : "MerchantCategoryCode",
    "MerchantCustomerIdentification" : "MerchantCustomerIdentification"
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
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExpectedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
    "CutOffDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Authorisation" : {
      "CompletionDateTime" : "2000-01-23T04:56:07.000+00:00",
      "AuthorisationType" : { }
    },
    "Permission" : { },
    "Charges" : [ {
      "Type" : [ "UK.OBIE.CHAPSOut", "UK.OBIE.BalanceTransferOut", "UK.OBIE.MoneyTransferOut" ],
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "ChargeBearer" : { }
    }, {
      "Type" : [ "UK.OBIE.CHAPSOut", "UK.OBIE.BalanceTransferOut", "UK.OBIE.MoneyTransferOut" ],
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "ChargeBearer" : { }
    } ],
    "ConsentId" : "ConsentId",
    "Initiation" : {
      "SupplementaryData" : { },
      "LocalInstrument" : [ "UK.OBIE.BACS", "UK.OBIE.BalanceTransfer", "UK.OBIE.CHAPS", "UK.OBIE.Euro1", "UK.OBIE.FPS", "UK.OBIE.Link", "UK.OBIE.MoneyTransfer", "UK.OBIE.Paym", "UK.OBIE.SEPACreditTransfer", "UK.OBIE.SEPAInstantCreditTransfer", "UK.OBIE.SWIFT", "UK.OBIE.Target2" ],
      "DebtorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "RemittanceInformation" : {
        "Unstructured" : "Unstructured",
        "Reference" : "Reference"
      },
      "EndToEndIdentification" : "EndToEndIdentification",
      "InstructionIdentification" : "InstructionIdentification",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "RequestedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
      "InstructedAmount" : {
        "Currency" : "Currency"
      },
      "CreditorPostalAddress" : {
        "StreetName" : "StreetName",
        "CountrySubDivision" : "CountrySubDivision",
        "Department" : "Department",
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : "BuildingNumber",
        "TownName" : "TownName",
        "Country" : "Country",
        "SubDepartment" : "SubDepartment",
        "AddressType" : { },
        "PostCode" : "PostCode"
      }
    },
    "ExpectedSettlementDateTime" : "2000-01-23T04:56:07.000+00:00"
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Domestic Scheduled Payments") :: apiTagMockedData :: Nil
     )

     lazy val createDomesticScheduledPaymentConsents : OBPEndpoint = {
       case "domestic-scheduled-payment-consents" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             } yield {
             (json.parse("""{
  "Meta" : {
    "FirstAvailableDateTime" : { },
    "TotalPages" : 0
  },
  "Risk" : {
    "PaymentContextCode" : { },
    "DeliveryAddress" : {
      "StreetName" : "StreetName",
      "CountrySubDivision" : [ "CountrySubDivision", "CountrySubDivision" ],
      "AddressLine" : [ "AddressLine", "AddressLine" ],
      "BuildingNumber" : "BuildingNumber",
      "TownName" : "TownName",
      "Country" : "Country",
      "PostCode" : "PostCode"
    },
    "MerchantCategoryCode" : "MerchantCategoryCode",
    "MerchantCustomerIdentification" : "MerchantCustomerIdentification"
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
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExpectedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
    "CutOffDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Authorisation" : {
      "CompletionDateTime" : "2000-01-23T04:56:07.000+00:00",
      "AuthorisationType" : { }
    },
    "Permission" : { },
    "Charges" : [ {
      "Type" : [ "UK.OBIE.CHAPSOut", "UK.OBIE.BalanceTransferOut", "UK.OBIE.MoneyTransferOut" ],
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "ChargeBearer" : { }
    }, {
      "Type" : [ "UK.OBIE.CHAPSOut", "UK.OBIE.BalanceTransferOut", "UK.OBIE.MoneyTransferOut" ],
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "ChargeBearer" : { }
    } ],
    "ConsentId" : "ConsentId",
    "Initiation" : {
      "SupplementaryData" : { },
      "LocalInstrument" : [ "UK.OBIE.BACS", "UK.OBIE.BalanceTransfer", "UK.OBIE.CHAPS", "UK.OBIE.Euro1", "UK.OBIE.FPS", "UK.OBIE.Link", "UK.OBIE.MoneyTransfer", "UK.OBIE.Paym", "UK.OBIE.SEPACreditTransfer", "UK.OBIE.SEPAInstantCreditTransfer", "UK.OBIE.SWIFT", "UK.OBIE.Target2" ],
      "DebtorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "RemittanceInformation" : {
        "Unstructured" : "Unstructured",
        "Reference" : "Reference"
      },
      "EndToEndIdentification" : "EndToEndIdentification",
      "InstructionIdentification" : "InstructionIdentification",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "RequestedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
      "InstructedAmount" : {
        "Currency" : "Currency"
      },
      "CreditorPostalAddress" : {
        "StreetName" : "StreetName",
        "CountrySubDivision" : "CountrySubDivision",
        "Department" : "Department",
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : "BuildingNumber",
        "TownName" : "TownName",
        "Country" : "Country",
        "SubDepartment" : "SubDepartment",
        "AddressType" : { },
        "PostCode" : "PostCode"
      }
    },
    "ExpectedSettlementDateTime" : "2000-01-23T04:56:07.000+00:00"
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       createDomesticScheduledPayments, 
       apiVersion, 
       nameOf(createDomesticScheduledPayments),
       "POST", 
       "/domestic-scheduled-payments", 
       "Create Domestic Scheduled Payments",
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
    "Status" : { },
    "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExpectedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
    "DomesticScheduledPaymentId" : "DomesticScheduledPaymentId",
    "Charges" : [ {
      "Type" : [ "UK.OBIE.CHAPSOut", "UK.OBIE.BalanceTransferOut", "UK.OBIE.MoneyTransferOut" ],
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "ChargeBearer" : { }
    }, {
      "Type" : [ "UK.OBIE.CHAPSOut", "UK.OBIE.BalanceTransferOut", "UK.OBIE.MoneyTransferOut" ],
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "ChargeBearer" : { }
    } ],
    "ConsentId" : "ConsentId",
    "Initiation" : {
      "SupplementaryData" : { },
      "LocalInstrument" : [ "UK.OBIE.BACS", "UK.OBIE.BalanceTransfer", "UK.OBIE.CHAPS", "UK.OBIE.Euro1", "UK.OBIE.FPS", "UK.OBIE.Link", "UK.OBIE.MoneyTransfer", "UK.OBIE.Paym", "UK.OBIE.SEPACreditTransfer", "UK.OBIE.SEPAInstantCreditTransfer", "UK.OBIE.SWIFT", "UK.OBIE.Target2" ],
      "DebtorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "RemittanceInformation" : {
        "Unstructured" : "Unstructured",
        "Reference" : "Reference"
      },
      "EndToEndIdentification" : "EndToEndIdentification",
      "InstructionIdentification" : "InstructionIdentification",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "RequestedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
      "InstructedAmount" : {
        "Currency" : "Currency"
      },
      "CreditorPostalAddress" : {
        "StreetName" : "StreetName",
        "CountrySubDivision" : "CountrySubDivision",
        "Department" : "Department",
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : "BuildingNumber",
        "TownName" : "TownName",
        "Country" : "Country",
        "SubDepartment" : "SubDepartment",
        "AddressType" : { },
        "PostCode" : "PostCode"
      }
    },
    "ExpectedSettlementDateTime" : "2000-01-23T04:56:07.000+00:00",
    "MultiAuthorisation" : {
      "Status" : { },
      "NumberReceived" : 6,
      "LastUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
      "ExpirationDateTime" : "2000-01-23T04:56:07.000+00:00",
      "NumberRequired" : 0
    }
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Domestic Scheduled Payments") :: apiTagMockedData :: Nil
     )

     lazy val createDomesticScheduledPayments : OBPEndpoint = {
       case "domestic-scheduled-payments" :: Nil JsonPost _ => {
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
    "Status" : { },
    "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExpectedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
    "DomesticScheduledPaymentId" : "DomesticScheduledPaymentId",
    "Charges" : [ {
      "Type" : [ "UK.OBIE.CHAPSOut", "UK.OBIE.BalanceTransferOut", "UK.OBIE.MoneyTransferOut" ],
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "ChargeBearer" : { }
    }, {
      "Type" : [ "UK.OBIE.CHAPSOut", "UK.OBIE.BalanceTransferOut", "UK.OBIE.MoneyTransferOut" ],
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "ChargeBearer" : { }
    } ],
    "ConsentId" : "ConsentId",
    "Initiation" : {
      "SupplementaryData" : { },
      "LocalInstrument" : [ "UK.OBIE.BACS", "UK.OBIE.BalanceTransfer", "UK.OBIE.CHAPS", "UK.OBIE.Euro1", "UK.OBIE.FPS", "UK.OBIE.Link", "UK.OBIE.MoneyTransfer", "UK.OBIE.Paym", "UK.OBIE.SEPACreditTransfer", "UK.OBIE.SEPAInstantCreditTransfer", "UK.OBIE.SWIFT", "UK.OBIE.Target2" ],
      "DebtorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "RemittanceInformation" : {
        "Unstructured" : "Unstructured",
        "Reference" : "Reference"
      },
      "EndToEndIdentification" : "EndToEndIdentification",
      "InstructionIdentification" : "InstructionIdentification",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "RequestedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
      "InstructedAmount" : {
        "Currency" : "Currency"
      },
      "CreditorPostalAddress" : {
        "StreetName" : "StreetName",
        "CountrySubDivision" : "CountrySubDivision",
        "Department" : "Department",
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : "BuildingNumber",
        "TownName" : "TownName",
        "Country" : "Country",
        "SubDepartment" : "SubDepartment",
        "AddressType" : { },
        "PostCode" : "PostCode"
      }
    },
    "ExpectedSettlementDateTime" : "2000-01-23T04:56:07.000+00:00",
    "MultiAuthorisation" : {
      "Status" : { },
      "NumberReceived" : 6,
      "LastUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
      "ExpirationDateTime" : "2000-01-23T04:56:07.000+00:00",
      "NumberRequired" : 0
    }
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getDomesticScheduledPaymentConsentsConsentId, 
       apiVersion, 
       nameOf(getDomesticScheduledPaymentConsentsConsentId),
       "GET", 
       "/domestic-scheduled-payment-consents/CONSENTID", 
       "Get Domestic Scheduled Payment Consents",
       s"""${mockedDataText(true)}
""", 
       emptyObjectJson,
       json.parse("""{
  "Meta" : {
    "FirstAvailableDateTime" : { },
    "TotalPages" : 0
  },
  "Risk" : {
    "PaymentContextCode" : { },
    "DeliveryAddress" : {
      "StreetName" : "StreetName",
      "CountrySubDivision" : [ "CountrySubDivision", "CountrySubDivision" ],
      "AddressLine" : [ "AddressLine", "AddressLine" ],
      "BuildingNumber" : "BuildingNumber",
      "TownName" : "TownName",
      "Country" : "Country",
      "PostCode" : "PostCode"
    },
    "MerchantCategoryCode" : "MerchantCategoryCode",
    "MerchantCustomerIdentification" : "MerchantCustomerIdentification"
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
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExpectedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
    "CutOffDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Authorisation" : {
      "CompletionDateTime" : "2000-01-23T04:56:07.000+00:00",
      "AuthorisationType" : { }
    },
    "Permission" : { },
    "Charges" : [ {
      "Type" : [ "UK.OBIE.CHAPSOut", "UK.OBIE.BalanceTransferOut", "UK.OBIE.MoneyTransferOut" ],
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "ChargeBearer" : { }
    }, {
      "Type" : [ "UK.OBIE.CHAPSOut", "UK.OBIE.BalanceTransferOut", "UK.OBIE.MoneyTransferOut" ],
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "ChargeBearer" : { }
    } ],
    "ConsentId" : "ConsentId",
    "Initiation" : {
      "SupplementaryData" : { },
      "LocalInstrument" : [ "UK.OBIE.BACS", "UK.OBIE.BalanceTransfer", "UK.OBIE.CHAPS", "UK.OBIE.Euro1", "UK.OBIE.FPS", "UK.OBIE.Link", "UK.OBIE.MoneyTransfer", "UK.OBIE.Paym", "UK.OBIE.SEPACreditTransfer", "UK.OBIE.SEPAInstantCreditTransfer", "UK.OBIE.SWIFT", "UK.OBIE.Target2" ],
      "DebtorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "RemittanceInformation" : {
        "Unstructured" : "Unstructured",
        "Reference" : "Reference"
      },
      "EndToEndIdentification" : "EndToEndIdentification",
      "InstructionIdentification" : "InstructionIdentification",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "RequestedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
      "InstructedAmount" : {
        "Currency" : "Currency"
      },
      "CreditorPostalAddress" : {
        "StreetName" : "StreetName",
        "CountrySubDivision" : "CountrySubDivision",
        "Department" : "Department",
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : "BuildingNumber",
        "TownName" : "TownName",
        "Country" : "Country",
        "SubDepartment" : "SubDepartment",
        "AddressType" : { },
        "PostCode" : "PostCode"
      }
    },
    "ExpectedSettlementDateTime" : "2000-01-23T04:56:07.000+00:00"
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Domestic Scheduled Payments") :: apiTagMockedData :: Nil
     )

     lazy val getDomesticScheduledPaymentConsentsConsentId : OBPEndpoint = {
       case "domestic-scheduled-payment-consents" :: consentid :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             } yield {
             (json.parse("""{
  "Meta" : {
    "FirstAvailableDateTime" : { },
    "TotalPages" : 0
  },
  "Risk" : {
    "PaymentContextCode" : { },
    "DeliveryAddress" : {
      "StreetName" : "StreetName",
      "CountrySubDivision" : [ "CountrySubDivision", "CountrySubDivision" ],
      "AddressLine" : [ "AddressLine", "AddressLine" ],
      "BuildingNumber" : "BuildingNumber",
      "TownName" : "TownName",
      "Country" : "Country",
      "PostCode" : "PostCode"
    },
    "MerchantCategoryCode" : "MerchantCategoryCode",
    "MerchantCustomerIdentification" : "MerchantCustomerIdentification"
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
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExpectedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
    "CutOffDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Authorisation" : {
      "CompletionDateTime" : "2000-01-23T04:56:07.000+00:00",
      "AuthorisationType" : { }
    },
    "Permission" : { },
    "Charges" : [ {
      "Type" : [ "UK.OBIE.CHAPSOut", "UK.OBIE.BalanceTransferOut", "UK.OBIE.MoneyTransferOut" ],
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "ChargeBearer" : { }
    }, {
      "Type" : [ "UK.OBIE.CHAPSOut", "UK.OBIE.BalanceTransferOut", "UK.OBIE.MoneyTransferOut" ],
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "ChargeBearer" : { }
    } ],
    "ConsentId" : "ConsentId",
    "Initiation" : {
      "SupplementaryData" : { },
      "LocalInstrument" : [ "UK.OBIE.BACS", "UK.OBIE.BalanceTransfer", "UK.OBIE.CHAPS", "UK.OBIE.Euro1", "UK.OBIE.FPS", "UK.OBIE.Link", "UK.OBIE.MoneyTransfer", "UK.OBIE.Paym", "UK.OBIE.SEPACreditTransfer", "UK.OBIE.SEPAInstantCreditTransfer", "UK.OBIE.SWIFT", "UK.OBIE.Target2" ],
      "DebtorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "RemittanceInformation" : {
        "Unstructured" : "Unstructured",
        "Reference" : "Reference"
      },
      "EndToEndIdentification" : "EndToEndIdentification",
      "InstructionIdentification" : "InstructionIdentification",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "RequestedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
      "InstructedAmount" : {
        "Currency" : "Currency"
      },
      "CreditorPostalAddress" : {
        "StreetName" : "StreetName",
        "CountrySubDivision" : "CountrySubDivision",
        "Department" : "Department",
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : "BuildingNumber",
        "TownName" : "TownName",
        "Country" : "Country",
        "SubDepartment" : "SubDepartment",
        "AddressType" : { },
        "PostCode" : "PostCode"
      }
    },
    "ExpectedSettlementDateTime" : "2000-01-23T04:56:07.000+00:00"
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getDomesticScheduledPaymentsDomesticScheduledPaymentId, 
       apiVersion, 
       nameOf(getDomesticScheduledPaymentsDomesticScheduledPaymentId),
       "GET", 
       "/domestic-scheduled-payments/DOMESTICSCHEDULEDPAYMENTID", 
       "Get Domestic Scheduled Payments",
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
    "Status" : { },
    "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExpectedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
    "DomesticScheduledPaymentId" : "DomesticScheduledPaymentId",
    "Charges" : [ {
      "Type" : [ "UK.OBIE.CHAPSOut", "UK.OBIE.BalanceTransferOut", "UK.OBIE.MoneyTransferOut" ],
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "ChargeBearer" : { }
    }, {
      "Type" : [ "UK.OBIE.CHAPSOut", "UK.OBIE.BalanceTransferOut", "UK.OBIE.MoneyTransferOut" ],
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "ChargeBearer" : { }
    } ],
    "ConsentId" : "ConsentId",
    "Initiation" : {
      "SupplementaryData" : { },
      "LocalInstrument" : [ "UK.OBIE.BACS", "UK.OBIE.BalanceTransfer", "UK.OBIE.CHAPS", "UK.OBIE.Euro1", "UK.OBIE.FPS", "UK.OBIE.Link", "UK.OBIE.MoneyTransfer", "UK.OBIE.Paym", "UK.OBIE.SEPACreditTransfer", "UK.OBIE.SEPAInstantCreditTransfer", "UK.OBIE.SWIFT", "UK.OBIE.Target2" ],
      "DebtorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "RemittanceInformation" : {
        "Unstructured" : "Unstructured",
        "Reference" : "Reference"
      },
      "EndToEndIdentification" : "EndToEndIdentification",
      "InstructionIdentification" : "InstructionIdentification",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "RequestedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
      "InstructedAmount" : {
        "Currency" : "Currency"
      },
      "CreditorPostalAddress" : {
        "StreetName" : "StreetName",
        "CountrySubDivision" : "CountrySubDivision",
        "Department" : "Department",
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : "BuildingNumber",
        "TownName" : "TownName",
        "Country" : "Country",
        "SubDepartment" : "SubDepartment",
        "AddressType" : { },
        "PostCode" : "PostCode"
      }
    },
    "ExpectedSettlementDateTime" : "2000-01-23T04:56:07.000+00:00",
    "MultiAuthorisation" : {
      "Status" : { },
      "NumberReceived" : 6,
      "LastUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
      "ExpirationDateTime" : "2000-01-23T04:56:07.000+00:00",
      "NumberRequired" : 0
    }
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Domestic Scheduled Payments") :: apiTagMockedData :: Nil
     )

     lazy val getDomesticScheduledPaymentsDomesticScheduledPaymentId : OBPEndpoint = {
       case "domestic-scheduled-payments" :: domesticscheduledpaymentid :: Nil JsonGet _ => {
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
    "Status" : { },
    "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExpectedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
    "DomesticScheduledPaymentId" : "DomesticScheduledPaymentId",
    "Charges" : [ {
      "Type" : [ "UK.OBIE.CHAPSOut", "UK.OBIE.BalanceTransferOut", "UK.OBIE.MoneyTransferOut" ],
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "ChargeBearer" : { }
    }, {
      "Type" : [ "UK.OBIE.CHAPSOut", "UK.OBIE.BalanceTransferOut", "UK.OBIE.MoneyTransferOut" ],
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "ChargeBearer" : { }
    } ],
    "ConsentId" : "ConsentId",
    "Initiation" : {
      "SupplementaryData" : { },
      "LocalInstrument" : [ "UK.OBIE.BACS", "UK.OBIE.BalanceTransfer", "UK.OBIE.CHAPS", "UK.OBIE.Euro1", "UK.OBIE.FPS", "UK.OBIE.Link", "UK.OBIE.MoneyTransfer", "UK.OBIE.Paym", "UK.OBIE.SEPACreditTransfer", "UK.OBIE.SEPAInstantCreditTransfer", "UK.OBIE.SWIFT", "UK.OBIE.Target2" ],
      "DebtorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "RemittanceInformation" : {
        "Unstructured" : "Unstructured",
        "Reference" : "Reference"
      },
      "EndToEndIdentification" : "EndToEndIdentification",
      "InstructionIdentification" : "InstructionIdentification",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "RequestedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
      "InstructedAmount" : {
        "Currency" : "Currency"
      },
      "CreditorPostalAddress" : {
        "StreetName" : "StreetName",
        "CountrySubDivision" : "CountrySubDivision",
        "Department" : "Department",
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : "BuildingNumber",
        "TownName" : "TownName",
        "Country" : "Country",
        "SubDepartment" : "SubDepartment",
        "AddressType" : { },
        "PostCode" : "PostCode"
      }
    },
    "ExpectedSettlementDateTime" : "2000-01-23T04:56:07.000+00:00",
    "MultiAuthorisation" : {
      "Status" : { },
      "NumberReceived" : 6,
      "LastUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
      "ExpirationDateTime" : "2000-01-23T04:56:07.000+00:00",
      "NumberRequired" : 0
    }
  }
}"""), callContext)
           }
         }
       }

}



