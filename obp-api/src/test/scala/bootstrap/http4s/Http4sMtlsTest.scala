package bootstrap.http4s

import java.security.KeyStore
import java.security.cert.X509Certificate

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import code.api.CertificateConstants
import code.api.util.CertificateUtil
import com.nimbusds.jose.util.X509CertUtils
import org.http4s.{HttpApp, Method, Request, Response, Status}
import org.http4s.server.{SecureSession, ServerRequestKeys}
import org.scalatest.{FlatSpec, Matchers}
import org.typelevel.ci.CIString

class Http4sMtlsTest extends FlatSpec with Matchers {

  private val psd2CertHeader = CIString("PSD2-CERT")

  private def loadTestKeystore: KeyStore = {
    val keyStore = KeyStore.getInstance("JKS")
    val in = getClass.getResourceAsStream("/cert/server.jks")
    try keyStore.load(in, "123456".toCharArray) finally in.close()
    keyStore
  }

  private lazy val testCertificate: X509Certificate = {
    val keyStore = loadTestKeystore
    val aliases = keyStore.aliases()
    var certificate: X509Certificate = null
    while (aliases.hasMoreElements && certificate == null) {
      keyStore.getCertificate(aliases.nextElement()) match {
        case x509: X509Certificate => certificate = x509
        case _ =>
      }
    }
    certificate should not be null
    certificate
  }

  // Echoes the PSD2-CERT header value the app actually sees, or NONE
  private val echoApp: HttpApp[IO] = HttpApp[IO] { req =>
    val headerValue = req.headers.get(psd2CertHeader).map(_.head.value).getOrElse("NONE")
    IO.pure(Response[IO](Status.Ok).withEntity(headerValue))
  }

  private def run(req: Request[IO]): String =
    Http4sMtls.injectClientCertificate(echoApp).apply(req).flatMap(_.as[String]).unsafeRunSync()

  private def secureSessionWith(cert: X509Certificate): Some[SecureSession] =
    Some(SecureSession("session-id", "TLS_AES_128_GCM_SHA256", 128, List(cert)))

  "toPem" should "produce a parseable canonical PEM" in {
    val pem = Http4sMtls.toPem(testCertificate)
    pem should startWith(CertificateConstants.BEGIN_CERT + "\n")
    pem should endWith("\n" + CertificateConstants.END_CERT)
    X509CertUtils.parse(pem) should not be null
    // the normalize fallback used by the consumer lookup must round-trip it
    X509CertUtils.parse(CertificateUtil.normalizePemX509Certificate(pem)) should not be null
  }

  "injectClientCertificate" should "inject the handshake certificate as the PSD2-CERT header" in {
    val req = Request[IO](Method.GET)
      .withAttribute(ServerRequestKeys.SecureSession, secureSessionWith(testCertificate))
    run(req) shouldEqual Http4sMtls.toPem(testCertificate)
  }

  it should "strip a client-supplied PSD2-CERT header when there is no client certificate" in {
    val req = Request[IO](Method.GET).withHeaders("PSD2-CERT" -> "spoofed-value")
    run(req) shouldEqual "NONE"
  }

  it should "replace a client-supplied PSD2-CERT header with the handshake certificate" in {
    val req = Request[IO](Method.GET)
      .withHeaders("PSD2-CERT" -> "spoofed-value")
      .withAttribute(ServerRequestKeys.SecureSession, secureSessionWith(testCertificate))
    run(req) shouldEqual Http4sMtls.toPem(testCertificate)
  }

  it should "pass the request through untouched when the TLS session has no peer certificate" in {
    val req = Request[IO](Method.GET)
      .withAttribute(ServerRequestKeys.SecureSession, Some(SecureSession("session-id", "TLS_AES_128_GCM_SHA256", 128, List.empty[X509Certificate])))
    run(req) shouldEqual "NONE"
  }

  "buildSslContext" should "build an SSLContext from the checked-in dev keystores" in {
    val certDir = new java.io.File(getClass.getResource("/cert/server.jks").toURI).getParent
    val config = Http4sMtls.MtlsConfig(
      keystorePath = s"$certDir/server.jks",
      keystorePassword = "123456",
      truststorePath = s"$certDir/server.trust.jks",
      truststorePassword = "123456",
      needClientAuth = true
    )
    val sslContext = Http4sMtls.buildSslContext(config)
    sslContext should not be null
    sslContext.getProtocol shouldEqual "TLS"
  }
}
