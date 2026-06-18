package code.api.berlin.group.v2

import org.json4s._
import code.util.Helper.MdcLoggable
import org.json4s.{Extraction, Formats}
import com.openbankproject.commons.util.JsonAliases.prettyRender
import code.api.util.CustomJsonFormats
import org.scalatest.{FlatSpec, Matchers, Tag}

/**
 * Feature: berlin-group-v2-http4s, Property 2: JSON factory output schema compliance
 *
 * **Validates: Requirements 8.1, 8.2, 8.3, 8.4**
 *
 * For any mocked JSON factory method in JSONFactory_BERLIN_GROUP_v2, the serialized
 * JSON string SHALL contain all field names required by the corresponding v2.3 OpenAPI
 * schema.
 *
 * Property-based approach: uses random UUID/string generators with multiple iterations
 * to verify schema compliance regardless of input values.
 */
class JSONFactoryBGv2Test extends FlatSpec with Matchers with MdcLoggable {

  implicit val formats: Formats = CustomJsonFormats.formats

  object SchemaComplianceTag extends Tag("Property2_JSONFactorySchemaCompliance")

  private def serialize(obj: AnyRef): String = {
    prettyRender(Extraction.decompose(obj))
  }

  // ── Random generators (replacing ScalaCheck) ────────────────────────
  private val random = new scala.util.Random(42) // fixed seed for reproducibility

  private def randomUUID(): String = java.util.UUID.randomUUID().toString

  private def randomAlphaStr(): String = {
    val len = random.nextInt(10) + 1
    random.alphanumeric.take(len).mkString
  }

  private def randomTransactionId(): String = s"${randomAlphaStr()}-${random.nextInt(10000)}"

  private val paymentProducts = List(
    "sepa-credit-transfers",
    "instant-sepa-credit-transfers",
    "target-2-payments",
    "cross-border-credit-transfers"
  )
  private def randomPaymentProduct(): String = paymentProducts(random.nextInt(paymentProducts.size))

  private val paymentServices = List("payments", "bulk-payments", "periodic-payments")
  private def randomPaymentService(): String = paymentServices(random.nextInt(paymentServices.size))

  private val resourcePaths = List(
    "payments/sepa-credit-transfers",
    "bulk-payments/instant-sepa-credit-transfers",
    "periodic-payments/target-2-payments"
  )
  private def randomResourcePath(): String = resourcePaths(random.nextInt(resourcePaths.size))

  private val iterations = 10

  // ── Requirement 8.1: Account list JSON schema compliance ──────────

  "mockAccountList" should "contain all required account list fields (Req 8.1)" taggedAs SchemaComplianceTag in {
    val json = serialize(JSONFactory_BERLIN_GROUP_v2.mockAccountList)
    logger.debug(s"mockAccountList JSON: $json")
    json should include("accounts")
    json should include("resourceId")
    json should include("iban")
    json should include("currency")
    json should include("cashAccountType")
    json should include("_links")
  }

  "mockAccountDetails" should "contain all required account fields for any accountId (Req 8.1)" taggedAs SchemaComplianceTag in {
    for (_ <- 1 to iterations) {
      val accountId = randomUUID()
      val json = serialize(JSONFactory_BERLIN_GROUP_v2.mockAccountDetails(accountId))
      json should include("resourceId")
      json should include("iban")
      json should include("currency")
      json should include("product")
      json should include("cashAccountType")
      json should include("_links")
      json should include("balances")
      json should include(accountId)
    }
  }

  "mockBalances" should "contain all required balance fields for any accountId (Req 8.1)" taggedAs SchemaComplianceTag in {
    for (_ <- 1 to iterations) {
      val accountId = randomUUID()
      val json = serialize(JSONFactory_BERLIN_GROUP_v2.mockBalances(accountId))
      json should include("account")
      json should include("balances")
      json should include("balanceAmount")
      json should include("balanceType")
      json should include("currency")
      json should include("amount")
    }
  }

