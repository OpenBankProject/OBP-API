package code.api.v2_1_0

import org.json4s._
import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.TransactionTypes.TransactionType
import code.api.Constant.{ApiPathZero, CAN_SEE_TRANSACTION_REQUESTS}
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil.{EmptyBody, ResourceDoc, _}
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.http4s.Http4sRequestAttributes.{EndpointHelpers, RequestOps}
import code.api.util.http4s.ResourceDocMiddleware
import code.api.util.http4s.IdempotencyMiddleware
import code.api.util.newstyle.ViewNewStyle
import code.api.util.{APIUtil, ApiRole, CallContext, CustomJsonFormats, NewStyle}
import code.api.v1_2_1.{JSONFactory => JSONFactory121, SuccessMessage}
import code.api.v1_3_0.{JSONFactory1_3_0, PhysicalCardJSON, PostPhysicalCardJSON}
import code.api.v1_4_0.JSONFactory1_4_0
import code.api.v2_0_0.{JSONFactory200, TransactionTypeJsonV200}
import code.api.v2_1_0.JSONFactory210._
import code.atms.Atms
import code.bankconnectors.Connector
import code.branches.Branches
import code.consumer.Consumers
import code.customer.CustomerX
import code.entitlement.Entitlement
import code.metrics.APIMetrics
import code.model.{BankX, Consumer, UserX}
import code.products.Products
import code.sandbox.{OBPDataImport, SandboxData, SandboxDataImport}
import code.usercustomerlinks.UserCustomerLink
import code.users.Users
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.dto.GetProductsParam
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.TransactionRequestTypes._
import com.openbankproject.commons.model.enums.{ChallengeType, SuppliedAnswerType, TransactionRequestTypes}
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus, ScannedApiVersion}
import net.liftweb.common.{Failure, Full}
import com.openbankproject.commons.util.JsonAliases.{compactRender, prettyRender}
import org.json4s.JsonDSL._
import org.json4s.{Extraction, Formats}
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{write => liftWrite}
import org.http4s._
import org.http4s.dsl.io._

import java.util.Date
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

object Http4s210 {
  val implementedInApiVersion: ScannedApiVersion = ApiVersion.v2_1_0
  val versionStatus: String                       = ApiVersionStatus.STABLE.toString
  val resourceDocs: ArrayBuffer[ResourceDoc]       = ArrayBuffer[ResourceDoc]()

  implicit val formats: Formats = CustomJsonFormats.formats

  type HttpF[A] = OptionT[IO, A]

  val createCustomerEntitlementsRequiredForSpecificBank: List[ApiRole] =
    canCreateCustomer :: canCreateUserCustomerLink :: Nil
  val createCustomerEntitlementsRequiredForAnyBank: List[ApiRole] =
    canCreateCustomerAtAnyBank :: canCreateUserCustomerLinkAtAnyBank :: Nil
  val createCustomerEntitlementsRequiredText: String =
    createCustomerEntitlementsRequiredForSpecificBank.mkString(" and ") +
      " OR " + createCustomerEntitlementsRequiredForAnyBank.mkString(" and ")
  // Alias preserves the typo'd name used in Lift's APIMethods210.scala
  // (`createCustomeEntitlementsRequiredText` — missing the `r` in
  // "Custome"). Restored descriptions interpolate the typo'd reference,
  // and we keep the bug-for-bug compatibility so the restoration runs
  // verbatim against Lift's source-of-truth.
  private val createCustomeEntitlementsRequiredText: String = createCustomerEntitlementsRequiredText
  private val getTransactionTypesIsPublic =
    APIUtil.getPropsAsBoolValue("apiOptions.getTransactionTypesIsPublic", true)

  object Implementations2_1_0 {
    val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

    // ─── root ─────────────────────────────────────────────────────────────────

