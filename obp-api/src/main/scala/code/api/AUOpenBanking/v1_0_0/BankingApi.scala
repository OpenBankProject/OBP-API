package code.api.AUOpenBanking.v1_0_0

import code.api.berlin.group.v1_3.JvalueCaseClass
import code.api.util.APIUtil.{defaultBankId, _}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.{ApiTag, NewStyle}
import code.api.util.NewStyle.HttpCode
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{AccountId, BankId}
import net.liftweb.common.Full
import net.liftweb.http.rest.RestHelper
import net.liftweb.json
import net.liftweb.json._

import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer
import com.openbankproject.commons.ExecutionContext.Implicits.global

object APIMethods_BankingApi extends RestHelper {
    val apiVersion =  ApiCollector.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      getAccountDetail ::
      getPayeeDetail ::
      getProductDetail ::
      getTransactionDetail ::
      getTransactions ::
      listAccounts ::
      listBalance ::
      listBalancesBulk ::
      listBalancesSpecificAccounts ::
      listDirectDebits ::
      listDirectDebitsBulk ::
      listDirectDebitsSpecificAccounts ::
      listPayees ::
      listProducts ::
      listScheduledPayments ::
      listScheduledPaymentsBulk ::
      listScheduledPaymentsSpecificAccounts ::
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
       getPayeeDetail, 
       apiVersion, 
       nameOf(getPayeeDetail),
       "GET", 
       "/banking/payees/PAYEE_ID", 
       "Get Payee Detail",
       s"""${mockedDataText(true)}
            Obtain detailed information on a single payee

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
       ApiTag("Banking") ::ApiTag("Payees") :: apiTagMockedData :: Nil
     )

     lazy val getPayeeDetail : OBPEndpoint = {
       case "banking":: "payees" :: payeeId :: Nil JsonGet _ => {
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
       getProductDetail, 
       apiVersion, 
       nameOf(getProductDetail),
       "GET", 
       "/banking/products/PRODUCT_ID", 
       "Get Product Detail",
       s"""${mockedDataText(true)}
            Obtain detailed information on a single product offered openly to the market

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
       ApiTag("Banking") ::ApiTag("Products") :: apiTagMockedData :: Nil
     )

     lazy val getProductDetail : OBPEndpoint = {
       case "banking":: "products" :: productId :: Nil JsonGet _ => {
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
            
     resourceDocs += ResourceDoc(
       listAccounts, 
       apiVersion, 
       nameOf(listAccounts),
       "GET", 
       "/banking/accounts", 
       "Get Accounts",
       s"""${mockedDataText(false)}
            Obtain a list of accounts

            """,
       emptyObjectJson,
       json.parse("""{
  "data" : {
    "accounts" : [ {
      "accountId" : "accountId",
      "maskedNumber" : "maskedNumber",
      "openStatus" : "OPEN",
      "displayName" : "displayName",
      "isOwned" : true,
      "nickname" : "nickname",
      "creationDate" : "creationDate",
      "productName" : "productName",
      "productCategory" : { }
    }, {
      "accountId" : "accountId",
      "maskedNumber" : "maskedNumber",
      "openStatus" : "OPEN",
      "displayName" : "displayName",
      "isOwned" : true,
      "nickname" : "nickname",
      "creationDate" : "creationDate",
      "productName" : "productName",
      "productCategory" : { }
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
       ApiTag("Banking") ::ApiTag("Accounts") :: Nil
     )

     lazy val listAccounts : OBPEndpoint = {
       case "banking":: "accounts" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u, BankId(defaultBankId))
             (coreAccounts, callContext) <- NewStyle.function.getCoreBankAccountsFuture(availablePrivateAccounts, callContext)
             } yield {
             (JSONFactory_AU_OpenBanking_1_0_0.createListAccountsJson(coreAccounts), HttpCode.`200`(callContext))
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       listBalance, 
       apiVersion, 
       nameOf(listBalance),
       "GET", 
       "/banking/accounts/ACCOUNT_ID/balance", 
       "Get Account Balance",
       s"""${mockedDataText(false)}
            Obtain the balance for a single specified account

            """,
       emptyObjectJson,
       json.parse("""{
  "data" : {
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
  },
  "meta" : " ",
  "links" : {
    "self" : "self"
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Banking") ::ApiTag("Accounts") :: Nil
     )

     lazy val listBalance : OBPEndpoint = {
       case "banking":: "accounts" :: accountId:: "balance" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             (account, callContext) <- NewStyle.function.checkBankAccountExists(BankId(defaultBankId), AccountId(accountId), callContext)
             } yield {
             (JSONFactory_AU_OpenBanking_1_0_0.createAccountBalanceJson(account), HttpCode.`200`(callContext))
           }
         }
       }
            
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
            
     resourceDocs += ResourceDoc(
       listDirectDebits, 
       apiVersion, 
       nameOf(listDirectDebits),
       "GET", 
       "/banking/accounts/ACCOUNT_ID/direct-debits", 
       "Get Direct Debits For Account",
       s"""${mockedDataText(true)}
            Obtain direct debit authorisations for a specific account

            """,
       emptyObjectJson,
       json.parse("""{
  "data" : {
    "directDebitAuthorisations" : [ {
      "lastDebitAmount" : "lastDebitAmount",
      "accountId" : "accountId",
      "lastDebitDateTime" : "lastDebitDateTime",
      "authorisedEntity" : {
        "arbn" : "arbn",
        "description" : "description",
        "financialInstitution" : "financialInstitution",
        "abn" : "abn",
        "acn" : "acn"
      }
    }, {
      "lastDebitAmount" : "lastDebitAmount",
      "accountId" : "accountId",
      "lastDebitDateTime" : "lastDebitDateTime",
      "authorisedEntity" : {
        "arbn" : "arbn",
        "description" : "description",
        "financialInstitution" : "financialInstitution",
        "abn" : "abn",
        "acn" : "acn"
      }
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
       ApiTag("Banking") ::ApiTag("Direct Debits") :: apiTagMockedData :: Nil
     )

     lazy val listDirectDebits : OBPEndpoint = {
       case "banking":: "accounts" :: accountId:: "direct-debits" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{
  "data" : {
    "directDebitAuthorisations" : [ {
      "lastDebitAmount" : "lastDebitAmount",
      "accountId" : "accountId",
      "lastDebitDateTime" : "lastDebitDateTime",
      "authorisedEntity" : {
        "arbn" : "arbn",
        "description" : "description",
        "financialInstitution" : "financialInstitution",
        "abn" : "abn",
        "acn" : "acn"
      }
    }, {
      "lastDebitAmount" : "lastDebitAmount",
      "accountId" : "accountId",
      "lastDebitDateTime" : "lastDebitDateTime",
      "authorisedEntity" : {
        "arbn" : "arbn",
        "description" : "description",
        "financialInstitution" : "financialInstitution",
        "abn" : "abn",
        "acn" : "acn"
      }
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
       listDirectDebitsBulk, 
       apiVersion, 
       nameOf(listDirectDebitsBulk),
       "GET", 
       "/banking/accounts/direct-debits", 
       "Get Bulk Direct Debits",
       s"""${mockedDataText(true)}
            Obtain direct debit authorisations for multiple, filtered accounts

            """,
       emptyObjectJson,
       json.parse("""{
  "data" : {
    "directDebitAuthorisations" : [ {
      "lastDebitAmount" : "lastDebitAmount",
      "accountId" : "accountId",
      "lastDebitDateTime" : "lastDebitDateTime",
      "authorisedEntity" : {
        "arbn" : "arbn",
        "description" : "description",
        "financialInstitution" : "financialInstitution",
        "abn" : "abn",
        "acn" : "acn"
      }
    }, {
      "lastDebitAmount" : "lastDebitAmount",
      "accountId" : "accountId",
      "lastDebitDateTime" : "lastDebitDateTime",
      "authorisedEntity" : {
        "arbn" : "arbn",
        "description" : "description",
        "financialInstitution" : "financialInstitution",
        "abn" : "abn",
        "acn" : "acn"
      }
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
       ApiTag("Banking") ::ApiTag("Direct Debits") :: apiTagMockedData :: Nil
     )

     lazy val listDirectDebitsBulk : OBPEndpoint = {
       case "banking":: "accounts":: "direct-debits" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{
  "data" : {
    "directDebitAuthorisations" : [ {
      "lastDebitAmount" : "lastDebitAmount",
      "accountId" : "accountId",
      "lastDebitDateTime" : "lastDebitDateTime",
      "authorisedEntity" : {
        "arbn" : "arbn",
        "description" : "description",
        "financialInstitution" : "financialInstitution",
        "abn" : "abn",
        "acn" : "acn"
      }
    }, {
      "lastDebitAmount" : "lastDebitAmount",
      "accountId" : "accountId",
      "lastDebitDateTime" : "lastDebitDateTime",
      "authorisedEntity" : {
        "arbn" : "arbn",
        "description" : "description",
        "financialInstitution" : "financialInstitution",
        "abn" : "abn",
        "acn" : "acn"
      }
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
       listDirectDebitsSpecificAccounts, 
       apiVersion, 
       nameOf(listDirectDebitsSpecificAccounts),
       "POST", 
       "/banking/accounts/direct-debits", 
       "Get Direct Debits For Specific Accounts",
       s"""${mockedDataText(true)}
            Obtain direct debit authorisations for a specified list of accounts

            """,
       json.parse("""{
  "data" : {
    "accountIds" : [ "accountIds", "accountIds" ]
  },
    "meta" : " ",
}"""),
       json.parse("""{
  "data" : {
    "directDebitAuthorisations" : [ {
      "lastDebitAmount" : "lastDebitAmount",
      "accountId" : "accountId",
      "lastDebitDateTime" : "lastDebitDateTime",
      "authorisedEntity" : {
        "arbn" : "arbn",
        "description" : "description",
        "financialInstitution" : "financialInstitution",
        "abn" : "abn",
        "acn" : "acn"
      }
    }, {
      "lastDebitAmount" : "lastDebitAmount",
      "accountId" : "accountId",
      "lastDebitDateTime" : "lastDebitDateTime",
      "authorisedEntity" : {
        "arbn" : "arbn",
        "description" : "description",
        "financialInstitution" : "financialInstitution",
        "abn" : "abn",
        "acn" : "acn"
      }
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
       ApiTag("Banking") ::ApiTag("Direct Debits") :: apiTagMockedData :: Nil
     )

     lazy val listDirectDebitsSpecificAccounts : OBPEndpoint = {
       case "banking":: "accounts":: "direct-debits" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{
  "data" : {
    "directDebitAuthorisations" : [ {
      "lastDebitAmount" : "lastDebitAmount",
      "accountId" : "accountId",
      "lastDebitDateTime" : "lastDebitDateTime",
      "authorisedEntity" : {
        "arbn" : "arbn",
        "description" : "description",
        "financialInstitution" : "financialInstitution",
        "abn" : "abn",
        "acn" : "acn"
      }
    }, {
      "lastDebitAmount" : "lastDebitAmount",
      "accountId" : "accountId",
      "lastDebitDateTime" : "lastDebitDateTime",
      "authorisedEntity" : {
        "arbn" : "arbn",
        "description" : "description",
        "financialInstitution" : "financialInstitution",
        "abn" : "abn",
        "acn" : "acn"
      }
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
       listPayees, 
       apiVersion, 
       nameOf(listPayees),
       "GET", 
       "/banking/payees", 
       "Get Payees",
       s"""${mockedDataText(true)}
            Obtain a list of pre-registered payees

            """,
       emptyObjectJson,
       json.parse("""{
  "data" : {
    "payees" : [ {
      "nickname" : "nickname",
      "description" : "description",
      "payeeId" : "payeeId",
      "type" : "DOMESTIC",
      "creationDate" : "creationDate"
    }, {
      "nickname" : "nickname",
      "description" : "description",
      "payeeId" : "payeeId",
      "type" : "DOMESTIC",
      "creationDate" : "creationDate"
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
       ApiTag("Banking") ::ApiTag("Payees") :: apiTagMockedData :: Nil
     )

     lazy val listPayees : OBPEndpoint = {
       case "banking":: "payees" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{
  "data" : {
    "payees" : [ {
      "nickname" : "nickname",
      "description" : "description",
      "payeeId" : "payeeId",
      "type" : "DOMESTIC",
      "creationDate" : "creationDate"
    }, {
      "nickname" : "nickname",
      "description" : "description",
      "payeeId" : "payeeId",
      "type" : "DOMESTIC",
      "creationDate" : "creationDate"
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
       listProducts, 
       apiVersion, 
       nameOf(listProducts),
       "GET", 
       "/banking/products", 
       "Get Products",
       s"""${mockedDataText(true)}
            Obtain a list of products that are currently openly offered to the market

Note that the results returned by this end point are expected to be ordered according to updated-since

### Conventions

In the product reference payloads there are a number of recurring conventions that are explained here, in one place.

#### Arrays Of Features

In the product detail payload there are a number of arrays articulating generic features, constraints, prices, etc. The intent of these arrays is as follows:

- Each element in an array has the same structure so that clients can reliably interpret the payloads

- Each element as a type element that is an enumeration of the specific aspect of a product being described, such as types of fees.

- Each element has a field name \\[additionalValue\\](#productfeaturetypedoc). This is a generic field with contents that will vary based on the type of object being described. The contents of this field for the ADDITIONAL_CARDS feature is the number of cards allowed while the contents of this field for the MAX_LIMIT constraint would be the maximum credit limit allowed for the product.

- An element in these arrays of the same type may appear more than once. For instance, a product may offer two separate loyalty programs that the customer can select from. A fixed term mortgage may have different rates for different term lengths.

- An element in these arrays may contain an additionalInfo and additionalInfoUri field. The additionalInfo field is used to provide displayable text clarifying the purpose of the element in some way when the product is presented to a customer. The additionalInfoUri provides a link to externally hosted information specifically relevant to that feature of the product.

- Depending on the type of data being represented there may be additional specific fields.

#### URIs To More Information

As the complexities and nuances of a financial product can not easily be fully expressed in a data structure without a high degree of complexity it is necessary to provide additional reference information that a potential customer can access so that they are fully informed of the features and implications of the product. The payloads for product reference therefore contain numerous fields that are provided to allow the product holder to describe the product more fully using a web page hosted on their online channels.

These URIs do not need to all link to different pages. If desired, they can all link to a single hosted page and use difference HTML anchors to focus on a specific topic such as eligibility or fees.

#### Linkage To Accounts

From the moment that a customer applies for a product and an account is created the account and the product that spawned it will diverge. Rates and features of the product may change and a discount may be negotiated for the account.

For this reason, while productCategory is a common field between accounts and products, there is no specific ID that can be used to link an account to a product within the regime.

Similarly, many of the fields and objects in the product payload will appear in the account detail payload but the structures and semantics are not identical as one refers to a product that can potentially be originated and one refers to an account that actual has been instantiated and created along with the associated decisions inherent in that process.

#### Dates

It is expected that data consumers needing this data will call relatively frequently to ensure the data they have is representative of the current offering from a bank. To minimise the volume and frequency of these calls the ability to set a lastUpdated field with the date and time of the last update to this product is included. A call for a list of products can then be filtered to only return products that have been updated since the last time that data was obtained using the updated-since query parameter.

In addition, the concept of effective date and time has also been included. This allows for a product to be marked for obsolescence, or introduction, from a certain time without the need for an update to show that a product has been changed. The inclusion of these dates also removes the need to represent deleted products in the payload. Products that are no long offered can be marked not effective for a few weeks before they are then removed from the product set as an option entirely.

            """,
       emptyObjectJson,
       json.parse("""{
  "data" : {
    "products" : [ {
      "effectiveTo" : "effectiveTo",
      "lastUpdated" : "lastUpdated",
      "additionalInformation" : {
        "eligibilityUri" : "eligibilityUri",
        "bundleUri" : "bundleUri",
        "feesAndPricingUri" : "feesAndPricingUri",
        "termsUri" : "termsUri",
        "overviewUri" : "overviewUri"
      },
      "brandName" : "brandName",
      "isTailored" : true,
      "productId" : "productId",
      "name" : "name",
      "description" : "description",
      "applicationUri" : "applicationUri",
      "effectiveFrom" : "effectiveFrom",
      "brand" : "brand",
      "productCategory" : { }
    }, {
      "effectiveTo" : "effectiveTo",
      "lastUpdated" : "lastUpdated",
      "additionalInformation" : {
        "eligibilityUri" : "eligibilityUri",
        "bundleUri" : "bundleUri",
        "feesAndPricingUri" : "feesAndPricingUri",
        "termsUri" : "termsUri",
        "overviewUri" : "overviewUri"
      },
      "brandName" : "brandName",
      "isTailored" : true,
      "productId" : "productId",
      "name" : "name",
      "description" : "description",
      "applicationUri" : "applicationUri",
      "effectiveFrom" : "effectiveFrom",
      "brand" : "brand",
      "productCategory" : { }
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
       ApiTag("Banking") ::ApiTag("Products") :: apiTagMockedData :: Nil
     )

     lazy val listProducts : OBPEndpoint = {
       case "banking":: "products" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{
  "data" : {
    "products" : [ {
      "effectiveTo" : "effectiveTo",
      "lastUpdated" : "lastUpdated",
      "additionalInformation" : {
        "eligibilityUri" : "eligibilityUri",
        "bundleUri" : "bundleUri",
        "feesAndPricingUri" : "feesAndPricingUri",
        "termsUri" : "termsUri",
        "overviewUri" : "overviewUri"
      },
      "brandName" : "brandName",
      "isTailored" : true,
      "productId" : "productId",
      "name" : "name",
      "description" : "description",
      "applicationUri" : "applicationUri",
      "effectiveFrom" : "effectiveFrom",
      "brand" : "brand",
      "productCategory" : { }
    }, {
      "effectiveTo" : "effectiveTo",
      "lastUpdated" : "lastUpdated",
      "additionalInformation" : {
        "eligibilityUri" : "eligibilityUri",
        "bundleUri" : "bundleUri",
        "feesAndPricingUri" : "feesAndPricingUri",
        "termsUri" : "termsUri",
        "overviewUri" : "overviewUri"
      },
      "brandName" : "brandName",
      "isTailored" : true,
      "productId" : "productId",
      "name" : "name",
      "description" : "description",
      "applicationUri" : "applicationUri",
      "effectiveFrom" : "effectiveFrom",
      "brand" : "brand",
      "productCategory" : { }
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
       listScheduledPayments, 
       apiVersion, 
       nameOf(listScheduledPayments),
       "GET", 
       "/banking/accounts/ACCOUNT_ID/payments/scheduled", 
       "Get Scheduled Payments for Account",
       s"""${mockedDataText(true)}
            Obtain scheduled, outgoing payments for a specific account

            """,
       emptyObjectJson,
       json.parse("""{
  "data" : {
    "scheduledPayments" : [ {
      "recurrence" : {
        "nextPaymentDate" : "nextPaymentDate",
        "eventBased" : {
          "description" : "description"
        },
        "onceOff" : {
          "paymentDate" : "paymentDate"
        },
        "recurrenceUType" : "onceOff",
        "intervalSchedule" : {
          "intervals" : [ {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          }, {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          } ],
          "paymentsRemaining" : 0,
          "finalPaymentDate" : "finalPaymentDate",
          "nonBusinessDayTreatment" : "ON"
        },
        "lastWeekDay" : {
          "paymentsRemaining" : 6,
          "interval" : "interval",
          "finalPaymentDate" : "finalPaymentDate",
          "lastWeekDay" : 1
        }
      },
      "scheduledPaymentId" : "scheduledPaymentId",
      "payeeReference" : "payeeReference",
      "payerReference" : "payerReference",
      "nickname" : "nickname",
      "from" : {
        "accountId" : "accountId"
      },
      "paymentSet" : [ {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      }, {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      } ],
      "status" : "ACTIVE"
    }, {
      "recurrence" : {
        "nextPaymentDate" : "nextPaymentDate",
        "eventBased" : {
          "description" : "description"
        },
        "onceOff" : {
          "paymentDate" : "paymentDate"
        },
        "recurrenceUType" : "onceOff",
        "intervalSchedule" : {
          "intervals" : [ {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          }, {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          } ],
          "paymentsRemaining" : 0,
          "finalPaymentDate" : "finalPaymentDate",
          "nonBusinessDayTreatment" : "ON"
        },
        "lastWeekDay" : {
          "paymentsRemaining" : 6,
          "interval" : "interval",
          "finalPaymentDate" : "finalPaymentDate",
          "lastWeekDay" : 1
        }
      },
      "scheduledPaymentId" : "scheduledPaymentId",
      "payeeReference" : "payeeReference",
      "payerReference" : "payerReference",
      "nickname" : "nickname",
      "from" : {
        "accountId" : "accountId"
      },
      "paymentSet" : [ {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      }, {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      } ],
      "status" : "ACTIVE"
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
       ApiTag("Banking") ::ApiTag("Scheduled Payments") :: apiTagMockedData :: Nil
     )

     lazy val listScheduledPayments : OBPEndpoint = {
       case "banking":: "accounts" :: accountId:: "payments":: "scheduled" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{
  "data" : {
    "scheduledPayments" : [ {
      "recurrence" : {
        "nextPaymentDate" : "nextPaymentDate",
        "eventBased" : {
          "description" : "description"
        },
        "onceOff" : {
          "paymentDate" : "paymentDate"
        },
        "recurrenceUType" : "onceOff",
        "intervalSchedule" : {
          "intervals" : [ {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          }, {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          } ],
          "paymentsRemaining" : 0,
          "finalPaymentDate" : "finalPaymentDate",
          "nonBusinessDayTreatment" : "ON"
        },
        "lastWeekDay" : {
          "paymentsRemaining" : 6,
          "interval" : "interval",
          "finalPaymentDate" : "finalPaymentDate",
          "lastWeekDay" : 1
        }
      },
      "scheduledPaymentId" : "scheduledPaymentId",
      "payeeReference" : "payeeReference",
      "payerReference" : "payerReference",
      "nickname" : "nickname",
      "from" : {
        "accountId" : "accountId"
      },
      "paymentSet" : [ {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      }, {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      } ],
      "status" : "ACTIVE"
    }, {
      "recurrence" : {
        "nextPaymentDate" : "nextPaymentDate",
        "eventBased" : {
          "description" : "description"
        },
        "onceOff" : {
          "paymentDate" : "paymentDate"
        },
        "recurrenceUType" : "onceOff",
        "intervalSchedule" : {
          "intervals" : [ {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          }, {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          } ],
          "paymentsRemaining" : 0,
          "finalPaymentDate" : "finalPaymentDate",
          "nonBusinessDayTreatment" : "ON"
        },
        "lastWeekDay" : {
          "paymentsRemaining" : 6,
          "interval" : "interval",
          "finalPaymentDate" : "finalPaymentDate",
          "lastWeekDay" : 1
        }
      },
      "scheduledPaymentId" : "scheduledPaymentId",
      "payeeReference" : "payeeReference",
      "payerReference" : "payerReference",
      "nickname" : "nickname",
      "from" : {
        "accountId" : "accountId"
      },
      "paymentSet" : [ {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      }, {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      } ],
      "status" : "ACTIVE"
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
       listScheduledPaymentsBulk, 
       apiVersion, 
       nameOf(listScheduledPaymentsBulk),
       "GET", 
       "/banking/payments/scheduled", 
       "Get Scheduled Payments Bulk",
       s"""${mockedDataText(true)}
            Obtain scheduled payments for multiple, filtered accounts that are the source of funds for the payments

            """,
       emptyObjectJson,
       json.parse("""{
  "data" : {
    "scheduledPayments" : [ {
      "recurrence" : {
        "nextPaymentDate" : "nextPaymentDate",
        "eventBased" : {
          "description" : "description"
        },
        "onceOff" : {
          "paymentDate" : "paymentDate"
        },
        "recurrenceUType" : "onceOff",
        "intervalSchedule" : {
          "intervals" : [ {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          }, {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          } ],
          "paymentsRemaining" : 0,
          "finalPaymentDate" : "finalPaymentDate",
          "nonBusinessDayTreatment" : "ON"
        },
        "lastWeekDay" : {
          "paymentsRemaining" : 6,
          "interval" : "interval",
          "finalPaymentDate" : "finalPaymentDate",
          "lastWeekDay" : 1
        }
      },
      "scheduledPaymentId" : "scheduledPaymentId",
      "payeeReference" : "payeeReference",
      "payerReference" : "payerReference",
      "nickname" : "nickname",
      "from" : {
        "accountId" : "accountId"
      },
      "paymentSet" : [ {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      }, {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      } ],
      "status" : "ACTIVE"
    }, {
      "recurrence" : {
        "nextPaymentDate" : "nextPaymentDate",
        "eventBased" : {
          "description" : "description"
        },
        "onceOff" : {
          "paymentDate" : "paymentDate"
        },
        "recurrenceUType" : "onceOff",
        "intervalSchedule" : {
          "intervals" : [ {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          }, {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          } ],
          "paymentsRemaining" : 0,
          "finalPaymentDate" : "finalPaymentDate",
          "nonBusinessDayTreatment" : "ON"
        },
        "lastWeekDay" : {
          "paymentsRemaining" : 6,
          "interval" : "interval",
          "finalPaymentDate" : "finalPaymentDate",
          "lastWeekDay" : 1
        }
      },
      "scheduledPaymentId" : "scheduledPaymentId",
      "payeeReference" : "payeeReference",
      "payerReference" : "payerReference",
      "nickname" : "nickname",
      "from" : {
        "accountId" : "accountId"
      },
      "paymentSet" : [ {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      }, {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      } ],
      "status" : "ACTIVE"
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
       ApiTag("Banking") ::ApiTag("Scheduled Payments") :: apiTagMockedData :: Nil
     )

     lazy val listScheduledPaymentsBulk : OBPEndpoint = {
       case "banking":: "payments":: "scheduled" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{
  "data" : {
    "scheduledPayments" : [ {
      "recurrence" : {
        "nextPaymentDate" : "nextPaymentDate",
        "eventBased" : {
          "description" : "description"
        },
        "onceOff" : {
          "paymentDate" : "paymentDate"
        },
        "recurrenceUType" : "onceOff",
        "intervalSchedule" : {
          "intervals" : [ {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          }, {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          } ],
          "paymentsRemaining" : 0,
          "finalPaymentDate" : "finalPaymentDate",
          "nonBusinessDayTreatment" : "ON"
        },
        "lastWeekDay" : {
          "paymentsRemaining" : 6,
          "interval" : "interval",
          "finalPaymentDate" : "finalPaymentDate",
          "lastWeekDay" : 1
        }
      },
      "scheduledPaymentId" : "scheduledPaymentId",
      "payeeReference" : "payeeReference",
      "payerReference" : "payerReference",
      "nickname" : "nickname",
      "from" : {
        "accountId" : "accountId"
      },
      "paymentSet" : [ {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      }, {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      } ],
      "status" : "ACTIVE"
    }, {
      "recurrence" : {
        "nextPaymentDate" : "nextPaymentDate",
        "eventBased" : {
          "description" : "description"
        },
        "onceOff" : {
          "paymentDate" : "paymentDate"
        },
        "recurrenceUType" : "onceOff",
        "intervalSchedule" : {
          "intervals" : [ {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          }, {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          } ],
          "paymentsRemaining" : 0,
          "finalPaymentDate" : "finalPaymentDate",
          "nonBusinessDayTreatment" : "ON"
        },
        "lastWeekDay" : {
          "paymentsRemaining" : 6,
          "interval" : "interval",
          "finalPaymentDate" : "finalPaymentDate",
          "lastWeekDay" : 1
        }
      },
      "scheduledPaymentId" : "scheduledPaymentId",
      "payeeReference" : "payeeReference",
      "payerReference" : "payerReference",
      "nickname" : "nickname",
      "from" : {
        "accountId" : "accountId"
      },
      "paymentSet" : [ {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      }, {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      } ],
      "status" : "ACTIVE"
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
       listScheduledPaymentsSpecificAccounts, 
       apiVersion, 
       nameOf(listScheduledPaymentsSpecificAccounts),
       "POST", 
       "/banking/payments/scheduled", 
       "Get Scheduled Payments For Specific Accounts",
       s"""${mockedDataText(true)}
            Obtain scheduled payments for a specified list of accounts

            """,
       json.parse("""{
  "data" : {
    "accountIds" : [ "accountIds", "accountIds" ]
  },
    "meta" : " ",
}"""),
       json.parse("""{
  "data" : {
    "scheduledPayments" : [ {
      "recurrence" : {
        "nextPaymentDate" : "nextPaymentDate",
        "eventBased" : {
          "description" : "description"
        },
        "onceOff" : {
          "paymentDate" : "paymentDate"
        },
        "recurrenceUType" : "onceOff",
        "intervalSchedule" : {
          "intervals" : [ {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          }, {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          } ],
          "paymentsRemaining" : 0,
          "finalPaymentDate" : "finalPaymentDate",
          "nonBusinessDayTreatment" : "ON"
        },
        "lastWeekDay" : {
          "paymentsRemaining" : 6,
          "interval" : "interval",
          "finalPaymentDate" : "finalPaymentDate",
          "lastWeekDay" : 1
        }
      },
      "scheduledPaymentId" : "scheduledPaymentId",
      "payeeReference" : "payeeReference",
      "payerReference" : "payerReference",
      "nickname" : "nickname",
      "from" : {
        "accountId" : "accountId"
      },
      "paymentSet" : [ {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      }, {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      } ],
      "status" : "ACTIVE"
    }, {
      "recurrence" : {
        "nextPaymentDate" : "nextPaymentDate",
        "eventBased" : {
          "description" : "description"
        },
        "onceOff" : {
          "paymentDate" : "paymentDate"
        },
        "recurrenceUType" : "onceOff",
        "intervalSchedule" : {
          "intervals" : [ {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          }, {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          } ],
          "paymentsRemaining" : 0,
          "finalPaymentDate" : "finalPaymentDate",
          "nonBusinessDayTreatment" : "ON"
        },
        "lastWeekDay" : {
          "paymentsRemaining" : 6,
          "interval" : "interval",
          "finalPaymentDate" : "finalPaymentDate",
          "lastWeekDay" : 1
        }
      },
      "scheduledPaymentId" : "scheduledPaymentId",
      "payeeReference" : "payeeReference",
      "payerReference" : "payerReference",
      "nickname" : "nickname",
      "from" : {
        "accountId" : "accountId"
      },
      "paymentSet" : [ {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      }, {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      } ],
      "status" : "ACTIVE"
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
       ApiTag("Banking") ::ApiTag("Scheduled Payments") :: apiTagMockedData :: Nil
     )

     lazy val listScheduledPaymentsSpecificAccounts : OBPEndpoint = {
       case "banking":: "payments":: "scheduled" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{
  "data" : {
    "scheduledPayments" : [ {
      "recurrence" : {
        "nextPaymentDate" : "nextPaymentDate",
        "eventBased" : {
          "description" : "description"
        },
        "onceOff" : {
          "paymentDate" : "paymentDate"
        },
        "recurrenceUType" : "onceOff",
        "intervalSchedule" : {
          "intervals" : [ {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          }, {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          } ],
          "paymentsRemaining" : 0,
          "finalPaymentDate" : "finalPaymentDate",
          "nonBusinessDayTreatment" : "ON"
        },
        "lastWeekDay" : {
          "paymentsRemaining" : 6,
          "interval" : "interval",
          "finalPaymentDate" : "finalPaymentDate",
          "lastWeekDay" : 1
        }
      },
      "scheduledPaymentId" : "scheduledPaymentId",
      "payeeReference" : "payeeReference",
      "payerReference" : "payerReference",
      "nickname" : "nickname",
      "from" : {
        "accountId" : "accountId"
      },
      "paymentSet" : [ {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      }, {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      } ],
      "status" : "ACTIVE"
    }, {
      "recurrence" : {
        "nextPaymentDate" : "nextPaymentDate",
        "eventBased" : {
          "description" : "description"
        },
        "onceOff" : {
          "paymentDate" : "paymentDate"
        },
        "recurrenceUType" : "onceOff",
        "intervalSchedule" : {
          "intervals" : [ {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          }, {
            "interval" : "interval",
            "dayInInterval" : "dayInInterval"
          } ],
          "paymentsRemaining" : 0,
          "finalPaymentDate" : "finalPaymentDate",
          "nonBusinessDayTreatment" : "ON"
        },
        "lastWeekDay" : {
          "paymentsRemaining" : 6,
          "interval" : "interval",
          "finalPaymentDate" : "finalPaymentDate",
          "lastWeekDay" : 1
        }
      },
      "scheduledPaymentId" : "scheduledPaymentId",
      "payeeReference" : "payeeReference",
      "payerReference" : "payerReference",
      "nickname" : "nickname",
      "from" : {
        "accountId" : "accountId"
      },
      "paymentSet" : [ {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      }, {
        "isAmountCalculated" : true,
        "amount" : "amount",
        "currency" : "currency",
        "to" : {
          "domestic" : {
            "payeeAccountUType" : "account",
            "payId" : {
              "identifier" : "identifier",
              "name" : "name",
              "type" : "EMAIL"
            },
            "account" : {
              "bsb" : "bsb",
              "accountName" : "accountName",
              "accountNumber" : "accountNumber"
            },
            "card" : {
              "cardNumber" : "cardNumber"
            }
          },
          "accountId" : "accountId",
          "biller" : {
            "billerName" : "billerName",
            "crn" : "crn",
            "billerCode" : "billerCode"
          },
          "toUType" : "accountId",
          "payeeId" : "payeeId",
          "international" : {
            "bankDetails" : {
              "country" : "country",
              "routingNumber" : "routingNumber",
              "fedWireNumber" : "fedWireNumber",
              "chipNumber" : "chipNumber",
              "legalEntityIdentifier" : "legalEntityIdentifier",
              "accountNumber" : "accountNumber",
              "bankAddress" : {
                "address" : "address",
                "name" : "name"
              },
              "sortCode" : "sortCode",
              "beneficiaryBankBIC" : "beneficiaryBankBIC"
            },
            "beneficiaryDetails" : {
              "country" : "country",
              "name" : "name",
              "message" : "message"
            }
          }
        }
      } ],
      "status" : "ACTIVE"
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



