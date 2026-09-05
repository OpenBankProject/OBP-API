package code.model

import code.api.util.DoobieUtil
import code.setup.ServerSetup
import doobie.implicits._
import net.liftweb.common.Full
import net.liftweb.util.Helpers

/**
 * A NULL call-limit column has to read back as the configured limit, not as "unlimited".
 *
 * Lift's readers differ by field type, and the migration's first version applied one rule to all of
 * them. MappedBoolean's getter is `data openOr false`, so a NULL flag is false whatever the field
 * declared. MappedLong's reader is `if (isNull) defaultValue else v` (MappedLong.scala:321), so a
 * NULL number really did come back as the declared default - and for these six columns that default
 * is `APIUtil.getPropsAsLongValue("rate_limiting_per_*", -1)`, the instance's configured limit.
 *
 * Reading them as a hardcoded -1 turns "the configured limit" into "no limit" for any row whose
 * column is NULL, which is what a row predating the columns has: Schemifier added them with
 * ALTER TABLE ADD COLUMN and no backfill. It is not only cosmetic - MigrationOfConsumerRateLimiting
 * seeds the ratelimiting table from these values, so a -1 read here is written into the table that
 * RateLimitingUtil enforces from.
 */
class ConsumerNullCallLimitTest extends ServerSetup {

  feature("a consumer row whose call-limit columns are NULL") {

    scenario("reads back the configured limit, not unlimited") {
      // A value no default could produce, so the assertion cannot pass by accident.
      setPropsValues("rate_limiting_per_minute" -> "97")

      val key = "nulllimit_" + Helpers.randomString(12).toLowerCase
      // Raw SQL on purpose: the store's own insert always binds a value, so this is the only way
      // to produce the row an older database carries.
      DoobieUtil.runUpdate(
        sql"""INSERT INTO consumer
              (consumerid, key_c, secret, azp, sub, isactive, name, description, developeremail,
               persecondcalllimit, perminutecalllimit, perhourcalllimit, perdaycalllimit,
               perweekcalllimit, permonthcalllimit)
              VALUES (${"cid_" + key}, $key, 'secret', ${"azp_" + key}, ${"sub_" + key}, true,
               ${"name " + key}, 'a consumer whose limit columns predate the columns',
               'someone@example.com', NULL, NULL, NULL, NULL, NULL, NULL)"""
          .update.run)

      Consumer.findByKey(key) match {
        case Full(consumer) =>
          consumer.perMinuteCallLimit should equal(97L)
          // The rest fall back to their own props, which are unset here, so -1 is correct for them.
          consumer.perSecondCallLimit should equal(Consumer.perSecondCallLimitDefault)
          consumer.perHourCallLimit should equal(Consumer.perHourCallLimitDefault)
        case other => fail(s"the consumer that was just inserted must be readable, got $other")
      }

      DoobieUtil.runUpdate(sql"DELETE FROM consumer WHERE key_c = $key".update.run)
    }

    scenario("a consumer created through the provider carries the configured limit too") {
      setPropsValues("rate_limiting_per_minute" -> "97")

      val row = Consumer.defaults
      row.perMinuteCallLimit should equal(97L)
    }
  }
}
