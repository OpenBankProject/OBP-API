package code.api.util

import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal

import code.util.Helper.MdcLoggable
import net.liftweb.util.Helpers.tryo

/**
 * Who is calling, given the TLS peer and the forwarded certificate header.
 *
 * OBP terminates TLS in some deployments and sits behind a proxy that terminates it in others, and
 * the two look like different problems only while the question is "which deployment is this". The
 * question that unifies them is asked per request:
 *
 *   is my TLS peer the caller, or a forwarder I trust to tell me who the caller is?
 *
 * This is the `X-Forwarded-For` trust model applied to certificates:
 *
 *  - peer is a trusted forwarder  -> the peer is NOT the caller; the forwarded `PSD2-CERT` header
 *    names the caller, and is trustworthy precisely because the forwarder authenticated itself;
 *  - peer is anything else        -> the peer IS the caller; any inbound `PSD2-CERT` is a spoofing
 *    attempt and is discarded.
 *
 * Development-as-edge and production-behind-nginx are then the same code path with a different
 * allowlist, which is the point: see docs/MTLS_TOPOLOGIES.md.
 *
 * Kept deliberately pure and free of http4s so the whole decision table can be tested without a
 * server, a socket or a handshake.
 */
object PeerTrust extends MdcLoggable {

  /**
   * `ForwardedCaller.via` for the one case where the certificate came from a bare PSD2-CERT
   * header with no authenticated hop behind it (`trustForwardedHeaderWithoutTls`). Named so a
   * consumer of `certificateTrustDetail` — notably [[TokenBinding]], which needs cryptographic
   * proof of possession, not merely a claimed header value — can tell this case apart from a
   * cryptographically vouched-for forward from a `mtls.trusted_proxy`.
   */
  val UnauthenticatedHopDetail = "unauthenticated hop"

  /**
   * A peer allowed to speak for someone else.
   *
   * @param issuer  canonical issuer DN — the CA that signed the proxy certificate
   * @param subject canonical subject DN, or None for "any subject this issuer signs"
   */
  case class TrustedProxy(issuer: String, subject: Option[String])

  case class TrustConfig(
    trustedProxies: List[TrustedProxy] = Nil,
    trustForwardedHeaderWithoutTls: Boolean = true
  ) {
    def isForwarder(certificate: X509Certificate): Boolean = {
      val certIssuer = canonicalOf(certificate.getIssuerX500Principal)
      val certSubject = canonicalOf(certificate.getSubjectX500Principal)
      trustedProxies.exists { proxy =>
        proxy.issuer == certIssuer && proxy.subject.forall(_ == certSubject)
      }
    }
  }

  /** The outcome of the question above. */
  sealed trait Resolution {
    /** The certificate identifying the caller, as it should reach the rest of OBP. */
    def callerPem: Option[String]
    /**
     * The trust mode alone — "direct", "forwarded" or "none". This is what the metric row stores
     * (certificate_trust): three values, so per-environment questions like "is everything here
     * resolving as forwarded yet?" are a GROUP BY, not a LIKE over prose.
     */
    def mode: String
    /**
     * The specifics behind the mode — the forwarding proxy's canonical subject DN for "forwarded",
     * the rejection reason for "none", nothing for "direct" (the handshake says it all). Stored as
     * certificate_trust_detail on the metric row.
     */
    def detail: Option[String]
    /** One line for the log: the mode and the detail together. */
    def describe: String
  }

  /** The TLS peer is the caller: OBP is the edge. */
  case class DirectCaller(pem: String) extends Resolution {
    val callerPem: Option[String] = Some(pem)
    val mode: String = "direct"
    val detail: Option[String] = None
    val describe: String = "direct"
  }

  /** A trusted forwarder named the caller. */
  case class ForwardedCaller(pem: String, via: String) extends Resolution {
    val callerPem: Option[String] = Some(pem)
    val mode: String = "forwarded"
    val detail: Option[String] = Some(via)
    val describe: String = s"forwarded via $via"
  }

