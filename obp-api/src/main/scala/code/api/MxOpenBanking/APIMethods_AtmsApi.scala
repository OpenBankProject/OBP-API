package code.api.MxOpenBanking

import code.api.Constant
import code.api.MxOpenBanking.JSONFactory_MX_OPEN_FINANCE_0_0_1.createGetAtmsResponse
import code.api.util.APIUtil._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.{APIUtil, ApiTag, CallContext, NewStyle}
import code.bankconnectors.Connector
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model._
import dispatch.Future
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.http.rest.RestHelper
import net.liftweb.json
import net.liftweb.json._

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer

object APIMethods_AtmsApi extends RestHelper {
    val apiVersion =  OBP_Mx_OpenBanking_1_0_0.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      getMxAtms :: 
      headMxAtms ::
      Nil

     val getMxAtmsResponseJson = json.parse(
    """{
      |  "meta": {
      |    "LastUpdated": "2021-05-26T14:49:38.258Z",
      |    "TotalResults": 0,
      |    "Agreement": "To be confirmed",
      |    "License": "To be confirmed",
      |    "TermsOfUse": "To be confirmed"
      |  },
      |  "data": [
      |    {
      |      "Brand": [
      |        {
      |          "BrandName": "MÉXICO",
      |          "ATM": [
      |            {
      |              "Identification": "999994090",
      |              "SupportedLanguages": [
      |                  "es",
      |                  "en",
      |                  "fr",
      |                  "pt",
      |                  "io"
      |              ],
      |              "ATMServices": [
      |                  "ATBA"
      |              ],
      |              "Accessibility": [
      |                  "ATAC"
      |              ],
      |              "Access24HoursIndicator": true,
      |              "SupportedCurrencies": [
      |                  "MXN",
      |                  "USD",
      |                  "GBP"
      |              ],
      |              "MinimumPossibleAmount": "5",
      |              "Note": [
      |                "string"
      |              ],
      |              "OtherAccessibility": [
      |                {
      |                  "Code": "stri",
      |                  "Description": "string",
      |                  "Name": "string"
      |                }
      |              ],
      |              "OtherATMServices": [
      |                {
      |                  "Code": "stri",
      |                  "Description": "string",
      |                  "Name": "string"
      |                }
      |              ],
      |              "Branch": {
      |                "Identification": "N/A"
      |              },
      |              "Location": {
      |                "LocationCategory": [
      |                    "ATBE",
      |                    "ATBI",
      |                    "ATBL",
      |                    "ATOT",
      |                    "ATRO",
      |                    "ATRU"
      |                ],
      |                "OtherLocationCategory": [
      |                  {
      |                    "Code": "stri",
      |                    "Description": "string",
      |                    "Name": "TELEFONOS DE MEXICO SAB DE CV SAN JERONIMO"
      |                  }
      |                ],
      |                "Site": {
      |                  "Identification": "string",
      |                  "Name": "string"
      |                },
      |                "PostalAddress": {
      |                  "AddressLine": "SAN JERONIMO LIDICE",
      |                  "BuildingNumber": "9",
      |                  "StreetName": "PINOS",
      |                  "TownName": "MAGDALENA CONTRERAS",
      |                  "CountrySubDivision": [
      |                    "CD MEXICO"
      |                  ],
      |                  "Country": "MX",
      |                  "PostCode": "10100",
      |                  "GeoLocation": {
      |                    "GeographicCoordinates": {
      |                      "Latitude": "19.333474",
      |                      "Longitude": "-99.215063"
      |                    }
      |                  }
      |                }
      |              },
      |              "FeeSurcharges": {
      |                "CashWithdrawalNational": "20.00 MXN",
      |                "CashWithdrawalInternational": "20.00 MXN",
      |                "BalanceInquiry": "20.00MXN"
      |              }
      |            }
      |          ]
      |        }
      |      ]
      |    }
      |  ]
      |}""".stripMargin)       
     resourceDocs += ResourceDoc(
       getMxAtms, 
       apiVersion, 
       nameOf(getMxAtms),
       "GET", 
       "/atms", 
       "getAccountByAccountId",
       s"""${mockedDataText(false)}
            Get Account by AccountId
            """,
       emptyObjectJson,
       getMxAtmsResponseJson,
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Accounts") :: apiTagMXOpenFinance :: Nil
     )
    
     lazy val getMxAtms : OBPEndpoint = {
       case "atms" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(user), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             (banks, callContext) <- NewStyle.function.getBanks(callContext)
             (atms, callContext) <- NewStyle.function.getAllAtms(callContext)
           } yield {
             (createGetAtmsResponse(banks, atms), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       headMxAtms, 
       apiVersion, 
       nameOf(headMxAtms),
       "GET", 
       "/accounts", 
       "getAccounts",
       s"""${mockedDataText(false)}
            Get Accounts
            """,
       emptyObjectJson,
       emptyObjectJson,
       List(
         UserNotLoggedIn, 
         ConsentNotFound,
         ConsentNotBeforeIssue,
         ConsentExpiredIssue, 
         UnknownError
       ),
       ApiTag("Accounts") :: apiTagMXOpenFinance :: Nil
     )

     lazy val headMxAtms : OBPEndpoint = {
       case "accounts" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
           } yield {
             (json.parse("""{}"""), callContext)
           }
         }
       }

}



