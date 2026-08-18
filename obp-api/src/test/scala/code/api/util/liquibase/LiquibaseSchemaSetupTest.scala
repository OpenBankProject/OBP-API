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
 * 1. **It must be on by default, because nothing else creates a table.**
 *    Schemifier creates nothing (ToSchemify.models is Nil) and Flyway is gone, so "off" does not
 *    mean "something else handles it" - it means the database has no tables. The default is the
 *    CI configuration too: the workflows write their props from scratch and mention no database
 *    prop at all. That is not a hypothetical - when `flyway.enabled` defaulted to false with
 *    Schemifier already empty, every CI shard died on the first table it touched while local runs
 *    stayed green off a hand-edited props file. Set it to false only to take schema management out
 *    of the application entirely and run the migrations yourself.
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

  "the liquibase.enabled default" should "be on, since nothing else creates the schema" in {
    // Held against ToSchemify.models: while that list is empty, nothing but Liquibase creates a
    // table, so a default of false means a deployment silently gets no schema at all.
    withClue("nothing else creates a table while ToSchemify.models is empty: ") {
      bootstrap.liftweb.ToSchemify.models shouldBe empty
    }
    LiquibaseSchemaSetup.enabledByDefault should equal(true)
  }
}
