package code.api.builder

import code.api.OBPRestHelper
import code.api.util.APIUtil.{OBPEndpoint, getAllowedEndpoints}
import com.openbankproject.commons.util.ApiVersion
import code.util.Helper.MdcLoggable

object OBP_APIBuilder extends OBPRestHelper with APIMethods_APIBuilder with MdcLoggable {

  val version = ApiVersion.apiBuilder
  val versionStatus = "DRAFT"

  val endpoints = ImplementationsBuilderAPI.endpointsOfBuilderAPI
  
  val allResourceDocs = ImplementationsBuilderAPI.resourceDocs

  // Filter the possible endpoints by the disabled / enabled Props settings and add them together
  val routes : List[OBPEndpoint] = getAllowedEndpoints(endpoints, ImplementationsBuilderAPI.resourceDocs)


  // Make them available for use!
  registerRoutes(routes, allResourceDocs, apiPrefix)

  logger.info(s"version $version has been run! There are ${routes.length} routes.")
  
}
