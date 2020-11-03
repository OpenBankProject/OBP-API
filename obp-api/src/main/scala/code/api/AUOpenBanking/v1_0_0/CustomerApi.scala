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

object APIMethods_CustomerApi extends RestHelper {
    val apiVersion =  ApiCollector.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      getCustomer ::
      getCustomerDetail ::
      Nil

            
     resourceDocs += ResourceDoc(
       getCustomer, 
       apiVersion, 
       nameOf(getCustomer),
       "GET", 
       "/common/customer", 
       "Get Customer",
       s"""${mockedDataText(true)}
            Obtain basic information on the customer that has authorised the current session

            """,
       emptyObjectJson,
       json.parse("""{
  "data" : {
    "person" : {
      "middleNames" : [ "middleNames", "middleNames" ],
      "firstName" : "firstName",
      "lastName" : "lastName",
      "occupationCode" : "occupationCode",
      "prefix" : "prefix",
      "suffix" : "suffix",
      "lastUpdateTime" : "lastUpdateTime"
    },
    "organisation" : {
      "agentRole" : "agentRole",
      "agentLastName" : "agentLastName",
      "establishmentDate" : "establishmentDate",
      "businessName" : "businessName",
      "registeredCountry" : "registeredCountry",
      "abn" : "abn",
      "acn" : "acn",
      "industryCode" : "industryCode",
      "organisationType" : "SOLE_TRADER",
      "legalName" : "legalName",
      "isACNCRegistered" : true,
      "agentFirstName" : "agentFirstName",
      "shortName" : "shortName",
      "lastUpdateTime" : "lastUpdateTime"
    },
    "customerUType" : "person"
  },
  "meta" : " ",
  "links" : {
    "self" : "self"
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Common") ::ApiTag("Customer") :: apiTagMockedData :: Nil
     )

     lazy val getCustomer : OBPEndpoint = {
       case "common":: "customer" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{
  "data" : {
    "person" : {
      "middleNames" : [ "middleNames", "middleNames" ],
      "firstName" : "firstName",
      "lastName" : "lastName",
      "occupationCode" : "occupationCode",
      "prefix" : "prefix",
      "suffix" : "suffix",
      "lastUpdateTime" : "lastUpdateTime"
    },
    "organisation" : {
      "agentRole" : "agentRole",
      "agentLastName" : "agentLastName",
      "establishmentDate" : "establishmentDate",
      "businessName" : "businessName",
      "registeredCountry" : "registeredCountry",
      "abn" : "abn",
      "acn" : "acn",
      "industryCode" : "industryCode",
      "organisationType" : "SOLE_TRADER",
      "legalName" : "legalName",
      "isACNCRegistered" : true,
      "agentFirstName" : "agentFirstName",
      "shortName" : "shortName",
      "lastUpdateTime" : "lastUpdateTime"
    },
    "customerUType" : "person"
  },
  "meta" : " ",
  "links" : {
    "self" : "self"
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getCustomerDetail, 
       apiVersion, 
       nameOf(getCustomerDetail),
       "GET", 
       "/common/customer/detail", 
       "Get Customer Detail",
       s"""${mockedDataText(true)}
            Obtain detailed information on the authorised customer within the current session.

            """,
       emptyObjectJson,
       json.parse("""{
  "data" : {
    "person" : "",
    "organisation" : "",
    "customerUType" : "person"
  },
  "meta" : " ",
  "links" : {
    "self" : "self"
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Common") ::ApiTag("Customer") :: apiTagMockedData :: Nil
     )

     lazy val getCustomerDetail : OBPEndpoint = {
       case "common":: "customer":: "detail" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{
  "data" : {
    "person" : "",
    "organisation" : "",
    "customerUType" : "person"
  },
  "meta" : " ",
  "links" : {
    "self" : "self"
  }
}"""), callContext)
           }
         }
       }

}



