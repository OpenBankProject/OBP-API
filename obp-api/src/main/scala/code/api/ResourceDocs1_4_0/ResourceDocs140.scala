package code.api.ResourceDocs1_4_0

import code.api.OBPRestHelper
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus}

// All request dispatch migrated to Http4sResourceDocs (wired into Http4sApp.baseServices).
// These objects are retained solely as accessors for ImplementationsResourceDocs —
// the business-logic entry point delegated to by the centralised http4s service.
// They are NOT registered in LiftRules.statelessDispatch.

object ResourceDocs140 extends OBPRestHelper with ResourceDocsAPIMethods with MdcLoggable {
  lazy val version: com.openbankproject.commons.util.ScannedApiVersion      = ApiVersion.v1_4_0
  lazy val versionStatus = ApiVersionStatus.STABLE.toString
  // routes intentionally empty — all traffic served by Http4sResourceDocs
}

// Kept so Http4sResourceDocs can reference ResourceDocs300.ResourceDocs600.
object ResourceDocs300 extends OBPRestHelper with ResourceDocsAPIMethods with MdcLoggable {
  lazy val version      : ApiVersion = ApiVersion.v3_0_0
  lazy val versionStatus              = ApiVersionStatus.STABLE.toString
  // routes intentionally empty — all traffic served by Http4sResourceDocs

