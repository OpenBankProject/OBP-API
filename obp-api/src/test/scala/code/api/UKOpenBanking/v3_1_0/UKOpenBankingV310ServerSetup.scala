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

package code.api.UKOpenBanking.v3_1_0

import code.api.util.APIUtil.OAuth._
import code.setup.{APIResponse, DefaultUsers, ServerSetupWithTestData}
import code.setup.OBPReq

/**
 * Shared setup + request helpers for the UK Open Banking v3.1 test suites.
 *
 * Base path is `/open-banking/v3.1` (derived from
 * ScannedApiVersion("open-banking","UK","v3.1")). The `*Authed` helpers attach
 * the OAuth credentials of `user1`; the `*Unauthed` helpers send no credentials
 * so the endpoint's `authenticatedAccess` (or `applicationAccess`) check fires.
 */
trait UKOpenBankingV310ServerSetup extends ServerSetupWithTestData with DefaultUsers {

  def v31Request: OBPReq = baseRequest / "open-banking" / "v3.1"

  // Build a request from path segments, e.g. v31("accounts", accountId, "balances").
  def v31(segments: String*): OBPReq = segments.foldLeft(v31Request)((req, s) => req / s)

  def getAuthed(segments: String*): APIResponse = makeGetRequest(v31(segments: _*).GET <@ (user1))
  def getUnauthed(segments: String*): APIResponse = makeGetRequest(v31(segments: _*).GET)

  // For IDOR regression tests: user2 authenticated (a different user AND a different OAuth1
  // consumer than user1, see DefaultUsers), acting on a resource (e.g. a consent) that belongs
  // to a different user/consumer.
  def getAuthedAsUser2(segments: String*): APIResponse = makeGetRequest(v31(segments: _*).GET <@ (user2))

  def postAuthed(body: String, segments: String*): APIResponse = makePostRequest(v31(segments: _*).POST <@ (user1), body)
  def postUnauthed(body: String, segments: String*): APIResponse = makePostRequest(v31(segments: _*).POST, body)

  def deleteAuthed(segments: String*): APIResponse = makeDeleteRequest(v31(segments: _*).DELETE <@ (user1))
  def deleteUnauthed(segments: String*): APIResponse = makeDeleteRequest(v31(segments: _*).DELETE)

  def deleteAuthedAsUser2(segments: String*): APIResponse = makeDeleteRequest(v31(segments: _*).DELETE <@ (user2))
}
