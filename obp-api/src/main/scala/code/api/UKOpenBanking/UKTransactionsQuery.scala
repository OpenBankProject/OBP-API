package code.api.UKOpenBanking

import code.api.APIFailureNewStyle
import code.api.Constant
import code.api.util.APIUtil.{HTTPParam, createQueriesByHttpParams, fullBoxOrException, passesPsd2Aisp, unboxFull}
import code.api.util.{CallContext, OBPLimit, OBPQueryParam}
import code.api.util.ErrorMessages.UnknownError
import code.api.util.NewStyle
import code.api.util.newstyle.ViewNewStyle
import code.model.{BankAccountExtended, ModeratedTransaction, UserExtended}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{AccountId, Bank, BankAccount, BankIdAccountId, TransactionAttribute, User, View, ViewId}
import net.liftweb.common.Full
import org.http4s.Request
import cats.effect.IO

import scala.concurrent.Future

/**
 * Reading an account's transactions under a UK Open Banking consent.
 *
 * v3.1 and v4.0.1 expose the same read at two paths and serialise it with their own JSON factories,
 * but everything before that last step is one procedure: check the consent, resolve Detail-or-Basic,
 * turn the request into query params, narrow them by the directions the consent granted, fetch, and
 * filter what came back. It lived twice, once per version, and the direction rule then had to be
 * written twice as well -- the same shape that let an earlier fix land in one copy and not the other.
 *
 * The versions keep only what actually differs: their route, and the factory they yield through.
 */
object UKTransactionsQuery extends MdcLoggable {

  /**
   * Everything the version-specific `yield` needs, once the shared work is done.
   *
   * `transactions` is already direction-filtered, and `attributes` was fetched for exactly those
   * rows, so a caller cannot accidentally serialise the unfiltered list.
   */
  case class Result(
    account: BankAccount,
    view: View,
    transactions: List[ModeratedTransaction],
    attributes: List[TransactionAttribute]
  )

  /**
   * @param req the http4s request, read for its pagination/filter headers
   * @param u   the authenticated caller, whose consent scope decides the directions
   */
  def read(req: Request[IO], u: User, cc: CallContext, accountId: AccountId): Future[Result] = {
    val detailViewId = ViewId(Constant.SYSTEM_READ_TRANSACTIONS_DETAIL_VIEW_ID)
    val basicViewId = ViewId(Constant.SYSTEM_READ_TRANSACTIONS_BASIC_VIEW_ID)
    for {
      _ <- NewStyle.function.checkUKConsent(u, Some(cc))
      _ <- passesPsd2Aisp(Some(cc))
      (account, _) <- NewStyle.function.getBankAccountByAccountId(accountId, Some(cc))
      (bank: Bank, _) <- NewStyle.function.getBank(account.bankId, Some(cc))
      view <- ViewNewStyle.checkViewsAccessAndReturnView(
        detailViewId, basicViewId, BankIdAccountId(account.bankId, accountId), Full(u), Some(cc))
      params <- Future {
        createQueriesByHttpParams(req.headers.headers.toList.map(h => HTTPParam(h.name.toString, List(h.value))))
      } map { x =>
        unboxFull(fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, Some(cc.toLight))))
      }
      // Resolved before the query so the direction can shape it: the database has to apply the
      // restriction and the page limit together, or the page is trimmed after the fact and a
      // Credits-only consent on a debit-heavy account sees a short page it cannot tell from the end
      // of the data.
      grantsCredits = UKAmounts.grantsView(Constant.SYSTEM_READ_TRANSACTIONS_CREDITS_VIEW_ID, account.bankId, accountId, u, cc)
      grantsDebits = UKAmounts.grantsView(Constant.SYSTEM_READ_TRANSACTIONS_DEBITS_VIEW_ID, account.bankId, accountId, u, cc)
      directedParams = params ++ UKAmounts.directionQueryParam(grantsCredits, grantsDebits)
      (transactions, _) <- BankAccountExtended(account)
        .getModeratedTransactionsFuture(bank, Full(u), view, Some(cc), directedParams) map { x =>
        unboxFull(fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, Some(cc.toLight))))
      }
      // ReadTransactionsCredits / ReadTransactionsDebits restrict which rows the consent may see,
      // not which fields, so they are applied here rather than through the view's can_* set. Kept
      // alongside the query restriction on purpose: it is what holds the consent's scope when the
      // connector ignored the param.
      directedTransactions = UKAmounts.filterByGrantedDirections(transactions, grantsCredits, grantsDebits)
      _ = warnIfPageWasTrimmed(transactions, directedTransactions, directedParams, cc)
      (moderatedAttributes: List[TransactionAttribute], _) <- NewStyle.function.getModeratedAttributesByTransactions(
        account.bankId,
        directedTransactions.map(_.id),
        view.viewId,
        Some(cc))
    } yield Result(account, view, directedTransactions, moderatedAttributes)
  }

  /**
   * Say so when the direction restriction had to be applied after the page limit.
   *
   * `OBPTransactionDirection` is translated by LocalMappedConnector; the remote connectors take a
   * frozen outbound message (see RestConnector_vMar2019_FrozenTest) carrying only limit, offset and
   * the date range, so they cannot receive it and return both directions. The filter above then
   * removes rows from an already-limited page, and the TPP gets a short page indistinguishable from
   * the end of the data -- a Credits-only consent on a debit-heavy account can see nothing at all.
   *
   * Nothing here can repair that without the connector's cooperation, and silently returning the
   * short page is how the defect stayed invisible in the first place. So it is logged: a full page
   * that lost rows to the filter is the exact signature, and it tells an operator which connector
   * still needs to honour the param.
   */
  def warnIfPageWasTrimmed(
    fetched: List[ModeratedTransaction],
    kept: List[ModeratedTransaction],
    params: List[OBPQueryParam],
    cc: CallContext
  ): Unit = {
    val limit = params.collectFirst { case OBPLimit(value) => value }.getOrElse(Constant.Pagination.limit)
    if (kept.size < fetched.size && fetched.size >= limit) {
      logger.warn(
        s"UK transactions: the direction restriction was applied after the page limit -- " +
        s"${fetched.size - kept.size} of $limit rows removed from a full page for consent " +
        s"${cc.consumer.map(_.consumerId.get).getOrElse("unknown")}. The connector in use did not " +
        s"honour OBPTransactionDirection, so this page is short and the TPP cannot tell. " +
        s"Implement the param in that connector to fix the pagination.")
    }
  }
}
