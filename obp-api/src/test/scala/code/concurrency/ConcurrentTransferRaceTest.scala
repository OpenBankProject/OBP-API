/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

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
package code.concurrency

import code.api.util.APIUtil.OAuth._
import code.api.v1_4_0.JSONFactory1_4_0.TransactionRequestAccountJsonV140
import code.api.v2_0_0.TransactionRequestBodyJsonV200
import code.api.v4_0_0.ChallengeAnswerJson400
import code.bankconnectors.Connector
import code.model.BankAccountX
import code.transaction.MappedTransaction
import com.openbankproject.commons.model.{AccountId, AmountOfMoneyJsonV121}
import com.openbankproject.commons.model.enums.TransactionRequestStatus
import org.json4s.native.Serialization.write
import org.json4s._

import java.util.Date
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
 * Simulates the two highest-impact money-movement races:
 *
 *  A. Lost balance update — `LocalMappedConnectorInternal.saveTransaction` reads the
 *     balance, adds the amount in memory, then writes the whole row back with no lock
 *     and no version column. N concurrent transfers can each read the same starting
 *     balance and overwrite one another, so fewer debits land than transfers completed.
 *
 *  B. Transaction-request state-machine double-spend — the answer-challenge handler
 *     checks `status == "INITIATED"`, then runs the payment, then flips the status.
 *     The check and the flip are not atomic, so two concurrent answers to the SAME
 *     request can both pass the gate and both execute the payment.
 *
 *  S. Historical-payment balance lost-update — `LocalMappedConnector.saveHistoricalTransaction`
 *     is a second, independent read-modify-write path on the account balance (sibling of A):
 *     it reads `fromAccount.balance`, adds the amount, and `.save()`s the row with no lock.
 *     N concurrent `makeHistoricalPayment` calls reusing one fromAccount snapshot each read the
 *     same starting balance and overwrite one another.
 *
 * All assert the correct outcome, so they are EXPECTED TO FAIL while the races are
 * unfixed — the "expected vs actual" clue is the evidence. Tagged ConcurrencyRace.
 */
class ConcurrentTransferRaceTest extends ConcurrentRaceSetup {
 
