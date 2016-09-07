package code.api.ResourceDocs1_4_0

import code.api.OBPRestHelper


import net.liftweb.common.Loggable


object ResourceDocs extends OBPRestHelper with ResourceDocsAPIMethods with Loggable {

  val VERSION = "1.4.0"


  val routes = List(
    ImplementationsResourceDocs.getResourceDocsObp,
    ImplementationsResourceDocs.getResourceDocsSwagger
  )

  routes.foreach(route => {
    oauthServe(apiPrefix{route})
  })

}