  // ── Requirement 8.2: Transaction list JSON schema compliance ──────

  "mockTransactions" should "contain all required transaction fields for any accountId (Req 8.2)" taggedAs SchemaComplianceTag in {
    for (_ <- 1 to iterations) {
      val accountId = randomUUID()
      val json = serialize(JSONFactory_BERLIN_GROUP_v2.mockTransactions(accountId))
      json should include("booked")
      json should include("pending")
      json should include("transactionId")
      json should include("transactionAmount")
      json should include("bookingDate")
      json should include("remittanceInformationUnstructured")
    }
  }

  "mockTransactionDetails" should "contain all required transaction detail fields for any accountId and transactionId (Req 8.2)" taggedAs SchemaComplianceTag in {
    for (_ <- 1 to iterations) {
      val accountId = randomUUID()
      val transactionId = randomTransactionId()
      val json = serialize(JSONFactory_BERLIN_GROUP_v2.mockTransactionDetails(accountId, transactionId))
      json should include("transactionId")
      json should include("transactionAmount")
      json should include("bookingDate")
      json should include("valueDate")
      json should include(transactionId)
    }
  }

  // ── Card Account schema compliance (Req 8.1 extended) ─────────────

  "mockCardAccountList" should "contain all required card account list fields (Req 8.1)" taggedAs SchemaComplianceTag in {
    val json = serialize(JSONFactory_BERLIN_GROUP_v2.mockCardAccountList)
    logger.debug(s"mockCardAccountList JSON: $json")
    json should include("cardAccounts")
    json should include("resourceId")
    json should include("maskedPan")
    json should include("currency")
    json should include("cashAccountType")
    json should include("_links")
  }

  "mockCardAccountDetails" should "contain all required card account fields for any accountId (Req 8.1)" taggedAs SchemaComplianceTag in {
    for (_ <- 1 to iterations) {
      val accountId = randomUUID()
      val json = serialize(JSONFactory_BERLIN_GROUP_v2.mockCardAccountDetails(accountId))
      json should include("resourceId")
      json should include("maskedPan")
      json should include("currency")
      json should include("cashAccountType")
      json should include("_links")
      json should include(accountId)
    }
  }

  "mockCardAccountBalances" should "contain all required balance fields for any accountId (Req 8.1)" taggedAs SchemaComplianceTag in {
    for (_ <- 1 to iterations) {
      val accountId = randomUUID()
      val json = serialize(JSONFactory_BERLIN_GROUP_v2.mockCardAccountBalances(accountId))
      json should include("account")
      json should include("balances")
      json should include("balanceAmount")
      json should include("balanceType")
    }
  }

  "mockCardAccountTransactions" should "contain all required transaction fields for any accountId (Req 8.2)" taggedAs SchemaComplianceTag in {
    for (_ <- 1 to iterations) {
      val accountId = randomUUID()
      val json = serialize(JSONFactory_BERLIN_GROUP_v2.mockCardAccountTransactions(accountId))
      json should include("booked")
      json should include("pending")
      json should include("transactionId")
      json should include("transactionAmount")
      json should include("bookingDate")
    }
  }

  // ── Requirement 8.3: Payment initiation JSON schema compliance ────

  "mockPaymentInitiation" should "contain all required payment initiation fields for any paymentProduct (Req 8.3)" taggedAs SchemaComplianceTag in {
    for (_ <- 1 to iterations) {
      val paymentProduct = randomPaymentProduct()
      val json = serialize(JSONFactory_BERLIN_GROUP_v2.mockPaymentInitiation(paymentProduct))
      json should include("transactionStatus")
      json should include("paymentId")
      json should include("_links")
    }
  }

  "mockPaymentStatus" should "contain transactionStatus field (Req 8.3)" taggedAs SchemaComplianceTag in {
    val json = serialize(JSONFactory_BERLIN_GROUP_v2.mockPaymentStatus)
    logger.debug(s"mockPaymentStatus JSON: $json")
    json should include("transactionStatus")
  }

