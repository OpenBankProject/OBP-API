package code.bankconnectors.opencorridor

import org.json4s._
import code.api.ChargePolicy
import code.api.util.APIUtil.getScaMethodAtInstance
import code.api.util.ErrorMessages._
import code.api.util.{APIUtil, CallContext, NewStyle}
import code.api.v7_0_0.JSONFactory700.{OpenCorridorPromiseJsonV700, PostOpenCorridorPromiseJsonV700, TransactionRequestBodyOpenCorridorJsonV700}
import code.util.Helper
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.dto.{OpenCorridorAccountRouting, OpenCorridorBeneficiary, OpenCorridorMoneyValue, OpenCorridorOriginator, OutBoundOpenCorridorCreditNotification}
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.ChallengeType.OBP_TRANSACTION_REQUEST_CHALLENGE
import com.openbankproject.commons.model.enums.{TransactionRequestAttributeType, TransactionRequestStatus, TransactionRequestTypes}
import code.messageoutbox.MessageOutbox
import code.transactionrequests.MappedTransactionRequest
import net.liftweb.common.Box

import java.util.Date
import org.json4s.native.Serialization.write
import org.json4s.NoTypeHints
import org.json4s.native.Serialization
import net.liftweb.util.StringHelpers

import scala.concurrent.Future

// OPEN_CORRIDOR_PROMISE Transaction Request — Travel-Rule-friendly payment.
//
// Money-movement is identical to SIMPLE today (same beneficiary routing shape).
// What's distinct is the mandatory `originator` block, persisted alongside the TR
// and surfaced on v7 responses. Lives in its own package so future Open Corridor
// extensions (Cardano Promise, netting, settlement) have a home that does not bloat
// `LocalMappedConnector`.
object OpenCorridorProcessor {

