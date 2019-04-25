package code.api.STET.v1_4

import java.util.Date

import code.api.util.{APIUtil, CustomJsonFormats}
import code.model.{ModeratedBankAccount, ModeratedTransaction}
import com.openbankproject.commons.model.{BankAccount, TransactionRequest}

import scala.collection.immutable.List

object JSONFactory_STET_1_4 extends CustomJsonFormats {

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
  
  case class TransactionBalanceLink(
    href: Href= Href("v1/accounts"),
  ) extends Links
  
  case class TransactionLastLink(
    href: Href= Href("v1/accounts"),
  ) extends Links
  
  case class TransactionNextLink(
    href: Href= Href("v1/accounts"),
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
    transactions: List[TransactionJsonV1],
    _links: List[Links]= List(
      Self(Href("v1/accounts/Alias1/transactions")),
      ParentList(Href("v1/accounts")),
      TransactionBalanceLink(Href("v1/accounts/Alias1/balances")),
      TransactionLastLink(Href("v1/accounts/sAlias1/transactions?page=last")),
      TransactionNextLink(Href("v1/accounts/Alias1/transactions?page=3"))
    )
  )
  
  case class TransactionJsonV1(
    entryReference: String,
    creditDebitIndicator: String,
    transactionAmount: AmountOfMoneyV1,
    bookingDate: Date,
    status: String,
    remittanceInformation: List[String]
  )

  def createTransactionListJSON(accounts: List[BankAccount]): AccountsJsonV1 = 
    AccountsJsonV1(
      accounts =accounts.map(
        account => CoreAccountJsonV1(
          resourceId = account.accountId.value,
          bicFi = "",
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
      entryReference = transaction.id.value,
      creditDebitIndicator = "",
      transactionAmount = AmountOfMoneyV1(APIUtil.stringOptionOrNull(transaction.currency),transaction.amount.get.toString()),
      bookingDate = transaction.startDate.get,
      status = "COMPLETED",
      remittanceInformation = List(APIUtil.stringOptionOrNull(transaction.description))
    )
  }
  
  def createTransactionFromRequestJSON(transactionRequest : TransactionRequest) : TransactionJsonV1 = {
    TransactionJsonV1(
      entryReference = transactionRequest.id.value,
      creditDebitIndicator = transactionRequest.name,
      transactionAmount = AmountOfMoneyV1(transactionRequest.charge.value.currency,transactionRequest.charge.value.amount),
      bookingDate = transactionRequest.start_date,
      status = "BOOK",
      remittanceInformation = List(transactionRequest.body.description)
    )
  }
  
  def createTransactionsJson(transactions: List[ModeratedTransaction], transactionRequests: List[TransactionRequest]) : TransactionsJsonV1 = {
      val transactions_booked: List[TransactionJsonV1] = transactions.map(createTransactionJSON)
      val transactions_pending: List[TransactionJsonV1] =transactionRequests.filter(_.status!="COMPLETED").map(createTransactionFromRequestJSON)
    TransactionsJsonV1(
      transactions = transactions_booked:::transactions_pending:::Nil
    )
  }

}
