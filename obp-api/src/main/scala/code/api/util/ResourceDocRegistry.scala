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
      ConstantsBG.berlinGroupVersion1 -> (() => code.api.berlin.group.v1_3.Http4sBGv13.resourceDocs.toSeq),
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
      // Berlin Group v2 is NOT listed here -- Http4sBGv2 is a ScannedApis registrant (its
      // apiVersion is ConstantsBG.berlinGroupVersion2), so it is picked up by `scanned` below.
    )
    // Every standard discovered via ScannedApis (UK OB 200/310/401, BG v1.3 canonical + alias,
    // BG v2, and any future `with ScannedApis` standard). Explicit entries win on key collision --
    // BG v1.3 canonical is both explicit above and a registrant, same underlying buffer either way.
    //
    // Sorted, and folded into a ListMap, so the registry has ONE defined iteration order.
    // ScannedApis.versionMapScannedApis is an unordered Map, and several standards share
    // partialFunctionNames (the BG v1.3 alias re-stamps the canonical BG v1.3 docs, so it collides
    // with both BG v1.3 and -- on getAccountDetails, getAccountList, getCardAccountBalances,
    // getCardAccountTransactionList, getTransactionDetails -- with BG v2). Consumers such as
    // Http4s600's top-apis/popular-apis and JSONFactory6.0.0's metrics build
    // `partialFunctionName -> operationId` with `.toMap`, where the LAST entry wins, so leaving the
    // order to Map's hash iteration would leave the reported operation_id undefined and let it
    // shift silently whenever a standard is added or removed.
    val scanned: ListMap[ApiVersion, () => Seq[ResourceDoc]] =
      ScannedApis.versionMapScannedApis.toSeq
        .collect { case (version: ScannedApiVersion, apis) if !explicit.contains(version) =>
          version -> (() => apis.allResourceDocs.toSeq) }
        .sortBy(_._1.fullyQualifiedVersion)
        .foldLeft(ListMap.empty[ApiVersion, () => Seq[ResourceDoc]])(_ + _)
    explicit ++ scanned
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
   * consumers only ever `.find`/build a lookup map from this list, never rely on duplicates. */
  lazy val allStaticResourceDocs: List[ResourceDoc] =
    unionVersions.flatMap(docsFor).toList.distinctBy(_.operationId)
}