  // Create an OPEN_CORRIDOR_PROMISE Transaction Request: validate the originator block,
  // resolve the destination counterparty (via the same getOrCreateCounterparty
  // path SIMPLE uses), persist the TR with the originator side-car, and return it.
  def create(
    user: User,
    bankId: BankId,
    accountId: AccountId,
    viewId: ViewId,
    fromAccount: BankAccount,
    body: TransactionRequestBodyOpenCorridorJsonV700,
    callContext: Option[CallContext]
  ): Future[(TransactionRequest, Option[CallContext])] = {

    val transactionRequestType = TransactionRequestType("OPEN_CORRIDOR_PROMISE")

    for {
      _ <- Helper.booleanToFuture(s"$InvalidJsonValue originator.name must be non-empty", cc = callContext) {
        body.originator.name.trim.nonEmpty
      }
      _ <- Helper.booleanToFuture(s"$InvalidJsonValue originator.address must be non-empty", cc = callContext) {
        body.originator.address.trim.nonEmpty
      }
      _ <- Helper.booleanToFuture(s"$InvalidJsonValue originator.account_routing.scheme must be non-empty", cc = callContext) {
        body.originator.account_routing.scheme.trim.nonEmpty
      }
      _ <- Helper.booleanToFuture(s"$InvalidJsonValue originator.account_routing.address must be non-empty", cc = callContext) {
        body.originator.account_routing.address.trim.nonEmpty
      }

      (toCounterparty, callContext) <- NewStyle.function.getOrCreateCounterparty(
        name = body.to.name,
        description = body.to.description,
        currency = body.value.currency,
        createdByUserId = user.userId,
        thisBankId = bankId.value,
        thisAccountId = accountId.value,
        thisViewId = viewId.value,
        otherBankRoutingScheme = StringHelpers.snakify(body.to.other_bank_routing_scheme).toUpperCase,
        otherBankRoutingAddress = body.to.other_bank_routing_address,
        otherBranchRoutingScheme = StringHelpers.snakify(body.to.other_branch_routing_scheme).toUpperCase,
        otherBranchRoutingAddress = body.to.other_branch_routing_address,
        otherAccountRoutingScheme = StringHelpers.snakify(body.to.other_account_routing_scheme).toUpperCase,
        otherAccountRoutingAddress = body.to.other_account_routing_address,
        otherAccountSecondaryRoutingScheme = StringHelpers.snakify(body.to.other_account_secondary_routing_scheme).toUpperCase,
        otherAccountSecondaryRoutingAddress = body.to.other_account_secondary_routing_address,
        callContext
      )
      // Resolve the far BANK only — it must be registered here (the corridor
      // registry is what OBP-API authoritatively knows), and its id is stamped
      // on the TR as mTo_BankId, which the settle-pair netting selects by. The
      // beneficiary ACCOUNT is deliberately NOT resolved: it lives in the far
      // bank's CBS and is validated by the beneficiary Bank Node at credit
      // time — requiring it to exist in OBP-API would demand an integration to
      // the far bank's account list.
      (toBank, callContext) <- resolveFarBank(
        StringHelpers.snakify(body.to.other_bank_routing_scheme).toUpperCase,
        body.to.other_bank_routing_address,
        callContext
      )
      // A corridor is inter-bank by definition: a same-bank "promise" needs no
      // Travel-Rule relay and can never be settled (settlement is pairwise).
      _ <- Helper.booleanToFuture(s"$OpenCorridorSameBankNotAllowed", cc = callContext) {
        toBank.bankId.value != bankId.value
      }
      // Routing-only carrier for the TR row and charge plumbing. No Transaction
      // ever posts against it: promises are held at PENDING (getStatus) and the
      // net later moves between the settlement accounts, which the settle-pair
      // step resolves separately.
      toAccount = BankAccountCommons(
        accountId = AccountId(body.to.other_account_routing_address),
        accountType = "",
        balance = 0,
        currency = body.value.currency,
        name = body.to.name,
        label = "",
        number = "",
        bankId = toBank.bankId,
        lastUpdate = new Date(),
        branchId = "",
        accountRoutings = List(
          AccountRouting(
            StringHelpers.snakify(body.to.other_account_routing_scheme).toUpperCase,
            body.to.other_account_routing_address)) ++
          (if (body.to.other_account_secondary_routing_scheme.trim.nonEmpty)
            List(AccountRouting(
              StringHelpers.snakify(body.to.other_account_secondary_routing_scheme).toUpperCase,
              body.to.other_account_secondary_routing_address))
          else Nil),
        accountRules = List.empty,
        accountHolder = body.to.name
      )
      _ <- Helper.booleanToFuture(s"$CounterpartyBeneficiaryPermit", cc = callContext) {
        toCounterparty.isBeneficiary
      }
      _ <- Helper.booleanToFuture(s"$InvalidChargePolicy", cc = callContext) {
        ChargePolicy.values.contains(ChargePolicy.withName(body.charge_policy))
      }

      transDetailsSerialized <- NewStyle.function.tryons(UnknownError, 400, callContext) {
        write(body)(Serialization.formats(NoTypeHints))
      }

      (createdTransactionRequest, callContext) <- NewStyle.function.createTransactionRequestv400(
        user,
        viewId,
        fromAccount,
        toAccount,
        transactionRequestType,
        body,
        transDetailsSerialized,
        body.charge_policy,
        Some(OBP_TRANSACTION_REQUEST_CHALLENGE),
        getScaMethodAtInstance(transactionRequestType.value).toOption,
        None,
        callContext
      )
    } yield (createdTransactionRequest, callContext)
  }

  // The far bank must exist in the corridor registry. OBP-scheme routing names
  // the bank id directly; any other scheme (BIC, ...) is matched against the
  // registered banks' bank routing.
  private def resolveFarBank(
    scheme: String,
    address: String,
    callContext: Option[CallContext]
  ): Future[(Bank, Option[CallContext])] = {
    if (scheme == "OBP" || scheme == "OBP_BANK_ID")
      NewStyle.function.getBank(BankId(address), callContext)
    else
      NewStyle.function.getBanks(callContext).map { case (banks, cc) =>
        val bank = banks.find(b =>
          b.bankRoutingScheme.equalsIgnoreCase(scheme) && b.bankRoutingAddress == address)
        (APIUtil.unboxFullOrFail(Box(bank), cc, s"$BankNotFound bank_routing: $scheme $address", 404), cc)
      }
  }

  // ─── Promise report-back (salt relay intake) ────────────────────────────────
  //
  // Transaction Request attribute names carrying the on-chain promise evidence.
  // The evidence triplet (commitment, salt, preimage) is opaque to OBP-API: it is
  // stored verbatim and relayed to the beneficiary bank in obp_credit_notification,
  // where the receiving Bank Node recomputes SHA-256(salt ‖ preimage) against the
  // on-chain commitment and refuses to credit on a mismatch.
  val PromiseAttributeTxHash     = "open_corridor_tx_hash"
  val PromiseAttributeBlockchain = "open_corridor_blockchain"
  val PromiseAttributeCommitment = "open_corridor_commitment"
  val PromiseAttributeSalt       = "open_corridor_salt"
  val PromiseAttributePreimage   = "open_corridor_preimage"
  // Audit side-car: who attached the evidence, and when.
  val PromiseAttributeReportedBy = "open_corridor_promise_reported_by"
  val PromiseAttributeReportedAt = "open_corridor_promise_reported_at"

