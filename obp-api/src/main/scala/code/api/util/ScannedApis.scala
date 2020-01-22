package code.api.util

import code.api.util.APIUtil.{ApiRelation, OBPEndpoint, ResourceDoc}
import code.util.ClassScanUtils
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}
import net.liftweb.http.LiftRules

import scala.collection.mutable.ArrayBuffer

/**
  * any object extends this trait will be scanned and register the allResourceDocs and routes
  */
trait ScannedApis extends LiftRules.DispatchPF {
  val apiVersion: ScannedApiVersion
  lazy val version: ApiVersion = this.apiVersion
  val allResourceDocs: ArrayBuffer[ResourceDoc]
  val routes: List[OBPEndpoint]
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
