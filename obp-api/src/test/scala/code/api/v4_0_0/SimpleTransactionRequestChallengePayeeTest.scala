/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.api.v4_0_0

import code.api.Constant.SYSTEM_OWNER_VIEW_ID
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages.attemptedToOpenAnEmptyBox
import code.metadata.counterparties.Counterparties
import code.model.BankAccountX
import code.transactionrequests.MappedTransactionRequest
import com.openbankproject.commons.model.enums.TransactionRequestStatus
import com.openbankproject.commons.model.{AccountId, AmountOfMoneyJsonV121, BankAccount, BankId}
import net.liftweb.mapper.By
import org.json4s.native.Serialization.write
import org.scalatest.Tag

/**
 * This test checks that answering the challenge of a SIMPLE transaction request pays the
 * account the payer named, and no other.
 *
 * A SIMPLE request names its payee by routing. When it is created, the routing schemes are
 * normalised (`snakify(scheme).toUpperCase`, so `obp` becomes `OBP`) and a counterparty is found
 * or created with them. When the challenge is answered, `createTransactionAfterChallengeV210`
 * does not reuse that counterparty: it looks one up again from the routing stored in the request
 * body, which still has the scheme as the payer wrote it. The database comparison is
 * case-sensitive, so `obp` misses the counterparty stored as `OBP`, and the lookup falls back to
 * the secondary routing. When that is empty, as it usually is, the fallback asks for any
 * counterparty whose secondary routing is empty, belonging to anyone, and the payment goes to
 * whatever account that counterparty points at.
 *
 * The scenario sets that up: an unrelated account owns a counterparty with an empty secondary
 * routing that points at a decoy account, created before the payer's request so that it comes
 * first. The payer then sends a SIMPLE request to the intended account with the scheme written
 * in lower case, for an amount that needs a challenge, and answers it.
 */
class SimpleTransactionRequestChallengePayeeTest extends V400ServerSetup {

  object VersionOfApi extends Tag("v4.0.0")
  object SimpleChallengePayeeTag extends Tag("SimpleTransactionRequestChallengePayee")

  private val currency = "AED"
  // Above the challenge threshold, so the request waits for a challenge answer.
  private val amount = BigDecimal("30000.00")

  private def balanceOf(bankId: BankId, accountId: AccountId): BigDecimal =
    BankAccountX(bankId, accountId).map(_.balance).openOrThrowException(attemptedToOpenAnEmptyBox)

