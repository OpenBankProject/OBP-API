package code.api.util.liquibase

import java.io.{File, FileOutputStream}
import java.net.{URL, URLClassLoader}
import java.nio.file.{Files, Path, Paths}
import java.sql.DriverManager
import java.util.jar.{JarEntry, JarOutputStream}
import javax.sql.DataSource
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * The changelog appearing twice on the classpath must not stop the application from starting.
 *
 * This is not a contrived arrangement - it is the startup OBP-STARTUP-GUIDE.md documents and
 * recommends:
 *
 *     java -cp "obp-api/src/main/resources:obp-api/target/obp-api.jar" bootstrap.http4s.Http4sServer
 *
 * The source directory goes first on purpose, so a locally edited default.props takes effect
 * without rebuilding the jar - Lift's Props does not read `-D` flags reliably, so the classpath is
 * the mechanism. The jar naturally also contains everything under src/main/resources, so every
 * resource is present twice, and that was harmless while Flyway owned the schema.
 *
 * Liquibase's changelog parser refuses a duplicate outright:
 *
 *     Found 2 files with the path 'db/changelog/db.changelog-master.yaml'
 *
 * which turns the documented start into an immediate boot failure. The refusal is there to protect
 * against two genuinely different files answering to one path; here they are the same file reached
 * two ways, and the classpath order already says which one is meant. So the mode is relaxed to warn
 * and take the first - the first being the source directory, which is exactly the copy the
 * documented start exists to prefer.
 *
 * The cost of relaxing it is real and worth naming: if the jar is stale relative to src, the
 * warning is the only sign that two versions existed. That is the same trap as the stale
 * target/classes copy described in CLAUDE.md, and the answer is the same - rebuild, or delete the
 * copy you do not mean.
 */
class DuplicateChangelogOnClasspathTest extends AnyFlatSpec with Matchers {

  /** Resolved rather than hardcoded: the suite's working directory is the module, not the repo. */
  private val changelogRoot: Path = {
    val candidates = List(Paths.get("src/main/resources"), Paths.get("obp-api/src/main/resources"))
    candidates.find(p => Files.isDirectory(p.resolve("db/changelog"))).getOrElse(
      throw new IllegalStateException(
        s"cannot find db/changelog under any of $candidates from ${Paths.get(".").toAbsolutePath}"))
  }

  /** A jar holding the same db/changelog resources the source directory holds. */
  private def changelogJar(): File = {
    val jar = Files.createTempFile("obp-changelog-", ".jar").toFile
    jar.deleteOnExit()
    val out = new JarOutputStream(new FileOutputStream(jar))
    try {
      val dir = changelogRoot.resolve("db/changelog")
      Files.list(dir).forEach { (p: Path) =>
        out.putNextEntry(new JarEntry("db/changelog/" + p.getFileName.toString))
        out.write(Files.readAllBytes(p))
        out.closeEntry()
      }
    } finally out.close()
    jar
  }

  /** src/main/resources first, then the jar - the order the documented start uses. */
  private def duplicatingClassLoader(): ClassLoader = {
    val urls: Array[URL] = Array(
      changelogRoot.toAbsolutePath.toUri.toURL,
      changelogJar().toURI.toURL
    )
    new URLClassLoader(urls, getClass.getClassLoader)
  }

  private def dataSourceFor(name: String): DataSource = {
    val ds = new org.h2.jdbcx.JdbcDataSource()
    ds.setURL(s"jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;NON_KEYWORDS=VALUE")
    ds.setUser("sa")
    ds.setPassword("")
    ds
  }

  private def tableCount(name: String): Long = {
    val c = DriverManager.getConnection(
      s"jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE", "sa", "")
    try {
      val st = c.createStatement()
      try {
        // BASE TABLE only: the changelog also creates the three OIDC views, and a view is not a
        // table this count is about.
        val rs = st.executeQuery(
          "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'PUBLIC' " +
            "AND table_type = 'BASE TABLE' " +
            "AND table_name NOT IN ('DATABASECHANGELOG', 'DATABASECHANGELOGLOCK')")
        rs.next()
        rs.getLong(1)
      } finally st.close()
    } finally c.close()
  }

  "a changelog reachable twice on the classpath" should "still build the schema" in {
    // Both copies must genuinely be present, or this asserts nothing.
    val loader = duplicatingClassLoader()
    withClue("the fixture must actually produce a duplicate: ") {
      var found = 0
      val e = loader.getResources(LiquibaseSchemaSetup.changeLogPath)
      while (e.hasMoreElements) { e.nextElement(); found += 1 }
      found should be >= 2
    }

    // Through bringUpToDate, the entry point Boot calls, rather than configure + update - the
    // parse happens inside it and so does the tolerance for the duplicate.
    val db = "duplicate_changelog"
    LiquibaseSchemaSetup.bringUpToDate(dataSourceFor(db), loader)

    withClue("the schema must have been built from one of the two copies: ") {
      tableCount(db) should equal(147L)
    }
  }
}
