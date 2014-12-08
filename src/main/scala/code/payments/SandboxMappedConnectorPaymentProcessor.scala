/*
package code.payments

import code.model.{TransactionId, MappedTransaction, BankAccount}
import net.liftweb.common.{Full, Box}
import net.liftweb.util.Helpers._
import code.util.Helper.convertToSmallestCurrencyUnits

/**
 * This payment processor works against the relational db mapped MappedLocalConnector implementation.
 */
object SandboxMappedConnectorPaymentProcessor extends PaymentProcessor {

  override def makePayment(fromAccount: BankAccount, toAccount: BankAccount, amt: BigDecimal): Box[String] = {
    val fromTransAmt = -amt //from account balance should decrease
    val toTransAmt = amt //to account balance should increase

    //we need to save a copy of this payment as a transaction in each of the accounts involved, with opposite amounts
    val sentTransactionId = saveTransaction(fromAccount, toAccount, fromTransAmt)
    saveTransaction(toAccount, fromAccount, toTransAmt)

    sentTransactionId.map(_.value)
  }

  /**
   * Saves a transaction with amount @amt and counterparty @counterparty for account @account. Returns the id
   * of the saved transaction.
   */
  private def saveTransaction(account : BankAccount, counterparty : BankAccount, amt : BigDecimal) : Box[TransactionId] = {

    val transactionTime = now
    val currency = account.currency


    //TODO: need to update account balances..., but that's not possible via the BankAccount trait, this needs to
    //work on the MappedBankAccount (that presumably exists)

    val newAccountBalance : Long = 0//???


    val mappedTransaction = MappedTransaction.create
      .bank(account.bankId.value)
      .account(account.accountId.value)
      .transactionType("sandbox-payment")
      .amount(convertToSmallestCurrencyUnits(amt, currency))
      .newAccountBalance(newAccountBalance)
      .currency(currency)
      .tStartDate(transactionTime)
      .tFinishDate(transactionTime)
      .description("")
      .counterpartyAccountHolder(counterparty.accountHolder)
      .counterpartyAccountNumber(counterparty.number)
      .counterpartyAccountKind(counterparty.accountType)
      .counterpartyBankName(counterparty.bankName)
      .counterpartyIban(counterparty.iban.getOrElse(""))
      .counterpartyNationalId(counterparty.nationalIdentifier).saveMe

    Full(mappedTransaction.theTransactionId)
  }
}
*/
