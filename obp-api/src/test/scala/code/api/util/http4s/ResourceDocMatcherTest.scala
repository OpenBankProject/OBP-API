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

package code.api.util.http4s

import org.json4s._
import code.api.util.APIUtil.ResourceDoc
import code.api.util.ApiTag.ResourceDocTag
import com.openbankproject.commons.util.ApiShortVersions
import com.openbankproject.commons.util.ApiVersion
import org.json4s.JsonAST.JObject
import org.http4s._
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers, Tag}

import scala.collection.mutable.ArrayBuffer

/**
 * Unit tests for ResourceDocMatcher's path parameter extraction and CallContext wiring.
 *
 * Which doc serves a request is decided by the routes (see ResourceDocRouteSelectionTest and
 * ResourceDocRealCatalogSelectionTest); once the doc is known, the middleware reads BANK_ID, ACCOUNT_ID,
 * VIEW_ID and COUNTERPARTY_ID from the positions the doc's template gives them.
 */
class ResourceDocMatcherTest extends FeatureSpec with Matchers with GivenWhenThen {
  
  object ResourceDocMatcherTag extends Tag("ResourceDocMatcher")
  private val v700 = ApiShortVersions.`v7.0.0`.toString
  private val base = s"/obp/$v700"
  
  // Helper to create minimal ResourceDoc for testing
  private def createResourceDoc(
    verb: String,
    url: String,
    operationId: String = "testOperation"
  ): ResourceDoc = {
    ResourceDoc(
      implementedInApiVersion = ApiVersion.v7_0_0,
      partialFunctionName = operationId,
      requestVerb = verb,
      requestUrl = url,
      summary = "Test endpoint",
      description = "Test description",
      exampleRequestBody = JObject(Nil),
      successResponseBody = JObject(Nil),
      errorResponseBodies = List.empty,
      tags = List(ResourceDocTag("test")),
      roles = None
    )
  }
  
  feature("ResourceDocMatcher - path parameter extraction") {

    scenario("BANK_ID is read from its position", ResourceDocMatcherTag) {
      val resourceDoc = createResourceDoc("GET", "/banks/BANK_ID/accounts", "getAccounts")
      val params = ResourceDocMatcher.extractPathParams(Uri.Path.unsafeFromString(s"$base/banks/gh.29.uk/accounts"), resourceDoc)

      params should equal(Map("BANK_ID" -> "gh.29.uk"))
    }

    scenario("BANK_ID and ACCOUNT_ID are read from their positions", ResourceDocMatcherTag) {
      val resourceDoc = createResourceDoc("GET", "/banks/BANK_ID/accounts/ACCOUNT_ID/account", "getAccount")
      val params = ResourceDocMatcher.extractPathParams(
        Uri.Path.unsafeFromString(s"$base/banks/gh.29.uk/accounts/8ca8a7e4/account"), resourceDoc)

      params should equal(Map("BANK_ID" -> "gh.29.uk", "ACCOUNT_ID" -> "8ca8a7e4"))
    }

    scenario("VIEW_ID is read wherever the template puts it", ResourceDocMatcherTag) {
      val resourceDoc = createResourceDoc("GET", "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions", "getTransactions")
      val params = ResourceDocMatcher.extractPathParams(
        Uri.Path.unsafeFromString(s"$base/banks/gh.29.uk/accounts/8ca8a7e4/owner/transactions"), resourceDoc)

      params should equal(Map("BANK_ID" -> "gh.29.uk", "ACCOUNT_ID" -> "8ca8a7e4", "VIEW_ID" -> "owner"))
    }

    scenario("COUNTERPARTY_ID is read from its position", ResourceDocMatcherTag) {
      val resourceDoc = createResourceDoc(
        "GET", "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties/COUNTERPARTY_ID", "getCounterparty")
      val params = ResourceDocMatcher.extractPathParams(
        Uri.Path.unsafeFromString(s"$base/banks/gh.29.uk/accounts/8ca8a7e4/owner/counterparties/cp1"), resourceDoc)

      params.get("COUNTERPARTY_ID") should be(Some("cp1"))
    }

    scenario("Any other capitalised word in a template is documentation, not a parameter", ResourceDocMatcherTag) {
      val resourceDoc = createResourceDoc(
        "POST", "/banks/BANK_ID/accounts/ACCOUNT_ID/GRANT_VIEW_ID/transaction-request-types/SEPA/transaction-requests", "createTransactionRequestSepa")
      val params = ResourceDocMatcher.extractPathParams(
        Uri.Path.unsafeFromString(s"$base/banks/gh.29.uk/accounts/8ca8a7e4/owner/transaction-request-types/SEPA/transaction-requests"), resourceDoc)

      params should equal(Map("BANK_ID" -> "gh.29.uk", "ACCOUNT_ID" -> "8ca8a7e4"))
    }
  }

