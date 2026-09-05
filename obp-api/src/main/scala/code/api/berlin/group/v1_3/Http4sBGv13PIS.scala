package code.api.berlin.group.v1_3

import org.json4s._
import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.berlin.group.ConstantsBG
import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3._
import code.api.berlin.group.v1_3.model.TransactionStatus.mapTransactionStatus
import code.api.berlin.group.v1_3.model._
import code.api.util.APIUtil.{EmptyBody, ResourceDoc, UserOrApplication, getScaMethodAtInstance, getServerUrl, isValidCurrencyISOCode, mockedDataText, passesPsd2Pisp}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.CustomJsonFormats
import code.api.util.APIUtil.OBPReturnType
import code.api.util.{ApiTag, CallContext, Consent, NewStyle}
import code.api.util.http4s.Http4sRequestAttributes.{EndpointHelpers, RequestOps}
import code.api.util.http4s.{ErrorResponseConverter, RequestScopeConnection}
import code.fx.fx
import code.transactionrequests.TransactionRequests
import code.util.Helper
import code.util.Helper.{MdcLoggable, booleanToFuture}
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.TransactionRequestStatus._
import com.openbankproject.commons.model.enums.TransactionRequestTypes._
import com.openbankproject.commons.model.enums.{ChallengeType, PaymentServiceTypes, StrongCustomerAuthenticationStatus, SuppliedAnswerType, TransactionRequestStatus, TransactionRequestTypes}
import net.liftweb.common.Box.tryo
import net.liftweb.common.Full
import com.openbankproject.commons.util.json
import org.json4s.Formats
import com.openbankproject.commons.util.JsonAliases.prettyRender
import org.json4s.{Extraction => LiftExtraction}
import org.http4s._
import org.http4s.dsl.io._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

/**
 * Native http4s aggregator for Berlin Group v1.3 – Payment Initiation Service (PIS).
 * Ports all 24 PIS endpoints from code.api.builder.PaymentInitiationServicePISApi.
 * Route handlers declared as lazy val (avoids 64KB <init> limit for large objects).
 */
object Http4sBGv13PIS extends MdcLoggable {

  type HttpF[A] = OptionT[IO, A]

  implicit val formats: Formats = CustomJsonFormats.formats

  // ResourceDoc example bodies are written as `json.parse(...)` (JValue). Since the json4s
  // migration, JValue itself extends scala.Product, so an implicit JValue => JvalueCaseClass
  // conversion never fires. Each example body is therefore wrapped explicitly in
  // JvalueCaseClass(...) so resource-docs serialization takes its special-case path (no field
  // reflection; the jvalueToCaseclass wrapper key is stripped) instead of reflecting on a raw JObject.

  val implementedInApiVersion = ConstantsBG.berlinGroupVersion1
  val resourceDocs = ArrayBuffer[ResourceDoc]()

  val bgV13Prefix: Path =
    Root / ConstantsBG.berlinGroupVersion1.urlPrefix / ConstantsBG.berlinGroupVersion1.apiShortVersion

  // ── private helpers (ported from APIMethods_PaymentInitiationServicePISApi) ─

  private def checkPaymentServerTypeError(paymentService: String) = {
    s"${InvalidTransactionRequestType.replaceAll("TRANSACTION_REQUEST_TYPE", "PAYMENT_SERVICE in the URL.")}: '${paymentService}'.It should be `payments` or `periodic-payments` for now, will support `bulk-payments` soon"
  }

  private def checkPaymentProductError(paymentProduct: String) =
    s"${InvalidTransactionRequestType.replaceAll("TRANSACTION_REQUEST_TYPE", "PAYMENT_PRODUCT in the URL.")}: '${paymentProduct}'.It should be `sepa-credit-transfers`for now, will support (instant-sepa-credit-transfers, target-2-payments, cross-border-credit-transfers) soon."

  private def checkPaymentServiceType(paymentService: String) = tryo {
    PaymentServiceTypes.withName(paymentService.replaceAll("-", "_"))
  }.isDefined

  /**
   * Fetch a payment the caller is entitled to address.
   *
   * Berlin Group names a payment by its id alone — there is no account in the path — so nothing in
   * the route ties the payment to whoever is calling. Fetching one must therefore also establish
   * that the caller is the party that lodged it; otherwise any authenticated TPP holding a paymentId
   * could read another TPP's payment, list or start authorisations on it, or cancel it. Under
   * NextGenPSD2 a payment initiation resource belongs to the TPP that created it, and only that TPP
   * addresses it afterwards.
   *
   * Two things have to line up, because Berlin Group binds a payment to the TPP and the ASPSP
   * separately knows which PSU it is for.
   *
   *  - The TPP. The consumer that lodged the payment is recorded on it, and a caller presenting a
   *    different one is refused even when it is acting for the same PSU: one TPP's mandate over a
   *    payment is not another's. Payments lodged before the consumer was recorded carry none, and
   *    fall back to the person check alone rather than becoming unaddressable.
   *  - The person. A payment records the principal that lodged it and, when it was lodged under a
   *    consent, the PSU it was lodged for; a caller presents the same two. Any overlap is enough, so
   *    a payment lodged on a client-credentials token can still be authorised under the PSU's token
   *    and the other way round. A payment carrying neither identity belongs to nobody.
   */
  private def getOwnPaymentImpl(paymentId: String, callContext: Option[CallContext]): OBPReturnType[TransactionRequest] =
    for {
      (transactionRequest, callContext) <- NewStyle.function.getTransactionRequestImpl(TransactionRequestId(paymentId), callContext)
      initiators = Set(transactionRequest.user_id, transactionRequest.on_behalf_of_user_id).flatten.filter(_.nonEmpty)
      callers = callContext.toSet[CallContext].flatMap(cc => cc.user.toOption.map(_.userId) ++ Consent.actingPsu(cc).map(_.userId))
      callingConsumer = callContext.flatMap(_.consumer.map(_.consumerId))
      // Read straight off the stored row rather than through the TransactionRequest model: which
      // TPP lodged a payment is this guard's business, not something every REST connector needs on
      // the wire, and that model's shape is a frozen contract.
      lodgedByConsumer = TransactionRequests.transactionRequestProvider.vend
        .getMappedTransactionRequest(TransactionRequestId(paymentId))
        .toOption.flatMap(tr => Consent.present(tr.consumerId))
      sameTpp = lodgedByConsumer.forall(lodgedBy => callingConsumer.contains(lodgedBy))
      _ <- Helper.booleanToFuture(s"$PaymentNotInitiatedByCaller Payment id: $paymentId.", 403, callContext) {
        sameTpp && initiators.exists(callers)
      }
    } yield (transactionRequest, callContext)

