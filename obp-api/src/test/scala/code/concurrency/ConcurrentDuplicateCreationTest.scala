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

import code.accountholders.{AccountHolders, MapperAccountHolders}
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.v2_0_0.CreateEntitlementJSON
import code.consumer.Consumers
import code.entitlement.Entitlement
import code.metadata.counterparties.{Counterparties, MappedCounterpartyMetadata}
import code.model.Consumer
import code.model.dataAccess.ResourceUser
import code.users.LiftUsers
import code.api.util.DoobieUtil
import code.usercustomerlinks.DoobieUserCustomerLinkProvider
import com.openbankproject.commons.model.{AccountId, BankIdAccountId}
import doobie.implicits._
import org.json4s.native.Serialization.write

import java.util.{Date, UUID}
import scala.util.Failure

/**
 * Simulates three check-then-insert races. Each asserts the correct row count, so each is
 * EXPECTED TO FAIL while the race is unfixed — the "expected vs actual" clue is the evidence.
 *
 *  C. Entitlement grant — `MappedEntitlements.addEntitlement` inserts unconditionally and the
 *     only unique index is on the per-row UUID, so concurrent identical grants all persist.
 *  D. Account holder — `getOrCreateAccountHolder` does find-then-create with no unique index on
 *     (user, bank, account), so concurrent callers all miss the find and all insert.
 *  F. Counterparty metadata — `getOrCreateMetadata` does check-then-insert, BUT a
 *     UniqueIndex(counterpartyId) backs the table; this verifies the second insert's constraint
 *     conflict is handled gracefully (no thrown 500) rather than testing for duplicate rows.
 *
 * C runs over HTTP (the request-per-transaction path the user asked about). D and F use a
 * barrier-synchronised provider-layer fan-out because their contended code is a getOrCreate
 * method, not an HTTP endpoint.
 *
 *  I. OAuth user duplicate — LiftUsers.getOrCreateUserByProviderId does find-then-create with
 *     no surrounding transaction. ResourceUser has UniqueIndex(provider_, providerId), so the
 *     second concurrent create throws an uncaught JDBC constraint-violation exception rather
 *     than gracefully returning the existing user. Concurrent first-time OAuth logins → one
 *     request gets a 500 instead of the expected login response.
 *
 *  L. UserCustomerLink duplicate — DoobieUserCustomerLinkProvider.getOCreateUserCustomerLink
 *     does find-then-create with no surrounding transaction. mappedusercustomerlink has
 *     UniqueIndex(mUserId, mCustomerId), so the second concurrent create throws an uncaught
 *     JDBC exception rather than returning the existing link.
 *
 *  W. OAuth2 consumer duplicate — Consumers.getOrCreateConsumer does find-then-create with no
 *     surrounding transaction. Consumer has UniqueIndex(azp, sub); unlike I/L the create IS
 *     wrapped in tryo, so the second concurrent insert does not throw a 500 — instead the
 *     violation is swallowed into a Failure box and the caller gets no usable consumer (it
 *     cannot authenticate), which defeats the get-or-create contract just the same.
 */
class ConcurrentDuplicateCreationTest extends ConcurrentRaceSetup {

  Feature("Concurrent check-then-insert must not create duplicate rows") {

    Scenario("C: concurrent identical entitlement grants must create exactly one row", ConcurrencyRace) {
      Given("user1 can grant entitlements at any bank, and a target user without the role")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canCreateEntitlementAtAnyBank.toString)
      val targetUserId = resourceUser2.userId
      val role         = ApiRole.CanGetAnyUser.toString
      val before       = dbEntitlementCount("", targetUserId, role)
      val n            = 8
      val body         = write(CreateEntitlementJSON(bank_id = "", role_name = role))

      When(s"$n identical grant requests are fired concurrently")
      val responses = fireConcurrently(n) { _ =>
        val req = (v2_0_0_Request / "users" / targetUserId / "entitlements").POST <@ user1
        makePostRequestAsync(req, body)
      }

      Then("exactly one entitlement row must exist for (bank,user,role)")
      val after   = dbEntitlementCount("", targetUserId, role)
      val created = after - before
      withClue(s"response codes=${responses.map(_.code)} before=$before after=$after created=$created (expected 1) — ") {
        created should equal(1L)
      }
    }

