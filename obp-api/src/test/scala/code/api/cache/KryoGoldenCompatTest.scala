package code.api.cache

import org.scalatest.{FlatSpec, Matchers}

import java.util.Base64
import scala.io.Source
import scala.util.Try

/**
 * What the OLD chill wrote, the NEW chill must not silently misread.
 *
 * PR #2890 moves chill 0.9.3 -> 0.9.5 (and chill-bijection 0.9.1 -> 0.9.5), and a chill upgrade
 * carries a Kryo upgrade. The commit that made the move says what that means for a live system:
 *
 *   "the entries already in Redis were written by the old one, so some of them will fail to
 *    decode after the rollout. A test environment never shows this, because it starts from an
 *    empty cache."
 *
 * That last sentence is the problem this file exists for. The only Kryo test in the suite,
 * RedisDeserializeMissTest, round-trips through `encode` and `decode` -- BOTH of which run on
 * whichever chill is on the classpath. A format change is invisible to it by construction: the
 * new encoder and the new decoder agree with each other no matter what they agree on.
 *
 * So the fixture had to be produced from outside the current build:
 * `src/test/resources/kryo_golden_chill_0_9_3.txt` holds ten values encoded by the real chill
 * 0.9.3, taken from the pre-migration worktree's own classpath. It cannot be regenerated once
 * this branch merges -- every classpath in the repository will have the new chill, and "what the
 * old one wrote" stops being observable. Regenerating it with 0.9.5 would turn this file into a
 * test that the new chill can read itself, which is what RedisDeserializeMissTest already does.
 *
 * ── What is asserted, and what deliberately is not ──
 *
 * NOT asserted: that every value still decodes. The migration commit accepts that some will not,
 * and the accepted consequence is a cold cache -- the codec reports the failure, scalacache
 * treats the read as a miss, the source block recomputes and rewrites the key.
 *
 * Asserted: that a value either decodes to the SAME value, or fails outright. The outcome that
 * must never happen is the third one -- decoding "successfully" into something different.
 * A silent misread is not a cold cache; it is wrong data served for a full TTL, and no log line
 * anywhere would say so.
 *
 * The count is reported rather than bounded. How many of the ten survive is a fact about two
 * library versions, not something this branch controls, and an assertion on it would be a
 * number nobody could act on. What the run is for is the third outcome, and the printed
 * breakdown, which tells whoever reads it how cold the cache will actually be on rollout.
 */
class KryoGoldenCompatTest extends FlatSpec with Matchers {

  private val FIXTURE = "/kryo_golden_chill_0_9_3.txt"

  /** name -> the bytes chill 0.9.3 produced for it. */
  private lazy val golden: List[(String, Array[Byte])] = {
    val stream = getClass.getResourceAsStream(FIXTURE)
    stream should not be null
    val src = Source.fromInputStream(stream, "UTF-8")
    try src.getLines()
          .filterNot(l => l.trim.isEmpty || l.startsWith("#"))
          .map { line =>
            val Array(name, b64) = line.split("\t", 2)
            name -> Base64.getDecoder.decode(b64)
          }.toList
    finally src.close()
  }

  /** The values those bytes are supposed to mean. Written out here, not derived. */
  private val expected: Map[String, Any] = Map(
    "string"       -> "a-cached-string",
    "int"          -> 42,
    "long"         -> 1234567890123L,
    "boolean"      -> true,
    "double"       -> 3.25d,
    "jlist-string" -> java.util.Arrays.asList("a", "b", "c"),
    "jlist-empty"  -> new java.util.ArrayList[String](),
    "jmap"         -> { val m = new java.util.LinkedHashMap[String, String](); m.put("k1", "v1"); m },
    "nested"       -> java.util.Arrays.asList(
                        java.util.Arrays.asList("x"),
                        java.util.Arrays.asList("y", "z")),
    "byte-array"   -> Array[Byte](1, 2, 3, 4)
  )

  private def sameValue(a: Any, b: Any): Boolean = (a, b) match {
    case (x: Array[_], y: Array[_]) => x.sameElements(y)
    case (x, y)                     => x == y
  }

  "the fixture" should "be present and non-trivial" in {
    // A fixture that failed to load would make every assertion below vacuous.
    withClue("kryo_golden_chill_0_9_3.txt is missing or empty -- without it this file asserts " +
             "nothing at all. It cannot be regenerated from this branch; recover it from git. ") {
      golden.size should be >= 8
    }
    golden.map(_._1).toSet should contain allOf ("string", "jlist-string", "jmap")
  }

  it should "never decode old bytes into a DIFFERENT value" in {
    import com.twitter.chill.KryoInjection

    val misread = golden.flatMap { case (name, bytes) =>
      KryoInjection.invert(bytes) match {
        case scala.util.Success(v) if !sameValue(v, expected(name)) =>
          Some(s"$name: old bytes decoded to <$v> (${v.getClass.getName}) but were written as " +
               s"<${expected(name)}> (${expected(name).getClass.getName})")
        case _ => None   // decoded correctly, or failed -- both acceptable, see the header
      }
    }

    withClue(s"${misread.size} value(s) written by chill 0.9.3 decode under 0.9.5 into something " +
             s"OTHER than what was written. This is the one outcome the upgrade note does not " +
             s"cover: not a cold cache, but wrong data served for a full TTL, with nothing in " +
             s"any log to say so:\n${misread.mkString("\n")}\n") {
      misread shouldBe empty
    }
  }

  it should "report how much of an existing cache survives the upgrade" in {
    import com.twitter.chill.KryoInjection

    val (ok, failed) = golden.partition { case (name, bytes) =>
      Try(KryoInjection.invert(bytes)).toOption.flatMap(_.toOption).exists(sameValue(_, expected(name)))
    }
    // Informational on purpose -- see the header. The rollout consequence of the failures is a
    // recompute, which is a cost rather than a defect, and pinning the number would freeze a
    // property of two third-party libraries.
    info(s"${ok.size}/${golden.size} values written by chill 0.9.3 still decode correctly under " +
         s"the chill on this classpath")
    if (failed.nonEmpty)
      info(s"cold on rollout (recomputed on first read): ${failed.map(_._1).mkString(", ")}")
    succeed
  }
}
