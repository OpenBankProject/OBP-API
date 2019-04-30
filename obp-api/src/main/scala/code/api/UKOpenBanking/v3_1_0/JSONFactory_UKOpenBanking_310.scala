package code.api.UKOpenBanking.v3_1_0

import java.util.Date

import code.api.Constant
import code.api.util.APIUtil.DateWithDayExampleObject
import code.api.util.CustomJsonFormats
import code.model.{ModeratedBankAccount, ModeratedTransaction}
import com.openbankproject.commons.model.{AmountOfMoneyJsonV121, BankAccount, TransactionRequest}

import scala.collection.immutable.List

object JSONFactory_UKOpenBanking_310 extends CustomJsonFormats {

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

  case class TransactionsJsonUKV310(
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
    Type: String = "ClosingAvailable"
  )

  case class CurrencyExchangeJson(
    SourceCurrency: String,
    TargetCurrency: String,
    UnitCurrency: String,
    ExchangeRate: Int,
    ContractIdentification: String,
    QuotationDate: Date,
    InstructedAmount: AmountOfMoneyJsonV121
  )
  
  case class CardInstrumentJson(
    CardSchemeName: String = "AmericanExpress",
    AuthorisationType: String = "ConsumerDevice",
    Name: String = "string",
    Identification: String = "string"
  )
  
  case class MerchantDetailsJson(
    MerchantName: String = "String",
    MerchantCategoryCode : String = "String"
  )
  
  case class PostalAddressJson(
    AddressType: String ="Business",
    Department: String ="string",
    SubDepartment: String ="string",
    StreetName: String ="string",
    BuildingNumber: String ="string",
    PostCode: String ="string",
    TownName: String ="string",
    CountrySubDivision: String ="string",
    Country: String ="string",
    AddressLine: List[String]= List("string")
  )
  
  case class AgentJson(
    SchemeName: List[String] = List("UK.OBIE.BICFI"),
    Identification: String = "string",
    Name: String = "string",
    PostalAddress: PostalAddressJson = PostalAddressJson(),
  )
  
  case class TransactionInnerAccountJson(
    SchemeName: List[String] = List("UK.OBIE.BBAN"),
    Identification: String = "string",
    Name: String = "string",
    SecondaryIdentification: String = "string",
  )
  
