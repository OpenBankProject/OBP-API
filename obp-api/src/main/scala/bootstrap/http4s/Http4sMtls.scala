package bootstrap.http4s

import java.io.{File, FileInputStream}
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

import cats.data.Kleisli
import cats.effect.IO
import code.api.{CertificateConstants, RequestHeader}
import code.api.util.APIUtil
import code.util.Helper.MdcLoggable
import fs2.io.net.tls.{TLSContext, TLSParameters}
import net.liftweb.util.Props
import org.http4s.{Header, HttpApp}
import org.http4s.server.ServerRequestKeys
import org.typelevel.ci.CIString

/**
 * Dev-only in-process mTLS termination for the http4s server.
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
 * The prop is honoured ONLY in Development run mode. In production OBP must sit behind a reverse
 * proxy that terminates mTLS and forwards the client certificate as the PSD2-CERT header — this
 * feature exists so local development does not need that proxy.
 */
object Http4sMtls extends MdcLoggable {

  case class MtlsConfig(
    keystorePath: String,
    keystorePassword: String,
    truststorePath: String,
    truststorePassword: String,
    needClientAuth: Boolean
  )

  /** True only when mtls.enabled=true AND run.mode is development. */
  lazy val enabled: Boolean = {
    val propEnabled = APIUtil.getPropsAsBoolValue("mtls.enabled", false)
    if (propEnabled && Props.mode != Props.RunModes.Development) {
      logger.warn("mtls.enabled=true is ignored: in-process mTLS termination is a dev-only feature " +
        s"and run.mode is ${Props.mode}. In production terminate mTLS at a reverse proxy that sets the " +
        s"${RequestHeader.`PSD2-CERT`} header.")
      false
    } else propEnabled
  }

  // The checked-in dev pair: a CN=localhost server keypair and the TESOBE CA that signs the dev
  // client certificates. Repo-relative, so they resolve when the server is launched from the repo
  // root (which is what the build_and_run scripts do). Only ever reached in Development run mode.
  private val DevKeystorePath = "obp-api/src/test/resources/cert/server.jks"
  private val DevTruststorePath = "obp-api/src/test/resources/cert/server.trust.jks"
  private val DevStorePassword = "123456"

  /** The OBP_-prefixed environment variable APIUtil.getPropsValue consults ahead of the props file. */
  private def envVarOf(propName: String): String = "OBP_" + propName.replace('.', '_').toUpperCase

  lazy val config: MtlsConfig = {
    def prop(name: String): Option[String] =
      APIUtil.getPropsValue(name).toOption.map(_.trim).filter(_.nonEmpty)

    // Returns the absolute store path plus its password, defaulting both to the dev pair.
    def store(pathProp: String, devPath: String, passwordProp: String): (String, String) = {
      val path = prop(pathProp).getOrElse {
        logger.warn(s"'$pathProp' is not set — falling back to the checked-in dev store '$devPath'. " +
          s"Set '$pathProp' (or ${envVarOf(pathProp)}) to use your own certificates.")
        devPath
      }
      val file = new File(path)
      if (!file.isFile) throw new RuntimeException(
        s"mtls.enabled=true but '$pathProp' points at '$path', which does not exist " +
          s"(resolved to ${file.getAbsolutePath} from working directory ${new File(".").getAbsolutePath}). " +
          s"Launch from the repo root or set '$pathProp' to an absolute path.")
      val password = prop(passwordProp).getOrElse {
        logger.warn(s"'$passwordProp' is not set — falling back to the checked-in dev store password.")
        DevStorePassword
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

  private val psd2CertHeaderName = CIString(RequestHeader.`PSD2-CERT`)

  // Canonical PEM: 64-column base64 with \n separators, the format developers paste when
  // registering a Consumer, so the verbatim clientCertificate DB lookup can match. The
  // normalizePemX509Certificate fallback and the consent removeBreakLines compare cover the rest.
  private val pemEncoder = java.util.Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))

  def toPem(certificate: X509Certificate): String =
    s"${CertificateConstants.BEGIN_CERT}\n${pemEncoder.encodeToString(certificate.getEncoded)}\n${CertificateConstants.END_CERT}"

  /**
   * Replaces the Jetty customizer of the old RunMTLSWebApp: takes the client certificate that
   * Ember verified during the TLS handshake (exposed via ServerRequestKeys.SecureSession) and
   * injects it as the PSD2-CERT header. Any client-supplied PSD2-CERT header is always removed
   * first — when OBP terminates TLS itself, that header can only be a spoofing attempt.
   */
  def injectClientCertificate(httpApp: HttpApp[IO]): HttpApp[IO] = Kleisli { req =>
    val stripped = req.removeHeader(psd2CertHeaderName)
    val leafCertificate: Option[X509Certificate] = req.attributes
      .lookup(ServerRequestKeys.SecureSession)
      .flatten
      .flatMap(_.X509Certificate.headOption)
    val requestForApp = leafCertificate match {
      case Some(certificate) => stripped.putHeaders(Header.Raw(psd2CertHeaderName, toPem(certificate)))
      case None => stripped
    }
    httpApp(requestForApp)
  }
}
