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

// AUTO-GENERATED from UK Open Banking read-write-api-specs v4.0.1 (PaymentInitiation).
// Spec-faithful scaffold: routes return synthesized example JSON from the
// OpenAPI schemas (the specs carry no examples). Deepen to real OBP
// connector logic per endpoint later, mirroring v3_1_0.
object Http4sUKOBv401PaymentInitiation extends MdcLoggable {
  type HttpF[A] = OptionT[IO, A]
  implicit val formats: Formats = CustomJsonFormats.formats
  val implementedInApiVersion: ScannedApiVersion = ApiVersion.ukOpenBankingV401
  val resourceDocs = ArrayBuffer[ResourceDoc]()
  private def parseBody(s: String): JObject = JsonAliases.parse(s).asInstanceOf[JObject]
  val ukV401Prefix = Root / ApiVersion.ukOpenBankingV401.urlPrefix / ApiVersion.ukOpenBankingV401.apiShortVersion

  private val EXREQ_createDomesticPaymentConsents: String = """{
  "Data": {
    "ReadRefundAccount": "No",
    "Initiation": {
      "InstructionIdentification": "string",
      "EndToEndIdentification": "string",
      "LocalInstrument": "string",
      "InstructedAmount": {
        "Amount": "string",
        "Currency": "string"
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
      },
      "CreditorAgent": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
      ],
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "SupplementaryData": {}
    },
    "Authorisation": {
      "AuthorisationType": "Any",
      "CompletionDateTime": "2020-01-01T00:00:00+00:00"
    },
    "SCASupportData": {
      "RequestedSCAExemptionType": "BillPayment",
      "AppliedAuthenticationApproach": "CA",
      "ReferencePaymentOrderId": "string"
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
  private val EX_createDomesticPaymentConsents: String = """{
  "Data": {
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
    "ReadRefundAccount": "No",
    "CutOffDateTime": "2020-01-01T00:00:00+00:00",
    "ExpectedExecutionDateTime": "2020-01-01T00:00:00+00:00",
    "ExpectedSettlementDateTime": "2020-01-01T00:00:00+00:00",
    "Charges": [
      {
        "ChargeBearer": "BorneByCreditor",
        "Type": "string",
        "Amount": {
          "Amount": "string",
          "Currency": "string"
        }
      }
    ],
    "Initiation": {
      "InstructionIdentification": "string",
      "EndToEndIdentification": "string",
      "LocalInstrument": "string",
      "InstructedAmount": {
        "Amount": "string",
        "Currency": "string"
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
      },
      "CreditorAgent": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
      ],
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "SupplementaryData": {}
    },
    "Authorisation": {
      "AuthorisationType": "Any",
      "CompletionDateTime": "2020-01-01T00:00:00+00:00"
    },
    "SCASupportData": {
      "RequestedSCAExemptionType": "BillPayment",
      "AppliedAuthenticationApproach": "CA",
      "ReferencePaymentOrderId": "string"
    },
    "Debtor": {
      "SchemeName": "string",
      "Identification": "string",
      "Name": "string",
      "SecondaryIdentification": "string",
      "LEI": "IZ9Q00LZEVUKWCQY6X15"
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
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val createDomesticPaymentConsents: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV401Prefix` / "pisp" / "domestic-payment-consents" =>
      EndpointHelpers.executeFutureCreated(req)(Future.successful(parseBody(EX_createDomesticPaymentConsents)))
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createDomesticPaymentConsents),
    "POST",
    "/pisp/domestic-payment-consents",
    "Create a Domestic Payment Consent",
    """Enables a PISP to register an intent to initiate a Domestic Payment.""",
    parseBody(EXREQ_createDomesticPaymentConsents),
    parseBody(EX_createDomesticPaymentConsents),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Domestic Payment Consents") :: Nil,
    http4sPartialFunction = Some(createDomesticPaymentConsents)
  )

  private val EX_getDomesticPaymentConsentsConsentId: String = """{
  "Data": {
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
    "ReadRefundAccount": "No",
    "CutOffDateTime": "2020-01-01T00:00:00+00:00",
    "ExpectedExecutionDateTime": "2020-01-01T00:00:00+00:00",
    "ExpectedSettlementDateTime": "2020-01-01T00:00:00+00:00",
    "Charges": [
      {
        "ChargeBearer": "BorneByCreditor",
        "Type": "string",
        "Amount": {
          "Amount": "string",
          "Currency": "string"
        }
      }
    ],
    "Initiation": {
      "InstructionIdentification": "string",
      "EndToEndIdentification": "string",
      "LocalInstrument": "string",
      "InstructedAmount": {
        "Amount": "string",
        "Currency": "string"
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
      },
      "CreditorAgent": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
      ],
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "SupplementaryData": {}
    },
    "Authorisation": {
      "AuthorisationType": "Any",
      "CompletionDateTime": "2020-01-01T00:00:00+00:00"
    },
    "SCASupportData": {
      "RequestedSCAExemptionType": "BillPayment",
      "AppliedAuthenticationApproach": "CA",
      "ReferencePaymentOrderId": "string"
    },
    "Debtor": {
      "SchemeName": "string",
      "Identification": "string",
      "Name": "string",
      "SecondaryIdentification": "string",
      "LEI": "IZ9Q00LZEVUKWCQY6X15"
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
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val getDomesticPaymentConsentsConsentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "pisp" / "domestic-payment-consents" / consentId =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getDomesticPaymentConsentsConsentId)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getDomesticPaymentConsentsConsentId),
    "GET",
    "/pisp/domestic-payment-consents/CONSENT_ID",
    "Get a Domestic Payment Consent",
    """Enables a PISP to retrieve the status of an intent to initiate a Domestic Payment.""",
    EmptyBody,
    parseBody(EX_getDomesticPaymentConsentsConsentId),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Domestic Payment Consents") :: Nil,
    http4sPartialFunction = Some(getDomesticPaymentConsentsConsentId)
  )

  private val EX_getDomesticPaymentConsentsConsentIdFundsConfirmation: String = """{
  "Data": {
    "FundsAvailableResult": {
      "FundsAvailableDateTime": "2020-01-01T00:00:00+00:00",
      "FundsAvailable": true
    },
    "SupplementaryData": {}
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val getDomesticPaymentConsentsConsentIdFundsConfirmation: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "pisp" / "domestic-payment-consents" / consentId / "funds-confirmation" =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getDomesticPaymentConsentsConsentIdFundsConfirmation)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getDomesticPaymentConsentsConsentIdFundsConfirmation),
    "GET",
    "/pisp/domestic-payment-consents/CONSENT_ID/funds-confirmation",
    "Confirm availability of funds for a Domestic Payment",
    """Enables a PISP to check whether a PSU has sufficient available funds for a Domestic Payment.""",
    EmptyBody,
    parseBody(EX_getDomesticPaymentConsentsConsentIdFundsConfirmation),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Domestic Payment Consents") :: Nil,
    http4sPartialFunction = Some(getDomesticPaymentConsentsConsentIdFundsConfirmation)
  )

  private val EXREQ_createDomesticPayments: String = """{
  "Data": {
    "ConsentId": "string",
    "Initiation": {
      "InstructionIdentification": "string",
      "EndToEndIdentification": "string",
      "LocalInstrument": "string",
      "InstructedAmount": {
        "Amount": "string",
        "Currency": "string"
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
      },
      "CreditorAgent": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
      ],
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
  private val EX_createDomesticPayments: String = """{
  "Data": {
    "DomesticPaymentId": "string",
    "ConsentId": "string",
    "CreationDateTime": "2020-01-01T00:00:00+00:00",
    "Status": "RCVD",
    "StatusUpdateDateTime": "2020-01-01T00:00:00+00:00",
    "StatusReason": [
      {
        "StatusReasonCode": "ERIN",
        "StatusReasonDescription": "string",
        "Path": "string"
      }
    ],
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
        "Type": "string",
        "Amount": {
          "Amount": "string",
          "Currency": "string"
        }
      }
    ],
    "Initiation": {
      "InstructionIdentification": "string",
      "EndToEndIdentification": "string",
      "LocalInstrument": "string",
      "InstructedAmount": {
        "Amount": "string",
        "Currency": "string"
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
      },
      "CreditorAgent": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
      ],
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "SupplementaryData": {}
    },
    "MultiAuthorisation": {
      "Status": "AUTH",
      "NumberRequired": 0,
      "NumberReceived": 0,
      "LastUpdateDateTime": "2020-01-01T00:00:00+00:00",
      "ExpirationDateTime": "2020-01-01T00:00:00+00:00"
    },
    "Debtor": {
      "SchemeName": "string",
      "Identification": "string",
      "Name": "string",
      "SecondaryIdentification": "string",
      "LEI": "IZ9Q00LZEVUKWCQY6X15"
    }
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val createDomesticPayments: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV401Prefix` / "pisp" / "domestic-payments" =>
      EndpointHelpers.executeFutureCreated(req)(Future.successful(parseBody(EX_createDomesticPayments)))
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createDomesticPayments),
    "POST",
    "/pisp/domestic-payments",
    "Initiate a Domestic Payment",
    """Enables a PISP to initiate an already PSU-approved Domestic Payment.""",
    parseBody(EXREQ_createDomesticPayments),
    parseBody(EX_createDomesticPayments),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Domestic Payments") :: Nil,
    http4sPartialFunction = Some(createDomesticPayments)
  )

