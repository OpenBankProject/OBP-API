package code.api.v1_3_0

import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.Constant._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil.{EmptyBody, ResourceDoc, _}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.http4s.Http4sRequestAttributes.{EndpointHelpers, RequestOps}
import code.api.util.http4s.ResourceDocMiddleware
import code.api.util.NewStyle
import code.api.v1_2_1.JSONFactory
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus, ScannedApiVersion}
import org.http4s._
import org.http4s.dsl.io._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

object Http4s130 {
  val implementedInApiVersion: ScannedApiVersion = ApiVersion.v1_3_0
  val versionStatus: String                       = ApiVersionStatus.DEPRECATED.toString
  val resourceDocs: ArrayBuffer[ResourceDoc]       = ArrayBuffer[ResourceDoc]()

  import code.api.util.ApiRole._

  type HttpF[A] = OptionT[IO, A]

  object Implementations1_3_0 {
    val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

    // ─── root ────────────────────────────────────────────────────────────────

    val root: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` =>
        EndpointHelpers.executeAndRespond(req) { _ =>
          Future.successful(JSONFactory.getApiInfoJSON(ApiVersion.v1_3_0, versionStatus))
        }
      case req @ GET -> `prefixPath` / "root" =>
        EndpointHelpers.executeAndRespond(req) { _ =>
          Future.successful(JSONFactory.getApiInfoJSON(ApiVersion.v1_3_0, versionStatus))
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(root),
      "GET",
      "/root",
      "Get API Info (root)",
      """Returns information about:
        |
        |* API version
        |* Hosted by information
        |* Git Commit""",
      EmptyBody,
      apiInfoJSON,
      List(UnknownError, MandatoryPropertyIsNotSet),
      apiTagApi :: Nil,
      None,
      http4sPartialFunction = Some(root)
    )

    // ─── getCards ────────────────────────────────────────────────────────────

    val getCards: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "cards" =>
        EndpointHelpers.withUser(req) { (u, cc) =>
          NewStyle.function.getPhysicalCardsForUser(u, Some(cc)).map {
            case (cards, _) => JSONFactory1_3_0.createPhysicalCardsJSON(cards, u)
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getCards),
      "GET",
      "/cards",
      "Get cards for the current user",
      "Returns data about all the physical cards a user has been issued. These could be debit cards, credit cards, etc.",
      EmptyBody,
      physicalCardsJSON,
      List(AuthenticatedUserIsRequired, UnknownError),
      List(apiTagCard),
      None,
      http4sPartialFunction = Some(getCards)
    )

    // ─── getCardsForBank ─────────────────────────────────────────────────────

    val getCardsForBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "cards" =>
        EndpointHelpers.withUserAndBank(req) { (u, bank, cc) =>
          for {
            httpParams            <- NewStyle.function.extractHttpParamsFromUrl(cc.url)
            (obpQueryParams, cc2) <- createQueriesByHttpParamsFuture(httpParams, Some(cc))
            (cards, _)            <- NewStyle.function.getPhysicalCardsForBank(bank, u, obpQueryParams, cc2)
          } yield JSONFactory1_3_0.createPhysicalCardsJSON(cards, u)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getCardsForBank),
      "GET",
      "/banks/BANK_ID/cards",
      "Get cards for the specified bank",
      "",
      EmptyBody,
      physicalCardsJSON,
      List(AuthenticatedUserIsRequired, BankNotFound, UnknownError),
      List(apiTagCard),
      Some(List(canGetCardsForBank)),
      http4sPartialFunction = Some(getCardsForBank)
    )

    // ─── allRoutes ───────────────────────────────────────────────────────────

    private val allOwnRoutes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
      root.run(req)
        .orElse(getCards.run(req))
        .orElse(getCardsForBank.run(req))
    }

    val allRoutesWithMiddleware: HttpRoutes[IO] = ResourceDocMiddleware.apply(resourceDocs)(allOwnRoutes)

    // ─── path-rewriting bridge: /obp/v1.3.0/… → /obp/v1.2.1/… ─────────────
    // Delegates to Http4s121 so all inherited v1.2.1 endpoints are served
    // under the v1.3.0 URL prefix without duplicating any logic.

    val v130ToV121Bridge: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
      val rawPath = req.uri.path.renderString
      if (rawPath.startsWith("/obp/v1.3.0/")) {
        val rewritten    = rawPath.replaceFirst("/obp/v1\\.3\\.0/", "/obp/v1.2.1/")
        val newUri       = req.uri.withPath(Uri.Path.unsafeFromString(rewritten))
        val rewrittenReq = req.withUri(newUri)
        code.api.v1_2_1.Http4s121.wrappedRoutesV121Services.run(rewrittenReq)
      } else {
        OptionT.none[IO, Response[IO]]
      }
    }
  }

  // Own middleware-wrapped routes take priority; inherited v1.2.1 paths follow.
  val wrappedRoutesV130Services: HttpRoutes[IO] =
    Kleisli[HttpF, Request[IO], Response[IO]] { req =>
      Implementations1_3_0.allRoutesWithMiddleware.run(req)
        .orElse(Implementations1_3_0.v130ToV121Bridge.run(req))
    }
}
