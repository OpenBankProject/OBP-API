package code.api.BahrainOBF.v1_0_0

import code.api.APIFailureNewStyle
import code.api.berlin.group.v1_3.JvalueCaseClass
import net.liftweb.json
import net.liftweb.json._
import code.api.util.APIUtil.{defaultBankId, _}
import code.api.util.NewStyle
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
import com.openbankproject.commons.ExecutionContext.Implicits.global
import scala.concurrent.Future
import code.api.BahrainOBF.v1_0_0.ApiCollector
import code.api.util.ApiTag

object APIMethods_BalancesApi extends RestHelper {
    val apiVersion =  ApiCollector.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      accountsAccountIdBalancesGet ::
      balancesGet ::
      Nil

            
     resourceDocs += ResourceDoc(
       accountsAccountIdBalancesGet, 
       apiVersion, 
       nameOf(accountsAccountIdBalancesGet),
       "GET", 
       "/accounts/ACCOUNT_ID/balances", 
       "Get Accounts Balances by AccountId",
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
    "First" : "http://example.com/aeiou",
    "Self" : "http://example.com/aeiou"
  },
  "Data" : {
    "Balance" : [ {
      "Type" : { },
      "AccountId" : { },
      "CreditLine" : [ {
        "Type" : "Available",
        "Amount" : { },
        "Included" : true
      }, {
        "Type" : "Available",
        "Amount" : { },
        "Included" : true
      } ],
      "Amount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "CreditDebitIndicator" : { },
      "DateTime" : "2000-01-23T04:56:07.000+00:00"
    }, {
      "Type" : { },
      "AccountId" : { },
      "CreditLine" : [ {
        "Type" : "Available",
        "Amount" : { },
        "Included" : true
      }, {
        "Type" : "Available",
        "Amount" : { },
        "Included" : true
      } ],
      "Amount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "CreditDebitIndicator" : { },
      "DateTime" : "2000-01-23T04:56:07.000+00:00"
    } ]
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Balances") :: apiTagMockedData :: Nil
     )

     lazy val accountsAccountIdBalancesGet : OBPEndpoint = {
       case "accounts" :: accountId:: "balances" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
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
    "First" : "http://example.com/aeiou",
    "Self" : "http://example.com/aeiou"
  },
  "Data" : {
    "Balance" : [ {
      "Type" : { },
      "AccountId" : { },
      "CreditLine" : [ {
        "Type" : "Available",
        "Amount" : { },
        "Included" : true
      }, {
        "Type" : "Available",
        "Amount" : { },
        "Included" : true
      } ],
      "Amount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "CreditDebitIndicator" : { },
      "DateTime" : "2000-01-23T04:56:07.000+00:00"
    }, {
      "Type" : { },
      "AccountId" : { },
      "CreditLine" : [ {
        "Type" : "Available",
        "Amount" : { },
        "Included" : true
      }, {
        "Type" : "Available",
        "Amount" : { },
        "Included" : true
      } ],
      "Amount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "CreditDebitIndicator" : { },
      "DateTime" : "2000-01-23T04:56:07.000+00:00"
    } ]
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       balancesGet, 
       apiVersion, 
       nameOf(balancesGet),
       "GET", 
       "/balances", 
       "Get Balances",
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
    "First" : "http://example.com/aeiou",
    "Self" : "http://example.com/aeiou"
  },
  "Data" : {
    "Balance" : [ {
      "Type" : { },
      "AccountId" : { },
      "CreditLine" : [ {
        "Type" : "Available",
        "Amount" : { },
        "Included" : true
      }, {
        "Type" : "Available",
        "Amount" : { },
        "Included" : true
      } ],
      "Amount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "CreditDebitIndicator" : { },
      "DateTime" : "2000-01-23T04:56:07.000+00:00"
    }, {
      "Type" : { },
      "AccountId" : { },
      "CreditLine" : [ {
        "Type" : "Available",
        "Amount" : { },
        "Included" : true
      }, {
        "Type" : "Available",
        "Amount" : { },
        "Included" : true
      } ],
      "Amount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "CreditDebitIndicator" : { },
      "DateTime" : "2000-01-23T04:56:07.000+00:00"
    } ]
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Balances") :: apiTagMockedData :: Nil
     )

     lazy val balancesGet : OBPEndpoint = {
       case "balances" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
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
    "First" : "http://example.com/aeiou",
    "Self" : "http://example.com/aeiou"
  },
  "Data" : {
    "Balance" : [ {
      "Type" : { },
      "AccountId" : { },
      "CreditLine" : [ {
        "Type" : "Available",
        "Amount" : { },
        "Included" : true
      }, {
        "Type" : "Available",
        "Amount" : { },
        "Included" : true
      } ],
      "Amount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "CreditDebitIndicator" : { },
      "DateTime" : "2000-01-23T04:56:07.000+00:00"
    }, {
      "Type" : { },
      "AccountId" : { },
      "CreditLine" : [ {
        "Type" : "Available",
        "Amount" : { },
        "Included" : true
      }, {
        "Type" : "Available",
        "Amount" : { },
        "Included" : true
      } ],
      "Amount" : {
        "Amount" : { },
        "Currency" : { }
      },
      "CreditDebitIndicator" : { },
      "DateTime" : "2000-01-23T04:56:07.000+00:00"
    } ]
  }
}"""), callContext)
           }
         }
       }

}



