package code.sandbox

import code.metadata.counterparties.{MappedCounterpartyMetadata}
import code.model.dataAccess.{MappedBankAccount, MappedBank}
import code.model.{MappedTransaction, AccountId, BankId}
import code.bankbranches.{MappedBranch, MappedDataLicense}
import code.util.Helper.convertToSmallestCurrencyUnits
import net.liftweb.common.{Full, Failure, Box}
import net.liftweb.mapper.Mapper
import net.liftweb.util.Helpers._

case class MappedSaveable[T <: Mapper[_]](value : T) extends Saveable[T] {
  def save() = value.save()
}

object LocalMappedConnectorDataImport extends OBPDataImport with CreateViewImpls with CreateOBPUsers {

  type BankType = MappedBank
  type AccountType = MappedBankAccount
  type MetadataType = MappedCounterpartyMetadata
  type TransactionType = MappedTransaction
  type BranchType = MappedBranch
  type DataLicenseType = MappedDataLicense

  protected def createSaveableBanks(data : List[SandboxBankImport]) : Box[List[Saveable[BankType]]] = {
    val mappedBanks = data.map(bank => {
      MappedBank.create
        .permalink(bank.id)
        .fullBankName(bank.full_name)
        .shortBankName(bank.short_name)
        .logoURL(bank.logo)
        .websiteURL(bank.website)
    })

    val validationErrors = mappedBanks.flatMap(_.validate)

    if(validationErrors.nonEmpty) {
      Failure(s"Errors: ${validationErrors.map(_.msg)}")
    } else {
      Full(mappedBanks.map(MappedSaveable(_)))
    }
  }

  protected def createSaveableBranches(data : List[SandboxBankBranchImport]) : Box[List[Saveable[BranchType]]] = {
    val mappedBankBranches = data.map(bankBranch => {
      MappedBranch.create
        .mBranchId(bankBranch.id)
        .mBankId(bankBranch.bank)
        .mName(bankBranch.name)
      // TODO add the other fields
    })

    val validationErrors = mappedBankBranches.flatMap(_.validate)

    if(validationErrors.nonEmpty) {
      Failure(s"Errors: ${validationErrors.map(_.msg)}")
    } else {
      Full(mappedBankBranches.map(MappedSaveable(_)))
    }
  }




  protected def createSaveableAccount(acc : SandboxAccountImport, banks : List[BankType]) : Box[Saveable[AccountType]] = {

    val mappedAccount = for {
      balance <- tryo{BigDecimal(acc.balance.amount)} ?~ s"Invalid balance: ${acc.balance.amount}"
      currency = acc.balance.currency
    } yield {
      MappedBankAccount.create
        .theAccountId(acc.id)
        .bank(acc.bank)
        .accountLabel(acc.label)
        .accountNumber(acc.number)
        .kind(acc.`type`)
        .accountIban(acc.IBAN)
        .accountCurrency(currency)
        .accountBalance(convertToSmallestCurrencyUnits(balance, currency))
    }

    val validationErrors = mappedAccount.map(_.validate).getOrElse(Nil)

    if(validationErrors.nonEmpty) {
      Failure(s"Errors: ${validationErrors.map(_.msg)}")
    } else {
      mappedAccount.map(MappedSaveable(_))
    }
  }


  override protected def createSaveableTransaction(t : SandboxTransactionImport, createdBanks : List[BankType], createdAccounts : List[AccountType]):
  Box[Saveable[TransactionType]] = {

    for {
      createdAcc <- Box(createdAccounts.find(acc => acc.accountId == AccountId(t.this_account.id) && acc.bankId == BankId(t.this_account.bank))) ?~ {
        logger.warn("Data import failed because a created account was not found for a transaction when it should have been")
        "Server Error"
      }
      currency = createdAcc.currency
      newBalanceValueAsBigDecimal <- tryo{BigDecimal(t.details.new_balance)} ?~ s"Invalid new balance: ${t.details.new_balance}"
      tValueAsBigDecimal <- tryo{BigDecimal(t.details.value)} ?~ s"Invalid transaction value: ${t.details.value}"
      postedDate <- tryo{dateFormat.parse(t.details.posted)} ?~ s"Invalid date format: ${t.details.posted}. Expected pattern $datePattern"
      completedDate <-tryo{dateFormat.parse(t.details.completed)} ?~ s"Invalid date format: ${t.details.completed}. Expected pattern $datePattern"
    } yield {
      val mappedTransaction = MappedTransaction.create
        .bank(t.this_account.bank)
        .account(t.this_account.id)
        .transactionId(t.id)
        .transactionType(t.details.`type`)
        .amount(convertToSmallestCurrencyUnits(tValueAsBigDecimal, currency))
        .newAccountBalance(convertToSmallestCurrencyUnits(newBalanceValueAsBigDecimal, currency))
        .currency(currency)
        .tStartDate(postedDate)
        .tFinishDate(completedDate)
        .description(t.details.description)
        .counterpartyAccountHolder(t.counterparty.flatMap(_.name).getOrElse(""))
        .counterpartyAccountNumber(t.counterparty.flatMap(_.account_number).getOrElse(""))

      MappedSaveable(mappedTransaction)
    }
  }

}
