package code.api.util

import java.security.cert.X509Certificate

import code.api.util.PeerTrust._
import code.api.util.SelfSignedCertificateUtil.generateSelfSignedCert
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * The decision table of docs/MTLS_TOPOLOGIES.md §3, one test per row.
 *
 * Pure: no server, no socket, no handshake. That is the point of keeping the rule in a function
 * rather than in middleware — the branch that matters most in production (a forwarded header
 * arriving over a hop with no client certificate) is otherwise the hardest one to exercise.
 */
class PeerTrustTest extends AnyFlatSpec with Matchers {

  private val ProxyCn = "CN=nginx-prod-1"

  private def certFor(cn: String): X509Certificate =
    generateSelfSignedCert(cn)._2.asInstanceOf[X509Certificate]

  private val proxyCert = certFor("nginx-prod-1")
  private val appCert = certFor("some-tpp")

  private def canonical(dn: String) = PeerTrust.canonicalDn(dn).get

  // Self-signed, so issuer == subject. Real proxies are CA-signed; the matching logic is the same.
  private val proxyIsTrusted = TrustConfig(
    trustedProxies = List(TrustedProxy(canonical(ProxyCn), Some(canonical(ProxyCn)))),
    trustForwardedHeaderWithoutTls = false
  )
  private val nothingIsTrusted = TrustConfig(Nil, trustForwardedHeaderWithoutTls = false)

  private val forwardedPem = CertificateUtil.toPem(appCert)

  "a trusted forwarder that forwards a certificate" should "identify the caller from the header" in {
    val resolution = resolve(Some(proxyCert), Some(forwardedPem), proxyIsTrusted)
    resolution.callerPem shouldEqual Some(forwardedPem)
    resolution shouldBe a[ForwardedCaller]
    resolution.describe should include("forwarded via")
  }

  "a trusted forwarder that forwards nothing" should "yield no caller rather than falling back to the proxy" in {
    // The trap this design exists to avoid: treating the proxy's own certificate as an identity
    // would make every un-certificated request look like the same TPP.
    val resolution = resolve(Some(proxyCert), None, proxyIsTrusted)
    resolution.callerPem shouldBe None
    resolution shouldBe a[NoCaller]
  }

  "a peer that is not a trusted forwarder" should "be the caller itself" in {
    val resolution = resolve(Some(appCert), None, proxyIsTrusted)
    resolution.callerPem shouldEqual Some(CertificateUtil.toPem(appCert))
    resolution shouldBe a[DirectCaller]
  }

  it should "have any forwarded header discarded as a spoofing attempt" in {
    // OBP is the edge here, so a PSD2-CERT header cannot have come from a proxy we trust.
    val resolution = resolve(Some(appCert), Some(CertificateUtil.toPem(proxyCert)), proxyIsTrusted)
    resolution.callerPem shouldEqual Some(CertificateUtil.toPem(appCert))
  }

  "an empty allowlist" should "make every peer a direct caller — OBP as the TLS edge" in {
    // Development's configuration, and the one that reproduces the pre-existing dev behaviour.
    resolve(Some(proxyCert), Some(forwardedPem), nothingIsTrusted).callerPem shouldEqual
      Some(CertificateUtil.toPem(proxyCert))
  }

  "a forwarded header with no TLS peer" should "be trusted only when the prop allows it" in {
    val permissive = TrustConfig(Nil, trustForwardedHeaderWithoutTls = true)
    resolve(None, Some(forwardedPem), permissive).callerPem shouldEqual Some(forwardedPem)

    // Fail closed: nothing authenticated the sender, so the header proves nothing.
    val strict = TrustConfig(Nil, trustForwardedHeaderWithoutTls = false)
    resolve(None, Some(forwardedPem), strict).callerPem shouldBe None
    resolve(None, Some(forwardedPem), strict).describe should include("trust_forwarded_header_without_tls")
  }

  "no peer and no header" should "be no caller" in {
    resolve(None, None, proxyIsTrusted).callerPem shouldBe None
  }

  "an unparseable forwarded value" should "still be passed on when the forwarder is trusted" in {
    // Deciding WHO is calling and deciding whether their certificate is any good are different
    // jobs. The authorisation layer owns the error code, so garbage must reach it.
    resolve(Some(proxyCert), Some("not a certificate"), proxyIsTrusted).callerPem shouldEqual
      Some("not a certificate")
  }

