package code.api.util

import code.api.berlin.group.ConstantsBG
import code.api.util.APIUtil.ResourceDoc
import com.openbankproject.commons.util.ApiVersion._
import com.openbankproject.commons.util.{ApiStandards, ApiVersion, ScannedApiVersion}

import scala.collection.immutable.ListMap

/**
 * Single source of truth for "which resource docs does version X serve" — used both by the
 * per-version resource-docs dispatcher (ResourceDocsAPIMethods.getResourceDocsList, i.e.
 * /resource-docs/{VERSION}/... and API Explorer) and by the global operation-id union
 * (allStaticResourceDocs / getAllResourceDocs, used wherever an operation id must be resolved:
 * api-collection-endpoint creation, top-apis/popular-apis lookups, metrics, ...).
 *
 * These used to be two independently hand-maintained registries and drifted three times: Berlin
 * Group v2 was served by the dispatcher but missing from the union (BGv2-getAccountDetails could
 * not be added to an API collection), the union was based on the v6 aggregation excluding v7-only
 * operation ids, and the Berlin Group v1.3 alias was missing from the union too. Deriving both
 * from one `registry` map makes that class of drift structurally impossible: add a version once,
 * both call sites see it.
 *
 * Rule for adding a new API standard: implement `with ScannedApis` (see that trait) and it is
 * picked up automatically via the `scanned` half of `registry` below — no edit needed here. Only
 * standards that cannot be discovered that way (or that need to override the source composing
 * function, e.g. the cumulative per-version OBP-standard aggregations) need an `explicit` entry.
 *
 * Deliberately its own file/object, NOT a member of `APIUtil`: the Implementations* objects for
 * each version re-enter `APIUtil` during their own initialization (prop lookups, etc.), so a
 * strict `val` living inside `APIUtil` risks a class-init deadlock. Everything here stays `lazy`
 * and is first touched at request/test time, well after Props and `ApiVersion.setUrlPrefix` have
 * run in Boot.
 */
object ResourceDocRegistry {

  /** version -> that surface's docs. Thunks, not values: the ScannedApis-discovered arms are
   * lazy vals themselves and the OBP-standard aggregations are cumulative lazy vals too — wrapping
   * in a function defers evaluation to first use of THIS registry, not construction of the map. */
  lazy val registry: ListMap[ApiVersion, () => Seq[ResourceDoc]] = {
    val explicit: ListMap[ApiVersion, () => Seq[ResourceDoc]] = ListMap(
      v7_0_0 -> (() => code.api.v7_0_0.Http4s700.allResourceDocs.toSeq),
      v6_0_0 -> (() => code.api.util.http4s.Http4sResourceDocAggregation.v600.toSeq),
      v5_1_0 -> (() => code.api.util.http4s.Http4sResourceDocAggregation.v510.toSeq),
      v5_0_0 -> (() => code.api.util.http4s.Http4sResourceDocAggregation.v500.toSeq),
      v4_0_0 -> (() => code.api.util.http4s.Http4sResourceDocAggregation.v400.toSeq),
      v3_1_0 -> (() => code.api.util.http4s.Http4sResourceDocAggregation.v310.toSeq),
      v3_0_0 -> (() => code.api.util.http4s.Http4sResourceDocAggregation.v300.toSeq),
      v2_2_0 -> (() => code.api.util.http4s.Http4sResourceDocAggregation.v220.toSeq),
      v2_1_0 -> (() => code.api.util.http4s.Http4sResourceDocAggregation.v210.toSeq),
      v2_0_0 -> (() => code.api.util.http4s.Http4sResourceDocAggregation.v200.toSeq),
      v1_4_0 -> (() => code.api.util.http4s.Http4sResourceDocAggregation.v140.toSeq),
      v1_3_0 -> (() => code.api.util.http4s.Http4sResourceDocAggregation.v130.toSeq),
      v1_2_1 -> (() => code.api.util.http4s.Http4sResourceDocAggregation.v121.toSeq),
      `dynamic-endpoint` -> (() => code.api.dynamic.endpoint.OBPAPIDynamicEndpoint.allResourceDocs.toSeq),
      `dynamic-entity` -> (() => code.api.dynamic.entity.OBPAPIDynamicEntity.allResourceDocs.toSeq)
      // Neither Berlin Group nor UK Open Banking is listed here: they are all ScannedApis
      // registrants, so `scanned` picks them up and -- crucially -- orders them against each other
      // by standardPrecedence below. Naming one of them here would pin it ahead of that ordering.
    )
    // Every standard discovered via ScannedApis (UK OB 200/310/401, BG v1.3 canonical + alias,
    // BG v2, and any future `with ScannedApis` standard), folded into a ListMap so the registry has
    // ONE defined iteration order.
    //
    // Order matters beyond determinism: Http4s600's top-apis/popular-apis and JSONFactory6.0.0's
    // metrics build `partialFunctionName -> operationId` with `.toMap`, where the LAST entry wins.
    // Berlin Group and UK Open Banking share three partialFunctionNames -- getBalances,
    // getAccountList, getAccountBalances -- and the hand-written union this registry replaced
    // listed UK before BG, so Berlin Group won all three. Sorting alphabetically put UK last and
    // silently flipped them to UKv4.0.1-getBalances / UKv2.0-getAccountList /
    // UKv2.0-getAccountBalances in metrics output, so the precedence is now explicit.
    val scanned: ListMap[ApiVersion, () => Seq[ResourceDoc]] =
      ScannedApis.versionMapScannedApis.toSeq
        .collect { case (version: ScannedApiVersion, apis) if !explicit.contains(version) =>
          version -> (() => apis.allResourceDocs.toSeq) }
        .sortBy(entry => sortKey(code.api.berlin.group.v1_3.OBP_BERLIN_GROUP_1_3_Alias.apiVersion)(entry._1))
        .foldLeft(ListMap.empty[ApiVersion, () => Seq[ResourceDoc]])(_ + _)
    explicit ++ scanned
  }

