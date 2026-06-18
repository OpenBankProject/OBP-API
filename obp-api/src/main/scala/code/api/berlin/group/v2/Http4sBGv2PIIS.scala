package code.api.berlin.group.v2

import org.json4s._
import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.berlin.group.ConstantsBG
import code.api.util.APIUtil.{EmptyBody, ResourceDoc}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.CustomJsonFormats
import code.util.Helper.MdcLoggable
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.JsonAliases.prettyRender
import org.json4s.{Extraction, Formats}
import org.http4s._
import org.http4s.dsl.io._

import scala.collection.mutable.ArrayBuffer
import scala.language.implicitConversions

object Http4sBGv2PIIS extends MdcLoggable {

  type HttpF[A] = OptionT[IO, A]

  implicit val formats: Formats = CustomJsonFormats.formats
  implicit def convertAnyToJsonString(any: Any): String = prettyRender(Extraction.decompose(any))

  val implementedInApiVersion = ConstantsBG.berlinGroupVersion2
  val resourceDocs = ArrayBuffer[ResourceDoc]()

  val bgV2Prefix = Root / ConstantsBG.berlinGroupVersion2.urlPrefix / ConstantsBG.berlinGroupVersion2.apiShortVersion

  // ── POST /v2/funds-confirmations ──────────────────────────────────

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(postConfirmationOfFunds),
    "POST",
    "/funds-confirmations",
    "Confirmation of Funds Request",
    "Checks whether a specific amount is available on an account.",
    EmptyBody,
    JSONFactory_BERLIN_GROUP_v2.mockFundsConfirmation,
    List(UnknownError),
    apiTagPSD2PIIS :: apiTagBerlinGroupM :: Nil,
    http4sPartialFunction = Some(postConfirmationOfFunds)
  )

  val postConfirmationOfFunds: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `bgV2Prefix` / "funds-confirmations" =>
      Ok(convertAnyToJsonString(JSONFactory_BERLIN_GROUP_v2.mockFundsConfirmation))
  }

  // ── Combined routes ───────────────────────────────────────────────

  val routes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    postConfirmationOfFunds(req)
  }
}
