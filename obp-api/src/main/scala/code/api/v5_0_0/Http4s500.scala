package code.api.v5_0_0

import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.Constant._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil.{EmptyBody, ResourceDoc}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.http4s.ResourceDocMiddleware
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.api.util.{CustomJsonFormats, NewStyle}
import code.api.v4_0_0.JSONFactory400
import code.api.v5_0_0.JSONFactory500
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.BankId
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus, ScannedApiVersion}
import net.liftweb.json.JsonAST.prettyRender
import net.liftweb.json.{Extraction, Formats}
import org.http4s._
import org.http4s.dsl.io._

import scala.collection.mutable.ArrayBuffer
import scala.language.{higherKinds, implicitConversions}

object Http4s500 {

  type HttpF[A] = OptionT[IO, A]

  implicit val formats: Formats = CustomJsonFormats.formats
  implicit def convertAnyToJsonString(any: Any): String = prettyRender(Extraction.decompose(any))

  val implementedInApiVersion: ScannedApiVersion = ApiVersion.v5_0_0
  val versionStatus: String = ApiVersionStatus.STABLE.toString
  val resourceDocs: ArrayBuffer[ResourceDoc] = ArrayBuffer[ResourceDoc]()

  object Implementations5_0_0 {

    val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(root),
      "GET",
      "/root",
      "Get API Info (root)",
      """Returns information about:
        |
        |* API version
        |* Hosted by information
        |* Hosted at information
        |* Energy source information
        |* Git Commit""",
      EmptyBody,
      apiInfoJson400,
      List(
        UnknownError,
        MandatoryPropertyIsNotSet
      ),
      apiTagApi :: Nil,
      http4sPartialFunction = Some(root)
    )

    val root: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "root" =>
        val responseJson = convertAnyToJsonString(
          JSONFactory400.getApiInfoJSON(OBPAPI5_0_0.version, OBPAPI5_0_0.versionStatus)
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
      """Get banks on this API instance
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

    val getBanks: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          for {
            (banks, callContext) <- NewStyle.function.getBanks(Some(cc))
          } yield JSONFactory400.createBanksJson(banks)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getBank),
      "GET",
      "/banks/BANK_ID",
      "Get Bank",
      """Get the bank specified by BANK_ID
        |Returns information about a single bank specified by BANK_ID including:
        |
        |* Bank code and full name of bank
        |* Logo URL
        |* Website""",
      EmptyBody,
      bankJson500,
      List(
        UnknownError,
        BankNotFound
      ),
      apiTagBank :: apiTagPSD2AIS :: apiTagPsd2 :: Nil,
      http4sPartialFunction = Some(getBank)
    )

    val getBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankId =>
        EndpointHelpers.withBank(req) { (bank, cc) =>
          for {
            (attributes, callContext) <- NewStyle.function.getBankAttributesByBank(BankId(bankId), Some(cc))
          } yield JSONFactory500.createBankJSON500(bank, attributes)
        }
    }

    val allRoutes: HttpRoutes[IO] =
      Kleisli[HttpF, Request[IO], Response[IO]] { req: Request[IO] =>
        root(req)
          .orElse(getBanks(req))
          .orElse(getBank(req))
      }

    val allRoutesWithMiddleware: HttpRoutes[IO] =
      ResourceDocMiddleware.apply(resourceDocs)(allRoutes)
  }

  val wrappedRoutesV500Services: HttpRoutes[IO] = Implementations5_0_0.allRoutesWithMiddleware
}
