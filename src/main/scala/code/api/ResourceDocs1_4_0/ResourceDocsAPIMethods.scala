package code.api.ResourceDocs1_4_0

import code.api.util.APIUtil
import code.api.util.APIUtil._
import code.api.util.APIUtil.ApiVersion._
import code.api.v1_2_1.Akka
import code.api.v1_4_0.{APIMethods140, JSONFactory1_4_0, OBPAPI1_4_0}
import code.api.v2_2_0.{APIMethods220, OBPAPI2_2_0}
import code.api.v3_0_0.{APIMethods300, OBPAPI3_0_0}
import code.api.v3_0_0.OBPAPI3_0_0._
import code.bankconnectors.vMar2017.KafkaMappedConnector_vMar2017
import net.liftweb.common.{Box, Empty, Full}
import code.util.Helper.MdcLoggable
import net.liftweb.http.rest.RestHelper
import net.liftweb.http.{JsonResponse, Req, S}
import net.liftweb.json._
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.JsonDSL._
import net.liftweb.util.Props

import scala.collection.immutable.Nil
import scala.collection.mutable
import scalacache.ScalaCache
import scalacache.guava.GuavaCache

// JObject creation
import code.api.v1_2_1.{APIInfoJSON, APIMethods121, HostedBy, OBPAPI1_2_1}
import code.api.v1_3_0.{APIMethods130, OBPAPI1_3_0}
import code.api.v2_0_0.{APIMethods200, OBPAPI2_0_0}
import code.api.v2_1_0.{APIMethods210, OBPAPI2_1_0}
import code.api.util.ErrorMessages._

import scala.collection.mutable.ArrayBuffer

// So we can include resource docs from future versions
import java.text.SimpleDateFormat
import code.model._
import code.api.ResourceDocs1_4_0.SwaggerJSONFactory._
import code.api.util.ErrorMessages._

import scalacache.memoization.memoizeSync
import concurrent.duration._
import code.util.Helper.booleanToBox


trait ResourceDocsAPIMethods extends MdcLoggable with APIMethods220 with APIMethods210 with APIMethods200 with APIMethods140 with APIMethods130 with APIMethods121{
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  // We add previous APIMethods so we have access to the Resource Docs
  self: RestHelper =>