  /** Nobody is identified by a certificate. Most OBP endpoints do not need one. */
  case class NoCaller(reason: String) extends Resolution {
    val callerPem: Option[String] = None
    val mode: String = "none"
    val detail: Option[String] = Some(reason)
    val describe: String = s"none: $reason"
  }

  private def canonicalOf(principal: X500Principal): String = principal.getName(X500Principal.CANONICAL)

  /**
   * A configured DN in the same canonical form the certificates are compared in, or None when it
   * cannot be parsed as a DN at all.
   *
   * Note that `X500Principal.CANONICAL` normalises case and whitespace but does NOT reorder the
   * RDNs, so a DN whose components are written in a different order than the certificate's will not
   * match. `openssl x509 -noout -issuer -subject -nameopt RFC2253` prints the form to copy.
   */
  def canonicalDn(dn: String): Option[String] = {
    // The empty string is a VALID X.500 name — an empty RDN sequence — so X500Principal accepts it
    // and canonicalises it back to "". Left to that, a blank `mtls.trusted_proxy.N.subject` would
    // become a rule matching any certificate with an empty subject rather than a configuration
    // error. Reject it here.
    if (dn.trim.isEmpty) None
    else tryo(new X500Principal(dn.trim).getName(X500Principal.CANONICAL)).toOption
  }

  /**
   * The rule. Every branch is covered by the decision table in docs/MTLS_TOPOLOGIES.md §3.
   *
   * `forwarded` is the inbound `PSD2-CERT` value, already canonicalised by Psd2CertIngress. It is
   * passed through as a string rather than a parsed certificate on purpose: an unparseable value
   * must still reach the authorisation layer, which owns the error code. Deciding who the caller is
   * and deciding whether their certificate is any good are different jobs.
   */
  def resolve(peer: Option[X509Certificate], forwarded: Option[String], config: TrustConfig): Resolution =
    peer match {
      case Some(peerCertificate) if config.isForwarder(peerCertificate) =>
        val via = canonicalOf(peerCertificate.getSubjectX500Principal)
        forwarded match {
          case Some(pem) => ForwardedCaller(pem, via)
          // Legitimate: most endpoints need no certificate, and the proxy forwards what it got.
          case None => NoCaller(s"trusted forwarder $via sent no certificate")
        }

      case Some(peerCertificate) =>
        // The peer authenticated as itself, so it is the caller. Any inbound header is discarded:
        // when we are the edge, a forwarded certificate can only be a spoofing attempt.
        if (forwarded.isDefined) {
          logger.debug("Discarding an inbound PSD2-CERT header: the TLS peer is not a trusted forwarder, " +
            s"so it is the caller (subject ${canonicalOf(peerCertificate.getSubjectX500Principal)})")
        }
        DirectCaller(CertificateUtil.toPem(peerCertificate))

      case None =>
        forwarded match {
          case Some(pem) if config.trustForwardedHeaderWithoutTls =>
            ForwardedCaller(pem, UnauthenticatedHopDetail)
          case Some(_) =>
            // Fail closed: with no authenticated peer there is nothing to make the header credible.
            NoCaller("PSD2-CERT was sent over a hop with no client certificate, and " +
              "mtls.trust_forwarded_header_without_tls is false")
          case None =>
            NoCaller("no TLS client certificate and no PSD2-CERT header")
        }
    }

  private val TrustForwardedWithoutTlsProp = "mtls.trust_forwarded_header_without_tls"

  /**
   * The configured trust, read once.
   *
   * Proxies are indexed pairs — `mtls.trusted_proxy.1.issuer` / `.subject` — rather than one
   * delimited list, because a DN contains commas and any comma-separated list of DNs is ambiguous
   * the first time somebody writes a real one. `subject=*` accepts any subject that issuer signs.
   */
  lazy val config: TrustConfig = fromProps()

