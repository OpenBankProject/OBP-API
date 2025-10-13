package code.api.ResourceDocs1_4_0

import code.api.OBPRestHelper
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus}


object ResourceDocs140 extends OBPRestHelper with ResourceDocsAPIMethods with MdcLoggable {
  val version = ApiVersion.v1_4_0 //    "1.4.0" // We match other api versions so API explorer can easily use the path.
  val versionStatus = ApiVersionStatus.STABLE.toString
  val routes = List(
    ImplementationsResourceDocs.getResourceDocsObp,
    ImplementationsResourceDocs.getBankLevelDynamicResourceDocsObp,
    ImplementationsResourceDocs.getResourceDocsSwagger,
  )
  routes.foreach(route => {
    oauthServe(apiPrefix{route})
  })
}


// Hack to provide Resource Docs / Swagger on endpoints other than 1.4.0 where it is defined.
object ResourceDocs200 extends OBPRestHelper with ResourceDocsAPIMethods with MdcLoggable {
  val version = ApiVersion.v2_0_0 // "2.0.0" // We match other api versions so API explorer can easily use the path.
  val versionStatus = ApiVersionStatus.STABLE.toString
  val routes = List(
    ImplementationsResourceDocs.getResourceDocsObp,
    ImplementationsResourceDocs.getResourceDocsSwagger,
    ImplementationsResourceDocs.getBankLevelDynamicResourceDocsObp,
  )
  routes.foreach(route => {
    oauthServe(apiPrefix{route})
  })
}


// Hack to provide Resource Docs / Swagger on endpoints other than 1.4.0 where it is defined.
object ResourceDocs210 extends OBPRestHelper with ResourceDocsAPIMethods with MdcLoggable {
  val version: ApiVersion = ApiVersion.v2_1_0 //   "2.1.0" // We match other api versions so API explorer can easily use the path.
  val versionStatus = ApiVersionStatus.STABLE.toString
  val routes = List(
    ImplementationsResourceDocs.getResourceDocsObp,
    ImplementationsResourceDocs.getResourceDocsSwagger,
    ImplementationsResourceDocs.getBankLevelDynamicResourceDocsObp,
  )
  routes.foreach(route => {
    oauthServe(apiPrefix{route})
  })
}

// Hack to provide Resource Docs / Swagger on endpoints other than 1.4.0 where it is defined.
object ResourceDocs220 extends OBPRestHelper with ResourceDocsAPIMethods with MdcLoggable {
  val version: ApiVersion = ApiVersion.v2_2_0 // "2.2.0" // We match other api versions so API explorer can easily use the path.
  val versionStatus = ApiVersionStatus.STABLE.toString
  val routes = List(
    ImplementationsResourceDocs.getResourceDocsObp,
    ImplementationsResourceDocs.getResourceDocsSwagger,
    ImplementationsResourceDocs.getBankLevelDynamicResourceDocsObp,
  )
  routes.foreach(route => {
    oauthServe(apiPrefix{route})
  })
}

// Hack to provide Resource Docs / Swagger on endpoints other than 1.4.0 where it is defined.
object ResourceDocs300 extends OBPRestHelper with ResourceDocsAPIMethods with MdcLoggable {
  val version : ApiVersion = ApiVersion.v3_0_0 // = "3.0.0" // We match other api versions so API explorer can easily use the path.
  val versionStatus = ApiVersionStatus.STABLE.toString
  val routes = List(
    ImplementationsResourceDocs.getResourceDocsObp,
    ImplementationsResourceDocs.getResourceDocsSwagger,
    ImplementationsResourceDocs.getBankLevelDynamicResourceDocsObp,
  )
  routes.foreach(route => {
    oauthServe(apiPrefix{route})
  })

  // Hack to provide Resource Docs / Swagger on endpoints other than 1.4.0 where it is defined.
  object ResourceDocs310 extends OBPRestHelper with ResourceDocsAPIMethods with MdcLoggable {
    val version: ApiVersion = ApiVersion.v3_1_0 // = "3.0.0" // We match other api versions so API explorer can easily use the path.
    val versionStatus = ApiVersionStatus.STABLE.toString
    val routes = List(
      ImplementationsResourceDocs.getResourceDocsObp,
      ImplementationsResourceDocs.getResourceDocsSwagger,
      ImplementationsResourceDocs.getBankLevelDynamicResourceDocsObp,
    )
    routes.foreach(route => {
      oauthServe(apiPrefix {
        route
      })
    })
  }
  // Hack to provide Resource Docs / Swagger on endpoints other than 1.4.0 where it is defined.
  object ResourceDocs400 extends OBPRestHelper with ResourceDocsAPIMethods with MdcLoggable {
    val version: ApiVersion = ApiVersion.v4_0_0 // = "4.0.0" // We match other api versions so API explorer can easily use the path.
    val versionStatus = ApiVersionStatus.STABLE.toString
    val routes = List(
      ImplementationsResourceDocs.getResourceDocsObpV400,
      ImplementationsResourceDocs.getResourceDocsSwagger,
      ImplementationsResourceDocs.getBankLevelDynamicResourceDocsObp,
    )
    routes.foreach(route => {
      oauthServe(apiPrefix {
        route
      })
    })
  }
  // Hack to provide Resource Docs / Swagger on endpoints other than 1.4.0 where it is defined.
  object ResourceDocs500 extends OBPRestHelper with ResourceDocsAPIMethods with MdcLoggable {
    val version: ApiVersion = ApiVersion.v5_0_0 
    val versionStatus = ApiVersionStatus.STABLE.toString
    val routes = List(
      ImplementationsResourceDocs.getResourceDocsObpV400,
      ImplementationsResourceDocs.getResourceDocsSwagger,
      ImplementationsResourceDocs.getBankLevelDynamicResourceDocsObp,
    )
    routes.foreach(route => {
      oauthServe(apiPrefix {
        route
      })
    })
  } 
  
  object ResourceDocs510 extends OBPRestHelper with ResourceDocsAPIMethods with MdcLoggable {
    val version: ApiVersion = ApiVersion.v5_1_0 
    val versionStatus = ApiVersionStatus.BLEEDING_EDGE.toString
    val routes = List(
      ImplementationsResourceDocs.getResourceDocsObpV400,
      ImplementationsResourceDocs.getResourceDocsSwagger,
      ImplementationsResourceDocs.getBankLevelDynamicResourceDocsObp,
//      ImplementationsResourceDocs.getStaticResourceDocsObp
    )
    routes.foreach(route => {
      oauthServe(apiPrefix {
        route
      })
    })
  }
  
  object ResourceDocs600 extends OBPRestHelper with ResourceDocsAPIMethods with MdcLoggable {
    val version: ApiVersion = ApiVersion.v6_0_0 
    val versionStatus = ApiVersionStatus.BLEEDING_EDGE.toString
    val routes = List(
      ImplementationsResourceDocs.getResourceDocsObpV400,
      ImplementationsResourceDocs.getResourceDocsSwagger,
      ImplementationsResourceDocs.getBankLevelDynamicResourceDocsObp,
//      ImplementationsResourceDocs.getStaticResourceDocsObp
    )
    routes.foreach(route => {
      oauthServe(apiPrefix {
        route
      })
    })
  }

}