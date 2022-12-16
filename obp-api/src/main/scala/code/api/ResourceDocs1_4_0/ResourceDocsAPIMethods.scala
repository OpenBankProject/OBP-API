package code.api.ResourceDocs1_4_0

import java.util.UUID.randomUUID
import code.api.OBPRestHelper
import code.api.builder.OBP_APIBuilder
import code.api.cache.Caching
import code.api.dynamic.endpoint.helper.{DynamicEndpointHelper, DynamicEndpoints}
import code.api.dynamic.entity.helper.DynamicEntityHelper
import code.api.util.APIUtil._
import code.api.util.ApiRole.{canReadDynamicResourceDocsAtOneBank, canReadResourceDoc, canReadStaticResourceDoc}
import code.api.util.ApiTag._
import code.api.util.ExampleValue.endpointMappingRequestBodyExample
import code.api.util._
import code.api.v1_4_0.JSONFactory1_4_0.ResourceDocsJson
import code.api.v1_4_0.{APIMethods140, JSONFactory1_4_0, OBPAPI1_4_0}
import code.api.v2_2_0.{APIMethods220, OBPAPI2_2_0}
import code.api.v3_0_0.OBPAPI3_0_0
import code.api.v3_1_0.OBPAPI3_1_0
import code.api.v4_0_0.{APIMethods400, OBPAPI4_0_0}
import code.apicollectionendpoint.MappedApiCollectionEndpointsProvider
import code.util.Helper.MdcLoggable
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{BankId, ListResult, User}
import com.openbankproject.commons.model.enums.ContentParam.{ALL, DYNAMIC, STATIC}
import com.openbankproject.commons.model.enums.LanguageParam._
import com.openbankproject.commons.model.enums.{ContentParam, LanguageParam}
import com.openbankproject.commons.util.ApiStandards._
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}
import com.tesobe.CacheKeyFromArguments
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.http.{JsonResponse, LiftRules, S}
import net.liftweb.json
import net.liftweb.json.JsonAST.{JField, JString, JValue}
import net.liftweb.json._
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.Props

import java.util.concurrent.ConcurrentHashMap
import code.api.util.NewStyle.HttpCode
import code.api.v5_0_0.OBPAPI5_0_0
import code.util.Helper

import scala.collection.immutable.{List, Nil}
import scala.concurrent.Future

// JObject creation
import code.api.v1_2_1.{APIInfoJSON, APIMethods121, HostedBy, OBPAPI1_2_1}
import code.api.v1_3_0.{APIMethods130, OBPAPI1_3_0}
import code.api.v2_0_0.{APIMethods200, OBPAPI2_0_0}
import code.api.v2_1_0.{APIMethods210, OBPAPI2_1_0}

import scala.collection.mutable.ArrayBuffer

// So we can include resource docs from future versions
import code.api.util.ErrorMessages._
import code.util.Helper.booleanToBox

import scala.concurrent.duration._

import com.openbankproject.commons.ExecutionContext.Implicits.global


trait ResourceDocsAPIMethods extends MdcLoggable with APIMethods220 with APIMethods210 with APIMethods200 with APIMethods140 with APIMethods130 with APIMethods121{
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  // We add previous APIMethods so we have access to the Resource Docs
  self: OBPRestHelper =>

