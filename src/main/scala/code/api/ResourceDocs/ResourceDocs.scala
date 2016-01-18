package code.api.ResourceDocs

import code.api.OBPRestHelper


import net.liftweb.common.Loggable


object ResourceDocs extends OBPRestHelper with ResourceDocsAPIMethods with Loggable {

  val VERSION = "1.3.0"

  val routes = List(
    ImplementationsResourceDocs.getResourceDocsObp(VERSION),
    ImplementationsResourceDocs.getResourceDocsSwagger(VERSION)
  )

  routes.foreach(route => {
    oauthServe(apiPrefix{route})
  })

}
