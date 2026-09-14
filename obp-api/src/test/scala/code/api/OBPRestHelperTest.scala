/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

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

