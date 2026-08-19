package code.api.v2_0_0

import scala.language.reflectiveCalls
import code.api.OBPRestHelper
import code.api.util.VersionedOBPApis
import code.api.v1_4_0.OBPAPI1_4_0
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus}

/*
This file defines which endpoints from all the versions are available in v2.0.0.
All v2.0.0 endpoints have been migrated to Http4s200 — this object is retained
only for resource-doc aggregation and the Lift dispatch registry.
 */
object OBPAPI2_0_0 extends OBPRestHelper with MdcLoggable with VersionedOBPApis {

  lazy val version: ApiVersion = ApiVersion.v2_0_0
  lazy val versionStatus = ApiVersionStatus.DEPRECATED.toString

  val Implementations2_0_0 = Http4s200.Implementations2_0_0

  def allResourceDocs = collectResourceDocs(OBPAPI1_4_0.allResourceDocs, Http4s200.resourceDocs)

  logger.info(s"version $version has been run! ${allResourceDocs.length} allResourceDocs.")
}
