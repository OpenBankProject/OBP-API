package code.setup

import code.api.util.APIUtil

/**
 * Refuses to let the test suite run against anything but a throwaway database.
 *
 * `ServerSetup.resetDatabaseForTestClass()` issues 140 `DELETE FROM` statements at the start of
 * every test class, against whatever `db.url` happens to point at. Until this existed the only
 * thing standing between that and a real database was the contents of a props file: one typo in
 * `test.default.props`, or an `OBP_DB_URL` inherited from the shell, and the suite would empty
 * `obp-mapped`. Nothing in the code objected.
 *
 * So the rule is a whitelist, not a blacklist - a blacklist has to guess the names of databases
 * that matter, and it would have to be updated every time someone creates one:
 *
 *   - `jdbc:h2:mem:...`      an in-memory database; it ceases to exist when the JVM does
 *   - `.../obp_suite_<...>`  the per-shard databases a Postgres run creates and drops

 *
 * Everything else - `obp-mapped`, `obp-mapped-test`, `api-tester`, `bnpp-demo`,
 * `obp_ttk_sandbox`, and any file-backed H2 - fails the check.
 *
 * It throws, and it is called from `TestServer`'s object initializer, which makes the object
 * permanently unusable in that JVM: every suite that so much as mentions TestServer then dies
 * with NoClassDefFoundError before reaching a database. Halting the JVM instead was tried first
 * and is worse - the forked test JVM disappears, the plugin reports "no tests" rather than a
 * failure, and the build comes out BUILD SUCCESS. A guard that prevents the damage but hands back
 * a green build is only half a guard: the next person points at the wrong database, sees success,
 * and believes the suite ran.
 */
object DisposableDatabaseGuard {

  /** Postgres/other JDBC URLs end in the database name, possibly with `?params`. */
  private val JdbcDatabaseName = """^jdbc:[a-z0-9]+://[^/]+/([^/?;]+).*$""".r

  private val AllowedDatabaseName =
    """^(obp_suite_[a-z0-9_]+|obp_liquibase_migration_test|obp_test_only)$""".r

  /** True when this URL names a database it is safe to empty and drop. */
  def isDisposable(url: String): Boolean = url match {
    case u if u.startsWith("jdbc:h2:mem:") => true
    case JdbcDatabaseName(name)            => AllowedDatabaseName.matches(name)
    case _                                 => false
  }

  /** Reason to show when a URL is rejected, so the message names the actual database. */
  def describe(url: String): String = url match {
    case JdbcDatabaseName(name) => s"database '$name'"
    case _                      => s"url '$url'"
  }

  def assertDisposable(): Unit = {
    val url = APIUtil.getPropsValue("db.url").openOr("")
    if (!isDisposable(url)) {
      // stderr and stdout: whichever the runner captures, this has to be the thing that is read.
      val message =
        s"""
           |========================================================================
           |REFUSING TO RUN: the tests would use a database that is not disposable.
           |
           |  ${describe(url)}
           |  db.url = $url
           |
           |Every test class starts by deleting the contents of 140 tables. That is
           |safe only against a database built to be thrown away, so the suite runs
           |against these and nothing else:
           |
           |  jdbc:h2:mem:...                  in-memory, gone when the JVM exits
           |  .../obp_suite_<name>             a per-shard database for a Postgres run
           |  .../obp_liquibase_migration_test PostgresMigrationTest's own database
           |  .../obp_test_only                what scripts/create_test_db.sh creates
           |
           |Set db.url in test.default.props, or OBP_DB_URL in the environment, to
           |one of those. Nothing has been written to the database named above.
           |========================================================================
           |""".stripMargin
      System.err.println(message)
      System.out.println(message)
      System.err.flush()
      System.out.flush()
      throw new IllegalStateException(
        s"refusing to run the test suite against ${describe(url)} - it is not disposable")
    }
  }
}
