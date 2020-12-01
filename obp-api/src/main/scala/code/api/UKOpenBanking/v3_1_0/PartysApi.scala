package code.api.UKOpenBanking.v3_1_0

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

object APIMethods_PartysApi extends RestHelper {
    val apiVersion = OBP_UKOpenBanking_310.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      getAccountsAccountIdParty ::
      getParty ::
      Nil

            
     resourceDocs += ResourceDoc(
       getAccountsAccountIdParty, 
       apiVersion, 
       nameOf(getAccountsAccountIdParty),
       "GET", 
       "/accounts/ACCOUNTID/party", 
       "Get Party",
       s"""${mockedDataText(true)}
""", 
       emptyObjectJson,
       json.parse("""{
  "Meta" : {
    "FirstAvailableDateTime" : { },
    "TotalPages" : 0
  },
  "Links" : {
    "Last" : "http://example.com/aeiou",
    "Prev" : "http://example.com/aeiou",
    "Next" : "http://example.com/aeiou",
    "Self" : "http://example.com/aeiou",
    "First" : "http://example.com/aeiou"
  },
  "Data" : {
    "Party" : {
      "PartyNumber" : "PartyNumber",
      "PartyId" : "PartyId",
      "Address" : [ {
        "StreetName" : "StreetName",
        "CountrySubDivision" : "CountrySubDivision",
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : "BuildingNumber",
        "TownName" : "TownName",
        "Country" : "Country",
        "AddressType" : { },
        "PostCode" : "PostCode"
      }, {
        "StreetName" : "StreetName",
        "CountrySubDivision" : "CountrySubDivision",
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : "BuildingNumber",
        "TownName" : "TownName",
        "Country" : "Country",
        "AddressType" : { },
        "PostCode" : "PostCode"
      } ],
      "Phone" : "Phone",
      "Mobile" : "Mobile",
      "PartyType" : { },
      "EmailAddress" : "EmailAddress",
      "Name" : "Name"
    }
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Partys") :: apiTagMockedData :: Nil
     )

     lazy val getAccountsAccountIdParty : OBPEndpoint = {
       case "accounts" :: accountid:: "party" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
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
    "Self" : "http://example.com/aeiou",
    "First" : "http://example.com/aeiou"
  },
  "Data" : {
    "Party" : {
      "PartyNumber" : "PartyNumber",
      "PartyId" : "PartyId",
      "Address" : [ {
        "StreetName" : "StreetName",
        "CountrySubDivision" : "CountrySubDivision",
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : "BuildingNumber",
        "TownName" : "TownName",
        "Country" : "Country",
        "AddressType" : { },
        "PostCode" : "PostCode"
      }, {
        "StreetName" : "StreetName",
        "CountrySubDivision" : "CountrySubDivision",
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : "BuildingNumber",
        "TownName" : "TownName",
        "Country" : "Country",
        "AddressType" : { },
        "PostCode" : "PostCode"
      } ],
      "Phone" : "Phone",
      "Mobile" : "Mobile",
      "PartyType" : { },
      "EmailAddress" : "EmailAddress",
      "Name" : "Name"
    }
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getParty, 
       apiVersion, 
       nameOf(getParty),
       "GET", 
       "/party", 
       "Get Party",
       s"""${mockedDataText(true)}
""", 
       emptyObjectJson,
       json.parse("""{
  "Meta" : {
    "FirstAvailableDateTime" : { },
    "TotalPages" : 0
  },
  "Links" : {
    "Last" : "http://example.com/aeiou",
    "Prev" : "http://example.com/aeiou",
    "Next" : "http://example.com/aeiou",
    "Self" : "http://example.com/aeiou",
    "First" : "http://example.com/aeiou"
  },
  "Data" : {
    "Party" : {
      "PartyNumber" : "PartyNumber",
      "PartyId" : "PartyId",
      "Address" : [ {
        "StreetName" : "StreetName",
        "CountrySubDivision" : "CountrySubDivision",
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : "BuildingNumber",
        "TownName" : "TownName",
        "Country" : "Country",
        "AddressType" : { },
        "PostCode" : "PostCode"
      }, {
        "StreetName" : "StreetName",
        "CountrySubDivision" : "CountrySubDivision",
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : "BuildingNumber",
        "TownName" : "TownName",
        "Country" : "Country",
        "AddressType" : { },
        "PostCode" : "PostCode"
      } ],
      "Phone" : "Phone",
      "Mobile" : "Mobile",
      "PartyType" : { },
      "EmailAddress" : "EmailAddress",
      "Name" : "Name"
    }
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Partys") :: apiTagMockedData :: Nil
     )

     lazy val getParty : OBPEndpoint = {
       case "party" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc)
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
    "Self" : "http://example.com/aeiou",
    "First" : "http://example.com/aeiou"
  },
  "Data" : {
    "Party" : {
      "PartyNumber" : "PartyNumber",
      "PartyId" : "PartyId",
      "Address" : [ {
        "StreetName" : "StreetName",
        "CountrySubDivision" : "CountrySubDivision",
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : "BuildingNumber",
        "TownName" : "TownName",
        "Country" : "Country",
        "AddressType" : { },
        "PostCode" : "PostCode"
      }, {
        "StreetName" : "StreetName",
        "CountrySubDivision" : "CountrySubDivision",
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : "BuildingNumber",
        "TownName" : "TownName",
        "Country" : "Country",
        "AddressType" : { },
        "PostCode" : "PostCode"
      } ],
      "Phone" : "Phone",
      "Mobile" : "Mobile",
      "PartyType" : { },
      "EmailAddress" : "EmailAddress",
      "Name" : "Name"
    }
  }
}"""), callContext)
           }
         }
       }

}