  val ImplementationsResourceDocs = new Object() {

    val localResourceDocs = ArrayBuffer[ResourceDoc]()
    val emptyObjectJson = EmptyClassJson()

    val implementedInApiVersion = ApiVersion.v1_4_0

    implicit val formats = CustomJsonFormats.rolesMappedToClassesFormats

    // avoid repeat execute method getSpecialInstructions, here save the calculate results.
    private val specialInstructionMap = new ConcurrentHashMap[String, Option[String]]()
    // Find any special instructions for partialFunctionName
    def getSpecialInstructions(partialFunctionName: String):  Option[String] = {
      specialInstructionMap.computeIfAbsent(partialFunctionName, _ => {
        // The files should be placed in a folder called special_instructions_for_resources folder inside the src resources folder
        // Each file should match a partial function name or it will be ignored.
        // The format of the file should be mark down.
        val filename = s"/special_instructions_for_resources/${partialFunctionName}.md"
        logger.debug(s"getSpecialInstructions getting $filename")
        val source = LiftRules.loadResourceAsString(filename)
        logger.debug(s"getSpecialInstructions source is $source")
        source match {
          case Full(payload) =>
            logger.debug(s"getSpecialInstructions payload is $payload")
            Some(payload)
          case _ =>
            logger.debug(s"getSpecialInstructions Could not find / load $filename")
            None
        }
      })
    }

    def getResourceDocsList(requestedApiVersion : ApiVersion) : Option[List[ResourceDoc]] =
    {

      // Determine if the partialFunctionName is due to be "featured" in API Explorer etc.
      // "Featured" means shown at the top of the list or so.
      def getIsFeaturedApi(partialFunctionName: String) : Boolean = {
        val partialFunctionNames = APIUtil.getPropsValue("featured_apis") match {
          case Full(v) =>
            v.split(",").map(_.trim).toList
          case _ =>
            List()
        }
        partialFunctionNames.filter(_ == partialFunctionName).length > 0
      }


      // Return a different list of resource docs depending on the version being called.
      // For instance 1_3_0 will have the docs for 1_3_0 and 1_2_1 (when we started adding resource docs) etc.

      logger.debug(s"getResourceDocsList says requestedApiVersion is $requestedApiVersion")

      val resourceDocs = requestedApiVersion match {
        case ApiVersion.`b1`     => OBP_APIBuilder.allResourceDocs
        case ApiVersion.v5_0_0 => OBPAPI5_0_0.allResourceDocs
        case ApiVersion.v4_0_0 => OBPAPI4_0_0.allResourceDocs
        case ApiVersion.v3_1_0 => OBPAPI3_1_0.allResourceDocs
        case ApiVersion.v3_0_0 => OBPAPI3_0_0.allResourceDocs
        case ApiVersion.v2_2_0 => OBPAPI2_2_0.allResourceDocs
        case ApiVersion.v2_1_0 => OBPAPI2_1_0.allResourceDocs
        case ApiVersion.v2_0_0 => Implementations2_0_0.resourceDocs ++ Implementations1_4_0.resourceDocs ++ Implementations1_3_0.resourceDocs ++ Implementations1_2_1.resourceDocs
        case ApiVersion.v1_4_0 => Implementations1_4_0.resourceDocs ++ Implementations1_3_0.resourceDocs ++ Implementations1_2_1.resourceDocs
        case ApiVersion.v1_3_0 => Implementations1_3_0.resourceDocs ++ Implementations1_2_1.resourceDocs
        case ApiVersion.v1_2_1 => Implementations1_2_1.resourceDocs
        case version: ScannedApiVersion => ScannedApis.versionMapScannedApis(version).allResourceDocs
        case _ => ArrayBuffer.empty[ResourceDoc]
      }

      logger.debug(s"There are ${resourceDocs.length} resource docs available to $requestedApiVersion")

      val versionRoutes = requestedApiVersion match {
        case ApiVersion.`b1`     => OBP_APIBuilder.routes
        case ApiVersion.v5_0_0 => OBPAPI5_0_0.routes
        case ApiVersion.v4_0_0 => OBPAPI4_0_0.routes
        case ApiVersion.v3_1_0 => OBPAPI3_1_0.routes
        case ApiVersion.v3_0_0 => OBPAPI3_0_0.routes
        case ApiVersion.v2_2_0 => OBPAPI2_2_0.routes
        case ApiVersion.v2_1_0 => OBPAPI2_1_0.routes
        case ApiVersion.v2_0_0 => OBPAPI2_0_0.routes
        case ApiVersion.v1_4_0 => OBPAPI1_4_0.routes
        case ApiVersion.v1_3_0 => OBPAPI1_3_0.routes
        case ApiVersion.v1_2_1 => OBPAPI1_2_1.routes
        case version: ScannedApiVersion => ScannedApis.versionMapScannedApis(version).routes
        case _                 => Nil
      }

      logger.debug(s"There are ${versionRoutes.length} routes available to $requestedApiVersion")


      // We only want the resource docs for which a API route exists else users will see 404s
      // Get a list of the partial function classes represented in the routes available to this version.
      val versionRoutesClasses = versionRoutes.map { vr => vr.getClass }

      // Only return the resource docs that have available routes
      val activeResourceDocs = resourceDocs.filter(rd => versionRoutesClasses.contains(rd.partialFunction.getClass))

      logger.debug(s"There are ${activeResourceDocs.length} resource docs available to $requestedApiVersion")


      val activePlusLocalResourceDocs = ArrayBuffer[ResourceDoc]()

      activePlusLocalResourceDocs ++= activeResourceDocs
      requestedApiVersion match
      {
        // only `obp` standard show the `localResourceDocs`
        case version: ScannedApiVersion 
          if(version.apiStandard == obp.toString && version==ApiVersion.v4_0_0) =>  
            activePlusLocalResourceDocs ++= localResourceDocs.filterNot(_.partialFunctionName == nameOf(getResourceDocsObp))
        case version: ScannedApiVersion 
          if(version.apiStandard == obp.toString && version!=ApiVersion.v4_0_0) => 
            activePlusLocalResourceDocs ++= localResourceDocs.filterNot(_.partialFunctionName == nameOf(getResourceDocsObpV400))
        case _ => ; // all other standards only show their own apis.
      }


      // Add any featured status and special instructions from Props

      val theResourceDocs = for {
        x <- activePlusLocalResourceDocs

        y = x.copy(
          isFeatured = getIsFeaturedApi(x.partialFunctionName),
          specialInstructions = getSpecialInstructions(x.partialFunctionName),
          requestUrl =  s"/${x.implementedInApiVersion.urlPrefix}/${x.implementedInApiVersion.vDottedApiVersion}${x.requestUrl}", // This is the "implemented" in url
          specifiedUrl = Some(s"/${x.implementedInApiVersion.urlPrefix}/${requestedApiVersion.vDottedApiVersion}${x.requestUrl}"), // This is the "specified" in url when we call the resourceDoc api
        )
      } yield {
        y.connectorMethods = x.connectorMethods // scala language bug, var field can't be kept when do copy, it must reset itself manually.
        y
      }


      logger.debug(s"There are ${theResourceDocs.length} resource docs (including local) available to $requestedApiVersion")

      // Sort by endpoint, verb. Thus / is shown first then /accounts and /banks etc. Seems to read quite well like that.
      Some(theResourceDocs.toList.sortBy(rd => (rd.requestUrl, rd.requestVerb)))
    }





    // TODO constrain version?
    // strip the leading v
    def cleanApiVersionString (version: String) : String = {
      version.stripPrefix("v").stripPrefix("V")
    }



    //implicit val scalaCache  = ScalaCache(GuavaCache(underlyingGuavaCache))
    // if upload DynamicEntity, will generate corresponding endpoints, when current cache timeout, the new endpoints will be shown.
    // so if you want the new generated endpoints shown timely, set this value to a small number, or set to a big number
    val getDynamicResourceDocsTTL : Int = APIUtil.getPropsValue(s"dynamicResourceDocsObp.cache.ttl.seconds", "3600").toInt
    val getStaticResourceDocsTTL : Int = APIUtil.getPropsValue(s"staticResourceDocsObp.cache.ttl.seconds", "86400").toInt

    /**
     * 
     * @param requestedApiVersion
     * @param resourceDocTags
     * @param partialFunctionNames
     * @param contentParam if this is Some(`true`), only show dynamic endpoints, if Some(`false`), only show static. If it is None,  we will show all.  default is None
     * @return
     */
    private def getStaticResourceDocsObpCached(requestedApiVersionString : String,
                                         resourceDocTags: Option[List[ResourceDocTag]],
                                         partialFunctionNames: Option[List[String]],
                                         languageParam: Option[LanguageParam],
                                         contentParam: Option[ContentParam],
                                         cacheModifierParam: Option[String],
                                         isVersion4OrHigher:Boolean
    ) : Box[JValue] = {
      /**
       * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
       * is just a temporary value field with UUID values in order to prevent any ambiguity.
       * The real value will be assigned by Macro during compile time at this line of a code:
       * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
       */
      var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
      CacheKeyFromArguments.buildCacheKey {
        Caching.memoizeSyncWithProvider (Some(cacheKey.toString())) (getStaticResourceDocsTTL second) {
          logger.debug(s"Generating OBP Resource Docs requestedApiVersion is $requestedApiVersionString")
          val requestedApiVersion  = ApiVersionUtils.valueOf(requestedApiVersionString)

          val resourceDocJson = resourceDocsToResourceDocJson(getResourceDocsList(requestedApiVersion), resourceDocTags, partialFunctionNames, isVersion4OrHigher, languageParam)
          resourceDocJson.map(resourceDocsJsonToJsonResponse)
        }
      }
    }

    /**
     *
     * @param requestedApiVersion
     * @param resourceDocTags
     * @param partialFunctionNames
     * @param contentParam if this is Some(`true`), only show dynamic endpoints, if Some(`false`), only show static. If it is None,  we will show all.  default is None
     * @return
     */
    private def getAllResourceDocsObpCached(requestedApiVersionString :  String,
      resourceDocTags: Option[List[ResourceDocTag]],
      partialFunctionNames: Option[List[String]],
      languageParam: Option[LanguageParam],
      contentParam: Option[ContentParam],
      cacheModifierParam: Option[String],
      isVersion4OrHigher:Boolean
    ) : Box[JValue] = {
      /**
       * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
       * is just a temporary value field with UUID values in order to prevent any ambiguity.
       * The real value will be assigned by Macro during compile time at this line of a code:
       * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
       */
      var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
      CacheKeyFromArguments.buildCacheKey {
        Caching.memoizeSyncWithProvider (Some(cacheKey.toString())) (getStaticResourceDocsTTL second) {
          logger.debug(s"Generating OBP Resource Docs requestedApiVersion is $requestedApiVersionString")
          val requestedApiVersion  = ApiVersionUtils.valueOf(requestedApiVersionString)
          
          val dynamicDocs = allDynamicResourceDocs
            .filter(rd => rd.implementedInApiVersion == requestedApiVersion)
            .map(it => it.specifiedUrl match {
              case Some(_) => it
              case _ =>
                it.specifiedUrl = Some(s"/${it.implementedInApiVersion.urlPrefix}/${requestedApiVersion.vDottedApiVersion}${it.requestUrl}")
                it
            })
            .toList

          val filteredDocs = resourceDocTags match {
            // We have tags
            case Some(tags) => {
              // This can create duplicates to use toSet below
              for {
                r <- dynamicDocs
                t <- tags
                if r.tags.contains(t)
              } yield {
                r
              }
            }
            // tags param was not mentioned in url or was empty, so return all
            case None => dynamicDocs
          }

          val staticDocs = getResourceDocsList(requestedApiVersion)
          
          val allDocs = staticDocs.map( _ ++ filteredDocs)
          
          val resourceDocJson = resourceDocsToResourceDocJson(allDocs, resourceDocTags, partialFunctionNames, isVersion4OrHigher, languageParam)
          resourceDocJson.map(resourceDocsJsonToJsonResponse)
        }
      }
    }
    
    private def getResourceDocsObpDynamicCached(
                                         resourceDocTags: Option[List[ResourceDocTag]],
                                         partialFunctionNames: Option[List[String]],
                                         languageParam: Option[LanguageParam],
                                         contentParam: Option[ContentParam],
                                         cacheModifierParam: Option[String],
                                         bankId:Option[String],
                                         isVersion4OrHigher:Boolean
                                        ): Option[JValue] = {
      var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
      CacheKeyFromArguments.buildCacheKey {
        Caching.memoizeSyncWithProvider (Some(cacheKey.toString())) (getDynamicResourceDocsTTL second) {
          val dynamicDocs = allDynamicResourceDocs
            .filter(rd => if (bankId.isDefined) rd.createdByBankId == bankId else true)
            .map(it => it.specifiedUrl match {
              case Some(_) => it
              case _ =>
                it.specifiedUrl = if(it.partialFunctionName.startsWith("dynamicEntity"))Some(s"/${it.implementedInApiVersion.urlPrefix}/${ApiVersion.`dynamic-entity`}${it.requestUrl}") else Some(s"/${it.implementedInApiVersion.urlPrefix}/${ApiVersion.`dynamic-endpoint`}${it.requestUrl}")
                it
            })
            .toList
    
          val filteredDocs = resourceDocTags match {
            // We have tags
            case Some(tags) => {
              // This can create duplicates to use toSet below
              for {
                r <- dynamicDocs
                t <- tags
                if r.tags.contains(t)
              } yield {
                r
              }
            }
            // tags param was not mentioned in url or was empty, so return all
            case None => dynamicDocs
          }
    
          val resourceDocJson = resourceDocsToResourceDocJson(Some(filteredDocs), resourceDocTags, partialFunctionNames, isVersion4OrHigher, languageParam)
          resourceDocJson.map(resourceDocsJsonToJsonResponse)
    }}}



    private def resourceDocsToResourceDocJson(rd: Option[List[ResourceDoc]],
                                              resourceDocTags: Option[List[ResourceDocTag]],
                                              partialFunctionNames: Option[List[String]],
                                              isVersion4OrHigher:Boolean, 
                                              languageParam: Option[LanguageParam]): Option[ResourceDocsJson] = {
      for {
        resourceDocs <- rd
      } yield {
        // Filter
        val rdFiltered = ResourceDocsAPIMethodsUtil.filterResourceDocs(resourceDocs, resourceDocTags, partialFunctionNames)
        // Format the data as json
        JSONFactory1_4_0.createResourceDocsJson(rdFiltered, isVersion4OrHigher, languageParam)
      }
    }
    
    private val getChineseVersionResourceDocs : Box[JsonResponse] = {
      val stream = getClass().getClassLoader().getResourceAsStream("ResourceDocs/ResourceDocs-Chinese.json")
      val chineseVersion = try {
        val bufferedSource = scala.io.Source.fromInputStream(stream, "utf-8")
        val jsonStringFromFile = bufferedSource.mkString
        json.parse(jsonStringFromFile);
      } finally {
        stream.close()
      }
      Full(successJsonResponse(chineseVersion))
    }
    def upperName(name: String): (String, String) = (name.toUpperCase(), name)




    val exampleResourceDoc =  ResourceDoc(
      dummy(implementedInApiVersion.toString, "DUMMY"),
      implementedInApiVersion,
      "testResourceDoc",
      "GET",
      "/dummy",
      "Test Resource Doc.",
      """I am only a test Resource Doc""",
      emptyObjectJson,
      emptyObjectJson,
      UnknownError :: Nil,
      List(apiTagDocumentation))


    val exampleResourceDocsJson = JSONFactory1_4_0.createResourceDocsJson(List(exampleResourceDoc), false, None)
    
    val exampleResourceDocsJsonV400 = JSONFactory1_4_0.createResourceDocsJson(List(exampleResourceDoc), true, None)




    def getResourceDocsDescription(isBankLevelResourceDoc: Boolean) = {

      val endpointBankIdPath = if (isBankLevelResourceDoc) "/banks/BANK_ID" else ""
    
      s"""Get documentation about the RESTful resources on this server including example bodies for POST and PUT requests.
         |
         |This is the native data format used to document OBP endpoints. Each endpoint has a Resource Doc (a Scala case class) defined in the source code.
         |
         | This endpoint is used by OBP API Explorer to display and work with the API documentation.
         |
         | Most (but not all) fields are also available in swagger format. (The Swagger endpoint is built from Resource Docs.)
         |
         | API_VERSION is the version you want documentation about e.g. v3.0.0
         |
         | You may filter this endpoint with tags parameter e.g. ?tags=Account,Bank
         |
         | You may filter this endpoint with functions parameter e.g. ?functions=enableDisableConsumers,getConnectorMetrics
         |
         | For possible function values, see implemented_by.function in the JSON returned by this endpoint or the OBP source code or the footer of the API Explorer which produces a comma separated list of functions that reflect the server or filtering by API Explorer based on tags etc.
         |
         | You may filter this endpoint using the 'content' url parameter, e.g. ?content=dynamic
         | if set content=dynamic, only show dynamic endpoints, if content=static, only show the static endpoints. if omit this parameter, we will show all the endpoints.
         |
         | You may need some other language resource docs, now we support en and zh , e.g. ?language=zh
         | 
         | You can filter with api-collection-id, but api-collection-id can not be used with others together. If api-collection-id is used in URL, it will ignore all other parameters. 
         |
         | You can easily pass the cache, use different value for cache-modifier, eg: ?cache-modifier= 123
         |
         |See the Resource Doc endpoint for more information.
         |
         |Following are more examples:
         |${getObpApiRoot}/v4.0.0$endpointBankIdPath/resource-docs/v4.0.0/obp
         |${getObpApiRoot}/v4.0.0$endpointBankIdPath/resource-docs/v4.0.0/obp?tags=Account,Bank
         |${getObpApiRoot}/v4.0.0$endpointBankIdPath/resource-docs/v4.0.0/obp?functions=getBanks,bankById
         |${getObpApiRoot}/v4.0.0$endpointBankIdPath/resource-docs/v4.0.0/obp?language=zh
         |${getObpApiRoot}/v4.0.0$endpointBankIdPath/resource-docs/v4.0.0/obp?content=static,dynamic,all
         |${getObpApiRoot}/v4.0.0$endpointBankIdPath/resource-docs/v4.0.0/obp?api-collection-id=4e866c86-60c3-4268-a221-cb0bbf1ad221
         |${getObpApiRoot}/v4.0.0$endpointBankIdPath/resource-docs/v4.0.0/obp?cache-modifier=3141592653
         |
         |<ul>
         |<li> operation_id is concatenation of "v", version and function and should be unique (used for DOM element IDs etc. maybe used to link to source code) </li>
         |<li> version references the version that the API call is defined in.</li>
         |<li> function is the (scala) partial function that implements this endpoint. It is unique per version of the API.</li>
         |<li> request_url is empty for the root call, else the path. It contains the standard prefix (e.g. /obp) and the implemented version (the version where this endpoint was defined) e.g. /obp/v1.2.0/resource</li>
         |<li> specified_url (recommended to use) is empty for the root call, else the path. It contains the standard prefix (e.g. /obp) and the version specified in the call e.g. /obp/v3.1.0/resource. In OBP, endpoints are first made available at the request_url, but the same resource (function call) is often made available under later versions (specified_url). To access the latest version of all endpoints use the latest version available on your OBP instance e.g. /obp/v3.1.0 - To get the original version use the request_url. We recommend to use the specified_url since non semantic improvements are more likely to be applied to later implementations of the call.</li>
         |<li> summary is a short description inline with the swagger terminology. </li>
         |<li> description may contain html markup (generated from markdown on the server).</li>
         |</ul>
      """
    }
    
    
    localResourceDocs += ResourceDoc(
      getResourceDocsObp,
      implementedInApiVersion,
      "getResourceDocsObp",
      "GET",
      "/resource-docs/API_VERSION/obp",
      "Get Resource Docs.",
      getResourceDocsDescription(false),
      emptyObjectJson,
      exampleResourceDocsJson, 
      UnknownError :: Nil,
      List(apiTagDocumentation, apiTagApi),
      Some(List(canReadResourceDoc))
    )

    def resourceDocsRequireRole = APIUtil.getPropsAsBoolValue("resource_docs_requires_role", false)
    // Provides resource documents so that API Explorer (or other apps) can display API documentation
    // Note: description uses html markup because original markdown doesn't easily support "_" and there are multiple versions of markdown.
    def getResourceDocsObp : OBPEndpoint = {
      case "resource-docs" :: requestedApiVersionString :: "obp" :: Nil JsonGet _ => {
        val (tags, partialFunctions, languageParam, contentParam, apiCollectionIdParam, cacheModifierParam) = ResourceDocsAPIMethodsUtil.getParams()
        cc =>
          getApiLevelResourceDocs(cc,requestedApiVersionString, tags, partialFunctions, languageParam, contentParam, apiCollectionIdParam, cacheModifierParam, false, false)
      }
    }
    
    localResourceDocs += ResourceDoc(
      getResourceDocsObpV400,
      implementedInApiVersion,
      nameOf(getResourceDocsObpV400),
      "GET",
      "/resource-docs/API_VERSION/obp",
      "Get Resource Docs",
      getResourceDocsDescription(false),
      emptyObjectJson,
      exampleResourceDocsJsonV400,
      UnknownError :: Nil,
      List(apiTagDocumentation, apiTagApi),
      Some(List(canReadResourceDoc))
    )
    
    def getResourceDocsObpV400 : OBPEndpoint = {
      case "resource-docs" :: requestedApiVersionString :: "obp" :: Nil JsonGet _ => {
        val (tags, partialFunctions, languageParam, contentParam, apiCollectionIdParam, cacheModifierParam) = ResourceDocsAPIMethodsUtil.getParams()
        cc =>
          getApiLevelResourceDocs(cc,requestedApiVersionString, tags, partialFunctions, languageParam, contentParam, apiCollectionIdParam, cacheModifierParam, true, false)
      }
    }

//    localResourceDocs += ResourceDoc(
//      getStaticResourceDocsObp,
//      implementedInApiVersion,
//      nameOf(getStaticResourceDocsObp),
//      "GET",
//      "/static-resource-docs/API_VERSION/obp",
//      "Get Static Resource Docs",
//      getResourceDocsDescription(false),
//      emptyObjectJson,
//      exampleResourceDocsJsonV400,
//      UnknownError :: Nil,
//      List(apiTagDocumentation, apiTagApi),
//      Some(List(canReadStaticResourceDoc))
//    )
//
//    def getStaticResourceDocsObp : OBPEndpoint = {
//      case "static-resource-docs" :: requestedApiVersionString :: "obp" :: Nil JsonGet _ => {
//        val (tags, partialFunctions, languageParam, contentParam, apiCollectionIdParam, cacheModifierParam) = ResourceDocsAPIMethodsUtil.getParams()
//        cc =>
//          getApiLevelResourceDocs(
//            cc,requestedApiVersionString,
//            tags,
//            partialFunctions,
//            languageParam,
//            Some(ContentParam.STATIC) ,//Note: here it set to default STATIC value.
//            apiCollectionIdParam,
//            cacheModifierParam,
//            true,
//            true
//          )
//      }
//    }


    //API level just mean, this response will be forward to liftweb directly.
    private def getApiLevelResourceDocs(
      cc: CallContext,
      requestedApiVersionString: String,
      tags: Option[List[ResourceDocTag]],
      partialFunctions: Option[List[String]],
      languageParam: Option[LanguageParam],
      contentParam: Option[ContentParam],
      apiCollectionIdParam: Option[String],
      cacheModifierParam: Option[String],
      isVersion4OrHigher: Boolean,
      isStaticResource: Boolean,
    ) = {
        for {
          (u: Box[User], callContext: Option[CallContext]) <- resourceDocsRequireRole match {
            case false => anonymousAccess(cc)
            case true => authenticatedAccess(cc) // If set resource_docs_requires_role=true, we need check the authentication
          }
          _ <- resourceDocsRequireRole match {
            case false => Future()
            case true => // If set resource_docs_requires_role=true, we need check the the roles as well
              if(isStaticResource)
                NewStyle.function.hasAtLeastOneEntitlement(failMsg = UserHasMissingRoles + canReadStaticResourceDoc.toString)("", u.map(_.userId).getOrElse(""), ApiRole.canReadStaticResourceDoc :: Nil, cc.callContext)
              else
                NewStyle.function.hasAtLeastOneEntitlement(failMsg = UserHasMissingRoles + canReadResourceDoc.toString)("", u.map(_.userId).getOrElse(""), ApiRole.canReadResourceDoc :: Nil, cc.callContext)
          }
          requestedApiVersion <- NewStyle.function.tryons(s"$InvalidApiVersionString $requestedApiVersionString", 400, callContext) {ApiVersionUtils.valueOf(requestedApiVersionString)}
          _ <- Helper.booleanToFuture(s"$ApiVersionNotSupported $requestedApiVersionString", 400, callContext)(versionIsAllowed(requestedApiVersion))
          json <- languageParam match {
            case Some(ZH) => Future(getChineseVersionResourceDocs)
            case _ if (apiCollectionIdParam.isDefined) =>
              val operationIds = MappedApiCollectionEndpointsProvider.getApiCollectionEndpoints(apiCollectionIdParam.getOrElse("")).map(_.operationId).map(getObpFormatOperationId)
              val resourceDocs = ResourceDoc.getResourceDocs(operationIds)
              val resourceDocsJson = JSONFactory1_4_0.createResourceDocsJson(resourceDocs, isVersion4OrHigher, languageParam)
              val resourceDocsJsonJValue = Full(resourceDocsJsonToJsonResponse(resourceDocsJson))
              Future(resourceDocsJsonJValue.map(successJsonResponse(_)))
            case _ =>
              contentParam match {
                case Some(DYNAMIC) =>
                  val dynamicDocs: Box[JValue] = getResourceDocsObpDynamicCached(tags, partialFunctions, languageParam, contentParam, cacheModifierParam, None, isVersion4OrHigher)
                  Future(dynamicDocs.map(successJsonResponse(_)))
                case Some(STATIC) =>
                  val staticDocs: Box[JValue] = getStaticResourceDocsObpCached(requestedApiVersionString, tags, partialFunctions, languageParam, contentParam, cacheModifierParam, isVersion4OrHigher)
                  Future(staticDocs.map(successJsonResponse(_)))
                case _ =>
                  val docs: Box[JValue] = getAllResourceDocsObpCached(requestedApiVersionString, tags, partialFunctions, languageParam, contentParam, cacheModifierParam, isVersion4OrHigher)
                  Future(docs.map(successJsonResponse(_)))
              }
          }
        } yield {
          (json, HttpCode.`200`(callContext))
        }
    }

    localResourceDocs += ResourceDoc(
      getBankLevelDynamicResourceDocsObp,
      implementedInApiVersion,
      nameOf(getBankLevelDynamicResourceDocsObp),
      "GET",
      "/banks/BANK_ID/resource-docs/API_VERSION/obp",
      "Get Bank Level Dynamic Resource Docs.",
      getResourceDocsDescription(true),
      emptyObjectJson,
      exampleResourceDocsJson,
      UnknownError :: Nil,
      List(apiTagDocumentation, apiTagApi),
      Some(List(canReadDynamicResourceDocsAtOneBank))
    )

    // Provides resource documents so that API Explorer (or other apps) can display API documentation
    // Note: description uses html markup because original markdown doesn't easily support "_" and there are multiple versions of markdown.
    def getBankLevelDynamicResourceDocsObp : OBPEndpoint = {
      case "banks" :: bankId :: "resource-docs" :: requestedApiVersionString :: "obp" :: Nil JsonGet _ => {
        val (tags, partialFunctions, languageParam, contentParam, apiCollectionIdParam, cacheModifierParam) = ResourceDocsAPIMethodsUtil.getParams()
        cc =>
          for {
            (u: Box[User], callContext: Option[CallContext]) <- resourceDocsRequireRole match {
              case false => anonymousAccess(cc)
              case true => authenticatedAccess(cc) // If set resource_docs_requires_role=true, we need check the authentication
            }
            (_, callContext) <- NewStyle.function.getBank(BankId(bankId), Option(cc))
            _ <- resourceDocsRequireRole match {
              case false => Future()
              case true => // If set resource_docs_requires_role=true, we need check the the roles as well
                NewStyle.function.hasAtLeastOneEntitlement(failMsg = UserHasMissingRoles + ApiRole.canReadDynamicResourceDocsAtOneBank.toString)(
                  bankId, u.map(_.userId).getOrElse(""), ApiRole.canReadDynamicResourceDocsAtOneBank::Nil, cc.callContext
                )
            }
            requestedApiVersion <- NewStyle.function.tryons(s"$InvalidApiVersionString $requestedApiVersionString", 400, callContext) {ApiVersionUtils.valueOf(requestedApiVersionString)}
            json <- NewStyle.function.tryons(s"$UnknownError Can not create dynamic resource docs.", 400, callContext) {
              getResourceDocsObpDynamicCached(tags, partialFunctions, languageParam, contentParam,  cacheModifierParam, Some(bankId), false).map(successJsonResponse(_)).get
            }
          } yield {
            (Full(json), HttpCode.`200`(callContext))
          }
      }
    }


    localResourceDocs += ResourceDoc(
      getResourceDocsSwagger,
      implementedInApiVersion,
      "getResourceDocsSwagger",
      "GET",
      "/resource-docs/API_VERSION/swagger",
      "Get Swagger documentation",
      s"""Returns documentation about the RESTful resources on this server in Swagger format.
         |
         |API_VERSION is the version you want documentation about e.g. v3.0.0
         |
         |You may filter this endpoint using the 'tags' url parameter e.g. ?tags=Account,Bank
         |
         |(All endpoints are given one or more tags which for used in grouping)
         |
         |You may filter this endpoint using the 'functions' url parameter e.g. ?functions=getBanks,bankById
         |
         |(Each endpoint is implemented in the OBP Scala code by a 'function')
         |
         |See the Resource Doc endpoint for more information.
         |
         |Following are more examples:
         |${getObpApiRoot}/v3.1.0/resource-docs/v3.1.0/swagger
         |${getObpApiRoot}/v3.1.0/resource-docs/v3.1.0/swagger?tags=Account,Bank
         |${getObpApiRoot}/v3.1.0/resource-docs/v3.1.0/swagger?functions=getBanks,bankById
         |${getObpApiRoot}/v3.1.0/resource-docs/v3.1.0/swagger?tags=Account,Bank,PSD2&functions=getBanks,bankById
         |
      """,
      emptyObjectJson,
      emptyObjectJson,
      UnknownError :: Nil,
      List(apiTagDocumentation, apiTagApi)
    )


    def getResourceDocsSwagger : OBPEndpoint = {
      case "resource-docs" :: requestedApiVersionString :: "swagger" :: Nil JsonGet _ => {
        cc =>{
          for {
            (resourceDocTags, partialFunctions, languageParam, contentParam, apiCollectionIdParam, cacheModifierParam) <- tryo(ResourceDocsAPIMethodsUtil.getParams())
            requestedApiVersion <- tryo(ApiVersionUtils.valueOf(requestedApiVersionString)) ?~! s"$InvalidApiVersionString Current Version is $requestedApiVersionString"
            _ <- booleanToBox(versionIsAllowed(requestedApiVersion), s"$ApiVersionNotSupported Current Version is $requestedApiVersionString")
            staticJson <- getResourceDocsSwaggerCached(requestedApiVersionString, resourceDocTags, partialFunctions)
            dynamicJson <- getResourceDocsSwagger(requestedApiVersionString, resourceDocTags, partialFunctions)
          } yield {
            successJsonResponse(staticJson.merge(dynamicJson))
          }
        }
      }
    }


    private def getResourceDocsSwaggerCached(requestedApiVersionString : String, resourceDocTags: Option[List[ResourceDocTag]], partialFunctionNames: Option[List[String]]) : Box[JValue] = {
      /**
       * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
       * is just a temporary value field with UUID values in order to prevent any ambiguity.
       * The real value will be assigned by Macro during compile time at this line of a code:
       * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
       */
      var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
      CacheKeyFromArguments.buildCacheKey {
        Caching.memoizeSyncWithProvider (Some(cacheKey.toString())) (getStaticResourceDocsTTL second) {
          logger.debug(s"Generating Swagger requestedApiVersion is $requestedApiVersionString")

          Box.tryo(ApiVersionUtils.valueOf(requestedApiVersionString)) match {
            case Full(requestedApiVersion) =>
              val resourceDocs: Option[List[ResourceDoc]] = getResourceDocsList(requestedApiVersion)
              getResourceDocsSwagger(requestedApiVersionString, resourceDocTags, partialFunctionNames, resourceDocs)
            case e =>
              (e ?~! InvalidApiVersionString).asInstanceOf[Box[JValue]]
          }
        }
      }
    }

    // if not supply resourceDocs parameter, just get dynamic ResourceDocs swagger
    private def getResourceDocsSwagger(requestedApiVersionString : String,
                                       resourceDocTags: Option[List[ResourceDocTag]],
                                       partialFunctionNames: Option[List[String]],
                                       resourceDocs: Option[List[ResourceDoc]] = None) : Box[JValue] = {

      // build swagger and remove not used definitions
      def buildSwagger(resourceDoc: SwaggerJSONFactory.SwaggerResourceDoc, definitions: json.JValue) = {
        val jValue = Extraction.decompose(resourceDoc)
        val JObject(pathsRef) = definitions \\ "$ref"
        val JObject(definitionsRef) = jValue \\ "$ref"
        val RefRegx = "#/definitions/([^/]+)".r

        val allRefTypeName: Set[String] = Set(pathsRef, definitionsRef).flatMap { fields =>
          fields.collect {
            case JField(_, JString(RefRegx(v))) => v
          }
        }
        // filter out all not used definitions
        val usedDefinitions = {
          val JObject(fields) = definitions \ "definitions"
           JObject(
              JField("definitions",
                JObject(
                  fields.collect {
                    case jf @JField(name, _) if allRefTypeName.contains(name) => jf
                  }
                )
              ) :: Nil
           )
        }

        jValue merge usedDefinitions
      }

      def resourceDocsToJValue(resourceDocs: Option[List[ResourceDoc]]): Box[JValue] = {
        for {
          requestedApiVersion <- Box.tryo(ApiVersionUtils.valueOf(requestedApiVersionString)) ?~! InvalidApiVersionString
          _ <- booleanToBox(versionIsAllowed(requestedApiVersion), ApiVersionNotSupported)
          rd <- resourceDocs
        } yield {
          // Filter
          val rdFiltered = ResourceDocsAPIMethodsUtil
            .filterResourceDocs(rd, resourceDocTags, partialFunctionNames)
            .map {
              /**
               * dynamic endpoints related structure is not STABLE structure, no need be parsed to a static structure.
               * So here filter out them.
               */
              case doc if (doc.partialFunctionName == nameOf(APIMethods400.Implementations4_0_0.createDynamicEndpoint) ||
                doc.partialFunctionName == nameOf(APIMethods400.Implementations4_0_0.createBankLevelDynamicEndpoint) ) =>
                doc.copy(exampleRequestBody =  ExampleValue.dynamicEndpointRequestBodyEmptyExample,
                  successResponseBody = ExampleValue.dynamicEndpointResponseBodyEmptyExample
                )

              case doc if (doc.partialFunctionName == nameOf(APIMethods400.Implementations4_0_0.createEndpointMapping) ||
                doc.partialFunctionName == nameOf(APIMethods400.Implementations4_0_0.createBankLevelEndpointMapping) ) =>
                doc.copy(
                  exampleRequestBody = endpointMappingRequestBodyExample,
                  successResponseBody = endpointMappingRequestBodyExample
                )
                
              case doc if ( doc.partialFunctionName == nameOf(APIMethods400.Implementations4_0_0.getDynamicEndpoint) ||
                doc.partialFunctionName == nameOf(APIMethods400.Implementations4_0_0.getBankLevelDynamicEndpoint)) =>
                doc.copy(successResponseBody = ExampleValue.dynamicEndpointResponseBodyEmptyExample)

              case doc if (doc.partialFunctionName == nameOf(APIMethods400.Implementations4_0_0.getDynamicEndpoints) ||
                doc.partialFunctionName == nameOf(APIMethods400.Implementations4_0_0.getMyDynamicEndpoints) ||
                doc.partialFunctionName == nameOf(APIMethods400.Implementations4_0_0.getBankLevelDynamicEndpoints)
                )=>
                doc.copy(successResponseBody = ListResult(
                  "dynamic_endpoints",
                  List(ExampleValue.dynamicEndpointResponseBodyEmptyExample)
                ))

              case doc =>
                doc
            }
          // Format the data as json
          val json = SwaggerJSONFactory.createSwaggerResourceDoc(rdFiltered, requestedApiVersion)
          //Get definitions of objects of success responses
          val allSwaggerDefinitionCaseClasses = SwaggerDefinitionsJSON.allFields
          val jsonAST = SwaggerJSONFactory.loadDefinitions(rdFiltered, allSwaggerDefinitionCaseClasses)
          // Merge both results and return
          buildSwagger(json, jsonAST)
        }
      }

      resourceDocs match {
        case docs @Some(_) => resourceDocsToJValue(docs)
        case _ =>
          val dynamicDocs = allDynamicResourceDocs
          resourceDocsToJValue(Some(dynamicDocs))
      }
    }

    if (Props.devMode) {
      localResourceDocs += ResourceDoc(
        dummy(implementedInApiVersion.vDottedApiVersion, "DUMMY"),
        implementedInApiVersion,
        "testResourceDoc",
        "GET",
        "/dummy",
        "Test Resource Doc.",
        """
          |I am only a test Resource Doc
          |
          |#This should be H1
          |
          |##This should be H2
          |
          |###This should be H3
          |
          |####This should be H4
          |
          |Here is a list with two items:
          |
          |* One
          |* Two
          |
          |There are underscores by them selves _
          |
          |There are _underscores_ around a word
          |
          |There are underscores_in_words
          |
          |There are 'underscores_in_words_inside_quotes'
          |
          |There are (underscores_in_words_in_brackets)
          |
          |_etc_...""",
        emptyObjectJson,
        emptyObjectJson,
        UnknownError :: Nil,
        List(apiTagDocumentation))
    }



    def dummy(apiVersion : String, apiVersionStatus: String) : OBPEndpoint = {
      case "dummy" :: Nil JsonGet req => {
        cc =>
          val apiDetails: JValue = {
            val hostedBy = new HostedBy("Dummy Org", "contact@example.com", "12345", "http://www.example.com")
            val apiInfoJSON = new APIInfoJSON(apiVersion, apiVersionStatus, gitCommit, "dummy-connector", hostedBy)
            Extraction.decompose(apiInfoJSON)
          }

          Full(successJsonResponse(apiDetails, 200))
      }
    }

  }

