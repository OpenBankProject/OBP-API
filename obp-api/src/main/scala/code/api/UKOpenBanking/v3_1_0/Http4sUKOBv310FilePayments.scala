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

/** UK Open Banking v3.1 — FilePaymentsApi stubs migrated to http4s (NotImplemented marker, 200). */
object Http4sUKOBv310FilePayments extends MdcLoggable {
  type HttpF[A] = OptionT[IO, A]
  implicit val formats: Formats = CustomJsonFormats.formats
  val implementedInApiVersion: ScannedApiVersion = ApiVersion.ukOpenBankingV31
  val resourceDocs = ArrayBuffer[ResourceDoc]()
  private def parseBody(s: String): org.json4s.JObject = com.openbankproject.commons.util.JsonAliases.parse(s).asInstanceOf[org.json4s.JObject]
  val ukV31Prefix = Root / ApiVersion.ukOpenBankingV31.urlPrefix / ApiVersion.ukOpenBankingV31.apiShortVersion
  private val tag = ApiTag("File Payments") :: apiTagMockedData :: Nil

  // Deeper path (/{id}/file) must come before single-wildcard (/{id}) in `routes`
  lazy val createFilePaymentConsentsConsentIdFile: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV31Prefix` / "file-payment-consents" / _ / "file" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createFilePaymentConsentsConsentIdFile),
    "POST",
    "/file-payment-consents/CONSENTID/file",
    "Create File Payment Consents File",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    EmptyBody,
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(createFilePaymentConsentsConsentIdFile)
  )

  lazy val createFilePaymentConsents: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV31Prefix` / "file-payment-consents" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createFilePaymentConsents),
    "POST",
    "/file-payment-consents",
    "Create File Payment Consents",
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
    "CutOffDateTime" : "2000-01-23T04:56:07.000+00:00",
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
      "ControlSum" : 0.80082819046101150206595775671303272247314453125,
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
      "FileType" : [ "UK.OBIE.PaymentInitiation.3.1", "UK.OBIE.pain.001.001.08" ],
      "FileHash" : "FileHash",
      "NumberOfTransactions" : "NumberOfTransactions",
      "FileReference" : "FileReference",
      "RequestedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00"
    }
  }
}"""),
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(createFilePaymentConsents)
  )

  lazy val createFilePayments: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV31Prefix` / "file-payments" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createFilePayments),
    "POST",
    "/file-payments",
    "Create File Payments",
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
    "FilePaymentId" : "FilePaymentId",
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
      "ControlSum" : 0.80082819046101150206595775671303272247314453125,
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
      "FileType" : [ "UK.OBIE.PaymentInitiation.3.1", "UK.OBIE.pain.001.001.08" ],
      "FileHash" : "FileHash",
      "NumberOfTransactions" : "NumberOfTransactions",
      "FileReference" : "FileReference",
      "RequestedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00"
    },
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
    http4sPartialFunction = Some(createFilePayments)
  )

  lazy val getFilePaymentConsentsConsentIdFile: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "file-payment-consents" / _ / "file" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getFilePaymentConsentsConsentIdFile),
    "GET",
    "/file-payment-consents/CONSENTID/file",
    "Get File Payment Consents File",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    EmptyBody,
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getFilePaymentConsentsConsentIdFile)
  )

  lazy val getFilePaymentConsentsConsentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "file-payment-consents" / _ =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getFilePaymentConsentsConsentId),
    "GET",
    "/file-payment-consents/CONSENTID",
    "Get File Payment Consents",
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
    "CutOffDateTime" : "2000-01-23T04:56:07.000+00:00",
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
      "ControlSum" : 0.80082819046101150206595775671303272247314453125,
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
      "FileType" : [ "UK.OBIE.PaymentInitiation.3.1", "UK.OBIE.pain.001.001.08" ],
      "FileHash" : "FileHash",
      "NumberOfTransactions" : "NumberOfTransactions",
      "FileReference" : "FileReference",
      "RequestedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00"
    }
  }
}"""),
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getFilePaymentConsentsConsentId)
  )

  lazy val getFilePaymentsFilePaymentIdReportFile: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "file-payments" / _ / "report-file" =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getFilePaymentsFilePaymentIdReportFile),
    "GET",
    "/file-payments/FILEPAYMENTID/report-file",
    "Get File Payments Report File",
    s"""${mockedDataText(true)}""",
    EmptyBody,
    EmptyBody,
    List(AuthenticatedUserIsRequired, UnknownError),
    tag,
    http4sPartialFunction = Some(getFilePaymentsFilePaymentIdReportFile)
  )

  lazy val getFilePaymentsFilePaymentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV31Prefix` / "file-payments" / _ =>
      EndpointHelpers.withUser(req) { (_, _) => Future.successful(ErrorMessages.NotImplemented) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getFilePaymentsFilePaymentId),
    "GET",
    "/file-payments/FILEPAYMENTID",
    "Get File Payments",
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
    "FilePaymentId" : "FilePaymentId",
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
      "ControlSum" : 0.80082819046101150206595775671303272247314453125,
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
      "FileType" : [ "UK.OBIE.PaymentInitiation.3.1", "UK.OBIE.pain.001.001.08" ],
      "FileHash" : "FileHash",
      "NumberOfTransactions" : "NumberOfTransactions",
      "FileReference" : "FileReference",
      "RequestedExecutionDateTime" : "2000-01-23T04:56:07.000+00:00"
    },
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
    http4sPartialFunction = Some(getFilePaymentsFilePaymentId)
  )

  // Routes ordered deep-first: /{id}/file and /{id}/report-file before /{id}
  val routes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    createFilePaymentConsentsConsentIdFile(req)
      .orElse(createFilePaymentConsents(req))
      .orElse(createFilePayments(req))
      .orElse(getFilePaymentConsentsConsentIdFile(req))
      .orElse(getFilePaymentConsentsConsentId(req))
      .orElse(getFilePaymentsFilePaymentIdReportFile(req))
      .orElse(getFilePaymentsFilePaymentId(req))
  }
}
