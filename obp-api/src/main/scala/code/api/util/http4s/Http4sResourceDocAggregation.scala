package code.api.util.http4s

import code.api.util.APIUtil.ResourceDoc
import code.api.v1_2_1.Http4s121
import code.api.v1_3_0.Http4s130
import code.api.v1_4_0.Http4s140
import code.api.v2_0_0.Http4s200
import code.api.v2_1_0.Http4s210
import code.api.v2_2_0.Http4s220
import code.api.v3_0_0.Http4s300
import code.api.v3_1_0.Http4s310
import code.api.v4_0_0.Http4s400
import code.api.v5_0_0.Http4s500
import code.api.v5_1_0.Http4s510
import code.api.v6_0_0.Http4s600
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ScannedApiVersion

import scala.collection.mutable.ArrayBuffer

/**
 * Lift-free per-version resource-doc aggregation.
 *
 * Replaces the chain that used to live on the Lift `OBPAPIx_x_x` aggregator objects
 * (`OBPAPINxx.allResourceDocs = collectResourceDocs(OBPAPI{prev}.allResourceDocs, Http4sNxx.resourceDocs)`).
 * The aggregation logic is identical — same inputs (`Http4sNxx.resourceDocs`), same
 * (requestUrl, requestVerb) dedup keeping the newest version, same per-version `excludeEndpoints`
 * filter — so doc counts are unchanged. The point is that the computation no longer runs on an
 * `OBPRestHelper`/Lift object, so `ResourceDocsAPIMethods` and `Http4s700` can source the catalog
 * without touching the Lift dispatch aggregators (now deleted).
 *
 * The `excludeEndpoints` lists below moved here from those objects verbatim. They are pure
 * `List[String]` of partial-function names (`nameOf` is a compile-time macro yielding the member
 * name, so pointing it at `Http4sNNN.ImplementationsX` instead of the old re-export produces the
 * same strings).
 */
object Http4sResourceDocAggregation {

  // Descending sort by ApiVersion — identical to OBPRestHelper.collectResourceDocs.
  private implicit val ordering: Ordering[ScannedApiVersion] = new Ordering[ScannedApiVersion] {
    override def compare(x: ScannedApiVersion, y: ScannedApiVersion): Int = y.toString().compareTo(x.toString())
  }

  /** Lift-free copy of OBPRestHelper.collectResourceDocs: dedup by (requestUrl, requestVerb), keep newest. */
  def dedup(docs: ArrayBuffer[ResourceDoc]*): ArrayBuffer[ResourceDoc] = {
    val sorted: Seq[ResourceDoc] = docs.flatten.sortBy(_.implementedInApiVersion)
    val result = ArrayBuffer[ResourceDoc]()
    val urlAndMethods = scala.collection.mutable.Set[(String, String)]()
    for (doc <- sorted) {
      val urlAndMethod = (doc.requestUrl, doc.requestVerb)
      if (!urlAndMethods.contains(urlAndMethod)) {
        urlAndMethods.add(urlAndMethod)
        result += doc
      }
    }
    result
  }

  /** Moved from OBPAPI4_0_0.excludeEndpoints. */
  lazy val excludeEndpointsV400: List[String] =
    nameOf(Http4s121.Implementations1_2_1.addPermissionForUserForBankAccountForMultipleViews) ::
      nameOf(Http4s121.Implementations1_2_1.removePermissionForUserForBankAccountForAllViews) ::
      nameOf(Http4s121.Implementations1_2_1.addPermissionForUserForBankAccountForOneView) ::
      nameOf(Http4s121.Implementations1_2_1.removePermissionForUserForBankAccountForOneView) ::
      nameOf(Http4s310.Implementations3_1_0.createAccount) ::
      nameOf(Http4s310.Implementations3_1_0.revokeConsent) ::
      Nil

  /** Moved from OBPAPI5_1_0.excludeEndpoints. */
  lazy val excludeEndpointsV510: List[String] =
    nameOf(Http4s300.Implementations3_0_0.getUserByUsername) ::  // following 4 endpoints miss Provider parameter in the URL, we introduce new ones in V510.
      nameOf(Http4s310.Implementations3_1_0.getBadLoginStatus) ::
      nameOf(Http4s310.Implementations3_1_0.unlockUser) ::
      nameOf(Http4s400.Implementations4_0_0.lockUser) ::
      nameOf(Http4s400.Implementations4_0_0.createUserWithAccountAccess) ::  // following 3 endpoints miss ViewId parameter in the URL, we introduce new ones in V510.
      nameOf(Http4s400.Implementations4_0_0.grantUserAccessToView) ::
      nameOf(Http4s400.Implementations4_0_0.revokeUserAccessToView) ::
      nameOf(Http4s400.Implementations4_0_0.revokeGrantUserAccessToViews) ::// this endpoint is forbidden in V510, we do not support multi views in one endpoint from V510.
      Nil

