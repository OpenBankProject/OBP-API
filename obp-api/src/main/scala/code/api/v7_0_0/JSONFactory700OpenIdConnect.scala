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
package code.api.v7_0_0

/*
 * The JSON of the v7.0.0 endpoints an OIDC provider (such as OBP-OIDC) calls on OBP-API.
 *
 * Declared at package level, in a file of their own, for the reason given in JSONFactory700Operations.scala.
 */

/**
 * This class is what an OIDC provider reads to finish a consent flow: the Consent's status, the Consent Request
 * it came from, the Consumer it belongs to (as both consumer_id and client_id, the Consumer Key), and the User who
 * gave it. The provider takes these from OBP-API's record instead of from the browser's callback URL.
 */
case class OpenIdConnectConsentJsonV700(
  consent_id: String,
  status: String,
  consent_request_id: Option[String],
  consumer_id: String,
  client_id: Option[String],
  user_id: String,
  username: String,
  provider: String
)
