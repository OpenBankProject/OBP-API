/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)
*/
package code.api.util

import code.util.Helper.MdcLoggable

/** Single source of truth for resolving the trusted client IP, used by both Lift and http4s.
 *
 *  Defaults to the immediate socket peer — i.e. no proxy trust, behaving safely when OBP is
 *  reachable directly. To pick up the real client IP from a reverse proxy:
 *
 *    trust.proxy.enabled = true
 *    trust.proxy.header  = X-Real-IP        # default; or "X-Forwarded-For"
 *    trust.proxy.peers   = 10.0.0.5, 172.16.0.0/12   # optional; see below
 *
 *  The proxy MUST overwrite the configured header so clients cannot spoof it. Example NGINX:
 *
 *    proxy_set_header X-Real-IP $remote_addr;
 *
 *  `X-Forwarded-For` carries a chain: each hop (NGINX, a server-side application such as
 *  API Explorer II, Opey, OBP-MCP) appends the address it received the request from, so the
 *  chain reads "client, first hop, second hop, ...". Anyone can write anything at the left
 *  end, so the chain is read from the right: skip every address in `trust.proxy.peers` and
 *  the first address that is not trusted is the client. A caller that is not trusted cannot
 *  name a false address, because it becomes the client itself. This needs every hop listed
 *  in `trust.proxy.peers`; with the list unset every address counts as trusted and the
 *  leftmost address is used, which is only safe when the outermost proxy replaces the chain.
 *
 *  `trust.proxy.peers` closes a gap: without it, the header is believed from whoever sent the
 *  request, so a caller that can reach OBP-API directly (bypassing the proxy) can name any
 *  address it likes, to slip past per-IP limits or to get someone else penalised. With it, the
 *  header is believed only when the TCP peer is one of the listed addresses or CIDR ranges;
 *  from any other peer it is ignored and the peer itself is the client. Unset, the behaviour is
 *  unchanged (the header is believed from anyone), and Deployment Checks reports it.
 *
 *  Addresses are returned in canonical form, without the brackets http4s puts around IPv6.
 */
object RemoteIpUtil extends MdcLoggable {

  /** How a request's client address was decided, for Deployment Checks. */
  final case class Resolution(
    clientIp: String,
    socketPeer: String,
    /** The configured header (or any forwarding header when trust is off) was present. */
    forwardingHeaderPresent: Boolean,
    /** The header's value became the client address. */
    headerHonoured: Boolean,
    /** The header was present but ignored, because its sender is not in trust.proxy.peers. */
    headerFromUntrustedPeer: Boolean
  )

  private val ForwardingHeaders = List("X-Real-IP", "X-Forwarded-For")

  /** Resolve the trusted client IP.
   *  @param socketPeer the immediate TCP peer's address (proxy IP, or real client if direct)
   *  @param getHeader  function to read a request header by name (case-insensitive); returns
   *                    the raw header value if present
   *  @return the trusted client IP — either the parsed header value or `socketPeer` as fallback
   */
  def resolveClientIp(socketPeer: String, getHeader: String => Option[String]): String =
    resolve(socketPeer, getHeader).clientIp

  /** Like [[resolveClientIp]], with the details of the decision. */
  def resolve(socketPeer: String, getHeader: String => Option[String]): Resolution = {
    val peer = canonical(socketPeer)
    if (!APIUtil.getPropsAsBoolValue("trust.proxy.enabled", false)) {
      Resolution(peer, peer, ForwardingHeaders.exists(h => getHeader(h).exists(_.trim.nonEmpty)), headerHonoured = false, headerFromUntrustedPeer = false)
    } else {
      val headerName = APIUtil.getPropsValue("trust.proxy.header", "X-Real-IP")
      val fromHeader = getHeader(headerName).flatMap(raw => extractClientIp(headerName, raw))
      fromHeader match {
        case None => Resolution(peer, peer, forwardingHeaderPresent = false, headerHonoured = false, headerFromUntrustedPeer = false)
        case Some(_) if !peerIsTrusted(peer) => Resolution(peer, peer, forwardingHeaderPresent = true, headerHonoured = false, headerFromUntrustedPeer = true)
        case Some(client) => Resolution(client, peer, forwardingHeaderPresent = true, headerHonoured = true, headerFromUntrustedPeer = false)
      }
    }
  }

  /** The hops a request passed through, for API Metrics: the X-Forwarded-For chain it arrived
   *  with (all header lines, comma-joined in order) followed by the TCP peer, so the last hop is
   *  recorded too. This is a record of what arrived, not a decision: entries left of the first
   *  address that is not trusted may have been written by the client. The client address itself
   *  comes from [[resolve]]. */
  def forwardedForPath(socketPeer: String, forwardedForHeader: Option[String]): String = {
    val peer = canonical(socketPeer)
    val incoming = forwardedForHeader.map(_.trim).filter(_.nonEmpty)
    (incoming.toList ++ List(peer).filter(_.nonEmpty)).mkString(", ")
  }

