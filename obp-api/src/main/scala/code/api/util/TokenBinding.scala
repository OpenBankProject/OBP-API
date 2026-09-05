package code.api.util

import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Base64

import code.api.util.APIUtil.`getPSD2-CERT`
import code.api.util.ErrorMessages._
import code.util.Helper.MdcLoggable
import com.nimbusds.jwt.SignedJWT
import net.liftweb.common.{Box, Failure, Full}
import net.liftweb.util.Helpers.tryo

/**
 * Sender-constrained (certificate-bound) access token verification — RFC 8705, as required
 * by FAPI at the resource server. A FAPI-grade authorization server (e.g. Keycloak with
 * certificate-bound access tokens enabled per client) stamps the confirmation claim
 * `cnf.x5t#S256` = base64url(SHA-256(DER of the client certificate)) into the access token.
 * This object compares that claim against the certificate the caller actually presented,
 * so a stolen bearer token cannot be replayed from a different TLS client.
 *
 * The caller certificate is whatever [[PeerTrust]] resolved for this request (the direct TLS
 * peer, or one forwarded by a trusted proxy) — delivered via the PSD2-CERT request header, the
 * same channel the PSD2 certificate checks use.
 *
 * This check specifically requires cryptographic proof of possession — that is the entire point
 * of RFC 8705 sender-constraining — so it does NOT simply trust whatever PeerTrust resolved as
 * "the caller" for authorization purposes elsewhere. PeerTrust's own default
 * (`mtls.trust_forwarded_header_without_tls=true` with no `mtls.enabled`/trusted proxies
 * configured) treats an unauthenticated PSD2-CERT header as identifying the caller, which is
 * sufficient for endpoints that only need SOME certificate to attribute a request to, but is
 * exactly the "publicly-known QWAC replayed alongside a stolen bearer token" attack RFC 8705
 * exists to prevent — a PEM is public information; only a live TLS handshake (direct, or
 * forwarded by a proxy whose OWN certificate matched an `mtls.trusted_proxy` entry) proves the
 * presenter actually holds the private key. `certificateTrustDetail ==
 * PeerTrust.UnauthenticatedHopDetail` is precisely PeerTrust's own name for "no such proof", so
 * `callerCertificateForBinding` below treats it the same as no certificate at all.
 *
 * Gated by the oauth2.token_binding.mode Props value:
 *  - NONE (default): no checking — existing deployments are untouched.
 *  - MONITOR: check tokens that carry cnf.x5t#S256 and log mismatches, but never reject.
 *  - ENFORCE: reject bound tokens that do not match (or arrive with no certificate);
 *             tokens without a cnf claim still pass, so a mixed estate can migrate app by app.
 *  - REQUIRED: every OAuth2 token must be bound and match — full FAPI posture.
 *
 * DPoP (cnf.jkt, the FAPI 2.0 alternative binding) is not yet supported.
 */
object TokenBinding extends MdcLoggable {

  final val ModePropsName = "oauth2.token_binding.mode"

  object Mode extends Enumeration {
    val NONE, MONITOR, ENFORCE, REQUIRED = Value
  }

  /**
   * The configured mode. An unrecognised value cannot be allowed to silently harden or soften
   * the instance, so it logs loudly and behaves as NONE — the same behaviour as before the
   * prop existed.
   */
  def configuredMode: Mode.Value = {
    val raw = APIUtil.getPropsValue(ModePropsName, Mode.NONE.toString).trim.toUpperCase
    Mode.values.find(_.toString == raw).getOrElse {
      logger.error(s"$ModePropsName has invalid value '$raw' (valid values: ${Mode.values.mkString(", ")}). " +
        s"Falling back to ${Mode.NONE} — token binding is NOT being checked.")
      Mode.NONE
    }
  }

  /** base64url without padding of SHA-256 over the certificate's DER encoding (x5t#S256). */
  def x5tS256(certificate: X509Certificate): String =
    Base64.getUrlEncoder.withoutPadding.encodeToString(
      MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded))

  /**
   * The cnf.x5t#S256 claim of a JWT, if present. The token's signature must already have been
   * validated by the caller — this only parses claims.
   */
  def cnfX5tS256(jwtToken: String): Option[String] =
    tryo(SignedJWT.parse(jwtToken).getJWTClaimsSet.getJSONObjectClaim("cnf")).toOption
      .flatMap(Option(_))
      .flatMap(cnf => Option(cnf.get("x5t#S256")))
      .map(_.toString)
      .filter(_.nonEmpty)

  /**
   * The pure decision — mode and inputs passed explicitly so it is testable without Props or
   * a server. Returns Full(()) when the request may proceed.
   */
  def verify(
      mode: Mode.Value,
      cnfThumbprint: Option[String],
      callerCertificate: Option[X509Certificate],
      logContext: => String
  ): Box[Unit] = {
    (mode, cnfThumbprint, callerCertificate) match {
      case (Mode.NONE, _, _) =>
        Full(())
      case (Mode.REQUIRED, None, _) =>
        Failure(Oauth2TokenBindingRequired)
      case (_, None, _) => // MONITOR / ENFORCE: an unbound token passes untouched
        Full(())
      case (Mode.MONITOR, Some(_), None) =>
        logger.warn(s"TOKEN BINDING MONITOR: token is certificate-bound (cnf.x5t#S256) " +
          s"but no client certificate was presented. $logContext")
        Full(())
      case (_, Some(_), None) => // ENFORCE / REQUIRED
        Failure(Oauth2TokenBindingCertificateMissing)
      case (m, Some(expected), Some(certificate)) =>
        val presented = x5tS256(certificate)
        // constant-time comparison — thumbprints are secrets-adjacent
        val matches = MessageDigest.isEqual(expected.getBytes("UTF-8"), presented.getBytes("UTF-8"))
        if (matches) Full(())
        else if (m == Mode.MONITOR) {
          logger.warn(s"TOKEN BINDING MONITOR: thumbprint mismatch — token cnf.x5t#S256=$expected, " +
            s"presented certificate=$presented. $logContext")
          Full(())
        } else {
          Failure(Oauth2TokenBindingCertificateMismatch)
        }
    }
  }

  /**
   * The certificate to bind against, or None when what PeerTrust resolved for this request is
   * not cryptographic proof of possession (see the class doc). Reads `cc.certificateTrust` /
   * `certificateTrustDetail`, which is exactly PeerTrust's own resolution for this request
   * (populated by CallerCertificate from `PeerTrust.Resolution`) — not re-derived from the raw
   * PSD2-CERT header, so this can never disagree with what PeerTrust decided.
   */
  private[util] def callerCertificateForBinding(cc: CallContext): Option[X509Certificate] = {
    val isUnauthenticatedForward =
      cc.certificateTrust.contains("forwarded") &&
        cc.certificateTrustDetail.contains(PeerTrust.UnauthenticatedHopDetail)
    if (isUnauthenticatedForward) None
    else `getPSD2-CERT`(cc.requestHeaders)
      .flatMap(pem => tryo(BerlinGroupSigning.parseCertificate(pem)).toOption)
  }

  /**
   * Props-driven entry point for the OAuth2 login path. Call after the token's signature has
   * been validated.
   */
  def verifyTokenBinding(jwtToken: String, cc: CallContext): Box[Unit] = {
    val mode = configuredMode
    if (mode == Mode.NONE) Full(())
    else {
      verify(
        mode,
        cnfX5tS256(jwtToken),
        callerCertificateForBinding(cc),
        s"url=${cc.url} correlationId=${cc.correlationId}"
      )
    }
  }
}
