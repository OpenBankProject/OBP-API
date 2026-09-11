package code.api.util.http4s

import org.json4s._
import code.api.util.APIUtil.ResourceDoc
import code.api.util.ApiTag.ResourceDocTag
import com.openbankproject.commons.util.ApiShortVersions
import com.openbankproject.commons.util.ApiVersion
import org.json4s.JsonAST.JObject
import org.http4s._
import org.scalatest.{GivenWhenThen, Tag}

import scala.collection.mutable.ArrayBuffer
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers

/**
 * Unit tests for ResourceDocMatcher
 * 
 * Tests ResourceDoc matching and path parameter extraction:
 * - Matching by verb and exact path
 * - Matching with BANK_ID variable
 * - Matching with BANK_ID + ACCOUNT_ID variables
 * - Matching with BANK_ID + ACCOUNT_ID + VIEW_ID variables
 * - Matching with COUNTERPARTY_ID variable
 * - Non-matching requests return None
 * - Path parameter extraction for all variable types
 * 
 */
class ResourceDocMatcherTest extends AnyFeatureSpec with Matchers with GivenWhenThen {
  
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
  
  Feature("ResourceDocMatcher - Exact path matching") {
    
    Scenario("Match GET request with exact path", ResourceDocMatcherTag) {
      Given("A ResourceDoc for GET /banks")
      val resourceDocs = ArrayBuffer(
        createResourceDoc("GET", "/banks", "getBanks")
      )
      
      When(s"Matching a GET request to $base/banks")
      val path = Uri.Path.unsafeFromString(s"$base/banks")
      val result = ResourceDocMatcher.findResourceDoc("GET", path, resourceDocs)
      
      Then("Should find the matching ResourceDoc")
      result should be(defined)
      result.get.partialFunctionName should equal("getBanks")
    }
    
    Scenario("Match POST request with exact path", ResourceDocMatcherTag) {
      Given("A ResourceDoc for POST /banks")
      val resourceDocs = ArrayBuffer(
        createResourceDoc("POST", "/banks", "createBank")
      )
      
      When(s"Matching a POST request to $base/banks")
      val path = Uri.Path.unsafeFromString(s"$base/banks")
      val result = ResourceDocMatcher.findResourceDoc("POST", path, resourceDocs)
      
      Then("Should find the matching ResourceDoc")
      result should be(defined)
      result.get.partialFunctionName should equal("createBank")
    }
    
    Scenario("Match request with multi-segment path", ResourceDocMatcherTag) {
      Given("A ResourceDoc for GET /management/metrics")
      val resourceDocs = ArrayBuffer(
        createResourceDoc("GET", "/management/metrics", "getMetrics")
      )
      
      When(s"Matching a GET request to $base/management/metrics")
      val path = Uri.Path.unsafeFromString(s"$base/management/metrics")
      val result = ResourceDocMatcher.findResourceDoc("GET", path, resourceDocs)
      
      Then("Should find the matching ResourceDoc")
      result should be(defined)
      result.get.partialFunctionName should equal("getMetrics")
    }
    
    Scenario("Verb mismatch returns None", ResourceDocMatcherTag) {
      Given("A ResourceDoc for GET /banks")
      val resourceDocs = ArrayBuffer(
        createResourceDoc("GET", "/banks", "getBanks")
      )
      
      When(s"Matching a POST request to $base/banks")
      val path = Uri.Path.unsafeFromString(s"$base/banks")
      val result = ResourceDocMatcher.findResourceDoc("POST", path, resourceDocs)
      
      Then("Should return None")
      result should be(None)
    }
    
    Scenario("Path mismatch returns None", ResourceDocMatcherTag) {
      Given("A ResourceDoc for GET /banks")
      val resourceDocs = ArrayBuffer(
        createResourceDoc("GET", "/banks", "getBanks")
      )
      
      When(s"Matching a GET request to $base/accounts")
      val path = Uri.Path.unsafeFromString(s"$base/accounts")
      val result = ResourceDocMatcher.findResourceDoc("GET", path, resourceDocs)
      
      Then("Should return None")
      result should be(None)
    }
  }
  