    Scenario("D: concurrent getOrCreateAccountHolder for one (user,account) must create one row", ConcurrencyRace) {
      Given("an account owned by user1, with user3 not yet a holder")
      val bank      = createBank("__conc-holder-bank")
      val bankId    = bank.bankId
      val accountId = AccountId("__conc_holder_acc")
      createAccountRelevantResource(Some(resourceUser1), bankId, accountId, "EUR")
      val user  = resourceUser3
      val biaId = BankIdAccountId(bankId, accountId)

      def holderCount: Long = MapperAccountHolders.count(bankId.value, accountId.value)

      val before = holderCount
      val n      = 8

      When(s"$n threads concurrently getOrCreateAccountHolder for the same (user3, account)")
      val results = runConcurrentWithBarrier(n) { _ =>
        AccountHolders.accountHolders.vend.getOrCreateAccountHolder(user, biaId)
      }

      Then("the holder count must grow by exactly one")
      val after   = holderCount
      val created = after - before
      withClue(s"results.success=${results.map(_.isSuccess)} before=$before after=$after created=$created (expected 1) — ") {
        created should equal(1L)
      }
    }

    Scenario("I: concurrent first-time OAuth logins must not throw a constraint violation", ConcurrencyRace) {
      Given("a provider+id pair that has no ResourceUser yet")
      val provider          = "__conc_oauth_provider_i"
      val idGivenByProvider = "__conc_oauth_id_i"
      // Clean up from any prior run.
      ResourceUser.deleteAllByProviderAndProviderId(provider, idGivenByProvider)
      val n = 2

      When(s"$n concurrent getOrCreateUserByProviderId calls race for the same (provider, id)")
      val results = runConcurrentWithBarrier(n) { _ =>
        LiftUsers.getOrCreateUserByProviderId(
          provider          = provider,
          idGivenByProvider = idGivenByProvider,
          consentId         = None,
          name              = Some("conc-oauth-test"),
          email             = Some("conc-oauth@test.invalid")
        )
      }

      Then("no call must throw and exactly one ResourceUser row must exist (UniqueIndex present but exception uncaught)")
      val failures  = results.collect { case scala.util.Failure(e) => e.getClass.getSimpleName + ": " + e.getMessage }
      val userCount = ResourceUser.countByProviderAndProviderId(provider, idGivenByProvider)
      withClue(s"failures=$failures userCount=$userCount (expected: no failures, 1 row) — ") {
        failures shouldBe empty
        userCount should equal(1L)
      }
    }

    Scenario("L: concurrent getOCreateUserCustomerLink must not throw and must create exactly one link", ConcurrencyRace) {
      Given("a user-customer pair with no existing link (mappedusercustomerlink has UniqueIndex(mUserId, mCustomerId))")
      val userId     = resourceUser1.userId
      val customerId = UUID.randomUUID.toString

      def linkCount: Long = DoobieUtil.runQuery(
        sql"SELECT COUNT(*) FROM mappedusercustomerlink WHERE muserid = $userId AND mcustomerid = $customerId"
          .query[Long].unique)
      val before = linkCount
      val n      = 8

      When(s"$n concurrent getOCreateUserCustomerLink calls race for the same (userId, customerId)")
      val results = runConcurrentWithBarrier(n) { _ =>
        DoobieUserCustomerLinkProvider.getOCreateUserCustomerLink(userId, customerId, new Date(), true)
      }

      Then("no call may throw and exactly one link row must exist")
      val after    = linkCount
      val created  = after - before
      val failures = results.collect { case scala.util.Failure(e) => e.getClass.getSimpleName + ": " + e.getMessage }
      withClue(s"before=$before after=$after created=$created failures=$failures (expected: 1 row, no throws) — ") {
        failures shouldBe empty
        created should equal(1L)
      }
    }

