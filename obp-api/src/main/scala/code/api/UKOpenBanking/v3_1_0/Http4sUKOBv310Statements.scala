package code.api.UKOpenBanking.v3_1_0

import org.json4s._
import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import code.api.util.APIUtil.{EmptyBody, ResourceDoc, mockedDataText}
import code.api.util.ApiTag
import code.api.util.ApiTag._
import code.api.util.CustomJsonFormats
import code.api.util.ErrorMessages
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, UnknownError}
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.util.Helper.MdcLoggable
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}
import com.openbankproject.commons.util.json
import org.json4s.Formats
import org.http4s._
import org.http4s.dsl.io._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

/**
 * UK Open Banking v3.1 — StatementsApi stubs migrated to http4s (NotImplemented marker, 200).
 * Note: the /accounts/{id}/statements/{sid}/transactions route is also declared by
 * TransactionsApi (duplicate); Lift serves Statements first, so it lives here.
 */
object Http4sUKOBv310Statements extends MdcLoggable {
  type HttpF[A] = OptionT[IO, A]
  implicit val formats: Formats = CustomJsonFormats.formats
  val implementedInApiVersion: ScannedApiVersion = ApiVersion.ukOpenBankingV31
  val resourceDocs = ArrayBuffer[ResourceDoc]()
  private def parseBody(s: String): code.api.berlin.group.v1_3.JvalueCaseClass = code.api.berlin.group.v1_3.JvalueCaseClass(com.openbankproject.commons.util.JsonAliases.parse(s).asInstanceOf[org.json4s.JObject])
  val ukV31Prefix = Root / ApiVersion.ukOpenBankingV31.urlPrefix / ApiVersion.ukOpenBankingV31.apiShortVersion
  private val tag = ApiTag("Statements") :: apiTagMockedData :: Nil

