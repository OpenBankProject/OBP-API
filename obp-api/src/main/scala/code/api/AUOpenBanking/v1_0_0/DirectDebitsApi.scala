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

object APIMethods_DirectDebitsApi extends RestHelper {
    val apiVersion =  ApiCollector.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      listDirectDebits ::
      listDirectDebitsBulk ::
      listDirectDebitsSpecificAccounts ::
      Nil

            
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

}



