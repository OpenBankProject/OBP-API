package code.api.util

import code.api.berlin.group.ConstantsBG
import code.api.berlin.group.v1_3.Http4sBGv13Alias
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

  // The Berlin Group v1.3 alias is the one surface a deployment can switch off:
  // berlin_group_v1_3_alias_path is unset by default and is supplied for test runs by
  // test.default.props and by the two CI workflows. test.default.props is gitignored
  // (.gitignore:21), so a fresh clone, a colleague's checkout or an IDE ScalaTest run may not have
  // it -- the pin below cancels there instead of failing with a message that gives no hint a prop
  // is missing. The per-surface loop needs no such guard: an unconfigured alias reports the
  // unaddressable ScannedApiVersion("", "", ""), which ScannedApis now drops, so it is not a
  // surface at all rather than an empty one.
  private lazy val aliasIsConfigured: Boolean = Http4sBGv13Alias.resourceDocs.nonEmpty
  private val aliasNotConfigured =
    "berlin_group_v1_3_alias_path is not set, so the Berlin Group v1.3 alias contributes no docs"

  private def label(version: ApiVersion): String = version match {
    case sv: ScannedApiVersion => sv.fullyQualifiedVersion
    case other => other.toString
  }

  // Scoped to ResourceDocRegistry.unionVersions -- the current OBP surface plus every non-OBP
  // standard. The superseded OBP aggregations (v6.0.0 and older) and the two dynamic arms are
  // deliberately out of the union; see ResourceDocRegistry.obpUnionVersion for why, and for the
  // accepted consequence that an operation id living only in a superseded aggregation stays
  // unresolvable.
  private lazy val surfaces: List[(String, Seq[String])] =
    ResourceDocRegistry.unionVersions.toList
      .map(version => (label(version), ResourceDocRegistry.docsFor(version).map(_.operationId)))

  feature("getAllResourceDocs contains every per-standard resource-doc surface the union covers") {
    scenario("the registry itself is non-empty", RegistryParityTag) {
      surfaces should not be empty
    }

    surfaces.foreach { case (label, operationIds) =>
      scenario(s"$label operation ids are all resolvable globally", RegistryParityTag) {
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

    // Berlin Group and UK Open Banking both publish getBalances, getAccountList and
    // getAccountBalances. Http4s600's top-apis/popular-apis and JSONFactory6.0.0's metrics resolve
    // a partialFunctionName with `.toMap`, which keeps the LAST matching entry, so the registry's
    // iteration order decides the operation_id those endpoints report. The hand-written union that
    // preceded this registry listed UK before BG, giving Berlin Group the three names; sorting the
    // scanned standards alphabetically silently handed them to UK Open Banking instead. This pins
    // the resolved values so the precedence cannot drift again unnoticed.
    scenario("Berlin Group keeps the partialFunctionNames it shares with UK Open Banking", RegistryParityTag) {
      val resolved = APIUtil.getAllResourceDocs
        .map(doc => doc.partialFunctionName -> doc.operationId).toMap
      resolved.get("getBalances") shouldBe Some("BGv1.3-getBalances")
      resolved.get("getAccountList") shouldBe Some("BGv2-getAccountList")
      resolved.get("getAccountBalances") shouldBe Some("BGv2-getAccountBalances")
    }

    // The Berlin Group v1.3 alias only re-publishes the canonical BG v1.3 docs, so it must never
    // win a partialFunctionName away from the standard it copied. Its apiStandard is the first
    // segment of berlin_group_v1_3_alias_path, so a deployment can point it at a name an existing
    // standard already uses ("BG/v9"); ranking by that string alone put the alias alongside Berlin
    // Group and, sorting after "v2", ahead of it. Ranking is by identity instead, and the synthetic
    // alias below exercises the colliding configuration without needing a JVM under that prop.
    scenario("a derived alias never outranks the standard it re-publishes", RegistryParityTag) {
      val syntheticAlias = ScannedApiVersion("BG", "BG", "v9")
      val rankOf = ResourceDocRegistry.sortKey(syntheticAlias) _
      withClue("the alias must sort before Berlin Group, i.e. lose the `.toMap` last-wins race ") {
        rankOf(syntheticAlias) should be < rankOf(ConstantsBG.berlinGroupVersion2)
        rankOf(syntheticAlias) should be < rankOf(ConstantsBG.berlinGroupVersion1)
      }
      withClue("the alias must also sort before UK Open Banking ") {
        rankOf(syntheticAlias) should be < rankOf(ApiVersion.ukOpenBankingV401)
      }
      withClue("UK must still sort before Berlin Group, so BG keeps the names they share ") {
        rankOf(ApiVersion.ukOpenBankingV401) should be < rankOf(ConstantsBG.berlinGroupVersion2)
      }
    }

    // An unconfigured configuration-gated standard reports ScannedApiVersion("", "", ""), whose
    // fullyQualifiedVersion is "" as well. While ScannedApis kept it, ApiVersionUtils.valueOf("")
    // resolved successfully and GET /obp/v7.0.0/resource-docs//obp answered 200 with an empty
    // document list instead of the 400 every other unknown version string gets.
    scenario("an unaddressable empty version is not a registered API version", RegistryParityTag) {
      ScannedApis.versionMapScannedApis.keys.foreach { version =>
        withClue(s"$version was registered despite addressing nothing ") {
          (version.urlPrefix.trim + version.apiStandard.trim + version.apiShortVersion.trim) should not be empty
        }
      }
      ApiVersionUtils.versions.map(_.fullyQualifiedVersion) should not contain ""
      an[IllegalArgumentException] should be thrownBy ApiVersionUtils.valueOf("")
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