  private def resourceDocsJsonToJsonResponse(resourceDocsJson: ResourceDocsJson): JValue = {
    /**
     * replace JValue key: jsonClass --> api_role
     */
    def replaceJsonKey(json: JValue): JValue = json transformField {
      case JField("jsonClass", x) => JField("role", x)
      case JField("requiresBankId", x) => JField("requires_bank_id", x)
    }

    /**
     * This is only used for remove `JvalueCaseClass` in JValue.
     *
     * @`implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)`
     *            For SwaggerCodeGen-obp, need to change String --> JValue implicitly.
     *            There will introduce the new key `JvalueCaseClass` in JValue.
     *            So in GetResourceDoc API, we need remove it.
     */
    def removeJsonKeyAndKeepChildObject(json: JValue): JValue = json transform {
      case JObject(List(JField("jvalueToCaseclass", JObject(x)))) => JObject(x)
    }

    /**
     * replace JValue value: ApiRole$CanCreateUser --> CanCreateUser
     */
    def replaceJsonValue(json: JValue): JValue = json transformField {
      case JField("role", JString(x)) => JField("role", JString(x.replace("ApiRole$", "")))
    }

    replaceJsonValue(replaceJsonKey(removeJsonKeyAndKeepChildObject(Extraction.decompose(resourceDocsJson)(CustomJsonFormats.formats))))
  }
}

