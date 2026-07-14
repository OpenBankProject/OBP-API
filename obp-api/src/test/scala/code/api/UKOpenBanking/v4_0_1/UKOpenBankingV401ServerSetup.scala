package code.api.UKOpenBanking.v4_0_1

import code.api.util.APIUtil.OAuth._
import code.setup.{APIResponse, DefaultUsers, ServerSetupWithTestData}
import code.setup.OBPReq

/**
 * Shared setup + request helpers for the UK Open Banking v4.0.1 test suites.
 *
 * Mirrors UKOpenBankingV310ServerSetup. Base path is `/open-banking/v4.0.1`
 * (derived from ScannedApiVersion("open-banking","UK","v4.0.1")). The `*Authed`
 * helpers attach the DirectLogin credentials of `user1`; the `*Unauthed` helpers
 * send none so the endpoint's ResourceDocMiddleware auth check (every UK v4.0.1
 * endpoint declares AuthenticatedUserIsRequired) fires with a 401.
 *
 * Adds put/patch helpers on top of v3.1's get/post/delete because v4.0.1 has
 * PUT + PATCH endpoints (VRP/payment updates).
 */
trait UKOpenBankingV401ServerSetup extends ServerSetupWithTestData with DefaultUsers {

  def v401Request: OBPReq = baseRequest / "open-banking" / "v4.0.1"

  // Build a request from path segments, e.g. v401("aisp", "accounts", accountId, "balances").
  def v401(segments: String*): OBPReq = segments.foldLeft(v401Request)((req, s) => req / s)

  def getAuthed(segments: String*): APIResponse = makeGetRequest(v401(segments: _*).GET <@ (user1))
  def getUnauthed(segments: String*): APIResponse = makeGetRequest(v401(segments: _*).GET)

  def postAuthed(body: String, segments: String*): APIResponse = makePostRequest(v401(segments: _*).POST <@ (user1), body)
  def postUnauthed(body: String, segments: String*): APIResponse = makePostRequest(v401(segments: _*).POST, body)

  def putAuthed(body: String, segments: String*): APIResponse = makePutRequest(v401(segments: _*).PUT <@ (user1), body)
  def putUnauthed(body: String, segments: String*): APIResponse = makePutRequest(v401(segments: _*).PUT, body)

  def patchAuthed(body: String, segments: String*): APIResponse = makePatchRequest(v401(segments: _*).PATCH <@ (user1), body)
  def patchUnauthed(body: String, segments: String*): APIResponse = makePatchRequest(v401(segments: _*).PATCH, body)

  def deleteAuthed(segments: String*): APIResponse = makeDeleteRequest(v401(segments: _*).DELETE <@ (user1))
  def deleteUnauthed(segments: String*): APIResponse = makeDeleteRequest(v401(segments: _*).DELETE)
}
