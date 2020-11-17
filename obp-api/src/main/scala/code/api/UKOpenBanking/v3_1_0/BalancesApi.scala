package code.api.UKOpenBanking.v3_1_0

import code.api.Constant
import code.api.berlin.group.v1_3.JvalueCaseClass
import code.api.util.APIUtil._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.{ApiTag, NewStyle}

import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{AccountId, BankIdAccountId, View, ViewId}
import net.liftweb.common.Full
import net.liftweb.http.rest.RestHelper
import net.liftweb.json
import net.liftweb.json._

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import com.openbankproject.commons.ExecutionContext.Implicits.global

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
       ApiTag("Balances") :: Nil
     )

     lazy val getAccountsAccountIdBalances : OBPEndpoint = {
       case "accounts" :: AccountId(accountId):: "balances" :: Nil JsonGet _ => {
         cc =>
           val viewId = ViewId(Constant.SYSTEM_READ_BALANCES_VIEW_ID)
           for {
             (Full(user), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             _ <- NewStyle.function.checkUKConsent(user, callContext)
             _ <- passesPsd2Aisp(callContext)
             (account, callContext) <- NewStyle.function.getBankAccountByAccountId(accountId, callContext)
             view: View <- NewStyle.function.checkViewAccessAndReturnView(viewId, BankIdAccountId(account.bankId, accountId), Full(user), callContext)
             moderatedAccount <- NewStyle.function.moderatedBankAccountCore(account, view, Full(user), callContext)
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
       ApiTag("Balances") :: Nil
     )

     lazy val getBalances : OBPEndpoint = {
       case "balances" :: Nil JsonGet _ => {
         cc =>
           for {
            (Full(u), callContext) <- authenticatedAccess(cc)

            availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u)
          
            (accounts, callContext)<- NewStyle.function.getBankAccounts(availablePrivateAccounts, callContext)
          
          } yield {
            (JSONFactory_UKOpenBanking_310.createBalancesJSON(accounts), callContext)
          }
         }
       }

}



