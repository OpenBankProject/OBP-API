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
  // When using these tags in resource docs, as we now have many APIs, it's best not to have too use too many tags per endpoint.
  val apiTagOldStyle = ResourceDocTag("Old-Style")
  val apiTagTransactionRequest = ResourceDocTag("Transaction-Request")
  val apiTagTransactionRequestAttribute = ResourceDocTag("Transaction-Request-Attribute")
  val apiTagVrp = ResourceDocTag("VRP")
  val apiTagApi = ResourceDocTag("API")
  val apiTagOAuth = ResourceDocTag("OAuth")
  val apiTagOIDC = ResourceDocTag("OIDC")
  val apiTagBank = ResourceDocTag("Bank")
  val apiTagBankAttribute = ResourceDocTag("Bank-Attribute")
  val apiTagAccount = ResourceDocTag("Account")
  val apiTagAccountAttribute = ResourceDocTag("Account-Attribute")
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
  val apiTagTransactionAttribute = ResourceDocTag("Transaction-Attribute")
  val apiTagTransactionFirehose = ResourceDocTag("Transaction-Firehose")
  val apiTagCounterpartyMetaData = ResourceDocTag("Counterparty-Metadata")
  val apiTagTransactionMetaData = ResourceDocTag("Transaction-Metadata")
  val apiTagView = ResourceDocTag("View-Custom")
  val apiTagSystemView = ResourceDocTag("View-System")
  val apiTagEntitlement = ResourceDocTag("Entitlement")
  val apiTagRole = ResourceDocTag("Role")
  val apiTagABAC = ResourceDocTag("ABAC")
  val apiTagScope = ResourceDocTag("Scope")
  val apiTagOwnerRequired = ResourceDocTag("OwnerViewRequired")
  val apiTagCounterparty = ResourceDocTag("Counterparty")
  val apiTagKyc = ResourceDocTag("KYC")
  val apiTagCustomer = ResourceDocTag("Customer")
  val apiTagCustomerAttribute = ResourceDocTag("Customer-Attribute")
  val apiTagOnboarding = ResourceDocTag("Onboarding")
  val apiTagUser = ResourceDocTag("User") // Use for User Management / Info APIs
  val apiTagUserInvitation = ResourceDocTag("User-Invitation")
  val apiTagAttribute = ResourceDocTag("Attribute")
  val apiTagUserAttribute = ResourceDocTag("User-Attribute")
  val apiTagMeeting = ResourceDocTag("Customer-Meeting")
  val apiTagExperimental = ResourceDocTag("Experimental")
  val apiTagPerson = ResourceDocTag("Person")
  val apiTagCard = ResourceDocTag("Card")
  val apiTagCardAttribute = ResourceDocTag("Card-Attribute")
  val apiTagSandbox = ResourceDocTag("Sandbox")
  val apiTagBranch = ResourceDocTag("Branch")
  val apiTagATM = ResourceDocTag("ATM")
  val apiTagAtmAttribute = ResourceDocTag("ATM-Attribute")
  val apiTagProduct = ResourceDocTag("Product")
  val apiTagProductAttribute = ResourceDocTag("Product-Attribute")
  val apiTagProductCollection = ResourceDocTag("Product-Collection")
  val apiTagOpenData = ResourceDocTag("Open-Data")
  val apiTagConsumer = ResourceDocTag("Consumer")
  val apiTagSearchWarehouse = ResourceDocTag("Data-Warehouse")
  val apiTagFx = ResourceDocTag("FX")
  val apiTagMessage = ResourceDocTag("Customer-Message")
  val apiTagMetric = ResourceDocTag("Metric")
  val apiTagDocumentation = ResourceDocTag("Documentation")
  val apiTagBerlinGroup = ResourceDocTag("Berlin-Group")
  val apiTagSigningBaskets = ResourceDocTag("Signing Baskets")
  val apiTagUKOpenBanking = ResourceDocTag("UKOpenBanking")
  val apiTagMXOpenFinance = ResourceDocTag("MXOpenFinance")
  val apiTagAggregateMetrics = ResourceDocTag("Aggregate-Metrics")
  val apiTagSystemIntegrity = ResourceDocTag("System-Integrity")
  val apiTagBalance = ResourceDocTag("Balance")
  val apiTagGroup = ResourceDocTag("Group")
  val apiTagWebhook = ResourceDocTag("Webhook")
  val apiTagMockedData = ResourceDocTag("Mocked-Data")
  val apiTagConsent = ResourceDocTag("Consent")
  val apiTagMethodRouting = ResourceDocTag("Method-Routing")
  val apiTagWebUiProps = ResourceDocTag("WebUi-Props")
  val apiTagEndpointMapping = ResourceDocTag("Endpoint-Mapping")
  val apiTagRateLimits = ResourceDocTag("Rate-Limits")
  val apiTagCounterpartyLimits = ResourceDocTag("Counterparty-Limits")
  val apiTagDevOps = ResourceDocTag("DevOps")
  val apiTagSystem = ResourceDocTag("System")
  val apiTagCache = ResourceDocTag("Cache")
  val apiTagLogCache = ResourceDocTag("Log-Cache")

  val apiTagApiCollection = ResourceDocTag("Api-Collection")
  
  val apiTagDynamicResourceDoc = ResourceDocTag("Dynamic-Resource-Doc")
  val apiTagDynamicMessageDoc = ResourceDocTag("Dynamic-Message-Doc")

  val apiTagDAuth = ResourceDocTag("DAuth")

  val apiTagDynamic = ResourceDocTag("Dynamic")
  val apiTagDynamicEntity = ResourceDocTag("Dynamic-Entity")
  val apiTagManageDynamicEntity = ResourceDocTag("Dynamic-Entity-Manage")
  val apiTagDynamicEndpoint = ResourceDocTag("Dynamic-Endpoint")
  val apiTagManageDynamicEndpoint = ResourceDocTag("Dynamic-Endpoint-Manage")

  val apiTagJsonSchemaValidation = ResourceDocTag("JSON-Schema-Validation")
  val apiTagAuthenticationTypeValidation = ResourceDocTag("Authentication-Type-Validation")
  val apiTagConnectorMethod = ResourceDocTag("Connector-Method")

  // To mark the Berlin Group APIs suggested order of implementation
  val apiTagBerlinGroupM = ResourceDocTag("Berlin-Group-M")
  
  //PSD2 Tags.
  val apiTagPsd2 = ResourceDocTag("PSD2")
  val apiTagPSD2AIS=ResourceDocTag("Account Information Service (AIS)")
  val apiTagPSD2PIIS=ResourceDocTag("Confirmation of Funds Service (PIIS)")
  val apiTagPSD2PIS=ResourceDocTag("Payment Initiation Service (PIS)")


  val apiTagDirectory = ResourceDocTag("Directory")

  
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

object myApp extends App{
  println(ApiTag.allDisplayTagNames)
  println(ApiTag.allDisplayTagNames)
}



