package code.api.ResourceDocs1_4_0

import code.api.OBPRestHelper


import code.util.Helper.MdcLoggable


object ResourceDocs extends OBPRestHelper with ResourceDocsAPIMethods with MdcLoggable {

  val version = "1.4.0" // Does it make sense to have this version match an api version?
  val versionStatus = "UNKNOWN"


  val routes = List(
    ImplementationsResourceDocs.getResourceDocsObp,
    ImplementationsResourceDocs.getResourceDocsSwagger
  )

  routes.foreach(route => {
    oauthServe(apiPrefix{route})
  })

}
