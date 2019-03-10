package code.api.builder

import code.api.OBPRestHelper
import code.api.util.APIUtil.{OBPEndpoint, ResourceDoc, getAllowedEndpoints}
import code.api.util.ApiVersion
import code.util.Helper.MdcLoggable

object OBP_APIBuilder extends OBPRestHelper with APIMethods_APIBuilder with MdcLoggable {

  val version = ApiVersion.apiBuilder
  val versionStatus = "DRAFT"

  val endpoints = ImplementationsBuilderAPI.endpointsOfBuilderAPI
  
  val allResourceDocs = ImplementationsBuilderAPI.resourceDocs
  
  def findResourceDoc(pf: OBPEndpoint): Option[ResourceDoc] = {
    allResourceDocs.find(_.partialFunction==pf)
  }

  // Filter the possible endpoints by the disabled / enabled Props settings and add them together
  val routes : List[OBPEndpoint] = getAllowedEndpoints(endpoints, ImplementationsBuilderAPI.resourceDocs)


  // Make them available for use!
  routes.foreach(route => {
    oauthServe(("api-builder" / version.vDottedApiVersion()).oPrefix{route}, findResourceDoc(route))
  })

  logger.info(s"version $version has been run! There are ${routes.length} routes.")
  
}
