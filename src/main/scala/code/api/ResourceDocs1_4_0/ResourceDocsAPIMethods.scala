package code.api.ResourceDocs1_4_0

import code.api.util.APIUtil
import code.api.util.APIUtil.ApiVersion
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

import code.api.util.APIUtil.{ResourceDoc, _}
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

    val resourceDocs = ArrayBuffer[ResourceDoc]()
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

      // Sort by endpoint, verb. Thus / is shown first then /accounts and /banks etc. Seems to read quite well like that.
      Some(activeResourceDocs.toList.sortBy(rd => (rd.requestUrl, rd.requestVerb)))
    }



    resourceDocs += ResourceDoc(
      getResourceDocsObp,
      statedApiVersion,
      "getResourceDocsObp",
      "GET",
      "/resource-docs/API_VERSION/obp",
      "Get Resource Documentation in OBP format.",
      """Returns documentation about the RESTful resources on this server including example body for POST or PUT requests.
        | Thus the OBP API Explorer (and other apps) can display and work with the API documentation.
        | In the future this information will be used to create Swagger (WIP) and RAML files.
        |<ul>
        |<li> operation_id is concatenation of version and function and should be unque (the aim of this is to allow links to code) </li>
        |<li> version references the version that the API call is defined in.</li>
        |<li> function is the (scala) function.</li>
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

    private def getResourceDocsObpCached(showCore: Option[Boolean], showPSD2: Option[Boolean], showOBWG: Option[Boolean], requestedApiVersion : ApiVersion) : Box[JsonResponse] = {
      // cache this function with the parameters of the function
      memoizeSync (getResourceDocsTTL millisecond) {
        logger.debug(s"Generating OBP Resource Docs showCore is $showCore showPSD2 is $showPSD2 showOBWG is $showOBWG requestedApiVersion is $requestedApiVersion")
        val json = for {
          rd <- getResourceDocsList(requestedApiVersion)
        } yield {
          // Filter
          val rdFiltered = filterResourceDocs(showCore, showPSD2, showOBWG, rd)
          // Format the data as json
          val json = JSONFactory1_4_0.createResourceDocsJson(rdFiltered)
          // Return
          successJsonResponse(Extraction.decompose(json))
        }
        json
      }
    }


    def upperName(name: String): (String, String) = (name.toUpperCase(), name)


 def getParams() : (Option[Boolean], Option[Boolean], Option[Boolean] ) = {

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


   (showCore, showPSD2, showOBWG)

 }

  def getResourceDocsObp : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
    case "resource-docs" :: requestedApiVersionString :: "obp" :: Nil JsonGet _ => {
     val (showCore, showPSD2, showOBWG) =  getParams()
      user => {

       for {
         requestedApiVersion <- convertToApiVersion(requestedApiVersionString) ?~! InvalidApiVersionString
         _ <- booleanToBox(versionIsAllowed(requestedApiVersion), ApiVersionNotSupported)
         json <- getResourceDocsObpCached(showCore, showPSD2, showOBWG, requestedApiVersion)
        }
        yield {
          json
        }
      }
      }
    }

/*
Filter Resource Docs based on the query parameters, else return the full list.
We don't assume a default catalog (as API Explorer does)
so the caller must specify any required filtering by catalog explicitly.
 */
def filterResourceDocs(showCore: Option[Boolean], showPSD2: Option[Boolean], showOBWG: Option[Boolean], allResources: List[ResourceDoc]) : List[ResourceDoc] = {

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

    filteredResources3
}

    resourceDocs += ResourceDoc(
      getResourceDocsSwagger,
      statedApiVersion,
      "getResourceDocsSwagger",
      "GET",
      "/resource-docs/v.2.2.0/swagger",
      "Get Resource Documentation in Swagger format. Work In Progress!",
      """Returns documentation about the RESTful resources on this server in Swagger format.
        | Currently this is incomplete.
      """,
      emptyObjectJson,
      emptyObjectJson,
      UnknownError :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagApi)
    )


    private def getResourceDocsSwaggerCached(showCore: Option[Boolean], showPSD2: Option[Boolean], showOBWG: Option[Boolean], requestedApiVersionString : String) : Box[JsonResponse] = {
      // cache this function with the parameters of the function
      memoizeSync (getResourceDocsTTL millisecond) {
        logger.debug(s"Generating Swagger showCore is $showCore showPSD2 is $showPSD2 showOBWG is $showOBWG requestedApiVersion is $requestedApiVersionString")
        val jsonOut = for {
            requestedApiVersion <- convertToApiVersion(requestedApiVersionString) ?~! InvalidApiVersionString
            _ <- booleanToBox(versionIsAllowed(requestedApiVersion), ApiVersionNotSupported)
          rd <- getResourceDocsList(requestedApiVersion)
        } yield {
          // Filter
          val rdFiltered = filterResourceDocs(showCore, showPSD2, showOBWG, rd)
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


    def getResourceDocsSwagger : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "resource-docs" :: requestedApiVersion :: "swagger" :: Nil JsonGet _ => {
        val (showCore, showPSD2, showOBWG) =  getParams()
        user => getResourceDocsSwaggerCached(showCore, showPSD2, showOBWG, requestedApiVersion)
      }
    }



    if (Props.devMode) {
      resourceDocs += ResourceDoc(
        dummy(statedApiVersion, "DUMMY"),
        statedApiVersion,
        "testResourceDoc",
        "GET",
        "/dummy",
        "I am only a test resource Doc",
        """
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


