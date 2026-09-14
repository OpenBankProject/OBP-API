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

package code.chat

import code.api.util.APIUtil

import java.net.URI
import scala.util.Try

/**
 * Link-host policy for chat message content.
 *
 * Chat messages may contain URLs; to blunt phishing, message content is only
 * accepted when every http(s) URL in it points at an allowed host. Allowed
 * hosts are this instance's own host (from the `hostname` prop) plus the
 * entries of the `chat.allowed_link_hosts` prop (comma-separated hostnames or
 * URLs, e.g. "portal.example.com,github.com"). A URL's host passes when it
 * equals an allowed host or is a subdomain of one — substring matching is
 * deliberately avoided ("openbankproject.com.evil.example" must not pass).
 *
 * The Portal enforces the same policy at render time (links to other hosts
 * render as inert text); this server-side check keeps other API clients from
 * storing messages the Portal would refuse to linkify.
 */
object ChatLinkPolicy {

  private val UrlPattern = "(?i)\\bhttps?://[^\\s<>\"']+".r

  /** Used only when chat.allowed_link_hosts is not defined; a configured list replaces these. */
  private val DefaultAllowedHosts = Set("tesobe.com", "openbankproject.com")

  def allowedHosts: Set[String] = {
    val ownHost = APIUtil.getPropsValue("hostname").toList.flatMap(hostOf)
    // Every app this instance advertises in its App Directory (public_*_url
    // props) is part of the ecosystem, so links between them always pass.
    val appDirectoryHosts = APIUtil.getAppDiscoveryPairs.flatMap { case (_, url) => hostOf(url) }
    val extraHosts = APIUtil.getPropsValue("chat.allowed_link_hosts") match {
      case net.liftweb.common.Full(value) => value.split(",").toList.flatMap(hostOf).toSet
      case _ => DefaultAllowedHosts
    }
    ownHost.toSet ++ appDirectoryHosts.toSet ++ extraHosts
  }

  /**
   * Hosts of the http(s) URLs in `content` that the policy does not allow.
   * Empty result means the content passes. Unparseable URLs fail closed.
   */
  def disallowedLinkHosts(content: String): List[String] = {
    val allowed = allowedHosts
    // localhost, *.localhost, 127.0.0.1 and ::1 are the same place; if any
    // loopback form is allowed (dev instances), all loopback forms pass.
    val loopbackAllowed = allowed.exists(isLoopback)
    UrlPattern.findAllIn(content).toList
      .map(url => hostOf(url).getOrElse("(unparseable URL)"))
      .distinct
      .filterNot(host =>
        allowed.exists(a => host == a || host.endsWith("." + a)) ||
        (loopbackAllowed && isLoopback(host)))
  }

  private def isLoopback(host: String): Boolean =
    host == "localhost" || host == "127.0.0.1" || host == "::1" || host.endsWith(".localhost")

  /** Lowercase host of a URL or bare hostname; None when it cannot be determined. */
  private def hostOf(entry: String): Option[String] = {
    // strip punctuation that commonly trails a URL in prose/markdown
    val trimmed = entry.trim.replaceAll("[),.;:!?'\"\\]]+$", "")
    if (trimmed.isEmpty) None
    else if (trimmed.contains("://"))
      Try(Option(new URI(trimmed).getHost)).toOption.flatten.map(_.toLowerCase)
    else if (trimmed.matches("[A-Za-z0-9.-]+")) Some(trimmed.toLowerCase)
    else None
  }
}
