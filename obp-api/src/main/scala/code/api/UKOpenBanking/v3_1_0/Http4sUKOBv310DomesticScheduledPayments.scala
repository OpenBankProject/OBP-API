package code.api.UKOpenBanking.v3_1_0

import org.json4s._
import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import code.api.util.APIUtil.{EmptyBody, ResourceDoc, mockedDataText}
import code.api.util.ApiTag
import code.api.util.ApiTag._
import code.api.util.CustomJsonFormats
import code.api.util.ErrorMessages
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, UnknownError}
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.util.Helper.MdcLoggable
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}
import com.openbankproject.commons.util.json
import org.json4s.Formats
import org.http4s._
import org.http4s.dsl.io._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

/** UK Open Banking v3.1 — DomesticScheduledPaymentsApi stubs migrated to http4s (NotImplemented marker, 200). */
object Http4sUKOBv310DomesticScheduledPayments extends MdcLoggable {
  type HttpF[A] = OptionT[IO, A]
  implicit val formats: Formats = CustomJsonFormats.formats
  val implementedInApiVersion: ScannedApiVersion = ApiVersion.ukOpenBankingV31
  val resourceDocs = ArrayBuffer[ResourceDoc]()
  private def parseBody(s: String): org.json4s.JObject = com.openbankproject.commons.util.JsonAliases.parse(s).asInstanceOf[org.json4s.JObject]
  val ukV31Prefix = Root / ApiVersion.ukOpenBankingV31.urlPrefix / ApiVersion.ukOpenBankingV31.apiShortVersion
  private val tag = ApiTag("Domestic Scheduled Payments") :: apiTagMockedData :: Nil

