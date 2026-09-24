package code.api.util

import code.api.util.APIUtil.MessageDoc
import org.scalatest.{FlatSpec, Matchers}

/**
 * Guards the in-process cache added to JsonSchemaGenerator.messageDocsToJsonSchema.
 *
 * Building the schema walks the full field tree of every message type via Scala runtime
 * reflection (recursive <:</=:= subtype checks), which is the expensive, GC-pressure-generating
 * part this cache exists to avoid paying on every call. A test that only checks the returned
 * JSON is unchanged would pass even if the cache silently never hit (wrong key, wrong TTL
 * handling, etc.) - that exact class of mistake happened once already while writing this fix (a
 * cache-key placeholder tuple with the wrong arity, caught by the compiler, not by output
 * correctness). So this asserts on both: the second call must be dramatically faster, and it
 * must return the identical JSON as the first.
 *
 * Each scenario uses its own unique connector name (via a nanoTime suffix) so it always starts
 * from a genuinely cold cache entry, independent of whatever else has run in this JVM.
 */
class JsonSchemaGeneratorCacheTest extends FlatSpec with Matchers {

  // A case class tree wide and deep enough that the reflection cost this cache exists to avoid
  // clearly dominates JVM/JIT noise -- a handful of fields on a single small type (tried first)
  // measures in the same single-digit milliseconds as scheduling jitter, which made the timing
  // assertion below flaky. This shape is closer to a real connector's message docs (see
  // MessageDocsJsonSchemaTest, which asserts real connectors have 100+ definitions).
  case class SchemaCacheTestAddress(city: String, postcode: Option[String], country: String)
  case class SchemaCacheTestContact(email: String, phone: Option[String], address: SchemaCacheTestAddress)
  case class SchemaCacheTestLineItem(sku: String, quantity: Int, unitPrice: BigDecimal, tags: List[String])
  case class SchemaCacheTestOrder(
    id: String, status: String, createdAt: String, contact: SchemaCacheTestContact,
    items: List[SchemaCacheTestLineItem], notes: Option[String]
  )
  case class SchemaCacheTestBatch(
    batchId: String, orders: List[SchemaCacheTestOrder], primaryContact: SchemaCacheTestContact,
    secondaryContacts: List[SchemaCacheTestContact], metadata: Map[String, String]
  )
  case class SchemaCacheTestPayload(
    id: String,
    amount: BigDecimal,
    tags: List[String],
    address: SchemaCacheTestAddress,
    batch: SchemaCacheTestBatch
  )

  private def freshConnectorName(name: String): String =
    s"JsonSchemaGeneratorCacheTest-$name-${System.nanoTime()}"

  private def samplePayload(): SchemaCacheTestPayload = {
    val address = SchemaCacheTestAddress("Berlin", Some("13359"), "DE")
    val contact = SchemaCacheTestContact("a@example.com", Some("+49 30 1234567"), address)
    val order = SchemaCacheTestOrder(
      "order-1", "PENDING", "2026-09-23T00:00:00Z", contact,
      List(SchemaCacheTestLineItem("sku-1", 2, BigDecimal(9.99), List("x", "y"))),
      Some("note")
    )
    val batch = SchemaCacheTestBatch("batch-1", List(order), contact, List(contact), Map("k" -> "v"))
    SchemaCacheTestPayload("id-1", BigDecimal(1), List("a", "b"), address, batch)
  }

  private def sampleMessageDocs(connectorName: String): List[MessageDoc] = List(
    MessageDoc(
      process = s"obp.$connectorName.getThing",
      messageFormat = "JSON",
      description = "test message doc for cache verification",
      exampleOutboundMessage = samplePayload(),
      exampleInboundMessage = samplePayload()
    )
  )

  "JsonSchemaGenerator.messageDocsToJsonSchema" should "return identical output on a cache hit as on the initial cold call" in {
    val connectorName = freshConnectorName("identical-output")
    val docs = sampleMessageDocs(connectorName)

    val first = JsonSchemaGenerator.messageDocsToJsonSchema(docs, connectorName)
    val second = JsonSchemaGenerator.messageDocsToJsonSchema(docs, connectorName)

    second should equal(first)
  }

  it should "be dramatically faster on a cache hit than on the initial cold call" in {
    val connectorName = freshConnectorName("timing")
    val docs = sampleMessageDocs(connectorName)

    val coldStart = System.nanoTime()
    JsonSchemaGenerator.messageDocsToJsonSchema(docs, connectorName)
    val coldNanos = System.nanoTime() - coldStart

    // A handful of warm calls, not just one: a single fast call could be a lucky JIT/scheduling
    // blip rather than an actual cache hit. Comparing the median (not every sample, and not the
    // fastest) against the cold call keeps this robust to a single GC pause or JIT hiccup landing
    // on one warm sample while still requiring a real, consistent improvement.
    val warmNanosSamples = (1 to 7).map { _ =>
      val start = System.nanoTime()
      JsonSchemaGenerator.messageDocsToJsonSchema(docs, connectorName)
      System.nanoTime() - start
    }
    val medianWarmNanos = warmNanosSamples.sorted.apply(warmNanosSamples.size / 2)

    withClue(s"cold=${coldNanos}ns warm=${warmNanosSamples.sorted.mkString(",")}ns median=${medianWarmNanos}ns: ") {
      medianWarmNanos should be < (coldNanos / 2)
    }
  }

  it should "isolate different connector names as independent cache entries" in {
    val connectorA = freshConnectorName("connector-a")
    val connectorB = freshConnectorName("connector-b")

    val schemaA = JsonSchemaGenerator.messageDocsToJsonSchema(sampleMessageDocs(connectorA), connectorA)
    val schemaB = JsonSchemaGenerator.messageDocsToJsonSchema(sampleMessageDocs(connectorB), connectorB)

    (schemaA \ "title") should equal(org.json4s.JString(s"$connectorA Message Schemas"))
    (schemaB \ "title") should equal(org.json4s.JString(s"$connectorB Message Schemas"))
  }
}
