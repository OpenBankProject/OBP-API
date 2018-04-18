package code.api.UKOpenBanking.v2_0_0

import java.util.Date

import code.api.Constant
import code.api.util.APIUtil.exampleDate
import code.api.v1_2_1.AmountOfMoneyJsonV121
import code.model.{BankAccount, ModeratedTransaction}
import code.transactionrequests.TransactionRequests.TransactionRequest

import scala.collection.immutable.List

object JSONFactory_UKOpenBanking_200 {

  implicit val formats = net.liftweb.json.DefaultFormats

  case class Accounts(Data: AccountList, Links: Links, Meta: Meta)
  case class AccountList(Account: List[Account])
  case class Account(
                      AccountId: String,
                      Currency: String,
                      AccountType: String,
                      AccountSubType: String,
                      Nickname: String,
                      Account: AccountInner
                    )
  case class AccountInner(
                           SchemeName: String,
                           Identification: String,
                           Name: String,
                           SecondaryIdentification: Option[String] = None,
                         )

  case class Links(Self: String)
  case class Meta(TotalPages: Int)

  case class TransactionsJsonUKV200(
    Data: TransactionsInnerJson,
    Links: Links,
    Meta: MetaInnerJson
  )
  
  case class BankTransactionCodeJson(
    Code: String,
    SubCode: String
  )
  
  case class TransactionCodeJson(
    Code: String,
    Issuer: String
  )
  
  case class BalanceUKOpenBankingJson(
    Amount: AmountOfMoneyJsonV121,
    CreditDebitIndicator: String,
    Type: String
  )

  case class TransactionInnerJson(
    AccountId: String,
    TransactionId: String,
    TransactionReference: String,
    Amount: AmountOfMoneyJsonV121,
    CreditDebitIndicator: String,
    Status: String,
    BookingDateTime: Date,
    ValueDateTime: Date,
    TransactionInformation: String,
    BankTransactionCode: BankTransactionCodeJson,
    ProprietaryBankTransactionCode: TransactionCodeJson,
    Balance: BalanceUKOpenBankingJson
  )
  
  case class TransactionsInnerJson(
    Transaction: List[TransactionInnerJson]
  )
  
  case class MetaInnerJson(
    TotalPages: Int,
    FirstAvailableDateTime: Date,
    LastAvailableDateTime: Date
  )
  
  def createAccountsListJSON(accounts: List[BankAccount]) = {
    val list = accounts.map(
      x => Account(
        AccountId = x.accountId.value,
        Currency = x.currency,
        AccountType = x.accountType,
        AccountSubType = x.accountType,
        Nickname = x.label,
        AccountInner(
          SchemeName = x.accountRoutingScheme,
          Identification = x.accountRoutingAddress,
          Name = x.name
        )
      )
    )
    Accounts(
      Data = AccountList(list),
      Links = Links(Self = Constant.HostName + "/open-banking/v2.0/accounts"),
      Meta = Meta(TotalPages = 1)
    )
  }
  
  def createTransactionsJson(transactions: List[ModeratedTransaction], transactionRequests: List[TransactionRequest]) : TransactionsJsonUKV200 = {
    val accountId = transactions.head.bankAccount.get.accountId.value
    val transactionsInnerJson = transactions.map(
      transaction=>TransactionInnerJson(
        AccountId = accountId,
        TransactionId  = transaction.id.value,
        TransactionReference = transaction.description.getOrElse(""),
        Amount = AmountOfMoneyJsonV121(
          currency = transaction.currency.getOrElse("") ,
          amount= transaction.amount.getOrElse(BigDecimal(0)).toString()
        ),
        CreditDebitIndicator = "Credit",
        Status = "Booked",
        BookingDateTime = transaction.startDate.get,
        ValueDateTime = transaction.finishDate.get,
        TransactionInformation = transaction.description.getOrElse(""),
        BankTransactionCode = BankTransactionCodeJson("",""),
        ProprietaryBankTransactionCode = TransactionCodeJson("Transfer", "AlphaBank"),
        Balance = BalanceUKOpenBankingJson(
          Amount = AmountOfMoneyJsonV121(
            currency = transaction.currency.getOrElse(""),
            amount = transaction.balance
          ),
          CreditDebitIndicator = "Credit",
          Type = "InterimBooked"
        ))
    )
    TransactionsJsonUKV200(
      Data = TransactionsInnerJson(transactionsInnerJson),
      Links = Links(Constant.HostName + s"/open-banking/v2.0/accounts/${accountId}/transactions/"),
      Meta = MetaInnerJson(
        TotalPages = 1,
        FirstAvailableDateTime = exampleDate,
        LastAvailableDateTime = exampleDate
      )
    )
  }

}
