import java.sql.*;
import org.flywaydb.core.Flyway;
import liquibase.command.CommandScope;

/**
 * Builds a throwaway Postgres database from the Flyway scripts, then generates the baseline
 * changelog from it.
 *
 * Generating from a live database rather than writing the changelog by hand is the point: the 118
 * Postgres scripts are a translation of the 118 H2 ones, which are Schemifier's own exported DDL
 * verbatim, and that lineage is why the schema can be trusted. A hand-written changelog would
 * discard it and bet the production schema on a type mapping being right.
 *
 * From Postgres and not from H2, because H2 stores identifiers uppercase: a changelog generated
 * there carries uppercase names, and if Liquibase then quotes them on Postgres the result is a
 * case-sensitive "MAPPEDATM" that every unquoted lowercase query the application issues will never
 * find. The table would exist and be permanently invisible.
 *
 * Run it, then normalise the output - see scripts/normalise_generated_changelog.py, which explains
 * why the raw output is not committable.
 */
public class GenerateChangelog {
  public static void main(String[] args) throws Exception {
    String db = args[0], out = args[1];
    String admin = "jdbc:postgresql://localhost:5432/postgres";
    String url = "jdbc:postgresql://localhost:5432/" + db;
    String user = System.getProperty("user.name");

    try (Connection c = DriverManager.getConnection(admin, user, "")) {
      c.createStatement().execute(
        "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '" + db +
        "' AND pid <> pg_backend_pid()");
      c.createStatement().execute("DROP DATABASE IF EXISTS " + db);
      c.createStatement().execute("CREATE DATABASE " + db);
    }

    Flyway flyway = Flyway.configure(GenerateChangelog.class.getClassLoader())
      .dataSource(url, user, "")
      .locations("classpath:db/migration/postgres")
      .baselineOnMigrate(true)
      .load();
    System.out.println("flyway applied: " + flyway.migrate().migrationsExecuted);

    new CommandScope("generateChangeLog")
      .addArgumentValue("url", url)
      .addArgumentValue("username", user)
      .addArgumentValue("password", "")
      .addArgumentValue("changelogFile", out)
      .addArgumentValue("excludeObjects", "flyway_schema_history")
      .execute();
    System.out.println("changelog written: " + out);
  }
}