  def fromProps(): TrustConfig = {
    def prop(name: String): Option[String] =
      APIUtil.getPropsValue(name).toOption.map(_.trim).filter(_.nonEmpty)

    val proxies = Stream.from(1)
      .map(i => (i, prop(s"mtls.trusted_proxy.$i.issuer")))
      .takeWhile(_._2.isDefined)
      .flatMap { case (i, issuerProp) =>
        val issuer = issuerProp.get
        canonicalDn(issuer) match {
          case None =>
            // Fail closed: an unparseable DN matches nothing, so this proxy simply is not trusted.
            logger.error(s"mtls.trusted_proxy.$i.issuer is not a valid X.500 distinguished name and " +
              s"will never match: '$issuer'. Print the value to use with " +
              s"'openssl x509 -in proxy.crt -noout -issuer -nameopt RFC2253'.")
            None
          case Some(canonicalIssuer) =>
            prop(s"mtls.trusted_proxy.$i.subject") match {
              case None | Some("*") =>
                logger.warn(s"mtls.trusted_proxy.$i trusts ANY certificate issued by '$issuer' to act " +
                  s"as a forwarder. Set mtls.trusted_proxy.$i.subject to pin a specific proxy unless " +
                  s"that CA signs nothing else.")
                Some(TrustedProxy(canonicalIssuer, None))
              case Some(subject) =>
                canonicalDn(subject) match {
                  case None =>
                    logger.error(s"mtls.trusted_proxy.$i.subject is not a valid X.500 distinguished name " +
                      s"and will never match: '$subject'.")
                    None
                  case Some(canonicalSubject) => Some(TrustedProxy(canonicalIssuer, Some(canonicalSubject)))
                }
            }
        }
      }.toList

    val trustWithoutTlsProp = APIUtil.getPropsAsBoolValue(TrustForwardedWithoutTlsProp, defaultValue = true)
    val mtlsEnabled = APIUtil.getPropsAsBoolValue("mtls.enabled", defaultValue = false)
    val trustWithoutTls = effectiveTrustWithoutTls(trustWithoutTlsProp, proxies.isEmpty, mtlsEnabled)

    if (proxies.isEmpty) {
      logger.info("No mtls.trusted_proxy.N.issuer configured: OBP treats its TLS peer as the caller " +
        "(it is the edge). Configure trusted proxies if a reverse proxy forwards PSD2-CERT.")
    } else {
      logger.info(s"Trusted forwarders: ${proxies.map(p => p.subject.getOrElse("<any subject>") + " issued by " + p.issuer).mkString("; ")}")
    }
    if (trustWithoutTlsProp && !trustWithoutTls) {
      logger.info(s"$TrustForwardedWithoutTlsProp=true is ignored: mtls.enabled=true with no trusted " +
        "proxies makes OBP the TLS edge, so a PSD2-CERT header from a peer that presented no client " +
        "certificate can only be a spoofing attempt and is stripped.")
    }
    if (trustWithoutTls) {
      // Deliberately noisy: the default is the permissive setting, chosen so that existing
      // deployments upgrade unchanged. It should not be able to sit there unnoticed.
      logger.warn(s"$TrustForwardedWithoutTlsProp is true: a PSD2-CERT header is trusted even when the " +
        "sender presented no client certificate, so anything that can reach this port can claim any " +
        "TPP identity. Set it to false once the proxy authenticates itself over mTLS.")
    }
    TrustConfig(proxies, trustWithoutTls)
  }

  /**
   * Whether a `PSD2-CERT` header on a request with no TLS client certificate may name the caller.
   *
   * The prop exists for the plain-proxy-hop deployment, where OBP terminates no TLS and the header
   * is all there is. But when OBP terminates mTLS itself AND no trusted forwarder is configured,
   * OBP is provably the edge: every legitimate caller identity arrives in the handshake, so a
   * header on a certless request (possible under `mtls.client_auth=want`) can only be a spoofing
   * attempt. The pre-generalisation middleware (`Http4sMtls.injectClientCertificate`) always
   * stripped it in that deployment; honouring the prop's permissive default there would silently
   * re-open the hole the old code closed, so the prop is ignored for that one shape.
   */
  private[util] def effectiveTrustWithoutTls(propValue: Boolean, noProxiesConfigured: Boolean, mtlsEnabled: Boolean): Boolean =
    propValue && !(mtlsEnabled && noProxiesConfigured)
}
