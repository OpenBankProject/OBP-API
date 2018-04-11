package code.setup

import java.util.Date

import code.model._
import code.model.dataAccess._
import com.mongodb.QueryBuilder
import net.liftweb.util.Helpers._
import code.api.util.ErrorMessages._
import scala.math.BigDecimal
import scala.math.BigDecimal.RoundingMode
import scala.util.Random._

/**
  * This trait is for Liftweb record, link to MangoDB....
  */
trait LocalRecordConnectorTestSetup extends TestConnectorSetupWithStandardPermissions {

  override protected def createBank(id : String) : Bank = {
    HostedBank.createRecord.
      name(randomString(5)).
      alias(randomString(5)).
      permalink(id).
      national_identifier(randomString(5)).
      save(true)
  }

  override protected def createAccount(bankId: BankId, accountId : AccountId, currency : String) : BankAccount = {
    val q = QueryBuilder.start(HostedBank.permalink.name).is(bankId.value).get()
    val hostedBank = HostedBank.find(q).openOrThrowException(attemptedToOpenAnEmptyBox)

    Account.createRecord.
      accountBalance(900000000).
      holder(randomString(4)).
      accountNumber(randomString(4)).
      kind(randomString(4)).
      accountName(randomString(4)).
      permalink(accountId.value).
      bankID(hostedBank.id.get).
      accountLabel(randomString(4)).
      accountCurrency(currency).
      save(true)
  }

  override protected def createTransaction(account: BankAccount, startDate: Date, finishDate: Date) = {

    val thisAccountBank = OBPBank.createRecord.
      IBAN(randomString(5)).
      national_identifier(account.nationalIdentifier).
      name(account.bankName)
    val thisAccount = OBPAccount.createRecord.
      holder(account.accountHolder).
      number(account.number).
      kind(account.accountType).
      bank(thisAccountBank)

    val otherAccountBank = OBPBank.createRecord.
      IBAN(randomString(5)).
      national_identifier(randomString(5)).
      name(randomString(5))

    val otherAccount = OBPAccount.createRecord.
      holder(randomString(5)).
      number(randomString(5)).
      kind(randomString(5)).
      bank(otherAccountBank)

    val transactionAmount = BigDecimal(nextDouble * 1000).setScale(2,RoundingMode.HALF_UP)

    val newBalance : OBPBalance = OBPBalance.createRecord.
      currency(account.currency).
      amount(account.balance + transactionAmount)

    val newValue : OBPValue = OBPValue.createRecord.
      currency(account.currency).
      amount(transactionAmount)

    val details ={
      OBPDetails
        .createRecord
        .kind(randomString(5))
        .posted(startDate)
        .other_data(randomString(5))
        .new_balance(newBalance)
        .value(newValue)
        .completed(finishDate)
        .label(randomString(5))
    }
    val transaction = OBPTransaction.createRecord.
      this_account(thisAccount).
      other_account(otherAccount).
      details(details)

    val env = OBPEnvelope.createRecord.
      obp_transaction(transaction).save(true)

    //slightly ugly
    account.asInstanceOf[Account].accountBalance(newBalance.amount.get).accountLastUpdate(now).save(true)

    env.save(true)
  }
  
  override protected def createTransactionRequest(account: BankAccount) = Unit

}
