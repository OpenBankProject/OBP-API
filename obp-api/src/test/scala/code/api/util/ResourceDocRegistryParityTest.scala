package code.api.util

import code.setup.ServerSetup
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}
import org.scalatest.Tag

/**
 * Guards the invariant that APIUtil.getAllResourceDocs — the global operation-id registry used
 * wherever an operation id must be resolved (api-collection endpoint validation, top-apis
 * operation-id lookups, ...) — contains every per-standard resource-doc surface the resource-docs
 * dispatcher can serve to API Explorer.
 *
 * Both sides are now derived from the single ResourceDocRegistry.registry map, so this class of
 * drift (which happened three times by hand: Berlin Group v2, v7-only operation ids, and the
 * Berlin Group v1.3 alias) is structurally impossible going forward — see ResourceDocRegistry's
 * doc comment. This test's job is narrower than it used to be: it iterates the registry itself
 * (rather than a hand-typed list of standards) so it stays correct as standards are added or
 * removed without needing an edit here, and it catches an accidental regression back to two
 * independently hand-maintained registries.
 */
class ResourceDocRegistryParityTest extends ServerSetup {

  object RegistryParityTag extends Tag("ResourceDocRegistryParity")

  private lazy val allOperationIds: Set[String] =
    APIUtil.getAllResourceDocs.map(_.operationId).toSet

  private def label(version: ApiVersion): String = version match {
    case sv: ScannedApiVersion => sv.fullyQualifiedVersion
    case other => other.toString
  }

  // Dynamic arms (dynamic-endpoint / dynamic-entity) are excluded: they are runtime-mutable and
  // ResourceDocRegistry.allStaticResourceDocs itself excludes them for the same reason (see its
  // doc comment) -- APIUtil.getAllResourceDocs appends them fresh via allDynamicResourceDocs
  // instead, so comparing them against this registry-derived snapshot would be meaningless.
  private lazy val surfaces: List[(String, Seq[String])] =
    (ResourceDocRegistry.registry - ApiVersion.`dynamic-endpoint` - ApiVersion.`dynamic-entity`)
      .toList
      .map { case (version, docsThunk) => (label(version), docsThunk().map(_.operationId)) }

  feature("getAllResourceDocs contains every per-standard resource-doc surface the dispatcher can serve") {
    scenario("the registry itself is non-empty", RegistryParityTag) {
      surfaces should not be empty
      surfaces.exists(_._2.nonEmpty) shouldBe true
    }

    surfaces.foreach { case (label, operationIds) =>
      scenario(s"$label operation ids are all resolvable globally", RegistryParityTag) {
        val missing = operationIds.filterNot(allOperationIds.contains)
        withClue(s"$label operation ids missing from getAllResourceDocs: ${missing.take(10).mkString(", ")} ") {
          missing shouldBe empty
        }
      }
    }

    scenario("the operation id from the sandbox bug report resolves", RegistryParityTag) {
      allOperationIds should contain("BGv2-getAccountDetails")
    }
  }
}
