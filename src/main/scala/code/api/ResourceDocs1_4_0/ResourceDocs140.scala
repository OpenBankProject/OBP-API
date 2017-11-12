package code.api.ResourceDocs1_4_0

import code.api.OBPRestHelper


import code.util.Helper.MdcLoggable


object ResourceDocs140 extends OBPRestHelper with ResourceDocsAPIMethods with MdcLoggable {
  val version = "1.4.0" // We match other api versions so API explorer can easily use the path.
  val versionStatus = "STABLE"
  val routes = List(
    ImplementationsResourceDocs.getResourceDocsObp,
    ImplementationsResourceDocs.getResourceDocsSwagger
  )
  routes.foreach(route => {
    oauthServe(apiPrefix{route})
  })
}


// Hack to provide Resource Docs / Swagger on endpoints other than 1.4.0 where it is defined.
object ResourceDocs200 extends OBPRestHelper with ResourceDocsAPIMethods with MdcLoggable {
  val version = "2.0.0" // We match other api versions so API explorer can easily use the path.
  val versionStatus = "DRAFT"
  val routes = List(
    ImplementationsResourceDocs.getResourceDocsObp,
    ImplementationsResourceDocs.getResourceDocsSwagger
  )
  routes.foreach(route => {
    oauthServe(apiPrefix{route})
  })
}


// Hack to provide Resource Docs / Swagger on endpoints other than 1.4.0 where it is defined.
object ResourceDocs210 extends OBPRestHelper with ResourceDocsAPIMethods with MdcLoggable {
  val version = "2.1.0" // We match other api versions so API explorer can easily use the path.
  val versionStatus = "DRAFT"
  val routes = List(
    ImplementationsResourceDocs.getResourceDocsObp,
    ImplementationsResourceDocs.getResourceDocsSwagger
  )
  routes.foreach(route => {
    oauthServe(apiPrefix{route})
  })
}

// Hack to provide Resource Docs / Swagger on endpoints other than 1.4.0 where it is defined.
object ResourceDocs220 extends OBPRestHelper with ResourceDocsAPIMethods with MdcLoggable {
  val version = "2.2.0" // We match other api versions so API explorer can easily use the path.
  val versionStatus = "DRAFT"
  val routes = List(
    ImplementationsResourceDocs.getResourceDocsObp,
    ImplementationsResourceDocs.getResourceDocsSwagger
  )
  routes.foreach(route => {
    oauthServe(apiPrefix{route})
  })
}

// Hack to provide Resource Docs / Swagger on endpoints other than 1.4.0 where it is defined.
object ResourceDocs300 extends OBPRestHelper with ResourceDocsAPIMethods with MdcLoggable {
  val version = "3.0.0" // We match other api versions so API explorer can easily use the path.
  val versionStatus = "DRAFT"
  val routes = List(
    ImplementationsResourceDocs.getResourceDocsObp,
    ImplementationsResourceDocs.getResourceDocsSwagger
  )
  routes.foreach(route => {
    oauthServe(apiPrefix{route})
  })
}