  Feature("ResourceDocMatcher - BANK_ID variable matching") {
    
    Scenario("Match request with BANK_ID variable", ResourceDocMatcherTag) {
      Given("A ResourceDoc for GET /banks/BANK_ID")
      val resourceDocs = ArrayBuffer(
        createResourceDoc("GET", "/banks/BANK_ID", "getBank")
      )
      
      When(s"Matching a GET request to $base/banks/gh.29.de")
      val path = Uri.Path.unsafeFromString(s"$base/banks/gh.29.de")
      val result = ResourceDocMatcher.findResourceDoc("GET", path, resourceDocs)
      
      Then("Should find the matching ResourceDoc")
      result should be(defined)
      result.get.partialFunctionName should equal("getBank")
    }
    
    Scenario("Match request with BANK_ID and additional segments", ResourceDocMatcherTag) {
      Given("A ResourceDoc for GET /banks/BANK_ID/accounts")
      val resourceDocs = ArrayBuffer(
        createResourceDoc("GET", "/banks/BANK_ID/accounts", "getBankAccounts")
      )
      
      When(s"Matching a GET request to $base/banks/test-bank-1/accounts")
      val path = Uri.Path.unsafeFromString(s"$base/banks/test-bank-1/accounts")
      val result = ResourceDocMatcher.findResourceDoc("GET", path, resourceDocs)
      
      Then("Should find the matching ResourceDoc")
      result should be(defined)
      result.get.partialFunctionName should equal("getBankAccounts")
    }
    
    Scenario("Extract BANK_ID parameter value", ResourceDocMatcherTag) {
      Given("A matched ResourceDoc with BANK_ID")
      val resourceDoc = createResourceDoc("GET", "/banks/BANK_ID", "getBank")
      
      When(s"Extracting path parameters from $base/banks/gh.29.de")
      val path = Uri.Path.unsafeFromString(s"$base/banks/gh.29.de")
      val params = ResourceDocMatcher.extractPathParams(path, resourceDoc)
      
      Then("Should extract BANK_ID value")
      params should contain key "BANK_ID"
      params("BANK_ID") should equal("gh.29.de")
    }
  }
  
  Feature("ResourceDocMatcher - BANK_ID + ACCOUNT_ID variables") {
    
    Scenario("Match request with BANK_ID and ACCOUNT_ID variables", ResourceDocMatcherTag) {
      Given("A ResourceDoc for GET /banks/BANK_ID/accounts/ACCOUNT_ID")
      val resourceDocs = ArrayBuffer(
        createResourceDoc("GET", "/banks/BANK_ID/accounts/ACCOUNT_ID", "getBankAccount")
      )
      
      When(s"Matching a GET request to $base/banks/gh.29.de/accounts/test1")
      val path = Uri.Path.unsafeFromString(s"$base/banks/gh.29.de/accounts/test1")
      val result = ResourceDocMatcher.findResourceDoc("GET", path, resourceDocs)
      
      Then("Should find the matching ResourceDoc")
      result should be(defined)
      result.get.partialFunctionName should equal("getBankAccount")
    }
    
    Scenario("Extract BANK_ID and ACCOUNT_ID parameter values", ResourceDocMatcherTag) {
      Given("A matched ResourceDoc with BANK_ID and ACCOUNT_ID")
      val resourceDoc = createResourceDoc("GET", "/banks/BANK_ID/accounts/ACCOUNT_ID", "getBankAccount")
      
      When(s"Extracting path parameters from $base/banks/gh.29.de/accounts/test1")
      val path = Uri.Path.unsafeFromString(s"$base/banks/gh.29.de/accounts/test1")
      val params = ResourceDocMatcher.extractPathParams(path, resourceDoc)
      
      Then("Should extract both BANK_ID and ACCOUNT_ID values")
      params should contain key "BANK_ID"
      params should contain key "ACCOUNT_ID"
      params("BANK_ID") should equal("gh.29.de")
      params("ACCOUNT_ID") should equal("test1")
    }
    
    Scenario("Match request with BANK_ID, ACCOUNT_ID and additional segments", ResourceDocMatcherTag) {
      Given("A ResourceDoc for GET /banks/BANK_ID/accounts/ACCOUNT_ID/transactions")
      val resourceDocs = ArrayBuffer(
        createResourceDoc("GET", "/banks/BANK_ID/accounts/ACCOUNT_ID/transactions", "getTransactions")
      )
      
      When(s"Matching a GET request to $base/banks/test-bank/accounts/acc-123/transactions")
      val path = Uri.Path.unsafeFromString(s"$base/banks/test-bank/accounts/acc-123/transactions")
      val result = ResourceDocMatcher.findResourceDoc("GET", path, resourceDocs)
      
      Then("Should find the matching ResourceDoc")
      result should be(defined)
      result.get.partialFunctionName should equal("getTransactions")
    }
  }
  
