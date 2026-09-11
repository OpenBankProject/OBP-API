package bootstrap.http4s

import java.security.KeyStore
import java.security.cert.X509Certificate

import code.api.CertificateConstants
import code.api.util.CertificateUtil
import com.nimbusds.jose.util.X509CertUtils
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class Http4sMtlsTest extends AnyFlatSpec with Matchers {

  private val ServerJksResource = "/cert/server.jks"

  private def loadTestKeystore: KeyStore = {
    val keyStore = KeyStore.getInstance("JKS")
    val in = getClass.getResourceAsStream(ServerJksResource)
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

  "toPem" should "produce a parseable canonical PEM" in {
    val pem = Http4sMtls.toPem(testCertificate)
    pem should startWith(CertificateConstants.BEGIN_CERT + "\n")
    pem should endWith("\n" + CertificateConstants.END_CERT)
    X509CertUtils.parse(pem) should not be null
    // the normalize fallback used by the consumer lookup must round-trip it
    X509CertUtils.parse(CertificateUtil.normalizePemX509Certificate(pem)) should not be null
  }

  // Header injection moved to code.api.util.http4s.CallerCertificate when the "OBP is the edge"
  // assumption was generalised into the peer-vs-forwarder rule. Those scenarios live on in
  // CallerCertificateTest, where they are now one row of a table rather than the whole behaviour.

  // The dev-store guard recognises these files by digest wherever they have been copied to, so the
  // constants must track the files. Regenerating any dev store without updating them would leave a
  // check that silently matches nothing — this test is what turns that into a build failure.
  "the dev store digests" should "still match the checked-in files" in {
    val certDir = new java.io.File(getClass.getResource(ServerJksResource).toURI).getParentFile
    Http4sMtls.DevStoreDigests.foreach { case (name, digest) =>
      withClue(s"$name (regenerated without updating Http4sMtls.DevStoreDigests?): ") {
        Http4sMtls.sha256Of(new java.io.File(certDir, name)) shouldEqual digest
      }
    }
  }

  it should "cover every store checked into the cert directory" in {
    // A store the map does not know is a store the production guard will not refuse to boot on.
    // .crt/.key PEM files are not loadable as keystores, so only store extensions matter here.
    val certDir = new java.io.File(getClass.getResource(ServerJksResource).toURI).getParentFile
    val storeFiles = certDir.listFiles().map(_.getName)
      .filter(n => n.endsWith(".jks") || n.endsWith(".p12") || n.endsWith(".pfx"))
    storeFiles.toSet shouldEqual Http4sMtls.DevStoreDigests.keySet
  }

  // Outside Production the guard must not fire: development is the whole point of the dev pair.
  it should "not block a non-production run" in {
    val certDir = new java.io.File(getClass.getResource(ServerJksResource).toURI).getParentFile
    noException should be thrownBy
      Http4sMtls.assertNotADevStoreInProduction("mtls.keystore.path", new java.io.File(certDir, "server.jks"))
  }

  "buildSslContext" should "build an SSLContext from the checked-in dev keystores" in {
    val certDir = new java.io.File(getClass.getResource(ServerJksResource).toURI).getParent
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

  // A TPP's certificates arrive as often in PKCS12 as in JKS, and the store type cannot be
  // sniffed from the bytes cheaply — getting it wrong fails at load with an opaque parse error.
  "keyStoreTypeOf" should "select PKCS12 for .p12/.pfx and JKS otherwise" in {
    Http4sMtls.keyStoreTypeOf("/path/server.p12") shouldEqual "PKCS12"
    Http4sMtls.keyStoreTypeOf("/path/server.PFX") shouldEqual "PKCS12"
    Http4sMtls.keyStoreTypeOf("/path/server.jks") shouldEqual "JKS"
    Http4sMtls.keyStoreTypeOf("/path/server") shouldEqual "JKS"
  }

  it should "let buildSslContext load a PKCS12 keystore" in {
    val certDir = new java.io.File(getClass.getResource(ServerJksResource).toURI).getParent
    // localhost_san_dns_ip.pfx carries SAN DNS:localhost + IP:127.0.0.1, which JKS server.jks
    // lacks — clients that do not fall back to CN need this one.
    val config = Http4sMtls.MtlsConfig(
      keystorePath = s"$certDir/localhost_san_dns_ip.pfx",
      keystorePassword = "123456",
      truststorePath = s"$certDir/server.trust.jks",
      truststorePassword = "123456",
      needClientAuth = true
    )
    Http4sMtls.buildSslContext(config) should not be null
  }
}
