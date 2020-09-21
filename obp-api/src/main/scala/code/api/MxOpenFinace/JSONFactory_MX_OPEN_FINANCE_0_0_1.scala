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
    UnitCurrency: Option[String],
    ExchangeRate: String,
    ContractIdentification: Option[String],
    QuotationDate: Option[String],
    InstructedAmount: Option[AmountMXOFV001]
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

  case class BalanceMXOFV001(
                              AccountIndicator: String,
                              Type: String = "ClosingAvailable",
                              Amount: AmountMXOFV001
                            )

  case class MerchantDetailsMXOFV001(
                                      MerchantName: Option[String],
                                      MerchantCategoryCode: Option[String]
                                    )

  case class TransactionRecipientMXOFV001(
                                           SchemeName: Option[String],
                                           Identification: Option[String],
                                           Name: String
                                         )

  case class RecipientAccountMXOFV001(
                                       SchemeName: Option[String],
                                       Identification: String,
                                       Name: Option[String]
                                     )

  case class TransactionSenderMXOFV001(
                                        SchemeName: Option[String],
                                        Identification: Option[String],
                                        Name: String
                                      )

  case class SenderAccountMXOFV001(
                                    SchemeName: Option[String],
                                    Identification: String,
                                    Name: Option[String]
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
                                       Balance: Option[BalanceMXOFV001],
                                       MerchantDetails: Option[MerchantDetailsMXOFV001],
                                       TransactionRecipient: Option[TransactionRecipientMXOFV001],
                                       RecipientAccount: Option[RecipientAccountMXOFV001],
                                       TransactionSender: Option[TransactionSenderMXOFV001],
                                       SenderAccount: Option[SenderAccountMXOFV001],
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
    DateTime = new Date(),
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

  private def accountAttributeOptValue(name: String, 
                                            bankId: BankId, 
                                            accountId: AccountId, 
                                            list: List[AccountAttribute]): Option[String] =
    list.filter(e => e.name == name && e.bankId == bankId && e.accountId == accountId).headOption.map(_.value)
  private def accountAttributeValue(name: String, 
                                    bankId: BankId,
                                    accountId: AccountId, 
                                    list: List[AccountAttribute]): String =
    accountAttributeOptValue(name, bankId, accountId, list).getOrElse("")

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
  
  def createReadAccountBasicJsonMXOFV10(account : ModeratedBankAccountCore, 
                                        moderatedAttributes: List[AccountAttribute],
                                        view: View): ReadAccountBasicMXOFV001 = {
    
    val accountBasic = AccountBasicMXOFV001(
      AccountId = account.accountId.value,
      Status = accountAttributeValue("Status", account.bankId, account.accountId, moderatedAttributes),
      StatusUpdateDateTime = accountAttributeValue("StatusUpdateDateTime", account.bankId, account.accountId, moderatedAttributes),
      Currency = account.currency.getOrElse(""),
      AccountType = account.accountType.getOrElse(""),
      AccountSubType = accountAttributeValue("AccountSubType", account.bankId, account.accountId, moderatedAttributes),
      AccountIndicator = accountAttributeValue("AccountIndicator", account.bankId, account.accountId, moderatedAttributes),
      OnboardingType = accountAttributeOptValue("OnboardingType", account.bankId, account.accountId, moderatedAttributes),
      Nickname = account.label,
      OpeningDate = accountAttributeOptValue("OpeningDate", account.bankId, account.accountId, moderatedAttributes),
      MaturityDate = accountAttributeOptValue("MaturityDate", account.bankId, account.accountId, moderatedAttributes),
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
          Some(
            ServicerMXOFV001(
              SchemeName = accountAttributeValue("Servicer_SchemeName", account.bankId, account.accountId, moderatedAttributes),
              Identification = accountAttributeValue("Servicer_Identification", account.bankId, account.accountId, moderatedAttributes)
            )
          )
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
  def createReadAccountsBasicJsonMXOFV10(accounts : List[(BankAccount, View)],
                                         moderatedAttributes: List[AccountAttribute]): ReadAccountBasicMXOFV001 = {
    val accountsBasic = accounts.map(account =>
      AccountBasicMXOFV001(
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
        Account = account._2.viewId.value match {
          case Constant.READ_ACCOUNTS_DETAIL_VIEW_ID =>
            account._1.accountRoutings.headOption.map(e =>
              AccountDetailMXOFV001(SchemeName = e.scheme, Identification = e.address, None)
            )
          case _ =>
            None
        },
        Servicer = account._2.viewId.value match {
          case Constant.READ_ACCOUNTS_DETAIL_VIEW_ID =>
            Some(
              ServicerMXOFV001(
                SchemeName = accountAttributeValue("Servicer_SchemeName", account._1.bankId, account._1.accountId, moderatedAttributes),
                Identification = accountAttributeValue("Servicer_Identification", account._1.bankId, account._1.accountId, moderatedAttributes)
              )
            )
          case _ =>
            None
        }
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

  def createGetTransactionsByAccountIdMXOFV10(bankId: BankId,
                                              moderatedTransactions : List[ModeratedTransaction],
                                              attributes: List[TransactionAttribute],
                                              view: View): ReadTransactionMXOFV001 = {

    val accountId = moderatedTransactions.map(_.bankAccount.map(_.accountId.value)).flatten.headOption.getOrElse("")
    
    val transactions = moderatedTransactions.map(
      moderatedTransaction =>
        TransactionBasicMXOFV001(
        AccountId = accountId,
        TransactionId = moderatedTransaction.id.value,
        TransactionReference = transactionAttributeOptValue("TransactionReference", bankId, moderatedTransaction.id, attributes),
        TransferTracingCode = transactionAttributeOptValue("TransferTracingCode", bankId, moderatedTransaction.id, attributes),
        AccountIndicator = moderatedTransaction.bankAccount.map(_.accountType).flatten.getOrElse(null),
        Status = "BOOKED", // [ Booked, Pending, Cancelled ]
        BookingDateTime = moderatedTransaction.startDate.map(_.toString).getOrElse(null),
        ValueDateTime = transactionAttributeOptValue("ValueDateTime", bankId, moderatedTransaction.id, attributes),
        TransactionInformation = moderatedTransaction.description.getOrElse(null),
        AddressLine = transactionAttributeOptValue("AddressLine", bankId, moderatedTransaction.id, attributes),
        Amount = AmountMXOFV001(
          moderatedTransaction.amount.map(_.bigDecimal.toString).getOrElse(null),
          moderatedTransaction.currency.getOrElse(null),
        ),
        CurrencyExchange = Some(CurrencyExchangeMXOFV001(
          SourceCurrency = transactionAttributeValue("CurrencyExchange_SourceCurrency", bankId, moderatedTransaction.id, attributes),
          TargetCurrency = transactionAttributeValue("CurrencyExchange_TargetCurrency", bankId, moderatedTransaction.id, attributes),
          UnitCurrency = transactionAttributeOptValue("CurrencyExchange_UnitCurrency", bankId, moderatedTransaction.id, attributes),
          ExchangeRate = transactionAttributeValue("CurrencyExchange_ExchangeRate", bankId, moderatedTransaction.id, attributes),
          ContractIdentification = transactionAttributeOptValue("ContractIdentification", bankId, moderatedTransaction.id, attributes),
          QuotationDate = transactionAttributeOptValue("CurrencyExchange_QuotationDate", bankId, moderatedTransaction.id, attributes),
          InstructedAmount = Some(
            AmountMXOFV001(
              Amount = transactionAttributeValue("CurrencyExchange_InstructedAmount_Amount", bankId, moderatedTransaction.id, attributes), 
              Currency = transactionAttributeValue("CurrencyExchange_InstructedAmount_Currency", bankId, moderatedTransaction.id, attributes), 
            )
          )
        )),
        BankTransactionCode = Some(
          BankTransactionCodeMXOFV001(
            Code = transactionAttributeValue("BankTransactionCode_Code", bankId, moderatedTransaction.id, attributes),
            SubCode = transactionAttributeValue("BankTransactionCode_SubCode", bankId, moderatedTransaction.id, attributes),
          )
        ),
        Balance = view.viewId.value match {
          case Constant.READ_TRANSACTIONS_DETAIL_VIEW_ID =>
            Some(
              BalanceMXOFV001(
                AccountIndicator = transactionAttributeValue("Balance_AccountIndicator", bankId, moderatedTransaction.id, attributes),
                Type = transactionAttributeValue("Balance_AccountIndicator", bankId, moderatedTransaction.id, attributes),
                Amount = AmountMXOFV001(
                  Amount = transactionAttributeValue("Balance_Amount_Amount", bankId, moderatedTransaction.id, attributes),
                  Currency = transactionAttributeValue("Balance_Amount_Currency", bankId, moderatedTransaction.id, attributes),
                )
              )
            )
          case _ =>  None
        }, 
        MerchantDetails = view.viewId.value match {
          case Constant.READ_TRANSACTIONS_DETAIL_VIEW_ID =>
            Some(
              MerchantDetailsMXOFV001(
                MerchantName = transactionAttributeOptValue("MerchantDetails_MerchantName", bankId, moderatedTransaction.id, attributes),
                MerchantCategoryCode = transactionAttributeOptValue("MerchantDetails_MerchantCategoryCode", bankId, moderatedTransaction.id, attributes),
              )
            )
          case _ =>  None
        },
        TransactionRecipient = view.viewId.value match {
          case Constant.READ_TRANSACTIONS_DETAIL_VIEW_ID => 
            Some(
              TransactionRecipientMXOFV001(
                SchemeName = transactionAttributeOptValue("TransactionRecipient_SchemeName", bankId, moderatedTransaction.id, attributes),
                Identification = transactionAttributeOptValue("TransactionRecipient_Identification", bankId, moderatedTransaction.id, attributes),
                Name = transactionAttributeValue("TransactionRecipient_Name", bankId, moderatedTransaction.id, attributes),
              )
            )
          case _ =>  None
        },
        RecipientAccount = view.viewId.value match {
          case Constant.READ_TRANSACTIONS_DETAIL_VIEW_ID =>
            Some(
              RecipientAccountMXOFV001(
                SchemeName = transactionAttributeOptValue("RecipientAccount_SchemeName", bankId, moderatedTransaction.id, attributes),
                Identification = transactionAttributeValue("RecipientAccount_Identification", bankId, moderatedTransaction.id, attributes),
                Name = transactionAttributeOptValue("RecipientAccount_Name", bankId, moderatedTransaction.id, attributes),
              )
            )
          case _ =>  None
        },
        TransactionSender = view.viewId.value match {
          case Constant.READ_TRANSACTIONS_DETAIL_VIEW_ID =>
            Some(
              TransactionSenderMXOFV001(
                SchemeName = transactionAttributeOptValue("TransactionSender_SchemeName", bankId, moderatedTransaction.id, attributes),
                Identification = transactionAttributeOptValue("TransactionSender_Identification", bankId, moderatedTransaction.id, attributes),
                Name = transactionAttributeValue("TransactionSender_Name", bankId, moderatedTransaction.id, attributes),
              )
            )
          case _ =>  None
        },
        SenderAccount = view.viewId.value match {
          case Constant.READ_TRANSACTIONS_DETAIL_VIEW_ID =>
            Some(
              SenderAccountMXOFV001(
                SchemeName = transactionAttributeOptValue("SenderAccount_SchemeName", bankId, moderatedTransaction.id, attributes),
                Identification = transactionAttributeValue("SenderAccount_Identification", bankId, moderatedTransaction.id, attributes),
                Name = transactionAttributeOptValue("SenderAccount_Name", bankId, moderatedTransaction.id, attributes),
              )
            )
          case _ =>  None
        },
        CardInstrument = Some(
          CardInstrumentMXOFV001(
            CardSchemeName = transactionAttributeValue("CardInstrument_CardSchemeName", bankId, moderatedTransaction.id, attributes),
            AuthorisationType = transactionAttributeValue("CardInstrument_AuthorisationType", bankId, moderatedTransaction.id, attributes),
            Name = transactionAttributeValue("CardInstrument_Name", bankId, moderatedTransaction.id, attributes),
            Identification = transactionAttributeValue("CardInstrument_Identification", bankId, moderatedTransaction.id, attributes),
          )
        ),
        SupplementaryData = None,
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
