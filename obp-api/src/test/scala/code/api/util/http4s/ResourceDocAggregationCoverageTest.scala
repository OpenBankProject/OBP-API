package code.api.util.http4s

import code.setup.ServerSetup

import scala.reflect.runtime.{universe => ru}

/**
 * Guards the one thing about `Http4sResourceDocAggregation.allVersions` that a compiler
 * cannot: that it lists every version whose cumulative catalog the object defines.
 *
 * `allVersions` is what `FrozenClassUtil` enumerates, having replaced a classpath scan for
 * `VersionedOBPApis` implementors. A scan could not be forgotten; a hand-written list can.
 * Adding v8.0.0 means adding `lazy val v800` to the chain AND an `allVersions` entry, and
 * omitting the second is silent: the frozen-contract scenarios compare against a persisted
 * fixture that has no v8.0.0 key either, so all five keep passing while the new version's
 * endpoint set and example-class structures go unguarded.
 *
 * So: every `vNNN` catalog the object declares must appear in `allVersions`.
 */
class ResourceDocAggregationCoverageTest extends ServerSetup {

  /** `v121`, `v600`, … — the per-version catalogs, found reflectively so a new one counts. */
  private val declaredCatalogs: Set[String] = {
    val mirror = ru.runtimeMirror(getClass.getClassLoader)
    mirror
      .classSymbol(Http4sResourceDocAggregation.getClass)
      .toType
      .members
      .filter(_.isTerm)
      .map(_.name.decodedName.toString.trim)
      .filter(_.matches("^v\\d{3}$"))
      .toSet
  }

  // The catalog vals are named v121/v510/...; take the digits so the mapping does not
  // depend on how ApiVersion happens to render separators.
  private val listedVersions: List[String] =
    Http4sResourceDocAggregation.allVersions.map(c => "v" + c.version.dottedApiVersion.filter(_.isDigit))

  feature("Http4sResourceDocAggregation.allVersions covers the catalogs it defines") {

    scenario("every per-version catalog declared on the object is listed in allVersions") {
      // Guards the reflection itself, so an empty result can never pass vacuously.
      declaredCatalogs should not be empty
      (declaredCatalogs -- listedVersions.toSet) shouldBe empty
    }

    scenario("no version is listed twice") {
      listedVersions.distinct.size should equal(listedVersions.size)
    }

    scenario("versions are ordered oldest first, as the cumulative chain is") {
      val versions = Http4sResourceDocAggregation.allVersions.map(_.version.dottedApiVersion)
      versions should equal(versions.sortBy { v =>
        val p = v.split('.').map(_.toInt)
        (p(0), p(1), p(2))
      })
    }
  }
}
