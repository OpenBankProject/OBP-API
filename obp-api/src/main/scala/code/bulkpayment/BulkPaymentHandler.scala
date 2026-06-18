package code.bulkpayment

import org.json4s._
import code.api.util.{APIUtil, CallContext, ErrorMessages, NewStyle}
import code.api.util.ErrorMessages._
import code.api.v7_0_0.JSONFactory700
import code.bankconnectors.{Connector => BankConnector}
import code.routingscheme.{RoutingSchemeValidation, RoutingSchemes}
import code.transactionrequests.MappedTransactionRequestProvider
import code.util.Helper
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.TransactionRequestStatus
import net.liftweb.common.{Box, Full}
import org.json4s.{Extraction, Formats}
import com.openbankproject.commons.util.JsonAliases.prettyRender

import scala.concurrent.Future

/**
 * Orchestrator for BULK transaction-requests in mapped-mode.
 *
 * Mapped mode = fan out each payment into a real Transaction (debit source / credit
 * destination). All resulting Transactions share the parent BULK transaction-request_id.
 * Per-payment outcomes (SUCCEEDED / FAILED + reason) are recorded in the
 * BulkPayment side-table so callers can map each result back to its
 * end_to_end_id.
 *
 * In adapter mode this orchestrator would be replaced by a single connector call
 * that hands the whole batch to the south side; the side-table rows would be
 * populated later via callbacks.
 */
object BulkPaymentHandler {

  /** Idempotency + envelope checks. Returns an error message or unit. */
  def validateEnvelope(
    body: JSONFactory700.TransactionRequestBodyBulkJsonV700,
    fromAccount: BankAccount,
    callContext: Option[CallContext]
  )(implicit formats: Formats): Future[Unit] = {
    val maxItems = APIUtil.getPropsAsIntValue("bulk_payments.max_items_per_batch", 1000)
    val sourceCurrency = fromAccount.currency

    for {
      _ <- Helper.booleanToFuture(BulkPaymentsArrayEmpty, 400, callContext) {
        body.payments.nonEmpty
      }
      _ <- Helper.booleanToFuture(BulkPaymentsArrayTooLarge, 400, callContext) {
        body.payments.size <= maxItems
      }
      _ <- Helper.booleanToFuture(BulkPaymentCurrencyMismatch, 400, callContext) {
        body.value.currency == sourceCurrency &&
          body.payments.forall(_.value.currency == sourceCurrency)
      }
      _ <- Helper.booleanToFuture(BulkDuplicateEndToEndId, 400, callContext) {
        body.payments.map(_.end_to_end_id).distinct.size == body.payments.size
      }
      // Total = sum of items (server-validated against caller's declared total)
      _ <- {
        val declared = BigDecimal(body.value.amount)
        val actual = body.payments.map(p => BigDecimal(p.value.amount)).sum
        Helper.booleanToFuture(
          s"$InvalidNumber Declared total $declared does not match sum of payments $actual", 400, callContext
        ) {
          declared == actual
        }
      }
      _ <- Helper.booleanToFuture(BulkBatchReferenceAlreadyUsed, 409, callContext) {
        !BulkPayments.bulkPayment.vend.isBatchReferenceUsed(
          fromAccount.bankId.value, fromAccount.accountId.value, body.batch_reference
        )
      }
    } yield ()
  }

  /**
   * Fan-out: for each item, validate routing + resolve destination + makePayment;
   * record outcome in BulkPayment. Always returns one row per input item.
   * Validation failures do NOT abort the batch — they mark the item FAILED and continue.
   */
  def executeAllItems(
    body: JSONFactory700.TransactionRequestBodyBulkJsonV700,
    fromAccount: BankAccount,
    transactionRequestId: String,
    chargePolicy: String,
    callContext: Option[CallContext]
  )(implicit formats: Formats): Future[List[BulkPaymentTrait]] = {
    // Run items sequentially — preserves debit ordering against the source
    // account so per-leg balance checks are deterministic.
    body.payments.zipWithIndex.foldLeft(Future.successful(List.empty[BulkPaymentTrait])) {
      case (accFut, (item, idx)) =>
        accFut.flatMap { acc =>
          executeOneItem(item, idx, fromAccount, transactionRequestId, chargePolicy, callContext)
            .map(row => acc :+ row)
        }
    }
  }

