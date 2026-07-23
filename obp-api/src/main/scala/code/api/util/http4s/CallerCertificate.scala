package code.api.util.http4s

import java.security.cert.X509Certificate

import cats.effect.IO
import code.api.RequestHeader
import code.api.util.PeerTrust
import code.util.Helper.MdcLoggable
import org.http4s.{Header, Request}
import org.http4s.server.ServerRequestKeys
import org.typelevel.ci.CIString

/**
 * Sets `PSD2-CERT` to the certificate of whoever is actually calling.
 *
 * Thin shell over [[PeerTrust.resolve]]: it collects the two inputs — the certificate Ember
 * verified in the TLS handshake, and the forwarded header — hands them to the rule, and rewrites
 * the request accordingly. All of the reasoning lives in `PeerTrust`, which is pure and testable
 * without a server; this file exists only to connect it to http4s.
 *
 * Replaces `Http4sMtls.injectClientCertificate`, which did the same job for the single deployment
 * it was written for (OBP as the mTLS edge in development) by unconditionally overwriting the
 * header with the handshake certificate. That is one branch of the rule rather than the whole of
 * it, and applying it behind a proxy would replace the App's certificate with the proxy's.
 *
 * Applied unconditionally from [[Http4sApp.httpApp]], not from the `mtls.enabled` branch of
 * Http4sServer: the deployment where the answer is least obvious — a proxy forwarding the header
 * over a hop with no client certificate — enables no TLS middleware at all.
 */
object CallerCertificate extends MdcLoggable {

  private val psd2CertHeaderName = CIString(RequestHeader.`PSD2-CERT`)

  /** Certificate the TLS handshake verified, if this request arrived over mTLS at all. */
  private def peerCertificate(req: Request[IO]): Option[X509Certificate] =
    req.attributes
      .lookup(ServerRequestKeys.SecureSession)
      .flatten
      .flatMap(_.X509Certificate.headOption)

  def resolveCaller(req: Request[IO]): Request[IO] = resolveCaller(req, PeerTrust.config)

  /** Overload taking the trust configuration explicitly, so tests can pin a deployment. */
  def resolveCaller(req: Request[IO], config: PeerTrust.TrustConfig): Request[IO] = {
    val forwarded = req.headers.get(psd2CertHeaderName).map(_.head.value).filter(_.trim.nonEmpty)
    val peer = peerCertificate(req)

    // Nothing to decide and nothing to strip: the overwhelmingly common request.
    if (peer.isEmpty && forwarded.isEmpty) req
    else {
      val resolution = PeerTrust.resolve(peer, forwarded, config)
      logger.debug(s"Caller certificate resolved as ${resolution.describe}")

      val withHeader = resolution.callerPem match {
        case Some(pem) => req.putHeaders(Header.Raw(psd2CertHeaderName, pem))
        case None => req.removeHeader(psd2CertHeaderName)
      }
      withHeader.withAttribute(Http4sRequestAttributes.callerCertificateTrustKey, resolution.describe)
    }
  }
}
