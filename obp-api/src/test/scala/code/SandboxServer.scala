package code

import cats.effect._
import cats.effect.unsafe.{IORuntime, IORuntimeBuilder}
import code.api.Constant.HostName
import code.api.util.APIUtil
import code.api.util.ApiRole._
import code.api.util.http4s.Http4sApp
import code.connector.MockedCbsConnector.setPropsValues
import code.consumer.Consumers
import code.entitlement.Entitlement
import code.model.TokenType.Access
import code.model.dataAccess.AuthUser
import code.model.UserX
import code.token.Tokens
import code.users.Users
import com.comcast.ip4s._
import net.liftweb.common.{Empty, Failure, Full, Logger}
import net.liftweb.util.Helpers._
import net.liftweb.util.Props
import org.http4s.ember.server.EmberServerBuilder

import java.util.{Date, UUID}
import scala.concurrent.duration._
import scala.io.StdIn

/**
 * SandboxServer — A standalone HTTP server backed by H2 in-memory database
 * for testing import flows with external applications.
 *
 * Every run starts fresh (H2 in-memory, wiped on JVM exit).
 * The server keeps running until you press ENTER.
 *
 * Usage:
 *   mvn -pl obp-api -am test-compile exec:java \
 *     -Dexec.mainClass="code.SandboxServer" \
 *     -Dexec.classpathScope="test"
 *
 * On startup it prints the DirectLogin token and base URL so the external
 * app can authenticate immediately without any setup.
 */
object SandboxServer {

  private val logger = Logger("code.SandboxServer")

  val sandboxPort     = 8080
  val sandboxUsername = "sandbox_user"
  val sandboxPassword = "sandbox_pass123"
  val sandboxEmail    = "sandbox@example.com"

  private val sandboxProps: Map[String, String] = Map(
    "db.driver"                             -> "org.h2.Driver",
    "db.url"                                -> "jdbc:h2:mem:sandbox;DB_CLOSE_ON_EXIT=FALSE;DB_CLOSE_DELAY=-1",
    "remotedata.db.driver"                  -> "org.h2.Driver",
    "remotedata.db.url"                     -> "jdbc:h2:mem:sandbox_remote;DB_CLOSE_ON_EXIT=FALSE;DB_CLOSE_DELAY=-1",
    "connector"                             -> "star",
    "starConnector_supported_types"         -> "mapped,internal",
    "migration_scripts.execute_all"         -> "true",
    "migration_scripts.execute"             -> "true",
    "transactionRequests_supported_types"   -> "ACCOUNT,COUNTERPARTY,SEPA,SANDBOX_TAN,FREE_FORM",
    "create_system_views_at_boot"           -> "true",
    "create_just_in_time_entitlements"      -> "true",
    "api_instance_id"                       -> "sandbox_1",
    "berlin_group_mandatory_headers"        -> "",
    "berlin_group_mandatory_header_consent" -> "",
    "allow_dauth"                           -> "false",
    "jwt_token_secret"                      -> "sandbox-jwt-secret-at-least-256-bits-long-ok",
    // Pekko — avoid port conflicts with running test suite
    "pekko.remote.artery.canonical.port"    -> "0",
    "pekko.remote.artery.bind.port"         -> "0",
    "display_internal_errors"               -> "true",
    // Override test.default.props hostname (which points to port 8016)
    "hostname"                              -> s"http://localhost:$sandboxPort",
    "allow_oauth2_login"                    -> "true",
    "oauth2.oidc_provider"                  -> "obp-oidc",
    "oauth2.obp_oidc.host"                  -> "http://localhost:9000",
    "oauth2.obp_oidc.issuer"                -> "http://localhost:9000/obp-oidc",
    "oauth2.obp_oidc.well_known"            -> "http://localhost:9000/obp-oidc/.well-known/openid-configuration",
    "oauth2.jwk_set.url"                    -> "https://www.googleapis.com/oauth2/v3/certs,https://login.microsoftonline.com/97d6317d-60a0-413c-b50c-d799f60bfdd2/discovery/v2.0/keys,http://localhost:7787/realms/master/protocol/openid-connect/certs,http://localhost:9000/obp-oidc/jwks",
  )

