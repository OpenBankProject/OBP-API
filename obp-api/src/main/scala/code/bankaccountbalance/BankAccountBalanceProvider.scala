package code.bankaccountbalance

import code.model.dataAccess.MappedBankAccount
import code.util.Helper
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{AccountId, BalanceId, BankId}
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future

object BankAccountBalanceX extends SimpleInjector {

  val bankAccountBalanceProvider = new Inject(buildOne _) {}

  def buildOne: BankAccountBalanceProviderTrait = MappedBankAccountBalanceProvider

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

object MappedBankAccountBalanceProvider extends BankAccountBalanceProviderTrait {

  override def getBankAccountBalances(accountId: AccountId): Future[Box[List[BankAccountBalance]]] = Future {
    tryo{
      BankAccountBalance.findAll(
        By(BankAccountBalance.AccountId_,accountId.value)
    )}
  }
  override def getBankAccountsBalances(accountIds: List[AccountId]): Future[Box[List[BankAccountBalance]]] = Future {
    tryo {
      BankAccountBalance.findAll(
        ByList(BankAccountBalance.AccountId_, accountIds.map(_.value))
      )
    }
  }

  override def getBankAccountBalanceById(balanceId: BalanceId): Future[Box[BankAccountBalance]] = Future {
    // Find a balance by its ID
    BankAccountBalance.find(
      By(BankAccountBalance.BalanceId_, balanceId.value)
    )
  }

  override def createOrUpdateBankAccountBalance(
    bankId: BankId,
    accountId: AccountId,
    balanceId: Option[BalanceId],
    balanceType: String,
    balanceAmount: BigDecimal
  ): Future[Box[BankAccountBalance]] = Future {
    // Get the MappedBankAccount for the given account ID
    val mappedBankAccount = code.model.dataAccess.MappedBankAccount
    .find(
        By(MappedBankAccount.theAccountId, accountId.value)
      )

    mappedBankAccount match {
      case Full(account) =>
        balanceId match {
          case Some(id) =>
            BankAccountBalance.find(
              By(BankAccountBalance.BalanceId_, id.value)
            ) match {
              case Full(balance) =>
                tryo {
                  balance
                    .BankId_(bankId.value)
                    .AccountId_(accountId.value)
                    .BalanceType(balanceType)
                    .BalanceAmount(Helper.convertToSmallestCurrencyUnits(balanceAmount, account.currency))
                    .saveMe()
                }
              case _ => Empty
            }
          case _ =>
            tryo {
              BankAccountBalance.create
                .BankId_(bankId.value)
                .AccountId_(accountId.value)
                .BalanceType(balanceType)
                .BalanceAmount(Helper.convertToSmallestCurrencyUnits(balanceAmount, account.currency))
                .saveMe()
            }
        }
      case _ => Empty
    }
  }

  override def deleteBankAccountBalance(balanceId: BalanceId): Future[Box[Boolean]] = Future {
    // Delete a balance by its ID
    BankAccountBalance.find(
      By(BankAccountBalance.BalanceId_, balanceId.value)
    ).map(_.delete_!)
  }

}
