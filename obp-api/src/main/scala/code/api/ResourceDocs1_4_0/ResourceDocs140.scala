package code.api.ResourceDocs1_4_0

import code.api.OBPRestHelper
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus}

// All request dispatch migrated to Http4sResourceDocs (wired into Http4sApp.baseServices).
// These objects are retained solely as accessors for ImplementationsResourceDocs —
// the business-logic entry point delegated to by the centralised http4s service.
// They are NOT registered in LiftRules.statelessDispatch.

object ResourceDocs140 extends OBPRestHelper with ResourceDocsAPIMethods with MdcLoggable {
  val version      = ApiVersion.v1_4_0
  val versionStatus = ApiVersionStatus.STABLE.toString
  // routes intentionally empty — all traffic served by Http4sResourceDocs
}

// Kept so Http4sResourceDocs can reference ResourceDocs300.ResourceDocs600.
object ResourceDocs300 extends OBPRestHelper with ResourceDocsAPIMethods with MdcLoggable {
  val version      : ApiVersion = ApiVersion.v3_0_0
  val versionStatus              = ApiVersionStatus.STABLE.toString
  // routes intentionally empty — all traffic served by Http4sResourceDocs

  // Retained to provide ImplementationsResourceDocs with includeTechnologyInResponse=true.
  // v6.0.0 resource-docs responses include the `technology` field; all other versions
  // leave it as None.  Http4sResourceDocs picks this instance for v6.0.0 URLs.
  object ResourceDocs600 extends OBPRestHelper with ResourceDocsAPIMethods with MdcLoggable {
    val version      : ApiVersion = ApiVersion.v6_0_0
    val versionStatus              = ApiVersionStatus.BLEEDING_EDGE.toString
    override def includeTechnologyInResponse: Boolean = true
    // routes intentionally empty — all traffic served by Http4sResourceDocs
  }
}
