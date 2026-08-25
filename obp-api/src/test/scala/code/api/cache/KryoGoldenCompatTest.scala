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
 * That last sentence is the problem this file exists for. The only other Kryo test in the suite,
 * RedisDeserializeMissTest, round-trips through `encode` and `decode` -- BOTH of which run on
 * whichever chill is on the classpath. A format change is invisible to it by construction: the
 * new encoder and the new decoder agree with each other no matter what they agree on.
 *
 * So the fixtures had to be produced from outside the current build. Two of them, both encoded on
 * the pre-migration 2.12 classpath by the real chill 0.9.3, neither regenerable once every
 * checkout carries 0.9.5:
 *
 *   kryo_golden_chill_0_9_3.txt          ten Java values      name<TAB>base64
 *   kryo_scala_golden_chill_0_9_3.tsv    eleven Scala values  name<TAB>base64<TAB>runtime class
 *
 * ── Why the second fixture, and why it records a class ──
 *
 * The first version of this file held Java collections only and compared with `==`. It could not
 * have caught the defect it was written to catch, for two independent reasons, and both were
 * found the hard way -- by the defect reaching a running instance.
 *
 * What OBP-API actually caches is Scala collections; `java.util.ArrayList` appears nowhere in
 * the memoized providers. And under `==` a Scala `List()` EQUALS a `Queue()`: both are `Seq`, and
 * Seq equality is element-wise, so an empty one of each compares equal. An empty `List` written
 * by 0.9.3 decodes under 0.9.5 into a `scala.collection.immutable.Queue`, which the old assertion
 * would have waved through -- while every call site whose signature says `List` fails with
 *
 *     class scala.collection.immutable.Queue cannot be cast to
 *     class scala.collection.immutable.List
 *
 * Measured on GET /management/dynamic-message-docs and GET /management/connector-methods: 200 on
 * 2.12, 500 on 2.13 reading 2.12's entry, for the whole TTL, because a read that throws does not
 * evict the key.
 *
 * So the Scala fixture records the runtime class each value was written as, and this file asserts
 * on THAT. Equality is not enough; the class is what the call site depends on.
 *
 * ── The four outcomes ──
 *
 *   decodes to the same value, same class    fine
 *   fails to decode                          fine -- a cold cache, which the upgrade note accepts
 *   decodes to a DIFFERENT value             asserted against from the start
 *   decodes to an equal value of another CLASS   the one that got through, now asserted
 *
 * Survival counts are reported rather than bounded: how many of the values survive is a fact
 * about two third-party libraries, not something this branch controls, and an assertion on it
 * would freeze a number nobody could act on. What the run is for is the last two outcomes.
 */
class KryoGoldenCompatTest extends FlatSpec with Matchers {

  private val JAVA_FIXTURE  = "/kryo_golden_chill_0_9_3.txt"
  private val SCALA_FIXTURE = "/kryo_scala_golden_chill_0_9_3.tsv"

  private def readFixture(path: String): List[Array[String]] = {
    val stream = getClass.getResourceAsStream(path)
    stream should not be null
    val src = Source.fromInputStream(stream, "UTF-8")
    try src.getLines()
          .filterNot(l => l.trim.isEmpty || l.startsWith("#"))
          .map(_.split("\t"))
          .toList
    finally src.close()
  }

  /** name -> the bytes chill 0.9.3 produced for it. */
  private lazy val javaGolden: List[(String, Array[Byte])] =
    readFixture(JAVA_FIXTURE).map(f => f(0) -> Base64.getDecoder.decode(f(1)))

  /** name -> (bytes, the runtime class the value had WHEN WRITTEN). */
  private lazy val scalaGolden: List[(String, Array[Byte], String)] =
    readFixture(SCALA_FIXTURE).map(f => (f(0), Base64.getDecoder.decode(f(1)), f(2)))

  /** The values the Java bytes are supposed to mean. Written out here, not derived. */
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

  // ── fixtures present ───────────────────────────────────────────────────────────────

  "both fixtures" should "be present and non-trivial" in {
    // A fixture that failed to load would make every assertion below vacuous.
    withClue("kryo_golden_chill_0_9_3.txt is missing or empty -- without it this file asserts " +
             "nothing at all. It cannot be regenerated from this branch; recover it from git. ") {
      javaGolden.size should be >= 8
    }
    withClue("kryo_scala_golden_chill_0_9_3.tsv is missing or empty. This is the fixture that " +
             "covers what OBP-API actually caches; without it the Java values alone would pass " +
             "while the Scala ones drift, which is exactly what happened once already. ") {
      scalaGolden.size should be >= 8
    }
    scalaGolden.map(_._1).toSet should contain allOf ("scala-list-empty", "scala-map", "scala-option-none")
  }

  // ── the assertion the first version was missing ────────────────────────────────────

