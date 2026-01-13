package code.api.v7_0_0

import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.Constant._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.ResourceDocs1_4_0.{ResourceDocs140, ResourceDocsAPIMethodsUtil}
import code.api.util.APIUtil.{EmptyBody, _}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.{ApiVersionUtils, CustomJsonFormats, NewStyle}
import code.api.v1_4_0.JSONFactory1_4_0
import code.api.v4_0_0.JSONFactory400
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus, ScannedApiVersion}
import net.liftweb.json.JsonAST.prettyRender
import net.liftweb.json.{Extraction, Formats}
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers._
import org.typelevel.vault.Key

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

  case class CallContext(userId: String, requestId: String)
  val callContextKey: Key[CallContext] =
    Key.newKey[IO, CallContext].unsafeRunSync()(cats.effect.unsafe.IORuntime.global)

  object CallContextMiddleware {

    def withCallContext(routes: HttpRoutes[IO]): HttpRoutes[IO] =
      Kleisli[HttpF, Request[IO], Response[IO]] { req: Request[IO] =>
        val callContext = CallContext(userId = "example-user", requestId = java.util.UUID.randomUUID().toString)
        val updatedAttributes = req.attributes.insert(callContextKey, callContext)
        val updatedReq = req.withAttributes(updatedAttributes)
        routes(updatedReq)
      }
  }

  object Implementations7_0_0 {

    // Common prefix: /obp/v7.0.0
    val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString
    private val jsonContentType: `Content-Type` = `Content-Type`(MediaType.application.json)


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
        |${userAuthenticationMessage(false)}""",
      EmptyBody,
      apiInfoJSON,
      List(UnknownError, "no connector set"),
      apiTagApi :: Nil,
      http4sPartialFunction = Some(root)
    )

    // Route: GET /obp/v7.0.0/root
    val root: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "root" =>
        val callContext = req.attributes.lookup(callContextKey).get.asInstanceOf[CallContext]
        Ok(IO.fromFuture(IO(
          for {
            _ <- Future() // Just start async call
          } yield {
            convertAnyToJsonString(
              JSONFactory700.getApiInfoJSON(implementedInApiVersion, s"Hello, ${callContext.userId}! Your request ID is ${callContext.requestId}.")
            )
          }
        ))).map(_.withContentType(jsonContentType))
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
        |* Website
        |${userAuthenticationMessage(false)}""",
      EmptyBody,
      banksJSON,
      List(UnknownError),
      apiTagBank :: Nil,
      http4sPartialFunction = Some(getBanks)
    )

    // Route: GET /obp/v7.0.0/banks
    val getBanks: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" =>
        import com.openbankproject.commons.ExecutionContext.Implicits.global
        Ok(IO.fromFuture(IO(
          for {
            (banks, callContext) <- NewStyle.function.getBanks(None)
          } yield {
            convertAnyToJsonString(JSONFactory400.createBanksJson(banks))
          }
        ))).map(_.withContentType(jsonContentType))
    }

    val getResourceDocsObpV700: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "resource-docs" / requestedApiVersionString / "obp" =>
        import com.openbankproject.commons.ExecutionContext.Implicits.global
        val logic = for {
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
        Ok(IO.fromFuture(IO(logic))).map(_.withContentType(jsonContentType))
    }

    // All routes combined
    val allRoutes: HttpRoutes[IO] =
      Kleisli[HttpF, Request[IO], Response[IO]] { req: Request[IO] =>
        root(req).orElse(getBanks(req)).orElse(getResourceDocsObpV700(req))
      }
  }

  val wrappedRoutesV700Services: HttpRoutes[IO] = CallContextMiddleware.withCallContext(Implementations7_0_0.allRoutes)
}