  /** Moved from OBPAPI6_0_0.excludeEndpoints. */
  lazy val excludeEndpointsV600: List[String] =
    nameOf(Http4s300.Implementations3_0_0.getUserByUsername) ::
      nameOf(Http4s310.Implementations3_1_0.getBadLoginStatus) ::
      nameOf(Http4s310.Implementations3_1_0.unlockUser) ::
      nameOf(Http4s400.Implementations4_0_0.lockUser) ::
      nameOf(Http4s400.Implementations4_0_0.createUserWithAccountAccess) ::
      nameOf(Http4s400.Implementations4_0_0.grantUserAccessToView) ::
      nameOf(Http4s400.Implementations4_0_0.revokeUserAccessToView) ::
      nameOf(Http4s400.Implementations4_0_0.revokeGrantUserAccessToViews) ::
      nameOf(Http4s400.Implementations4_0_0.getMyPersonalUserAttributes) ::
      nameOf(Http4s400.Implementations4_0_0.createMyPersonalUserAttribute) ::
      nameOf(Http4s400.Implementations4_0_0.updateMyPersonalUserAttribute) ::
      nameOf(Http4s510.Implementations5_1_0.createNonPersonalUserAttribute) ::
      nameOf(Http4s510.Implementations5_1_0.getNonPersonalUserAttributes) ::
      nameOf(Http4s510.Implementations5_1_0.deleteNonPersonalUserAttribute) ::
      Nil

  private def filterExcluded(docs: ArrayBuffer[ResourceDoc], excludeEndpoints: List[String]): ArrayBuffer[ResourceDoc] =
    if (excludeEndpoints.isEmpty) docs
    else docs.filterNot(it => it.partialFunctionName.matches(excludeEndpoints.mkString("|")))

  // Each Http4sNxx.resourceDocs buffer is populated inside the nested ImplementationsNxx object body
  // (resourceDocs += calls are non-lazy statements there). The wrappedRoutesVxxxServices in each Http4sNxx
  // uses a Kleisli wrapper that defers ImplementationsNxx initialization to the first HTTP request, so
  // resourceDocs is empty when Http4sResourceDocAggregation is first evaluated via a resource-docs request.
  // Accessing the ImplementationsNxx object directly forces its initialization, populating the buffer.
  // Http4s121 is the exception: its wrappedRoutesV121Services is a direct lazy val reference
  // (not Kleisli-wrapped), so Implementations1_2_1 is always initialized by Http4sApp startup.
  private def forceInit(impl: Any): Unit = ()

  // The cumulative per-version catalogs (each = all docs from v1.2.1 up to and including this version).
  lazy val v121: ArrayBuffer[ResourceDoc] = Http4s121.resourceDocs
  lazy val v130: ArrayBuffer[ResourceDoc] = {
    forceInit(Http4s130.Implementations1_3_0)
    dedup(v121, Http4s130.resourceDocs)
  }
  lazy val v140: ArrayBuffer[ResourceDoc] = {
    forceInit(Http4s140.Implementations1_4_0)
    dedup(v130, Http4s140.resourceDocs)
  }
  lazy val v200: ArrayBuffer[ResourceDoc] = {
    forceInit(Http4s200.Implementations2_0_0)
    dedup(v140, Http4s200.resourceDocs)
  }
  lazy val v210: ArrayBuffer[ResourceDoc] = {
    forceInit(Http4s210.Implementations2_1_0)
    dedup(v200, Http4s210.resourceDocs)
  }
  lazy val v220: ArrayBuffer[ResourceDoc] = {
    forceInit(Http4s220.Implementations2_2_0)
    dedup(v210, Http4s220.resourceDocs)
  }
  lazy val v300: ArrayBuffer[ResourceDoc] = {
    forceInit(Http4s300.Implementations3_0_0)
    dedup(v220, Http4s300.resourceDocs)
  }
  lazy val v310: ArrayBuffer[ResourceDoc] = {
    forceInit(Http4s310.Implementations3_1_0)
    dedup(v300, Http4s310.resourceDocs)
  }
  lazy val v400: ArrayBuffer[ResourceDoc] = {
    forceInit(Http4s400.Implementations4_0_0)
    filterExcluded(dedup(v310, Http4s400.resourceDocs), excludeEndpointsV400)
  }
  lazy val v500: ArrayBuffer[ResourceDoc] = {
    forceInit(Http4s500.Implementations5_0_0)
    dedup(v400, Http4s500.resourceDocs)
  }
  lazy val v510: ArrayBuffer[ResourceDoc] = {
    forceInit(Http4s510.Implementations5_1_0)
    filterExcluded(dedup(v500, Http4s510.resourceDocs), excludeEndpointsV510)
  }
  lazy val v600: ArrayBuffer[ResourceDoc] = {
    forceInit(Http4s600.Implementations6_0_0)
    filterExcluded(dedup(v510, Http4s600.resourceDocs), excludeEndpointsV600)
  }
}