  val ImplementationsResourceDocs = new Object() {

    val localResourceDocs = ArrayBuffer[ResourceDoc]()
    val emptyObjectJson = EmptyClassJson()
    val statedApiVersion : String = "1_4_0"

    val exampleDateString : String ="22/08/2013"
    val simpleDateFormat : SimpleDateFormat = new SimpleDateFormat("dd/mm/yyyy")
    val exampleDate = simpleDateFormat.parse(exampleDateString)


    def getResourceDocsList(requestedApiVersion : ApiVersion) : Option[List[ResourceDoc]] =
    {
      // Return a different list of resource docs depending on the version being called.
      // For instance 1_3_0 will have the docs for 1_3_0 and 1_2_1 (when we started adding resource docs) etc.

      logger.debug(s"getResourceDocsList says requestedApiVersion is $requestedApiVersion")

      val resourceDocs = requestedApiVersion match {
        case ApiVersion.v3_0_0 => OBPAPI3_0_0.allResourceDocs
        case ApiVersion.v2_2_0 => OBPAPI2_2_0.allResourceDocs
        case ApiVersion.v2_1_0 => OBPAPI2_1_0.allResourceDocs
        case ApiVersion.v2_0_0 => Implementations2_0_0.resourceDocs ++ Implementations1_4_0.resourceDocs ++ Implementations1_3_0.resourceDocs ++ Implementations1_2_1.resourceDocs
        case ApiVersion.v1_4_0 => Implementations1_4_0.resourceDocs ++ Implementations1_3_0.resourceDocs ++ Implementations1_2_1.resourceDocs
        case ApiVersion.v1_3_0 => Implementations1_3_0.resourceDocs ++ Implementations1_2_1.resourceDocs
        case ApiVersion.v1_2_1 => Implementations1_2_1.resourceDocs
      }

      logger.debug(s"There are ${resourceDocs.length} resource docs available to $requestedApiVersion")

      val versionRoutes = requestedApiVersion match {
        case ApiVersion.v3_0_0 => OBPAPI3_0_0.routes
        case ApiVersion.v2_2_0 => OBPAPI2_2_0.routes
        case ApiVersion.v2_1_0 => OBPAPI2_1_0.routes
        case ApiVersion.v2_0_0 => OBPAPI2_0_0.routes
        case ApiVersion.v1_4_0 => OBPAPI1_4_0.routes
        case ApiVersion.v1_3_0 => OBPAPI1_3_0.routes
        case ApiVersion.v1_2_1 => OBPAPI1_2_1.routes
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
      activePlusLocalResourceDocs ++= localResourceDocs


      logger.debug(s"There are ${activePlusLocalResourceDocs.length} resource docs (including local) available to $requestedApiVersion")

      // Sort by endpoint, verb. Thus / is shown first then /accounts and /banks etc. Seems to read quite well like that.
      Some(activePlusLocalResourceDocs.toList.sortBy(rd => (rd.requestUrl, rd.requestVerb)))
    }





    // TODO constrain version?
    // strip the leading v
    def cleanApiVersionString (version: String) : String = {
      version.stripPrefix("v").stripPrefix("V")
    }


    def stringToOptBoolean (x: String) : Option[Boolean] = x.toLowerCase match {
      case "true" | "yes" | "1" | "-1" => Some(true)
      case "false" | "no" | "0" => Some(false)
      case _ => Empty
    }


    //implicit val scalaCache  = ScalaCache(GuavaCache(underlyingGuavaCache))
    val getResourceDocsTTL : Int = 1000 * 60 * 60 * 24

    private def getResourceDocsObpCached(showCore: Option[Boolean], showPSD2: Option[Boolean], showOBWG: Option[Boolean], requestedApiVersion : ApiVersion, resourceDocTags: Option[List[ResourceDocTag]], partialFunctionNames: Option[List[String]]) : Box[JsonResponse] = {
      // cache this function with the parameters of the function
      memoizeSync (getResourceDocsTTL millisecond) {
        logger.debug(s"Generating OBP Resource Docs showCore is $showCore showPSD2 is $showPSD2 showOBWG is $showOBWG requestedApiVersion is $requestedApiVersion")
        val json = for {
          rd <- getResourceDocsList(requestedApiVersion)
        } yield {
          // Filter
          val rdFiltered = filterResourceDocs(rd, showCore, showPSD2, showOBWG, resourceDocTags, partialFunctionNames)
          // Format the data as json
          val json = JSONFactory1_4_0.createResourceDocsJson(rdFiltered)
          // Return
          successJsonResponse(Extraction.decompose(json))
        }
        json
      }
    }


    def upperName(name: String): (String, String) = (name.toUpperCase(), name)


 def getParams() : (Option[Boolean], Option[Boolean], Option[Boolean], Option[List[ResourceDocTag]], Option[List[String]] ) = {

   val showCore: Option[Boolean] = for {
     x <- S.param("core")
     y <- stringToOptBoolean(x)
   } yield y

   val showPSD2: Option[Boolean] = for {
     x <- S.param("psd2")
     y <- stringToOptBoolean(x)
   } yield y

   val showOBWG: Option[Boolean] = for {
     x <- S.param("obwg")
     y <- stringToOptBoolean(x)
   } yield y


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


   (showCore, showPSD2, showOBWG, tags, partialFunctionNames)
 }



    localResourceDocs += ResourceDoc(
      getResourceDocsObp,
      statedApiVersion,
      "getResourceDocsObp",
      "GET",
      "/resource-docs/API_VERSION/obp",
      "Get Resource Docs in OBP format.",
      """Get documentation about the RESTful resources on this server including example bodies for POST and PUT requests.
        |
        | This endpoint is used by OBP API Explorer to display and work with the API documentation.
        |
        | Most (but not all) fields are also available in swagger format.
        |
        | API_VERSION is the version you want documentation about e.g. v3.0.0
        |
        | You may query this endpoint with tags parameter e.g. ?tags=Account,Bank
        |
        |
        | You may query this endpoint with functions parameter e.g. ?functions=enableDisableConsumers,getConnectorMetrics
        |
        | For possible function values, see implemented_by.function in the JSON returned by this endpoint or the OBP source code or the footer of the API Explorer which produces a comma separated list of functions that reflect the server or filtering by API Explorer based on tags etc.
        |
        |<ul>
        |<li> operation_id is concatenation of "v", version and function and should be unique (used for DOM element IDs etc. maybe used to link to source code) </li>
        |<li> version references the version that the API call is defined in.</li>
        |<li> function is the (scala) partial function that implements this endpoint. It is unique per version of the API.</li>
        |<li> request_url is empty for the root call, else the path.</li>
        |<li> summary is a short description inline with the swagger terminology. </li>
        |<li> description may contain html markup (generated from markdown on the server).</li>
        |</ul>
      """,
      emptyObjectJson,
      emptyObjectJson,
      UnknownError :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagApi)
    )

    // Provides resource documents so that API Explorer (or other apps) can display API documentation
    // Note: description uses html markup because original markdown doesn't easily support "_" and there are multiple versions of markdown.

    def getResourceDocsObp : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
    case "resource-docs" :: requestedApiVersionString :: "obp" :: Nil JsonGet _ => {
     val (showCore, showPSD2, showOBWG, tags, partialFunctions) =  getParams()
      user => {

       for {
         requestedApiVersion <- convertToApiVersion(requestedApiVersionString) ?~! InvalidApiVersionString
         _ <- booleanToBox(versionIsAllowed(requestedApiVersion), ApiVersionNotSupported)
         json <- getResourceDocsObpCached(showCore, showPSD2, showOBWG, requestedApiVersion, tags, partialFunctions)
        }
        yield {
          json
        }
      }
      }
    }