  feature("ResourceDocMatcher - Path parameter extraction edge cases") {
    
    scenario("Extract parameters from path with no variables", ResourceDocMatcherTag) {
      Given("A ResourceDoc with no path variables")
      val resourceDoc = createResourceDoc("GET", "/banks", "getBanks")
      
      When("Extracting path parameters")
      val path = Uri.Path.unsafeFromString(s"$base/banks")
      val params = ResourceDocMatcher.extractPathParams(path, resourceDoc)
      
      Then("Should return empty map")
      params should be(empty)
    }
    
    scenario("Extract parameters with special characters in values", ResourceDocMatcherTag) {
      Given("A ResourceDoc with BANK_ID")
      val resourceDoc = createResourceDoc("GET", "/banks/BANK_ID", "getBank")
      
      When("Extracting path parameters with special characters")
      val path = Uri.Path.unsafeFromString(s"$base/banks/gh.29.de-test_bank")
      val params = ResourceDocMatcher.extractPathParams(path, resourceDoc)
      
      Then("Should extract the full value including special characters")
      params should contain key "BANK_ID"
      params("BANK_ID") should equal("gh.29.de-test_bank")
    }
    
    scenario("Return empty map when path doesn't match template", ResourceDocMatcherTag) {
      Given("A ResourceDoc for /banks/BANK_ID")
      val resourceDoc = createResourceDoc("GET", "/banks/BANK_ID", "getBank")
      
      When("Extracting parameters from path with different segment count")
      val path = Uri.Path.unsafeFromString(s"$base/accounts")
      val params = ResourceDocMatcher.extractPathParams(path, resourceDoc)
      
      Then("Should return empty map due to segment count mismatch")
      params should be(empty)
    }
  }
  
  feature("ResourceDocMatcher - attachToCallContext") {
    
    scenario("Attach ResourceDoc to CallContext", ResourceDocMatcherTag) {
      Given("A CallContext and a matched ResourceDoc")
      val resourceDoc = createResourceDoc("GET", "/banks", "getBanks")
      val callContext = code.api.util.CallContext(
        correlationId = "test-correlation-id"
      )
      
      When("Attaching ResourceDoc to CallContext")
      val updatedContext = ResourceDocMatcher.attachToCallContext(callContext, resourceDoc)
      
      Then("CallContext should have resourceDocument set")
      updatedContext.resourceDocument should be(defined)
      updatedContext.resourceDocument.get should equal(resourceDoc)
    }
    
    scenario("Attach ResourceDoc sets operationId", ResourceDocMatcherTag) {
      Given("A CallContext and a matched ResourceDoc")
      val resourceDoc = createResourceDoc("GET", "/banks/BANK_ID", "getBank")
      val callContext = code.api.util.CallContext(
        correlationId = "test-correlation-id"
      )
      
      When("Attaching ResourceDoc to CallContext")
      val updatedContext = ResourceDocMatcher.attachToCallContext(callContext, resourceDoc)
      
      Then("CallContext should have operationId set")
      updatedContext.operationId should be(defined)
      updatedContext.operationId.get should equal(resourceDoc.operationId)
    }
    
    scenario("Preserve other CallContext fields when attaching ResourceDoc", ResourceDocMatcherTag) {
      Given("A CallContext with existing fields")
      val resourceDoc = createResourceDoc("GET", "/banks", "getBanks")
      val originalContext = code.api.util.CallContext(
        correlationId = "test-correlation-id",
        url = s"$base/banks",
        verb = "GET",
        implementedInVersion = v700
      )
      
      When("Attaching ResourceDoc to CallContext")
      val updatedContext = ResourceDocMatcher.attachToCallContext(originalContext, resourceDoc)
      
      Then("Other fields should be preserved")
      updatedContext.correlationId should equal(originalContext.correlationId)
      updatedContext.url should equal(originalContext.url)
      updatedContext.verb should equal(originalContext.verb)
      updatedContext.implementedInVersion should equal(originalContext.implementedInVersion)
    }
  }
  
  feature("ResourceDocMatcher - path parameters of a path with an empty segment") {

    scenario("An empty segment is read by position, so the parameters of the other segments keep their place", ResourceDocMatcherTag) {
      Given("a doc with a placeholder between BANK_ID and ACCOUNT_ID, and a request that leaves the placeholder empty")
      val resourceDoc = createResourceDoc("GET", "/banks/BANK_ID/api-products/API_PRODUCT_CODE/accounts/ACCOUNT_ID", "getProductAccount")
      val path = Uri.Path.unsafeFromString(s"$base/banks/gh.29.uk/api-products//accounts/8ca8a7e4")

      When("the path parameters are extracted")
      val params = ResourceDocMatcher.extractPathParams(path, resourceDoc)

      Then("BANK_ID and ACCOUNT_ID are the values of the request, each at its own position")
      params should equal(Map("BANK_ID" -> "gh.29.uk", "ACCOUNT_ID" -> "8ca8a7e4"))
    }

    scenario("An empty BANK_ID segment is an empty BANK_ID, not a missing one", ResourceDocMatcherTag) {
      val resourceDoc = createResourceDoc("GET", "/banks/BANK_ID/accounts", "getAccounts")
      val path = Uri.Path.unsafeFromString(s"$base/banks//accounts")

      ResourceDocMatcher.extractPathParams(path, resourceDoc).get("BANK_ID") should be(Some(""))
    }

    scenario("A path whose segment count differs from the template still yields nothing", ResourceDocMatcherTag) {
      val resourceDoc = createResourceDoc("GET", "/banks/BANK_ID/accounts", "getAccounts")
      val path = Uri.Path.unsafeFromString(s"$base/banks/gh.29.uk/accounts/extra")

      ResourceDocMatcher.extractPathParams(path, resourceDoc) should be(Map.empty)
    }

    scenario("A trailing slash does not change the parameters of the doc it was matched with", ResourceDocMatcherTag) {
      val resourceDoc = createResourceDoc("GET", "/banks/BANK_ID", "getBank")
      val path = Uri.Path.unsafeFromString(s"$base/banks/gh.29.uk/")

      ResourceDocMatcher.extractPathParams(path, resourceDoc).get("BANK_ID") should be(Some("gh.29.uk"))
    }
  }
}
