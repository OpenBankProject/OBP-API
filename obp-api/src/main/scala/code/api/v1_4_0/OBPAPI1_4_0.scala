package code.api.v1_4_0

import scala.language.reflectiveCalls
import code.api.OBPRestHelper
import code.api.util.VersionedOBPApis
import code.api.v1_3_0.OBPAPI1_3_0
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus}

/*
This file defines which endpoints from all the versions are available in v1.4.0.
All v1.4.0 endpoints have been migrated to Http4s140 — this object is retained
only for resource-doc aggregation and the Lift dispatch registry.
 */
object OBPAPI1_4_0 extends OBPRestHelper with MdcLoggable with VersionedOBPApis {

  val version: ApiVersion = ApiVersion.v1_4_0
  val versionStatus = ApiVersionStatus.DEPRECATED.toString

  val Implementations1_4_0 = Http4s140.Implementations1_4_0

  def allResourceDocs = collectResourceDocs(OBPAPI1_3_0.allResourceDocs, Http4s140.resourceDocs)

  logger.info(s"version $version has been run! ${allResourceDocs.length} allResourceDocs.")
}
