package code.api.UKOpenBanking.v3_1_0

import code.api.OBPRestHelper
import code.api.util.APIUtil.ResourceDoc
import code.api.util.ScannedApis
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus, ScannedApiVersion}

import scala.collection.mutable.ArrayBuffer

/*
 * All UK Open Banking v3.1 endpoints have been migrated to their respective
 * Http4sUKOBv310* objects (all 20 categories, ~67 endpoints).
 *
 * This aggregator is retained for ScannedApis registration (class-path scanning)
 * and so that external callers (APIUtil, SwaggerJSONFactory) that access
 * OBP_UKOpenBanking_310.apiVersion / .allResourceDocs continue to compile.
 * Routes are served by Http4sUKOBv310.wrappedRoutes in Http4sApp (ahead of the
 * Lift bridge).
 */
object OBP_UKOpenBanking_310 extends OBPRestHelper with MdcLoggable with ScannedApis {

  override val apiVersion: ScannedApiVersion = ApiVersion.ukOpenBankingV31
  val versionStatus: String = ApiVersionStatus.DRAFT.toString

  override val allResourceDocs: ArrayBuffer[ResourceDoc] = Http4sUKOBv310.resourceDocs
}