    Scenario("F: concurrent getOrCreateMetadata must stay graceful and leave exactly one row", ConcurrencyRace) {
      Given("a counterparty whose metadata row does not exist yet (UniqueIndex(counterpartyId) backs the table)")
      val bank      = createBank("__conc-cp-bank")
      val bankId    = bank.bankId
      val accountId = AccountId("__conc_cp_acc")
      createAccountRelevantResource(Some(resourceUser1), bankId, accountId, "EUR")
      val cp             = createCounterparty(bankId.value, accountId.value, java.util.UUID.randomUUID.toString, true, resourceUser1.userId)
      val counterpartyId = cp.counterpartyId

      def metaCount: Long = MappedCounterpartyMetadata.countByCounterpartyId(counterpartyId)

      val before = metaCount
      val n      = 8

      When(s"$n threads concurrently getOrCreateMetadata for the same counterparty")
      val results = runConcurrentWithBarrier(n) { _ =>
        Counterparties.counterparties.vend.getOrCreateMetadata(bankId, accountId, counterpartyId, "__conc_cp_name")
      }

      Then("no call may throw, and exactly one metadata row must exist (constraint conflict handled gracefully)")
      val after  = metaCount
      val thrown = results.collect { case Failure(e) => s"${e.getClass.getSimpleName}:${e.getMessage}" }
      withClue(s"before=$before after=$after thrown=$thrown (expected after=1, no throws) — ") {
        after should equal(1L)
        thrown shouldBe empty
      }
    }

    Scenario("W: concurrent getOrCreateConsumer for one (azp,sub) must resolve to the existing row, not a swallowed Failure", ConcurrencyRace) {
      Given("no consumer with this (azp, sub) yet (Consumer has UniqueIndex(azp, sub))")
      val azp = "__conc_w_azp_" + UUID.randomUUID.toString.take(8)
      val sub = "__conc_w_sub_" + UUID.randomUUID.toString.take(8)

      def consumerCount: Long = Consumer.countByAzpAndSub(azp, sub)
      val n = 2

      When(s"$n threads concurrently getOrCreateConsumer for the same (azp, sub)")
      val results = runConcurrentWithBarrier(n) { i =>
        Consumers.consumers.vend.getOrCreateConsumer(
          consumerId      = None,
          key             = None,
          secret          = None,
          aud             = Some("__conc_w_aud"),
          azp             = Some(azp),
          iss             = Some("__conc_w_iss_" + i), // distinct iss; UniqueIndex is on (azp, sub) only
          sub             = Some(sub),
          isActive        = Some(true),
          name            = Some("conc-w-consumer"),
          appType         = None,
          description     = Some("conc-w"),
          developerEmail  = Some("conc-w@test.invalid"),
          redirectURL     = None,
          createdByUserId = Some(resourceUser1.userId)
        )
      }

      Then("every caller must receive a usable Full(consumer); exactly one row must exist")
      // getOrCreateConsumer wraps its saveMe in tryo, so the second concurrent insert does not throw —
      // it is swallowed into a Failure box. The caller then holds no usable consumer (cannot authenticate),
      // which defeats the get-or-create contract just as surely as a 500 would.
      val thrown      = results.collect { case Failure(e) => e.getClass.getSimpleName + ": " + e.getMessage.take(120) }
      val emptyBoxes  = results.collect { case scala.util.Success(box) if box.isEmpty => box.toString.take(120) }
      val count       = consumerCount
      withClue(
        s"thrown=$thrown emptyBoxes=$emptyBoxes count=$count (expected: no throws, no empty boxes, 1 row) — " +
        s"the second concurrent create hits UniqueIndex(azp,sub); tryo swallows it into a Failure box " +
        s"instead of re-fetching and returning the existing consumer — "
      ) {
        thrown shouldBe empty
        emptyBoxes shouldBe empty
        count should equal(1L)
      }
    }
  }
}
