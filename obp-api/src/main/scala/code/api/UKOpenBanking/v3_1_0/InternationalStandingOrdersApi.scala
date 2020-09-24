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

object APIMethods_InternationalStandingOrdersApi extends RestHelper {
    val apiVersion = OBP_UKOpenBanking_310.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      createInternationalStandingOrderConsents ::
      createInternationalStandingOrders ::
      getInternationalStandingOrderConsentsConsentId ::
      getInternationalStandingOrdersInternationalStandingOrderPaymentId ::
      Nil

            
     resourceDocs += ResourceDoc(
       createInternationalStandingOrderConsents, 
       apiVersion, 
       nameOf(createInternationalStandingOrderConsents),
       "POST", 
       "/international-standing-order-consents", 
       "Create International Standing Order Consents",
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
      "Reference" : "Reference",
      "CurrencyOfTransfer" : "CurrencyOfTransfer",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "Purpose" : { },
      "ChargeBearer" : { },
      "FinalPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Frequency" : "Frequency",
      "FirstPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "NumberOfPayments" : "NumberOfPayments",
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
    }
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("International Standing Orders") :: apiTagMockedData :: Nil
     )

     lazy val createInternationalStandingOrderConsents : OBPEndpoint = {
       case "international-standing-order-consents" :: Nil JsonPost _ => {
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
      "Reference" : "Reference",
      "CurrencyOfTransfer" : "CurrencyOfTransfer",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "Purpose" : { },
      "ChargeBearer" : { },
      "FinalPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Frequency" : "Frequency",
      "FirstPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "NumberOfPayments" : "NumberOfPayments",
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
    }
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       createInternationalStandingOrders, 
       apiVersion, 
       nameOf(createInternationalStandingOrders),
       "POST", 
       "/international-standing-orders", 
       "Create International Standing Orders",
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
    "InternationalStandingOrderId" : "InternationalStandingOrderId",
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
      "Reference" : "Reference",
      "CurrencyOfTransfer" : "CurrencyOfTransfer",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "Purpose" : { },
      "ChargeBearer" : { },
      "FinalPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Frequency" : "Frequency",
      "FirstPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "NumberOfPayments" : "NumberOfPayments",
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
       ApiTag("International Standing Orders") :: apiTagMockedData :: Nil
     )

     lazy val createInternationalStandingOrders : OBPEndpoint = {
       case "international-standing-orders" :: Nil JsonPost _ => {
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
    "InternationalStandingOrderId" : "InternationalStandingOrderId",
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
      "Reference" : "Reference",
      "CurrencyOfTransfer" : "CurrencyOfTransfer",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "Purpose" : { },
      "ChargeBearer" : { },
      "FinalPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Frequency" : "Frequency",
      "FirstPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "NumberOfPayments" : "NumberOfPayments",
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
       getInternationalStandingOrderConsentsConsentId, 
       apiVersion, 
       nameOf(getInternationalStandingOrderConsentsConsentId),
       "GET", 
       "/international-standing-order-consents/CONSENTID", 
       "Get International Standing Order Consents",
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
      "Reference" : "Reference",
      "CurrencyOfTransfer" : "CurrencyOfTransfer",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "Purpose" : { },
      "ChargeBearer" : { },
      "FinalPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Frequency" : "Frequency",
      "FirstPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "NumberOfPayments" : "NumberOfPayments",
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
    }
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("International Standing Orders") :: apiTagMockedData :: Nil
     )

     lazy val getInternationalStandingOrderConsentsConsentId : OBPEndpoint = {
       case "international-standing-order-consents" :: consentid :: Nil JsonGet _ => {
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
      "Reference" : "Reference",
      "CurrencyOfTransfer" : "CurrencyOfTransfer",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "Purpose" : { },
      "ChargeBearer" : { },
      "FinalPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Frequency" : "Frequency",
      "FirstPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "NumberOfPayments" : "NumberOfPayments",
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
    }
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getInternationalStandingOrdersInternationalStandingOrderPaymentId, 
       apiVersion, 
       nameOf(getInternationalStandingOrdersInternationalStandingOrderPaymentId),
       "GET", 
       "/international-standing-orders/INTERNATIONALSTANDINGORDERPAYMENTID", 
       "Get International Standing Orders",
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
    "InternationalStandingOrderId" : "InternationalStandingOrderId",
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
      "Reference" : "Reference",
      "CurrencyOfTransfer" : "CurrencyOfTransfer",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "Purpose" : { },
      "ChargeBearer" : { },
      "FinalPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Frequency" : "Frequency",
      "FirstPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "NumberOfPayments" : "NumberOfPayments",
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
       ApiTag("International Standing Orders") :: apiTagMockedData :: Nil
     )

     lazy val getInternationalStandingOrdersInternationalStandingOrderPaymentId : OBPEndpoint = {
       case "international-standing-orders" :: internationalstandingorderpaymentid :: Nil JsonGet _ => {
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
    "InternationalStandingOrderId" : "InternationalStandingOrderId",
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
      "Reference" : "Reference",
      "CurrencyOfTransfer" : "CurrencyOfTransfer",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "Purpose" : { },
      "ChargeBearer" : { },
      "FinalPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "Frequency" : "Frequency",
      "FirstPaymentDateTime" : "2000-01-23T04:56:07.000+00:00",
      "NumberOfPayments" : "NumberOfPayments",
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



