package code.api.UKOpenBanking.v2_0_0

import java.util.Date

import code.api.Constant
import code.api.util.APIUtil.DateWithDayExampleObject
import code.api.util.CustomJsonFormats
import com.openbankproject.commons.model.AmountOfMoneyJsonV121
import code.model.{ModeratedBankAccount, ModeratedTransaction}
import code.model.toBankAccountExtended
import com.openbankproject.commons.model.TransactionRequest
import com.openbankproject.commons.model.BankAccount

import scala.collection.immutable.List

object JSONFactory_UKOpenBanking_200 extends CustomJsonFormats {

  case class Accounts(Data: AccountList, Links: Links, Meta: MetaUK)
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
  case class MetaUK(TotalPages: Int)

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
  
  case class CreditLineJson(
    Included: Boolean,
    Amount: AmountOfMoneyJsonV121,
    Type: String
  )
  
  case class BalanceJsonUKV200(
    AccountId: String,
    Amount: AmountOfMoneyJsonV121,
    CreditDebitIndicator: String,
    Type: String,
    DateTime: Date,
    CreditLine: List[CreditLineJson]
  )
  
  case class DataJsonUKV200(
    Balance: List[BalanceJsonUKV200]
  )
  
  case class MetaBisJson(
    TotalPages: Int
  )
  
  case class AccountBalancesUKV200(
    Data: DataJsonUKV200,
    Links: Links,
    Meta: MetaBisJson
  )
  
  def createAccountsListJSON(accounts: List[BankAccount]): Accounts = {
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
      Meta = MetaUK(TotalPages = 1)
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
        FirstAvailableDateTime = DateWithDayExampleObject,
        LastAvailableDateTime = DateWithDayExampleObject
      )
    )
  }

  def createAccountJSON(accounts: List[BankAccount]) = {
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
      Links = Links(Self = s"${Constant.HostName}/open-banking/v2.0/accounts/" + list.head.AccountId),
      Meta = MetaUK(TotalPages = 1)
    )
  }
  
  def createAccountBalanceJSON(moderatedAccount : ModeratedBankAccount) = {
    val accountId = moderatedAccount.accountId.value
    
    val dataJson = DataJsonUKV200(
      List(BalanceJsonUKV200(
        AccountId = accountId,
        Amount = AmountOfMoneyJsonV121(moderatedAccount.currency.getOrElse(""), moderatedAccount.balance),
        CreditDebitIndicator = moderatedAccount.owners.getOrElse(null).head.name,
        Type = "Credit",
        DateTime = null,
        CreditLine = List(CreditLineJson(
          Included = true,
          Amount = AmountOfMoneyJsonV121(moderatedAccount.currency.getOrElse(""),moderatedAccount.balance),
          Type = "Pre-Agreed"
        )))))
    
    AccountBalancesUKV200(
      Data = dataJson,
      Links = Links(s"${Constant.HostName}/open-banking/v2.0/accounts/${accountId}/balances/"),
      Meta = MetaBisJson(1)
    )
  }
  
  def createBalancesJSON(accounts: List[BankAccount]) = {
    
    val dataJson = DataJsonUKV200(
      accounts.map(account => BalanceJsonUKV200(
        AccountId = account.accountId.value,
        Amount = AmountOfMoneyJsonV121(account.currency, account.balance.toString()),
        CreditDebitIndicator = account.userOwners.headOption.getOrElse(null).name,
        Type = "Credit",
        DateTime = null,
        CreditLine = List(CreditLineJson(
          Included = true,
          Amount = AmountOfMoneyJsonV121(account.currency, account.balance.toString()),
          Type = "Pre-Agreed"
        )))))
    
    AccountBalancesUKV200(
      Data = dataJson,
      Links = Links(s"${Constant.HostName}/open-banking/v2.0/balances/"),
      Meta = MetaBisJson(1)
    )
  }

}
