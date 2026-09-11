package code.setup

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * The whitelist has to reject every database that is not disposable, by name.
 *
 * This is the check that stands between `resetDatabaseForTestClass`'s 140 `DELETE FROM`
 * statements and a real database, so the databases that exist on a developer machine are named
 * here explicitly rather than left to a pattern that looks about right.
 */
class DisposableDatabaseGuardTest extends AnyFlatSpec with Matchers {

  import DisposableDatabaseGuard.isDisposable

  "the guard" should "allow an in-memory H2 database" in {
    isDisposable("jdbc:h2:mem:OBPTest;DB_CLOSE_ON_EXIT=FALSE;DB_CLOSE_DELAY=-1") should equal(true)
    isDisposable("jdbc:h2:mem:liquibase_upgrade_existing;DB_CLOSE_DELAY=-1") should equal(true)
  }

  it should "allow the per-shard and migration-test Postgres databases" in {
    isDisposable("jdbc:postgresql://localhost:5432/obp_suite_shard_1") should equal(true)
    isDisposable("jdbc:postgresql://localhost:5432/obp_suite_shard_4_20260818") should equal(true)
    isDisposable("jdbc:postgresql://localhost:5432/obp_liquibase_migration_test") should equal(true)
    isDisposable("jdbc:postgresql://localhost:5432/obp_suite_shard_1?sslmode=disable") should
      equal(true)
  }

  it should "allow the database name the repository's own script recommends" in {
    // scripts/create_test_db.sh defaults to this name and calls it wipe-safe and throwaway, but
    // the guard rejected it - so following the repository's own instructions produced a suite
    // that refused to start, with a message saying the database was not disposable.
    isDisposable("jdbc:postgresql://localhost:5432/obp_test_only") should equal(true)
  }

  it should "refuse every database that exists on this machine for real" in {
    // These are the databases actually present on the developer machine this was written on.
    // If the whitelist ever widens enough to admit one of them, this fails.
    val real = List("obp-mapped", "obp-mapped-test", "api-tester", "bnpp-demo",
      "obp_ttk_sandbox", "postgres", "template1")
    real.foreach { db =>
      withClue(s"'$db' must never be treated as disposable: ") {
        isDisposable(s"jdbc:postgresql://localhost:5432/$db") should equal(false)
      }
    }
  }

  it should "refuse a name that merely starts like an allowed one" in {
    // obp_suite_ is a prefix; obp-mapped is not, and neither is a name that only looks close.
    isDisposable("jdbc:postgresql://localhost:5432/obp_suite") should equal(false)
    isDisposable("jdbc:postgresql://localhost:5432/obp_liquibase_migration_test_real") should
      equal(false)
    isDisposable("jdbc:postgresql://localhost:5432/obp_test_only_real") should equal(false)
    isDisposable("jdbc:postgresql://localhost:5432/notobp_suite_shard_1") should equal(false)
  }

  it should "refuse a file-backed H2, which survives the JVM" in {
    isDisposable("jdbc:h2:./lift_proto.db;AUTO_SERVER=TRUE") should equal(false)
    isDisposable("jdbc:h2:/var/lib/obp/obp") should equal(false)
  }

  it should "refuse an empty or unreadable url rather than assume it is safe" in {
    isDisposable("") should equal(false)
    isDisposable("jdbc:postgresql://localhost:5432/") should equal(false)
  }

  "an absent db.url" should "resolve to the in-memory default the application itself uses" in {
    // The guard reads db.url and refuses anything that is not disposable, and an unset prop is
    // not a url - but it is not a danger either: the application falls back to an in-memory H2,
    // so an unset db.url is the safest configuration there is.
    //
    // Reading it as an empty string and refusing that is what CI actually did. The workflows write
    // test.default.props from scratch and set no db.url at all, so every shard died in
    // TestServer's initializer with "refusing to run the test suite against url ''", while every
    // local run stayed green off a props file that happens to set one. Same shape as the
    // flyway.enabled default: the code's fallback IS the CI configuration.
    //
    // Resolved through DBUtil.dbUrl rather than reproduced here, so the guard cannot decide the
    // suite is pointed somewhere the application is not.
    DisposableDatabaseGuard.resolvedDbUrl should startWith("jdbc:")
    withClue(s"the resolved url must be disposable: ${DisposableDatabaseGuard.resolvedDbUrl} ") {
      isDisposable(DisposableDatabaseGuard.resolvedDbUrl) should equal(true)
    }
  }

  it should "be disposable when nothing is configured at all" in {
    // The CI case, asserted directly against the constant the application falls back to.
    isDisposable(code.api.Constant.h2DatabaseDefaultUrlValue) should equal(true)
  }

  it should "name the database in the rejection message, so the message is actionable" in {
    DisposableDatabaseGuard.describe("jdbc:postgresql://localhost:5432/obp-mapped") should
      include("obp-mapped")
  }
}
