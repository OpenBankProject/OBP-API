package code.api.MxOF

import code.api.OBPRestHelper
import code.api.util.APIUtil.{OBPEndpoint, ResourceDoc, getAllowedEndpoints}
import code.api.util.ScannedApis
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.{ApiVersion,ApiVersionStatus}

import scala.collection.mutable.ArrayBuffer

/*
This file defines which endpoints from all the versions are available in v1
 */
object CNBV9_1_0_0 extends OBPRestHelper with MdcLoggable with ScannedApis {
  // CNBV9
  override val apiVersion = ApiVersion.cnbv9
  val versionStatus = ApiVersionStatus.DRAFT.toString

  private[this] val endpoints = APIMethods_AtmsApi.endpoints
  override val allResourceDocs: ArrayBuffer[ResourceDoc] = APIMethods_AtmsApi.resourceDocs

  // Filter the possible endpoints by the disabled / enabled Props settings and add them together
  override val routes: List[OBPEndpoint] = getAllowedEndpoints(endpoints, allResourceDocs)

  // Make them available for use!
  registerRoutes(routes, allResourceDocs, apiPrefix)

  logger.info(s"version $version has been run! There are ${routes.length} routes.")
}
