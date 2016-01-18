package code.api.ResourceDocsRD1

import code.api.v1_4_0.{APIMethods140, JSONFactory1_4_0, OBPAPI1_4_0}
import net.liftweb.common.{Box, Full, Loggable}
import net.liftweb.http.rest.RestHelper
import net.liftweb.http.{JsonResponse, Req}
import net.liftweb.json.Extraction
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.JsonDSL._
import net.liftweb.util.Props

import scala.collection.immutable.Nil

// JObject creation
import code.api.v1_2_1.{APIInfoJSON, APIMethods121, HostedBy, OBPAPI1_2_1}
import code.api.v1_3_0.{APIMethods130, OBPAPI1_3_0}
import code.api.v2_0_0.{APIMethods200, OBPAPI2_0_0}

import scala.collection.mutable.ArrayBuffer

// So we can include resource docs from future versions
import java.text.SimpleDateFormat

import code.api.util.APIUtil.{ResourceDoc, _}
import code.model._

trait ResourceDocsAPIMethods extends Loggable with APIMethods200 with APIMethods140 with APIMethods130 with APIMethods121{
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

      val resourceDocs2_0_0 = Implementations2_0_0.resourceDocs ++ resourceDocs ++ Implementations1_3_0.resourceDocs ++ Implementations1_2_1.resourceDocs

      // When we add a new version, update this.
      val resourceDocsAll = resourceDocs2_0_0

      val cumulativeResourceDocs =  requestedApiVersion match {
        case "2.0.0" => resourceDocs2_0_0
        case "1.4.0" => resourceDocs ++ Implementations1_3_0.resourceDocs ++ Implementations1_2_1.resourceDocs
        case "1.3.0" => Implementations1_3_0.resourceDocs ++ Implementations1_2_1.resourceDocs
        case "1.2.1" => Implementations1_2_1.resourceDocs
        case _ => resourceDocsAll
      }

      // Only return APIs that are present in the list of routes for the version called
      val liveResourceDocs =  requestedApiVersion match {
        case "2.0.0" => cumulativeResourceDocs.filter(rd => OBPAPI2_0_0.routes.contains(rd.partialFunction))
        case "1.4.0" => cumulativeResourceDocs.filter(rd => OBPAPI1_4_0.routes.contains(rd.partialFunction))
        case "1.3.0" => cumulativeResourceDocs.filter(rd => OBPAPI1_3_0.routes.contains(rd.partialFunction))
        case "1.2.1" => cumulativeResourceDocs.filter(rd => OBPAPI1_2_1.routes.contains(rd.partialFunction))
        case _ => cumulativeResourceDocs // Will be all ResourceDocs across all versions
      }

      // Sort by endpoint, verb. Thus / is shown first then /accounts and /banks etc. Seems to read quite well like that.
      Some(cumulativeResourceDocs.toList.sortBy(rd => (rd.requestUrl, rd.requestVerb)))
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
        |<li> description can contain html markup.</li>
        |</ul>
      """,
      emptyObjectJson,
      emptyObjectJson,
      emptyObjectJson :: Nil
    )

    // Provides resource documents so that API Explorer (or other apps) can display API documentation
    // Note: description uses html markup because original markdown doesn't easily support "_" and there are multiple versions of markdown.
    def getResourceDocsObp : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "resource-docs" :: requestedApiVersion :: "obp" :: Nil JsonGet _ => {
        user => {
          for {
            rd <- getResourceDocsList(requestedApiVersion)
          } yield {
            // Format the data as json
            val json = JSONFactory1_4_0.createResourceDocsJson(rd)
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
      emptyObjectJson :: Nil
    )

    def getResourceDocsSwagger : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "resource-docs" :: requestedApiVersion :: "swagger" :: Nil JsonGet _ => {
        user => {
          for {
            rd <- getResourceDocsList(requestedApiVersion)
          } yield {
            // Format the data as json
            val json = SwaggerJSONFactory.createSwaggerResourceDoc(rd)
            // Return
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }






    if (Props.devMode) {
      resourceDocs += ResourceDoc(
        dummy(apiVersion),
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
        emptyObjectJson :: Nil)
    }



    def dummy(apiVersion : String) : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "dummy" :: Nil JsonGet json => {
        user =>
          val apiDetails: JValue = {
            val hostedBy = new HostedBy("TESOBE", "contact@tesobe.com", "+49 (0)30 8145 3994")
            val apiInfoJSON = new APIInfoJSON(apiVersion, gitCommit, hostedBy)
            Extraction.decompose(apiInfoJSON)
          }

          Full(successJsonResponse(apiDetails, 200))
      }
    }

  }



}


