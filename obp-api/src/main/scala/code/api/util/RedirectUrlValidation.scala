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

package code.api.util

import java.net.URI

import code.api.util.ErrorMessages.{InvalidRedirectUrl, RedirectUrlHostNotAllowed}
import code.util.Helper
import net.liftweb.common.Box

import scala.concurrent.Future
import scala.util.Try

/**
 * This object decides whether a redirect URL may be stored on a Consumer, or used as a Berlin Group
 * TPP redirect.
 *
 * An OIDC provider (OBP-OIDC) sends the browser, together with an authorisation code, to whichever
 * redirect URL is registered on the Consumer. A registered `javascript:` URL runs script on the
 * provider's page, and a URL with a wildcard, user information or a plain-http public host hands the
 * code to whoever controls that address. So every redirect URL is checked when it is written, not only
 * when it is used.
 *
 * A Consumer's redirect URL field can hold several URLs separated by commas or whitespace, the same way
 * OBP-OIDC and `getOidcClient` split it. Every entry must pass. The rules are:
 *
 *  - `https:` with a host;
 *  - `http:` only for `localhost`, `127.0.0.1` or `[::1]`, for development;
 *  - a private-use app scheme in reverse-domain form (RFC 8252), such as `com.example.app:/callback`;
 *  - never a wildcard (`*`), user information (`user@host`) or a fragment (`#...`).
 *
 * An empty value is allowed: Consumers that only use DirectLogin or client credentials have no redirect.
 *
 * An instance can also limit which hosts a Consumer's redirect URL may point to, with the optional prop
 * `redirect_url_allowed_hosts`. Empty (the default) puts no limit on hosts. When it is set, every https or
 * http entry must name a listed host, where `portal.example.com` matches that host exactly and
 * `.example.com` matches example.com and any host under it; the API's own host is always included. App
 * schemes have no host to check and are not affected. The host list is checked when an endpoint writes a
 * Consumer's redirect URL. It is not applied to Berlin Group dynamic registration or TPP redirect headers,
 * which by definition belong to third parties, nor to redirect URLs already stored.
 */
object RedirectUrlValidation {

  private val loopbackHosts = Set("localhost", "127.0.0.1", "[::1]")

  // A reverse-domain scheme: at least two dot-separated labels, e.g. com.example.app or x-com.example.ios.
  private val reverseDomainScheme = """^[a-z][a-z0-9+-]*(\.[a-z0-9+-]+)+$""".r

  /**
   * The hosts a Consumer redirect URL may point to on this instance, or Nil when there is no limit.
   * This is a def, not a val, so a change to the prop (in tests, or after a restart) is always seen.
   */
  def allowedHosts: List[String] = {
    val listed = APIUtil.getPropsValue("redirect_url_allowed_hosts", "")
      .split("[,\\s]+").map(_.trim.toLowerCase).filter(_.nonEmpty).toList
    if (listed.isEmpty) Nil
    else {
      val ownHost = APIUtil.getPropsValue("hostname").toOption
        .flatMap(hostname => Try(new URI(hostname)).toOption.flatMap(uri => Option(uri.getHost)))
        .map(_.toLowerCase)
      (listed ++ ownHost).distinct
    }
  }

  private def hostIsAllowed(host: String, allowed: List[String]): Boolean = {
    val lowerCaseHost = host.toLowerCase
    allowed.exists { allowedHost =>
      if (allowedHost.startsWith(".")) lowerCaseHost == allowedHost.drop(1) || lowerCaseHost.endsWith(allowedHost)
      else lowerCaseHost == allowedHost
    }
  }

  /**
   * Returns why an entry that already meets the redirect URL rules points to a host this instance does not
   * allow, or None when there is no host list, the host is listed, or the entry is an app scheme.
   */
  def hostProblemWith(entry: String): Option[String] = allowedHosts match {
    case Nil => None
    case allowed =>
      Try(new URI(entry)).toOption
        .filter(uri => Option(uri.getScheme).map(_.toLowerCase).exists(scheme => scheme == "https" || scheme == "http"))
        .flatMap(uri => Option(uri.getHost))
        .filterNot(hostIsAllowed(_, allowed))
        .map(host => s"the host '$host' is not one of the hosts this instance allows: ${allowed.mkString(", ")}")
  }

