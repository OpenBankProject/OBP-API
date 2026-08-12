package code.api.UKOpenBanking.v4_0_1

import code.api.UKOpenBanking.UKAmounts

import java.util.Date

import code.api.Constant
import code.api.util.CustomJsonFormats
import code.model.{ModeratedBankAccountCore, ModeratedTransaction}
import com.openbankproject.commons.model.{AccountAttribute, AccountId, BankAccount, BankId, TransactionAttribute, View}

import scala.collection.immutable.List

/**
 * JSON shaping for the UK Open Banking v4.0.1 AIS/consent endpoints that are
 * backed by real OBP connector data (see Http4sUKOBv401AccountInfo).
 *
 * v4.0.1 changed the Account/Balance/Transaction resource shapes relative to
 * v3.1 (e.g. AccountTypeCode replaces AccountType, Account.SchemeName is a
 * single string not a list, Balance carries a top-level TotalValue, Transaction
 * adds TransactionMutability/Status codes) so the v3.1 factory cannot be reused
 * as-is. The connector calls that produce the underlying data are unchanged
 * from v3.1 (see Http4sUKOBv310Accounts/Balances/Transactions).
 */
object JSONFactory_UKOpenBanking_401 extends CustomJsonFormats {

  case class LinksV401(
    Self: String,
    First: String,
    Prev: String,
    Next: String,
    Last: String
  )

  case class MetaV401(
    TotalPages: Int,
    FirstAvailableDateTime: Date,
    LastAvailableDateTime: Date
  )

  case class AmountV401(
    Amount: String,
    Currency: String,
    SubType: Option[String] = None
  )

  // ---------------------------------------------------------------------
  // Accounts
  // ---------------------------------------------------------------------
  case class AccountInnerV401(
    SchemeName: String,
    Identification: String,
    Name: Option[String] = None,
    SecondaryIdentification: Option[String] = None
  )

  case class ServicerV401(
    SchemeName: String,
    Identification: String,
    Name: Option[String] = None
  )

  case class AccountV401(
    AccountId: String,
    Status: String,
    StatusUpdateDateTime: Option[String] = None,
    Currency: String,
    AccountCategory: Option[String] = None,
    AccountTypeCode: String,
    Description: Option[String] = None,
    Nickname: Option[String] = None,
    OpeningDate: Option[String] = None,
    MaturityDate: Option[String] = None,
    SwitchStatus: Option[String] = None,
    Account: List[AccountInnerV401] = Nil,
    Servicer: Option[ServicerV401] = None
  )

  case class AccountListV401(Account: List[AccountV401])
  case class AccountsUKV401(Data: AccountListV401, Links: LinksV401, Meta: MetaV401)

  // ---------------------------------------------------------------------
  // Balances
  // ---------------------------------------------------------------------
  case class CreditLineV401(Included: Boolean, Type: String, Amount: AmountV401)

  case class BalanceV401(
    AccountId: String,
    CreditDebitIndicator: String,
    Type: String,
    DateTime: Option[Date],
    Amount: AmountV401,
    LocalAmount: AmountV401,
    CreditLine: List[CreditLineV401] = Nil
  )

  case class BalanceDataV401(Balance: List[BalanceV401], TotalValue: Option[AmountV401] = None)
  case class BalancesUKV401(Data: BalanceDataV401, Links: LinksV401, Meta: MetaV401)

  // ---------------------------------------------------------------------
  // Transactions
  // ---------------------------------------------------------------------
  case class MerchantDetailsV401(
    MerchantName: Option[String] = None,
    MerchantCategoryCode: Option[String] = None
  )

  case class TransactionBalanceV401(
    CreditDebitIndicator: String,
    Type: String,
    Amount: AmountV401
  )

  case class TransactionV401(
    AccountId: String,
    TransactionId: String,
    TransactionReference: Option[String] = None,
    StatementReference: List[String] = Nil,
    // No default: the direction has to be derived from the amount at every construction site
    // (see UKAmounts). A default is how "Credit" ended up on every debit.
    CreditDebitIndicator: String,
    Status: String = "BOOK",
    TransactionMutability: String = "Mutable",
    BookingDateTime: Date,
    ValueDateTime: Date,
    TransactionInformation: Option[String] = None,
    Amount: AmountV401,
    ChargeAmount: Option[AmountV401] = None,
    Balance: Option[TransactionBalanceV401] = None,
    MerchantDetails: Option[MerchantDetailsV401] = None
  )

