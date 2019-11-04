package code.api.AUOpenBanking.v1

import code.api.APIFailureNewStyle
import code.api.berlin.group.v1_3.JvalueCaseClass
import net.liftweb.json
import net.liftweb.json._
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
import code.api.AUOpenBanking.v1.ApiCollector
import code.api.util.ApiTag

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
       json.parse(""""""),
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
  "meta" : { },
  "links" : {
    "self" : "self"
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("Common") ::ApiTag("Customer") :: apiTagMockedData :: Nil
     )

     lazy val getCustomer : OBPEndpoint = {
       case "common":: "customer" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc, UserNotLoggedIn)
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
  "meta" : { },
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
       json.parse(""""""),
       json.parse("""{
  "data" : {
    "person" : "",
    "organisation" : "",
    "customerUType" : "person"
  },
  "meta" : { },
  "links" : {
    "self" : "self"
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("Common") ::ApiTag("Customer") :: apiTagMockedData :: Nil
     )

     lazy val getCustomerDetail : OBPEndpoint = {
       case "common":: "customer":: "detail" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{
  "data" : {
    "person" : "",
    "organisation" : "",
    "customerUType" : "person"
  },
  "meta" : { },
  "links" : {
    "self" : "self"
  }
}"""), callContext)
           }
         }
       }

}



