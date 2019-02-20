package code.api.STET.v1_4

import java.util.Date

import code.api.util.APIUtil
import code.api.v2_1_0.IbanJson
import code.model.{BankAccount, CoreAccount, ModeratedBankAccount, ModeratedTransaction}
import code.transactionrequests.TransactionRequests.TransactionRequest

import scala.collection.immutable.List

object JSONFactory_STET_1_4 {

  implicit val formats = net.liftweb.json.DefaultFormats

  trait Links
  case class Balances(balances: String) extends Links
  case class Transactions(trasactions: String) extends Links
  case class ViewAccount(viewAccount: String) extends Links
  case class CoreAccountJsonV1(
                                 resourceId: String,
                                 bicFi: String,
                                 currency: String,
                                 accountType: String,
                                 cashAccountType: String,
                                 name: String,
                                 usage: String="PRIV",
                                 psuStatus: String="Co-account Holder",
                               )
  case class Href(href: String) extends Links
  
  case class Self(self: Href= Href("v1/accounts?page=2")) extends Links
  
  case class First(self: Href= Href("v1/accounts")) extends Links
  
  case class Last(
    href: Href= Href("v1/accounts?page=last"),
    templated: Boolean = true
  ) extends Links

  case class Next(
    href: Href= Href("v1/accounts?page=3"),
    templated: Boolean = true
  ) extends Links
  
  case class Prev(
    href: Href= Href("v1/accounts"),
    templated: Boolean = true
  ) extends Links
  
  case class ParentList(
    `parent-list`: Href= Href("v1/accounts"),
  ) extends Links
  
  case class TransactionsLinks(
    transactions: Href= Href("v1/accounts/Alias1/transactions"),
  ) extends Links
  
  case class AccountsJsonV1(
    accounts: List[CoreAccountJsonV1],
    _links: List[Links]
  )
  
  case class AmountOfMoneyV1(
    currency : String,
    content : String
  )
  case class ClosingBookedBody(
    amount : AmountOfMoneyV1,
    date: String //eg:  “2017-10-25”, this is not a valid datetime (not java.util.Date)
  )
  case class ExpectedBody(
    amount : AmountOfMoneyV1,
    lastActionDateTime: Date
  )
  case class AccountBalance(
    name:String,
    balanceAmount: AmountOfMoneyV1,
    balanceType:String,
    lastCommittedTransaction: String
  )
  case class AccountBalances(
    `balances`: List[AccountBalance],
    _links: List[Links] = List(
      Self(Href("v1/accounts/Alias1/balances-report")),
      ParentList(Href("v1/accounts")),
      TransactionsLinks(Href("v1/accounts/Alias1/transactions"))
    )
  )
  
  case class TransactionsJsonV1(
    transactions_booked: List[TransactionJsonV1],
    transactions_pending: List[TransactionJsonV1],
    _links: List[ViewAccount]
  )
  
  case class TransactionJsonV1(
    transactionId: String,
    creditorName: String,
    creditorAccount: IbanJson,
    amount: AmountOfMoneyV1,
    bookingDate: Date,
    valueDate: Date,
    remittanceInformationUnstructured: String
  )

  def createTransactionListJSON(accounts: List[BankAccount]): AccountsJsonV1 = 
    AccountsJsonV1(
      accounts =accounts.map(
        account => CoreAccountJsonV1(
          resourceId = account.accountId.value,
          bicFi = account.swift_bic.getOrElse(account.iban.getOrElse("")),
          currency = account.currency,
          accountType = account.accountType,
          cashAccountType = "CACC",
          name = account.accountHolder)
      ),
      _links=List(Self(), First(), Last(), Next(), Prev())
    )
  
  def createAccountBalanceJSON(moderatedAccount: ModeratedBankAccount) = {
    AccountBalances(
      `balances`= List(
        AccountBalance(
          name = "Solde comptable au 12/01/2017",
          balanceAmount = AmountOfMoneyV1(moderatedAccount.currency.getOrElse(""),moderatedAccount.balance),
          balanceType= moderatedAccount.accountType.getOrElse(""),
          lastCommittedTransaction="A452CH"
        )
      )
    )
  }
  
  def createTransactionJSON(transaction : ModeratedTransaction) : TransactionJsonV1 = {
    TransactionJsonV1(
      transactionId = transaction.id.value,
      creditorName = "",
      creditorAccount = IbanJson(APIUtil.stringOptionOrNull(transaction.bankAccount.get.iban)),
      amount = AmountOfMoneyV1(APIUtil.stringOptionOrNull(transaction.currency), transaction.amount.get.toString()),
      bookingDate = transaction.startDate.get,
      valueDate = transaction.finishDate.get,
      remittanceInformationUnstructured = APIUtil.stringOptionOrNull(transaction.description)
    )
  }
  
  def createTransactionFromRequestJSON(transactionRequest : TransactionRequest) : TransactionJsonV1 = {
    TransactionJsonV1(
      transactionId = transactionRequest.id.value,
      creditorName = transactionRequest.name,
      creditorAccount = IbanJson(transactionRequest.from.account_id),
      amount = AmountOfMoneyV1(transactionRequest.charge.value.currency, transactionRequest.charge.value.amount),
      bookingDate = transactionRequest.start_date,
      valueDate = transactionRequest.end_date,
      remittanceInformationUnstructured = transactionRequest.body.description
    )
  }
  
  def createTransactionsJson(transactions: List[ModeratedTransaction], transactionRequests: List[TransactionRequest]) : TransactionsJsonV1 = {
    TransactionsJsonV1(
      transactions_booked =transactions.map(createTransactionJSON),
      transactions_pending =transactionRequests.filter(_.status!="COMPLETED").map(createTransactionFromRequestJSON),
      _links = ViewAccount(s"/${OBP_STET_1_4.version}/accounts/${transactionRequests.head.from.account_id}/balances")::Nil
    )
  }

}
