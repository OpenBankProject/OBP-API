package bootstrap.http4s

import java.io.{File, FileInputStream}
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

import cats.effect.IO
import code.api.util.{APIUtil, CertificateUtil}
import code.util.Helper.MdcLoggable
import fs2.io.net.tls.{TLSContext, TLSParameters}
import net.liftweb.util.Props

/**
 * In-process mTLS termination for the http4s server.
 *
 * The old Lift/Jetty stack had RunMTLSWebApp.scala (removed with the Jetty teardown): an embedded
 * server that terminated mutual TLS itself and injected the verified client certificate into the
 * request as the PSD2-CERT header. This object is its http4s equivalent, toggled by props instead
 * of a separate launcher:
 *
 *   mtls.enabled=true
 *   mtls.keystore.path=/path/to/server.jks          (server keypair)
 *   mtls.keystore.password=...
 *   mtls.truststore.path=/path/to/server.trust.jks  (CAs allowed to sign client certificates)
 *   mtls.truststore.password=...
 *   mtls.client_auth=need                           (need = reject certless handshakes; want = optional)
 *
 * Only `mtls.enabled` is mandatory: the four store props fall back to the checked-in dev keystore
 * pair (see DevKeystorePath below) so that switching a local server to mTLS is a single toggle.
 * Every fallback is logged at WARN naming the resolved file, so the default is never silent.
 * Like every OBP prop these are also settable from the environment as OBP_MTLS_ENABLED,
 * OBP_MTLS_KEYSTORE_PATH, ... (see APIUtil.getPropsValue, which reads the environment first).
 *
 * This object only builds the TLS context and the store configuration. Deciding whether the peer it
 * authenticates IS the caller or a proxy speaking for one belongs to
 * code.api.util.PeerTrust / code.api.util.http4s.CallerCertificate, which run for every request
 * whether or not TLS terminates here — see docs/MTLS_TOPOLOGIES.md.
 *
 * Honoured in every run mode. It was Development-only while OBP-as-the-edge was assumed to be a
 * local convenience; the guard that replaced the run-mode gate refuses to boot a Production server
 * on the development certificates checked into this repository, which is the risk the mode gate was
 * really standing in for.
 */
object Http4sMtls extends MdcLoggable {

  case class MtlsConfig(
    keystorePath: String,
    keystorePassword: String,
    truststorePath: String,
    truststorePassword: String,
    needClientAuth: Boolean
  )

  /** True when mtls.enabled=true, in any run mode. */
  lazy val enabled: Boolean = APIUtil.getPropsAsBoolValue("mtls.enabled", false)

  // The checked-in dev pair: a CN=localhost server keypair and the TESOBE CA that signs the dev
  // client certificates. Repo-relative, so they resolve when the server is launched from the repo
  // root (which is what the build_and_run scripts do).
  private val DevKeystorePath = "obp-api/src/test/resources/cert/server.jks"
  private val DevTruststorePath = "obp-api/src/test/resources/cert/server.trust.jks"
  private val DevStorePassword = "123456"

  /**
   * SHA-256 of EVERY store checked into `obp-api/src/test/resources/cert`, keyed by filename, so
   * they can be recognised wherever they are copied to.
   *
   * All of them are public: the private keys are in the repository and the password is in the
   * source. A production server using any of them would accept forged client certificates and
   * present a certificate anyone can impersonate, so [[assertNotADevStoreInProduction]] refuses to
   * boot. The list must cover both the legacy JKS pair (the prop defaults) and the role-named
   * PKCS12 set that generate_dev_certs.sh writes — docs/MTLS.md steers readers to the latter, so
   * guarding only the pair the props default to would leave the recommended set unguarded.
   *
   * Http4sMtlsTest asserts these digests still match the files, so regenerating any dev store
   * fails the build here rather than silently disarming the check.
   */
  private[http4s] val DevStoreDigests: Map[String, String] = Map(
    "server.jks"               -> "c51d3c1694b3d3a5cb9fd7d41011b75ec22c2a35ef48f9713f9b0e00d54d78eb",
    "server.trust.jks"         -> "f47613f7ec4de4e291668c070047c256bf4578cfa9b1dabe5a61d056461473d0",
    "localhost_san_dns_ip.pfx" -> "e78c09858fe0659bc67d14570e67e57d750280d10db43980992e54ba60a7427d",
    "obp-server.p12"           -> "e56ec88d23fb1691d494090f6abec98332b3410e75251814f70ac0ed981bf918",
    "dev-truststore.p12"       -> "9c606b3012ffc01e85d26c196b80d1ad5f731fa6a7b5c6ed94736d79d800e3ec",
    "tpp-client.p12"           -> "717bdb83aa3d85f6245cf99762a080cfdf45d9e87de699d7339e600a90754e4a",
    "proxy-client.p12"         -> "e7f1e212a25642daaabd587184836afcf05506cdf19eb5ad570bdbe3352d82a8",
    "expired-tpp.p12"          -> "42c9564f96b21354f3b0f9a91925c2e81a22bad6850070d1618966a2cafe0e09"
  )

  private[http4s] def sha256Of(file: File): String = {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    val in = new FileInputStream(file)
    try {
      val buffer = new Array[Byte](8192)
      var read = in.read(buffer)
      while (read != -1) {
        digest.update(buffer, 0, read)
        read = in.read(buffer)
      }
    } finally in.close()
    digest.digest().map("%02x".format(_)).mkString
  }

