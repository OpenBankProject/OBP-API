package code.api.ResourceDocs1_4_0

import scala.language.reflectiveCalls
import code.api.Constant.HostName
import code.api.{OBPRestHelper, ResponseHeader}
import code.api.cache.Caching
import code.api.util.APIUtil._
import code.api.util.{APIUtil, ApiVersionUtils, YAMLUtils}
import code.api.v1_4_0.JSONFactory1_4_0
import code.apicollectionendpoint.MappedApiCollectionEndpointsProvider
import code.util.Helper.{MdcLoggable, SILENCE_IS_GOLDEN}
import com.openbankproject.commons.model.enums.ContentParam.{DYNAMIC, STATIC}
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus}
import net.liftweb.http.{GetRequest, InMemoryResponse, PlainTextResponse, Req, S, StreamingResponse}


object ResourceDocs140 extends OBPRestHelper with ResourceDocsAPIMethods with MdcLoggable {
  val version = ApiVersion.v1_4_0 //    "1.4.0" // We match other api versions so API explorer can easily use the path.
  val versionStatus = ApiVersionStatus.STABLE.toString
  val routes: Seq[OBPEndpoint] = List(
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
  val routes: Seq[OBPEndpoint] = List(
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
  val routes: Seq[OBPEndpoint] = List(
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
  val routes: Seq[OBPEndpoint] = List(
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
  val routes: Seq[OBPEndpoint] = List(
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
    val routes: Seq[OBPEndpoint] = List(
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
    val routes: Seq[OBPEndpoint] = List(
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
    val routes: Seq[OBPEndpoint] = List(
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
    val routes: Seq[OBPEndpoint] = List(
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
    override def includeTechnologyInResponse: Boolean = true
    val routes: Seq[OBPEndpoint] = List(
      ImplementationsResourceDocs.getResourceDocsObpV400,
      ImplementationsResourceDocs.getResourceDocsSwagger,
      ImplementationsResourceDocs.getResourceDocsOpenAPI31,
      ImplementationsResourceDocs.getBankLevelDynamicResourceDocsObp,
//      ImplementationsResourceDocs.getStaticResourceDocsObp
    )
    routes.foreach(route => {
      oauthServe(apiPrefix {
        route
      })
    })
    
    // Register YAML endpoint using standard RestHelper approach
    serve {
      case Req("obp" :: versionStr :: "resource-docs" :: requestedApiVersionString :: "openapi.yaml" :: Nil, _, GetRequest) if versionStr == version.toString => 
        val (resourceDocTags, partialFunctions, locale, contentParam, apiCollectionIdParam) = ResourceDocsAPIMethodsUtil.getParams()
        
        // Validate parameters
        if (S.param("tags").exists(_.trim.isEmpty)) {
          PlainTextResponse("Invalid tags parameter - empty values not allowed", 400)
        } else if (S.param("functions").exists(_.trim.isEmpty)) {
          PlainTextResponse("Invalid functions parameter - empty values not allowed", 400) 
        } else if (S.param("api-collection-id").exists(_.trim.isEmpty)) {
          PlainTextResponse("Invalid api-collection-id parameter - empty values not allowed", 400)
        } else if (S.param("content").isDefined && contentParam.isEmpty) {
          PlainTextResponse("Invalid content parameter. Valid values: static, dynamic, all", 400)
        } else {
          try {
            val requestedApiVersion = ApiVersionUtils.valueOf(requestedApiVersionString)
            if (!versionIsAllowed(requestedApiVersion)) {
              PlainTextResponse(s"API Version not supported: $requestedApiVersionString", 400)
            } else if (locale.isDefined && APIUtil.obpLocaleValidation(locale.get) != SILENCE_IS_GOLDEN) {
              PlainTextResponse(s"Invalid locale: ${locale.get}", 400)
            } else {
              val isVersion4OrHigher = true
              val cacheKey = APIUtil.createResourceDocCacheKey(
                Some("openapi31yaml"),
                requestedApiVersionString,
                resourceDocTags,
                partialFunctions,
                locale,
                contentParam,
                apiCollectionIdParam,
                Some(isVersion4OrHigher)
              )
              val cacheValueFromRedis = Caching.getStaticSwaggerDocCache(cacheKey)
              
              if (cacheValueFromRedis.isDefined) {
                // If we already have a cached YAML string, serve it as before.
                val yamlString = cacheValueFromRedis.get
                val headers = List("Content-Type" -> YAMLUtils.getYAMLContentType, (ResponseHeader.`Correlation-Id` -> getCorrelationId()))
                val bytes = yamlString.getBytes("UTF-8")
                InMemoryResponse(bytes, headers, Nil, 200)
              } else {
                // Generate OpenAPI JSON JValue (this may be large) but stream YAML generation to avoid
                // building a huge YAML string in memory.
                val openApiJValue = try {
                  val resourceDocsJsonFiltered = locale match {
                    case _ if (apiCollectionIdParam.isDefined) =>
                      val operationIds = MappedApiCollectionEndpointsProvider.getApiCollectionEndpoints(apiCollectionIdParam.getOrElse("")).map(_.operationId).map(getObpFormatOperationId)
                      val resourceDocs = ResourceDoc.getResourceDocs(operationIds)
                      val resourceDocsJson = JSONFactory1_4_0.createResourceDocsJson(resourceDocs, isVersion4OrHigher, locale, includeTechnology = includeTechnologyInResponse)
                      resourceDocsJson.resource_docs
                    case _ =>
                      contentParam match {
                        case Some(DYNAMIC) =>
                          ImplementationsResourceDocs.getResourceDocsObpDynamicCached(resourceDocTags, partialFunctions, locale, None, isVersion4OrHigher).head.resource_docs
                        case Some(STATIC) => {
                          ImplementationsResourceDocs.getStaticResourceDocsObpCached(requestedApiVersionString, resourceDocTags, partialFunctions, locale, isVersion4OrHigher).head.resource_docs
                        }
                        case _ => {
                          ImplementationsResourceDocs.getAllResourceDocsObpCached(requestedApiVersionString, resourceDocTags, partialFunctions, locale, contentParam, isVersion4OrHigher).head.resource_docs
                        }
                      }
                  }
                  
                  val hostname = HostName
                  val openApiDoc = code.api.ResourceDocs1_4_0.OpenAPI31JSONFactory.createOpenAPI31Json(resourceDocsJsonFiltered, requestedApiVersionString, hostname)
                  code.api.ResourceDocs1_4_0.OpenAPI31JSONFactory.OpenAPI31JsonFormats.toJValue(openApiDoc)
                } catch {
                  case e: Exception =>
                    logger.error(s"Error generating OpenAPI JSON: ${e.getMessage}", e)
                    throw e
                }
                
                // Attempt to obtain an InputStream that streams YAML
                YAMLUtils.jValueToYAMLInputStream(openApiJValue) match {
                  case scala.util.Success(in) =>
                    val headers = List("Content-Type" -> YAMLUtils.getYAMLContentType, (ResponseHeader.`Correlation-Id` -> getCorrelationId()))
                    // StreamingResponse takes a function that returns an InputStream when called by Lift
                    // StreamingResponse constructor expects: data, onEnd, size, headers, cookies, code
                    // Provide the InputStream directly as `data`, an onEnd that closes the stream,
                    // -1L for unknown size, the headers, empty cookies list, and HTTP 200 code.
                    StreamingResponse(in, () => { try { in.close() } catch { case _: Throwable => () } }, -1L, headers, Nil, 200)
                  case scala.util.Failure(e) =>
                    logger.error(s"Error streaming OpenAPI YAML: ${e.getMessage}", e)
                    // Fallback: try a safe conversion to a string (may be memory heavy) and return that, or an error.
                    // We attempt a safe string conversion as a last resort.
                    val yamlResult = YAMLUtils.jValueToYAMLSafe(openApiJValue, s"# Error converting OpenAPI to YAML: ${openApiJValue.toString}")
                    if (yamlResult.nonEmpty) {
                      Caching.setStaticSwaggerDocCache(cacheKey, yamlResult)
                      val headers = List("Content-Type" -> YAMLUtils.getYAMLContentType, (ResponseHeader.`Correlation-Id` -> getCorrelationId()))
                      val bytes = yamlResult.getBytes("UTF-8")
                      InMemoryResponse(bytes, headers, Nil, 200)
                    } else {
                      PlainTextResponse(s"Error generating OpenAPI YAML: ${e.getMessage}", 500)
                    }
                }
              }
            }
          } catch {
            case _: Exception =>
              PlainTextResponse(s"Invalid API version: $requestedApiVersionString", 400)
          }
        }
    }
  }

}
