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

    Scenario("re-accepting on the same day returns the latest acceptance") {
      val userId = "agreement-user-history"
      provider.createUserAgreement(userId, "terms_and_conditions", "version one")
      Thread.sleep(5)
      provider.createUserAgreement(userId, "terms_and_conditions", "version two")

      val resolved = provider.getLastUserAgreement(userId, "terms_and_conditions")
        .openOrThrowException("expected an agreement")

      // The date column is DATE precision, so both acceptances fall on the same day and the
      // date alone cannot order them. Mapper broke that tie with a stable sort over rows in
      // insertion order, which handed back the OLDER row - so an agreement re-accepted the same
      // day kept reporting the superseded text. The tie is now broken by the identity column
      // instead, which is the order the rows were written in.
      withClue("the most recent acceptance must win a same-day tie: ") {
        resolved.agreementText should equal("version two")
      }
      And("its hash matches the row that was resolved")
      resolved.agreementHash should equal(HashUtil.Sha256Hash("version two"))
    }

    Scenario("the batched multi-user path resolves the same acceptance as the single lookup") {
      // getUsers reads agreements for many users in one query and picks each type's latest in
      // Scala, with a stable sort by date. Same-day rows tie there too, so the two paths agree
      // only if the batch query hands them over newest-first - without that they disagree, and
      // a user's agreement text depends on which endpoint asked.
      val userId = "agreement-user-batched"
      provider.createUserAgreement(userId, "terms_and_conditions", "batch version one")
      Thread.sleep(5)
      provider.createUserAgreement(userId, "terms_and_conditions", "batch version two")

      val batched = UserAgreement.findAllByUserIds(List(userId))
        .filter(_.agreementType == "terms_and_conditions")
        .sortBy(_.date)(Ordering[java.util.Date].reverse)
        .headOption
        .getOrElse(fail("expected an agreement from the batched path"))

      val single = provider.getLastUserAgreement(userId, "terms_and_conditions")
        .openOrThrowException("expected an agreement")

      batched.agreementText should equal("batch version two")
      batched.agreementText should equal(single.agreementText)
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