  /**
   * Refuses to boot a Production server on the repository's development certificates.
   *
   * This replaces the run-mode gate that used to disable mTLS termination outside Development. That
   * gate was guarding the wrong thing: terminating mTLS in production is a supported topology (see
   * docs/MTLS_TOPOLOGIES.md), while doing it with a keypair published in a public repository is
   * not. Checking the file rather than the run mode also catches the store being copied elsewhere,
   * which a path check would miss.
   */
  private[http4s] def assertNotADevStoreInProduction(propName: String, file: File): Unit =
    if (Props.mode == Props.RunModes.Production) {
      val digest = sha256Of(file)
      DevStoreDigests.find(_._2 == digest).foreach { case (knownAs, _) =>
        throw new RuntimeException(
          s"'$propName' points at ${file.getAbsolutePath}, which is the development store '$knownAs' " +
            "checked into the OBP-API repository. Its private key is public and its password is " +
            "'123456', so it cannot be used with run.mode=production. Supply your own certificates.")
      }
    }

  /** The OBP_-prefixed environment variable APIUtil.getPropsValue consults ahead of the props file. */
  private def envVarOf(propName: String): String = "OBP_" + propName.replace('.', '_').toUpperCase

  lazy val config: MtlsConfig = {
    def prop(name: String): Option[String] =
      APIUtil.getPropsValue(name).toOption.map(_.trim).filter(_.nonEmpty)

    // Returns the absolute store path plus its password, defaulting both to the dev pair.
    def store(pathProp: String, devPath: String, passwordProp: String): (String, String) = {
      val configuredPath = prop(pathProp)
      val path = configuredPath.getOrElse {
        logger.warn(s"'$pathProp' is not set — falling back to the checked-in dev store '$devPath'. " +
          s"Set '$pathProp' (or ${envVarOf(pathProp)}) to use your own certificates.")
        devPath
      }
      val file = new File(path)
      if (!file.isFile) throw new RuntimeException(
        s"mtls.enabled=true but '$pathProp' points at '$path', which does not exist " +
          s"(resolved to ${file.getAbsolutePath} from working directory ${new File(".").getAbsolutePath}). " +
          s"Launch from the repo root or set '$pathProp' to an absolute path.")
      assertNotADevStoreInProduction(pathProp, file)
      // The password only follows the dev default when the store itself does. An operator-supplied
      // store with a missing password prop must fail here, by name — falling back to '123456'
      // would surface later as an opaque keystore integrity error instead.
      val password = prop(passwordProp).getOrElse {
        if (configuredPath.isEmpty) {
          logger.warn(s"'$passwordProp' is not set — using the checked-in dev store password to match the dev store.")
          DevStorePassword
        } else throw new RuntimeException(
          s"'$pathProp' is set but '$passwordProp' is not. Set '$passwordProp' (or ${envVarOf(passwordProp)}) " +
            "to the password of that store.")
      }
      (file.getAbsolutePath, password)
    }

    val (keystorePath, keystorePassword) = store("mtls.keystore.path", DevKeystorePath, "mtls.keystore.password")
    val (truststorePath, truststorePassword) = store("mtls.truststore.path", DevTruststorePath, "mtls.truststore.password")
    MtlsConfig(
      keystorePath = keystorePath,
      keystorePassword = keystorePassword,
      truststorePath = truststorePath,
      truststorePassword = truststorePassword,
      needClientAuth = APIUtil.getPropsValue("mtls.client_auth", "need").toLowerCase != "want"
    )
  }

  /**
   * KeyStore type for a store file, by extension: `.p12` / `.pfx` are PKCS12, everything else JKS.
   * Both formats are common for the certificates a TPP hands over — `obp-api/src/test/resources/cert`
   * carries examples of each — and guessing wrong fails at load with an opaque parse error.
   */
  private[http4s] def keyStoreTypeOf(path: String): String =
    path.toLowerCase match {
      case p if p.endsWith(".p12") || p.endsWith(".pfx") => "PKCS12"
      case _ => "JKS"
    }

  def buildSslContext(config: MtlsConfig): SSLContext = {
    def loadStore(path: String, password: String): KeyStore = {
      val keyStore = KeyStore.getInstance(keyStoreTypeOf(path))
      val in = new FileInputStream(path)
      try keyStore.load(in, password.toCharArray) finally in.close()
      keyStore
    }
    val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    keyManagerFactory.init(loadStore(config.keystorePath, config.keystorePassword), config.keystorePassword.toCharArray)
    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    trustManagerFactory.init(loadStore(config.truststorePath, config.truststorePassword))
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, null)
    sslContext
  }

  def tlsContext: TLSContext[IO] = TLSContext.Builder.forAsync[IO].fromSSLContext(buildSslContext(config))

  def tlsParameters: TLSParameters =
    if (config.needClientAuth) TLSParameters(needClientAuth = true)
    else TLSParameters(wantClientAuth = true)

  // Canonical PEM, via the one emitter every path shares (CertificateUtil.toPem): the format
  // developers paste when registering a Consumer, so the verbatim clientCertificate DB lookup can
  // match. Ingress normalisation rewrites forwarded certificates into this same form, so a
  // certificate presented in a handshake and the same certificate arriving through a proxy are
  // byte-identical.
  def toPem(certificate: X509Certificate): String = CertificateUtil.toPem(certificate)
}
