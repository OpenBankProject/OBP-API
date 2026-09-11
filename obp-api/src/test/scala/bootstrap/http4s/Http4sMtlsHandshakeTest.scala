package bootstrap.http4s

import java.io.{File, FileOutputStream}
import java.net.URL
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.{HttpsURLConnection, KeyManagerFactory, SSLContext, TrustManagerFactory}

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import code.api.util.{CertificateUtil, PeerTrust}
import code.api.util.SelfSignedCertificateUtil.generateSelfSignedCert
import code.api.util.http4s.CallerCertificate
import com.comcast.ip4s.{Host, Port}
import fs2.io.net.tls.{TLSContext, TLSParameters}
import org.http4s.{HttpApp, Response, Status}
import org.http4s.ember.server.EmberServerBuilder
import org.scalatest.BeforeAndAfterAll
import org.typelevel.ci.CIString
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * End-to-end proof of mTLS termination: a real Ember server built the same way as Http4sServer's
 * mtls.enabled branch (buildSslContext from JKS files -> TLSContext -> withTLS(needClientAuth)),
 * with the caller resolved exactly as Http4sApp.httpApp does it, exercised over a real TLS
 * handshake. This is the only place Ember's population of ServerRequestKeys.SecureSession is
 * actually verified — and therefore the only place proving the peer certificate the whole
 * peer-vs-forwarder rule depends on is really there.
 */
class Http4sMtlsHandshakeTest extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  private val storePassword = "123456"

  private val (serverKey, serverCert) = generateSelfSignedCert("localhost")
  private val (clientKey, clientCert) = generateSelfSignedCert("test-tpp")

  private def writeJks(fill: KeyStore => Unit): File = {
    val keyStore = KeyStore.getInstance("JKS")
    keyStore.load(null, null)
    fill(keyStore)
    val file = File.createTempFile("mtls-test-", ".jks")
    file.deleteOnExit()
    val out = new FileOutputStream(file)
    try keyStore.store(out, storePassword.toCharArray) finally out.close()
    file
  }

  private val serverKeystore = writeJks(_.setKeyEntry("server", serverKey, storePassword.toCharArray, Array(serverCert)))
  private val serverTruststore = writeJks(_.setCertificateEntry("client", clientCert))

  private val echoApp: HttpApp[IO] = HttpApp[IO] { req =>
    val headerValue = req.headers.get(CIString("PSD2-CERT")).map(_.head.value).getOrElse("NONE")
    IO.pure(Response[IO](Status.Ok).withEntity(headerValue))
  }

  // Two deployments served by one server, selected by path, so both are exercised over a real
  // handshake rather than only in the pure tests:
  //   /as-edge      — nothing is a forwarder, so the handshake certificate is the caller
  //   /behind-proxy — the client certificate IS the trusted proxy, so the header names the caller
  private val asEdge = PeerTrust.TrustConfig(Nil, trustForwardedHeaderWithoutTls = false)
  private lazy val behindProxy = {
    val proxyDn = PeerTrust.canonicalDn(
      clientCert.asInstanceOf[X509Certificate].getSubjectX500Principal.getName).get
    PeerTrust.TrustConfig(List(PeerTrust.TrustedProxy(proxyDn, Some(proxyDn))),
      trustForwardedHeaderWithoutTls = false)
  }

  private val resolvingApp: HttpApp[IO] = HttpApp[IO] { req =>
    val config = if (req.uri.path.renderString.startsWith("/behind-proxy")) behindProxy else asEdge
    echoApp(CallerCertificate.resolveCaller(req, config))
  }

  private var shutdown: IO[Unit] = IO.unit
  private var serverPort: Int = 0

  override def beforeAll(): Unit = {
    val sslContext = Http4sMtls.buildSslContext(Http4sMtls.MtlsConfig(
      keystorePath = serverKeystore.getAbsolutePath,
      keystorePassword = storePassword,
      truststorePath = serverTruststore.getAbsolutePath,
      truststorePassword = storePassword,
      needClientAuth = true
    ))
    val (server, release) = EmberServerBuilder
      .default[IO]
      .withHost(Host.fromString("127.0.0.1").get)
      .withPort(Port.fromInt(0).get)
      .withTLS(TLSContext.Builder.forAsync[IO].fromSSLContext(sslContext), TLSParameters(needClientAuth = true))
      .withHttpApp(resolvingApp)
      .build
      .allocated
      .unsafeRunSync()
    serverPort = server.address.getPort
    shutdown = release
  }

  override def afterAll(): Unit = shutdown.unsafeRunSync()

  private def clientSslContext(withClientCert: Boolean): SSLContext = {
    val trustStore = KeyStore.getInstance("JKS")
    trustStore.load(null, null)
    trustStore.setCertificateEntry("server", serverCert)
    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    trustManagerFactory.init(trustStore)
    val keyManagers = if (withClientCert) {
      val clientStore = KeyStore.getInstance("JKS")
      clientStore.load(null, null)
      clientStore.setKeyEntry("client", clientKey, storePassword.toCharArray, Array(clientCert))
      val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
      keyManagerFactory.init(clientStore, storePassword.toCharArray)
      keyManagerFactory.getKeyManagers
    } else null
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(keyManagers, trustManagerFactory.getTrustManagers, null)
    sslContext
  }

  private def get(withClientCert: Boolean, path: String = "/as-edge",
                  forwarded: String = "spoofed-value"): String = {
    val connection = new URL(s"https://127.0.0.1:$serverPort$path")
      .openConnection().asInstanceOf[HttpsURLConnection]
    connection.setSSLSocketFactory(clientSslContext(withClientCert).getSocketFactory)
    // the throwaway server cert has no SAN; a custom verifier also disables the JDK's
    // in-handshake endpoint identification, which would otherwise reject it
    connection.setHostnameVerifier((_, _) => true)
    connection.setRequestProperty("PSD2-CERT", forwarded)
    try scala.io.Source.fromInputStream(connection.getInputStream).mkString
    finally connection.disconnect()
  }

  "a TLS handshake with a client certificate" should "surface that certificate as the PSD2-CERT header" in {
    get(withClientCert = true) shouldEqual Http4sMtls.toPem(clientCert.asInstanceOf[X509Certificate])
  }

  "a TLS handshake without a client certificate" should "be rejected when client_auth=need" in {
    an[Exception] should be thrownBy get(withClientCert = false)
  }

  // The production topology, over a real handshake: the peer authenticates as the trusted proxy and
  // the certificate it forwards survives. The old middleware would have overwritten it with the
  // proxy's own, which is the failure this whole design exists to prevent.
  "a request from a trusted forwarder" should "keep the forwarded certificate, not the peer's" in {
    // Single-line: an HTTP header value cannot contain newlines, so this is the only shape a real
    // proxy can forward — canonical multi-line PEM is rejected outright by the client here. It is
    // also why Psd2CertIngress has to canonicalise on the way in rather than assuming a form.
    val forwardedPem = CertificateUtil.normalizePemX509Certificate(
      CertificateUtil.toPem(serverCert.asInstanceOf[X509Certificate]))
    val seenByApp = get(withClientCert = true, path = "/behind-proxy", forwarded = forwardedPem)

    seenByApp shouldEqual forwardedPem
    seenByApp should not equal Http4sMtls.toPem(clientCert.asInstanceOf[X509Certificate])
  }
}
