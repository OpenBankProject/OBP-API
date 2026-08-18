package code.api.util.liquibase

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * The contract Liquibase has to meet before it can take the schema over from Flyway.
 *
 * Flyway needed one SQL script set per vendor, which is why `db/migration/h2` and
 * `db/migration/postgres` are 118 files each and the other three vendors have none. Liquibase
 * generates the dialect itself from one changelog, so there is no per-vendor folder to pick -
 * but the two things that actually bit this branch under Flyway carry over unchanged, and are
 * pinned here.
 *
 * 1. **It must be off until asked, and then on by default when it is the only thing left.**
 *    `liquibase.enabled` starts false while Flyway still owns the schema. It flips to true in
 *    the same commit that removes Flyway - never before, never after. The reason is on the
 *    record: the CI workflows write their props from scratch and mention no database prop at
 *    all, so the code's default IS the CI configuration. When `flyway.enabled` defaulted to
 *    false with Schemifier already empty, every CI shard died on the first table it touched
 *    while local runs stayed green off a hand-edited props file.
 *
 * 2. **The changelog has to be on the classpath under a known path.** Flyway failed loudly when
 *    a location held no migrations only after this branch added a check; Liquibase treats a
 *    missing changelog as an error, but the path is a string and worth pinning.
 */
class LiquibaseSchemaSetupTest extends AnyFlatSpec with Matchers {

  "the changelog path" should "point at a resource that is actually on the classpath" in {
    // A typo here is a boot-time failure on every deployment, so it is checked against the
    // classpath rather than against another copy of the same string.
    val path = LiquibaseSchemaSetup.changeLogPath
    withClue(s"$path must resolve on the classpath: ") {
      Option(getClass.getClassLoader.getResource(path)) should not be empty
    }
  }

  "the liquibase.enabled default" should "stay false while Flyway still owns the schema" in {
    // Deliberately the opposite of flyway.enabled's current default. Two migration tools both
    // creating tables on boot is the one state that must never exist, so during the changeover
    // exactly one of them is on. This assertion is what flips - together with the default - in
    // the commit that removes Flyway.
    LiquibaseSchemaSetup.enabledByDefault should equal(false)
  }

  it should "never be on at the same time as flyway.enabled" in {
    // The invariant that outlives the changeover: whichever tool is the authority, it is the
    // only one. Written as a mutual exclusion so it keeps meaning after the defaults flip.
    val both = LiquibaseSchemaSetup.enabledByDefault &&
      code.api.util.flyway.FlywaySchemaSetup.enabledByDefault
    withClue("both migration tools default to on - they would both create tables on boot: ") {
      both should equal(false)
    }
  }
}
