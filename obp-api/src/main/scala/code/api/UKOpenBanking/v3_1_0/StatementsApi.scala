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

object APIMethods_StatementsApi extends RestHelper {
    val apiVersion = OBP_UKOpenBanking_310.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      getAccountsAccountIdStatements ::
      getAccountsAccountIdStatementsStatementId ::
      getAccountsAccountIdStatementsStatementIdFile ::
      getAccountsAccountIdStatementsStatementIdTransactions ::
      getStatements ::
      Nil

            
     resourceDocs += ResourceDoc(
       getAccountsAccountIdStatements, 
       apiVersion, 
       nameOf(getAccountsAccountIdStatements),
       "GET", 
       "/accounts/ACCOUNTID/statements", 
       "Get Statements",
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
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Statements") :: apiTagMockedData :: Nil
     )

     lazy val getAccountsAccountIdStatements : OBPEndpoint = {
       case "accounts" :: accountid:: "statements" :: Nil JsonGet _ => {
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
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getAccountsAccountIdStatementsStatementId, 
       apiVersion, 
       nameOf(getAccountsAccountIdStatementsStatementId),
       "GET", 
       "/accounts/ACCOUNTID/statements/STATEMENTID", 
       "Get Statements",
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
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Statements") :: apiTagMockedData :: Nil
     )

     lazy val getAccountsAccountIdStatementsStatementId : OBPEndpoint = {
       case "accounts" :: accountid:: "statements" :: statementid :: Nil JsonGet _ => {
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
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getAccountsAccountIdStatementsStatementIdFile, 
       apiVersion, 
       nameOf(getAccountsAccountIdStatementsStatementIdFile),
       "GET", 
       "/accounts/ACCOUNTID/statements/STATEMENTID/file", 
       "Get Statements",
       s"""${mockedDataText(true)}
""", 
       emptyObjectJson,
       emptyObjectJson,
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Statements") :: apiTagMockedData :: Nil
     )

     lazy val getAccountsAccountIdStatementsStatementIdFile : OBPEndpoint = {
       case "accounts" :: accountid:: "statements" :: statementid:: "file" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getAccountsAccountIdStatementsStatementIdTransactions, 
       apiVersion, 
       nameOf(getAccountsAccountIdStatementsStatementIdTransactions),
       "GET", 
       "/accounts/ACCOUNTID/statements/STATEMENTID/transactions", 
       "Get Transactions",
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
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Statements") ::ApiTag("Transactions") :: apiTagMockedData :: Nil
     )

     lazy val getAccountsAccountIdStatementsStatementIdTransactions : OBPEndpoint = {
       case "accounts" :: accountid:: "statements" :: statementid:: "transactions" :: Nil JsonGet _ => {
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
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getStatements, 
       apiVersion, 
       nameOf(getStatements),
       "GET", 
       "/statements", 
       "Get Statements",
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
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Statements") :: apiTagMockedData :: Nil
     )

     lazy val getStatements : OBPEndpoint = {
       case "statements" :: Nil JsonGet _ => {
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
}"""), callContext)
           }
         }
       }

}



