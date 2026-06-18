package code.bankconnectors.opencorridor

import org.json4s._
import code.api.ChargePolicy
import code.api.util.APIUtil.getScaMethodAtInstance
import code.api.util.ErrorMessages._
import code.api.util.{CallContext, NewStyle}
import code.api.v7_0_0.JSONFactory700.TransactionRequestBodyOpenCorridorJsonV700
import code.util.Helper
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.ChallengeType.OBP_TRANSACTION_REQUEST_CHALLENGE
import org.json4s.native.Serialization.write
import org.json4s.NoTypeHints
import org.json4s.native.Serialization
import net.liftweb.util.StringHelpers

import scala.concurrent.Future

// OPEN_CORRIDOR Transaction Request — Travel-Rule-friendly payment.
//
// Money-movement is identical to SIMPLE today (same beneficiary routing shape).
// What's distinct is the mandatory `originator` block, persisted alongside the TR
// and surfaced on v7 responses. Lives in its own package so future OPEN_CORRIDOR
// extensions (Cardano Promise, netting, settlement) have a home that does not bloat
// `LocalMappedConnector`.
object OpenCorridorProcessor {

  // Create an OPEN_CORRIDOR Transaction Request: validate the originator block,
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

    val transactionRequestType = TransactionRequestType("OPEN_CORRIDOR")

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
      (toAccount, callContext) <- NewStyle.function.getBankAccountFromCounterparty(toCounterparty, true, callContext)
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
}