  case class TransactionDataV401(Transaction: List[TransactionV401])
  case class TransactionsUKV401(Data: TransactionDataV401, Links: LinksV401, Meta: MetaV401)

  // ---------------------------------------------------------------------
  // Consent
  // ---------------------------------------------------------------------
  case class StatusReasonV401(
    StatusReasonCode: String,
    StatusReasonDescription: String
  )
  case class ConsentDataV401(
    ConsentId: String,
    CreationDateTime: String,
    Status: String,
    StatusReason: List[StatusReasonV401],
    StatusUpdateDateTime: String,
    Permissions: List[String],
    ExpirationDateTime: Option[String],
    TransactionFromDateTime: Option[String],
    TransactionToDateTime: Option[String]
  )
  case class ConsentResponseV401(
    Data: ConsentDataV401,
    Risk: Map[String, String],
    Links: LinksV401,
    Meta: MetaV401
  )

  private def links(path: String): LinksV401 = {
    val url = s"${Constant.HostName}/open-banking/v4.0.1$path"
    LinksV401(Self = url, First = url, Prev = url, Next = url, Last = url)
  }

  private def meta(): MetaV401 = MetaV401(TotalPages = 1, FirstAvailableDateTime = new Date(), LastAvailableDateTime = new Date())

  private def accountAttributeOptValue(name: String, bankId: BankId, accountId: AccountId, list: List[AccountAttribute]): Option[String] =
    list.find(e => e.name == name && e.bankId == bankId && e.accountId == accountId).map(_.value)

  private def transactionAttributeOptValue(name: String, bankId: BankId, transactionId: com.openbankproject.commons.model.TransactionId, list: List[TransactionAttribute]): Option[String] =
    list.find(e => e.name == name && e.bankId == bankId && e.transactionId == transactionId).map(_.value)

  def createAccountsListJSON(
    accounts: List[(BankAccount, View)],
    moderatedAttributes: List[AccountAttribute]
  ): AccountsUKV401 = {

    def getServicer(account: (BankAccount, View)): Option[ServicerV401] = {
      account._2.viewId.value match {
        case Constant.SYSTEM_READ_ACCOUNTS_DETAIL_VIEW_ID =>
          val schemeName = accountAttributeOptValue("Servicer_SchemeName", account._1.bankId, account._1.accountId, moderatedAttributes)
          val identification = accountAttributeOptValue("Servicer_Identification", account._1.bankId, account._1.accountId, moderatedAttributes)
          (schemeName, identification) match {
            case (Some(scheme), Some(id)) => Some(ServicerV401(SchemeName = scheme, Identification = id))
            case _ => None
          }
        case _ => None
      }
    }

    def getAccountDetails(account: (BankAccount, View)): Option[AccountInnerV401] = {
      account._2.viewId.value match {
        case Constant.SYSTEM_READ_ACCOUNTS_DETAIL_VIEW_ID =>
          account._1.accountRoutings.headOption.map(e =>
            AccountInnerV401(SchemeName = e.scheme, Identification = e.address)
          )
        case _ => None
      }
    }

    val list = accounts.map { account =>
      AccountV401(
        AccountId = account._1.accountId.value,
        Status = accountAttributeOptValue("Status", account._1.bankId, account._1.accountId, moderatedAttributes).getOrElse("Enabled"),
        StatusUpdateDateTime = accountAttributeOptValue("StatusUpdateDateTime", account._1.bankId, account._1.accountId, moderatedAttributes),
        Currency = account._1.currency,
        AccountCategory = accountAttributeOptValue("AccountCategory", account._1.bankId, account._1.accountId, moderatedAttributes),
        AccountTypeCode = account._1.accountType,
        Nickname = Some(account._1.label),
        OpeningDate = accountAttributeOptValue("OpeningDate", account._1.bankId, account._1.accountId, moderatedAttributes),
        MaturityDate = accountAttributeOptValue("MaturityDate", account._1.bankId, account._1.accountId, moderatedAttributes),
        SwitchStatus = accountAttributeOptValue("SwitchStatus", account._1.bankId, account._1.accountId, moderatedAttributes),
        Account = getAccountDetails(account).toList,
        Servicer = getServicer(account)
      )
    }
    AccountsUKV401(
      Data = AccountListV401(list),
      Links = links("/aisp/accounts"),
      Meta = meta()
    )
  }