  // Retained to provide ImplementationsResourceDocs with includeTechnologyInResponse=true.
  // v6.0.0 resource-docs responses include the `technology` field; all other versions
  // leave it as None.  Http4sResourceDocs picks this instance for v6.0.0 URLs.
  object ResourceDocs600 extends OBPRestHelper with ResourceDocsAPIMethods with MdcLoggable {
    lazy val version      : ApiVersion = ApiVersion.v6_0_0
    lazy val versionStatus              = ApiVersionStatus.BLEEDING_EDGE.toString
    override def includeTechnologyInResponse: Boolean = true
    // routes intentionally empty — all traffic served by Http4sResourceDocs
  }
}
//
//
//package code.api.ResourceDocs1_4_0
//
//import scala.language.reflectiveCalls
//import code.api.Constant.HostName
//import code.api.{OBPRestHelper, ResponseHeader}
//import code.api.cache.Caching
//import code.api.util.APIUtil._
//import code.api.util.{APIUtil, ApiVersionUtils, YAMLUtils}
//import code.api.v1_4_0.JSONFactory1_4_0
//import code.apicollectionendpoint.MappedApiCollectionEndpointsProvider
//import code.util.Helper.{MdcLoggable, SILENCE_IS_GOLDEN}
//import com.openbankproject.commons.model.enums.ContentParam.{DYNAMIC, STATIC}
//import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus}
//
//
//object ResourceDocs140 extends OBPRestHelper with ResourceDocsAPIMethods with MdcLoggable {
//  val version = ApiVersion.v1_4_0 //    "1.4.0" // We match other api versions so API explorer can easily use the path.
//  val versionStatus = ApiVersionStatus.STABLE.toString
//  val routes: List[OBPEndpoint] = List(
//    ImplementationsResourceDocs.getResourceDocsObp,
//    ImplementationsResourceDocs.getBankLevelDynamicResourceDocsObp,
//    ImplementationsResourceDocs.getResourceDocsSwagger,
//  )
//  registerRoutes(routes, ImplementationsResourceDocs.localResourceDocs, apiPrefix)
//}
//
//
//// Hack to provide Resource Docs / Swagger on endpoints other than 1.4.0 where it is defined.
//object ResourceDocs200 extends OBPRestHelper with ResourceDocsAPIMethods with MdcLoggable {
//  val version = ApiVersion.v2_0_0 // "2.0.0" // We match other api versions so API explorer can easily use the path.
//  val versionStatus = ApiVersionStatus.STABLE.toString
//  val routes: List[OBPEndpoint] = List(
//    ImplementationsResourceDocs.getResourceDocsObp,
//    ImplementationsResourceDocs.getResourceDocsSwagger,
//    ImplementationsResourceDocs.getBankLevelDynamicResourceDocsObp,
//  )
//  registerRoutes(routes, ImplementationsResourceDocs.localResourceDocs, apiPrefix)
//}
//
//
//// Hack to provide Resource Docs / Swagger on endpoints other than 1.4.0 where it is defined.
//object ResourceDocs210 extends OBPRestHelper with ResourceDocsAPIMethods with MdcLoggable {
//  val version: ApiVersion = ApiVersion.v2_1_0 //   "2.1.0" // We match other api versions so API explorer can easily use the path.
//  val versionStatus = ApiVersionStatus.STABLE.toString
//  val routes: List[OBPEndpoint] = List(
//    ImplementationsResourceDocs.getResourceDocsObp,
//    ImplementationsResourceDocs.getResourceDocsSwagger,
//    ImplementationsResourceDocs.getBankLevelDynamicResourceDocsObp,
//  )
//  registerRoutes(routes, ImplementationsResourceDocs.localResourceDocs, apiPrefix)
//}
//
//// Hack to provide Resource Docs / Swagger on endpoints other than 1.4.0 where it is defined.
//object ResourceDocs220 extends OBPRestHelper with ResourceDocsAPIMethods with MdcLoggable {
//  val version: ApiVersion = ApiVersion.v2_2_0 // "2.2.0" // We match other api versions so API explorer can easily use the path.
//  val versionStatus = ApiVersionStatus.STABLE.toString
//  val routes: List[OBPEndpoint] = List(
//    ImplementationsResourceDocs.getResourceDocsObp,
//    ImplementationsResourceDocs.getResourceDocsSwagger,
//    ImplementationsResourceDocs.getBankLevelDynamicResourceDocsObp,
//  )
//  registerRoutes(routes, ImplementationsResourceDocs.localResourceDocs, apiPrefix)
//}
//
//// Hack to provide Resource Docs / Swagger on endpoints other than 1.4.0 where it is defined.
//object ResourceDocs300 extends OBPRestHelper with ResourceDocsAPIMethods with MdcLoggable {
//  val version : ApiVersion = ApiVersion.v3_0_0 // = "3.0.0" // We match other api versions so API explorer can easily use the path.
//  val versionStatus = ApiVersionStatus.STABLE.toString
//  val routes: List[OBPEndpoint] = List(
//    ImplementationsResourceDocs.getResourceDocsObp,
//    ImplementationsResourceDocs.getResourceDocsSwagger,
//    ImplementationsResourceDocs.getBankLevelDynamicResourceDocsObp,
//  )
//  registerRoutes(routes, ImplementationsResourceDocs.localResourceDocs, apiPrefix)
//
//  // Hack to provide Resource Docs / Swagger on endpoints other than 1.4.0 where it is defined.
//  object ResourceDocs310 extends OBPRestHelper with ResourceDocsAPIMethods with MdcLoggable {
//    val version: ApiVersion = ApiVersion.v3_1_0 // = "3.0.0" // We match other api versions so API explorer can easily use the path.
//    val versionStatus = ApiVersionStatus.STABLE.toString
//    val routes: List[OBPEndpoint] = List(
//      ImplementationsResourceDocs.getResourceDocsObp,
//      ImplementationsResourceDocs.getResourceDocsSwagger,
//      ImplementationsResourceDocs.getBankLevelDynamicResourceDocsObp,
//    )
//    registerRoutes(routes, ImplementationsResourceDocs.localResourceDocs, apiPrefix)
//  }
//  // Hack to provide Resource Docs / Swagger on endpoints other than 1.4.0 where it is defined.
//  object ResourceDocs400 extends OBPRestHelper with ResourceDocsAPIMethods with MdcLoggable {
//    val version: ApiVersion = ApiVersion.v4_0_0 // = "4.0.0" // We match other api versions so API explorer can easily use the path.
//    val versionStatus = ApiVersionStatus.STABLE.toString
//    val routes: List[OBPEndpoint] = List(
//      ImplementationsResourceDocs.getResourceDocsObpV400,
//      ImplementationsResourceDocs.getResourceDocsSwagger,
//      ImplementationsResourceDocs.getBankLevelDynamicResourceDocsObp,
//    )
//    registerRoutes(routes, ImplementationsResourceDocs.localResourceDocs, apiPrefix)
//  }
//  // Hack to provide Resource Docs / Swagger on endpoints other than 1.4.0 where it is defined.
//  object ResourceDocs500 extends OBPRestHelper with ResourceDocsAPIMethods with MdcLoggable {
//    val version: ApiVersion = ApiVersion.v5_0_0
//    val versionStatus = ApiVersionStatus.STABLE.toString
//    val routes: List[OBPEndpoint] = List(
//      ImplementationsResourceDocs.getResourceDocsObpV400,
//      ImplementationsResourceDocs.getResourceDocsSwagger,
//      ImplementationsResourceDocs.getBankLevelDynamicResourceDocsObp,
//    )
//    registerRoutes(routes, ImplementationsResourceDocs.localResourceDocs, apiPrefix)
//  }
//
//  object ResourceDocs510 extends OBPRestHelper with ResourceDocsAPIMethods with MdcLoggable {
//    val version: ApiVersion = ApiVersion.v5_1_0
//    val versionStatus = ApiVersionStatus.BLEEDING_EDGE.toString
//    val routes: List[OBPEndpoint] = List(
//      ImplementationsResourceDocs.getResourceDocsObpV400,
//      ImplementationsResourceDocs.getResourceDocsSwagger,
//      ImplementationsResourceDocs.getBankLevelDynamicResourceDocsObp,
//      //      ImplementationsResourceDocs.getStaticResourceDocsObp
//    )
//    registerRoutes(routes, ImplementationsResourceDocs.localResourceDocs, apiPrefix)
//  }
//
//  object ResourceDocs600 extends OBPRestHelper with ResourceDocsAPIMethods with MdcLoggable {
//    val version: ApiVersion = ApiVersion.v6_0_0
//    val versionStatus = ApiVersionStatus.BLEEDING_EDGE.toString
//    override def includeTechnologyInResponse: Boolean = true
//    val routes: List[OBPEndpoint] = List(
//      ImplementationsResourceDocs.getResourceDocsObpV400,
//      ImplementationsResourceDocs.getResourceDocsSwagger,
//      ImplementationsResourceDocs.getResourceDocsOpenAPI31,
//      ImplementationsResourceDocs.getBankLevelDynamicResourceDocsObp,
//      //      ImplementationsResourceDocs.getStaticResourceDocsObp
//    )
//    registerRoutes(routes, ImplementationsResourceDocs.localResourceDocs, apiPrefix)
//    // openapi.yaml raw `serve { ... }` block was migrated to
//    // `code.api.util.http4s.Http4sResourceDocs.handleGetResourceDocsOpenAPI31Yaml`.
//  }
//
//}