  def main(args: Array[String]): Unit = {
    // Force Lift to run in Test mode so it loads test.default.props (H2 default, no PostgreSQL).
    // Must be set before Props is first accessed.
    System.setProperty("run.mode", "test")
    // Touch Props to trigger its Scala object initialization (sets lockedProviders = Nil).
    // Only after that can we safely prepend our overrides — otherwise Boot.boot() would
    // reinitialize Props and wipe whatever we injected before first access.
    val _ = Props.mode
    setPropsValues(sandboxProps.toSeq: _*)

    logger.info("[SandboxServer] Booting Lift...")
    new bootstrap.liftweb.Boot().boot
    logger.info("[SandboxServer] Lift boot complete")

    implicit val runtime: IORuntime = IORuntimeBuilder().build()

    try {
      val serverResource = EmberServerBuilder
        .default[IO]
        .withHost(ipv4"127.0.0.1")
        .withPort(Port.fromInt(sandboxPort).getOrElse(port"8001"))
        .withHttpApp(Http4sApp.httpApp)
        .withShutdownTimeout(scala.concurrent.duration.DurationInt(1).second)
        .build

      val fiber = serverResource.use(_ => IO.never).start.unsafeRunSync()
      waitForServer()
      logger.info(s"[SandboxServer] Server started on port $sandboxPort")

      printEffectiveProps()
      val tokenKey = setupSandboxUser()
      printStartupBanner(tokenKey)
      printDatabaseContents()

      println("Press ENTER to shut down the sandbox server...")
      StdIn.readLine()

      logger.info("[SandboxServer] Shutting down...")
      (fiber.cancel >> fiber.join).unsafeRunSync()
    } finally {
      runtime.shutdown()
    }
  }

  private def orThrow[T](box: net.liftweb.common.Box[T], msg: String): T = box match {
    case Full(v)                   => v
    case Failure(failMsg, ex, _)   => throw new RuntimeException(s"$msg: $failMsg", ex.openOr(null))
    case Empty                     => throw new RuntimeException(s"$msg: got Empty")
  }

  private def waitForServer(timeoutMs: Long = 30000, intervalMs: Long = 500): Unit = {
    val url        = s"http://localhost:$sandboxPort/obp/v6.0.0/root"
    val deadline   = System.currentTimeMillis() + timeoutMs
    var ready      = false
    while (!ready && System.currentTimeMillis() < deadline) {
      try {
        val conn = new java.net.URL(url).openConnection().asInstanceOf[java.net.HttpURLConnection]
        conn.setConnectTimeout(intervalMs.toInt)
        conn.setReadTimeout(intervalMs.toInt)
        conn.connect()
        if (conn.getResponseCode == 200) ready = true
        conn.disconnect()
      } catch {
        case _: java.io.IOException => Thread.sleep(intervalMs)
      }
    }
    if (!ready) throw new RuntimeException(s"[SandboxServer] Server did not become ready within ${timeoutMs}ms")
  }

  private def setupSandboxUser(): String = {
    // 1. Create AuthUser (needed for DirectLogin password auth)
    if (AuthUser.findByUsername(sandboxUsername).isEmpty) {
      val authUser = AuthUser(
        email = sandboxEmail,
        firstName = "Sandbox",
        lastName = "User",
        username = sandboxUsername,
        validated = true,
        passwordShouldBeChanged = false).withPassword(sandboxPassword)
      authUser.save
    }

    // 2. Get or create the ResourceUser created by AuthUser.save()
    val resourceUser = orThrow(
      Users.users.vend
        .getUserByProviderAndUsername(HostName, sandboxUsername)
        .map(_.asInstanceOf[code.model.dataAccess.ResourceUser]),
      "Failed to resolve sandbox resource user after AuthUser.save()"
    )

    // 3. Create a Consumer
    val consumer = orThrow(
      Consumers.consumers.vend.createConsumer(
        key             = Some("obp-sandbox-populator-key"),
        secret          = Some("obp-sandbox-populator-secret"),
        isActive        = Some(true),
        name            = Some("OBP Sandbox Populator"),
        appType         = None,
        description     = Some("OBP Sandbox Populator"),
        developerEmail  = Some(sandboxEmail),
        redirectURL     = None,
        createdByUserId = Some(resourceUser.userId),
        None, None, None
      ),
      "Failed to create sandbox consumer"
    )

    // 4. Create a long-lived DirectLogin token
    val expiration = weeks(520) // ~10 years
    val token = orThrow(
      Tokens.tokens.vend.createToken(
        Access,
        Some(consumer.id),
        Some(resourceUser.id),
        Some(randomString(40).toLowerCase),
        Some(randomString(40).toLowerCase),
        Some(expiration),
        Some(TimeSpan(expiration + System.currentTimeMillis())),
        Some(new Date()),
        None
      ),
      "Failed to create sandbox token"
    )

    // 5. Grant global entitlements.
    //    Bank-specific roles (CanCreateAccount, CanCreateCustomer, etc.) are granted
    //    automatically at request time via create_just_in_time_entitlements=true
    //    as long as the user has CanCreateEntitlementAtAnyBank.
    val globalRoles = List(
      canCreateBank,
      canCreateEntitlementAtAnyBank,
      canGetAnyUser,
      canCreateCustomerAtAnyBank,
      canGetCustomersAtAllBanks,
      canGetCustomersMinimalAtAllBanks,
      canCreateUserCustomerLinkAtAnyBank,
      canGetUserCustomerLinkAtAnyBank,
      canCreateFxRateAtAnyBank,
      canCreateHistoricalTransaction,
      canCreateCounterpartyAtAnyBank,
    )

    globalRoles.foreach { role =>
      Entitlement.entitlement.vend.addEntitlement("", resourceUser.userId, role.toString)
    }

    logger.info(s"[SandboxServer] Sandbox user ready: userId=${resourceUser.userId}")
    token.key
  }

