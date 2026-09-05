package code.api.UKOpenBanking.v2_0_0

import code.api.OBPRestHelper
import code.api.util.APIUtil.ResourceDoc
import code.api.util.ScannedApis
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus, ScannedApiVersion}

import scala.collection.mutable.ArrayBuffer

/*
 * All v2.0 UK Open Banking endpoints have been migrated to Http4sUKOBv200AIS.
 * This stub is retained for ScannedApis registration (class-path scanning) and
 * so that external callers (APIUtil, SwaggerJSONFactory) that access
 * OBP_UKOpenBanking_200.apiVersion / .allResourceDocs continue to compile.
 * Routes are served by Http4sUKOBv200.wrappedRoutes in Http4sApp (ahead of the Lift bridge).
 */
object OBP_UKOpenBanking_200 extends OBPRestHelper with MdcLoggable with ScannedApis {

  override val apiVersion: ScannedApiVersion = ApiVersion.ukOpenBankingV20
  val versionStatus: String = ApiVersionStatus.DRAFT.toString

  override val allResourceDocs: ArrayBuffer[ResourceDoc] = Http4sUKOBv200.resourceDocs
}
