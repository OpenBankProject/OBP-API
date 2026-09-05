package code.model

import code.api.util.DoobieUtil
import code.setup.ServerSetup
import doobie.implicits._
import net.liftweb.common.Full
import net.liftweb.util.Helpers

/**
 * A consumer whose createdbyuserid is NULL must not take the whole listing down.
 *
 * `consumer.createdbyuserid` is nullable, and the store binds it as Option and hands back null -
 * which is exactly what Lift did, since MappedString's JDBC setter is `if (isNull) null`. Nothing
 * wrong there. What broke is one layer up.
 *
 * Under Lift, `c.createdByUserId` returned the MappedString FIELD, and the JSON factories call
 * `.toString()` on it. MappedField.toString is:
 *
 *     override def toString: String = get match { case null => ""; case v => v.toString }
 *
 * so a NULL column produced "" and the lookup simply found no user. Once the entity became a case
 * class with `createdByUserId: String`, the very same `.toString()` is being called on a raw null
 * and throws - the type signature says String, so nothing warned:
 *
 *     Cannot invoke "String.toString()" because the return value of
 *     "code.model.Consumer.createdByUserId()" is null
 *
 * It is not an edge case on real data: 21 of 196 rows in the reference database hold NULL there, so
 * any endpoint that lists consumers hits it. Found by the contract suite against a clone of that
 * database, not by this suite - the suites create their consumers through the provider, which
 * always supplies a value.
 */
class ConsumerNullCreatedByUserIdTest extends ServerSetup {

  feature("a consumer row whose createdbyuserid is NULL") {

    scenario("is readable, and reads back as null the way Mapper did") {
      val key = "nullcreator_" + Helpers.randomString(12).toLowerCase
      // Raw SQL on purpose: the provider always supplies a creator, so this is the only way to
      // produce the row a long-lived database carries.
      DoobieUtil.runUpdate(
        sql"""INSERT INTO consumer
              (consumerid, key_c, secret, azp, sub, isactive, name, description, developeremail,
               createdbyuserid)
              VALUES (${"cid_" + key}, $key, 'secret', ${"azp_" + key}, ${"sub_" + key}, true,
               ${"name " + key}, 'a consumer with no recorded creator',
               'someone@example.com', NULL)"""
          .update.run)

      try {
        Consumer.findByKey(key) match {
          case Full(consumer) =>
            withClue("Mapper's MappedString read a NULL column as null: ") {
              consumer.createdByUserId should equal(null)
            }
          case other => fail(s"the consumer that was just inserted must be readable, got $other")
        }
      } finally {
        DoobieUtil.runUpdate(sql"DELETE FROM consumer WHERE key_c = $key".update.run)
      }
    }

    scenario("can still be rendered as JSON, rather than taking the endpoint down with an NPE") {
      val key = "nullcreator_" + Helpers.randomString(12).toLowerCase
      DoobieUtil.runUpdate(
        sql"""INSERT INTO consumer
              (consumerid, key_c, secret, azp, sub, isactive, name, description, developeremail,
               createdbyuserid)
              VALUES (${"cid_" + key}, $key, 'secret', ${"azp_" + key}, ${"sub_" + key}, true,
               ${"name " + key}, 'a consumer with no recorded creator',
               'someone@example.com', NULL)"""
          .update.run)

      try {
        val consumer = Consumer.findByKey(key).openOrThrowException("just inserted")
        val emptyLimits = code.api.v6_0_0.ActiveRateLimitsJsonV600(
          considered_rate_limit_ids = Nil,
          active_at_date = new java.util.Date(),
          active_per_second_rate_limit = -1L,
          active_per_minute_rate_limit = -1L,
          active_per_hour_rate_limit = -1L,
          active_per_day_rate_limit = -1L,
          active_per_week_rate_limit = -1L,
          active_per_month_rate_limit = -1L)
        val noCalls = code.api.v6_0_0.RateLimitV600(None, None, "NOT_SET")
        val emptyCounters = code.api.v6_0_0.RedisCallCountersJsonV600(
          noCalls, noCalls, noCalls, noCalls, noCalls, noCalls)

        // This is the call the v6 consumers listing makes for every row.
        val json = code.api.v6_0_0.JSONFactory600.createConsumerJsonV600(
          consumer, None, emptyLimits, emptyCounters)

        withClue("a consumer with no recorded creator has no user to report: ") {
          json.created_by_user should equal(null)
        }
      } finally {
        DoobieUtil.runUpdate(sql"DELETE FROM consumer WHERE key_c = $key".update.run)
      }
    }
  }
}
