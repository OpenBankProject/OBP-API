package code.api.UKOpenBanking.v4_0_1

import code.api.Constant
import code.api.util.APIUtil.OAuth._
import code.api.util.NewStyle
import code.setup.{APIResponse, DefaultUsers, ServerSetupWithTestData}
import code.setup.OBPReq
import code.views.Views
import com.openbankproject.commons.model.{AccountId, Transaction, User}

import scala.concurrent.Await
import scala.concurrent.duration._

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

  /**
   * Grants the UK-Open-Banking-specific read views (basic + detail for accounts,
   * balances, transactions) that ServerSetupWithTestData's default view grants
   * (owner/auditor/accountant/firehose/public/random) do not include. Needed for
   * getAccounts/getAccountsAccountId/Balances/Transactions to surface real data
   * instead of filtering the account out via APIUtil.checkViewAccessAndReturnView.
   */
  def grantUKReadViews(accountId: AccountId, user: User): Unit = {
    List(
      Constant.SYSTEM_READ_ACCOUNTS_BASIC_VIEW_ID,
      Constant.SYSTEM_READ_ACCOUNTS_DETAIL_VIEW_ID,
      Constant.SYSTEM_READ_BALANCES_VIEW_ID,
      Constant.SYSTEM_READ_TRANSACTIONS_BASIC_VIEW_ID,
      Constant.SYSTEM_READ_TRANSACTIONS_DETAIL_VIEW_ID
    ).foreach { viewId =>
      val view = Views.views.vend.getOrCreateSystemView(viewId).openOrThrowException(s"could not create system view $viewId")
      Views.views.vend.grantAccessToSystemView(testBankId1, accountId, view, user)
    }
  }

  /**
   * Seeds transactions for an already-created test account, mirroring
   * TestConnectorSetup.createTransactions (used automatically by suites listed in
   * ServerSetup.suitesNeedingTransactionData). This suite seeds on demand instead
   * of joining that global set, to keep the shared file untouched.
   */
  def seedTransactions(accountId: AccountId): List[Transaction] = {
    val (account, _) = Await.result(NewStyle.function.getBankAccountByAccountId(accountId, None), 10.seconds)
    createTransactions(List(account))
  }

  // Build a request from path segments, e.g. v401("aisp", "accounts", accountId, "balances").
  def v401(segments: String*): OBPReq = segments.foldLeft(v401Request)((req, s) => req / s)

  def getAuthed(segments: String*): APIResponse = makeGetRequest(v401(segments: _*).GET <@ (user1))
  def getUnauthed(segments: String*): APIResponse = makeGetRequest(v401(segments: _*).GET)

  // user2 has no private accounts (only user1 gets them in beforeEach) — use for empty-data scenarios.
  def getAuthedAsUser2(segments: String*): APIResponse = makeGetRequest(v401(segments: _*).GET <@ (user2))

  def postAuthed(body: String, segments: String*): APIResponse = makePostRequest(v401(segments: _*).POST <@ (user1), body)
  def postUnauthed(body: String, segments: String*): APIResponse = makePostRequest(v401(segments: _*).POST, body)

  def putAuthed(body: String, segments: String*): APIResponse = makePutRequest(v401(segments: _*).PUT <@ (user1), body)
  def putUnauthed(body: String, segments: String*): APIResponse = makePutRequest(v401(segments: _*).PUT, body)

  def patchAuthed(body: String, segments: String*): APIResponse = makePatchRequest(v401(segments: _*).PATCH <@ (user1), body)
  def patchUnauthed(body: String, segments: String*): APIResponse = makePatchRequest(v401(segments: _*).PATCH, body)

  def deleteAuthed(segments: String*): APIResponse = makeDeleteRequest(v401(segments: _*).DELETE <@ (user1))
  def deleteUnauthed(segments: String*): APIResponse = makeDeleteRequest(v401(segments: _*).DELETE)

  // For IDOR regression tests: user2 authenticated, but acting on a resource (e.g. a consent)
  // that belongs to a different user (typically resourceUser1).
  def deleteAuthedAsUser2(segments: String*): APIResponse = makeDeleteRequest(v401(segments: _*).DELETE <@ (user2))
}
