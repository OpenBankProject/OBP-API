package code.api.v7_0_0

import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.Constant._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.ResourceDocs1_4_0.{ResourceDocs140, ResourceDocsAPIMethodsUtil}
import code.api.util.APIUtil.{EmptyBody, _}
import code.api.util.ApiRole.{canGetCardsForBank, canReadResourceDoc}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.http4s.{Http4sRequestAttributes, ResourceDocMiddleware}
import code.api.util.http4s.Http4sRequestAttributes.RequestOps
import code.api.util.{ApiRole, ApiVersionUtils, CallContext, CustomJsonFormats, NewStyle}
import code.api.v1_3_0.JSONFactory1_3_0
import code.api.v1_4_0.JSONFactory1_4_0
import code.api.v4_0_0.JSONFactory400
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus, ScannedApiVersion}
import net.liftweb.json.JsonAST.prettyRender
import net.liftweb.json.{Extraction, Formats}
import org.http4s._
import org.http4s.dsl.io._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.language.{higherKinds, implicitConversions}

object Http4s700 {

  type HttpF[A] = OptionT[IO, A]

  implicit val formats: Formats = CustomJsonFormats.formats
  implicit def convertAnyToJsonString(any: Any): String = prettyRender(Extraction.decompose(any))

  val implementedInApiVersion: ScannedApiVersion = ApiVersion.v7_0_0
  val versionStatus = ApiVersionStatus.STABLE.toString
  val resourceDocs = ArrayBuffer[ResourceDoc]()

  object Implementations7_0_0 {

    // Common prefix: /obp/v7.0.0
    val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

    // ResourceDoc with AuthenticatedUserIsRequired in errorResponseBodies indicates auth is required
    // ResourceDocMiddleware will automatically handle authentication based on this metadata
    // No explicit auth code needed in the endpoint handler - just like Lift's wrappedWithAuthCheck
    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(root),
      "GET",
      "/root",
      "Get API Info (root)",
      s"""Returns information about:
        |
        |* API version
        |* Hosted by information
        |* Git Commit
        """,
      EmptyBody,
      apiInfoJSON, 
      List(
        UnknownError
      ),
      apiTagApi :: Nil,
      http4sPartialFunction = Some(root)
    )

