package code.api.util

import code.api.util.APIUtil.{ApiRelation, OBPEndpoint, ResourceDoc}
import code.util.ClassScanUtils
import net.liftweb.http.LiftRules

import scala.collection.mutable.ArrayBuffer

trait ScannedApis extends LiftRules.DispatchPF {
  val apiVersion: ScannedApiVersion
  lazy val version: ApiVersion = this.apiVersion
  val allResourceDocs: ArrayBuffer[ResourceDoc]
  val routes: List[OBPEndpoint]
  //  val apiRelations: ArrayBuffer[ApiRelation]
}

object ScannedApis {
  lazy val versionMapScannedApis: Map[ScannedApiVersion, ScannedApis] = ClassScanUtils.getImplementClass(classOf[ScannedApis]).map(it=> (it.apiVersion, it)).toMap
}
