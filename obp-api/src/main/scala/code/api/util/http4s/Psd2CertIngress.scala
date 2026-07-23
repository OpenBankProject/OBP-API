package code.api.util.http4s

import cats.effect.IO
import code.api.RequestHeader
import code.api.util.CertificateUtil
import code.util.Helper.MdcLoggable
import org.http4s.{Header, Request}
import org.typelevel.ci.CIString

/**
 * Rewrites the `PSD2-CERT` request header into canonical PEM, whoever terminated TLS.
 *
 * The same certificate reaches OBP in several encodings depending on the deployment: nginx's
 * `$ssl_client_escaped_cert` is percent-encoded, HAProxy rebuilds a single-line PEM, the dev-mode
 * in-process terminator (bootstrap.http4s.Http4sMtls) injects canonical PEM, and a hand-built
 * client may send bare base64 with no PEM markers. Everything downstream — the Consumer lookup by
 * certificate, the consent/Consumer match, the PSD2 regulated-entity gate — then compares
 * certificates as strings, so each encoding behaves like a different certificate. That is the
 * mechanism behind "it works in development and fails in production".
 *
 * Normalising here, once, at the outermost layer, makes all of those comparisons exact. It also
 * removes the requirement documented in docs/MTLS_DEV_MODE.md that a proxy must hand OBP plain PEM:
 * nginx's percent-encoded form is understood directly, so no njs decoding step is needed.
 *
 * This layer never rejects a request. A header that cannot be parsed as a certificate is passed
 * through untouched, for the authorisation code to reject with its own error — normalisation is
 * not authentication, and conflating them here would move an access-control decision into a
 * formatting concern.
 *
 * Applied unconditionally, in [[Http4sApp.httpApp]], because the deployment that most needs it —
 * a proxy forwarding the header over a plain HTTP hop — runs no TLS middleware at all.
 */
object Psd2CertIngress extends MdcLoggable {

  private val psd2CertHeaderName = CIString(RequestHeader.`PSD2-CERT`)

  /**
   * The request with its `PSD2-CERT` header canonicalised. Returns the request unchanged when the
   * header is absent, already canonical, or not parseable as a certificate.
   */
  def canonicalize(req: Request[IO]): Request[IO] =
    req.headers.get(psd2CertHeaderName).map(_.head.value) match {
      case None => req
      case Some(rawValue) =>
        CertificateUtil.canonicalizePemX509Certificate(rawValue) match {
          case Some(canonical) if canonical == rawValue =>
            req
          case Some(canonical) =>
            logger.debug(s"${RequestHeader.`PSD2-CERT`} canonicalised on ingress " +
              s"(${rawValue.length} chars in, ${canonical.length} out)")
            req.putHeaders(Header.Raw(psd2CertHeaderName, canonical))
          case None =>
            // Left for the authorisation layer to reject: it owns the error code and the audit trail.
            logger.debug(s"${RequestHeader.`PSD2-CERT`} is not a parseable X509 certificate; passing it through unchanged")
            req
        }
    }
}
