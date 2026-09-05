package code.bankaccountbalance

import code.api.util.{APIUtil, DoobieUtil}
import code.util.Helper
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{AccountId, BalanceId, BankId}
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future

object BankAccountBalanceX extends SimpleInjector {

  val bankAccountBalanceProvider = new Inject(() => buildOne) {}

  def buildOne: BankAccountBalanceProviderTrait = DoobieBankAccountBalanceProvider

  // Helper to get the count out of an option
  def countOfBankAccountBalance(listOpt: Option[List[BankAccountBalance]]): Int = {
    val count = listOpt match {
      case Some(list) => list.size
      case None => 0
    }
    count
  }
}

trait BankAccountBalanceProviderTrait {

  def getBankAccountBalances(accountId: AccountId): Future[Box[List[BankAccountBalance]]]

  def getBankAccountsBalances(accountIds: List[AccountId]): Future[Box[List[BankAccountBalance]]]

  def getBankAccountBalanceById(balanceId: BalanceId): Future[Box[BankAccountBalance]]

  def createOrUpdateBankAccountBalance(
    bankId: BankId,
    accountId: AccountId,
    balanceId: Option[BalanceId],
    balanceType: String,
    balanceAmount: BigDecimal): Future[Box[BankAccountBalance]]

  def deleteBankAccountBalance(balanceId: BalanceId): Future[Box[Boolean]]

}

object DoobieBankAccountBalanceProvider extends BankAccountBalanceProviderTrait {

  override def getBankAccountBalances(accountId: AccountId): Future[Box[List[BankAccountBalance]]] = Future {
    tryo(BankAccountBalance.findAllByAccountId(accountId.value))
  }

  override def getBankAccountsBalances(accountIds: List[AccountId]): Future[Box[List[BankAccountBalance]]] = Future {
    tryo(BankAccountBalance.findAllByAccountIds(accountIds.map(_.value)))
  }

  override def getBankAccountBalanceById(balanceId: BalanceId): Future[Box[BankAccountBalance]] = Future {
    BankAccountBalance.findByBalanceId(balanceId.value)
  }

  /**
   * Both branches require the account to exist and return Empty when it does not - the account is
   * what supplies the currency the amount is converted into for storage. On the update branch an
   * unknown balanceId is likewise Empty rather than an insert: this is create-or-update on a
   * supplied id, not upsert-by-id.
   */
  override def createOrUpdateBankAccountBalance(
    bankId: BankId,
    accountId: AccountId,
    balanceId: Option[BalanceId],
    balanceType: String,
    balanceAmount: BigDecimal
  ): Future[Box[BankAccountBalance]] = Future {
    DoobieUtil.runQuery(
      sql"SELECT accountcurrency FROM mappedbankaccount WHERE theaccountid = ${accountId.value} LIMIT 1"
        .query[String].option
    ) match {
      case Some(currency) =>
        val amountSmallestUnit = Helper.convertToSmallestCurrencyUnits(balanceAmount, currency)
        balanceId match {
          case Some(id) =>
            BankAccountBalance.findByBalanceId(id.value) match {
              case Full(_) =>
                tryo {
                  BankAccountBalance.update(id.value, bankId.value, accountId.value, balanceType, amountSmallestUnit)
                  BankAccountBalance.findByBalanceId(id.value)
                    .openOrThrowException("the row just updated must still be readable")
                }
              case _ => Empty
            }
          case _ =>
            tryo {
              BankAccountBalance.insert(
                APIUtil.generateUUID(), bankId.value, accountId.value, balanceType, amountSmallestUnit)
            }
        }
      case _ => Empty
    }
  }

  override def deleteBankAccountBalance(balanceId: BalanceId): Future[Box[Boolean]] = Future {
    BankAccountBalance.findByBalanceId(balanceId.value)
      .map(_ => BankAccountBalance.deleteByBalanceId(balanceId.value))
  }

}
