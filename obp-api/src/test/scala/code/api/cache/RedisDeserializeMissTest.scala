package code.api.cache
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers


/**
 * Guards the cache self-healing contract of the Redis memoize codec.
 *
 * The Kryo codec used for Redis-backed memoization must REPORT A FAILURE when the cached bytes
 * cannot be decoded (corrupt entry, a class-shape change across a redeploy, Kryo registration
 * drift), and the memoize layer must turn that into a MISS: recompute from the source block,
 * repopulate the key, self-heal on the next call.
 *
 * History: the pre-scalacache-0.28 codec returned the sentinel "NONE".asInstanceOf[T] instead,
 * which the cache treated as a valid HIT - every caller expecting the real type got a
 * ClassCastException for the whole TTL. scalacache 0.28 moved to Left(FailedToDecode); the
 * in-house memoize layer that replaced scalacache expresses the same contract as decode
 * returning None. These tests fail if a sentinel ever comes back.
 */
class RedisDeserializeMissTest extends AnyFlatSpec with Matchers {

  "Redis codec decode" should "report a miss (None) on undecodable bytes instead of returning a sentinel value" in {
    val garbage: Array[Byte] = Array[Byte](0x7f, 0x00, 0x33, -1, 42, 9, 88, 0x11)
    Redis.decode[List[String]](garbage) shouldBe None
  }

  it should "never yield the legacy \"NONE\" sentinel for corrupt bytes" in {
    val garbage: Array[Byte] = Array[Byte](-128, -1, -2, -3, 0, 1, 2, 3)
    val outcome = Redis.decode[String](garbage)
    outcome shouldBe None
    outcome should not be Some("NONE")
  }

  it should "round-trip a value encoded by the same codec" in {
    val value = List("mapped", "rest_vMar2019", "rabbitmq_vOct2024")
    val bytes = Redis.encode(value)
    Redis.decode[List[String]](bytes) shouldBe Some(value)
  }
}
