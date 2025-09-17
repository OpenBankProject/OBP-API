package code.api

import code.api.util.APIUtil.{ResourceDoc, EmptyBody}
import code.api.OBPRestHelper
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}
import org.scalatest.{FlatSpec, Matchers, Tag}

/**
 * Unit tests for OBPRestHelper.isAutoValidate method
 * 
 * This test suite covers basic scenarios for the isAutoValidate function:
 * - When doc.isValidateEnabled is true
 * - When autoValidateAll is false
 * - When doc.isValidateDisabled is true
 * - When doc.implementedInApiVersion is not ScannedApiVersion
 * - Basic version comparison logic
 */
class OBPRestHelperTest extends FlatSpec with Matchers {
  
  object tag extends Tag("OBPRestHelper")
  
  // Create a test instance of OBPRestHelper
  private val testHelper = new OBPRestHelper {
    val version: com.openbankproject.commons.util.ApiVersion = ScannedApiVersion("obp", "OBP", "v4.0.0")
    val versionStatus: String = "stable"
  }
  
  // Helper method to create a ResourceDoc with specific validation settings
  private def createResourceDoc(
    version: ScannedApiVersion,
    isValidateEnabled: Boolean = false,
    isValidateDisabled: Boolean = false
  ): ResourceDoc = {
    // Create a minimal ResourceDoc for testing
    val doc = new ResourceDoc(
      partialFunction = null, // Not used in our tests
      implementedInApiVersion = version,
      partialFunctionName = "testFunction",
      requestVerb = "GET",
      requestUrl = "/test",
      summary = "Test endpoint",
      description = "Test description",
      exampleRequestBody = EmptyBody,
      successResponseBody = EmptyBody,
      errorResponseBodies = List(),
      tags = List()
    )
    
    // Set validation flags using reflection or direct method calls
    if (isValidateEnabled) {
      doc.enableAutoValidate()
    }
    if (isValidateDisabled) {
      doc.disableAutoValidate()
    }
    
    doc
  }
  
  "isAutoValidate" should "return true when doc.isValidateEnabled is true" taggedAs tag in {
    val v4_0_0 = ScannedApiVersion("obp", "OBP", "v4.0.0")
    val doc = createResourceDoc(v4_0_0, isValidateEnabled = true)
    val result = testHelper.isAutoValidate(doc, autoValidateAll = false)
    result shouldBe true
  }
  
  it should "return false when autoValidateAll is false and doc.isValidateEnabled is false" taggedAs tag in {
    val v4_0_0 = ScannedApiVersion("obp", "OBP", "v4.0.0")
    val doc = createResourceDoc(v4_0_0, isValidateEnabled = false)
    val result = testHelper.isAutoValidate(doc, autoValidateAll = false)
    result shouldBe false
  }
  
  it should "return false when doc.isValidateDisabled is true" taggedAs tag in {
    val v4_0_0 = ScannedApiVersion("obp", "OBP", "v4.0.0")
    val doc = createResourceDoc(v4_0_0, isValidateDisabled = true)
    val result = testHelper.isAutoValidate(doc, autoValidateAll = true)
    result shouldBe false
  }
  

  
  it should "return false for versions before v4.0.0" taggedAs tag in {
    val v3_1_0 = ScannedApiVersion("obp", "OBP", "v3.1.0")
    val doc = createResourceDoc(v3_1_0)
    val result = testHelper.isAutoValidate(doc, autoValidateAll = true)
    result shouldBe false
  }
  
  it should "return true for v4.0.0" taggedAs tag in {
    val v4_0_0 = ScannedApiVersion("obp", "OBP", "v4.0.0")
    val doc = createResourceDoc(v4_0_0)
    val result = testHelper.isAutoValidate(doc, autoValidateAll = true)
    result shouldBe true
  }
  
  it should "return true for versions after v4.0.0" taggedAs tag in {
    val v5_0_0 = ScannedApiVersion("obp", "OBP", "v5.0.0")
    val doc = createResourceDoc(v5_0_0)
    val result = testHelper.isAutoValidate(doc, autoValidateAll = true)
    result shouldBe true
  }
  
  it should "return true for v4.1.0 (major=4, minor=1)" taggedAs tag in {
    val v4_1_0 = ScannedApiVersion("obp", "OBP", "v4.1.0")
    val doc = createResourceDoc(v4_1_0)
    val result = testHelper.isAutoValidate(doc, autoValidateAll = true)
    result shouldBe true
  }
  
  it should "return false for malformed version strings" taggedAs tag in {
    val malformedVersion = ScannedApiVersion("obp", "OBP", "v4") // Missing minor version
    val doc = createResourceDoc(malformedVersion)
    val result = testHelper.isAutoValidate(doc, autoValidateAll = true)
    result shouldBe false
  }
  
  it should "prioritize isValidateEnabled over autoValidateAll" taggedAs tag in {
    val v3_1_0 = ScannedApiVersion("obp", "OBP", "v3.1.0") // v3.1.0 normally wouldn't auto-validate
    val doc = createResourceDoc(v3_1_0, isValidateEnabled = true)
    val result = testHelper.isAutoValidate(doc, autoValidateAll = true)
    result shouldBe true // Should be true because isValidateEnabled is true
  }
  
  it should "prioritize isValidateDisabled over autoValidateAll" taggedAs tag in {
    val v4_0_0 = ScannedApiVersion("obp", "OBP", "v4.0.0") // v4.0.0 normally would auto-validate
    val doc = createResourceDoc(v4_0_0, isValidateDisabled = true)
    val result = testHelper.isAutoValidate(doc, autoValidateAll = true)
    result shouldBe false // Should be false because isValidateDisabled is true
  }
}

