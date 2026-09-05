package code.api.UKOpenBanking.v4_0_1

import code.api.OBPRestHelper
import code.api.util.APIUtil.ResourceDoc
import code.api.util.ScannedApis
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus, ScannedApiVersion}

import scala.collection.mutable.ArrayBuffer

/**
 * UK Open Banking Read/Write v4.0.1 — ScannedApis discovery marker.
 *
 * Mirrors OBP_UKOpenBanking_310: the routes are served natively by http4s
 * (Http4sUKOBv401.wrappedRoutes, wired in Http4sApp.baseServices); this object
 * only exposes apiVersion + allResourceDocs so classpath scanning auto-registers
 * the version and its resource docs / swagger. It carries no Lift routes.
 */
object OBP_UKOpenBanking_401 extends OBPRestHelper with MdcLoggable with ScannedApis {
  override val apiVersion: ScannedApiVersion = ApiVersion.ukOpenBankingV401
  lazy val versionStatus: String = ApiVersionStatus.DRAFT.toString
  override val allResourceDocs: ArrayBuffer[ResourceDoc] = Http4sUKOBv401.resourceDocs
}
