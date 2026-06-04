package code.api.v1_2_1

import code.api.OBPRestHelper
import code.api.util.VersionedOBPApis
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus}

/*
This file defines which endpoints from all the versions are available in v1.2.1.
All v1.2.1 endpoints have been migrated to Http4s121 — this object is retained
only for resource-doc aggregation and the Lift dispatch registry.
 */
object OBPAPI1_2_1 extends OBPRestHelper with MdcLoggable with VersionedOBPApis {

  val version: ApiVersion = ApiVersion.v1_2_1
  val versionStatus = ApiVersionStatus.DEPRECATED.toString

  val Implementations1_2_1 = Http4s121.Implementations1_2_1

  def allResourceDocs = Http4s121.resourceDocs

  logger.info(s"version $version has been run! ${allResourceDocs.length} allResourceDocs.")
}
