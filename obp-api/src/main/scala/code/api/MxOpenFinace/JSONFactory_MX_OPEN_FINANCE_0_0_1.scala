package code.api.MxOpenFinace

import java.util.Date

import code.api.Constant
import code.api.util.CustomJsonFormats
import code.model.{ModeratedBankAccountCore, ModeratedTransaction}
import com.openbankproject.commons.model._
import net.liftweb.json.JValue

import scala.collection.immutable.List

case class JvalueCaseClass(jvalueToCaseclass: JValue)

object JSONFactory_MX_OPEN_FINANCE_0_0_1 extends CustomJsonFormats {

  case class MetaMXOFV001(LastAvailableDateTime: Date, FirstAvailableDateTime: Date, TotalPages: Int)
  case class LinksMXOFV001(Self: String, First: Option[String], Prev: Option[String], Next: Option[String], Last: Option[String] )
  case class ReadAccountBasicMXOFV001(Data: DataAccountBasicMXOFV001, Links: LinksMXOFV001, Meta: MetaMXOFV001)
  case class DataAccountBasicMXOFV001(Account: List[AccountBasicMXOFV001])
  case class ServicerMXOFV001(SchemeName: String,
                              Identification: String)
  case class AccountBasicMXOFV001(AccountId: String,
                                  Status: String,
                                  StatusUpdateDateTime: String,
                                  Currency: String,
                                  AccountType: String,
                                  AccountSubType: String,
                                  AccountIndicator: String,
                                  OnboardingType: Option[String],
                                  Nickname: Option[String],
                                  OpeningDate: Option[String],
                                  MaturityDate: Option[String],
                                  Account: Option[AccountDetailMXOFV001],
                                  Servicer: Option[ServicerMXOFV001]
                               )
  case class AccountDetailMXOFV001(
                                  SchemeName: String,
                                  Identification: String,
                                  Name: Option[String]
                                )

  case class AmountMXOFV001(
    Amount: String,
    Currency: String
  )
  case class CurrencyExchangeMXOFV001(
    SourceCurrency: String,
    TargetCurrency: String,
    UnitCurrency: String,
    ExchangeRate: Double,
    ContractIdentification: String,
    QuotationDate: String,
    InstructedAmount: AmountMXOFV001
  )
  case class BankTransactionCodeMXOFV001(
    Code: String,
    SubCode: String
  )
  case class CardInstrumentMXOFV001(
    CardSchemeName: String,
    AuthorisationType: String,
    Name: String,
    Identification: String
  )
  case class AdditionalProp1MXOFV001(

  )
  case class SupplementaryDataMXOFV001(
    additionalProp1: AdditionalProp1MXOFV001
  )
  
  case class TransactionBasicMXOFV001(
                                       AccountId: String,
                                       TransactionId: String,
                                       TransactionReference: Option[String],
                                       TransferTracingCode: Option[String],
                                       AccountIndicator: String,
                                       Status: String,
                                       BookingDateTime: String,
                                       ValueDateTime: Option[String],
                                       TransactionInformation: String,
                                       AddressLine: Option[String],
                                       Amount: AmountMXOFV001,
                                       CurrencyExchange: Option[CurrencyExchangeMXOFV001],
                                       BankTransactionCode: Option[BankTransactionCodeMXOFV001],
                                       CardInstrument: Option[CardInstrumentMXOFV001],
                                       SupplementaryData: Option[SupplementaryDataMXOFV001]
  )

  case class ReadTransactionMXOFV001(Data: List[TransactionBasicMXOFV001], Links: LinksMXOFV001, Meta: MetaMXOFV001)

  case class ConsentPostBodyDataMXOFV001(
    TransactionToDateTime: String,
    ExpirationDateTime: String,
    Permissions: List[String],
    TransactionFromDateTime: String
  )
  case class ConsentPostBodyMXOFV001(
    Data: ConsentPostBodyDataMXOFV001
  )
  
  
  lazy val metaMocked = MetaMXOFV001(
    LastAvailableDateTime = new Date(),
    FirstAvailableDateTime = new Date(),
    TotalPages = 0
  )
  lazy val linksMocked = LinksMXOFV001(Self = "Self", None, None, None, None)

  lazy val accountBasic = AccountBasicMXOFV001(
    AccountId = "string",
    Status = "Enabled",
    StatusUpdateDateTime = "2020-08-28T06:44:05.618Z",
    Currency = "string",
    AccountType = "RegularAccount",
    AccountSubType = "Personal",
    AccountIndicator = "Debit",
    OnboardingType = Some("OnSite"),
    Nickname = Some("Amazon"),
    OpeningDate = Some("2020-08-28T06:44:05.618Z"),
    MaturityDate = Some("2020-08-28T06:44:05.618Z"),
    Account = Some(
        AccountDetailMXOFV001(
        SchemeName = "string",
        Identification = "string",
        Name = Some("string")
      )
    ),
    Servicer = Some(ServicerMXOFV001(SchemeName = "string", Identification = "string"))
  )

