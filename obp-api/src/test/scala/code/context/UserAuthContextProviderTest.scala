package code.context

import code.setup.ServerSetup
import com.openbankproject.commons.model.BasicUserAuthContext

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Characterization of the user-auth-context provider, written before the implementation moves to
 * Doobie. Sibling of ConsentAuthContextProviderTest - same table shape, same provider shape, and
 * (before this change) the same copy-pasted bug.
 *
 * UserAuthContextTest (v3.1.0) covers createUserAuthContext directly - the always-insert path -
 * end to end, so that path is not re-tested here beyond confirming it through the provider seam.
 * Nothing in the suite exercises createOrUpdateUserAuthContexts, which is the path AuthUser's
 * login flow and ConsentUtil actually use.
 *
 * Driven through UserAuthContextProvider.vend, the same seam AuthUser and ConsentUtil use, so
 * this keeps testing whichever implementation buildOne returns.
 */
class UserAuthContextProviderTest extends ServerSetup {

  private def provider = UserAuthContextProvider.userAuthContextProvider.vend
  private def await[A](f: scala.concurrent.Future[A]) = Await.result(f, 10.seconds)

  private val userA = "user-auth-context-test-A"
  private val userB = "user-auth-context-test-B"

  override def beforeEach() = {
    super.beforeEach()
    await(provider.deleteUserAuthContexts(userA))
    await(provider.deleteUserAuthContexts(userB))
  }

  Feature("user auth context storage") {

    Scenario("create then read back, with the consumer id carried over") {
      val created = await(provider.createUserAuthContext(userA, "psuId", "u1", "consumer-1"))
      created.isDefined should equal(true)

      val all = provider.getUserAuthContextsBox(userA).openOrThrowException("just created")
      all.map(_.key) should equal(List("psuId"))
      all.head.value should equal("u1")
      all.head.consumerId should equal("consumer-1")
    }

    Scenario("createUserAuthContext rejects a blank consumer id") {
      val result = await(provider.createUserAuthContext(userA, "psuId", "u1", ""))
      result.isDefined should equal(false)
    }

    Scenario("createUserAuthContext always inserts, even for a repeated key") {
      val first = await(provider.createUserAuthContext(userA, "psuId", "u1", "consumer-1"))
      first.isDefined should equal(true)
      // The unique index is (userId, key, createdAt): two writes for the same key in the same
      // millisecond collide. Space them out so this checks "always inserts", not timing.
      Thread.sleep(5)
      val second = await(provider.createUserAuthContext(userA, "psuId", "u2", "consumer-1"))
      second.isDefined should equal(true)

      val all = provider.getUserAuthContextsBox(userA).openOrThrowException("created twice")
      all.count(_.key == "psuId") should equal(2)
      all.map(_.value).toSet should equal(Set("u1", "u2"))
    }

    Scenario("createOrUpdateUserAuthContexts inserts a fresh key") {
      val result = provider.createOrUpdateUserAuthContexts(
        userA, List(BasicUserAuthContext("psuId", "u1")))
      result.openOrThrowException("created").map(_.value) should equal(List("u1"))
    }

    Scenario("createOrUpdateUserAuthContexts overwrites an existing key rather than adding a row") {
      provider.createOrUpdateUserAuthContexts(userA, List(BasicUserAuthContext("psuId", "u1")))
      provider.createOrUpdateUserAuthContexts(userA, List(BasicUserAuthContext("psuId", "u2")))

      val all = provider.getUserAuthContextsBox(userA).openOrThrowException("updated")
      all.count(_.key == "psuId") should equal(1)
      all.head.value should equal("u2")
    }

    Scenario("deleteUserAuthContexts is scoped to one user id") {
      await(provider.createUserAuthContext(userA, "psuId", "u1", "consumer-1"))
      await(provider.createUserAuthContext(userB, "psuId", "u1", "consumer-1"))

      await(provider.deleteUserAuthContexts(userA))

      provider.getUserAuthContextsBox(userA).openOrThrowException("checked").isEmpty should equal(true)
      provider.getUserAuthContextsBox(userB).openOrThrowException("checked").isEmpty should equal(false)
    }

    Scenario("deleteUserAuthContextById removes just that row") {
      val created = await(provider.createUserAuthContext(userA, "psuId", "u1", "consumer-1"))
        .openOrThrowException("just created")
      await(provider.createUserAuthContext(userA, "other", "u2", "consumer-1"))

      await(provider.deleteUserAuthContextById(created.userAuthContextId))

      val remaining = provider.getUserAuthContextsBox(userA).openOrThrowException("checked")
      remaining.map(_.key) should equal(List("other"))
    }

    Scenario("deleteUserAuthContextById on a missing id is Empty, not a successful no-op") {
      val result = await(provider.deleteUserAuthContextById("does-not-exist"))
      result.isDefined should equal(false)
    }
  }
}
