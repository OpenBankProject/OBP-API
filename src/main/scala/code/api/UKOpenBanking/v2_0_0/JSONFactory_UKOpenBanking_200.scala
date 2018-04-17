package code.api.UKOpenBanking.v2_0_0

import code.api.Constant
import code.model.BankAccount

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
      Links = Links(Self = Constant.HostName + "/open-banking/v2.0/accounts/" + list.head.AccountId),
      Meta = Meta(TotalPages = 1)
    )
  }

}
