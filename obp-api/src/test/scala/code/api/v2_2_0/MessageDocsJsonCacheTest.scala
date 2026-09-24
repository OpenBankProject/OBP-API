package code.api.v2_2_0

import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger

import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}

class MessageDocsJsonCacheTest extends FlatSpec with Matchers with BeforeAndAfterEach {

  override def beforeEach(): Unit = MessageDocsJsonCache.invalidateAll()

  "MessageDocsJsonCache" should "run the generator once for repeated requests to one connector" in {
    val calls = new AtomicInteger(0)
    val results = (1 to 5).map(_ => MessageDocsJsonCache.getOrCompute("c1") { calls.incrementAndGet(); JSONFactory220.MessageDocsJson(Nil) })
    calls.get shouldBe 1
    results.distinct.size shouldBe 1
    (results.head eq results.last) shouldBe true
  }

  it should "keep one entry per connector" in {
    val calls = new AtomicInteger(0)
    MessageDocsJsonCache.getOrCompute("c1") { calls.incrementAndGet(); JSONFactory220.MessageDocsJson(Nil) }
    MessageDocsJsonCache.getOrCompute("c2") { calls.incrementAndGet(); JSONFactory220.MessageDocsJson(Nil) }
    MessageDocsJsonCache.getOrCompute("c1") { calls.incrementAndGet(); JSONFactory220.MessageDocsJson(Nil) }
    calls.get shouldBe 2
    MessageDocsJsonCache.size shouldBe 2
  }

  it should "run the generator once for a concurrent cold burst" in {
    val calls = new AtomicInteger(0)
    val n = 16
    val pool = Executors.newFixedThreadPool(n)
    val start = new CountDownLatch(1)
    val done = new CountDownLatch(n)
    (1 to n).foreach { _ =>
      pool.execute(new Runnable {
        def run(): Unit = {
          start.await()
          MessageDocsJsonCache.getOrCompute("burst") { calls.incrementAndGet(); Thread.sleep(200); JSONFactory220.MessageDocsJson(Nil) }
          done.countDown()
        }
      })
    }
    start.countDown()
    done.await(30, TimeUnit.SECONDS) shouldBe true
    pool.shutdownNow()
    calls.get shouldBe 1
  }

  it should "not cache a failure and should rethrow the original exception" in {
    val calls = new AtomicInteger(0)
    val boom = new RuntimeException("boom")
    val thrown = the[RuntimeException] thrownBy MessageDocsJsonCache.getOrCompute("bad") { calls.incrementAndGet(); throw boom }
    (thrown eq boom) shouldBe true
    MessageDocsJsonCache.getOrCompute("bad") { calls.incrementAndGet(); JSONFactory220.MessageDocsJson(Nil) }
    calls.get shouldBe 2
  }

  it should "regenerate after invalidateAll" in {
    val calls = new AtomicInteger(0)
    MessageDocsJsonCache.getOrCompute("c1") { calls.incrementAndGet(); JSONFactory220.MessageDocsJson(Nil) }
    MessageDocsJsonCache.invalidateAll()
    MessageDocsJsonCache.getOrCompute("c1") { calls.incrementAndGet(); JSONFactory220.MessageDocsJson(Nil) }
    calls.get shouldBe 2
  }
}
