package code.bankaccountbalance

import code.model.dataAccess.MappedBankAccount
import code.util.Helper.MdcLoggable
import code.util.{Helper, MappedUUID}
import com.openbankproject.commons.model.{AccountId, BalanceId, BankAccountBalanceTrait, BankId}
import net.liftweb.common.{Empty, Failure, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

import java.util.Date

class BankAccountBalance extends BankAccountBalanceTrait
  with KeyedMapper[String, BankAccountBalance]
  with CreatedUpdated
  with MdcLoggable {

  override def getSingleton = BankAccountBalance

  // Define BalanceId_ as the primary key
  override def primaryKeyField = BalanceId_.asInstanceOf[KeyedMetaMapper[String, BankAccountBalance]].primaryKeyField
  
  object BankId_ extends MappedUUID(this)
  object AccountId_ extends MappedUUID(this)
  object BalanceId_ extends MappedUUID(this)
  object BalanceType extends MappedString(this, 255)
  //this is the smallest unit of currency! eg. cents, yen, pence, øre, etc.
  object BalanceAmount extends MappedLong(this)
  object ReferenceDate extends MappedDate(this)

  val foreignMappedBankAccountCurrency = tryo{code.model.dataAccess.MappedBankAccount
    .find(
      By(MappedBankAccount.theAccountId, AccountId_.get))
    .map(_.currency)
    .getOrElse("EUR")
  }.getOrElse("EUR")
  
  override def bankId: BankId = BankId(BankId_.get)
  override def accountId: AccountId = AccountId(AccountId_.get)
  override def balanceId: BalanceId = BalanceId(BalanceId_.get)
  override def balanceType: String = BalanceType.get
  override def balanceAmount: BigDecimal = Helper.smallestCurrencyUnitToBigDecimal(BalanceAmount.get, foreignMappedBankAccountCurrency)
  override def lastChangeDateTime: Option[Date] = Some(this.updatedAt.get)
  override def referenceDate: Option[String] = {
    net.liftweb.util.Helpers.tryo {
      Option(ReferenceDate.get) match {
        case Some(d) => Some(d.toString)
        case None =>
          logger.warn(s"ReferenceDate is missing for BalanceId=${BalanceId_.get}, AccountId=${AccountId_.get}, BankId=${BankId_.get}")
          None
      }
    } match {
      case Full(v) => v
      case f: Failure =>
        // extract throwable if present; otherwise create one from the message
        val t = f.exception.openOr(new RuntimeException(f.msg))
        logger.error(s"Error while retrieving referenceDate for BalanceId=${BalanceId_.get}, AccountId=${AccountId_.get}, BankId=${BankId_.get}: ${f.msg}", t)
        None
      case Empty =>
        // Defensive: treat as missing
        None
    }
  }
}

object BankAccountBalance
  extends BankAccountBalance
    with KeyedMetaMapper[String, BankAccountBalance]
    with CreatedUpdated {}