  /** One sentence for the Glossary and the boot log saying whether this instance limits redirect URL hosts. */
  def allowedHostsDescription: String = allowedHosts match {
    case Nil => "On this instance a redirect URL may point to any host that meets the redirect URL rules."
    case allowed => s"On this instance a redirect URL must point to one of these hosts: ${allowed.mkString(", ")}."
  }

  /** Splits a stored redirect URL value into its entries, the same way OBP-OIDC does. */
  def entries(redirectUrls: String): List[String] =
    Option(redirectUrls).toList.flatMap(_.split("[,\\s]+").toList).map(_.trim).filter(_.nonEmpty)

  /** Returns why one redirect URL entry is not allowed, or None when it is allowed. */
  def problemWith(entry: String): Option[String] = {
    if (entry.contains("*")) Some("a wildcard is not allowed")
    else Try(new URI(entry)).toOption match {
      case None => Some("it is not a valid URI")
      case Some(uri) if uri.getScheme == null => Some("it has no scheme")
      case Some(uri) if uri.getRawFragment != null => Some("a fragment is not allowed")
      case Some(uri) if uri.getRawUserInfo != null => Some("user information is not allowed")
      case Some(uri) =>
        uri.getScheme.toLowerCase match {
          case "https" =>
            if (Option(uri.getHost).exists(_.nonEmpty)) None
            else Some("an https URL must name a host")
          case "http" =>
            if (Option(uri.getHost).map(_.toLowerCase).exists(loopbackHosts.contains)) None
            else Some("http is only allowed for localhost, 127.0.0.1 or [::1]; use https")
          case scheme if reverseDomainScheme.pattern.matcher(scheme).matches() =>
            None
          case scheme =>
            Some(s"the scheme '$scheme' is not allowed; use https, or an app scheme in reverse-domain form such as com.example.app")
        }
    }
  }

  /** Describes the first entry that is not allowed, or None when every entry is allowed. */
  def firstProblem(redirectUrls: String): Option[String] =
    entries(redirectUrls).iterator
      .map(entry => problemWith(entry).map(reason => s"'$entry': $reason"))
      .collectFirst { case Some(description) => description }

  def isValid(redirectUrls: String): Boolean = firstProblem(redirectUrls).isEmpty

  /**
   * The full error message for the first entry of a Consumer redirect URL value that is not allowed, or None
   * when every entry is allowed. An entry that breaks the rules gives InvalidRedirectUrl. With
   * `applyHostList`, an entry whose host is not on this instance's list gives RedirectUrlHostNotAllowed.
   */
  def consumerRedirectUrlError(redirectUrls: String, applyHostList: Boolean): Option[String] =
    entries(redirectUrls).iterator.map { entry =>
      problemWith(entry).map(reason => s"$InvalidRedirectUrl'$entry': $reason")
        .orElse(if (applyHostList) hostProblemWith(entry).map(reason => s"$RedirectUrlHostNotAllowed'$entry': $reason") else None)
    }.collectFirst { case Some(message) => message }

  /** The entries that meet the rules, for readers that must skip invalid stored data. */
  def validEntries(redirectUrls: String): List[String] = entries(redirectUrls).filter(problemWith(_).isEmpty)

  /**
   * Fails with 400, naming the offending entry, when any entry of a Consumer redirect URL value is not allowed.
   * `applyHostList` is false only for Berlin Group dynamic registration, whose TPPs use their own domains.
   */
  def checkRedirectUrls(redirectUrls: String, callContext: Option[CallContext], applyHostList: Boolean = true): Future[Box[Unit]] = {
    val error = consumerRedirectUrlError(redirectUrls, applyHostList)
    Helper.booleanToFuture(error.getOrElse(""), 400, callContext) { error.isEmpty }
  }
}