  def createAccountBalanceJSON(moderatedAccount: ModeratedBankAccountCore): BalancesUKV401 = {
    val accountId = moderatedAccount.accountId.value
    val rawBalance = moderatedAccount.balance.getOrElse("")
    val amount = AmountV401(
      Amount = UKAmounts.unsignedAmountString(rawBalance),
      Currency = moderatedAccount.currency.getOrElse("")
    )
    val balance = BalanceV401(
      AccountId = accountId,
      CreditDebitIndicator = UKAmounts.creditDebitIndicatorOfString(rawBalance),
      Type = "ClosingAvailable",
      DateTime = None,
      Amount = amount,
      LocalAmount = amount
    )
    BalancesUKV401(
      Data = BalanceDataV401(Balance = List(balance), TotalValue = Some(amount)),
      Links = links(s"/aisp/accounts/$accountId/balances"),
      Meta = meta()
    )
  }

  def createBalancesJSON(accounts: List[BankAccount]): BalancesUKV401 = {
    val balances = accounts.map { account =>
      val amount = AmountV401(Amount = UKAmounts.unsignedAmount(account.balance), Currency = account.currency)
      BalanceV401(
        AccountId = account.accountId.value,
        CreditDebitIndicator = UKAmounts.creditDebitIndicator(account.balance),
        Type = "ClosingAvailable",
        DateTime = Some(account.lastUpdate),
        Amount = amount,
        LocalAmount = amount
      )
    }
    val totalValue = balances.headOption.map(_.Amount)
    BalancesUKV401(
      Data = BalanceDataV401(Balance = balances, TotalValue = totalValue),
      Links = links("/aisp/balances"),
      Meta = meta()
    )
  }

  private def transactionJson(
    accountId: String,
    bankId: BankId,
    moderatedTransaction: ModeratedTransaction,
    attributes: List[TransactionAttribute],
    view: Option[View]
  ): TransactionV401 = {
    def getMerchantDetails: Option[MerchantDetailsV401] = view match {
      case Some(v) if v.viewId.value == Constant.SYSTEM_READ_TRANSACTIONS_DETAIL_VIEW_ID =>
        val merchantName = transactionAttributeOptValue("MerchantDetails_MerchantName", bankId, moderatedTransaction.id, attributes)
        val merchantCategoryCode = transactionAttributeOptValue("MerchantDetails_CategoryCode", bankId, moderatedTransaction.id, attributes)
        if (merchantName.isDefined || merchantCategoryCode.isDefined)
          Some(MerchantDetailsV401(MerchantName = merchantName, MerchantCategoryCode = merchantCategoryCode))
        else None
      case _ => None
    }

    // UK keeps the sign out of Amount and in CreditDebitIndicator; OBP holds one signed number.
    val amount = AmountV401(
      Amount = UKAmounts.unsignedAmount(moderatedTransaction.amount),
      Currency = moderatedTransaction.currency.getOrElse("")
    )
    TransactionV401(
      AccountId = accountId,
      TransactionId = moderatedTransaction.id.value,
      TransactionReference = moderatedTransaction.description,
      TransactionInformation = moderatedTransaction.description,
      BookingDateTime = moderatedTransaction.startDate.get,
      ValueDateTime = moderatedTransaction.finishDate.get,
      Amount = amount,
      CreditDebitIndicator = UKAmounts.creditDebitIndicator(moderatedTransaction.amount),
      Balance = Some(TransactionBalanceV401(
        CreditDebitIndicator = UKAmounts.creditDebitIndicatorOfString(moderatedTransaction.balance),
        Type = "InterimBooked",
        Amount = AmountV401(
          Amount = UKAmounts.unsignedAmountString(moderatedTransaction.balance),
          Currency = moderatedTransaction.currency.getOrElse(""))
      )),
      MerchantDetails = getMerchantDetails
    )
  }

