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

object APIMethods_PayeesApi extends RestHelper {
    val apiVersion =  ApiCollector.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      getPayeeDetail ::
      listPayees ::
      Nil

            
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

}