  /**
   * Shared business logic for all three initiate-payment variants (payments / periodic-payments /
   * bulk-payments). Mirrors `initiatePaymentImplementation` from the Lift builder; auth is handled
   * by middleware (authMode = UserOrApplication), so no inline applicationAccess call.
   */
  private def initiatePaymentImpl(
    paymentService: String,
    paymentProduct: String,
    callContext: Option[CallContext]
  ): Future[org.json4s.JValue] = {
    val u = callContext.flatMap(_.user.toOption)
    val rawBody = callContext.flatMap(_.httpBody).getOrElse("")
    val bodyJson = scala.util.Try(json.parse(rawBody)).getOrElse(json.JNothing)
    for {
      _ <- passesPsd2Pisp(callContext)
      paymentServiceType <- NewStyle.function.tryons(checkPaymentServerTypeError(paymentService), 404, callContext) {
        PaymentServiceTypes.withName(paymentService.replaceAll("-", "_"))
      }
      transactionRequestType <- NewStyle.function.tryons(checkPaymentProductError(paymentProduct), 404, callContext) {
        TransactionRequestTypes.withName(paymentProduct.replaceAll("-", "_").toUpperCase)
      }
      sepaCreditTransfersBerlinGroupV13 <- if (paymentServiceType.equals(PaymentServiceTypes.payments)) {
        NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $SepaCreditTransfersBerlinGroupV13 ", 400, callContext) {
          bodyJson.extract[SepaCreditTransfersBerlinGroupV13]
        }
      } else if (paymentServiceType.equals(PaymentServiceTypes.periodic_payments)) {
        NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PeriodicSepaCreditTransfersBerlinGroupV13 ", 400, callContext) {
          bodyJson.extract[PeriodicSepaCreditTransfersBerlinGroupV13]
        }
      } else {
        Future { throw new RuntimeException(checkPaymentServerTypeError(paymentServiceType.toString)) }
      }
      isValidAmountNumber <- NewStyle.function.tryons(s"$InvalidNumber Current input is ${sepaCreditTransfersBerlinGroupV13.instructedAmount.amount} ", 400, callContext) {
        BigDecimal(sepaCreditTransfersBerlinGroupV13.instructedAmount.amount)
      }
      _ <- Helper.booleanToFuture(s"${NotPositiveAmount} Current input is: '${isValidAmountNumber}'", cc = callContext) {
        isValidAmountNumber > BigDecimal("0")
      }
      _ <- Helper.booleanToFuture(s"${InvalidISOCurrencyCode} Current input is: '${sepaCreditTransfersBerlinGroupV13.instructedAmount.currency}'", cc = callContext) {
        isValidCurrencyISOCode(sepaCreditTransfersBerlinGroupV13.instructedAmount.currency)
      }
      _ <- NewStyle.function.isEnabledTransactionRequests(callContext)
      (createdTransactionRequest, _) <- transactionRequestType match {
        case TransactionRequestTypes.SEPA_CREDIT_TRANSFERS =>
          NewStyle.function.createTransactionRequestBGV1(
            initiator = u,
            paymentServiceType,
            transactionRequestType,
            transactionRequestBody = sepaCreditTransfersBerlinGroupV13,
            callContext
          )
      }
    } yield {
      LiftExtraction.decompose(JSONFactory_BERLIN_GROUP_1_3.createTransactionRequestJson(createdTransactionRequest))
    }
  }

  // ── DELETE /{paymentService}/{paymentProduct}/{paymentId} ──────────────────────────────────────────────────────
  // Variable response: 202 (SCA required) with CancelPaymentResponseJson body, or 204 (direct cancel) with no body.
  // Custom IO handler to produce truly-empty 204 (NoContent) — executeFutureWithStatus would always add a body.
  lazy val cancelPayment: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ DELETE -> `bgV13Prefix` / paymentService / paymentProduct / paymentId =>
      implicit val cc: CallContext = req.callContext
      val callContext = Some(cc)
      RequestScopeConnection.fromFuture {
        val u = cc.user.openOrThrowException(AuthenticatedUserIsRequired)
        for {
          _ <- passesPsd2Pisp(callContext)
          _ <- NewStyle.function.tryons(checkPaymentServerTypeError(paymentService), 404, callContext) {
            PaymentServiceTypes.withName(paymentService.replaceAll("-", "_"))
          }
          transactionRequestTypes <- NewStyle.function.tryons(checkPaymentProductError(paymentProduct), 404, callContext) {
            TransactionRequestTypes.withName(paymentProduct.replaceAll("-", "_").toUpperCase)
          }
          (transactionRequest, _) <- getOwnPaymentImpl(paymentId, callContext)
          transactionRequestBody <- NewStyle.function.tryons(s"${UnknownError} No data for Payment Body ", 400, callContext) {
            transactionRequest.body.to_sepa_credit_transfers.get
          }
          fromAccountIban = transactionRequestBody.debtorAccount.iban
          toAccountIban   = transactionRequestBody.creditorAccount.iban
          (_, _)          <- NewStyle.function.getBankAccountByIban(fromAccountIban, callContext)
          (ibanChecker, _) <- NewStyle.function.validateAndCheckIbanNumber(toAccountIban, callContext)
          _ <- Helper.booleanToFuture(invalidIban, cc = callContext) { ibanChecker.isValid == true }
          (_, _) <- NewStyle.function.getToBankAccountByIban(toAccountIban, callContext)
          currentStatus = transactionRequest.status.toUpperCase()
          mappedStatus  = mapTransactionStatus(currentStatus)
          (canBeCancelled, _, startSca) <- transactionRequestTypes match {
            case TransactionRequestTypes.SEPA_CREDIT_TRANSFERS =>
              currentStatus match {
                case TransactionStatus.RCVD.code | "INITIATED" =>
                  NewStyle.function.saveTransactionRequestStatusImpl(transactionRequest.id, CANCELLED.toString, callContext) map { _ =>
                    (true, callContext, Some(false))
                  }
                case TransactionStatus.ACCP.code | "COMPLETED" | TransactionStatus.PDNG.code | "PENDING" =>
                  NewStyle.function.cancelPaymentV400(TransactionId(transactionRequest.transaction_ids), callContext) flatMap { x =>
                    x._1 match {
                      case CancelPayment(true, Some(startSca)) if startSca =>
                        NewStyle.function.saveTransactionRequestStatusImpl(transactionRequest.id, CANCELLATION_PENDING.toString, callContext) map { _ =>
                          (true, x._2, Some(startSca))
                        }
                      case CancelPayment(true, Some(startSca)) if !startSca =>
                        NewStyle.function.saveTransactionRequestStatusImpl(transactionRequest.id, CANCELLED.toString, callContext) map { _ =>
                          (true, x._2, Some(startSca))
                        }
                      case CancelPayment(false, _) =>
                        Future.successful((false, x._2, Some(false)))
                    }
                  }
                case TransactionStatus.CANC.code | "CANCELLED" =>
                  Future.successful((true, callContext, Some(false)))
                case _ =>
                  Future.successful((false, callContext, Some(false)))
              }
          }
          _ <- Helper.booleanToFuture(
            failMsg = s"$TransactionRequestCannotBeCancelled Payment status: $mappedStatus. Only payments in RCVD, ACCP, PDNG, or CANC status can be cancelled.",
            cc = callContext
          ) { canBeCancelled == true }
          (updatedTransactionRequest, _) <- getOwnPaymentImpl(paymentId, callContext)
        } yield {
          startSca.getOrElse(false) match {
            case true  => Some(createCancellationTransactionRequestJson(updatedTransactionRequest))
            case false => None
          }
        }
      }.attempt.flatMap {
        case Right(Some(cancelJson)) =>
          Accepted(prettyRender(LiftExtraction.decompose(cancelJson)))
        case Right(None) =>
          NoContent()
        case Left(err) =>
          ErrorResponseConverter.toHttp4sResponse(err, cc)
      }
  }

  // ── GET /{paymentService}/{paymentProduct}/{paymentId}/cancellation-authorisations/{cancellationId} ───
  lazy val getPaymentCancellationScaStatus: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV13Prefix` / paymentService / paymentProduct / paymentId / "cancellation-authorisations" / cancellationId =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        val callContext = Some(cc)
        for {
          _ <- passesPsd2Pisp(callContext)
          _ <- NewStyle.function.tryons(checkPaymentServerTypeError(paymentService), 404, callContext) {
            PaymentServiceTypes.withName(paymentService.replaceAll("-", "_"))
          }
          _ <- NewStyle.function.tryons(checkPaymentProductError(paymentProduct), 404, callContext) {
            TransactionRequestTypes.withName(paymentProduct.replaceAll("-", "_").toUpperCase)
          }
          (_, _) <- getOwnPaymentImpl(paymentId, callContext)
          (challenge, _) <- NewStyle.function.getChallenge(cancellationId, callContext)
        } yield {
          JSONFactory_BERLIN_GROUP_1_3.ScaStatusJsonV13(challenge.scaStatus.map(_.toString).getOrElse("None"))
        }
      }
  }

  // ── GET /{paymentService}/{paymentProduct}/{paymentId} (with checkPaymentServiceType guard in Lift) ──
  lazy val getPaymentInformation: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV13Prefix` / paymentService / paymentProduct / paymentId if checkPaymentServiceType(paymentService) =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        val callContext = Some(cc)
        for {
          _ <- passesPsd2Pisp(callContext)
          _ <- NewStyle.function.tryons(checkPaymentServerTypeError(paymentService), 404, callContext) {
            PaymentServiceTypes.withName(paymentService.replaceAll("-", "_"))
          }
          _ <- NewStyle.function.tryons(checkPaymentProductError(paymentProduct), 404, callContext) {
            TransactionRequestTypes.withName(paymentProduct.replaceAll("-", "_").toUpperCase)
          }
          (transactionRequest, _) <- getOwnPaymentImpl(paymentId, callContext)
          transactionRequestBody <- NewStyle.function.tryons(s"${UnknownError} No data for Payment Body ", 400, callContext) {
            transactionRequest.body.to_sepa_credit_transfers.get
          }
        } yield {
          transactionRequestBody
        }
      }
  }

  // ── GET /{paymentService}/{paymentProduct}/{paymentId}/authorisations ─────────────────────────────
  lazy val getPaymentInitiationAuthorisation: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV13Prefix` / paymentService / paymentProduct / paymentId / "authorisations" =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        val callContext = Some(cc)
        for {
          _ <- passesPsd2Pisp(callContext)
          _ <- NewStyle.function.tryons(checkPaymentServerTypeError(paymentService), 404, callContext) {
            PaymentServiceTypes.withName(paymentService.replaceAll("-", "_"))
          }
          _ <- NewStyle.function.tryons(checkPaymentProductError(paymentProduct), 404, callContext) {
            TransactionRequestTypes.withName(paymentProduct.replaceAll("-", "_").toUpperCase)
          }
          (_, _) <- getOwnPaymentImpl(paymentId, callContext)
          (challenges, _) <- NewStyle.function.getChallengesByTransactionRequestId(paymentId, callContext)
        } yield {
          JSONFactory_BERLIN_GROUP_1_3.createStartPaymentAuthorisationsJson(challenges)
        }
      }
  }

  // ── GET /{paymentService}/{paymentProduct}/{paymentId}/cancellation-authorisations ──────────────────
  lazy val getPaymentInitiationCancellationAuthorisationInformation: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV13Prefix` / paymentService / paymentProduct / paymentId / "cancellation-authorisations" =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        val callContext = Some(cc)
        for {
          _ <- passesPsd2Pisp(callContext)
          _ <- NewStyle.function.tryons(checkPaymentServerTypeError(paymentService), 404, callContext) {
            PaymentServiceTypes.withName(paymentService.replaceAll("-", "_"))
          }
          _ <- NewStyle.function.tryons(checkPaymentProductError(paymentProduct), 404, callContext) {
            TransactionRequestTypes.withName(paymentProduct.replaceAll("-", "_").toUpperCase)
          }
          (_, _) <- getOwnPaymentImpl(paymentId, callContext)
          (challenges, _) <- NewStyle.function.getChallengesByTransactionRequestId(paymentId, callContext)
        } yield {
          JSONFactory_BERLIN_GROUP_1_3.CancellationJsonV13(challenges.map(_.challengeId))
        }
      }
  }

  // ── GET /{paymentService}/{paymentProduct}/{paymentId}/authorisations/{authorisationId} ────────────
  lazy val getPaymentInitiationScaStatus: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV13Prefix` / paymentService / paymentProduct / paymentId / "authorisations" / authorisationId =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        val callContext = Some(cc)
        for {
          _ <- passesPsd2Pisp(callContext)
          _ <- NewStyle.function.tryons(checkPaymentServerTypeError(paymentService), 404, callContext) {
            PaymentServiceTypes.withName(paymentService.replaceAll("-", "_"))
          }
          _ <- NewStyle.function.tryons(checkPaymentProductError(paymentProduct), 404, callContext) {
            TransactionRequestTypes.withName(paymentProduct.replaceAll("-", "_").toUpperCase)
          }
          (_, _) <- getOwnPaymentImpl(paymentId, callContext)
          (challenge, _) <- NewStyle.function.getChallenge(authorisationId, callContext)
        } yield {
          json.parse(s"""{"scaStatus" : "${challenge.scaStatus.getOrElse("None")}"}""")
        }
      }
  }

  // ── GET /{paymentService}/{paymentProduct}/{paymentId}/status ─────────────────────────────────────
  lazy val getPaymentInitiationStatus: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `bgV13Prefix` / paymentService / paymentProduct / paymentId / "status" =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        val callContext = Some(cc)
        import org.json4s.JsonDSL._
        for {
          _ <- passesPsd2Pisp(callContext)
          _ <- NewStyle.function.tryons(checkPaymentServerTypeError(paymentService), 404, callContext) {
            PaymentServiceTypes.withName(paymentService.replaceAll("-", "_"))
          }
          _ <- NewStyle.function.tryons(checkPaymentProductError(paymentProduct), 404, callContext) {
            TransactionRequestTypes.withName(paymentProduct.replaceAll("-", "_").toUpperCase)
          }
          (transactionRequest, _) <- getOwnPaymentImpl(paymentId, callContext)
          transactionRequestStatus = mapTransactionStatus(transactionRequest.status)
          transactionRequestAmount <- NewStyle.function.tryons(s"${InvalidNumber} transaction request amount cannot convert to a Decimal", 400, callContext) {
            BigDecimal(transactionRequest.body.to_sepa_credit_transfers.get.instructedAmount.amount)
          }
          transactionRequestCurrency <- NewStyle.function.tryons(s"${InvalidCurrency} can not get currency from this paymentId(${paymentId})", 400, callContext) {
            transactionRequest.body.to_sepa_credit_transfers.get.instructedAmount.currency
          }
          transactionRequestFromAccount = transactionRequest.from
          (fromAccount, _) <- NewStyle.function.checkBankAccountExists(
            BankId(transactionRequestFromAccount.bank_id),
            AccountId(transactionRequestFromAccount.account_id),
            callContext
          )
          fromAccountBalance  = fromAccount.balance
          fromAccountCurrency = fromAccount.currency
          rate = fx.exchangeRate(transactionRequestCurrency, fromAccountCurrency, None, callContext)
          _ <- Helper.booleanToFuture(s"$InvalidCurrency The requested currency conversion (${transactionRequestCurrency} to ${fromAccountCurrency}) is not supported.", cc = callContext) {
            rate.isDefined
          }
          requestChangedCurrencyAmount = fx.convert(transactionRequestAmount, rate)
          fundsAvailable = (fromAccountBalance >= requestChangedCurrencyAmount)
          transactionRequestStatusCheckedFunds = if (fundsAvailable) transactionRequestStatus else TransactionStatus.RCVD.code
        } yield {
          ("transactionStatus" -> transactionRequestStatusCheckedFunds) ~
            ("fundsAvailable"  -> fundsAvailable)
        }
      }
  }

  // ── POST /payments/{paymentProduct} ──────────────────────────────────────────────────────────────
  // Auth: applicationAccess in Lift → authMode = UserOrApplication in ResourceDoc
  lazy val initiatePayments: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `bgV13Prefix` / "payments" / paymentProduct =>
      EndpointHelpers.executeFutureCreated(req) {
        initiatePaymentImpl("payments", paymentProduct, Some(req.callContext))
      }
  }

  // ── POST /periodic-payments/{paymentProduct} ──────────────────────────────────────────────────────
  lazy val initiatePeriodicPayments: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `bgV13Prefix` / "periodic-payments" / paymentProduct =>
      EndpointHelpers.executeFutureCreated(req) {
        initiatePaymentImpl("periodic-payments", paymentProduct, Some(req.callContext))
      }
  }

  // ── POST /bulk-payments/{paymentProduct} ──────────────────────────────────────────────────────────
  lazy val initiateBulkPayments: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `bgV13Prefix` / "bulk-payments" / paymentProduct =>
      EndpointHelpers.executeFutureCreated(req) {
        initiatePaymentImpl("bulk-payments", paymentProduct, Some(req.callContext))
      }
  }

  // ── POST /{paymentService}/{paymentProduct}/{paymentId}/authorisations (3 body-guard variants) ───
  //
  // Dispatches on the request body:
  //   scaAuthenticationData → startPaymentAuthorisationTransactionAuthorisation (real SCA logic)
  //   psuData               → startPaymentAuthorisationUpdatePsuAuthentication   (mocked)
  //   authenticationMethodId → startPaymentAuthorisationSelectPsuAuthenticationMethod (mocked)
  lazy val startPaymentAuthorisationAll: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `bgV13Prefix` / paymentService / paymentProduct / paymentId / "authorisations" =>
      EndpointHelpers.executeFutureCreated(req) {
        val cc          = req.callContext
        val callContext = Some(cc)
        val u           = cc.user.openOrThrowException(AuthenticatedUserIsRequired)
        val parsedJson  = scala.util.Try(json.parse(cc.httpBody.getOrElse(""))).getOrElse(json.JNothing)
        if (startsAuthorisation(parsedJson)) {
          for {
            _ <- passesPsd2Pisp(callContext)
            _ <- NewStyle.function.tryons(checkPaymentServerTypeError(paymentService), 404, callContext) {
              PaymentServiceTypes.withName(paymentService.replaceAll("-", "_"))
            }
            _ <- NewStyle.function.tryons(checkPaymentProductError(paymentProduct), 404, callContext) {
              TransactionRequestTypes.withName(paymentProduct.replaceAll("-", "_").toUpperCase)
            }
            (_, _) <- getOwnPaymentImpl(paymentId, callContext)
            (challenges, _) <- NewStyle.function.createChallengesC2(
              List(u.userId),
              ChallengeType.BERLIN_GROUP_PAYMENT_CHALLENGE,
              Some(paymentId),
              getScaMethodAtInstance(SEPA_CREDIT_TRANSFERS.toString).toOption,
              Some(StrongCustomerAuthenticationStatus.received),
              None,
              None,
              callContext
            )
            challenge <- NewStyle.function.tryons(InvalidConnectorResponseForCreateChallenge, 400, callContext) {
              challenges.head
            }
          } yield {
            JSONFactory_BERLIN_GROUP_1_3.createStartPaymentAuthorisationJson(challenge)
          }
        } else if (checkUpdatePsuAuthentication(parsedJson) || checkSelectPsuAuthenticationMethod(parsedJson)) {
          // Mocked for the updatePsuAuthentication and selectPsuAuthenticationMethod variants, which
          // are Embedded-approach steps OBP does not implement. Guarded now: this was the
          // unconditional final else, so any body the server could not recognise was answered with
          // this example -- a fabricated authorisationId, returned 201, that matches no challenge.
          // The TPP only discovers it at the PUT, where the id resolves to nothing.
          Future.successful(json.parse(
            """{
              "challengeData": {
                "scaStatus": "received",
                "authorisationId": "88695566-6642-46d5-9985-0d824624f507",
                "psuMessage": "Please check your SMS at a mobile device.",
                "_links": {
                  "scaStatus": "/v1.3/payments/sepa-credit-transfers/88695566-6642-46d5-9985-0d824624f507"
                }
              }
            }"""))
        } else {
          // None of the recognised shapes. A malformed request has to be reported as one; handing
          // back an id that was never minted only moves the failure somewhere harder to read.
          Helper.booleanToFuture(
            failMsg = s"$InvalidJsonFormat The Json body should be empty, or one of " +
              s"updatePsuAuthentication, selectPsuAuthenticationMethod or transactionAuthorisation.",
            failCode = 400, cc = callContext)(false).map(_ => json.parse("{}"))
        }
      }
  }

  // ── POST /{paymentService}/{paymentProduct}/{paymentId}/cancellation-authorisations (3 variants) ─
  lazy val startPaymentInitiationCancellationAuthorisationAll: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `bgV13Prefix` / paymentService / paymentProduct / paymentId / "cancellation-authorisations" =>
      EndpointHelpers.executeFutureCreated(req) {
        val cc          = req.callContext
        val callContext = Some(cc)
        val u           = cc.user.openOrThrowException(AuthenticatedUserIsRequired)
        val parsedJson  = scala.util.Try(json.parse(cc.httpBody.getOrElse(""))).getOrElse(json.JNothing)
        if (startsAuthorisation(parsedJson)) {
          for {
            _ <- passesPsd2Pisp(callContext)
            _ <- NewStyle.function.tryons(checkPaymentServerTypeError(paymentService), 404, callContext) {
              PaymentServiceTypes.withName(paymentService.replaceAll("-", "_"))
            }
            _ <- NewStyle.function.tryons(checkPaymentProductError(paymentProduct), 404, callContext) {
              TransactionRequestTypes.withName(paymentProduct.replaceAll("-", "_").toUpperCase)
            }
            (transactionRequest, _) <- getOwnPaymentImpl(paymentId, callContext)
            _ <- Helper.booleanToFuture(failMsg = CannotStartTheAuthorisationProcessForTheCancellation, cc = callContext) {
              transactionRequest.status == TransactionRequestStatus.CANCELLATION_PENDING.toString
            }
            (challenges, _) <- NewStyle.function.createChallengesC2(
              List(u.userId),
              ChallengeType.BERLIN_GROUP_PAYMENT_CHALLENGE,
              Some(paymentId),
              getScaMethodAtInstance(SEPA_CREDIT_TRANSFERS.toString).toOption,
              Some(StrongCustomerAuthenticationStatus.received),
              None,
              None,
              callContext
            )
            challenge <- NewStyle.function.tryons(InvalidConnectorResponseForCreateChallenge, 400, callContext) {
              challenges.head
            }
          } yield {
            JSONFactory_BERLIN_GROUP_1_3.createStartPaymentInitiationCancellationAuthorisation(
              challenge, paymentService, paymentProduct, paymentId
            )
          }
        } else if (checkUpdatePsuAuthentication(parsedJson) || checkSelectPsuAuthenticationMethod(parsedJson)) {
          // Mocked for the updatePsuAuthentication and selectPsuAuthenticationMethod variants, which
          // are Embedded-approach steps OBP does not implement. Guarded now: this was the
          // unconditional final else, so any body the server could not recognise was answered with
          // this example -- a fabricated authorisationId, returned 201, that matches no challenge.
          // The TPP only discovers it at the PUT, where the id resolves to nothing.
          Future.successful(json.parse(
            """{
              "scaStatus": "received",
              "authorisationId": "123auth456",
              "psuMessage": "Please use your BankApp for transaction Authorisation.",
              "_links": {
                "scaStatus": {
                  "href": "/v1.3/payments/qwer3456tzui7890/authorisations/123auth456"
                }
              }
            }"""))
        } else {
          // None of the recognised shapes. A malformed request has to be reported as one; handing
          // back an id that was never minted only moves the failure somewhere harder to read.
          Helper.booleanToFuture(
            failMsg = s"$InvalidJsonFormat The Json body should be empty, or one of " +
              s"updatePsuAuthentication, selectPsuAuthenticationMethod or transactionAuthorisation.",
            failCode = 400, cc = callContext)(false).map(_ => json.parse("{}"))
        }
      }
  }

  // ── PUT /{paymentService}/{paymentProduct}/{paymentId}/cancellation-authorisations/{authorisationId} (4 variants) ─
  lazy val updatePaymentCancellationPsuDataAll: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ PUT -> `bgV13Prefix` / paymentService / paymentProduct / paymentId / "cancellation-authorisations" / authorisationId =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        val callContext = Some(cc)
        val parsedJson  = scala.util.Try(json.parse(cc.httpBody.getOrElse(""))).getOrElse(json.JNothing)
        if (checkTransactionAuthorisation(parsedJson)) {
          for {
            _ <- passesPsd2Pisp(callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $UpdatePaymentPsuDataJson "
            transactionAuthorisation <- NewStyle.function.tryons(failMsg, 400, callContext) {
              parsedJson.extract[TransactionAuthorisation]
            }
            _ <- NewStyle.function.tryons(checkPaymentServerTypeError(paymentService), 404, callContext) {
              PaymentServiceTypes.withName(paymentService.replaceAll("-", "_"))
            }
            _ <- NewStyle.function.tryons(checkPaymentProductError(paymentProduct), 404, callContext) {
              TransactionRequestTypes.withName(paymentProduct.replaceAll("-", "_").toUpperCase)
            }
            transactionRequestId = TransactionRequestId(paymentId)
            (existingTransactionRequest, _) <- getOwnPaymentImpl(transactionRequestId.value, callContext)
            _ <- Helper.booleanToFuture(failMsg = CannotUpdatePSUDataCancellation, cc = callContext) {
              existingTransactionRequest.status == TransactionRequestStatus.INITIATED.toString ||
              existingTransactionRequest.status == TransactionRequestStatus.CANCELLATION_PENDING.toString ||
              existingTransactionRequest.status == TransactionRequestStatus.COMPLETED.toString
            }
            (_, _) <- getOwnPaymentImpl(paymentId, callContext)
            (challenge, _) <- NewStyle.function.validateChallengeAnswerC4(
              ChallengeType.BERLIN_GROUP_PAYMENT_CHALLENGE,
              Some(paymentId),
              None,
              authorisationId,
              transactionAuthorisation.scaAuthenticationData,
              SuppliedAnswerType.PLAIN_TEXT_VALUE,
              callContext
            )
            (fromAccount, _) <- NewStyle.function.checkBankAccountExists(
              BankId(existingTransactionRequest.from.bank_id),
              AccountId(existingTransactionRequest.from.account_id),
              callContext
            )
            _ <- challenge.scaStatus match {
              case Some(status) if status == StrongCustomerAuthenticationStatus.finalised =>
                NewStyle.function.saveTransactionRequestStatusImpl(existingTransactionRequest.id, CANCELLED.toString, callContext)
              case Some(status) if status == StrongCustomerAuthenticationStatus.failed =>
                NewStyle.function.saveTransactionRequestStatusImpl(existingTransactionRequest.id, REJECTED.toString, callContext)
              case _ =>
                Future(Full(true))
            }
          } yield {
            JSONFactory_BERLIN_GROUP_1_3.createStartPaymentCancellationAuthorisationJson(
              challenge, paymentService, paymentProduct, paymentId
            )
          }
        } else if (checkUpdatePsuAuthentication(parsedJson)) {
          Future.successful(json.parse(
            """{
              "scaStatus": "psuAuthenticated",
              "_links": {
                "authoriseTransaction": {"href": "/psd2/v1.3/payments/1234-wertiq-983/authorisations/123auth456"}
              }
            }"""))
        } else if (checkSelectPsuAuthenticationMethod(parsedJson)) {
          Future.successful(json.parse(
            """{
              "scaStatus": "scaMethodSelected",
              "chosenScaMethod": {
                "authenticationType": "SMS_OTP",
                "authenticationMethodId": "myAuthenticationID"},
              "challengeData": {
                "otpMaxLength": 6,
                "otpFormat": "integer"},
              "_links": {
                "authoriseTransaction": {"href": "/psd2/v1.3/payments/1234-wertiq-983/authorisations/123auth456"}
              }
            }"""))
        } else if (checkAuthorisationConfirmation(parsedJson)) {
          // authorisationConfirmation variant. Guarded by the checker that already existed for it:
          // this was the unconditional final else, so a body matching none of the four shapes -- an
          // empty one included -- was answered "scaStatus": "finalised", the terminal success state
          // of strong customer authentication, for an authorisation nothing had happened to.
          Future.successful(json.parse(
            """{
              "scaStatus": "finalised",
              "_links":{
                "status":  {"href":"/v1.3/payments/sepa-credit-transfers/qwer3456tzui7890/status"}
              }
            }"""))
        } else {
          // None of the four Berlin Group shapes. Malformed, and it has to say so: claiming an SCA
          // outcome for a request the server could not read is worse than any of them.
          Helper.booleanToFuture(
            failMsg = s"$InvalidJsonFormat The Json body should be one of updatePsuAuthentication, " +
              s"selectPsuAuthenticationMethod, transactionAuthorisation or authorisationConfirmation.",
            failCode = 400, cc = callContext)(false).map(_ => json.parse("{}"))
        }
      }
  }

  // ── PUT /{paymentService}/{paymentProduct}/{paymentId}/authorisations/{authorisationId} (4 variants) ─
  lazy val updatePaymentPsuDataAll: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ PUT -> `bgV13Prefix` / paymentService / paymentProduct / paymentId / "authorisations" / authorisationId =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        val callContext = Some(cc)
        val parsedJson  = scala.util.Try(json.parse(cc.httpBody.getOrElse(""))).getOrElse(json.JNothing)
        if (checkTransactionAuthorisation(parsedJson)) {
          val u = cc.user.openOrThrowException(AuthenticatedUserIsRequired)
          for {
            _ <- passesPsd2Pisp(callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $TransactionAuthorisation "
            transactionAuthorisationJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
              parsedJson.extract[TransactionAuthorisation]
            }
            _ <- NewStyle.function.tryons(checkPaymentServerTypeError(paymentService), 404, callContext) {
              PaymentServiceTypes.withName(paymentService.replaceAll("-", "_"))
            }
            _ <- NewStyle.function.tryons(checkPaymentProductError(paymentProduct), 404, callContext) {
              TransactionRequestTypes.withName(paymentProduct.replaceAll("-", "_").toUpperCase)
            }
            transactionRequestId = TransactionRequestId(paymentId)
            (existingTransactionRequest, _) <- getOwnPaymentImpl(transactionRequestId.value, callContext)
            _ <- Helper.booleanToFuture(failMsg = CannotUpdatePSUData, cc = callContext) {
              existingTransactionRequest.status == TransactionStatus.RCVD.code
            }
            (_, _) <- NewStyle.function.getChallenge(authorisationId, callContext)
            (challenge, _) <- NewStyle.function.validateChallengeAnswerC4(
              ChallengeType.BERLIN_GROUP_PAYMENT_CHALLENGE,
              Some(paymentId),
              None,
              authorisationId,
              transactionAuthorisationJson.scaAuthenticationData,
              SuppliedAnswerType.PLAIN_TEXT_VALUE,
              callContext
            )
            (fromAccount, _) <- NewStyle.function.checkBankAccountExists(
              BankId(existingTransactionRequest.from.bank_id),
              AccountId(existingTransactionRequest.from.account_id),
              callContext
            )
            _ <- challenge.scaStatus match {
              case Some(status) if status == StrongCustomerAuthenticationStatus.finalised =>
                NewStyle.function.createTransactionAfterChallengeV210(fromAccount, existingTransactionRequest, callContext) flatMap { _ =>
                  NewStyle.function.saveTransactionRequestStatusImpl(existingTransactionRequest.id, COMPLETED.toString, callContext)
                }
              case Some(status) if status == StrongCustomerAuthenticationStatus.failed =>
                NewStyle.function.saveTransactionRequestStatusImpl(existingTransactionRequest.id, REJECTED.toString, callContext)
              case _ =>
                Future(Full(true))
            }
          } yield {
            JSONFactory_BERLIN_GROUP_1_3.createUpdatePaymentPsuDataTransactionAuthorisationJson(challenge)
          }
        } else if (checkUpdatePsuAuthentication(parsedJson)) {
          Future.successful(json.parse(
            """{
              "scaStatus": "finalised",
              "_links": {
                "scaStatus": {"href":"/v1.3/payments/sepa-credit-transfers/88695566-6642-46d5-9985-0d824624f507"}
              }
            }"""))
        } else if (checkSelectPsuAuthenticationMethod(parsedJson)) {
          Future.successful(json.parse(
            """{
              "scaStatus": "scaMethodSelected",
              "chosenScaMethod": {
                "authenticationType": "SMS_OTP",
                "authenticationMethodId": "myAuthenticationID"},
              "challengeData": {
                "otpMaxLength": 6,
                "otpFormat": "integer"},
              "_links": {
                "authoriseTransaction": {"href": "/psd2/v1.3/payments/1234-wertiq-983/authorisations/123auth456"}
              }
            }"""))
        } else if (checkAuthorisationConfirmation(parsedJson)) {
          // authorisationConfirmation variant. Guarded by the checker that already existed for it:
          // this was the unconditional final else, so a body matching none of the four shapes -- an
          // empty one included -- was answered "scaStatus": "finalised", the terminal success state
          // of strong customer authentication, for an authorisation nothing had happened to.
          Future.successful(json.parse(
            """{
              "scaStatus": "finalised",
              "_links":{
                "status":  {"href":"/v1.3/payments/sepa-credit-transfers/qwer3456tzui7890/status"}
              }
            }"""))
        } else {
          // None of the four Berlin Group shapes. Malformed, and it has to say so: claiming an SCA
          // outcome for a request the server could not read is worse than any of them.
          Helper.booleanToFuture(
            failMsg = s"$InvalidJsonFormat The Json body should be one of updatePsuAuthentication, " +
              s"selectPsuAuthenticationMethod, transactionAuthorisation or authorisationConfirmation.",
            failCode = 400, cc = callContext)(false).map(_ => json.parse("{}"))
        }
      }
  }

  // ── ResourceDocs ───────────────────────────────────────────────────────────────────────────────────

  private val generalPaymentSummaryText: String =
    s"""  This method is used to initiate a payment at the ASPSP.

  ## Variants of Payment Initiation Requests

  This method to initiate a payment initiation at the ASPSP can be sent with either a JSON body or an pain.001 body depending on the payment product in the path.

  There are the following **payment products**:

    - Payment products with payment information in *JSON* format:
      - ***sepa-credit-transfers***
      - ***instant-sepa-credit-transfers***
      - ***target-2-payments***
      - ***cross-border-credit-transfers***
    - Payment products with payment information in *pain.001* XML format:
      - ***pain.001-sepa-credit-transfers***
      - ***pain.001-instant-sepa-credit-transfers***
      - ***pain.001-target-2-payments***
      - ***pain.001-cross-border-credit-transfers***

    - Furthermore the request body depends on the **payment-service**
      - ***payments***: A single payment initiation request.
      - ***bulk-payments***: A collection of several payment iniatiation requests.
        In case of a *pain.001* message there are more than one payments contained in the *pain.001 message.
        In case of a *JSON* there are several JSON payment blocks contained in a joining list.
      - ***periodic-payments***:
       Create a standing order initiation resource for recurrent i.e. periodic payments addressable under {paymentId}
       with all data relevant for the corresponding payment product and the execution of the standing order contained in a JSON body.

  This is the first step in the API to initiate the related recurring/periodic payment.

  Additional Instructions:

  for PAYMENT_SERVICE use payments

  for PAYMENT_PRODUCT use sepa-credit-transfers
  """

  private val generalStartPaymentAuthorisationSummary: String =
    s"""${mockedDataText(true)}
Create an authorisation sub-resource and start the authorisation process.
The message might in addition transmit authentication and authorisation related data.

This method is iterated n times for a n times SCA authorisation in a
corporate context, each creating an own authorisation sub-endpoint for
the corresponding PSU authorising the transaction.

The ASPSP might make the usage of this access method unnecessary in case
of only one SCA process needed, since the related authorisation resource
might be automatically created by the ASPSP after the submission of the
payment data with the first POST payments/{payment-product} call.

The start authorisation process is a process which is needed for creating a new authorisation
or cancellation sub-resource.
"""

  private val startPaymentAuthorisationResponse = JvalueCaseClass(json.parse("""{
    "challengeData": {
      "scaStatus": "received",
      "authorisationId": "88695566-6642-46d5-9985-0d824624f507",
      "psuMessage": "Please check your SMS at a mobile device.",
      "_links": {
        "scaStatus": "/v1.3/payments/sepa-credit-transfers/88695566-6642-46d5-9985-0d824624f507"
      }
    }
  }"""))

  private val generalStartPaymentInitiationCancellationAuthorisationSummary: String =
    s"""${mockedDataText(true)}
Creates an authorisation sub-resource and start the authorisation process of the cancellation of the addressed payment.
The message might in addition transmit authentication and authorisation related data.
"""

  private val startPaymentInitiationCancellationAuthorisationResponse = JvalueCaseClass(json.parse("""{
    "scaStatus": "received",
    "authorisationId": "123auth456",
    "psuMessage": "Please use your BankApp for transaction Authorisation.",
    "_links": {
      "scaStatus": {
        "href": "/v1.3/payments/qwer3456tzui7890/authorisations/123auth456"
      }
    }
  }"""))

  private val generalUpdatePaymentCancellationPsuDataSummary: String =
    s"""${mockedDataText(true)}
This method updates PSU data on the cancellation authorisation resource if needed.
It may authorise a cancellation of the payment within the Embedded SCA Approach where needed.
"""

  private val generalUpdatePaymentPsuDataSummary: String =
    s"""${mockedDataText(false)}
This methods updates PSU data on the authorisation resource if needed.
It may authorise a payment within the Embedded SCA Approach where needed.

  NOTE: For this endpoint, for sandbox mode, the `scaAuthenticationData` is fixed value: 123. To make the process work.
        Normally the app use will get SMS/EMAIL to get the value for this process.
"""

  private def initCancelAndGetResourceDocs(): Unit = {
    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(cancelPayment),
      "DELETE", "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENTID",
      "Payment Cancellation Request",
      s"""${mockedDataText(false)}
This method initiates the cancellation of a payment. Depending on the payment-service, the payment-product
and the ASPSP's implementation, this TPP call might be sufficient to cancel a payment. If an authorisation
of the payment cancellation is mandated by the ASPSP, a corresponding hyperlink will be contained in the
response message. Cancels the addressed payment with resource identification paymentId if applicable to the
payment-service, payment-product and received in product related timelines (e.g. before end of business day
for scheduled payments of the last business day before the scheduled execution day). The response to this
DELETE command will tell the TPP whether the * access method was rejected * access method was successful,
or * access method is generally applicable, but further authorisation processes are needed.
""",
      EmptyBody,
      CancelPaymentResponseJson(
        "ACTC",
        _links = CancelPaymentResponseLinks(
          self            = LinkHrefJson(s"/v1.3/payments/sepa-credit-transfers/1234-wertiq-983"),
          status          = LinkHrefJson(s"/v1.3/payments/sepa-credit-transfers/1234-wertiq-983/status"),
          startAuthorisation = LinkHrefJson(s"/v1.3/payments/sepa-credit-transfers/cancellation-authorisations/1234-wertiq-983/status")
        )
      ),
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Payment Initiation Service (PIS)") :: Nil,
      http4sPartialFunction = Some(cancelPayment)
    )

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getPaymentCancellationScaStatus),
      "GET", "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENTID/cancellation-authorisations/CANCELLATIONID",
      "Read the SCA status of the payment cancellation's authorisation.",
      s"""${mockedDataText(false)}
This method returns the SCA status of a payment initiation's authorisation sub-resource.
""",
      EmptyBody,
      JvalueCaseClass(json.parse("""{"scaStatus" : "psuAuthenticated"}""")),
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Payment Initiation Service (PIS)") :: apiTagBerlinGroupM :: Nil,
      http4sPartialFunction = Some(getPaymentCancellationScaStatus)
    )

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getPaymentInformation),
      "GET", "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENTID",
      "Get Payment Information",
      s"""${mockedDataText(false)}
Returns the content of a payment object""",
      EmptyBody,
      JvalueCaseClass(json.parse("""{
        "debtorAccount":{
          "iban":"GR12 1234 5123 4511 3981 4475 477"
        },
        "instructedAmount":{
          "currency":"EUR",
          "amount":"1234"
        },
        "creditorAccount":{
          "iban":"GR12 1234 5123 4514 4575 3645 077"
        },
        "creditorName":"70charname"
      }""")),
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Payment Initiation Service (PIS)") :: apiTagBerlinGroupM :: Nil,
      http4sPartialFunction = Some(getPaymentInformation)
    )

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getPaymentInitiationAuthorisation),
      "GET", "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENTID/authorisations",
      "Get Payment Initiation Authorisation Sub-Resources Request",
      s"""${mockedDataText(false)}
Read a list of all authorisation subresources IDs which have been created.

This function returns an array of hyperlinks to all generated authorisation sub-resources.
""",
      EmptyBody,
      JvalueCaseClass(json.parse("""[{
        "scaStatus": "received",
        "authorisationId": "940948c7-1c86-4d88-977e-e739bf2c1492",
        "psuMessage": "Please check your SMS at a mobile device.",
        "_links": {"scaStatus": "/v1.3/payments/sepa-credit-transfers/940948c7-1c86-4d88-977e-e739bf2c1492"}
      }]""")),
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Payment Initiation Service (PIS)") :: apiTagBerlinGroupM :: Nil,
      http4sPartialFunction = Some(getPaymentInitiationAuthorisation)
    )

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getPaymentInitiationCancellationAuthorisationInformation),
      "GET", "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENTID/cancellation-authorisations",
      "Get Cancellation Authorisation Sub-Resources Request",
      s"""${mockedDataText(false)}
Retrieve a list of all created cancellation authorisation sub-resources.
""",
      EmptyBody,
      JvalueCaseClass(json.parse("""{"cancellationIds" : ["faa3657e-13f0-4feb-a6c3-34bf21a9ae8e"]}""")),
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Payment Initiation Service (PIS)") :: apiTagBerlinGroupM :: Nil,
      http4sPartialFunction = Some(getPaymentInitiationCancellationAuthorisationInformation)
    )

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getPaymentInitiationScaStatus),
      "GET", "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENT_ID/authorisations/AUTHORISATION_ID",
      "Read the SCA Status of the payment authorisation",
      s"""${mockedDataText(false)}
This method returns the SCA status of a payment initiation's authorisation sub-resource.
""",
      EmptyBody,
      JvalueCaseClass(json.parse("""{"scaStatus" : "psuAuthenticated"}""")),
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Payment Initiation Service (PIS)") :: apiTagBerlinGroupM :: Nil,
      http4sPartialFunction = Some(getPaymentInitiationScaStatus)
    )

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getPaymentInitiationStatus),
      "GET", "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENT_ID/status",
      "Payment initiation status request",
      s"""${mockedDataText(false)}
Check the transaction status of a payment initiation.""",
      EmptyBody,
      JvalueCaseClass(json.parse(s"""{"transactionStatus": "${TransactionStatus.ACCP.code}"}""")),
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Payment Initiation Service (PIS)") :: apiTagBerlinGroupM :: Nil,
      http4sPartialFunction = Some(getPaymentInitiationStatus)
    )
  }

  private def initInitiatePaymentResourceDocs(): Unit = {
    val initiatePaymentRequestBody = JvalueCaseClass(json.parse(s"""{
      "debtorAccount": {"iban": "DE123456987480123"},
      "instructedAmount": {"currency": "EUR", "amount": "100"},
      "creditorAccount": {"iban": "UK12 1234 5123 4517 2948 6166 077"},
      "creditorName": "70charname"
    }"""))
    val initiatePaymentResponseBody = JvalueCaseClass(json.parse(s"""{
      "transactionStatus": "${TransactionStatus.RCVD.code}",
      "paymentId": "1234-wertiq-983",
      "_links": {
        "scaRedirect": {"href": "$getServerUrl/otp?flow=payment&paymentService=payments&paymentProduct=sepa_credit_transfers&paymentId=b0472c21-6cea-4ee0-b036-3e253adb3b0b"},
        "self": {"href": "/v1.3/payments/sepa-credit-transfers/1234-wertiq-983"},
        "status": {"href": "/v1.3/payments/1234-wertiq-983/status"},
        "scaStatus": {"href": "/v1.3/payments/1234-wertiq-983/authorisations/123auth456"}
      }
    }"""))

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(initiatePayments),
      "POST", "/payments/PAYMENT_PRODUCT",
      "Payment initiation request(payments)",
      s"""${mockedDataText(false)}
$generalPaymentSummaryText""",
      initiatePaymentRequestBody,
      initiatePaymentResponseBody,
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Payment Initiation Service (PIS)") :: apiTagBerlinGroupM :: Nil,
      authMode = UserOrApplication,
      http4sPartialFunction = Some(initiatePayments)
    )

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(initiatePeriodicPayments),
      "POST", "/periodic-payments/PAYMENT_PRODUCT",
      "Payment initiation request(periodic-payments)",
      s"""${mockedDataText(false)}
$generalPaymentSummaryText""",
      JvalueCaseClass(json.parse(s"""{
        "instructedAmount": {"currency": "EUR", "amount": "123"},
        "debtorAccount": {"iban": "DE40100100103307118608"},
        "creditorName": "Merchant123",
        "creditorAccount": {"iban": "DE23100120020123456789"},
        "remittanceInformationUnstructured": "Ref Number Abonnement",
        "startDate": "2018-03-01",
        "executionRule": "preceding",
        "frequency": "Monthly",
        "dayOfExecution": "01"
      }""")),
      JvalueCaseClass(json.parse(s"""{
        "transactionStatus": "${TransactionStatus.RCVD.code}",
        "paymentId": "1234-wertiq-983",
        "_links": {
          "scaRedirect": {"href": "$getServerUrl/otp?flow=payment&paymentService=payments&paymentProduct=sepa_credit_transfers&paymentId=b0472c21-6cea-4ee0-b036-3e253adb3b0b"},
          "self": {"href": "/v1.3/periodic-payments/instant-sepa-credit-transfer/1234-wertiq-983"},
          "status": {"href": "/v1.3/periodic-payments/1234-wertiq-983/status"},
          "scaStatus": {"href": "/v1.3/periodic-payments/1234-wertiq-983/authorisations/123auth456"}
        }
      }""")),
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Payment Initiation Service (PIS)") :: apiTagBerlinGroupM :: Nil,
      authMode = UserOrApplication,
      http4sPartialFunction = Some(initiatePeriodicPayments)
    )

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(initiateBulkPayments),
      "POST", "/bulk-payments/PAYMENT_PRODUCT",
      "Payment initiation request(bulk-payments)",
      s"""${mockedDataText(true)}
$generalPaymentSummaryText""",
      JvalueCaseClass(json.parse(s"""{
        "batchBookingPreferred": "true",
        "debtorAccount": {"iban": "DE40100100103307118608"},
        "paymentInformationId": "my-bulk-identification-1234",
        "requestedExecutionDate": "2018-08-01",
        "payments": [
          {"instructedAmount": {"currency": "EUR", "amount": "123.50"}, "creditorName": "Merchant123",
           "creditorAccount": {"iban": "DE02100100109307118603"},
           "remittanceInformationUnstructured": "Ref Number Merchant 1"},
          {"instructedAmount": {"currency": "EUR", "amount": "34.10"}, "creditorName": "Merchant456",
           "creditorAccount": {"iban": "FR7612345987650123456789014"},
           "remittanceInformationUnstructured": "Ref Number Merchant 2"}
        ]
      }""")),
      JvalueCaseClass(json.parse(s"""{
        "transactionStatus": "${TransactionStatus.RCVD.code}",
        "paymentId": "1234-wertiq-983",
        "_links": {
          "scaRedirect": {"href": "$getServerUrl/otp?flow=payment&paymentService=payments&paymentProduct=sepa_credit_transfers&paymentId=b0472c21-6cea-4ee0-b036-3e253adb3b0b"},
          "self": {"href": "/v1.3/bulk-payments/sepa-credit-transfers/1234-wertiq-983"},
          "status": {"href": "/v1.3/bulk-payments/1234-wertiq-983/status"},
          "scaStatus": {"href": "/v1.3/bulk-payments/1234-wertiq-983/authorisations/123auth456"}
        }
      }""")),
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Payment Initiation Service (PIS)") :: apiTagBerlinGroupM :: Nil,
      authMode = UserOrApplication,
      http4sPartialFunction = Some(initiateBulkPayments)
    )
  }

  private def initStartAuthorisationResourceDocs(): Unit = {
    // POST /PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENT_ID/authorisations — 3 body variants
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      "startPaymentAuthorisationUpdatePsuAuthentication",
      "POST", "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENT_ID/authorisations",
      "Start the authorisation process for a payment initiation (updatePsuAuthentication)",
      generalStartPaymentAuthorisationSummary,
      JvalueCaseClass(json.parse("""{"psuData": {"password": "start12"}}""")),
      startPaymentAuthorisationResponse,
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Payment Initiation Service (PIS)") :: apiTagBerlinGroupM :: Nil,
      http4sPartialFunction = Some(startPaymentAuthorisationAll)
    )
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      "startPaymentAuthorisationSelectPsuAuthenticationMethod",
      "POST", "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENT_ID/authorisations",
      "Start the authorisation process for a payment initiation (selectPsuAuthenticationMethod)",
      generalStartPaymentAuthorisationSummary,
      JvalueCaseClass(json.parse("""{"authenticationMethodId":""}""")),
      startPaymentAuthorisationResponse,
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Payment Initiation Service (PIS)") :: apiTagBerlinGroupM :: Nil,
      http4sPartialFunction = Some(startPaymentAuthorisationAll)
    )
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      "startPaymentAuthorisationTransactionAuthorisation",
      "POST", "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENT_ID/authorisations",
      "Start the authorisation process for a payment initiation (transactionAuthorisation)",
      s"""${mockedDataText(false)}
Create an authorisation sub-resource and start the authorisation process.
The message might in addition transmit authentication and authorisation related data.
""",
      JvalueCaseClass(json.parse("""{"scaAuthenticationData":"123"}""")),
      startPaymentAuthorisationResponse,
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Payment Initiation Service (PIS)") :: apiTagBerlinGroupM :: Nil,
      http4sPartialFunction = Some(startPaymentAuthorisationAll)
    )

    // POST /PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENT_ID/cancellation-authorisations — 3 body variants
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      "startPaymentInitiationCancellationAuthorisationTransactionAuthorisation",
      "POST", "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENT_ID/cancellation-authorisations",
      "Start the authorisation process for the cancellation of the addressed payment (transactionAuthorisation)",
      s"""${mockedDataText(false)}
Creates an authorisation sub-resource and start the authorisation process of the cancellation of the addressed payment.
""",
      JvalueCaseClass(json.parse("""{"scaAuthenticationData":""}""")),
      JvalueCaseClass(json.parse("""{
        "scaStatus": "received",
        "authorisationId": "123auth456",
        "psuMessage": "Please use your BankApp for transaction Authorisation.",
        "_links": {"scaStatus": {"href": "/v1.3/payments/qwer3456tzui7890/authorisations/123auth456"}}
      }""")),
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Payment Initiation Service (PIS)") :: apiTagBerlinGroupM :: Nil,
      http4sPartialFunction = Some(startPaymentInitiationCancellationAuthorisationAll)
    )
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      "startPaymentInitiationCancellationAuthorisationUpdatePsuAuthentication",
      "POST", "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENT_ID/cancellation-authorisations",
      "Start the authorisation process for the cancellation of the addressed payment (updatePsuAuthentication)",
      generalStartPaymentInitiationCancellationAuthorisationSummary,
      JvalueCaseClass(json.parse("""{"psuData": {"password": "start12"}}""")),
      startPaymentInitiationCancellationAuthorisationResponse,
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Payment Initiation Service (PIS)") :: apiTagBerlinGroupM :: Nil,
      http4sPartialFunction = Some(startPaymentInitiationCancellationAuthorisationAll)
    )
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      "startPaymentInitiationCancellationAuthorisationSelectPsuAuthenticationMethod",
      "POST", "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENT_ID/cancellation-authorisations",
      "Start the authorisation process for the cancellation of the addressed payment (selectPsuAuthenticationMethod)",
      generalStartPaymentInitiationCancellationAuthorisationSummary,
      JvalueCaseClass(json.parse("""{"authenticationMethodId":""}""")),
      startPaymentInitiationCancellationAuthorisationResponse,
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Payment Initiation Service (PIS)") :: apiTagBerlinGroupM :: Nil,
      http4sPartialFunction = Some(startPaymentInitiationCancellationAuthorisationAll)
    )
  }

  private def initUpdatePsuDataResourceDocs(): Unit = {
    // PUT /PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENT_ID/cancellation-authorisations/AUTHORISATION_ID — 4 variants
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      "updatePaymentCancellationPsuDataTransactionAuthorisation",
      "PUT", "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENT_ID/cancellation-authorisations/AUTHORISATION_ID",
      "Update PSU Data for payment initiation cancellation (transactionAuthorisation)",
      s"""${mockedDataText(false)}
This method updates PSU data on the cancellation authorisation resource if needed.
""",
      JvalueCaseClass(json.parse("""{"scaAuthenticationData":"123"}""")),
      JvalueCaseClass(json.parse("""{
        "scaStatus":"finalised",
        "psuMessage":"Please check your SMS at a mobile device.",
        "_links":{"scaStatus":"/v1.3/payments/sepa-credit-transfers/PAYMENT_ID/4f4a8b7f-9968-4183-92ab-ca512b396bfc"}
      }""")),
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Payment Initiation Service (PIS)") :: apiTagBerlinGroupM :: Nil,
      http4sPartialFunction = Some(updatePaymentCancellationPsuDataAll)
    )
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      "updatePaymentCancellationPsuDataUpdatePsuAuthentication",
      "PUT", "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENT_ID/cancellation-authorisations/AUTHORISATION_ID",
      "Update PSU Data for payment initiation cancellation (updatePsuAuthentication)",
      generalUpdatePaymentCancellationPsuDataSummary,
      JvalueCaseClass(json.parse("""{"psuData":{"password":"start12"}}""")),
      JvalueCaseClass(json.parse("""{
        "scaStatus": "psuAuthenticated",
        "_links": {"authoriseTransaction": {"href": "/psd2/v1.3/payments/1234-wertiq-983/authorisations/123auth456"}}
      }""")),
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Payment Initiation Service (PIS)") :: apiTagBerlinGroupM :: Nil,
      http4sPartialFunction = Some(updatePaymentCancellationPsuDataAll)
    )
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      "updatePaymentCancellationPsuDataSelectPsuAuthenticationMethod",
      "PUT", "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENT_ID/cancellation-authorisations/AUTHORISATION_ID",
      "Update PSU Data for payment initiation cancellation (selectPsuAuthenticationMethod)",
      generalUpdatePaymentCancellationPsuDataSummary,
      JvalueCaseClass(json.parse("""{"authenticationMethodId":""}""")),
      JvalueCaseClass(json.parse("""{
        "scaStatus": "scaMethodSelected",
        "chosenScaMethod": {"authenticationType": "SMS_OTP", "authenticationMethodId": "myAuthenticationID"},
        "challengeData": {"otpMaxLength": 6, "otpFormat": "integer"},
        "_links": {"authoriseTransaction": {"href": "/psd2/v1.3/payments/1234-wertiq-983/authorisations/123auth456"}}
      }""")),
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Payment Initiation Service (PIS)") :: apiTagBerlinGroupM :: Nil,
      http4sPartialFunction = Some(updatePaymentCancellationPsuDataAll)
    )
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      "updatePaymentCancellationPsuDataAuthorisationConfirmation",
      "PUT", "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENT_ID/cancellation-authorisations/AUTHORISATION_ID",
      "Update PSU Data for payment initiation cancellation (authorisationConfirmation)",
      generalUpdatePaymentCancellationPsuDataSummary,
      JvalueCaseClass(json.parse("""{"confirmationCode":"confirmationCode"}""")),
      JvalueCaseClass(json.parse("""{
        "scaStatus": "finalised",
        "_links":{"status":  {"href":"/v1.3/payments/sepa-credit-transfers/qwer3456tzui7890/status"}}
      }""")),
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Payment Initiation Service (PIS)") :: apiTagBerlinGroupM :: Nil,
      http4sPartialFunction = Some(updatePaymentCancellationPsuDataAll)
    )

    // PUT /PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENT_ID/authorisations/AUTHORISATION_ID — 4 variants
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      "updatePaymentPsuDataTransactionAuthorisation",
      "PUT", "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENT_ID/authorisations/AUTHORISATION_ID",
      "Update PSU data for payment initiation (transactionAuthorisation)",
      generalUpdatePaymentPsuDataSummary,
      JvalueCaseClass(json.parse("""{"scaAuthenticationData":"123"}""")),
      JvalueCaseClass(json.parse("""{
        "scaStatus": "finalised",
        "psuMessage": "Please check your SMS at a mobile device.",
        "_links": {"scaStatus": {"href":"/v1.3/payments/sepa-credit-transfers/88695566-6642-46d5-9985-0d824624f507"}}
      }""")),
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Payment Initiation Service (PIS)") :: apiTagBerlinGroupM :: Nil,
      http4sPartialFunction = Some(updatePaymentPsuDataAll)
    )
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      "updatePaymentPsuDataUpdatePsuAuthentication",
      "PUT", "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENT_ID/authorisations/AUTHORISATION_ID",
      "Update PSU data for payment initiation (updatePsuAuthentication)",
      generalUpdatePaymentPsuDataSummary,
      JvalueCaseClass(json.parse("""{"psuData": {"password": "start12"}}""")),
      JvalueCaseClass(json.parse("""{
        "scaStatus": "finalised",
        "_links": {"scaStatus": {"href":"/v1.3/payments/sepa-credit-transfers/88695566-6642-46d5-9985-0d824624f507"}}
      }""")),
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Payment Initiation Service (PIS)") :: apiTagBerlinGroupM :: Nil,
      http4sPartialFunction = Some(updatePaymentPsuDataAll)
    )
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      "updatePaymentPsuDataSelectPsuAuthenticationMethod",
      "PUT", "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENT_ID/authorisations/AUTHORISATION_ID",
      "Update PSU data for payment initiation (selectPsuAuthenticationMethod)",
      generalUpdatePaymentPsuDataSummary,
      JvalueCaseClass(json.parse("""{"authenticationMethodId":""}""")),
      JvalueCaseClass(json.parse("""{
        "scaStatus": "scaMethodSelected",
        "chosenScaMethod": {"authenticationType": "SMS_OTP", "authenticationMethodId": "myAuthenticationID"},
        "challengeData": {"otpMaxLength": 6, "otpFormat": "integer"},
        "_links": {"authoriseTransaction": {"href": "/psd2/v1.3/payments/1234-wertiq-983/authorisations/123auth456"}}
      }""")),
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Payment Initiation Service (PIS)") :: apiTagBerlinGroupM :: Nil,
      http4sPartialFunction = Some(updatePaymentPsuDataAll)
    )
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      "updatePaymentPsuDataAuthorisationConfirmation",
      "PUT", "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENT_ID/authorisations/AUTHORISATION_ID",
      "Update PSU data for payment initiation (authorisationConfirmation)",
      generalUpdatePaymentPsuDataSummary,
      JvalueCaseClass(json.parse("""{"confirmationCode":"confirmationCode"}""")),
      JvalueCaseClass(json.parse("""{
        "scaStatus": "finalised",
        "_links":{"status":  {"href":"/v1.3/payments/sepa-credit-transfers/qwer3456tzui7890/status"}}
      }""")),
      List(AuthenticatedUserIsRequired, UnknownError),
      ApiTag("Payment Initiation Service (PIS)") :: apiTagBerlinGroupM :: Nil,
      http4sPartialFunction = Some(updatePaymentPsuDataAll)
    )
  }

  // Initialise all ResourceDocs at object-construction time
  initCancelAndGetResourceDocs()
  initInitiatePaymentResourceDocs()
  initStartAuthorisationResourceDocs()
  initUpdatePsuDataResourceDocs()

  val routes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    cancelPayment(req)
      .orElse(getPaymentCancellationScaStatus(req))
      .orElse(getPaymentInformation(req))
      .orElse(getPaymentInitiationAuthorisation(req))
      .orElse(getPaymentInitiationCancellationAuthorisationInformation(req))
      .orElse(getPaymentInitiationScaStatus(req))
      .orElse(getPaymentInitiationStatus(req))
      .orElse(initiatePayments(req))
      .orElse(initiatePeriodicPayments(req))
      .orElse(initiateBulkPayments(req))
      .orElse(startPaymentAuthorisationAll(req))
      .orElse(startPaymentInitiationCancellationAuthorisationAll(req))
      .orElse(updatePaymentCancellationPsuDataAll(req))
      .orElse(updatePaymentPsuDataAll(req))
  }
}
