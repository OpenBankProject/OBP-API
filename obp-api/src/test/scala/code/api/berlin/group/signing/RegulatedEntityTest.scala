package code.api.berlin.group.signing

import code.api.berlin.group.v1_3.BerlinGroupServerSetupV1_3
import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3.ErrorMessagesBG

class RegulatedEntityTest extends BerlinGroupServerSetupV1_3 with PSD2SigningTestSupport {

  override def beforeEach(): Unit = {
    super.beforeEach()
    
    // Additional test-specific properties
    setPropsValues(
      "use_consumer_limits" -> "false",
      "allow_anonymous_access" -> "true",
      "berlin_group_psd2_signing_enabled" -> "true"
    )
    
    // Validate that everything is set up correctly
    validateTestSetup()
  }

  // Override certificate details for this specific test
  override protected def tppCommonName: String = "Berlin Group Test TPP Certificate"
  override protected def tppOrganization: String = "Some Test Bank"
  override protected def tppSignaturePassword: String = "testpassword123"
  override protected def tppSignatureAlias: String = "bnm test"

  scenario("Create signed consent request with dynamically generated certificates") {
    Given("A consent request body")
    val requestBody = """{
      "access": {
        "accounts": [],
        "balances": [],
        "transactions": []
      },
      "recurringIndicator": true,
      "validUntil": "2024-12-31",
      "frequencyPerDay": 4
    }"""

    When("I sign the request using the generated certificates")
    val headers = signPSD2Request(requestBody)
    
    Then("The headers should contain the required PSD2 signing elements")
    headers should contain key "X-Request-ID"
    headers should contain key "Digest"
    headers should contain key "TPP-Signature-Certificate"
    headers should contain key "Signature"

    And("I can use the signed request with OBP's HTTP client")
    val request = (V1_3_BG / "consents").POST
    val response = makePostRequestAdditionalHeader(request, requestBody, headers.toList)

    // Since this is a test certificate, we expect authentication to fail but with proper structure
    response.code should equal(401)
    response.body.extract[ErrorMessagesBG].tppMessages.head.code should equal("CERTIFICATE_BLOCKED")
  }

  scenario("Test certificate validation and signing process") {
    Given("A payment initiation request body")
    val paymentRequestBody = """{
      "instructedAmount": {
        "currency": "EUR",
        "amount": "123.45"
      },
      "debtorAccount": {
        "iban": "DE02100100109307118603"
      },
      "creditorName": "John Doe",
      "creditorAccount": {
        "iban": "DE23100120020123456789"
      },
      "remittanceInformationUnstructured": "Test payment"
    }"""

    When("I create a signature for the payment request")
    val signedHeaders = signPSD2Request(paymentRequestBody)

    Then("The signature should be valid and contain all required headers")
    signedHeaders should have size (11)
    signedHeaders("Digest") should startWith("SHA-256=")
    signedHeaders("Signature") should include("keyId=")
    signedHeaders("X-Request-ID") should not be empty

    And("The request should be properly formatted for Berlin Group API")
    val request = (V1_3_BG / "payments" / "sepa-credit-transfers").POST
    val response = makePostRequestAdditionalHeader(request, paymentRequestBody, signedHeaders.toList)
    
    // We expect authentication failure with test certificates, but the structure should be valid
    response.code should (equal(401) or equal(400) or equal(403))
  }

  scenario("Test custom certificate parameters") {
    Given("Custom certificate parameters")
    val customCertData = TestCertificateGenerator.generateTestCertificate(
      commonName = "Custom Test Certificate",
      organizationName = "Custom Test Org",
      validityDays = 30
    )

    customCertData should be a 'success

    When("I inspect the generated certificate")
    val certData = customCertData.get

    Then("It should have the correct properties")
    certData.certificate.getSubjectDN.getName should include("Custom Test Certificate")
    certData.certificate.getSubjectDN.getName should include("Custom Test Org")
    
    And("The certificate should be valid")
    certData.certificate.checkValidity() // Should not throw exception
  }
}