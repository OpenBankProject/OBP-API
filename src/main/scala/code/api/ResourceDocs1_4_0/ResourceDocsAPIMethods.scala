package code.api.ResourceDocs1_4_0

import code.api.v1_4_0.{APIMethods140, JSONFactory1_4_0, OBPAPI1_4_0}
import code.api.v2_2_0.{APIMethods220, OBPAPI2_2_0}
import net.liftweb.common.{Box, Empty, Full, Loggable}
import net.liftweb.http.rest.RestHelper
import net.liftweb.http.{JsonResponse, Req, S}
import net.liftweb.json._
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.JsonDSL._
import net.liftweb.util.Props

import scala.collection.immutable.Nil

// JObject creation
import code.api.v1_2_1.{APIInfoJSON, APIMethods121, HostedBy, OBPAPI1_2_1}
import code.api.v1_3_0.{APIMethods130, OBPAPI1_3_0}
import code.api.v2_0_0.{APIMethods200, OBPAPI2_0_0}
import code.api.v2_1_0.{APIMethods210, OBPAPI2_1_0}

import scala.collection.mutable.ArrayBuffer

// So we can include resource docs from future versions
import java.text.SimpleDateFormat

import code.api.util.APIUtil.{ResourceDoc, _}
import code.model._

trait ResourceDocsAPIMethods extends Loggable with APIMethods220 with APIMethods210 with APIMethods200 with APIMethods140 with APIMethods130 with APIMethods121{
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  // We add previous APIMethods so we have access to the Resource Docs
  self: RestHelper =>

  val ImplementationsResourceDocs = new Object() {

    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val emptyObjectJson : JValue = Nil
    val apiVersion : String = "1_4_0"

    val exampleDateString : String ="22/08/2013"
    val simpleDateFormat : SimpleDateFormat = new SimpleDateFormat("dd/mm/yyyy")
    val exampleDate = simpleDateFormat.parse(exampleDateString)


    def getResourceDocsList(requestedApiVersion : String) : Option[List[ResourceDoc]] =
    {
      // Return a different list of resource docs depending on the version being called.
      // For instance 1_3_0 will have the docs for 1_3_0 and 1_2_1 (when we started adding resource docs) etc.

      logger.info(s"getResourceDocsList says requestedApiVersion is $requestedApiVersion")

      val resourceDocs = requestedApiVersion match {
        case "2.2.0" => Implementations2_2_0.resourceDocs ++ Implementations2_1_0.resourceDocs ++ Implementations2_0_0.resourceDocs ++ Implementations1_4_0.resourceDocs ++ Implementations1_3_0.resourceDocs ++ Implementations1_2_1.resourceDocs
        case "2.1.0" => Implementations2_1_0.resourceDocs ++ Implementations2_0_0.resourceDocs ++ Implementations1_4_0.resourceDocs ++ Implementations1_3_0.resourceDocs ++ Implementations1_2_1.resourceDocs
        case "2.0.0" => Implementations2_0_0.resourceDocs ++ Implementations1_4_0.resourceDocs ++ Implementations1_3_0.resourceDocs ++ Implementations1_2_1.resourceDocs
        case "1.4.0" => Implementations1_4_0.resourceDocs ++ Implementations1_3_0.resourceDocs ++ Implementations1_2_1.resourceDocs
        case "1.3.0" => Implementations1_3_0.resourceDocs ++ Implementations1_2_1.resourceDocs
        case "1.2.1" => Implementations1_2_1.resourceDocs
      }

      logger.info(s"There are ${resourceDocs.length} resource docs available to $requestedApiVersion")

      val versionRoutes = requestedApiVersion match {
        case "2.2.0" => OBPAPI2_2_0.routes
        case "2.1.0" => OBPAPI2_1_0.routes
        case "2.0.0" => OBPAPI2_0_0.routes
        case "1.4.0" => OBPAPI1_4_0.routes
        case "1.3.0" => OBPAPI1_3_0.routes
        case "1.2.1" => OBPAPI1_2_1.routes
      }

      logger.info(s"There are ${versionRoutes.length} routes available to $requestedApiVersion")


      // We only want the resource docs for which a API route exists else users will see 404s
      // Get a list of the partial function classes represented in the routes available to this version.
      val versionRoutesClasses = versionRoutes.map { vr => vr.getClass }

      // Only return the resource docs that have available routes
      val activeResourceDocs = resourceDocs.filter(rd => versionRoutesClasses.contains(rd.partialFunction.getClass))

      logger.info(s"There are ${activeResourceDocs.length} resource docs available to $requestedApiVersion")

      // Sort by endpoint, verb. Thus / is shown first then /accounts and /banks etc. Seems to read quite well like that.
      Some(activeResourceDocs.toList.sortBy(rd => (rd.requestUrl, rd.requestVerb)))
    }



    resourceDocs += ResourceDoc(
      getResourceDocsObp,
      apiVersion,
      "getResourceDocsObp",
      "GET",
      "/resource-docs/obp",
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
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagApiInfo)
    )

