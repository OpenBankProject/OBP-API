package code.api.util.http4s

import java.security.cert.X509Certificate

import cats.effect.IO
import code.api.CertificateConstants
import code.api.util.CertificateUtil
import code.api.util.SelfSignedCertificateUtil.generateSelfSignedCert
import org.http4s.{Header, Method, Request, Uri}
import org.typelevel.ci.CIString
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * The point of ingress normalisation: the same certificate arrives in whichever encoding the
 * deployment's TLS terminator happens to produce, and everything downstream compares certificates
 * as strings. These tests pin every encoding we know of to one canonical form.
 */
class Psd2CertIngressTest extends AnyFlatSpec with Matchers {

  private val psd2CertHeader = CIString("PSD2-CERT")
  private val NotACertificate = "not a certificate"

  private val certificate: X509Certificate =
    generateSelfSignedCert("test-tpp")._2.asInstanceOf[X509Certificate]

  /** What every encoding below must reduce to. */
  private val canonical: String = CertificateUtil.toPem(certificate)

  private val base64Body: String = canonical
    .replace(CertificateConstants.BEGIN_CERT, "")
    .replace(CertificateConstants.END_CERT, "")
    .replaceAll("\\s", "")

  private def singleLinePem: String =
    s"${CertificateConstants.BEGIN_CERT}$base64Body${CertificateConstants.END_CERT}"

  /** nginx's $ssl_client_escaped_cert: percent-encoded, newlines as %0A. */
  private def percentEncoded: String = canonical
    .replace("\n", "%0A")
    .replace(" ", "%20")
    .replace("+", "%2B")
    .replace("/", "%2F")
    .replace("=", "%3D")

  private def canonicalizedHeaderOf(rawValue: String): Option[String] = {
    val req = Request[IO](Method.GET, Uri.unsafeFromString("/obp/v5.1.0/root"))
      .putHeaders(Header.Raw(psd2CertHeader, rawValue))
    Psd2CertIngress.canonicalize(req).headers.get(psd2CertHeader).map(_.head.value)
  }

  "canonicalizePemX509Certificate" should "reduce every encoding a TLS terminator produces to one form" in {
    // The dev-mode in-process terminator (Http4sMtls) already injects this form.
    CertificateUtil.canonicalizePemX509Certificate(canonical) shouldEqual Some(canonical)
    // HAProxy rebuilds a single-line PEM.
    CertificateUtil.canonicalizePemX509Certificate(singleLinePem) shouldEqual Some(canonical)
    // nginx forwards $ssl_client_escaped_cert, which needed an njs decoding step before this.
    CertificateUtil.canonicalizePemX509Certificate(percentEncoded) shouldEqual Some(canonical)
    // A hand-built client may send bare base64 with no PEM markers at all.
    CertificateUtil.canonicalizePemX509Certificate(base64Body) shouldEqual Some(canonical)
    // Whitespace damage in transit.
    CertificateUtil.canonicalizePemX509Certificate(s"  $singleLinePem  ") shouldEqual Some(canonical)
  }

  it should "not corrupt a certificate whose base64 contains '+'" in {
    // java.net.URLDecoder maps '+' to a space, which is right for form encoding and wrong for a
    // base64 payload. The key is generated, so cancel rather than pass vacuously if this one has
    // no '+' to be corrupted.
    assume(base64Body.contains("+"), "generated certificate has no '+' in its base64")
    val escapedExceptPlus = canonical.replace("\n", "%0A")
    CertificateUtil.canonicalizePemX509Certificate(escapedExceptPlus) shouldEqual Some(canonical)
  }

  it should "return None for values that are not certificates" in {
    CertificateUtil.canonicalizePemX509Certificate("") shouldBe None
    CertificateUtil.canonicalizePemX509Certificate("   ") shouldBe None
    CertificateUtil.canonicalizePemX509Certificate(NotACertificate) shouldBe None
    CertificateUtil.canonicalizePemX509Certificate("-4611686018427387904") shouldBe None // the getHeaderValue sentinel
  }

  "the ingress middleware" should "canonicalise the PSD2-CERT header in place" in {
    canonicalizedHeaderOf(percentEncoded) shouldEqual Some(canonical)
    canonicalizedHeaderOf(singleLinePem) shouldEqual Some(canonical)
    canonicalizedHeaderOf(canonical) shouldEqual Some(canonical)
  }

  it should "leave a request with no PSD2-CERT header untouched" in {
    val req = Request[IO](Method.GET, Uri.unsafeFromString("/obp/v5.1.0/root"))
    Psd2CertIngress.canonicalize(req).headers.get(psd2CertHeader) shouldBe None
  }

  it should "pass an unparseable header through unchanged rather than rejecting it" in {
    // Normalisation is not authentication: the authorisation layer owns the error code, so a
    // garbage header must survive to reach it rather than being dropped or 400'd here.
    canonicalizedHeaderOf(NotACertificate) shouldEqual Some(NotACertificate)
  }

  // The regression this phase had to be designed around. Consumers are registered by pasting a PEM,
  // so the stored value may be single-line; the lookup used to match it because the raw header was
  // single-line too. Canonicalising the header changes that first comparison, and the whitespace-
  // normalising fallback has to be what catches it — otherwise those Consumers stop authenticating.
  "a Consumer stored with a single-line PEM" should "still match a canonicalised header after normalisation" in {
    val storedByConsumerRegistration = singleLinePem
    val headerAfterIngress = canonicalizedHeaderOf(percentEncoded).get

    headerAfterIngress should not equal storedByConsumerRegistration // the exact-match lookup misses
    CertificateUtil.comparePemX509Certificates(headerAfterIngress, storedByConsumerRegistration) shouldBe true
    CertificateUtil.normalizePemX509Certificate(headerAfterIngress) shouldEqual storedByConsumerRegistration
  }
}