  Feature("ResourceDocMatcher - BANK_ID + ACCOUNT_ID + VIEW_ID variables") {
    
    Scenario("Match request with BANK_ID, ACCOUNT_ID and VIEW_ID variables", ResourceDocMatcherTag) {
      Given("A ResourceDoc for GET /banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions")
      val resourceDocs = ArrayBuffer(
        createResourceDoc("GET", "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions", "getTransactionsForView")
      )
      
      When(s"Matching a GET request to $base/banks/gh.29.de/accounts/test1/owner/transactions")
      val path = Uri.Path.unsafeFromString(s"$base/banks/gh.29.de/accounts/test1/owner/transactions")
      val result = ResourceDocMatcher.findResourceDoc("GET", path, resourceDocs)
      
      Then("Should find the matching ResourceDoc")
      result should be(defined)
      result.get.partialFunctionName should equal("getTransactionsForView")
    }
    
    Scenario("Extract BANK_ID, ACCOUNT_ID and VIEW_ID parameter values", ResourceDocMatcherTag) {
      Given("A matched ResourceDoc with BANK_ID, ACCOUNT_ID and VIEW_ID")
      val resourceDoc = createResourceDoc("GET", "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions", "getTransactionsForView")
      
      When(s"Extracting path parameters from $base/banks/gh.29.de/accounts/test1/owner/transactions")
      val path = Uri.Path.unsafeFromString(s"$base/banks/gh.29.de/accounts/test1/owner/transactions")
      val params = ResourceDocMatcher.extractPathParams(path, resourceDoc)
      
      Then("Should extract all three parameter values")
      params should contain key "BANK_ID"
      params should contain key "ACCOUNT_ID"
      params should contain key "VIEW_ID"
      params("BANK_ID") should equal("gh.29.de")
      params("ACCOUNT_ID") should equal("test1")
      params("VIEW_ID") should equal("owner")
    }
    
    Scenario("Match request with VIEW_ID in different position", ResourceDocMatcherTag) {
      Given("A ResourceDoc for GET /banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/account")
      val resourceDocs = ArrayBuffer(
        createResourceDoc("GET", "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/account", "getAccountForView")
      )
      
      When(s"Matching a GET request to $base/banks/test-bank/accounts/acc-1/public/account")
      val path = Uri.Path.unsafeFromString(s"$base/banks/test-bank/accounts/acc-1/public/account")
      val result = ResourceDocMatcher.findResourceDoc("GET", path, resourceDocs)
      
      Then("Should find the matching ResourceDoc")
      result should be(defined)
      result.get.partialFunctionName should equal("getAccountForView")
    }
  }
  
  Feature("ResourceDocMatcher - COUNTERPARTY_ID variable") {
    
    Scenario("Match request with COUNTERPARTY_ID variable", ResourceDocMatcherTag) {
      Given("A ResourceDoc for GET /banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties/COUNTERPARTY_ID")
      val resourceDocs = ArrayBuffer(
        createResourceDoc("GET", "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties/COUNTERPARTY_ID", "getCounterparty")
      )
      
      When("Matching a GET request with counterparty ID")
      val path = Uri.Path.unsafeFromString(s"$base/banks/gh.29.de/accounts/test1/owner/counterparties/ff010868-ac7d-4f96-9fc5-70dd5757e891")
      val result = ResourceDocMatcher.findResourceDoc("GET", path, resourceDocs)
      
      Then("Should find the matching ResourceDoc")
      result should be(defined)
      result.get.partialFunctionName should equal("getCounterparty")
    }
    
    Scenario("Extract COUNTERPARTY_ID parameter value", ResourceDocMatcherTag) {
      Given("A matched ResourceDoc with COUNTERPARTY_ID")
      val resourceDoc = createResourceDoc("GET", "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties/COUNTERPARTY_ID", "getCounterparty")
      
      When("Extracting path parameters")
      val path = Uri.Path.unsafeFromString(s"$base/banks/gh.29.de/accounts/test1/owner/counterparties/ff010868-ac7d-4f96-9fc5-70dd5757e891")
      val params = ResourceDocMatcher.extractPathParams(path, resourceDoc)
      
      Then("Should extract all parameter values including COUNTERPARTY_ID")
      params should contain key "BANK_ID"
      params should contain key "ACCOUNT_ID"
      params should contain key "VIEW_ID"
      params should contain key "COUNTERPARTY_ID"
      params("BANK_ID") should equal("gh.29.de")
      params("ACCOUNT_ID") should equal("test1")
      params("VIEW_ID") should equal("owner")
      params("COUNTERPARTY_ID") should equal("ff010868-ac7d-4f96-9fc5-70dd5757e891")
    }
    
    Scenario("Match request with COUNTERPARTY_ID in different URL structure", ResourceDocMatcherTag) {
      Given("A ResourceDoc for DELETE /management/counterparties/COUNTERPARTY_ID")
      val resourceDocs = ArrayBuffer(
        createResourceDoc("DELETE", "/management/counterparties/COUNTERPARTY_ID", "deleteCounterparty")
      )
      
      When("Matching a DELETE request")
      val path = Uri.Path.unsafeFromString(s"$base/management/counterparties/counterparty-123")
      val result = ResourceDocMatcher.findResourceDoc("DELETE", path, resourceDocs)
      
      Then("Should find the matching ResourceDoc")
      result should be(defined)
      result.get.partialFunctionName should equal("deleteCounterparty")
    }
  }
  
