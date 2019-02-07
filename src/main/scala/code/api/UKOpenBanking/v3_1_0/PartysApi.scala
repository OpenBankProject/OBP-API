package code.api.UKOpenBanking.v3_1_0

import code.api.APIFailureNewStyle
import code.api.berlin.group.v1_3.JvalueCaseClass
import net.liftweb.json
import net.liftweb.json._
import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3
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

trait APIMethods_PartysApi { self: RestHelper =>
  val ImplementationsPartysApi = new Object() {
    val apiVersion: ApiVersion = ApiVersion.berlinGroupV1_3
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(resourceDocs, apiRelations)
    implicit val formats = net.liftweb.json.DefaultFormats
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
       json.parse(""""""),
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
       Catalogs(notCore, notPSD2, notOBWG), 
       apiTagPartys :: apiTagMockedData :: Nil
     )

     lazy val getAccountsAccountIdParty : OBPEndpoint = {
       case "accounts" :: accountid:: "party" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
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
       json.parse(""""""),
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
       Catalogs(notCore, notPSD2, notOBWG), 
       apiTagPartys :: apiTagMockedData :: Nil
     )

     lazy val getParty : OBPEndpoint = {
       case "party" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
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
}



