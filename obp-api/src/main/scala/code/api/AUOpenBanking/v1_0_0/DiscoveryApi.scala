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

object APIMethods_DiscoveryApi extends RestHelper {
    val apiVersion =  ApiCollector.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      getOutages ::
      getStatus ::
      Nil

            
     resourceDocs += ResourceDoc(
       getOutages, 
       apiVersion, 
       nameOf(getOutages),
       "GET", 
       "/discovery/outages", 
       "Get Outages",
       s"""${mockedDataText(true)}
            Obtain a list of scheduled outages for the implementation

            """,
       emptyObjectJson,
       json.parse("""{
  "data" : {
    "outages" : [ {
      "duration" : "duration",
      "outageTime" : "outageTime",
      "isPartial" : true,
      "explanation" : "explanation"
    }, {
      "duration" : "duration",
      "outageTime" : "outageTime",
      "isPartial" : true,
      "explanation" : "explanation"
    } ]
  },
  "meta" : " ",
  "links" : {
    "self" : "self"
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Common") ::ApiTag("Discovery") :: apiTagMockedData :: Nil
     )

     lazy val getOutages : OBPEndpoint = {
       case "discovery":: "outages" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{
  "data" : {
    "outages" : [ {
      "duration" : "duration",
      "outageTime" : "outageTime",
      "isPartial" : true,
      "explanation" : "explanation"
    }, {
      "duration" : "duration",
      "outageTime" : "outageTime",
      "isPartial" : true,
      "explanation" : "explanation"
    } ]
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
       getStatus, 
       apiVersion, 
       nameOf(getStatus),
       "GET", 
       "/discovery/status", 
       "Get Status",
       s"""${mockedDataText(true)}
            Obtain a health check status for the implementation

            """,
       emptyObjectJson,
       json.parse("""{
  "data" : {
    "updateTime" : "updateTime",
    "explanation" : "explanation",
    "expectedResolutionTime" : "expectedResolutionTime",
    "detectionTime" : "detectionTime",
    "status" : "OK"
  },
  "meta" : " ",
  "links" : {
    "self" : "self"
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Common") ::ApiTag("Discovery") :: apiTagMockedData :: Nil
     )

     lazy val getStatus : OBPEndpoint = {
       case "discovery":: "status" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{
  "data" : {
    "updateTime" : "updateTime",
    "explanation" : "explanation",
    "expectedResolutionTime" : "expectedResolutionTime",
    "detectionTime" : "detectionTime",
    "status" : "OK"
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



