package code.api.util

import code.api.berlin.group.v1_3.{Http4sBGv13Alias, OBP_BERLIN_GROUP_1_3_Alias}
import code.setup.ServerSetup
import com.openbankproject.commons.util.{ApiStandards, ApiVersion, ScannedApiVersion}
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

  // The Berlin Group v1.3 alias is the one surface in the union a deployment can switch off:
  // berlin_group_v1_3_alias_path is unset by default and is supplied for test runs by
  // test.default.props and by the two CI workflows. test.default.props is gitignored
  // (.gitignore:21), so a fresh clone, a colleague's checkout or an IDE ScalaTest run may not have
  // it -- assertions that genuinely need the alias cancel there instead of failing with a message
  // that gives no hint a prop is missing.
  private lazy val aliasVersion: ApiVersion = OBP_BERLIN_GROUP_1_3_Alias.apiVersion
  private lazy val aliasIsConfigured: Boolean = Http4sBGv13Alias.resourceDocs.nonEmpty
  private val aliasNotConfigured =
    "berlin_group_v1_3_alias_path is not set, so the Berlin Group v1.3 alias contributes no docs"

  private def label(version: ApiVersion): String = version match {
    case v if v == aliasVersion && !aliasIsConfigured => "Berlin Group v1.3 alias (not configured)"
    case sv: ScannedApiVersion => sv.fullyQualifiedVersion
    case other => other.toString
  }

  // Scoped to ResourceDocRegistry.unionVersions -- the current OBP surface plus every non-OBP
  // standard. The superseded OBP aggregations (v6.0.0 and older) and the two dynamic arms are
  // deliberately out of the union; see ResourceDocRegistry.obpUnionVersion for why, and for the
  // accepted consequence that an operation id living only in a superseded aggregation stays
  // unresolvable.
  private lazy val surfaces: List[(ApiVersion, String, Seq[String])] =
    ResourceDocRegistry.unionVersions.toList
      .map(version => (version, label(version), ResourceDocRegistry.docsFor(version).map(_.operationId)))

  feature("getAllResourceDocs contains every per-standard resource-doc surface the union covers") {
    scenario("the registry itself is non-empty", RegistryParityTag) {
      surfaces should not be empty
    }

    surfaces.foreach { case (version, label, operationIds) =>
      scenario(s"$label operation ids are all resolvable globally", RegistryParityTag) {
        if (version == aliasVersion && !aliasIsConfigured) cancel(aliasNotConfigured)
        // Non-empty matters as much as membership: an empty surface is trivially a subset of the
        // union, so without this a standard whose docs silently stop being registered (the very
        // failure mode this test exists for) would pass unnoticed.
        withClue(s"$label contributed no operation ids at all -- did its docs stop being registered? ") {
          operationIds should not be empty
        }
        val missing = operationIds.filterNot(allOperationIds.contains)
        withClue(s"$label operation ids missing from getAllResourceDocs: ${missing.take(10).mkString(", ")} ") {
          missing shouldBe empty
        }
      }
    }

    // Guards the one hand-maintained knob left in the registry: if a v8.0.0 aggregation is added
    // without moving obpUnionVersion, the union would keep serving the v7 surface and every
    // v8-only operation id would silently be unresolvable -- the exact bug this PR started from.
    scenario("obpUnionVersion is the newest OBP-standard version in the registry", RegistryParityTag) {
      val obpVersions = ResourceDocRegistry.registry.keys.toList.collect {
        case sv: ScannedApiVersion
          if sv.apiStandard == ApiStandards.obp.toString &&
             sv != ApiVersion.`dynamic-endpoint` && sv != ApiVersion.`dynamic-entity` => sv
      }
      obpVersions should not be empty

      // Rank by position in ApiVersionUtils.versions, which lists the OBP versions oldest-first.
      // indexOf returns -1 for anything absent from that (also hand-maintained) list, and a -1
      // would lose every maxBy comparison -- so a v8.0.0 added to the registry but not to
      // ApiVersionUtils.versions would leave v7 as the maximum and let this scenario pass, in
      // exactly the two-places-to-edit case it exists to catch. Establish coverage first.
      val unranked = obpVersions.filter(ApiVersionUtils.versions.indexOf(_) < 0)
      withClue(s"OBP versions in the registry but missing from ApiVersionUtils.versions: " +
        s"${unranked.map(_.fullyQualifiedVersion).mkString(", ")} -- add them there so they can be " +
        s"ranked, otherwise this guard cannot see them ") {
        unranked shouldBe empty
      }

      val newest = obpVersions.maxBy(ApiVersionUtils.versions.indexOf(_))
      withClue(s"registry holds OBP versions ${obpVersions.map(_.fullyQualifiedVersion).mkString(", ")} " +
        s"but obpUnionVersion is ${ResourceDocRegistry.obpUnionVersion} ") {
        newest shouldBe ResourceDocRegistry.obpUnionVersion
      }
    }

    // The three named pins below are the three historical drift instances. They are NOT redundant
    // with the loop above: both sides of that loop are now derived from ResourceDocRegistry, so its
    // membership half holds by construction and cannot fail. What the loop still catches is a
    // surface going empty; what these pins still catch is a specific operation id disappearing.

    scenario("the operation id from the sandbox bug report resolves", RegistryParityTag) {
      allOperationIds should contain("BGv2-getAccountDetails")
    }

    // The alias's operation-id prefix is derived from the configured path (0.6/v1 in the test props
    // yields BGv1-...), so the expected id is read back from the alias's own docs rather than
    // hard-coded -- a deployment that configures a different path would otherwise fail here for no
    // real reason.
    scenario("the operation id from the Berlin Group v1.3 alias resolves", RegistryParityTag) {
      if (!aliasIsConfigured) cancel(aliasNotConfigured)
      val aliasOperationId = Http4sBGv13Alias.resourceDocs
        .find(_.partialFunctionName == "getPaymentInitiationStatus").map(_.operationId)
      withClue("the alias is configured but publishes no getPaymentInitiationStatus doc ") {
        aliasOperationId shouldBe defined
      }
      allOperationIds should contain(aliasOperationId.get)
    }

    // The union used to be built from the v6.0.0 aggregation, so v7-only operation ids were
    // absent from it. getMyMetrics exists only in v7.0.0, so it pins the v7 base specifically.
    scenario("a v7-only operation id resolves", RegistryParityTag) {
      allOperationIds should contain("OBPv7.0.0-getMyMetrics")
    }
  }
}
