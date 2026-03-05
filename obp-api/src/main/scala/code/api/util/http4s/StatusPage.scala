package code.api.util.http4s

import cats.effect.IO
import code.api.util.APIUtil
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`

object StatusPage {

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root =>
      val appDiscoveryLinks = APIUtil.getAppDiscoveryPairs.map { case (name, url) =>
        val displayName = name
          .stripPrefix("public_")
          .stripSuffix("_url")
          .replace("_", " ")
          .split(" ")
          .map(_.capitalize)
          .mkString(" ")
        s"""        <li><a href="$url">$displayName</a> <small>($name)</small></li>"""
      }.mkString("\n")

      val html =
        s"""<!DOCTYPE html>
           |<html>
           |<head>
           |  <title>OBP API - Status Page</title>
           |  <style>
           |    body { font-family: sans-serif; max-width: 800px; margin: 40px auto; padding: 0 20px; }
           |    h1 { color: #333; }
           |    h2 { color: #555; margin-top: 30px; }
           |    ul { line-height: 2; }
           |    a { color: #0066cc; }
           |    small { color: #999; }
           |  </style>
           |</head>
           |<body>
           |  <h1>Welcome to the OBP API</h1>
           |
           |  <h2>App Directory</h2>
           |  <ul>
           |$appDiscoveryLinks
           |  </ul>
           |
           |  <h2>API Endpoints</h2>
           |  <ul>
           |    <li><a href="/obp/v6.0.0/root">API Info</a></li>
           |    <li><a href="/obp/v6.0.0/resource-docs/v6.0.0/obp">API Documentation</a></li>
           |    <li><a href="/obp/v6.0.0/banks">Banks</a></li>
           |  </ul>
           |</body>
           |</html>""".stripMargin

      Ok(html).map(_.withContentType(`Content-Type`(MediaType.text.html)))
  }
}
