package code.api.UKOpenBanking.v4_0_1

import code.api.UKOpenBanking.{UKAmounts, UKTransactionsQuery}

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import code.api.APIFailureNewStyle
import code.api.Constant
import code.api.UKOpenBanking.v3_1_0.JSONFactory_UKOpenBanking_310.ConsentPostBodyUKV310
import code.api.util.APIUtil.{EmptyBody, ResourceDoc, HTTPParam, UserOrApplication, connectorEmptyResponse, createQueriesByHttpParams, defaultBankId, fullBoxOrException, passesPsd2Aisp, unboxFull, unboxFullOrFail, parseIso8601OrDayDate}
import code.api.util.ApiTag
import code.api.util.CallContext
import code.api.util.CustomJsonFormats
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, BankNotFound, ConsentNotFound, ConsentViewNotFund, InvalidJsonFormat, InvalidUKConsentPermissions, UnknownError}
import code.api.util.http4s.Http4sRequestAttributes.{EndpointHelpers, RequestOps}
import code.api.util.newstyle.ViewNewStyle
import code.api.util.{APIUtil, Consent, ConsentJWT, JwtUtil, NewStyle}
import code.consent.Consents
import code.model.{BankAccountExtended, UserExtended}
import code.util.Helper
import code.util.Helper.MdcLoggable
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{AccountId, BankId, BankIdAccountId, TransactionAttribute, View, ViewId}
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}
import com.openbankproject.commons.util.JsonAliases
import net.liftweb.common.{Box, Full}
import org.json4s.{Formats, JObject}
import org.http4s._
import org.http4s.dsl.io._
import com.openbankproject.commons.ExecutionContext.Implicits.global

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

// AUTO-GENERATED from UK Open Banking read-write-api-specs v4.0.1 (AccountInfo).
// Spec-faithful scaffold: routes return synthesized example JSON from the
// OpenAPI schemas (the specs carry no examples). Deepen to real OBP
// connector logic per endpoint later, mirroring v3_1_0.
object Http4sUKOBv401AccountInfo extends MdcLoggable {
  type HttpF[A] = OptionT[IO, A]
  implicit val formats: Formats = CustomJsonFormats.formats
  val implementedInApiVersion: ScannedApiVersion = ApiVersion.ukOpenBankingV401
  val resourceDocs = ArrayBuffer[ResourceDoc]()
  private def parseBody(s: String): JObject = JsonAliases.parse(s).asInstanceOf[JObject]
  val ukV401Prefix = Root / ApiVersion.ukOpenBankingV401.urlPrefix / ApiVersion.ukOpenBankingV401.apiShortVersion

