package code.api.Polish.v2_1_1_1

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

object APIMethods_AISApi extends RestHelper {
    val apiVersion =  OBP_PAPI_2_1_1_1.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      deleteConsent ::
      getAccount ::
      getAccounts ::
      getHolds ::
      getTransactionDetail ::
      getTransactionsCancelled ::
      getTransactionsDone ::
      getTransactionsPending ::
      getTransactionsRejected ::
      getTransactionsScheduled ::
      Nil

            
     resourceDocs += ResourceDoc(
       deleteConsent, 
       apiVersion, 
       nameOf(deleteConsent),
       "POST", 
       "/accounts/v2_1_1.1/deleteConsent", 
       "Removes consent",
       s"""${mockedDataText(true)}
Removes consent""", 
       json.parse("""{
  "consentId" : "consentId"
}"""),
       emptyObjectJson,
       List(UserNotLoggedIn, UnknownError),
       ApiTag("AIS") :: apiTagMockedData :: Nil
     )

     lazy val deleteConsent : OBPEndpoint = {
       case "accounts":: "v2_1_1.1":: "deleteConsent" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             } yield {
             (NotImplemented, callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getAccount, 
       apiVersion, 
       nameOf(getAccount),
       "POST", 
       "/accounts/v2_1_1.1/getAccount", 
       "Get detailed information about user payment account",
       s"""${mockedDataText(true)}
User identification based on access token""", 
       json.parse("""{
  "requestHeader" : "",
  "accountNumber" : "accountNumber"
}"""),
       json.parse("""{
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  },
  "account" : {
    "auxData" : { },
    "bank" : {
      "address" : "",
      "bicOrSwift" : "bicOrSwift",
      "name" : "name"
    },
    "bookingBalance" : "bookingBalance",
    "accountType" : {
      "code" : "code",
      "description" : "description"
    },
    "accountTypeName" : "accountTypeName",
    "currency" : "currency",
    "nameAddress" : {
      "value" : [ "value", "value", "value", "value" ]
    },
    "accountNumber" : "accountNumber",
    "accountNameClient" : "accountNameClient",
    "accountHolderType" : "individual",
    "availableBalance" : "availableBalance"
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("AIS") :: apiTagMockedData :: Nil
     )

     lazy val getAccount : OBPEndpoint = {
       case "accounts":: "v2_1_1.1":: "getAccount" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             } yield {
             (json.parse("""{
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  },
  "account" : {
    "auxData" : { },
    "bank" : {
      "address" : "",
      "bicOrSwift" : "bicOrSwift",
      "name" : "name"
    },
    "bookingBalance" : "bookingBalance",
    "accountType" : {
      "code" : "code",
      "description" : "description"
    },
    "accountTypeName" : "accountTypeName",
    "currency" : "currency",
    "nameAddress" : {
      "value" : [ "value", "value", "value", "value" ]
    },
    "accountNumber" : "accountNumber",
    "accountNameClient" : "accountNameClient",
    "accountHolderType" : "individual",
    "availableBalance" : "availableBalance"
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getAccounts, 
       apiVersion, 
       nameOf(getAccounts),
       "POST", 
       "/accounts/v2_1_1.1/getAccounts", 
       "Get information about all user's payment account",
       s"""${mockedDataText(true)}
User identification based on access token""", 
       json.parse("""{
  "perPage" : 1,
  "requestHeader" : "",
  "pageId" : "pageId"
}"""),
       json.parse("""{
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  },
  "pageInfo" : {
    "previousPage" : "previousPage",
    "nextPage" : "nextPage"
  },
  "accounts" : [ {
    "accountType" : {
      "code" : "code",
      "description" : "description"
    },
    "accountTypeName" : "accountTypeName",
    "accountNumber" : "accountNumber"
  }, {
    "accountType" : {
      "code" : "code",
      "description" : "description"
    },
    "accountTypeName" : "accountTypeName",
    "accountNumber" : "accountNumber"
  } ]
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("AIS") :: apiTagMockedData :: Nil
     )

     lazy val getAccounts : OBPEndpoint = {
       case "accounts":: "v2_1_1.1":: "getAccounts" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             } yield {
             (json.parse("""{
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  },
  "pageInfo" : {
    "previousPage" : "previousPage",
    "nextPage" : "nextPage"
  },
  "accounts" : [ {
    "accountType" : {
      "code" : "code",
      "description" : "description"
    },
    "accountTypeName" : "accountTypeName",
    "accountNumber" : "accountNumber"
  }, {
    "accountType" : {
      "code" : "code",
      "description" : "description"
    },
    "accountTypeName" : "accountTypeName",
    "accountNumber" : "accountNumber"
  } ]
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getHolds, 
       apiVersion, 
       nameOf(getHolds),
       "POST", 
       "/accounts/v2_1_1.1/getHolds", 
       "Get list of user's holded operations",
       s"""${mockedDataText(true)}
""", 
       json.parse(""""""""),
       json.parse("""{
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  },
  "pageInfo" : {
    "previousPage" : "previousPage",
    "nextPage" : "nextPage"
  },
  "holds" : [ "", "" ]
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("AIS") :: apiTagMockedData :: Nil
     )

     lazy val getHolds : OBPEndpoint = {
       case "accounts":: "v2_1_1.1":: "getHolds" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             } yield {
             (json.parse("""{
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  },
  "pageInfo" : {
    "previousPage" : "previousPage",
    "nextPage" : "nextPage"
  },
  "holds" : [ "", "" ]
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getTransactionDetail, 
       apiVersion, 
       nameOf(getTransactionDetail),
       "POST", 
       "/accounts/v2_1_1.1/getTransactionDetail", 
       "Get detailed information about user's single transaction",
       s"""${mockedDataText(true)}
""", 
       json.parse("""{
  "itemId" : "itemId",
  "requestHeader" : "",
  "bookingDate" : "2000-01-23T04:56:07.000+00:00",
  "accountNumber" : "accountNumber"
}"""),
       json.parse("""{
  "baseInfo" : "",
  "cardInfo" : {
    "cardHolder" : "cardHolder",
    "cardNumber" : "cardNumber"
  },
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  },
  "baseCurrency" : "baseCurrency",
  "usedPaymentInstrumentId" : "usedPaymentInstrumentId",
  "holdExpirationDate" : "2000-01-23T04:56:07.000+00:00",
  "zusInfo" : {
    "contributionType" : "contributionType",
    "contributionId" : "contributionId",
    "paymentTypeId" : "paymentTypeId",
    "payerInfo" : {
      "nip" : "nip",
      "additionalPayorId" : "additionalPayorId",
      "additionalPayorIdType" : "P"
    },
    "contributionPeriod" : "contributionPeriod",
    "obligationId" : "obligationId"
  },
  "usInfo" : {
    "periodId" : "periodId",
    "periodType" : "periodType",
    "payerInfo" : {
      "payorId" : "payorId",
      "payorIdType" : "N"
    },
    "formCode" : "formCode",
    "year" : 6026,
    "obligationId" : "obligationId"
  },
  "tppTransactionId" : "tppTransactionId",
  "transactionRate" : [ {
    "rate" : 0.8008281904610115,
    "toCurrency" : "toCurrency",
    "fromCurrency" : "fromCurrency"
  }, {
    "rate" : 0.8008281904610115,
    "toCurrency" : "toCurrency",
    "fromCurrency" : "fromCurrency"
  } ],
  "currencyDate" : "2000-01-23T04:56:07.000+00:00",
  "rejectionReason" : "rejectionReason",
  "amountBaseCurrency" : "amountBaseCurrency",
  "tppName" : "tppName"
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("AIS") :: apiTagMockedData :: Nil
     )

     lazy val getTransactionDetail : OBPEndpoint = {
       case "accounts":: "v2_1_1.1":: "getTransactionDetail" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             } yield {
             (json.parse("""{
  "baseInfo" : "",
  "cardInfo" : {
    "cardHolder" : "cardHolder",
    "cardNumber" : "cardNumber"
  },
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  },
  "baseCurrency" : "baseCurrency",
  "usedPaymentInstrumentId" : "usedPaymentInstrumentId",
  "holdExpirationDate" : "2000-01-23T04:56:07.000+00:00",
  "zusInfo" : {
    "contributionType" : "contributionType",
    "contributionId" : "contributionId",
    "paymentTypeId" : "paymentTypeId",
    "payerInfo" : {
      "nip" : "nip",
      "additionalPayorId" : "additionalPayorId",
      "additionalPayorIdType" : "P"
    },
    "contributionPeriod" : "contributionPeriod",
    "obligationId" : "obligationId"
  },
  "usInfo" : {
    "periodId" : "periodId",
    "periodType" : "periodType",
    "payerInfo" : {
      "payorId" : "payorId",
      "payorIdType" : "N"
    },
    "formCode" : "formCode",
    "year" : 6026,
    "obligationId" : "obligationId"
  },
  "tppTransactionId" : "tppTransactionId",
  "transactionRate" : [ {
    "rate" : 0.8008281904610115,
    "toCurrency" : "toCurrency",
    "fromCurrency" : "fromCurrency"
  }, {
    "rate" : 0.8008281904610115,
    "toCurrency" : "toCurrency",
    "fromCurrency" : "fromCurrency"
  } ],
  "currencyDate" : "2000-01-23T04:56:07.000+00:00",
  "rejectionReason" : "rejectionReason",
  "amountBaseCurrency" : "amountBaseCurrency",
  "tppName" : "tppName"
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getTransactionsCancelled, 
       apiVersion, 
       nameOf(getTransactionsCancelled),
       "POST", 
       "/accounts/v2_1_1.1/getTransactionsCancelled", 
       "Get list of user cancelled transactions",
       s"""${mockedDataText(true)}
""", 
       json.parse(""""""""),
       json.parse("""{
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  },
  "pageInfo" : {
    "previousPage" : "previousPage",
    "nextPage" : "nextPage"
  },
  "transactions" : [ "", "" ]
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("AIS") :: apiTagMockedData :: Nil
     )

     lazy val getTransactionsCancelled : OBPEndpoint = {
       case "accounts":: "v2_1_1.1":: "getTransactionsCancelled" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             } yield {
             (json.parse("""{
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  },
  "pageInfo" : {
    "previousPage" : "previousPage",
    "nextPage" : "nextPage"
  },
  "transactions" : [ "", "" ]
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getTransactionsDone, 
       apiVersion, 
       nameOf(getTransactionsDone),
       "POST", 
       "/accounts/v2_1_1.1/getTransactionsDone", 
       "Get list of user done transactions",
       s"""${mockedDataText(true)}
""", 
       json.parse(""""""""),
       json.parse("""{
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  },
  "pageInfo" : {
    "previousPage" : "previousPage",
    "nextPage" : "nextPage"
  },
  "transactions" : [ "", "" ]
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("AIS") :: apiTagMockedData :: Nil
     )

     lazy val getTransactionsDone : OBPEndpoint = {
       case "accounts":: "v2_1_1.1":: "getTransactionsDone" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             } yield {
             (json.parse("""{
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  },
  "pageInfo" : {
    "previousPage" : "previousPage",
    "nextPage" : "nextPage"
  },
  "transactions" : [ "", "" ]
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getTransactionsPending, 
       apiVersion, 
       nameOf(getTransactionsPending),
       "POST", 
       "/accounts/v2_1_1.1/getTransactionsPending", 
       "Get list of user's pending transactions",
       s"""${mockedDataText(true)}
""", 
       json.parse(""""""""),
       json.parse("""{
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  },
  "pageInfo" : {
    "previousPage" : "previousPage",
    "nextPage" : "nextPage"
  },
  "transactions" : [ "", "" ]
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("AIS") :: apiTagMockedData :: Nil
     )

     lazy val getTransactionsPending : OBPEndpoint = {
       case "accounts":: "v2_1_1.1":: "getTransactionsPending" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             } yield {
             (json.parse("""{
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  },
  "pageInfo" : {
    "previousPage" : "previousPage",
    "nextPage" : "nextPage"
  },
  "transactions" : [ "", "" ]
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getTransactionsRejected, 
       apiVersion, 
       nameOf(getTransactionsRejected),
       "POST", 
       "/accounts/v2_1_1.1/getTransactionsRejected", 
       "Get list of user's rejected transactions",
       s"""${mockedDataText(true)}
""", 
       json.parse(""""""""),
       json.parse("""{
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  },
  "pageInfo" : {
    "previousPage" : "previousPage",
    "nextPage" : "nextPage"
  },
  "transactions" : [ "", "" ]
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("AIS") :: apiTagMockedData :: Nil
     )

     lazy val getTransactionsRejected : OBPEndpoint = {
       case "accounts":: "v2_1_1.1":: "getTransactionsRejected" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             } yield {
             (json.parse("""{
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  },
  "pageInfo" : {
    "previousPage" : "previousPage",
    "nextPage" : "nextPage"
  },
  "transactions" : [ "", "" ]
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getTransactionsScheduled, 
       apiVersion, 
       nameOf(getTransactionsScheduled),
       "POST", 
       "/accounts/v2_1_1.1/getTransactionsScheduled", 
       "Get list of user scheduled transactions",
       s"""${mockedDataText(true)}
""", 
       json.parse(""""""""),
       json.parse("""{
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  },
  "pageInfo" : {
    "previousPage" : "previousPage",
    "nextPage" : "nextPage"
  },
  "transactions" : [ "", "" ]
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("AIS") :: apiTagMockedData :: Nil
     )

     lazy val getTransactionsScheduled : OBPEndpoint = {
       case "accounts":: "v2_1_1.1":: "getTransactionsScheduled" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
             } yield {
             (json.parse("""{
  "responseHeader" : {
    "sendDate" : "2000-01-23T04:56:07.000+00:00",
    "requestId" : "046b6c7f-0b8a-43b9-b35d-6489e6daee91",
    "isCallback" : true
  },
  "pageInfo" : {
    "previousPage" : "previousPage",
    "nextPage" : "nextPage"
  },
  "transactions" : [ "", "" ]
}"""), callContext)
           }
         }
       }

}