  lazy val ofReadAccountBasic = ReadAccountBasicMXOFV001(Meta = metaMocked, Links = linksMocked, Data = DataAccountBasicMXOFV001(Account = List(accountBasic)))

  val dataJson = DataJsonMXOFV001(
    List(BalanceJsonMXOFV001(
    AccountId = "accountId",
    Amount = AmountOfMoneyJsonV121("currency", "1000"),
    CreditDebitIndicator = "Credit",
    Type = "Available",
    DateTime = null,
    CreditLine = List(CreditLineJsonMXOFV001(
      Included = true,
      Amount = AmountOfMoneyJsonV121("currency", "1000"),
      Type = "Pre-Agreed"
    )))))
  
  lazy val ofReadBalances = AccountBalancesMXOFV001(
    Data = dataJson,
    Links = LinksMXOFV001(
      s"${Constant.HostName}/mx-open-finance/v0.0.1/accounts/accountId/balances",
      None,
      None,
      None,
      None),
    Meta = MetaMXOFV001(
      new Date(),
      new Date(),
      0
    )
  )

  private def extractOptionalAttributeValue(name: String, 
                                            bankId: BankId, 
                                            accountId: AccountId, 
                                            list: List[AccountAttribute]): Option[String] =
    list.filter(e => e.name == name && e.bankId == bankId && e.accountId == accountId).headOption.map(_.value)
  private def extractAttributeValue(name: String, 
                                    bankId: BankId,
                                    accountId: AccountId, 
                                    list: List[AccountAttribute]): String =
    extractOptionalAttributeValue(name, bankId, accountId, list).getOrElse("")
  
  def createReadAccountBasicJsonMXOFV10(account : ModeratedBankAccountCore, 
                                        moderatedAttributes: List[AccountAttribute],
                                        view: View): ReadAccountBasicMXOFV001 = {
    
    val accountBasic = AccountBasicMXOFV001(
      AccountId = account.accountId.value,
      Status = extractAttributeValue("Status", account.bankId, account.accountId, moderatedAttributes),
      StatusUpdateDateTime = extractAttributeValue("StatusUpdateDateTime", account.bankId, account.accountId, moderatedAttributes),
      Currency = account.currency.getOrElse(""),
      AccountType = account.accountType.getOrElse(""),
      AccountSubType = extractAttributeValue("AccountSubType", account.bankId, account.accountId, moderatedAttributes),
      AccountIndicator = extractAttributeValue("AccountIndicator", account.bankId, account.accountId, moderatedAttributes),
      OnboardingType = extractOptionalAttributeValue("OnboardingType", account.bankId, account.accountId, moderatedAttributes),
      Nickname = account.label,
      OpeningDate = extractOptionalAttributeValue("OpeningDate", account.bankId, account.accountId, moderatedAttributes),
      MaturityDate = extractOptionalAttributeValue("MaturityDate", account.bankId, account.accountId, moderatedAttributes),
      Account = view.viewId.value match {
        case Constant.READ_ACCOUNTS_DETAIL_VIEW_ID =>
          account.accountRoutings.headOption.map(e =>
            AccountDetailMXOFV001(SchemeName = e.scheme, Identification = e.address, None)
          )
        case _ => 
          None
      },
      Servicer = view.viewId.value match {
        case Constant.READ_ACCOUNTS_DETAIL_VIEW_ID =>
          None
        case _ =>
          None
      }
    )
    val links = LinksMXOFV001(
      s"${Constant.HostName}/mx-open-finance/v0.0.1/accounts/" + account.accountId.value,
      None,
      None,
      None,
      None
    )
    val meta = MetaMXOFV001(
      TotalPages = 1,
      FirstAvailableDateTime = new Date(),
      LastAvailableDateTime = new Date()
    )
    ReadAccountBasicMXOFV001(Meta = meta, Links = links, Data = DataAccountBasicMXOFV001(Account = List(accountBasic)))
  }
  def createReadAccountsBasicJsonMXOFV10(accounts : List[BankAccount]): ReadAccountBasicMXOFV001 = {
    val accountsBasic = accounts.map(account =>
      AccountBasicMXOFV001(
        AccountId = account.accountId.value,
        Status = "",
        StatusUpdateDateTime = "",
        Currency = account.currency,
        AccountType = account.accountType,
        AccountSubType = "",
        AccountIndicator = "",
        OnboardingType = None,
        Nickname = Some(account.label),
        OpeningDate = None,
        MaturityDate = None,
        Account = account.accountRoutings.headOption.map(e =>
          AccountDetailMXOFV001(SchemeName = e.scheme, Identification = e.address, None)
        ),
        Servicer = None
      )
    )
    val links = LinksMXOFV001(
      s"${Constant.HostName}/mx-open-finance/v0.0.1/accounts/",
      None,
      None,
      None,
      None
    )
    val meta = MetaMXOFV001(
      TotalPages = 1,
      FirstAvailableDateTime = new Date(),
      LastAvailableDateTime = new Date()
    )
    ReadAccountBasicMXOFV001(Meta = meta, Links = links, Data = DataAccountBasicMXOFV001(Account = accountsBasic))
  }

