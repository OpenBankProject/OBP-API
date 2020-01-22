package code.api.AUOpenBanking.v1_0_0

import code.api.util.APIUtil.getServerUrl
import code.api.util.CustomJsonFormats
import com.openbankproject.commons.model.{BankAccount, CoreAccount}
import com.openbankproject.commons.util.ScannedApiVersion

import scala.collection.immutable.List

case class AccountJson(
  accountId: String,
  creationDate: String,
  displayName: String,
  nickname: String,
  openStatus: String,
  isOwned: Boolean,
  maskedNumber: String,
  productCategory: String,
  productName: String
)
case class AccountsDataJson(
  accounts: List[AccountJson]
)
case class LinksJson(
  self: String,
  first: String,
  prev: String,
  next: String,
  last: String
)
case class AccountsMetaJson(
  totalRecords: Double,
  totalPages: Double
)
case class AccountListJson(
  data: AccountsDataJson,
  links: LinksJson,
  meta: AccountsMetaJson
)
case class PursesJson(
  amount: String,
  currency: String
)
case class BalanceDataJson(
  accountId: String,
  currentBalance: String,
  availableBalance: String,
  currency: String,
  amortisedLimit: String,
  creditLimit: String,
  purses: List[PursesJson]
)
case class BalacenMetaJson(
)
case class BalanceLinks(
  self: String
)
case class AccountBalanceJson(
  data: BalanceDataJson,
  meta: BalacenMetaJson,
  links: BalanceLinks
)


object JSONFactory_AU_OpenBanking_1_0_0 extends CustomJsonFormats {
  def createListAccountsJson(coreAccounts : List[CoreAccount]) ={
    val accountsJson = coreAccounts.map(obpCoreAccount =>AccountJson(
      accountId = obpCoreAccount.id,
      creationDate = "",
      displayName = obpCoreAccount.label,
      nickname ="",
      openStatus = "Open",
      isOwned = true,
      maskedNumber = "String",
      productCategory= obpCoreAccount.accountType,
      productName= obpCoreAccount.accountType
    ))
    val totalRecords = coreAccounts.length.toDouble
    val totalPages = 1.toDouble
    val links = LinksJson(
      self = s"$getServerUrl/${ApiCollector.apiVersion.asInstanceOf[ScannedApiVersion].urlPrefix}/${ApiCollector.version}/banking/accounts",
      first =s"$getServerUrl/${ApiCollector.apiVersion.asInstanceOf[ScannedApiVersion].urlPrefix}/${ApiCollector.version}/banking/accounts",
      prev =s"$getServerUrl/${ApiCollector.apiVersion.asInstanceOf[ScannedApiVersion].urlPrefix}/${ApiCollector.version}/banking/accounts",
      next =s"$getServerUrl/${ApiCollector.apiVersion.asInstanceOf[ScannedApiVersion].urlPrefix}/${ApiCollector.version}/banking/accounts",
      last =s"$getServerUrl/${ApiCollector.apiVersion.asInstanceOf[ScannedApiVersion].urlPrefix}/${ApiCollector.version}/banking/accounts",
    )
    val meta = AccountsMetaJson(
      totalRecords,
      totalPages
    )
    AccountListJson(
      AccountsDataJson(accountsJson),
      links,
      meta
    )
  }

  def createAccountBalanceJson(account : BankAccount) = {
    val balanceDataJson = BalanceDataJson(
      accountId = account.accountId.value,
      currentBalance = account.balance.toString(),
      availableBalance = account.balance.toString(),
      currency = account.currency,
      amortisedLimit ="",
      creditLimit ="",
      purses = Nil
    )
    val meta = BalacenMetaJson()
    val links =  BalanceLinks(
      self = s"$getServerUrl/${ApiCollector.apiVersion.asInstanceOf[ScannedApiVersion].urlPrefix}/${ApiCollector.version}/banking/accounts/${account.accountId.value}/balance",
    )
    AccountBalanceJson(
      balanceDataJson,
      meta,
      links
    )
  }
}