  case class TransactionInnerJson(
    AccountId: String,
    TransactionId: String,
    TransactionReference: String,
    StatementReference: List[String] = List("String"),
    Amount: AmountOfMoneyJsonV121,
    CreditDebitIndicator: String ="Credit",
    Status: String ="Booked",
    BookingDateTime: Date,
    ValueDateTime: Date,
    AddressLine: String = "String",
    ChargeAmount: AmountOfMoneyJsonV121,
    TransactionInformation: String,
    CurrencyExchange:CurrencyExchangeJson,
    BankTransactionCode: BankTransactionCodeJson,
    ProprietaryBankTransactionCode: TransactionCodeJson,
    CardInstrument: CardInstrumentJson,
    SupplementaryData:String = "",//Empty object {}. not sure what does it mean 
    Balance: BalanceUKOpenBankingJson,
    MerchantDetails: MerchantDetailsJson = MerchantDetailsJson(),
    CreditorAgent:AgentJson = AgentJson(),
    CreditorAccount:TransactionInnerAccountJson = TransactionInnerAccountJson(),
    DebtorAgent:AgentJson= AgentJson(),
    DebtorAccount:TransactionInnerAccountJson = TransactionInnerAccountJson(),
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
  
  case class BalanceJsonUKV310(
    AccountId: String,
    Amount: AmountOfMoneyJsonV121,
    CreditDebitIndicator: String,
    Type: String,
    DateTime: Date,
    CreditLine: List[CreditLineJson]
  )
  
  case class DataJsonUKV310(
    Balance: List[BalanceJsonUKV310]
  )
  
  
  case class AccountBalancesUKV310(
    Data: DataJsonUKV310,
    Links: LinksV310,
    Meta: MetaUKV310
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
        Self = Constant.HostName + "/open-banking/v3.1/accounts",
        First = Constant.HostName + "/open-banking/v3.1/accounts",
        Prev = Constant.HostName + "/open-banking/v3.1/accounts",
        Next = Constant.HostName + "/open-banking/v3.1/accounts",
        Last = Constant.HostName + "/open-banking/v3.1/accounts",
      ),
      Meta = MetaUKV310(
        TotalPages = 1,
        FirstAvailableDateTime = new Date(),
        LastAvailableDateTime = new Date(),
      )
    )
  }
  
  def createTransactionsJson(transactions: List[ModeratedTransaction], transactionRequests: List[TransactionRequest]) : TransactionsJsonUKV310 = {
    val accountId = transactions.head.bankAccount.get.accountId.value
    val transactionsInnerJson = transactions.map(
      transaction=>TransactionInnerJson(
        accountId,
        transaction.id.value,
        TransactionReference = transaction.description.getOrElse(""),
        Amount = AmountOfMoneyJsonV121(
          currency = transaction.currency.getOrElse("") ,
          amount= transaction.amount.getOrElse(BigDecimal(0)).toString()),
        BookingDateTime = transaction.startDate.get,
        ValueDateTime = transaction.finishDate.get,
        ChargeAmount = AmountOfMoneyJsonV121(transaction.currency.getOrElse(""),"0"),
        TransactionInformation = transaction.description.getOrElse(""),
        CurrencyExchange = CurrencyExchangeJson(
          SourceCurrency = transaction.bankAccount.map(_.currency).flatten.getOrElse(""),
          TargetCurrency = "",//No currency in the otherBankAccount,
          UnitCurrency = "",
          ExchangeRate = 0,
          ContractIdentification = "string",
          QuotationDate = new Date(),
          InstructedAmount = AmountOfMoneyJsonV121(transaction.bankAccount.map(_.currency).flatten.getOrElse(""),"")), 
        BankTransactionCode = BankTransactionCodeJson("",""),
        ProprietaryBankTransactionCode = TransactionCodeJson("Transfer", "AlphaBank"),
        CardInstrument = CardInstrumentJson(),
        Balance =BalanceUKOpenBankingJson(
          Amount = AmountOfMoneyJsonV121(
            currency = transaction.currency.getOrElse(""),
            amount = transaction.balance
          ),
          CreditDebitIndicator = "Credit",
          Type = "InterimBooked"
        )
      )
    )
    TransactionsJsonUKV310(
      Data = TransactionsInnerJson(transactionsInnerJson),
      Links = LinksV310(
        Constant.HostName + s"/open-banking/v3.1/accounts/${accountId}/transactions",
        Constant.HostName + s"/open-banking/v3.1/accounts/${accountId}/transactions",
        Constant.HostName + s"/open-banking/v3.1/accounts/${accountId}/transactions",
        Constant.HostName + s"/open-banking/v3.1/accounts/${accountId}/transactions",
        Constant.HostName + s"/open-banking/v3.1/accounts/${accountId}/transactions"
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
        s"${Constant.HostName}/open-banking/v3.1/accounts/" + list.head.AccountId,
        s"${Constant.HostName}/open-banking/v3.1/accounts/" + list.head.AccountId,
        s"${Constant.HostName}/open-banking/v3.1/accounts/" + list.head.AccountId,
        s"${Constant.HostName}/open-banking/v3.1/accounts/" + list.head.AccountId,
        s"${Constant.HostName}/open-banking/v3.1/accounts/" + list.head.AccountId),
      Meta = MetaUKV310(
        TotalPages = 1,
        FirstAvailableDateTime = new Date(),
        LastAvailableDateTime = new Date()
      )
    )
  }
  
  def createAccountBalanceJSON(moderatedAccount : ModeratedBankAccount) = {
    val accountId = moderatedAccount.accountId.value
    
    val dataJson = DataJsonUKV310(
      List(BalanceJsonUKV310(
        AccountId = accountId,
        Amount = AmountOfMoneyJsonV121(moderatedAccount.currency.getOrElse(""), moderatedAccount.balance),
        CreditDebitIndicator = "Credit",
        Type = "ClosingAvailable",
        DateTime = null,
        CreditLine = List(CreditLineJson(
          Included = true,
          Amount = AmountOfMoneyJsonV121(moderatedAccount.currency.getOrElse(""),moderatedAccount.balance),
          Type = "Pre-Agreed"
        )))))
    
    AccountBalancesUKV310(
      Data = dataJson,
      Links = LinksV310(
        s"${Constant.HostName}/open-banking/v3.1/accounts/${accountId}/balances",
        s"${Constant.HostName}/open-banking/v3.1/accounts/${accountId}/balances",
        s"${Constant.HostName}/open-banking/v3.1/accounts/${accountId}/balances",
        s"${Constant.HostName}/open-banking/v3.1/accounts/${accountId}/balances",
        s"${Constant.HostName}/open-banking/v3.1/accounts/${accountId}/balances"),
      Meta = MetaUKV310(
        0,
        new Date(),
        new Date(),
      )
    )
  }
  
  def createBalancesJSON(accounts: List[BankAccount]) = {
    
    val dataJson = DataJsonUKV310(
      accounts.map(account => BalanceJsonUKV310(
        AccountId = account.accountId.value,
        Amount = AmountOfMoneyJsonV121(account.currency, account.balance.toString()),
        CreditDebitIndicator = "Credit",
        Type = "ClosingAvailable",
        DateTime = account.lastUpdate,
        CreditLine = List(CreditLineJson(
          Included = true,
          Amount = AmountOfMoneyJsonV121(account.currency, account.balance.toString()),
          Type = "Available"
        )))))
    
    AccountBalancesUKV310(
      Data = dataJson,
      Links = LinksV310(
        s"${Constant.HostName}/open-banking/v3.1/balances",
        s"${Constant.HostName}/open-banking/v3.1/balances",
        s"${Constant.HostName}/open-banking/v3.1/balances",
        s"${Constant.HostName}/open-banking/v3.1/balances",
        s"${Constant.HostName}/open-banking/v3.1/balances"),
      Meta = MetaUKV310(
        0,
        new Date(),
        new Date()
      )
    )
  }

}
