package code.api.UKOpenBanking.v3_1_0

import code.api.APIFailureNewStyle
import code.api.berlin.group.v1_3.JvalueCaseClass
import net.liftweb.json
import net.liftweb.json._
import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3
import code.api.util.APIUtil.{defaultBankId, _}
import code.api.util.{ApiVersion, NewStyle}
import code.api.util.ErrorMessages._
import code.api.util.ApiTag._
import code.api.util.NewStyle.HttpCode
import code.bankconnectors.Connector
import code.model._
import code.util.Helper
import code.views.Views
import net.liftweb.common.Full
import net.liftweb.http.rest.RestHelper
import com.github.dwickern.macros.NameOf.nameOf
import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import code.api.UKOpenBanking.v3_1_0.OBP_UKOpenBanking_310
import code.api.util.ApiTag

object APIMethods_InternationalPaymentsApi extends RestHelper {
    val apiVersion = OBP_UKOpenBanking_310.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      createInternationalPaymentConsents ::
      createInternationalPayments ::
      getInternationalPaymentConsentsConsentId ::
      getInternationalPaymentConsentsConsentIdFundsConfirmation ::
      getInternationalPaymentsInternationalPaymentId ::
      Nil

            
     resourceDocs += ResourceDoc(
       createInternationalPaymentConsents, 
       apiVersion, 
       nameOf(createInternationalPaymentConsents),
       "POST", 
       "/international-payment-consents", 
       "Create International Payment Consents",
       s"""${mockedDataText(true)}
""", 
       json.parse(""""""),
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
    "ExchangeRateInformation" : {
      "ExchangeRate" : 0.80082819046101150206595775671303272247314453125,
      "ExpirationDateTime" : "2000-01-23T04:56:07.000+00:00",
      "RateType" : { },
      "UnitCurrency" : "UnitCurrency",
      "ContractIdentification" : "ContractIdentification"
    },
    "Authorisation" : {
      "CompletionDateTime" : "2000-01-23T04:56:07.000+00:00",
      "AuthorisationType" : { }
    },
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
      "DebtorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "CreditorAgent" : {
        "PostalAddress" : {
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
        },
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "EndToEndIdentification" : "EndToEndIdentification",
      "InstructionIdentification" : "InstructionIdentification",
      "CurrencyOfTransfer" : "CurrencyOfTransfer",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "Purpose" : { },
      "ChargeBearer" : { },
      "InstructionPriority" : { },
      "LocalInstrument" : [ "UK.OBIE.BACS", "UK.OBIE.BalanceTransfer", "UK.OBIE.CHAPS", "UK.OBIE.Euro1", "UK.OBIE.FPS", "UK.OBIE.Link", "UK.OBIE.MoneyTransfer", "UK.OBIE.Paym", "UK.OBIE.SEPACreditTransfer", "UK.OBIE.SEPAInstantCreditTransfer", "UK.OBIE.SWIFT", "UK.OBIE.Target2" ],
      "RemittanceInformation" : {
        "Unstructured" : "Unstructured",
        "Reference" : "Reference"
      },
      "ExchangeRateInformation" : {
        "ExchangeRate" : 6.02745618307040320615897144307382404804229736328125,
        "UnitCurrency" : "UnitCurrency",
        "ContractIdentification" : "ContractIdentification"
      },
      "Creditor" : {
        "PostalAddress" : {
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
        },
        "Name" : "Name"
      },
      "InstructedAmount" : {
        "Currency" : "Currency"
      }
    },
    "ExpectedSettlementDateTime" : "2000-01-23T04:56:07.000+00:00"
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("International Payments") :: apiTagMockedData :: Nil
     )

     lazy val createInternationalPaymentConsents : OBPEndpoint = {
       case "international-payment-consents" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
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
    "ExchangeRateInformation" : {
      "ExchangeRate" : 0.80082819046101150206595775671303272247314453125,
      "ExpirationDateTime" : "2000-01-23T04:56:07.000+00:00",
      "RateType" : { },
      "UnitCurrency" : "UnitCurrency",
      "ContractIdentification" : "ContractIdentification"
    },
    "Authorisation" : {
      "CompletionDateTime" : "2000-01-23T04:56:07.000+00:00",
      "AuthorisationType" : { }
    },
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
      "DebtorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "CreditorAgent" : {
        "PostalAddress" : {
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
        },
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "EndToEndIdentification" : "EndToEndIdentification",
      "InstructionIdentification" : "InstructionIdentification",
      "CurrencyOfTransfer" : "CurrencyOfTransfer",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "Purpose" : { },
      "ChargeBearer" : { },
      "InstructionPriority" : { },
      "LocalInstrument" : [ "UK.OBIE.BACS", "UK.OBIE.BalanceTransfer", "UK.OBIE.CHAPS", "UK.OBIE.Euro1", "UK.OBIE.FPS", "UK.OBIE.Link", "UK.OBIE.MoneyTransfer", "UK.OBIE.Paym", "UK.OBIE.SEPACreditTransfer", "UK.OBIE.SEPAInstantCreditTransfer", "UK.OBIE.SWIFT", "UK.OBIE.Target2" ],
      "RemittanceInformation" : {
        "Unstructured" : "Unstructured",
        "Reference" : "Reference"
      },
      "ExchangeRateInformation" : {
        "ExchangeRate" : 6.02745618307040320615897144307382404804229736328125,
        "UnitCurrency" : "UnitCurrency",
        "ContractIdentification" : "ContractIdentification"
      },
      "Creditor" : {
        "PostalAddress" : {
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
        },
        "Name" : "Name"
      },
      "InstructedAmount" : {
        "Currency" : "Currency"
      }
    },
    "ExpectedSettlementDateTime" : "2000-01-23T04:56:07.000+00:00"
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       createInternationalPayments, 
       apiVersion, 
       nameOf(createInternationalPayments),
       "POST", 
       "/international-payments", 
       "Create International Payments",
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
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExpectedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExchangeRateInformation" : {
      "ExchangeRate" : 0.80082819046101150206595775671303272247314453125,
      "ExpirationDateTime" : "2000-01-23T04:56:07.000+00:00",
      "RateType" : { },
      "UnitCurrency" : "UnitCurrency",
      "ContractIdentification" : "ContractIdentification"
    },
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
    "InternationalPaymentId" : "InternationalPaymentId",
    "ConsentId" : "ConsentId",
    "Initiation" : {
      "SupplementaryData" : { },
      "DebtorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "CreditorAgent" : {
        "PostalAddress" : {
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
        },
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "EndToEndIdentification" : "EndToEndIdentification",
      "InstructionIdentification" : "InstructionIdentification",
      "CurrencyOfTransfer" : "CurrencyOfTransfer",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "Purpose" : { },
      "ChargeBearer" : { },
      "InstructionPriority" : { },
      "LocalInstrument" : [ "UK.OBIE.BACS", "UK.OBIE.BalanceTransfer", "UK.OBIE.CHAPS", "UK.OBIE.Euro1", "UK.OBIE.FPS", "UK.OBIE.Link", "UK.OBIE.MoneyTransfer", "UK.OBIE.Paym", "UK.OBIE.SEPACreditTransfer", "UK.OBIE.SEPAInstantCreditTransfer", "UK.OBIE.SWIFT", "UK.OBIE.Target2" ],
      "RemittanceInformation" : {
        "Unstructured" : "Unstructured",
        "Reference" : "Reference"
      },
      "ExchangeRateInformation" : {
        "ExchangeRate" : 6.02745618307040320615897144307382404804229736328125,
        "UnitCurrency" : "UnitCurrency",
        "ContractIdentification" : "ContractIdentification"
      },
      "Creditor" : {
        "PostalAddress" : {
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
        },
        "Name" : "Name"
      },
      "InstructedAmount" : {
        "Currency" : "Currency"
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
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("International Payments") :: apiTagMockedData :: Nil
     )

     lazy val createInternationalPayments : OBPEndpoint = {
       case "international-payments" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
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
    "ExchangeRateInformation" : {
      "ExchangeRate" : 0.80082819046101150206595775671303272247314453125,
      "ExpirationDateTime" : "2000-01-23T04:56:07.000+00:00",
      "RateType" : { },
      "UnitCurrency" : "UnitCurrency",
      "ContractIdentification" : "ContractIdentification"
    },
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
    "InternationalPaymentId" : "InternationalPaymentId",
    "ConsentId" : "ConsentId",
    "Initiation" : {
      "SupplementaryData" : { },
      "DebtorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "CreditorAgent" : {
        "PostalAddress" : {
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
        },
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "EndToEndIdentification" : "EndToEndIdentification",
      "InstructionIdentification" : "InstructionIdentification",
      "CurrencyOfTransfer" : "CurrencyOfTransfer",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "Purpose" : { },
      "ChargeBearer" : { },
      "InstructionPriority" : { },
      "LocalInstrument" : [ "UK.OBIE.BACS", "UK.OBIE.BalanceTransfer", "UK.OBIE.CHAPS", "UK.OBIE.Euro1", "UK.OBIE.FPS", "UK.OBIE.Link", "UK.OBIE.MoneyTransfer", "UK.OBIE.Paym", "UK.OBIE.SEPACreditTransfer", "UK.OBIE.SEPAInstantCreditTransfer", "UK.OBIE.SWIFT", "UK.OBIE.Target2" ],
      "RemittanceInformation" : {
        "Unstructured" : "Unstructured",
        "Reference" : "Reference"
      },
      "ExchangeRateInformation" : {
        "ExchangeRate" : 6.02745618307040320615897144307382404804229736328125,
        "UnitCurrency" : "UnitCurrency",
        "ContractIdentification" : "ContractIdentification"
      },
      "Creditor" : {
        "PostalAddress" : {
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
        },
        "Name" : "Name"
      },
      "InstructedAmount" : {
        "Currency" : "Currency"
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
       getInternationalPaymentConsentsConsentId, 
       apiVersion, 
       nameOf(getInternationalPaymentConsentsConsentId),
       "GET", 
       "/international-payment-consents/CONSENTID", 
       "Get International Payment Consents",
       s"""${mockedDataText(true)}
""", 
       json.parse(""""""),
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
    "ExchangeRateInformation" : {
      "ExchangeRate" : 0.80082819046101150206595775671303272247314453125,
      "ExpirationDateTime" : "2000-01-23T04:56:07.000+00:00",
      "RateType" : { },
      "UnitCurrency" : "UnitCurrency",
      "ContractIdentification" : "ContractIdentification"
    },
    "Authorisation" : {
      "CompletionDateTime" : "2000-01-23T04:56:07.000+00:00",
      "AuthorisationType" : { }
    },
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
      "DebtorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "CreditorAgent" : {
        "PostalAddress" : {
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
        },
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "EndToEndIdentification" : "EndToEndIdentification",
      "InstructionIdentification" : "InstructionIdentification",
      "CurrencyOfTransfer" : "CurrencyOfTransfer",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "Purpose" : { },
      "ChargeBearer" : { },
      "InstructionPriority" : { },
      "LocalInstrument" : [ "UK.OBIE.BACS", "UK.OBIE.BalanceTransfer", "UK.OBIE.CHAPS", "UK.OBIE.Euro1", "UK.OBIE.FPS", "UK.OBIE.Link", "UK.OBIE.MoneyTransfer", "UK.OBIE.Paym", "UK.OBIE.SEPACreditTransfer", "UK.OBIE.SEPAInstantCreditTransfer", "UK.OBIE.SWIFT", "UK.OBIE.Target2" ],
      "RemittanceInformation" : {
        "Unstructured" : "Unstructured",
        "Reference" : "Reference"
      },
      "ExchangeRateInformation" : {
        "ExchangeRate" : 6.02745618307040320615897144307382404804229736328125,
        "UnitCurrency" : "UnitCurrency",
        "ContractIdentification" : "ContractIdentification"
      },
      "Creditor" : {
        "PostalAddress" : {
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
        },
        "Name" : "Name"
      },
      "InstructedAmount" : {
        "Currency" : "Currency"
      }
    },
    "ExpectedSettlementDateTime" : "2000-01-23T04:56:07.000+00:00"
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("International Payments") :: apiTagMockedData :: Nil
     )

     lazy val getInternationalPaymentConsentsConsentId : OBPEndpoint = {
       case "international-payment-consents" :: consentid :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
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
    "ExchangeRateInformation" : {
      "ExchangeRate" : 0.80082819046101150206595775671303272247314453125,
      "ExpirationDateTime" : "2000-01-23T04:56:07.000+00:00",
      "RateType" : { },
      "UnitCurrency" : "UnitCurrency",
      "ContractIdentification" : "ContractIdentification"
    },
    "Authorisation" : {
      "CompletionDateTime" : "2000-01-23T04:56:07.000+00:00",
      "AuthorisationType" : { }
    },
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
      "DebtorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "CreditorAgent" : {
        "PostalAddress" : {
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
        },
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "EndToEndIdentification" : "EndToEndIdentification",
      "InstructionIdentification" : "InstructionIdentification",
      "CurrencyOfTransfer" : "CurrencyOfTransfer",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "Purpose" : { },
      "ChargeBearer" : { },
      "InstructionPriority" : { },
      "LocalInstrument" : [ "UK.OBIE.BACS", "UK.OBIE.BalanceTransfer", "UK.OBIE.CHAPS", "UK.OBIE.Euro1", "UK.OBIE.FPS", "UK.OBIE.Link", "UK.OBIE.MoneyTransfer", "UK.OBIE.Paym", "UK.OBIE.SEPACreditTransfer", "UK.OBIE.SEPAInstantCreditTransfer", "UK.OBIE.SWIFT", "UK.OBIE.Target2" ],
      "RemittanceInformation" : {
        "Unstructured" : "Unstructured",
        "Reference" : "Reference"
      },
      "ExchangeRateInformation" : {
        "ExchangeRate" : 6.02745618307040320615897144307382404804229736328125,
        "UnitCurrency" : "UnitCurrency",
        "ContractIdentification" : "ContractIdentification"
      },
      "Creditor" : {
        "PostalAddress" : {
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
        },
        "Name" : "Name"
      },
      "InstructedAmount" : {
        "Currency" : "Currency"
      }
    },
    "ExpectedSettlementDateTime" : "2000-01-23T04:56:07.000+00:00"
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getInternationalPaymentConsentsConsentIdFundsConfirmation, 
       apiVersion, 
       nameOf(getInternationalPaymentConsentsConsentIdFundsConfirmation),
       "GET", 
       "/international-payment-consents/CONSENTID/funds-confirmation", 
       "Get International Payment Consents Funds Confirmation",
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
    "SupplementaryData" : { },
    "FundsAvailableResult" : {
      "FundsAvailableDateTime" : "2000-01-23T04:56:07.000+00:00",
      "FundsAvailable" : true
    }
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("International Payments") :: apiTagMockedData :: Nil
     )

     lazy val getInternationalPaymentConsentsConsentIdFundsConfirmation : OBPEndpoint = {
       case "international-payment-consents" :: consentid:: "funds-confirmation" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
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
       getInternationalPaymentsInternationalPaymentId, 
       apiVersion, 
       nameOf(getInternationalPaymentsInternationalPaymentId),
       "GET", 
       "/international-payments/INTERNATIONALPAYMENTID", 
       "Get International Payments",
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
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExpectedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExchangeRateInformation" : {
      "ExchangeRate" : 0.80082819046101150206595775671303272247314453125,
      "ExpirationDateTime" : "2000-01-23T04:56:07.000+00:00",
      "RateType" : { },
      "UnitCurrency" : "UnitCurrency",
      "ContractIdentification" : "ContractIdentification"
    },
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
    "InternationalPaymentId" : "InternationalPaymentId",
    "ConsentId" : "ConsentId",
    "Initiation" : {
      "SupplementaryData" : { },
      "DebtorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "CreditorAgent" : {
        "PostalAddress" : {
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
        },
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "EndToEndIdentification" : "EndToEndIdentification",
      "InstructionIdentification" : "InstructionIdentification",
      "CurrencyOfTransfer" : "CurrencyOfTransfer",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "Purpose" : { },
      "ChargeBearer" : { },
      "InstructionPriority" : { },
      "LocalInstrument" : [ "UK.OBIE.BACS", "UK.OBIE.BalanceTransfer", "UK.OBIE.CHAPS", "UK.OBIE.Euro1", "UK.OBIE.FPS", "UK.OBIE.Link", "UK.OBIE.MoneyTransfer", "UK.OBIE.Paym", "UK.OBIE.SEPACreditTransfer", "UK.OBIE.SEPAInstantCreditTransfer", "UK.OBIE.SWIFT", "UK.OBIE.Target2" ],
      "RemittanceInformation" : {
        "Unstructured" : "Unstructured",
        "Reference" : "Reference"
      },
      "ExchangeRateInformation" : {
        "ExchangeRate" : 6.02745618307040320615897144307382404804229736328125,
        "UnitCurrency" : "UnitCurrency",
        "ContractIdentification" : "ContractIdentification"
      },
      "Creditor" : {
        "PostalAddress" : {
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
        },
        "Name" : "Name"
      },
      "InstructedAmount" : {
        "Currency" : "Currency"
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
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("International Payments") :: apiTagMockedData :: Nil
     )

     lazy val getInternationalPaymentsInternationalPaymentId : OBPEndpoint = {
       case "international-payments" :: internationalpaymentid :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
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
    "ExchangeRateInformation" : {
      "ExchangeRate" : 0.80082819046101150206595775671303272247314453125,
      "ExpirationDateTime" : "2000-01-23T04:56:07.000+00:00",
      "RateType" : { },
      "UnitCurrency" : "UnitCurrency",
      "ContractIdentification" : "ContractIdentification"
    },
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
    "InternationalPaymentId" : "InternationalPaymentId",
    "ConsentId" : "ConsentId",
    "Initiation" : {
      "SupplementaryData" : { },
      "DebtorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "CreditorAgent" : {
        "PostalAddress" : {
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
        },
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "EndToEndIdentification" : "EndToEndIdentification",
      "InstructionIdentification" : "InstructionIdentification",
      "CurrencyOfTransfer" : "CurrencyOfTransfer",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "Purpose" : { },
      "ChargeBearer" : { },
      "InstructionPriority" : { },
      "LocalInstrument" : [ "UK.OBIE.BACS", "UK.OBIE.BalanceTransfer", "UK.OBIE.CHAPS", "UK.OBIE.Euro1", "UK.OBIE.FPS", "UK.OBIE.Link", "UK.OBIE.MoneyTransfer", "UK.OBIE.Paym", "UK.OBIE.SEPACreditTransfer", "UK.OBIE.SEPAInstantCreditTransfer", "UK.OBIE.SWIFT", "UK.OBIE.Target2" ],
      "RemittanceInformation" : {
        "Unstructured" : "Unstructured",
        "Reference" : "Reference"
      },
      "ExchangeRateInformation" : {
        "ExchangeRate" : 6.02745618307040320615897144307382404804229736328125,
        "UnitCurrency" : "UnitCurrency",
        "ContractIdentification" : "ContractIdentification"
      },
      "Creditor" : {
        "PostalAddress" : {
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
        },
        "Name" : "Name"
      },
      "InstructedAmount" : {
        "Currency" : "Currency"
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