  /** The configured trusted peers, as parsed CIDR ranges (a single address is a /32 or /128). */
  def trustedPeers: List[(Array[Byte], Int)] =
    APIUtil.getPropsValue("trust.proxy.peers").toList
      .flatMap(_.split(",").map(_.trim).filter(_.nonEmpty))
      .flatMap(parseCidr)

  /** True when trust.proxy.peers is unset (anyone is believed) or the peer is in it. */
  def peerIsTrusted(peer: String): Boolean = {
    val peers = trustedPeers
    peers.isEmpty || addressBytes(peer).exists(bytes => peers.exists { case (net, bits) => inRange(bytes, net, bits) })
  }

  private def parseCidr(value: String): Option[(Array[Byte], Int)] = {
    val (address, bits) = value.split("/", 2) match {
      case Array(a, b) => (a, scala.util.Try(b.trim.toInt).toOption)
      case Array(a) => (a, None)
    }
    addressBytes(address).flatMap { bytes =>
      val size = bytes.length * 8
      val prefix = bits.getOrElse(size)
      if (prefix < 0 || prefix > size) { logger.warn(s"RemoteIpUtil says: ignoring invalid trust.proxy.peers entry $value"); None }
      else Some((bytes, prefix))
    }
  }

  private def addressBytes(value: String): Option[Array[Byte]] = {
    val v = canonical(value)
    if (com.google.common.net.InetAddresses.isInetAddress(v)) Some(com.google.common.net.InetAddresses.forString(v).getAddress) else None
  }

  private def inRange(address: Array[Byte], network: Array[Byte], prefix: Int): Boolean =
    address.length == network.length && (0 until prefix).forall { bit =>
      val mask = 0x80 >> (bit % 8)
      (address(bit / 8) & mask) == (network(bit / 8) & mask)
    }

  /** An address without IPv6 brackets, in canonical form when it is a literal address. */
  def canonical(value: String): String = {
    val unbracketed = Option(value).map(_.trim.stripPrefix("[").stripSuffix("]")).getOrElse("")
    if (com.google.common.net.InetAddresses.isInetAddress(unbracketed))
      com.google.common.net.InetAddresses.toAddrString(com.google.common.net.InetAddresses.forString(unbracketed))
    else unbracketed
  }

  /** The client address a forwarding header names, in canonical form.
   *  A single-value header (X-Real-IP) yields its value. If the request carried it more than
   *  once, the values arrive comma-joined and the first is used, as before.
   *  X-Forwarded-For yields the client found by [[clientFromForwardedFor]]. */
  private def extractClientIp(headerName: String, raw: String): Option[String] =
    if (headerName.equalsIgnoreCase("X-Forwarded-For")) clientFromForwardedFor(raw)
    else Option(raw.split(",").headOption.getOrElse("").trim).filter(_.nonEmpty).map(canonical)

  /** The client named by an X-Forwarded-For chain, read from the right.
   *
   *  Each hop appends the address it received the request from, so the rightmost entry was
   *  written by the TCP peer (already checked to be trusted), the next one by the hop before
   *  it, and so on. Walking leftwards, every address in trust.proxy.peers is a hop that can
   *  be believed about the entry to its left; the first address that is not in the list is
   *  the client. Entries further left were written by the client or by hops nobody vouches
   *  for, and are ignored.
   *
   *  An entry that is not an address (for example "unknown") stops the walk: nothing to its
   *  left can be believed, so the nearest trusted address to its right is the client, or
   *  None (meaning the TCP peer) when it is the rightmost entry.
   *
   *  When every entry is trusted, including when trust.proxy.peers is unset, the leftmost
   *  entry is the client. */
  private[util] def clientFromForwardedFor(raw: String): Option[String] = {
    val chainFromTheRight = raw.split(",").map(entry => canonical(entry)).filter(_.nonEmpty).toList.reverse
    val firstUntrustedIndex = chainFromTheRight.indexWhere(address => !peerIsTrusted(address))
    if (firstUntrustedIndex < 0) chainFromTheRight.lastOption
    else {
      val firstUntrusted = chainFromTheRight(firstUntrustedIndex)
      if (addressBytes(firstUntrusted).isDefined) Some(firstUntrusted)
      else if (firstUntrustedIndex == 0) None
      else Some(chainFromTheRight(firstUntrustedIndex - 1))
    }
  }
}
