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
import code.api.util.ErrorMessages
import code.api.v1_4_0.JSONFactory1_4_0.TransactionRequestAccountJsonV140
import code.api.v2_0_0.TransactionRequestBodyJsonV200
import code.metadata.counterparties.Counterparties
import code.model.BankAccountX
import com.openbankproject.commons.model.{AccountId, AmountOfMoneyJsonV121, BankId, ErrorMessage}
import com.openbankproject.commons.util.ApiVersion
import org.json4s.native.Serialization.write
import org.scalatest.Tag

/**
 * This suite covers the checks on a REFUND transaction request. A refund either pays back money the
 * account received (`from`) or asks for money the account paid out to be sent back (`to`). In both
 * cases OBP-API may only debit the account the caller is authorised on, the refund must be for a
 * payment between the account and the named party, and a payment can be refunded only once.
 */
class RefundTransactionRequestTest extends V400ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)

  // The caller's account (A) and the other party (B), both at the same bank. user1 has the owner view
  // on both, so only the refund rules themselves can refuse the requests below.
  val bankId = testBankId1
  val callerAccountId = testAccountId1
  val otherPartyAccountId = testAccountId0
  // An account that has nothing to do with the payment being refunded.
  val unrelatedBankId = testBankId2
  val unrelatedAccountId = testAccountId0

  def transactionRequestsUrl(bank: BankId, account: AccountId, transactionRequestType: String) =
    v4_0_0_Request / "banks" / bank.value / "accounts" / account.value / SYSTEM_OWNER_VIEW_ID /
      "transaction-request-types" / transactionRequestType / "transaction-requests"

  override def beforeEach(): Unit = {
    super.beforeEach()
    setPropsValues(
      "transactionRequests_supported_types" -> "SANDBOX_TAN,COUNTERPARTY,SEPA,ACCOUNT_OTP,ACCOUNT,SIMPLE,REFUND",
      "REFUND_OTP_INSTRUCTION_TRANSPORT" -> "DUMMY")
  }

  /** This pays 1 EUR from the caller's account to the other party and returns the transaction id. */
  def payTheOtherParty(): String = {
    val body = TransactionRequestBodyJsonV200(
      TransactionRequestAccountJsonV140(bankId.value, otherPartyAccountId.value),
      AmountOfMoneyJsonV121("EUR", "1"),
      "A payment that may later be refunded")
    val response = makePostRequest(transactionRequestsUrl(bankId, callerAccountId, "ACCOUNT").POST <@ user1, write(body))
    response.code should equal(201)
    val created = response.body.extract[TransactionRequestWithChargeJSON400]
    created.transaction_ids.headOption.getOrElse(fail(s"The payment did not complete: ${created.status}"))
  }

  def refundBody(transactionId: String, to: Option[TransactionRequestRefundTo], from: Option[TransactionRequestRefundFrom]) =
    TransactionRequestBodyRefundJsonV400(
      to = to,
      from = from,
      value = AmountOfMoneyJsonV121("EUR", "1"),
      description = "Refund",
      refund = RefundJson(transaction_id = transactionId, reason_code = "CUST"))

  def askForMoneyBack(transactionId: String, fromBank: BankId, fromAccount: AccountId) =
    makePostRequest(
      transactionRequestsUrl(bankId, callerAccountId, "REFUND").POST <@ user1,
      write(refundBody(transactionId, Some(TransactionRequestRefundTo(Some(fromBank.value), Some(fromAccount.value), None)), None)))

  /**
   * This creates a beneficiary counterparty on the caller's account that points at the other party's
   * account, with that account's id as its IBAN: the shape the challenge-answer step looks for when it
   * works out which account a `to` refund debits.
   */
  def counterpartyOnCallerAccountPointingAtOtherParty(): String =
    Counterparties.counterparties.vend.createCounterparty(
      createdByUserId = resourceUser1.userId,
      thisBankId = bankId.value,
      thisAccountId = callerAccountId.value,
      thisViewId = "",
      name = java.util.UUID.randomUUID.toString,
      otherAccountRoutingScheme = "OBP",
      otherAccountRoutingAddress = otherPartyAccountId.value,
      otherBankRoutingScheme = "OBP",
      otherBankRoutingAddress = bankId.value,
      otherBranchRoutingScheme = "OBP",
      otherBranchRoutingAddress = "Berlin",
      isBeneficiary = true,
      otherAccountSecondaryRoutingScheme = "IBAN",
      otherAccountSecondaryRoutingAddress = otherPartyAccountId.value,
      description = "",
      currency = "EUR",
      bespoke = Nil
    ).map(_.counterpartyId).openOrThrowException("The counterparty could not be created")

  /**
   * This creates a beneficiary counterparty on the caller's account for an account at another bank, which
   * OBP-API represents by a stand-in account built from the counterparty's routings, and pays it 1 EUR.
   * It returns the counterparty id and the transaction id.
   */
  def payAnExternalParty(): (String, String) = {
    // On the local connector, money to or from another bank passes through the default bank's
    // settlement accounts, so the test bank needs them.
    List(code.api.Constant.INCOMING_SETTLEMENT_ACCOUNT_ID, code.api.Constant.OUTGOING_SETTLEMENT_ACCOUNT_ID).foreach { settlementAccountId =>
      if (code.model.dataAccess.MappedBankAccount.find(
        net.liftweb.mapper.By(code.model.dataAccess.MappedBankAccount.bank, code.api.util.APIUtil.defaultBankId),
        net.liftweb.mapper.By(code.model.dataAccess.MappedBankAccount.theAccountId, settlementAccountId)).isEmpty)
        code.model.dataAccess.MappedBankAccount.create
          .bank(code.api.util.APIUtil.defaultBankId).theAccountId(settlementAccountId).accountCurrency("EUR").saveMe()
    }
    val externalAccountReference = "EXTERNAL-" + java.util.UUID.randomUUID.toString.take(8)
    val counterpartyId = Counterparties.counterparties.vend.createCounterparty(
      createdByUserId = resourceUser1.userId,
      thisBankId = bankId.value,
      thisAccountId = callerAccountId.value,
      thisViewId = "",
      name = java.util.UUID.randomUUID.toString,
      otherAccountRoutingScheme = "BBAN",
      otherAccountRoutingAddress = externalAccountReference,
      otherBankRoutingScheme = "BIC",
      otherBankRoutingAddress = "EXTERNALXXX",
      otherBranchRoutingScheme = "",
      otherBranchRoutingAddress = "",
      isBeneficiary = true,
      otherAccountSecondaryRoutingScheme = "IBAN",
      otherAccountSecondaryRoutingAddress = externalAccountReference,
      description = "",
      currency = "EUR",
      bespoke = Nil
    ).map(_.counterpartyId).openOrThrowException("The counterparty could not be created")
    val body = code.api.v2_1_0.TransactionRequestBodyCounterpartyJSON(
      code.api.v2_1_0.CounterpartyIdJson(counterpartyId), AmountOfMoneyJsonV121("EUR", "1"), "A payment to another bank", "SHARED")
    val response = makePostRequest(transactionRequestsUrl(bankId, callerAccountId, "COUNTERPARTY").POST <@ user1, write(body))
    response.code should equal(201)
    val created = response.body.extract[TransactionRequestWithChargeJSON400]
    (counterpartyId, created.transaction_ids.headOption.getOrElse(fail(s"The payment did not complete: ${created.status}")))
  }

  def balanceOf(account: AccountId): BigDecimal =
    BankAccountX(bankId, account).map(_.balance).openOrThrowException("Account not found")

  feature("A REFUND only debits the account the caller is authorised on") {

    scenario("Answering the challenge of a `to` refund does not debit another account on this instance", VersionOfApi) {
      Given("A payment from the caller to the other party, and a refund request asking for it back")
      val transactionId = payTheOtherParty()
      counterpartyOnCallerAccountPointingAtOtherParty()
      val refundResponse = askForMoneyBack(transactionId, bankId, otherPartyAccountId)
      refundResponse.code should equal(201)
      val refund = refundResponse.body.extract[TransactionRequestWithChargeJSON400]
      val challenge = refund.challenges.find(_.user_id == resourceUser1.userId).getOrElse(fail("No challenge for the caller"))
      val otherPartyBalanceBefore = balanceOf(otherPartyAccountId)

      When("The caller answers the challenge on their own account")
      val answerResponse = makePostRequest(
        (transactionRequestsUrl(bankId, callerAccountId, "REFUND") / refund.id / "challenge").POST <@ user1,
        write(ChallengeAnswerJson400(id = challenge.id, answer = "123")))

      Then("The payment is refused and the other party's account is not debited")
      answerResponse.code should equal(400)
      answerResponse.body.extract[ErrorMessage].message should startWith(ErrorMessages.InsufficientAuthorisationToCreateTransactionRequest)
      balanceOf(otherPartyAccountId) should equal(otherPartyBalanceBefore)
    }

    scenario("Without an adapter making the payment, a `to` refund from an external party is not paid", VersionOfApi) {
      Given("A payment to an account at another bank, and a refund request asking for it back")
      val (counterpartyId, transactionId) = payAnExternalParty()
      val refundResponse = makePostRequest(
        transactionRequestsUrl(bankId, callerAccountId, "REFUND").POST <@ user1,
        write(refundBody(transactionId, Some(TransactionRequestRefundTo(None, None, Some(counterpartyId))), None)))
      refundResponse.code should equal(201)
      val refund = refundResponse.body.extract[TransactionRequestWithChargeJSON400]
      val challenge = refund.challenges.find(_.user_id == resourceUser1.userId).getOrElse(fail("No challenge for the caller"))
      val callerBalanceBefore = balanceOf(callerAccountId)

      When("The caller answers the challenge, on an instance where the local connector would make the payment")
      val answerResponse = makePostRequest(
        (transactionRequestsUrl(bankId, callerAccountId, "REFUND") / refund.id / "challenge").POST <@ user1,
        write(ChallengeAnswerJson400(id = challenge.id, answer = "123")))

      Then("The payment is refused and the caller's account is not credited")
      answerResponse.code should equal(400)
      answerResponse.body.extract[ErrorMessage].message should startWith(ErrorMessages.InvalidTransactionRequestType)
      balanceOf(callerAccountId) should equal(callerBalanceBefore)
    }

    scenario("A challenge can't be answered through an account the transaction request does not involve", VersionOfApi) {
      Given("A refund request between the caller's account and the other party")
      val transactionId = payTheOtherParty()
      val refund = askForMoneyBack(transactionId, bankId, otherPartyAccountId).body.extract[TransactionRequestWithChargeJSON400]
      val challenge = refund.challenges.find(_.user_id == resourceUser1.userId).getOrElse(fail("No challenge for the caller"))

      When("The challenge is answered through an unrelated account")
      val answerResponse = makePostRequest(
        (transactionRequestsUrl(unrelatedBankId, unrelatedAccountId, "REFUND") / refund.id / "challenge").POST <@ user1,
        write(ChallengeAnswerJson400(id = challenge.id, answer = "123")))

      Then("The transaction request is not found for that account")
      answerResponse.code should equal(400)
      answerResponse.body.extract[ErrorMessage].message should startWith(ErrorMessages.InvalidTransactionRequestId)
    }
  }

  feature("A REFUND must be for a payment between the account and the named party") {

    scenario("A `to` refund naming a party the payment was not made with is refused", VersionOfApi) {
      val transactionId = payTheOtherParty()
      val response = askForMoneyBack(transactionId, unrelatedBankId, unrelatedAccountId)
      response.code should equal(400)
      response.body.extract[ErrorMessage].message should startWith(ErrorMessages.RefundTransactionNotWithThisParty)
    }

    scenario("A `from` refund of a payment the account made, not received, is refused", VersionOfApi) {
      Given("A payment the caller made to the other party")
      val transactionId = payTheOtherParty()
      val counterpartyId = counterpartyOnCallerAccountPointingAtOtherParty()

      When("The caller tries to pay it back to the other party as if they had received it")
      val response = makePostRequest(
        transactionRequestsUrl(bankId, callerAccountId, "REFUND").POST <@ user1,
        write(refundBody(transactionId, None, Some(TransactionRequestRefundFrom(counterpartyId)))))

      Then("The refund is refused")
      response.code should equal(400)
      response.body.extract[ErrorMessage].message should startWith(ErrorMessages.RefundTransactionNotWithThisParty)
    }

    scenario("A payment can be refunded only once", VersionOfApi) {
      val transactionId = payTheOtherParty()
      askForMoneyBack(transactionId, bankId, otherPartyAccountId).code should equal(201)

      val secondResponse = askForMoneyBack(transactionId, bankId, otherPartyAccountId)
      secondResponse.code should equal(400)
      secondResponse.body.extract[ErrorMessage].message should startWith(ErrorMessages.RefundedTransaction)
    }
  }
}