  /**
   * Standards in ASCENDING precedence: a standard later in this list wins a partialFunctionName it
   * shares with an earlier one, because the `.toMap` consumers keep the last entry. This
   * reproduces the order of the hand-written union that preceded this registry (UK Open Banking,
   * then Berlin Group). A standard that is not listed ranks below all of them.
   */
  private val standardPrecedence: List[String] =
    List(ApiVersion.ukOpenBankingV20.apiStandard, ConstantsBG.berlinGroupVersion1.apiStandard)

  /** Below every entry of standardPrecedence, whose lowest index is -1 for an unlisted standard. */
  private val derivedStandardRank: Int = -2

  /**
   * Total order over registry keys: precedence first, then the version's own identity.
   *
   * `derivedAliasVersion` is the version of a standard that merely re-publishes another standard's
   * docs -- today only the Berlin Group v1.3 alias. It is matched by identity, NOT by its
   * apiStandard, because that string is the first segment of `berlin_group_v1_3_alias_path` and a
   * deployment may legitimately choose one that an existing standard already uses: configured as
   * "BG/v9" the alias would otherwise rank alongside Berlin Group and, sorting after "v2", let its
   * re-stamped copies win getBalances, getAccountList and getAccountBalances away from the
   * canonical docs it copied. Ranking it derivedStandardRank keeps that impossible for any
   * configuration.
   *
   * The tie-breaker is (apiStandard, apiShortVersion) rather than fullyQualifiedVersion because
   * that pair is exactly ScannedApiVersion's equals/hashCode key, so two distinct keys of a Map
   * keyed by version always differ in it and sortBy never has to fall back to the unordered input.
   * fullyQualifiedVersion concatenates the two (apiStandard.toUpperCase + apiShortVersion) and can
   * therefore collide across distinct keys -- ("BG", "v1.3") and ("BGV", "1.3") both render
   * "BGV1.3" -- which a deployment could reach through berlin_group_v1_3_alias_path.
   *
   * Curried and package-private so a test can rank against a synthetic alias without having to
   * restart the JVM under a different berlin_group_v1_3_alias_path.
   */
  private[util] def sortKey(derivedAliasVersion: ScannedApiVersion)
                           (version: ScannedApiVersion): (Int, String, String) = {
    val rank =
      if (version == derivedAliasVersion) derivedStandardRank
      else standardPrecedence.indexOf(version.apiStandard)
    (rank, version.apiStandard, version.apiShortVersion)
  }

