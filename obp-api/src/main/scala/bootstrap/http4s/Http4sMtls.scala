package bootstrap.http4s

import java.io.FileInputStream
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

  lazy val config: MtlsConfig = {
    def required(name: String): String = APIUtil.getPropsValue(name)
      .openOrThrowException(s"mtls.enabled=true requires the props value '$name' to be set")
    MtlsConfig(
      keystorePath = required("mtls.keystore.path"),
      keystorePassword = required("mtls.keystore.password"),
      truststorePath = required("mtls.truststore.path"),
      truststorePassword = required("mtls.truststore.password"),
      needClientAuth = APIUtil.getPropsValue("mtls.client_auth", "need").toLowerCase != "want"
    )
  }

  def buildSslContext(config: MtlsConfig): SSLContext = {
    def loadJks(path: String, password: String): KeyStore = {
      val keyStore = KeyStore.getInstance("JKS")
      val in = new FileInputStream(path)
      try keyStore.load(in, password.toCharArray) finally in.close()
      keyStore
    }
    val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    keyManagerFactory.init(loadJks(config.keystorePath, config.keystorePassword), config.keystorePassword.toCharArray)
    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    trustManagerFactory.init(loadJks(config.truststorePath, config.truststorePassword))
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
