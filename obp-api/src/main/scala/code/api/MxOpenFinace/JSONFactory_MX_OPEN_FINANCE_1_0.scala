package code.api.MxOpenFinace

import java.util.Date

import code.api.Constant
import code.api.util.CustomJsonFormats
import code.model.ModeratedBankAccountCore
import com.openbankproject.commons.model.BankAccount
import net.liftweb.json.JValue

case class JvalueCaseClass(jvalueToCaseclass: JValue)

object JSONFactory_MX_OPEN_FINANCE_1_0 extends CustomJsonFormats {

  case class MetaMXOF10(LastAvailableDateTime: Date, FirstAvailableDateTime: Date, TotalPages: Int)
  case class LinksMXOF10(Last: String, Prev: String, Next: String, Self: String, First: String)
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

  lazy val metaMocked = MetaMXOF10(
    LastAvailableDateTime = new Date(),
    FirstAvailableDateTime = new Date(),
    TotalPages = 0
  )
  lazy val linksMocked = LinksMXOF10(Last = "Last", Prev = "Prev", Next = "Next", Self = "Self", First = "First")

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

  def createReadAccountBasicJsonMXOFV10(account : ModeratedBankAccountCore): ReadAccountBasicMXOF10 = {
    val accountBasic = AccountBasicMXOF10(
      AccountId = account.accountId.value,
      Status = "",
      StatusUpdateDateTime = "",
      Currency = account.currency.getOrElse(""),
      AccountType = account.accountType.getOrElse(""),
      AccountSubType = "",
      AccountIndicator = "",
      OnboardingType = None,
      Nickname = account.label,
      OpeningDate = None,
      MaturityDate = None,
      Account = account.accountRoutings.headOption.map(e => 
        AccountDetailMXOF10(SchemeName = e.scheme, Identification = e.address, None)
      ),
      Servicer = None
    )
    val links = LinksMXOF10(
      s"${Constant.HostName}/mx-open-finance/v1.0/accounts/" + account.accountId.value,
      s"${Constant.HostName}/mx-open-finance/v1.0/accounts/" + account.accountId.value,
      s"${Constant.HostName}/mx-open-finance/v1.0/accounts/" + account.accountId.value,
      s"${Constant.HostName}/mx-open-finance/v1.0/accounts/" + account.accountId.value,
      s"${Constant.HostName}/mx-open-finance/v1.0/accounts/" + account.accountId.value)
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
      s"${Constant.HostName}/mx-open-finance/v1.0/accounts/",
      s"${Constant.HostName}/mx-open-finance/v1.0/accounts/",
      s"${Constant.HostName}/mx-open-finance/v1.0/accounts/",
      s"${Constant.HostName}/mx-open-finance/v1.0/accounts/")
    val meta = MetaMXOF10(
      TotalPages = 1,
      FirstAvailableDateTime = new Date(),
      LastAvailableDateTime = new Date()
    )
    ReadAccountBasicMXOF10(Meta = meta, Links = links, Data = DataAccountBasicMXOF10(Account = accountsBasic))
  }




}