  /** What the per-version resource-docs dispatcher serves for this version (empty if unknown). */
  def docsFor(version: ApiVersion): Seq[ResourceDoc] = registry.get(version).map(_ ()).getOrElse(Nil)

  /**
   * The OBP-standard surface the global union is built from.
   *
   * The registry also holds the cumulative aggregations for every older OBP version, because the
   * dispatcher must still serve /resource-docs/OBPv4.0.0/obp and friends. Those are NOT folded into
   * the union: they are not subsets of the v7 aggregation (an endpoint dropped after v4 keeps its
   * operation id there), so including them would add ~287 operation ids that the union never
   * carried, 234 of which collide on partialFunctionName with an entry already present -- and the
   * `.toMap` consumers above would then report the OLDEST id (getBanks -> OBPv1.2.1-getBanks)
   * instead of the current one in metrics, top-apis and popular-apis output.
   *
   * Consequence, deliberately accepted: an operation id that exists ONLY in a superseded
   * aggregation stays unresolvable by api-collection-endpoint creation, exactly as before this
   * refactor. ResourceDocRegistryParityTest pins that this constant is the newest OBP-standard
   * version in the registry, so adding v8 without moving it fails the build rather than silently
   * dropping v8-only operation ids from the union.
   */
  val obpUnionVersion: ApiVersion = v7_0_0

  private def isObpStandard(version: ApiVersion): Boolean = version match {
    case sv: ScannedApiVersion => sv.apiStandard == ApiStandards.obp.toString
    case _ => false
  }

  /** Versions whose docs make up the global union: the current OBP surface plus every non-OBP
   * standard. Excluding the other OBP-standard keys also excludes `dynamic-endpoint` /
   * `dynamic-entity` (both carry apiStandard "obp"), which must stay out for a second reason:
   * they are runtime-mutable and APIUtil.getAllResourceDocs appends them FRESH on every call, so
   * caching them in this lazy union would serve stale dynamic docs. */
  lazy val unionVersions: Seq[ApiVersion] =
    registry.keys.filter(v => v == obpUnionVersion || !isObpStandard(v)).toSeq

  /** The global operation-id union. Deduped by operationId: the surfaces legitimately overlap, and
   * consumers only ever `.find`/build a lookup map from this list, never rely on duplicates.
   *
   * LAST wins, not first — the same direction as the `.toMap` consumers `unionVersions`' ordering
   * was built for. `distinctBy` keeps the FIRST occurrence, which ran the ordering backwards and
   * broke the very case the ordering exists for: the Berlin Group v1.3 alias re-stamps the
   * canonical docs with `implementedInApiVersion.copy(apiStandard = "BG")`, so with the natural
   * configuration `berlin_group_v1_3_alias_path=<prefix>/v1.3` its operation ids are BYTE-IDENTICAL
   * to the canonical ones (fullyQualifiedVersion is apiStandard + apiShortVersion, and neither
   * differs). Ranked `derivedStandardRank` the alias sorts FIRST, so first-wins silently replaced
   * all 55 canonical BG v1.3 docs with alias copies whose url prefix is the alias path — leaving
   * the union with zero new operation ids and the canonical ones resolving to the wrong URL.
   *
   * reverse/distinctBy/reverse rather than a Map: it keeps the last occurrence while preserving
   * the relative order of everything that survives, which the `.toMap` consumers do not care
   * about but `ResourceDocRegistryParityTest` and the resource-docs listing do. */
  lazy val allStaticResourceDocs: List[ResourceDoc] =
    unionVersions.flatMap(docsFor).toList.reverse.distinctBy(_.operationId).reverse
}
