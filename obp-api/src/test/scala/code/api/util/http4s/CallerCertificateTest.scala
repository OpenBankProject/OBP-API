package code.api.util.http4s

import java.security.cert.X509Certificate

import cats.effect.IO
import code.api.util.{CertificateUtil, PeerTrust}
import code.api.util.SelfSignedCertificateUtil.generateSelfSignedCert
import org.http4s.{Method, Request}
import org.http4s.server.{SecureSession, ServerRequestKeys}
import org.typelevel.ci.CIString
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * The http4s half of the caller-resolution rule: that the two inputs are collected from the right
 * places and the outgoing header is rewritten to match the decision. The decision itself is covered
 * exhaustively in `code.api.util.PeerTrustTest`.
 *
 * The first four scenarios were `Http4sMtlsTest`'s tests of `injectClientCertificate`, which this
 * middleware replaced. They are the "OBP is the TLS edge" row of the table, and keeping them is how
 * we know that deployment did not change behaviour when the rule was generalised.
 */
class CallerCertificateTest extends AnyFlatSpec with Matchers {

  private val psd2CertHeader = CIString("PSD2-CERT")

  private def certFor(cn: String): X509Certificate =
    generateSelfSignedCert(cn)._2.asInstanceOf[X509Certificate]

  private val clientCert = certFor("test-tpp")
  private val proxyCert = certFor("nginx-prod-1")

  /** OBP is the edge: nothing is a forwarder. Development, and the pre-existing dev behaviour. */
  private val asEdge = PeerTrust.TrustConfig(Nil, trustForwardedHeaderWithoutTls = false)

  /** Production behind nginx: the proxy is a forwarder, and a bare header is not trusted. */
  private val behindProxy = PeerTrust.TrustConfig(
    List(PeerTrust.TrustedProxy(
      PeerTrust.canonicalDn("CN=nginx-prod-1").get,
      Some(PeerTrust.canonicalDn("CN=nginx-prod-1").get))),
    trustForwardedHeaderWithoutTls = false
  )

  /** Today's production: a proxy over a plain hop, header taken on trust. */
  private val plainHop = PeerTrust.TrustConfig(Nil, trustForwardedHeaderWithoutTls = true)

  private def secureSessionWith(certs: List[X509Certificate]): Some[SecureSession] =
    Some(SecureSession("session-id", "TLS_AES_128_GCM_SHA256", 128, certs))

  private def requestWith(peer: Option[X509Certificate], header: Option[String]): Request[IO] = {
    val withHeader = header.foldLeft(Request[IO](Method.GET))((r, v) => r.withHeaders("PSD2-CERT" -> v))
    peer.foldLeft(withHeader)((r, c) => r.withAttribute(ServerRequestKeys.SecureSession, secureSessionWith(List(c))))
  }

  private def headerAfter(peer: Option[X509Certificate], header: Option[String],
                          config: PeerTrust.TrustConfig): Option[String] =
    CallerCertificate.resolveCaller(requestWith(peer, header), config)
      .headers.get(psd2CertHeader).map(_.head.value)

  "OBP as the TLS edge" should "inject the handshake certificate as the PSD2-CERT header" in {
    headerAfter(Some(clientCert), None, asEdge) shouldEqual Some(CertificateUtil.toPem(clientCert))
  }

  it should "strip a client-supplied PSD2-CERT header when there is no client certificate" in {
    headerAfter(None, Some("spoofed-value"), asEdge) shouldBe None
  }

  it should "replace a client-supplied PSD2-CERT header with the handshake certificate" in {
    headerAfter(Some(clientCert), Some("spoofed-value"), asEdge) shouldEqual
      Some(CertificateUtil.toPem(clientCert))
  }

  it should "leave the request alone when the TLS session has no peer certificate" in {
    val req = Request[IO](Method.GET).withAttribute(ServerRequestKeys.SecureSession, secureSessionWith(Nil))
    CallerCertificate.resolveCaller(req, asEdge).headers.get(psd2CertHeader) shouldBe None
  }

  // The production topology this design exists for: nginx authenticates itself in the handshake and
  // names the App in the header. Getting this backwards — the failure the old middleware would have
  // produced if it had simply been enabled in production — makes every request look like the proxy.
  "OBP behind a trusted proxy" should "keep the App's certificate, not the proxy's" in {
    val appPem = CertificateUtil.toPem(clientCert)
    headerAfter(Some(proxyCert), Some(appPem), behindProxy) shouldEqual Some(appPem)
    headerAfter(Some(proxyCert), Some(appPem), behindProxy) should not equal
      Some(CertificateUtil.toPem(proxyCert))
  }

  it should "yield no certificate when the proxy forwards none, rather than falling back to the proxy's" in {
    headerAfter(Some(proxyCert), None, behindProxy) shouldBe None
  }

  it should "treat a peer outside the allowlist as the caller, not as a forwarder" in {
    // A certificate from an unknown peer cannot speak for anyone else, so its header is discarded.
    val appPem = CertificateUtil.toPem(clientCert)
    headerAfter(Some(certFor("someone-else")), Some(appPem), behindProxy) should not equal Some(appPem)
  }

  "a plain proxy hop" should "honour the forwarded header when the prop allows it" in {
    val appPem = CertificateUtil.toPem(clientCert)
    headerAfter(None, Some(appPem), plainHop) shouldEqual Some(appPem)
  }

  "the resolution" should "be recorded for the metric row" in {
    def trustOf(req: Request[IO], config: PeerTrust.TrustConfig) =
      CallerCertificate.resolveCaller(req, config)
        .attributes.lookup(Http4sRequestAttributes.callerCertificateTrustKey)

    trustOf(requestWith(Some(clientCert), None), asEdge).map(_.mode) shouldEqual Some("direct")
    trustOf(requestWith(Some(proxyCert), Some("pem")), behindProxy).get.mode shouldEqual "forwarded"
    trustOf(requestWith(None, Some("pem")), asEdge).get.mode shouldEqual "none"

    // The detail is what makes the metric row diagnosable: WHICH proxy vouched, or WHY nobody did.
    trustOf(requestWith(Some(clientCert), None), asEdge).get.detail shouldBe None
    trustOf(requestWith(Some(proxyCert), Some("pem")), behindProxy).get.detail shouldEqual
      Some(PeerTrust.canonicalDn("CN=nginx-prod-1").get)
    trustOf(requestWith(None, Some("pem")), asEdge).get.detail.get should include("no client certificate")
  }

  it should "not be set on a request that carries no certificate material at all" in {
    val untouched = CallerCertificate.resolveCaller(Request[IO](Method.GET), asEdge)
    untouched.headers.get(psd2CertHeader) shouldBe None
    untouched.attributes.lookup(Http4sRequestAttributes.callerCertificateTrustKey) shouldBe None
  }
}