object ResourceDocsAPIMethodsUtil extends MdcLoggable{



  def stringToOptBoolean (x: String) : Option[Boolean] = x.toLowerCase match {
    case "true" | "yes" | "1" | "-1" => Some(true)
    case "false" | "no" | "0" => Some(false)
    case _ => Empty
  }

  def stringToLanguageParam (x: String) : Option[LanguageParam] = x.toLowerCase match {
    case "en"  => Some(EN)
    case "zh"  => Some(ZH)
    case "es" | "es_ES"  => Some(ES)
    case _ => Empty
  }

  def stringToContentParam (x: String) : Option[ContentParam] = x.toLowerCase match {
    case "dynamic"  => Some(DYNAMIC)
    case "static"  => Some(STATIC)
    case "all"  => Some(ALL)
    case _ => None
  }

  def getParams() : (Option[List[ResourceDocTag]], Option[List[String]], Option[LanguageParam], Option[ContentParam], Option[String], Option[String]) = {

    val rawTagsParam = S.param("tags")


    val tags: Option[List[ResourceDocTag]] =
      rawTagsParam match {
        // if tags= is supplied in the url, we want to ignore it
        case Full("") => None
        // if tags is not mentioned at all, we want to ignore it
        case Empty => None
        case _  => {
          val commaSeparatedList : String = rawTagsParam.getOrElse("")
          val tagList : List[String] = commaSeparatedList.trim().split(",").toList
          val resourceDocTags =
            for {
              y <- tagList
            } yield {
              ResourceDocTag(y)
            }
          Some(resourceDocTags)
        }
      }
    logger.info(s"tagsOption is $tags")

    // So we can produce a reduced list of resource docs to prevent manual editing of swagger files.
    val rawPartialFunctionNames = S.param("functions")

    val partialFunctionNames: Option[List[String]] =
      rawPartialFunctionNames match {
        // if functions= is supplied in the url, we want to ignore it
        case Full("") => None
        // if functions is not mentioned at all, we want to ignore it
        case Empty => None
        case _  => {
          val commaSeparatedList : String = rawPartialFunctionNames.getOrElse("")
          val stringList : List[String] = commaSeparatedList.trim().split(",").toList
          val pfns =
            for {
              y <- stringList
            } yield {
              y
            }
          Some(pfns)
        }
      }
    logger.info(s"partialFunctionNames is $partialFunctionNames")

    // So we can produce a reduced list of resource docs to prevent manual editing of swagger files.
    val languageParam = for {
      x <- S.param("language").or(S.param("locale"))
      y <- stringToLanguageParam(x)
    } yield y
    logger.info(s"languageParam is $languageParam")

    // So we can produce a reduced list of resource docs to prevent manual editing of swagger files.
    val contentParam = for {
      x <- S.param("content")
      y <- stringToContentParam(x)
    } yield y
    logger.info(s"content is $contentParam")

    val apiCollectionIdParam = for {
      x <- S.param("api-collection-id")
    } yield x
    logger.info(s"apiCollectionIdParam is $apiCollectionIdParam")
    
    val cacheModifierParam = for {
      x <- S.param("cache-modifier")
    } yield x
    logger.info(s"cacheModifierParam is $cacheModifierParam")

    (tags, partialFunctionNames, languageParam, contentParam, apiCollectionIdParam, cacheModifierParam)
  }


