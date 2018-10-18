package code.api.util

object ApiTag {
  // Used to tag Resource Docs
  case class ResourceDocTag(tag: String)

  // Use the *singular* case. for both the variable name and string.
  // e.g. "This call is Payment related"
  val apiTagTransactionRequest = ResourceDocTag("Transaction-Request")
  val apiTagApi = ResourceDocTag("API")
  val apiTagBank = ResourceDocTag("Bank")
  val apiTagAccount = ResourceDocTag("Account")
  val apiTagAccountPublic = ResourceDocTag("Account-Public")
  val apiTagAccountFirehose = ResourceDocTag("Account-Firehose")
  val apiTagFirehoseData = ResourceDocTag("FirehoseData")
  val apiTagPublicData = ResourceDocTag("PublicData")
  val apiTagPrivateData = ResourceDocTag("PrivateData")
  val apiTagTransaction = ResourceDocTag("Transaction")
  val apiTagTransactionFirehose = ResourceDocTag("Transaction-Firehose")
  val apiTagCounterpartyMetaData = ResourceDocTag("Counterparty-Metadata")
  val apiTagTransactionMetaData = ResourceDocTag("Transaction-Metadata")
  val apiTagView = ResourceDocTag("Account-View")
  val apiTagEntitlement = ResourceDocTag("Entitlement")
  val apiTagRole = ResourceDocTag("API-Role")
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
  val apiTagSandbox = ResourceDocTag("API-Sandbox")
  val apiTagBranch = ResourceDocTag("Bank-Branch")
  val apiTagATM = ResourceDocTag("Bank-ATM")
  val apiTagProduct = ResourceDocTag("Bank-Product")
  val apiTagOpenData = ResourceDocTag("Open-Data")
  val apiTagConsumer = ResourceDocTag("API-Consumer")
  val apiTagSearchWarehouse = ResourceDocTag("Data-Warehouse")
  val apiTagFx = ResourceDocTag("Bank-FX")
  val apiTagMessage = ResourceDocTag("Customer-Message")
  val apiTagMetric = ResourceDocTag("API-Metric")
  val apiTagDocumentation = ResourceDocTag("API-Documentation")
  val apiTagBerlinGroup = ResourceDocTag("Berlin-Group")
  val apiTagUKOpenBanking = ResourceDocTag("UKOpenBanking")
  val apiTagApiBuilder = ResourceDocTag("API_Builder")
  val apiTagAggregateMetrics = ResourceDocTag("Aggregate-Metrics")
  val apiTagNewStyle = ResourceDocTag("New-Style")
  val apiTagWebhook = ResourceDocTag("Webhook")
}



