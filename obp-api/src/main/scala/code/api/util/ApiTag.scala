package code.api.util

import com.openbankproject.commons.util.ReflectUtils

import scala.collection.mutable.{Map => MutableMap}

object ApiTag {
  // Used to tag Resource Docs
  case class ResourceDocTag(tag: String) {
    val displayTag = tag.replace("-", " ")
  }

  // Use the *singular* case. for both the variable name and string.
  // e.g. "This call is Payment related"
  val apiTagTransactionRequest = ResourceDocTag("Transaction-Request")
  val apiTagApi = ResourceDocTag("API")
  val apiTagBank = ResourceDocTag("Bank")
  val apiTagAccount = ResourceDocTag("Account")
  val apiTagAccountAccess = ResourceDocTag("Account-Access")
  val apiTagDirectDebit = ResourceDocTag("Direct-Debit")
  val apiTagStandingOrder = ResourceDocTag("Standing-Order")
  val apiTagAccountMetadata = ResourceDocTag("Account-Metadata")
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
  val apiTagView = ResourceDocTag("View-(Custom)")
  val apiTagSystemView = ResourceDocTag("View-(System)")
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
  val apiTagProductCollection = ResourceDocTag("Product-Collection")
  val apiTagOpenData = ResourceDocTag("Open-Data")
  val apiTagConsumer = ResourceDocTag("Consumer")
  val apiTagSearchWarehouse = ResourceDocTag("Data-Warehouse")
  val apiTagFx = ResourceDocTag("FX")
  val apiTagMessage = ResourceDocTag("Customer-Message")
  val apiTagMetric = ResourceDocTag("Metric")
  val apiTagDocumentation = ResourceDocTag("Documentation")
  val apiTagBerlinGroup = ResourceDocTag("Berlin-Group")
  val apiTagUKOpenBanking = ResourceDocTag("UKOpenBanking")
  val apiTagMXOpenFinance = ResourceDocTag("MXOpenFinance")
  val apiTagApiBuilder = ResourceDocTag("API-Builder")
  val apiTagAggregateMetrics = ResourceDocTag("Aggregate-Metrics")
  val apiTagNewStyle = ResourceDocTag("New-Style")
  val apiTagWebhook = ResourceDocTag("Webhook")
  val apiTagMockedData = ResourceDocTag("Mocked-Data")
  val apiTagConsent = ResourceDocTag("Consent")
  val apiTagMethodRouting = ResourceDocTag("Method-Routing")
  val apiTagWebUiProps = ResourceDocTag("WebUi-Props")
  val apiTagManageDynamicEntity = ResourceDocTag("Dynamic-Entity-(Manage)")
  val apiTagManageDynamicEndpoint = ResourceDocTag("Dynamic-Endpoint-(Manage)")
  val apiTagApiCollection = ResourceDocTag("Api-Collection")

  val apiTagDynamic = ResourceDocTag("Dynamic")
  val apiTagDynamicEntity = ResourceDocTag("Dynamic-Entity")
  val apiTagDynamicEndpoint = ResourceDocTag("Dynamic-Endpoint")

  val apiTagJsonSchemaValidation = ResourceDocTag("JSON-Schema-Validation")
  val apiTagAuthenticationTypeValidation = ResourceDocTag("Authentication-Type-Validation")

  // To mark the Berlin Group APIs suggested order of implementation
  val apiTagBerlinGroupM = ResourceDocTag("Berlin-Group-M")
  
  //PSD2 Tags.
  val apiTagPsd2 = ResourceDocTag("PSD2")
  val apiTagPSD2AIS=ResourceDocTag("Account Information Service (AIS)")
  val apiTagPSD2PIIS=ResourceDocTag("Confirmation of Funds Service (PIIS)")
  val apiTagPSD2PIS=ResourceDocTag("Payment Initiation Service (PIS)")

  
  //Note: the followings are for the code generator -- UKOpenBankingV3.1.0
  val apiTagUkAccountAccess = ResourceDocTag("UK-AccountAccess")
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

  //Note: the followings are for the code generator -- AUOpenBankingV1.0.0
  val apiTagBanking = ResourceDocTag("AU-Banking")

  private[this] val tagNameSymbolMapTag: MutableMap[String, ResourceDocTag] = MutableMap()

  /**
    * get a ResourceDocTag by tag symbol string, if not exists, create one with the symbol
    * @param tagSymbol tag content
    * @return exists or created ResourceDocTags
    */
  def apply(tagSymbol: String): ResourceDocTag =  this.tagNameSymbolMapTag.getOrElseUpdate(tagSymbol, ResourceDocTag(tagSymbol))

  private lazy val staticTags: Map[String, ResourceDocTag] = ReflectUtils.getFieldsNameToValue[ResourceDocTag](this)

  val staticTagNames: Set[String] = staticTags.values.map(_.displayTag).toSet
  /**
   * get all the tag's display name, include dynamic tags.
   * @return all the tag's display names
   */
  def allDisplayTagNames: Set[String] =
    (staticTags ++ tagNameSymbolMapTag)
    .values.map(_.displayTag).toSet
}



