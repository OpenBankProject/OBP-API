package code.api.v7_0_0

import code.api.Constant
import code.api.util.APIUtil
import code.api.util.ErrorMessages
import code.api.util.ErrorMessages.MandatoryPropertyIsNotSet
import code.api.v4_0_0.{EnergySource400, HostedAt400, HostedBy400}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.ApiVersion

object JSONFactory700 extends MdcLoggable {

  case class ErrorMessageEntryJsonV700(code: String, name: String, message: String)

  // Cached for server lifetime: ErrorMessages is a static catalog of `val X = "OBP-NNNNN: ..."`
  // strings, so reflecting over it once at first access is sufficient. Filters:
  //  - only String-typed fields (skips synthetic lazy-val bitmaps and helper defs)
  //  - only values starting with "OBP-" (skips helper strings that don't carry a code)
  lazy val errorMessagesCatalog: List[ErrorMessageEntryJsonV700] = {
    ErrorMessages.getClass.getDeclaredFields.toList
      .filter(f => f.getType == classOf[String])
      .flatMap { f =>
        f.setAccessible(true)
        Option(f.get(ErrorMessages)).collect { case s: String => s }
          .filter(_.startsWith("OBP-"))
          .map { msg =>
            val colonIdx = msg.indexOf(':')
            val (code, text) =
              if (colonIdx > 0) (msg.substring(0, colonIdx), msg.substring(colonIdx + 1).trim)
              else ("", msg)
            ErrorMessageEntryJsonV700(code = code, name = f.getName, message = text)
          }
      }
      .sortBy(e => (e.code, e.name))
  }


  case class APIInfoJsonV700(
    version: String,
    version_status: String,
    git_commit: String,
    stage: String,
    connector: String,
    hostname: String,
    local_identity_provider: String,
    hosted_by: HostedBy400,
    hosted_at: HostedAt400,
    energy_source: EnergySource400,
    resource_docs_requires_role: Boolean
  )

  def getApiInfoJSON(apiVersion: ApiVersion, apiVersionStatus: String): APIInfoJsonV700 = {
    val organisation = APIUtil.hostedByOrganisation
    val email = APIUtil.hostedByEmail
    val phone = APIUtil.hostedByPhone
    val organisationWebsite = APIUtil.organisationWebsite
    val hostedBy = new HostedBy400(organisation, email, phone, organisationWebsite)

    val organisationHostedAt = APIUtil.hostedAtOrganisation
    val organisationWebsiteHostedAt = APIUtil.hostedAtOrganisationWebsite
    val hostedAt = HostedAt400(organisationHostedAt, organisationWebsiteHostedAt)

    val organisationEnergySource = APIUtil.energySourceOrganisation
    val organisationWebsiteEnergySource = APIUtil.energySourceOrganisationWebsite
    val energySource = EnergySource400(organisationEnergySource, organisationWebsiteEnergySource)

    val connector = code.api.Constant.CONNECTOR.openOrThrowException(s"$MandatoryPropertyIsNotSet. The missing prop is `connector` ")
    val resourceDocsRequiresRole = APIUtil.resourceDocsRequiresRole

    APIInfoJsonV700(
      version = apiVersion.vDottedApiVersion,
      version_status = apiVersionStatus,
      git_commit = APIUtil.gitCommit,
      connector = connector,
      hostname = Constant.HostName,
      stage = System.getProperty("run.mode"),
      local_identity_provider = Constant.localIdentityProvider,
      hosted_by = hostedBy,
      hosted_at = hostedAt,
      energy_source = energySource,
      resource_docs_requires_role = resourceDocsRequiresRole
    )
  }
}

