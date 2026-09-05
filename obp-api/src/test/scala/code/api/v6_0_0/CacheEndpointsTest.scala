/**
Open Bank Project - API
Copyright (C) 2011-2024, TESOBE GmbH

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
TESOBE GmbH
Osloerstrasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)
*/
package code.api.v6_0_0

import org.json4s._
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanGetCacheConfig, CanGetCacheInfo, CanInvalidateCacheNamespace}
import code.api.util.ErrorMessages.{InvalidJsonFormat, UserHasMissingRoles, AuthenticatedUserIsRequired}
import code.api.v6_0_0.Http4s600.Implementations6_0_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.native.Serialization.write
import org.scalatest.Tag

class CacheEndpointsTest extends V600ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getCacheConfig":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations6_0_0.getCacheConfig))
  object ApiEndpoint2 extends Tag(nameOf(Implementations6_0_0.getCacheInfo))
  object ApiEndpoint3 extends Tag(nameOf(Implementations6_0_0.invalidateCacheNamespace))

  // ============================================================================================================
  // GET /system/cache/config - Get Cache Configuration
  // ============================================================================================================

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We call getCacheConfig without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v6.0.0 without credentials")
      val request = (v6_0_0_Request / "system" / "cache" / "config").GET
      val response = makeGetRequest(request)
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Missing role") {
    scenario("We call getCacheConfig without the CanGetCacheConfig role", ApiEndpoint1, VersionOfApi) {
      When("We make a request v6.0.0 without the required role")
      val request = (v6_0_0_Request / "system" / "cache" / "config").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 403")
      response.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanGetCacheConfig)
      response.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanGetCacheConfig)
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access") {
    scenario("We call getCacheConfig with the CanGetCacheConfig role", ApiEndpoint1, VersionOfApi) {
      Given("We have a user with CanGetCacheConfig entitlement")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetCacheConfig.toString)

      When("We make a request v6.0.0 with proper role")
      val request = (v6_0_0_Request / "system" / "cache" / "config").GET <@ (user1)
      val response = makeGetRequest(request)

      Then("We should get a 200")
      response.code should equal(200)

      And("The response should have the correct structure")
      val cacheConfig = response.body.extract[CacheConfigJsonV600]
      cacheConfig.instance_id should not be empty
      cacheConfig.environment should not be empty
      cacheConfig.global_prefix should not be empty

      And("Redis status should have valid data")
      cacheConfig.redis_status.available shouldBe a[Boolean]
      cacheConfig.redis_status.url should not be empty
      cacheConfig.redis_status.port should be > 0
      cacheConfig.redis_status.use_ssl shouldBe a[Boolean]

      And("In-memory status should have valid data")
      cacheConfig.in_memory_status.available shouldBe a[Boolean]
      cacheConfig.in_memory_status.current_size should be >= 0L
    }
  }

  // ============================================================================================================
  // GET /system/cache/info - Get Cache Information
  // ============================================================================================================

  feature(s"test $ApiEndpoint2 version $VersionOfApi - Unauthorized access") {
    scenario("We call getCacheInfo without user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v6.0.0 without credentials")
      val request = (v6_0_0_Request / "system" / "cache" / "info").GET
      val response = makeGetRequest(request)
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }
  }

  feature(s"test $ApiEndpoint2 version $VersionOfApi - Missing role") {
    scenario("We call getCacheInfo without the CanGetCacheInfo role", ApiEndpoint2, VersionOfApi) {
      When("We make a request v6.0.0 without the required role")
      val request = (v6_0_0_Request / "system" / "cache" / "info").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 403")
      response.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanGetCacheInfo)
      response.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanGetCacheInfo)
    }
  }

  feature(s"test $ApiEndpoint2 version $VersionOfApi - Authorized access") {
    scenario("We call getCacheInfo with the CanGetCacheInfo role", ApiEndpoint2, VersionOfApi) {
      Given("We have a user with CanGetCacheInfo entitlement")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetCacheInfo.toString)

      When("We make a request v6.0.0 with proper role")
      val request = (v6_0_0_Request / "system" / "cache" / "info").GET <@ (user1)
      val response = makeGetRequest(request)

      Then("We should get a 200")
      response.code should equal(200)

      And("The response should have the correct structure")
      val cacheInfo = response.body.extract[CacheInfoJsonV600]
      cacheInfo.namespaces should not be null
      cacheInfo.total_keys should be >= 0
      cacheInfo.redis_available shouldBe a[Boolean]

      And("Each namespace should have valid data")
      cacheInfo.namespaces.foreach { namespace =>
        namespace.namespace_id should not be empty
        namespace.prefix should not be empty
        namespace.current_version should be > 0L
        namespace.key_count should be >= 0
        namespace.description should not be empty
        namespace.category should not be empty
        namespace.storage_location should not be empty
        namespace.storage_location should (equal("redis") or equal("memory") or equal("both") or equal("unknown"))
        namespace.ttl_info should not be empty
        namespace.ttl_info shouldBe a[String]
      }
    }
  }

  // ============================================================================================================
  // POST /management/cache/namespaces/invalidate - Invalidate Cache Namespace
  // ============================================================================================================

  feature(s"test $ApiEndpoint3 version $VersionOfApi - Unauthorized access") {
    scenario("We call invalidateCacheNamespace without user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v6.0.0 without credentials")
      val request = (v6_0_0_Request / "management" / "cache" / "namespaces" / "invalidate").POST
      val response = makePostRequest(request, write(InvalidateCacheNamespaceJsonV600("rd_localised")))
      Then("We should get a 401")
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }
  }

  feature(s"test $ApiEndpoint3 version $VersionOfApi - Missing role") {
    scenario("We call invalidateCacheNamespace without the CanInvalidateCacheNamespace role", ApiEndpoint3, VersionOfApi) {
      When("We make a request v6.0.0 without the required role")
      val request = (v6_0_0_Request / "management" / "cache" / "namespaces" / "invalidate").POST <@ (user1)
      val response = makePostRequest(request, write(InvalidateCacheNamespaceJsonV600("rd_localised")))
      Then("We should get a 403")
      response.code should equal(403)
      And("error should be " + UserHasMissingRoles + CanInvalidateCacheNamespace)
      response.body.extract[ErrorMessage].message should equal(UserHasMissingRoles + CanInvalidateCacheNamespace)
    }
  }

  feature(s"test $ApiEndpoint3 version $VersionOfApi - Invalid JSON format") {
    scenario("We call invalidateCacheNamespace with invalid JSON", ApiEndpoint3, VersionOfApi) {
      Given("We have a user with CanInvalidateCacheNamespace entitlement")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanInvalidateCacheNamespace.toString)

      When("We make a request with invalid JSON")
      val request = (v6_0_0_Request / "management" / "cache" / "namespaces" / "invalidate").POST <@ (user1)
      val response = makePostRequest(request, """{"invalid": "json"}""")

      Then("We should get a 400")
      response.code should equal(400)
      And("error should be InvalidJsonFormat")
      response.body.extract[ErrorMessage].message should startWith(InvalidJsonFormat)
    }
  }

  feature(s"test $ApiEndpoint3 version $VersionOfApi - Invalid namespace_id") {
    scenario("We call invalidateCacheNamespace with non-existent namespace_id", ApiEndpoint3, VersionOfApi) {
      Given("We have a user with CanInvalidateCacheNamespace entitlement")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanInvalidateCacheNamespace.toString)

      When("We make a request with invalid namespace_id")
      val request = (v6_0_0_Request / "management" / "cache" / "namespaces" / "invalidate").POST <@ (user1)
      val response = makePostRequest(request, write(InvalidateCacheNamespaceJsonV600("invalid_namespace")))

      Then("We should get a 400")
      response.code should equal(400)
      And("error should mention invalid namespace_id")
      val errorMessage = response.body.extract[ErrorMessage].message
      errorMessage should include("Invalid namespace_id")
      errorMessage should include("invalid_namespace")
    }
  }

  feature(s"test $ApiEndpoint3 version $VersionOfApi - Authorized access with valid namespace") {
    scenario("We call invalidateCacheNamespace with valid rd_localised namespace", ApiEndpoint3, VersionOfApi) {
      Given("We have a user with CanInvalidateCacheNamespace entitlement")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanInvalidateCacheNamespace.toString)

      When("We make a request with valid namespace_id")
      val request = (v6_0_0_Request / "management" / "cache" / "namespaces" / "invalidate").POST <@ (user1)
      val response = makePostRequest(request, write(InvalidateCacheNamespaceJsonV600("rd_localised")))

      Then("We should get a 200")
      response.code should equal(200)

      And("The response should have the correct structure")
      val result = response.body.extract[InvalidatedCacheNamespaceJsonV600]
      result.namespace_id should equal("rd_localised")
      result.old_version should be > 0L
      result.new_version should be > result.old_version
      result.new_version should equal(result.old_version + 1)
      result.status should equal("invalidated")
    }

    scenario("We call invalidateCacheNamespace with valid connector namespace", ApiEndpoint3, VersionOfApi) {
      Given("We have a user with CanInvalidateCacheNamespace entitlement")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanInvalidateCacheNamespace.toString)

      When("We make a request with connector namespace_id")
      val request = (v6_0_0_Request / "management" / "cache" / "namespaces" / "invalidate").POST <@ (user1)
      val response = makePostRequest(request, write(InvalidateCacheNamespaceJsonV600("connector")))

      Then("We should get a 200")
      response.code should equal(200)

      And("The response should have the correct structure")
      val result = response.body.extract[InvalidatedCacheNamespaceJsonV600]
      result.namespace_id should equal("connector")
      result.old_version should be > 0L
      result.new_version should be > result.old_version
      result.status should equal("invalidated")
    }

    scenario("We call invalidateCacheNamespace with valid abac_rule namespace", ApiEndpoint3, VersionOfApi) {
      Given("We have a user with CanInvalidateCacheNamespace entitlement")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanInvalidateCacheNamespace.toString)

      When("We make a request with abac_rule namespace_id")
      val request = (v6_0_0_Request / "management" / "cache" / "namespaces" / "invalidate").POST <@ (user1)
      val response = makePostRequest(request, write(InvalidateCacheNamespaceJsonV600("abac_rule")))

      Then("We should get a 200")
      response.code should equal(200)

      And("The response should have the correct structure")
      val result = response.body.extract[InvalidatedCacheNamespaceJsonV600]
      result.namespace_id should equal("abac_rule")
      result.status should equal("invalidated")
    }
  }

  feature(s"test $ApiEndpoint3 version $VersionOfApi - Version increment validation") {
    scenario("We verify that cache version increments correctly on multiple invalidations", ApiEndpoint3, VersionOfApi) {
      Given("We have a user with CanInvalidateCacheNamespace entitlement")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanInvalidateCacheNamespace.toString)

      When("We invalidate the same namespace twice")
      val request1 = (v6_0_0_Request / "management" / "cache" / "namespaces" / "invalidate").POST <@ (user1)
      val response1 = makePostRequest(request1, write(InvalidateCacheNamespaceJsonV600("rd_dynamic")))

      Then("First invalidation should succeed")
      response1.code should equal(200)
      val result1 = response1.body.extract[InvalidatedCacheNamespaceJsonV600]
      val firstNewVersion = result1.new_version

      When("We invalidate again")
      val request2 = (v6_0_0_Request / "management" / "cache" / "namespaces" / "invalidate").POST <@ (user1)
      val response2 = makePostRequest(request2, write(InvalidateCacheNamespaceJsonV600("rd_dynamic")))

      Then("Second invalidation should succeed")
      response2.code should equal(200)
      val result2 = response2.body.extract[InvalidatedCacheNamespaceJsonV600]

      And("Version should have incremented again")
      result2.old_version should equal(firstNewVersion)
      result2.new_version should equal(firstNewVersion + 1)
      result2.status should equal("invalidated")
    }
  }

  // ============================================================================================================
  // Cross-endpoint test - Verify cache info updates after invalidation
  // ============================================================================================================

  feature(s"Integration test - Cache endpoints interaction") {
    scenario("We verify cache info shows updated version after invalidation", ApiEndpoint2, ApiEndpoint3, VersionOfApi) {
      Given("We have a user with both CanGetCacheInfo and CanInvalidateCacheNamespace entitlements")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetCacheInfo.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanInvalidateCacheNamespace.toString)

      When("We get the initial cache info")
      val getRequest1 = (v6_0_0_Request / "system" / "cache" / "info").GET <@ (user1)
      val getResponse1 = makeGetRequest(getRequest1)
      getResponse1.code should equal(200)
      val cacheInfo1 = getResponse1.body.extract[CacheInfoJsonV600]

      // Find the rd_static namespace (or any other valid namespace)
      val targetNamespace = "rd_static"
      val initialVersion = cacheInfo1.namespaces.find(_.namespace_id == targetNamespace).map(_.current_version)

      When("We invalidate the namespace")
      val invalidateRequest = (v6_0_0_Request / "management" / "cache" / "namespaces" / "invalidate").POST <@ (user1)
      val invalidateResponse = makePostRequest(invalidateRequest, write(InvalidateCacheNamespaceJsonV600(targetNamespace)))
      invalidateResponse.code should equal(200)
      val invalidateResult = invalidateResponse.body.extract[InvalidatedCacheNamespaceJsonV600]

      When("We get the cache info again")
      val getRequest2 = (v6_0_0_Request / "system" / "cache" / "info").GET <@ (user1)
      val getResponse2 = makeGetRequest(getRequest2)
      getResponse2.code should equal(200)
      val cacheInfo2 = getResponse2.body.extract[CacheInfoJsonV600]

      Then("The namespace version should have been incremented")
      val updatedNamespace = cacheInfo2.namespaces.find(_.namespace_id == targetNamespace)
      updatedNamespace should not be None

      if (initialVersion.isDefined) {
        updatedNamespace.get.current_version should be > initialVersion.get
      }
      updatedNamespace.get.current_version should equal(invalidateResult.new_version)
    }
  }
}
