package code.api.UKOpenBanking.v4_0_1

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import code.api.util.APIUtil.{EmptyBody, ResourceDoc}
import code.api.util.ApiTag
import code.api.util.CustomJsonFormats
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, UnknownError}
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.util.Helper.MdcLoggable
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}
import com.openbankproject.commons.util.JsonAliases
import org.json4s.{Formats, JObject}
import org.http4s._
import org.http4s.dsl.io._
import com.openbankproject.commons.ExecutionContext.Implicits.global

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

// AUTO-GENERATED from UK Open Banking read-write-api-specs v4.0.1 (Vrp).
// Spec-faithful scaffold: routes return synthesized example JSON from the
// OpenAPI schemas (the specs carry no examples). Deepen to real OBP
// connector logic per endpoint later, mirroring v3_1_0.
object Http4sUKOBv401Vrp extends MdcLoggable {
  type HttpF[A] = OptionT[IO, A]
  implicit val formats: Formats = CustomJsonFormats.formats
  val implementedInApiVersion: ScannedApiVersion = ApiVersion.ukOpenBankingV401
  val resourceDocs = ArrayBuffer[ResourceDoc]()
  private def parseBody(s: String): JObject = JsonAliases.parse(s).asInstanceOf[JObject]
  val ukV401Prefix = Root / ApiVersion.ukOpenBankingV401.urlPrefix / ApiVersion.ukOpenBankingV401.apiShortVersion

