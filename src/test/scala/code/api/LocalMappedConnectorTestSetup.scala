package code.api

import java.util.Date
import bootstrap.liftweb.ToSchemify
import code.model.dataAccess._
import code.model._
import net.liftweb.mapper.MetaMapper
import net.liftweb.util.Helpers._

import scala.util.Random

trait LocalMappedConnectorTestSetup extends LocalConnectorTestSetup {

  override protected def createBank(id : String) : Bank = {
    MappedBank.create
      .fullBankName(randomString(5))
      .shortBankName(randomString(5))
      .permalink(id)
      .national_identifier(randomString(5)).saveMe
  }

  override protected def createAccount(bankId: BankId, accountId : AccountId, currency : String) : BankAccount = {
    MappedBankAccount.create
      .bank(bankId.value)
      .theAccountId(accountId.value)
      .accountCurrency(currency)
      .accountBalance(10000)
      .holder(randomString(4))
      .accountNumber(randomString(4))
      .accountLabel(randomString(4)).saveMe
  }

  override protected def createTransaction(account: BankAccount, startDate: Date, finishDate: Date) = {
    //ugly
    val mappedBankAccount = account.asInstanceOf[MappedBankAccount]

    val accountBalanceBefore = mappedBankAccount.accountBalance.get
    val transactionAmount = Random.nextInt(1000).toLong
    val accountBalanceAfter = accountBalanceBefore + transactionAmount

    mappedBankAccount.accountBalance(accountBalanceAfter).save

    MappedTransaction.create
      .bank(account.bankId.value)
      .account(account.accountId.value)
      .transactionType(randomString(5))
      .tStartDate(startDate)
      .tFinishDate(finishDate)
      .currency(account.currency)
      .amount(transactionAmount)
      .newAccountBalance(accountBalanceAfter)
      .description(randomString(5))
      .counterpartyAccountHolder(randomString(5))
      .counterpartyAccountKind(randomString(5))
      .counterpartyAccountNumber(randomString(5))
      .counterpartyBankName(randomString(5))
      .counterpartyIban(randomString(5))
      .counterpartyNationalId(randomString(5))
      .saveMe
      .toTransaction.openOrThrowException("Test setup issue: could not create Transaction")
  }

  override protected def wipeTestData() = {
    //returns true if the model should not be wiped after each test
    def exclusion(m : MetaMapper[_]) = {
      m == Nonce || m == Token || m == Consumer || m == OBPUser || m == APIUser
    }

    //empty the relational db tables after each test
    ToSchemify.models.filterNot(exclusion).foreach(_.bulkDelete_!!())
  }
}