  Feature("ResourceDocMatcher - Non-matching requests") {
    
    Scenario("Return None when no ResourceDoc matches", ResourceDocMatcherTag) {
      Given("ResourceDocs for specific endpoints")
      val resourceDocs = ArrayBuffer(
        createResourceDoc("GET", "/banks", "getBanks"),
        createResourceDoc("GET", "/banks/BANK_ID", "getBank"),
        createResourceDoc("POST", "/banks", "createBank")
      )
      
      When("Matching a request that doesn't match any ResourceDoc")
      val path = Uri.Path.unsafeFromString(s"$base/accounts")
      val result = ResourceDocMatcher.findResourceDoc("GET", path, resourceDocs)
      
      Then("Should return None")
      result should be(None)
    }
    
    Scenario("Return None when verb doesn't match", ResourceDocMatcherTag) {
      Given("A ResourceDoc for GET /banks")
      val resourceDocs = ArrayBuffer(
        createResourceDoc("GET", "/banks", "getBanks")
      )
      
      When(s"Matching a DELETE request to $base/banks")
      val path = Uri.Path.unsafeFromString(s"$base/banks")
      val result = ResourceDocMatcher.findResourceDoc("DELETE", path, resourceDocs)
      
      Then("Should return None")
      result should be(None)
    }
    
    Scenario("Return None when path segment count doesn't match", ResourceDocMatcherTag) {
      Given("A ResourceDoc for GET /banks/BANK_ID/accounts")
      val resourceDocs = ArrayBuffer(
        createResourceDoc("GET", "/banks/BANK_ID/accounts", "getBankAccounts")
      )
      
      When("Matching a request with different segment count")
      val path = Uri.Path.unsafeFromString(s"$base/banks/gh.29.de")
      val result = ResourceDocMatcher.findResourceDoc("GET", path, resourceDocs)
      
      Then("Should return None")
      result should be(None)
    }
    
    Scenario("Return None when literal segments don't match", ResourceDocMatcherTag) {
      Given("A ResourceDoc for GET /banks/BANK_ID/accounts")
      val resourceDocs = ArrayBuffer(
        createResourceDoc("GET", "/banks/BANK_ID/accounts", "getBankAccounts")
      )
      
      When("Matching a request with different literal segment")
      val path = Uri.Path.unsafeFromString(s"$base/banks/gh.29.de/transactions")
      val result = ResourceDocMatcher.findResourceDoc("GET", path, resourceDocs)
      
      Then("Should return None")
      result should be(None)
    }
  }
  
