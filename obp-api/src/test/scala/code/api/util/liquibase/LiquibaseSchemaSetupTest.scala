package code.api.util.liquibase

import java.sql.{DriverManager, SQLException}
import liquibase.exception.{CommandExecutionException, LockException}
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
 *    Schemifier is not called anywhere in obp-api any more - the whole net.liftweb.mapper surface
 *    was removed once the last Mapper entity moved to Doobie - and Flyway is gone, so "off" does
 *    not mean "something else handles it" - it means the database has no tables. The default is the
 *    CI configuration too: the workflows write their props from scratch and mention no database
 *    prop at all. That is not a hypothetical - when `flyway.enabled` defaulted to false with
 *    Schemifier already empty, every CI shard died on the first table it touched while local runs
 *    stayed green off a hand-edited props file. Set it to false only to take schema management out
 *    of the application entirely and run the migrations yourself.
 *
 * 2. **An H2 URL must carry NON_KEYWORDS=VALUE.** A new dependency, not an inherited one: the
 *    Flyway scripts quoted every identifier, so a `"VALUE"` column never met the keyword, and the
 *    changelog's unquoted `value` does. It matters most where nobody would look for it - the CI
 *    workflows write their props from scratch and set no db.url at all, so CI runs on
 *    Constant.h2DatabaseDefaultUrlValue and a well-meaning edit to that string would fail every
 *    shard at the first CREATE TABLE.
 *
 * 3. **The changelog has to be on the classpath under a known path.** Flyway failed loudly when
 *    a location held no migrations only after this branch added a check; Liquibase treats a
 *    missing changelog as an error, but the path is a string and worth pinning.
 */
class LiquibaseSchemaSetupTest extends AnyFlatSpec with Matchers {

  "the default H2 url" should "carry NON_KEYWORDS=VALUE, which the changelog now depends on" in {
    withClue("CI sets no db.url, so this string is what CI runs on: ") {
      code.api.Constant.h2DatabaseDefaultUrlValue should include("NON_KEYWORDS=VALUE")
    }
  }

  it should "actually be required - a column called value fails to create without it" in {
    // Asserted against H2 rather than against the string, so this says the dependency is real
    // rather than that somebody once wrote the token down.
    def createValueColumn(url: String): Unit = {
      val c = DriverManager.getConnection(url, "sa", "")
      try {
        val st = c.createStatement()
        try {
          st.execute("DROP TABLE IF EXISTS keyword_probe")
          st.execute("CREATE TABLE keyword_probe(value VARCHAR(255))")
        } finally st.close()
      } finally c.close()
    }

    a[SQLException] should be thrownBy
      createValueColumn("jdbc:h2:mem:keyword_probe_without;DB_CLOSE_DELAY=-1")
    noException should be thrownBy
      createValueColumn("jdbc:h2:mem:keyword_probe_with;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1")
  }

  "the stale-lock message" should "still fire when the lock failure arrives wrapped" in {
    // A start killed mid-migration leaves its row in DATABASECHANGELOGLOCK and every later start
    // waits on a lock nobody will release - a silence that reads as a hang. bringUpToDate turns
    // that into a message naming the fix, but only if it recognises the exception, and Liquibase
    // runs changes through a command layer that is free to wrap what a step threw. Matching the
    // exception type directly would be a message that never prints; this is why it walks the
    // cause chain.
    val bare = new LockException("could not acquire change log lock")
    val wrapped = new CommandExecutionException(new RuntimeException("update failed", bare))

    LiquibaseSchemaSetup.causedByLockException(bare) should equal(true)
    LiquibaseSchemaSetup.causedByLockException(wrapped) should equal(true)
    LiquibaseSchemaSetup.causedByLockException(new RuntimeException("something else")) should
      equal(false)
  }

  it should "not spin on a self-referential cause chain" in {
    // Runs on the boot path, where a hang costs more than a missing message.
    val looping = new RuntimeException("a") {
      override def getCause: Throwable = this
    }
    LiquibaseSchemaSetup.causedByLockException(looping) should equal(false)
  }

  "the changelog path" should "point at a resource that is actually on the classpath" in {
    // A typo here is a boot-time failure on every deployment, so it is checked against the
    // classpath rather than against another copy of the same string.
    val path = LiquibaseSchemaSetup.changeLogPath
    withClue(s"$path must resolve on the classpath: ") {
      Option(getClass.getClassLoader.getResource(path)) should not be empty
    }
  }

  "the liquibase.enabled default" should "be on, since nothing else creates the schema" in {
    // Used to hold this against ToSchemify.models being empty. That field is gone now - removed
    // along with the rest of obp-api's net.liftweb.mapper surface - rather than merely empty, so
    // the specific regression this used to catch (something repopulating ToSchemify.models) can't
    // happen any more: the symbol doesn't exist to repopulate.
    //
    // That is narrower than "nothing else can ever create a table again", and this test does not
    // claim the wider guarantee: lift-persistence (bundling net.liftweb.mapper - Schemifier,
    // MetaMapper, the rest) is still a live dependency, so a new Mapper entity plus a fresh
    // Schemifier.schemify(...) call would compile and run today, and nothing in this suite boots
    // Boot.scala to notice one running alongside Liquibase. That would mean writing Lift Mapper
    // code from scratch in a codebase with zero remaining entities to copy from - unlikely, but
    // not something this assertion, or any other, currently guards against.
    LiquibaseSchemaSetup.enabledByDefault should equal(true)
  }
}
