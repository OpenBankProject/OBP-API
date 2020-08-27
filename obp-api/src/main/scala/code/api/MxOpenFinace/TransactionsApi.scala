package code.api.MxOpenFinace

import code.api.berlin.group.v1_3.JvalueCaseClass
import code.api.util.APIUtil._
import code.api.util.ApiTag
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import net.liftweb.common.Full
import net.liftweb.http.rest.RestHelper
import net.liftweb.json
import net.liftweb.json._

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer

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
  "Meta" : {
    "LastAvailableDateTime" : "2000-01-23T04:56:07.000+00:00",
    "FirstAvailableDateTime" : "2000-01-23T04:56:07.000+00:00",
    "TotalPages" : 0
  },
  "Links" : {
    "Last" : "Last",
    "Prev" : "Prev",
    "Next" : "Next",
    "Self" : "Self",
    "First" : "First"
  },
  "Data" : {
    "Transaction" : [ "{}", "{}" ]
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
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{
  "Meta" : {
    "LastAvailableDateTime" : "2000-01-23T04:56:07.000+00:00",
    "FirstAvailableDateTime" : "2000-01-23T04:56:07.000+00:00",
    "TotalPages" : 0
  },
  "Links" : {
    "Last" : "Last",
    "Prev" : "Prev",
    "Next" : "Next",
    "Self" : "Self",
    "First" : "First"
  },
  "Data" : {
    "Transaction" : [ "{}", "{}" ]
  }
}"""), callContext)
           }
         }
       }

}