  Feature("ResourceDocMatcher - Path parameter extraction edge cases") {
    
    Scenario("Extract parameters from path with no variables", ResourceDocMatcherTag) {
      Given("A ResourceDoc with no path variables")
      val resourceDoc = createResourceDoc("GET", "/banks", "getBanks")
      
      When("Extracting path parameters")
      val path = Uri.Path.unsafeFromString(s"$base/banks")
      val params = ResourceDocMatcher.extractPathParams(path, resourceDoc)
      
      Then("Should return empty map")
      params should be(empty)
    }
    
    Scenario("Extract parameters with special characters in values", ResourceDocMatcherTag) {
      Given("A ResourceDoc with BANK_ID")
      val resourceDoc = createResourceDoc("GET", "/banks/BANK_ID", "getBank")
      
      When("Extracting path parameters with special characters")
      val path = Uri.Path.unsafeFromString(s"$base/banks/gh.29.de-test_bank")
      val params = ResourceDocMatcher.extractPathParams(path, resourceDoc)
      
      Then("Should extract the full value including special characters")
      params should contain key "BANK_ID"
      params("BANK_ID") should equal("gh.29.de-test_bank")
    }
    
    Scenario("Return empty map when path doesn't match template", ResourceDocMatcherTag) {
      Given("A ResourceDoc for /banks/BANK_ID")
      val resourceDoc = createResourceDoc("GET", "/banks/BANK_ID", "getBank")
      
      When("Extracting parameters from path with different segment count")
      val path = Uri.Path.unsafeFromString(s"$base/accounts")
      val params = ResourceDocMatcher.extractPathParams(path, resourceDoc)
      
      Then("Should return empty map due to segment count mismatch")
      params should be(empty)
    }
  }
  
  Feature("ResourceDocMatcher - attachToCallContext") {
    
    Scenario("Attach ResourceDoc to CallContext", ResourceDocMatcherTag) {
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
    
    Scenario("Attach ResourceDoc sets operationId", ResourceDocMatcherTag) {
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
    
    Scenario("Preserve other CallContext fields when attaching ResourceDoc", ResourceDocMatcherTag) {
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
  
  Feature("ResourceDocMatcher - Multiple ResourceDocs selection") {
    
    Scenario("Select correct ResourceDoc from multiple candidates", ResourceDocMatcherTag) {
      Given("Multiple ResourceDocs with different paths")
      val resourceDocs = ArrayBuffer(
        createResourceDoc("GET", "/banks", "getBanks"),
        createResourceDoc("GET", "/banks/BANK_ID", "getBank"),
        createResourceDoc("GET", "/banks/BANK_ID/accounts", "getBankAccounts"),
        createResourceDoc("POST", "/banks", "createBank")
      )
      
      When("Matching a specific request")
      val path = Uri.Path.unsafeFromString(s"$base/banks/gh.29.de/accounts")
      val result = ResourceDocMatcher.findResourceDoc("GET", path, resourceDocs)
      
      Then("Should select the most specific matching ResourceDoc")
      result should be(defined)
      result.get.partialFunctionName should equal("getBankAccounts")
    }
    
    Scenario("Match first ResourceDoc when multiple exact matches exist", ResourceDocMatcherTag) {
      Given("Multiple ResourceDocs with same path and verb")
      val resourceDocs = ArrayBuffer(
        createResourceDoc("GET", "/banks", "getBanks1"),
        createResourceDoc("GET", "/banks", "getBanks2")
      )
      
      When("Matching a request")
      val path = Uri.Path.unsafeFromString(s"$base/banks")
      val result = ResourceDocMatcher.findResourceDoc("GET", path, resourceDocs)
      
      Then("Should return the first matching ResourceDoc")
      result should be(defined)
      result.get.partialFunctionName should equal("getBanks1")
    }
  }
  
  Feature("ResourceDocMatcher - Case sensitivity") {
    
    Scenario("HTTP verb matching is case-insensitive", ResourceDocMatcherTag) {
      Given("A ResourceDoc with uppercase GET")
      val resourceDocs = ArrayBuffer(
        createResourceDoc("GET", "/banks", "getBanks")
      )
      
      When("Matching with lowercase get")
      val path = Uri.Path.unsafeFromString(s"$base/banks")
      val result = ResourceDocMatcher.findResourceDoc("get", path, resourceDocs)
      
      Then("Should find the matching ResourceDoc")
      result should be(defined)
      result.get.partialFunctionName should equal("getBanks")
    }
    
    Scenario("Path matching is case-sensitive for literal segments", ResourceDocMatcherTag) {
      Given("A ResourceDoc for /banks")
      val resourceDocs = ArrayBuffer(
        createResourceDoc("GET", "/banks", "getBanks")
      )
      
      When("Matching with different case /Banks")
      val path = Uri.Path.unsafeFromString(s"$base/Banks")
      val result = ResourceDocMatcher.findResourceDoc("GET", path, resourceDocs)
      
      Then("Should not match (case-sensitive)")
      result should be(None)
    }
  }
}
