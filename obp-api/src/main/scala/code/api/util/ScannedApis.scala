package code.api.util

import code.api.util.APIUtil.ResourceDoc
import code.util.ClassScanUtils
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}

import scala.collection.mutable.ArrayBuffer

/**
  * any object extends this trait will be scanned and register the allResourceDocs and routes.
  * Endpoint dispatch is served natively by http4s; this trait is now only a discovery marker for
  * version + resource-doc aggregation (no longer a Lift `LiftRules.DispatchPF`).
  */
trait ScannedApis {
  val apiVersion: ScannedApiVersion
  lazy val version: ApiVersion = this.apiVersion
  val allResourceDocs: ArrayBuffer[ResourceDoc]
  //  val apiRelations: ArrayBuffer[ApiRelation]
}

object ScannedApis {
  /**
    * this map value are all scanned objects those extends ScannedApiVersion, the key is it apiVersion field
    *
    * Registrants whose version carries no urlPrefix, apiStandard and apiShortVersion are dropped:
    * such a version addresses nothing, and it is how a configuration-gated standard reports itself
    * as switched off (OBP_BERLIN_GROUP_1_3_Alias falls back to ScannedApiVersion("", "", "") when
    * berlin_group_v1_3_alias_path is unset). Keeping it here leaked into everything built from this
    * map: its fullyQualifiedVersion is "" too, so ApiVersionUtils.valueOf("") resolved successfully
    * and GET /obp/v7.0.0/resource-docs//obp answered 200 with an empty document list instead of the
    * 400 InvalidApiVersionString any other unknown version string gets.
    */
  lazy val versionMapScannedApis: Map[ScannedApiVersion, ScannedApis] =
    ClassScanUtils.getSubTypeObjects[ScannedApis]
    .filter(it => isAddressable(it.apiVersion))
    .map(it=> (it.apiVersion, it))
    .toMap

  private def isAddressable(version: ScannedApiVersion): Boolean =
    version.urlPrefix.trim.nonEmpty || version.apiStandard.trim.nonEmpty || version.apiShortVersion.trim.nonEmpty
}
