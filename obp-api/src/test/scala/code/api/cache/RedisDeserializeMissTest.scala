package code.api.cache

import org.scalatest.{FlatSpec, Matchers}

/**
 * Guards the cache self-healing contract of Redis.deserialize.
 *
 * The Kryo codec used for Redis-backed memoization must THROW when the cached
 * bytes cannot be decoded (corrupt entry, class-shape change across a redeploy,
 * Kryo registration drift). scalacache treats a throwing cache read as a MISS:
 * it recomputes the value from the source block and repopulates the key, so the
 * cache self-heals on the next call.
 *
 * The old behaviour returned the sentinel "NONE".asInstanceOf[T] instead, which
 * scalacache treated as a valid HIT — every caller expecting the real type got a
 * ClassCastException for the whole TTL. These tests fail if that sentinel ever
 * comes back.
 */
class RedisDeserializeMissTest extends FlatSpec with Matchers {

  private def codec[T](implicit m: Manifest[T]) = Redis.anyToByte[T]

  "Redis codec deserialize" should "throw on undecodable bytes instead of returning a sentinel value" in {
    val garbage: Array[Byte] = Array[Byte](0x7f, 0x00, 0x33, -1, 42, 9, 88, 0x11)
    an[Exception] should be thrownBy codec[List[String]].deserialize(garbage)
  }

  it should "never yield the legacy \"NONE\" sentinel for corrupt bytes" in {
    val garbage: Array[Byte] = Array[Byte](-128, -1, -2, -3, 0, 1, 2, 3)
    val outcome = scala.util.Try(codec[String].deserialize(garbage))
    outcome.isFailure shouldBe true
    outcome.toOption should not be Some("NONE")
  }

  it should "round-trip a value serialized by the same codec" in {
    val value = List("mapped", "rest_vMar2019", "rabbitmq_vOct2024")
    val bytes = codec[List[String]].serialize(value)
    codec[List[String]].deserialize(bytes) shouldBe value
  }
}