  private val EX_getDomesticPaymentsDomesticPaymentId: String = """{
  "Data": {
    "DomesticPaymentId": "string",
    "ConsentId": "string",
    "CreationDateTime": "2020-01-01T00:00:00+00:00",
    "Status": "RCVD",
    "StatusUpdateDateTime": "2020-01-01T00:00:00+00:00",
    "StatusReason": [
      {
        "StatusReasonCode": "ERIN",
        "StatusReasonDescription": "string",
        "Path": "string"
      }
    ],
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
        "Type": "string",
        "Amount": {
          "Amount": "string",
          "Currency": "string"
        }
      }
    ],
    "Initiation": {
      "InstructionIdentification": "string",
      "EndToEndIdentification": "string",
      "LocalInstrument": "string",
      "InstructedAmount": {
        "Amount": "string",
        "Currency": "string"
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
      },
      "CreditorAgent": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
      ],
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "SupplementaryData": {}
    },
    "MultiAuthorisation": {
      "Status": "AUTH",
      "NumberRequired": 0,
      "NumberReceived": 0,
      "LastUpdateDateTime": "2020-01-01T00:00:00+00:00",
      "ExpirationDateTime": "2020-01-01T00:00:00+00:00"
    },
    "Debtor": {
      "SchemeName": "string",
      "Identification": "string",
      "Name": "string",
      "SecondaryIdentification": "string",
      "LEI": "IZ9Q00LZEVUKWCQY6X15"
    }
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val getDomesticPaymentsDomesticPaymentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "pisp" / "domestic-payments" / domesticPaymentId =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getDomesticPaymentsDomesticPaymentId)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getDomesticPaymentsDomesticPaymentId),
    "GET",
    "/pisp/domestic-payments/DOMESTIC_PAYMENT_ID",
    "Get a Domestic Payment",
    """Enables a PISP to retrieve the status of a Domestic Payment.""",
    EmptyBody,
    parseBody(EX_getDomesticPaymentsDomesticPaymentId),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Domestic Payments") :: Nil,
    http4sPartialFunction = Some(getDomesticPaymentsDomesticPaymentId)
  )

  private val EX_getDomesticPaymentsDomesticPaymentIdPaymentDetails: String = """{
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
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val getDomesticPaymentsDomesticPaymentIdPaymentDetails: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "pisp" / "domestic-payments" / domesticPaymentId / "payment-details" =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getDomesticPaymentsDomesticPaymentIdPaymentDetails)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getDomesticPaymentsDomesticPaymentIdPaymentDetails),
    "GET",
    "/pisp/domestic-payments/DOMESTIC_PAYMENT_ID/payment-details",
    "Get details of a Domestic Payment",
    """Enables a PISP to retrieve detailed information on the status of a Domestic Payment.""",
    EmptyBody,
    parseBody(EX_getDomesticPaymentsDomesticPaymentIdPaymentDetails),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Domestic Payments") :: Nil,
    http4sPartialFunction = Some(getDomesticPaymentsDomesticPaymentIdPaymentDetails)
  )

  private val EXREQ_createDomesticScheduledPaymentConsents: String = """{
  "Data": {
    "Permission": "Create",
    "ReadRefundAccount": "No",
    "Initiation": {
      "InstructionIdentification": "string",
      "EndToEndIdentification": "string",
      "LocalInstrument": "string",
      "RequestedExecutionDateTime": "2020-01-01T00:00:00+00:00",
      "InstructedAmount": {
        "Amount": "string",
        "Currency": "string"
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
      ],
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "SupplementaryData": {}
    },
    "Authorisation": {
      "AuthorisationType": "Any",
      "CompletionDateTime": "2020-01-01T00:00:00+00:00"
    },
    "SCASupportData": {
      "RequestedSCAExemptionType": "BillPayment",
      "AppliedAuthenticationApproach": "CA",
      "ReferencePaymentOrderId": "string"
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
  private val EX_createDomesticScheduledPaymentConsents: String = """{
  "Data": {
    "ConsentId": "string",
    "CreationDateTime": "2020-01-01T00:00:00+00:00",
    "Status": "AWAU",
    "StatusUpdateDateTime": "2020-01-01T00:00:00+00:00",
    "StatusReason": [
      {
        "StatusReasonCode": "ERIN",
        "StatusReasonDescription": "string",
        "Path": "string"
      }
    ],
    "Permission": "Create",
    "ReadRefundAccount": "No",
    "CutOffDateTime": "2020-01-01T00:00:00+00:00",
    "ExpectedExecutionDateTime": "2020-01-01T00:00:00+00:00",
    "ExpectedSettlementDateTime": "2020-01-01T00:00:00+00:00",
    "Charges": [
      {
        "ChargeBearer": "BorneByCreditor",
        "Type": "string",
        "Amount": {
          "Amount": "string",
          "Currency": "string"
        }
      }
    ],
    "Initiation": {
      "InstructionIdentification": "string",
      "EndToEndIdentification": "string",
      "LocalInstrument": "string",
      "RequestedExecutionDateTime": "2020-01-01T00:00:00+00:00",
      "InstructedAmount": {
        "Amount": "string",
        "Currency": "string"
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
      ],
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "SupplementaryData": {}
    },
    "Authorisation": {
      "AuthorisationType": "Any",
      "CompletionDateTime": "2020-01-01T00:00:00+00:00"
    },
    "SCASupportData": {
      "RequestedSCAExemptionType": "BillPayment",
      "AppliedAuthenticationApproach": "CA",
      "ReferencePaymentOrderId": "string"
    },
    "Debtor": {
      "SchemeName": "string",
      "Identification": "string",
      "Name": "string",
      "SecondaryIdentification": "string",
      "LEI": "IZ9Q00LZEVUKWCQY6X15"
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
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val createDomesticScheduledPaymentConsents: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV401Prefix` / "pisp" / "domestic-scheduled-payment-consents" =>
      EndpointHelpers.executeFutureCreated(req)(Future.successful(parseBody(EX_createDomesticScheduledPaymentConsents)))
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createDomesticScheduledPaymentConsents),
    "POST",
    "/pisp/domestic-scheduled-payment-consents",
    "Create a Domestic Scheduled Payment Consent",
    """Enables a PISP to register an intent to initiate a Domestic Scheduled Payment.""",
    parseBody(EXREQ_createDomesticScheduledPaymentConsents),
    parseBody(EX_createDomesticScheduledPaymentConsents),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Domestic Scheduled Payment Consents") :: Nil,
    http4sPartialFunction = Some(createDomesticScheduledPaymentConsents)
  )

  private val EX_getDomesticScheduledPaymentConsentsConsentId: String = """{
  "Data": {
    "ConsentId": "string",
    "CreationDateTime": "2020-01-01T00:00:00+00:00",
    "Status": "AWAU",
    "StatusUpdateDateTime": "2020-01-01T00:00:00+00:00",
    "StatusReason": [
      {
        "StatusReasonCode": "ERIN",
        "StatusReasonDescription": "string",
        "Path": "string"
      }
    ],
    "Permission": "Create",
    "ReadRefundAccount": "No",
    "CutOffDateTime": "2020-01-01T00:00:00+00:00",
    "ExpectedExecutionDateTime": "2020-01-01T00:00:00+00:00",
    "ExpectedSettlementDateTime": "2020-01-01T00:00:00+00:00",
    "Charges": [
      {
        "ChargeBearer": "BorneByCreditor",
        "Type": "string",
        "Amount": {
          "Amount": "string",
          "Currency": "string"
        }
      }
    ],
    "Initiation": {
      "InstructionIdentification": "string",
      "EndToEndIdentification": "string",
      "LocalInstrument": "string",
      "RequestedExecutionDateTime": "2020-01-01T00:00:00+00:00",
      "InstructedAmount": {
        "Amount": "string",
        "Currency": "string"
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
      ],
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "SupplementaryData": {}
    },
    "Authorisation": {
      "AuthorisationType": "Any",
      "CompletionDateTime": "2020-01-01T00:00:00+00:00"
    },
    "SCASupportData": {
      "RequestedSCAExemptionType": "BillPayment",
      "AppliedAuthenticationApproach": "CA",
      "ReferencePaymentOrderId": "string"
    },
    "Debtor": {
      "SchemeName": "string",
      "Identification": "string",
      "Name": "string",
      "SecondaryIdentification": "string",
      "LEI": "IZ9Q00LZEVUKWCQY6X15"
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
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val getDomesticScheduledPaymentConsentsConsentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "pisp" / "domestic-scheduled-payment-consents" / consentId =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getDomesticScheduledPaymentConsentsConsentId)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getDomesticScheduledPaymentConsentsConsentId),
    "GET",
    "/pisp/domestic-scheduled-payment-consents/CONSENT_ID",
    "Get a Domestic Scheduled Payment Consent",
    """Enables a PISP to retrieve the status of an intent to initiate a Domestic Scheduled Payment.""",
    EmptyBody,
    parseBody(EX_getDomesticScheduledPaymentConsentsConsentId),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Domestic Scheduled Payment Consents") :: Nil,
    http4sPartialFunction = Some(getDomesticScheduledPaymentConsentsConsentId)
  )

  private val EXREQ_createDomesticScheduledPayments: String = """{
  "Data": {
    "ConsentId": "string",
    "Initiation": {
      "InstructionIdentification": "string",
      "EndToEndIdentification": "string",
      "LocalInstrument": "string",
      "RequestedExecutionDateTime": "2020-01-01T00:00:00+00:00",
      "InstructedAmount": {
        "Amount": "string",
        "Currency": "string"
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
      ],
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
  private val EX_createDomesticScheduledPayments: String = """{
  "Data": {
    "DomesticScheduledPaymentId": "string",
    "ConsentId": "string",
    "CreationDateTime": "2020-01-01T00:00:00+00:00",
    "Status": "CANC",
    "StatusUpdateDateTime": "2020-01-01T00:00:00+00:00",
    "StatusReason": [
      {
        "StatusReasonCode": "ERIN",
        "StatusReasonDescription": "string",
        "Path": "string"
      }
    ],
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
        "Type": "string",
        "Amount": {
          "Amount": "string",
          "Currency": "string"
        }
      }
    ],
    "Initiation": {
      "InstructionIdentification": "string",
      "EndToEndIdentification": "string",
      "LocalInstrument": "string",
      "RequestedExecutionDateTime": "2020-01-01T00:00:00+00:00",
      "InstructedAmount": {
        "Amount": "string",
        "Currency": "string"
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
      ],
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "SupplementaryData": {}
    },
    "MultiAuthorisation": {
      "Status": "AUTH",
      "NumberRequired": 0,
      "NumberReceived": 0,
      "LastUpdateDateTime": "2020-01-01T00:00:00+00:00",
      "ExpirationDateTime": "2020-01-01T00:00:00+00:00"
    },
    "Debtor": {
      "SchemeName": "string",
      "Identification": "string",
      "Name": "string",
      "SecondaryIdentification": "string",
      "LEI": "IZ9Q00LZEVUKWCQY6X15"
    }
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val createDomesticScheduledPayments: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV401Prefix` / "pisp" / "domestic-scheduled-payments" =>
      EndpointHelpers.executeFutureCreated(req)(Future.successful(parseBody(EX_createDomesticScheduledPayments)))
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createDomesticScheduledPayments),
    "POST",
    "/pisp/domestic-scheduled-payments",
    "Initiate a Domestic Scheduled Payment",
    """Enables a PISP to initiate an already PSU-approved Domestic Scheduled Payment.""",
    parseBody(EXREQ_createDomesticScheduledPayments),
    parseBody(EX_createDomesticScheduledPayments),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Domestic Scheduled Payments") :: Nil,
    http4sPartialFunction = Some(createDomesticScheduledPayments)
  )

  private val EX_getDomesticScheduledPaymentsDomesticScheduledPaymentId: String = """{
  "Data": {
    "DomesticScheduledPaymentId": "string",
    "ConsentId": "string",
    "CreationDateTime": "2020-01-01T00:00:00+00:00",
    "Status": "CANC",
    "StatusUpdateDateTime": "2020-01-01T00:00:00+00:00",
    "StatusReason": [
      {
        "StatusReasonCode": "ERIN",
        "StatusReasonDescription": "string",
        "Path": "string"
      }
    ],
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
        "Type": "string",
        "Amount": {
          "Amount": "string",
          "Currency": "string"
        }
      }
    ],
    "Initiation": {
      "InstructionIdentification": "string",
      "EndToEndIdentification": "string",
      "LocalInstrument": "string",
      "RequestedExecutionDateTime": "2020-01-01T00:00:00+00:00",
      "InstructedAmount": {
        "Amount": "string",
        "Currency": "string"
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
      ],
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "SupplementaryData": {}
    },
    "MultiAuthorisation": {
      "Status": "AUTH",
      "NumberRequired": 0,
      "NumberReceived": 0,
      "LastUpdateDateTime": "2020-01-01T00:00:00+00:00",
      "ExpirationDateTime": "2020-01-01T00:00:00+00:00"
    },
    "Debtor": {
      "SchemeName": "string",
      "Identification": "string",
      "Name": "string",
      "SecondaryIdentification": "string",
      "LEI": "IZ9Q00LZEVUKWCQY6X15"
    }
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val getDomesticScheduledPaymentsDomesticScheduledPaymentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "pisp" / "domestic-scheduled-payments" / domesticScheduledPaymentId =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getDomesticScheduledPaymentsDomesticScheduledPaymentId)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getDomesticScheduledPaymentsDomesticScheduledPaymentId),
    "GET",
    "/pisp/domestic-scheduled-payments/DOMESTIC_SCHEDULED_PAYMENT_ID",
    "Get a Domestic Scheduled Payment",
    """Enables a PISP to retrieve the status of a Domestic Scheduled Payment.""",
    EmptyBody,
    parseBody(EX_getDomesticScheduledPaymentsDomesticScheduledPaymentId),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Domestic Scheduled Payments") :: Nil,
    http4sPartialFunction = Some(getDomesticScheduledPaymentsDomesticScheduledPaymentId)
  )

  private val EX_getDomesticScheduledPaymentsDomesticScheduledPaymentIdPaymentDetails: String = """{
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
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val getDomesticScheduledPaymentsDomesticScheduledPaymentIdPaymentDetails: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "pisp" / "domestic-scheduled-payments" / domesticScheduledPaymentId / "payment-details" =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getDomesticScheduledPaymentsDomesticScheduledPaymentIdPaymentDetails)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getDomesticScheduledPaymentsDomesticScheduledPaymentIdPaymentDetails),
    "GET",
    "/pisp/domestic-scheduled-payments/DOMESTIC_SCHEDULED_PAYMENT_ID/payment-details",
    "Get details of a Domestic Scheduled Payment",
    """Enables a PISP to retrieve detailed information on the status of a Domestic Scheduled Payment.""",
    EmptyBody,
    parseBody(EX_getDomesticScheduledPaymentsDomesticScheduledPaymentIdPaymentDetails),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Domestic Scheduled Payments") :: Nil,
    http4sPartialFunction = Some(getDomesticScheduledPaymentsDomesticScheduledPaymentIdPaymentDetails)
  )

  private val EXREQ_createDomesticStandingOrderConsents: String = """{
  "Data": {
    "Permission": "Create",
    "ReadRefundAccount": "No",
    "Initiation": {
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "NumberOfPayments": "string",
      "FirstPaymentAmount": {
        "Amount": "string",
        "Currency": "string"
      },
      "RecurringPaymentAmount": {
        "Amount": "string",
        "Currency": "string"
      },
      "FinalPaymentAmount": {
        "Amount": "string",
        "Currency": "string"
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
      ],
      "MandateRelatedInformation": {
        "MandateIdentification": "string",
        "Classification": "FIXE",
        "CategoryPurposeCode": "BONU",
        "FirstPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "RecurringPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "FinalPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "Frequency": {
          "Type": "ADHO",
          "CountPerPeriod": 1,
          "PointInTime": "00"
        },
        "Reason": "string"
      },
      "SupplementaryData": {}
    },
    "Authorisation": {
      "AuthorisationType": "Any",
      "CompletionDateTime": "2020-01-01T00:00:00+00:00"
    },
    "SCASupportData": {
      "RequestedSCAExemptionType": "BillPayment",
      "AppliedAuthenticationApproach": "CA",
      "ReferencePaymentOrderId": "string"
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
  private val EX_createDomesticStandingOrderConsents: String = """{
  "Data": {
    "ConsentId": "string",
    "CreationDateTime": "2020-01-01T00:00:00+00:00",
    "Status": "AWAU",
    "StatusUpdateDateTime": "2020-01-01T00:00:00+00:00",
    "StatusReason": [
      {
        "StatusReasonCode": "ERIN",
        "StatusReasonDescription": "string",
        "Path": "string"
      }
    ],
    "Permission": "Create",
    "ReadRefundAccount": "No",
    "CutOffDateTime": "2020-01-01T00:00:00+00:00",
    "Charges": [
      {
        "ChargeBearer": "BorneByCreditor",
        "Type": "string",
        "Amount": {
          "Amount": "string",
          "Currency": "string"
        }
      }
    ],
    "Initiation": {
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "NumberOfPayments": "string",
      "FirstPaymentAmount": {
        "Amount": "string",
        "Currency": "string"
      },
      "RecurringPaymentAmount": {
        "Amount": "string",
        "Currency": "string"
      },
      "FinalPaymentAmount": {
        "Amount": "string",
        "Currency": "string"
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
      ],
      "MandateRelatedInformation": {
        "MandateIdentification": "string",
        "Classification": "FIXE",
        "CategoryPurposeCode": "BONU",
        "FirstPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "RecurringPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "FinalPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "Frequency": {
          "Type": "ADHO",
          "CountPerPeriod": 1,
          "PointInTime": "00"
        },
        "Reason": "string"
      },
      "SupplementaryData": {}
    },
    "Authorisation": {
      "AuthorisationType": "Any",
      "CompletionDateTime": "2020-01-01T00:00:00+00:00"
    },
    "SCASupportData": {
      "RequestedSCAExemptionType": "BillPayment",
      "AppliedAuthenticationApproach": "CA",
      "ReferencePaymentOrderId": "string"
    },
    "Debtor": {
      "SchemeName": "string",
      "Identification": "string",
      "Name": "string",
      "SecondaryIdentification": "string",
      "LEI": "IZ9Q00LZEVUKWCQY6X15"
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
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val createDomesticStandingOrderConsents: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV401Prefix` / "pisp" / "domestic-standing-order-consents" =>
      EndpointHelpers.executeFutureCreated(req)(Future.successful(parseBody(EX_createDomesticStandingOrderConsents)))
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createDomesticStandingOrderConsents),
    "POST",
    "/pisp/domestic-standing-order-consents",
    "Create a Domestic Standing Order Consent",
    """Enables a PISP to register an intent to initiate a Domestic Standing Order arrangement.""",
    parseBody(EXREQ_createDomesticStandingOrderConsents),
    parseBody(EX_createDomesticStandingOrderConsents),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Domestic Standing Order Consents") :: Nil,
    http4sPartialFunction = Some(createDomesticStandingOrderConsents)
  )

  private val EX_getDomesticStandingOrderConsentsConsentId: String = """{
  "Data": {
    "ConsentId": "string",
    "CreationDateTime": "2020-01-01T00:00:00+00:00",
    "Status": "AWAU",
    "StatusUpdateDateTime": "2020-01-01T00:00:00+00:00",
    "StatusReason": [
      {
        "StatusReasonCode": "ERIN",
        "StatusReasonDescription": "string",
        "Path": "string"
      }
    ],
    "Permission": "Create",
    "ReadRefundAccount": "No",
    "CutOffDateTime": "2020-01-01T00:00:00+00:00",
    "Charges": [
      {
        "ChargeBearer": "BorneByCreditor",
        "Type": "string",
        "Amount": {
          "Amount": "string",
          "Currency": "string"
        }
      }
    ],
    "Initiation": {
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "NumberOfPayments": "string",
      "FirstPaymentAmount": {
        "Amount": "string",
        "Currency": "string"
      },
      "RecurringPaymentAmount": {
        "Amount": "string",
        "Currency": "string"
      },
      "FinalPaymentAmount": {
        "Amount": "string",
        "Currency": "string"
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
      ],
      "MandateRelatedInformation": {
        "MandateIdentification": "string",
        "Classification": "FIXE",
        "CategoryPurposeCode": "BONU",
        "FirstPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "RecurringPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "FinalPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "Frequency": {
          "Type": "ADHO",
          "CountPerPeriod": 1,
          "PointInTime": "00"
        },
        "Reason": "string"
      },
      "SupplementaryData": {}
    },
    "Authorisation": {
      "AuthorisationType": "Any",
      "CompletionDateTime": "2020-01-01T00:00:00+00:00"
    },
    "SCASupportData": {
      "RequestedSCAExemptionType": "BillPayment",
      "AppliedAuthenticationApproach": "CA",
      "ReferencePaymentOrderId": "string"
    },
    "Debtor": {
      "SchemeName": "string",
      "Identification": "string",
      "Name": "string",
      "SecondaryIdentification": "string",
      "LEI": "IZ9Q00LZEVUKWCQY6X15"
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
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val getDomesticStandingOrderConsentsConsentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "pisp" / "domestic-standing-order-consents" / consentId =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getDomesticStandingOrderConsentsConsentId)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getDomesticStandingOrderConsentsConsentId),
    "GET",
    "/pisp/domestic-standing-order-consents/CONSENT_ID",
    "Get a Domestic Standing Order Consent",
    """Enables a PISP to retrieve the status of an intent to initiate a Domestic Standing Order arrangement.""",
    EmptyBody,
    parseBody(EX_getDomesticStandingOrderConsentsConsentId),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Domestic Standing Order Consents") :: Nil,
    http4sPartialFunction = Some(getDomesticStandingOrderConsentsConsentId)
  )

  private val EXREQ_createDomesticStandingOrders: String = """{
  "Data": {
    "ConsentId": "string",
    "Initiation": {
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "NumberOfPayments": "string",
      "FirstPaymentAmount": {
        "Amount": "string",
        "Currency": "string"
      },
      "RecurringPaymentAmount": {
        "Amount": "string",
        "Currency": "string"
      },
      "FinalPaymentAmount": {
        "Amount": "string",
        "Currency": "string"
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
      ],
      "MandateRelatedInformation": {
        "MandateIdentification": "string",
        "Classification": "FIXE",
        "CategoryPurposeCode": "BONU",
        "FirstPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "RecurringPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "FinalPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "Frequency": {
          "Type": "ADHO",
          "CountPerPeriod": 1,
          "PointInTime": "00"
        },
        "Reason": "string"
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
  private val EX_createDomesticStandingOrders: String = """{
  "Data": {
    "DomesticStandingOrderId": "string",
    "ConsentId": "string",
    "CreationDateTime": "2020-01-01T00:00:00+00:00",
    "Status": "CANC",
    "StatusUpdateDateTime": "2020-01-01T00:00:00+00:00",
    "StatusReason": [
      {
        "StatusReasonCode": "ERIN",
        "StatusReasonDescription": "string",
        "Path": "string"
      }
    ],
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
        "Type": "string",
        "Amount": {
          "Amount": "string",
          "Currency": "string"
        }
      }
    ],
    "Initiation": {
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "NumberOfPayments": "string",
      "FirstPaymentAmount": {
        "Amount": "string",
        "Currency": "string"
      },
      "RecurringPaymentAmount": {
        "Amount": "string",
        "Currency": "string"
      },
      "FinalPaymentAmount": {
        "Amount": "string",
        "Currency": "string"
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
      ],
      "MandateRelatedInformation": {
        "MandateIdentification": "string",
        "Classification": "FIXE",
        "CategoryPurposeCode": "BONU",
        "FirstPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "RecurringPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "FinalPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "Frequency": {
          "Type": "ADHO",
          "CountPerPeriod": 1,
          "PointInTime": "00"
        },
        "Reason": "string"
      },
      "SupplementaryData": {}
    },
    "MultiAuthorisation": {
      "Status": "AUTH",
      "NumberRequired": 0,
      "NumberReceived": 0,
      "LastUpdateDateTime": "2020-01-01T00:00:00+00:00",
      "ExpirationDateTime": "2020-01-01T00:00:00+00:00"
    },
    "Debtor": {
      "SchemeName": "string",
      "Identification": "string",
      "Name": "string",
      "SecondaryIdentification": "string",
      "LEI": "IZ9Q00LZEVUKWCQY6X15"
    }
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val createDomesticStandingOrders: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV401Prefix` / "pisp" / "domestic-standing-orders" =>
      EndpointHelpers.executeFutureCreated(req)(Future.successful(parseBody(EX_createDomesticStandingOrders)))
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createDomesticStandingOrders),
    "POST",
    "/pisp/domestic-standing-orders",
    "Submit a Domestic Standing Order",
    """Enables a PISP to submit a Domestic Standing Order payment under an already PSU-approved Domestic Standing Order arrangement.""",
    parseBody(EXREQ_createDomesticStandingOrders),
    parseBody(EX_createDomesticStandingOrders),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Domestic Standing Orders") :: Nil,
    http4sPartialFunction = Some(createDomesticStandingOrders)
  )

  private val EX_getDomesticStandingOrdersDomesticStandingOrderId: String = """{
  "Data": {
    "DomesticStandingOrderId": "string",
    "ConsentId": "string",
    "CreationDateTime": "2020-01-01T00:00:00+00:00",
    "Status": "CANC",
    "StatusUpdateDateTime": "2020-01-01T00:00:00+00:00",
    "StatusReason": [
      {
        "StatusReasonCode": "ERIN",
        "StatusReasonDescription": "string",
        "Path": "string"
      }
    ],
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
        "Type": "string",
        "Amount": {
          "Amount": "string",
          "Currency": "string"
        }
      }
    ],
    "Initiation": {
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "NumberOfPayments": "string",
      "FirstPaymentAmount": {
        "Amount": "string",
        "Currency": "string"
      },
      "RecurringPaymentAmount": {
        "Amount": "string",
        "Currency": "string"
      },
      "FinalPaymentAmount": {
        "Amount": "string",
        "Currency": "string"
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
      ],
      "MandateRelatedInformation": {
        "MandateIdentification": "string",
        "Classification": "FIXE",
        "CategoryPurposeCode": "BONU",
        "FirstPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "RecurringPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "FinalPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "Frequency": {
          "Type": "ADHO",
          "CountPerPeriod": 1,
          "PointInTime": "00"
        },
        "Reason": "string"
      },
      "SupplementaryData": {}
    },
    "MultiAuthorisation": {
      "Status": "AUTH",
      "NumberRequired": 0,
      "NumberReceived": 0,
      "LastUpdateDateTime": "2020-01-01T00:00:00+00:00",
      "ExpirationDateTime": "2020-01-01T00:00:00+00:00"
    },
    "Debtor": {
      "SchemeName": "string",
      "Identification": "string",
      "Name": "string",
      "SecondaryIdentification": "string",
      "LEI": "IZ9Q00LZEVUKWCQY6X15"
    }
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val getDomesticStandingOrdersDomesticStandingOrderId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "pisp" / "domestic-standing-orders" / domesticStandingOrderId =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getDomesticStandingOrdersDomesticStandingOrderId)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getDomesticStandingOrdersDomesticStandingOrderId),
    "GET",
    "/pisp/domestic-standing-orders/DOMESTIC_STANDING_ORDER_ID",
    "Get a Domestic Standing Order",
    """Enables a PISP to retrieve the status of a Domestic Standing Order payment.""",
    EmptyBody,
    parseBody(EX_getDomesticStandingOrdersDomesticStandingOrderId),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Domestic Standing Orders") :: Nil,
    http4sPartialFunction = Some(getDomesticStandingOrdersDomesticStandingOrderId)
  )

  private val EX_getDomesticStandingOrdersDomesticStandingOrderIdPaymentDetails: String = """{
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
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val getDomesticStandingOrdersDomesticStandingOrderIdPaymentDetails: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "pisp" / "domestic-standing-orders" / domesticStandingOrderId / "payment-details" =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getDomesticStandingOrdersDomesticStandingOrderIdPaymentDetails)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getDomesticStandingOrdersDomesticStandingOrderIdPaymentDetails),
    "GET",
    "/pisp/domestic-standing-orders/DOMESTIC_STANDING_ORDER_ID/payment-details",
    "Get details of a Domestic Standing Order Payment",
    """Enables a PISP to retrieve detailed information on the status of a Domestic Standing Order payment.""",
    EmptyBody,
    parseBody(EX_getDomesticStandingOrdersDomesticStandingOrderIdPaymentDetails),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Domestic Standing Orders") :: Nil,
    http4sPartialFunction = Some(getDomesticStandingOrdersDomesticStandingOrderIdPaymentDetails)
  )

  private val EXREQ_createFilePaymentConsents: String = """{
  "Data": {
    "Initiation": {
      "FileType": "string",
      "FileHash": "string",
      "FileReference": "string",
      "NumberOfTransactions": "string",
      "ControlSum": 0,
      "RequestedExecutionDateTime": "2020-01-01T00:00:00+00:00",
      "LocalInstrument": "string",
      "CreditorAgent": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "SupplementaryData": {}
    },
    "Authorisation": {
      "AuthorisationType": "Any",
      "CompletionDateTime": "2020-01-01T00:00:00+00:00"
    },
    "SCASupportData": {
      "RequestedSCAExemptionType": "BillPayment",
      "AppliedAuthenticationApproach": "CA",
      "ReferencePaymentOrderId": "string"
    }
  }
}"""
  private val EX_createFilePaymentConsents: String = """{
  "Data": {
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
    "CutOffDateTime": "2020-01-01T00:00:00+00:00",
    "Charges": [
      {
        "ChargeBearer": "BorneByCreditor",
        "Type": "string",
        "Amount": {
          "Amount": "string",
          "Currency": "string"
        }
      }
    ],
    "Initiation": {
      "FileType": "string",
      "FileHash": "string",
      "FileReference": "string",
      "NumberOfTransactions": "string",
      "ControlSum": 0,
      "RequestedExecutionDateTime": "2020-01-01T00:00:00+00:00",
      "LocalInstrument": "string",
      "CreditorAgent": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "SupplementaryData": {}
    },
    "Authorisation": {
      "AuthorisationType": "Any",
      "CompletionDateTime": "2020-01-01T00:00:00+00:00"
    },
    "SCASupportData": {
      "RequestedSCAExemptionType": "BillPayment",
      "AppliedAuthenticationApproach": "CA",
      "ReferencePaymentOrderId": "string"
    },
    "Debtor": {
      "SchemeName": "string",
      "Identification": "string",
      "Name": "string",
      "SecondaryIdentification": "string",
      "LEI": "IZ9Q00LZEVUKWCQY6X15"
    }
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val createFilePaymentConsents: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV401Prefix` / "pisp" / "file-payment-consents" =>
      EndpointHelpers.executeFutureCreated(req)(Future.successful(parseBody(EX_createFilePaymentConsents)))
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createFilePaymentConsents),
    "POST",
    "/pisp/file-payment-consents",
    "Create a File Payment Consent",
    """Enables a PISP to register an intent to initiate a File Payment.""",
    parseBody(EXREQ_createFilePaymentConsents),
    parseBody(EX_createFilePaymentConsents),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("File Payment Consents") :: Nil,
    http4sPartialFunction = Some(createFilePaymentConsents)
  )

  private val EX_getFilePaymentConsentsConsentId: String = """{
  "Data": {
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
    "CutOffDateTime": "2020-01-01T00:00:00+00:00",
    "Charges": [
      {
        "ChargeBearer": "BorneByCreditor",
        "Type": "string",
        "Amount": {
          "Amount": "string",
          "Currency": "string"
        }
      }
    ],
    "Initiation": {
      "FileType": "string",
      "FileHash": "string",
      "FileReference": "string",
      "NumberOfTransactions": "string",
      "ControlSum": 0,
      "RequestedExecutionDateTime": "2020-01-01T00:00:00+00:00",
      "LocalInstrument": "string",
      "CreditorAgent": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "SupplementaryData": {}
    },
    "Authorisation": {
      "AuthorisationType": "Any",
      "CompletionDateTime": "2020-01-01T00:00:00+00:00"
    },
    "SCASupportData": {
      "RequestedSCAExemptionType": "BillPayment",
      "AppliedAuthenticationApproach": "CA",
      "ReferencePaymentOrderId": "string"
    },
    "Debtor": {
      "SchemeName": "string",
      "Identification": "string",
      "Name": "string",
      "SecondaryIdentification": "string",
      "LEI": "IZ9Q00LZEVUKWCQY6X15"
    }
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val getFilePaymentConsentsConsentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "pisp" / "file-payment-consents" / consentId =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getFilePaymentConsentsConsentId)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getFilePaymentConsentsConsentId),
    "GET",
    "/pisp/file-payment-consents/CONSENT_ID",
    "Get a File Payment Consent",
    """Enables a PISP to retrieve the status of an intent to initiate a File Payment.""",
    EmptyBody,
    parseBody(EX_getFilePaymentConsentsConsentId),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("File Payment Consents") :: Nil,
    http4sPartialFunction = Some(getFilePaymentConsentsConsentId)
  )

  private val EX_getFilePaymentConsentsConsentIdFile: String = """{}"""
  lazy val getFilePaymentConsentsConsentIdFile: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "pisp" / "file-payment-consents" / consentId / "file" =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getFilePaymentConsentsConsentIdFile)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getFilePaymentConsentsConsentIdFile),
    "GET",
    "/pisp/file-payment-consents/CONSENT_ID/file",
    "Get a File Payment Consent's Uploaded File",
    """Enables a PISP to download a file that has been previously uploaded.""",
    EmptyBody,
    parseBody(EX_getFilePaymentConsentsConsentIdFile),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("File Payment Consents") :: Nil,
    http4sPartialFunction = Some(getFilePaymentConsentsConsentIdFile)
  )

  private val EX_createFilePaymentConsentsConsentIdFile: String = """{}"""
  lazy val createFilePaymentConsentsConsentIdFile: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV401Prefix` / "pisp" / "file-payment-consents" / consentId / "file" =>
      EndpointHelpers.executeFutureCreated(req)(Future.successful(parseBody(EX_createFilePaymentConsentsConsentIdFile)))
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createFilePaymentConsentsConsentIdFile),
    "POST",
    "/pisp/file-payment-consents/CONSENT_ID/file",
    "Upload a File for a ConsentId",
    """Enables a PISP to upload a file of payments to the ASPSP for PSU to authenticate.""",
    EmptyBody,
    parseBody(EX_createFilePaymentConsentsConsentIdFile),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("File Payment Consents") :: Nil,
    http4sPartialFunction = Some(createFilePaymentConsentsConsentIdFile)
  )

  private val EXREQ_createFilePayments: String = """{
  "Data": {
    "ConsentId": "string",
    "Initiation": {
      "FileType": "string",
      "FileHash": "string",
      "FileReference": "string",
      "NumberOfTransactions": "string",
      "ControlSum": 0,
      "RequestedExecutionDateTime": "2020-01-01T00:00:00+00:00",
      "LocalInstrument": "string",
      "CreditorAgent": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "SupplementaryData": {}
    }
  }
}"""
  private val EX_createFilePayments: String = """{
  "Data": {
    "FilePaymentId": "string",
    "ConsentId": "string",
    "CreationDateTime": "2020-01-01T00:00:00+00:00",
    "Status": "PDNG",
    "StatusUpdateDateTime": "2020-01-01T00:00:00+00:00",
    "StatusReason": [
      {
        "StatusReasonCode": "ERIN",
        "StatusReasonDescription": "string",
        "Path": "string"
      }
    ],
    "Charges": [
      {
        "ChargeBearer": "BorneByCreditor",
        "Type": "string",
        "Amount": {
          "Amount": "string",
          "Currency": "string"
        }
      }
    ],
    "Initiation": {
      "FileType": "string",
      "FileHash": "string",
      "FileReference": "string",
      "NumberOfTransactions": "string",
      "ControlSum": 0,
      "RequestedExecutionDateTime": "2020-01-01T00:00:00+00:00",
      "LocalInstrument": "string",
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
      "CreditorAgent": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "SupplementaryData": {}
    },
    "MultiAuthorisation": {
      "Status": "AUTH",
      "NumberRequired": 0,
      "NumberReceived": 0,
      "LastUpdateDateTime": "2020-01-01T00:00:00+00:00",
      "ExpirationDateTime": "2020-01-01T00:00:00+00:00"
    },
    "Debtor": {
      "SchemeName": "string",
      "Identification": "string",
      "Name": "string",
      "SecondaryIdentification": "string",
      "LEI": "IZ9Q00LZEVUKWCQY6X15"
    }
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val createFilePayments: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV401Prefix` / "pisp" / "file-payments" =>
      EndpointHelpers.executeFutureCreated(req)(Future.successful(parseBody(EX_createFilePayments)))
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createFilePayments),
    "POST",
    "/pisp/file-payments",
    "Submit a File Payment",
    """Enables a PISP to instruct the ASPSP to start processing the payments within the file.""",
    parseBody(EXREQ_createFilePayments),
    parseBody(EX_createFilePayments),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("File Payments") :: Nil,
    http4sPartialFunction = Some(createFilePayments)
  )

  private val EX_getFilePaymentsFilePaymentId: String = """{
  "Data": {
    "FilePaymentId": "string",
    "ConsentId": "string",
    "CreationDateTime": "2020-01-01T00:00:00+00:00",
    "Status": "PDNG",
    "StatusUpdateDateTime": "2020-01-01T00:00:00+00:00",
    "StatusReason": [
      {
        "StatusReasonCode": "ERIN",
        "StatusReasonDescription": "string",
        "Path": "string"
      }
    ],
    "Charges": [
      {
        "ChargeBearer": "BorneByCreditor",
        "Type": "string",
        "Amount": {
          "Amount": "string",
          "Currency": "string"
        }
      }
    ],
    "Initiation": {
      "FileType": "string",
      "FileHash": "string",
      "FileReference": "string",
      "NumberOfTransactions": "string",
      "ControlSum": 0,
      "RequestedExecutionDateTime": "2020-01-01T00:00:00+00:00",
      "LocalInstrument": "string",
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
      "CreditorAgent": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "SupplementaryData": {}
    },
    "MultiAuthorisation": {
      "Status": "AUTH",
      "NumberRequired": 0,
      "NumberReceived": 0,
      "LastUpdateDateTime": "2020-01-01T00:00:00+00:00",
      "ExpirationDateTime": "2020-01-01T00:00:00+00:00"
    },
    "Debtor": {
      "SchemeName": "string",
      "Identification": "string",
      "Name": "string",
      "SecondaryIdentification": "string",
      "LEI": "IZ9Q00LZEVUKWCQY6X15"
    }
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val getFilePaymentsFilePaymentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "pisp" / "file-payments" / filePaymentId =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getFilePaymentsFilePaymentId)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getFilePaymentsFilePaymentId),
    "GET",
    "/pisp/file-payments/FILE_PAYMENT_ID",
    "Get a File Payment by FilePaymentId",
    """Enables a PISP to retrieve the status of a file payment.""",
    EmptyBody,
    parseBody(EX_getFilePaymentsFilePaymentId),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("File Payments") :: Nil,
    http4sPartialFunction = Some(getFilePaymentsFilePaymentId)
  )

  private val EX_getFilePaymentsFilePaymentIdPaymentDetails: String = """{
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
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val getFilePaymentsFilePaymentIdPaymentDetails: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "pisp" / "file-payments" / filePaymentId / "payment-details" =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getFilePaymentsFilePaymentIdPaymentDetails)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getFilePaymentsFilePaymentIdPaymentDetails),
    "GET",
    "/pisp/file-payments/FILE_PAYMENT_ID/payment-details",
    "Get payment details for a File Payment",
    """Enables a PISP to retrieve detailed information on the status of payments within a File.""",
    EmptyBody,
    parseBody(EX_getFilePaymentsFilePaymentIdPaymentDetails),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("File Payments") :: Nil,
    http4sPartialFunction = Some(getFilePaymentsFilePaymentIdPaymentDetails)
  )

  private val EX_getFilePaymentsFilePaymentIdReportFile: String = """{}"""
  lazy val getFilePaymentsFilePaymentIdReportFile: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "pisp" / "file-payments" / filePaymentId / "report-file" =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getFilePaymentsFilePaymentIdReportFile)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getFilePaymentsFilePaymentIdReportFile),
    "GET",
    "/pisp/file-payments/FILE_PAYMENT_ID/report-file",
    "Get a File Payment's Report File",
    """Enables a PISP to download a payment report file from an ASPSP.""",
    EmptyBody,
    parseBody(EX_getFilePaymentsFilePaymentIdReportFile),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("File Payments") :: Nil,
    http4sPartialFunction = Some(getFilePaymentsFilePaymentIdReportFile)
  )

  private val EXREQ_createInternationalPaymentConsents: String = """{
  "Data": {
    "ReadRefundAccount": "No",
    "Initiation": {
      "InstructionIdentification": "string",
      "EndToEndIdentification": "string",
      "LocalInstrument": "string",
      "InstructionPriority": "Normal",
      "ExtendedPurpose": "string",
      "ChargeBearer": "BorneByCreditor",
      "CurrencyOfTransfer": "string",
      "DestinationCountryCode": "string",
      "InstructedAmount": {
        "Amount": "string",
        "Currency": "string"
      },
      "ExchangeRateInformation": {
        "UnitCurrency": "string",
        "ExchangeRate": 0,
        "RateType": "Actual",
        "ContractIdentification": "string"
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
      },
      "Creditor": {
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
      "CreditorAgent": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
      ],
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "SupplementaryData": {}
    },
    "Authorisation": {
      "AuthorisationType": "Any",
      "CompletionDateTime": "2020-01-01T00:00:00+00:00"
    },
    "SCASupportData": {
      "RequestedSCAExemptionType": "BillPayment",
      "AppliedAuthenticationApproach": "CA",
      "ReferencePaymentOrderId": "string"
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
  private val EX_createInternationalPaymentConsents: String = """{
  "Data": {
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
    "ReadRefundAccount": "No",
    "CutOffDateTime": "2020-01-01T00:00:00+00:00",
    "ExpectedExecutionDateTime": "2020-01-01T00:00:00+00:00",
    "ExpectedSettlementDateTime": "2020-01-01T00:00:00+00:00",
    "Charges": [
      {
        "ChargeBearer": "BorneByCreditor",
        "Type": "string",
        "Amount": {
          "Amount": "string",
          "Currency": "string"
        }
      }
    ],
    "ExchangeRateInformation": {
      "UnitCurrency": "string",
      "ExchangeRate": 0,
      "RateType": "Actual",
      "ContractIdentification": "string",
      "ExpirationDateTime": "2020-01-01T00:00:00+00:00"
    },
    "Initiation": {
      "InstructionIdentification": "string",
      "EndToEndIdentification": "string",
      "LocalInstrument": "string",
      "InstructionPriority": "Normal",
      "ExtendedPurpose": "string",
      "ChargeBearer": "BorneByCreditor",
      "CurrencyOfTransfer": "string",
      "DestinationCountryCode": "string",
      "InstructedAmount": {
        "Amount": "string",
        "Currency": "string"
      },
      "ExchangeRateInformation": {
        "UnitCurrency": "string",
        "ExchangeRate": 0,
        "RateType": "Actual",
        "ContractIdentification": "string"
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
      },
      "Creditor": {
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
      "CreditorAgent": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
      ],
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "SupplementaryData": {}
    },
    "Authorisation": {
      "AuthorisationType": "Any",
      "CompletionDateTime": "2020-01-01T00:00:00+00:00"
    },
    "SCASupportData": {
      "RequestedSCAExemptionType": "BillPayment",
      "AppliedAuthenticationApproach": "CA",
      "ReferencePaymentOrderId": "string"
    },
    "Debtor": {
      "SchemeName": "string",
      "Identification": "string",
      "Name": "string",
      "SecondaryIdentification": "string",
      "LEI": "IZ9Q00LZEVUKWCQY6X15"
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
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val createInternationalPaymentConsents: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV401Prefix` / "pisp" / "international-payment-consents" =>
      EndpointHelpers.executeFutureCreated(req)(Future.successful(parseBody(EX_createInternationalPaymentConsents)))
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createInternationalPaymentConsents),
    "POST",
    "/pisp/international-payment-consents",
    "Create an International Payment Consent",
    """Enables a PISP to register an intent to initiate an International Payment.""",
    parseBody(EXREQ_createInternationalPaymentConsents),
    parseBody(EX_createInternationalPaymentConsents),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("International Payment Consents") :: Nil,
    http4sPartialFunction = Some(createInternationalPaymentConsents)
  )

  private val EX_getInternationalPaymentConsentsConsentId: String = """{
  "Data": {
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
    "ReadRefundAccount": "No",
    "CutOffDateTime": "2020-01-01T00:00:00+00:00",
    "ExpectedExecutionDateTime": "2020-01-01T00:00:00+00:00",
    "ExpectedSettlementDateTime": "2020-01-01T00:00:00+00:00",
    "Charges": [
      {
        "ChargeBearer": "BorneByCreditor",
        "Type": "string",
        "Amount": {
          "Amount": "string",
          "Currency": "string"
        }
      }
    ],
    "ExchangeRateInformation": {
      "UnitCurrency": "string",
      "ExchangeRate": 0,
      "RateType": "Actual",
      "ContractIdentification": "string",
      "ExpirationDateTime": "2020-01-01T00:00:00+00:00"
    },
    "Initiation": {
      "InstructionIdentification": "string",
      "EndToEndIdentification": "string",
      "LocalInstrument": "string",
      "InstructionPriority": "Normal",
      "ExtendedPurpose": "string",
      "ChargeBearer": "BorneByCreditor",
      "CurrencyOfTransfer": "string",
      "DestinationCountryCode": "string",
      "InstructedAmount": {
        "Amount": "string",
        "Currency": "string"
      },
      "ExchangeRateInformation": {
        "UnitCurrency": "string",
        "ExchangeRate": 0,
        "RateType": "Actual",
        "ContractIdentification": "string"
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
      },
      "Creditor": {
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
      "CreditorAgent": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
      ],
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "SupplementaryData": {}
    },
    "Authorisation": {
      "AuthorisationType": "Any",
      "CompletionDateTime": "2020-01-01T00:00:00+00:00"
    },
    "SCASupportData": {
      "RequestedSCAExemptionType": "BillPayment",
      "AppliedAuthenticationApproach": "CA",
      "ReferencePaymentOrderId": "string"
    },
    "Debtor": {
      "SchemeName": "string",
      "Identification": "string",
      "Name": "string",
      "SecondaryIdentification": "string",
      "LEI": "IZ9Q00LZEVUKWCQY6X15"
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
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val getInternationalPaymentConsentsConsentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "pisp" / "international-payment-consents" / consentId =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getInternationalPaymentConsentsConsentId)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getInternationalPaymentConsentsConsentId),
    "GET",
    "/pisp/international-payment-consents/CONSENT_ID",
    "Get an International Payment Consent",
    """Enables a PISP to retrieve the status of an intent to initiate an International Payment.""",
    EmptyBody,
    parseBody(EX_getInternationalPaymentConsentsConsentId),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("International Payment Consents") :: Nil,
    http4sPartialFunction = Some(getInternationalPaymentConsentsConsentId)
  )

  private val EX_getInternationalPaymentConsentsConsentIdFundsConfirmation: String = """{
  "Data": {
    "FundsAvailableResult": {
      "FundsAvailableDateTime": "2020-01-01T00:00:00+00:00",
      "FundsAvailable": true
    },
    "SupplementaryData": {}
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val getInternationalPaymentConsentsConsentIdFundsConfirmation: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "pisp" / "international-payment-consents" / consentId / "funds-confirmation" =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getInternationalPaymentConsentsConsentIdFundsConfirmation)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getInternationalPaymentConsentsConsentIdFundsConfirmation),
    "GET",
    "/pisp/international-payment-consents/CONSENT_ID/funds-confirmation",
    "Confirm Funds Availability for an International Payment",
    """Enables a PISP to check whether a PSU has sufficient available funds for an International Payment.""",
    EmptyBody,
    parseBody(EX_getInternationalPaymentConsentsConsentIdFundsConfirmation),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("International Payment Consents") :: Nil,
    http4sPartialFunction = Some(getInternationalPaymentConsentsConsentIdFundsConfirmation)
  )

  private val EXREQ_createInternationalPayments: String = """{
  "Data": {
    "ConsentId": "string",
    "Initiation": {
      "InstructionIdentification": "string",
      "EndToEndIdentification": "string",
      "LocalInstrument": "string",
      "InstructionPriority": "Normal",
      "ExtendedPurpose": "string",
      "ChargeBearer": "BorneByCreditor",
      "CurrencyOfTransfer": "string",
      "DestinationCountryCode": "string",
      "InstructedAmount": {
        "Amount": "string",
        "Currency": "string"
      },
      "ExchangeRateInformation": {
        "UnitCurrency": "string",
        "ExchangeRate": 0,
        "RateType": "Actual",
        "ContractIdentification": "string"
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
      },
      "Creditor": {
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
      "CreditorAgent": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
      ],
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
  private val EX_createInternationalPayments: String = """{
  "Data": {
    "InternationalPaymentId": "string",
    "ConsentId": "string",
    "CreationDateTime": "2020-01-01T00:00:00+00:00",
    "Status": "RCVD",
    "StatusUpdateDateTime": "2020-01-01T00:00:00+00:00",
    "StatusReason": [
      {
        "StatusReasonCode": "ERIN",
        "StatusReasonDescription": "string",
        "Path": "string"
      }
    ],
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
        "Type": "string",
        "Amount": {
          "Amount": "string",
          "Currency": "string"
        }
      }
    ],
    "ExchangeRateInformation": {
      "UnitCurrency": "string",
      "ExchangeRate": 0,
      "RateType": "Actual",
      "ContractIdentification": "string",
      "ExpirationDateTime": "2020-01-01T00:00:00+00:00"
    },
    "Initiation": {
      "InstructionIdentification": "string",
      "EndToEndIdentification": "string",
      "LocalInstrument": "string",
      "InstructionPriority": "Normal",
      "ExtendedPurpose": "string",
      "ChargeBearer": "BorneByCreditor",
      "CurrencyOfTransfer": "string",
      "DestinationCountryCode": "string",
      "InstructedAmount": {
        "Amount": "string",
        "Currency": "string"
      },
      "ExchangeRateInformation": {
        "UnitCurrency": "string",
        "ExchangeRate": 0,
        "RateType": "Actual",
        "ContractIdentification": "string"
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
      },
      "Creditor": {
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
      "CreditorAgent": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
      ],
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "SupplementaryData": {}
    },
    "MultiAuthorisation": {
      "Status": "AUTH",
      "NumberRequired": 0,
      "NumberReceived": 0,
      "LastUpdateDateTime": "2020-01-01T00:00:00+00:00",
      "ExpirationDateTime": "2020-01-01T00:00:00+00:00"
    },
    "Debtor": {
      "SchemeName": "string",
      "Identification": "string",
      "Name": "string",
      "SecondaryIdentification": "string",
      "LEI": "IZ9Q00LZEVUKWCQY6X15"
    }
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val createInternationalPayments: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV401Prefix` / "pisp" / "international-payments" =>
      EndpointHelpers.executeFutureCreated(req)(Future.successful(parseBody(EX_createInternationalPayments)))
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createInternationalPayments),
    "POST",
    "/pisp/international-payments",
    "Initiate an International Payment",
    """Enables a PISP to initiate an already PSU-approved International Payment.""",
    parseBody(EXREQ_createInternationalPayments),
    parseBody(EX_createInternationalPayments),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("International Payments") :: Nil,
    http4sPartialFunction = Some(createInternationalPayments)
  )

  private val EX_getInternationalPaymentsInternationalPaymentId: String = """{
  "Data": {
    "InternationalPaymentId": "string",
    "ConsentId": "string",
    "CreationDateTime": "2020-01-01T00:00:00+00:00",
    "Status": "RCVD",
    "StatusUpdateDateTime": "2020-01-01T00:00:00+00:00",
    "StatusReason": [
      {
        "StatusReasonCode": "ERIN",
        "StatusReasonDescription": "string",
        "Path": "string"
      }
    ],
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
        "Type": "string",
        "Amount": {
          "Amount": "string",
          "Currency": "string"
        }
      }
    ],
    "ExchangeRateInformation": {
      "UnitCurrency": "string",
      "ExchangeRate": 0,
      "RateType": "Actual",
      "ContractIdentification": "string",
      "ExpirationDateTime": "2020-01-01T00:00:00+00:00"
    },
    "Initiation": {
      "InstructionIdentification": "string",
      "EndToEndIdentification": "string",
      "LocalInstrument": "string",
      "InstructionPriority": "Normal",
      "ExtendedPurpose": "string",
      "ChargeBearer": "BorneByCreditor",
      "CurrencyOfTransfer": "string",
      "DestinationCountryCode": "string",
      "InstructedAmount": {
        "Amount": "string",
        "Currency": "string"
      },
      "ExchangeRateInformation": {
        "UnitCurrency": "string",
        "ExchangeRate": 0,
        "RateType": "Actual",
        "ContractIdentification": "string"
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
      },
      "Creditor": {
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
      "CreditorAgent": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
      ],
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "SupplementaryData": {}
    },
    "MultiAuthorisation": {
      "Status": "AUTH",
      "NumberRequired": 0,
      "NumberReceived": 0,
      "LastUpdateDateTime": "2020-01-01T00:00:00+00:00",
      "ExpirationDateTime": "2020-01-01T00:00:00+00:00"
    },
    "Debtor": {
      "SchemeName": "string",
      "Identification": "string",
      "Name": "string",
      "SecondaryIdentification": "string",
      "LEI": "IZ9Q00LZEVUKWCQY6X15"
    }
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val getInternationalPaymentsInternationalPaymentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "pisp" / "international-payments" / internationalPaymentId =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getInternationalPaymentsInternationalPaymentId)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getInternationalPaymentsInternationalPaymentId),
    "GET",
    "/pisp/international-payments/INTERNATIONAL_PAYMENT_ID",
    "Get an International Payment",
    """Enables a PISP to retrieve the status of an International Payment.""",
    EmptyBody,
    parseBody(EX_getInternationalPaymentsInternationalPaymentId),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("International Payments") :: Nil,
    http4sPartialFunction = Some(getInternationalPaymentsInternationalPaymentId)
  )

  private val EX_getInternationalPaymentsInternationalPaymentIdPaymentDetails: String = """{
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
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val getInternationalPaymentsInternationalPaymentIdPaymentDetails: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "pisp" / "international-payments" / internationalPaymentId / "payment-details" =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getInternationalPaymentsInternationalPaymentIdPaymentDetails)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getInternationalPaymentsInternationalPaymentIdPaymentDetails),
    "GET",
    "/pisp/international-payments/INTERNATIONAL_PAYMENT_ID/payment-details",
    "Get details of an International Payment",
    """Enables a PISP to retrieve detailed information on the status of an International Payment.""",
    EmptyBody,
    parseBody(EX_getInternationalPaymentsInternationalPaymentIdPaymentDetails),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("International Payments") :: Nil,
    http4sPartialFunction = Some(getInternationalPaymentsInternationalPaymentIdPaymentDetails)
  )

  private val EXREQ_createInternationalScheduledPaymentConsents: String = """{
  "Data": {
    "Permission": "Create",
    "ReadRefundAccount": "No",
    "Initiation": {
      "InstructionIdentification": "string",
      "EndToEndIdentification": "string",
      "LocalInstrument": "string",
      "InstructionPriority": "Normal",
      "ExtendedPurpose": "string",
      "ChargeBearer": "BorneByCreditor",
      "RequestedExecutionDateTime": "2020-01-01T00:00:00+00:00",
      "CurrencyOfTransfer": "string",
      "DestinationCountryCode": "string",
      "InstructedAmount": {
        "Amount": "string",
        "Currency": "string"
      },
      "ExchangeRateInformation": {
        "UnitCurrency": "string",
        "ExchangeRate": 0,
        "RateType": "Actual",
        "ContractIdentification": "string"
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
      },
      "Creditor": {
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
      "CreditorAgent": {
        "SchemeName": "string",
        "Identification": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
        "Name": "string",
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
      ],
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "SupplementaryData": {}
    },
    "Authorisation": {
      "AuthorisationType": "Any",
      "CompletionDateTime": "2020-01-01T00:00:00+00:00"
    },
    "SCASupportData": {
      "RequestedSCAExemptionType": "BillPayment",
      "AppliedAuthenticationApproach": "CA",
      "ReferencePaymentOrderId": "string"
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
  private val EX_createInternationalScheduledPaymentConsents: String = """{
  "Data": {
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
    "Permission": "Create",
    "ReadRefundAccount": "No",
    "CutOffDateTime": "2020-01-01T00:00:00+00:00",
    "ExpectedExecutionDateTime": "2020-01-01T00:00:00+00:00",
    "ExpectedSettlementDateTime": "2020-01-01T00:00:00+00:00",
    "Charges": [
      {
        "ChargeBearer": "BorneByCreditor",
        "Type": "string",
        "Amount": {
          "Amount": "string",
          "Currency": "string"
        }
      }
    ],
    "ExchangeRateInformation": {
      "UnitCurrency": "string",
      "ExchangeRate": 0,
      "RateType": "Actual",
      "ContractIdentification": "string",
      "ExpirationDateTime": "2020-01-01T00:00:00+00:00"
    },
    "Initiation": {
      "InstructionIdentification": "string",
      "EndToEndIdentification": "string",
      "LocalInstrument": "string",
      "InstructionPriority": "Normal",
      "ExtendedPurpose": "string",
      "ChargeBearer": "BorneByCreditor",
      "RequestedExecutionDateTime": "2020-01-01T00:00:00+00:00",
      "CurrencyOfTransfer": "string",
      "DestinationCountryCode": "string",
      "InstructedAmount": {
        "Amount": "string",
        "Currency": "string"
      },
      "ExchangeRateInformation": {
        "UnitCurrency": "string",
        "ExchangeRate": 0,
        "RateType": "Actual",
        "ContractIdentification": "string"
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
      },
      "Creditor": {
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
      "CreditorAgent": {
        "SchemeName": "string",
        "Identification": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
        "Name": "string",
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
      ],
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "SupplementaryData": {}
    },
    "Authorisation": {
      "AuthorisationType": "Any",
      "CompletionDateTime": "2020-01-01T00:00:00+00:00"
    },
    "SCASupportData": {
      "RequestedSCAExemptionType": "BillPayment",
      "AppliedAuthenticationApproach": "CA",
      "ReferencePaymentOrderId": "string"
    },
    "Debtor": {
      "SchemeName": "string",
      "Identification": "string",
      "Name": "string",
      "SecondaryIdentification": "string",
      "LEI": "IZ9Q00LZEVUKWCQY6X15"
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
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val createInternationalScheduledPaymentConsents: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV401Prefix` / "pisp" / "international-scheduled-payment-consents" =>
      EndpointHelpers.executeFutureCreated(req)(Future.successful(parseBody(EX_createInternationalScheduledPaymentConsents)))
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createInternationalScheduledPaymentConsents),
    "POST",
    "/pisp/international-scheduled-payment-consents",
    "Create an International Scheduled Payment Consent",
    """Enables a PISP to register an intent to initiate an International Scheduled Payment.""",
    parseBody(EXREQ_createInternationalScheduledPaymentConsents),
    parseBody(EX_createInternationalScheduledPaymentConsents),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("International Scheduled Payments Consents") :: Nil,
    http4sPartialFunction = Some(createInternationalScheduledPaymentConsents)
  )

  private val EX_getInternationalScheduledPaymentConsentsConsentId: String = """{
  "Data": {
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
    "Permission": "Create",
    "ReadRefundAccount": "No",
    "CutOffDateTime": "2020-01-01T00:00:00+00:00",
    "ExpectedExecutionDateTime": "2020-01-01T00:00:00+00:00",
    "ExpectedSettlementDateTime": "2020-01-01T00:00:00+00:00",
    "Charges": [
      {
        "ChargeBearer": "BorneByCreditor",
        "Type": "string",
        "Amount": {
          "Amount": "string",
          "Currency": "string"
        }
      }
    ],
    "ExchangeRateInformation": {
      "UnitCurrency": "string",
      "ExchangeRate": 0,
      "RateType": "Actual",
      "ContractIdentification": "string",
      "ExpirationDateTime": "2020-01-01T00:00:00+00:00"
    },
    "Initiation": {
      "InstructionIdentification": "string",
      "EndToEndIdentification": "string",
      "LocalInstrument": "string",
      "InstructionPriority": "Normal",
      "ExtendedPurpose": "string",
      "ChargeBearer": "BorneByCreditor",
      "RequestedExecutionDateTime": "2020-01-01T00:00:00+00:00",
      "CurrencyOfTransfer": "string",
      "DestinationCountryCode": "string",
      "InstructedAmount": {
        "Amount": "string",
        "Currency": "string"
      },
      "ExchangeRateInformation": {
        "UnitCurrency": "string",
        "ExchangeRate": 0,
        "RateType": "Actual",
        "ContractIdentification": "string"
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
      },
      "Creditor": {
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
      "CreditorAgent": {
        "SchemeName": "string",
        "Identification": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
        "Name": "string",
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
      ],
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "SupplementaryData": {}
    },
    "Authorisation": {
      "AuthorisationType": "Any",
      "CompletionDateTime": "2020-01-01T00:00:00+00:00"
    },
    "SCASupportData": {
      "RequestedSCAExemptionType": "BillPayment",
      "AppliedAuthenticationApproach": "CA",
      "ReferencePaymentOrderId": "string"
    },
    "Debtor": {
      "SchemeName": "string",
      "Identification": "string",
      "Name": "string",
      "SecondaryIdentification": "string",
      "LEI": "IZ9Q00LZEVUKWCQY6X15"
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
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val getInternationalScheduledPaymentConsentsConsentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "pisp" / "international-scheduled-payment-consents" / consentId =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getInternationalScheduledPaymentConsentsConsentId)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getInternationalScheduledPaymentConsentsConsentId),
    "GET",
    "/pisp/international-scheduled-payment-consents/CONSENT_ID",
    "Get an International Scheduled Payment Consent",
    """Enables a PISP to retrieve the status of an intent to initiate an International Scheduled Payment.""",
    EmptyBody,
    parseBody(EX_getInternationalScheduledPaymentConsentsConsentId),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("International Scheduled Payments Consents") :: Nil,
    http4sPartialFunction = Some(getInternationalScheduledPaymentConsentsConsentId)
  )

  private val EX_getInternationalScheduledPaymentConsentsConsentIdFundsConfirmation: String = """{
  "Data": {
    "FundsAvailableResult": {
      "FundsAvailableDateTime": "2020-01-01T00:00:00+00:00",
      "FundsAvailable": true
    },
    "SupplementaryData": {}
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val getInternationalScheduledPaymentConsentsConsentIdFundsConfirmation: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "pisp" / "international-scheduled-payment-consents" / consentId / "funds-confirmation" =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getInternationalScheduledPaymentConsentsConsentIdFundsConfirmation)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getInternationalScheduledPaymentConsentsConsentIdFundsConfirmation),
    "GET",
    "/pisp/international-scheduled-payment-consents/CONSENT_ID/funds-confirmation",
    "Confirm Funds Availability for an International Scheduled Payment",
    """Enables a PISP to check whether a PSU has sufficient available funds for an International Scheduled Payment.""",
    EmptyBody,
    parseBody(EX_getInternationalScheduledPaymentConsentsConsentIdFundsConfirmation),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("International Scheduled Payments Consents") :: Nil,
    http4sPartialFunction = Some(getInternationalScheduledPaymentConsentsConsentIdFundsConfirmation)
  )

  private val EXREQ_createInternationalScheduledPayments: String = """{
  "Data": {
    "ConsentId": "string",
    "Initiation": {
      "InstructionIdentification": "string",
      "EndToEndIdentification": "string",
      "LocalInstrument": "string",
      "InstructionPriority": "Normal",
      "ExtendedPurpose": "string",
      "ChargeBearer": "BorneByCreditor",
      "RequestedExecutionDateTime": "2020-01-01T00:00:00+00:00",
      "CurrencyOfTransfer": "string",
      "DestinationCountryCode": "string",
      "InstructedAmount": {
        "Amount": "string",
        "Currency": "string"
      },
      "ExchangeRateInformation": {
        "UnitCurrency": "string",
        "ExchangeRate": 0,
        "RateType": "Actual",
        "ContractIdentification": "string"
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
      },
      "Creditor": {
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
      "CreditorAgent": {
        "SchemeName": "string",
        "Identification": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
        "Name": "string",
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
      ],
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
  private val EX_createInternationalScheduledPayments: String = """{
  "Data": {
    "InternationalScheduledPaymentId": "string",
    "ConsentId": "string",
    "CreationDateTime": "2020-01-01T00:00:00+00:00",
    "Status": "CANC",
    "StatusUpdateDateTime": "2020-01-01T00:00:00+00:00",
    "StatusReason": [
      {
        "StatusReasonCode": "ERIN",
        "StatusReasonDescription": "string",
        "Path": "string"
      }
    ],
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
        "Type": "string",
        "Amount": {
          "Amount": "string",
          "Currency": "string"
        }
      }
    ],
    "ExchangeRateInformation": {
      "UnitCurrency": "string",
      "ExchangeRate": 0,
      "RateType": "Actual",
      "ContractIdentification": "string",
      "ExpirationDateTime": "2020-01-01T00:00:00+00:00"
    },
    "Initiation": {
      "InstructionIdentification": "string",
      "EndToEndIdentification": "string",
      "LocalInstrument": "string",
      "InstructionPriority": "Normal",
      "ExtendedPurpose": "string",
      "ChargeBearer": "BorneByCreditor",
      "RequestedExecutionDateTime": "2020-01-01T00:00:00+00:00",
      "CurrencyOfTransfer": "string",
      "DestinationCountryCode": "string",
      "InstructedAmount": {
        "Amount": "string",
        "Currency": "string"
      },
      "ExchangeRateInformation": {
        "UnitCurrency": "string",
        "ExchangeRate": 0,
        "RateType": "Actual",
        "ContractIdentification": "string"
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
      },
      "Creditor": {
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
      "CreditorAgent": {
        "SchemeName": "string",
        "Identification": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
        "Name": "string",
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
      ],
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "SupplementaryData": {}
    },
    "MultiAuthorisation": {
      "Status": "AUTH",
      "NumberRequired": 0,
      "NumberReceived": 0,
      "LastUpdateDateTime": "2020-01-01T00:00:00+00:00",
      "ExpirationDateTime": "2020-01-01T00:00:00+00:00"
    },
    "Debtor": {
      "SchemeName": "string",
      "Identification": "string",
      "Name": "string",
      "SecondaryIdentification": "string",
      "LEI": "IZ9Q00LZEVUKWCQY6X15"
    }
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val createInternationalScheduledPayments: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV401Prefix` / "pisp" / "international-scheduled-payments" =>
      EndpointHelpers.executeFutureCreated(req)(Future.successful(parseBody(EX_createInternationalScheduledPayments)))
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createInternationalScheduledPayments),
    "POST",
    "/pisp/international-scheduled-payments",
    "Initiate an International Scheduled Payment",
    """Enables a PISP to initiate an already PSU-approved International Scheduled Payment.""",
    parseBody(EXREQ_createInternationalScheduledPayments),
    parseBody(EX_createInternationalScheduledPayments),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("International Scheduled Payments") :: Nil,
    http4sPartialFunction = Some(createInternationalScheduledPayments)
  )

  private val EX_getInternationalScheduledPaymentsInternationalScheduledPaymentId: String = """{
  "Data": {
    "InternationalScheduledPaymentId": "string",
    "ConsentId": "string",
    "CreationDateTime": "2020-01-01T00:00:00+00:00",
    "Status": "CANC",
    "StatusUpdateDateTime": "2020-01-01T00:00:00+00:00",
    "StatusReason": [
      {
        "StatusReasonCode": "ERIN",
        "StatusReasonDescription": "string",
        "Path": "string"
      }
    ],
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
        "Type": "string",
        "Amount": {
          "Amount": "string",
          "Currency": "string"
        }
      }
    ],
    "ExchangeRateInformation": {
      "UnitCurrency": "string",
      "ExchangeRate": 0,
      "RateType": "Actual",
      "ContractIdentification": "string",
      "ExpirationDateTime": "2020-01-01T00:00:00+00:00"
    },
    "Initiation": {
      "InstructionIdentification": "string",
      "EndToEndIdentification": "string",
      "LocalInstrument": "string",
      "InstructionPriority": "Normal",
      "ExtendedPurpose": "string",
      "ChargeBearer": "BorneByCreditor",
      "RequestedExecutionDateTime": "2020-01-01T00:00:00+00:00",
      "CurrencyOfTransfer": "string",
      "DestinationCountryCode": "string",
      "InstructedAmount": {
        "Amount": "string",
        "Currency": "string"
      },
      "ExchangeRateInformation": {
        "UnitCurrency": "string",
        "ExchangeRate": 0,
        "RateType": "Actual",
        "ContractIdentification": "string"
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
      },
      "Creditor": {
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
      "CreditorAgent": {
        "SchemeName": "string",
        "Identification": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
        "Name": "string",
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
      ],
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "SupplementaryData": {}
    },
    "MultiAuthorisation": {
      "Status": "AUTH",
      "NumberRequired": 0,
      "NumberReceived": 0,
      "LastUpdateDateTime": "2020-01-01T00:00:00+00:00",
      "ExpirationDateTime": "2020-01-01T00:00:00+00:00"
    },
    "Debtor": {
      "SchemeName": "string",
      "Identification": "string",
      "Name": "string",
      "SecondaryIdentification": "string",
      "LEI": "IZ9Q00LZEVUKWCQY6X15"
    }
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val getInternationalScheduledPaymentsInternationalScheduledPaymentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "pisp" / "international-scheduled-payments" / internationalScheduledPaymentId =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getInternationalScheduledPaymentsInternationalScheduledPaymentId)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getInternationalScheduledPaymentsInternationalScheduledPaymentId),
    "GET",
    "/pisp/international-scheduled-payments/INTERNATIONAL_SCHEDULED_PAYMENT_ID",
    "Get an International Scheduled Payment",
    """Enables a PISP to retrieve the status of an International Scheduled Payment.""",
    EmptyBody,
    parseBody(EX_getInternationalScheduledPaymentsInternationalScheduledPaymentId),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("International Scheduled Payments") :: Nil,
    http4sPartialFunction = Some(getInternationalScheduledPaymentsInternationalScheduledPaymentId)
  )

  private val EX_getInternationalScheduledPaymentsInternationalScheduledPaymentIdPaymentDetails: String = """{
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
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val getInternationalScheduledPaymentsInternationalScheduledPaymentIdPaymentDetails: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "pisp" / "international-scheduled-payments" / internationalScheduledPaymentId / "payment-details" =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getInternationalScheduledPaymentsInternationalScheduledPaymentIdPaymentDetails)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getInternationalScheduledPaymentsInternationalScheduledPaymentIdPaymentDetails),
    "GET",
    "/pisp/international-scheduled-payments/INTERNATIONAL_SCHEDULED_PAYMENT_ID/payment-details",
    "Get details of an International Scheduled Payment",
    """Enables a PISP to retrieve detailed information on the status of an International Scheduled Payment.""",
    EmptyBody,
    parseBody(EX_getInternationalScheduledPaymentsInternationalScheduledPaymentIdPaymentDetails),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("International Scheduled Payments") :: Nil,
    http4sPartialFunction = Some(getInternationalScheduledPaymentsInternationalScheduledPaymentIdPaymentDetails)
  )

  private val EXREQ_createInternationalStandingOrderConsents: String = """{
  "Data": {
    "Permission": "Create",
    "ReadRefundAccount": "No",
    "Initiation": {
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "NumberOfPayments": "string",
      "ExtendedPurpose": "string",
      "ChargeBearer": "BorneByCreditor",
      "CurrencyOfTransfer": "string",
      "DestinationCountryCode": "string",
      "InstructedAmount": {
        "Amount": "string",
        "Currency": "string"
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
      },
      "Creditor": {
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
      "CreditorAgent": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
      ],
      "MandateRelatedInformation": {
        "MandateIdentification": "string",
        "Classification": "FIXE",
        "CategoryPurposeCode": "BONU",
        "FirstPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "RecurringPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "FinalPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "Frequency": {
          "Type": "ADHO",
          "CountPerPeriod": 1,
          "PointInTime": "00"
        },
        "Reason": "string"
      },
      "SupplementaryData": {}
    },
    "Authorisation": {
      "AuthorisationType": "Any",
      "CompletionDateTime": "2020-01-01T00:00:00+00:00"
    },
    "SCASupportData": {
      "RequestedSCAExemptionType": "BillPayment",
      "AppliedAuthenticationApproach": "CA",
      "ReferencePaymentOrderId": "string"
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
  private val EX_createInternationalStandingOrderConsents: String = """{
  "Data": {
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
    "Permission": "Create",
    "ReadRefundAccount": "No",
    "CutOffDateTime": "2020-01-01T00:00:00+00:00",
    "Charges": [
      {
        "ChargeBearer": "BorneByCreditor",
        "Type": "string",
        "Amount": {
          "Amount": "string",
          "Currency": "string"
        }
      }
    ],
    "Initiation": {
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "NumberOfPayments": "string",
      "ExtendedPurpose": "string",
      "ChargeBearer": "BorneByCreditor",
      "CurrencyOfTransfer": "string",
      "DestinationCountryCode": "string",
      "InstructedAmount": {
        "Amount": "string",
        "Currency": "string"
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
      },
      "Creditor": {
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
      "CreditorAgent": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
      ],
      "MandateRelatedInformation": {
        "MandateIdentification": "string",
        "Classification": "FIXE",
        "CategoryPurposeCode": "BONU",
        "FirstPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "RecurringPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "FinalPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "Frequency": {
          "Type": "ADHO",
          "CountPerPeriod": 1,
          "PointInTime": "00"
        },
        "Reason": "string"
      },
      "SupplementaryData": {}
    },
    "Authorisation": {
      "AuthorisationType": "Any",
      "CompletionDateTime": "2020-01-01T00:00:00+00:00"
    },
    "SCASupportData": {
      "RequestedSCAExemptionType": "BillPayment",
      "AppliedAuthenticationApproach": "CA",
      "ReferencePaymentOrderId": "string"
    },
    "Debtor": {
      "SchemeName": "string",
      "Identification": "string",
      "Name": "string",
      "SecondaryIdentification": "string",
      "LEI": "IZ9Q00LZEVUKWCQY6X15"
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
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val createInternationalStandingOrderConsents: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV401Prefix` / "pisp" / "international-standing-order-consents" =>
      EndpointHelpers.executeFutureCreated(req)(Future.successful(parseBody(EX_createInternationalStandingOrderConsents)))
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createInternationalStandingOrderConsents),
    "POST",
    "/pisp/international-standing-order-consents",
    "Create an International Standing Order Consent",
    """Enables a PISP to register an intent to initiate an International Standing Order arrangement.""",
    parseBody(EXREQ_createInternationalStandingOrderConsents),
    parseBody(EX_createInternationalStandingOrderConsents),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("International Standing Orders Consents") :: Nil,
    http4sPartialFunction = Some(createInternationalStandingOrderConsents)
  )

  private val EX_getInternationalStandingOrderConsentsConsentId: String = """{
  "Data": {
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
    "Permission": "Create",
    "ReadRefundAccount": "No",
    "CutOffDateTime": "2020-01-01T00:00:00+00:00",
    "Charges": [
      {
        "ChargeBearer": "BorneByCreditor",
        "Type": "string",
        "Amount": {
          "Amount": "string",
          "Currency": "string"
        }
      }
    ],
    "Initiation": {
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "NumberOfPayments": "string",
      "ExtendedPurpose": "string",
      "ChargeBearer": "BorneByCreditor",
      "CurrencyOfTransfer": "string",
      "DestinationCountryCode": "string",
      "InstructedAmount": {
        "Amount": "string",
        "Currency": "string"
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
      },
      "Creditor": {
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
      "CreditorAgent": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
      ],
      "MandateRelatedInformation": {
        "MandateIdentification": "string",
        "Classification": "FIXE",
        "CategoryPurposeCode": "BONU",
        "FirstPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "RecurringPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "FinalPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "Frequency": {
          "Type": "ADHO",
          "CountPerPeriod": 1,
          "PointInTime": "00"
        },
        "Reason": "string"
      },
      "SupplementaryData": {}
    },
    "Authorisation": {
      "AuthorisationType": "Any",
      "CompletionDateTime": "2020-01-01T00:00:00+00:00"
    },
    "SCASupportData": {
      "RequestedSCAExemptionType": "BillPayment",
      "AppliedAuthenticationApproach": "CA",
      "ReferencePaymentOrderId": "string"
    },
    "Debtor": {
      "SchemeName": "string",
      "Identification": "string",
      "Name": "string",
      "SecondaryIdentification": "string",
      "LEI": "IZ9Q00LZEVUKWCQY6X15"
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
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val getInternationalStandingOrderConsentsConsentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "pisp" / "international-standing-order-consents" / consentId =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getInternationalStandingOrderConsentsConsentId)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getInternationalStandingOrderConsentsConsentId),
    "GET",
    "/pisp/international-standing-order-consents/CONSENT_ID",
    "Get an International Standing Order Consent",
    """Enables a PISP to retrieve the status of an intent to initiate an International Standing Order arrangement.""",
    EmptyBody,
    parseBody(EX_getInternationalStandingOrderConsentsConsentId),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("International Standing Orders Consents") :: Nil,
    http4sPartialFunction = Some(getInternationalStandingOrderConsentsConsentId)
  )

  private val EXREQ_createInternationalStandingOrders: String = """{
  "Data": {
    "ConsentId": "string",
    "Initiation": {
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "NumberOfPayments": "string",
      "ExtendedPurpose": "string",
      "ChargeBearer": "BorneByCreditor",
      "CurrencyOfTransfer": "string",
      "DestinationCountryCode": "string",
      "InstructedAmount": {
        "Amount": "string",
        "Currency": "string"
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
      },
      "Creditor": {
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
      "CreditorAgent": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
      ],
      "MandateRelatedInformation": {
        "MandateIdentification": "string",
        "Classification": "FIXE",
        "CategoryPurposeCode": "BONU",
        "FirstPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "RecurringPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "FinalPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "Frequency": {
          "Type": "ADHO",
          "CountPerPeriod": 1,
          "PointInTime": "00"
        },
        "Reason": "string"
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
  private val EX_createInternationalStandingOrders: String = """{
  "Data": {
    "InternationalStandingOrderId": "string",
    "ConsentId": "string",
    "CreationDateTime": "2020-01-01T00:00:00+00:00",
    "Status": "CANC",
    "StatusUpdateDateTime": "2020-01-01T00:00:00+00:00",
    "StatusReason": [
      {
        "StatusReasonCode": "ERIN",
        "StatusReasonDescription": "string",
        "Path": "string"
      }
    ],
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
        "Type": "string",
        "Amount": {
          "Amount": "string",
          "Currency": "string"
        }
      }
    ],
    "Initiation": {
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "NumberOfPayments": "string",
      "ExtendedPurpose": "string",
      "ChargeBearer": "BorneByCreditor",
      "CurrencyOfTransfer": "string",
      "DestinationCountryCode": "string",
      "InstructedAmount": {
        "Amount": "string",
        "Currency": "string"
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
      },
      "Creditor": {
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
      "CreditorAgent": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
      ],
      "MandateRelatedInformation": {
        "MandateIdentification": "string",
        "Classification": "FIXE",
        "CategoryPurposeCode": "BONU",
        "FirstPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "RecurringPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "FinalPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "Frequency": {
          "Type": "ADHO",
          "CountPerPeriod": 1,
          "PointInTime": "00"
        },
        "Reason": "string"
      },
      "SupplementaryData": {}
    },
    "MultiAuthorisation": {
      "Status": "AUTH",
      "NumberRequired": 0,
      "NumberReceived": 0,
      "LastUpdateDateTime": "2020-01-01T00:00:00+00:00",
      "ExpirationDateTime": "2020-01-01T00:00:00+00:00"
    },
    "Debtor": {
      "SchemeName": "string",
      "Identification": "string",
      "Name": "string",
      "SecondaryIdentification": "string",
      "LEI": "IZ9Q00LZEVUKWCQY6X15"
    }
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val createInternationalStandingOrders: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV401Prefix` / "pisp" / "international-standing-orders" =>
      EndpointHelpers.executeFutureCreated(req)(Future.successful(parseBody(EX_createInternationalStandingOrders)))
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createInternationalStandingOrders),
    "POST",
    "/pisp/international-standing-orders",
    "Submit an International Standing Order",
    """Enables a PISP to submit an International Standing Order payment under an already PSU-approved International Standing Order arrangement.""",
    parseBody(EXREQ_createInternationalStandingOrders),
    parseBody(EX_createInternationalStandingOrders),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("International Standing Orders") :: Nil,
    http4sPartialFunction = Some(createInternationalStandingOrders)
  )

  private val EX_getInternationalStandingOrdersInternationalStandingOrderPaymentId: String = """{
  "Data": {
    "InternationalStandingOrderId": "string",
    "ConsentId": "string",
    "CreationDateTime": "2020-01-01T00:00:00+00:00",
    "Status": "CANC",
    "StatusUpdateDateTime": "2020-01-01T00:00:00+00:00",
    "StatusReason": [
      {
        "StatusReasonCode": "ERIN",
        "StatusReasonDescription": "string",
        "Path": "string"
      }
    ],
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
        "Type": "string",
        "Amount": {
          "Amount": "string",
          "Currency": "string"
        }
      }
    ],
    "Initiation": {
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
            "Invoicer": "80200112344562",
            "Invoicee": "80200112344562",
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
      "NumberOfPayments": "string",
      "ExtendedPurpose": "string",
      "ChargeBearer": "BorneByCreditor",
      "CurrencyOfTransfer": "string",
      "DestinationCountryCode": "string",
      "InstructedAmount": {
        "Amount": "string",
        "Currency": "string"
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
      },
      "Creditor": {
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
      "CreditorAgent": {
        "SchemeName": "string",
        "Identification": "string",
        "Name": "string",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
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
      ],
      "MandateRelatedInformation": {
        "MandateIdentification": "string",
        "Classification": "FIXE",
        "CategoryPurposeCode": "BONU",
        "FirstPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "RecurringPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "FinalPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "Frequency": {
          "Type": "ADHO",
          "CountPerPeriod": 1,
          "PointInTime": "00"
        },
        "Reason": "string"
      },
      "SupplementaryData": {}
    },
    "MultiAuthorisation": {
      "Status": "AUTH",
      "NumberRequired": 0,
      "NumberReceived": 0,
      "LastUpdateDateTime": "2020-01-01T00:00:00+00:00",
      "ExpirationDateTime": "2020-01-01T00:00:00+00:00"
    },
    "Debtor": {
      "SchemeName": "string",
      "Identification": "string",
      "Name": "string",
      "SecondaryIdentification": "string",
      "LEI": "IZ9Q00LZEVUKWCQY6X15"
    }
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val getInternationalStandingOrdersInternationalStandingOrderPaymentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "pisp" / "international-standing-orders" / internationalStandingOrderPaymentId =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getInternationalStandingOrdersInternationalStandingOrderPaymentId)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getInternationalStandingOrdersInternationalStandingOrderPaymentId),
    "GET",
    "/pisp/international-standing-orders/INTERNATIONAL_STANDING_ORDER_PAYMENT_ID",
    "Get an International Standing Order",
    """Enables a PISP to retrieve the status of an International Standing Order payment.""",
    EmptyBody,
    parseBody(EX_getInternationalStandingOrdersInternationalStandingOrderPaymentId),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("International Standing Orders") :: Nil,
    http4sPartialFunction = Some(getInternationalStandingOrdersInternationalStandingOrderPaymentId)
  )

  private val EX_getInternationalStandingOrdersInternationalStandingOrderPaymentIdPaymentDetails: String = """{
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
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val getInternationalStandingOrdersInternationalStandingOrderPaymentIdPaymentDetails: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "pisp" / "international-standing-orders" / internationalStandingOrderPaymentId / "payment-details" =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getInternationalStandingOrdersInternationalStandingOrderPaymentIdPaymentDetails)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getInternationalStandingOrdersInternationalStandingOrderPaymentIdPaymentDetails),
    "GET",
    "/pisp/international-standing-orders/INTERNATIONAL_STANDING_ORDER_PAYMENT_ID/payment-details",
    "Get details of an International Standing Order Payment",
    """Enables a PISP to retrieve detailed information on the status of an International Standing Order payment.""",
    EmptyBody,
    parseBody(EX_getInternationalStandingOrdersInternationalStandingOrderPaymentIdPaymentDetails),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("International Standing Orders") :: Nil,
    http4sPartialFunction = Some(getInternationalStandingOrdersInternationalStandingOrderPaymentIdPaymentDetails)
  )

  val routes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    createDomesticPaymentConsents(req)
      .orElse(getDomesticPaymentConsentsConsentId(req)
      .orElse(getDomesticPaymentConsentsConsentIdFundsConfirmation(req)
      .orElse(createDomesticPayments(req)
      .orElse(getDomesticPaymentsDomesticPaymentId(req)
      .orElse(getDomesticPaymentsDomesticPaymentIdPaymentDetails(req)
      .orElse(createDomesticScheduledPaymentConsents(req)
      .orElse(getDomesticScheduledPaymentConsentsConsentId(req)
      .orElse(createDomesticScheduledPayments(req)
      .orElse(getDomesticScheduledPaymentsDomesticScheduledPaymentId(req)
      .orElse(getDomesticScheduledPaymentsDomesticScheduledPaymentIdPaymentDetails(req)
      .orElse(createDomesticStandingOrderConsents(req)
      .orElse(getDomesticStandingOrderConsentsConsentId(req)
      .orElse(createDomesticStandingOrders(req)
      .orElse(getDomesticStandingOrdersDomesticStandingOrderId(req)
      .orElse(getDomesticStandingOrdersDomesticStandingOrderIdPaymentDetails(req)
      .orElse(createFilePaymentConsents(req)
      .orElse(getFilePaymentConsentsConsentId(req)
      .orElse(getFilePaymentConsentsConsentIdFile(req)
      .orElse(createFilePaymentConsentsConsentIdFile(req)
      .orElse(createFilePayments(req)
      .orElse(getFilePaymentsFilePaymentId(req)
      .orElse(getFilePaymentsFilePaymentIdPaymentDetails(req)
      .orElse(getFilePaymentsFilePaymentIdReportFile(req)
      .orElse(createInternationalPaymentConsents(req)
      .orElse(getInternationalPaymentConsentsConsentId(req)
      .orElse(getInternationalPaymentConsentsConsentIdFundsConfirmation(req)
      .orElse(createInternationalPayments(req)
      .orElse(getInternationalPaymentsInternationalPaymentId(req)
      .orElse(getInternationalPaymentsInternationalPaymentIdPaymentDetails(req)
      .orElse(createInternationalScheduledPaymentConsents(req)
      .orElse(getInternationalScheduledPaymentConsentsConsentId(req)
      .orElse(getInternationalScheduledPaymentConsentsConsentIdFundsConfirmation(req)
      .orElse(createInternationalScheduledPayments(req)
      .orElse(getInternationalScheduledPaymentsInternationalScheduledPaymentId(req)
      .orElse(getInternationalScheduledPaymentsInternationalScheduledPaymentIdPaymentDetails(req)
      .orElse(createInternationalStandingOrderConsents(req)
      .orElse(getInternationalStandingOrderConsentsConsentId(req)
      .orElse(createInternationalStandingOrders(req)
      .orElse(getInternationalStandingOrdersInternationalStandingOrderPaymentId(req)
      .orElse(getInternationalStandingOrdersInternationalStandingOrderPaymentIdPaymentDetails(req)))))))))))))))))))))))))))))))))))))))))
  }
}
