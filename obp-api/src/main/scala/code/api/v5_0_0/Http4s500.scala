package code.api.v5_0_0

import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.Constant._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil.{EmptyBody, ResourceDoc}
import code.api.util.APIUtil
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.http4s.ResourceDocMiddleware
import code.api.util.http4s.Http4sRequestAttributes.{EndpointHelpers, RequestOps}
import code.api.util.http4s.ErrorResponseConverter
import code.api.util.{CustomJsonFormats, NewStyle}
import code.api.util.APIUtil.getProductsIsPublic
import code.api.v4_0_0.JSONFactory400
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.BankId
import com.openbankproject.commons.model.ProductCode
import com.openbankproject.commons.dto.GetProductsParam
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus, ScannedApiVersion}
import dispatch.{Http => DispatchHttp, as => DispatchAs, url => DispatchUrl}
import java.nio.charset.StandardCharsets
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

  private def okJson[A](a: A): IO[Response[IO]] = {
    val jsonString = prettyRender(Extraction.decompose(a))
    Ok(jsonString)
  }

  private def executeFuture[A](req: Request[IO])(f: => scala.concurrent.Future[A]): IO[Response[IO]] = {
    implicit val cc: code.api.util.CallContext = req.callContext
    IO.fromFuture(IO(f)).attempt.flatMap {
      case Right(result) => okJson(result)
      case Left(err) => ErrorResponseConverter.toHttp4sResponse(err, cc)
    }
  }

  val implementedInApiVersion: ScannedApiVersion = ApiVersion.v5_0_0
  val versionStatus: String = ApiVersionStatus.STABLE.toString
  val resourceDocs: ArrayBuffer[ResourceDoc] = ArrayBuffer[ResourceDoc]()

  object Implementations5_0_0 {

    val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString
    private val prefixPathString = s"/${ApiPathZero.toString}/${implementedInApiVersion.toString}"
    private val liftProxyBaseUrl = APIUtil.getPropsValue("http4s.lift_proxy_base_url", "http://localhost:8080")

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

    private val productsAuthErrorBodies =
      if (getProductsIsPublic) List(BankNotFound, UnknownError)
      else List(AuthenticatedUserIsRequired, BankNotFound, UnknownError)

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getProducts),
      "GET",
      "/banks/BANK_ID/products",
      "Get Products",
      s"""Get products offered by the bank specified by BANK_ID.
         |
         |Can filter with attributes name and values.
         |URL params example: /banks/some-bank-id/products?&limit=50&offset=1
         |
         |${code.api.util.APIUtil.userAuthenticationMessage(!getProductsIsPublic)}""".stripMargin,
      EmptyBody,
      productsJsonV400,
      productsAuthErrorBodies,
      List(apiTagProduct),
      http4sPartialFunction = Some(getProducts)
    )

    val getProducts: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankId / "products" =>
        executeFuture(req) {
          val cc = req.callContext
          val params = req.uri.query.multiParams.toList.map { case (k, vs) =>
            GetProductsParam(k, vs.toList)
          }
          for {
            (products, callContext) <- NewStyle.function.getProducts(BankId(bankId), params, Some(cc))
          } yield JSONFactory400.createProductsJson(products)
        }
    }

    resourceDocs += ResourceDoc(
      null,
      implementedInApiVersion,
      nameOf(getProduct),
      "GET",
      "/banks/BANK_ID/products/PRODUCT_CODE",
      "Get Bank Product",
      s"""Returns information about a financial Product offered by the bank specified by BANK_ID and PRODUCT_CODE.
         |
         |${code.api.util.APIUtil.userAuthenticationMessage(!getProductsIsPublic)}""".stripMargin,
      EmptyBody,
      productJsonV400,
      productsAuthErrorBodies ::: List(ProductNotFoundByProductCode),
      List(apiTagProduct),
      http4sPartialFunction = Some(getProduct)
    )

    val getProduct: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankId / "products" / productCode =>
        executeFuture(req) {
          val cc = req.callContext
          val bankIdObj = BankId(bankId)
          val productCodeObj = ProductCode(productCode)
          for {
            (product, callContext) <- NewStyle.function.getProduct(bankIdObj, productCodeObj, Some(cc))
            (productAttributes, callContext) <- NewStyle.function.getProductAttributesByBankAndCode(bankIdObj, productCodeObj, callContext)
            (productFees, callContext) <- NewStyle.function.getProductFeesFromProvider(bankIdObj, productCodeObj, callContext)
          } yield JSONFactory400.createProductJson(product, productAttributes, productFees)
        }
    }

    private def proxyToLift(req: Request[IO]): IO[Response[IO]] = {
      val targetUrl = liftProxyBaseUrl.stripSuffix("/") + req.uri.renderString
      val filteredHeaders = req.headers.headers
        .filterNot(h => {
          val name = h.name.toString.toLowerCase
          name == "host" || name == "content-length" || name == "transfer-encoding"
        })
        .map(h => h.name.toString -> h.value)
        .toMap

      for {
        body <- req.bodyText.compile.string
        dispatchReq = (
          DispatchUrl(targetUrl)
            .setMethod(req.method.name)
            .setBodyEncoding(StandardCharsets.UTF_8)
            .setBody(body)
            <:< filteredHeaders
        )
        liftResp <- IO.fromFuture(IO(DispatchHttp.default(dispatchReq > DispatchAs.Response(p => p))))
        status = org.http4s.Status.fromInt(liftResp.getStatusCode).getOrElse(org.http4s.Status.InternalServerError)
        responseBody = liftResp.getResponseBody
        correlationHeader = Option(liftResp.getHeader("Correlation-Id")).filter(_.nonEmpty)
        base = Response[IO](status).withEntity(responseBody)
        withCorrelation = correlationHeader match {
          case Some(value) => base.putHeaders(Header.Raw(org.typelevel.ci.CIString("Correlation-Id"), value))
          case None => base
        }
      } yield withCorrelation
    }

    val proxy: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req if req.uri.path.renderString.startsWith(prefixPathString) =>
        proxyToLift(req)
    }

    val allRoutes: HttpRoutes[IO] =
      Kleisli[HttpF, Request[IO], Response[IO]] { req: Request[IO] =>
        root(req)
          .orElse(getBanks(req))
          .orElse(getBank(req))
          .orElse(getProducts(req))
          .orElse(getProduct(req))
          .orElse(proxy(req))
      }

    val allRoutesWithMiddleware: HttpRoutes[IO] =
      ResourceDocMiddleware.apply(resourceDocs)(allRoutes)
  }

  val wrappedRoutesV500Services: HttpRoutes[IO] = Implementations5_0_0.allRoutesWithMiddleware
}
