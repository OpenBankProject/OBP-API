/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

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

import java.lang.reflect.{Proxy => JProxy}
import java.security.cert.X509Certificate

import bootstrap.liftweb.Boot
import code.api.RequestHeader
import net.liftweb.http.LiftRules
import net.liftweb.http.provider.HTTPContext
import org.apache.commons.codec.binary.Base64
import org.eclipse.jetty.server._
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.eclipse.jetty.webapp.WebAppContext
import sun.security.provider.X509Factory

object RunMTLSWebApp extends App {
  val servletContextPath = "/"
  //set run mode value to "development", So the value is true of Props.devMode
  System.setProperty("run.mode", "development")

  {
    val tempHTTPContext = JProxy.newProxyInstance(this.getClass.getClassLoader, Array(classOf[HTTPContext]),
      (_, method, _) => {
        if (method.getName == "path") {
          servletContextPath
        } else {
          throw new IllegalAccessException(s"Should not call this object method except 'path' method, current call method name is: ${method.getName}")
          ??? // should not call other method.
        }
      }).asInstanceOf[HTTPContext]
    LiftRules.setContext(tempHTTPContext)

    new Boot()
  }

  // add client certificate to request header
  def customizer(connector: Connector, channelConfig: HttpConfiguration, request: Request): Unit = {
    val clientCertificate = request.getAttribute("javax.servlet.request.X509Certificate").asInstanceOf[Array[X509Certificate]]
    val httpFields = request.getHttpFields
    if (clientCertificate != null && httpFields != null) {
      val encoder = new Base64(64)
      val content = new String(
        encoder.encode(
          clientCertificate.head.getEncoded()
        )
      ).trim
      val certificate =
        s"""${X509Factory.BEGIN_CERT}
           |$content
           |${X509Factory.END_CERT}
           |""".stripMargin
      httpFields.add(RequestHeader.`PSD2-CERT`, certificate)
    }
  }
  val server = new Server()
  // set MTLS
  val connectors: Array[Connector] = {
    val https = new HttpConfiguration
    https.addCustomizer(new SecureRequestCustomizer)
    // RESET HEADER
    https.addCustomizer(customizer)

    val sslContextFactory = new SslContextFactory()
    sslContextFactory.setKeyStorePath(this.getClass.getResource("/cert/server.jks").toExternalForm)
    sslContextFactory.setKeyStorePassword("123456")

    sslContextFactory.setTrustStorePath(this.getClass.getResource("/cert/server.trust.jks").toExternalForm)
    sslContextFactory.setTrustStorePassword("123456")
    sslContextFactory.setNeedClientAuth(true)

    sslContextFactory.setProtocol("TLSv1.2")

    val connector = new ServerConnector(server, new SslConnectionFactory(sslContextFactory, "http/1.1"), new HttpConnectionFactory(https))
    connector.setPort(8080)

    Array(connector)
  }
  server.setConnectors(connectors)

  val context = new WebAppContext()
  context.setServer(server)
  context.setContextPath(servletContextPath)
  // current project absolute path
  val basePath = this.getClass.getResource("/").toString .replaceFirst("target[/\\\\].*$", "")
  context.setWar(s"${basePath}src/main/webapp")

  // rename JSESSIONID, avoid conflict with other project when start two project at local
  context.getSessionHandler.getSessionCookieConfig.setName("JSESSIONID_OBP_API")

  server.setHandler(context)

  try {
    println(">>> STARTING EMBEDDED JETTY SERVER, Start https at port 443, PRESS ANY KEY TO STOP")
    server.start()
    while (System.in.available() == 0) {
      Thread.sleep(5000)
    }
    server.stop()
    server.join()
  } catch {
    case exc : Exception => {
      exc.printStackTrace()
      sys.exit(100)
    }
  }
}
