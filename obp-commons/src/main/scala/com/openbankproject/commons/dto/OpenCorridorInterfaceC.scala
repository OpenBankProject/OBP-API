package com.openbankproject.commons.dto

import org.json4s.JValue

/**
 * Open Corridor — Interface C wire DTOs (OBP-API → Bank Node over RabbitMQ).
 *
 * LOCKED WIRE CONTRACT. The consuming side is already implemented and
 * transport-integration-tested in the OBP Bank Node
 * (`OBP-Bank-Node/crates/obp-bank-node/src/interface_c/types.rs` + `router.rs`);
 * these classes must serialize to exactly that shape.
 *
 * Unlike the stock connector DTOs, these bodies carry NO
 * `outboundAdapterCallContext` wrapper and the field names ARE the wire format
 * (lower_snake_case): OBP-API publishes one AMQP message per operation to the
 * bank's vhost queue `obp_rpc_queue` with the AMQP properties carrying the
 * routing metadata (`messageId` = operation, `correlationId`, `replyTo`), and
 * the Bank Node parses the body directly into its own snake_case types.
 *
 * The reply travels as the OBP inbound envelope (`InBoundOpenCorridorReply`):
 * camelCase envelope keys, `status.errorCode == ""` meaning success, and a
 * message-specific snake_case `data` object.
 */

case class OpenCorridorMoneyValue(
  currency: String,
  amount: String
)

case class OpenCorridorOriginator(
  name: String,
  address: Option[String]
)

case class OpenCorridorAccountRouting(
  scheme: String,
  address: String
)

/** The customer the beneficiary bank's CBS is asked to credit. */
case class OpenCorridorBeneficiary(
  name: String,
  account_routing: OpenCorridorAccountRouting
)

/**
 * `obp_credit_notification` — published to the CREDITOR (beneficiary) bank's
 * vhost: "credit your customer". `beneficiary` names the customer and account
 * to credit — the CBS cannot act (or refuse) without it. The three `promise_*`
 * evidence fields are the point of the salt relay: they carry the commit–reveal
 * data (commitment, salt, preimage) so the beneficiary bank can open the
 * originating bank's on-chain commitment without its cooperation. They are
 * opaque to OBP-API — relayed verbatim from what the originating Bank Node
 * reported back (§5.1 report-back endpoint), never parsed.
 */
case class OutBoundOpenCorridorCreditNotification(
  transaction_request_id: String,
  value: OpenCorridorMoneyValue,
  description: Option[String],
  originator: Option[OpenCorridorOriginator],
  beneficiary: Option[OpenCorridorBeneficiary],
  /// Set when this credit is a RETURN: the transaction_request_id of the
  /// original promise whose credit was refused. The receiving node must not
  /// return a return — a refused return parks for an operator instead.
  return_of: Option[String],
  netting_snapshot_id: Option[String],
  promise_id: Option[String],
  promise_blockchain: Option[String],
  promise_commitment: Option[String],
  promise_salt: Option[String],
  promise_preimage: Option[String]
)

/**
 * Reply `data` for `obp_credit_notification`: the bank verified the evidence
 * (recomputed SHA-256(salt ‖ preimage) against the commitment) and delivered
 * the credit to its CBS.
 */
case class InBoundOpenCorridorCreditNotificationData(
  transaction_request_id: String,
  verified: Boolean,
  cbs_reference: Option[String]
)

/**
 * `obp_settlement_instruction` — published to the DEBTOR bank's vhost: "settle
 * the net now on your rail". `amount` is in MAJOR units (the Bank Node parses
 * to minor units itself, assuming 2 decimals — documented limitation for
 * non-2-exponent currencies). `idempotency_key` is REQUIRED by the consumer:
 * it keeps a durable settlement record per key and will never pay twice for
 * the same key; re-publishing the same instruction returns the recorded state
 * (redelivery doubles as status polling for the SUBMITTED → FINAL transition).
 */
case class OutBoundOpenCorridorSettlementInstruction(
  snapshot_id: Option[String],
  settlement_id: String,
  settlement_system: String,
  currency: String,
  amount: String,
  creditor_bank_id: String,
  creditor_address: String,
  idempotency_key: String,
  /// What the transfer is for: absent = a corridor net settlement;
  /// "PLATFORM_FEE" = the periodic platform fee sweep (creditor = the
  /// platform's settlement account). Same execution semantics either way.
  purpose: Option[String] = None
)

/**
 * `obp_settlement_advice` — published to BOTH party banks' vhosts after a
 * netted settle: "these promises are now covered". Purely reconciliatory — no
 * money moves on this message (the debtor's `obp_settlement_instruction` does
 * that, and at net zero no instruction exists at all — this advice is then the
 * only settle-time message either bank receives).
 * `covered_transaction_request_ids` is the FULL covered list of the pair, both
 * directions: each node stamps whatever matches its own records (credits it
 * paid out AND its own outbound promises); ids of the counterparty's records
 * match nothing there and are ignored.
 * Credit notifications themselves travel at promise-report-back time, not here.
 */
case class OutBoundOpenCorridorSettlementAdvice(
  settlement_id: String,
  currency: String,
  net_amount: String,
  debtor_bank_id: String,
  creditor_bank_id: String,
  covered_transaction_request_ids: List[String],
  idempotency_key: String
)

/** Reply `data` for `obp_settlement_advice`: the bank marked the listed credits settled. */
case class InBoundOpenCorridorSettlementAdviceData(
  settlement_id: String,
  acknowledged: Boolean
)

/**
 * Reply `data` for `obp_settlement_instruction`. `status` is one of:
 *  - SETTLING  — an attempt is in flight (or crashed mid-flight; the node will
 *                not auto-retry an ambiguous attempt)
 *  - SUBMITTED — broadcast to the chain, NOT yet final
 *  - FINAL     — confirmed at >= finality_depth; treat as settled only now
 */
case class InBoundOpenCorridorSettlementData(
  settlement_id: String,
  status: String,
  tx_id: Option[String],
  blockchain: Option[String],
  asset: Option[String],
  asset_amount: Option[String],
  depth: Option[Long],
  finality_depth: Option[Long]
)

/** Envelope keys are camelCase on the wire — matches the Bank Node's `ReplyEnvelope`. */
case class OpenCorridorInboundCallContext(
  correlationId: String
)

/** `errorCode == ""` means success. Non-empty codes are the Bank Node's
  * `OBP-BANK-NODE-*` failures (COMMITMENT-MISMATCH, CBS-DELIVERY-FAILED,
  * SETTLEMENT-FAILED, SETTLEMENT-NOT-CONFIGURED, BAD-MESSAGE, NOT-IMPLEMENTED)
  * and must never be swallowed by the caller. */
case class OpenCorridorReplyStatus(
  errorCode: String,
  backendMessages: List[String]
)

/**
 * The reply envelope the Bank Node sends on `replyTo`, matched by
 * `correlationId`. `data` stays a raw JValue here because its shape depends on
 * the messageId; parse it into `InBoundOpenCorridorCreditNotificationData` /
 * `InBoundOpenCorridorSettlementData` at the call site.
 */
case class InBoundOpenCorridorReply(
  inboundAdapterCallContext: OpenCorridorInboundCallContext,
  status: OpenCorridorReplyStatus,
  data: JValue
)