    // Provides resource documents so that API Explorer (or other apps) can display API documentation
    // Note: description uses html markup because original markdown doesn't easily support "_" and there are multiple versions of markdown.


    // TODO constrain version?
    // strip the leading v
    def cleanApiVersionString (version: String) : String = {version.stripPrefix("v").stripPrefix("V")}

    // Todo add query parameters



    // /api/item/search/foo or /api/item/search?q=foo
//    case "search" :: q JsonGet _ =>
//    (for {
//      searchString <- q ::: S.params("q")
//      item <- Item.search(searchString)
//    } yield item).distinct: JValue



    def getResourceDocsObp : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "resource-docs" :: requestedApiVersion :: "obp" :: Nil JsonGet _ => {
        user => {
          for {
            rd <- getResourceDocsList(cleanApiVersionString(requestedApiVersion))
          } yield {
            // Filter
            val rdFiltered = filterResourceDocs(rd)
            // Format the data as json
            val json = JSONFactory1_4_0.createResourceDocsJson(rdFiltered)
            // Return
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }


    resourceDocs += ResourceDoc(
      getResourceDocsSwagger,
      apiVersion,
      "getResourceDocsSwagger",
      "GET",
      "/resource-docs/swagger",
      "Get Resource Documentation in Swagger format. Work In Progress!",
      """Returns documentation about the RESTful resources on this server in Swagger format.
        | Currently this is incomplete.
      """,
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagApiInfo)
    )





/*
Filter Resource Docs based on the query parameters, else return the full list.
We don't assume a default catalog (as API Explorer does)
so the caller must specify any required filtering by catalog explicitly.
 */
def filterResourceDocs(allResources: List[ResourceDoc]) : List[ResourceDoc] = {

    def stringToOptBoolean (x: String) : Option[Boolean] = x.toLowerCase match {
      case "true" | "yes" | "1" | "-1" => Some(true)
      case "false" | "no" | "0" => Some(false)
      case _ => Empty
    }

    val showCoreParam: Option[Boolean] = for {
      x <- S.param("core")
      y <- stringToOptBoolean(x)
    } yield y

    logger.info(s"showCore is $showCoreParam")

    val showPSD2Param: Option[Boolean] = for {
      x <- S.param("psd2")
      y <- stringToOptBoolean(x)
    } yield y

    logger.info(s"showPSD2 is $showPSD2Param")

    val showOBWGParam: Option[Boolean] = for {
      x <- S.param("obwg")
      y <- stringToOptBoolean(x)
    } yield y

    logger.info(s"showOBWG is $showOBWGParam")


    val showCore = showCoreParam
    val showOBWG = showOBWGParam
    val showPSD2 = showPSD2Param

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


    def getResourceDocsSwagger : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "resource-docs" :: requestedApiVersion :: "swagger" :: Nil JsonGet _ => {
        user => {
          for {
            rd <- getResourceDocsList(cleanApiVersionString(requestedApiVersion))
          } yield {
            // Filter
            val rdFiltered = filterResourceDocs(rd)
            // Format the data as json
            val json = SwaggerJSONFactory.createSwaggerResourceDoc(rdFiltered, requestedApiVersion)
            //Get definitions of objects of success responses
            val jsonAST = SwaggerJSONFactory.loadDefinitions(rdFiltered)
            // Merge both results and return
            successJsonResponse(Extraction.decompose(json) merge jsonAST)
          }
        }
      }
    }






    if (Props.devMode) {
      resourceDocs += ResourceDoc(
        dummy(apiVersion, "DUMMY"),
        apiVersion,
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
        emptyObjectJson :: Nil,
        Catalogs(notCore, notPSD2, notOBWG),
        List(apiTagApiInfo))
    }



    def dummy(apiVersion : String, apiVersionStatus: String) : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "dummy" :: Nil JsonGet json => {
        user =>
          val apiDetails: JValue = {
            val hostedBy = new HostedBy("TESOBE", "contact@tesobe.com", "+49 (0)30 8145 3994")
            val apiInfoJSON = new APIInfoJSON(apiVersion, apiVersionStatus, gitCommit, hostedBy)
            Extraction.decompose(apiInfoJSON)
          }

          Full(successJsonResponse(apiDetails, 200))
      }
    }

  }



}