  private val EXREQ_createAccountAccessConsents: String = """{
  "Data": {
    "Permissions": [
      "ReadAccountsBasic"
    ],
    "ExpirationDateTime": "2020-01-01T00:00:00+00:00",
    "TransactionFromDateTime": "2020-01-01T00:00:00+00:00",
    "TransactionToDateTime": "2020-01-01T00:00:00+00:00"
  },
  "Risk": {}
}"""
  private val EX_createAccountAccessConsents: String = """{
  "Data": {
    "ConsentId": "string",
    "CreationDateTime": "2024-05-29T00:00:00Z",
    "Status": "AWAU",
    "StatusReason": [
      {
        "StatusReasonCode": "U004",
        "StatusReasonDescription": "Permissions field is missing",
        "Path": "Data.Permissions"
      }
    ],
    "StatusUpdateDateTime": "2020-01-01T00:00:00+00:00",
    "Permissions": [
      "ReadAccountsBasic"
    ],
    "ExpirationDateTime": "2020-01-01T00:00:00+00:00",
    "TransactionFromDateTime": "2020-01-01T00:00:00+00:00",
    "TransactionToDateTime": "2020-01-01T00:00:00+00:00"
  },
  "Risk": {},
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
  lazy val createAccountAccessConsents: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV401Prefix` / "aisp" / "account-access-consents" =>
      // Check auth FIRST (before body parsing) to mirror Lift's wrappedWithAuthCheck behaviour:
      // unauthenticated -> 401, invalid body -> 400.
      EndpointHelpers.executeFutureCreated(req) {
        implicit val cc: CallContext = req.callContext
        for {
          // Spec Step 2: the TPP lodges the consent via a client-credentials grant -- authenticated
          // as an app (consumer) but with no PSU yet. Require some authentication (a consumer or a
          // user) but not a PSU specifically; reject only a fully anonymous request. The PSU is
          // bound later at authorise time (mUserId stays null until then), mirroring the Berlin
          // Group native consent flow (Http4sBGv13AIS.createConsent). A request with a real user
          // (e.g. DirectLogin) still works -- createdByUser just carries it through.
          _ <- if (cc.user.isEmpty && cc.consumer.isEmpty)
                 Future.failed(new RuntimeException(AuthenticatedUserIsRequired))
               else Future.successful(())
          // A pure client-credentials token still resolves cc.user to an auto-vivified
          // pseudo-user (idGivenByProvider == the calling consumer's own client key) rather than
          // leaving it Empty -- that pseudo-user is not a PSU, so it must not become the
          // consent's owner (it would permanently block the real PSU's authorise-time
          // ConsentDoesNotMatchUser check). Only carry a genuine PSU session through.
          createdByUser = cc.user.toOption
            .filterNot(u => cc.consumer.map(_.key.get).contains(u.idGivenByProvider))
          consentJson <- Future.fromTry(scala.util.Try(
            JsonAliases.parse(cc.httpBody.getOrElse("{}")).extract[ConsentPostBodyUKV310]
          ))
          // Separate step (not inlined into the saveUKConsent call below) so a bad date string
          // fails here -> 400. NewStyle.function.tryons (not Future.fromTry) is required for
          // that: ErrorResponseConverter only special-cases APIFailureNewStyle to preserve a set
          // HTTP code -- tryons wraps failures that way, a bare Future.fromTry(Try(...)) doesn't
          // and falls through to unknownErrorToResponse, i.e. 500.
          (expirationDateTime, transactionFromDateTime, transactionToDateTime) <- NewStyle.function.tryons(
            s"$InvalidJsonFormat The Json body should have valid ISO-8601 ExpirationDateTime/TransactionFromDateTime/TransactionToDateTime values ", 400, Some(cc)) {
            (
              consentJson.Data.ExpirationDateTime.map(parseIso8601OrDayDate),
              consentJson.Data.TransactionFromDateTime.map(parseIso8601OrDayDate),
              consentJson.Data.TransactionToDateTime.map(parseIso8601OrDayDate)
            )
          }
          // The standard requires the ASPSP to refuse malformed permission combinations with 400
          // rather than create a consent that can never be exercised -- see
          // Consent.validateUKConsentPermissions for the rules and why they matter.
          _ <- Consent.validateUKConsentPermissions(consentJson.Data.Permissions) match {
            case Some(reason) =>
              Helper.booleanToFuture(s"$InvalidUKConsentPermissions$reason", 400, Some(cc))(false)
            case None => Future.successful(true)
          }
          consumerId = cc.consumer.map(_.consumerId.get)
          _ <- passesPsd2Aisp(Some(cc))
          createdConsent <- Future(Consents.consentProvider.vend.saveUKConsent(
            createdByUser,
            bankId = None,
            accountIds = None,
            consumerId = consumerId,
            permissions = consentJson.Data.Permissions,
            expirationDateTime = expirationDateTime,
            transactionFromDateTime = transactionFromDateTime,
            transactionToDateTime = transactionToDateTime,
            apiStandard = Some("UKOpenBanking"),
            apiVersion = Some("4.0.1")
          )) map { i => connectorEmptyResponse(i, Some(cc)) }
        } yield {
          JSONFactory_UKOpenBanking_401.createConsentResponseJSON(
            consentId = createdConsent.consentId,
            creationDateTime = createdConsent.creationDateTime.toString,
            status = createdConsent.status,
            statusUpdateDateTime = createdConsent.statusUpdateDateTime.toString,
            permissions = consentJson.Data.Permissions,
            expirationDateTime = consentJson.Data.ExpirationDateTime,
            transactionFromDateTime = consentJson.Data.TransactionFromDateTime,
            transactionToDateTime = consentJson.Data.TransactionToDateTime,
            selfPath = "/aisp/account-access-consents"
          )
        }
      }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createAccountAccessConsents),
    "POST",
    "/aisp/account-access-consents",
    "Create an Account Access Consent",
    """Enables an AISP to ask an ASPSP to create a new account-access-consent resource, by sending a copy of the consent to the ASPSP.""",
    parseBody(EXREQ_createAccountAccessConsents),
    parseBody(EX_createAccountAccessConsents),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Account Access Consents") :: Nil,
    // Consent lodging is a client-credentials call: the TPP is authenticated as an app, and there
    // is no PSU yet. The handler above already says so; this makes the ResourceDoc say it too.
    // Without it the doc defaults to UserOnly, which sends the middleware down anonymousAccess and
    // 401s any request that carries no user -- so the endpoint only works today because OAuth2
    // token parsing auto-vivifies a user for a client-credentials token. Matches the Berlin Group
    // twin (Http4sBGv13AIS.createConsent), which has always been UserOrApplication.
    authMode = UserOrApplication,
    http4sPartialFunction = Some(createAccountAccessConsents)
  )

  private val EX_getAccountAccessConsentsConsentId: String = """{
  "Data": {
    "ConsentId": "string",
    "CreationDateTime": "2024-05-29T00:00:00Z",
    "Status": "AWAU",
    "StatusReason": [
      {
        "StatusReasonCode": "U004",
        "StatusReasonDescription": "Permissions field is missing",
        "Path": "Data.Permissions"
      }
    ],
    "StatusUpdateDateTime": "2020-01-01T00:00:00+00:00",
    "Permissions": [
      "ReadAccountsBasic"
    ],
    "ExpirationDateTime": "2020-01-01T00:00:00+00:00",
    "TransactionFromDateTime": "2020-01-01T00:00:00+00:00",
    "TransactionToDateTime": "2020-01-01T00:00:00+00:00"
  },
  "Risk": {},
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
  lazy val getAccountAccessConsentsConsentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "aisp" / "account-access-consents" / consentId =>
      // Not withUser: the standard has the AISP poll its own consent with a client-credentials
      // token, which carries no PSU. Consent.checkUKConsentAccess decides who may read it from
      // whichever identity the session does carry.
      EndpointHelpers.executeAndRespond(req) { cc =>
        for {
          consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
            unboxFullOrFail(_, Some(cc), ConsentNotFound, 403)
          }
          _ <- Consent.assertUKConsentAccess(consent.userId, consent.consumerId, cc)
          consentViews <- Future(JwtUtil.getSignedPayloadAsJson(consent.jsonWebToken).map(
            JsonAliases.parse(_).extract[ConsentJWT].views.map(_.view_id)
          )) map { unboxFullOrFail(_, Some(cc), s"$ConsentViewNotFund ($consentId)") }
        } yield {
          JSONFactory_UKOpenBanking_401.createConsentResponseJSON(
            consentId = consent.consentId,
            creationDateTime = consent.creationDateTime.toString,
            status = consent.status,
            statusUpdateDateTime = consent.statusUpdateDateTime.toString,
            permissions = consentViews,
            expirationDateTime = Option(consent.expirationDateTime).map(_.toString),
            transactionFromDateTime = Option(consent.transactionFromDateTime).map(_.toString),
            transactionToDateTime = Option(consent.transactionToDateTime).map(_.toString),
            selfPath = s"/aisp/account-access-consents/$consentId"
          )
        }
      }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccountAccessConsentsConsentId),
    "GET",
    "/aisp/account-access-consents/CONSENT_ID",
    "Get an Account Access Consent",
    """Enables an AISP to retrieve the status of an AIS consent.""",
    EmptyBody,
    parseBody(EX_getAccountAccessConsentsConsentId),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Account Access Consents") :: Nil,
    // Same reasoning as the POST that lodges the consent: the AISP polls it with a
    // client-credentials token, so a PSU cannot be required. See Consent.checkUKConsentAccess.
    authMode = UserOrApplication,
    http4sPartialFunction = Some(getAccountAccessConsentsConsentId)
  )

  private val EX_deleteAccountAccessConsentsConsentId: String = """{}"""
  lazy val deleteAccountAccessConsentsConsentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ DELETE -> `ukV401Prefix` / "aisp" / "account-access-consents" / consentId =>
      // Not withUserDelete -- see the GET twin above.
      EndpointHelpers.executeDelete(req) { cc =>
        for {
          _ <- passesPsd2Aisp(Some(cc))
          consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
            unboxFullOrFail(_, Some(cc), ConsentNotFound, 403)
          }
          _ <- Consent.assertUKConsentAccess(consent.userId, consent.consumerId, cc)
          _ <- Future(Consents.consentProvider.vend.revoke(consentId)) map {
            i => connectorEmptyResponse(i, Some(cc))
          }
        } yield ()
      }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(deleteAccountAccessConsentsConsentId),
    "DELETE",
    "/aisp/account-access-consents/CONSENT_ID",
    "Delete an Account Access Consent",
    """Enables an AISP to inform the ASPSP that the PSU has revoked their consent.""",
    EmptyBody,
    parseBody(EX_deleteAccountAccessConsentsConsentId),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Account Access Consents") :: Nil,
    // As above: revoking is a client-credentials call in the standard's AISP flow.
    authMode = UserOrApplication,
    http4sPartialFunction = Some(deleteAccountAccessConsentsConsentId)
  )

  private val EX_getAccounts: String = """{
  "Data": {
    "Account": [
      {
        "AccountId": "22289",
        "Status": "Enabled",
        "StatusUpdateDateTime": "2020-01-01T00:00:00+00:00",
        "Currency": "string",
        "AccountCategory": "Business",
        "AccountTypeCode": "CACC",
        "Description": "string",
        "Nickname": "string",
        "OpeningDate": "2020-01-01T00:00:00+00:00",
        "MaturityDate": "2020-01-01T00:00:00+00:00",
        "SwitchStatus": "string",
        "Account": [
          {
            "SchemeName": "string",
            "Identification": "80200112344562",
            "Name": "Jane Smith",
            "LEI": "IZ9Q00LZEVUKWCQY6X15",
            "SecondaryIdentification": "87562298675897"
          }
        ],
        "StatementFrequencyAndFormat": [
          {
            "Frequency": "YEAR",
            "CommunicationMethod": "EMAL",
            "Format": "DPDF",
            "DeliveryAddress": {
              "AddressType": {},
              "Department": "Finance",
              "SubDepartment": "Payroll",
              "StreetName": {},
              "BuildingNumber": {},
              "BuildingName": {},
              "Floor": {},
              "UnitNumber": {},
              "Room": {},
              "PostBox": {},
              "TownLocationName": {},
              "DistrictName": {},
              "CareOf": {},
              "PostCode": {},
              "TownName": {},
              "CountrySubDivision": "string",
              "Country": "string",
              "AddressLine": []
            }
          }
        ],
        "Servicer": {
          "SchemeName": "UK.OBIE.BICFI",
          "Identification": "80200112344562",
          "Name": "Agent Name"
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
  lazy val getAccounts: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "aisp" / "accounts" =>
      EndpointHelpers.withUser(req) { (u, cc) =>
        val detailViewId = ViewId(Constant.SYSTEM_READ_ACCOUNTS_DETAIL_VIEW_ID)
        val basicViewId = ViewId(Constant.SYSTEM_READ_ACCOUNTS_BASIC_VIEW_ID)
        for {
          _ <- NewStyle.function.checkUKConsent(u, Some(cc))
          _ <- passesPsd2Aisp(Some(cc))
          availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u)
          (accounts, _) <- NewStyle.function.getBankAccounts(availablePrivateAccounts, Some(cc))
          (moderatedAttributes, _) <- NewStyle.function.getModeratedAccountAttributesByAccounts(
            accounts.map(a => BankIdAccountId(a.bankId, a.accountId)),
            basicViewId,
            Some(cc))
        } yield {
          val accountsWithView = accounts.flatMap { account =>
            APIUtil.checkViewAccessAndReturnView(detailViewId, BankIdAccountId(account.bankId, account.accountId), Full(u), Some(cc)).or(
              APIUtil.checkViewAccessAndReturnView(basicViewId, BankIdAccountId(account.bankId, account.accountId), Full(u), Some(cc))
            ).toOption.map(view => (account, view))
          }
          JSONFactory_UKOpenBanking_401.createAccountsListJSON(accountsWithView, moderatedAttributes)
        }
      }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccounts),
    "GET",
    "/aisp/accounts",
    "Get Accounts",
    """Enables an AISP to retrieve a list of a PSU's accounts and information about those account(s) that the PSU has consented to.""",
    EmptyBody,
    parseBody(EX_getAccounts),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Accounts") :: Nil,
    http4sPartialFunction = Some(getAccounts)
  )

  private val EX_getAccountsAccountId: String = """{
  "Data": {
    "Account": [
      {
        "AccountId": "22289",
        "Status": "Enabled",
        "StatusUpdateDateTime": "2020-01-01T00:00:00+00:00",
        "Currency": "string",
        "AccountCategory": "Business",
        "AccountTypeCode": "CACC",
        "Description": "string",
        "Nickname": "string",
        "OpeningDate": "2020-01-01T00:00:00+00:00",
        "MaturityDate": "2020-01-01T00:00:00+00:00",
        "SwitchStatus": "string",
        "Account": [
          {
            "SchemeName": "string",
            "Identification": "80200112344562",
            "Name": "Jane Smith",
            "LEI": "IZ9Q00LZEVUKWCQY6X15",
            "SecondaryIdentification": "87562298675897"
          }
        ],
        "StatementFrequencyAndFormat": [
          {
            "Frequency": "YEAR",
            "CommunicationMethod": "EMAL",
            "Format": "DPDF",
            "DeliveryAddress": {
              "AddressType": {},
              "Department": "Finance",
              "SubDepartment": "Payroll",
              "StreetName": {},
              "BuildingNumber": {},
              "BuildingName": {},
              "Floor": {},
              "UnitNumber": {},
              "Room": {},
              "PostBox": {},
              "TownLocationName": {},
              "DistrictName": {},
              "CareOf": {},
              "PostCode": {},
              "TownName": {},
              "CountrySubDivision": "string",
              "Country": "string",
              "AddressLine": []
            }
          }
        ],
        "Servicer": {
          "SchemeName": "UK.OBIE.BICFI",
          "Identification": "80200112344562",
          "Name": "Agent Name"
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
  lazy val getAccountsAccountId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "aisp" / "accounts" / accountIdStr =>
      EndpointHelpers.withUser(req) { (u, cc) =>
        val accountId = AccountId(accountIdStr)
        val detailViewId = ViewId(Constant.SYSTEM_READ_ACCOUNTS_DETAIL_VIEW_ID)
        val basicViewId = ViewId(Constant.SYSTEM_READ_ACCOUNTS_BASIC_VIEW_ID)
        for {
          _ <- NewStyle.function.checkUKConsent(u, Some(cc))
          _ <- passesPsd2Aisp(Some(cc))
          availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u) map {
            _.filter(_.accountId.value == accountId.value)
          }
          (accounts, _) <- NewStyle.function.getBankAccounts(availablePrivateAccounts, Some(cc))
          (moderatedAttributes, _) <- NewStyle.function.getModeratedAccountAttributesByAccounts(
            accounts.map(a => BankIdAccountId(a.bankId, a.accountId)),
            basicViewId,
            Some(cc))
        } yield {
          val accountsWithView = accounts.flatMap { account =>
            APIUtil.checkViewAccessAndReturnView(detailViewId, BankIdAccountId(account.bankId, account.accountId), Full(u), Some(cc)).or(
              APIUtil.checkViewAccessAndReturnView(basicViewId, BankIdAccountId(account.bankId, account.accountId), Full(u), Some(cc))
            ).toOption.map(view => (account, view))
          }
          JSONFactory_UKOpenBanking_401.createAccountsListJSON(accountsWithView, moderatedAttributes)
        }
      }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccountsAccountId),
    "GET",
    "/aisp/accounts/ACCOUNT_ID",
    "Get an Account by AccountId",
    """Enables an AISP to retrieve information about a specific PSU account.""",
    EmptyBody,
    parseBody(EX_getAccountsAccountId),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Accounts") :: Nil,
    http4sPartialFunction = Some(getAccountsAccountId)
  )

  private val EX_getAccountsAccountIdBalances: String = """{
  "Data": {
    "Balance": [
      {
        "AccountId": "22289",
        "CreditDebitIndicator": "Credit",
        "Type": "CLAV",
        "DateTime": "2020-01-01T00:00:00+00:00",
        "Amount": {
          "Amount": "1209.06",
          "Currency": "GBP",
          "SubType": "BCUR"
        },
        "CreditLine": [
          {
            "Included": true,
            "Type": "Available",
            "Amount": {
              "Amount": {},
              "Currency": {}
            }
          }
        ],
        "LocalAmount": {
          "Amount": "1209.06",
          "Currency": "GBP",
          "SubType": "BCUR"
        }
      }
    ],
    "TotalValue": {
      "Amount": "1209.06",
      "Currency": "GBP"
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
  lazy val getAccountsAccountIdBalances: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "aisp" / "accounts" / accountIdStr / "balances" =>
      EndpointHelpers.withUser(req) { (u, cc) =>
        val accountId = AccountId(accountIdStr)
        val viewId = ViewId(Constant.SYSTEM_READ_BALANCES_VIEW_ID)
        for {
          _ <- NewStyle.function.checkUKConsent(u, Some(cc))
          _ <- passesPsd2Aisp(Some(cc))
          (account, _) <- NewStyle.function.getBankAccountByAccountId(accountId, Some(cc))
          view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(account.bankId, accountId), Full(u), Some(cc))
          moderatedAccount <- NewStyle.function.moderatedBankAccountCore(account, view, Full(u), Some(cc))
        } yield JSONFactory_UKOpenBanking_401.createAccountBalanceJSON(moderatedAccount)
      }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccountsAccountIdBalances),
    "GET",
    "/aisp/accounts/ACCOUNT_ID/balances",
    "Get Balances for an AccountId",
    """Enables an AISP to retrieve account balance information for a specific PSU account.""",
    EmptyBody,
    parseBody(EX_getAccountsAccountIdBalances),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Balances") :: Nil,
    http4sPartialFunction = Some(getAccountsAccountIdBalances)
  )

  private val EX_getAccountsAccountIdBeneficiaries: String = """{
  "Data": {
    "Beneficiary": [
      {
        "AccountId": "22289",
        "BeneficiaryId": "Ben1",
        "BeneficiaryType": "Ordinary",
        "Reference": "Towbar Club",
        "SupplementaryData": {},
        "CreditorAgent": {
          "SchemeName": "UK.OBIE.BICFI",
          "Identification": "80200112344562",
          "Name": "Agent Name",
          "PostalAddress": {
            "AddressType": "BIZZ",
            "Department": "Finance",
            "SubDepartment": "Payroll",
            "StreetName": "Bank Street",
            "BuildingNumber": "11",
            "BuildingName": "string",
            "Floor": "11",
            "UnitNumber": "A88",
            "Room": "Basement 03",
            "PostBox": "PO Box 123456",
            "TownLocationName": "London",
            "DistrictName": "Greater London",
            "CareOf": "Jane Smith",
            "PostCode": "EC2N 4AG",
            "TownName": "London",
            "CountrySubDivision": "string",
            "Country": "string",
            "AddressLine": [
              "string"
            ]
          },
          "LEI": "IZ9Q00LZEVUKWCQY6X15"
        },
        "CreditorAccount": {
          "SchemeName": "string",
          "Identification": "80200112344562",
          "Name": "Jane Smith",
          "SecondaryIdentification": "87562298675897",
          "Proxy": {
            "Identification": "2360549017905188",
            "Code": "TELE",
            "Type": "string"
          }
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
  lazy val getAccountsAccountIdBeneficiaries: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "aisp" / "accounts" / accountId / "beneficiaries" =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getAccountsAccountIdBeneficiaries)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccountsAccountIdBeneficiaries),
    "GET",
    "/aisp/accounts/ACCOUNT_ID/beneficiaries",
    "Get Beneficiaries for an AccountId",
    """Enables an AISP to retrieve Beneficiary information for a specific PSU account.""",
    EmptyBody,
    parseBody(EX_getAccountsAccountIdBeneficiaries),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Beneficiaries") :: Nil,
    http4sPartialFunction = Some(getAccountsAccountIdBeneficiaries)
  )

  private val EX_getAccountsAccountIdDirectDebits: String = """{
  "Data": {
    "DirectDebit": [
      {
        "AccountId": "22289",
        "DirectDebitId": "string",
        "DirectDebitStatusCode": "ACTV",
        "MandateRelatedInformation": {
          "MandateIdentification": "Golfers",
          "Classification": "FIXE",
          "CategoryPurposeCode": "BONU",
          "FirstPaymentDateTime": "2024-04-25T12:46:49.425Z",
          "RecurringPaymentDateTime": "2024-04-25T12:46:49.425Z",
          "FinalPaymentDateTime": "2024-04-25T12:46:49.425Z",
          "Frequency": {
            "Type": "MNTH",
            "CountPerPeriod": 1,
            "PointInTime": "00"
          },
          "Reason": "To pay monthly membership"
        },
        "Name": "string",
        "PreviousPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "PreviousPaymentAmount": {
          "Amount": "1209.06",
          "Currency": "GBP"
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
  lazy val getAccountsAccountIdDirectDebits: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "aisp" / "accounts" / accountId / "direct-debits" =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getAccountsAccountIdDirectDebits)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccountsAccountIdDirectDebits),
    "GET",
    "/aisp/accounts/ACCOUNT_ID/direct-debits",
    "Get Direct Debits for an AccountId",
    """Enables an AISP to retrieve Direct Debit information for a specific PSU account.""",
    EmptyBody,
    parseBody(EX_getAccountsAccountIdDirectDebits),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Direct Debits") :: Nil,
    http4sPartialFunction = Some(getAccountsAccountIdDirectDebits)
  )

  private val EX_getAccountsAccountIdOffers: String = """{
  "Data": {
    "Offer": [
      {
        "AccountId": "22289",
        "OfferId": "Offer1",
        "OfferType": "LimitIncrease",
        "Description": "Credit limit increase for the account up to £10000.00",
        "StartDateTime": "2024-05-29T00:00:00Z",
        "EndDateTime": "2024-06-29T00:00:00Z",
        "Rate": "100.00",
        "Value": 10,
        "Term": "Starting first of the month and ending at the end of year",
        "URL": "http://modelbank.com/offer/offer1",
        "Amount": {
          "Amount": "1209.06",
          "Currency": "GBP"
        },
        "Fee": {
          "Amount": "1209.06",
          "Currency": "GBP"
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
  lazy val getAccountsAccountIdOffers: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "aisp" / "accounts" / accountId / "offers" =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getAccountsAccountIdOffers)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccountsAccountIdOffers),
    "GET",
    "/aisp/accounts/ACCOUNT_ID/offers",
    "Get Offers for an AccountId",
    """Enables an AISP to retrieve any offer information for a specific PSU account.""",
    EmptyBody,
    parseBody(EX_getAccountsAccountIdOffers),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Offers") :: Nil,
    http4sPartialFunction = Some(getAccountsAccountIdOffers)
  )

  private val EX_getAccountsAccountIdParties: String = """{
  "Data": {
    "Party": [
      {
        "PartyId": "PXSIF023",
        "PartyNumber": "20202002",
        "PartyType": "Joint",
        "Name": "Mx Jane Smith",
        "FullLegalName": "Jane Smith",
        "LegalStructure": "UK.OBIE.Individual",
        "LEI": "IZ9Q00LZEVUKWCQY6X15",
        "BeneficialOwnership": true,
        "AccountRole": "string",
        "EmailAddress": "d.user@semiotec.co.jp",
        "Phone": "+44-2079460000",
        "Mobile": "+44-7700900000",
        "Relationships": {
          "Account": {
            "Related": "https://api.alphabank.com/open-banking/v4.0/aisp/accounts/89019",
            "Id": "89019"
          }
        },
        "Address": [
          {
            "AddressType": "BIZZ",
            "Department": "Finance",
            "SubDepartment": "Payroll",
            "StreetName": "Bank Street",
            "BuildingNumber": "11",
            "BuildingName": "string",
            "Floor": "11",
            "UnitNumber": "A88",
            "Room": "Basement 03",
            "PostBox": "PO Box 123456",
            "TownLocationName": "London",
            "DistrictName": "Greater London",
            "CareOf": "Jane Smith",
            "PostCode": "EC2N 4AG",
            "TownName": "London",
            "CountrySubDivision": "string",
            "Country": "string",
            "AddressLine": [
              "string"
            ]
          }
        ]
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
  lazy val getAccountsAccountIdParties: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "aisp" / "accounts" / accountId / "parties" =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getAccountsAccountIdParties)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccountsAccountIdParties),
    "GET",
    "/aisp/accounts/ACCOUNT_ID/parties",
    "Get Parties for an AccountId",
    """Enables an AISP to retrieve details about the PSU account-holder(s)/operator(s).""",
    EmptyBody,
    parseBody(EX_getAccountsAccountIdParties),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Parties") :: Nil,
    http4sPartialFunction = Some(getAccountsAccountIdParties)
  )

  private val EX_getAccountsAccountIdParty: String = """{
  "Data": {
    "Party": {
      "PartyId": "PXSIF023",
      "PartyNumber": "20202002",
      "PartyType": "Joint",
      "Name": "Mx Jane Smith",
      "FullLegalName": "Jane Smith",
      "LegalStructure": "UK.OBIE.Individual",
      "LEI": "IZ9Q00LZEVUKWCQY6X15",
      "BeneficialOwnership": true,
      "AccountRole": "string",
      "EmailAddress": "d.user@semiotec.co.jp",
      "Phone": "+44-2079460000",
      "Mobile": "+44-7700900000",
      "Relationships": {
        "Account": {
          "Related": "https://api.alphabank.com/open-banking/v4.0/aisp/accounts/89019",
          "Id": "89019"
        }
      },
      "Address": [
        {
          "AddressType": "BIZZ",
          "Department": "Finance",
          "SubDepartment": "Payroll",
          "StreetName": "Bank Street",
          "BuildingNumber": "11",
          "BuildingName": "string",
          "Floor": "11",
          "UnitNumber": "A88",
          "Room": "Basement 03",
          "PostBox": "PO Box 123456",
          "TownLocationName": "London",
          "DistrictName": "Greater London",
          "CareOf": "Jane Smith",
          "PostCode": "EC2N 4AG",
          "TownName": "London",
          "CountrySubDivision": "string",
          "Country": "string",
          "AddressLine": [
            "string"
          ]
        }
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
  lazy val getAccountsAccountIdParty: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "aisp" / "accounts" / accountId / "party" =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getAccountsAccountIdParty)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccountsAccountIdParty),
    "GET",
    "/aisp/accounts/ACCOUNT_ID/party",
    "Get Party for an AccountId",
    """Enables an AISP to retrieve details about the party that gave permission to the AISP to view a specific PSU account.""",
    EmptyBody,
    parseBody(EX_getAccountsAccountIdParty),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Parties") :: Nil,
    http4sPartialFunction = Some(getAccountsAccountIdParty)
  )

  private val EX_getAccountsAccountIdProduct: String = """{
  "Data": {
    "Product": [
      {
        "ProductName": "321 Product",
        "ProductId": "51B",
        "AccountId": "22289",
        "SecondaryProductId": "CA78",
        "ProductType": "PersonalCurrentAccount",
        "MarketingStateId": "22878123",
        "OtherProductType": {
          "Name": "e-Wallet",
          "Description": "Virtual wallet",
          "ProductDetails": {
            "Segment": [
              "GEAS"
            ],
            "FeeFreeLength": 0,
            "FeeFreeLengthPeriod": "PACT",
            "MonthlyMaximumCharge": "string",
            "Notes": [
              "string"
            ],
            "OtherSegment": {
              "Code": {},
              "Name": {},
              "Description": {}
            }
          },
          "CreditInterest": {
            "TierBandSet": [
              {}
            ]
          },
          "Overdraft": {
            "Notes": [
              "string"
            ],
            "OverdraftTierBandSet": [
              {}
            ]
          },
          "LoanInterest": {
            "Notes": [
              "string"
            ],
            "LoanInterestTierBandSet": [
              {}
            ]
          },
          "Repayment": {
            "RepaymentType": "USBA",
            "RepaymentFrequency": "SMDA",
            "AmountType": "RABD",
            "Notes": [
              "string"
            ],
            "OtherRepaymentType": {
              "Code": {},
              "Name": {},
              "Description": {}
            },
            "OtherRepaymentFrequency": {
              "Code": {},
              "Name": {},
              "Description": {}
            },
            "OtherAmountType": {
              "Code": {},
              "Name": {},
              "Description": {}
            },
            "RepaymentFeeCharges": {
              "RepaymentFeeChargeDetail": [],
              "RepaymentFeeChargeCap": []
            },
            "RepaymentHoliday": [
              {}
            ]
          },
          "OtherFeesCharges": [
            {
              "TariffType": "TTEL",
              "TariffName": "string",
              "OtherTariffType": {},
              "FeeChargeDetail": [],
              "FeeChargeCap": []
            }
          ],
          "SupplementaryData": {}
        },
        "BCA": {
          "ProductDetails": {
            "Segment": [
              "ClientAccount"
            ],
            "FeeFreeLength": 0,
            "FeeFreeLengthPeriod": "Day",
            "Notes": [
              "string"
            ]
          },
          "CreditInterest": {
            "TierBandSet": [
              {}
            ]
          },
          "Overdraft": {
            "Notes": [
              "string"
            ],
            "OverdraftTierBandSet": [
              {}
            ]
          },
          "OtherFeesCharges": [
            {
              "TariffType": "Electronic",
              "TariffName": "TariffName",
              "OtherTariffType": {},
              "FeeChargeDetail": [],
              "FeeChargeCap": []
            }
          ]
        },
        "PCA": {
          "ProductDetails": {
            "Segment": [
              "Basic"
            ],
            "MonthlyMaximumCharge": "MonthlyMaximumCharge",
            "Notes": [
              "string"
            ]
          },
          "CreditInterest": {
            "TierBandSet": [
              {}
            ]
          },
          "Overdraft": {
            "Notes": [
              "string"
            ],
            "OverdraftTierBandSet": [
              {}
            ]
          },
          "OtherFeesCharges": {
            "FeeChargeDetail": [
              {}
            ],
            "FeeChargeCap": [
              {}
            ]
          }
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
  lazy val getAccountsAccountIdProduct: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "aisp" / "accounts" / accountId / "product" =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getAccountsAccountIdProduct)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccountsAccountIdProduct),
    "GET",
    "/aisp/accounts/ACCOUNT_ID/product",
    "Get Product for an AccountId",
    """Enables an AISP to retrieve the account product information for a specific PSU account.""",
    EmptyBody,
    parseBody(EX_getAccountsAccountIdProduct),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Products") :: Nil,
    http4sPartialFunction = Some(getAccountsAccountIdProduct)
  )

  private val EX_getAccountsAccountIdScheduledPayments: String = """{
  "Data": {
    "ScheduledPayment": [
      {
        "AccountId": "22289",
        "ScheduledPaymentId": "SP03",
        "ScheduledPaymentDateTime": "2017-07-12T00:00:00+00:00",
        "ScheduledType": "Arrival",
        "Reference": "Towbar Club",
        "DebtorReference": "REF51561806",
        "InstructedAmount": {
          "Amount": "1209.06",
          "Currency": "GBP"
        },
        "CreditorAgent": {
          "SchemeName": "UK.OBIE.BICFI",
          "Identification": "80200112344562",
          "Name": "Agent Name",
          "PostalAddress": {
            "AddressType": "BIZZ",
            "Department": "Finance",
            "SubDepartment": "Payroll",
            "StreetName": "Bank Street",
            "BuildingNumber": "11",
            "BuildingName": "string",
            "Floor": "11",
            "UnitNumber": "A88",
            "Room": "Basement 03",
            "PostBox": "PO Box 123456",
            "TownLocationName": "London",
            "DistrictName": "Greater London",
            "CareOf": "Jane Smith",
            "PostCode": "EC2N 4AG",
            "TownName": "London",
            "CountrySubDivision": "string",
            "Country": "string",
            "AddressLine": [
              "string"
            ]
          },
          "LEI": "IZ9Q00LZEVUKWCQY6X15"
        },
        "CreditorAccount": {
          "SchemeName": "string",
          "Identification": "string",
          "Name": "Jane Smith",
          "SecondaryIdentification": "87562298675897",
          "Proxy": {
            "Identification": "2360549017905188",
            "Code": "TELE",
            "Type": "string"
          }
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
  lazy val getAccountsAccountIdScheduledPayments: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "aisp" / "accounts" / accountId / "scheduled-payments" =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getAccountsAccountIdScheduledPayments)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccountsAccountIdScheduledPayments),
    "GET",
    "/aisp/accounts/ACCOUNT_ID/scheduled-payments",
    "Get Scheduled Payments for an AccountId",
    """Enables an AISP to retrieve Scheduled Payment information for a specific PSU account.""",
    EmptyBody,
    parseBody(EX_getAccountsAccountIdScheduledPayments),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Scheduled Payments") :: Nil,
    http4sPartialFunction = Some(getAccountsAccountIdScheduledPayments)
  )

  private val EX_getAccountsAccountIdStandingOrders: String = """{
  "Data": {
    "StandingOrder": [
      {
        "AccountId": "22289",
        "StandingOrderId": "Ben5",
        "NextPaymentDateTime": "2017-07-12T00:00:00+00:00",
        "LastPaymentDateTime": "2017-07-12T00:00:00+00:00",
        "NumberOfPayments": "string",
        "StandingOrderStatusCode": "ACTV",
        "FirstPaymentAmount": {
          "Amount": "1209.06",
          "Currency": "GBP"
        },
        "NextPaymentAmount": {
          "Amount": "1209.06",
          "Currency": "GBP"
        },
        "LastPaymentAmount": {
          "Amount": "1209.06",
          "Currency": "GBP"
        },
        "FinalPaymentAmount": {
          "Amount": "1209.06",
          "Currency": "GBP"
        },
        "CreditorAgent": {
          "SchemeName": "UK.OBIE.BICFI",
          "Identification": "80200112344562",
          "Name": "Agent Name",
          "PostalAddress": {
            "AddressType": "BIZZ",
            "Department": "Finance",
            "SubDepartment": "Payroll",
            "StreetName": "Bank Street",
            "BuildingNumber": "11",
            "BuildingName": "string",
            "Floor": "11",
            "UnitNumber": "A88",
            "Room": "Basement 03",
            "PostBox": "PO Box 123456",
            "TownLocationName": "London",
            "DistrictName": "Greater London",
            "CareOf": "Jane Smith",
            "PostCode": "EC2N 4AG",
            "TownName": "London",
            "CountrySubDivision": "string",
            "Country": "string",
            "AddressLine": [
              "string"
            ]
          },
          "LEI": "IZ9Q00LZEVUKWCQY6X15"
        },
        "CreditorAccount": {
          "SchemeName": "string",
          "Identification": "string",
          "Name": "Jane Smith",
          "SecondaryIdentification": "87562298675897",
          "Proxy": {
            "Identification": "2360549017905188",
            "Code": "TELE",
            "Type": "string"
          }
        },
        "SupplementaryData": {},
        "MandateRelatedInformation": {
          "MandateIdentification": "Golfers",
          "Classification": "FIXE",
          "CategoryPurposeCode": "BONU",
          "FirstPaymentDateTime": "2024-04-25T12:46:49.425Z",
          "RecurringPaymentDateTime": "2024-04-25T12:46:49.425Z",
          "FinalPaymentDateTime": "2024-04-25T12:46:49.425Z",
          "Frequency": {
            "Type": "MNTH",
            "CountPerPeriod": 1,
            "PointInTime": "00"
          },
          "Reason": "To pay monthly membership"
        },
        "RemittanceInformation": {
          "Structured": [
            {
              "ReferredDocumentInformation": [],
              "ReferredDocumentAmount": {},
              "CreditorReferenceInformation": {},
              "Invoicer": {},
              "Invoicee": {},
              "TaxRemittance": "string",
              "AdditionalRemittanceInformation": []
            }
          ],
          "Unstructured": [
            "string"
          ]
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
  lazy val getAccountsAccountIdStandingOrders: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "aisp" / "accounts" / accountId / "standing-orders" =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getAccountsAccountIdStandingOrders)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccountsAccountIdStandingOrders),
    "GET",
    "/aisp/accounts/ACCOUNT_ID/standing-orders",
    "Get Standing Orders for an AccountId",
    """Enables an AISP to retrieve Standing Order information for a specific PSU account.""",
    EmptyBody,
    parseBody(EX_getAccountsAccountIdStandingOrders),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Standing Orders") :: Nil,
    http4sPartialFunction = Some(getAccountsAccountIdStandingOrders)
  )

  private val EX_getAccountsAccountIdStatements: String = """{
  "Data": {
    "Statement": [
      {
        "AccountId": "22289",
        "StatementId": "8sfhke-sifhkeuf-97813",
        "StatementReference": "002",
        "Type": "RegularPeriodic",
        "StartDateTime": "2017-07-12T00:00:00+00:00",
        "EndDateTime": "2017-07-12T00:00:00+00:00",
        "CreationDateTime": "2024-05-29T00:00:00Z",
        "StatementDescription": [
          "August 2017 Statement"
        ],
        "StatementBenefit": [
          {
            "Type": "UK.OBIE.Cashback",
            "Amount": {
              "Amount": {},
              "Currency": {}
            }
          }
        ],
        "StatementFee": [
          {
            "Description": "International usage charge",
            "CreditDebitIndicator": "Credit",
            "Type": "UK.OBIE.Annual",
            "Rate": 0.05,
            "RateType": "UK.OBIE.AER",
            "Frequency": "UK.OBIE.StatementMonthly",
            "Amount": {
              "Amount": {},
              "Currency": {}
            }
          }
        ],
        "StatementInterest": [
          {
            "Description": "Interest occurred over statement duration",
            "CreditDebitIndicator": "Credit",
            "Type": "UK.OBIE.Total",
            "Rate": 0.05,
            "RateType": "UK.OBIE.FixedRate",
            "Frequency": "UK.OBIE.Monthly",
            "Amount": {
              "Amount": {},
              "Currency": {}
            }
          }
        ],
        "StatementAmount": [
          {
            "CreditDebitIndicator": "Credit",
            "Type": "UK.OBIE.CreditLimit",
            "Amount": {
              "Amount": {},
              "Currency": {},
              "SubType": "BCUR"
            },
            "LocalAmount": {
              "Amount": {},
              "Currency": {},
              "SubType": "BCUR"
            }
          }
        ],
        "StatementDateTime": [
          {
            "DateTime": "2024-05-29T00:00:00Z",
            "Type": "UK.OBIE.NextStatement"
          }
        ],
        "StatementRate": [
          {
            "Rate": "0.224",
            "Type": "UK.OBIE.AnnualCash"
          }
        ],
        "StatementValue": [
          {
            "Value": "string",
            "Type": "UK.OBIE.Credit"
          }
        ],
        "TotalValue": {
          "Amount": "1209.06",
          "Currency": "GBP"
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
  lazy val getAccountsAccountIdStatements: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "aisp" / "accounts" / accountId / "statements" =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getAccountsAccountIdStatements)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccountsAccountIdStatements),
    "GET",
    "/aisp/accounts/ACCOUNT_ID/statements",
    "Get Statements for an AccountId",
    """Enables an AISP to retrieve statement information for a specific PSU account.""",
    EmptyBody,
    parseBody(EX_getAccountsAccountIdStatements),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Statements") :: Nil,
    http4sPartialFunction = Some(getAccountsAccountIdStatements)
  )

  private val EX_getAccountsAccountIdStatementsStatementId: String = """{
  "Data": {
    "Statement": [
      {
        "AccountId": "22289",
        "StatementId": "8sfhke-sifhkeuf-97813",
        "StatementReference": "002",
        "Type": "RegularPeriodic",
        "StartDateTime": "2017-07-12T00:00:00+00:00",
        "EndDateTime": "2017-07-12T00:00:00+00:00",
        "CreationDateTime": "2024-05-29T00:00:00Z",
        "StatementDescription": [
          "August 2017 Statement"
        ],
        "StatementBenefit": [
          {
            "Type": "UK.OBIE.Cashback",
            "Amount": {
              "Amount": {},
              "Currency": {}
            }
          }
        ],
        "StatementFee": [
          {
            "Description": "International usage charge",
            "CreditDebitIndicator": "Credit",
            "Type": "UK.OBIE.Annual",
            "Rate": 0.05,
            "RateType": "UK.OBIE.AER",
            "Frequency": "UK.OBIE.StatementMonthly",
            "Amount": {
              "Amount": {},
              "Currency": {}
            }
          }
        ],
        "StatementInterest": [
          {
            "Description": "Interest occurred over statement duration",
            "CreditDebitIndicator": "Credit",
            "Type": "UK.OBIE.Total",
            "Rate": 0.05,
            "RateType": "UK.OBIE.FixedRate",
            "Frequency": "UK.OBIE.Monthly",
            "Amount": {
              "Amount": {},
              "Currency": {}
            }
          }
        ],
        "StatementAmount": [
          {
            "CreditDebitIndicator": "Credit",
            "Type": "UK.OBIE.CreditLimit",
            "Amount": {
              "Amount": {},
              "Currency": {},
              "SubType": "BCUR"
            },
            "LocalAmount": {
              "Amount": {},
              "Currency": {},
              "SubType": "BCUR"
            }
          }
        ],
        "StatementDateTime": [
          {
            "DateTime": "2024-05-29T00:00:00Z",
            "Type": "UK.OBIE.NextStatement"
          }
        ],
        "StatementRate": [
          {
            "Rate": "0.224",
            "Type": "UK.OBIE.AnnualCash"
          }
        ],
        "StatementValue": [
          {
            "Value": "string",
            "Type": "UK.OBIE.Credit"
          }
        ],
        "TotalValue": {
          "Amount": "1209.06",
          "Currency": "GBP"
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
  lazy val getAccountsAccountIdStatementsStatementId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "aisp" / "accounts" / accountId / "statements" / statementId =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getAccountsAccountIdStatementsStatementId)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccountsAccountIdStatementsStatementId),
    "GET",
    "/aisp/accounts/ACCOUNT_ID/statements/STATEMENT_ID",
    "Get Statement by StatementId for an AccountId",
    """Enables an AISP to retrieve the statement information resource for a specific statement.""",
    EmptyBody,
    parseBody(EX_getAccountsAccountIdStatementsStatementId),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Statements") :: Nil,
    http4sPartialFunction = Some(getAccountsAccountIdStatementsStatementId)
  )

  private val EX_getAccountsAccountIdStatementsStatementIdFile: String = """{}"""
  lazy val getAccountsAccountIdStatementsStatementIdFile: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "aisp" / "accounts" / accountId / "statements" / statementId / "file" =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getAccountsAccountIdStatementsStatementIdFile)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccountsAccountIdStatementsStatementIdFile),
    "GET",
    "/aisp/accounts/ACCOUNT_ID/statements/STATEMENT_ID/file",
    "Get Statement file by StatementId for an AccountId",
    """Enables an AISP to retrieve a non-json representation of a specific statement.""",
    EmptyBody,
    parseBody(EX_getAccountsAccountIdStatementsStatementIdFile),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Statements") :: Nil,
    http4sPartialFunction = Some(getAccountsAccountIdStatementsStatementIdFile)
  )

  private val EX_getAccountsAccountIdStatementsStatementIdTransactions: String = """{
  "Data": {
    "Transaction": [
      {
        "AccountId": "22289",
        "TransactionId": "string",
        "TransactionReference": "string",
        "StatementReference": [
          "002"
        ],
        "CreditDebitIndicator": "Credit",
        "Status": "BOOK",
        "TransactionMutability": "Mutable",
        "BookingDateTime": "2020-01-01T00:00:00+00:00",
        "ValueDateTime": "2020-01-01T00:00:00+00:00",
        "TransactionInformation": "string",
        "AddressLine": "string",
        "Amount": {
          "Amount": "1209.06",
          "Currency": "GBP"
        },
        "ChargeAmount": {
          "Amount": "1209.06",
          "Currency": "GBP"
        },
        "CurrencyExchange": {
          "SourceCurrency": "string",
          "TargetCurrency": "string",
          "UnitCurrency": "string",
          "ExchangeRate": 0,
          "ContractIdentification": "string",
          "QuotationDate": "2020-01-01T00:00:00+00:00",
          "InstructedAmount": {
            "Amount": "1209.06",
            "Currency": "GBP"
          }
        },
        "BankTransactionCode": {
          "Code": "string",
          "SubCode": "string"
        },
        "ProprietaryBankTransactionCode": {
          "Code": "string",
          "Issuer": "string"
        },
        "ExtendedProprietaryBankTransactionCodes": [
          {
            "Code": "string",
            "Issuer": "string",
            "Description": "string"
          }
        ],
        "Balance": {
          "CreditDebitIndicator": "Credit",
          "Type": "CLAV",
          "Amount": {
            "Amount": "1209.06",
            "Currency": "GBP"
          }
        },
        "MerchantDetails": {
          "MerchantName": "string",
          "MerchantCategoryCode": "string"
        },
        "CreditorAgent": {
          "SchemeName": "UK.OBIE.BICFI",
          "Identification": "string",
          "Name": "Agent Name",
          "LEI": "IZ9Q00LZEVUKWCQY6X15",
          "PostalAddress": {
            "AddressType": "BIZZ",
            "Department": "Finance",
            "SubDepartment": "Payroll",
            "StreetName": "Bank Street",
            "BuildingNumber": "11",
            "BuildingName": "string",
            "Floor": "11",
            "UnitNumber": "A88",
            "Room": "Basement 03",
            "PostBox": "PO Box 123456",
            "TownLocationName": "London",
            "DistrictName": "Greater London",
            "CareOf": "Jane Smith",
            "PostCode": "EC2N 4AG",
            "TownName": "London",
            "CountrySubDivision": "string",
            "Country": "string",
            "AddressLine": [
              "string"
            ]
          }
        },
        "CreditorAccount": {
          "SchemeName": "string",
          "Identification": "80200112344562",
          "Name": "Jane Smith",
          "SecondaryIdentification": "87562298675897",
          "Proxy": {
            "Identification": "2360549017905188",
            "Code": "TELE",
            "Type": "string"
          }
        },
        "DebtorAgent": {
          "SchemeName": "UK.OBIE.BICFI",
          "Identification": "string",
          "Name": "Agent Name",
          "LEI": "IZ9Q00LZEVUKWCQY6X15",
          "PostalAddress": {
            "AddressType": "BIZZ",
            "Department": "Finance",
            "SubDepartment": "Payroll",
            "StreetName": "Bank Street",
            "BuildingNumber": "11",
            "BuildingName": "string",
            "Floor": "11",
            "UnitNumber": "A88",
            "Room": "Basement 03",
            "PostBox": "PO Box 123456",
            "TownLocationName": "London",
            "DistrictName": "Greater London",
            "CareOf": "Jane Smith",
            "PostCode": "EC2N 4AG",
            "TownName": "London",
            "CountrySubDivision": "string",
            "Country": "string",
            "AddressLine": [
              "string"
            ]
          }
        },
        "DebtorAccount": {
          "SchemeName": "string",
          "Identification": "80200112344562",
          "Name": "Jane Smith",
          "SecondaryIdentification": "87562298675897",
          "Proxy": {
            "Identification": "2360549017905188",
            "Code": "TELE",
            "Type": "string"
          }
        },
        "CardInstrument": {
          "CardSchemeName": "AmericanExpress",
          "AuthorisationType": "ConsumerDevice",
          "Name": "string",
          "Identification": "string"
        },
        "SupplementaryData": {},
        "CategoryPurposeCode": "BONU",
        "PaymentPurposeCode": "BKDF",
        "UltimateCreditor": {
          "Name": "string",
          "Identification": "string",
          "LEI": "IZ9Q00LZEVUKWCQY6X15",
          "SchemeName": "string",
          "PostalAddress": {
            "AddressType": "BIZZ",
            "Department": "Finance",
            "SubDepartment": "Payroll",
            "StreetName": "Bank Street",
            "BuildingNumber": "11",
            "BuildingName": "string",
            "Floor": "11",
            "UnitNumber": "A88",
            "Room": "Basement 03",
            "PostBox": "PO Box 123456",
            "TownLocationName": "London",
            "DistrictName": "Greater London",
            "CareOf": "Jane Smith",
            "PostCode": "EC2N 4AG",
            "TownName": "London",
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
            "Department": "Finance",
            "SubDepartment": "Payroll",
            "StreetName": "Bank Street",
            "BuildingNumber": "11",
            "BuildingName": "string",
            "Floor": "11",
            "UnitNumber": "A88",
            "Room": "Basement 03",
            "PostBox": "PO Box 123456",
            "TownLocationName": "London",
            "DistrictName": "Greater London",
            "CareOf": "Jane Smith",
            "PostCode": "EC2N 4AG",
            "TownName": "London",
            "CountrySubDivision": "string",
            "Country": "string",
            "AddressLine": [
              "string"
            ]
          }
        },
        "IntermediaryAgent1": {
          "Name": "string",
          "SchemeName": "string",
          "Identification": "string",
          "LEI": "IZ9Q00LZEVUKWCQY6X15",
          "PostalAddress": {
            "AddressType": "BIZZ",
            "Department": "Finance",
            "SubDepartment": "Payroll",
            "StreetName": "Bank Street",
            "BuildingNumber": "11",
            "BuildingName": "string",
            "Floor": "11",
            "UnitNumber": "A88",
            "Room": "Basement 03",
            "PostBox": "PO Box 123456",
            "TownLocationName": "London",
            "DistrictName": "Greater London",
            "CareOf": "Jane Smith",
            "PostCode": "EC2N 4AG",
            "TownName": "London",
            "CountrySubDivision": "string",
            "Country": "string",
            "AddressLine": [
              "string"
            ]
          },
          "ProcessingStatus": "PDNG"
        },
        "IntermediaryAgent2": {
          "Name": "string",
          "SchemeName": "string",
          "Identification": "string",
          "LEI": "IZ9Q00LZEVUKWCQY6X15",
          "PostalAddress": {
            "AddressType": "BIZZ",
            "Department": "Finance",
            "SubDepartment": "Payroll",
            "StreetName": "Bank Street",
            "BuildingNumber": "11",
            "BuildingName": "string",
            "Floor": "11",
            "UnitNumber": "A88",
            "Room": "Basement 03",
            "PostBox": "PO Box 123456",
            "TownLocationName": "London",
            "DistrictName": "Greater London",
            "CareOf": "Jane Smith",
            "PostCode": "EC2N 4AG",
            "TownName": "London",
            "CountrySubDivision": "string",
            "Country": "string",
            "AddressLine": [
              "string"
            ]
          },
          "ProcessingStatus": "PDNG"
        },
        "IntermediaryAgent3": {
          "Name": "string",
          "SchemeName": "string",
          "Identification": "string",
          "LEI": "IZ9Q00LZEVUKWCQY6X15",
          "PostalAddress": {
            "AddressType": "BIZZ",
            "Department": "Finance",
            "SubDepartment": "Payroll",
            "StreetName": "Bank Street",
            "BuildingNumber": "11",
            "BuildingName": "string",
            "Floor": "11",
            "UnitNumber": "A88",
            "Room": "Basement 03",
            "PostBox": "PO Box 123456",
            "TownLocationName": "London",
            "DistrictName": "Greater London",
            "CareOf": "Jane Smith",
            "PostCode": "EC2N 4AG",
            "TownName": "London",
            "CountrySubDivision": "string",
            "Country": "string",
            "AddressLine": [
              "string"
            ]
          },
          "ProcessingStatus": "PDNG"
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
  lazy val getAccountsAccountIdStatementsStatementIdTransactions: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "aisp" / "accounts" / accountId / "statements" / statementId / "transactions" =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getAccountsAccountIdStatementsStatementIdTransactions)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccountsAccountIdStatementsStatementIdTransactions),
    "GET",
    "/aisp/accounts/ACCOUNT_ID/statements/STATEMENT_ID/transactions",
    "Get Statement Transactions for an AccountId",
    """Enables an AISP to retrieve transactions that appear on a selected statement for a specific PSU account.""",
    EmptyBody,
    parseBody(EX_getAccountsAccountIdStatementsStatementIdTransactions),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Statements") :: Nil,
    http4sPartialFunction = Some(getAccountsAccountIdStatementsStatementIdTransactions)
  )

  private val EX_getAccountsAccountIdTransactions: String = """{
  "Data": {
    "Transaction": [
      {
        "AccountId": "22289",
        "TransactionId": "string",
        "TransactionReference": "string",
        "StatementReference": [
          "002"
        ],
        "CreditDebitIndicator": "Credit",
        "Status": "BOOK",
        "TransactionMutability": "Mutable",
        "BookingDateTime": "2020-01-01T00:00:00+00:00",
        "ValueDateTime": "2020-01-01T00:00:00+00:00",
        "TransactionInformation": "string",
        "AddressLine": "string",
        "Amount": {
          "Amount": "1209.06",
          "Currency": "GBP"
        },
        "ChargeAmount": {
          "Amount": "1209.06",
          "Currency": "GBP"
        },
        "CurrencyExchange": {
          "SourceCurrency": "string",
          "TargetCurrency": "string",
          "UnitCurrency": "string",
          "ExchangeRate": 0,
          "ContractIdentification": "string",
          "QuotationDate": "2020-01-01T00:00:00+00:00",
          "InstructedAmount": {
            "Amount": "1209.06",
            "Currency": "GBP"
          }
        },
        "BankTransactionCode": {
          "Code": "string",
          "SubCode": "string"
        },
        "ProprietaryBankTransactionCode": {
          "Code": "string",
          "Issuer": "string"
        },
        "ExtendedProprietaryBankTransactionCodes": [
          {
            "Code": "string",
            "Issuer": "string",
            "Description": "string"
          }
        ],
        "Balance": {
          "CreditDebitIndicator": "Credit",
          "Type": "CLAV",
          "Amount": {
            "Amount": "1209.06",
            "Currency": "GBP"
          }
        },
        "MerchantDetails": {
          "MerchantName": "string",
          "MerchantCategoryCode": "string"
        },
        "CreditorAgent": {
          "SchemeName": "UK.OBIE.BICFI",
          "Identification": "string",
          "Name": "Agent Name",
          "LEI": "IZ9Q00LZEVUKWCQY6X15",
          "PostalAddress": {
            "AddressType": "BIZZ",
            "Department": "Finance",
            "SubDepartment": "Payroll",
            "StreetName": "Bank Street",
            "BuildingNumber": "11",
            "BuildingName": "string",
            "Floor": "11",
            "UnitNumber": "A88",
            "Room": "Basement 03",
            "PostBox": "PO Box 123456",
            "TownLocationName": "London",
            "DistrictName": "Greater London",
            "CareOf": "Jane Smith",
            "PostCode": "EC2N 4AG",
            "TownName": "London",
            "CountrySubDivision": "string",
            "Country": "string",
            "AddressLine": [
              "string"
            ]
          }
        },
        "CreditorAccount": {
          "SchemeName": "string",
          "Identification": "80200112344562",
          "Name": "Jane Smith",
          "SecondaryIdentification": "87562298675897",
          "Proxy": {
            "Identification": "2360549017905188",
            "Code": "TELE",
            "Type": "string"
          }
        },
        "DebtorAgent": {
          "SchemeName": "UK.OBIE.BICFI",
          "Identification": "string",
          "Name": "Agent Name",
          "LEI": "IZ9Q00LZEVUKWCQY6X15",
          "PostalAddress": {
            "AddressType": "BIZZ",
            "Department": "Finance",
            "SubDepartment": "Payroll",
            "StreetName": "Bank Street",
            "BuildingNumber": "11",
            "BuildingName": "string",
            "Floor": "11",
            "UnitNumber": "A88",
            "Room": "Basement 03",
            "PostBox": "PO Box 123456",
            "TownLocationName": "London",
            "DistrictName": "Greater London",
            "CareOf": "Jane Smith",
            "PostCode": "EC2N 4AG",
            "TownName": "London",
            "CountrySubDivision": "string",
            "Country": "string",
            "AddressLine": [
              "string"
            ]
          }
        },
        "DebtorAccount": {
          "SchemeName": "string",
          "Identification": "80200112344562",
          "Name": "Jane Smith",
          "SecondaryIdentification": "87562298675897",
          "Proxy": {
            "Identification": "2360549017905188",
            "Code": "TELE",
            "Type": "string"
          }
        },
        "CardInstrument": {
          "CardSchemeName": "AmericanExpress",
          "AuthorisationType": "ConsumerDevice",
          "Name": "string",
          "Identification": "string"
        },
        "SupplementaryData": {},
        "CategoryPurposeCode": "BONU",
        "PaymentPurposeCode": "BKDF",
        "UltimateCreditor": {
          "Name": "string",
          "Identification": "string",
          "LEI": "IZ9Q00LZEVUKWCQY6X15",
          "SchemeName": "string",
          "PostalAddress": {
            "AddressType": "BIZZ",
            "Department": "Finance",
            "SubDepartment": "Payroll",
            "StreetName": "Bank Street",
            "BuildingNumber": "11",
            "BuildingName": "string",
            "Floor": "11",
            "UnitNumber": "A88",
            "Room": "Basement 03",
            "PostBox": "PO Box 123456",
            "TownLocationName": "London",
            "DistrictName": "Greater London",
            "CareOf": "Jane Smith",
            "PostCode": "EC2N 4AG",
            "TownName": "London",
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
            "Department": "Finance",
            "SubDepartment": "Payroll",
            "StreetName": "Bank Street",
            "BuildingNumber": "11",
            "BuildingName": "string",
            "Floor": "11",
            "UnitNumber": "A88",
            "Room": "Basement 03",
            "PostBox": "PO Box 123456",
            "TownLocationName": "London",
            "DistrictName": "Greater London",
            "CareOf": "Jane Smith",
            "PostCode": "EC2N 4AG",
            "TownName": "London",
            "CountrySubDivision": "string",
            "Country": "string",
            "AddressLine": [
              "string"
            ]
          }
        },
        "IntermediaryAgent1": {
          "Name": "string",
          "SchemeName": "string",
          "Identification": "string",
          "LEI": "IZ9Q00LZEVUKWCQY6X15",
          "PostalAddress": {
            "AddressType": "BIZZ",
            "Department": "Finance",
            "SubDepartment": "Payroll",
            "StreetName": "Bank Street",
            "BuildingNumber": "11",
            "BuildingName": "string",
            "Floor": "11",
            "UnitNumber": "A88",
            "Room": "Basement 03",
            "PostBox": "PO Box 123456",
            "TownLocationName": "London",
            "DistrictName": "Greater London",
            "CareOf": "Jane Smith",
            "PostCode": "EC2N 4AG",
            "TownName": "London",
            "CountrySubDivision": "string",
            "Country": "string",
            "AddressLine": [
              "string"
            ]
          },
          "ProcessingStatus": "PDNG"
        },
        "IntermediaryAgent2": {
          "Name": "string",
          "SchemeName": "string",
          "Identification": "string",
          "LEI": "IZ9Q00LZEVUKWCQY6X15",
          "PostalAddress": {
            "AddressType": "BIZZ",
            "Department": "Finance",
            "SubDepartment": "Payroll",
            "StreetName": "Bank Street",
            "BuildingNumber": "11",
            "BuildingName": "string",
            "Floor": "11",
            "UnitNumber": "A88",
            "Room": "Basement 03",
            "PostBox": "PO Box 123456",
            "TownLocationName": "London",
            "DistrictName": "Greater London",
            "CareOf": "Jane Smith",
            "PostCode": "EC2N 4AG",
            "TownName": "London",
            "CountrySubDivision": "string",
            "Country": "string",
            "AddressLine": [
              "string"
            ]
          },
          "ProcessingStatus": "PDNG"
        },
        "IntermediaryAgent3": {
          "Name": "string",
          "SchemeName": "string",
          "Identification": "string",
          "LEI": "IZ9Q00LZEVUKWCQY6X15",
          "PostalAddress": {
            "AddressType": "BIZZ",
            "Department": "Finance",
            "SubDepartment": "Payroll",
            "StreetName": "Bank Street",
            "BuildingNumber": "11",
            "BuildingName": "string",
            "Floor": "11",
            "UnitNumber": "A88",
            "Room": "Basement 03",
            "PostBox": "PO Box 123456",
            "TownLocationName": "London",
            "DistrictName": "Greater London",
            "CareOf": "Jane Smith",
            "PostCode": "EC2N 4AG",
            "TownName": "London",
            "CountrySubDivision": "string",
            "Country": "string",
            "AddressLine": [
              "string"
            ]
          },
          "ProcessingStatus": "PDNG"
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
  lazy val getAccountsAccountIdTransactions: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "aisp" / "accounts" / accountIdStr / "transactions" =>
      EndpointHelpers.withUser(req) { (u, cc) =>
        val accountId = AccountId(accountIdStr)
        // The read itself is shared with v3.1 -- see UKTransactionsQuery. Only the factory differs.
        UKTransactionsQuery.read(req, u, cc, accountId) map { result =>
          JSONFactory_UKOpenBanking_401.createTransactionsJsonNew(
            result.account.bankId, accountId.value, result.transactions, result.attributes, result.view)
        }
      }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAccountsAccountIdTransactions),
    "GET",
    "/aisp/accounts/ACCOUNT_ID/transactions",
    "Get Transactions for an AccountId",
    """Enables an AISP to retrieve transaction information for a specific PSU account.""",
    EmptyBody,
    parseBody(EX_getAccountsAccountIdTransactions),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Transactions") :: Nil,
    http4sPartialFunction = Some(getAccountsAccountIdTransactions)
  )

  private val EX_getBalances: String = """{
  "Data": {
    "Balance": [
      {
        "AccountId": "22289",
        "CreditDebitIndicator": "Credit",
        "Type": "CLAV",
        "DateTime": "2020-01-01T00:00:00+00:00",
        "Amount": {
          "Amount": "1209.06",
          "Currency": "GBP",
          "SubType": "BCUR"
        },
        "CreditLine": [
          {
            "Included": true,
            "Type": "Available",
            "Amount": {
              "Amount": {},
              "Currency": {}
            }
          }
        ],
        "LocalAmount": {
          "Amount": "1209.06",
          "Currency": "GBP",
          "SubType": "BCUR"
        }
      }
    ],
    "TotalValue": {
      "Amount": "1209.06",
      "Currency": "GBP"
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
  lazy val getBalances: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "aisp" / "balances" =>
      EndpointHelpers.withUser(req) { (u, cc) =>
        val balancesViewId = ViewId(Constant.SYSTEM_READ_BALANCES_VIEW_ID)
        for {
          _ <- NewStyle.function.checkUKConsent(u, Some(cc))
          _ <- passesPsd2Aisp(Some(cc))
          availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u)
          (accounts, _) <- NewStyle.function.getBankAccounts(availablePrivateAccounts, Some(cc))
        } yield {
          // Holding some view on an account is not the same as being allowed to read its balance:
          // ReadBalances is a permission a consent may simply never have asked for. The per-account
          // balances endpoint checks it, this one did not -- so it answered for every account the
          // caller could see, whatever the consent said. Filter on the same view it does.
          val readable = accounts.filter { account =>
            APIUtil.checkViewAccessAndReturnView(
              balancesViewId, BankIdAccountId(account.bankId, account.accountId), Full(u), Some(cc)).isDefined
          }
          JSONFactory_UKOpenBanking_401.createBalancesJSON(readable)
        }
      }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getBalances),
    "GET",
    "/aisp/balances",
    "Get Balances",
    """Enables an AISP to retrieve balance information for account(s) that the PSU has consented to.""",
    EmptyBody,
    parseBody(EX_getBalances),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Balances") :: Nil,
    http4sPartialFunction = Some(getBalances)
  )

  private val EX_getBeneficiaries: String = """{
  "Data": {
    "Beneficiary": [
      {
        "AccountId": "22289",
        "BeneficiaryId": "Ben1",
        "BeneficiaryType": "Ordinary",
        "Reference": "Towbar Club",
        "SupplementaryData": {},
        "CreditorAgent": {
          "SchemeName": "UK.OBIE.BICFI",
          "Identification": "80200112344562",
          "Name": "Agent Name",
          "PostalAddress": {
            "AddressType": "BIZZ",
            "Department": "Finance",
            "SubDepartment": "Payroll",
            "StreetName": "Bank Street",
            "BuildingNumber": "11",
            "BuildingName": "string",
            "Floor": "11",
            "UnitNumber": "A88",
            "Room": "Basement 03",
            "PostBox": "PO Box 123456",
            "TownLocationName": "London",
            "DistrictName": "Greater London",
            "CareOf": "Jane Smith",
            "PostCode": "EC2N 4AG",
            "TownName": "London",
            "CountrySubDivision": "string",
            "Country": "string",
            "AddressLine": [
              "string"
            ]
          },
          "LEI": "IZ9Q00LZEVUKWCQY6X15"
        },
        "CreditorAccount": {
          "SchemeName": "string",
          "Identification": "80200112344562",
          "Name": "Jane Smith",
          "SecondaryIdentification": "87562298675897",
          "Proxy": {
            "Identification": "2360549017905188",
            "Code": "TELE",
            "Type": "string"
          }
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
  lazy val getBeneficiaries: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "aisp" / "beneficiaries" =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getBeneficiaries)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getBeneficiaries),
    "GET",
    "/aisp/beneficiaries",
    "Get Beneficiaries",
    """Enables an AISP to retrieve Beneficiary information for account(s) that the PSU has consented to.""",
    EmptyBody,
    parseBody(EX_getBeneficiaries),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Beneficiaries") :: Nil,
    http4sPartialFunction = Some(getBeneficiaries)
  )

  private val EX_getDirectDebits: String = """{
  "Data": {
    "DirectDebit": [
      {
        "AccountId": "22289",
        "DirectDebitId": "string",
        "DirectDebitStatusCode": "ACTV",
        "MandateRelatedInformation": {
          "MandateIdentification": "Golfers",
          "Classification": "FIXE",
          "CategoryPurposeCode": "BONU",
          "FirstPaymentDateTime": "2024-04-25T12:46:49.425Z",
          "RecurringPaymentDateTime": "2024-04-25T12:46:49.425Z",
          "FinalPaymentDateTime": "2024-04-25T12:46:49.425Z",
          "Frequency": {
            "Type": "MNTH",
            "CountPerPeriod": 1,
            "PointInTime": "00"
          },
          "Reason": "To pay monthly membership"
        },
        "Name": "string",
        "PreviousPaymentDateTime": "2020-01-01T00:00:00+00:00",
        "PreviousPaymentAmount": {
          "Amount": "1209.06",
          "Currency": "GBP"
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
  lazy val getDirectDebits: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "aisp" / "direct-debits" =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getDirectDebits)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getDirectDebits),
    "GET",
    "/aisp/direct-debits",
    "Get Direct Debits",
    """Enables an AISP to retrieve Direct Debit information for account(s) that the PSU has consented to.""",
    EmptyBody,
    parseBody(EX_getDirectDebits),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Direct Debits") :: Nil,
    http4sPartialFunction = Some(getDirectDebits)
  )

  private val EX_getOffers: String = """{
  "Data": {
    "Offer": [
      {
        "AccountId": "22289",
        "OfferId": "Offer1",
        "OfferType": "LimitIncrease",
        "Description": "Credit limit increase for the account up to £10000.00",
        "StartDateTime": "2024-05-29T00:00:00Z",
        "EndDateTime": "2024-06-29T00:00:00Z",
        "Rate": "100.00",
        "Value": 10,
        "Term": "Starting first of the month and ending at the end of year",
        "URL": "http://modelbank.com/offer/offer1",
        "Amount": {
          "Amount": "1209.06",
          "Currency": "GBP"
        },
        "Fee": {
          "Amount": "1209.06",
          "Currency": "GBP"
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
  lazy val getOffers: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "aisp" / "offers" =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getOffers)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getOffers),
    "GET",
    "/aisp/offers",
    "Get Offers",
    """Enables an AISP to retrieve any offer information for account(s) that the PSU has consented to.""",
    EmptyBody,
    parseBody(EX_getOffers),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Offers") :: Nil,
    http4sPartialFunction = Some(getOffers)
  )

  private val EX_getParty: String = """{
  "Data": {
    "Party": {
      "PartyId": "PXSIF023",
      "PartyNumber": "20202002",
      "PartyType": "Joint",
      "Name": "Mx Jane Smith",
      "FullLegalName": "Jane Smith",
      "LegalStructure": "UK.OBIE.Individual",
      "LEI": "IZ9Q00LZEVUKWCQY6X15",
      "BeneficialOwnership": true,
      "AccountRole": "string",
      "EmailAddress": "d.user@semiotec.co.jp",
      "Phone": "+44-2079460000",
      "Mobile": "+44-7700900000",
      "Relationships": {
        "Account": {
          "Related": "https://api.alphabank.com/open-banking/v4.0/aisp/accounts/89019",
          "Id": "89019"
        }
      },
      "Address": [
        {
          "AddressType": "BIZZ",
          "Department": "Finance",
          "SubDepartment": "Payroll",
          "StreetName": "Bank Street",
          "BuildingNumber": "11",
          "BuildingName": "string",
          "Floor": "11",
          "UnitNumber": "A88",
          "Room": "Basement 03",
          "PostBox": "PO Box 123456",
          "TownLocationName": "London",
          "DistrictName": "Greater London",
          "CareOf": "Jane Smith",
          "PostCode": "EC2N 4AG",
          "TownName": "London",
          "CountrySubDivision": "string",
          "Country": "string",
          "AddressLine": [
            "string"
          ]
        }
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
  lazy val getParty: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "aisp" / "party" =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getParty)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getParty),
    "GET",
    "/aisp/party",
    "Get Party",
    """Retrieve details about the party that gave permission to the AISP to view an account(s).""",
    EmptyBody,
    parseBody(EX_getParty),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Parties") :: Nil,
    http4sPartialFunction = Some(getParty)
  )

  private val EX_getProducts: String = """{
  "Data": {
    "Product": [
      {
        "ProductName": "321 Product",
        "ProductId": "51B",
        "AccountId": "22289",
        "SecondaryProductId": "CA78",
        "ProductType": "PersonalCurrentAccount",
        "MarketingStateId": "22878123",
        "OtherProductType": {
          "Name": "e-Wallet",
          "Description": "Virtual wallet",
          "ProductDetails": {
            "Segment": [
              "GEAS"
            ],
            "FeeFreeLength": 0,
            "FeeFreeLengthPeriod": "PACT",
            "MonthlyMaximumCharge": "string",
            "Notes": [
              "string"
            ],
            "OtherSegment": {
              "Code": {},
              "Name": {},
              "Description": {}
            }
          },
          "CreditInterest": {
            "TierBandSet": [
              {}
            ]
          },
          "Overdraft": {
            "Notes": [
              "string"
            ],
            "OverdraftTierBandSet": [
              {}
            ]
          },
          "LoanInterest": {
            "Notes": [
              "string"
            ],
            "LoanInterestTierBandSet": [
              {}
            ]
          },
          "Repayment": {
            "RepaymentType": "USBA",
            "RepaymentFrequency": "SMDA",
            "AmountType": "RABD",
            "Notes": [
              "string"
            ],
            "OtherRepaymentType": {
              "Code": {},
              "Name": {},
              "Description": {}
            },
            "OtherRepaymentFrequency": {
              "Code": {},
              "Name": {},
              "Description": {}
            },
            "OtherAmountType": {
              "Code": {},
              "Name": {},
              "Description": {}
            },
            "RepaymentFeeCharges": {
              "RepaymentFeeChargeDetail": [],
              "RepaymentFeeChargeCap": []
            },
            "RepaymentHoliday": [
              {}
            ]
          },
          "OtherFeesCharges": [
            {
              "TariffType": "TTEL",
              "TariffName": "string",
              "OtherTariffType": {},
              "FeeChargeDetail": [],
              "FeeChargeCap": []
            }
          ],
          "SupplementaryData": {}
        },
        "BCA": {
          "ProductDetails": {
            "Segment": [
              "ClientAccount"
            ],
            "FeeFreeLength": 0,
            "FeeFreeLengthPeriod": "Day",
            "Notes": [
              "string"
            ]
          },
          "CreditInterest": {
            "TierBandSet": [
              {}
            ]
          },
          "Overdraft": {
            "Notes": [
              "string"
            ],
            "OverdraftTierBandSet": [
              {}
            ]
          },
          "OtherFeesCharges": [
            {
              "TariffType": "Electronic",
              "TariffName": "TariffName",
              "OtherTariffType": {},
              "FeeChargeDetail": [],
              "FeeChargeCap": []
            }
          ]
        },
        "PCA": {
          "ProductDetails": {
            "Segment": [
              "Basic"
            ],
            "MonthlyMaximumCharge": "MonthlyMaximumCharge",
            "Notes": [
              "string"
            ]
          },
          "CreditInterest": {
            "TierBandSet": [
              {}
            ]
          },
          "Overdraft": {
            "Notes": [
              "string"
            ],
            "OverdraftTierBandSet": [
              {}
            ]
          },
          "OtherFeesCharges": {
            "FeeChargeDetail": [
              {}
            ],
            "FeeChargeCap": [
              {}
            ]
          }
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
  lazy val getProducts: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "aisp" / "products" =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getProducts)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getProducts),
    "GET",
    "/aisp/products",
    "Get Products",
    """Enables an AISP to retrieve the account product information for account(s) that the PSU has consented to.""",
    EmptyBody,
    parseBody(EX_getProducts),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Products") :: Nil,
    http4sPartialFunction = Some(getProducts)
  )

  private val EX_getScheduledPayments: String = """{
  "Data": {
    "ScheduledPayment": [
      {
        "AccountId": "22289",
        "ScheduledPaymentId": "SP03",
        "ScheduledPaymentDateTime": "2017-07-12T00:00:00+00:00",
        "ScheduledType": "Arrival",
        "Reference": "Towbar Club",
        "DebtorReference": "REF51561806",
        "InstructedAmount": {
          "Amount": "1209.06",
          "Currency": "GBP"
        },
        "CreditorAgent": {
          "SchemeName": "UK.OBIE.BICFI",
          "Identification": "80200112344562",
          "Name": "Agent Name",
          "PostalAddress": {
            "AddressType": "BIZZ",
            "Department": "Finance",
            "SubDepartment": "Payroll",
            "StreetName": "Bank Street",
            "BuildingNumber": "11",
            "BuildingName": "string",
            "Floor": "11",
            "UnitNumber": "A88",
            "Room": "Basement 03",
            "PostBox": "PO Box 123456",
            "TownLocationName": "London",
            "DistrictName": "Greater London",
            "CareOf": "Jane Smith",
            "PostCode": "EC2N 4AG",
            "TownName": "London",
            "CountrySubDivision": "string",
            "Country": "string",
            "AddressLine": [
              "string"
            ]
          },
          "LEI": "IZ9Q00LZEVUKWCQY6X15"
        },
        "CreditorAccount": {
          "SchemeName": "string",
          "Identification": "string",
          "Name": "Jane Smith",
          "SecondaryIdentification": "87562298675897",
          "Proxy": {
            "Identification": "2360549017905188",
            "Code": "TELE",
            "Type": "string"
          }
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
  lazy val getScheduledPayments: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "aisp" / "scheduled-payments" =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getScheduledPayments)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getScheduledPayments),
    "GET",
    "/aisp/scheduled-payments",
    "Get Scheduled Payments",
    """Enables an AISP to retrieve Scheduled Payment information for account(s) that the PSU has consented to.""",
    EmptyBody,
    parseBody(EX_getScheduledPayments),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Scheduled Payments") :: Nil,
    http4sPartialFunction = Some(getScheduledPayments)
  )

  private val EX_getStandingOrders: String = """{
  "Data": {
    "StandingOrder": [
      {
        "AccountId": "22289",
        "StandingOrderId": "Ben5",
        "NextPaymentDateTime": "2017-07-12T00:00:00+00:00",
        "LastPaymentDateTime": "2017-07-12T00:00:00+00:00",
        "NumberOfPayments": "string",
        "StandingOrderStatusCode": "ACTV",
        "FirstPaymentAmount": {
          "Amount": "1209.06",
          "Currency": "GBP"
        },
        "NextPaymentAmount": {
          "Amount": "1209.06",
          "Currency": "GBP"
        },
        "LastPaymentAmount": {
          "Amount": "1209.06",
          "Currency": "GBP"
        },
        "FinalPaymentAmount": {
          "Amount": "1209.06",
          "Currency": "GBP"
        },
        "CreditorAgent": {
          "SchemeName": "UK.OBIE.BICFI",
          "Identification": "80200112344562",
          "Name": "Agent Name",
          "PostalAddress": {
            "AddressType": "BIZZ",
            "Department": "Finance",
            "SubDepartment": "Payroll",
            "StreetName": "Bank Street",
            "BuildingNumber": "11",
            "BuildingName": "string",
            "Floor": "11",
            "UnitNumber": "A88",
            "Room": "Basement 03",
            "PostBox": "PO Box 123456",
            "TownLocationName": "London",
            "DistrictName": "Greater London",
            "CareOf": "Jane Smith",
            "PostCode": "EC2N 4AG",
            "TownName": "London",
            "CountrySubDivision": "string",
            "Country": "string",
            "AddressLine": [
              "string"
            ]
          },
          "LEI": "IZ9Q00LZEVUKWCQY6X15"
        },
        "CreditorAccount": {
          "SchemeName": "string",
          "Identification": "string",
          "Name": "Jane Smith",
          "SecondaryIdentification": "87562298675897",
          "Proxy": {
            "Identification": "2360549017905188",
            "Code": "TELE",
            "Type": "string"
          }
        },
        "SupplementaryData": {},
        "MandateRelatedInformation": {
          "MandateIdentification": "Golfers",
          "Classification": "FIXE",
          "CategoryPurposeCode": "BONU",
          "FirstPaymentDateTime": "2024-04-25T12:46:49.425Z",
          "RecurringPaymentDateTime": "2024-04-25T12:46:49.425Z",
          "FinalPaymentDateTime": "2024-04-25T12:46:49.425Z",
          "Frequency": {
            "Type": "MNTH",
            "CountPerPeriod": 1,
            "PointInTime": "00"
          },
          "Reason": "To pay monthly membership"
        },
        "RemittanceInformation": {
          "Structured": [
            {
              "ReferredDocumentInformation": [],
              "ReferredDocumentAmount": {},
              "CreditorReferenceInformation": {},
              "Invoicer": {},
              "Invoicee": {},
              "TaxRemittance": "string",
              "AdditionalRemittanceInformation": []
            }
          ],
          "Unstructured": [
            "string"
          ]
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
  lazy val getStandingOrders: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "aisp" / "standing-orders" =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getStandingOrders)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getStandingOrders),
    "GET",
    "/aisp/standing-orders",
    "Get Standing Orders",
    """Enables an AISP to retrieve Standing Order information for account(s) that the PSU has consented to.""",
    EmptyBody,
    parseBody(EX_getStandingOrders),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Standing Orders") :: Nil,
    http4sPartialFunction = Some(getStandingOrders)
  )

  private val EX_getStatements: String = """{
  "Data": {
    "Statement": [
      {
        "AccountId": "22289",
        "StatementId": "8sfhke-sifhkeuf-97813",
        "StatementReference": "002",
        "Type": "RegularPeriodic",
        "StartDateTime": "2017-07-12T00:00:00+00:00",
        "EndDateTime": "2017-07-12T00:00:00+00:00",
        "CreationDateTime": "2024-05-29T00:00:00Z",
        "StatementDescription": [
          "August 2017 Statement"
        ],
        "StatementBenefit": [
          {
            "Type": "UK.OBIE.Cashback",
            "Amount": {
              "Amount": {},
              "Currency": {}
            }
          }
        ],
        "StatementFee": [
          {
            "Description": "International usage charge",
            "CreditDebitIndicator": "Credit",
            "Type": "UK.OBIE.Annual",
            "Rate": 0.05,
            "RateType": "UK.OBIE.AER",
            "Frequency": "UK.OBIE.StatementMonthly",
            "Amount": {
              "Amount": {},
              "Currency": {}
            }
          }
        ],
        "StatementInterest": [
          {
            "Description": "Interest occurred over statement duration",
            "CreditDebitIndicator": "Credit",
            "Type": "UK.OBIE.Total",
            "Rate": 0.05,
            "RateType": "UK.OBIE.FixedRate",
            "Frequency": "UK.OBIE.Monthly",
            "Amount": {
              "Amount": {},
              "Currency": {}
            }
          }
        ],
        "StatementAmount": [
          {
            "CreditDebitIndicator": "Credit",
            "Type": "UK.OBIE.CreditLimit",
            "Amount": {
              "Amount": {},
              "Currency": {},
              "SubType": "BCUR"
            },
            "LocalAmount": {
              "Amount": {},
              "Currency": {},
              "SubType": "BCUR"
            }
          }
        ],
        "StatementDateTime": [
          {
            "DateTime": "2024-05-29T00:00:00Z",
            "Type": "UK.OBIE.NextStatement"
          }
        ],
        "StatementRate": [
          {
            "Rate": "0.224",
            "Type": "UK.OBIE.AnnualCash"
          }
        ],
        "StatementValue": [
          {
            "Value": "string",
            "Type": "UK.OBIE.Credit"
          }
        ],
        "TotalValue": {
          "Amount": "1209.06",
          "Currency": "GBP"
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
  lazy val getStatements: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "aisp" / "statements" =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getStatements)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getStatements),
    "GET",
    "/aisp/statements",
    "Get Statements",
    """Enables an AISP to retrieve statement information for account(s) that the PSU has consented to.""",
    EmptyBody,
    parseBody(EX_getStatements),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Statements") :: Nil,
    http4sPartialFunction = Some(getStatements)
  )

  private val EX_getTransactions: String = """{
  "Data": {
    "Transaction": [
      {
        "AccountId": "22289",
        "TransactionId": "string",
        "TransactionReference": "string",
        "StatementReference": [
          "002"
        ],
        "CreditDebitIndicator": "Credit",
        "Status": "BOOK",
        "TransactionMutability": "Mutable",
        "BookingDateTime": "2020-01-01T00:00:00+00:00",
        "ValueDateTime": "2020-01-01T00:00:00+00:00",
        "TransactionInformation": "string",
        "AddressLine": "string",
        "Amount": {
          "Amount": "1209.06",
          "Currency": "GBP"
        },
        "ChargeAmount": {
          "Amount": "1209.06",
          "Currency": "GBP"
        },
        "CurrencyExchange": {
          "SourceCurrency": "string",
          "TargetCurrency": "string",
          "UnitCurrency": "string",
          "ExchangeRate": 0,
          "ContractIdentification": "string",
          "QuotationDate": "2020-01-01T00:00:00+00:00",
          "InstructedAmount": {
            "Amount": "1209.06",
            "Currency": "GBP"
          }
        },
        "BankTransactionCode": {
          "Code": "string",
          "SubCode": "string"
        },
        "ProprietaryBankTransactionCode": {
          "Code": "string",
          "Issuer": "string"
        },
        "ExtendedProprietaryBankTransactionCodes": [
          {
            "Code": "string",
            "Issuer": "string",
            "Description": "string"
          }
        ],
        "Balance": {
          "CreditDebitIndicator": "Credit",
          "Type": "CLAV",
          "Amount": {
            "Amount": "1209.06",
            "Currency": "GBP"
          }
        },
        "MerchantDetails": {
          "MerchantName": "string",
          "MerchantCategoryCode": "string"
        },
        "CreditorAgent": {
          "SchemeName": "UK.OBIE.BICFI",
          "Identification": "string",
          "Name": "Agent Name",
          "LEI": "IZ9Q00LZEVUKWCQY6X15",
          "PostalAddress": {
            "AddressType": "BIZZ",
            "Department": "Finance",
            "SubDepartment": "Payroll",
            "StreetName": "Bank Street",
            "BuildingNumber": "11",
            "BuildingName": "string",
            "Floor": "11",
            "UnitNumber": "A88",
            "Room": "Basement 03",
            "PostBox": "PO Box 123456",
            "TownLocationName": "London",
            "DistrictName": "Greater London",
            "CareOf": "Jane Smith",
            "PostCode": "EC2N 4AG",
            "TownName": "London",
            "CountrySubDivision": "string",
            "Country": "string",
            "AddressLine": [
              "string"
            ]
          }
        },
        "CreditorAccount": {
          "SchemeName": "string",
          "Identification": "80200112344562",
          "Name": "Jane Smith",
          "SecondaryIdentification": "87562298675897",
          "Proxy": {
            "Identification": "2360549017905188",
            "Code": "TELE",
            "Type": "string"
          }
        },
        "DebtorAgent": {
          "SchemeName": "UK.OBIE.BICFI",
          "Identification": "string",
          "Name": "Agent Name",
          "LEI": "IZ9Q00LZEVUKWCQY6X15",
          "PostalAddress": {
            "AddressType": "BIZZ",
            "Department": "Finance",
            "SubDepartment": "Payroll",
            "StreetName": "Bank Street",
            "BuildingNumber": "11",
            "BuildingName": "string",
            "Floor": "11",
            "UnitNumber": "A88",
            "Room": "Basement 03",
            "PostBox": "PO Box 123456",
            "TownLocationName": "London",
            "DistrictName": "Greater London",
            "CareOf": "Jane Smith",
            "PostCode": "EC2N 4AG",
            "TownName": "London",
            "CountrySubDivision": "string",
            "Country": "string",
            "AddressLine": [
              "string"
            ]
          }
        },
        "DebtorAccount": {
          "SchemeName": "string",
          "Identification": "80200112344562",
          "Name": "Jane Smith",
          "SecondaryIdentification": "87562298675897",
          "Proxy": {
            "Identification": "2360549017905188",
            "Code": "TELE",
            "Type": "string"
          }
        },
        "CardInstrument": {
          "CardSchemeName": "AmericanExpress",
          "AuthorisationType": "ConsumerDevice",
          "Name": "string",
          "Identification": "string"
        },
        "SupplementaryData": {},
        "CategoryPurposeCode": "BONU",
        "PaymentPurposeCode": "BKDF",
        "UltimateCreditor": {
          "Name": "string",
          "Identification": "string",
          "LEI": "IZ9Q00LZEVUKWCQY6X15",
          "SchemeName": "string",
          "PostalAddress": {
            "AddressType": "BIZZ",
            "Department": "Finance",
            "SubDepartment": "Payroll",
            "StreetName": "Bank Street",
            "BuildingNumber": "11",
            "BuildingName": "string",
            "Floor": "11",
            "UnitNumber": "A88",
            "Room": "Basement 03",
            "PostBox": "PO Box 123456",
            "TownLocationName": "London",
            "DistrictName": "Greater London",
            "CareOf": "Jane Smith",
            "PostCode": "EC2N 4AG",
            "TownName": "London",
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
            "Department": "Finance",
            "SubDepartment": "Payroll",
            "StreetName": "Bank Street",
            "BuildingNumber": "11",
            "BuildingName": "string",
            "Floor": "11",
            "UnitNumber": "A88",
            "Room": "Basement 03",
            "PostBox": "PO Box 123456",
            "TownLocationName": "London",
            "DistrictName": "Greater London",
            "CareOf": "Jane Smith",
            "PostCode": "EC2N 4AG",
            "TownName": "London",
            "CountrySubDivision": "string",
            "Country": "string",
            "AddressLine": [
              "string"
            ]
          }
        },
        "IntermediaryAgent1": {
          "Name": "string",
          "SchemeName": "string",
          "Identification": "string",
          "LEI": "IZ9Q00LZEVUKWCQY6X15",
          "PostalAddress": {
            "AddressType": "BIZZ",
            "Department": "Finance",
            "SubDepartment": "Payroll",
            "StreetName": "Bank Street",
            "BuildingNumber": "11",
            "BuildingName": "string",
            "Floor": "11",
            "UnitNumber": "A88",
            "Room": "Basement 03",
            "PostBox": "PO Box 123456",
            "TownLocationName": "London",
            "DistrictName": "Greater London",
            "CareOf": "Jane Smith",
            "PostCode": "EC2N 4AG",
            "TownName": "London",
            "CountrySubDivision": "string",
            "Country": "string",
            "AddressLine": [
              "string"
            ]
          },
          "ProcessingStatus": "PDNG"
        },
        "IntermediaryAgent2": {
          "Name": "string",
          "SchemeName": "string",
          "Identification": "string",
          "LEI": "IZ9Q00LZEVUKWCQY6X15",
          "PostalAddress": {
            "AddressType": "BIZZ",
            "Department": "Finance",
            "SubDepartment": "Payroll",
            "StreetName": "Bank Street",
            "BuildingNumber": "11",
            "BuildingName": "string",
            "Floor": "11",
            "UnitNumber": "A88",
            "Room": "Basement 03",
            "PostBox": "PO Box 123456",
            "TownLocationName": "London",
            "DistrictName": "Greater London",
            "CareOf": "Jane Smith",
            "PostCode": "EC2N 4AG",
            "TownName": "London",
            "CountrySubDivision": "string",
            "Country": "string",
            "AddressLine": [
              "string"
            ]
          },
          "ProcessingStatus": "PDNG"
        },
        "IntermediaryAgent3": {
          "Name": "string",
          "SchemeName": "string",
          "Identification": "string",
          "LEI": "IZ9Q00LZEVUKWCQY6X15",
          "PostalAddress": {
            "AddressType": "BIZZ",
            "Department": "Finance",
            "SubDepartment": "Payroll",
            "StreetName": "Bank Street",
            "BuildingNumber": "11",
            "BuildingName": "string",
            "Floor": "11",
            "UnitNumber": "A88",
            "Room": "Basement 03",
            "PostBox": "PO Box 123456",
            "TownLocationName": "London",
            "DistrictName": "Greater London",
            "CareOf": "Jane Smith",
            "PostCode": "EC2N 4AG",
            "TownName": "London",
            "CountrySubDivision": "string",
            "Country": "string",
            "AddressLine": [
              "string"
            ]
          },
          "ProcessingStatus": "PDNG"
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
  lazy val getTransactions: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "aisp" / "transactions" =>
      EndpointHelpers.withUser(req) { (u, cc) =>
        for {
          _ <- NewStyle.function.checkUKConsent(u, Some(cc))
          _ <- passesPsd2Aisp(Some(cc))
          (bank, _) <- NewStyle.function.getBank(BankId(defaultBankId), Some(cc))
          availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u)
          (accounts, _) <- NewStyle.function.getBankAccounts(availablePrivateAccounts, Some(cc))
          // One lookup per distinct bank rather than one per account: a consent may name accounts at
          // several banks, and moderation needs each account's own.
          banksById <- Future.sequence(accounts.map(_.bankId).distinct.map { bankId =>
            NewStyle.function.getBank(bankId, Some(cc)).map { case (b, _) => bankId -> b }
          }).map(_.toMap)
          allTxns <- Future {
            val detailViewId = ViewId(Constant.SYSTEM_READ_TRANSACTIONS_DETAIL_VIEW_ID)
            val basicViewId = ViewId(Constant.SYSTEM_READ_TRANSACTIONS_BASIC_VIEW_ID)
            accounts.flatMap { bankAccount =>
              (for {
                // The owner view, which this used to gate on, comes from holding the account and so
                // says nothing about what a consent permits -- it let a consent that never asked for
                // transactions read them, and moderated them as the owner rather than as the granted
                // view. Gate on the transaction views the consent actually grants, exactly as the
                // per-account endpoint does.
                view <- APIUtil.checkViewAccessAndReturnView(detailViewId, BankIdAccountId(bankAccount.bankId, bankAccount.accountId), Full(u), Some(cc))
                  .or(APIUtil.checkViewAccessAndReturnView(basicViewId, BankIdAccountId(bankAccount.bankId, bankAccount.accountId), Full(u), Some(cc)))
                // The account's own bank, not the instance's default one. moderateTransactionsWithSameAccount
                // builds the moderated account from whatever Bank it is handed and then refuses every
                // transaction that does not belong to it -- so passing the default bank returned an empty
                // list for every account not held there, logging "Attempted to moderate a transaction using
                // the incorrect moderated account" once per row. The `.getOrElse(Nil)` below is what made
                // that look like "this account has no transactions" rather than like a failure.
                accountBank <- Box(banksById.get(bankAccount.bankId)) ?~! s"$BankNotFound ${bankAccount.bankId.value}"
                params = createQueriesByHttpParams(req.headers.headers.toList.map(h => HTTPParam(h.name.toString, List(h.value)))).getOrElse(Nil)
                // Resolved per account, because a consent can grant different directions on different
                // accounts. Same three calls the per-account endpoint makes through UKTransactionsQuery:
                // the query param so the database applies the restriction with the page limit, and the
                // filter so the restriction still holds when the connector ignores the param.
                grantsCredits = UKAmounts.grantsView(Constant.SYSTEM_READ_TRANSACTIONS_CREDITS_VIEW_ID, bankAccount.bankId, bankAccount.accountId, u, cc)
                grantsDebits = UKAmounts.grantsView(Constant.SYSTEM_READ_TRANSACTIONS_DEBITS_VIEW_ID, bankAccount.bankId, bankAccount.accountId, u, cc)
                directedParams = params ++ UKAmounts.directionQueryParam(grantsCredits, grantsDebits)
                (transactions, _) <- BankAccountExtended(bankAccount).getModeratedTransactions(accountBank, Full(u), view, BankIdAccountId(bankAccount.bankId, bankAccount.accountId), Some(cc), directedParams)
                directed = UKAmounts.filterByGrantedDirections(transactions, grantsCredits, grantsDebits)
                _ = UKTransactionsQuery.warnIfPageWasTrimmed(transactions, directed, directedParams, cc)
              } yield directed).getOrElse(Nil)
            }
          }
        } yield JSONFactory_UKOpenBanking_401.createTransactionsJson(bank.bankId, allTxns)
      }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getTransactions),
    "GET",
    "/aisp/transactions",
    "Get Transactions",
    """Enables an AISP to retrieve transaction information for account(s) that the PSU has consented to.""",
    EmptyBody,
    parseBody(EX_getTransactions),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Transactions") :: Nil,
    http4sPartialFunction = Some(getTransactions)
  )

  val routes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    createAccountAccessConsents(req)
      .orElse(getAccountAccessConsentsConsentId(req)
      .orElse(deleteAccountAccessConsentsConsentId(req)
      .orElse(getAccounts(req)
      .orElse(getAccountsAccountId(req)
      .orElse(getAccountsAccountIdBalances(req)
      .orElse(getAccountsAccountIdBeneficiaries(req)
      .orElse(getAccountsAccountIdDirectDebits(req)
      .orElse(getAccountsAccountIdOffers(req)
      .orElse(getAccountsAccountIdParties(req)
      .orElse(getAccountsAccountIdParty(req)
      .orElse(getAccountsAccountIdProduct(req)
      .orElse(getAccountsAccountIdScheduledPayments(req)
      .orElse(getAccountsAccountIdStandingOrders(req)
      .orElse(getAccountsAccountIdStatements(req)
      .orElse(getAccountsAccountIdStatementsStatementId(req)
      .orElse(getAccountsAccountIdStatementsStatementIdFile(req)
      .orElse(getAccountsAccountIdStatementsStatementIdTransactions(req)
      .orElse(getAccountsAccountIdTransactions(req)
      .orElse(getBalances(req)
      .orElse(getBeneficiaries(req)
      .orElse(getDirectDebits(req)
      .orElse(getOffers(req)
      .orElse(getParty(req)
      .orElse(getProducts(req)
      .orElse(getScheduledPayments(req)
      .orElse(getStandingOrders(req)
      .orElse(getStatements(req)
      .orElse(getTransactions(req)))))))))))))))))))))))))))))
  }
}
