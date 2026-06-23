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

  def postAuthed(body: String, segments: String*): APIResponse = makePostRequest(v31(segments: _*).POST <@ (user1), body)
  def postUnauthed(body: String, segments: String*): APIResponse = makePostRequest(v31(segments: _*).POST, body)

  def deleteAuthed(segments: String*): APIResponse = makeDeleteRequest(v31(segments: _*).DELETE <@ (user1))
  def deleteUnauthed(segments: String*): APIResponse = makeDeleteRequest(v31(segments: _*).DELETE)
}
