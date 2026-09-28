package code.telemetry

import code.telemetry.TrafficSources.{AddressCaller, ConsumerCaller, Note}
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}

/**
 * This suite checks what TrafficSources records for a request and how it merges minutes into a
 * window. Times are passed in, so the minutes are chosen by the test.
 */
class TrafficSourcesTest extends FlatSpec with Matchers with BeforeAndAfterEach {

  override def beforeEach(): Unit = TrafficSources.clear()

  private val minute = 60000L
  private val t0 = 1790000000000L - 1790000000000L % minute // the start of a minute

  private def note(operationId: Option[String] = None, consumer: Option[String] = None, refusedBy: Option[String] = None): Note = {
    val n = new Note
    n.operationId = operationId
    n.apiVersion = operationId.map(_ => "v7.0.0")
    n.consumerId = consumer
    n.consumerName = consumer.map(c => s"app $c")
    n.refusedBy = refusedBy
    n
  }

  "TrafficSources" should "count an authenticated request under its Consumer and its address, and pair the Consumer with the endpoint" in {
    TrafficSources.record(note(Some("OBPv7.0.0-getBanks"), Some("consumer-1")), "198.51.100.1", 200, 12, t0)

    val consumers = TrafficSources.consumers(1, t0)
    consumers.map(c => (c.key, c.requests)) shouldBe List(("consumer-1", 1L))
    consumers.head.details.head.name shouldBe "app consumer-1"
    consumers.head.details.head.lastIp shouldBe "198.51.100.1"

    val addresses = TrafficSources.addresses(1, t0)
    addresses.map(_.key) shouldBe List("198.51.100.1")
    addresses.head.details.head.consumers.toList shouldBe List("consumer-1")

    TrafficSources.callerEndpoints(1, t0).map(_.key) shouldBe List((ConsumerCaller("consumer-1"), "OBPv7.0.0-getBanks"))
  }

  it should "count an anonymous request under its address only, and group unknown paths as unmatched" in {
    TrafficSources.record(note(), "203.0.113.9", 404, 2, t0)
    TrafficSources.consumers(1, t0) shouldBe empty
    TrafficSources.addresses(1, t0).head.details.head.unmatched shouldBe 1L
    TrafficSources.callerEndpoints(1, t0).map(_.key) shouldBe List((AddressCaller("203.0.113.9"), TrafficSources.UnmatchedEndpoint))
  }

  it should "record a refusal under the limiter that refused" in {
    TrafficSources.record(note(refusedBy = Some("ip_penalty")), "203.0.113.9", 429, 1, t0)
    val pair = TrafficSources.callerEndpoints(1, t0).head
    pair.key._2 shouldBe "refused:ip_penalty"
    pair.details.head.refused shouldBe 1L
  }

  it should "merge minutes into the window asked for, and leave out older minutes" in {
    (0 until 3).foreach { m =>
      TrafficSources.record(note(Some("OBPv7.0.0-getBanks")), "203.0.113.9", 200, 5, t0 + m * minute)
    }
    val now = t0 + 2 * minute
    TrafficSources.addresses(1, now).head.requests shouldBe 1L
    TrafficSources.addresses(5, now).head.requests shouldBe 3L
    TrafficSources.addresses(5, now).head.error shouldBe 0L
  }
}
