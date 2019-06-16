package code.api.UKOpenBanking.v3_1_0

import code.api.APIFailureNewStyle
import code.api.berlin.group.v1_3.JvalueCaseClass
import code.api.util.APIUtil.{defaultBankId, _}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.{ApiTag, NewStyle}
import code.bankconnectors.Connector
import code.model._
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{AccountId, BankId, BankIdAccountId, ViewId}
import net.liftweb.common.Full
import net.liftweb.http.rest.RestHelper
import net.liftweb.json
import net.liftweb.json._

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object APIMethods_TransactionsApi extends RestHelper {
    val apiVersion = OBP_UKOpenBanking_310.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      getAccountsAccountIdStatementsStatementIdTransactions ::
      getAccountsAccountIdTransactions ::
      getTransactions ::
      Nil

            
     resourceDocs += ResourceDoc(
       getAccountsAccountIdStatementsStatementIdTransactions, 
       apiVersion, 
       nameOf(getAccountsAccountIdStatementsStatementIdTransactions),
       "GET", 
       "/accounts/ACCOUNTID/statements/STATEMENTID/transactions", 
       "Get Transactions",
       s"""${mockedDataText(true)}
""", 
       json.parse(""""""),
       json.parse("""{
  "Meta" : {
    "FirstAvailableDateTime": "2019-03-06T07:38:51.169Z",
    "LastAvailableDateTime": "2019-03-06T07:38:51.169Z"
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
    "Transaction" : [ {
      "Status" : "string",
      "SupplementaryData" : {},
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
          "AddressType" : "string",
          "PostCode" : "PostCode"
        },
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "DebtorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "AccountId" : "string",
      "TransactionReference" : "TransactionReference",
      "ProprietaryBankTransactionCode" : {
        "Issuer" : "Issuer",
        "Code" : "Code"
      },
      "AddressLine" : "AddressLine",
      "Amount" : {
        "Amount" : "string",
        "Currency" : "Currency"
      },
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "CreditDebitIndicator" : "Credit",
      "CurrencyExchange" : {
        "SourceCurrency" : "SourceCurrency",
        "ExchangeRate" : 0.80082819046101150206595775671303272247314453125,
        "QuotationDate" : "2000-01-23T04:56:07.000+00:00",
        "UnitCurrency" : "UnitCurrency",
        "ContractIdentification" : "ContractIdentification",
        "InstructedAmount" : {
          "Amount" : "string",
          "Currency" : "Currency"
        },
        "TargetCurrency" : "TargetCurrency"
      },
      "StatementReference" : [ "StatementReference", "StatementReference" ],
      "ChargeAmount" : {
        "Amount" : "string",
        "Currency" : "Currency"
      },
      "TransactionId" : "TransactionId",
      "TransactionInformation" : "string",
      "BookingDateTime" : "2000-01-23T04:56:07.000+00:00",
      "BankTransactionCode" : {
        "SubCode" : "SubCode",
        "Code" : "Code"
      },
      "MerchantDetails" : {
        "MerchantName" : "MerchantName",
        "MerchantCategoryCode" : "MerchantCategoryCode"
      },
      "CardInstrument" : {
        "AuthorisationType" : "string",
        "Identification" : "Identification",
        "CardSchemeName" : "string",
        "Name" : "Name"
      },
      "ValueDateTime" : "2000-01-23T04:56:07.000+00:00",
      "DebtorAgent" : {
        "PostalAddress" : {
          "StreetName" : "StreetName",
          "CountrySubDivision" : "CountrySubDivision",
          "Department" : "Department",
          "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
          "BuildingNumber" : "BuildingNumber",
          "TownName" : "TownName",
          "Country" : "Country",
          "SubDepartment" : "SubDepartment",
          "AddressType" : "string",
          "PostCode" : "PostCode"
        },
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "Balance" : {
        "Type" : "string",
        "Amount" : {
          "Amount" : "string",
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      }
    }, {
      "Status" : "string",
      "SupplementaryData" : {},
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
          "AddressType" : "string",
          "PostCode" : "PostCode"
        },
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "DebtorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "AccountId" : "string",
      "TransactionReference" : "TransactionReference",
      "ProprietaryBankTransactionCode" : {
        "Issuer" : "Issuer",
        "Code" : "Code"
      },
      "AddressLine" : "AddressLine",
      "Amount" : {
        "Amount" : "string",
        "Currency" : "Currency"
      },
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "CreditDebitIndicator" : "Credit",
      "CurrencyExchange" : {
        "SourceCurrency" : "SourceCurrency",
        "ExchangeRate" : 0.80082819046101150206595775671303272247314453125,
        "QuotationDate" : "2000-01-23T04:56:07.000+00:00",
        "UnitCurrency" : "UnitCurrency",
        "ContractIdentification" : "ContractIdentification",
        "InstructedAmount" : {
          "Amount" : "string",
          "Currency" : "Currency"
        },
        "TargetCurrency" : "TargetCurrency"
      },
      "StatementReference" : [ "StatementReference", "StatementReference" ],
      "ChargeAmount" : {
        "Amount" : "string",
        "Currency" : "Currency"
      },
      "TransactionId" : "TransactionId",
      "TransactionInformation" : "string",
      "BookingDateTime" : "2000-01-23T04:56:07.000+00:00",
      "BankTransactionCode" : {
        "SubCode" : "SubCode",
        "Code" : "Code"
      },
      "MerchantDetails" : {
        "MerchantName" : "MerchantName",
        "MerchantCategoryCode" : "MerchantCategoryCode"
      },
      "CardInstrument" : {
        "AuthorisationType" : "string",
        "Identification" : "Identification",
        "CardSchemeName" : "string",
        "Name" : "Name"
      },
      "ValueDateTime" : "2000-01-23T04:56:07.000+00:00",
      "DebtorAgent" : {
        "PostalAddress" : {
          "StreetName" : "StreetName",
          "CountrySubDivision" : "CountrySubDivision",
          "Department" : "Department",
          "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
          "BuildingNumber" : "BuildingNumber",
          "TownName" : "TownName",
          "Country" : "Country",
          "SubDepartment" : "SubDepartment",
          "AddressType" : "string",
          "PostCode" : "PostCode"
        },
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "Balance" : {
        "Type" : "string",
        "Amount" : {
          "Amount" : "string",
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      }
    } ]
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("Statements") ::ApiTag("Transactions") :: apiTagMockedData :: Nil
     )

     lazy val getAccountsAccountIdStatementsStatementIdTransactions : OBPEndpoint = {
       case "accounts" :: accountid:: "statements" :: statementid:: "transactions" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc)
             } yield {
             (json.parse("""{
  "Meta" : {
    "FirstAvailableDateTime": "2019-03-06T07:38:51.169Z",
    "LastAvailableDateTime": "2019-03-06T07:38:51.169Z"
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
    "Transaction" : [ {
      "Status" : "string",
      "SupplementaryData" : {},
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
          "AddressType" : "string",
          "PostCode" : "PostCode"
        },
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "DebtorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "AccountId" : "string",
      "TransactionReference" : "TransactionReference",
      "ProprietaryBankTransactionCode" : {
        "Issuer" : "Issuer",
        "Code" : "Code"
      },
      "AddressLine" : "AddressLine",
      "Amount" : {
        "Amount" : "string",
        "Currency" : "Currency"
      },
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "CreditDebitIndicator" : "Credit",
      "CurrencyExchange" : {
        "SourceCurrency" : "SourceCurrency",
        "ExchangeRate" : 0.80082819046101150206595775671303272247314453125,
        "QuotationDate" : "2000-01-23T04:56:07.000+00:00",
        "UnitCurrency" : "UnitCurrency",
        "ContractIdentification" : "ContractIdentification",
        "InstructedAmount" : {
          "Amount" : "string",
          "Currency" : "Currency"
        },
        "TargetCurrency" : "TargetCurrency"
      },
      "StatementReference" : [ "StatementReference", "StatementReference" ],
      "ChargeAmount" : {
        "Amount" : "string",
        "Currency" : "Currency"
      },
      "TransactionId" : "TransactionId",
      "TransactionInformation" : "string",
      "BookingDateTime" : "2000-01-23T04:56:07.000+00:00",
      "BankTransactionCode" : {
        "SubCode" : "SubCode",
        "Code" : "Code"
      },
      "MerchantDetails" : {
        "MerchantName" : "MerchantName",
        "MerchantCategoryCode" : "MerchantCategoryCode"
      },
      "CardInstrument" : {
        "AuthorisationType" : "string",
        "Identification" : "Identification",
        "CardSchemeName" : "string",
        "Name" : "Name"
      },
      "ValueDateTime" : "2000-01-23T04:56:07.000+00:00",
      "DebtorAgent" : {
        "PostalAddress" : {
          "StreetName" : "StreetName",
          "CountrySubDivision" : "CountrySubDivision",
          "Department" : "Department",
          "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
          "BuildingNumber" : "BuildingNumber",
          "TownName" : "TownName",
          "Country" : "Country",
          "SubDepartment" : "SubDepartment",
          "AddressType" : "string",
          "PostCode" : "PostCode"
        },
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "Balance" : {
        "Type" : "string",
        "Amount" : {
          "Amount" : "string",
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      }
    }, {
      "Status" : "string",
      "SupplementaryData" : {},
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
          "AddressType" : "string",
          "PostCode" : "PostCode"
        },
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "DebtorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "AccountId" : "string",
      "TransactionReference" : "TransactionReference",
      "ProprietaryBankTransactionCode" : {
        "Issuer" : "Issuer",
        "Code" : "Code"
      },
      "AddressLine" : "AddressLine",
      "Amount" : {
        "Amount" : "string",
        "Currency" : "Currency"
      },
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "CreditDebitIndicator" : "Credit",
      "CurrencyExchange" : {
        "SourceCurrency" : "SourceCurrency",
        "ExchangeRate" : 0.80082819046101150206595775671303272247314453125,
        "QuotationDate" : "2000-01-23T04:56:07.000+00:00",
        "UnitCurrency" : "UnitCurrency",
        "ContractIdentification" : "ContractIdentification",
        "InstructedAmount" : {
          "Amount" : "string",
          "Currency" : "Currency"
        },
        "TargetCurrency" : "TargetCurrency"
      },
      "StatementReference" : [ "StatementReference", "StatementReference" ],
      "ChargeAmount" : {
        "Amount" : "string",
        "Currency" : "Currency"
      },
      "TransactionId" : "TransactionId",
      "TransactionInformation" : "string",
      "BookingDateTime" : "2000-01-23T04:56:07.000+00:00",
      "BankTransactionCode" : {
        "SubCode" : "SubCode",
        "Code" : "Code"
      },
      "MerchantDetails" : {
        "MerchantName" : "MerchantName",
        "MerchantCategoryCode" : "MerchantCategoryCode"
      },
      "CardInstrument" : {
        "AuthorisationType" : "string",
        "Identification" : "Identification",
        "CardSchemeName" : "string",
        "Name" : "Name"
      },
      "ValueDateTime" : "2000-01-23T04:56:07.000+00:00",
      "DebtorAgent" : {
        "PostalAddress" : {
          "StreetName" : "StreetName",
          "CountrySubDivision" : "CountrySubDivision",
          "Department" : "Department",
          "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
          "BuildingNumber" : "BuildingNumber",
          "TownName" : "TownName",
          "Country" : "Country",
          "SubDepartment" : "SubDepartment",
          "AddressType" : "string",
          "PostCode" : "PostCode"
        },
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "Balance" : {
        "Type" : "string",
        "Amount" : {
          "Amount" : "string",
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      }
    } ]
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getAccountsAccountIdTransactions, 
       apiVersion, 
       nameOf(getAccountsAccountIdTransactions),
       "GET", 
       "/accounts/ACCOUNT_ID/transactions", 
       "Get Transactions",
       s"""""", 
       json.parse(""""""),
       json.parse("""{
  "Meta" : {
    "FirstAvailableDateTime": "2019-03-06T07:38:51.169Z",
    "LastAvailableDateTime": "2019-03-06T07:38:51.169Z"
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
    "Transaction" : [ {
      "Status" : "string",
      "SupplementaryData" : {},
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
          "AddressType" : "string",
          "PostCode" : "PostCode"
        },
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "DebtorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "AccountId" : "string",
      "TransactionReference" : "TransactionReference",
      "ProprietaryBankTransactionCode" : {
        "Issuer" : "Issuer",
        "Code" : "Code"
      },
      "AddressLine" : "AddressLine",
      "Amount" : {
        "Amount" : "string",
        "Currency" : "Currency"
      },
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "CreditDebitIndicator" : "Credit",
      "CurrencyExchange" : {
        "SourceCurrency" : "SourceCurrency",
        "ExchangeRate" : 0.80082819046101150206595775671303272247314453125,
        "QuotationDate" : "2000-01-23T04:56:07.000+00:00",
        "UnitCurrency" : "UnitCurrency",
        "ContractIdentification" : "ContractIdentification",
        "InstructedAmount" : {
          "Amount" : "string",
          "Currency" : "Currency"
        },
        "TargetCurrency" : "TargetCurrency"
      },
      "StatementReference" : [ "StatementReference", "StatementReference" ],
      "ChargeAmount" : {
        "Amount" : "string",
        "Currency" : "Currency"
      },
      "TransactionId" : "TransactionId",
      "TransactionInformation" : "string",
      "BookingDateTime" : "2000-01-23T04:56:07.000+00:00",
      "BankTransactionCode" : {
        "SubCode" : "SubCode",
        "Code" : "Code"
      },
      "MerchantDetails" : {
        "MerchantName" : "MerchantName",
        "MerchantCategoryCode" : "MerchantCategoryCode"
      },
      "CardInstrument" : {
        "AuthorisationType" : "string",
        "Identification" : "Identification",
        "CardSchemeName" : "string",
        "Name" : "Name"
      },
      "ValueDateTime" : "2000-01-23T04:56:07.000+00:00",
      "DebtorAgent" : {
        "PostalAddress" : {
          "StreetName" : "StreetName",
          "CountrySubDivision" : "CountrySubDivision",
          "Department" : "Department",
          "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
          "BuildingNumber" : "BuildingNumber",
          "TownName" : "TownName",
          "Country" : "Country",
          "SubDepartment" : "SubDepartment",
          "AddressType" : "string",
          "PostCode" : "PostCode"
        },
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "Balance" : {
        "Type" : "string",
        "Amount" : {
          "Amount" : "string",
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      }
    }, {
      "Status" : "string",
      "SupplementaryData" : {},
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
          "AddressType" : "string",
          "PostCode" : "PostCode"
        },
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "DebtorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "AccountId" : "string",
      "TransactionReference" : "TransactionReference",
      "ProprietaryBankTransactionCode" : {
        "Issuer" : "Issuer",
        "Code" : "Code"
      },
      "AddressLine" : "AddressLine",
      "Amount" : {
        "Amount" : "string",
        "Currency" : "Currency"
      },
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "CreditDebitIndicator" : "Credit",
      "CurrencyExchange" : {
        "SourceCurrency" : "SourceCurrency",
        "ExchangeRate" : 0.80082819046101150206595775671303272247314453125,
        "QuotationDate" : "2000-01-23T04:56:07.000+00:00",
        "UnitCurrency" : "UnitCurrency",
        "ContractIdentification" : "ContractIdentification",
        "InstructedAmount" : {
          "Amount" : "string",
          "Currency" : "Currency"
        },
        "TargetCurrency" : "TargetCurrency"
      },
      "StatementReference" : [ "StatementReference", "StatementReference" ],
      "ChargeAmount" : {
        "Amount" : "string",
        "Currency" : "Currency"
      },
      "TransactionId" : "TransactionId",
      "TransactionInformation" : "string",
      "BookingDateTime" : "2000-01-23T04:56:07.000+00:00",
      "BankTransactionCode" : {
        "SubCode" : "SubCode",
        "Code" : "Code"
      },
      "MerchantDetails" : {
        "MerchantName" : "MerchantName",
        "MerchantCategoryCode" : "MerchantCategoryCode"
      },
      "CardInstrument" : {
        "AuthorisationType" : "string",
        "Identification" : "Identification",
        "CardSchemeName" : "string",
        "Name" : "Name"
      },
      "ValueDateTime" : "2000-01-23T04:56:07.000+00:00",
      "DebtorAgent" : {
        "PostalAddress" : {
          "StreetName" : "StreetName",
          "CountrySubDivision" : "CountrySubDivision",
          "Department" : "Department",
          "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
          "BuildingNumber" : "BuildingNumber",
          "TownName" : "TownName",
          "Country" : "Country",
          "SubDepartment" : "SubDepartment",
          "AddressType" : "string",
          "PostCode" : "PostCode"
        },
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "Balance" : {
        "Type" : "string",
        "Amount" : {
          "Amount" : "string",
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      }
    } ]
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("Transactions") :: apiTagMockedData :: Nil
     )

     lazy val getAccountsAccountIdTransactions : OBPEndpoint = {
       case "accounts" :: AccountId(accountId):: "transactions" :: Nil JsonGet _ => {
         cc =>
           for {
            (Full(u), callContext) <- authorizedAccess(cc)
            (bankAccount, callContext) <- Future { BankAccountX(BankId(defaultBankId), accountId, callContext) } map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(DefaultBankIdNotSet, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
            view <- NewStyle.function.view(ViewId("owner"), BankIdAccountId(bankAccount.bankId, bankAccount.accountId), callContext)
            params <- Future { createQueriesByHttpParams(callContext.get.requestHeaders)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
          
            (transactionRequests, callContext) <- Future { Connector.connector.vend.getTransactionRequests210(u, bankAccount)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(InvalidConnectorResponseForGetTransactionRequests210, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
          
            (transactions, callContext) <- Future { bankAccount.getModeratedTransactions(Full(u), view, callContext, params)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
          
          } yield {
            (JSONFactory_UKOpenBanking_310.createTransactionsJson(transactions, transactionRequests), callContext)
          }
         }
       }
            
     resourceDocs += ResourceDoc(
       getTransactions, 
       apiVersion, 
       nameOf(getTransactions),
       "GET", 
       "/transactions", 
       "Get Transactions",
       s"""""", 
       json.parse(""""""),
       json.parse("""{
  "Meta" : {
    "FirstAvailableDateTime": "2019-03-06T07:38:51.169Z",
    "LastAvailableDateTime": "2019-03-06T07:38:51.169Z"
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
    "Transaction" : [ {
      "Status" : "string",
      "SupplementaryData" : {},
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
          "AddressType" : "string",
          "PostCode" : "PostCode"
        },
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "DebtorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "AccountId" : "string",
      "TransactionReference" : "TransactionReference",
      "ProprietaryBankTransactionCode" : {
        "Issuer" : "Issuer",
        "Code" : "Code"
      },
      "AddressLine" : "AddressLine",
      "Amount" : {
        "Amount" : "string",
        "Currency" : "Currency"
      },
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "CreditDebitIndicator" : "Credit",
      "CurrencyExchange" : {
        "SourceCurrency" : "SourceCurrency",
        "ExchangeRate" : 0.80082819046101150206595775671303272247314453125,
        "QuotationDate" : "2000-01-23T04:56:07.000+00:00",
        "UnitCurrency" : "UnitCurrency",
        "ContractIdentification" : "ContractIdentification",
        "InstructedAmount" : {
          "Amount" : "string",
          "Currency" : "Currency"
        },
        "TargetCurrency" : "TargetCurrency"
      },
      "StatementReference" : [ "StatementReference", "StatementReference" ],
      "ChargeAmount" : {
        "Amount" : "string",
        "Currency" : "Currency"
      },
      "TransactionId" : "TransactionId",
      "TransactionInformation" : "string",
      "BookingDateTime" : "2000-01-23T04:56:07.000+00:00",
      "BankTransactionCode" : {
        "SubCode" : "SubCode",
        "Code" : "Code"
      },
      "MerchantDetails" : {
        "MerchantName" : "MerchantName",
        "MerchantCategoryCode" : "MerchantCategoryCode"
      },
      "CardInstrument" : {
        "AuthorisationType" : "string",
        "Identification" : "Identification",
        "CardSchemeName" : "string",
        "Name" : "Name"
      },
      "ValueDateTime" : "2000-01-23T04:56:07.000+00:00",
      "DebtorAgent" : {
        "PostalAddress" : {
          "StreetName" : "StreetName",
          "CountrySubDivision" : "CountrySubDivision",
          "Department" : "Department",
          "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
          "BuildingNumber" : "BuildingNumber",
          "TownName" : "TownName",
          "Country" : "Country",
          "SubDepartment" : "SubDepartment",
          "AddressType" : "string",
          "PostCode" : "PostCode"
        },
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "Balance" : {
        "Type" : "string",
        "Amount" : {
          "Amount" : "string",
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      }
    }, {
      "Status" : "string",
      "SupplementaryData" : {},
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
          "AddressType" : "string",
          "PostCode" : "PostCode"
        },
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "DebtorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "AccountId" : "string",
      "TransactionReference" : "TransactionReference",
      "ProprietaryBankTransactionCode" : {
        "Issuer" : "Issuer",
        "Code" : "Code"
      },
      "AddressLine" : "AddressLine",
      "Amount" : {
        "Amount" : "string",
        "Currency" : "Currency"
      },
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "CreditDebitIndicator" : "Credit",
      "CurrencyExchange" : {
        "SourceCurrency" : "SourceCurrency",
        "ExchangeRate" : 0.80082819046101150206595775671303272247314453125,
        "QuotationDate" : "2000-01-23T04:56:07.000+00:00",
        "UnitCurrency" : "UnitCurrency",
        "ContractIdentification" : "ContractIdentification",
        "InstructedAmount" : {
          "Amount" : "string",
          "Currency" : "Currency"
        },
        "TargetCurrency" : "TargetCurrency"
      },
      "StatementReference" : [ "StatementReference", "StatementReference" ],
      "ChargeAmount" : {
        "Amount" : "string",
        "Currency" : "Currency"
      },
      "TransactionId" : "TransactionId",
      "TransactionInformation" : "string",
      "BookingDateTime" : "2000-01-23T04:56:07.000+00:00",
      "BankTransactionCode" : {
        "SubCode" : "SubCode",
        "Code" : "Code"
      },
      "MerchantDetails" : {
        "MerchantName" : "MerchantName",
        "MerchantCategoryCode" : "MerchantCategoryCode"
      },
      "CardInstrument" : {
        "AuthorisationType" : "string",
        "Identification" : "Identification",
        "CardSchemeName" : "string",
        "Name" : "Name"
      },
      "ValueDateTime" : "2000-01-23T04:56:07.000+00:00",
      "DebtorAgent" : {
        "PostalAddress" : {
          "StreetName" : "StreetName",
          "CountrySubDivision" : "CountrySubDivision",
          "Department" : "Department",
          "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
          "BuildingNumber" : "BuildingNumber",
          "TownName" : "TownName",
          "Country" : "Country",
          "SubDepartment" : "SubDepartment",
          "AddressType" : "string",
          "PostCode" : "PostCode"
        },
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "Balance" : {
        "Type" : "string",
        "Amount" : {
          "Amount" : "string",
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      }
    } ]
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("Transactions") :: apiTagMockedData :: Nil
     )

     lazy val getTransactions : OBPEndpoint = {
       case "transactions" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc)
  
             availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u)
  
             Full(accounts) <- Connector.connector.vend.getBankAccounts(availablePrivateAccounts, callContext)
  
             transactionAndTransactionRequestTuple = for{
               bankAccount <- accounts
             } yield{
               for{
                 view <- Views.views.vend.view(ViewId("owner"), BankIdAccountId(bankAccount.bankId, bankAccount.accountId))
                 params <- createQueriesByHttpParams(callContext.get.requestHeaders)
                 (transactionRequests, callContext) <- Connector.connector.vend.getTransactionRequests210(u, bankAccount)
                 (transactions, callContext) <-  bankAccount.getModeratedTransactions(Full(u), view, callContext, params)
               } yield{
                 (transactionRequests,transactions)
               } 
             }
             //TODO, need to try the error handling here...
             transactionRequests = transactionAndTransactionRequestTuple.map(_.map(_._1)).flatten.flatten
             transactions= transactionAndTransactionRequestTuple.map(_.map(_._2)).flatten.flatten
             
          } yield {
            (JSONFactory_UKOpenBanking_310.createTransactionsJson(transactions, transactionRequests), callContext)
          }
         }
       }

}