  "issuer matching" should "accept any subject when the allowlist entry has none" in {
    val anySubjectFromThatIssuer =
      TrustConfig(List(TrustedProxy(canonical(ProxyCn), None)), trustForwardedHeaderWithoutTls = false)
    resolve(Some(proxyCert), Some(forwardedPem), anySubjectFromThatIssuer) shouldBe a[ForwardedCaller]
    // A different certificate is not covered: its issuer differs.
    resolve(Some(appCert), Some(forwardedPem), anySubjectFromThatIssuer) shouldBe a[DirectCaller]
  }

  it should "reject a subject that does not match, even from the right issuer" in {
    val wrongSubject = TrustConfig(
      List(TrustedProxy(canonical(ProxyCn), Some(canonical("CN=nginx-prod-2")))),
      trustForwardedHeaderWithoutTls = false
    )
    resolve(Some(proxyCert), Some(forwardedPem), wrongSubject) shouldBe a[DirectCaller]
  }

  "canonicalDn" should "normalise case and spacing but not RDN order" in {
    // What operators can get away with...
    canonicalDn("CN=nginx-prod-1,  O=TESOBE GmbH,C=DE") shouldEqual
      canonicalDn("cn=NGINX-PROD-1,O=tesobe gmbh, c=de")
    // ...and what they cannot. Hence the openssl command in the operator docs.
    canonicalDn("CN=nginx-prod-1,O=TESOBE GmbH,C=DE") should not equal
      canonicalDn("C=DE,O=TESOBE GmbH,CN=nginx-prod-1")
  }

  it should "return None for something that is not a distinguished name" in {
    canonicalDn("nginx-prod-1") shouldBe None
    canonicalDn("") shouldBe None
  }

  // The metric row stores the structured form (certificate_trust / certificate_trust_detail),
  // not the describe sentence — so the mode/detail split is contract, not presentation.
  "the resolution's structured form" should "expose mode and detail separately" in {
    val forwarded = resolve(Some(proxyCert), Some(forwardedPem), proxyIsTrusted)
    forwarded.mode shouldEqual "forwarded"
    forwarded.detail shouldEqual Some(canonical(ProxyCn))

    val direct = resolve(Some(appCert), None, proxyIsTrusted)
    direct.mode shouldEqual "direct"
    direct.detail shouldBe None

    val none = resolve(None, None, proxyIsTrusted)
    none.mode shouldEqual "none"
    none.detail.get should include("no TLS client certificate")
  }

  // mtls.trust_forwarded_header_without_tls exists for the plain-proxy-hop deployment. When OBP
  // terminates mTLS itself and no forwarder is configured it is provably the edge, and the prop's
  // permissive default would re-open the spoofing hole the pre-generalisation middleware closed
  // (a certless peer under client_auth=want sending its own PSD2-CERT).
  "trust_forwarded_header_without_tls" should "be ignored when OBP terminates mTLS as the edge" in {
    PeerTrust.effectiveTrustWithoutTls(propValue = true, noProxiesConfigured = true, mtlsEnabled = true) shouldBe false
  }

  it should "be honoured on a plain proxy hop, where the header is all there is" in {
    PeerTrust.effectiveTrustWithoutTls(propValue = true, noProxiesConfigured = true, mtlsEnabled = false) shouldBe true
  }

  it should "be honoured when trusted proxies are configured, mTLS or not" in {
    // With an allowlist the deployment is behind-proxy; certless hops may still be legitimate
    // (e.g. one proxy already on mTLS, another not yet migrated).
    PeerTrust.effectiveTrustWithoutTls(propValue = true, noProxiesConfigured = false, mtlsEnabled = true) shouldBe true
    PeerTrust.effectiveTrustWithoutTls(propValue = true, noProxiesConfigured = false, mtlsEnabled = false) shouldBe true
  }

  it should "never turn an explicit false back on" in {
    PeerTrust.effectiveTrustWithoutTls(propValue = false, noProxiesConfigured = true, mtlsEnabled = true) shouldBe false
    PeerTrust.effectiveTrustWithoutTls(propValue = false, noProxiesConfigured = false, mtlsEnabled = false) shouldBe false
  }
}
