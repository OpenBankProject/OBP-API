package code.api.MxOpenFinace

import code.api.APIFailureNewStyle
import code.api.util.APIUtil.{defaultBankId, passesPsd2Aisp, _}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.{ApiTag, NewStyle}
import code.model._
import code.util.Helper
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{AccountId, BankId, BankIdAccountId}
import net.liftweb.common.Full
import net.liftweb.http.rest.RestHelper
import net.liftweb.json
import net.liftweb.json._

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

object APIMethods_TransactionsApi extends RestHelper {
    val apiVersion =  MxOpenFinanceCollector.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      getTransactionsByAccountId ::
      Nil

            
     resourceDocs += ResourceDoc(
       getTransactionsByAccountId, 
       apiVersion, 
       nameOf(getTransactionsByAccountId),
       "GET", 
       "/accounts/ACCOUNT_ID/transactions", 
       "getTransactionsByAccountId",
       s"""${mockedDataText(true)}
            Get Transactions
            """,
       json.parse(""""""),
       json.parse("""{
  "Data": {
    "Transaction": [
      {
        "AccountId": "string",
        "TransactionId": "string",
        "TransactionReference": "string",
        "TransferTracingCode": "string",
        "AccountIndicator": "Debit",
        "Status": "Booked",
        "BookingDateTime": "2020-08-31T13:10:34.249Z",
        "ValueDateTime": "2020-08-31T13:10:34.249Z",
        "TransactionInformation": "string",
        "AddressLine": "string",
        "Amount": {
          "Amount": "string",
          "Currency": "string"
        },
        "CurrencyExchange": {
          "SourceCurrency": "string",
          "TargetCurrency": "string",
          "UnitCurrency": "string",
          "ExchangeRate": 0,
          "ContractIdentification": "string",
          "QuotationDate": "2020-08-31T13:10:34.249Z",
          "InstructedAmount": {
            "Amount": "string",
            "Currency": "string"
          }
        },
        "BankTransactionCode": {
          "Code": "string",
          "SubCode": "string"
        },
        "CardInstrument": {
          "CardSchemeName": "AmericanExpress",
          "AuthorisationType": "CHIP",
          "Name": "string",
          "Identification": "string"
        },
        "SupplementaryData": {
          "additionalProp1": {}
        }
      },
      {
        "AccountId": "string",
        "TransactionId": "string",
        "TransactionReference": "string",
        "TransferTracingCode": "string",
        "AccountIndicator": "Debit",
        "Status": "Booked",
        "BookingDateTime": "2020-08-31T13:10:34.249Z",
        "ValueDateTime": "2020-08-31T13:10:34.249Z",
        "TransactionInformation": "string",
        "AddressLine": "string",
        "Amount": {
          "Amount": "string",
          "Currency": "string"
        },
        "CurrencyExchange": {
          "SourceCurrency": "string",
          "TargetCurrency": "string",
          "UnitCurrency": "string",
          "ExchangeRate": 0,
          "ContractIdentification": "string",
          "QuotationDate": "2020-08-31T13:10:34.249Z",
          "InstructedAmount": {
            "Amount": "string",
            "Currency": "string"
          }
        },
        "BankTransactionCode": {
          "Code": "string",
          "SubCode": "string"
        },
        "Balance": {
          "AccountIndicator": "Debit",
          "Type": "Available",
          "Amount": {
            "Amount": "string",
            "Currency": "string"
          }
        },
        "MerchantDetails": {
          "MerchantName": "string",
          "MerchantCategoryCode": "string"
        },
        "TransactionRecipient": {
          "SchemeName": "string",
          "Identification": "string",
          "Name": "string"
        },
        "RecipientAccount": {
          "SchemeName": "string",
          "Identification": "string",
          "Name": "string"
        },
        "TransactionSender": {
          "SchemeName": "string",
          "Identification": "string",
          "Name": "string"
        },
        "SenderAccount": {
          "SchemeName": "string",
          "Identification": "string",
          "Name": "string"
        },
        "CardInstrument": {
          "CardSchemeName": "AmericanExpress",
          "AuthorisationType": "CHIP",
          "Name": "string",
          "Identification": "string"
        },
        "SupplementaryData": {
          "additionalProp1": {}
        }
      }
    ]
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-08-31T13:10:34.249Z",
    "LastAvailableDateTime": "2020-08-31T13:10:34.249Z"
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("Transactions") :: apiTagMockedData :: Nil
     )

     lazy val getTransactionsByAccountId : OBPEndpoint = {
       case "accounts" :: accountId:: "transactions" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             _ <- passesPsd2Aisp(callContext)

             _ <- Helper.booleanToFuture(failMsg= DefaultBankIdNotSet ) {defaultBankId != "DEFAULT_BANK_ID_NOT_SET"}

             bankId = BankId(defaultBankId)

             (bank, callContext) <- NewStyle.function.getBank(bankId, callContext)

             (bankAccount, callContext) <- NewStyle.function.checkBankAccountExists(bankId, AccountId(accountId), callContext)

             view <- NewStyle.function.checkOwnerViewAccessAndReturnOwnerView(u, BankIdAccountId(bankAccount.bankId, bankAccount.accountId), callContext)

             params <- Future { createQueriesByHttpParams(callContext.get.requestHeaders)} map {
               x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
             } map { unboxFull(_) }

             (transactions, callContext) <- bankAccount.getModeratedTransactionsFuture(bank, Full(u), view, BankIdAccountId(bankId,bankAccount.accountId), callContext, params) map {
               x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
             } map { unboxFull(_) }
             
             } yield {
              (JSONFactory_MX_OPEN_FINANCE_0_0_1.createGetTransactionsByAccountIdMXOFV10(transactions), callContext)
           }
         }
       }

}