  // Tables we explicitly populate in setupSandboxUser, in display order.
  private val sandboxTables: List[(String, String)] = List(
    "Auth Users"   -> "authuser",
    "Resource Users" -> "resourceuser",
    "Consumers"    -> "consumer",
    "Tokens"       -> "token",
    "Entitlements" -> "mappedentitlement",
  )

  private def printDatabaseContents(): Unit = {
    import net.liftweb.db.DB
    import net.liftweb.util.DefaultConnectionIdentifier

    println("\n╔══ Sandbox seed data ══════════════════════════════════════════════════════════")
    // Borrow a connection from Lift's own pool — the same pool the ORM writes through.
    // A fresh DriverManager.getConnection would create a new empty in-memory DB due to
    // classloader isolation in H2.
    DB.use(DefaultConnectionIdentifier) { conn =>
      for ((label, tbl) <- sandboxTables) {
        val st = conn.createStatement()
        try {
          val rs    = st.executeQuery(s"""SELECT * FROM "${tbl.toUpperCase}"""")
          val meta  = rs.getMetaData
          val ncols = meta.getColumnCount
          val cols  = (1 to ncols).map(i => meta.getColumnName(i))
          // Collect all rows first so we can compute per-column widths from actual data.
          val rows = Iterator.continually(rs).takeWhile(_.next()).map { r =>
            (1 to ncols).map(i => Option(r.getString(i)).getOrElse("NULL"))
          }.toVector
          rs.close()
          val colWidths = (0 until ncols).map { c =>
            (cols(c).length +: rows.map(_(c).length)).max
          }
          def formatRow(cells: IndexedSeq[String]) =
            cells.zipWithIndex.map { case (v, i) => v.padTo(colWidths(i), ' ') }.mkString(" | ")
          val divider = colWidths.map("-" * _).mkString("-+-")
          println(s"║\n║  $label")
          println(s"║  ${formatRow(cols)}")
          println(s"║  $divider")
          if (rows.isEmpty) println(s"║    (empty)")
          else rows.foreach(row => println(s"║  ${formatRow(row)}"))
        } finally st.close()
      }
    }
    println("\n╚══════════════════════════════════════════════════════════════════════════════")
  }

  private def printEffectiveProps(): Unit = {
    val lines = sandboxProps.keys.toSeq.sorted.map { k =>
      val v = Props.get(k).getOrElse("<not set>")
      f"  $k%-50s = $v"
    }.mkString("\n")
    println(
      s"""
         |╔══════════════════════════════════════════════════════════════════════════════╗
         |║                         Effective Props                                      ║
         |╚══════════════════════════════════════════════════════════════════════════════╝
         |$lines
         |""".stripMargin)
  }

  private def printStartupBanner(tokenKey: String): Unit = {
    val baseUrl = s"http://localhost:$sandboxPort/obp/v6.0.0"
    val banner =
      s"""
         |╔══════════════════════════════════════════════════════════════════════════════╗
         |║                        OBP-API Sandbox Server                                ║
         |╠══════════════════════════════════════════════════════════════════════════════╣
         |║  Base URL   : $baseUrl
         |║  Database   : H2 in-memory (fresh on every start)
         |╠══════════════════════════════════════════════════════════════════════════════╣
         |║  Credentials (for DirectLogin token refresh):
         |║    Username : $sandboxUsername
         |║    Password : $sandboxPassword
         |╠══════════════════════════════════════════════════════════════════════════════╣
         |║  Auth header for all requests:
         |║    DirectLogin token=$tokenKey
         |╠══════════════════════════════════════════════════════════════════════════════╣
         |║  To get a fresh token:
         |║    GET $baseUrl/../my/logins/direct
         |║    Header: DirectLogin username="$sandboxUsername",password="$sandboxPassword",consumer_key=<key>
         |╚══════════════════════════════════════════════════════════════════════════════╝
         |""".stripMargin
    println(banner)
  }
}
