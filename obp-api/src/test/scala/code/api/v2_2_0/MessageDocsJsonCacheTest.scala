package code.api.v2_2_0

import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger

import code.api.v2_2_0.MessageDocsJsonCache.SharedStore
import org.json4s.JValue
import org.json4s.JsonDSL._
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}

import scala.collection.concurrent.TrieMap

class MessageDocsJsonCacheTest extends FlatSpec with Matchers with BeforeAndAfterEach {

  /** A shared level that counts traffic; `down` makes it behave like an unreachable Redis. */
  private class FakeStore(down: Boolean = false) extends SharedStore {
    val data = TrieMap.empty[String, String]
    val gets = new AtomicInteger(0)
    val sets = new AtomicInteger(0)
    def get(key: String): Option[String] = { gets.incrementAndGet(); if (down) None else data.get(key) }
    def set(key: String, value: String): Unit = { sets.incrementAndGet(); if (!down) data.put(key, value) }
  }

  private def doc(tag: String): JValue = ("message_docs" -> List(("process" -> tag): JValue))

  override def beforeEach(): Unit = MessageDocsJsonCache.invalidateAll()

  "MessageDocsJsonCache" should "run the generator once for repeated requests to one connector" in {
    val store = new FakeStore
    val calls = new AtomicInteger(0)
    val results = (1 to 5).map(_ => MessageDocsJsonCache.getOrCompute("c1", store) { calls.incrementAndGet(); doc("c1") })
    calls.get shouldBe 1
    results.distinct.size shouldBe 1
    (results.head eq results.last) shouldBe true
    store.gets.get shouldBe 1 // the in-process level answers every request after the first
    store.sets.get shouldBe 1
  }

  it should "keep one entry per connector" in {
    val store = new FakeStore
    val calls = new AtomicInteger(0)
    MessageDocsJsonCache.getOrCompute("c1", store) { calls.incrementAndGet(); doc("c1") }
    MessageDocsJsonCache.getOrCompute("c2", store) { calls.incrementAndGet(); doc("c2") }
    MessageDocsJsonCache.getOrCompute("c1", store) { calls.incrementAndGet(); doc("c1") }
    calls.get shouldBe 2
    MessageDocsJsonCache.size shouldBe 2
    store.data.size shouldBe 2
  }

  it should "run the generator once for a concurrent cold burst" in {
    val store = new FakeStore
    val calls = new AtomicInteger(0)
    val n = 16
    val pool = Executors.newFixedThreadPool(n)
    val start = new CountDownLatch(1)
    val done = new CountDownLatch(n)
    (1 to n).foreach { _ =>
      pool.execute(new Runnable {
        def run(): Unit = {
          start.await()
          MessageDocsJsonCache.getOrCompute("burst", store) { calls.incrementAndGet(); Thread.sleep(200); doc("burst") }
          done.countDown()
        }
      })
    }
    start.countDown()
    done.await(30, TimeUnit.SECONDS) shouldBe true
    pool.shutdownNow()
    calls.get shouldBe 1
    store.gets.get shouldBe 1
    store.sets.get shouldBe 1
  }

  it should "not cache a failure and should rethrow the original exception" in {
    val store = new FakeStore
    val calls = new AtomicInteger(0)
    val boom = new RuntimeException("boom")
    val thrown = the[RuntimeException] thrownBy MessageDocsJsonCache.getOrCompute("bad", store) { calls.incrementAndGet(); throw boom }
    (thrown eq boom) shouldBe true
    store.sets.get shouldBe 0
    MessageDocsJsonCache.getOrCompute("bad", store) { calls.incrementAndGet(); doc("bad") }
    calls.get shouldBe 2
  }

  it should "serve from the shared level without running the generator, e.g. after a restart" in {
    val store = new FakeStore
    MessageDocsJsonCache.getOrCompute("c1", store) { doc("c1") }
    MessageDocsJsonCache.invalidateAll() // a fresh process: empty in-process level, warm Redis
    val calls = new AtomicInteger(0)
    val again = MessageDocsJsonCache.getOrCompute("c1", store) { calls.incrementAndGet(); doc("other") }
    calls.get shouldBe 0
    again shouldBe doc("c1")
    store.sets.get shouldBe 1
  }

  it should "still cache in-process when the shared level is unreachable" in {
    val store = new FakeStore(down = true)
    val calls = new AtomicInteger(0)
    (1 to 5).foreach(_ => MessageDocsJsonCache.getOrCompute("c1", store) { calls.incrementAndGet(); doc("c1") })
    calls.get shouldBe 1
  }

  it should "treat an unparsable shared entry as a miss and regenerate" in {
    val store = new FakeStore
    store.data.put("message-docs-v2.2.0-c1", "{not json")
    val calls = new AtomicInteger(0)
    val r = MessageDocsJsonCache.getOrCompute("c1", store) { calls.incrementAndGet(); doc("c1") }
    calls.get shouldBe 1
    r shouldBe doc("c1")
  }

  it should "serve the same JSON from the generating instance as from another instance reading the shared level" in {
    // Numbers are where a render/parse round trip can change the value or its formatting.
    val awkward: JValue = ("message_docs" -> List[JValue](
      ("decimal" -> BigDecimal("10.10")): JValue,
      ("double" -> 0.1): JValue,
      ("big" -> BigInt("12345678901234567890")): JValue,
      ("neg" -> -5): JValue))
    val store = new FakeStore
    val generated = MessageDocsJsonCache.getOrCompute("c1", store) { awkward }
    MessageDocsJsonCache.invalidateAll() // another replica: empty in-process level, same shared level
    val fromShared = MessageDocsJsonCache.getOrCompute("c1", store) { fail("must be served from the shared level") }
    generated shouldBe fromShared
    org.json4s.native.JsonMethods.compact(org.json4s.native.JsonMethods.render(generated)) shouldBe
      org.json4s.native.JsonMethods.compact(org.json4s.native.JsonMethods.render(fromShared))
  }

  it should "regenerate after invalidateAll when the shared level is empty" in {
    val calls = new AtomicInteger(0)
    val a = new FakeStore
    MessageDocsJsonCache.getOrCompute("c1", a) { calls.incrementAndGet(); doc("c1") }
    MessageDocsJsonCache.invalidateAll()
    MessageDocsJsonCache.getOrCompute("c1", new FakeStore) { calls.incrementAndGet(); doc("c1") }
    calls.get shouldBe 2
  }
}
