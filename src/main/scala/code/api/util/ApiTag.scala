package code.api.util

import scala.collection.mutable.{Map => MutableMap}

object ApiTag {
  // Used to tag Resource Docs
  case class ResourceDocTag(tag: String)

  // Use the *singular* case. for both the variable name and string.
  // e.g. "This call is Payment related"
  val apiTagTransactionRequest = ResourceDocTag("Transaction-Request")
  val apiTagApi = ResourceDocTag("API")
  val apiTagBank = ResourceDocTag("Bank")
  val apiTagAccount = ResourceDocTag("Account")
  val apiTagAccountApplication = ResourceDocTag("Account-Application")
  val apiTagAccountPublic = ResourceDocTag("Account-Public")
  val apiTagAccountFirehose = ResourceDocTag("Account-Firehose")
  val apiTagFirehoseData = ResourceDocTag("FirehoseData")
  val apiTagPublicData = ResourceDocTag("PublicData")
  val apiTagPrivateData = ResourceDocTag("PrivateData")
  val apiTagTransaction = ResourceDocTag("Transaction")
  val apiTagTransactionFirehose = ResourceDocTag("Transaction-Firehose")
  val apiTagCounterpartyMetaData = ResourceDocTag("Counterparty-Metadata")
  val apiTagTransactionMetaData = ResourceDocTag("Transaction-Metadata")
  val apiTagView = ResourceDocTag("View")
  val apiTagEntitlement = ResourceDocTag("Entitlement")
  val apiTagRole = ResourceDocTag("Role")
  val apiTagScope = ResourceDocTag("Scope")
  val apiTagOwnerRequired = ResourceDocTag("OwnerViewRequired")
  val apiTagCounterparty = ResourceDocTag("Counterparty")
  val apiTagKyc = ResourceDocTag("KYC")
  val apiTagCustomer = ResourceDocTag("Customer")
  val apiTagOnboarding = ResourceDocTag("Onboarding")
  val apiTagUser = ResourceDocTag("User")
  val apiTagMeeting = ResourceDocTag("Customer-Meeting")
  val apiTagExperimental = ResourceDocTag("Experimental")
  val apiTagPerson = ResourceDocTag("Person")
  val apiTagCard = ResourceDocTag("Card")
  val apiTagSandbox = ResourceDocTag("Sandbox")
  val apiTagBranch = ResourceDocTag("Branch")
  val apiTagATM = ResourceDocTag("ATM")
  val apiTagProduct = ResourceDocTag("Product")
  val apiTagOpenData = ResourceDocTag("Open-Data")
  val apiTagConsumer = ResourceDocTag("Consumer")
  val apiTagSearchWarehouse = ResourceDocTag("Data-Warehouse")
  val apiTagFx = ResourceDocTag("FX")
  val apiTagMessage = ResourceDocTag("Customer-Message")
  val apiTagMetric = ResourceDocTag("Metric")
  val apiTagDocumentation = ResourceDocTag("Documentation")
  val apiTagBerlinGroup = ResourceDocTag("Berlin-Group")
  val apiTagUKOpenBanking = ResourceDocTag("UKOpenBanking")
  val apiTagApiBuilder = ResourceDocTag("API-Builder")
  val apiTagAggregateMetrics = ResourceDocTag("Aggregate-Metrics")
  val apiTagNewStyle = ResourceDocTag("New-Style")
  val apiTagWebhook = ResourceDocTag("Webhook")
  val apiTagMockedData = ResourceDocTag("Mocked-Data")

  
  //Note: the followings are for the code generator -- UKOpenBankingV3.1.0
  val apiTagAccountAccess = ResourceDocTag("UK-AccountAccess")
  val apiTagAccounts = ResourceDocTag("UK-Accounts")
  val apiTagBalances = ResourceDocTag("UK-Balances")
  val apiTagBeneficiaries = ResourceDocTag("UK-Beneficiaries ")
  val apiTagDirectDebits = ResourceDocTag("UK-DirectDebits")
  val apiTagDomesticPayments = ResourceDocTag("UK-DomesticPayments")
  val apiTagDomesticScheduledPayments = ResourceDocTag("UK-DomesticScheduledPayments")
  val apiTagDomesticStandingOrders = ResourceDocTag("UK-DomesticStandingOrders")
  val apiTagFilePayments = ResourceDocTag("UK-FilePayments")
  val apiTagFundsConfirmations = ResourceDocTag("UK-FundsConfirmations")
  val apiTagInternationalPayments = ResourceDocTag("UK-InternationalPayments ")
  val apiTagInternationalScheduledPayments = ResourceDocTag("UK-InternationalScheduledPayments")
  val apiTagInternationalStandingOrders = ResourceDocTag("UK-InternationalStandingOrders")
  val apiTagOffers = ResourceDocTag("UK-Offers")
  val apiTagPartys = ResourceDocTag("UK-Partys")
  val apiTagProducts = ResourceDocTag("UK-Products")
  val apiTagScheduledPayments = ResourceDocTag("UK-ScheduledPayments")
  val apiTagStandingOrders = ResourceDocTag("UK-StandingOrders")
  val apiTagStatements = ResourceDocTag("UK-Statements")
  val apiTagTransactions = ResourceDocTag("UK-Transactions")

  private[this] val tagNameSymbolMapTag: MutableMap[String, ResourceDocTag] = MutableMap()

  /**
    * get a ResourceDocTag by tag symbol string, if not exists, create one with the symbol
    * @param tagSymbol tag content
    * @return exists or created ResourceDocTags
    */
  def apply(tagSymbol: String): ResourceDocTag =  this.tagNameSymbolMapTag.getOrElseUpdate(tagSymbol, ResourceDocTag(tagSymbol))

}