  /**
   * Runs the whole story. With `forgetRecordedCounterparty`, the counterparty id recorded at creation
   * is cleared before the challenge is answered, which is the state of requests created before the
   * id was recorded, so the challenge step has to resolve the routing from the body.
   */
  private def payWithChallenge(forgetRecordedCounterparty: Boolean): Unit = {
    setPropsValues("transactionRequests_supported_types" -> "SEPA,SANDBOX_TAN,FREE_FORM,COUNTERPARTY,ACCOUNT,ACCOUNT_OTP,SIMPLE,CARD,AGENT_CASH_WITHDRAWAL")

    Given("a payer's account, the account they want to pay, and an unrelated account with a decoy payee")
    // Each run gets its own bank and accounts, so the two scenarios do not share balances or counterparties.
    val runSuffix = java.util.UUID.randomUUID().toString.take(8)
    val bankId = createBank(s"__simple-payee-bank-$runSuffix").bankId
    val payerAccount: BankAccount = createAccountRelevantResource(Some(resourceUser1), bankId, AccountId(s"__simple_payer_$runSuffix"), currency)
    val intendedAccount: BankAccount = createAccountRelevantResource(None, bankId, AccountId(s"__simple_intended_$runSuffix"), currency)
    val unrelatedAccount: BankAccount = createAccountRelevantResource(Some(resourceUser2), bankId, AccountId(s"__simple_unrelated_$runSuffix"), currency)
    val decoyAccount: BankAccount = createAccountRelevantResource(None, bankId, AccountId(s"__simple_decoy_$runSuffix"), currency)

    And("the unrelated account owns a counterparty that points at the decoy account and has an empty secondary routing")
    Counterparties.counterparties.vend.createCounterparty(
      createdByUserId = resourceUser2.userId,
      thisBankId = bankId.value,
      thisAccountId = unrelatedAccount.accountId.value,
      thisViewId = SYSTEM_OWNER_VIEW_ID,
      name = "Decoy payee of an unrelated account",
      otherAccountRoutingScheme = "OBP",
      otherAccountRoutingAddress = decoyAccount.accountId.value,
      otherBankRoutingScheme = "OBP",
      otherBankRoutingAddress = bankId.value,
      otherBranchRoutingScheme = "",
      otherBranchRoutingAddress = "",
      isBeneficiary = true,
      otherAccountSecondaryRoutingScheme = "",
      otherAccountSecondaryRoutingAddress = "",
      description = "",
      currency = currency,
      bespoke = Nil
    ).openOrThrowException(attemptedToOpenAnEmptyBox)

    val payerBalanceBefore = balanceOf(bankId, payerAccount.accountId)
    val intendedBalanceBefore = balanceOf(bankId, intendedAccount.accountId)
    val decoyBalanceBefore = balanceOf(bankId, decoyAccount.accountId)

    When("the payer creates a SIMPLE request to the intended account, writing the routing scheme as `obp`")
    val simpleBody = TransactionRequestBodySimpleJsonV400(
      to = PostSimpleCounterpartyJson400(
        name = "Intended payee",
        description = "The account the payer wants to pay",
        other_bank_routing_scheme = "obp",
        other_bank_routing_address = bankId.value,
        other_account_routing_scheme = "obp",
        other_account_routing_address = intendedAccount.accountId.value,
        other_account_secondary_routing_scheme = "",
        other_account_secondary_routing_address = "",
        other_branch_routing_scheme = "",
        other_branch_routing_address = ""
      ),
      value = AmountOfMoneyJsonV121(currency, amount.toString),
      description = "SIMPLE payment that needs a challenge",
      charge_policy = "SHARED"
    )
    val transactionRequestsRequest = (v4_0_0_Request / "banks" / bankId.value / "accounts" / payerAccount.accountId.value /
      SYSTEM_OWNER_VIEW_ID / "transaction-request-types" / "SIMPLE" / "transaction-requests").POST <@ user1
    val createResponse = makePostRequest(transactionRequestsRequest, write(simpleBody))

    Then("the request is created and waits for a challenge answer")
    withClue(createResponse.body) { createResponse.code shouldBe 201 }
    val transactionRequestId = (createResponse.body \ "id").values.toString
    val challengeId = (createResponse.body \ "challenges" \ "id").children.headOption.map(_.values.toString).getOrElse("")
    withClue(createResponse.body) { challengeId should not be empty }

    When("the payer answers the challenge")
    val answerRequest = (v4_0_0_Request / "banks" / bankId.value / "accounts" / payerAccount.accountId.value /
      SYSTEM_OWNER_VIEW_ID / "transaction-request-types" / "SIMPLE" / "transaction-requests" / transactionRequestId / "challenge").POST <@ user1
    if (forgetRecordedCounterparty) {
      And("the request carries no recorded counterparty, as requests created before the fix do not")
      MappedTransactionRequest.find(By(MappedTransactionRequest.mTransactionRequestId, transactionRequestId))
        .map(_.mCounterpartyId("").saveMe()).openOrThrowException(attemptedToOpenAnEmptyBox)
    }
    val answerResponse = makePostRequest(answerRequest, write(ChallengeAnswerJson400(id = challengeId, answer = "123")))

    Then("the payment completes")
    withClue(answerResponse.body) {
      answerResponse.code shouldBe 202
      (answerResponse.body \ "status").values.toString shouldBe TransactionRequestStatus.COMPLETED.toString
    }

    val balanceChanges =
      s"payer ${balanceOf(bankId, payerAccount.accountId) - payerBalanceBefore}, " +
        s"intended ${balanceOf(bankId, intendedAccount.accountId) - intendedBalanceBefore}, " +
        s"decoy ${balanceOf(bankId, decoyAccount.accountId) - decoyBalanceBefore}"
    withClue(s"Balance changes: $balanceChanges. ") {
      And("the payer is debited and the intended account is credited")
      balanceOf(bankId, payerAccount.accountId) shouldBe payerBalanceBefore - amount
      balanceOf(bankId, intendedAccount.accountId) shouldBe intendedBalanceBefore + amount

      And("the decoy account of the unrelated counterparty is not")
      balanceOf(bankId, decoyAccount.accountId) shouldBe decoyBalanceBefore
    }
  }

  feature("Answering a SIMPLE transaction request's challenge pays the payee the payer named") {

    scenario("A new request: the counterparty resolved at creation is paid", VersionOfApi, SimpleChallengePayeeTag) {
      payWithChallenge(forgetRecordedCounterparty = false)
    }

    scenario("A request created before the counterparty was recorded: its routing is resolved among the paying account's counterparties",
      VersionOfApi, SimpleChallengePayeeTag) {
      payWithChallenge(forgetRecordedCounterparty = true)
    }
  }
}
