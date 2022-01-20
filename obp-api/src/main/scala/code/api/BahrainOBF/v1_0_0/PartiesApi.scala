package code.api.BahrainOBF.v1_0_0

import code.api.APIFailureNewStyle
import code.api.berlin.group.v1_3.JvalueCaseClass
import net.liftweb.json
import net.liftweb.json._
import code.api.util.APIUtil.{defaultBankId, _}
import code.api.util.NewStyle
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
import com.openbankproject.commons.ExecutionContext.Implicits.global
import scala.concurrent.Future
import code.api.BahrainOBF.v1_0_0.ApiCollector
import code.api.util.ApiTag

object APIMethods_PartiesApi extends RestHelper {
    val apiVersion =  ApiCollector.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      accountsAccountIdPartiesGet ::
      accountsAccountIdPartyGet ::
      partyGet ::
      Nil

            
     resourceDocs += ResourceDoc(
       accountsAccountIdPartiesGet, 
       apiVersion, 
       nameOf(accountsAccountIdPartiesGet),
       "GET", 
       "/accounts/ACCOUNT_ID/parties", 
       "Get Accounts Parties by AccountId",
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
    "First" : "http://example.com/aeiou",
    "Self" : "http://example.com/aeiou"
  },
  "Data" : {
    "Party" : [ {
      "BeneficialOwnership" : true,
      "Address" : [ {
        "CountrySubDivision" : { },
        "StreetName" : { },
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : { },
        "TownName" : { },
        "Country" : { },
        "AddressType" : { },
        "PostCode" : { }
      }, {
        "CountrySubDivision" : { },
        "StreetName" : { },
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : { },
        "TownName" : { },
        "Country" : { },
        "AddressType" : { },
        "PostCode" : { }
      } ],
      "AccountRole" : { },
      "Mobile" : { },
      "EmailAddress" : { },
      "LegalStructure" : { },
      "Name" : { },
      "PartyNumber" : { },
      "Relationships" : {
        "Account" : {
          "Related" : "http://example.com/aeiou",
          "Id" : "Id"
        }
      },
      "FullLegalName" : { },
      "PartyId" : { },
      "Phone" : { },
      "PartyType" : { }
    }, {
      "BeneficialOwnership" : true,
      "Address" : [ {
        "CountrySubDivision" : { },
        "StreetName" : { },
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : { },
        "TownName" : { },
        "Country" : { },
        "AddressType" : { },
        "PostCode" : { }
      }, {
        "CountrySubDivision" : { },
        "StreetName" : { },
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : { },
        "TownName" : { },
        "Country" : { },
        "AddressType" : { },
        "PostCode" : { }
      } ],
      "AccountRole" : { },
      "Mobile" : { },
      "EmailAddress" : { },
      "LegalStructure" : { },
      "Name" : { },
      "PartyNumber" : { },
      "Relationships" : {
        "Account" : {
          "Related" : "http://example.com/aeiou",
          "Id" : "Id"
        }
      },
      "FullLegalName" : { },
      "PartyId" : { },
      "Phone" : { },
      "PartyType" : { }
    } ]
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Parties") :: apiTagMockedData :: Nil
     )

     lazy val accountsAccountIdPartiesGet : OBPEndpoint = {
       case "accounts" :: accountId:: "parties" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
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
    "First" : "http://example.com/aeiou",
    "Self" : "http://example.com/aeiou"
  },
  "Data" : {
    "Party" : [ {
      "BeneficialOwnership" : true,
      "Address" : [ {
        "CountrySubDivision" : { },
        "StreetName" : { },
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : { },
        "TownName" : { },
        "Country" : { },
        "AddressType" : { },
        "PostCode" : { }
      }, {
        "CountrySubDivision" : { },
        "StreetName" : { },
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : { },
        "TownName" : { },
        "Country" : { },
        "AddressType" : { },
        "PostCode" : { }
      } ],
      "AccountRole" : { },
      "Mobile" : { },
      "EmailAddress" : { },
      "LegalStructure" : { },
      "Name" : { },
      "PartyNumber" : { },
      "Relationships" : {
        "Account" : {
          "Related" : "http://example.com/aeiou",
          "Id" : "Id"
        }
      },
      "FullLegalName" : { },
      "PartyId" : { },
      "Phone" : { },
      "PartyType" : { }
    }, {
      "BeneficialOwnership" : true,
      "Address" : [ {
        "CountrySubDivision" : { },
        "StreetName" : { },
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : { },
        "TownName" : { },
        "Country" : { },
        "AddressType" : { },
        "PostCode" : { }
      }, {
        "CountrySubDivision" : { },
        "StreetName" : { },
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : { },
        "TownName" : { },
        "Country" : { },
        "AddressType" : { },
        "PostCode" : { }
      } ],
      "AccountRole" : { },
      "Mobile" : { },
      "EmailAddress" : { },
      "LegalStructure" : { },
      "Name" : { },
      "PartyNumber" : { },
      "Relationships" : {
        "Account" : {
          "Related" : "http://example.com/aeiou",
          "Id" : "Id"
        }
      },
      "FullLegalName" : { },
      "PartyId" : { },
      "Phone" : { },
      "PartyType" : { }
    } ]
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       accountsAccountIdPartyGet, 
       apiVersion, 
       nameOf(accountsAccountIdPartyGet),
       "GET", 
       "/accounts/ACCOUNT_ID/party", 
       "Get Accounts Party by AccountId",
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
    "First" : "http://example.com/aeiou",
    "Self" : "http://example.com/aeiou"
  },
  "Data" : {
    "Party" : {
      "BeneficialOwnership" : true,
      "Address" : [ {
        "CountrySubDivision" : { },
        "StreetName" : { },
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : { },
        "TownName" : { },
        "Country" : { },
        "AddressType" : { },
        "PostCode" : { }
      }, {
        "CountrySubDivision" : { },
        "StreetName" : { },
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : { },
        "TownName" : { },
        "Country" : { },
        "AddressType" : { },
        "PostCode" : { }
      } ],
      "AccountRole" : { },
      "Mobile" : { },
      "EmailAddress" : { },
      "LegalStructure" : { },
      "Name" : { },
      "PartyNumber" : { },
      "Relationships" : {
        "Account" : {
          "Related" : "http://example.com/aeiou",
          "Id" : "Id"
        }
      },
      "FullLegalName" : { },
      "PartyId" : { },
      "Phone" : { },
      "PartyType" : { }
    }
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Parties") :: apiTagMockedData :: Nil
     )

