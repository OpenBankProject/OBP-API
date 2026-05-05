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
Osloerstrasse 16/17
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
 *
 *  The proxy MUST overwrite the configured header so clients cannot spoof it. Example NGINX:
 *
 *    proxy_set_header X-Real-IP $remote_addr;
 *
 *  For `X-Forwarded-For`, the leftmost value is treated as the client. This is only
 *  trustworthy when the proxy is configured with `set_real_ip_from` + `real_ip_recursive`
 *  so it sanitises the forwarded chain before forwarding upstream. `X-Real-IP` is the
 *  simpler choice for single-proxy deployments.
 */
object RemoteIpUtil extends MdcLoggable {

  /** Resolve the trusted client IP.
   *  @param socketPeer the immediate TCP peer's address (proxy IP, or real client if direct)
   *  @param getHeader  function to read a request header by name (case-insensitive); returns
   *                    the raw header value if present
   *  @return the trusted client IP — either the parsed header value or `socketPeer` as fallback
   */
  def resolveClientIp(socketPeer: String, getHeader: String => Option[String]): String = {
    if (!APIUtil.getPropsAsBoolValue("trust.proxy.enabled", false)) {
      socketPeer
    } else {
      val headerName = APIUtil.getPropsValue("trust.proxy.header", "X-Real-IP")
      getHeader(headerName)
        .flatMap(raw => extractClientIp(headerName, raw))
        .getOrElse(socketPeer)
    }
  }

  /** Single-value headers (X-Real-IP) yield the value as-is.
   *  X-Forwarded-For is comma-separated; the leftmost entry is the original client. */
  private def extractClientIp(headerName: String, raw: String): Option[String] = {
    val candidate =
      if (headerName.equalsIgnoreCase("X-Forwarded-For"))
        raw.split(",").headOption.getOrElse("")
      else
        raw
    val trimmed = candidate.trim
    if (trimmed.isEmpty) None else Some(trimmed)
  }
}