  lazy val getAccountsAccountIdStatements: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "accounts" / _ / "statements" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccountsAccountIdStatements),
    "GET",
    "/accounts/ACCOUNTID/statements",
    "Get Statements",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    parseBody("""{
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
    "Statement" : [ {
      "AccountId" : { },
      "StatementReference" : "StatementReference",
      "StatementDateTime" : [ {
        "Type" : [ "UK.OBIE.BalanceTransferPromoEnd", "UK.OBIE.DirectDebitDue", "UK.OBIE.LastPayment", "UK.OBIE.LastStatement", "UK.OBIE.NextStatement", "UK.OBIE.PaymentDue", "UK.OBIE.PurchasePromoEnd", "UK.OBIE.StatementAvailable" ],
        "DateTime" : "2000-01-23T04:56:07.000+00:00"
      }, {
        "Type" : [ "UK.OBIE.BalanceTransferPromoEnd", "UK.OBIE.DirectDebitDue", "UK.OBIE.LastPayment", "UK.OBIE.LastStatement", "UK.OBIE.NextStatement", "UK.OBIE.PaymentDue", "UK.OBIE.PurchasePromoEnd", "UK.OBIE.StatementAvailable" ],
        "DateTime" : "2000-01-23T04:56:07.000+00:00"
      } ],
      "StatementInterest" : [ {
        "Type" : [ "UK.OBIE.BalanceTransfer", "UK.OBIE.Cash", "UK.OBIE.EstimatedNext", "UK.OBIE.Purchase", "UK.OBIE.Total" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      }, {
        "Type" : [ "UK.OBIE.BalanceTransfer", "UK.OBIE.Cash", "UK.OBIE.EstimatedNext", "UK.OBIE.Purchase", "UK.OBIE.Total" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      } ],
      "Type" : { },
      "StartDateTime" : "2000-01-23T04:56:07.000+00:00",
      "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
      "StatementRate" : [ {
        "Type" : [ "UK.OBIE.AnnualBalanceTransfer", "UK.OBIE.AnnualBalanceTransferAfterPromo", "UK.OBIE.AnnualBalanceTransferPromo", "UK.OBIE.AnnualCash", "UK.OBIE.AnnualPurchase", "UK.OBIE.AnnualPurchaseAfterPromo", "UK.OBIE.AnnualPurchasePromo", "UK.OBIE.MonthlyBalanceTransfer", "UK.OBIE.MonthlyCash", "UK.OBIE.MonthlyPurchase" ],
        "Rate" : "Rate"
      }, {
        "Type" : [ "UK.OBIE.AnnualBalanceTransfer", "UK.OBIE.AnnualBalanceTransferAfterPromo", "UK.OBIE.AnnualBalanceTransferPromo", "UK.OBIE.AnnualCash", "UK.OBIE.AnnualPurchase", "UK.OBIE.AnnualPurchaseAfterPromo", "UK.OBIE.AnnualPurchasePromo", "UK.OBIE.MonthlyBalanceTransfer", "UK.OBIE.MonthlyCash", "UK.OBIE.MonthlyPurchase" ],
        "Rate" : "Rate"
      } ],
      "EndDateTime" : "2000-01-23T04:56:07.000+00:00",
      "StatementId" : "StatementId",
      "StatementValue" : [ {
        "Type" : [ "UK.OBIE.AirMilesPoints", "UK.OBIE.AirMilesPointsBalance", "UK.OBIE.Credits", "UK.OBIE.Debits", "UK.OBIE.HotelPoints", "UK.OBIE.HotelPointsBalance", "UK.OBIE.RetailShoppingPoints", "UK.OBIE.RetailShoppingPointsBalance" ],
        "Value" : 0
      }, {
        "Type" : [ "UK.OBIE.AirMilesPoints", "UK.OBIE.AirMilesPointsBalance", "UK.OBIE.Credits", "UK.OBIE.Debits", "UK.OBIE.HotelPoints", "UK.OBIE.HotelPointsBalance", "UK.OBIE.RetailShoppingPoints", "UK.OBIE.RetailShoppingPointsBalance" ],
        "Value" : 0
      } ],
      "StatementBenefit" : [ {
        "Type" : [ "UK.OBIE.Cashback", "UK.OBIE.Insurance", "UK.OBIE.TravelDiscount", "UK.OBIE.TravelInsurance" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        }
      }, {
        "Type" : [ "UK.OBIE.Cashback", "UK.OBIE.Insurance", "UK.OBIE.TravelDiscount", "UK.OBIE.TravelInsurance" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        }
      } ],
      "StatementFee" : [ {
        "Type" : [ "UK.OBIE.Annual", "UK.OBIE.BalanceTransfer", "UK.OBIE.CashAdvance", "UK.OBIE.CashTransaction", "UK.OBIE.ForeignCashTransaction", "UK.OBIE.ForeignTransaction", "UK.OBIE.Gambling", "UK.OBIE.LatePayment", "UK.OBIE.MoneyTransfer", "UK.OBIE.Monthly", "UK.OBIE.Overlimit", "UK.OBIE.PostalOrder", "UK.OBIE.PrizeEntry", "UK.OBIE.StatementCopy", "UK.OBIE.Total" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      }, {
        "Type" : [ "UK.OBIE.Annual", "UK.OBIE.BalanceTransfer", "UK.OBIE.CashAdvance", "UK.OBIE.CashTransaction", "UK.OBIE.ForeignCashTransaction", "UK.OBIE.ForeignTransaction", "UK.OBIE.Gambling", "UK.OBIE.LatePayment", "UK.OBIE.MoneyTransfer", "UK.OBIE.Monthly", "UK.OBIE.Overlimit", "UK.OBIE.PostalOrder", "UK.OBIE.PrizeEntry", "UK.OBIE.StatementCopy", "UK.OBIE.Total" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      } ],
      "StatementDescription" : [ "StatementDescription", "StatementDescription" ],
      "StatementAmount" : [ {
        "Type" : [ "UK.OBIE.ArrearsClosingBalance", "UK.OBIE.AvailableBalance", "UK.OBIE.AverageBalanceWhenInCredit", "UK.OBIE.AverageBalanceWhenInDebit", "UK.OBIE.AverageDailyBalance", "UK.OBIE.BalanceTransferClosingBalance", "UK.OBIE.CashClosingBalance", "UK.OBIE.ClosingBalance", "UK.OBIE.CreditLimit", "UK.OBIE.CurrentPayment", "UK.OBIE.DirectDebitPaymentDue", "UK.OBIE.FSCSInsurance", "UK.OBIE.MinimumPaymentDue", "UK.OBIE.PendingTransactionsBalance", "UK.OBIE.PreviousClosingBalance", "UK.OBIE.PreviousPayment", "UK.OBIE.PurchaseClosingBalance", "UK.OBIE.StartingBalance", "UK.OBIE.TotalAdjustments", "UK.OBIE.TotalCashAdvances", "UK.OBIE.TotalCharges", "UK.OBIE.TotalCredits", "UK.OBIE.TotalDebits", "UK.OBIE.TotalPurchases" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      }, {
        "Type" : [ "UK.OBIE.ArrearsClosingBalance", "UK.OBIE.AvailableBalance", "UK.OBIE.AverageBalanceWhenInCredit", "UK.OBIE.AverageBalanceWhenInDebit", "UK.OBIE.AverageDailyBalance", "UK.OBIE.BalanceTransferClosingBalance", "UK.OBIE.CashClosingBalance", "UK.OBIE.ClosingBalance", "UK.OBIE.CreditLimit", "UK.OBIE.CurrentPayment", "UK.OBIE.DirectDebitPaymentDue", "UK.OBIE.FSCSInsurance", "UK.OBIE.MinimumPaymentDue", "UK.OBIE.PendingTransactionsBalance", "UK.OBIE.PreviousClosingBalance", "UK.OBIE.PreviousPayment", "UK.OBIE.PurchaseClosingBalance", "UK.OBIE.StartingBalance", "UK.OBIE.TotalAdjustments", "UK.OBIE.TotalCashAdvances", "UK.OBIE.TotalCharges", "UK.OBIE.TotalCredits", "UK.OBIE.TotalDebits", "UK.OBIE.TotalPurchases" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      } ]
    }, {
      "AccountId" : { },
      "StatementReference" : "StatementReference",
      "StatementDateTime" : [ {
        "Type" : [ "UK.OBIE.BalanceTransferPromoEnd", "UK.OBIE.DirectDebitDue", "UK.OBIE.LastPayment", "UK.OBIE.LastStatement", "UK.OBIE.NextStatement", "UK.OBIE.PaymentDue", "UK.OBIE.PurchasePromoEnd", "UK.OBIE.StatementAvailable" ],
        "DateTime" : "2000-01-23T04:56:07.000+00:00"
      }, {
        "Type" : [ "UK.OBIE.BalanceTransferPromoEnd", "UK.OBIE.DirectDebitDue", "UK.OBIE.LastPayment", "UK.OBIE.LastStatement", "UK.OBIE.NextStatement", "UK.OBIE.PaymentDue", "UK.OBIE.PurchasePromoEnd", "UK.OBIE.StatementAvailable" ],
        "DateTime" : "2000-01-23T04:56:07.000+00:00"
      } ],
      "StatementInterest" : [ {
        "Type" : [ "UK.OBIE.BalanceTransfer", "UK.OBIE.Cash", "UK.OBIE.EstimatedNext", "UK.OBIE.Purchase", "UK.OBIE.Total" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      }, {
        "Type" : [ "UK.OBIE.BalanceTransfer", "UK.OBIE.Cash", "UK.OBIE.EstimatedNext", "UK.OBIE.Purchase", "UK.OBIE.Total" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      } ],
      "Type" : { },
      "StartDateTime" : "2000-01-23T04:56:07.000+00:00",
      "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
      "StatementRate" : [ {
        "Type" : [ "UK.OBIE.AnnualBalanceTransfer", "UK.OBIE.AnnualBalanceTransferAfterPromo", "UK.OBIE.AnnualBalanceTransferPromo", "UK.OBIE.AnnualCash", "UK.OBIE.AnnualPurchase", "UK.OBIE.AnnualPurchaseAfterPromo", "UK.OBIE.AnnualPurchasePromo", "UK.OBIE.MonthlyBalanceTransfer", "UK.OBIE.MonthlyCash", "UK.OBIE.MonthlyPurchase" ],
        "Rate" : "Rate"
      }, {
        "Type" : [ "UK.OBIE.AnnualBalanceTransfer", "UK.OBIE.AnnualBalanceTransferAfterPromo", "UK.OBIE.AnnualBalanceTransferPromo", "UK.OBIE.AnnualCash", "UK.OBIE.AnnualPurchase", "UK.OBIE.AnnualPurchaseAfterPromo", "UK.OBIE.AnnualPurchasePromo", "UK.OBIE.MonthlyBalanceTransfer", "UK.OBIE.MonthlyCash", "UK.OBIE.MonthlyPurchase" ],
        "Rate" : "Rate"
      } ],
      "EndDateTime" : "2000-01-23T04:56:07.000+00:00",
      "StatementId" : "StatementId",
      "StatementValue" : [ {
        "Type" : [ "UK.OBIE.AirMilesPoints", "UK.OBIE.AirMilesPointsBalance", "UK.OBIE.Credits", "UK.OBIE.Debits", "UK.OBIE.HotelPoints", "UK.OBIE.HotelPointsBalance", "UK.OBIE.RetailShoppingPoints", "UK.OBIE.RetailShoppingPointsBalance" ],
        "Value" : 0
      }, {
        "Type" : [ "UK.OBIE.AirMilesPoints", "UK.OBIE.AirMilesPointsBalance", "UK.OBIE.Credits", "UK.OBIE.Debits", "UK.OBIE.HotelPoints", "UK.OBIE.HotelPointsBalance", "UK.OBIE.RetailShoppingPoints", "UK.OBIE.RetailShoppingPointsBalance" ],
        "Value" : 0
      } ],
      "StatementBenefit" : [ {
        "Type" : [ "UK.OBIE.Cashback", "UK.OBIE.Insurance", "UK.OBIE.TravelDiscount", "UK.OBIE.TravelInsurance" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        }
      }, {
        "Type" : [ "UK.OBIE.Cashback", "UK.OBIE.Insurance", "UK.OBIE.TravelDiscount", "UK.OBIE.TravelInsurance" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        }
      } ],
      "StatementFee" : [ {
        "Type" : [ "UK.OBIE.Annual", "UK.OBIE.BalanceTransfer", "UK.OBIE.CashAdvance", "UK.OBIE.CashTransaction", "UK.OBIE.ForeignCashTransaction", "UK.OBIE.ForeignTransaction", "UK.OBIE.Gambling", "UK.OBIE.LatePayment", "UK.OBIE.MoneyTransfer", "UK.OBIE.Monthly", "UK.OBIE.Overlimit", "UK.OBIE.PostalOrder", "UK.OBIE.PrizeEntry", "UK.OBIE.StatementCopy", "UK.OBIE.Total" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      }, {
        "Type" : [ "UK.OBIE.Annual", "UK.OBIE.BalanceTransfer", "UK.OBIE.CashAdvance", "UK.OBIE.CashTransaction", "UK.OBIE.ForeignCashTransaction", "UK.OBIE.ForeignTransaction", "UK.OBIE.Gambling", "UK.OBIE.LatePayment", "UK.OBIE.MoneyTransfer", "UK.OBIE.Monthly", "UK.OBIE.Overlimit", "UK.OBIE.PostalOrder", "UK.OBIE.PrizeEntry", "UK.OBIE.StatementCopy", "UK.OBIE.Total" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      } ],
      "StatementDescription" : [ "StatementDescription", "StatementDescription" ],
      "StatementAmount" : [ {
        "Type" : [ "UK.OBIE.ArrearsClosingBalance", "UK.OBIE.AvailableBalance", "UK.OBIE.AverageBalanceWhenInCredit", "UK.OBIE.AverageBalanceWhenInDebit", "UK.OBIE.AverageDailyBalance", "UK.OBIE.BalanceTransferClosingBalance", "UK.OBIE.CashClosingBalance", "UK.OBIE.ClosingBalance", "UK.OBIE.CreditLimit", "UK.OBIE.CurrentPayment", "UK.OBIE.DirectDebitPaymentDue", "UK.OBIE.FSCSInsurance", "UK.OBIE.MinimumPaymentDue", "UK.OBIE.PendingTransactionsBalance", "UK.OBIE.PreviousClosingBalance", "UK.OBIE.PreviousPayment", "UK.OBIE.PurchaseClosingBalance", "UK.OBIE.StartingBalance", "UK.OBIE.TotalAdjustments", "UK.OBIE.TotalCashAdvances", "UK.OBIE.TotalCharges", "UK.OBIE.TotalCredits", "UK.OBIE.TotalDebits", "UK.OBIE.TotalPurchases" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      }, {
        "Type" : [ "UK.OBIE.ArrearsClosingBalance", "UK.OBIE.AvailableBalance", "UK.OBIE.AverageBalanceWhenInCredit", "UK.OBIE.AverageBalanceWhenInDebit", "UK.OBIE.AverageDailyBalance", "UK.OBIE.BalanceTransferClosingBalance", "UK.OBIE.CashClosingBalance", "UK.OBIE.ClosingBalance", "UK.OBIE.CreditLimit", "UK.OBIE.CurrentPayment", "UK.OBIE.DirectDebitPaymentDue", "UK.OBIE.FSCSInsurance", "UK.OBIE.MinimumPaymentDue", "UK.OBIE.PendingTransactionsBalance", "UK.OBIE.PreviousClosingBalance", "UK.OBIE.PreviousPayment", "UK.OBIE.PurchaseClosingBalance", "UK.OBIE.StartingBalance", "UK.OBIE.TotalAdjustments", "UK.OBIE.TotalCashAdvances", "UK.OBIE.TotalCharges", "UK.OBIE.TotalCredits", "UK.OBIE.TotalDebits", "UK.OBIE.TotalPurchases" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      } ]
    } ]
  }
}"""),
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getAccountsAccountIdStatements)
  )

  // Deeper paths must come before the single-segment path in `routes`
  lazy val getAccountsAccountIdStatementsStatementIdFile: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "accounts" / _ / "statements" / _ / "file" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccountsAccountIdStatementsStatementIdFile),
    "GET",
    "/accounts/ACCOUNTID/statements/STATEMENTID/file",
    "Get Statements File",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    EmptyBody,
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getAccountsAccountIdStatementsStatementIdFile)
  )

  lazy val getAccountsAccountIdStatementsStatementIdTransactions: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "accounts" / _ / "statements" / _ / "transactions" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccountsAccountIdStatementsStatementIdTransactions),
    "GET",
    "/accounts/ACCOUNTID/statements/STATEMENTID/transactions",
    "Get Statement Transactions",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    parseBody("""{
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
    "Transaction" : [ {
      "Status" : { },
      "SupplementaryData" : { },
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
      "DebtorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "AccountId" : { },
      "TransactionReference" : "TransactionReference",
      "ProprietaryBankTransactionCode" : {
        "Issuer" : "Issuer",
        "Code" : "Code"
      },
      "AddressLine" : "AddressLine",
      "Amount" : {
        "Amount" : { },
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
          "Amount" : { },
          "Currency" : "Currency"
        },
        "TargetCurrency" : "TargetCurrency"
      },
      "StatementReference" : [ "StatementReference", "StatementReference" ],
      "ChargeAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "TransactionId" : "TransactionId",
      "TransactionInformation" : { },
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
        "AuthorisationType" : { },
        "Identification" : "Identification",
        "CardSchemeName" : { },
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
          "AddressType" : { },
          "PostCode" : "PostCode"
        },
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "Balance" : {
        "Type" : { },
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      }
    }, {
      "Status" : { },
      "SupplementaryData" : { },
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
      "DebtorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "AccountId" : { },
      "TransactionReference" : "TransactionReference",
      "ProprietaryBankTransactionCode" : {
        "Issuer" : "Issuer",
        "Code" : "Code"
      },
      "AddressLine" : "AddressLine",
      "Amount" : {
        "Amount" : { },
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
          "Amount" : { },
          "Currency" : "Currency"
        },
        "TargetCurrency" : "TargetCurrency"
      },
      "StatementReference" : [ "StatementReference", "StatementReference" ],
      "ChargeAmount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "TransactionId" : "TransactionId",
      "TransactionInformation" : { },
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
        "AuthorisationType" : { },
        "Identification" : "Identification",
        "CardSchemeName" : { },
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
          "AddressType" : { },
          "PostCode" : "PostCode"
        },
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "Balance" : {
        "Type" : { },
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      }
    } ]
  }
}"""),
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getAccountsAccountIdStatementsStatementIdTransactions)
  )

  lazy val getAccountsAccountIdStatementsStatementId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "accounts" / _ / "statements" / _ =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccountsAccountIdStatementsStatementId),
    "GET",
    "/accounts/ACCOUNTID/statements/STATEMENTID",
    "Get Statements",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    parseBody("""{
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
    "Statement" : [ {
      "AccountId" : { },
      "StatementReference" : "StatementReference",
      "StatementDateTime" : [ {
        "Type" : [ "UK.OBIE.BalanceTransferPromoEnd", "UK.OBIE.DirectDebitDue", "UK.OBIE.LastPayment", "UK.OBIE.LastStatement", "UK.OBIE.NextStatement", "UK.OBIE.PaymentDue", "UK.OBIE.PurchasePromoEnd", "UK.OBIE.StatementAvailable" ],
        "DateTime" : "2000-01-23T04:56:07.000+00:00"
      }, {
        "Type" : [ "UK.OBIE.BalanceTransferPromoEnd", "UK.OBIE.DirectDebitDue", "UK.OBIE.LastPayment", "UK.OBIE.LastStatement", "UK.OBIE.NextStatement", "UK.OBIE.PaymentDue", "UK.OBIE.PurchasePromoEnd", "UK.OBIE.StatementAvailable" ],
        "DateTime" : "2000-01-23T04:56:07.000+00:00"
      } ],
      "StatementInterest" : [ {
        "Type" : [ "UK.OBIE.BalanceTransfer", "UK.OBIE.Cash", "UK.OBIE.EstimatedNext", "UK.OBIE.Purchase", "UK.OBIE.Total" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      }, {
        "Type" : [ "UK.OBIE.BalanceTransfer", "UK.OBIE.Cash", "UK.OBIE.EstimatedNext", "UK.OBIE.Purchase", "UK.OBIE.Total" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      } ],
      "Type" : { },
      "StartDateTime" : "2000-01-23T04:56:07.000+00:00",
      "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
      "StatementRate" : [ {
        "Type" : [ "UK.OBIE.AnnualBalanceTransfer", "UK.OBIE.AnnualBalanceTransferAfterPromo", "UK.OBIE.AnnualBalanceTransferPromo", "UK.OBIE.AnnualCash", "UK.OBIE.AnnualPurchase", "UK.OBIE.AnnualPurchaseAfterPromo", "UK.OBIE.AnnualPurchasePromo", "UK.OBIE.MonthlyBalanceTransfer", "UK.OBIE.MonthlyCash", "UK.OBIE.MonthlyPurchase" ],
        "Rate" : "Rate"
      }, {
        "Type" : [ "UK.OBIE.AnnualBalanceTransfer", "UK.OBIE.AnnualBalanceTransferAfterPromo", "UK.OBIE.AnnualBalanceTransferPromo", "UK.OBIE.AnnualCash", "UK.OBIE.AnnualPurchase", "UK.OBIE.AnnualPurchaseAfterPromo", "UK.OBIE.AnnualPurchasePromo", "UK.OBIE.MonthlyBalanceTransfer", "UK.OBIE.MonthlyCash", "UK.OBIE.MonthlyPurchase" ],
        "Rate" : "Rate"
      } ],
      "EndDateTime" : "2000-01-23T04:56:07.000+00:00",
      "StatementId" : "StatementId",
      "StatementValue" : [ {
        "Type" : [ "UK.OBIE.AirMilesPoints", "UK.OBIE.AirMilesPointsBalance", "UK.OBIE.Credits", "UK.OBIE.Debits", "UK.OBIE.HotelPoints", "UK.OBIE.HotelPointsBalance", "UK.OBIE.RetailShoppingPoints", "UK.OBIE.RetailShoppingPointsBalance" ],
        "Value" : 0
      }, {
        "Type" : [ "UK.OBIE.AirMilesPoints", "UK.OBIE.AirMilesPointsBalance", "UK.OBIE.Credits", "UK.OBIE.Debits", "UK.OBIE.HotelPoints", "UK.OBIE.HotelPointsBalance", "UK.OBIE.RetailShoppingPoints", "UK.OBIE.RetailShoppingPointsBalance" ],
        "Value" : 0
      } ],
      "StatementBenefit" : [ {
        "Type" : [ "UK.OBIE.Cashback", "UK.OBIE.Insurance", "UK.OBIE.TravelDiscount", "UK.OBIE.TravelInsurance" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        }
      }, {
        "Type" : [ "UK.OBIE.Cashback", "UK.OBIE.Insurance", "UK.OBIE.TravelDiscount", "UK.OBIE.TravelInsurance" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        }
      } ],
      "StatementFee" : [ {
        "Type" : [ "UK.OBIE.Annual", "UK.OBIE.BalanceTransfer", "UK.OBIE.CashAdvance", "UK.OBIE.CashTransaction", "UK.OBIE.ForeignCashTransaction", "UK.OBIE.ForeignTransaction", "UK.OBIE.Gambling", "UK.OBIE.LatePayment", "UK.OBIE.MoneyTransfer", "UK.OBIE.Monthly", "UK.OBIE.Overlimit", "UK.OBIE.PostalOrder", "UK.OBIE.PrizeEntry", "UK.OBIE.StatementCopy", "UK.OBIE.Total" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      }, {
        "Type" : [ "UK.OBIE.Annual", "UK.OBIE.BalanceTransfer", "UK.OBIE.CashAdvance", "UK.OBIE.CashTransaction", "UK.OBIE.ForeignCashTransaction", "UK.OBIE.ForeignTransaction", "UK.OBIE.Gambling", "UK.OBIE.LatePayment", "UK.OBIE.MoneyTransfer", "UK.OBIE.Monthly", "UK.OBIE.Overlimit", "UK.OBIE.PostalOrder", "UK.OBIE.PrizeEntry", "UK.OBIE.StatementCopy", "UK.OBIE.Total" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      } ],
      "StatementDescription" : [ "StatementDescription", "StatementDescription" ],
      "StatementAmount" : [ {
        "Type" : [ "UK.OBIE.ArrearsClosingBalance", "UK.OBIE.AvailableBalance", "UK.OBIE.AverageBalanceWhenInCredit", "UK.OBIE.AverageBalanceWhenInDebit", "UK.OBIE.AverageDailyBalance", "UK.OBIE.BalanceTransferClosingBalance", "UK.OBIE.CashClosingBalance", "UK.OBIE.ClosingBalance", "UK.OBIE.CreditLimit", "UK.OBIE.CurrentPayment", "UK.OBIE.DirectDebitPaymentDue", "UK.OBIE.FSCSInsurance", "UK.OBIE.MinimumPaymentDue", "UK.OBIE.PendingTransactionsBalance", "UK.OBIE.PreviousClosingBalance", "UK.OBIE.PreviousPayment", "UK.OBIE.PurchaseClosingBalance", "UK.OBIE.StartingBalance", "UK.OBIE.TotalAdjustments", "UK.OBIE.TotalCashAdvances", "UK.OBIE.TotalCharges", "UK.OBIE.TotalCredits", "UK.OBIE.TotalDebits", "UK.OBIE.TotalPurchases" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      }, {
        "Type" : [ "UK.OBIE.ArrearsClosingBalance", "UK.OBIE.AvailableBalance", "UK.OBIE.AverageBalanceWhenInCredit", "UK.OBIE.AverageBalanceWhenInDebit", "UK.OBIE.AverageDailyBalance", "UK.OBIE.BalanceTransferClosingBalance", "UK.OBIE.CashClosingBalance", "UK.OBIE.ClosingBalance", "UK.OBIE.CreditLimit", "UK.OBIE.CurrentPayment", "UK.OBIE.DirectDebitPaymentDue", "UK.OBIE.FSCSInsurance", "UK.OBIE.MinimumPaymentDue", "UK.OBIE.PendingTransactionsBalance", "UK.OBIE.PreviousClosingBalance", "UK.OBIE.PreviousPayment", "UK.OBIE.PurchaseClosingBalance", "UK.OBIE.StartingBalance", "UK.OBIE.TotalAdjustments", "UK.OBIE.TotalCashAdvances", "UK.OBIE.TotalCharges", "UK.OBIE.TotalCredits", "UK.OBIE.TotalDebits", "UK.OBIE.TotalPurchases" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      } ]
    }, {
      "AccountId" : { },
      "StatementReference" : "StatementReference",
      "StatementDateTime" : [ {
        "Type" : [ "UK.OBIE.BalanceTransferPromoEnd", "UK.OBIE.DirectDebitDue", "UK.OBIE.LastPayment", "UK.OBIE.LastStatement", "UK.OBIE.NextStatement", "UK.OBIE.PaymentDue", "UK.OBIE.PurchasePromoEnd", "UK.OBIE.StatementAvailable" ],
        "DateTime" : "2000-01-23T04:56:07.000+00:00"
      }, {
        "Type" : [ "UK.OBIE.BalanceTransferPromoEnd", "UK.OBIE.DirectDebitDue", "UK.OBIE.LastPayment", "UK.OBIE.LastStatement", "UK.OBIE.NextStatement", "UK.OBIE.PaymentDue", "UK.OBIE.PurchasePromoEnd", "UK.OBIE.StatementAvailable" ],
        "DateTime" : "2000-01-23T04:56:07.000+00:00"
      } ],
      "StatementInterest" : [ {
        "Type" : [ "UK.OBIE.BalanceTransfer", "UK.OBIE.Cash", "UK.OBIE.EstimatedNext", "UK.OBIE.Purchase", "UK.OBIE.Total" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      }, {
        "Type" : [ "UK.OBIE.BalanceTransfer", "UK.OBIE.Cash", "UK.OBIE.EstimatedNext", "UK.OBIE.Purchase", "UK.OBIE.Total" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      } ],
      "Type" : { },
      "StartDateTime" : "2000-01-23T04:56:07.000+00:00",
      "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
      "StatementRate" : [ {
        "Type" : [ "UK.OBIE.AnnualBalanceTransfer", "UK.OBIE.AnnualBalanceTransferAfterPromo", "UK.OBIE.AnnualBalanceTransferPromo", "UK.OBIE.AnnualCash", "UK.OBIE.AnnualPurchase", "UK.OBIE.AnnualPurchaseAfterPromo", "UK.OBIE.AnnualPurchasePromo", "UK.OBIE.MonthlyBalanceTransfer", "UK.OBIE.MonthlyCash", "UK.OBIE.MonthlyPurchase" ],
        "Rate" : "Rate"
      }, {
        "Type" : [ "UK.OBIE.AnnualBalanceTransfer", "UK.OBIE.AnnualBalanceTransferAfterPromo", "UK.OBIE.AnnualBalanceTransferPromo", "UK.OBIE.AnnualCash", "UK.OBIE.AnnualPurchase", "UK.OBIE.AnnualPurchaseAfterPromo", "UK.OBIE.AnnualPurchasePromo", "UK.OBIE.MonthlyBalanceTransfer", "UK.OBIE.MonthlyCash", "UK.OBIE.MonthlyPurchase" ],
        "Rate" : "Rate"
      } ],
      "EndDateTime" : "2000-01-23T04:56:07.000+00:00",
      "StatementId" : "StatementId",
      "StatementValue" : [ {
        "Type" : [ "UK.OBIE.AirMilesPoints", "UK.OBIE.AirMilesPointsBalance", "UK.OBIE.Credits", "UK.OBIE.Debits", "UK.OBIE.HotelPoints", "UK.OBIE.HotelPointsBalance", "UK.OBIE.RetailShoppingPoints", "UK.OBIE.RetailShoppingPointsBalance" ],
        "Value" : 0
      }, {
        "Type" : [ "UK.OBIE.AirMilesPoints", "UK.OBIE.AirMilesPointsBalance", "UK.OBIE.Credits", "UK.OBIE.Debits", "UK.OBIE.HotelPoints", "UK.OBIE.HotelPointsBalance", "UK.OBIE.RetailShoppingPoints", "UK.OBIE.RetailShoppingPointsBalance" ],
        "Value" : 0
      } ],
      "StatementBenefit" : [ {
        "Type" : [ "UK.OBIE.Cashback", "UK.OBIE.Insurance", "UK.OBIE.TravelDiscount", "UK.OBIE.TravelInsurance" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        }
      }, {
        "Type" : [ "UK.OBIE.Cashback", "UK.OBIE.Insurance", "UK.OBIE.TravelDiscount", "UK.OBIE.TravelInsurance" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        }
      } ],
      "StatementFee" : [ {
        "Type" : [ "UK.OBIE.Annual", "UK.OBIE.BalanceTransfer", "UK.OBIE.CashAdvance", "UK.OBIE.CashTransaction", "UK.OBIE.ForeignCashTransaction", "UK.OBIE.ForeignTransaction", "UK.OBIE.Gambling", "UK.OBIE.LatePayment", "UK.OBIE.MoneyTransfer", "UK.OBIE.Monthly", "UK.OBIE.Overlimit", "UK.OBIE.PostalOrder", "UK.OBIE.PrizeEntry", "UK.OBIE.StatementCopy", "UK.OBIE.Total" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      }, {
        "Type" : [ "UK.OBIE.Annual", "UK.OBIE.BalanceTransfer", "UK.OBIE.CashAdvance", "UK.OBIE.CashTransaction", "UK.OBIE.ForeignCashTransaction", "UK.OBIE.ForeignTransaction", "UK.OBIE.Gambling", "UK.OBIE.LatePayment", "UK.OBIE.MoneyTransfer", "UK.OBIE.Monthly", "UK.OBIE.Overlimit", "UK.OBIE.PostalOrder", "UK.OBIE.PrizeEntry", "UK.OBIE.StatementCopy", "UK.OBIE.Total" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      } ],
      "StatementDescription" : [ "StatementDescription", "StatementDescription" ],
      "StatementAmount" : [ {
        "Type" : [ "UK.OBIE.ArrearsClosingBalance", "UK.OBIE.AvailableBalance", "UK.OBIE.AverageBalanceWhenInCredit", "UK.OBIE.AverageBalanceWhenInDebit", "UK.OBIE.AverageDailyBalance", "UK.OBIE.BalanceTransferClosingBalance", "UK.OBIE.CashClosingBalance", "UK.OBIE.ClosingBalance", "UK.OBIE.CreditLimit", "UK.OBIE.CurrentPayment", "UK.OBIE.DirectDebitPaymentDue", "UK.OBIE.FSCSInsurance", "UK.OBIE.MinimumPaymentDue", "UK.OBIE.PendingTransactionsBalance", "UK.OBIE.PreviousClosingBalance", "UK.OBIE.PreviousPayment", "UK.OBIE.PurchaseClosingBalance", "UK.OBIE.StartingBalance", "UK.OBIE.TotalAdjustments", "UK.OBIE.TotalCashAdvances", "UK.OBIE.TotalCharges", "UK.OBIE.TotalCredits", "UK.OBIE.TotalDebits", "UK.OBIE.TotalPurchases" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      }, {
        "Type" : [ "UK.OBIE.ArrearsClosingBalance", "UK.OBIE.AvailableBalance", "UK.OBIE.AverageBalanceWhenInCredit", "UK.OBIE.AverageBalanceWhenInDebit", "UK.OBIE.AverageDailyBalance", "UK.OBIE.BalanceTransferClosingBalance", "UK.OBIE.CashClosingBalance", "UK.OBIE.ClosingBalance", "UK.OBIE.CreditLimit", "UK.OBIE.CurrentPayment", "UK.OBIE.DirectDebitPaymentDue", "UK.OBIE.FSCSInsurance", "UK.OBIE.MinimumPaymentDue", "UK.OBIE.PendingTransactionsBalance", "UK.OBIE.PreviousClosingBalance", "UK.OBIE.PreviousPayment", "UK.OBIE.PurchaseClosingBalance", "UK.OBIE.StartingBalance", "UK.OBIE.TotalAdjustments", "UK.OBIE.TotalCashAdvances", "UK.OBIE.TotalCharges", "UK.OBIE.TotalCredits", "UK.OBIE.TotalDebits", "UK.OBIE.TotalPurchases" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      } ]
    } ]
  }
}"""),
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getAccountsAccountIdStatementsStatementId)
  )

  lazy val getStatements: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "statements" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getStatements),
    "GET",
    "/statements",
    "Get Statements",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    parseBody("""{
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
    "Statement" : [ {
      "AccountId" : { },
      "StatementReference" : "StatementReference",
      "StatementDateTime" : [ {
        "Type" : [ "UK.OBIE.BalanceTransferPromoEnd", "UK.OBIE.DirectDebitDue", "UK.OBIE.LastPayment", "UK.OBIE.LastStatement", "UK.OBIE.NextStatement", "UK.OBIE.PaymentDue", "UK.OBIE.PurchasePromoEnd", "UK.OBIE.StatementAvailable" ],
        "DateTime" : "2000-01-23T04:56:07.000+00:00"
      }, {
        "Type" : [ "UK.OBIE.BalanceTransferPromoEnd", "UK.OBIE.DirectDebitDue", "UK.OBIE.LastPayment", "UK.OBIE.LastStatement", "UK.OBIE.NextStatement", "UK.OBIE.PaymentDue", "UK.OBIE.PurchasePromoEnd", "UK.OBIE.StatementAvailable" ],
        "DateTime" : "2000-01-23T04:56:07.000+00:00"
      } ],
      "StatementInterest" : [ {
        "Type" : [ "UK.OBIE.BalanceTransfer", "UK.OBIE.Cash", "UK.OBIE.EstimatedNext", "UK.OBIE.Purchase", "UK.OBIE.Total" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      }, {
        "Type" : [ "UK.OBIE.BalanceTransfer", "UK.OBIE.Cash", "UK.OBIE.EstimatedNext", "UK.OBIE.Purchase", "UK.OBIE.Total" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      } ],
      "Type" : { },
      "StartDateTime" : "2000-01-23T04:56:07.000+00:00",
      "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
      "StatementRate" : [ {
        "Type" : [ "UK.OBIE.AnnualBalanceTransfer", "UK.OBIE.AnnualBalanceTransferAfterPromo", "UK.OBIE.AnnualBalanceTransferPromo", "UK.OBIE.AnnualCash", "UK.OBIE.AnnualPurchase", "UK.OBIE.AnnualPurchaseAfterPromo", "UK.OBIE.AnnualPurchasePromo", "UK.OBIE.MonthlyBalanceTransfer", "UK.OBIE.MonthlyCash", "UK.OBIE.MonthlyPurchase" ],
        "Rate" : "Rate"
      }, {
        "Type" : [ "UK.OBIE.AnnualBalanceTransfer", "UK.OBIE.AnnualBalanceTransferAfterPromo", "UK.OBIE.AnnualBalanceTransferPromo", "UK.OBIE.AnnualCash", "UK.OBIE.AnnualPurchase", "UK.OBIE.AnnualPurchaseAfterPromo", "UK.OBIE.AnnualPurchasePromo", "UK.OBIE.MonthlyBalanceTransfer", "UK.OBIE.MonthlyCash", "UK.OBIE.MonthlyPurchase" ],
        "Rate" : "Rate"
      } ],
      "EndDateTime" : "2000-01-23T04:56:07.000+00:00",
      "StatementId" : "StatementId",
      "StatementValue" : [ {
        "Type" : [ "UK.OBIE.AirMilesPoints", "UK.OBIE.AirMilesPointsBalance", "UK.OBIE.Credits", "UK.OBIE.Debits", "UK.OBIE.HotelPoints", "UK.OBIE.HotelPointsBalance", "UK.OBIE.RetailShoppingPoints", "UK.OBIE.RetailShoppingPointsBalance" ],
        "Value" : 0
      }, {
        "Type" : [ "UK.OBIE.AirMilesPoints", "UK.OBIE.AirMilesPointsBalance", "UK.OBIE.Credits", "UK.OBIE.Debits", "UK.OBIE.HotelPoints", "UK.OBIE.HotelPointsBalance", "UK.OBIE.RetailShoppingPoints", "UK.OBIE.RetailShoppingPointsBalance" ],
        "Value" : 0
      } ],
      "StatementBenefit" : [ {
        "Type" : [ "UK.OBIE.Cashback", "UK.OBIE.Insurance", "UK.OBIE.TravelDiscount", "UK.OBIE.TravelInsurance" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        }
      }, {
        "Type" : [ "UK.OBIE.Cashback", "UK.OBIE.Insurance", "UK.OBIE.TravelDiscount", "UK.OBIE.TravelInsurance" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        }
      } ],
      "StatementFee" : [ {
        "Type" : [ "UK.OBIE.Annual", "UK.OBIE.BalanceTransfer", "UK.OBIE.CashAdvance", "UK.OBIE.CashTransaction", "UK.OBIE.ForeignCashTransaction", "UK.OBIE.ForeignTransaction", "UK.OBIE.Gambling", "UK.OBIE.LatePayment", "UK.OBIE.MoneyTransfer", "UK.OBIE.Monthly", "UK.OBIE.Overlimit", "UK.OBIE.PostalOrder", "UK.OBIE.PrizeEntry", "UK.OBIE.StatementCopy", "UK.OBIE.Total" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      }, {
        "Type" : [ "UK.OBIE.Annual", "UK.OBIE.BalanceTransfer", "UK.OBIE.CashAdvance", "UK.OBIE.CashTransaction", "UK.OBIE.ForeignCashTransaction", "UK.OBIE.ForeignTransaction", "UK.OBIE.Gambling", "UK.OBIE.LatePayment", "UK.OBIE.MoneyTransfer", "UK.OBIE.Monthly", "UK.OBIE.Overlimit", "UK.OBIE.PostalOrder", "UK.OBIE.PrizeEntry", "UK.OBIE.StatementCopy", "UK.OBIE.Total" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      } ],
      "StatementDescription" : [ "StatementDescription", "StatementDescription" ],
      "StatementAmount" : [ {
        "Type" : [ "UK.OBIE.ArrearsClosingBalance", "UK.OBIE.AvailableBalance", "UK.OBIE.AverageBalanceWhenInCredit", "UK.OBIE.AverageBalanceWhenInDebit", "UK.OBIE.AverageDailyBalance", "UK.OBIE.BalanceTransferClosingBalance", "UK.OBIE.CashClosingBalance", "UK.OBIE.ClosingBalance", "UK.OBIE.CreditLimit", "UK.OBIE.CurrentPayment", "UK.OBIE.DirectDebitPaymentDue", "UK.OBIE.FSCSInsurance", "UK.OBIE.MinimumPaymentDue", "UK.OBIE.PendingTransactionsBalance", "UK.OBIE.PreviousClosingBalance", "UK.OBIE.PreviousPayment", "UK.OBIE.PurchaseClosingBalance", "UK.OBIE.StartingBalance", "UK.OBIE.TotalAdjustments", "UK.OBIE.TotalCashAdvances", "UK.OBIE.TotalCharges", "UK.OBIE.TotalCredits", "UK.OBIE.TotalDebits", "UK.OBIE.TotalPurchases" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      }, {
        "Type" : [ "UK.OBIE.ArrearsClosingBalance", "UK.OBIE.AvailableBalance", "UK.OBIE.AverageBalanceWhenInCredit", "UK.OBIE.AverageBalanceWhenInDebit", "UK.OBIE.AverageDailyBalance", "UK.OBIE.BalanceTransferClosingBalance", "UK.OBIE.CashClosingBalance", "UK.OBIE.ClosingBalance", "UK.OBIE.CreditLimit", "UK.OBIE.CurrentPayment", "UK.OBIE.DirectDebitPaymentDue", "UK.OBIE.FSCSInsurance", "UK.OBIE.MinimumPaymentDue", "UK.OBIE.PendingTransactionsBalance", "UK.OBIE.PreviousClosingBalance", "UK.OBIE.PreviousPayment", "UK.OBIE.PurchaseClosingBalance", "UK.OBIE.StartingBalance", "UK.OBIE.TotalAdjustments", "UK.OBIE.TotalCashAdvances", "UK.OBIE.TotalCharges", "UK.OBIE.TotalCredits", "UK.OBIE.TotalDebits", "UK.OBIE.TotalPurchases" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      } ]
    }, {
      "AccountId" : { },
      "StatementReference" : "StatementReference",
      "StatementDateTime" : [ {
        "Type" : [ "UK.OBIE.BalanceTransferPromoEnd", "UK.OBIE.DirectDebitDue", "UK.OBIE.LastPayment", "UK.OBIE.LastStatement", "UK.OBIE.NextStatement", "UK.OBIE.PaymentDue", "UK.OBIE.PurchasePromoEnd", "UK.OBIE.StatementAvailable" ],
        "DateTime" : "2000-01-23T04:56:07.000+00:00"
      }, {
        "Type" : [ "UK.OBIE.BalanceTransferPromoEnd", "UK.OBIE.DirectDebitDue", "UK.OBIE.LastPayment", "UK.OBIE.LastStatement", "UK.OBIE.NextStatement", "UK.OBIE.PaymentDue", "UK.OBIE.PurchasePromoEnd", "UK.OBIE.StatementAvailable" ],
        "DateTime" : "2000-01-23T04:56:07.000+00:00"
      } ],
      "StatementInterest" : [ {
        "Type" : [ "UK.OBIE.BalanceTransfer", "UK.OBIE.Cash", "UK.OBIE.EstimatedNext", "UK.OBIE.Purchase", "UK.OBIE.Total" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      }, {
        "Type" : [ "UK.OBIE.BalanceTransfer", "UK.OBIE.Cash", "UK.OBIE.EstimatedNext", "UK.OBIE.Purchase", "UK.OBIE.Total" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      } ],
      "Type" : { },
      "StartDateTime" : "2000-01-23T04:56:07.000+00:00",
      "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
      "StatementRate" : [ {
        "Type" : [ "UK.OBIE.AnnualBalanceTransfer", "UK.OBIE.AnnualBalanceTransferAfterPromo", "UK.OBIE.AnnualBalanceTransferPromo", "UK.OBIE.AnnualCash", "UK.OBIE.AnnualPurchase", "UK.OBIE.AnnualPurchaseAfterPromo", "UK.OBIE.AnnualPurchasePromo", "UK.OBIE.MonthlyBalanceTransfer", "UK.OBIE.MonthlyCash", "UK.OBIE.MonthlyPurchase" ],
        "Rate" : "Rate"
      }, {
        "Type" : [ "UK.OBIE.AnnualBalanceTransfer", "UK.OBIE.AnnualBalanceTransferAfterPromo", "UK.OBIE.AnnualBalanceTransferPromo", "UK.OBIE.AnnualCash", "UK.OBIE.AnnualPurchase", "UK.OBIE.AnnualPurchaseAfterPromo", "UK.OBIE.AnnualPurchasePromo", "UK.OBIE.MonthlyBalanceTransfer", "UK.OBIE.MonthlyCash", "UK.OBIE.MonthlyPurchase" ],
        "Rate" : "Rate"
      } ],
      "EndDateTime" : "2000-01-23T04:56:07.000+00:00",
      "StatementId" : "StatementId",
      "StatementValue" : [ {
        "Type" : [ "UK.OBIE.AirMilesPoints", "UK.OBIE.AirMilesPointsBalance", "UK.OBIE.Credits", "UK.OBIE.Debits", "UK.OBIE.HotelPoints", "UK.OBIE.HotelPointsBalance", "UK.OBIE.RetailShoppingPoints", "UK.OBIE.RetailShoppingPointsBalance" ],
        "Value" : 0
      }, {
        "Type" : [ "UK.OBIE.AirMilesPoints", "UK.OBIE.AirMilesPointsBalance", "UK.OBIE.Credits", "UK.OBIE.Debits", "UK.OBIE.HotelPoints", "UK.OBIE.HotelPointsBalance", "UK.OBIE.RetailShoppingPoints", "UK.OBIE.RetailShoppingPointsBalance" ],
        "Value" : 0
      } ],
      "StatementBenefit" : [ {
        "Type" : [ "UK.OBIE.Cashback", "UK.OBIE.Insurance", "UK.OBIE.TravelDiscount", "UK.OBIE.TravelInsurance" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        }
      }, {
        "Type" : [ "UK.OBIE.Cashback", "UK.OBIE.Insurance", "UK.OBIE.TravelDiscount", "UK.OBIE.TravelInsurance" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        }
      } ],
      "StatementFee" : [ {
        "Type" : [ "UK.OBIE.Annual", "UK.OBIE.BalanceTransfer", "UK.OBIE.CashAdvance", "UK.OBIE.CashTransaction", "UK.OBIE.ForeignCashTransaction", "UK.OBIE.ForeignTransaction", "UK.OBIE.Gambling", "UK.OBIE.LatePayment", "UK.OBIE.MoneyTransfer", "UK.OBIE.Monthly", "UK.OBIE.Overlimit", "UK.OBIE.PostalOrder", "UK.OBIE.PrizeEntry", "UK.OBIE.StatementCopy", "UK.OBIE.Total" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      }, {
        "Type" : [ "UK.OBIE.Annual", "UK.OBIE.BalanceTransfer", "UK.OBIE.CashAdvance", "UK.OBIE.CashTransaction", "UK.OBIE.ForeignCashTransaction", "UK.OBIE.ForeignTransaction", "UK.OBIE.Gambling", "UK.OBIE.LatePayment", "UK.OBIE.MoneyTransfer", "UK.OBIE.Monthly", "UK.OBIE.Overlimit", "UK.OBIE.PostalOrder", "UK.OBIE.PrizeEntry", "UK.OBIE.StatementCopy", "UK.OBIE.Total" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      } ],
      "StatementDescription" : [ "StatementDescription", "StatementDescription" ],
      "StatementAmount" : [ {
        "Type" : [ "UK.OBIE.ArrearsClosingBalance", "UK.OBIE.AvailableBalance", "UK.OBIE.AverageBalanceWhenInCredit", "UK.OBIE.AverageBalanceWhenInDebit", "UK.OBIE.AverageDailyBalance", "UK.OBIE.BalanceTransferClosingBalance", "UK.OBIE.CashClosingBalance", "UK.OBIE.ClosingBalance", "UK.OBIE.CreditLimit", "UK.OBIE.CurrentPayment", "UK.OBIE.DirectDebitPaymentDue", "UK.OBIE.FSCSInsurance", "UK.OBIE.MinimumPaymentDue", "UK.OBIE.PendingTransactionsBalance", "UK.OBIE.PreviousClosingBalance", "UK.OBIE.PreviousPayment", "UK.OBIE.PurchaseClosingBalance", "UK.OBIE.StartingBalance", "UK.OBIE.TotalAdjustments", "UK.OBIE.TotalCashAdvances", "UK.OBIE.TotalCharges", "UK.OBIE.TotalCredits", "UK.OBIE.TotalDebits", "UK.OBIE.TotalPurchases" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      }, {
        "Type" : [ "UK.OBIE.ArrearsClosingBalance", "UK.OBIE.AvailableBalance", "UK.OBIE.AverageBalanceWhenInCredit", "UK.OBIE.AverageBalanceWhenInDebit", "UK.OBIE.AverageDailyBalance", "UK.OBIE.BalanceTransferClosingBalance", "UK.OBIE.CashClosingBalance", "UK.OBIE.ClosingBalance", "UK.OBIE.CreditLimit", "UK.OBIE.CurrentPayment", "UK.OBIE.DirectDebitPaymentDue", "UK.OBIE.FSCSInsurance", "UK.OBIE.MinimumPaymentDue", "UK.OBIE.PendingTransactionsBalance", "UK.OBIE.PreviousClosingBalance", "UK.OBIE.PreviousPayment", "UK.OBIE.PurchaseClosingBalance", "UK.OBIE.StartingBalance", "UK.OBIE.TotalAdjustments", "UK.OBIE.TotalCashAdvances", "UK.OBIE.TotalCharges", "UK.OBIE.TotalCredits", "UK.OBIE.TotalDebits", "UK.OBIE.TotalPurchases" ],
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        },
        "CreditDebitIndicator" : "Credit"
      } ]
    } ]
  }
}"""),
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getStatements)
  )

  // Routes ordered deep-first to avoid the single-wildcard pattern swallowing deeper paths
  val routes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    getAccountsAccountIdStatementsStatementIdFile(req)
      .orElse(getAccountsAccountIdStatementsStatementIdTransactions(req))
      .orElse(getAccountsAccountIdStatementsStatementId(req))
      .orElse(getAccountsAccountIdStatements(req))
      .orElse(getStatements(req))
  }
}
