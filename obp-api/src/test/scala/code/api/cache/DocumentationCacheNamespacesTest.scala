package code.api.cache

import java.util.UUID

import code.api.Constant
import code.api.util.{Glossary, JsonSchemaGenerator}
import code.api.v2_2_0.MessageDocsJsonCache
import code.setup.ServerSetup
import org.json4s.JsonAST.{JObject, JString}

/**
 * This suite checks that the documentation caches held in each instance's memory follow their
 * cache namespace: bumping `message_docs` rebuilds the message docs and the connector JSON Schema,
 * and bumping `glossary` changes the Glossary token that every resource-docs cache key carries.
 *
 * Bumping needs Redis (the version counter lives there); without it the scenarios cancel.
 */
class DocumentationCacheNamespacesTest extends ServerSetup {

  private def bump(namespaceId: String): Unit = {
    val bumped = Constant.incrementCacheNamespaceVersion(namespaceId)
    if (bumped.isEmpty) cancel(s"Redis is not reachable, so the $namespaceId namespace cannot be bumped")
  }

  private def freshConnector(): String = s"namespace-probe-${UUID.randomUUID().toString.take(8)}"

  feature("The documentation cache namespaces") {

    scenario("message_docs and glossary are listed with the other namespaces") {
      Constant.ALL_CACHE_NAMESPACES should contain allOf (Constant.MESSAGE_DOCS_NAMESPACE, Constant.GLOSSARY_NAMESPACE)
    }

    scenario("bumping message_docs rebuilds the message docs response") {
      val connector = freshConnector()
      var builds = 0
      // An object, like real message docs: the cache stores the rendered text and parses it back.
      def build() = { builds += 1; JObject("build" -> JString(s"$builds")) }
      MessageDocsJsonCache.getOrCompute(connector)(build())
      MessageDocsJsonCache.getOrCompute(connector)(build())
      builds shouldBe 1

      bump(Constant.MESSAGE_DOCS_NAMESPACE)
      MessageDocsJsonCache.getOrCompute(connector)(build())
      builds shouldBe 2
    }

    scenario("bumping message_docs rebuilds the connector JSON Schema") {
      val connector = freshConnector()
      val before = JsonSchemaGenerator.generatorCalls
      JsonSchemaGenerator.messageDocsToJsonSchema(Nil, connector)
      JsonSchemaGenerator.messageDocsToJsonSchema(Nil, connector)
      JsonSchemaGenerator.generatorCalls - before shouldBe 1

      bump(Constant.MESSAGE_DOCS_NAMESPACE)
      JsonSchemaGenerator.messageDocsToJsonSchema(Nil, connector)
      JsonSchemaGenerator.generatorCalls - before shouldBe 2
    }

    scenario("bumping glossary changes the Glossary token in resource-docs cache keys") {
      Glossary.invalidateGlossaryItemCache()
      val before = Glossary.glossaryVersionForCacheKey
      bump(Constant.GLOSSARY_NAMESPACE)
      Glossary.invalidateGlossaryItemCache()
      Glossary.glossaryVersionForCacheKey should not equal before
    }

    scenario("another instance sees a bump within a second, without a Redis read per request") {
      val first = Constant.recentCacheNamespaceVersion(Constant.MESSAGE_DOCS_NAMESPACE)
      // Simulate a bump made on another instance: the counter changes in Redis, not locally.
      val versionKey = s"${Constant.getGlobalCacheNamespacePrefix}cache_version_${Constant.MESSAGE_DOCS_NAMESPACE}"
      if (Redis.use(code.api.JedisMethod.INCR, versionKey, None, None).isEmpty) cancel("Redis is not reachable")
      Constant.recentCacheNamespaceVersion(Constant.MESSAGE_DOCS_NAMESPACE) shouldBe first // still the local copy
      Thread.sleep(Constant.RecentNamespaceVersionMillis + 100)
      Constant.recentCacheNamespaceVersion(Constant.MESSAGE_DOCS_NAMESPACE) shouldBe first + 1
    }
  }
}