     lazy val accountsAccountIdPartyGet : OBPEndpoint = {
       case "accounts" :: accountId:: "party" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
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
    "First" : "http://example.com/aeiou",
    "Self" : "http://example.com/aeiou"
  },
  "Data" : {
    "Party" : {
      "BeneficialOwnership" : true,
      "Address" : [ {
        "CountrySubDivision" : { },
        "StreetName" : { },
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : { },
        "TownName" : { },
        "Country" : { },
        "AddressType" : { },
        "PostCode" : { }
      }, {
        "CountrySubDivision" : { },
        "StreetName" : { },
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : { },
        "TownName" : { },
        "Country" : { },
        "AddressType" : { },
        "PostCode" : { }
      } ],
      "AccountRole" : { },
      "Mobile" : { },
      "EmailAddress" : { },
      "LegalStructure" : { },
      "Name" : { },
      "PartyNumber" : { },
      "Relationships" : {
        "Account" : {
          "Related" : "http://example.com/aeiou",
          "Id" : "Id"
        }
      },
      "FullLegalName" : { },
      "PartyId" : { },
      "Phone" : { },
      "PartyType" : { }
    }
  }
}"""), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       partyGet, 
       apiVersion, 
       nameOf(partyGet),
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
    "First" : "http://example.com/aeiou",
    "Self" : "http://example.com/aeiou"
  },
  "Data" : {
    "Party" : {
      "BeneficialOwnership" : true,
      "Address" : [ {
        "CountrySubDivision" : { },
        "StreetName" : { },
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : { },
        "TownName" : { },
        "Country" : { },
        "AddressType" : { },
        "PostCode" : { }
      }, {
        "CountrySubDivision" : { },
        "StreetName" : { },
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : { },
        "TownName" : { },
        "Country" : { },
        "AddressType" : { },
        "PostCode" : { }
      } ],
      "AccountRole" : { },
      "Mobile" : { },
      "EmailAddress" : { },
      "LegalStructure" : { },
      "Name" : { },
      "PartyNumber" : { },
      "Relationships" : {
        "Account" : {
          "Related" : "http://example.com/aeiou",
          "Id" : "Id"
        }
      },
      "FullLegalName" : { },
      "PartyId" : { },
      "Phone" : { },
      "PartyType" : { }
    }
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Parties") :: apiTagMockedData :: Nil
     )

     lazy val partyGet : OBPEndpoint = {
       case "party" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
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
    "First" : "http://example.com/aeiou",
    "Self" : "http://example.com/aeiou"
  },
  "Data" : {
    "Party" : {
      "BeneficialOwnership" : true,
      "Address" : [ {
        "CountrySubDivision" : { },
        "StreetName" : { },
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : { },
        "TownName" : { },
        "Country" : { },
        "AddressType" : { },
        "PostCode" : { }
      }, {
        "CountrySubDivision" : { },
        "StreetName" : { },
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : { },
        "TownName" : { },
        "Country" : { },
        "AddressType" : { },
        "PostCode" : { }
      } ],
      "AccountRole" : { },
      "Mobile" : { },
      "EmailAddress" : { },
      "LegalStructure" : { },
      "Name" : { },
      "PartyNumber" : { },
      "Relationships" : {
        "Account" : {
          "Related" : "http://example.com/aeiou",
          "Id" : "Id"
        }
      },
      "FullLegalName" : { },
      "PartyId" : { },
      "Phone" : { },
      "PartyType" : { }
    }
  }
}"""), callContext)
           }
         }
       }

}



