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

import code.loginattempts.{LoginAttempt, MappedBadLoginAttempt}
import code.transactionChallenge.{MappedChallengeProvider, MappedExpectedChallengeAnswer}
import net.liftweb.mapper.By
import org.mindrot.jbcrypt.BCrypt

import java.util.{Date, UUID}

/**
 * Simulates two authentication-layer concurrent counter races. Both assert the correct
 * (theoretically sound) outcome, so they are EXPECTED TO FAIL while the races are unfixed —
 * the "expected vs actual" clue is the evidence. Tagged ConcurrencyRace.
 *
 *  H. Bad-login attempt counter lost-update — LoginAttempt.incrementBadLoginAttempts reads the
 *     counter, increments in memory, then writes back with no lock or version column. N concurrent
 *     bad-login attempts each observe the same starting value and overwrite each other, so fewer
 *     increments land than attempts counted. Under a 5-attempt lockout threshold an attacker can
 *     send far more than 5 guesses before the lockout triggers.
 *
 *  K. Challenge attempt-counter lost-update — MappedChallengeProvider.validateChallenge reads
 *     attemptCounter, writes counter+1, then compares counter < allowedAttempts. Read and write are
 *     non-atomic, so N concurrent wrong answers each observe counter=0, each save counter=1, and
 *     each pass the gate. The counter never reaches the allowed-attempts threshold, enabling
 *     unlimited concurrent brute-force guesses without burning the attempt budget.
 */
class ConcurrentSecurityRaceTest extends ConcurrentRaceSetup {

  feature("Authentication counter atomicity under concurrency") {

    scenario("H: N concurrent bad-login increments must each land — no lockout bypass", ConcurrencyRace) {
      Given("a bad-login record pre-seeded at zero attempts for a dedicated test credential")
      val provider = "__conc_sec_provider_h"
      val username = "__conc_sec_user_h"
      // Clean up from any prior run (shared JVM, forkMode=once).
      MappedBadLoginAttempt.findAll(
        By(MappedBadLoginAttempt.Provider, provider),
        By(MappedBadLoginAttempt.mUsername, username)
      ).foreach(_.delete_!)
      MappedBadLoginAttempt.create
        .mUsername(username)
        .Provider(provider)
        .mBadAttemptsSinceLastSuccessOrReset(0)
        .mLastFailureDate(new Date())
        .saveMe()
      val n = 8

      When(s"$n bad-login increments are fired concurrently for the same credential")
      runConcurrentWithBarrier(n) { _ =>
        LoginAttempt.incrementBadLoginAttempts(provider, username)
      }

      Then("the counter must equal N — every increment must land, no lost-updates")
      val finalCounter = MappedBadLoginAttempt.find(
        By(MappedBadLoginAttempt.Provider, provider),
        By(MappedBadLoginAttempt.mUsername, username)
      ).map(_.badAttemptsSinceLastSuccessOrReset).getOrElse(0)
      withClue(
        s"finalCounter=$finalCounter (expected=$n): each of $n concurrent bad-login attempts must " +
        s"be counted — if fewer land, an attacker can bypass the lockout threshold by sending " +
        s"concurrent requests — "
      ) {
        finalCounter should equal(n)
      }
    }

    scenario("K: N concurrent wrong challenge answers must each consume one attempt — no brute-force bypass", ConcurrencyRace) {
      Given("a challenge seeded directly via MappedChallengeProvider with a known expected answer")
      // Raise the attempt limit so the limit-guard never fires early and interferes with the counter test.
      setPropsValues("transactionRequests_challenge_max_allowed_attempts" -> "100")
      val challengeId = UUID.randomUUID.toString
      val salt        = BCrypt.gensalt()
      MappedChallengeProvider.saveChallenge(
        challengeId            = challengeId,
        transactionRequestId   = UUID.randomUUID.toString,
        salt                   = salt,
        expectedAnswer         = BCrypt.hashpw("123", salt).substring(0, 44),
        expectedUserId         = resourceUser1.userId,
        scaMethod              = None,
        scaStatus              = None,
        consentId              = None,
        basketId               = None,
        authenticationMethodId = None,
        challengeType          = "OBP_TRANSACTION_REQUEST_CHALLENGE"
      )
      val n = 8

      When(s"$n concurrent wrong-answer validate calls hit the same challenge")
      runConcurrentWithBarrier(n) { _ =>
        MappedChallengeProvider.validateChallenge(
          challengeId    = challengeId,
          challengeAnswer = "definitelyWrongAnswer",
          userId         = Some(resourceUser1.userId)
        )
      }

      Then("the attempt counter must equal N — each wrong answer must consume exactly one attempt")
      val finalCounter = MappedExpectedChallengeAnswer
        .find(By(MappedExpectedChallengeAnswer.ChallengeId, challengeId))
        .map(_.AttemptCounter.get)
        .getOrElse(-1)
      withClue(
        s"finalCounter=$finalCounter (expected=$n): each of $n concurrent wrong-answer attempts must " +
        s"be counted — if fewer land, an attacker can submit unlimited concurrent guesses without " +
        s"exhausting the allowed-attempt budget — "
      ) {
        finalCounter should equal(n)
      }
    }
  }
}