  private def executeOneItem(
    item: JSONFactory700.BulkPaymentItemJsonV700,
    idx: Int,
    fromAccount: BankAccount,
    transactionRequestId: String,
    chargePolicy: String,
    callContext: Option[CallContext]
  )(implicit formats: Formats): Future[BulkPaymentTrait] = {
    // Closure that records a FAILED row and returns it.
    def recordFailure(reason: String): BulkPaymentTrait =
      BulkPayments.bulkPayment.vend.createBulkPayment(
        transactionRequestId = transactionRequestId,
        itemIndex = idx,
        endToEndId = item.end_to_end_id,
        routingScheme = item.to_account_routing.scheme,
        address = item.to_account_routing.address,
        currency = item.value.currency,
        amount = item.value.amount,
        description = item.description,
        status = "FAILED",
        failureReason = Some(reason),
        transactionId = None
      ).openOrThrowException("BulkPayment write failed")

    // 1. Scheme must be registered.
    val schemeBox = RoutingSchemes.routingScheme.vend.getRoutingScheme(item.to_account_routing.scheme)
    schemeBox match {
      case Full(scheme) =>
        // 2. Scheme must be ACCOUNT category (BULK only routes between accounts).
        if (scheme.category != "ACCOUNT")
          Future.successful(recordFailure(BulkPaymentRoutingSchemeWrongCategory))
        // 3. Address must match the scheme's pattern.
        else if (!RoutingSchemeValidation.addressMatchesPattern(scheme.addressPattern, item.to_account_routing.address))
          Future.successful(recordFailure(BulkPaymentAddressMismatch))
        else {
          // 4. Resolve destination account.
          BankConnector.connector.vend.getBankAccountByRouting(None, item.to_account_routing.scheme, item.to_account_routing.address, callContext)
            .flatMap { case (destBox, _) =>
              destBox match {
                case Full(toAccount) =>
                  // 5. Do the actual debit/credit.
                  //    transaction_request_id is the BULK parent — all payments share it.
                  val bodyShim = SingletonBulkLegBody(item.value, item.description)
                  BankConnector.connector.vend.makePaymentv210(
                    fromAccount,
                    toAccount,
                    TransactionRequestId(transactionRequestId),
                    bodyShim,
                    BigDecimal(item.value.amount),
                    item.description,
                    TransactionRequestType("BULK"),
                    chargePolicy,
                    callContext
                  ).map { case (txIdBox, _) =>
                    txIdBox match {
                      case Full(txId) =>
                        // Link the new transaction to the parent TR.
                        MappedTransactionRequestProvider
                          .saveTransactionRequestTransactionImpl(TransactionRequestId(transactionRequestId), txId)
                        BulkPayments.bulkPayment.vend.createBulkPayment(
                          transactionRequestId = transactionRequestId,
                          itemIndex = idx,
                          endToEndId = item.end_to_end_id,
                          routingScheme = item.to_account_routing.scheme,
                          address = item.to_account_routing.address,
                          currency = item.value.currency,
                          amount = item.value.amount,
                          description = item.description,
                          status = "SUCCEEDED",
                          failureReason = None,
                          transactionId = Some(txId.value)
                        ).openOrThrowException("BulkPayment write failed")
                      case _ =>
                        recordFailure(txIdBox.toString)
                    }
                  }
                case _ =>
                  Future.successful(recordFailure(s"$PayeeNotFound (scheme=${item.to_account_routing.scheme}, address=${item.to_account_routing.address})"))
              }
            }
        }
      case _ =>
        Future.successful(recordFailure(BulkPaymentRoutingSchemeNotRegistered))
    }
  }

  def computeStatus(items: List[BulkPaymentTrait]): String = {
    val succeeded = items.count(_.status == "SUCCEEDED")
    val failed = items.count(_.status == "FAILED")
    (succeeded, failed) match {
      case (n, 0) if n > 0 => "COMPLETED"
      case (n, _) if n > 0 => "PARTIALLY_COMPLETED"
      case _               => "FAILED"
    }
  }

  /** Thin shim implementing TransactionRequestCommonBodyJSON for one leg —
   *  makePaymentv210 needs this even though for BULK the meaningful body is at
   *  the batch level. */
  private case class SingletonBulkLegBody(
    value: AmountOfMoneyJsonV121,
    description: String
  ) extends TransactionRequestCommonBodyJSON
}
