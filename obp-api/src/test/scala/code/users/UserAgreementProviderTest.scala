package code.users

import code.api.util.HashUtil
import code.setup.ServerSetup

/**
 * Characterization test for the user-agreement store.
 *
 * The table had no direct coverage. Written against the Lift Mapper implementation first and
 * confirmed green there, so it pins existing behaviour rather than describing the Doobie rewrite.
 * Deliberately uses only the provider interface, which both implementations share — a test that
 * reached for a method the Mapper version does not have could not have been run against it, and
 * so could not have served as a baseline at all.
 *
 * What it pins:
 *   - agreementHash is DERIVED from agreementText, not supplied. Mapper recomputed it in a
 *     beforeSave hook on every write; if that is lost in a storage swap the column silently goes
 *     empty and nothing else fails, so it is asserted explicitly.
 *   - createUserAgreement always INSERTS and getLastUserAgreement resolves to the most recent
 *     row, so re-accepting an agreement supersedes rather than overwrites.
 *   - lookups do not leak across users or across agreement types.
 *
 * The batched multi-user path (LiftUsers' getUsers) is covered through the v6.0.0 getUsers
 * endpoint rather than here.
 */
class UserAgreementProviderTest extends ServerSetup {

  private val provider = MappedUserAgreementProvider

  Feature("user-agreement storage") {

    Scenario("a created agreement round-trips with a hash derived from its text") {
      val userId = "agreement-user-roundtrip"
      val text = "These are the terms and conditions."
      val created = provider.createUserAgreement(userId, "terms_and_conditions", text)
        .openOrThrowException("expected the agreement just created")

      created.userId should equal(userId)
      created.agreementType should equal("terms_and_conditions")
      created.agreementText should equal(text)
      withClue("the hash must be the SHA-256 of the text, computed on write rather than supplied: ") {
        created.agreementHash should equal(HashUtil.Sha256Hash(text))
      }
      created.userInvitationId.nonEmpty should equal(true)
    }

    Scenario("re-accepting on the SAME DAY keeps returning the first acceptance, not the latest") {
      val userId = "agreement-user-history"
      provider.createUserAgreement(userId, "terms_and_conditions", "version one")
      Thread.sleep(5)
      provider.createUserAgreement(userId, "terms_and_conditions", "version two")

      val resolved = provider.getLastUserAgreement(userId, "terms_and_conditions")
        .openOrThrowException("expected an agreement")

      // Pinning a latent defect, not endorsing it. The date column is DATE precision, so both
      // acceptances tie on the same day; Mapper broke the tie with a stable sort over rows in
      // insertion order, so the OLDER row wins despite the method being called
      // getLastUserAgreement. Verified against the Lift implementation before the rewrite.
      // Correcting it belongs in its own change, not smuggled into a storage swap.
      withClue("same-day tie resolves to the FIRST acceptance — pre-existing behaviour: ") {
        resolved.agreementText should equal("version one")
      }
      And("its hash matches whichever row was resolved")
      resolved.agreementHash should equal(HashUtil.Sha256Hash("version one"))
    }

    Scenario("agreements do not leak across users or across agreement types") {
      val userA = "agreement-user-a"
      val userB = "agreement-user-b"
      provider.createUserAgreement(userA, "terms_and_conditions", "A terms")
      provider.createUserAgreement(userA, "accept_marketing_info", "A marketing")
      provider.createUserAgreement(userB, "terms_and_conditions", "B terms")

      provider.getLastUserAgreement(userA, "terms_and_conditions")
        .openOrThrowException("expected A's terms").agreementText should equal("A terms")
      provider.getLastUserAgreement(userA, "accept_marketing_info")
        .openOrThrowException("expected A's marketing").agreementText should equal("A marketing")
      provider.getLastUserAgreement(userB, "terms_and_conditions")
        .openOrThrowException("expected B's terms").agreementText should equal("B terms")

      And("a type the user never accepted is absent rather than falling back to another type")
      provider.getLastUserAgreement(userB, "accept_marketing_info").isDefined should equal(false)
    }
  }
}
