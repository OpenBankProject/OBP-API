package code.api.util.http4s

import java.lang.management.ManagementFactory

import cats.effect.IO
import code.api.cache.Redis
import code.api.util.DoobieUtil
import code.util.Helper.MdcLoggable
import doobie._
import doobie.implicits._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers.{Accept, `Content-Type`}
import org.http4s.Charset

object StatusPage extends MdcLoggable {

  private lazy val gitProps: java.util.Properties = {
    val props = new java.util.Properties()
    val is = getClass.getResourceAsStream("/git.properties")
    if (is != null) {
      try props.load(is) finally is.close()
    }
    props
  }

  private def gitCommit: String =
    Option(gitProps.getProperty("git.commit.id")).getOrElse("unknown")

  private def apiInstanceId: String = code.api.Constant.ApiInstanceId

  private def uptimeSeconds: Long =
    ManagementFactory.getRuntimeMXBean.getUptime / 1000L

  private def prefersJson(req: Request[IO]): Boolean =
    req.headers.get[Accept].exists { accept =>
      accept.values.toList.exists { mediaRange =>
        mediaRange.mediaRange == MediaType.application.json
      }
    }

  private case class Checks(database: String, redis: String) {
    def allOk: Boolean = database == "ok" && redis == "ok"
  }

  private def runChecks: IO[Checks] = IO {
    val db = try {
      DoobieUtil.runQuery(sql"SELECT 1".query[Int].unique)
      "ok"
    } catch {
      case e: Throwable =>
        logger.warn(s"StatusPage says: database check failed: ${e.getMessage}")
        "fail"
    }
    val redis = try {
      if (Redis.isRedisReady) "ok" else "fail"
    } catch {
      case e: Throwable =>
        logger.warn(s"StatusPage says: redis check failed: ${e.getMessage}")
        "fail"
    }
    Checks(db, redis)
  }

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> Root / "status" =>
      runChecks.flatMap { checks =>
        val response = if (prefersJson(req)) jsonResponse(checks) else htmlResponse(checks)
        if (checks.allOk) response
        else response.map(_.withStatus(Status.ServiceUnavailable))
      }

    // Liveness probe: the process is running and can respond to HTTP.
    // Does not touch DB or Redis — those belong in /status (readiness).
    case GET -> Root / "health" =>
      Ok("""{"status":"ok"}""").map(_.withContentType(`Content-Type`(MediaType.application.json, Charset.`UTF-8`)))
  }

  private def jsonResponse(checks: Checks): IO[Response[IO]] = {
    val status = if (checks.allOk) "ok" else "degraded"
    val json =
      s"""{
         |  "status": "$status",
         |  "api_instance_id": "$apiInstanceId",
         |  "git_commit": "$gitCommit",
         |  "uptime_seconds": $uptimeSeconds,
         |  "checks": {
         |    "database": "${checks.database}",
         |    "redis": "${checks.redis}"
         |  }
         |}""".stripMargin
    Ok(json).map(_.withContentType(`Content-Type`(MediaType.application.json, Charset.`UTF-8`)))
  }

  private def htmlResponse(checks: Checks): IO[Response[IO]] = {
    val overall = if (checks.allOk) "ok" else "degraded"
    val overallColor = if (checks.allOk) "#2e7d32" else "#c62828"
    def badge(v: String): String = {
      val color = if (v == "ok") "#2e7d32" else "#c62828"
      s"""<span style="color: $color; font-weight: bold;">$v</span>"""
    }

    val html =
      s"""<!DOCTYPE html>
         |<html>
         |<head>
         |  <title>OBP API - Status</title>
         |  <style>
         |    body { font-family: sans-serif; max-width: 800px; margin: 40px auto; padding: 0 20px; }
         |    h1 { color: #333; }
         |    h2 { color: #555; margin-top: 30px; }
         |    table { border-collapse: collapse; }
         |    th, td { padding: 6px 12px; text-align: left; border-bottom: 1px solid #eee; }
         |  </style>
         |</head>
         |<body>
         |  <h1>OBP API Status: <span style="color: $overallColor;">$overall</span></h1>
         |
         |  <h2>Instance</h2>
         |  <table>
         |    <tr><th>api_instance_id</th><td>$apiInstanceId</td></tr>
         |    <tr><th>git_commit</th><td>$gitCommit</td></tr>
         |    <tr><th>uptime_seconds</th><td>$uptimeSeconds</td></tr>
         |  </table>
         |
         |  <h2>Checks</h2>
         |  <table>
         |    <tr><th>database</th><td>${badge(checks.database)}</td></tr>
         |    <tr><th>redis</th><td>${badge(checks.redis)}</td></tr>
         |  </table>
         |</body>
         |</html>""".stripMargin

    Ok(html).map(_.withContentType(`Content-Type`(MediaType.text.html, Charset.`UTF-8`)))
  }
}