  /**
   * Class drift that is known, and the reason it can no longer reach a caller.
   *
   * A signed-off baseline rather than a hard zero, for the same reason the contract suite keeps
   * pr90-base.accepted.json: this is a property of two third-party libraries, not something this
   * branch can change, and a permanently red suite is a suite people learn to ignore. What must
   * stay red is drift that nobody has looked at -- so anything NOT listed here fails, and adding
   * a line means writing down why it is safe.
   */
  private val knownDrift: Map[String, String] = Map(
    "scala-list-empty" ->
      ("Nil$ decodes as Queue under chill 0.9.5. Mitigated by Redis.serializationNamespace: the " +
       "cache key carries the Scala binary version, so a 2.13 instance cannot address the entry " +
       "a 2.12 instance wrote and it expires on its own TTL. CacheSerializationNamespaceTest " +
       "pins that isolation; remove it and this becomes reachable again.")
  )

  it should "never decode a Scala value into a DIFFERENT runtime class, except where recorded" in {
    import com.twitter.chill.KryoInjection

    val drifted = scalaGolden.flatMap { case (name, bytes, writtenAs) =>
      KryoInjection.invert(bytes).toOption.flatMap { v =>
        val nowIs = if (v == null) "null" else v.getClass.getName
        // Subclassing is not drift: a Vector written as `Vector` and read back as `Vector1` is
        // still assignable to every signature that named Vector, and nothing at a call site can
        // tell. What breaks is a class that is merely EQUAL -- List() == Queue() is true, and a
        // `List` signature still throws ClassCastException on it.
        val assignable =
          try Class.forName(writtenAs).isInstance(v) catch { case _: Throwable => nowIs == writtenAs }
        if (assignable) None
        else Some(s"$name: written as <$writtenAs>, decodes under this chill as <$nowIs>" +
                  (if (v.isInstanceOf[Iterable[_]]) " -- equal by value, so an == comparison " +
                     "would call this correct while every call site declaring the original type " +
                     "fails with ClassCastException" else ""))
      }
    }

    val unexplained = drifted.filterNot(line => knownDrift.keys.exists(k => line.startsWith(k + ":")))
    drifted.foreach { line =>
      knownDrift.collectFirst { case (k, why) if line.startsWith(k + ":") =>
        info(s"known drift -- $line")
        info(s"   mitigation: $why")
      }
    }

    withClue(s"${unexplained.size} Scala value(s) drift into a class the original signature cannot " +
             s"hold, and are not in knownDrift. This is not a cold cache -- the read SUCCEEDS and " +
             s"the caller gets a ClassCastException for the whole TTL, with nothing in any log to " +
             s"say so. Either mitigate it or add it to knownDrift with the reason it cannot reach " +
             s"a caller:\n${unexplained.mkString("\n")}\n") {
      unexplained shouldBe empty
    }

    // The baseline must not outlive what it describes. A name listed here that no longer drifts
    // is a line nobody will delete, and the next reader takes it as still true.
    val staleEntries = knownDrift.keys.filterNot(k => drifted.exists(_.startsWith(k + ":"))).toList
    withClue(s"knownDrift lists ${staleEntries.mkString(", ")}, which no longer drift. Remove " +
             s"them, or the baseline documents a hazard that stopped existing. ") {
      staleEntries shouldBe empty
    }
  }

  it should "never decode old bytes into a DIFFERENT value" in {
    import com.twitter.chill.KryoInjection

    val misread = javaGolden.flatMap { case (name, bytes) =>
      KryoInjection.invert(bytes) match {
        case scala.util.Success(v) if !sameValue(v, expected(name)) =>
          Some(s"$name: old bytes decoded to <$v> (${v.getClass.getName}) but were written as " +
               s"<${expected(name)}> (${expected(name).getClass.getName})")
        case _ => None   // decoded correctly, or failed -- both acceptable, see the header
      }
    }

    withClue(s"${misread.size} value(s) written by chill 0.9.3 decode under this chill into " +
             s"something OTHER than what was written:\n${misread.mkString("\n")}\n") {
      misread shouldBe empty
    }
  }

  it should "report how much of an existing cache survives the upgrade" in {
    import com.twitter.chill.KryoInjection

    val (jOk, jFailed) = javaGolden.partition { case (name, bytes) =>
      Try(KryoInjection.invert(bytes)).toOption.flatMap(_.toOption).exists(sameValue(_, expected(name)))
    }
    val (sOk, sFailed) = scalaGolden.partition { case (_, bytes, writtenAs) =>
      KryoInjection.invert(bytes).toOption.exists(v =>
        try Class.forName(writtenAs).isInstance(v) catch { case _: Throwable => false })
    }
    // Informational on purpose -- see the header. The rollout consequence of a failure is a
    // recompute, which is a cost rather than a defect, and pinning the number would freeze a
    // property of two third-party libraries.
    info(s"java  ${jOk.size}/${javaGolden.size} values written by chill 0.9.3 still decode correctly")
    info(s"scala ${sOk.size}/${scalaGolden.size} values still decode into an assignable class")
    if (jFailed.nonEmpty) info(s"cold on rollout (java):  ${jFailed.map(_._1).mkString(", ")}")
    if (sFailed.nonEmpty) info(s"cold on rollout (scala): ${sFailed.map(_._1).mkString(", ")}")
    succeed
  }
}