    val root: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` =>
        EndpointHelpers.executeAndRespond(req) { _ =>
          Future.successful(JSONFactory121.getApiInfoJSON(ApiVersion.v2_1_0, versionStatus))
        }
      case req @ GET -> `prefixPath` / "root" =>
        EndpointHelpers.executeAndRespond(req) { _ =>
          Future.successful(JSONFactory121.getApiInfoJSON(ApiVersion.v2_1_0, versionStatus))
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(root), "GET", "/root",
      "Get API Info (root)",
      """Returns information about:
        |
        |* API version
        |* Hosted by information
        |* Git Commit""",
      EmptyBody, apiInfoJSON,
      List(UnknownError, MandatoryPropertyIsNotSet), apiTagApi :: Nil, None,
      http4sPartialFunction = Some(root))

    // ─── sandboxDataImport ────────────────────────────────────────────────────

    val sandboxDataImport: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "sandbox" / "data-import" =>
        EndpointHelpers.withUserAndBodyCreated[SandboxDataImport, SuccessMessage](req) { (user, body, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(s"$DataImportDisabled", failCode = 403, cc = Some(cc)) {
              APIUtil.getPropsAsBoolValue("allow_sandbox_data_import", defaultValue = false)
            }
            _ <- code.util.Helper.booleanToFuture(UserHasMissingRoles + canCreateSandbox, failCode = 403, cc = Some(cc)) {
              APIUtil.hasEntitlement("", user.userId, canCreateSandbox)
            }
            _ <- code.util.Helper.booleanToFuture("Cannot import the sandbox data", cc = Some(cc)) {
              scala.util.Try(OBPDataImport.importer.vend.importData(body)).toOption.exists(_.isDefined)
            }
          } yield SuccessMessage("Success")
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(sandboxDataImport), "POST", "/sandbox/data-import",
      "Create sandbox",
      s"""Import bulk data into the sandbox (Authenticated access).
      |
      |This call can be used to create banks, users, accounts and transactions which are stored in the local RDBMS.
      |
      |The user needs to have CanCreateSandbox entitlement.
      |
      |Note: This is a monolithic call. You could also use a combination of endpoints including create bank, create user, create account and create transaction request to create similar data.
      |
      |An example of an import set of data (json) can be found [here](https://raw.githubusercontent.com/OpenBankProject/OBP-API/develop/obp-api/src/main/scala/code/api/sandbox/example_data/2016-04-28/example_import.json)
      |${userAuthenticationMessage(true)}
      |""",
      SandboxData.importJson, successMessage,
      List(AuthenticatedUserIsRequired, InvalidJsonFormat, DataImportDisabled, UserHasMissingRoles, UnknownError),
      List(apiTagSandbox),
      Some(List(canCreateSandbox)),
      http4sPartialFunction = Some(sandboxDataImport))

    // ─── getTransactionRequestTypesSupportedByBank ────────────────────────────

    private val getTransactionRequestTypesIsPublic =
      APIUtil.getPropsAsBoolValue("apiOptions.getTransactionRequestTypesIsPublic", true)

    val getTransactionRequestTypesSupportedByBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "transaction-request-types" =>
        EndpointHelpers.withBank(req) { (bank, cc) =>
          for {
            _ <- if (!getTransactionRequestTypesIsPublic)
                   code.util.Helper.booleanToFuture(AuthenticatedUserIsRequired, failCode = 401, cc = Some(cc)) { cc.user.isDefined }
                 else Future.unit
            transactionRequestTypes <- Future {
              APIUtil.getPropsValue("transactionRequests_supported_types", "")
            }
          } yield JSONFactory210.createTransactionRequestTypeJSON(transactionRequestTypes.split(",").toList)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getTransactionRequestTypesSupportedByBank), "GET",
      "/banks/BANK_ID/transaction-request-types",
      "Get Transaction Request Types at Bank",
      s"""Get the list of the Transaction Request Types supported by the bank.
         |
         |${userAuthenticationMessage(!getTransactionRequestTypesIsPublic)}""",
      EmptyBody, transactionRequestTypesJSON,
      List(AuthenticatedUserIsRequired, UnknownError),
      List(apiTagTransactionRequest, apiTagBank), None,
      http4sPartialFunction = Some(getTransactionRequestTypesSupportedByBank))

    // ─── createTransactionRequest ─────────────────────────────────────────────
    // Single handler for all transaction request types. Uses GRANT_VIEW_ID in
    // ResourceDoc templates so middleware bypasses view-access validation —
    // checkAuthorisationToCreateTransactionRequest handles that internally and
    // supports canCreateAnyTransactionRequest role bypass.

    // The 4 transaction request types this version knows how to handle. v4.0.0 adds more
    // (ACCOUNT, ACCOUNT_OTP, REFUND, SIMPLE, AGENT_CASH_WITHDRAWAL, CARD); the route guard
    private val v210SupportedTransactionRequestTypes: Set[String] =
      Set("SANDBOX_TAN", "COUNTERPARTY", "SEPA", "FREE_FORM")

    val createTransactionRequest: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "accounts" / _ / viewIdStr / "transaction-request-types" / transactionRequestTypeStr / "transaction-requests" =>
        implicit val cc: CallContext = req.callContext
        // Use cc.httpBody (cached by ResourceDocMiddleware via cachedBodyKey) instead of re-reading
        // req.bodyText, which is empty after the bridge cascade has already consumed the stream.
        (for {
          // Check type validity before requiring middleware-resolved entities: for an invalid
          // type the middleware finds no matching ResourceDoc and skips bankAccount resolution,
          // so cc.bankAccount is None — checking the type first avoids a misleading AccountNotFound.
          _ <- if (v210SupportedTransactionRequestTypes.contains(transactionRequestTypeStr)) IO.unit
               else IO.raiseError(new RuntimeException(liftWrite(code.api.APIFailureNewStyle(
                 s"$InvalidTransactionRequestType: '$transactionRequestTypeStr'", 400, Some(cc.toLight)))))
          jsonBody <- IO.pure(cc.httpBody.getOrElse(""))
          user     <- IO.fromOption(cc.user.toOption)(new RuntimeException(AuthenticatedUserIsRequired))
          account  <- IO.fromOption(cc.bankAccount)(new RuntimeException(AccountNotFound))
          result   <- code.api.util.http4s.RequestScopeConnection.fromFuture(
            createTransactionRequestImpl(jsonBody, user, account, ViewId(viewIdStr), transactionRequestTypeStr, cc))
        } yield result).attempt.flatMap {
          case Right(result) =>
            Created(prettyRender(Extraction.decompose(result)))
          case Left(err) =>
            code.api.util.http4s.ErrorResponseConverter.toHttp4sResponse(err, cc)
        }
    }

    val commonTxReqErrors = List(AuthenticatedUserIsRequired, InvalidBankIdFormat, InvalidAccountIdFormat,
      InvalidJsonFormat, BankNotFound, AccountNotFound, InsufficientAuthorisationToCreateTransactionRequest,
      InvalidTransactionRequestType, InvalidNumber, NotPositiveAmount, InvalidTransactionRequestCurrency,
      TransactionDisabled, UnknownError)

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(createTransactionRequest) + "SandboxTan", "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/GRANT_VIEW_ID/transaction-request-types/SANDBOX_TAN/transaction-requests",
      "Create Transaction Request (SANDBOX_TAN)",
      s"""When using SANDBOX_TAN, the payee is set in the request body.
         |
         |${userAuthenticationMessage(true)}""",
      transactionRequestBodyJsonV200, transactionRequestWithChargeJSON210,
      commonTxReqErrors, List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2), None,
      http4sPartialFunction = Some(createTransactionRequest))

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(createTransactionRequest) + "Counterparty", "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/GRANT_VIEW_ID/transaction-request-types/COUNTERPARTY/transaction-requests",
      "Create Transaction Request (COUNTERPARTY)",
      s"""When using COUNTERPARTY, specify the counterparty_id in the body.
         |
         |${userAuthenticationMessage(true)}""",
      transactionRequestBodyCounterpartyJSON, transactionRequestWithChargeJSON210,
      commonTxReqErrors, List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2), None,
      http4sPartialFunction = Some(createTransactionRequest))

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(createTransactionRequest) + "Sepa", "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/GRANT_VIEW_ID/transaction-request-types/SEPA/transaction-requests",
      "Create Transaction Request (SEPA)",
      s"""When using SEPA, specify the IBAN of a Counterparty in the body.
         |
         |${userAuthenticationMessage(true)}""",
      transactionRequestBodySEPAJSON, transactionRequestWithChargeJSON210,
      commonTxReqErrors, List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2), None,
      http4sPartialFunction = Some(createTransactionRequest))

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(createTransactionRequest) + "FreeForm", "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/GRANT_VIEW_ID/transaction-request-types/FREE_FORM/transaction-requests",
      "Create Transaction Request (FREE_FORM)",
      s"""Create a FREE_FORM Transaction Request.
         |
         |${userAuthenticationMessage(true)}""",
      transactionRequestBodyFreeFormJSON, transactionRequestWithChargeJSON210,
      commonTxReqErrors, List(apiTagTransactionRequest, apiTagPSD2PIS),
      // Role kept out of the ResourceDoc: in the Lift implementation
      // `canCreateAnyTransactionRequest` only bypasses view-permission checks
      // inside `checkAuthorisationToCreateTransactionRequest` — it is not a
      // required entitlement. Owner-view users must still be able to create
      // FREE_FORM requests without holding the role.
      None,
      http4sPartialFunction = Some(createTransactionRequest))

    // (Catch-all ResourceDoc for TRANSACTION_REQUEST_TYPE removed: it caused the v2.1.0
    // middleware to auth-check and route every type, including v4-only ones, then return
    // 400. The four specific docs above cover what v2.1.0 actually supports; v4-only
    // types miss the route guard and fall through to the Lift bridge.)

    private def createTransactionRequestImpl(
      jsonBody: String,
      user: User,
      fromAccount: BankAccount,
      viewId: ViewId,
      transactionRequestTypeStr: String,
      cc: CallContext
    ): Future[TransactionRequestWithChargeJSON210] = {
      val sharedChargePolicy = code.api.ChargePolicy.withName("SHARED").toString
      for {
        _ <- NewStyle.function.isEnabledTransactionRequests(Some(cc))
        _ <- code.util.Helper.booleanToFuture(InvalidAccountIdFormat, cc = Some(cc)) { isValidID(fromAccount.accountId.value) }
        _ <- code.util.Helper.booleanToFuture(InvalidBankIdFormat, cc = Some(cc)) { isValidID(fromAccount.bankId.value) }
        _ <- code.util.Helper.booleanToFuture(
          s"${InvalidTransactionRequestType}: '$transactionRequestTypeStr'", cc = Some(cc)) {
          APIUtil.getPropsValue("transactionRequests_supported_types", "").split(",").contains(transactionRequestTypeStr)
        }
        account = BankIdAccountId(fromAccount.bankId, fromAccount.accountId)
        _ <- NewStyle.function.checkAuthorisationToCreateTransactionRequest(viewId, account, user, Some(cc))
        transDetailsJson <- NewStyle.function.tryons(
          s"$InvalidJsonFormat The Json body should be the $TransactionRequestBodyCommonJSON", 400, Some(cc)) {
          com.openbankproject.commons.util.JsonAliases.parse(jsonBody).extract[TransactionRequestBodyCommonJSON]
        }
        isValidAmountNumber <- NewStyle.function.tryons(
          s"$InvalidNumber Current input is ${transDetailsJson.value.amount}", 400, Some(cc)) {
          BigDecimal(transDetailsJson.value.amount)
        }
        _ <- code.util.Helper.booleanToFuture(
          s"${NotPositiveAmount} Current input is: '$isValidAmountNumber'", cc = Some(cc)) {
          isValidAmountNumber > BigDecimal("0")
        }
        _ <- code.util.Helper.booleanToFuture(
          s"${InvalidISOCurrencyCode} Current input is: '${transDetailsJson.value.currency}'", cc = Some(cc)) {
          isValidCurrencyISOCode(transDetailsJson.value.currency)
        }
        _ <- code.util.Helper.booleanToFuture(
          s"$InvalidTransactionRequestCurrency From Account Currency is ${fromAccount.currency}, but Requested Transaction Currency is: ${transDetailsJson.value.currency}",
          cc = Some(cc)) {
          transDetailsJson.value.currency == fromAccount.currency
        }
        parsedJson = com.openbankproject.commons.util.JsonAliases.parse(jsonBody)
        (createdTransactionRequest, _) <- TransactionRequestTypes.withName(transactionRequestTypeStr) match {
          case SANDBOX_TAN =>
            for {
              body <- NewStyle.function.tryons(
                s"${InvalidJsonFormat}, it should be $SANDBOX_TAN json format", 400, Some(cc)) {
                parsedJson.extract[TransactionRequestBodySandBoxTanJSON]
              }
              toBankId    = BankId(body.to.bank_id)
              toAccountId = AccountId(body.to.account_id)
              (toAccount, _) <- NewStyle.function.checkBankAccountExists(toBankId, toAccountId, Some(cc))
              serialized <- NewStyle.function.tryons(UnknownError, 400, Some(cc)) {
                liftWrite(body)(Serialization.formats(org.json4s.NoTypeHints))
              }
              result <- NewStyle.function.createTransactionRequestv210(
                user, viewId, fromAccount, toAccount,
                com.openbankproject.commons.model.TransactionRequestType(transactionRequestTypeStr),
                body, serialized, sharedChargePolicy, None, None, Some(cc))
            } yield result
          case COUNTERPARTY =>
            for {
              body <- NewStyle.function.tryons(
                s"${InvalidJsonFormat}, it should be $COUNTERPARTY json format", 400, Some(cc)) {
                parsedJson.extract[TransactionRequestBodyCounterpartyJSON]
              }
              (toCounterparty, _) <- NewStyle.function.getCounterpartyByCounterpartyId(
                CounterpartyId(body.to.counterparty_id), Some(cc))
              (toAccount, _) <- NewStyle.function.getBankAccountFromCounterparty(toCounterparty, true, Some(cc))
              _ <- code.util.Helper.booleanToFuture(s"$CounterpartyBeneficiaryPermit", cc = Some(cc)) {
                toCounterparty.isBeneficiary
              }
              _ <- code.util.Helper.booleanToFuture(s"$InvalidChargePolicy", cc = Some(cc)) {
                code.api.ChargePolicy.values.contains(code.api.ChargePolicy.withName(body.charge_policy))
              }
              serialized <- NewStyle.function.tryons(UnknownError, 400, Some(cc)) {
                liftWrite(body)(Serialization.formats(org.json4s.NoTypeHints))
              }
              result <- NewStyle.function.createTransactionRequestv210(
                user, viewId, fromAccount, toAccount,
                com.openbankproject.commons.model.TransactionRequestType(transactionRequestTypeStr),
                body, serialized, body.charge_policy, None, None, Some(cc))
            } yield result
          case SEPA =>
            for {
              body <- NewStyle.function.tryons(
                s"${InvalidJsonFormat}, it should be $SEPA json format", 400, Some(cc)) {
                parsedJson.extract[TransactionRequestBodySEPAJSON]
              }
              (toCounterparty, _) <- NewStyle.function.getCounterpartyByIban(body.to.iban, Some(cc))
              (toAccount, _) <- NewStyle.function.getBankAccountFromCounterparty(toCounterparty, true, Some(cc))
              _ <- code.util.Helper.booleanToFuture(s"$CounterpartyBeneficiaryPermit", cc = Some(cc)) {
                toCounterparty.isBeneficiary
              }
              _ <- code.util.Helper.booleanToFuture(s"$InvalidChargePolicy", cc = Some(cc)) {
                code.api.ChargePolicy.values.contains(code.api.ChargePolicy.withName(body.charge_policy))
              }
              serialized <- NewStyle.function.tryons(UnknownError, 400, Some(cc)) {
                liftWrite(body)(Serialization.formats(org.json4s.NoTypeHints))
              }
              result <- NewStyle.function.createTransactionRequestv210(
                user, viewId, fromAccount, toAccount,
                com.openbankproject.commons.model.TransactionRequestType(transactionRequestTypeStr),
                body, serialized, body.charge_policy, None, None, Some(cc))
            } yield result
          case FREE_FORM =>
            for {
              body <- NewStyle.function.tryons(
                s"${InvalidJsonFormat}, it should be $FREE_FORM json format", 400, Some(cc)) {
                parsedJson.extract[TransactionRequestBodyFreeFormJSON]
              }
              serialized <- NewStyle.function.tryons(UnknownError, 400, Some(cc)) {
                liftWrite(body)(Serialization.formats(org.json4s.NoTypeHints))
              }
              result <- NewStyle.function.createTransactionRequestv210(
                user, viewId, fromAccount, fromAccount,
                com.openbankproject.commons.model.TransactionRequestType(transactionRequestTypeStr),
                body, serialized, sharedChargePolicy, None, None, Some(cc))
            } yield result
          case other =>
            // Should be unreachable: the route guard restricts the match to the 4
            // supported types above, so this branch only fires if a new type is
            // added to the guard without the corresponding case. Encoded as
            // APIFailureNewStyle JSON so ErrorResponseConverter maps it to 400,
            // not 500.
            val af = code.api.APIFailureNewStyle(s"$InvalidTransactionRequestType: '$transactionRequestTypeStr'", 400, Some(cc.toLight))
            Future.failed(new Exception(com.openbankproject.commons.util.JsonAliases.compactRender(org.json4s.Extraction.decompose(af))))
        }
      } yield JSONFactory210.createTransactionRequestWithChargeJSON(createdTransactionRequest)
    }

    // ─── answerTransactionRequestChallenge ────────────────────────────────────

    val answerTransactionRequestChallenge: HttpRoutes[IO] = HttpRoutes.of[IO] {
      // Same guard as createTransactionRequest: v4 trans-req types (ACCOUNT, ACCOUNT_OTP,
      // REFUND, SIMPLE, AGENT_CASH_WITHDRAWAL, CARD, …) need v4's answer-challenge
      // logic (maker-checker, ChallengeJsonV400 shape, attribute attachment). Routing
      // them through this handler returns the v2.1.0 shape and skips v4 validation,
      // so the test sees "400 did not equal 202". Let unknown types fall through to
      // the http4s v4.0.0 bridge, where Http4s400's answerTransactionRequestChallenge runs.
      case req @ POST -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "transaction-request-types" / transactionRequestTypeStr / "transaction-requests" / transReqIdStr / "challenge"
          if v210SupportedTransactionRequestTypes.contains(transactionRequestTypeStr) =>
        implicit val cc: CallContext = req.callContext
        val io = for {
          user     <- IO.fromOption(cc.user.toOption)(new RuntimeException(AuthenticatedUserIsRequired))
          account  <- IO.fromOption(cc.bankAccount)(new RuntimeException(AccountNotFound))
          // Use cached body from cc — req.bodyText is empty after upstream bridge cascade.
          jsonBody <- IO.pure(cc.httpBody.getOrElse(""))
          result   <- code.api.util.http4s.RequestScopeConnection.fromFuture(
            answerChallengeImpl(jsonBody, user, account, transactionRequestTypeStr, transReqIdStr, cc))
        } yield result
        io.attempt.flatMap {
          case Right(result) =>
            Accepted(prettyRender(Extraction.decompose(result)))
          case Left(err) =>
            code.api.util.http4s.ErrorResponseConverter.toHttp4sResponse(err, cc)
        }
    }

    // Register one ResourceDoc per supported type rather than a single
    // TRANSACTION_REQUEST_TYPE wildcard. The wildcard would also match v4-only
    // types (ACCOUNT, ACCOUNT_OTP, REFUND, SIMPLE, AGENT_CASH_WITHDRAWAL, CARD),
    // which the route guard then rejects — leaving the middleware to return 404
    // instead of letting the request fall through to the Lift fallback that
    // actually handles those types.
    private val answerChallengeCommonErrors = List(
      AuthenticatedUserIsRequired, InvalidBankIdFormat, InvalidAccountIdFormat, InvalidJsonFormat,
      BankNotFound, UserNoPermissionAccessView, TransactionRequestStatusNotInitiated,
      TransactionRequestTypeHasChanged, InvalidTransactionRequestChallengeId,
      AllowedAttemptsUsedUp, TransactionDisabled, UnknownError)

    private val answerChallengeTags = List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2)

    v210SupportedTransactionRequestTypes.foreach { trType =>
      resourceDocs += ResourceDoc(
        implementedInApiVersion, nameOf(answerTransactionRequestChallenge) + trType.toLowerCase.capitalize, "POST",
        s"/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/$trType/transaction-requests/TRANSACTION_REQUEST_ID/challenge",
        "Answer Transaction Request Challenge",
        """In Sandbox mode, any string that can be converted to a positive integer will be accepted as an answer.
          |
          |This endpoint expects the following data as provided in the createTransactionRequest response body:
          |
          |1)`TRANSACTION_REQUEST_TYPE` : as per the selected createTransactionRequest type, part of the request URL.
          |
          |2)`TRANSACTION_REQUEST_ID` : the value of the `id` field of the createTransactionRequest response body.
          |
          |3) `id` :  the value of `challenge.id` in the createTransactionRequest response body. 
          |
          |4) `answer` : Defaults to `123`, if running in sandbox mode. In production mode, the value will be sent via the configured SCA method.
          |
        """.stripMargin,
        challengeAnswerJSON, transactionRequestWithChargeJson,
        List(
          AuthenticatedUserIsRequired,
          InvalidBankIdFormat,
          InvalidAccountIdFormat,
          InvalidJsonFormat,
          BankNotFound,
          UserNoPermissionAccessView,
          TransactionRequestStatusNotInitiated,
          TransactionRequestTypeHasChanged,
          InvalidTransactionRequestChallengeId,
          AllowedAttemptsUsedUp,
          TransactionDisabled,
          UnknownError
        ), List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2), None,
        http4sPartialFunction = Some(answerTransactionRequestChallenge))
    }

    private def answerChallengeImpl(
      jsonBody: String,
      user: User,
      fromAccount: BankAccount,
      transactionRequestTypeStr: String,
      transReqIdStr: String,
      cc: CallContext
    ): Future[TransactionRequestWithChargeJSON210] = {
      val transReqId = TransactionRequestId(transReqIdStr)
      for {
        _ <- NewStyle.function.isEnabledTransactionRequests(Some(cc))
        _ <- code.util.Helper.booleanToFuture(InvalidAccountIdFormat, cc = Some(cc)) { isValidID(fromAccount.accountId.value) }
        _ <- code.util.Helper.booleanToFuture(InvalidBankIdFormat, cc = Some(cc)) { isValidID(fromAccount.bankId.value) }
        challengeAnswerJson <- NewStyle.function.tryons(
          s"$InvalidJsonFormat The Json body should be the ChallengeAnswerJSON", 400, Some(cc)) {
          com.openbankproject.commons.util.JsonAliases.parse(jsonBody).extract[code.api.v1_4_0.JSONFactory1_4_0.ChallengeAnswerJSON]
        }
        account = BankIdAccountId(fromAccount.bankId, fromAccount.accountId)
        viewId <- Future {
          val viewIdStr = cc.view.map(_.viewId.value).getOrElse("owner")
          ViewId(viewIdStr)
        }
        _ <- NewStyle.function.checkAuthorisationToCreateTransactionRequest(viewId, account, user, Some(cc))
        (existingTransactionRequest, _) <- NewStyle.function.getTransactionRequestImpl(transReqId, Some(cc))
        _ <- code.util.Helper.booleanToFuture(TransactionRequestStatusNotInitiated, cc = Some(cc)) {
          existingTransactionRequest.status.equals("INITIATED")
        }
        existingType = existingTransactionRequest.`type`
        _ <- code.util.Helper.booleanToFuture(
          s"${TransactionRequestTypeHasChanged} It should be: '$existingType', but current value ($transactionRequestTypeStr)",
          cc = Some(cc)) {
          existingType.equals(transactionRequestTypeStr)
        }
        _ <- code.util.Helper.booleanToFuture(s"${InvalidTransactionRequestChallengeId}", cc = Some(cc)) {
          existingTransactionRequest.challenge.id.equals(challengeAnswerJson.id)
        }
        _ <- code.util.Helper.booleanToFuture(s"${InvalidChallengeType}", cc = Some(cc)) {
          existingTransactionRequest.challenge.challenge_type == ChallengeType.OBP_TRANSACTION_REQUEST_CHALLENGE.toString
        }
        (isChallengeAnswerValidated, _) <- NewStyle.function.validateChallengeAnswer(
          challengeAnswerJson.id, challengeAnswerJson.answer, SuppliedAnswerType.PLAIN_TEXT_VALUE, Some(cc))
        _ <- code.util.Helper.booleanToFuture(
          s"${InvalidChallengeAnswer.replace("answer may be expired.", s"answer may be expired ($transactionRequestChallengeTtl seconds).")
            .replace("up your allowed attempts.", s"up your allowed attempts ($allowedAnswerTransactionRequestChallengeAttempts times).")} ",
          cc = Some(cc)) {
          isChallengeAnswerValidated == true
        }
        (transactionRequest, _) <- TransactionRequestTypes.withName(transactionRequestTypeStr) match {
          case TRANSFER_TO_PHONE | TRANSFER_TO_ATM | TRANSFER_TO_ACCOUNT =>
            NewStyle.function.createTransactionAfterChallengeV300(
              user, fromAccount, transReqId,
              com.openbankproject.commons.model.TransactionRequestType(transactionRequestTypeStr), Some(cc))
          case _ =>
            NewStyle.function.createTransactionAfterChallengeV210(fromAccount, existingTransactionRequest, Some(cc))
        }
      } yield JSONFactory210.createTransactionRequestWithChargeJSON(transactionRequest)
    }

    // ─── getTransactionRequests ───────────────────────────────────────────────

    val getTransactionRequests: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "transaction-requests" =>
        EndpointHelpers.withView(req) { (user, account, view, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(TransactionRequestsNotEnabled, cc = Some(cc)) {
              APIUtil.getPropsAsBoolValue("transactionRequests_enabled", false)
            }
            _ <- code.util.Helper.booleanToFuture(
              s"${ViewDoesNotPermitAccess} You need the `$CAN_SEE_TRANSACTION_REQUESTS` permission on the View(${view.viewId.value})",
              cc = Some(cc)) {
              view.allowed_actions.exists(_ == CAN_SEE_TRANSACTION_REQUESTS)
            }
            (transactionRequests, _) <- Future {
              unboxFullOrFail(
                Connector.connector.vend.getTransactionRequests210(user, account, Some(cc)),
                Some(cc), UnknownError, 500)
            }
          } yield JSONFactory210.createTransactionRequestJSONs(transactionRequests)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getTransactionRequests), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-requests",
      "Get Transaction Requests.",
      """Returns transaction requests for account specified by ACCOUNT_ID at bank specified by BANK_ID.
        |
        |The VIEW_ID specified must be 'owner' and the user must have access to this view.
        |
        |Version 2.0.0 now returns charge information.
        |
        |Transaction Requests serve to initiate transactions that may or may not proceed. They contain information including:
        |
        |* Transaction Request Id
        |* Type
        |* Status (INITIATED, COMPLETED)
        |* Challenge (in order to confirm the request)
        |* From Bank / Account
        |* Details including Currency, Value, Description and other initiation information specific to each type. (Could potentialy include a list of future transactions.)
        |* Related Transactions
        |
        |PSD2 Context: PSD2 requires transparency of charges to the customer.
        |This endpoint provides the charge that would be applied if the Transaction Request proceeds - and a record of that charge there after.
        |The customer can proceed with the Transaction by answering the security challenge.
        |
      """.stripMargin,
      EmptyBody, transactionRequestWithChargeJSONs210,
      List(AuthenticatedUserIsRequired, BankNotFound, AccountNotFound, UserHasMissingRoles, UnknownError),
      List(apiTagTransactionRequest, apiTagPsd2, apiTagOldStyle), None,
      http4sPartialFunction = Some(getTransactionRequests))

    // ─── getRoles ─────────────────────────────────────────────────────────────

    val getRoles: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "roles" =>
        EndpointHelpers.withUser(req) { (_, _) =>
          Future.successful(JSONFactory210.createAvailableRolesJSON(ApiRole.availableRoles.sorted))
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getRoles), "GET", "/roles",
      "Get Roles",
      s"""Returns all available roles
         |
         |${userAuthenticationMessage(true)}""",
      EmptyBody, availableRolesJSON,
      List(AuthenticatedUserIsRequired, UnknownError),
      List(apiTagRole),
      None,
      http4sPartialFunction = Some(getRoles))

    // ─── getEntitlementsByBankAndUser ─────────────────────────────────────────

    val getEntitlementsByBankAndUser: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "users" / userId / "entitlements" =>
        EndpointHelpers.withUserAndBank(req) { (user, bank, cc) =>
          val allowedEntitlements = canGetEntitlementsForAnyUserAtOneBank ::
            canGetEntitlementsForAnyUserAtAnyBank :: Nil
          val allowedEntitlementsTxt = UserHasMissingRoles + allowedEntitlements.mkString(" or ")
          for {
            _ <- code.util.Helper.booleanToFuture(allowedEntitlementsTxt, failCode = 403, cc = Some(cc)) {
              APIUtil.hasAtLeastOneEntitlement(bank.bankId.value, user.userId, allowedEntitlements)
            }
            (_, _) <- NewStyle.function.findByUserId(userId, Some(cc))
            entitlements <- Entitlement.entitlement.vend.getEntitlementsByUserIdFuture(userId) map {
              connectorEmptyResponse(_, Some(cc))
            }
          } yield {
            val filteredEntitlements = entitlements.filter(_.bankId == bank.bankId.value)
            if (isSuperAdmin(userId))
              JSONFactory200.withVirtualEntitlements(filteredEntitlements, JSONFactory200.superAdminVirtualRoles)
            else if (isOidcOperator(userId))
              JSONFactory200.withVirtualEntitlements(filteredEntitlements, JSONFactory200.oidcOperatorVirtualRoles)
            else
              JSONFactory200.createEntitlementJSONs(filteredEntitlements)
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getEntitlementsByBankAndUser), "GET",
      "/banks/BANK_ID/users/USER_ID/entitlements",
      "Get Entitlements for User at Bank",
      s"""Get Entitlements specified by BANK_ID and USER_ID
         |
         |${userAuthenticationMessage(true)}""",
      EmptyBody, entitlementJSONs,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagRole, apiTagEntitlement, apiTagUser),
      None,
      http4sPartialFunction = Some(getEntitlementsByBankAndUser))

    // ─── getConsumer ──────────────────────────────────────────────────────────

    val getConsumer: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "consumers" / consumerId =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(UserHasMissingRoles + canGetConsumers, failCode = 403, cc = Some(cc)) {
              APIUtil.hasEntitlement("", user.userId, canGetConsumers)
            }
            consumerIdLong <- NewStyle.function.tryons(InvalidConsumerId, 400, Some(cc)) {
              consumerId.toLong
            }
            consumer <- NewStyle.function.getConsumerByPrimaryId(consumerIdLong, Some(cc))
          } yield JSONFactory210.createConsumerJSON(consumer)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getConsumer), "GET",
      "/management/consumers/CONSUMER_ID",
      "Get Consumer",
      s"""Get the Consumer specified by CONSUMER_ID.""",
      EmptyBody, consumerJSON,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidConsumerId, UnknownError),
      List(apiTagConsumer, apiTagOldStyle),
      Some(List(canGetConsumers)),
      http4sPartialFunction = Some(getConsumer))

    // ─── getConsumers ─────────────────────────────────────────────────────────

    val getConsumers: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "consumers" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(UserHasMissingRoles + canGetConsumers, failCode = 403, cc = Some(cc)) {
              APIUtil.hasEntitlement("", user.userId, canGetConsumers)
            }
          } yield {
            val consumers = Consumer.findAll()
            JSONFactory210.createConsumerJSONs(consumers.sortWith(_.id.get < _.id.get))
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getConsumers), "GET",
      "/management/consumers",
      "Get Consumers",
      s"""Get the all Consumers.""",
      EmptyBody, consumersJson,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagConsumer, apiTagOldStyle),
      Some(List(canGetConsumers)),
      http4sPartialFunction = Some(getConsumers))

    // ─── enableDisableConsumers ───────────────────────────────────────────────

    val enableDisableConsumers: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "management" / "consumers" / consumerId =>
        EndpointHelpers.withUserAndBody[PutEnabledJSON, PutEnabledJSON](req) { (user, body, cc) =>
          for {
            _ <- body.enabled match {
              case true =>
                code.util.Helper.booleanToFuture(UserHasMissingRoles + canEnableConsumers, failCode = 403, cc = Some(cc)) {
                  APIUtil.hasEntitlement("", user.userId, canEnableConsumers)
                }
              case false =>
                code.util.Helper.booleanToFuture(UserHasMissingRoles + canDisableConsumers, failCode = 403, cc = Some(cc)) {
                  APIUtil.hasEntitlement("", user.userId, canDisableConsumers)
                }
            }
            consumer <- Future {
              unboxFullOrFail(
                Consumers.consumers.vend.getConsumerByPrimaryId(consumerId.toLong),
                Some(cc), InvalidConsumerId, 400)
            }
            updatedConsumer <- Future {
              unboxFullOrFail(
                Consumers.consumers.vend.updateConsumer(
                  consumer.id.get, None, None, Some(body.enabled),
                  None, None, None, None, None, None, None, None),
                Some(cc), "Cannot update Consumer", 400)
            }
          } yield PutEnabledJSON(updatedConsumer.isActive.get)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(enableDisableConsumers), "PUT",
      "/management/consumers/CONSUMER_ID",
      "Enable or Disable Consumers",
      s"""Enable/Disable a Consumer specified by CONSUMER_ID.""",
      putEnabledJSON, putEnabledJSON,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagConsumer, apiTagOldStyle),
      None,
      http4sPartialFunction = Some(enableDisableConsumers))

    // ─── addCardForBank ───────────────────────────────────────────────────────

    val addCardForBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "cards" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[PostPhysicalCardJSON, PhysicalCardJSON](req) { (user, bank, body, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(UserHasMissingRoles + canCreateCardsForBank, failCode = 403, cc = Some(cc)) {
              APIUtil.hasEntitlement(bank.bankId.value, user.userId, canCreateCardsForBank)
            }
            _ <- code.util.Helper.booleanToFuture(
              s"${maximumLimitExceeded.replace("10000", "10")} Current issue_number is ${body.issue_number}",
              cc = Some(cc)) {
              body.issue_number.length <= 10
            }
            _ <- body.allows match {
              case Nil => Future.successful(())
              case _ =>
                code.util.Helper.booleanToFuture(AllowedValuesAre + CardAction.availableValues.mkString(", "), cc = Some(cc)) {
                  body.allows.forall(a => CardAction.availableValues.contains(a))
                }
            }
            replacementReason <- NewStyle.function.tryons(AllowedValuesAre + CardReplacementReason.availableValues.mkString(", "), 400, Some(cc)) {
              CardReplacementReason.valueOf(body.replacement.reason_requested)
            }
            (_, _) <- NewStyle.function.getBankAccount(bank.bankId, AccountId(body.account_id), Some(cc))
            (card, _) <- NewStyle.function.createPhysicalCard(
              bankCardNumber    = body.bank_card_number,
              nameOnCard        = body.name_on_card,
              cardType          = "",
              issueNumber       = body.issue_number,
              serialNumber      = body.serial_number,
              validFrom         = body.valid_from_date,
              expires           = body.expires_date,
              enabled           = body.enabled,
              cancelled         = false,
              onHotList         = false,
              technology        = body.technology,
              networks          = body.networks,
              allows            = body.allows,
              accountId         = body.account_id,
              bankId            = bank.bankId.value,
              replacement       = Some(CardReplacementInfo(requestedDate = body.replacement.requested_date, reasonRequested = replacementReason)),
              pinResets         = body.pin_reset.map(e => PinResetInfo(e.requested_date, PinResetReason.valueOf(e.reason_requested.toUpperCase))),
              collected         = Option(CardCollectionInfo(body.collected)),
              posted            = Option(CardPostedInfo(body.posted)),
              customerId        = "",
              cvv               = "",
              brand             = "",
              callContext       = Some(cc)
            )
          } yield JSONFactory1_3_0.createPhysicalCardJSON(card, user)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(addCardForBank), "POST",
      "/banks/BANK_ID/cards",
      "Create Card",
      s"""Create Card at bank specified by BANK_ID.
         |
         |${userAuthenticationMessage(true)}""",
      postPhysicalCardJSON, physicalCardJSON,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, AllowedValuesAre, UnknownError),
      List(apiTagCard),
      Some(List(canCreateCardsForBank)),
      http4sPartialFunction = Some(addCardForBank))

    // ─── getUsers ─────────────────────────────────────────────────────────────

    val getUsers: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(UserHasMissingRoles + canGetAnyUser, failCode = 403, cc = Some(cc)) {
              APIUtil.hasEntitlement("", user.userId, canGetAnyUser)
            }
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(req.uri.renderString)
            (queryParams, _) <- createQueriesByHttpParamsFuture(httpParams, Some(cc))
            users <- Users.users.vend.getAllUsersF(queryParams)
          } yield JSONFactory210.createUserJSONs(users)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getUsers), "GET", "/users",
      "Get all Users",
      s"""Get all users
         |
         |Login is required.
         |CanGetAnyUser entitlement is required,
         |
         |${urlParametersDocument(false, false)}
         |* locked_status (if null ignore)
         |
      """.stripMargin,
      EmptyBody, usersJsonV200,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagUser),
      Some(List(canGetAnyUser)),
      http4sPartialFunction = Some(getUsers))

    // ─── createTransactionType ────────────────────────────────────────────────

    val createTransactionType: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "transaction-types" =>
        EndpointHelpers.withUserAndBankAndBody[TransactionTypeJsonV200, TransactionTypeJsonV200](req) { (user, bank, body, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(InsufficientAuthorisationToCreateTransactionType, failCode = 400, cc = Some(cc)) {
              APIUtil.hasEntitlement(bank.bankId.value, user.userId, canCreateTransactionType)
            }
            returnTransactionType <- Future {
              TransactionType.TransactionTypeProvider.vend.createOrUpdateTransactionType(body) match {
                case Full(t) => t
                case Failure(msg, _, _) =>
                  throw new Exception(compactRender(("failCode" -> 400) ~ ("failMsg" -> msg)))
                case _ =>
                  throw new Exception(compactRender(("failCode" -> 400) ~ ("failMsg" -> CreateTransactionTypeInsertError)))
              }
            }
          } yield JSONFactory200.createTransactionTypeJSON(returnTransactionType)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(createTransactionType), "PUT",
      "/banks/BANK_ID/transaction-types",
      "Create Transaction Type at bank",
      // TODO get the documentation of the parameters from the scala doc of the case class we return
      s"""Create Transaction Types for the bank specified by BANK_ID:
         |
         |  * id : Unique transaction type id across the API instance. SHOULD be a UUID. MUST be unique.
         |  * bank_id : The bank that supports this TransactionType
         |  * short_code : A short code (SHOULD have no-spaces) which MUST be unique across the bank. May be stored with Transactions to link here
         |  * summary : A succinct summary
         |  * description : A longer description
         |  * charge : The charge to the customer for each one of these
         |
         |${userAuthenticationMessage(getTransactionTypesIsPublic)}""".stripMargin,
      transactionTypeJsonV200, transactionType,
      List(AuthenticatedUserIsRequired, BankNotFound, InvalidJsonFormat,
        InsufficientAuthorisationToCreateTransactionType, UnknownError),
      List(apiTagBank),
      None,
      http4sPartialFunction = Some(createTransactionType))

    // ─── getAtm ───────────────────────────────────────────────────────────────

    private val getAtmsIsPublic = APIUtil.getPropsAsBoolValue("apiOptions.getAtmsIsPublic", true)

    val getAtm: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "atms" / atmIdStr =>
        EndpointHelpers.withBank(req) { (bank, cc) =>
          for {
            _ <- if (!getAtmsIsPublic)
                   code.util.Helper.booleanToFuture(AuthenticatedUserIsRequired, failCode = 401, cc = Some(cc)) { cc.user.isDefined }
                 else Future.unit
            atm <- Future {
              unboxFullOrFail(
                Atms.atmsProvider.vend.getAtm(bank.bankId, AtmId(atmIdStr)),
                Some(cc), AtmNotFoundByAtmId, 404)
            }
          } yield JSONFactory1_4_0.createAtmJson(atm)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getAtm), "GET",
      "/banks/BANK_ID/atms/ATM_ID",
      "Get Bank ATM",
      s"""Returns information about ATM for a single bank specified by BANK_ID and ATM_ID including:
      |
      |* Address
      |* Geo Location
      |* License the data under this endpoint is released under
      |
      |${userAuthenticationMessage(!getAtmsIsPublic)}""".stripMargin,
      EmptyBody, atmJson,
      List(AuthenticatedUserIsRequired, BankNotFound, AtmNotFoundByAtmId, UnknownError),
      List(apiTagATM, apiTagOldStyle), None,
      http4sPartialFunction = Some(getAtm))

    // ─── getBranch ────────────────────────────────────────────────────────────

    private val getBranchesIsPublic = APIUtil.getPropsAsBoolValue("apiOptions.getBranchesIsPublic", true)

    val getBranch: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "branches" / branchIdStr =>
        EndpointHelpers.withBank(req) { (bank, cc) =>
          for {
            _ <- if (!getBranchesIsPublic)
                   code.util.Helper.booleanToFuture(AuthenticatedUserIsRequired, failCode = 401, cc = Some(cc)) { cc.user.isDefined }
                 else Future.unit
            branch <- Future {
              unboxFullOrFail(
                Branches.branchesProvider.vend.getBranch(bank.bankId, BranchId(branchIdStr)),
                Some(cc), BranchNotFoundByBranchId, 404)
            }
          } yield JSONFactory1_4_0.createBranchJson(branch)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getBranch), "GET",
      "/banks/BANK_ID/branches/BRANCH_ID",
      "Get Bank Branch",
      s"""Returns information about branches for a single bank specified by BANK_ID and BRANCH_ID including:
      | meta.license.id and eta.license.name fields must not be empty. 
      |
      |* Name
      |* Address
      |* Geo Location
      |* License the data under this endpoint is released under
      |
      |${userAuthenticationMessage(!getBranchesIsPublic)}""".stripMargin,
      EmptyBody, branchJson,
      List(AuthenticatedUserIsRequired, BranchNotFoundByBranchId, UnknownError),
      List(apiTagBranch, apiTagOldStyle), None,
      http4sPartialFunction = Some(getBranch))

    // ─── getProduct ───────────────────────────────────────────────────────────

    private val getProductsIsPublic = APIUtil.getPropsAsBoolValue("apiOptions.getProductsIsPublic", true)

    val getProduct: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "products" / productCodeStr =>
        EndpointHelpers.withBank(req) { (bank, cc) =>
          for {
            _ <- if (!getProductsIsPublic)
                   code.util.Helper.booleanToFuture(AuthenticatedUserIsRequired, failCode = 401, cc = Some(cc)) { cc.user.isDefined }
                 else Future.unit
            (product, _) <- NewStyle.function.getProduct(bank.bankId, ProductCode(productCodeStr), Some(cc))
          } yield JSONFactory210.createProductJson(product)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getProduct), "GET",
      "/banks/BANK_ID/products/PRODUCT_CODE",
      "Get Bank Product",
      s"""Returns information about the financial products offered by a bank specified by BANK_ID and PRODUCT_CODE including:
      |
      |* Name
      |* Code
      |* Category
      |* Family
      |* Super Family
      |* More info URL
      |* Description
      |* Terms and Conditions
      |* License the data under this endpoint is released under
      |${userAuthenticationMessage(!getProductsIsPublic)}""".stripMargin,
      EmptyBody, productJsonV210,
      List(AuthenticatedUserIsRequired, ProductNotFoundByProductCode, UnknownError),
      List(apiTagProduct), None,
      http4sPartialFunction = Some(getProduct))

    // ─── getProducts ──────────────────────────────────────────────────────────

    val getProducts: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "products" =>
        EndpointHelpers.withBank(req) { (bank, cc) =>
          for {
            _ <- if (!getProductsIsPublic)
                   code.util.Helper.booleanToFuture(AuthenticatedUserIsRequired, failCode = 401, cc = Some(cc)) { cc.user.isDefined }
                 else Future.unit
            params = req.uri.query.multiParams.map { case (k, vs) => GetProductsParam(k, vs.toList) }.toList
            (products, _) <- NewStyle.function.getProducts(bank.bankId, params, Some(cc))
          } yield JSONFactory210.createProductsJson(products)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getProducts), "GET",
      "/banks/BANK_ID/products",
      "Get Bank Products",
      s"""Returns information about the financial products offered by a bank specified by BANK_ID including:
      |
      |* Name
      |* Code
      |* Category
      |* Family
      |* Super Family
      |* More info URL
      |* Description
      |* Terms and Conditions
      |* License the data under this endpoint is released under
      |${userAuthenticationMessage(!getProductsIsPublic)}""".stripMargin,
      EmptyBody, productsJsonV210,
      List(AuthenticatedUserIsRequired, BankNotFound, ProductNotFoundByProductCode, UnknownError),
      List(apiTagProduct), None,
      http4sPartialFunction = Some(getProducts))

    // ─── createCustomer ───────────────────────────────────────────────────────

    val createCustomer: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "customers" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[PostCustomerJsonV210, CustomerJsonV210](req) { (user, bank, body, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(InvalidBankIdFormat, cc = Some(cc)) {
              isValidID(bank.bankId.value)
            }
            _ <- code.util.Helper.booleanToFuture(
              s"${InvalidJsonFormat} customer_number can not contain `::::` characters", cc = Some(cc)) {
              !`checkIfContains::::` (body.customer_number)
            }
            _ <- code.util.Helper.booleanToFuture(createCustomerEntitlementsRequiredText, failCode = 403, cc = Some(cc)) {
              APIUtil.hasAllEntitlements(bank.bankId.value, user.userId, createCustomerEntitlementsRequiredForSpecificBank) ||
              APIUtil.hasAllEntitlements("", user.userId, createCustomerEntitlementsRequiredForAnyBank)
            }
            _ <- code.util.Helper.booleanToFuture(CustomerNumberAlreadyExists, cc = Some(cc)) {
              CustomerX.customerProvider.vend.checkCustomerNumberAvailable(bank.bankId, body.customer_number)
            }
            userId = if (body.user_id.nonEmpty) body.user_id else user.userId
            (customerUser, _) <- NewStyle.function.findByUserId(userId, Some(cc))
            customer <- Future {
              CustomerX.customerProvider.vend.addCustomer(
                bank.bankId, body.customer_number, body.legal_name, body.mobile_phone_number, body.email,
                CustomerFaceImage(body.face_image.date, body.face_image.url),
                body.date_of_birth, body.relationship_status, body.dependants, body.dob_of_dependants,
                body.highest_education_attained, body.employment_status, body.kyc_status, body.last_ok_date,
                Option(CreditRating(body.credit_rating.rating, body.credit_rating.source)),
                Option(CreditLimit(body.credit_limit.currency, body.credit_limit.amount)),
                "", "", ""
              ).getOrElse(throw new RuntimeException(CreateConsumerError))
            }
            _ <- code.util.Helper.booleanToFuture(CustomerAlreadyExistsForUser, cc = Some(cc)) {
              UserCustomerLink.userCustomerLink.vend.getUserCustomerLink(userId, customer.customerId).isEmpty
            }
            _ <- Future {
              UserCustomerLink.userCustomerLink.vend
                .createUserCustomerLink(userId, customer.customerId, new Date(), true)
                .getOrElse(throw new RuntimeException(CreateUserCustomerLinksError))
            }
          } yield JSONFactory210.createCustomerJson(customer)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(createCustomer), "POST",
      "/banks/BANK_ID/customers",
      "Create Customer",
      s"""Add a customer linked to the user specified by user_id
      |The Customer resource stores the customer number, legal name, email, phone number, their date of birth, relationship status, education attained, a url for a profile image, KYC status etc.
      |Dates need to be in the format 2013-01-21T23:08:00Z
      |
      |${userAuthenticationMessage(true)}
      |
      |$createCustomeEntitlementsRequiredText
      |""",
      postCustomerJsonV210, customerJsonV210,
      List(AuthenticatedUserIsRequired, BankNotFound, InvalidJsonFormat, CustomerNumberAlreadyExists,
        UserNotFoundById, CustomerAlreadyExistsForUser, CreateConsumerError, UnknownError),
      List(apiTagCustomer, apiTagPerson, apiTagOldStyle),
      None,
      http4sPartialFunction = Some(createCustomer))

    // ─── getCustomersForUser ──────────────────────────────────────────────────

    val getCustomersForUser: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / "current" / "customers" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          Future {
            val customers = CustomerX.customerProvider.vend.getCustomersByUserId(user.userId)
            JSONFactory210.createCustomersJson(customers)
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getCustomersForUser), "GET",
      "/users/current/customers",
      "Get Customers for Current User",
      """Gets all Customers that are linked to a User.
        |
        |Authentication via OAuth is required.""",
      EmptyBody, customerJsonV210,
      List(AuthenticatedUserIsRequired, UserCustomerLinksNotFoundForUser, UnknownError),
      List(apiTagCustomer, apiTagUser, apiTagOldStyle), None,
      http4sPartialFunction = Some(getCustomersForUser))

    // ─── getCustomersForCurrentUserAtBank ──────────────────────────────────────

    val getCustomersForCurrentUserAtBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "customers" =>
        EndpointHelpers.withUserAndBank(req) { (user, bank, cc) =>
          for {
            (customers, _) <- Connector.connector.vend.getCustomersByUserId(user.userId, Some(cc)) map {
              connectorEmptyResponse(_, Some(cc))
            }
          } yield {
            val bankCustomers = customers.filter(_.bankId == bank.bankId.value)
            JSONFactory210.createCustomersJson(bankCustomers)
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getCustomersForCurrentUserAtBank), "GET",
      "/banks/BANK_ID/customers",
      "Get Customers for current User at Bank",
      s"""Returns a list of Customers at the Bank that are linked to the currently authenticated User.
         |
         |${userAuthenticationMessage(true)}""",
      EmptyBody, customerJSONs,
      List(
        AuthenticatedUserIsRequired,
        BankNotFound,
        UserCustomerLinksNotFoundForUser,
        UserCustomerLinksNotFoundForUser,
        CustomerNotFoundByCustomerId,
        UnknownError
      ),
      List(apiTagCustomer), None,
      http4sPartialFunction = Some(getCustomersForCurrentUserAtBank))

    // ─── updateBranch ─────────────────────────────────────────────────────────

    val updateBranch: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "branches" / branchIdStr =>
        EndpointHelpers.withUserAndBankAndBodyCreated[BranchJsonPutV210, JSONFactory1_4_0.BranchJson](req) { (user, bank, body, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(
              s"$InvalidJsonValue BANK_ID has to be the same in the URL and Body", failCode = 400, cc = Some(cc)) {
              body.bank_id == bank.bankId.value
            }
            _ <- code.util.Helper.booleanToFuture(UserHasMissingRoles + canUpdateBranch, failCode = 403, cc = Some(cc)) {
              APIUtil.hasEntitlement(bank.bankId.value, user.userId, canUpdateBranch)
            }
            branch <- NewStyle.function.tryons(CouldNotTransformJsonToInternalModel + " Branch", 400, Some(cc)) {
              JSONFactory210.transformToBranch(BranchId(branchIdStr), body).head
            }
            (success, _) <- NewStyle.function.createOrUpdateBranch(branch, Some(cc))
          } yield JSONFactory1_4_0.createBranchJson(success)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(updateBranch), "PUT",
      "/banks/BANK_ID/branches/BRANCH_ID",
      "Update Branch",
      s"""Update an existing branch for a bank account (Authenticated access).
         |${userAuthenticationMessage(true)}""",
      branchJsonPut, branchJson,
      List(AuthenticatedUserIsRequired, BankNotFound, InvalidJsonFormat, UserHasMissingRoles, UnknownError),
      List(apiTagBranch),
      Some(List(canUpdateBranch)),
      http4sPartialFunction = Some(updateBranch))

    // ─── createBranch ─────────────────────────────────────────────────────────

    val createBranch: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "branches" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[BranchJsonPostV210, JSONFactory1_4_0.BranchJson](req) { (user, bank, body, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(
              s"$InvalidJsonValue BANK_ID has to be the same in the URL and Body", failCode = 400, cc = Some(cc)) {
              body.bank_id == bank.bankId.value
            }
            _ <- code.util.Helper.booleanToFuture(
              s"${InsufficientAuthorisationToCreateBranch}",
              failCode = 403, cc = Some(cc)) {
              APIUtil.hasAllEntitlements(bank.bankId.value, user.userId, canCreateBranch :: Nil) ||
              APIUtil.hasAllEntitlements("", user.userId, canCreateBranchAtAnyBank :: Nil)
            }
            branch <- NewStyle.function.tryons(CouldNotTransformJsonToInternalModel + " Branch", 400, Some(cc)) {
              JSONFactory210.transformToBranch(body).head
            }
            (success, _) <- NewStyle.function.createOrUpdateBranch(branch, Some(cc))
          } yield JSONFactory1_4_0.createBranchJson(success)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(createBranch), "POST",
      "/banks/BANK_ID/branches",
      "Create Branch",
      s"""Create branch for the bank (Authenticated access).
          |${userAuthenticationMessage(true)}""",
      branchJsonPost, branchJson,
      List(AuthenticatedUserIsRequired, BankNotFound, InvalidJsonFormat, InsufficientAuthorisationToCreateBranch, UnknownError),
      List(apiTagBranch, apiTagOpenData),
      None,
      http4sPartialFunction = Some(createBranch))

    // ─── updateConsumerRedirectUrl ────────────────────────────────────────────

    val updateConsumerRedirectUrl: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "management" / "consumers" / consumerId / "consumer" / "redirect_url" =>
        EndpointHelpers.withUserAndBody[ConsumerRedirectUrlJSON, ConsumerJsonV210](req) { (user, body, cc) =>
          for {
            _ <- APIUtil.getPropsAsBoolValue("consumers_enabled_by_default", false) match {
              case true => Future.unit
              case false =>
                code.util.Helper.booleanToFuture(UserHasMissingRoles + canUpdateConsumerRedirectUrl, failCode = 403, cc = Some(cc)) {
                  APIUtil.hasEntitlement("", user.userId, canUpdateConsumerRedirectUrl)
                }
            }
            consumerIdLong <- NewStyle.function.tryons(InvalidConsumerId, 400, Some(cc)) {
              consumerId.toLong
            }
            consumer <- NewStyle.function.getConsumerByPrimaryId(consumerIdLong, Some(cc))
            _ <- code.util.Helper.booleanToFuture(UserNoPermissionUpdateConsumer, failCode = 400, cc = Some(cc)) {
              consumer.createdByUserId.equals(user.userId)
            }
            updatedConsumer <- NewStyle.function.updateConsumer(
              id          = consumer.id.get,
              isActive    = Some(APIUtil.getPropsAsBoolValue("consumers_enabled_by_default", false)),
              redirectURL = Some(body.redirect_url),
              callContext = Some(cc)
            )
          } yield JSONFactory210.createConsumerJSON(updatedConsumer)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(updateConsumerRedirectUrl), "PUT",
      "/management/consumers/CONSUMER_ID/consumer/redirect_url",
      "Update Consumer RedirectUrl",
      s"""Update an existing redirectUrl for a Consumer specified by CONSUMER_ID.
        |
        | CONSUMER_ID can be obtained after you register the application. 
        | 
        | Or use the endpoint 'Get Consumers' to get it  
        | 
      """.stripMargin,
      consumerRedirectUrlJSON, consumerJSON,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagConsumer),
      None,
      http4sPartialFunction = Some(updateConsumerRedirectUrl))

    // ─── getMetrics ───────────────────────────────────────────────────────────

    val getMetrics: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "metrics" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(UserHasMissingRoles + canReadMetrics, failCode = 403, cc = Some(cc)) {
              APIUtil.hasEntitlement("", user.userId, canReadMetrics)
            }
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(req.uri.renderString)
            (obpQueryParams, _) <- createQueriesByHttpParamsFuture(httpParams, Some(cc))
            metrics <- Future(APIMetrics.apiMetrics.vend.getAllMetrics(obpQueryParams))
          } yield JSONFactory210.createMetricsJson(metrics)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getMetrics), "GET",
      "/management/metrics",
      "Get Metrics",
      s"""Get the all metrics
         |
         |require CanReadMetrics role
         |
         |Filters Part 1.*filtering* (no wilde cards etc.) parameters to GET /management/metrics
         |
         |Should be able to filter on the following metrics fields
         |
         |eg: /management/metrics?from_date=$DateWithMsExampleString&to_date=$DateWithMsExampleString&limit=50&offset=2
         |
         |1 from_date (defaults to one week before current date): eg:from_date=$DateWithMsExampleString
         |
         |2 to_date (defaults to current date) eg:to_date=$DateWithMsExampleString
         |
         |3 limit (for pagination: defaults to 50)  eg:limit=200
         |
         |4 offset (for pagination: zero index, defaults to 0) eg: offset=10
         |
         |5 sort_by (defaults to date field) eg: sort_by=date
         |  possible values:
         |    "url",
         |    "date",
         |    "username" (or "user_name" for backward compatibility),
         |    "app_name",
         |    "developer_email",
         |    "implemented_by_partial_function",
         |    "implemented_in_version",
         |    "consumer_id",
         |    "verb"
         |
         |6 direction (defaults to date desc) eg: direction=desc
         |
         |eg: /management/metrics?from_date=$DateWithMsExampleString&to_date=$DateWithMsExampleString&limit=10000&offset=0&anon=false&app_name=TeatApp&implemented_in_version=v2.1.0&verb=POST&user_id=c7b6cb47-cb96-4441-8801-35b57456753a&username=susan.uk.29@example.com&consumer_id=78
         |
         |Other filters:
         |
         |7 consumer_id  (if null ignore)
         |
         |8 user_id (if null ignore)
         |
         |9 anon (if null ignore) only support two value : true (return where user_id is null.) or false (return where user_id is not null.)
         |
         |10 url (if null ignore), note: can not contain '&'.
         |
         |11 app_name (if null ignore)
         |
         |12 implemented_by_partial_function (if null ignore),
         |
         |13 implemented_in_version (if null ignore)
         |
         |14 verb (if null ignore)
         |
         |15 correlation_id (if null ignore)
         |
         |16 duration (if null ignore) non digit chars will be silently omitted
         |
      """.stripMargin,
      EmptyBody, metricsJson,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagMetric, apiTagApi),
      Some(List(canReadMetrics)),
      http4sPartialFunction = Some(getMetrics))

    // ─── allRoutes ────────────────────────────────────────────────────────────

    private val allOwnRoutes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
      root.run(req)
        .orElse(sandboxDataImport.run(req))
        .orElse(getTransactionRequestTypesSupportedByBank.run(req))
        .orElse(createTransactionRequest.run(req))
        .orElse(answerTransactionRequestChallenge.run(req))
        .orElse(getTransactionRequests.run(req))
        .orElse(getRoles.run(req))
        .orElse(getEntitlementsByBankAndUser.run(req))
        .orElse(getConsumers.run(req))
        .orElse(getConsumer.run(req))
        .orElse(enableDisableConsumers.run(req))
        .orElse(addCardForBank.run(req))
        .orElse(getUsers.run(req))
        .orElse(createTransactionType.run(req))
        .orElse(getAtm.run(req))
        .orElse(getBranch.run(req))
        .orElse(getProduct.run(req))
        .orElse(getProducts.run(req))
        .orElse(createCustomer.run(req))
        .orElse(getCustomersForUser.run(req))
        .orElse(getCustomersForCurrentUserAtBank.run(req))
        .orElse(updateBranch.run(req))
        .orElse(createBranch.run(req))
        .orElse(updateConsumerRedirectUrl.run(req))
        .orElse(getMetrics.run(req))
    }

    val allRoutesWithMiddleware: HttpRoutes[IO] = ResourceDocMiddleware.apply(resourceDocs)(IdempotencyMiddleware(allOwnRoutes))

    // ─── path-rewriting bridge: /obp/v2.1.0/… → /obp/v2.0.0/… ──────────────
    // Delegates to Http4s200 so all inherited v2.0.0/v1.4.0/v1.3.0/v1.2.1 endpoints
    // are served under the v2.1.0 URL prefix without duplicating logic.

    val v210ToV200Bridge: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
      val rawPath = req.uri.path.renderString
      if (rawPath.startsWith("/obp/v2.1.0/")) {
        val rewritten    = rawPath.replaceFirst("/obp/v2\\.1\\.0/", "/obp/v2.0.0/")
        val newUri       = req.uri.withPath(Uri.Path.unsafeFromString(rewritten))
        val rewrittenReq = req.withUri(newUri)
        code.api.v2_0_0.Http4s200.wrappedRoutesV200Services.run(rewrittenReq)
      } else {
        OptionT.none[IO, Response[IO]]
      }
    }
  }

  // Own middleware-wrapped routes take priority; inherited v2.0.0/v1.4.0/v1.3.0/v1.2.1 paths follow.
  val wrappedRoutesV210Services: HttpRoutes[IO] =
    Kleisli[HttpF, Request[IO], Response[IO]] { req =>
      Implementations2_1_0.allRoutesWithMiddleware.run(req)
        .orElse(Implementations2_1_0.v210ToV200Bridge.run(req))
    }
}
