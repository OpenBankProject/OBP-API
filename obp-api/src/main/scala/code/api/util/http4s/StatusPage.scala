package code.api.util.http4s

import cats.effect.IO
import code.api.util.APIUtil
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers.{Accept, `Content-Type`}

object StatusPage {

  private def appDiscoveryPairs = APIUtil.getAppDiscoveryPairs

  private def humanName(key: String): String =
    key.stripPrefix("public_")
      .stripSuffix("_url")
      .replace("_", " ")
      .split(" ")
      .map(_.capitalize)
      .mkString(" ")

  private def prefersJson(req: Request[IO]): Boolean =
    req.headers.get[Accept].exists { accept =>
      accept.values.toList.exists { mediaRange =>
        mediaRange.mediaRange.satisfiedBy(MediaType.application.json)
      }
    }

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> Root =>
      if (prefersJson(req)) jsonResponse else htmlResponse
  }

  private def jsonResponse: IO[Response[IO]] = {
    val pairs = appDiscoveryPairs
    val appDirectory = pairs.map { case (name, url) =>
      s"""    {"name": "${humanName(name)}", "key": "$name", "url": "$url"}"""
    }.mkString(",\n")

    val json =
      s"""{
         |  "app_directory": [
         |$appDirectory
         |  ],
         |  "discovery_endpoints": {
         |    "api_info": "/obp/v6.0.0/root",
         |    "resource_docs": "/obp/v6.0.0/resource-docs/v6.0.0/obp",
         |    "well_known": "/obp/v5.1.0/well-known",
         |    "banks": "/obp/v6.0.0/banks"
         |  },
         |  "links": {
         |    "github": "https://github.com/OpenBankProject/OBP-API",
         |    "tesobe": "https://www.tesobe.com",
         |    "open_bank_project": "https://www.openbankproject.com"
         |  },
         |  "copyright": "Copyright TESOBE GmbH 2010-2026"
         |}""".stripMargin

    Ok(json).map(_.withContentType(`Content-Type`(MediaType.application.json)))
  }

  private def htmlResponse: IO[Response[IO]] = {
    val appDiscoveryLinks = appDiscoveryPairs.map { case (name, url) =>
      s"""        <li><a href="$url">${humanName(name)}</a> <small>($name)</small></li>"""
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
         |  <h1>Welcome to the OBP API technical discovery page</h1>
         |  <p>OBP API is a headless open source Open Banking API stack. Navigate to the Apps below to interact with the APIs or see the Discovery Endpoints.</p>
         |
         |  <h2>App Directory</h2>
         |  <ul>
         |$appDiscoveryLinks
         |  </ul>
         |
         |  <h2>Discovery Endpoints</h2>
         |<p>See also API Explorer, Portal or MCP Server above.</p>
         |  <ul>
         |    <li><a href="/obp/v6.0.0/root">API Info</a></li>
         |    <li><a href="/obp/v6.0.0/resource-docs/v6.0.0/obp">API Documentation</a></li>
         |    <li><a href="/obp/v5.1.0/well-known">Well Known URIs</a></li>
         |    <li><a href="/obp/v6.0.0/banks">Banks</a></li>
         |  </ul>
         |
         |  <h2>Links</h2>
         |  <ul>
         |    <li><a href="https://github.com/OpenBankProject/OBP-API">OBP-API on GitHub</a></li>
         |    <li><a href="https://www.tesobe.com">TESOBE</a></li>
         |    <li><a href="https://www.openbankproject.com">Open Bank Project</a></li>
         |  </ul>
         |
         |  <footer style="margin-top: 40px; padding-top: 20px; border-top: 1px solid #ddd; color: #999; font-size: 0.9em;">
         |    Copyright TESOBE GmbH 2010-2026
         |  </footer>
         |</body>
         |</html>""".stripMargin

    Ok(html).map(_.withContentType(`Content-Type`(MediaType.text.html)))
  }
}