  def createGetTransactionsByAccountIdMXOFV10(moderatedTransactions : List[ModeratedTransaction]): ReadTransactionMXOFV001 = {

    val accountId = moderatedTransactions.map(_.bankAccount.map(_.accountId.value)).flatten.headOption.getOrElse("")
    
    val transactions = moderatedTransactions.map(
      moderatedTransaction =>
        TransactionBasicMXOFV001(
        AccountId = accountId,
        TransactionId = moderatedTransaction.id.value,
        TransactionReference = None,
        TransferTracingCode = None,
        AccountIndicator = moderatedTransaction.bankAccount.map(_.accountType).flatten.getOrElse(null),
        Status = "BOOKED", // [ Booked, Pending, Cancelled ]
        BookingDateTime = moderatedTransaction.startDate.map(_.toString).getOrElse(null),
        ValueDateTime = None,
        TransactionInformation = moderatedTransaction.description.getOrElse(null),
        AddressLine = None,
        Amount = AmountMXOFV001(
          moderatedTransaction.amount.map(_.bigDecimal.toString).getOrElse(null),
          moderatedTransaction.currency.getOrElse(null),
        ),
        CurrencyExchange = None,
        BankTransactionCode = None,
        CardInstrument = None,
        SupplementaryData = None
      )
    )
    
    val links = LinksMXOFV001(
      s"${Constant.HostName}/mx-open-finance/v0.0.1/accounts/" + accountId + "/transactions",
      None,
      None,
      None,
      None
    )
    
    val meta = MetaMXOFV001(
      TotalPages = 1,
      FirstAvailableDateTime = new Date(),
      LastAvailableDateTime = new Date()
    )
    ReadTransactionMXOFV001(transactions, Meta = meta, Links = links)
  }


  def createAccountBalanceJSON(moderatedAccount: ModeratedBankAccountCore) = {
    val accountId = moderatedAccount.accountId.value

    val dataJson = DataJsonMXOFV001(
      List(BalanceJsonMXOFV001(
        AccountId = accountId,
        Amount = AmountOfMoneyJsonV121(moderatedAccount.currency.getOrElse(""), moderatedAccount.balance.getOrElse("")),
        CreditDebitIndicator = "Credit",
        Type = "Available",
        DateTime = null,
        CreditLine = List(CreditLineJsonMXOFV001(
          Included = true,
          Amount = AmountOfMoneyJsonV121(moderatedAccount.currency.getOrElse(""), moderatedAccount.balance.getOrElse("")),
          Type = "Pre-Agreed"
        )))))

    AccountBalancesMXOFV001(
      Data = dataJson,
      Links = LinksMXOFV001(
        s"${Constant.HostName}/mx-open-finance/v0.0.1/accounts/${accountId}/balances",
        None,
        None,
        None,
        None),
      Meta = MetaMXOFV001(
        new Date(),
        new Date(),
        0
      )
    )
  }

  case class CreditLineJsonMXOFV001(
                                   Included: Boolean,
                                   Amount: AmountOfMoneyJsonV121,
                                   Type: String
                                 )

  case class BalanceJsonMXOFV001(
                                AccountId: String,
                                Amount: AmountOfMoneyJsonV121,
                                CreditDebitIndicator: String,
                                Type: String,
                                DateTime: Date,
                                CreditLine: List[CreditLineJsonMXOFV001]
                              )

  case class DataJsonMXOFV001(
                             Balance: List[BalanceJsonMXOFV001]
                           )

  case class AccountBalancesMXOFV001(
                                      Data: DataJsonMXOFV001,
                                      Links: LinksMXOFV001,
                                      Meta: MetaMXOFV001
                                  )


}