  /*
Filter Resource Docs based on the query parameters, else return the full list.
We don't assume a default catalog (as API Explorer does)
so the caller must specify any required filtering by catalog explicitly.
 */
  def filterResourceDocs(
    allResources: List[ResourceDoc], 
    resourceDocTags: Option[List[ResourceDocTag]], 
    partialFunctionNames: Option[List[String]]
  ) : List[ResourceDoc] = {

    // Filter (include, exclude or ignore)
    val filteredResources1 : List[ResourceDoc] =  allResources

    // Check if we have partialFunctionNames as the parameters, and if so filter by them
    val filteredResources2 : List[ResourceDoc] = partialFunctionNames match {
      case Some(pfNames) => {
        // This can create duplicates to use toSet below
        for {
          rd <- filteredResources1
          partialFunctionName <- pfNames
          if rd.partialFunctionName.equals(partialFunctionName)
        } yield {
          rd
        }
      }
      // tags param was not mentioned in url or was empty, so return all
      case None => filteredResources1
    }

    val filteredResources3 : List[ResourceDoc] = filteredResources2


    // Check if we have tags, and if so filter by them
    val filteredResources4: List[ResourceDoc] = resourceDocTags match {
      // We have tags
      case Some(tags) => {
        // This can create duplicates to use toSet below
        for {
          r <- filteredResources3
          t <- tags
          if r.tags.contains(t)
        } yield {
          r
        }
      }
      // tags param was not mentioned in url or was empty, so return all
      case None => filteredResources3
    }
    

    val resourcesToUse = filteredResources4.toSet.toList


    logger.debug(s"allResources count is ${allResources.length}")
    logger.debug(s"filteredResources1 count is ${filteredResources1.length}")
    logger.debug(s"filteredResources2 count is ${filteredResources2.length}")
    logger.debug(s"filteredResources3 count is ${filteredResources3.length}")
    logger.debug(s"filteredResources4 count is ${filteredResources4.length}")
    logger.debug(s"resourcesToUse count is ${resourcesToUse.length}")


    if (filteredResources4.length > 0 && resourcesToUse.length == 0) {
      logger.info("tags filter reduced the list of resource docs to zero")
    }

    resourcesToUse
  }


}