  private val promiseEvidenceAttributeNames = Set(
    PromiseAttributeTxHash, PromiseAttributeBlockchain,
    PromiseAttributeCommitment, PromiseAttributeSalt, PromiseAttributePreimage
  )

  // Attach the Bank Node's on-chain promise evidence to a PENDING OPEN_CORRIDOR_PROMISE
  // Transaction Request. Idempotent: re-reporting identical evidence returns the stored
  // record; differing evidence is refused (OBP-40053) — evidence is append-once, so a
  // recorded commitment can never be silently replaced after the fact.
  def attachPromiseEvidence(
    user: User,
    bankId: BankId,
    accountId: AccountId,
    transactionRequestId: TransactionRequestId,
    body: PostOpenCorridorPromiseJsonV700,
    callContext: Option[CallContext]
  ): Future[(OpenCorridorPromiseJsonV700, Option[CallContext])] = {
    val submittedEvidence = Map(
      PromiseAttributeTxHash     -> body.tx_hash,
      PromiseAttributeBlockchain -> body.blockchain,
      PromiseAttributeCommitment -> body.commitment,
      PromiseAttributeSalt       -> body.salt,
      PromiseAttributePreimage   -> body.preimage
    )
    for {
      _ <- Helper.booleanToFuture(
        s"$InvalidJsonValue tx_hash, blockchain, commitment, salt and preimage must all be non-empty",
        cc = callContext) {
        submittedEvidence.values.forall(_.trim.nonEmpty)
      }
      (tr, callContext) <- NewStyle.function.getTransactionRequestImpl(transactionRequestId, callContext)
      // Row lock for the rest of the request transaction: closes the race where two
      // concurrent report-backs both see "no evidence yet" and both write.
      _ <- Helper.booleanToFuture(TransactionRequestLockFailed, cc = callContext) {
        code.bankconnectors.DoobieTransactionRequestQueries.lockTransactionRequest(transactionRequestId.value).isDefined
      }
      _ <- Helper.booleanToFuture(
        s"$InvalidTransactionRequestId Transaction Request ${transactionRequestId.value} does not belong to BANK_ID ${bankId.value} and ACCOUNT_ID ${accountId.value}.",
        cc = callContext) {
        tr.from.bank_id == bankId.value && tr.from.account_id == accountId.value
      }
      _ <- Helper.booleanToFuture(s"$OpenCorridorPromiseTypeMismatch Current type: ${tr.`type`}.", cc = callContext) {
        tr.`type` == TransactionRequestTypes.OPEN_CORRIDOR_PROMISE.toString
      }
      _ <- Helper.booleanToFuture(s"$OpenCorridorPromiseNotPending Current status: ${tr.status}.", cc = callContext) {
        tr.status == TransactionRequestStatus.PENDING.toString
      }
      (existingAttributes, callContext) <- NewStyle.function.getTransactionRequestAttributes(bankId, transactionRequestId, callContext)
      existingEvidence = existingAttributes
        .filter(attribute => promiseEvidenceAttributeNames.contains(attribute.name))
        .map(attribute => attribute.name -> attribute.value).toMap
      _ <- Helper.booleanToFuture(OpenCorridorPromiseEvidenceConflict, cc = callContext) {
        existingEvidence.isEmpty || existingEvidence == submittedEvidence
      }
      (promiseJson, callContext) <-
        if (existingEvidence.isEmpty) {
          val reportedAt = APIUtil.DateWithMsFormat.format(new Date())
          val attributes = (submittedEvidence
            + (PromiseAttributeReportedBy -> user.userId)
            + (PromiseAttributeReportedAt -> reportedAt))
            .toList.map { case (name, value) =>
              TransactionRequestAttributeJsonV400(name, TransactionRequestAttributeType.STRING.toString, value)
            }
          NewStyle.function.createTransactionRequestAttributes(
            bankId, transactionRequestId, attributes, isPersonal = false, callContext
          ) map { case (_, callContext) =>
            // First attach only (idempotent redeliveries skip this branch): the
            // promise now exists on-chain, so the beneficiary bank gets its
            // evidence-bearing credit notification immediately — the promise is
            // what gives it the confidence to pay out ahead of settlement.
            enqueueCreditNotification(transactionRequestId, submittedEvidence)
            (buildPromiseJson(tr, submittedEvidence, user.userId, reportedAt), callContext)
          }
        } else {
          // Idempotent redelivery: identical evidence already attached — return the stored record.
          val reportedBy = existingAttributes.find(_.name == PromiseAttributeReportedBy).map(_.value).getOrElse("")
          val reportedAt = existingAttributes.find(_.name == PromiseAttributeReportedAt).map(_.value).getOrElse("")
          Future.successful((buildPromiseJson(tr, existingEvidence, reportedBy, reportedAt), callContext))
        }
    } yield (promiseJson, callContext)
  }