  Feature("Concurrent money movement on a single account (transaction-level isolation)") {

    Scenario("A: N concurrent transfers from one account must not lose balance updates", ConcurrencyRace) {
      Given("a funded source account and a payee, with SANDBOX_TAN challenge disabled so each transfer is one-step")
      // High threshold → amounts below it skip the challenge and complete in a single request.
      setPropsValues("transactionRequests_challenge_threshold_SANDBOX_TAN" -> "100000000")
      val bank   = createBank("__conc-transfer-bank-a")
      val bankId = bank.bankId
      val fromId = AccountId("__conc_a_from")
      val toId   = AccountId("__conc_a_to")
      createAccountRelevantResource(Some(resourceUser1), bankId, fromId, "EUR")
      createAccountRelevantResource(Some(resourceUser1), bankId, toId, "EUR")

      val before           = dbAccountBalance(bankId, fromId)
      val n                = 10
      val amountStr        = "1.00" // 1.00 EUR = 100 in smallest currency units
      val debitPerTransfer = 100L

      val toAccountJson = TransactionRequestAccountJsonV140(bankId.value, toId.value)
      val body = write(TransactionRequestBodyJsonV200(
        toAccountJson, AmountOfMoneyJsonV121("EUR", amountStr), "concurrency-A"))

      When(s"$n SANDBOX_TAN transfers are fired concurrently from the same account")
      val responses = fireConcurrently(n) { _ =>
        val req = (v4_0_0_Request / "banks" / bankId.value / "accounts" / fromId.value /
          SystemOwnerViewId / "transaction-request-types" / "SANDBOX_TAN" / "transaction-requests").POST <@ user1
        makePostRequestAsync(req, body)
      }

      Then("the account must be debited exactly once for every transfer that reported COMPLETED")
      val completed = responses.count { r =>
        r.code == 201 && (r.body \ "status").values.toString == TransactionRequestStatus.COMPLETED.toString
      }
      val after           = dbAccountBalance(bankId, fromId)
      val actualDebited   = before - after
      val expectedDebited = completed * debitPerTransfer
      val lostUpdates     = if (debitPerTransfer == 0) 0 else (expectedDebited - actualDebited) / debitPerTransfer
      withClue(s"completed=$completed before=$before after=$after " +
        s"actualDebited=$actualDebited expectedDebited=$expectedDebited lostUpdates=$lostUpdates — ") {
        actualDebited should equal(expectedDebited)
      }
    }

    Scenario("B: concurrent answers to one challenge must execute the payment only once", ConcurrencyRace) {
      Given("a transaction request left in INITIATED state, with SANDBOX_TAN challenge forced on")
      // Zero threshold → every amount requires a challenge, leaving the request INITIATED.
      // DUMMY transport → the challenge is stored as hash("123"), so the fixed answer works
      // without sending a real OTP (same pattern used by ACCOUNT/SEPA tests in test.default.props).
      setPropsValues(
        "transactionRequests_challenge_threshold_SANDBOX_TAN" -> "0",
        "SANDBOX_TAN_OTP_INSTRUCTION_TRANSPORT" -> "DUMMY"
      )
      val bank   = createBank("__conc-transfer-bank-b")
      val bankId = bank.bankId
      val fromId = AccountId("__conc_b_from")
      val toId   = AccountId("__conc_b_to")
      createAccountRelevantResource(Some(resourceUser1), bankId, fromId, "EUR")
      createAccountRelevantResource(Some(resourceUser1), bankId, toId, "EUR")

      val before    = dbAccountBalance(bankId, fromId)
      val amountStr = "10.00" // 10.00 EUR = 1000 in smallest currency units
      val debit     = 1000L

      val toAccountJson = TransactionRequestAccountJsonV140(bankId.value, toId.value)
      val createBody = write(TransactionRequestBodyJsonV200(
        toAccountJson, AmountOfMoneyJsonV121("EUR", amountStr), "concurrency-B"))

      val createReq = (v4_0_0_Request / "banks" / bankId.value / "accounts" / fromId.value /
        SystemOwnerViewId / "transaction-request-types" / "SANDBOX_TAN" / "transaction-requests").POST <@ user1
      val createResp = makePostRequest(createReq, createBody)
      withClue(s"the create transaction-request must be INITIATED: code=${createResp.code} body=${createResp.body} — ") {
        createResp.code should equal(201)
        (createResp.body \ "status").values.toString should equal(TransactionRequestStatus.INITIATED.toString)
      }
      val transRequestId = (createResp.body \ "id").values.toString
      // `challenges` is a JArray; pluck the first element's id rather than letting
      // `\ "id"` map over the array (which would stringify to "List(...)").
      val challengeId = (createResp.body \ "challenges") match {
        case org.json4s.JArray(h :: _) => (h \ "id").values.toString
        case other                           => (other \ "id").values.toString
      }

      When("the same challenge is answered concurrently N times")
      val n = 8
      val answerBody = write(ChallengeAnswerJson400(id = challengeId, answer = "123"))
      val answers = fireConcurrently(n) { _ =>
        val req = (v4_0_0_Request / "banks" / bankId.value / "accounts" / fromId.value /
          SystemOwnerViewId / "transaction-request-types" / "SANDBOX_TAN" / "transaction-requests" /
          transRequestId / "challenge").POST <@ user1
        makePostRequestAsync(req, answerBody)
      }

      Then("the payment must execute exactly once — no double-spend")
      val after         = dbAccountBalance(bankId, fromId)
      val actualDebited = before - after
      val txnCount = MappedTransaction.countByBankAccount(bankId, fromId)
      withClue(s"challengeId=[$challengeId] answer codes=${answers.map(_.code)} " +
        s"firstAnswerBody=${answers.headOption.map(_.body).getOrElse("")} " +
        s"before=$before after=$after actualDebited=$actualDebited (expected=$debit) " +
        s"mappedTxnCount=$txnCount (expected=1) — ") {
        actualDebited should equal(debit)
        txnCount should equal(1L)
      }
    }

    Scenario("S: N concurrent makeHistoricalPayment calls must not lose balance updates", ConcurrencyRace) {
      Given("a funded source account and a payee, with one shared fromAccount snapshot")
      val bank   = createBank("__conc-hist-bank-s")
      val bankId = bank.bankId
      val fromId = AccountId("__conc_s_from")
      val toId   = AccountId("__conc_s_to")
      createAccountRelevantResource(Some(resourceUser1), bankId, fromId, "EUR")
      createAccountRelevantResource(Some(resourceUser1), bankId, toId, "EUR")

      // makeHistoricalPayment takes BankAccount objects directly — the same snapshot is reused by
      // every concurrent call, which is exactly how saveHistoricalTransaction reads a stale balance.
      val fromAccount = BankAccountX(bankId, fromId).getOrElse(fail("couldn't get from account"))
      val toAccount   = BankAccountX(bankId, toId).getOrElse(fail("couldn't get to account"))

      val before        = dbAccountBalance(bankId, fromId)
      val n             = 8
      val amount        = BigDecimal("1.00") // 1.00 EUR = 100 in smallest currency units
      val debitPerCall  = 100L

      When(s"$n historical payments are fired concurrently from the same account")
      val results = runConcurrentWithBarrier(n) { i =>
        Await.result(
          Connector.connector.vend.makeHistoricalPayment(
            fromAccount, toAccount, new Date(), new Date(),
            amount, "EUR", s"concurrency-S-$i", "SANDBOX_TAN", "SHARED", None
          ).map(_._1),
          30.seconds
        )
      }

      Then("the account must be debited once per successful payment")
      val succeeded       = results.count(_.map(_.isDefined).getOrElse(false))
      val after           = dbAccountBalance(bankId, fromId)
      val actualDebited   = before - after
      val expectedDebited = succeeded * debitPerCall
      val lostUpdates     = if (debitPerCall == 0) 0 else (expectedDebited - actualDebited) / debitPerCall
      withClue(s"succeeded=$succeeded before=$before after=$after " +
        s"actualDebited=$actualDebited expectedDebited=$expectedDebited lostUpdates=$lostUpdates — " +
        s"saveHistoricalTransaction reads fromAccount.balance and .save()s with no lock — ") {
        actualDebited should equal(expectedDebited)
      }
    }
  }
}
