package code.telemetry

import org.scalatest.{FlatSpec, Matchers}

import scala.collection.mutable

/**
 * This suite checks the guarantees of HeavyHitters, the Space-Saving algorithm (Metwally, Agrawal and El Abbadi, ICDT 2005): below
 * capacity it counts exactly; a key with more than N / capacity offers is always kept; and every
 * entry's true count lies between `count - error` and `count`.
 */
class HeavyHittersTest extends FlatSpec with Matchers {

  final class Hits { var n = 0 }

  "HeavyHitters" should "count exactly while it has free slots" in {
    val table = new HeavyHitters[String, Hits](10, () => new Hits)
    List("a", "b", "a", "c", "a").foreach(k => table.offer(k)(_.n += 1))
    table.snapshot.map(e => e.key -> (e.count, e.error)).toMap shouldBe Map("a" -> (3L, 0L), "b" -> (1L, 0L), "c" -> (1L, 0L))
    table.isFull shouldBe false
  }

  it should "keep a heavy key through a flood of distinct keys, within its error bound" in {
    val capacity = 10
    val table = new HeavyHitters[String, Hits](capacity, () => new Hits)
    val truth = mutable.Map.empty[String, Long].withDefaultValue(0L)
    def offer(k: String): Unit = { truth(k) += 1; table.offer(k)(_.n += 1) }
    // 300 offers of one key among 1,000 distinct keys: 300 > 1,300 / 10, so it must be kept.
    (1 to 1000).foreach { i => offer(s"noise-$i"); if (i % 10 == 0) (1 to 3).foreach(_ => offer("heavy")) }

    val entries = table.snapshot
    entries.size shouldBe capacity
    entries.map(_.key) should contain("heavy")
    entries.foreach { e =>
      withClue(s"${e.key}: count ${e.count}, error ${e.error}, true ${truth(e.key)}: ") {
        truth(e.key) should be <= e.count
        truth(e.key) should be >= (e.count - e.error)
      }
    }
    table.totalOffered shouldBe 1300L
  }

  it should "start a replacement entry's details afresh" in {
    val table = new HeavyHitters[String, Hits](1, () => new Hits)
    table.offer("a")(_.n += 1)
    table.offer("a")(_.n += 1)
    table.offer("b")(_.n += 1) // replaces "a": count 3, error 2, details from this offer only
    val only = table.snapshot.head
    (only.key, only.count, only.error, only.details.n) shouldBe (("b", 3L, 2L, 1))
  }
}
