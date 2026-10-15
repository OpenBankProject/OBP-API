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

import code.api.util.APIUtil

/**
 * This object says whether webhook deliveries are signed.
 *
 * A delivery is signed with this instance's server key, the one published at GET /obp/v3.1.0/certs,
 * so a receiver can check it came from OBP. That only works when the key is stable: with
 * `jwt.use.ssl=true` it is read from the configured keystore and every node shares it. Otherwise each
 * node generates a key pair of its own at startup, which differs between nodes and changes on every
 * restart, so a receiver could not verify against it; deliveries then go out unsigned and say so.
 */
object DynamicEntityWebhookSigning {
  val Signed = "signed"
  val NotConfigured = "not_configured"

  def isConfigured: Boolean = APIUtil.getPropsAsBoolValue("jwt.use.ssl", false)

  def signatureStatus: String = if (isConfigured) Signed else NotConfigured
}
