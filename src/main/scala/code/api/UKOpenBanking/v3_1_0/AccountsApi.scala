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
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("Accounts") :: apiTagMockedData :: Nil
     )

     lazy val getAccounts : OBPEndpoint = {
       case "accounts" :: Nil JsonGet _ => {
         cc =>
           for {
            (Full(u), callContext) <- authorizedAccess(cc)
            availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u)
            accounts <- {Connector.connector.vend.getBankAccountsFuture(availablePrivateAccounts, callContext)}
          } yield {
            (JSONFactory_UKOpenBanking_310.createAccountsListJSON(accounts.getOrElse(Nil)), callContext)
          }
           
         }
       }
            
     resourceDocs += ResourceDoc(
       getAccountsAccountId, 
       apiVersion, 
       nameOf(getAccountsAccountId),
       "GET", 
       "/accounts/ACCOUNTID", 
       "Get Accounts",
       s"""${mockedDataText(true)}
""", 
       json.parse(""""""),
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
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("Accounts") :: apiTagMockedData :: Nil
     )

     lazy val getAccountsAccountId : OBPEndpoint = {
       case "accounts" :: AccountId(accountId) :: Nil JsonGet _ => {
         cc =>
           for {
            (Full(u), callContext) <- authorizedAccess(cc)
            availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u) map {
              _.filter(_.accountId.value == accountId.value)
            }
            accounts <- {Connector.connector.vend.getBankAccountsFuture(availablePrivateAccounts, callContext)}
          } yield {
            (JSONFactory_UKOpenBanking_310.createAccountJSON(accounts.getOrElse(Nil)), callContext)
          }
         }
       }

}



