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

package code.api.berlin.group.v1_3

import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3.StartConsentAuthorisationJson
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.entitlement.Entitlement
import code.model.dataAccess.BankAccountRouting
import code.transactionChallenge.MappedExpectedChallengeAnswer
import com.openbankproject.commons.model.enums.AccountRoutingScheme
import net.liftweb.mapper.By
import org.scalatest.Tag

/**
 * A Berlin Group consent names its accounts when it is lodged, so once its PSU starts the
 * authorisation the consent is bound to an account and has a challenge nobody has answered yet.
 * Deleting that account with the cascading endpoint must take the challenge along; a challenge of a
 * consent bound to another account stays.
 */
class DeleteAccountCascadeConsentChallengeTest extends BerlinGroupConsentFixtures {

  object DeleteAccountCascadeConsentChallenge extends Tag("DeleteAccountCascadeConsentChallenge")

  private def ibanOf(bankId: String, accountId: String): String =
    BankAccountRouting.find(
      By(BankAccountRouting.BankId, bankId),
      By(BankAccountRouting.AccountId, accountId),
      By(BankAccountRouting.AccountRoutingScheme, AccountRoutingScheme.IBAN.toString)
    ).openOrThrowException(s"no IBAN for $bankId/$accountId").accountRouting.address

  private def challengesOf(consentId: String): Int =
    MappedExpectedChallengeAnswer.findAll(By(MappedExpectedChallengeAnswer.ConsentId, consentId)).size

  // The consent is lodged for one account and resourceUser1 starts its authorisation, which binds
  // the PSU and mints the challenge; the challenge is left unanswered.
  private def consentAwaitingItsChallenge(bankId: String, accountId: String): String = {
    val consentId = createUnclaimedBerlinGroupConsent(Some(ibanOf(bankId, accountId))).consentId
    val started = makePostRequest(
      (V1_3_BG / "consents" / consentId / "authorisations").POST <@ (user1),
      """{"scaAuthenticationData":""}""")
    started.code should equal(201)
    started.body.extract[StartConsentAuthorisationJson].authorisationId should not be empty
    consentId
  }

  feature("Deleting an account by cascade deletes the challenges of the consents bound to it") {
    scenario("A Berlin Group consent whose challenge is still unanswered", DeleteAccountCascadeConsentChallenge) {
      setPropsValues("suggested_default_sca_method" -> "DUMMY")
      Given("Two accounts resourceUser1 holds, each named by a consent awaiting its challenge")
      val held = ibanAddressableAccountsHeldBy(resourceUser1).toList.sorted
      held.size should be >= 2
      val (bankId, accountId) = held.head
      val (otherBankId, otherAccountId) = held(1)
      val consent = consentAwaitingItsChallenge(bankId, accountId)
      val otherConsent = consentAwaitingItsChallenge(otherBankId, otherAccountId)
      challengesOf(consent) should equal(1)
      challengesOf(otherConsent) should equal(1)

      When("The first account is deleted with the cascading endpoint")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.canDeleteAccountCascade.toString)
      val request = (baseRequest / "obp" / "v4.0.0" / "management" / "cascading" / "banks" / bankId /
        "accounts" / accountId).DELETE <@ (user1)
      makeDeleteRequest(request).code should equal(200)

      Then("The challenge of the consent bound to the deleted account is gone")
      challengesOf(consent) should equal(0)
      And("The challenge of the consent bound to the other account is kept")
      challengesOf(otherConsent) should equal(1)
    }
  }
}
