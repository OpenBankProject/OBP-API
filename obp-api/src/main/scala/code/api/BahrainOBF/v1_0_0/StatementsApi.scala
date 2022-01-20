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

object APIMethods_StatementsApi extends RestHelper {
    val apiVersion =  ApiCollector.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      accountsAccountIdStatementsGet ::
      accountsAccountIdStatementsStatementIdFileGet ::
      accountsAccountIdStatementsStatementIdGet ::
      accountsAccountIdStatementsStatementIdTransactionsGet ::
      statementsGet ::
      Nil

            
     resourceDocs += ResourceDoc(
       accountsAccountIdStatementsGet, 
       apiVersion, 
       nameOf(accountsAccountIdStatementsGet),
       "GET", 
       "/accounts/ACCOUNT_ID/statements", 
       "Get Accounts Statements by AccountId",
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
    "Statement" : [ { }, { } ]
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Statements") :: apiTagMockedData :: Nil
     )

     lazy val accountsAccountIdStatementsGet : OBPEndpoint = {
       case "accounts" :: accountId:: "statements" :: Nil JsonGet _ => {
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
    "Statement" : [ { }, { } ]
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       accountsAccountIdStatementsStatementIdFileGet, 
       apiVersion, 
       nameOf(accountsAccountIdStatementsStatementIdFileGet),
       "GET", 
       "/accounts/ACCOUNT_ID/statements/STATEMENT_ID/file", 
       "Get Accounts Statements File by AccountId and StatementId",
       s"""${mockedDataText(true)}
            
            """,
       json.parse(""""""),
       json.parse("""{ }"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Statements") :: apiTagMockedData :: Nil
     )

     lazy val accountsAccountIdStatementsStatementIdFileGet : OBPEndpoint = {
       case "accounts" :: accountId:: "statements" :: statementId:: "file" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{ }"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       accountsAccountIdStatementsStatementIdGet, 
       apiVersion, 
       nameOf(accountsAccountIdStatementsStatementIdGet),
       "GET", 
       "/accounts/ACCOUNT_ID/statements/STATEMENT_ID", 
       "Get Accounts Statement by AccountId and StatementId",
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
    "Statement" : [ { }, { } ]
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Statements") :: apiTagMockedData :: Nil
     )

     lazy val accountsAccountIdStatementsStatementIdGet : OBPEndpoint = {
       case "accounts" :: accountId:: "statements" :: statementId :: Nil JsonGet _ => {
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
    "Statement" : [ { }, { } ]
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       accountsAccountIdStatementsStatementIdTransactionsGet, 
       apiVersion, 
       nameOf(accountsAccountIdStatementsStatementIdTransactionsGet),
       "GET", 
       "/accounts/ACCOUNT_ID/statements/STATEMENT_ID/transactions", 
       "Get Accounts Statement Tranactions by AccountId and StatementId",
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
    "Transaction" : [ { }, { } ]
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Statements") :: apiTagMockedData :: Nil
     )

     lazy val accountsAccountIdStatementsStatementIdTransactionsGet : OBPEndpoint = {
       case "accounts" :: accountId:: "statements" :: statementId:: "transactions" :: Nil JsonGet _ => {
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
    "Transaction" : [ { }, { } ]
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       statementsGet, 
       apiVersion, 
       nameOf(statementsGet),
       "GET", 
       "/statements", 
       "Get Statements",
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
    "Statement" : [ { }, { } ]
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Statements") :: apiTagMockedData :: Nil
     )

     lazy val statementsGet : OBPEndpoint = {
       case "statements" :: Nil JsonGet _ => {
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
    "Statement" : [ { }, { } ]
  }
}"""), callContext)
           }
         }
       }

}



