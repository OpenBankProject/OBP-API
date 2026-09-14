/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package bootstrap.http4s

import cats.effect._
import code.api.util.APIUtil
import code.api.util.http4s.{Http4sApp, Http4sConfigUtil}
import code.util.Helper.MdcLoggable
import com.comcast.ip4s._
import org.http4s.ember.server._

object Http4sServer extends IOApp with MdcLoggable {

  //Start OBP relevant objects and settings; this step MUST be executed first
  // new bootstrap.http4s.Http4sBoot().boot
  new bootstrap.liftweb.Boot().boot

  // Get bind address: use bind_address prop if set, otherwise parse from hostname
  // Note: hostname prop must remain unchanged as it may be used for local_provider_name fallback
  val host =  Http4sConfigUtil.parseHostname(APIUtil.getPropsValue("bind_address",code.api.Constant.HostName))
  val port = APIUtil.getPropsAsIntValue("dev.port",8080)

  // Use shared httpApp configuration (same as tests)
  val httpApp = Http4sApp.httpApp

  override def run(args: List[String]): IO[ExitCode] = {
    // Force the peer-trust configuration at boot. It is a lazy val first needed when a request
    // carries certificate material, so without this an unparseable mtls.trusted_proxy.N DN (logged
    // at ERROR, proxy silently untrusted) would surface mid-traffic instead of in the boot log.
    code.api.util.PeerTrust.config

    val builder = EmberServerBuilder
      .default[IO]
      .withHost(Host.fromString(host).get)
      .withPort(Port.fromInt(port).get)
    val configuredBuilder = if (Http4sMtls.enabled) {
      logger.info(s"mTLS termination is ENABLED: serving HTTPS on port $port, " +
        s"client_auth=${if (Http4sMtls.config.needClientAuth) "need" else "want"}, " +
        s"keystore=${Http4sMtls.config.keystorePath}, truststore=${Http4sMtls.config.truststorePath}")
      if (code.api.Constant.HostName.startsWith("http://"))
        logger.warn("mtls.enabled=true but the hostname prop still starts with http:// — set it to https:// so generated links match the TLS listener.")
      // No certificate middleware here: Http4sApp.httpApp resolves the caller for every request,
      // TLS or not (code.api.util.http4s.CallerCertificate). Ember exposes the handshake
      // certificate on the request, which is all this branch needs to contribute.
      builder
        .withTLS(Http4sMtls.tlsContext, Http4sMtls.tlsParameters)
        .withHttpApp(httpApp)
    } else {
      builder.withHttpApp(httpApp)
    }
    configuredBuilder.build
      .use(_ => IO.never)
      .as(ExitCode.Success)
  }
}
