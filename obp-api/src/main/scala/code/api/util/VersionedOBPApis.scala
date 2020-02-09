package code.api.util

import code.api.util.APIUtil.{OBPEndpoint, ResourceDoc}
import com.openbankproject.commons.util.ApiVersion

import scala.collection.mutable.ArrayBuffer

trait VersionedOBPApis {
  def version : ApiVersion

  def versionStatus: String

  def allResourceDocs: ArrayBuffer[ResourceDoc]

  def routes: List[OBPEndpoint]
}
