/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

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
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.dynamicentitywebhook

import java.net.{InetAddress, URI}

import code.api.util.APIUtil

import scala.util.Try

/**
 * This object decides whether OBP may send a webhook request to a URL.
 *
 * A webhook makes OBP send requests to an address someone else chose. Without a check, that address
 * could be a service inside OBP's own network, which outsiders cannot otherwise reach (this is called
 * Server-Side Request Forgery), or OBP itself, where the webhook's request could write a record that
 * fires the webhook again. So a URL is refused when it is not HTTPS, when its host resolves to a
 * loopback, private, link-local (which includes the cloud metadata address 169.254.169.254) or
 * unique local address, or when it names this instance's own host and port.
 *
 * The check runs when a webhook is created or changed, and again before each delivery, because what
 * a host name resolves to can change after it was registered. A host name that does not resolve is
 * accepted at registration, since the delivery check refuses it if it later resolves somewhere
 * forbidden.
 */
object DynamicEntityWebhookUrlCheck {

  val MaxUrlLength = 1024

  /** Plain http is for local development only. */
  def allowHttp: Boolean = APIUtil.getPropsAsBoolValue("dynamic_entity_webhook.allow_http", false)

  /** Private and loopback addresses are for local development and tests only. */
  def allowPrivateAddresses: Boolean = APIUtil.getPropsAsBoolValue("dynamic_entity_webhook.allow_private_addresses", false)

  private def effectivePort(uri: URI): Int =
    if (uri.getPort != -1) uri.getPort
    else if ("https".equalsIgnoreCase(uri.getScheme)) 443
    else 80

  /** This instance's own scheme, host and port, from the hostname prop. */
  private lazy val ownUri: Option[URI] =
    APIUtil.getPropsValue("hostname").toOption.flatMap(h => Try(new URI(h.trim)).toOption).filter(_.getHost != null)

  private lazy val ownAddresses: Set[InetAddress] =
    ownUri.toList.flatMap(u => Try(InetAddress.getAllByName(u.getHost).toList).getOrElse(Nil)).toSet

  private def isForbiddenAddress(address: InetAddress): Boolean =
    address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress ||
      address.isAnyLocalAddress || address.isMulticastAddress ||
      (address.getAddress.length == 16 && (address.getAddress()(0) & 0xfe) == 0xfc) // IPv6 unique local, fc00::/7

  /**
   * This returns why OBP must not send requests to `url`, or None when it may.
   *
   * `resolve` looks a host name up; it is a parameter so that tests can say what a name resolves to.
   */
  def problem(url: String, resolve: String => List[InetAddress] = host => Try(InetAddress.getAllByName(host).toList).getOrElse(Nil)): Option[String] = {
    val trimmed = Option(url).map(_.trim).getOrElse("")
    Try(new URI(trimmed)).toOption match {
      case _ if trimmed.isEmpty => Some("The url is empty.")
      case _ if trimmed.length > MaxUrlLength => Some(s"The url is longer than $MaxUrlLength characters.")
      case None => Some("The url cannot be parsed.")
      case Some(uri) if !uri.isAbsolute || uri.getHost == null || uri.getHost.isEmpty => Some("The url must be absolute and name a host.")
      case Some(uri) if uri.getUserInfo != null => Some("The url must not carry a user name or password.")
      case Some(uri) if !("https".equalsIgnoreCase(uri.getScheme) || (allowHttp && "http".equalsIgnoreCase(uri.getScheme))) =>
        Some(if (allowHttp) "The url must use https or http." else "The url must use https.")
      case Some(uri) =>
        val host = uri.getHost.stripPrefix("[").stripSuffix("]")
        val port = effectivePort(uri)
        val addresses = resolve(host)
        val isThisInstance = ownUri.exists { own =>
          effectivePort(own) == port &&
            (own.getHost.equalsIgnoreCase(host) || addresses.exists(ownAddresses.contains))
        }
        if (isThisInstance) Some("The url points at this OBP instance.")
        else if (!allowPrivateAddresses && addresses.exists(isForbiddenAddress))
          Some(s"The url's host $host resolves to a loopback, private, link-local or unique local address.")
        else None
    }
  }
}
