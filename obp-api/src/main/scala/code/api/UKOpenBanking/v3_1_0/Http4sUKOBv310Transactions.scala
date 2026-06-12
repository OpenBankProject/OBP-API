package code.api.UKOpenBanking.v3_1_0

import org.json4s._
import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import code.api.APIFailureNewStyle
import code.api.Constant
import code.api.util.APIUtil.{HTTPParam, EmptyBody, ResourceDoc, createQueriesByHttpParams, defaultBankId, fullBoxOrException, mockedDataText, passesPsd2Aisp, unboxFull}
import code.api.util.ApiTag
import code.api.util.ApiTag._
import code.api.util.CustomJsonFormats
import code.api.util.ErrorMessages
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, UnknownError}
import code.api.util.NewStyle
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.api.util.newstyle.ViewNewStyle
import code.util.Helper.MdcLoggable
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{AccountId, BankId, BankIdAccountId, TransactionAttribute, ViewId}
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}
import net.liftweb.common.Full
import code.model.{BankAccountExtended, UserExtended}
import com.openbankproject.commons.util.json
import org.json4s.Formats
import org.http4s._
import org.http4s.dsl.io._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

/**
 * UK Open Banking v3.1 — TransactionsApi, migrated from Lift to http4s.
 *
 * getAccountsAccountIdStatementsStatementIdTransactions is declared as a stub in
 * Http4sUKOBv310Statements (which takes precedence in the Lift priority order).
 * This file owns:
 *   - getAccountsAccountIdTransactions: real business logic with UK consent check.
 *   - getTransactions: real business logic, bulk (no consent check, mirrors Lift).
 */
object Http4sUKOBv310Transactions extends MdcLoggable {
  type HttpF[A] = OptionT[IO, A]
  implicit val formats: Formats = CustomJsonFormats.formats
  val implementedInApiVersion: ScannedApiVersion = ApiVersion.ukOpenBankingV31
  val resourceDocs = ArrayBuffer[ResourceDoc]()
  private def parseBody(s: String): org.json4s.JObject = com.openbankproject.commons.util.JsonAliases.parse(s).asInstanceOf[org.json4s.JObject]
  val ukV31Prefix = Root / ApiVersion.ukOpenBankingV31.urlPrefix / ApiVersion.ukOpenBankingV31.apiShortVersion

