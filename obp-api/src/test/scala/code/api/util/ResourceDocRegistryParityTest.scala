package code.api.util

import code.setup.ServerSetup
import org.scalatest.Tag

/**
 * Guards the invariant that APIUtil.getAllResourceDocs — the global operation-id
 * registry used wherever an operation id must be resolved (api-collection endpoint
 * validation, top-apis operation-id lookups, ...) — contains every per-standard
 * resource-doc surface the resource-docs dispatcher can serve to API Explorer.
 *
 * These are two parallel registries (ResourceDocsAPIMethods dispatches per
 * standard/version; getAllResourceDocs aggregates them all), and they have drifted
 * twice: Berlin Group v2 was served by the dispatcher but missing from the global
 * registry (so BGv2-getAccountDetails could not be added to an API collection),
 * and the global registry was based on the v6 aggregation, excluding v7-only
 * operation ids. When you add a NEW API standard, register its docs in BOTH
 * places — and add its surface to this list.
 */
class ResourceDocRegistryParityTest extends ServerSetup {

  object RegistryParityTag extends Tag("ResourceDocRegistryParity")

  private lazy val allOperationIds: Set[String] =
    APIUtil.getAllResourceDocs.map(_.operationId).toSet

  private lazy val surfaces: List[(String, Seq[String])] = List(
    ("OBP standard (v7 aggregation)", code.api.v7_0_0.Http4s700.allResourceDocs.map(_.operationId).toSeq),
    ("Berlin Group v1.3", code.api.berlin.group.v1_3.Http4sBGv13.resourceDocs.map(_.operationId).toSeq),
    ("Berlin Group v2", code.api.berlin.group.v2.Http4sBGv2.resourceDocs.map(_.operationId).toSeq),
    ("UK Open Banking 2.0.0", code.api.UKOpenBanking.v2_0_0.OBP_UKOpenBanking_200.allResourceDocs.map(_.operationId).toSeq),
    ("UK Open Banking 3.1.0", code.api.UKOpenBanking.v3_1_0.OBP_UKOpenBanking_310.allResourceDocs.map(_.operationId).toSeq),
    ("UK Open Banking 4.0.1", code.api.UKOpenBanking.v4_0_1.OBP_UKOpenBanking_401.allResourceDocs.map(_.operationId).toSeq)
  )

  feature("getAllResourceDocs contains every per-standard resource-doc surface") {
    surfaces.foreach { case (label, operationIds) =>
      scenario(s"$label operation ids are all resolvable globally", RegistryParityTag) {
        operationIds should not be empty
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
