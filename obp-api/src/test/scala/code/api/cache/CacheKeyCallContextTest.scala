package code.api.cache

import java.io.File

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.io.Source

/**
 * Guards the invariant that no memoize cache key includes the CallContext.
 *
 * CallContext is a case class whose fields include startTime (Some(now)), correlationId, url,
 * verb, ipAddress and user, so its toString differs on every single request. A composite key
 * that renders it is therefore unique per request: the cache can never hit, and every call
 * writes a new Redis entry that survives until its TTL - unbounded key growth on any endpoint
 * with traffic, in exchange for zero cache benefit.
 *
 * Two sites carried callContext in their key (NewStyle.getEndpointMappings and
 * LocalMappedConnectorInternal.getCurrentFxRateCached). Both inherited it from the com.tesobe
 * CacheKeyFromArguments macro, which included every parameter not annotated @CacheKeyOmit -
 * neither site annotated theirs. The explicitization of those keys reproduced the macro output
 * verbatim, so the defect carried over; these two are now the deliberate divergences from the
 * macro-era format, and this test stops a third one appearing.
 *
 * Measured A/B on the two sites with their TTLs forced on: two calls differing only in
 * CallContext wrote two Redis keys at getCurrentFxRateCached (one per request, as above), and
 * zero at getEndpointMappings - there the cached value was a tuple carrying the CallContext,
 * whose lambda chill/Kryo could not encode, so every write failed and was swallowed as a miss.
 * Both are one key after the fix. Hence the second half of the clue below: a CallContext has no
 * business in the cached value either.
 *
 * A source scan rather than a runtime assertion because both TTLs default to 0, and
 * Caching.memoizeSyncWithProvider short-circuits on Duration.Zero without touching Redis - so
 * a live-Redis test would observe nothing under the default test props.
 */
class CacheKeyCallContextTest extends AnyFlatSpec with Matchers {

  /** The composite memoize key form: `val cacheKey = ("Class", "method", List(...).mkString("_"))`. */
  private val compositeCacheKeyLine = """\bcacheKey\s*=\s*\(.*mkString\("_"\)""".r

  /** Surefire runs with basedir = the module dir; a shell run from the repo root needs the prefix. */
  private val mainScalaDir: File =
    List(new File("src/main/scala"), new File("obp-api/src/main/scala"))
      .find(_.isDirectory)
      .getOrElse(fail("Cannot locate obp-api/src/main/scala - this guard must not pass by failing to look."))

  private def scalaFiles(dir: File): Iterator[File] =
    Option(dir.listFiles()).getOrElse(Array.empty[File]).iterator.flatMap {
      case d if d.isDirectory => scalaFiles(d)
      case f if f.getName.endsWith(".scala") => Iterator.single(f)
      case _ => Iterator.empty
    }

  "composite memoize cache keys" should "never render the CallContext" in {
    val offenders = scalaFiles(mainScalaDir).flatMap { file =>
      val source = Source.fromFile(file, "UTF-8")
      try
        source.getLines().zipWithIndex.collect {
          case (line, i)
            if compositeCacheKeyLine.findFirstIn(line).isDefined && line.contains("allContext") =>
            s"${file.getPath}:${i + 1}: ${line.trim}"
        }.toList
      finally source.close()
    }.toList

    withClue(
      "A CallContext in a memoize key makes the key unique per request: the cache never hits and " +
        "every call leaks a Redis entry for a whole TTL. Key on the business arguments only, and " +
        "make sure the cached VALUE does not carry a CallContext either - a hit would hand the " +
        "caller some earlier request's context. Offending lines:\n") {
      offenders shouldBe empty
    }
  }

  it should "have found the cache-key sites at all, so an empty result means clean and not mis-scoped" in {
    val matches = scalaFiles(mainScalaDir).count { file =>
      val source = Source.fromFile(file, "UTF-8")
      try source.getLines().exists(compositeCacheKeyLine.findFirstIn(_).isDefined)
      finally source.close()
    }
    matches should be >= 10
  }
}
