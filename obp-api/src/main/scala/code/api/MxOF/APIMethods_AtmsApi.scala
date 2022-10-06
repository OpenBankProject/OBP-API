package code.api.MxOF

import code.api.Constant
import code.api.MxOF.JSONFactory_MXOF_0_0_1.createGetAtmsResponse
import code.api.util.APIUtil._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.{APIUtil, ApiTag, CallContext, NewStyle}
import code.bankconnectors.Connector
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model._
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}
import dispatch.Future
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.http.Req
import net.liftweb.http.rest.RestHelper
import net.liftweb.json
import net.liftweb.json._

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer

object APIMethods_AtmsApi extends RestHelper {
  
  protected trait TestHead {
    /**
     * Test to see if the request is a GET and expecting JSON in the response.
     * The path and the Req instance are extracted.
     */
    def unapply(r: Req): Option[(List[String], Req)] =
      if (r.requestType.head_? && testResponse_?(r))
        Some(r.path.partPath -> r) else None

    def testResponse_?(r: Req): Boolean
  }
  
  lazy val JsonHead = new TestHead with JsonTest
  
  
    val apiVersion = ApiVersion.cnbv9
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
      |  "additionalProp1": "string",
      |  "additionalProp2": "string",
      |  "additionalProp3": "string"
      |}""".stripMargin)       
     resourceDocs += ResourceDoc(
       getMxAtms, 
       apiVersion, 
       nameOf(getMxAtms),
       "GET", 
       "/atms", 
       "Get ATMS",
       s"""${mockedDataText(false)}
            Gets a list of all ATM objects.
            """,
       EmptyBody,
       getMxAtmsResponseJson,
       List(UnknownError),
       ApiTag("ATM") :: apiTagMXOpenFinance :: Nil
     )
    
     lazy val getMxAtms : OBPEndpoint = {
       case "atms" :: Nil JsonGet _ => {
         cc =>
           for {
             (_, callContext) <- anonymousAccess(cc)
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
       "HEAD", 
       "/atms", 
       "Head ATMS",
       s"""${mockedDataText(false)}
            Gets header information on the current set of ATM data
            """,
       EmptyBody,
       EmptyBody,
       List(
         UnknownError
       ),
       ApiTag("ATM") :: apiTagMXOpenFinance :: Nil
     )

     lazy val headMxAtms : OBPEndpoint = {
       case "atms" :: Nil JsonHead _ => {
         cc =>
           for {
             (_, callContext) <- anonymousAccess(cc)
           } yield {
             ("", callContext)
           }
         }
       }

}