  private implicit val wireFormats: Formats = Serialization.formats(NoTypeHints)

  /** Build and enqueue the `obp_credit_notification` for a promise whose evidence
    * was just attached. The outbox row's correlation id is the promise TR id
    * (settlement-scoped messages use the settlement id there instead). */
  private def enqueueCreditNotification(
    transactionRequestId: TransactionRequestId,
    evidence: Map[String, String]
  ): Unit =
    MappedTransactionRequest
      .findByTransactionRequestId(transactionRequestId.value)
      .foreach { row =>
        // The CBS is asked to credit a specific customer: name + account
        // routing, read back from the promise TR's stored create body
        // (`mDetails`). Absent only when a legacy row predates the field.
        val beneficiary = scala.util.Try(org.json4s.native.JsonMethods.parse(row.details))
          .toOption.flatMap { details =>
            def str(field: JValue): Option[String] = field match {
              case JString(s) if s.trim.nonEmpty => Some(s)
              case _ => None
            }
            str(details \ "to" \ "other_account_routing_address").map { address =>
              OpenCorridorBeneficiary(
                name = str(details \ "to" \ "name").getOrElse(""),
                account_routing = OpenCorridorAccountRouting(
                  scheme = str(details \ "to" \ "other_account_routing_scheme").getOrElse(""),
                  address = address
                )
              )
            }
          }
        val wireBody = OutBoundOpenCorridorCreditNotification(
          transaction_request_id = transactionRequestId.value,
          value = OpenCorridorMoneyValue(row.bodyValueCurrency, row.bodyValueAmount),
          description = Option(row.bodyDescription).filter(_.nonEmpty),
          originator = Option(row.originatorName).filter(_.nonEmpty).map(name =>
            OpenCorridorOriginator(name, Option(row.originatorAddress).filter(_.nonEmpty))),
          beneficiary = beneficiary,
          return_of = scala.util.Try(org.json4s.native.JsonMethods.parse(row.details))
            .toOption.flatMap(_ \ "return_of" match {
              case JString(s) if s.trim.nonEmpty => Some(s)
              case _ => None
            }),
          netting_snapshot_id = None,
          promise_id = evidence.get(PromiseAttributeTxHash),
          promise_blockchain = evidence.get(PromiseAttributeBlockchain),
          promise_commitment = evidence.get(PromiseAttributeCommitment),
          promise_salt = evidence.get(PromiseAttributeSalt),
          promise_preimage = evidence.get(PromiseAttributePreimage)
        )
        MessageOutbox.enqueue(
          MessageOutbox.TYPE_OPEN_CORRIDOR, transactionRequestId.value,
          MessageOutbox.SUBJECT_TYPE_TRANSACTION_REQUEST_ID,
          "obp_credit_notification", row.toBankId,
          Serialization.write(wireBody))
      }

  private def buildPromiseJson(
    tr: TransactionRequest,
    evidence: Map[String, String],
    reportedByUserId: String,
    reportedAt: String
  ): OpenCorridorPromiseJsonV700 =
    OpenCorridorPromiseJsonV700(
      transaction_request_id = tr.id.value,
      transaction_request_status = tr.status,
      tx_hash = evidence.getOrElse(PromiseAttributeTxHash, ""),
      blockchain = evidence.getOrElse(PromiseAttributeBlockchain, ""),
      commitment = evidence.getOrElse(PromiseAttributeCommitment, ""),
      salt = evidence.getOrElse(PromiseAttributeSalt, ""),
      preimage = evidence.getOrElse(PromiseAttributePreimage, ""),
      reported_by_user_id = reportedByUserId,
      reported_at = reportedAt
    )
}