    // Route: GET /obp/v7.0.0/root
    // Authentication is handled automatically by ResourceDocMiddleware based on AuthenticatedUserIsRequired in ResourceDoc
    // The endpoint code only contains business logic - validated User is available from request attributes
    val root: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "root" =>
        val responseJson = convertAnyToJsonString(
          JSONFactory700.getApiInfoJSON(implementedInApiVersion, versionStatus)
        )
        Ok(responseJson)
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getBanks),
      "GET",
      "/banks",
      "Get Banks",
      s"""Get banks on this API instance
        |Returns a list of banks supported on this server:
        |
        |* ID used as parameter in URLs
        |* Short and full name of bank
        |* Logo URL
        |* Website""",
      EmptyBody,
      banksJSON,
      List(
        UnknownError
      ),
      apiTagBank :: Nil,
      http4sPartialFunction = Some(getBanks)
    )

    // Route: GET /obp/v7.0.0/banks
    val getBanks: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" =>
        implicit val cc: CallContext = req.callContext
        for {
          result <- IO.fromFuture(IO {
            for {
              (banks, callContext) <- NewStyle.function.getBanks(Some(cc))
            } yield convertAnyToJsonString(JSONFactory400.createBanksJson(banks))
          })
          response <- Ok(result)
        } yield response
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getCards),
      "GET",
      "/cards",
      "Get cards for the current user",
      "Returns data about all the physical cards a user has been issued. These could be debit cards, credit cards, etc.",
      EmptyBody,
      physicalCardsJSON,
      List(AuthenticatedUserIsRequired, UnknownError),
      apiTagCard :: Nil,
      http4sPartialFunction = Some(getCards)
    )

    // Route: GET /obp/v7.0.0/cards
    // Authentication handled by ResourceDocMiddleware based on AuthenticatedUserIsRequired
    val getCards: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "cards" =>
        implicit val cc: CallContext = req.callContext
        for {
          user <- IO.fromOption(cc.user.toOption)(new RuntimeException("User not found in CallContext"))
          result <- IO.fromFuture(IO {
            for {
              (cards, callContext) <- NewStyle.function.getPhysicalCardsForUser(user, Some(cc))
            } yield convertAnyToJsonString(JSONFactory1_3_0.createPhysicalCardsJSON(cards, user))
          })
          response <- Ok(result)
        } yield response
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getCardsForBank),
      "GET",
      "/banks/BANK_ID/cards",
      "Get cards for the specified bank",
      "",
      EmptyBody,
      physicalCardsJSON,
      List(AuthenticatedUserIsRequired, BankNotFound, UnknownError),
      apiTagCard :: Nil,
      Some(List(canGetCardsForBank)),
      http4sPartialFunction = Some(getCardsForBank)
    )

    // Route: GET /obp/v7.0.0/banks/BANK_ID/cards
    // Authentication and bank validation handled by ResourceDocMiddleware
    val getCardsForBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankId / "cards" =>
        implicit val cc: CallContext = req.callContext
        for {
          user <- IO.fromOption(cc.user.toOption)(new RuntimeException("User not found in CallContext"))
          bank <- IO.fromOption(cc.bank)(new RuntimeException("Bank not found in CallContext"))
          result <- IO.fromFuture(IO {
            for {
              httpParams <- NewStyle.function.extractHttpParamsFromUrl(req.uri.renderString)
              (obpQueryParams, callContext) <- createQueriesByHttpParamsFuture(httpParams, Some(cc))
              (cards, callContext) <- NewStyle.function.getPhysicalCardsForBank(bank, user, obpQueryParams, callContext)
            } yield convertAnyToJsonString(JSONFactory1_3_0.createPhysicalCardsJSON(cards, user))
          })
          response <- Ok(result)
        } yield response
    }
 
    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getResourceDocsObpV700),
      "GET",
      "/resource-docs/API_VERSION/obp",
      "Get Resource Docs",
      s"""Get documentation about the RESTful resources on this server including example body payloads.
        |
        |* API_VERSION: The version of the API for which you want documentation
        |
        |Returns JSON containing information about the endpoints including:
        |* Method (GET, POST, etc.)
        |* URL path
        |* Summary and description
        |* Example request and response bodies
        |* Required roles and permissions
        |
        |Optional query parameters:
        |* tags - filter by API tags
        |* functions - filter by function names
        |* locale - specify language for descriptions
        |* content - filter by content type""",
      EmptyBody,
      EmptyBody,
      List(
        UnknownError
      ),
      List(apiTagDocumentation, apiTagApi),
      http4sPartialFunction = Some(getResourceDocsObpV700)
    )

    val getResourceDocsObpV700: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "resource-docs" / requestedApiVersionString / "obp" =>
        implicit val cc: CallContext = req.callContext
        for {
          result <- IO.fromFuture(IO {
            // Check resource_docs_requires_role property
            val resourceDocsRequireRole = getPropsAsBoolValue("resource_docs_requires_role", false)
              
              for {
                // Authentication based on property
                (boxUser, cc1) <- if (resourceDocsRequireRole) 
                  authenticatedAccess(cc)
                else 
                  anonymousAccess(cc)
                
                // Role check based on property
                _ <- if (resourceDocsRequireRole) {
                  NewStyle.function.hasAtLeastOneEntitlement(
                    failMsg = UserHasMissingRoles + canReadResourceDoc.toString
                  )("", boxUser.map(_.userId).getOrElse(""), ApiRole.canReadResourceDoc :: Nil, cc1)
                } else {
                  Future.successful(())
                }
                
                httpParams <- NewStyle.function.extractHttpParamsFromUrl(req.uri.renderString)
                tagsParam = httpParams.filter(_.name == "tags").map(_.values).headOption
                functionsParam = httpParams.filter(_.name == "functions").map(_.values).headOption
                localeParam = httpParams.filter(param => param.name == "locale" || param.name == "language").map(_.values).flatten.headOption
                contentParam = httpParams.filter(_.name == "content").map(_.values).flatten.flatMap(ResourceDocsAPIMethodsUtil.stringToContentParam).headOption
                apiCollectionIdParam = httpParams.filter(_.name == "api-collection-id").map(_.values).flatten.headOption
                tags = tagsParam.map(_.map(ResourceDocTag(_)))
                functions = functionsParam.map(_.toList)
                requestedApiVersion <- Future(ApiVersionUtils.valueOf(requestedApiVersionString))
                resourceDocs = ResourceDocs140.ImplementationsResourceDocs.getResourceDocsList(requestedApiVersion).getOrElse(Nil)
                filteredDocs = ResourceDocsAPIMethodsUtil.filterResourceDocs(resourceDocs, tags, functions)
                resourceDocsJson = JSONFactory1_4_0.createResourceDocsJson(filteredDocs, isVersion4OrHigher = true, localeParam)
              } yield convertAnyToJsonString(resourceDocsJson)
            })
            response <- Ok(result)
          } yield response
    }


    // All routes combined (without middleware - for direct use)
    val allRoutes: HttpRoutes[IO] =
      Kleisli[HttpF, Request[IO], Response[IO]] { req: Request[IO] =>
        root(req)
          .orElse(getBanks(req))
          .orElse(getCards(req))
          .orElse(getCardsForBank(req))
          .orElse(getResourceDocsObpV700(req))
      }
    
    // Routes wrapped with ResourceDocMiddleware for automatic validation
    val allRoutesWithMiddleware: HttpRoutes[IO] = 
      ResourceDocMiddleware.apply(resourceDocs)(allRoutes)
  }

  // Routes with ResourceDocMiddleware - provides automatic validation based on ResourceDoc metadata
  // Authentication is automatic based on $AuthenticatedUserIsRequired in ResourceDoc errorResponseBodies
  // This matches Lift's wrappedWithAuthCheck behavior
  val wrappedRoutesV700Services: HttpRoutes[IO] = Implementations7_0_0.allRoutesWithMiddleware
}
