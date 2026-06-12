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

/** UK Open Banking v3.1 — InternationalPaymentsApi stubs migrated to http4s (NotImplemented marker, 200). */
object Http4sUKOBv310InternationalPayments extends MdcLoggable {
  type HttpF[A] = OptionT[IO, A]
  implicit val formats: Formats = CustomJsonFormats.formats
  val implementedInApiVersion: ScannedApiVersion = ApiVersion.ukOpenBankingV31
  val resourceDocs = ArrayBuffer[ResourceDoc]()
  private def parseBody(s: String): org.json4s.JObject = com.openbankproject.commons.util.JsonAliases.parse(s).asInstanceOf[org.json4s.JObject]
  val ukV31Prefix = Root / ApiVersion.ukOpenBankingV31.urlPrefix / ApiVersion.ukOpenBankingV31.apiShortVersion
  private val tag = ApiTag("International Payments") :: apiTagMockedData :: Nil

  lazy val createInternationalPaymentConsents: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV31Prefix` / "international-payment-consents" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createInternationalPaymentConsents),
    "POST",
    "/international-payment-consents",
    "Create International Payment Consents",
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
    "ExchangeRateInformation" : {
      "ExchangeRate" : 0.80082819046101150206595775671303272247314453125,
      "ExpirationDateTime" : "2000-01-23T04:56:07.000+00:00",
      "RateType" : { },
      "UnitCurrency" : "UnitCurrency",
      "ContractIdentification" : "ContractIdentification"
    },
    "Authorisation" : {
      "CompletionDateTime" : "2000-01-23T04:56:07.000+00:00",
      "AuthorisationType" : { }
    },
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
      "DebtorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "CreditorAgent" : {
        "PostalAddress" : {
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
        },
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "EndToEndIdentification" : "EndToEndIdentification",
      "InstructionIdentification" : "InstructionIdentification",
      "CurrencyOfTransfer" : "CurrencyOfTransfer",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "Purpose" : { },
      "ChargeBearer" : { },
      "InstructionPriority" : { },
      "LocalInstrument" : [ "UK.OBIE.BACS", "UK.OBIE.BalanceTransfer", "UK.OBIE.CHAPS", "UK.OBIE.Euro1", "UK.OBIE.FPS", "UK.OBIE.Link", "UK.OBIE.MoneyTransfer", "UK.OBIE.Paym", "UK.OBIE.SEPACreditTransfer", "UK.OBIE.SEPAInstantCreditTransfer", "UK.OBIE.SWIFT", "UK.OBIE.Target2" ],
      "RemittanceInformation" : {
        "Unstructured" : "Unstructured",
        "Reference" : "Reference"
      },
      "ExchangeRateInformation" : {
        "ExchangeRate" : 6.02745618307040320615897144307382404804229736328125,
        "UnitCurrency" : "UnitCurrency",
        "ContractIdentification" : "ContractIdentification"
      },
      "Creditor" : {
        "PostalAddress" : {
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
        },
        "Name" : "Name"
      },
      "InstructedAmount" : {
        "Currency" : "Currency"
      }
    },
    "ExpectedSettlementDateTime" : "2000-01-23T04:56:07.000+00:00"
  }
}"""),
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(createInternationalPaymentConsents)
  )

  lazy val createInternationalPayments: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV31Prefix` / "international-payments" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createInternationalPayments),
    "POST",
    "/international-payments",
    "Create International Payments",
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
    "ExchangeRateInformation" : {
      "ExchangeRate" : 0.80082819046101150206595775671303272247314453125,
      "ExpirationDateTime" : "2000-01-23T04:56:07.000+00:00",
      "RateType" : { },
      "UnitCurrency" : "UnitCurrency",
      "ContractIdentification" : "ContractIdentification"
    },
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
    "InternationalPaymentId" : "InternationalPaymentId",
    "ConsentId" : "ConsentId",
    "Initiation" : {
      "SupplementaryData" : { },
      "DebtorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "CreditorAgent" : {
        "PostalAddress" : {
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
        },
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "EndToEndIdentification" : "EndToEndIdentification",
      "InstructionIdentification" : "InstructionIdentification",
      "CurrencyOfTransfer" : "CurrencyOfTransfer",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "Purpose" : { },
      "ChargeBearer" : { },
      "InstructionPriority" : { },
      "LocalInstrument" : [ "UK.OBIE.BACS", "UK.OBIE.BalanceTransfer", "UK.OBIE.CHAPS", "UK.OBIE.Euro1", "UK.OBIE.FPS", "UK.OBIE.Link", "UK.OBIE.MoneyTransfer", "UK.OBIE.Paym", "UK.OBIE.SEPACreditTransfer", "UK.OBIE.SEPAInstantCreditTransfer", "UK.OBIE.SWIFT", "UK.OBIE.Target2" ],
      "RemittanceInformation" : {
        "Unstructured" : "Unstructured",
        "Reference" : "Reference"
      },
      "ExchangeRateInformation" : {
        "ExchangeRate" : 6.02745618307040320615897144307382404804229736328125,
        "UnitCurrency" : "UnitCurrency",
        "ContractIdentification" : "ContractIdentification"
      },
      "Creditor" : {
        "PostalAddress" : {
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
        },
        "Name" : "Name"
      },
      "InstructedAmount" : {
        "Currency" : "Currency"
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
    http4sPartialFunction = Some(createInternationalPayments)
  )

  // Deeper path must come before single-wildcard path in `routes`
  lazy val getInternationalPaymentConsentsConsentIdFundsConfirmation: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "international-payment-consents" / _ / "funds-confirmation" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getInternationalPaymentConsentsConsentIdFundsConfirmation),
    "GET",
    "/international-payment-consents/CONSENTID/funds-confirmation",
    "Get International Payment Consents Funds Confirmation",
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
    "SupplementaryData" : { },
    "FundsAvailableResult" : {
      "FundsAvailableDateTime" : "2000-01-23T04:56:07.000+00:00",
      "FundsAvailable" : true
    }
  }
}"""),
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getInternationalPaymentConsentsConsentIdFundsConfirmation)
  )

  lazy val getInternationalPaymentConsentsConsentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "international-payment-consents" / _ =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getInternationalPaymentConsentsConsentId),
    "GET",
    "/international-payment-consents/CONSENTID",
    "Get International Payment Consents",
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
    "ExchangeRateInformation" : {
      "ExchangeRate" : 0.80082819046101150206595775671303272247314453125,
      "ExpirationDateTime" : "2000-01-23T04:56:07.000+00:00",
      "RateType" : { },
      "UnitCurrency" : "UnitCurrency",
      "ContractIdentification" : "ContractIdentification"
    },
    "Authorisation" : {
      "CompletionDateTime" : "2000-01-23T04:56:07.000+00:00",
      "AuthorisationType" : { }
    },
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
      "DebtorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "CreditorAgent" : {
        "PostalAddress" : {
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
        },
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "EndToEndIdentification" : "EndToEndIdentification",
      "InstructionIdentification" : "InstructionIdentification",
      "CurrencyOfTransfer" : "CurrencyOfTransfer",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "Purpose" : { },
      "ChargeBearer" : { },
      "InstructionPriority" : { },
      "LocalInstrument" : [ "UK.OBIE.BACS", "UK.OBIE.BalanceTransfer", "UK.OBIE.CHAPS", "UK.OBIE.Euro1", "UK.OBIE.FPS", "UK.OBIE.Link", "UK.OBIE.MoneyTransfer", "UK.OBIE.Paym", "UK.OBIE.SEPACreditTransfer", "UK.OBIE.SEPAInstantCreditTransfer", "UK.OBIE.SWIFT", "UK.OBIE.Target2" ],
      "RemittanceInformation" : {
        "Unstructured" : "Unstructured",
        "Reference" : "Reference"
      },
      "ExchangeRateInformation" : {
        "ExchangeRate" : 6.02745618307040320615897144307382404804229736328125,
        "UnitCurrency" : "UnitCurrency",
        "ContractIdentification" : "ContractIdentification"
      },
      "Creditor" : {
        "PostalAddress" : {
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
        },
        "Name" : "Name"
      },
      "InstructedAmount" : {
        "Currency" : "Currency"
      }
    },
    "ExpectedSettlementDateTime" : "2000-01-23T04:56:07.000+00:00"
  }
}"""),
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getInternationalPaymentConsentsConsentId)
  )

  lazy val getInternationalPaymentsInternationalPaymentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "international-payments" / _ =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getInternationalPaymentsInternationalPaymentId),
    "GET",
    "/international-payments/INTERNATIONALPAYMENTID",
    "Get International Payments",
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
    "ExchangeRateInformation" : {
      "ExchangeRate" : 0.80082819046101150206595775671303272247314453125,
      "ExpirationDateTime" : "2000-01-23T04:56:07.000+00:00",
      "RateType" : { },
      "UnitCurrency" : "UnitCurrency",
      "ContractIdentification" : "ContractIdentification"
    },
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
    "InternationalPaymentId" : "InternationalPaymentId",
    "ConsentId" : "ConsentId",
    "Initiation" : {
      "SupplementaryData" : { },
      "DebtorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "CreditorAgent" : {
        "PostalAddress" : {
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
        },
        "SchemeName" : [ "UK.OBIE.BICFI" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "EndToEndIdentification" : "EndToEndIdentification",
      "InstructionIdentification" : "InstructionIdentification",
      "CurrencyOfTransfer" : "CurrencyOfTransfer",
      "CreditorAccount" : {
        "SecondaryIdentification" : "SecondaryIdentification",
        "SchemeName" : [ "UK.OBIE.BBAN", "UK.OBIE.IBAN", "UK.OBIE.PAN", "UK.OBIE.Paym", "UK.OBIE.SortCodeAccountNumber" ],
        "Identification" : "Identification",
        "Name" : "Name"
      },
      "Purpose" : { },
      "ChargeBearer" : { },
      "InstructionPriority" : { },
      "LocalInstrument" : [ "UK.OBIE.BACS", "UK.OBIE.BalanceTransfer", "UK.OBIE.CHAPS", "UK.OBIE.Euro1", "UK.OBIE.FPS", "UK.OBIE.Link", "UK.OBIE.MoneyTransfer", "UK.OBIE.Paym", "UK.OBIE.SEPACreditTransfer", "UK.OBIE.SEPAInstantCreditTransfer", "UK.OBIE.SWIFT", "UK.OBIE.Target2" ],
      "RemittanceInformation" : {
        "Unstructured" : "Unstructured",
        "Reference" : "Reference"
      },
      "ExchangeRateInformation" : {
        "ExchangeRate" : 6.02745618307040320615897144307382404804229736328125,
        "UnitCurrency" : "UnitCurrency",
        "ContractIdentification" : "ContractIdentification"
      },
      "Creditor" : {
        "PostalAddress" : {
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
        },
        "Name" : "Name"
      },
      "InstructedAmount" : {
        "Currency" : "Currency"
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
    http4sPartialFunction = Some(getInternationalPaymentsInternationalPaymentId)
  )

  val routes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    createInternationalPaymentConsents(req)
      .orElse(createInternationalPayments(req))
      .orElse(getInternationalPaymentConsentsConsentIdFundsConfirmation(req))
      .orElse(getInternationalPaymentConsentsConsentId(req))
      .orElse(getInternationalPaymentsInternationalPaymentId(req))
  }
}