  def createTransactionsJsonNew(
    bankId: BankId,
    accountId: String,
    moderatedTransactions: List[ModeratedTransaction],
    attributes: List[TransactionAttribute],
    view: View
  ): TransactionsUKV401 = {
    val transactions = moderatedTransactions.map(t => transactionJson(accountId, bankId, t, attributes, Some(view)))
    TransactionsUKV401(
      Data = TransactionDataV401(transactions),
      Links = links(s"/aisp/accounts/$accountId/transactions"),
      Meta = meta()
    )
  }

  def createTransactionsJson(bankId: BankId, moderatedTransactions: List[ModeratedTransaction]): TransactionsUKV401 = {
    val transactions = moderatedTransactions.map { t =>
      val accountId = t.bankAccount.map(_.accountId.value).orNull
      transactionJson(accountId, bankId, t, Nil, None)
    }
    TransactionsUKV401(
      Data = TransactionDataV401(transactions),
      Links = links("/aisp/transactions"),
      Meta = meta()
    )
  }

  // v4.0.1 uses ISO 20022-style four-letter status codes on the wire (AWAU/AUTH/RJCT/CANC/EXPD),
  // whereas OBP stores the long enum names (AWAITINGAUTHORISATION/AUTHORISED/REJECTED/REVOKED/EXPIRED).
  // Map at the serialization boundary only — storage keeps the long names. REVOKED maps to CANC
  // because the spec's revoke-side status is CANC and OBP does not distinguish dashboard-cancel from
  // AISP-DELETE-revoke. Unknown/other statuses pass through unchanged so nothing is silently hidden.
  def ukConsentStatusCode(status: String): String = status.toUpperCase match {
    case "AWAITINGAUTHORISATION" => "AWAU"
    case "AUTHORISED"            => "AUTH"
    case "REJECTED"              => "RJCT"
    case "REVOKED"               => "CANC"
    case "EXPIRED"               => "EXPD"
    case _                       => status
  }

  // Minimal StatusReason per the current status, mirroring the spec's own example wording/codes
  // (OBReadConsentResponse1). Richer per-transition reasons can follow later.
  private def ukConsentStatusReason(fourLetterCode: String): List[StatusReasonV401] = fourLetterCode match {
    case "AWAU" => List(StatusReasonV401("U036", "Waiting for completion of consent authorisation to be completed by user"))
    case "AUTH" => List(StatusReasonV401("U110", "The account access consent has been successfully authorised"))
    case "RJCT" => List(StatusReasonV401("U111", "The account access consent has been rejected"))
    case "CANC" => List(StatusReasonV401("U112", "The account access consent has been cancelled"))
    case "EXPD" => List(StatusReasonV401("U113", "The account access consent has passed its expiry date"))
    case _      => Nil
  }

  def createConsentResponseJSON(
    consentId: String,
    creationDateTime: String,
    status: String,
    statusUpdateDateTime: String,
    permissions: List[String],
    expirationDateTime: Option[String],
    transactionFromDateTime: Option[String],
    transactionToDateTime: Option[String],
    selfPath: String
  ): ConsentResponseV401 = {
    val statusCode = ukConsentStatusCode(status)
    ConsentResponseV401(
      Data = ConsentDataV401(
        ConsentId = consentId,
        CreationDateTime = creationDateTime,
        Status = statusCode,
        StatusReason = ukConsentStatusReason(statusCode),
        StatusUpdateDateTime = statusUpdateDateTime,
        Permissions = permissions,
        ExpirationDateTime = expirationDateTime,
        TransactionFromDateTime = transactionFromDateTime,
        TransactionToDateTime = transactionToDateTime
      ),
      Risk = Map.empty,
      Links = links(selfPath),
      Meta = meta()
    )
  }

}
