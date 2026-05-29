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
    */
  lazy val versionMapScannedApis: Map[ScannedApiVersion, ScannedApis] =
    ClassScanUtils.getSubTypeObjects[ScannedApis]
    .map(it=> (it.apiVersion, it))
    .toMap
}