  "mockPaymentDetails" should "contain all required payment detail fields for any inputs (Req 8.3)" taggedAs SchemaComplianceTag in {
    for (_ <- 1 to iterations) {
      val paymentService = randomPaymentService()
      val paymentProduct = randomPaymentProduct()
      val paymentId = randomTransactionId()
      val json = serialize(JSONFactory_BERLIN_GROUP_v2.mockPaymentDetails(paymentService, paymentProduct, paymentId))
      json should include("transactionStatus")
      json should include("paymentId")
      json should include("debtorAccount")
      json should include("instructedAmount")
      json should include("creditorAccount")
      json should include("creditorName")
      json should include(paymentId)
    }
  }

  "mockBulkPaymentExtendedStatus" should "contain all required extended status fields for any inputs (Req 8.3)" taggedAs SchemaComplianceTag in {
    for (_ <- 1 to iterations) {
      val paymentProduct = randomPaymentProduct()
      val paymentId = randomTransactionId()
      val json = serialize(JSONFactory_BERLIN_GROUP_v2.mockBulkPaymentExtendedStatus(paymentProduct, paymentId))
      json should include("transactionStatus")
      json should include("paymentId")
      json should include("fundsAvailable")
      json should include(paymentId)
    }
  }

  // ── Requirement 8.4: Funds confirmation JSON schema compliance ────

  "mockFundsConfirmation" should "contain fundsAvailable field (Req 8.4)" taggedAs SchemaComplianceTag in {
    val json = serialize(JSONFactory_BERLIN_GROUP_v2.mockFundsConfirmation)
    logger.debug(s"mockFundsConfirmation JSON: $json")
    json should include("fundsAvailable")
  }

  // ── Authorisation response schema compliance ──────────────────────

  "mockAuthorisationStart" should "contain all required authorisation fields for any inputs (Req 8.3)" taggedAs SchemaComplianceTag in {
    for (_ <- 1 to iterations) {
      val resourcePath = randomResourcePath()
      val resourceId = randomUUID()
      val json = serialize(JSONFactory_BERLIN_GROUP_v2.mockAuthorisationStart(resourcePath, resourceId))
      json should include("authorisationId")
      json should include("scaStatus")
      json should include("_links")
    }
  }

  "mockAuthorisationSubResources" should "contain authorisationIds field for any inputs (Req 8.3)" taggedAs SchemaComplianceTag in {
    for (_ <- 1 to iterations) {
      val resourcePath = randomResourcePath()
      val resourceId = randomUUID()
      val json = serialize(JSONFactory_BERLIN_GROUP_v2.mockAuthorisationSubResources(resourcePath, resourceId))
      json should include("authorisationIds")
    }
  }

  "mockAuthorisationStatus" should "contain scaStatus field for any authorisationId (Req 8.3)" taggedAs SchemaComplianceTag in {
    for (_ <- 1 to iterations) {
      val authorisationId = randomUUID()
      val json = serialize(JSONFactory_BERLIN_GROUP_v2.mockAuthorisationStatus(authorisationId))
      json should include("scaStatus")
    }
  }

  "mockUpdatePsuData" should "contain scaStatus and _links fields for any authorisationId (Req 8.3)" taggedAs SchemaComplianceTag in {
    for (_ <- 1 to iterations) {
      val authorisationId = randomUUID()
      val json = serialize(JSONFactory_BERLIN_GROUP_v2.mockUpdatePsuData(authorisationId))
      json should include("scaStatus")
      json should include("_links")
    }
  }

  "mockUpdateDebtorAccount" should "contain transactionStatus and debtorAccount fields for any resourceId (Req 8.3)" taggedAs SchemaComplianceTag in {
    for (_ <- 1 to iterations) {
      val resourceId = randomUUID()
      val json = serialize(JSONFactory_BERLIN_GROUP_v2.mockUpdateDebtorAccount(resourceId))
      json should include("transactionStatus")
      json should include("debtorAccount")
    }
  }
}
