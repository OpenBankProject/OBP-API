package code.api.v2_1_0

import scala.language.reflectiveCalls
import code.api.OBPRestHelper
import code.api.util.VersionedOBPApis
import code.api.v2_0_0.OBPAPI2_0_0
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus}

/*
This file defines which endpoints from all the versions are available in v2.1.0.
All v2.1.0 endpoints have been migrated to Http4s210 — this object is retained
only for resource-doc aggregation and the Lift dispatch registry.
 */
object OBPAPI2_1_0 extends OBPRestHelper with MdcLoggable with VersionedOBPApis {

  val version: ApiVersion = ApiVersion.v2_1_0
  val versionStatus = ApiVersionStatus.STABLE.toString

  val Implementations2_1_0 = Http4s210.Implementations2_1_0

  def allResourceDocs = collectResourceDocs(OBPAPI2_0_0.allResourceDocs, Http4s210.resourceDocs)

  logger.info(s"version $version has been run! ${allResourceDocs.length} allResourceDocs.")
}
