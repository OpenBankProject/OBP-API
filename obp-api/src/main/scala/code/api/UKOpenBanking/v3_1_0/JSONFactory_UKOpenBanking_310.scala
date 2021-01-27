package code.api.UKOpenBanking.v3_1_0

import java.util.Date

import code.api.Constant
import code.api.util.APIUtil.DateWithDayExampleObject
import code.api.util.CustomJsonFormats
import code.model.{ModeratedBankAccount, ModeratedBankAccountCore, ModeratedTransaction}
import com.openbankproject.commons.model.{AccountAttribute, AccountId, AmountOfMoneyJsonV121, BankAccount, BankId, TransactionAttribute, TransactionId, TransactionRequest, View}
import net.liftweb.json.JsonAST.JObject

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
                      Status: String = "",
                      StatusUpdateDateTime: String,
                      Currency: String,
                      AccountType: String,
                      AccountSubType: String,
                      AccountIndicator: String,
                      OnboardingType: Option[String],
                      Nickname: Option[String],
                      OpeningDate: Option[String],
                      MaturityDate: Option[String],
                      Account: List[AccountInner],
                      Servicer: Option[ServicerUKV310]
                    )
  case class AccountInner(
                           SchemeName: List[String],
                           Identification: String,
                           Name: Option[String] = None,
                           SecondaryIdentification: Option[String] = None, 
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
    MerchantName: Option[String] = None,
    MerchantCategoryCode : Option[String] = None
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
    MerchantDetails: Option[MerchantDetailsJson] = None,
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

  case class DataConsentPostResponseUKV310(
    ConsentId: String,
    CreationDateTime: String,
    Status: String,
    StatusUpdateDateTime: String,
    Permissions: List[String],
    ExpirationDateTime: String,
    TransactionFromDateTime: String,
    TransactionToDateTime: String
  )
  case class ConsentPostResponseUKV310(
    Data: DataConsentPostResponseUKV310,
    Links: LinksV310,
    Risk: String
  )
  case class ConsentPostBodyDataUKV310(
    TransactionToDateTime: String,
    ExpirationDateTime: String,
    Permissions: List[String],
    TransactionFromDateTime: String
  )
  case class ConsentPostBodyUKV310(
    Data: ConsentPostBodyDataUKV310,
    Risk: String
  )

  def createAccountsListJSON(accounts : List[(BankAccount, View)],
    moderatedAttributes: List[AccountAttribute]
  ): AccountsUKV310 = {

    def getServicer(account: (BankAccount, View)): Option[ServicerUKV310] = {
      account._2.viewId.value match {
        case Constant.SYSTEM_READ_ACCOUNTS_DETAIL_VIEW_ID =>
          val schemeName = accountAttributeValue("Servicer_SchemeName", account._1.bankId, account._1.accountId, moderatedAttributes)
          val identification = accountAttributeValue("Servicer_Identification", account._1.bankId, account._1.accountId, moderatedAttributes)
          val result = ServicerUKV310(
            SchemeName = List(schemeName),
            Identification = identification
          )
          if (schemeName != null || identification != null) Some(result) else None
        case _ =>
          None
      }
    }

    def getAccountDetails(account: (BankAccount, View)): Option[AccountInner] = {
      account._2.viewId.value match {
        case Constant.SYSTEM_READ_ACCOUNTS_DETAIL_VIEW_ID =>
          account._1.accountRoutings.headOption.map(e =>
            AccountInner(
              SchemeName = List(e.scheme),
              Identification = e.address,
            )
          )
        case _ =>
          None
      }
    }
    
    val list = accounts.map(
      account => AccountUKV310(
        AccountId = account._1.accountId.value,
        Status = accountAttributeValue("Status", account._1.bankId, account._1.accountId, moderatedAttributes),
        StatusUpdateDateTime = accountAttributeValue("StatusUpdateDateTime", account._1.bankId, account._1.accountId, moderatedAttributes),
        Currency = account._1.currency,
        AccountType = account._1.accountType,
        AccountSubType = accountAttributeValue("AccountSubType", account._1.bankId, account._1.accountId, moderatedAttributes),
        AccountIndicator = accountAttributeValue("AccountIndicator", account._1.bankId, account._1.accountId, moderatedAttributes),
        OnboardingType = accountAttributeOptValue("OnboardingType", account._1.bankId, account._1.accountId, moderatedAttributes),
        Nickname = Some(account._1.label),
        OpeningDate = accountAttributeOptValue("OpeningDate", account._1.bankId, account._1.accountId, moderatedAttributes),
        MaturityDate = accountAttributeOptValue("MaturityDate", account._1.bankId, account._1.accountId, moderatedAttributes),
        getAccountDetails(account).toList,
        Servicer = getServicer(account)
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
  private def accountAttributeOptValue(name: String,
    bankId: BankId,
    accountId: AccountId,
    list: List[AccountAttribute]): Option[String] =
    list.filter(e => e.name == name && e.bankId == bankId && e.accountId == accountId).headOption.map(_.value)
  private def accountAttributeValue(name: String,
    bankId: BankId,
    accountId: AccountId,
    list: List[AccountAttribute]): String =
    accountAttributeOptValue(name, bankId, accountId, list).getOrElse(null)

  private def transactionAttributeOptValue(name: String,
    bankId: BankId,
    transactionId: TransactionId,
    list: List[TransactionAttribute]): Option[String] =
    list.filter(e => e.name == name && e.bankId == bankId && e.transactionId == transactionId).headOption.map(_.value)

  private def transactionAttributeValue(name: String,
    bankId: BankId,
    transactionId: TransactionId,
    list: List[TransactionAttribute]): String =
    transactionAttributeOptValue(name, bankId, transactionId, list).getOrElse(null)

  def createTransactionsJson(transactions: List[ModeratedTransaction], transactionRequests: List[TransactionRequest]) : TransactionsJsonUKV310 = {
    val accountId = transactions.headOption.map(_.bankAccount.get.accountId.value).orNull
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
  
  def createTransactionsJsonNew(
    bankId: BankId,
    moderatedTransactions: List[ModeratedTransaction],
    attributes: List[TransactionAttribute],
    view: View
  ) : TransactionsJsonUKV310 = {
    val accountId = moderatedTransactions.headOption.map(_.bankAccount.get.accountId.value).orNull

    def getMerchantDetails(moderatedTransaction: ModeratedTransaction) = {
      view.viewId.value match {
        case Constant.SYSTEM_READ_TRANSACTIONS_DETAIL_VIEW_ID =>
          val merchantName = transactionAttributeOptValue("MerchantDetails_MerchantName", bankId, moderatedTransaction.id, attributes)
          val merchantCategoryCode = transactionAttributeOptValue("MerchantDetails_CategoryCode", bankId, moderatedTransaction.id, attributes)
          val result = MerchantDetailsJson(
            MerchantName = merchantName,
            MerchantCategoryCode = merchantCategoryCode
          )
          if (merchantName.isDefined || merchantCategoryCode.isDefined) Some(result) else None
        case _ => None
      }
    }
    
    val transactionsInnerJson = moderatedTransactions.map(
      moderatedTransaction=>TransactionInnerJson(
        accountId,
        moderatedTransaction.id.value,
        TransactionReference = moderatedTransaction.description.getOrElse(""),
        Amount = AmountOfMoneyJsonV121(
          currency = moderatedTransaction.currency.getOrElse("") ,
          amount= moderatedTransaction.amount.getOrElse(BigDecimal(0)).toString()),
        BookingDateTime = moderatedTransaction.startDate.get,
        ValueDateTime = moderatedTransaction.finishDate.get,
        ChargeAmount = AmountOfMoneyJsonV121(moderatedTransaction.currency.getOrElse(""),"0"),
        TransactionInformation = moderatedTransaction.description.getOrElse(""),
        CurrencyExchange = CurrencyExchangeJson(
          SourceCurrency = moderatedTransaction.bankAccount.map(_.currency).flatten.getOrElse(""),
          TargetCurrency = "",//No currency in the otherBankAccount,
          UnitCurrency = "",
          ExchangeRate = 0,
          ContractIdentification = "string",
          QuotationDate = new Date(),
          InstructedAmount = AmountOfMoneyJsonV121(moderatedTransaction.bankAccount.map(_.currency).flatten.getOrElse(""),"")), 
        BankTransactionCode = BankTransactionCodeJson("",""),
        ProprietaryBankTransactionCode = TransactionCodeJson("Transfer", "AlphaBank"),
        CardInstrument = CardInstrumentJson(),
        Balance =BalanceUKOpenBankingJson(
          Amount = AmountOfMoneyJsonV121(
            currency = moderatedTransaction.currency.getOrElse(""),
            amount = moderatedTransaction.balance
          ),
          CreditDebitIndicator = "Credit",
          Type = "InterimBooked"
        ),
        MerchantDetails = getMerchantDetails(moderatedTransaction)
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
  def createAccountBalanceJSON(moderatedAccount : ModeratedBankAccountCore) = {
    val accountId = moderatedAccount.accountId.value
    
    val dataJson = DataJsonUKV310(
      List(BalanceJsonUKV310(
        AccountId = accountId,
        Amount = AmountOfMoneyJsonV121(moderatedAccount.currency.getOrElse(""), moderatedAccount.balance.getOrElse("")),
        CreditDebitIndicator = "Credit",
        Type = "ClosingAvailable",
        DateTime = null,
        CreditLine = List(CreditLineJson(
          Included = true,
          Amount = AmountOfMoneyJsonV121(moderatedAccount.currency.getOrElse(""),moderatedAccount.balance.getOrElse("")),
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