  // -----------------------------------------------------------------------
  // getAccountsAccountIdStatementsStatementIdTransactions (stub)
  // The Lift handler for this route returned hard-coded mock JSON after auth.
  // Http4sUKOBv310Statements declares an identical route as a stub and is wired
  // before this object in Http4sUKOBv310.allRoutes, so the Statements stub takes
  // precedence at runtime.  We re-register the ResourceDoc here so that the doc
  // entry appears under the "Transactions" tag (matching the Lift source of truth)
  // in addition to the "Statements" entry.
  // -----------------------------------------------------------------------
  lazy val getAccountsAccountIdStatementsStatementIdTransactions: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "accounts" / _ / "statements" / _ / "transactions" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccountsAccountIdStatementsStatementIdTransactions),
    "GET",
    "/accounts/ACCOUNTID/statements/STATEMENTID/transactions",
    "Get Transactions",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    parseBody("""{
  "Meta" : {
    "FirstAvailableDateTime": "2019-03-06T07:38:51.169Z",
    "LastAvailableDateTime": "2019-03-06T07:38:51.169Z",
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
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Statements") :: ApiTag("Transactions") :: apiTagMockedData :: Nil,
    http4sPartialFunction = Some(getAccountsAccountIdStatementsStatementIdTransactions)
  )

  // -----------------------------------------------------------------------
  // getAccountsAccountIdTransactions — real business logic
  // -----------------------------------------------------------------------
  lazy val getAccountsAccountIdTransactions: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "accounts" / accountIdStr / "transactions" =>
      EndpointHelpers.withUser(req) { (u, cc) =>
        val accountId = AccountId(accountIdStr)
        val detailViewId = ViewId(Constant.SYSTEM_READ_TRANSACTIONS_DETAIL_VIEW_ID)
        val basicViewId = ViewId(Constant.SYSTEM_READ_TRANSACTIONS_BASIC_VIEW_ID)
        for {
          _ <- NewStyle.function.checkUKConsent(u, Some(cc))
          _ <- passesPsd2Aisp(Some(cc))
          (account, _) <- NewStyle.function.getBankAccountByAccountId(accountId, Some(cc))
          (bank, _) <- NewStyle.function.getBank(account.bankId, Some(cc))
          view <- ViewNewStyle.checkViewsAccessAndReturnView(detailViewId, basicViewId, BankIdAccountId(account.bankId, accountId), Full(u), Some(cc))
          params <- Future {
            createQueriesByHttpParams(req.headers.headers.toList.map(h => HTTPParam(h.name.toString, List(h.value))))
          } map { x =>
            unboxFull(fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, Some(cc.toLight))))
          }
          (transactions, _) <- BankAccountExtended(account).getModeratedTransactionsFuture(bank, Full(u), view, Some(cc), params) map { x =>
            unboxFull(fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, Some(cc.toLight))))
          }
          (moderatedAttributes: List[TransactionAttribute], _) <- NewStyle.function.getModeratedAttributesByTransactions(
            account.bankId,
            transactions.map(_.id),
            view.viewId,
            Some(cc))
        } yield JSONFactory_UKOpenBanking_310.createTransactionsJsonNew(account.bankId, transactions, moderatedAttributes, view)
      }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccountsAccountIdTransactions),
    "GET",
    "/accounts/ACCOUNT_ID/transactions",
    "Get Transactions",
    s"""""",
    EmptyBody,
    parseBody("""{
  "Meta" : {
    "FirstAvailableDateTime": "2019-03-06T07:38:51.169Z",
    "LastAvailableDateTime": "2019-03-06T07:38:51.169Z",
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
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Transactions") :: Nil,
    http4sPartialFunction = Some(getAccountsAccountIdTransactions)
  )

  // -----------------------------------------------------------------------
  // getTransactions — real business logic (bulk, no consent check)
  // -----------------------------------------------------------------------
  lazy val getTransactions: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "transactions" =>
      EndpointHelpers.withUser(req) { (u, cc) =>
        for {
          (bank, _) <- NewStyle.function.getBank(BankId(defaultBankId), Some(cc))
          availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u)
          (accounts, _) <- NewStyle.function.getBankAccounts(availablePrivateAccounts, Some(cc))
          allTxns <- Future {
            accounts.flatMap { bankAccount =>
              (for {
                view <- UserExtended(u).checkOwnerViewAccessAndReturnOwnerView(BankIdAccountId(bankAccount.bankId, bankAccount.accountId), Some(cc))
                params = createQueriesByHttpParams(req.headers.headers.toList.map(h => HTTPParam(h.name.toString, List(h.value)))).getOrElse(Nil)
                (transactions, _) <- BankAccountExtended(bankAccount).getModeratedTransactions(bank, Full(u), view, BankIdAccountId(bankAccount.bankId, bankAccount.accountId), Some(cc), params)
              } yield transactions).getOrElse(Nil)
            }
          }
        } yield JSONFactory_UKOpenBanking_310.createTransactionsJson(allTxns, Nil)
      }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getTransactions),
    "GET",
    "/transactions",
    "Get Transactions",
    s"""""",
    EmptyBody,
    parseBody("""{
  "Meta" : {
    "FirstAvailableDateTime": "2019-03-06T07:38:51.169Z",
    "LastAvailableDateTime": "2019-03-06T07:38:51.169Z",
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
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Transactions") :: Nil,
    http4sPartialFunction = Some(getTransactions)
  )

  // Routes ordered deep-first: 5-segment path before 3-segment before 1-segment
  val routes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    getAccountsAccountIdStatementsStatementIdTransactions(req)
      .orElse(getAccountsAccountIdTransactions(req))
      .orElse(getTransactions(req))
  }
}
