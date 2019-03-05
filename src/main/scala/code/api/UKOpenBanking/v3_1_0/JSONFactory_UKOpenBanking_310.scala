package code.api.UKOpenBanking.v3_1_0

import java.util.Date

import code.api.Constant
import code.api.util.APIUtil.DateWithDayExampleObject
import code.api.v1_2_1.AmountOfMoneyJsonV121
import code.model.{BankAccount, ModeratedBankAccount, ModeratedTransaction}
import code.transactionrequests.TransactionRequests.TransactionRequest

import scala.collection.immutable.List

object JSONFactory_UKOpenBanking_310 {

  implicit val formats = net.liftweb.json.DefaultFormats

  case class AccountsUKV310(Data: AccountList, Links: LinksV310, Meta: MetaUKV310)
  case class AccountList(Account: List[AccountUKV310])
  case class ServicerUKV310(
    SchemeName: List[String],
    Identification: String
  )
  case class AccountUKV310(
                      AccountId: String,
                      Currency: String,
                      AccountType: String,
                      AccountSubType: String,
                      Nickname: String,
                      Account: List[AccountInner],
                      Servicer: ServicerUKV310
                    )
  case class AccountInner(
                           SchemeName: List[String],
                           Identification: String,
                           Name: String,
                           SecondaryIdentification: String,
                         )

  case class LinksV310(
    Self: String,
    First: String,
    Prev: String,
    Next: String,
    Last: String
  )
  
  case class MetaUKV310(
    TotalPages: Int,
    FirstAvailableDateTime: Date,
    LastAvailableDateTime: Date,
  )

  case class TransactionsJsonUKV200(
    Data: TransactionsInnerJson,
    Links: LinksV310,
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
    Links: LinksV310,
    Meta: MetaBisJson
  )
  
  def createAccountsListJSON(accounts: List[BankAccount]): AccountsUKV310 = {
    val list = accounts.map(
      x => AccountUKV310(
        AccountId = x.accountId.value,
        Currency = x.currency,
        AccountType = x.accountType,
        AccountSubType = x.accountType,
        Nickname = x.label,
        List(AccountInner(
          SchemeName = List(x.accountRoutingScheme),
          Identification = x.accountRoutingAddress,
          Name = x.label,
          SecondaryIdentification=""
        )),
        ServicerUKV310(
          SchemeName = List(x.accountRoutingScheme),
          Identification = x.accountRoutingAddress
        )
      )
    )
    AccountsUKV310(
      Data = AccountList(list),
      Links = LinksV310(
        Self = Constant.HostName + "/open-banking/v2.0/accounts",
        First = Constant.HostName + "/open-banking/v2.0/accounts",
        Prev = Constant.HostName + "/open-banking/v2.0/accounts",
        Next = Constant.HostName + "/open-banking/v2.0/accounts",
        Last = Constant.HostName + "/open-banking/v2.0/accounts",
      ),
      Meta = MetaUKV310(
        TotalPages = 1,
        FirstAvailableDateTime = new Date(),
        LastAvailableDateTime = new Date(),
      )
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
      Links = LinksV310(
        Constant.HostName + s"/open-banking/v2.0/accounts/${accountId}/transactions/",
        Constant.HostName + s"/open-banking/v2.0/accounts/${accountId}/transactions/",
        Constant.HostName + s"/open-banking/v2.0/accounts/${accountId}/transactions/",
        Constant.HostName + s"/open-banking/v2.0/accounts/${accountId}/transactions/",
        Constant.HostName + s"/open-banking/v2.0/accounts/${accountId}/transactions/"
      ),
      Meta = MetaInnerJson(
        TotalPages = 1,
        FirstAvailableDateTime = DateWithDayExampleObject,
        LastAvailableDateTime = DateWithDayExampleObject
      )
    )
  }

  def createAccountJSON(accounts: List[BankAccount]) = {
    val list = accounts.map(
      x => AccountUKV310(
        AccountId = x.accountId.value,
        Currency = x.currency,
        AccountType = x.accountType,
        AccountSubType = x.accountType,
        Nickname = x.label,
        List(AccountInner(
          SchemeName = List(x.accountRoutingScheme),
          Identification = x.accountRoutingAddress,
          Name = x.name,
          SecondaryIdentification ="String"
        )),
        ServicerUKV310(
          SchemeName = List(x.accountRoutingScheme),
          Identification = x.accountRoutingAddress,
        )
      )
    )
    AccountsUKV310(
      Data = AccountList(list),
      Links = LinksV310(
        s"${Constant.HostName}/open-banking/v2.0/accounts/" + list.head.AccountId,
        s"${Constant.HostName}/open-banking/v2.0/accounts/" + list.head.AccountId,
        s"${Constant.HostName}/open-banking/v2.0/accounts/" + list.head.AccountId,
        s"${Constant.HostName}/open-banking/v2.0/accounts/" + list.head.AccountId,
        s"${Constant.HostName}/open-banking/v2.0/accounts/" + list.head.AccountId),
      Meta = MetaUKV310(
        TotalPages = 1,
        FirstAvailableDateTime = new Date(),
        LastAvailableDateTime = new Date()
      )
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
      Links = LinksV310(
        s"${Constant.HostName}/open-banking/v2.0/accounts/${accountId}/balances/",
        s"${Constant.HostName}/open-banking/v2.0/accounts/${accountId}/balances/",
        s"${Constant.HostName}/open-banking/v2.0/accounts/${accountId}/balances/",
        s"${Constant.HostName}/open-banking/v2.0/accounts/${accountId}/balances/",
        s"${Constant.HostName}/open-banking/v2.0/accounts/${accountId}/balances/"),
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
      Links = LinksV310(
        s"${Constant.HostName}/open-banking/v2.0/balances/",
        s"${Constant.HostName}/open-banking/v2.0/balances/",
        s"${Constant.HostName}/open-banking/v2.0/balances/",
        s"${Constant.HostName}/open-banking/v2.0/balances/",
        s"${Constant.HostName}/open-banking/v2.0/balances/"),
      Meta = MetaBisJson(1)
    )
  }

}