    localResourceDocs += ResourceDoc(
      getResourceDocsSwagger,
      statedApiVersion,
      "getResourceDocsSwagger",
      "GET",
      "/resource-docs/v.2.2.0/swagger",
      "Get Resource Docs in Swagger format.",
      """Returns documentation about the RESTful resources on this server in Swagger format.
        |
        |The information returned in this endpoint is continuously being enhanced.
        |
        |API_VERSION is the version you want documentation about e.g. v3.0.0
        |
        |You may query this endpoint with tags parameter e.g. ?tags=Account,Bank
        |
        |You may query this endpoint with functions parameter e.g. ?functions=enableDisableConsumers,getConnectorMetrics
        |
        |See the Resource Doc endpoint for more information.
        |
      """,
      emptyObjectJson,
      emptyObjectJson,
      UnknownError :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagApi)
    )


    def getResourceDocsSwagger : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "resource-docs" :: requestedApiVersion :: "swagger" :: Nil JsonGet _ => {
        val (showCore, showPSD2, showOBWG, resourceDocTags, partialFunctions) =  getParams()
        user => getResourceDocsSwaggerCached(showCore, showPSD2, showOBWG, requestedApiVersion, resourceDocTags, partialFunctions)
      }
    }






/*
Filter Resource Docs based on the query parameters, else return the full list.
We don't assume a default catalog (as API Explorer does)
so the caller must specify any required filtering by catalog explicitly.
 */
