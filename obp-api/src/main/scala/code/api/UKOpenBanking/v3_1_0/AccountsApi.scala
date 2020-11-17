package code.api.UKOpenBanking.v3_1_0

import code.api.Constant
import code.api.berlin.group.v1_3.JvalueCaseClass
import code.api.util.APIUtil._
import code.api.util.{APIUtil, ApiTag, CallContext, NewStyle}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{AccountAttribute, AccountId, BankAccount, BankIdAccountId, View, ViewId}
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.http.rest.RestHelper
import net.liftweb.json
import net.liftweb.json._

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import com.openbankproject.commons.ExecutionContext.Implicits.global

object APIMethods_AccountsApi extends RestHelper {
    val apiVersion = OBP_UKOpenBanking_310.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      getAccounts ::
      getAccountsAccountId ::
      Nil

            
     resourceDocs += ResourceDoc(
       getAccounts, 
       apiVersion, 
       nameOf(getAccounts),
       "GET", 
       "/accounts", 
       "Get Accounts",
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
    "Account" : [ {
      "Account" : [ {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      }, {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      } ],
      "Servicer" : {
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification"
      },
      "AccountId" : "String",
      "Description" : "Description",
      "Currency" : "Currency",
      "AccountType" : "String",
      "AccountSubType" : "String",
      "Nickname" : "Nickname"
    }, {
      "Account" : [ {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      }, {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      } ],
      "Servicer" : {
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification"
      },
      "AccountId" : "String",
      "Description" : "Description",
      "Currency" : "Currency",
      "AccountType" : "String",
      "AccountSubType" : "String",
      "Nickname" : "Nickname"
    } ]
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Accounts") :: Nil
     )

     lazy val getAccounts : OBPEndpoint = {
       case "accounts" :: Nil JsonGet _ => {
         cc =>
           val detailViewId = ViewId(Constant.SYSTEM_READ_ACCOUNTS_DETAIL_VIEW_ID)
           val basicViewId = ViewId(Constant.SYSTEM_READ_ACCOUNTS_BASIC_VIEW_ID)
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             _ <- NewStyle.function.checkUKConsent(u, callContext)
             _ <- passesPsd2Aisp(callContext)
             availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u)
             (accounts: List[BankAccount], callContext) <- NewStyle.function.getBankAccounts(availablePrivateAccounts, callContext)
             (moderatedAttributes: List[AccountAttribute], callContext) <- NewStyle.function.getModeratedAccountAttributesByAccounts(
               accounts.map(a => BankIdAccountId(a.bankId, a.accountId)),
               basicViewId,
               callContext: Option[CallContext])
           } yield {
             val allAccounts: List[Box[(BankAccount, View)]] = for (account: BankAccount <- accounts) yield {
               APIUtil.checkViewAccessAndReturnView(detailViewId, BankIdAccountId(account.bankId, account.accountId), Full(u)).or(
                 APIUtil.checkViewAccessAndReturnView(basicViewId, BankIdAccountId(account.bankId, account.accountId), Full(u))
               ) match {
                 case Full(view) =>
                   Full(account, view)
                 case _ =>
                   Empty
               }
             }
             val accountsWithProperView: List[(BankAccount, View)] = allAccounts.filter(_.isDefined).map(_.openOrThrowException(attemptedToOpenAnEmptyBox))
             (JSONFactory_UKOpenBanking_310.createAccountsListJSON(accountsWithProperView, moderatedAttributes), callContext)
           }
       }
     }
            
     resourceDocs += ResourceDoc(
       getAccountsAccountId, 
       apiVersion, 
       nameOf(getAccountsAccountId),
       "GET", 
       "/accounts/ACCOUNT_ID", 
       "Get Accounts",
       s"""
""", 
       emptyObjectJson,
       json.parse("""{
  "Meta" : {
    "FirstAvailableDateTime": "2019-03-05T13:09:30.399Z",
    "LastAvailableDateTime": "2019-03-05T13:09:30.399Z"
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
    "Account" : [ {
      "Account" : [ {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      }, {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      } ],
      "Servicer" : {
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification"
      },
      "AccountId" : "String",
      "Description" : "Description",
      "Currency" : "Currency",
      "AccountType" : "String",
      "AccountSubType" : "String",
      "Nickname" : "Nickname"
    }, {
      "Account" : [ {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      }, {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      } ],
      "Servicer" : {
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification"
      },
      "AccountId" : "String",
      "Description" : "Description",
      "Currency" : "Currency",
      "AccountType" : "String",
      "AccountSubType" : "String",
      "Nickname" : "Nickname"
    } ]
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Accounts") :: Nil
     )

     lazy val getAccountsAccountId : OBPEndpoint = {
       case "accounts" :: AccountId(accountId) :: Nil JsonGet _ => {
         val detailViewId = ViewId(Constant.SYSTEM_READ_ACCOUNTS_DETAIL_VIEW_ID)
         val basicViewId = ViewId(Constant.SYSTEM_READ_ACCOUNTS_BASIC_VIEW_ID)
             
         cc =>
           for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u) map {
              _.filter(_.accountId.value == accountId.value)
            }
            (accounts, callContext)<- NewStyle.function.getBankAccounts(availablePrivateAccounts, callContext)

            (moderatedAttributes: List[AccountAttribute], callContext) <- NewStyle.function.getModeratedAccountAttributesByAccounts(
              accounts.map(a => BankIdAccountId(a.bankId, a.accountId)),
              basicViewId,
              callContext: Option[CallContext])
           } yield {
             val allAccounts: List[Box[(BankAccount, View)]] = for (account: BankAccount <- accounts) yield {
               APIUtil.checkViewAccessAndReturnView(detailViewId, BankIdAccountId(account.bankId, account.accountId), Full(u)).or(
                 APIUtil.checkViewAccessAndReturnView(basicViewId, BankIdAccountId(account.bankId, account.accountId), Full(u))
               ) match {
                 case Full(view) =>
                   Full(account, view)
                 case _ =>
                   Empty
               }
             }
             val accountsWithProperView: List[(BankAccount, View)] = allAccounts.filter(_.isDefined).map(_.openOrThrowException(attemptedToOpenAnEmptyBox))
            (JSONFactory_UKOpenBanking_310.createAccountsListJSON(accountsWithProperView, moderatedAttributes), callContext)
          }
         }
       }

}



