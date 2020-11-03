package code.api.AUOpenBanking.v1_0_0

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

object APIMethods_AccountsApi extends RestHelper {
    val apiVersion =  ApiCollector.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      getAccountDetail ::
      getTransactionDetail ::
      getTransactions ::
//      listAccounts ::
//      listBalance ::
      listBalancesBulk ::
      listBalancesSpecificAccounts ::
      Nil

            
     resourceDocs += ResourceDoc(
       getAccountDetail, 
       apiVersion, 
       nameOf(getAccountDetail),
       "GET", 
       "/banking/accounts/ACCOUNT_ID", 
       "Get Account Detail",
       s"""${mockedDataText(true)}
            Obtain detailed information on a single account

            """,
       emptyObjectJson,
       json.parse("""{
  "data" : "",
  "meta" : " ",
  "links" : {
    "self" : "self"
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Banking") ::ApiTag("Accounts") :: apiTagMockedData :: Nil
     )

     lazy val getAccountDetail : OBPEndpoint = {
       case "banking":: "accounts" :: accountId :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{
  "data" : "",
  "meta" : " ",
  "links" : {
    "self" : "self"
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getTransactionDetail, 
       apiVersion, 
       nameOf(getTransactionDetail),
       "GET", 
       "/banking/accounts/ACCOUNT_ID/transactions/TRANSACTION_ID", 
       "Get Transaction Detail",
       s"""${mockedDataText(true)}
            Obtain detailed information on a transaction for a specific account

            """,
       emptyObjectJson,
       json.parse("""{
  "data" : "",
  "meta" : " ",
  "links" : {
    "self" : "self"
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Banking") ::ApiTag("Accounts") :: apiTagMockedData :: Nil
     )

     lazy val getTransactionDetail : OBPEndpoint = {
       case "banking":: "accounts" :: accountId:: "transactions" :: transactionId :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{
  "data" : "",
  "meta" : " ",
  "links" : {
    "self" : "self"
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getTransactions, 
       apiVersion, 
       nameOf(getTransactions),
       "GET", 
       "/banking/accounts/ACCOUNT_ID/transactions", 
       "Get Transactions For Account",
       s"""${mockedDataText(true)}
            Obtain transactions for a specific account.

Some general notes that apply to all end points that retrieve transactions:

- Where multiple transactions are returned, transactions should be ordered according to effective date in descending order

- As the date and time for a transaction can alter depending on status and transaction type two separate date/times are included in the payload. There are still some scenarios where neither of these time stamps is available. For the purpose of filtering and ordering it is expected that the data holder will use the "effective" date/time which will be defined as:

- Posted date/time if available, then

- Execution date/time if available, then

- A reasonable date/time nominated by the data holder using internal data structures

- For transaction amounts it should be assumed that a negative value indicates a reduction of the available balance on the account while a positive value indicates an increase in the available balance on the account

            """,
       emptyObjectJson,
       json.parse("""{
  "data" : {
    "transactions" : [ {
      "postingDateTime" : "postingDateTime",
      "amount" : "amount",
      "apcaNumber" : "apcaNumber",
      "isDetailAvailable" : true,
      "description" : "description",
      "type" : "FEE",
      "billerName" : "billerName",
      "executionDateTime" : "executionDateTime",
      "transactionId" : "transactionId",
      "merchantName" : "merchantName",
      "billerCode" : "billerCode",
      "reference" : "reference",
      "accountId" : "accountId",
      "merchantCategoryCode" : "merchantCategoryCode",
      "valueDateTime" : "valueDateTime",
      "currency" : "currency",
      "crn" : "crn",
      "status" : "PENDING"
    }, {
      "postingDateTime" : "postingDateTime",
      "amount" : "amount",
      "apcaNumber" : "apcaNumber",
      "isDetailAvailable" : true,
      "description" : "description",
      "type" : "FEE",
      "billerName" : "billerName",
      "executionDateTime" : "executionDateTime",
      "transactionId" : "transactionId",
      "merchantName" : "merchantName",
      "billerCode" : "billerCode",
      "reference" : "reference",
      "accountId" : "accountId",
      "merchantCategoryCode" : "merchantCategoryCode",
      "valueDateTime" : "valueDateTime",
      "currency" : "currency",
      "crn" : "crn",
      "status" : "PENDING"
    } ]
  },
  "meta" : {
    "totalRecords" : 0,
    "totalPages" : 6
  },
  "links" : {
    "next" : "next",
    "last" : "last",
    "prev" : "prev",
    "self" : "self",
    "first" : "first"
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Banking") ::ApiTag("Accounts") :: apiTagMockedData :: Nil
     )

     lazy val getTransactions : OBPEndpoint = {
       case "banking":: "accounts" :: accountId:: "transactions" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{
  "data" : {
    "transactions" : [ {
      "postingDateTime" : "postingDateTime",
      "amount" : "amount",
      "apcaNumber" : "apcaNumber",
      "isDetailAvailable" : true,
      "description" : "description",
      "type" : "FEE",
      "billerName" : "billerName",
      "executionDateTime" : "executionDateTime",
      "transactionId" : "transactionId",
      "merchantName" : "merchantName",
      "billerCode" : "billerCode",
      "reference" : "reference",
      "accountId" : "accountId",
      "merchantCategoryCode" : "merchantCategoryCode",
      "valueDateTime" : "valueDateTime",
      "currency" : "currency",
      "crn" : "crn",
      "status" : "PENDING"
    }, {
      "postingDateTime" : "postingDateTime",
      "amount" : "amount",
      "apcaNumber" : "apcaNumber",
      "isDetailAvailable" : true,
      "description" : "description",
      "type" : "FEE",
      "billerName" : "billerName",
      "executionDateTime" : "executionDateTime",
      "transactionId" : "transactionId",
      "merchantName" : "merchantName",
      "billerCode" : "billerCode",
      "reference" : "reference",
      "accountId" : "accountId",
      "merchantCategoryCode" : "merchantCategoryCode",
      "valueDateTime" : "valueDateTime",
      "currency" : "currency",
      "crn" : "crn",
      "status" : "PENDING"
    } ]
  },
  "meta" : {
    "totalRecords" : 0,
    "totalPages" : 6
  },
  "links" : {
    "next" : "next",
    "last" : "last",
    "prev" : "prev",
    "self" : "self",
    "first" : "first"
  }
}"""), callContext)
           }
         }
       }
            
//     resourceDocs += ResourceDoc(
//       listAccounts, 
//       apiVersion, 
//       nameOf(listAccounts),
//       "GET", 
//       "/banking/accounts", 
//       "Get Accounts",
//       s"""${mockedDataText(true)}
//            Obtain a list of accounts
//
//            """,
//       emptyObjectJson,
//       json.parse("""{
//  "data" : {
//    "accounts" : [ {
//      "accountId" : "accountId",
//      "maskedNumber" : "maskedNumber",
//      "openStatus" : "OPEN",
//      "displayName" : "displayName",
//      "isOwned" : true,
//      "nickname" : "nickname",
//      "creationDate" : "creationDate",
//      "productName" : "productName",
//      "productCategory" : { }
//    }, {
//      "accountId" : "accountId",
//      "maskedNumber" : "maskedNumber",
//      "openStatus" : "OPEN",
//      "displayName" : "displayName",
//      "isOwned" : true,
//      "nickname" : "nickname",
//      "creationDate" : "creationDate",
//      "productName" : "productName",
//      "productCategory" : { }
//    } ]
//  },
//  "meta" : {
//    "totalRecords" : 0,
//    "totalPages" : 6
//  },
//  "links" : {
//    "next" : "next",
//    "last" : "last",
//    "prev" : "prev",
//    "self" : "self",
//    "first" : "first"
//  }
//}"""),
//       List(UserNotLoggedIn, UnknownError),
//      
//       ApiTag("Banking") ::ApiTag("Accounts") :: apiTagMockedData :: Nil
//     )
//
//     lazy val listAccounts : OBPEndpoint = {
//       case "banking":: "accounts" :: Nil JsonGet _ => {
//         cc =>
//           for {
//             (Full(u), callContext) <- authorizedAccess(cc, UserNotLoggedIn)
//             } yield {
//            (json.parse("""{
//  "data" : {
//    "accounts" : [ {
//      "accountId" : "accountId",
//      "maskedNumber" : "maskedNumber",
//      "openStatus" : "OPEN",
//      "displayName" : "displayName",
//      "isOwned" : true,
//      "nickname" : "nickname",
//      "creationDate" : "creationDate",
//      "productName" : "productName",
//      "productCategory" : { }
//    }, {
//      "accountId" : "accountId",
//      "maskedNumber" : "maskedNumber",
//      "openStatus" : "OPEN",
//      "displayName" : "displayName",
//      "isOwned" : true,
//      "nickname" : "nickname",
//      "creationDate" : "creationDate",
//      "productName" : "productName",
//      "productCategory" : { }
//    } ]
//  },
//  "meta" : {
//    "totalRecords" : 0,
//    "totalPages" : 6
//  },
//  "links" : {
//    "next" : "next",
//    "last" : "last",
//    "prev" : "prev",
//    "self" : "self",
//    "first" : "first"
//  }
//}"""), callContext)
//           }
//         }
//       }
            
//     resourceDocs += ResourceDoc(
//       listBalance, 
//       apiVersion, 
//       nameOf(listBalance),
//       "GET", 
//       "/banking/accounts/ACCOUNT_ID/balance", 
//       "Get Account Balance",
//       s"""${mockedDataText(true)}
//            Obtain the balance for a single specified account
//
//            """,
//       emptyObjectJson,
//       json.parse("""{
//  "data" : {
//    "accountId" : "accountId",
//    "purses" : [ {
//      "amount" : "amount",
//      "currency" : "currency"
//    }, {
//      "amount" : "amount",
//      "currency" : "currency"
//    } ],
//    "amortisedLimit" : "amortisedLimit",
//    "currentBalance" : "currentBalance",
//    "creditLimit" : "creditLimit",
//    "currency" : "currency",
//    "availableBalance" : "availableBalance"
//  },
//  "meta" : " ",
//  "links" : {
//    "self" : "self"
//  }
//}"""),
//       List(UserNotLoggedIn, UnknownError),
//      
//       ApiTag("Banking") ::ApiTag("Accounts") :: apiTagMockedData :: Nil
//     )
//
//     lazy val listBalance : OBPEndpoint = {
//       case "banking":: "accounts" :: accountId:: "balance" :: Nil JsonGet _ => {
//         cc =>
//           for {
//             (Full(u), callContext) <- authorizedAccess(cc, UserNotLoggedIn)
//             } yield {
//            (json.parse("""{
//  "data" : {
//    "accountId" : "accountId",
//    "purses" : [ {
//      "amount" : "amount",
//      "currency" : "currency"
//    }, {
//      "amount" : "amount",
//      "currency" : "currency"
//    } ],
//    "amortisedLimit" : "amortisedLimit",
//    "currentBalance" : "currentBalance",
//    "creditLimit" : "creditLimit",
//    "currency" : "currency",
//    "availableBalance" : "availableBalance"
//  },
//  "meta" : " ",
//  "links" : {
//    "self" : "self"
//  }
//}"""), callContext)
//           }
//         }
//       }
            
     resourceDocs += ResourceDoc(
       listBalancesBulk, 
       apiVersion, 
       nameOf(listBalancesBulk),
       "GET", 
       "/banking/accounts/balances", 
       "Get Bulk Balances",
       s"""${mockedDataText(true)}
            Obtain balances for multiple, filtered accounts

            """,
       emptyObjectJson,
       json.parse("""{
  "data" : {
    "balances" : [ {
      "accountId" : "accountId",
      "purses" : [ {
        "amount" : "amount",
        "currency" : "currency"
      }, {
        "amount" : "amount",
        "currency" : "currency"
      } ],
      "amortisedLimit" : "amortisedLimit",
      "currentBalance" : "currentBalance",
      "creditLimit" : "creditLimit",
      "currency" : "currency",
      "availableBalance" : "availableBalance"
    }, {
      "accountId" : "accountId",
      "purses" : [ {
        "amount" : "amount",
        "currency" : "currency"
      }, {
        "amount" : "amount",
        "currency" : "currency"
      } ],
      "amortisedLimit" : "amortisedLimit",
      "currentBalance" : "currentBalance",
      "creditLimit" : "creditLimit",
      "currency" : "currency",
      "availableBalance" : "availableBalance"
    } ]
  },
  "meta" : {
    "totalRecords" : 0,
    "totalPages" : 6
  },
  "links" : {
    "next" : "next",
    "last" : "last",
    "prev" : "prev",
    "self" : "self",
    "first" : "first"
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Banking") ::ApiTag("Accounts") :: apiTagMockedData :: Nil
     )

     lazy val listBalancesBulk : OBPEndpoint = {
       case "banking":: "accounts":: "balances" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{
  "data" : {
    "balances" : [ {
      "accountId" : "accountId",
      "purses" : [ {
        "amount" : "amount",
        "currency" : "currency"
      }, {
        "amount" : "amount",
        "currency" : "currency"
      } ],
      "amortisedLimit" : "amortisedLimit",
      "currentBalance" : "currentBalance",
      "creditLimit" : "creditLimit",
      "currency" : "currency",
      "availableBalance" : "availableBalance"
    }, {
      "accountId" : "accountId",
      "purses" : [ {
        "amount" : "amount",
        "currency" : "currency"
      }, {
        "amount" : "amount",
        "currency" : "currency"
      } ],
      "amortisedLimit" : "amortisedLimit",
      "currentBalance" : "currentBalance",
      "creditLimit" : "creditLimit",
      "currency" : "currency",
      "availableBalance" : "availableBalance"
    } ]
  },
  "meta" : {
    "totalRecords" : 0,
    "totalPages" : 6
  },
  "links" : {
    "next" : "next",
    "last" : "last",
    "prev" : "prev",
    "self" : "self",
    "first" : "first"
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       listBalancesSpecificAccounts, 
       apiVersion, 
       nameOf(listBalancesSpecificAccounts),
       "POST", 
       "/banking/accounts/balances", 
       "Get Balances For Specific Accounts",
       s"""${mockedDataText(true)}
            Obtain balances for a specified list of accounts

            """,
       json.parse("""{
  "data" : {
    "accountIds" : [ "accountIds", "accountIds" ]
  },
    "meta" : " ",
}"""),
       json.parse("""{
  "data" : {
    "balances" : [ {
      "accountId" : "accountId",
      "purses" : [ {
        "amount" : "amount",
        "currency" : "currency"
      }, {
        "amount" : "amount",
        "currency" : "currency"
      } ],
      "amortisedLimit" : "amortisedLimit",
      "currentBalance" : "currentBalance",
      "creditLimit" : "creditLimit",
      "currency" : "currency",
      "availableBalance" : "availableBalance"
    }, {
      "accountId" : "accountId",
      "purses" : [ {
        "amount" : "amount",
        "currency" : "currency"
      }, {
        "amount" : "amount",
        "currency" : "currency"
      } ],
      "amortisedLimit" : "amortisedLimit",
      "currentBalance" : "currentBalance",
      "creditLimit" : "creditLimit",
      "currency" : "currency",
      "availableBalance" : "availableBalance"
    } ]
  },
  "meta" : {
    "totalRecords" : 0,
    "totalPages" : 6
  },
  "links" : {
    "next" : "next",
    "last" : "last",
    "prev" : "prev",
    "self" : "self",
    "first" : "first"
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Banking") ::ApiTag("Accounts") :: apiTagMockedData :: Nil
     )

     lazy val listBalancesSpecificAccounts : OBPEndpoint = {
       case "banking":: "accounts":: "balances" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{
  "data" : {
    "balances" : [ {
      "accountId" : "accountId",
      "purses" : [ {
        "amount" : "amount",
        "currency" : "currency"
      }, {
        "amount" : "amount",
        "currency" : "currency"
      } ],
      "amortisedLimit" : "amortisedLimit",
      "currentBalance" : "currentBalance",
      "creditLimit" : "creditLimit",
      "currency" : "currency",
      "availableBalance" : "availableBalance"
    }, {
      "accountId" : "accountId",
      "purses" : [ {
        "amount" : "amount",
        "currency" : "currency"
      }, {
        "amount" : "amount",
        "currency" : "currency"
      } ],
      "amortisedLimit" : "amortisedLimit",
      "currentBalance" : "currentBalance",
      "creditLimit" : "creditLimit",
      "currency" : "currency",
      "availableBalance" : "availableBalance"
    } ]
  },
  "meta" : {
    "totalRecords" : 0,
    "totalPages" : 6
  },
  "links" : {
    "next" : "next",
    "last" : "last",
    "prev" : "prev",
    "self" : "self",
    "first" : "first"
  }
}"""), callContext)
           }
         }
       }

}