def filterResourceDocs(allResources: List[ResourceDoc], showCore: Option[Boolean], showPSD2: Option[Boolean], showOBWG: Option[Boolean], resourceDocTags: Option[List[ResourceDocTag]], partialFunctionNames: Option[List[String]]) : List[ResourceDoc] = {

    // Filter (include, exclude or ignore)
    val filteredResources1 : List[ResourceDoc] = showCore match {
      case Some(true) => allResources.filter(x => x.catalogs.core == true)
      case Some(false) => allResources.filter(x => x.catalogs.core == false)
      case _ => allResources
    }

    val filteredResources2 : List[ResourceDoc] = showPSD2 match {
      case Some(true) => filteredResources1.filter(x => x.catalogs.psd2 == true)
      case Some(false) => filteredResources1.filter(x => x.catalogs.psd2 == false)
      case _ => filteredResources1
    }

    val filteredResources3 : List[ResourceDoc] = showOBWG match {
      case Some(true) => filteredResources2.filter(x => x.catalogs.obwg == true)
      case Some(false) => filteredResources2.filter(x => x.catalogs.obwg == false)
      case _ => filteredResources2
    }


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


  // Check if we have tags, and if so filter by them
  val filteredResources5: List[ResourceDoc] = partialFunctionNames match {
    // We have tags
    case Some(pfNames) => {
      // This can create duplicates to use toSet below
      for {
        rd <- filteredResources4
        partialFunctionName <- pfNames
        if rd.partialFunctionName.contains(partialFunctionName)
      } yield {
        rd
      }
    }
    // tags param was not mentioned in url or was empty, so return all
    case None => filteredResources4
  }



  val resourcesToUse = filteredResources5.toSet.toList


  logger.debug(s"allResources count is ${allResources.length}")
  logger.debug(s"filteredResources1 count is ${filteredResources1.length}")
  logger.debug(s"filteredResources2 count is ${filteredResources2.length}")
  logger.debug(s"filteredResources3 count is ${filteredResources3.length}")
  logger.debug(s"filteredResources4 count is ${filteredResources4.length}")
  logger.debug(s"filteredResources5 count is ${filteredResources5.length}")
  logger.debug(s"resourcesToUse count is ${resourcesToUse.length}")


  if (filteredResources4.length > 0 && resourcesToUse.length == 0) {
    logger.info("tags filter reduced the list of resource docs to zero")
  }





  resourcesToUse
}




    private def getResourceDocsSwaggerCached(showCore: Option[Boolean], showPSD2: Option[Boolean], showOBWG: Option[Boolean], requestedApiVersionString : String, resourceDocTags: Option[List[ResourceDocTag]], partialFunctionNames: Option[List[String]]) : Box[JsonResponse] = {
      // cache this function with the parameters of the function
      memoizeSync (getResourceDocsTTL millisecond) {
        logger.debug(s"Generating Swagger showCore is $showCore showPSD2 is $showPSD2 showOBWG is $showOBWG requestedApiVersion is $requestedApiVersionString")
        val jsonOut = for {
            requestedApiVersion <- convertToApiVersion(requestedApiVersionString) ?~! InvalidApiVersionString
            _ <- booleanToBox(versionIsAllowed(requestedApiVersion), ApiVersionNotSupported)
          rd <- getResourceDocsList(requestedApiVersion)
        } yield {
          // Filter
          val rdFiltered = filterResourceDocs(rd, showCore, showPSD2, showOBWG, resourceDocTags, partialFunctionNames)
          // Format the data as json
          val json = SwaggerJSONFactory.createSwaggerResourceDoc(rdFiltered, requestedApiVersion)
          //Get definitions of objects of success responses
          val jsonAST = SwaggerJSONFactory.loadDefinitions(rdFiltered)
          // Merge both results and return
          successJsonResponse(Extraction.decompose(json) merge jsonAST)
        }
        jsonOut
      }
    }






    if (Props.devMode) {
      localResourceDocs += ResourceDoc(
        dummy(statedApiVersion, "DUMMY"),
        statedApiVersion,
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
        Catalogs(notCore, notPSD2, notOBWG),
        List(apiTagApi))
    }



    def dummy(apiVersion : String, apiVersionStatus: String) : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "dummy" :: Nil JsonGet json => {
        user =>
          val apiDetails: JValue = {
            val hostedBy = new HostedBy("Dummy Org", "contact@example.com", "12345")
            val apiInfoJSON = new APIInfoJSON(apiVersion, apiVersionStatus, gitCommit, "dummy-connector", hostedBy, Akka(APIUtil.akkaSanityCheck()))
            Extraction.decompose(apiInfoJSON)
          }

          Full(successJsonResponse(apiDetails, 200))
      }
    }

  }



}


