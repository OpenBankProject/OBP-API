package code.api.cache

import org.scalatest.{FlatSpec, Matchers}

/**
 * Guards the cache self-healing contract of Redis.deserialize.
 *
 * The Kryo codec used for Redis-backed memoization must REPORT A FAILURE when the cached bytes
 * cannot be decoded (corrupt entry, class-shape change across a redeploy, Kryo registration
 * drift), and scalacache must turn that into a MISS: recompute from the source block, repopulate
 * the key, self-heal on the next call.
 *
 * How the failure is reported changed with scalacache 0.28. The codec used to throw from
 * deserialize; it now returns Left(FailedToDecode) from decode. The contract is unchanged, but the
 * machinery moved: RedisCacheBase.doGet raises the Left, and AbstractCache._caching - the path
 * memoize goes through - wraps the read in handleNonFatal and substitutes None. Reading only doGet
 * suggests the error reaches the caller; it does not.
 *
 * The old behaviour returned the sentinel "NONE".asInstanceOf[T] instead, which
 * scalacache treated as a valid HIT — every caller expecting the real type got a
 * ClassCastException for the whole TTL. These tests fail if that sentinel ever
 * comes back.
 */
class RedisDeserializeMissTest extends FlatSpec with Matchers {

  private def codec[T](implicit m: Manifest[T]) = Redis.anyToByte[T]

  "Redis codec decode" should "report a failure on undecodable bytes instead of returning a sentinel value" in {
    val garbage: Array[Byte] = Array[Byte](0x7f, 0x00, 0x33, -1, 42, 9, 88, 0x11)
    codec[List[String]].decode(garbage).isLeft shouldBe true
  }

  it should "never yield the legacy \"NONE\" sentinel for corrupt bytes" in {
    val garbage: Array[Byte] = Array[Byte](-128, -1, -2, -3, 0, 1, 2, 3)
    val outcome = codec[String].decode(garbage)
    outcome.isLeft shouldBe true
    outcome.right.toOption should not be Some("NONE")
  }

  it should "round-trip a value encoded by the same codec" in {
    val value = List("mapped", "rest_vMar2019", "rabbitmq_vOct2024")
    val bytes = codec[List[String]].encode(value)
    codec[List[String]].decode(bytes) shouldBe Right(value)
  }
}
