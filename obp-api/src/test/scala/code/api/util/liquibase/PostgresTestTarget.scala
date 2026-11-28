package code.api.util.liquibase

import org.scalatest.Assertions

/**
 * Whether a Postgres check may cancel itself, or has to run.
 *
 * `assume(reachable)` was the whole gate: a Postgres check cancelled itself wherever no Postgres
 * was listening, and CI had none - so the Postgres half of the schema was never exercised there.
 * That is the state the check exists to prevent. A cancelled test reports as a pass, so nothing
 * distinguished "CI verified the Postgres DDL" from "CI silently skipped it" except reading the log
 * for a cancellation nobody was looking for.
 *
 * `OBP_TEST_POSTGRES_REQUIRED=true` turns the cancellation into a failure. CI sets it alongside the
 * Postgres service container, so a broken service, a wrong URL, or a dropped `services:` block
 * fails the build instead of quietly restoring the old behaviour. Developers leave it unset and
 * keep the skip.
 */
object PostgresTestTarget {

  /** True when a missing Postgres must fail rather than cancel. */
  def required: Boolean =
    sys.env.get("OBP_TEST_POSTGRES_REQUIRED").exists(_.trim.equalsIgnoreCase("true"))

  /**
   * Cancel the test when Postgres is absent and optional; fail it when absent and required; return
   * normally when it is there.
   *
   * `required` is a parameter rather than a direct read of the environment so both branches are
   * reachable from a test - the environment cannot be changed from inside the JVM, and a branch no
   * test can enter is exactly the kind of thing this is here to stop.
   */
  def requireReachable(reachable: Boolean, target: String, required: Boolean = required): Unit =
    if (!reachable) {
      if (required) {
        Assertions.fail(
          s"OBP_TEST_POSTGRES_REQUIRED=true but no Postgres answered at $target. This check is the " +
          "only thing that exercises the Postgres DDL, so it must not be skipped where it is " +
          "required - start the service, or unset OBP_TEST_POSTGRES_REQUIRED to go back to skipping.")
      } else {
        Assertions.cancel(
          s"no Postgres at $target - skipping (set OBP_TEST_POSTGRES_URL to run this)")
      }
    }
}
