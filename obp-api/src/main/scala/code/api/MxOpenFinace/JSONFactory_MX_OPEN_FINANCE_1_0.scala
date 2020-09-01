package code.api.MxOpenFinace

import java.util.Date

import code.api.Constant
import code.api.util.CustomJsonFormats
import code.model.{ModeratedBankAccountCore, ModeratedTransaction}
import com.openbankproject.commons.model.BankAccount
import net.liftweb.json.JValue

case class JvalueCaseClass(jvalueToCaseclass: JValue)

object JSONFactory_MX_OPEN_FINANCE_1_0 extends CustomJsonFormats {

  case class MetaMXOF10(LastAvailableDateTime: Date, FirstAvailableDateTime: Date, TotalPages: Int)
  case class LinksMXOF10(Self: String, First: Option[String], Prev: Option[String], Next: Option[String], Last: Option[String] )
  case class ReadAccountBasicMXOF10(Data: DataAccountBasicMXOF10, Links: LinksMXOF10, Meta: MetaMXOF10)
  case class DataAccountBasicMXOF10(Account: List[AccountBasicMXOF10])
  case class ServicerMXOF10(SchemeName: String,
                            Identification: String)
  case class AccountBasicMXOF10(AccountId: String,
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
                                Account: Option[AccountDetailMXOF10],
                                Servicer: Option[ServicerMXOF10]
                               )
  case class AccountDetailMXOF10(
                                  SchemeName: String,
                                  Identification: String,
                                  Name: Option[String]
                                )

  case class AmountMXOF10(
    Amount: String,
    Currency: String
  )
  case class CurrencyExchangeMXOF10(
    SourceCurrency: String,
    TargetCurrency: String,
    UnitCurrency: String,
    ExchangeRate: Double,
    ContractIdentification: String,
    QuotationDate: String,
    InstructedAmount: AmountMXOF10
  )
  case class BankTransactionCodeMXOF10(
    Code: String,
    SubCode: String
  )
  case class CardInstrumentMXOF10(
    CardSchemeName: String,
    AuthorisationType: String,
    Name: String,
    Identification: String
  )
  case class AdditionalProp1MXOF10(

  )
  case class SupplementaryDataMXOF10(
    additionalProp1: AdditionalProp1MXOF10
  )
  
  case class TransactionBasicMXOF10(
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
    Amount: AmountMXOF10,
    CurrencyExchange: Option[CurrencyExchangeMXOF10],
    BankTransactionCode: Option[BankTransactionCodeMXOF10],
    CardInstrument: Option[CardInstrumentMXOF10],
    SupplementaryData: Option[SupplementaryDataMXOF10]
  )

  case class ReadTransactionMXOF10(Data: List[TransactionBasicMXOF10], Links: LinksMXOF10, Meta: MetaMXOF10)
  
  
  lazy val metaMocked = MetaMXOF10(
    LastAvailableDateTime = new Date(),
    FirstAvailableDateTime = new Date(),
    TotalPages = 0
  )
  lazy val linksMocked = LinksMXOF10(Self = "Self", None, None, None, None)

  lazy val accountBasic = AccountBasicMXOF10(
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
        AccountDetailMXOF10(
        SchemeName = "string",
        Identification = "string",
        Name = Some("string")
      )
    ),
    Servicer = Some(ServicerMXOF10(SchemeName = "string", Identification = "string"))
  )

  lazy val ofReadAccountBasic = ReadAccountBasicMXOF10(Meta = metaMocked, Links = linksMocked, Data = DataAccountBasicMXOF10(Account = List(accountBasic)))


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
                                        view: View): ReadAccountBasicMXOF10 = {
    
    val accountBasic = AccountBasicMXOF10(
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
        case Constant.READ_ACCOUNT_DETAIL_VIEW_ID =>
          account.accountRoutings.headOption.map(e =>
            AccountDetailMXOF10(SchemeName = e.scheme, Identification = e.address, None)
          )
        case _ => 
          None
      },
      Servicer = view.viewId.value match {
        case Constant.READ_ACCOUNT_DETAIL_VIEW_ID =>
          None
        case _ =>
          None
      }
    )
    val links = LinksMXOF10(
      s"${Constant.HostName}/mx-open-finance/v1.0/accounts/" + account.accountId.value,
      None,
      None,
      None,
      None
    )
    val meta = MetaMXOF10(
      TotalPages = 1,
      FirstAvailableDateTime = new Date(),
      LastAvailableDateTime = new Date()
    )
    ReadAccountBasicMXOF10(Meta = meta, Links = links, Data = DataAccountBasicMXOF10(Account = List(accountBasic)))
  }
  def createReadAccountsBasicJsonMXOFV10(accounts : List[BankAccount]): ReadAccountBasicMXOF10 = {
    val accountsBasic = accounts.map(account =>
      AccountBasicMXOF10(
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
          AccountDetailMXOF10(SchemeName = e.scheme, Identification = e.address, None)
        ),
        Servicer = None
      )
    )
    val links = LinksMXOF10(
      s"${Constant.HostName}/mx-open-finance/v1.0/accounts/",
      None,
      None,
      None,
      None
    )
    val meta = MetaMXOF10(
      TotalPages = 1,
      FirstAvailableDateTime = new Date(),
      LastAvailableDateTime = new Date()
    )
    ReadAccountBasicMXOF10(Meta = meta, Links = links, Data = DataAccountBasicMXOF10(Account = accountsBasic))
  }

  def createGetTransactionsByAccountIdMXOFV10(moderatedTransactions : List[ModeratedTransaction]): ReadTransactionMXOF10 = {

    val accountId = moderatedTransactions.map(_.bankAccount.map(_.accountId.value)).flatten.headOption.getOrElse("")
    
    val transactions = moderatedTransactions.map(
      moderatedTransaction =>
        TransactionBasicMXOF10(
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
        Amount = AmountMXOF10(
          moderatedTransaction.amount.map(_.bigDecimal.toString).getOrElse(null),
          moderatedTransaction.currency.getOrElse(null),
        ),
        CurrencyExchange = None,
        BankTransactionCode = None,
        CardInstrument = None,
        SupplementaryData = None
      )
    )
    
    val links = LinksMXOF10(
      s"${Constant.HostName}/mx-open-finance/v1.0/accounts/" + accountId + "/transactions",
      None,
      None,
      None,
      None
    )
    
    val meta = MetaMXOF10(
      TotalPages = 1,
      FirstAvailableDateTime = new Date(),
      LastAvailableDateTime = new Date()
    )
    ReadTransactionMXOF10(transactions, Meta = meta, Links = links)
  }




}