  private val EXREQ_domesticVrpConsentsPost: String = """{
  "Data": {
    "ReadRefundAccount": "Yes",
    "ControlParameters": {
      "ValidFromDateTime": "2020-01-01T00:00:00+00:00",
      "ValidToDateTime": "2020-01-01T00:00:00+00:00",
      "MaximumIndividualAmount": {
        "Amount": "string",
        "Currency": "string"
      },
      "PeriodicLimits": [
        {
          "PeriodType": "Day",
          "PeriodAlignment": "Consent",
          "Amount": "string",
          "Currency": "string"
        }
      ],
      "VRPType": [
        "string"
      ],
      "PSUAuthenticationMethods": [
        "string"
      ],
      "PSUInteractionTypes": [
        "InSession"
      ],
      "SupplementaryData": {}
    },
    "Initiation": {
      "DebtorAccount": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "SecondaryIdentification": "string",
        "Proxy": {
          "Identification": "string",
          "Code": "TELE",
          "Type": "string"
        }
      },
      "CreditorAccount": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "SecondaryIdentification": "string",
        "Proxy": {
          "Identification": "string",
          "Code": "TELE",
          "Type": "string"
        }
      },
      "CreditorPostalAddress": {
        "AddressType": "BIZZ",
        "Department": "string",
        "SubDepartment": "string",
        "StreetName": "string",
        "BuildingNumber": "string",
        "BuildingName": "string",
        "Floor": "string",
        "UnitNumber": "string",
        "Room": "string",
        "PostBox": "string",
        "TownLocationName": "string",
        "DistrictName": "string",
        "CareOf": "string",
        "PostCode": "string",
        "TownName": "string",
        "CountrySubDivision": "string",
        "Country": "string",
        "AddressLine": [
          "string"
        ]
      },
      "UltimateCreditor": {
        "Name": "string",
        "Identification": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
        "SchemeName": "string",
        "PostalAddress": {
          "AddressType": "BIZZ",
          "Department": "string",
          "SubDepartment": "string",
          "StreetName": "string",
          "BuildingNumber": "string",
          "BuildingName": "string",
          "Floor": "string",
          "UnitNumber": "string",
          "Room": "string",
          "PostBox": "string",
          "TownLocationName": "string",
          "DistrictName": "string",
          "CareOf": "string",
          "PostCode": "string",
          "TownName": "string",
          "CountrySubDivision": "string",
          "Country": "string",
          "AddressLine": [
            "string"
          ]
        }
      },
      "UltimateDebtor": {
        "Name": "string",
        "Identification": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
        "SchemeName": "string",
        "PostalAddress": {
          "AddressType": "BIZZ",
          "Department": "string",
          "SubDepartment": "string",
          "StreetName": "string",
          "BuildingNumber": "string",
          "BuildingName": "string",
          "Floor": "string",
          "UnitNumber": "string",
          "Room": "string",
          "PostBox": "string",
          "TownLocationName": "string",
          "DistrictName": "string",
          "CareOf": "string",
          "PostCode": "string",
          "TownName": "string",
          "CountrySubDivision": "string",
          "Country": "string",
          "AddressLine": [
            "string"
          ]
        }
      },
      "RemittanceInformation": {
        "Structured": [
          {
            "ReferredDocumentInformation": [
              {}
            ],
            "ReferredDocumentAmount": "string",
            "CreditorReferenceInformation": {
              "Code": {},
              "Issuer": "string",
              "Reference": "string"
            },
            "Invoicer": "string",
            "Invoicee": "string",
            "TaxRemittance": "string",
            "AdditionalRemittanceInformation": [
              "string"
            ]
          }
        ],
        "Unstructured": [
          "string"
        ]
      },
      "RegulatoryReporting": [
        {
          "DebitCreditReportingIndicator": "CRED",
          "Authority": {
            "Name": "string",
            "CountryCode": "string"
          },
          "Details": [
            {
              "Type": "string",
              "Date": {},
              "Country": {},
              "Amount": {},
              "Information": []
            }
          ]
        }
      ]
    }
  },
  "Risk": {
    "PaymentContextCode": "BillingGoodsAndServicesInAdvance",
    "MerchantCategoryCode": "string",
    "MerchantCustomerIdentification": "string",
    "ContractPresentIndicator": true,
    "BeneficiaryPrepopulatedIndicator": true,
    "PaymentPurposeCode": "BKDF",
    "CategoryPurposeCode": "BONU",
    "BeneficiaryAccountType": "Business",
    "DeliveryAddress": {
      "AddressType": "BIZZ",
      "Department": "string",
      "SubDepartment": "string",
      "StreetName": "string",
      "BuildingNumber": "string",
      "BuildingName": "string",
      "Floor": "string",
      "UnitNumber": "string",
      "Room": "string",
      "PostBox": "string",
      "TownLocationName": "string",
      "DistrictName": "string",
      "CareOf": "string",
      "PostCode": "string",
      "TownName": "string",
      "CountrySubDivision": "string",
      "Country": "string",
      "AddressLine": [
        "string"
      ]
    }
  }
}"""
  private val EX_domesticVrpConsentsPost: String = """{
  "Data": {
    "ReadRefundAccount": "Yes",
    "ConsentId": "string",
    "CreationDateTime": "2020-01-01T00:00:00+00:00",
    "Status": "AWAU",
    "StatusReason": [
      {
        "StatusReasonCode": "ERIN",
        "StatusReasonDescription": "string",
        "Path": "string"
      }
    ],
    "StatusUpdateDateTime": "2020-01-01T00:00:00+00:00",
    "ControlParameters": {
      "ValidFromDateTime": "2020-01-01T00:00:00+00:00",
      "ValidToDateTime": "2020-01-01T00:00:00+00:00",
      "MaximumIndividualAmount": {
        "Amount": "string",
        "Currency": "string"
      },
      "PeriodicLimits": [
        {
          "PeriodType": "Day",
          "PeriodAlignment": "Consent",
          "Amount": "string",
          "Currency": "string"
        }
      ],
      "VRPType": [
        "string"
      ],
      "PSUAuthenticationMethods": [
        "string"
      ],
      "PSUInteractionTypes": [
        "InSession"
      ],
      "SupplementaryData": {}
    },
    "Initiation": {
      "DebtorAccount": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "SecondaryIdentification": "string",
        "Proxy": {
          "Identification": "string",
          "Code": "TELE",
          "Type": "string"
        }
      },
      "CreditorAccount": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "SecondaryIdentification": "string",
        "Proxy": {
          "Identification": "string",
          "Code": "TELE",
          "Type": "string"
        }
      },
      "CreditorPostalAddress": {
        "AddressType": "BIZZ",
        "Department": "string",
        "SubDepartment": "string",
        "StreetName": "string",
        "BuildingNumber": "string",
        "BuildingName": "string",
        "Floor": "string",
        "UnitNumber": "string",
        "Room": "string",
        "PostBox": "string",
        "TownLocationName": "string",
        "DistrictName": "string",
        "CareOf": "string",
        "PostCode": "string",
        "TownName": "string",
        "CountrySubDivision": "string",
        "Country": "string",
        "AddressLine": [
          "string"
        ]
      },
      "UltimateCreditor": {
        "Name": "string",
        "Identification": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
        "SchemeName": "string",
        "PostalAddress": {
          "AddressType": "BIZZ",
          "Department": "string",
          "SubDepartment": "string",
          "StreetName": "string",
          "BuildingNumber": "string",
          "BuildingName": "string",
          "Floor": "string",
          "UnitNumber": "string",
          "Room": "string",
          "PostBox": "string",
          "TownLocationName": "string",
          "DistrictName": "string",
          "CareOf": "string",
          "PostCode": "string",
          "TownName": "string",
          "CountrySubDivision": "string",
          "Country": "string",
          "AddressLine": [
            "string"
          ]
        }
      },
      "UltimateDebtor": {
        "Name": "string",
        "Identification": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
        "SchemeName": "string",
        "PostalAddress": {
          "AddressType": "BIZZ",
          "Department": "string",
          "SubDepartment": "string",
          "StreetName": "string",
          "BuildingNumber": "string",
          "BuildingName": "string",
          "Floor": "string",
          "UnitNumber": "string",
          "Room": "string",
          "PostBox": "string",
          "TownLocationName": "string",
          "DistrictName": "string",
          "CareOf": "string",
          "PostCode": "string",
          "TownName": "string",
          "CountrySubDivision": "string",
          "Country": "string",
          "AddressLine": [
            "string"
          ]
        }
      },
      "RemittanceInformation": {
        "Structured": [
          {
            "ReferredDocumentInformation": [
              {}
            ],
            "ReferredDocumentAmount": "string",
            "CreditorReferenceInformation": {
              "Code": {},
              "Issuer": "string",
              "Reference": "string"
            },
            "Invoicer": "string",
            "Invoicee": "string",
            "TaxRemittance": "string",
            "AdditionalRemittanceInformation": [
              "string"
            ]
          }
        ],
        "Unstructured": [
          "string"
        ]
      },
      "RegulatoryReporting": [
        {
          "DebitCreditReportingIndicator": "CRED",
          "Authority": {
            "Name": "string",
            "CountryCode": "string"
          },
          "Details": [
            {
              "Type": "string",
              "Date": {},
              "Country": {},
              "Amount": {},
              "Information": []
            }
          ]
        }
      ]
    },
    "DebtorAccount": {
      "SchemeName": "string",
      "Identification": "string",
      "Name": "string",
      "SecondaryIdentification": "string",
      "Proxy": {
        "Identification": "string",
        "Code": "TELE",
        "Type": "string"
      }
    }
  },
  "Risk": {
    "PaymentContextCode": "BillingGoodsAndServicesInAdvance",
    "MerchantCategoryCode": "string",
    "MerchantCustomerIdentification": "string",
    "ContractPresentIndicator": true,
    "BeneficiaryPrepopulatedIndicator": true,
    "PaymentPurposeCode": "BKDF",
    "CategoryPurposeCode": "BONU",
    "BeneficiaryAccountType": "Business",
    "DeliveryAddress": {
      "AddressType": "BIZZ",
      "Department": "string",
      "SubDepartment": "string",
      "StreetName": "string",
      "BuildingNumber": "string",
      "BuildingName": "string",
      "Floor": "string",
      "UnitNumber": "string",
      "Room": "string",
      "PostBox": "string",
      "TownLocationName": "string",
      "DistrictName": "string",
      "CareOf": "string",
      "PostCode": "string",
      "TownName": "string",
      "CountrySubDivision": "string",
      "Country": "string",
      "AddressLine": [
        "string"
      ]
    }
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {}
}"""
  lazy val domesticVrpConsentsPost: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV401Prefix` / "pisp" / "domestic-vrp-consents" =>
      EndpointHelpers.executeFutureCreated(req)(Future.successful(parseBody(EX_domesticVrpConsentsPost)))
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(domesticVrpConsentsPost),
    "POST",
    "/pisp/domestic-vrp-consents",
    "Create a domestic VRP consent",
    """Enables a PISP to ask an ASPSP to create a new domestic-vrp-consent resource, by sending a copy of the consent to the ASPSP.""",
    parseBody(EXREQ_domesticVrpConsentsPost),
    parseBody(EX_domesticVrpConsentsPost),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Domestic VRP Consents") :: Nil,
    http4sPartialFunction = Some(domesticVrpConsentsPost)
  )

  private val EX_domesticVrpConsentsGet: String = """{
  "Data": {
    "ReadRefundAccount": "Yes",
    "ConsentId": "string",
    "CreationDateTime": "2020-01-01T00:00:00+00:00",
    "Status": "AWAU",
    "StatusReason": [
      {
        "StatusReasonCode": "ERIN",
        "StatusReasonDescription": "string",
        "Path": "string"
      }
    ],
    "StatusUpdateDateTime": "2020-01-01T00:00:00+00:00",
    "ControlParameters": {
      "ValidFromDateTime": "2020-01-01T00:00:00+00:00",
      "ValidToDateTime": "2020-01-01T00:00:00+00:00",
      "MaximumIndividualAmount": {
        "Amount": "string",
        "Currency": "string"
      },
      "PeriodicLimits": [
        {
          "PeriodType": "Day",
          "PeriodAlignment": "Consent",
          "Amount": "string",
          "Currency": "string"
        }
      ],
      "VRPType": [
        "string"
      ],
      "PSUAuthenticationMethods": [
        "string"
      ],
      "PSUInteractionTypes": [
        "InSession"
      ],
      "SupplementaryData": {}
    },
    "Initiation": {
      "DebtorAccount": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "SecondaryIdentification": "string",
        "Proxy": {
          "Identification": "string",
          "Code": "TELE",
          "Type": "string"
        }
      },
      "CreditorAccount": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "SecondaryIdentification": "string",
        "Proxy": {
          "Identification": "string",
          "Code": "TELE",
          "Type": "string"
        }
      },
      "CreditorPostalAddress": {
        "AddressType": "BIZZ",
        "Department": "string",
        "SubDepartment": "string",
        "StreetName": "string",
        "BuildingNumber": "string",
        "BuildingName": "string",
        "Floor": "string",
        "UnitNumber": "string",
        "Room": "string",
        "PostBox": "string",
        "TownLocationName": "string",
        "DistrictName": "string",
        "CareOf": "string",
        "PostCode": "string",
        "TownName": "string",
        "CountrySubDivision": "string",
        "Country": "string",
        "AddressLine": [
          "string"
        ]
      },
      "UltimateCreditor": {
        "Name": "string",
        "Identification": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
        "SchemeName": "string",
        "PostalAddress": {
          "AddressType": "BIZZ",
          "Department": "string",
          "SubDepartment": "string",
          "StreetName": "string",
          "BuildingNumber": "string",
          "BuildingName": "string",
          "Floor": "string",
          "UnitNumber": "string",
          "Room": "string",
          "PostBox": "string",
          "TownLocationName": "string",
          "DistrictName": "string",
          "CareOf": "string",
          "PostCode": "string",
          "TownName": "string",
          "CountrySubDivision": "string",
          "Country": "string",
          "AddressLine": [
            "string"
          ]
        }
      },
      "UltimateDebtor": {
        "Name": "string",
        "Identification": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
        "SchemeName": "string",
        "PostalAddress": {
          "AddressType": "BIZZ",
          "Department": "string",
          "SubDepartment": "string",
          "StreetName": "string",
          "BuildingNumber": "string",
          "BuildingName": "string",
          "Floor": "string",
          "UnitNumber": "string",
          "Room": "string",
          "PostBox": "string",
          "TownLocationName": "string",
          "DistrictName": "string",
          "CareOf": "string",
          "PostCode": "string",
          "TownName": "string",
          "CountrySubDivision": "string",
          "Country": "string",
          "AddressLine": [
            "string"
          ]
        }
      },
      "RemittanceInformation": {
        "Structured": [
          {
            "ReferredDocumentInformation": [
              {}
            ],
            "ReferredDocumentAmount": "string",
            "CreditorReferenceInformation": {
              "Code": {},
              "Issuer": "string",
              "Reference": "string"
            },
            "Invoicer": "string",
            "Invoicee": "string",
            "TaxRemittance": "string",
            "AdditionalRemittanceInformation": [
              "string"
            ]
          }
        ],
        "Unstructured": [
          "string"
        ]
      },
      "RegulatoryReporting": [
        {
          "DebitCreditReportingIndicator": "CRED",
          "Authority": {
            "Name": "string",
            "CountryCode": "string"
          },
          "Details": [
            {
              "Type": "string",
              "Date": {},
              "Country": {},
              "Amount": {},
              "Information": []
            }
          ]
        }
      ]
    },
    "DebtorAccount": {
      "SchemeName": "string",
      "Identification": "string",
      "Name": "string",
      "SecondaryIdentification": "string",
      "Proxy": {
        "Identification": "string",
        "Code": "TELE",
        "Type": "string"
      }
    }
  },
  "Risk": {
    "PaymentContextCode": "BillingGoodsAndServicesInAdvance",
    "MerchantCategoryCode": "string",
    "MerchantCustomerIdentification": "string",
    "ContractPresentIndicator": true,
    "BeneficiaryPrepopulatedIndicator": true,
    "PaymentPurposeCode": "BKDF",
    "CategoryPurposeCode": "BONU",
    "BeneficiaryAccountType": "Business",
    "DeliveryAddress": {
      "AddressType": "BIZZ",
      "Department": "string",
      "SubDepartment": "string",
      "StreetName": "string",
      "BuildingNumber": "string",
      "BuildingName": "string",
      "Floor": "string",
      "UnitNumber": "string",
      "Room": "string",
      "PostBox": "string",
      "TownLocationName": "string",
      "DistrictName": "string",
      "CareOf": "string",
      "PostCode": "string",
      "TownName": "string",
      "CountrySubDivision": "string",
      "Country": "string",
      "AddressLine": [
        "string"
      ]
    }
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {}
}"""
  lazy val domesticVrpConsentsGet: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "pisp" / "domestic-vrp-consents" / consentId =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_domesticVrpConsentsGet)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(domesticVrpConsentsGet),
    "GET",
    "/pisp/domestic-vrp-consents/CONSENT_ID",
    "Get a Domestic VRP Consent",
    """Enables a PISP to retrieve the status of a Domestic VRP Consent.""",
    EmptyBody,
    parseBody(EX_domesticVrpConsentsGet),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Domestic VRP Consents") :: Nil,
    http4sPartialFunction = Some(domesticVrpConsentsGet)
  )

  private val EXREQ_domesticVrpConsentsPut: String = """{
  "Data": {
    "ReadRefundAccount": "Yes",
    "ControlParameters": {
      "ValidFromDateTime": "2020-01-01T00:00:00+00:00",
      "ValidToDateTime": "2020-01-01T00:00:00+00:00",
      "MaximumIndividualAmount": {
        "Amount": "string",
        "Currency": "string"
      },
      "PeriodicLimits": [
        {
          "PeriodType": "Day",
          "PeriodAlignment": "Consent",
          "Amount": "string",
          "Currency": "string"
        }
      ],
      "VRPType": [
        "string"
      ],
      "PSUAuthenticationMethods": [
        "string"
      ],
      "PSUInteractionTypes": [
        "InSession"
      ],
      "SupplementaryData": {}
    },
    "Initiation": {
      "DebtorAccount": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "SecondaryIdentification": "string",
        "Proxy": {
          "Identification": "string",
          "Code": "TELE",
          "Type": "string"
        }
      },
      "CreditorAccount": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "SecondaryIdentification": "string",
        "Proxy": {
          "Identification": "string",
          "Code": "TELE",
          "Type": "string"
        }
      },
      "CreditorPostalAddress": {
        "AddressType": "BIZZ",
        "Department": "string",
        "SubDepartment": "string",
        "StreetName": "string",
        "BuildingNumber": "string",
        "BuildingName": "string",
        "Floor": "string",
        "UnitNumber": "string",
        "Room": "string",
        "PostBox": "string",
        "TownLocationName": "string",
        "DistrictName": "string",
        "CareOf": "string",
        "PostCode": "string",
        "TownName": "string",
        "CountrySubDivision": "string",
        "Country": "string",
        "AddressLine": [
          "string"
        ]
      },
      "UltimateCreditor": {
        "Name": "string",
        "Identification": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
        "SchemeName": "string",
        "PostalAddress": {
          "AddressType": "BIZZ",
          "Department": "string",
          "SubDepartment": "string",
          "StreetName": "string",
          "BuildingNumber": "string",
          "BuildingName": "string",
          "Floor": "string",
          "UnitNumber": "string",
          "Room": "string",
          "PostBox": "string",
          "TownLocationName": "string",
          "DistrictName": "string",
          "CareOf": "string",
          "PostCode": "string",
          "TownName": "string",
          "CountrySubDivision": "string",
          "Country": "string",
          "AddressLine": [
            "string"
          ]
        }
      },
      "UltimateDebtor": {
        "Name": "string",
        "Identification": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
        "SchemeName": "string",
        "PostalAddress": {
          "AddressType": "BIZZ",
          "Department": "string",
          "SubDepartment": "string",
          "StreetName": "string",
          "BuildingNumber": "string",
          "BuildingName": "string",
          "Floor": "string",
          "UnitNumber": "string",
          "Room": "string",
          "PostBox": "string",
          "TownLocationName": "string",
          "DistrictName": "string",
          "CareOf": "string",
          "PostCode": "string",
          "TownName": "string",
          "CountrySubDivision": "string",
          "Country": "string",
          "AddressLine": [
            "string"
          ]
        }
      },
      "RemittanceInformation": {
        "Structured": [
          {
            "ReferredDocumentInformation": [
              {}
            ],
            "ReferredDocumentAmount": "string",
            "CreditorReferenceInformation": {
              "Code": {},
              "Issuer": "string",
              "Reference": "string"
            },
            "Invoicer": "string",
            "Invoicee": "string",
            "TaxRemittance": "string",
            "AdditionalRemittanceInformation": [
              "string"
            ]
          }
        ],
        "Unstructured": [
          "string"
        ]
      },
      "RegulatoryReporting": [
        {
          "DebitCreditReportingIndicator": "CRED",
          "Authority": {
            "Name": "string",
            "CountryCode": "string"
          },
          "Details": [
            {
              "Type": "string",
              "Date": {},
              "Country": {},
              "Amount": {},
              "Information": []
            }
          ]
        }
      ]
    }
  },
  "Risk": {
    "PaymentContextCode": "BillingGoodsAndServicesInAdvance",
    "MerchantCategoryCode": "string",
    "MerchantCustomerIdentification": "string",
    "ContractPresentIndicator": true,
    "BeneficiaryPrepopulatedIndicator": true,
    "PaymentPurposeCode": "BKDF",
    "CategoryPurposeCode": "BONU",
    "BeneficiaryAccountType": "Business",
    "DeliveryAddress": {
      "AddressType": "BIZZ",
      "Department": "string",
      "SubDepartment": "string",
      "StreetName": "string",
      "BuildingNumber": "string",
      "BuildingName": "string",
      "Floor": "string",
      "UnitNumber": "string",
      "Room": "string",
      "PostBox": "string",
      "TownLocationName": "string",
      "DistrictName": "string",
      "CareOf": "string",
      "PostCode": "string",
      "TownName": "string",
      "CountrySubDivision": "string",
      "Country": "string",
      "AddressLine": [
        "string"
      ]
    }
  }
}"""
  private val EX_domesticVrpConsentsPut: String = """{
  "Data": {
    "ReadRefundAccount": "Yes",
    "ConsentId": "string",
    "CreationDateTime": "2020-01-01T00:00:00+00:00",
    "Status": "AWAU",
    "StatusReason": [
      {
        "StatusReasonCode": "ERIN",
        "StatusReasonDescription": "string",
        "Path": "string"
      }
    ],
    "StatusUpdateDateTime": "2020-01-01T00:00:00+00:00",
    "ControlParameters": {
      "ValidFromDateTime": "2020-01-01T00:00:00+00:00",
      "ValidToDateTime": "2020-01-01T00:00:00+00:00",
      "MaximumIndividualAmount": {
        "Amount": "string",
        "Currency": "string"
      },
      "PeriodicLimits": [
        {
          "PeriodType": "Day",
          "PeriodAlignment": "Consent",
          "Amount": "string",
          "Currency": "string"
        }
      ],
      "VRPType": [
        "string"
      ],
      "PSUAuthenticationMethods": [
        "string"
      ],
      "PSUInteractionTypes": [
        "InSession"
      ],
      "SupplementaryData": {}
    },
    "Initiation": {
      "DebtorAccount": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "SecondaryIdentification": "string",
        "Proxy": {
          "Identification": "string",
          "Code": "TELE",
          "Type": "string"
        }
      },
      "CreditorAccount": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "SecondaryIdentification": "string",
        "Proxy": {
          "Identification": "string",
          "Code": "TELE",
          "Type": "string"
        }
      },
      "CreditorPostalAddress": {
        "AddressType": "BIZZ",
        "Department": "string",
        "SubDepartment": "string",
        "StreetName": "string",
        "BuildingNumber": "string",
        "BuildingName": "string",
        "Floor": "string",
        "UnitNumber": "string",
        "Room": "string",
        "PostBox": "string",
        "TownLocationName": "string",
        "DistrictName": "string",
        "CareOf": "string",
        "PostCode": "string",
        "TownName": "string",
        "CountrySubDivision": "string",
        "Country": "string",
        "AddressLine": [
          "string"
        ]
      },
      "UltimateCreditor": {
        "Name": "string",
        "Identification": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
        "SchemeName": "string",
        "PostalAddress": {
          "AddressType": "BIZZ",
          "Department": "string",
          "SubDepartment": "string",
          "StreetName": "string",
          "BuildingNumber": "string",
          "BuildingName": "string",
          "Floor": "string",
          "UnitNumber": "string",
          "Room": "string",
          "PostBox": "string",
          "TownLocationName": "string",
          "DistrictName": "string",
          "CareOf": "string",
          "PostCode": "string",
          "TownName": "string",
          "CountrySubDivision": "string",
          "Country": "string",
          "AddressLine": [
            "string"
          ]
        }
      },
      "UltimateDebtor": {
        "Name": "string",
        "Identification": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
        "SchemeName": "string",
        "PostalAddress": {
          "AddressType": "BIZZ",
          "Department": "string",
          "SubDepartment": "string",
          "StreetName": "string",
          "BuildingNumber": "string",
          "BuildingName": "string",
          "Floor": "string",
          "UnitNumber": "string",
          "Room": "string",
          "PostBox": "string",
          "TownLocationName": "string",
          "DistrictName": "string",
          "CareOf": "string",
          "PostCode": "string",
          "TownName": "string",
          "CountrySubDivision": "string",
          "Country": "string",
          "AddressLine": [
            "string"
          ]
        }
      },
      "RemittanceInformation": {
        "Structured": [
          {
            "ReferredDocumentInformation": [
              {}
            ],
            "ReferredDocumentAmount": "string",
            "CreditorReferenceInformation": {
              "Code": {},
              "Issuer": "string",
              "Reference": "string"
            },
            "Invoicer": "string",
            "Invoicee": "string",
            "TaxRemittance": "string",
            "AdditionalRemittanceInformation": [
              "string"
            ]
          }
        ],
        "Unstructured": [
          "string"
        ]
      },
      "RegulatoryReporting": [
        {
          "DebitCreditReportingIndicator": "CRED",
          "Authority": {
            "Name": "string",
            "CountryCode": "string"
          },
          "Details": [
            {
              "Type": "string",
              "Date": {},
              "Country": {},
              "Amount": {},
              "Information": []
            }
          ]
        }
      ]
    },
    "DebtorAccount": {
      "SchemeName": "string",
      "Identification": "string",
      "Name": "string",
      "SecondaryIdentification": "string",
      "Proxy": {
        "Identification": "string",
        "Code": "TELE",
        "Type": "string"
      }
    }
  },
  "Risk": {
    "PaymentContextCode": "BillingGoodsAndServicesInAdvance",
    "MerchantCategoryCode": "string",
    "MerchantCustomerIdentification": "string",
    "ContractPresentIndicator": true,
    "BeneficiaryPrepopulatedIndicator": true,
    "PaymentPurposeCode": "BKDF",
    "CategoryPurposeCode": "BONU",
    "BeneficiaryAccountType": "Business",
    "DeliveryAddress": {
      "AddressType": "BIZZ",
      "Department": "string",
      "SubDepartment": "string",
      "StreetName": "string",
      "BuildingNumber": "string",
      "BuildingName": "string",
      "Floor": "string",
      "UnitNumber": "string",
      "Room": "string",
      "PostBox": "string",
      "TownLocationName": "string",
      "DistrictName": "string",
      "CareOf": "string",
      "PostCode": "string",
      "TownName": "string",
      "CountrySubDivision": "string",
      "Country": "string",
      "AddressLine": [
        "string"
      ]
    }
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {}
}"""
  lazy val domesticVrpConsentsPut: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ PUT -> `ukV401Prefix` / "pisp" / "domestic-vrp-consents" / consentId =>
      EndpointHelpers.executeFutureCreated(req)(Future.successful(parseBody(EX_domesticVrpConsentsPut)))
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(domesticVrpConsentsPut),
    "PUT",
    "/pisp/domestic-vrp-consents/CONSENT_ID",
    "Replace an existing domestic VRP consent",
    """Enables a PISP to replace an existing Domestic VRP Consent resource.  

This endpoint **must** only be used for the migration of Domestic VRP Consent resource data across API Standard versions where the ASPSP supports this PUT function.""",
    parseBody(EXREQ_domesticVrpConsentsPut),
    parseBody(EX_domesticVrpConsentsPut),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Domestic VRP Consents") :: Nil,
    http4sPartialFunction = Some(domesticVrpConsentsPut)
  )

  private val EX_domesticVrpConsentsDelete: String = """{}"""
  lazy val domesticVrpConsentsDelete: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ DELETE -> `ukV401Prefix` / "pisp" / "domestic-vrp-consents" / consentId =>
      EndpointHelpers.executeDelete(req) { cc => Future.successful(()) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(domesticVrpConsentsDelete),
    "DELETE",
    "/pisp/domestic-vrp-consents/CONSENT_ID",
    "Delete a Domestic VRP Consent",
    """Enables a PISP to ask an ASPSP to delete a previously consented Domestic VRP Consent resource.""",
    EmptyBody,
    parseBody(EX_domesticVrpConsentsDelete),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Domestic VRP Consents") :: Nil,
    http4sPartialFunction = Some(domesticVrpConsentsDelete)
  )

  private val EX_domesticVrpConsentsPatch: String = """{
  "Data": {
    "ReadRefundAccount": "Yes",
    "ConsentId": "string",
    "CreationDateTime": "2020-01-01T00:00:00+00:00",
    "Status": "AWAU",
    "StatusReason": [
      {
        "StatusReasonCode": "ERIN",
        "StatusReasonDescription": "string",
        "Path": "string"
      }
    ],
    "StatusUpdateDateTime": "2020-01-01T00:00:00+00:00",
    "ControlParameters": {
      "ValidFromDateTime": "2020-01-01T00:00:00+00:00",
      "ValidToDateTime": "2020-01-01T00:00:00+00:00",
      "MaximumIndividualAmount": {
        "Amount": "string",
        "Currency": "string"
      },
      "PeriodicLimits": [
        {
          "PeriodType": "Day",
          "PeriodAlignment": "Consent",
          "Amount": "string",
          "Currency": "string"
        }
      ],
      "VRPType": [
        "string"
      ],
      "PSUAuthenticationMethods": [
        "string"
      ],
      "PSUInteractionTypes": [
        "InSession"
      ],
      "SupplementaryData": {}
    },
    "Initiation": {
      "DebtorAccount": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "SecondaryIdentification": "string",
        "Proxy": {
          "Identification": "string",
          "Code": "TELE",
          "Type": "string"
        }
      },
      "CreditorAccount": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "SecondaryIdentification": "string",
        "Proxy": {
          "Identification": "string",
          "Code": "TELE",
          "Type": "string"
        }
      },
      "CreditorPostalAddress": {
        "AddressType": "BIZZ",
        "Department": "string",
        "SubDepartment": "string",
        "StreetName": "string",
        "BuildingNumber": "string",
        "BuildingName": "string",
        "Floor": "string",
        "UnitNumber": "string",
        "Room": "string",
        "PostBox": "string",
        "TownLocationName": "string",
        "DistrictName": "string",
        "CareOf": "string",
        "PostCode": "string",
        "TownName": "string",
        "CountrySubDivision": "string",
        "Country": "string",
        "AddressLine": [
          "string"
        ]
      },
      "UltimateCreditor": {
        "Name": "string",
        "Identification": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
        "SchemeName": "string",
        "PostalAddress": {
          "AddressType": "BIZZ",
          "Department": "string",
          "SubDepartment": "string",
          "StreetName": "string",
          "BuildingNumber": "string",
          "BuildingName": "string",
          "Floor": "string",
          "UnitNumber": "string",
          "Room": "string",
          "PostBox": "string",
          "TownLocationName": "string",
          "DistrictName": "string",
          "CareOf": "string",
          "PostCode": "string",
          "TownName": "string",
          "CountrySubDivision": "string",
          "Country": "string",
          "AddressLine": [
            "string"
          ]
        }
      },
      "UltimateDebtor": {
        "Name": "string",
        "Identification": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
        "SchemeName": "string",
        "PostalAddress": {
          "AddressType": "BIZZ",
          "Department": "string",
          "SubDepartment": "string",
          "StreetName": "string",
          "BuildingNumber": "string",
          "BuildingName": "string",
          "Floor": "string",
          "UnitNumber": "string",
          "Room": "string",
          "PostBox": "string",
          "TownLocationName": "string",
          "DistrictName": "string",
          "CareOf": "string",
          "PostCode": "string",
          "TownName": "string",
          "CountrySubDivision": "string",
          "Country": "string",
          "AddressLine": [
            "string"
          ]
        }
      },
      "RemittanceInformation": {
        "Structured": [
          {
            "ReferredDocumentInformation": [
              {}
            ],
            "ReferredDocumentAmount": "string",
            "CreditorReferenceInformation": {
              "Code": {},
              "Issuer": "string",
              "Reference": "string"
            },
            "Invoicer": "string",
            "Invoicee": "string",
            "TaxRemittance": "string",
            "AdditionalRemittanceInformation": [
              "string"
            ]
          }
        ],
        "Unstructured": [
          "string"
        ]
      },
      "RegulatoryReporting": [
        {
          "DebitCreditReportingIndicator": "CRED",
          "Authority": {
            "Name": "string",
            "CountryCode": "string"
          },
          "Details": [
            {
              "Type": "string",
              "Date": {},
              "Country": {},
              "Amount": {},
              "Information": []
            }
          ]
        }
      ]
    },
    "DebtorAccount": {
      "SchemeName": "string",
      "Identification": "string",
      "Name": "string",
      "SecondaryIdentification": "string",
      "Proxy": {
        "Identification": "string",
        "Code": "TELE",
        "Type": "string"
      }
    }
  },
  "Risk": {
    "PaymentContextCode": "BillingGoodsAndServicesInAdvance",
    "MerchantCategoryCode": "string",
    "MerchantCustomerIdentification": "string",
    "ContractPresentIndicator": true,
    "BeneficiaryPrepopulatedIndicator": true,
    "PaymentPurposeCode": "BKDF",
    "CategoryPurposeCode": "BONU",
    "BeneficiaryAccountType": "Business",
    "DeliveryAddress": {
      "AddressType": "BIZZ",
      "Department": "string",
      "SubDepartment": "string",
      "StreetName": "string",
      "BuildingNumber": "string",
      "BuildingName": "string",
      "Floor": "string",
      "UnitNumber": "string",
      "Room": "string",
      "PostBox": "string",
      "TownLocationName": "string",
      "DistrictName": "string",
      "CareOf": "string",
      "PostCode": "string",
      "TownName": "string",
      "CountrySubDivision": "string",
      "Country": "string",
      "AddressLine": [
        "string"
      ]
    }
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {}
}"""
  lazy val domesticVrpConsentsPatch: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ PATCH -> `ukV401Prefix` / "pisp" / "domestic-vrp-consents" / consentId =>
      EndpointHelpers.executeFutureCreated(req)(Future.successful(parseBody(EX_domesticVrpConsentsPatch)))
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(domesticVrpConsentsPatch),
    "PATCH",
    "/pisp/domestic-vrp-consents/CONSENT_ID",
    "Update an existing domestic VRP consent",
    """Enables a PISP to update an existing Domestic VRP Consent resource by submitting a JSON Patch payload.

This endpoint **must** only be used for the migration of Domestic VRP Consent resource data across API Standard versions where the ASPSP supports this PATCH function.""",
    EmptyBody,
    parseBody(EX_domesticVrpConsentsPatch),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Domestic VRP Consents") :: Nil,
    http4sPartialFunction = Some(domesticVrpConsentsPatch)
  )

  private val EXREQ_domesticVrpConsentsFundsConfirmation: String = """{
  "Data": {
    "ConsentId": "string",
    "Reference": "string",
    "InstructedAmount": {
      "Amount": "string",
      "Currency": "string"
    }
  }
}"""
  private val EX_domesticVrpConsentsFundsConfirmation: String = """{
  "Data": {
    "FundsConfirmationId": "string",
    "ConsentId": "string",
    "CreationDateTime": "2020-01-01T00:00:00+00:00",
    "Reference": "string",
    "FundsAvailableResult": {
      "FundsAvailableDateTime": "2020-01-01T00:00:00+00:00",
      "FundsAvailable": "Available"
    },
    "InstructedAmount": {
      "Amount": "string",
      "Currency": "string"
    }
  }
}"""
  lazy val domesticVrpConsentsFundsConfirmation: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV401Prefix` / "pisp" / "domestic-vrp-consents" / consentId / "funds-confirmation" =>
      EndpointHelpers.executeFutureCreated(req)(Future.successful(parseBody(EX_domesticVrpConsentsFundsConfirmation)))
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(domesticVrpConsentsFundsConfirmation),
    "POST",
    "/pisp/domestic-vrp-consents/CONSENT_ID/funds-confirmation",
    "Confirm Funds Availability for a Domestic VRP",
    """Enables a PISP to check whether a PSU has sufficient available funds for a Domestic VRP Payment.""",
    parseBody(EXREQ_domesticVrpConsentsFundsConfirmation),
    parseBody(EX_domesticVrpConsentsFundsConfirmation),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Domestic VRP Consents") :: Nil,
    http4sPartialFunction = Some(domesticVrpConsentsFundsConfirmation)
  )

  private val EXREQ_domesticVrpPost: String = """{
  "Data": {
    "ConsentId": "string",
    "PSUAuthenticationMethod": {},
    "PSUInteractionType": {},
    "VRPType": "string",
    "Initiation": {
      "DebtorAccount": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "SecondaryIdentification": "string",
        "Proxy": {
          "Identification": "string",
          "Code": "TELE",
          "Type": "string"
        }
      },
      "CreditorAccount": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "SecondaryIdentification": "string",
        "Proxy": {
          "Identification": "string",
          "Code": "TELE",
          "Type": "string"
        }
      },
      "CreditorPostalAddress": {
        "AddressType": "BIZZ",
        "Department": "string",
        "SubDepartment": "string",
        "StreetName": "string",
        "BuildingNumber": "string",
        "BuildingName": "string",
        "Floor": "string",
        "UnitNumber": "string",
        "Room": "string",
        "PostBox": "string",
        "TownLocationName": "string",
        "DistrictName": "string",
        "CareOf": "string",
        "PostCode": "string",
        "TownName": "string",
        "CountrySubDivision": "string",
        "Country": "string",
        "AddressLine": [
          "string"
        ]
      },
      "UltimateCreditor": {
        "Name": "string",
        "Identification": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
        "SchemeName": "string",
        "PostalAddress": {
          "AddressType": "BIZZ",
          "Department": "string",
          "SubDepartment": "string",
          "StreetName": "string",
          "BuildingNumber": "string",
          "BuildingName": "string",
          "Floor": "string",
          "UnitNumber": "string",
          "Room": "string",
          "PostBox": "string",
          "TownLocationName": "string",
          "DistrictName": "string",
          "CareOf": "string",
          "PostCode": "string",
          "TownName": "string",
          "CountrySubDivision": "string",
          "Country": "string",
          "AddressLine": [
            "string"
          ]
        }
      },
      "UltimateDebtor": {
        "Name": "string",
        "Identification": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
        "SchemeName": "string",
        "PostalAddress": {
          "AddressType": "BIZZ",
          "Department": "string",
          "SubDepartment": "string",
          "StreetName": "string",
          "BuildingNumber": "string",
          "BuildingName": "string",
          "Floor": "string",
          "UnitNumber": "string",
          "Room": "string",
          "PostBox": "string",
          "TownLocationName": "string",
          "DistrictName": "string",
          "CareOf": "string",
          "PostCode": "string",
          "TownName": "string",
          "CountrySubDivision": "string",
          "Country": "string",
          "AddressLine": [
            "string"
          ]
        }
      },
      "RemittanceInformation": {
        "Structured": [
          {
            "ReferredDocumentInformation": [
              {}
            ],
            "ReferredDocumentAmount": "string",
            "CreditorReferenceInformation": {
              "Code": {},
              "Issuer": "string",
              "Reference": "string"
            },
            "Invoicer": "string",
            "Invoicee": "string",
            "TaxRemittance": "string",
            "AdditionalRemittanceInformation": [
              "string"
            ]
          }
        ],
        "Unstructured": [
          "string"
        ]
      },
      "RegulatoryReporting": [
        {
          "DebitCreditReportingIndicator": "CRED",
          "Authority": {
            "Name": "string",
            "CountryCode": "string"
          },
          "Details": [
            {
              "Type": "string",
              "Date": {},
              "Country": {},
              "Amount": {},
              "Information": []
            }
          ]
        }
      ]
    },
    "Instruction": {
      "InstructionIdentification": "string",
      "EndToEndIdentification": "string",
      "RemittanceInformation": {
        "Structured": [
          {
            "ReferredDocumentInformation": [
              {}
            ],
            "ReferredDocumentAmount": "string",
            "CreditorReferenceInformation": {
              "Code": {},
              "Issuer": "string",
              "Reference": "string"
            },
            "Invoicer": "string",
            "Invoicee": "string",
            "TaxRemittance": "string",
            "AdditionalRemittanceInformation": [
              "string"
            ]
          }
        ],
        "Unstructured": [
          "string"
        ]
      },
      "LocalInstrument": "string",
      "InstructedAmount": {
        "Amount": "string",
        "Currency": "string"
      },
      "CreditorPostalAddress": {
        "AddressType": "BIZZ",
        "Department": "string",
        "SubDepartment": "string",
        "StreetName": "string",
        "BuildingNumber": "string",
        "BuildingName": "string",
        "Floor": "string",
        "UnitNumber": "string",
        "Room": "string",
        "PostBox": "string",
        "TownLocationName": "string",
        "DistrictName": "string",
        "CareOf": "string",
        "PostCode": "string",
        "TownName": "string",
        "CountrySubDivision": "string",
        "Country": "string",
        "AddressLine": [
          "string"
        ]
      },
      "CreditorAccount": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "SecondaryIdentification": "string",
        "Proxy": {
          "Identification": "string",
          "Code": "TELE",
          "Type": "string"
        }
      },
      "UltimateCreditor": {
        "Name": "string",
        "Identification": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
        "SchemeName": "string",
        "PostalAddress": {
          "AddressType": "BIZZ",
          "Department": "string",
          "SubDepartment": "string",
          "StreetName": "string",
          "BuildingNumber": "string",
          "BuildingName": "string",
          "Floor": "string",
          "UnitNumber": "string",
          "Room": "string",
          "PostBox": "string",
          "TownLocationName": "string",
          "DistrictName": "string",
          "CareOf": "string",
          "PostCode": "string",
          "TownName": "string",
          "CountrySubDivision": "string",
          "Country": "string",
          "AddressLine": [
            "string"
          ]
        }
      },
      "SupplementaryData": {}
    }
  },
  "Risk": {
    "PaymentContextCode": "BillingGoodsAndServicesInAdvance",
    "MerchantCategoryCode": "string",
    "MerchantCustomerIdentification": "string",
    "ContractPresentIndicator": true,
    "BeneficiaryPrepopulatedIndicator": true,
    "PaymentPurposeCode": "BKDF",
    "CategoryPurposeCode": "BONU",
    "BeneficiaryAccountType": "Business",
    "DeliveryAddress": {
      "AddressType": "BIZZ",
      "Department": "string",
      "SubDepartment": "string",
      "StreetName": "string",
      "BuildingNumber": "string",
      "BuildingName": "string",
      "Floor": "string",
      "UnitNumber": "string",
      "Room": "string",
      "PostBox": "string",
      "TownLocationName": "string",
      "DistrictName": "string",
      "CareOf": "string",
      "PostCode": "string",
      "TownName": "string",
      "CountrySubDivision": "string",
      "Country": "string",
      "AddressLine": [
        "string"
      ]
    }
  }
}"""
  private val EX_domesticVrpPost: String = """{
  "Data": {
    "DomesticVRPId": "string",
    "ConsentId": "string",
    "CreationDateTime": "2020-01-01T00:00:00+00:00",
    "Status": "RCVD",
    "StatusReason": [
      {
        "StatusReasonCode": "ERIN",
        "StatusReasonDescription": "string",
        "Path": "string"
      }
    ],
    "StatusUpdateDateTime": "2020-01-01T00:00:00+00:00",
    "ExpectedExecutionDateTime": "2020-01-01T00:00:00+00:00",
    "ExpectedSettlementDateTime": "2020-01-01T00:00:00+00:00",
    "Refund": {
      "Account": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "SecondaryIdentification": "string"
      }
    },
    "Charges": [
      {
        "ChargeBearer": "BorneByCreditor",
        "Type": "UK.OBIE.CHAPSOut",
        "Amount": {
          "Amount": "string",
          "Currency": "string"
        }
      }
    ],
    "Initiation": {
      "DebtorAccount": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "SecondaryIdentification": "string",
        "Proxy": {
          "Identification": "string",
          "Code": "TELE",
          "Type": "string"
        }
      },
      "CreditorAccount": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "SecondaryIdentification": "string",
        "Proxy": {
          "Identification": "string",
          "Code": "TELE",
          "Type": "string"
        }
      },
      "CreditorPostalAddress": {
        "AddressType": "BIZZ",
        "Department": "string",
        "SubDepartment": "string",
        "StreetName": "string",
        "BuildingNumber": "string",
        "BuildingName": "string",
        "Floor": "string",
        "UnitNumber": "string",
        "Room": "string",
        "PostBox": "string",
        "TownLocationName": "string",
        "DistrictName": "string",
        "CareOf": "string",
        "PostCode": "string",
        "TownName": "string",
        "CountrySubDivision": "string",
        "Country": "string",
        "AddressLine": [
          "string"
        ]
      },
      "UltimateCreditor": {
        "Name": "string",
        "Identification": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
        "SchemeName": "string",
        "PostalAddress": {
          "AddressType": "BIZZ",
          "Department": "string",
          "SubDepartment": "string",
          "StreetName": "string",
          "BuildingNumber": "string",
          "BuildingName": "string",
          "Floor": "string",
          "UnitNumber": "string",
          "Room": "string",
          "PostBox": "string",
          "TownLocationName": "string",
          "DistrictName": "string",
          "CareOf": "string",
          "PostCode": "string",
          "TownName": "string",
          "CountrySubDivision": "string",
          "Country": "string",
          "AddressLine": [
            "string"
          ]
        }
      },
      "UltimateDebtor": {
        "Name": "string",
        "Identification": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
        "SchemeName": "string",
        "PostalAddress": {
          "AddressType": "BIZZ",
          "Department": "string",
          "SubDepartment": "string",
          "StreetName": "string",
          "BuildingNumber": "string",
          "BuildingName": "string",
          "Floor": "string",
          "UnitNumber": "string",
          "Room": "string",
          "PostBox": "string",
          "TownLocationName": "string",
          "DistrictName": "string",
          "CareOf": "string",
          "PostCode": "string",
          "TownName": "string",
          "CountrySubDivision": "string",
          "Country": "string",
          "AddressLine": [
            "string"
          ]
        }
      },
      "RemittanceInformation": {
        "Structured": [
          {
            "ReferredDocumentInformation": [
              {}
            ],
            "ReferredDocumentAmount": "string",
            "CreditorReferenceInformation": {
              "Code": {},
              "Issuer": "string",
              "Reference": "string"
            },
            "Invoicer": "string",
            "Invoicee": "string",
            "TaxRemittance": "string",
            "AdditionalRemittanceInformation": [
              "string"
            ]
          }
        ],
        "Unstructured": [
          "string"
        ]
      },
      "RegulatoryReporting": [
        {
          "DebitCreditReportingIndicator": "CRED",
          "Authority": {
            "Name": "string",
            "CountryCode": "string"
          },
          "Details": [
            {
              "Type": "string",
              "Date": {},
              "Country": {},
              "Amount": {},
              "Information": []
            }
          ]
        }
      ]
    },
    "Instruction": {
      "InstructionIdentification": "string",
      "EndToEndIdentification": "string",
      "RemittanceInformation": {
        "Structured": [
          {
            "ReferredDocumentInformation": [
              {}
            ],
            "ReferredDocumentAmount": "string",
            "CreditorReferenceInformation": {
              "Code": {},
              "Issuer": "string",
              "Reference": "string"
            },
            "Invoicer": "string",
            "Invoicee": "string",
            "TaxRemittance": "string",
            "AdditionalRemittanceInformation": [
              "string"
            ]
          }
        ],
        "Unstructured": [
          "string"
        ]
      },
      "LocalInstrument": "string",
      "InstructedAmount": {
        "Amount": "string",
        "Currency": "string"
      },
      "CreditorPostalAddress": {
        "AddressType": "BIZZ",
        "Department": "string",
        "SubDepartment": "string",
        "StreetName": "string",
        "BuildingNumber": "string",
        "BuildingName": "string",
        "Floor": "string",
        "UnitNumber": "string",
        "Room": "string",
        "PostBox": "string",
        "TownLocationName": "string",
        "DistrictName": "string",
        "CareOf": "string",
        "PostCode": "string",
        "TownName": "string",
        "CountrySubDivision": "string",
        "Country": "string",
        "AddressLine": [
          "string"
        ]
      },
      "CreditorAccount": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "SecondaryIdentification": "string",
        "Proxy": {
          "Identification": "string",
          "Code": "TELE",
          "Type": "string"
        }
      },
      "UltimateCreditor": {
        "Name": "string",
        "Identification": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
        "SchemeName": "string",
        "PostalAddress": {
          "AddressType": "BIZZ",
          "Department": "string",
          "SubDepartment": "string",
          "StreetName": "string",
          "BuildingNumber": "string",
          "BuildingName": "string",
          "Floor": "string",
          "UnitNumber": "string",
          "Room": "string",
          "PostBox": "string",
          "TownLocationName": "string",
          "DistrictName": "string",
          "CareOf": "string",
          "PostCode": "string",
          "TownName": "string",
          "CountrySubDivision": "string",
          "Country": "string",
          "AddressLine": [
            "string"
          ]
        }
      },
      "SupplementaryData": {}
    },
    "DebtorAccount": {
      "SchemeName": "string",
      "Identification": "string",
      "Name": "string",
      "SecondaryIdentification": "string",
      "Proxy": {
        "Identification": "string",
        "Code": "TELE",
        "Type": "string"
      }
    }
  },
  "Risk": {
    "PaymentContextCode": "BillingGoodsAndServicesInAdvance",
    "MerchantCategoryCode": "string",
    "MerchantCustomerIdentification": "string",
    "ContractPresentIndicator": true,
    "BeneficiaryPrepopulatedIndicator": true,
    "PaymentPurposeCode": "BKDF",
    "CategoryPurposeCode": "BONU",
    "BeneficiaryAccountType": "Business",
    "DeliveryAddress": {
      "AddressType": "BIZZ",
      "Department": "string",
      "SubDepartment": "string",
      "StreetName": "string",
      "BuildingNumber": "string",
      "BuildingName": "string",
      "Floor": "string",
      "UnitNumber": "string",
      "Room": "string",
      "PostBox": "string",
      "TownLocationName": "string",
      "DistrictName": "string",
      "CareOf": "string",
      "PostCode": "string",
      "TownName": "string",
      "CountrySubDivision": "string",
      "Country": "string",
      "AddressLine": [
        "string"
      ]
    }
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {}
}"""
  lazy val domesticVrpPost: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV401Prefix` / "pisp" / "domestic-vrps" =>
      EndpointHelpers.executeFutureCreated(req)(Future.successful(parseBody(EX_domesticVrpPost)))
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(domesticVrpPost),
    "POST",
    "/pisp/domestic-vrps",
    "Initiate a Domestic VRP",
    """Enables a PISP to initiate a Domestic VRP transaction under an already PSU-approved Domestic VRP Consent.""",
    parseBody(EXREQ_domesticVrpPost),
    parseBody(EX_domesticVrpPost),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Domestic VRPs") :: Nil,
    http4sPartialFunction = Some(domesticVrpPost)
  )

  private val EX_domesticVrpGet: String = """{
  "Data": {
    "DomesticVRPId": "string",
    "ConsentId": "string",
    "CreationDateTime": "2020-01-01T00:00:00+00:00",
    "Status": "RCVD",
    "StatusReason": [
      {
        "StatusReasonCode": "ERIN",
        "StatusReasonDescription": "string",
        "Path": "string"
      }
    ],
    "StatusUpdateDateTime": "2020-01-01T00:00:00+00:00",
    "ExpectedExecutionDateTime": "2020-01-01T00:00:00+00:00",
    "ExpectedSettlementDateTime": "2020-01-01T00:00:00+00:00",
    "Refund": {
      "Account": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "SecondaryIdentification": "string"
      }
    },
    "Charges": [
      {
        "ChargeBearer": "BorneByCreditor",
        "Type": "UK.OBIE.CHAPSOut",
        "Amount": {
          "Amount": "string",
          "Currency": "string"
        }
      }
    ],
    "Initiation": {
      "DebtorAccount": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "SecondaryIdentification": "string",
        "Proxy": {
          "Identification": "string",
          "Code": "TELE",
          "Type": "string"
        }
      },
      "CreditorAccount": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "SecondaryIdentification": "string",
        "Proxy": {
          "Identification": "string",
          "Code": "TELE",
          "Type": "string"
        }
      },
      "CreditorPostalAddress": {
        "AddressType": "BIZZ",
        "Department": "string",
        "SubDepartment": "string",
        "StreetName": "string",
        "BuildingNumber": "string",
        "BuildingName": "string",
        "Floor": "string",
        "UnitNumber": "string",
        "Room": "string",
        "PostBox": "string",
        "TownLocationName": "string",
        "DistrictName": "string",
        "CareOf": "string",
        "PostCode": "string",
        "TownName": "string",
        "CountrySubDivision": "string",
        "Country": "string",
        "AddressLine": [
          "string"
        ]
      },
      "UltimateCreditor": {
        "Name": "string",
        "Identification": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
        "SchemeName": "string",
        "PostalAddress": {
          "AddressType": "BIZZ",
          "Department": "string",
          "SubDepartment": "string",
          "StreetName": "string",
          "BuildingNumber": "string",
          "BuildingName": "string",
          "Floor": "string",
          "UnitNumber": "string",
          "Room": "string",
          "PostBox": "string",
          "TownLocationName": "string",
          "DistrictName": "string",
          "CareOf": "string",
          "PostCode": "string",
          "TownName": "string",
          "CountrySubDivision": "string",
          "Country": "string",
          "AddressLine": [
            "string"
          ]
        }
      },
      "UltimateDebtor": {
        "Name": "string",
        "Identification": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
        "SchemeName": "string",
        "PostalAddress": {
          "AddressType": "BIZZ",
          "Department": "string",
          "SubDepartment": "string",
          "StreetName": "string",
          "BuildingNumber": "string",
          "BuildingName": "string",
          "Floor": "string",
          "UnitNumber": "string",
          "Room": "string",
          "PostBox": "string",
          "TownLocationName": "string",
          "DistrictName": "string",
          "CareOf": "string",
          "PostCode": "string",
          "TownName": "string",
          "CountrySubDivision": "string",
          "Country": "string",
          "AddressLine": [
            "string"
          ]
        }
      },
      "RemittanceInformation": {
        "Structured": [
          {
            "ReferredDocumentInformation": [
              {}
            ],
            "ReferredDocumentAmount": "string",
            "CreditorReferenceInformation": {
              "Code": {},
              "Issuer": "string",
              "Reference": "string"
            },
            "Invoicer": "string",
            "Invoicee": "string",
            "TaxRemittance": "string",
            "AdditionalRemittanceInformation": [
              "string"
            ]
          }
        ],
        "Unstructured": [
          "string"
        ]
      },
      "RegulatoryReporting": [
        {
          "DebitCreditReportingIndicator": "CRED",
          "Authority": {
            "Name": "string",
            "CountryCode": "string"
          },
          "Details": [
            {
              "Type": "string",
              "Date": {},
              "Country": {},
              "Amount": {},
              "Information": []
            }
          ]
        }
      ]
    },
    "Instruction": {
      "InstructionIdentification": "string",
      "EndToEndIdentification": "string",
      "RemittanceInformation": {
        "Structured": [
          {
            "ReferredDocumentInformation": [
              {}
            ],
            "ReferredDocumentAmount": "string",
            "CreditorReferenceInformation": {
              "Code": {},
              "Issuer": "string",
              "Reference": "string"
            },
            "Invoicer": "string",
            "Invoicee": "string",
            "TaxRemittance": "string",
            "AdditionalRemittanceInformation": [
              "string"
            ]
          }
        ],
        "Unstructured": [
          "string"
        ]
      },
      "LocalInstrument": "string",
      "InstructedAmount": {
        "Amount": "string",
        "Currency": "string"
      },
      "CreditorPostalAddress": {
        "AddressType": "BIZZ",
        "Department": "string",
        "SubDepartment": "string",
        "StreetName": "string",
        "BuildingNumber": "string",
        "BuildingName": "string",
        "Floor": "string",
        "UnitNumber": "string",
        "Room": "string",
        "PostBox": "string",
        "TownLocationName": "string",
        "DistrictName": "string",
        "CareOf": "string",
        "PostCode": "string",
        "TownName": "string",
        "CountrySubDivision": "string",
        "Country": "string",
        "AddressLine": [
          "string"
        ]
      },
      "CreditorAccount": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "SecondaryIdentification": "string",
        "Proxy": {
          "Identification": "string",
          "Code": "TELE",
          "Type": "string"
        }
      },
      "UltimateCreditor": {
        "Name": "string",
        "Identification": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
        "SchemeName": "string",
        "PostalAddress": {
          "AddressType": "BIZZ",
          "Department": "string",
          "SubDepartment": "string",
          "StreetName": "string",
          "BuildingNumber": "string",
          "BuildingName": "string",
          "Floor": "string",
          "UnitNumber": "string",
          "Room": "string",
          "PostBox": "string",
          "TownLocationName": "string",
          "DistrictName": "string",
          "CareOf": "string",
          "PostCode": "string",
          "TownName": "string",
          "CountrySubDivision": "string",
          "Country": "string",
          "AddressLine": [
            "string"
          ]
        }
      },
      "SupplementaryData": {}
    },
    "DebtorAccount": {
      "SchemeName": "string",
      "Identification": "string",
      "Name": "string",
      "SecondaryIdentification": "string",
      "Proxy": {
        "Identification": "string",
        "Code": "TELE",
        "Type": "string"
      }
    }
  },
  "Risk": {
    "PaymentContextCode": "BillingGoodsAndServicesInAdvance",
    "MerchantCategoryCode": "string",
    "MerchantCustomerIdentification": "string",
    "ContractPresentIndicator": true,
    "BeneficiaryPrepopulatedIndicator": true,
    "PaymentPurposeCode": "BKDF",
    "CategoryPurposeCode": "BONU",
    "BeneficiaryAccountType": "Business",
    "DeliveryAddress": {
      "AddressType": "BIZZ",
      "Department": "string",
      "SubDepartment": "string",
      "StreetName": "string",
      "BuildingNumber": "string",
      "BuildingName": "string",
      "Floor": "string",
      "UnitNumber": "string",
      "Room": "string",
      "PostBox": "string",
      "TownLocationName": "string",
      "DistrictName": "string",
      "CareOf": "string",
      "PostCode": "string",
      "TownName": "string",
      "CountrySubDivision": "string",
      "Country": "string",
      "AddressLine": [
        "string"
      ]
    }
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {}
}"""
  lazy val domesticVrpGet: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "pisp" / "domestic-vrps" / domesticVRPId =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_domesticVrpGet)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(domesticVrpGet),
    "GET",
    "/pisp/domestic-vrps/DOMESTIC_V_R_P_ID",
    "Get a Domestic VRP",
    """Enables a PISP to retrieve the status of a Domestic VRP transaction.""",
    EmptyBody,
    parseBody(EX_domesticVrpGet),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Domestic VRPs") :: Nil,
    http4sPartialFunction = Some(domesticVrpGet)
  )

  private val EX_domesticVrpPaymentDetailsGet: String = """{
  "Data": {
    "PaymentStatus": [
      {
        "PaymentTransactionId": "string",
        "Status": "CANC",
        "StatusUpdateDateTime": "2020-01-01T00:00:00+00:00",
        "StatusDetail": {
          "LocalInstrument": "string",
          "Status": "CANC",
          "StatusReason": "string",
          "StatusReasonDescription": "string"
        }
      }
    ]
  }
}"""
  lazy val domesticVrpPaymentDetailsGet: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "pisp" / "domestic-vrps" / domesticVRPId / "payment-details" =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_domesticVrpPaymentDetailsGet)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(domesticVrpPaymentDetailsGet),
    "GET",
    "/pisp/domestic-vrps/DOMESTIC_V_R_P_ID/payment-details",
    "Get details of a Domestic VRP Payment",
    """Enables a PISP to retrieve detailed information on the status of a Domestic VRP transaction.""",
    EmptyBody,
    parseBody(EX_domesticVrpPaymentDetailsGet),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Domestic VRPs") :: Nil,
    http4sPartialFunction = Some(domesticVrpPaymentDetailsGet)
  )

  val routes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    domesticVrpConsentsPost(req)
      .orElse(domesticVrpConsentsGet(req)
      .orElse(domesticVrpConsentsPut(req)
      .orElse(domesticVrpConsentsDelete(req)
      .orElse(domesticVrpConsentsPatch(req)
      .orElse(domesticVrpConsentsFundsConfirmation(req)
      .orElse(domesticVrpPost(req)
      .orElse(domesticVrpGet(req)
      .orElse(domesticVrpPaymentDetailsGet(req)))))))))
  }
}