  lazy val createDomesticScheduledPaymentConsents: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV31Prefix` / "domestic-scheduled-payment-consents" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createDomesticScheduledPaymentConsents),
    "POST",
    "/domestic-scheduled-payment-consents",
    "Create Domestic Scheduled Payment Consents",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    parseBody("""{
  "Meta" : {
    "FirstAvailableDateTime" : { },
    "TotalPages" : 0
  },
  "Risk" : {
    "PaymentContextCode" : { },
    "DeliveryAddress" : {
      "StreetName" : "StreetName",
      "CountrySubDivision" : [ "CountrySubDivision", "CountrySubDivision" ],
      "AddressLine" : [ "AddressLine", "AddressLine" ],
      "BuildingNumber" : "BuildingNumber",
      "TownName" : "TownName",
      "Country" : "Country",
      "PostCode" : "PostCode"
    },
    "MerchantCategoryCode" : "MerchantCategoryCode",
    "MerchantCustomerIdentification" : "MerchantCustomerIdentification"
  },
  "Links" : {
    "Last" : "http://example.com/aeiou",
    "Prev" : "http://example.com/aeiou",
    "Next" : "http://example.com/aeiou",
    "Self" : "http://example.com/aeiou",
    "First" : "http://example.com/aeiou"
  },
  "Data" : {
    "Status" : { },
    "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExpectedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
    "CutOffDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Authorisation" : {
      "CompletionDateTime" : "2000-01-23T04:56:07.000+00:00",
      "AuthorisationType" : { }
    },
    "Permission" : { },
    "Charges" : [ {
      "Type" : [ "UK.OBIE.CHAPSOut", "UK.OBIE.BalanceTransferOut", "UK.OBIE.MoneyTransferOut" ],
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "ChargeBearer" : { }
    }, {
      "Type" : [ "UK.OBIE.CHAPSOut", "UK.OBIE.BalanceTransferOut", "UK.OBIE.MoneyTransferOut" ],
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "ChargeBearer" : { }
    } ],
    "ConsentId" : "ConsentId",
    "Initiation" : {
      "SupplementaryData" : { },
      "LocalInstrument" : [ "UK.OBIE.BACS", "UK.OBIE.BalanceTransfer", "UK.OBIE.CHAPS", "UK.OBIE.Euro1", "UK.OBIE.FPS", "UK.OBIE.Link", "UK.OBIE.MoneyTransfer", "UK.OBIE.Paym", "UK.OBIE.SEPACreditTransfer", "UK.OBIE.SEPAInstantCreditTransfer", "UK.OBIE.SWIFT", "UK.OBIE.Target2" ],
      "DebtorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "RemittanceInformation" : {
        "Unstructured" : "Unstructured",
        "Reference" : "Reference"
      },
      "EndToEndIdentification" : "EndToEndIdentification",
      "InstructionIdentification" : "InstructionIdentification",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "RequestedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
      "InstructedAmount" : {
        "Currency" : "Currency"
      },
      "CreditorPostalAddress" : {
        "StreetName" : "StreetName",
        "CountrySubDivision" : "CountrySubDivision",
        "Department" : "Department",
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : "BuildingNumber",
        "TownName" : "TownName",
        "Country" : "Country",
        "SubDepartment" : "SubDepartment",
        "AddressType" : { },
        "PostCode" : "PostCode"
      }
    },
    "ExpectedSettlementDateTime" : "2000-01-23T04:56:07.000+00:00"
  }
}"""),
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(createDomesticScheduledPaymentConsents)
  )

  lazy val createDomesticScheduledPayments: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV31Prefix` / "domestic-scheduled-payments" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createDomesticScheduledPayments),
    "POST",
    "/domestic-scheduled-payments",
    "Create Domestic Scheduled Payments",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    parseBody("""{
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
    "Status" : { },
    "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExpectedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
    "DomesticScheduledPaymentId" : "DomesticScheduledPaymentId",
    "Charges" : [ {
      "Type" : [ "UK.OBIE.CHAPSOut", "UK.OBIE.BalanceTransferOut", "UK.OBIE.MoneyTransferOut" ],
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "ChargeBearer" : { }
    }, {
      "Type" : [ "UK.OBIE.CHAPSOut", "UK.OBIE.BalanceTransferOut", "UK.OBIE.MoneyTransferOut" ],
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "ChargeBearer" : { }
    } ],
    "ConsentId" : "ConsentId",
    "Initiation" : {
      "SupplementaryData" : { },
      "LocalInstrument" : [ "UK.OBIE.BACS", "UK.OBIE.BalanceTransfer", "UK.OBIE.CHAPS", "UK.OBIE.Euro1", "UK.OBIE.FPS", "UK.OBIE.Link", "UK.OBIE.MoneyTransfer", "UK.OBIE.Paym", "UK.OBIE.SEPACreditTransfer", "UK.OBIE.SEPAInstantCreditTransfer", "UK.OBIE.SWIFT", "UK.OBIE.Target2" ],
      "DebtorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "RemittanceInformation" : {
        "Unstructured" : "Unstructured",
        "Reference" : "Reference"
      },
      "EndToEndIdentification" : "EndToEndIdentification",
      "InstructionIdentification" : "InstructionIdentification",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "RequestedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
      "InstructedAmount" : {
        "Currency" : "Currency"
      },
      "CreditorPostalAddress" : {
        "StreetName" : "StreetName",
        "CountrySubDivision" : "CountrySubDivision",
        "Department" : "Department",
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : "BuildingNumber",
        "TownName" : "TownName",
        "Country" : "Country",
        "SubDepartment" : "SubDepartment",
        "AddressType" : { },
        "PostCode" : "PostCode"
      }
    },
    "ExpectedSettlementDateTime" : "2000-01-23T04:56:07.000+00:00",
    "MultiAuthorisation" : {
      "Status" : { },
      "NumberReceived" : 6,
      "LastUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
      "ExpirationDateTime" : "2000-01-23T04:56:07.000+00:00",
      "NumberRequired" : 0
    }
  }
}"""),
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(createDomesticScheduledPayments)
  )

  lazy val getDomesticScheduledPaymentConsentsConsentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "domestic-scheduled-payment-consents" / _ =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getDomesticScheduledPaymentConsentsConsentId),
    "GET",
    "/domestic-scheduled-payment-consents/CONSENTID",
    "Get Domestic Scheduled Payment Consents",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    parseBody("""{
  "Meta" : {
    "FirstAvailableDateTime" : { },
    "TotalPages" : 0
  },
  "Risk" : {
    "PaymentContextCode" : { },
    "DeliveryAddress" : {
      "StreetName" : "StreetName",
      "CountrySubDivision" : [ "CountrySubDivision", "CountrySubDivision" ],
      "AddressLine" : [ "AddressLine", "AddressLine" ],
      "BuildingNumber" : "BuildingNumber",
      "TownName" : "TownName",
      "Country" : "Country",
      "PostCode" : "PostCode"
    },
    "MerchantCategoryCode" : "MerchantCategoryCode",
    "MerchantCustomerIdentification" : "MerchantCustomerIdentification"
  },
  "Links" : {
    "Last" : "http://example.com/aeiou",
    "Prev" : "http://example.com/aeiou",
    "Next" : "http://example.com/aeiou",
    "Self" : "http://example.com/aeiou",
    "First" : "http://example.com/aeiou"
  },
  "Data" : {
    "Status" : { },
    "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExpectedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
    "CutOffDateTime" : "2000-01-23T04:56:07.000+00:00",
    "Authorisation" : {
      "CompletionDateTime" : "2000-01-23T04:56:07.000+00:00",
      "AuthorisationType" : { }
    },
    "Permission" : { },
    "Charges" : [ {
      "Type" : [ "UK.OBIE.CHAPSOut", "UK.OBIE.BalanceTransferOut", "UK.OBIE.MoneyTransferOut" ],
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "ChargeBearer" : { }
    }, {
      "Type" : [ "UK.OBIE.CHAPSOut", "UK.OBIE.BalanceTransferOut", "UK.OBIE.MoneyTransferOut" ],
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "ChargeBearer" : { }
    } ],
    "ConsentId" : "ConsentId",
    "Initiation" : {
      "SupplementaryData" : { },
      "LocalInstrument" : [ "UK.OBIE.BACS", "UK.OBIE.BalanceTransfer", "UK.OBIE.CHAPS", "UK.OBIE.Euro1", "UK.OBIE.FPS", "UK.OBIE.Link", "UK.OBIE.MoneyTransfer", "UK.OBIE.Paym", "UK.OBIE.SEPACreditTransfer", "UK.OBIE.SEPAInstantCreditTransfer", "UK.OBIE.SWIFT", "UK.OBIE.Target2" ],
      "DebtorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "RemittanceInformation" : {
        "Unstructured" : "Unstructured",
        "Reference" : "Reference"
      },
      "EndToEndIdentification" : "EndToEndIdentification",
      "InstructionIdentification" : "InstructionIdentification",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "RequestedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
      "InstructedAmount" : {
        "Currency" : "Currency"
      },
      "CreditorPostalAddress" : {
        "StreetName" : "StreetName",
        "CountrySubDivision" : "CountrySubDivision",
        "Department" : "Department",
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : "BuildingNumber",
        "TownName" : "TownName",
        "Country" : "Country",
        "SubDepartment" : "SubDepartment",
        "AddressType" : { },
        "PostCode" : "PostCode"
      }
    },
    "ExpectedSettlementDateTime" : "2000-01-23T04:56:07.000+00:00"
  }
}"""),
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getDomesticScheduledPaymentConsentsConsentId)
  )

  lazy val getDomesticScheduledPaymentsDomesticScheduledPaymentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "domestic-scheduled-payments" / _ =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getDomesticScheduledPaymentsDomesticScheduledPaymentId),
    "GET",
    "/domestic-scheduled-payments/DOMESTICSCHEDULEDPAYMENTID",
    "Get Domestic Scheduled Payments",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    parseBody("""{
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
    "Status" : { },
    "StatusUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
    "CreationDateTime" : "2000-01-23T04:56:07.000+00:00",
    "ExpectedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
    "DomesticScheduledPaymentId" : "DomesticScheduledPaymentId",
    "Charges" : [ {
      "Type" : [ "UK.OBIE.CHAPSOut", "UK.OBIE.BalanceTransferOut", "UK.OBIE.MoneyTransferOut" ],
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "ChargeBearer" : { }
    }, {
      "Type" : [ "UK.OBIE.CHAPSOut", "UK.OBIE.BalanceTransferOut", "UK.OBIE.MoneyTransferOut" ],
      "Amount" : {
        "Amount" : { },
        "Currency" : "Currency"
      },
      "ChargeBearer" : { }
    } ],
    "ConsentId" : "ConsentId",
    "Initiation" : {
      "SupplementaryData" : { },
      "LocalInstrument" : [ "UK.OBIE.BACS", "UK.OBIE.BalanceTransfer", "UK.OBIE.CHAPS", "UK.OBIE.Euro1", "UK.OBIE.FPS", "UK.OBIE.Link", "UK.OBIE.MoneyTransfer", "UK.OBIE.Paym", "UK.OBIE.SEPACreditTransfer", "UK.OBIE.SEPAInstantCreditTransfer", "UK.OBIE.SWIFT", "UK.OBIE.Target2" ],
      "DebtorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "RemittanceInformation" : {
        "Unstructured" : "Unstructured",
        "Reference" : "Reference"
      },
      "EndToEndIdentification" : "EndToEndIdentification",
      "InstructionIdentification" : "InstructionIdentification",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "RequestedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00",
      "InstructedAmount" : {
        "Currency" : "Currency"
      },
      "CreditorPostalAddress" : {
        "StreetName" : "StreetName",
        "CountrySubDivision" : "CountrySubDivision",
        "Department" : "Department",
        "AddressLine" : [ "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine", "AddressLine" ],
        "BuildingNumber" : "BuildingNumber",
        "TownName" : "TownName",
        "Country" : "Country",
        "SubDepartment" : "SubDepartment",
        "AddressType" : { },
        "PostCode" : "PostCode"
      }
    },
    "ExpectedSettlementDateTime" : "2000-01-23T04:56:07.000+00:00",
    "MultiAuthorisation" : {
      "Status" : { },
      "NumberReceived" : 6,
      "LastUpdateDateTime" : "2000-01-23T04:56:07.000+00:00",
      "ExpirationDateTime" : "2000-01-23T04:56:07.000+00:00",
      "NumberRequired" : 0
    }
  }
}"""),
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getDomesticScheduledPaymentsDomesticScheduledPaymentId)
  )

  val routes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    createDomesticScheduledPaymentConsents(req)
      .orElse(createDomesticScheduledPayments(req))
      .orElse(getDomesticScheduledPaymentConsentsConsentId(req))
      .orElse(getDomesticScheduledPaymentsDomesticScheduledPaymentId(req))
  }
}
