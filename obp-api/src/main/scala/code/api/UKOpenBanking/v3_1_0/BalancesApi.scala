package code.api.UKOpenBanking.v3_1_0

import code.api.APIFailureNewStyle
import code.api.berlin.group.v1_3.JvalueCaseClass
import code.api.util.APIUtil.{defaultBankId, _}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.{ApiTag, NewStyle}
import code.bankconnectors.Connector
import code.model._
import code.util.Helper
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

object APIMethods_BalancesApi extends RestHelper {
    val apiVersion = OBP_UKOpenBanking_310.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      getAccountsAccountIdBalances ::
      getBalances ::
      Nil

            
     resourceDocs += ResourceDoc(
       getAccountsAccountIdBalances, 
       apiVersion, 
       nameOf(getAccountsAccountIdBalances),
       "GET", 
       "/accounts/ACCOUNT_ID/balances", 
       "Get Balances",
       s"""""", 
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
    "Balance" : [ {
      "Type" : { },
      "AccountId" : { },
      "CreditLine" : [ {
        "Type" : { },
        "Included" : true,
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        }
      }, {
        "Type" : { },
        "Included" : true,
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        }
      } ],
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "CreditDebitIndicator" : "Credit",
      "DateTime" : "2000-01-23T04:56:07.000+00:00"
    }, {
      "Type" : { },
      "AccountId" : { },
      "CreditLine" : [ {
        "Type" : { },
        "Included" : true,
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        }
      }, {
        "Type" : { },
        "Included" : true,
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        }
      } ],
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "CreditDebitIndicator" : "Credit",
      "DateTime" : "2000-01-23T04:56:07.000+00:00"
    } ]
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("Balances") :: apiTagMockedData :: Nil
     )

     lazy val getAccountsAccountIdBalances : OBPEndpoint = {
       case "accounts" :: AccountId(accountId):: "balances" :: Nil JsonGet _ => {
         cc =>
           for {
            (Full(u), callContext) <- authorizedAccess(cc)

            (account, callContext) <- Future { BankAccountX(BankId(defaultBankId), accountId, callContext) } map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(DefaultBankIdNotSet, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }

            view <- NewStyle.function.view(ViewId("owner"), BankIdAccountId(account.bankId, account.accountId), callContext)
        
            _ <- Helper.booleanToFuture(failMsg = s"${UserNoPermissionAccessView} Current VIEW_ID (${view.viewId.value})") {(u.hasViewAccess(view))}
        
            moderatedAccount <- Future {account.moderatedBankAccount(view, Full(u), callContext)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
        
          } yield {
            (JSONFactory_UKOpenBanking_310.createAccountBalanceJSON(moderatedAccount), callContext)
          }
         }
       }
            
     resourceDocs += ResourceDoc(
       getBalances, 
       apiVersion, 
       nameOf(getBalances),
       "GET", 
       "/balances", 
       "Get Balances",
       s"""""", 
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
    "Balance" : [ {
      "Type" : { },
      "AccountId" : { },
      "CreditLine" : [ {
        "Type" : { },
        "Included" : true,
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        }
      }, {
        "Type" : { },
        "Included" : true,
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        }
      } ],
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "CreditDebitIndicator" : "Credit",
      "DateTime" : "2000-01-23T04:56:07.000+00:00"
    }, {
      "Type" : { },
      "AccountId" : { },
      "CreditLine" : [ {
        "Type" : { },
        "Included" : true,
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        }
      }, {
        "Type" : { },
        "Included" : true,
        "Amount" : {
          "Amount" : { },
          "Currency" : "Currency"
        }
      } ],
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "CreditDebitIndicator" : "Credit",
      "DateTime" : "2000-01-23T04:56:07.000+00:00"
    } ]
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("Balances") :: apiTagMockedData :: Nil
     )

     lazy val getBalances : OBPEndpoint = {
       case "balances" :: Nil JsonGet _ => {
         cc =>
           for {
            (Full(u), callContext) <- authorizedAccess(cc)

            availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u)
          
            accounts <- {Connector.connector.vend.getBankAccounts(availablePrivateAccounts, callContext)}
          
          } yield {
            (JSONFactory_UKOpenBanking_310.createBalancesJSON(accounts.getOrElse(Nil)), callContext)
          }
         }
       }

}



