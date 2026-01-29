package code.api.v5_0_0

import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.Constant._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil.{EmptyBody, OBPEndpoint, ResourceDoc, getProductsIsPublic}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.http4s.Http4sRequestAttributes.{EndpointHelpers, RequestOps}
import code.api.util.http4s.{ErrorResponseConverter, ResourceDocMiddleware}
import code.api.util.{CustomJsonFormats, NewStyle}
import code.api.v4_0_0.JSONFactory400
import code.api.{JsonResponseException, ResponseHeader}
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.dto.GetProductsParam
import com.openbankproject.commons.model.{BankId, ProductCode}
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus, ReflectUtils, ScannedApiVersion}
import net.liftweb.actor.LAFuture
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.http.provider._
import net.liftweb.http.{BasicResponse, InMemoryResponse, InternalServerErrorResponse, LiftResponse, LiftRules, LiftSession, NotFoundResponse, OutputStreamResponse, Req, S, StreamingResponse}
import net.liftweb.json.JsonAST.prettyRender
import net.liftweb.json.{Extraction, Formats}
import org.http4s._
import org.http4s.dsl.io._
import org.typelevel.ci.CIString

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream}
import java.time.format.DateTimeFormatter
import java.time.{ZoneOffset, ZonedDateTime}
import java.util.{Locale, UUID}
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

    private lazy val liftHandlers: List[(OBPEndpoint, Option[ResourceDoc])] = {
      val docs = OBPAPI5_0_0.allResourceDocs
      OBPAPI5_0_0.routes.flatMap { route =>
        val routeDocs = docs.filter(_.partialFunction == route)
        if (routeDocs.isEmpty) {
          List(OBPAPI5_0_0.apiPrefix(route) -> None)
        } else {
          val (autoValidateDocs, otherDocs) = routeDocs.partition(OBPAPI5_0_0.isAutoValidate(_, autoValidateAll = true))
          val autoValidateHandlers = autoValidateDocs.toList.map { doc =>
            OBPAPI5_0_0.apiPrefix(doc.wrappedWithAuthCheck(route)) -> Some(doc)
          }
          val otherHandlers = otherDocs.headOption.toList.map { doc =>
            OBPAPI5_0_0.apiPrefix(route) -> Some(doc)
          }
          autoValidateHandlers ++ otherHandlers
        }
      }
    }

    private def dispatchToLift(req: Request[IO]): IO[Response[IO]] = {
      for {
        bodyBytes <- req.body.compile.to(Array)
        liftReq = buildLiftReq(req, bodyBytes)
        liftResp <- IO {
          val session = LiftRules.statelessSession.vend.apply(liftReq)
          S.init(Full(liftReq), session) {
            try {
              val matchingHandler = liftHandlers.find { case (handler, _) =>
                if (liftReq.json_?) {
                  liftReq.json match {
                    case net.liftweb.common.Failure(_, _, _) => true
                    case _ => handler.isDefinedAt(liftReq)
                  }
                } else {
                  handler.isDefinedAt(liftReq)
                }
              }
              matchingHandler match {
                case Some((handler, doc)) =>
                  OBPAPI5_0_0.failIfBadAuthorizationHeader(doc) {
                    OBPAPI5_0_0.failIfBadJSON(liftReq, handler)
                  }
                case None =>
                  NotFoundResponse()
              }
            } catch {
              case JsonResponseException(jsonResponse) => jsonResponse
              case e if e.getClass.getName == "net.liftweb.http.rest.ContinuationException" =>
                resolveContinuation(e)
            }
          }
        }
        http4sResponse <- liftResponseToHttp4s(liftResp)
      } yield http4sResponse
    }

    private def resolveContinuation(exception: Throwable): LiftResponse = {
      val func =
        ReflectUtils
          .getCallByNameValue(exception, "f")
          .asInstanceOf[((=> LiftResponse) => Unit) => Unit]
      val future = new LAFuture[LiftResponse]
      val satisfy: (=> LiftResponse) => Unit = response => future.satisfy(response)
      func(satisfy)
      future.get(60 * 1000L).openOr(InternalServerErrorResponse())
    }

    private def buildLiftReq(req: Request[IO], body: Array[Byte]): Req = {
      val headers = http4sHeadersToParams(req.headers.headers)
      val params = http4sParamsToParams(req.uri.query.multiParams.toList)
      val httpRequest = new Http4sLiftRequest(
        req = req,
        body = body,
        headerParams = headers,
        queryParams = params
      )
      Req(
        httpRequest,
        LiftRules.statelessRewrite.toList,
        Nil,
        LiftRules.statelessReqTest.toList,
        System.nanoTime()
      )
    }

    private def http4sHeadersToParams(headers: List[Header.Raw]): List[HTTPParam] = {
      headers
        .groupBy(_.name.toString)
        .toList
        .map { case (name, values) =>
          HTTPParam(name, values.map(_.value))
        }
    }

    private def http4sParamsToParams(params: List[(String, collection.Seq[String])]): List[HTTPParam] = {
      params.map { case (name, values) =>
        HTTPParam(name, values.toList)
      }
    }

    private def liftResponseToHttp4s(response: LiftResponse): IO[Response[IO]] = {
      response.toResponse match {
        case InMemoryResponse(data, headers, _, code) =>
          IO.pure(buildHttp4sResponse(code, data, headers))
        case StreamingResponse(data, onEnd, _, headers, _, code) =>
          IO {
            val bytes = readAllBytes(data.asInstanceOf[InputStream])
            onEnd()
            buildHttp4sResponse(code, bytes, headers)
          }
        case OutputStreamResponse(out, _, headers, _, code) =>
          IO {
            val baos = new ByteArrayOutputStream()
            out(baos)
            buildHttp4sResponse(code, baos.toByteArray, headers)
          }
        case basic: BasicResponse =>
          IO.pure(buildHttp4sResponse(basic.code, Array.emptyByteArray, basic.headers))
      }
    }

    private def buildHttp4sResponse(code: Int, body: Array[Byte], headers: List[(String, String)]): Response[IO] = {
      val contentTypeHeader = headers.find { case (name, _) => name.equalsIgnoreCase("Content-Type") }
      val normalizedHeaders = contentTypeHeader match {
        case Some(_) => headers
        case None => ("Content-Type", "application/json; charset=utf-8") :: headers
      }
      val http4sHeaders = Headers(
        normalizedHeaders.map { case (name, value) => Header.Raw(CIString(name), value) }
      )
      Response[IO](
        status = org.http4s.Status.fromInt(code).getOrElse(org.http4s.Status.InternalServerError)
      ).withEntity(body).withHeaders(http4sHeaders)
    }

    private def readAllBytes(input: InputStream): Array[Byte] = {
      val buffer = new ByteArrayOutputStream()
      val chunk = new Array[Byte](4096)
      var read = input.read(chunk)
      while (read != -1) {
        buffer.write(chunk, 0, read)
        read = input.read(chunk)
      }
      buffer.toByteArray
    }

    private object Http4sLiftContext extends HTTPContext {
      private val attributesStore = scala.collection.mutable.Map.empty[String, Any]
      def path: String = ""
      def resource(path: String): java.net.URL = null
      def resourceAsStream(path: String): InputStream = null
      def mimeType(path: String): Box[String] = Empty
      def initParam(name: String): Box[String] = Empty
      def initParams: List[(String, String)] = Nil
      def attribute(name: String): Box[Any] = Box(attributesStore.get(name))
      def attributes: List[(String, Any)] = attributesStore.toList
      def setAttribute(name: String, value: Any): Unit = attributesStore.update(name, value)
      def removeAttribute(name: String): Unit = attributesStore.remove(name)
    }

    private object Http4sLiftProvider extends HTTPProvider {
      override protected def context: HTTPContext = Http4sLiftContext
    }

    private final class Http4sLiftSession(val sessionId: String) extends HTTPSession {
      private val attributesStore = scala.collection.mutable.Map.empty[String, Any]
      private var maxInactive: Long = 0L
      private val createdAt: Long = System.currentTimeMillis()
      def link(liftSession: LiftSession): Unit = ()
      def unlink(liftSession: LiftSession): Unit = ()
      def maxInactiveInterval: Long = maxInactive
      def setMaxInactiveInterval(interval: Long): Unit = { maxInactive = interval }
      def lastAccessedTime: Long = createdAt
      def setAttribute(name: String, value: Any): Unit = attributesStore.update(name, value)
      def attribute(name: String): Any = attributesStore.getOrElse(name, null)
      def removeAttribute(name: String): Unit = attributesStore.remove(name)
      def terminate: Unit = ()
    }

    private final class Http4sLiftRequest(
      req: Request[IO],
      body: Array[Byte],
      headerParams: List[HTTPParam],
      queryParams: List[HTTPParam]
    ) extends HTTPRequest {
      private val sessionValue = new Http4sLiftSession(UUID.randomUUID().toString)
      private val uriPath = req.uri.path.renderString
      private val uriQuery = req.uri.query.renderString
      private val remoteAddr = req.remoteAddr
      def cookies: List[HTTPCookie] = Nil
      def provider: HTTPProvider = Http4sLiftProvider
      def authType: Box[String] = Empty
      def headers(name: String): List[String] =
        headerParams.find(_.name.equalsIgnoreCase(name)).map(_.values).getOrElse(Nil)
      def headers: List[HTTPParam] = headerParams
      def contextPath: String = ""
      def context: HTTPContext = Http4sLiftContext
      def contentType: Box[String] = req.contentType.map(_.mediaType.toString)
      def uri: String = uriPath
      def url: String = req.uri.renderString
      def queryString: Box[String] = if (uriQuery.nonEmpty) Full(uriQuery) else Empty
      def param(name: String): List[String] = req.uri.query.multiParams.getOrElse(name, Nil).toList
      def params: List[HTTPParam] = queryParams
      def paramNames: List[String] = queryParams.map(_.name).distinct
      def session: HTTPSession = sessionValue
      def destroyServletSession(): Unit = ()
      def sessionId: Box[String] = Full(sessionValue.sessionId)
      def remoteAddress: String = remoteAddr.map(_.toUriString).getOrElse("")
      def remotePort: Int = req.uri.port.getOrElse(0)
      def remoteHost: String = remoteAddr.map(_.toUriString).getOrElse("")
      def serverName: String = req.uri.host.map(_.value).getOrElse("localhost")
      def scheme: String = req.uri.scheme.map(_.value).getOrElse("http")
      def serverPort: Int = req.uri.port.getOrElse(0)
      def method: String = req.method.name
      def suspendResumeSupport_? : Boolean = false
      def resumeInfo: Option[(Req, LiftResponse)] = None
      def suspend(timeout: Long): RetryState.Value = RetryState.TIMED_OUT
      def resume(what: (Req, LiftResponse)): Boolean = false
      def inputStream: InputStream = new ByteArrayInputStream(body)
      def multipartContent_? : Boolean = contentType.exists(_.toLowerCase.contains("multipart/"))
      def extractFiles: List[net.liftweb.http.ParamHolder] = Nil
      def locale: Box[Locale] = Empty
      def setCharacterEncoding(encoding: String): Unit = ()
      def snapshot: HTTPRequest = this
      def userAgent: Box[String] = header("User-Agent")
    }

    val proxy: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req if req.uri.path.renderString.startsWith(prefixPathString) =>
        dispatchToLift(req)
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

    private def ensureStandardHeaders(routes: HttpRoutes[IO]): HttpRoutes[IO] = {
      Kleisli[HttpF, Request[IO], Response[IO]] { req: Request[IO] =>
        routes.run(req).map { resp =>
          val now = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.RFC_1123_DATE_TIME)
          val existing = resp.headers.headers
          def hasHeader(name: String): Boolean =
            existing.exists(_.name.toString.equalsIgnoreCase(name))
          val existingCorrelationId = existing
            .find(_.name.toString.equalsIgnoreCase(ResponseHeader.`Correlation-Id`))
            .map(_.value)
            .getOrElse("")
          val correlationId =
            Option(existingCorrelationId).map(_.trim).filter(_.nonEmpty)
              .orElse(req.headers.get(CIString("X-Request-ID")).map(_.head.value))
              .getOrElse(UUID.randomUUID().toString)
          val extraHeaders = List.newBuilder[Header.Raw]
          if (existingCorrelationId.trim.isEmpty) {
            extraHeaders += Header.Raw(CIString(ResponseHeader.`Correlation-Id`), correlationId)
          }
          if (!hasHeader("Cache-Control")) {
            extraHeaders += Header.Raw(CIString("Cache-Control"), "no-cache, private, no-store")
          }
          if (!hasHeader("Pragma")) {
            extraHeaders += Header.Raw(CIString("Pragma"), "no-cache")
          }
          if (!hasHeader("Expires")) {
            extraHeaders += Header.Raw(CIString("Expires"), now)
          }
          if (!hasHeader("X-Frame-Options")) {
            extraHeaders += Header.Raw(CIString("X-Frame-Options"), "DENY")
          }
          val headersToAdd = extraHeaders.result()
          if (headersToAdd.isEmpty) resp
          else {
            val filtered = resp.headers.headers.filterNot(h =>
              h.name.toString.equalsIgnoreCase(ResponseHeader.`Correlation-Id`) &&
                h.value.trim.isEmpty
            )
            resp.copy(headers = Headers(filtered) ++ Headers(headersToAdd))
          }
        }
      }
    }

    val allRoutesWithMiddleware: HttpRoutes[IO] =
      ensureStandardHeaders(ResourceDocMiddleware.apply(resourceDocs)(allRoutes))
  }

  val wrappedRoutesV500Services: HttpRoutes[IO] = Implementations5_0_0.allRoutesWithMiddleware
}
