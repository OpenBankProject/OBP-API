package code.api.util.http4s

import org.json4s._
import cats.effect._
import code.api.util.APIUtil.{HTTPParam, ResourceDoc}
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, InvalidJsonFormat}
import code.api.util.{AuthHeaderParser, CallContext, RemoteIpUtil, WriteMetricUtil}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.{Bank, BankAccount, CounterpartyTrait, User, View}
import net.liftweb.common.{Box, Empty, Full}
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`
import org.typelevel.ci.CIString
import org.typelevel.vault.Key

import java.util.{Date, UUID}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.language.higherKinds
import code.api.util.http4s.RequestScopeConnection

/**
 * Http4s support utilities for OBP API.
 * 
 * This file contains three main components:
 * 
 * 1. Http4sRequestAttributes: Request attribute management and endpoint helpers
 *    - Stores CallContext in http4s request Vault
 *    - Provides helper methods to simplify endpoint implementations
 *    - Validated entities are stored in CallContext fields
 * 
 * 2. Http4sCallContextBuilder: Builds CallContext from http4s Request[IO]
 *    - Extracts headers, auth params, and request metadata
 *    - Supports DirectLogin, OAuth, and Gateway authentication
 * 
 * 3. ResourceDocMatcher: Matches requests to ResourceDoc entries
 *    - Finds ResourceDoc by HTTP verb and URL pattern
 *    - Extracts path parameters (BANK_ID, ACCOUNT_ID, etc.)
 *    - Attaches ResourceDoc to CallContext for metrics/rate limiting
 */

/**
 * Request attributes and helper methods for http4s endpoints.
 * 
 * CallContext is stored in request attributes using http4s Vault (type-safe key-value store).
 * Validated entities (user, bank, bankAccount, view, counterparty) are stored within CallContext.
 */
object Http4sRequestAttributes {
  
  /**
   * Vault key for storing CallContext in http4s request attributes.
   * CallContext contains request data and validated entities (user, bank, account, view, counterparty).
   */
  val callContextKey: Key[CallContext] =
    Key.newKey[IO, CallContext].unsafeRunSync()(cats.effect.unsafe.IORuntime.global)

  /**
   * Vault key for caching the (already-read) request body across bridge cascade hops.
   *
   * Http4s body streams are single-shot: `request.bodyText.compile.string` drains the stream.
   * Without a cache, the first version's middleware reads the body to build the CallContext;
   * subsequent bridge calls (v400→v310→v300→v220→v210) re-read an empty stream and the
   * eventual handler sees no body. The first `fromRequest` call stores the body as
   * `Some(String)` (POSTs/PUTs) or `None` (GETs/etc.) — later calls return it untouched.
   */
  val cachedBodyKey: Key[Option[String]] =
    Key.newKey[IO, Option[String]].unsafeRunSync()(cats.effect.unsafe.IORuntime.global)
  
  /**
   * Implicit class that adds .callContext accessor to Request[IO].
   * 
   * Usage:
   * {{{
   * import Http4sRequestAttributes.RequestOps
   * 
   * case req @ GET -> Root / "banks" =>
   *   implicit val cc: CallContext = req.callContext
   *   // Use cc for business logic
   * }}}
   */
  implicit class RequestOps(val req: Request[IO]) extends AnyVal {
    /**
     * Extract CallContext from request attributes.
     * Throws RuntimeException if not found (should never happen with ResourceDocMiddleware).
     */
    def callContext: CallContext = {
      req.attributes.lookup(callContextKey).getOrElse(
        throw new RuntimeException("CallContext not found in request attributes")
      )
    }
  }
  
  /**
   * Helper methods to eliminate boilerplate in endpoint implementations.
   * 
   * These methods handle:
   * - CallContext extraction from request
   * - User/Bank extraction from CallContext
   * - Future execution with IO.fromFuture
   * - JSON serialization with Lift JSON
   * - Ok response creation
   */
  object EndpointHelpers extends MdcLoggable {
    import com.openbankproject.commons.util.JsonAliases.prettyRender
    import org.json4s.{Extraction, Formats}

    // OBP responses are always JSON. Passing a raw String to http4s' Ok/Created uses the
    // default EntityEncoder[String], which sets `Content-Type: text/plain`. Override it so
    // clients (and the strict application/json check in the frontend) see the real type.
    private val jsonContentType = `Content-Type`(MediaType.application.json)

    private def toJsonOk[A](result: A)(implicit formats: Formats): IO[Response[IO]] = {
      val jsonString = prettyRender(Extraction.decompose(result))
      Ok(jsonString, jsonContentType)
    }

    /**
     * Write endpoint metric and log timing after the response is built.
     * Stamps endTime and httpCode onto the CallContext before converting to light form.
     */
    // Metric recording runs on the blocking threadpool (IO.blocking) so that
    // synchronous logger/DB writes do not steal cats-effect compute threads.
    // Not fired as a background fiber: response waits for metric to be written,
    // matching v6 behaviour and avoiding unbounded concurrent H2 write contention.
    private def recordMetric(responseBody: Any, response: Response[IO])(implicit cc: CallContext): IO[Unit] =
      if (!code.metrics.MetricsProps.writeMetrics) IO.unit
      else IO.blocking {
        val endTime = new Date()
        val duration = cc.startTime.map(s => endTime.getTime - s.getTime).getOrElse(-1L)
        val ccLight = cc.copy(httpCode = Some(response.status.code), endTime = Some(endTime)).toLight
        logger.info(s"Endpoint (${cc.verb}) ${cc.url} returned ${response.status.code}, took $duration Milliseconds")
        WriteMetricUtil.writeEndpointMetric(responseBody, Some(ccLight))
      }

    /**
     * Execute Future-based business logic and return JSON response.
     * Returns 200 OK on success, converts errors via ErrorResponseConverter.
     */
    def executeAndRespond[A](req: Request[IO])(f: CallContext => Future[A])(implicit formats: Formats): IO[Response[IO]] = {
      implicit val cc: CallContext = req.callContext
      RequestScopeConnection.fromFuture(f(cc)).attempt.flatMap {
        case Right(result) => toJsonOk(result).flatTap(recordMetric(result, _))
        case Left(err) => ErrorResponseConverter.toHttp4sResponse(err, cc).flatTap(recordMetric(err.getMessage, _))
      }
    }

    /**
     * Execute business logic requiring validated User.
     * Returns 200 OK on success, converts errors via ErrorResponseConverter.
     */
    def withUser[A](req: Request[IO])(f: (User, CallContext) => Future[A])(implicit formats: Formats): IO[Response[IO]] = {
      implicit val cc: CallContext = req.callContext
      val io = for {
        user <- IO.fromOption(cc.user.toOption)(new RuntimeException(AuthenticatedUserIsRequired))
        result <- RequestScopeConnection.fromFuture(f(user, cc))
      } yield result
      io.attempt.flatMap {
        case Right(result) => toJsonOk(result).flatTap(recordMetric(result, _))
        case Left(err) => ErrorResponseConverter.toHttp4sResponse(err, cc).flatTap(recordMetric(err.getMessage, _))
      }
    }

    /**
     * Execute business logic requiring validated Bank.
     * Returns 200 OK on success, converts errors via ErrorResponseConverter.
     */
    def withBank[A](req: Request[IO])(f: (Bank, CallContext) => Future[A])(implicit formats: Formats): IO[Response[IO]] = {
      implicit val cc: CallContext = req.callContext
      val io = for {
        bank <- IO.fromOption(cc.bank)(new RuntimeException("Bank not found in CallContext"))
        result <- RequestScopeConnection.fromFuture(f(bank, cc))
      } yield result
      io.attempt.flatMap {
        case Right(result) => toJsonOk(result).flatTap(recordMetric(result, _))
        case Left(err) => ErrorResponseConverter.toHttp4sResponse(err, cc).flatTap(recordMetric(err.getMessage, _))
      }
    }

    /**
     * Execute business logic requiring both User and Bank.
     * Returns 200 OK on success, converts errors via ErrorResponseConverter.
     */
    def withUserAndBank[A](req: Request[IO])(f: (User, Bank, CallContext) => Future[A])(implicit formats: Formats): IO[Response[IO]] = {
      implicit val cc: CallContext = req.callContext
      val io = for {
        user <- IO.fromOption(cc.user.toOption)(new RuntimeException(AuthenticatedUserIsRequired))
        bank <- IO.fromOption(cc.bank)(new RuntimeException("Bank not found in CallContext"))
        result <- RequestScopeConnection.fromFuture(f(user, bank, cc))
      } yield result
      io.attempt.flatMap {
        case Right(result) => toJsonOk(result).flatTap(recordMetric(result, _))
        case Left(err) => ErrorResponseConverter.toHttp4sResponse(err, cc).flatTap(recordMetric(err.getMessage, _))
      }
    }

    /**
     * Parse the request body from CallContext into type B.
     * Returns Left(error message) if body is absent or not valid JSON for B.
     */
    private def parseBody[B](cc: CallContext)(implicit formats: Formats, mf: Manifest[B]): Either[String, B] =
      cc.httpBody match {
        case None | Some("") => Left(s"$InvalidJsonFormat Missing request body.")
        case Some(raw) =>
          scala.util.Try(com.openbankproject.commons.util.JsonAliases.parse(raw).extract[B]).toEither.left.map(_ => s"$InvalidJsonFormat ${mf.runtimeClass.getSimpleName}")
      }

    /**
     * Execute POST/PUT business logic with JSON body parsing.
     * Returns 200 OK on success, 400 on body parse failure, converts errors via ErrorResponseConverter.
     */
    def executeFutureWithBody[B, A](req: Request[IO])(f: (B, CallContext) => Future[A])(implicit formats: Formats, mf: Manifest[B]): IO[Response[IO]] = {
      implicit val cc: CallContext = req.callContext
      parseBody[B](cc) match {
        case Left(msg)   => ErrorResponseConverter.createErrorResponse(400, msg, cc).flatTap(recordMetric(msg, _))
        case Right(body) =>
          RequestScopeConnection.fromFuture(f(body, cc)).attempt.flatMap {
            case Right(result) => toJsonOk(result).flatTap(recordMetric(result, _))
            case Left(err)     => ErrorResponseConverter.toHttp4sResponse(err, cc).flatTap(recordMetric(err.getMessage, _))
          }
      }
    }

    /**
     * Execute POST business logic with JSON body parsing.
     * Returns 201 Created on success, 400 on body parse failure, converts errors via ErrorResponseConverter.
     */
    def executeFutureWithBodyCreated[B, A](req: Request[IO])(f: (B, CallContext) => Future[A])(implicit formats: Formats, mf: Manifest[B]): IO[Response[IO]] = {
      implicit val cc: CallContext = req.callContext
      parseBody[B](cc) match {
        case Left(msg)   => ErrorResponseConverter.createErrorResponse(400, msg, cc).flatTap(recordMetric(msg, _))
        case Right(body) =>
          RequestScopeConnection.fromFuture(f(body, cc)).attempt.flatMap {
            case Right(result) =>
              val jsonString = prettyRender(Extraction.decompose(result))
              Created(jsonString, jsonContentType).flatTap(recordMetric(result, _))
            case Left(err) => ErrorResponseConverter.toHttp4sResponse(err, cc).flatTap(recordMetric(err.getMessage, _))
          }
      }
    }

    /**
     * Execute POST/PUT business logic with JSON body parsing, requiring validated User.
     * Returns 200 OK on success, 400 on body parse failure, converts errors via ErrorResponseConverter.
     */
    def withUserAndBody[B, A](req: Request[IO])(f: (User, B, CallContext) => Future[A])(implicit formats: Formats, mf: Manifest[B]): IO[Response[IO]] = {
      implicit val cc: CallContext = req.callContext
      parseBody[B](cc) match {
        case Left(msg) => ErrorResponseConverter.createErrorResponse(400, msg, cc).flatTap(recordMetric(msg, _))
        case Right(body) =>
          val io = for {
            user   <- IO.fromOption(cc.user.toOption)(new RuntimeException(AuthenticatedUserIsRequired))
            result <- RequestScopeConnection.fromFuture(f(user, body, cc))
          } yield result
          io.attempt.flatMap {
            case Right(result) => toJsonOk(result).flatTap(recordMetric(result, _))
            case Left(err)     => ErrorResponseConverter.toHttp4sResponse(err, cc).flatTap(recordMetric(err.getMessage, _))
          }
      }
    }

    /**
     * Execute POST business logic with JSON body parsing, requiring validated User.
     * Returns 201 Created on success, 400 on body parse failure, converts errors via ErrorResponseConverter.
     */
    def withUserAndBodyCreated[B, A](req: Request[IO])(f: (User, B, CallContext) => Future[A])(implicit formats: Formats, mf: Manifest[B]): IO[Response[IO]] = {
      implicit val cc: CallContext = req.callContext
      parseBody[B](cc) match {
        case Left(msg) => ErrorResponseConverter.createErrorResponse(400, msg, cc).flatTap(recordMetric(msg, _))
        case Right(body) =>
          val io = for {
            user   <- IO.fromOption(cc.user.toOption)(new RuntimeException(AuthenticatedUserIsRequired))
            result <- RequestScopeConnection.fromFuture(f(user, body, cc))
          } yield result
          io.attempt.flatMap {
            case Right(result) =>
              val jsonString = prettyRender(Extraction.decompose(result))
              Created(jsonString, jsonContentType).flatTap(recordMetric(result, _))
            case Left(err) => ErrorResponseConverter.toHttp4sResponse(err, cc).flatTap(recordMetric(err.getMessage, _))
          }
      }
    }

    /**
     * Execute POST/PUT business logic with JSON body parsing, requiring validated User and Bank.
     * Returns 200 OK on success, 400 on body parse failure, converts errors via ErrorResponseConverter.
     */
    def withUserAndBankAndBody[B, A](req: Request[IO])(f: (User, Bank, B, CallContext) => Future[A])(implicit formats: Formats, mf: Manifest[B]): IO[Response[IO]] = {
      implicit val cc: CallContext = req.callContext
      parseBody[B](cc) match {
        case Left(msg) => ErrorResponseConverter.createErrorResponse(400, msg, cc).flatTap(recordMetric(msg, _))
        case Right(body) =>
          val io = for {
            user   <- IO.fromOption(cc.user.toOption)(new RuntimeException(AuthenticatedUserIsRequired))
            bank   <- IO.fromOption(cc.bank)(new RuntimeException("Bank not found in CallContext"))
            result <- RequestScopeConnection.fromFuture(f(user, bank, body, cc))
          } yield result
          io.attempt.flatMap {
            case Right(result) => toJsonOk(result).flatTap(recordMetric(result, _))
            case Left(err)     => ErrorResponseConverter.toHttp4sResponse(err, cc).flatTap(recordMetric(err.getMessage, _))
          }
      }
    }

    /**
     * Execute POST business logic with JSON body parsing, requiring validated User and Bank.
     * Returns 201 Created on success, 400 on body parse failure, converts errors via ErrorResponseConverter.
     */
    def withUserAndBankAndBodyCreated[B, A](req: Request[IO])(f: (User, Bank, B, CallContext) => Future[A])(implicit formats: Formats, mf: Manifest[B]): IO[Response[IO]] = {
      implicit val cc: CallContext = req.callContext
      parseBody[B](cc) match {
        case Left(msg) => ErrorResponseConverter.createErrorResponse(400, msg, cc).flatTap(recordMetric(msg, _))
        case Right(body) =>
          val io = for {
            user   <- IO.fromOption(cc.user.toOption)(new RuntimeException(AuthenticatedUserIsRequired))
            bank   <- IO.fromOption(cc.bank)(new RuntimeException("Bank not found in CallContext"))
            result <- RequestScopeConnection.fromFuture(f(user, bank, body, cc))
          } yield result
          io.attempt.flatMap {
            case Right(result) =>
              val jsonString = prettyRender(Extraction.decompose(result))
              Created(jsonString, jsonContentType).flatTap(recordMetric(result, _))
            case Left(err) => ErrorResponseConverter.toHttp4sResponse(err, cc).flatTap(recordMetric(err.getMessage, _))
          }
      }
    }

    /**
     * Execute business logic requiring validated User and BankAccount (URL must contain ACCOUNT_ID).
     * Returns 200 OK on success, converts errors via ErrorResponseConverter.
     */
    def withBankAccount[A](req: Request[IO])(f: (User, BankAccount, CallContext) => Future[A])(implicit formats: Formats): IO[Response[IO]] = {
      implicit val cc: CallContext = req.callContext
      val io = for {
        user        <- IO.fromOption(cc.user.toOption)(new RuntimeException(AuthenticatedUserIsRequired))
        bankAccount <- IO.fromOption(cc.bankAccount)(new RuntimeException("BankAccount not found in CallContext"))
        result      <- RequestScopeConnection.fromFuture(f(user, bankAccount, cc))
      } yield result
      io.attempt.flatMap {
        case Right(result) => toJsonOk(result).flatTap(recordMetric(result, _))
        case Left(err)     => ErrorResponseConverter.toHttp4sResponse(err, cc).flatTap(recordMetric(err.getMessage, _))
      }
    }

    /**
     * Execute POST business logic requiring validated User, BankAccount, and View (URL must contain VIEW_ID).
     * Returns 201 Created on success, converts errors via ErrorResponseConverter.
     */
    def withViewCreated[A](req: Request[IO])(f: (User, BankAccount, View, CallContext) => Future[A])(implicit formats: Formats): IO[Response[IO]] = {
      implicit val cc: CallContext = req.callContext
      val io = for {
        user        <- IO.fromOption(cc.user.toOption)(new RuntimeException(AuthenticatedUserIsRequired))
        bankAccount <- IO.fromOption(cc.bankAccount)(new RuntimeException("BankAccount not found in CallContext"))
        view        <- IO.fromOption(cc.view)(new RuntimeException("View not found in CallContext"))
        result      <- RequestScopeConnection.fromFuture(f(user, bankAccount, view, cc))
      } yield result
      io.attempt.flatMap {
        case Right(result) =>
          val jsonString = prettyRender(Extraction.decompose(result))
          Created(jsonString, jsonContentType).flatTap(recordMetric(result, _))
        case Left(err) => ErrorResponseConverter.toHttp4sResponse(err, cc).flatTap(recordMetric(err.getMessage, _))
      }
    }

    /**
     * Execute POST business logic with JSON body parsing, requiring validated User, BankAccount, and View.
     * Returns 201 Created on success, 400 on body parse failure, converts errors via ErrorResponseConverter.
     */
    def withViewAndBodyCreated[B, A](req: Request[IO])(f: (User, BankAccount, View, B, CallContext) => Future[A])(implicit formats: Formats, mf: Manifest[B]): IO[Response[IO]] = {
      implicit val cc: CallContext = req.callContext
      parseBody[B](cc) match {
        case Left(msg) => ErrorResponseConverter.createErrorResponse(400, msg, cc).flatTap(recordMetric(msg, _))
        case Right(body) =>
          val io = for {
            user        <- IO.fromOption(cc.user.toOption)(new RuntimeException(AuthenticatedUserIsRequired))
            bankAccount <- IO.fromOption(cc.bankAccount)(new RuntimeException("BankAccount not found in CallContext"))
            view        <- IO.fromOption(cc.view)(new RuntimeException("View not found in CallContext"))
            result      <- RequestScopeConnection.fromFuture(f(user, bankAccount, view, body, cc))
          } yield result
          io.attempt.flatMap {
            case Right(result) =>
              val jsonString = prettyRender(Extraction.decompose(result))
              Created(jsonString, jsonContentType).flatTap(recordMetric(result, _))
            case Left(err) => ErrorResponseConverter.toHttp4sResponse(err, cc).flatTap(recordMetric(err.getMessage, _))
          }
      }
    }

    /**
     * Execute business logic requiring validated User, BankAccount, and View (URL must contain VIEW_ID).
     * Returns 200 OK on success, converts errors via ErrorResponseConverter.
     */
    def withView[A](req: Request[IO])(f: (User, BankAccount, View, CallContext) => Future[A])(implicit formats: Formats): IO[Response[IO]] = {
      implicit val cc: CallContext = req.callContext
      val io = for {
        user        <- IO.fromOption(cc.user.toOption)(new RuntimeException(AuthenticatedUserIsRequired))
        bankAccount <- IO.fromOption(cc.bankAccount)(new RuntimeException("BankAccount not found in CallContext"))
        view        <- IO.fromOption(cc.view)(new RuntimeException("View not found in CallContext"))
        result      <- RequestScopeConnection.fromFuture(f(user, bankAccount, view, cc))
      } yield result
      io.attempt.flatMap {
        case Right(result) => toJsonOk(result).flatTap(recordMetric(result, _))
        case Left(err)     => ErrorResponseConverter.toHttp4sResponse(err, cc).flatTap(recordMetric(err.getMessage, _))
      }
    }

    /**
     * Execute business logic requiring validated User, BankAccount, View, and Counterparty (URL must contain COUNTERPARTY_ID).
     * Returns 200 OK on success, converts errors via ErrorResponseConverter.
     */
    def withCounterparty[A](req: Request[IO])(f: (User, BankAccount, View, CounterpartyTrait, CallContext) => Future[A])(implicit formats: Formats): IO[Response[IO]] = {
      implicit val cc: CallContext = req.callContext
      val io = for {
        user         <- IO.fromOption(cc.user.toOption)(new RuntimeException(AuthenticatedUserIsRequired))
        bankAccount  <- IO.fromOption(cc.bankAccount)(new RuntimeException("BankAccount not found in CallContext"))
        view         <- IO.fromOption(cc.view)(new RuntimeException("View not found in CallContext"))
        counterparty <- IO.fromOption(cc.counterparty)(new RuntimeException("Counterparty not found in CallContext"))
        result       <- RequestScopeConnection.fromFuture(f(user, bankAccount, view, counterparty, cc))
      } yield result
      io.attempt.flatMap {
        case Right(result) => toJsonOk(result).flatTap(recordMetric(result, _))
        case Left(err)     => ErrorResponseConverter.toHttp4sResponse(err, cc).flatTap(recordMetric(err.getMessage, _))
      }
    }

    /**
     * Execute Future-based business logic with error handling.
     * Returns 200 OK on success, converts errors via ErrorResponseConverter.
     * Takes a by-name Future (caller manages CallContext themselves).
     */
    def executeFuture[A](req: Request[IO])(f: => Future[A])(implicit formats: Formats): IO[Response[IO]] = {
      implicit val cc: CallContext = req.callContext
      RequestScopeConnection.fromFuture(f).attempt.flatMap {
        case Right(result) => toJsonOk(result).flatTap(recordMetric(result, _))
        case Left(err) => ErrorResponseConverter.toHttp4sResponse(err, cc).flatTap(recordMetric(err.getMessage, _))
      }
    }

    /**
     * Execute Future-based business logic with error handling.
     * Returns 201 Created on success, converts errors via ErrorResponseConverter.
     */
    def executeFutureCreated[A](req: Request[IO])(f: => Future[A])(implicit formats: Formats): IO[Response[IO]] = {
      implicit val cc: CallContext = req.callContext
      RequestScopeConnection.fromFuture(f).attempt.flatMap {
        case Right(result) =>
          val jsonString = prettyRender(Extraction.decompose(result))
          Created(jsonString, jsonContentType).flatTap(recordMetric(result, _))
        case Left(err) => ErrorResponseConverter.toHttp4sResponse(err, cc).flatTap(recordMetric(err.getMessage, _))
      }
    }

    /**
     * Execute Future-based business logic that returns a (result, statusCode) pair, rendering the
     * result JSON with the caller-supplied HTTP status. Converts errors via ErrorResponseConverter.
     *
     * Used by the native dynamic-endpoint proxy (code.api.dynamic.endpoint.Http4sDynamicEndpoint),
     * where the status code is dynamic — it comes from the backend connector / obp_mock response
     * (the `code` field), not a fixed 200/201. The caller has already built and attached the
     * CallContext (auth/role checks run inside `f`); on a thrown auth/role failure the `.attempt`
     * branch renders the correct 401/403 via ErrorResponseConverter, exactly like the other helpers.
     */
    def executeFutureWithStatus[A](req: Request[IO])(f: => Future[(A, Int)])(implicit formats: Formats): IO[Response[IO]] = {
      implicit val cc: CallContext = req.callContext
      RequestScopeConnection.fromFuture(f).attempt.flatMap {
        case Right((result, code)) =>
          val jsonString = prettyRender(Extraction.decompose(result))
          val status = Status.fromInt(code).getOrElse(Status.Ok)
          IO.pure(Response[IO](status).withEntity(jsonString).withContentType(jsonContentType)).flatTap(recordMetric(result, _))
        case Left(err) => ErrorResponseConverter.toHttp4sResponse(err, cc).flatTap(recordMetric(err.getMessage, _))
      }
    }

    /**
     * Execute DELETE business logic (no auth required).
     * Returns 204 No Content on success, converts errors via ErrorResponseConverter.
     */
    def executeDelete(req: Request[IO])(f: CallContext => Future[_]): IO[Response[IO]] = {
      implicit val cc: CallContext = req.callContext
      RequestScopeConnection.fromFuture(f(cc)).attempt.flatMap {
        case Right(_)  => NoContent().flatTap(recordMetric("", _))
        case Left(err) => ErrorResponseConverter.toHttp4sResponse(err, cc).flatTap(recordMetric(err.getMessage, _))
      }
    }

    /**
     * Execute DELETE business logic requiring validated User.
     * Returns 204 No Content on success, converts errors via ErrorResponseConverter.
     */
    def withUserDelete(req: Request[IO])(f: (User, CallContext) => Future[_]): IO[Response[IO]] = {
      implicit val cc: CallContext = req.callContext
      val io = for {
        user   <- IO.fromOption(cc.user.toOption)(new RuntimeException(AuthenticatedUserIsRequired))
        result <- RequestScopeConnection.fromFuture(f(user, cc))
      } yield result
      io.attempt.flatMap {
        case Right(_)  => NoContent().flatTap(recordMetric("", _))
        case Left(err) => ErrorResponseConverter.toHttp4sResponse(err, cc).flatTap(recordMetric(err.getMessage, _))
      }
    }

    /**
     * Execute DELETE business logic requiring validated User and Bank.
     * Returns 204 No Content on success, converts errors via ErrorResponseConverter.
     */
    def withUserAndBankDelete(req: Request[IO])(f: (User, Bank, CallContext) => Future[_]): IO[Response[IO]] = {
      implicit val cc: CallContext = req.callContext
      val io = for {
        user   <- IO.fromOption(cc.user.toOption)(new RuntimeException(AuthenticatedUserIsRequired))
        bank   <- IO.fromOption(cc.bank)(new RuntimeException("Bank not found in CallContext"))
        result <- RequestScopeConnection.fromFuture(f(user, bank, cc))
      } yield result
      io.attempt.flatMap {
        case Right(_)  => NoContent().flatTap(recordMetric("", _))
        case Left(err) => ErrorResponseConverter.toHttp4sResponse(err, cc).flatTap(recordMetric(err.getMessage, _))
      }
    }
  }
}

/**
 * Builds shared CallContext from http4s Request[IO].
 * 
 * This builder extracts all necessary request data and populates the shared CallContext,
 * enabling the existing authentication and validation code to work with http4s requests.
 */
object Http4sCallContextBuilder {
  
  /**
   * Build CallContext from http4s Request[IO]
   * Populates all fields needed by getUserAndSessionContextFuture
   * 
   * @param request The http4s request
   * @param apiVersion The API version string (e.g., "v7.0.0")
   * @return IO[CallContext] with all request data populated
   */
  def fromRequest(request: Request[IO], apiVersion: String): IO[CallContext] = {
    val noBody = Set(Method.GET, Method.DELETE, Method.HEAD, Method.OPTIONS)
    for {
      body <- request.attributes.lookup(Http4sRequestAttributes.cachedBodyKey) match {
        case Some(cached) => IO.pure(cached)
        case None =>
          if (noBody.contains(request.method)) IO.pure(None)
          else request.bodyText.compile.string.map(s => if (s.isEmpty) None else Some(s))
      }
    } yield CallContext(
      url = request.uri.renderString,
      verb = request.method.name,
      implementedInVersion = apiVersion,
      correlationId = extractCorrelationId(request),
      ipAddress = extractIpAddress(request),
      requestHeaders = extractHeaders(request),
      httpBody = body,
      authReqHeaderField = extractAuthHeader(request),
      directLoginParams = extractDirectLoginParams(request),
      startTime = Some(new Date())
    )
  }
  
  /**
   * Extract headers from http4s request and convert to List[HTTPParam]
   */
  private def extractHeaders(request: Request[IO]): List[HTTPParam] = {
    request.headers.headers.map { h =>
      HTTPParam(h.name.toString, List(h.value))
    }.toList
  }
  
  /**
   * Extract correlation ID from X-Request-ID header or generate a new UUID
   */
  private def extractCorrelationId(request: Request[IO]): String = {
    request.headers.get(CIString("X-Request-ID"))
      .map(_.head.value)
      .getOrElse(UUID.randomUUID().toString)
  }
  
  /**
   * Extract the trusted client IP. Defaults to the immediate socket peer; consults a
   * forwarded-for header only when `trust.proxy.enabled = true`. See [[RemoteIpUtil]].
   */
  private def extractIpAddress(request: Request[IO]): String = {
    val socketPeer = request.remoteAddr.map(_.toUriString).getOrElse("")
    RemoteIpUtil.resolveClientIp(
      socketPeer,
      name => request.headers.get(CIString(name)).map(_.head.value)
    )
  }
  
  /**
   * Extract Authorization header value as Box[String]
   */
  private def extractAuthHeader(request: Request[IO]): Box[String] = {
    request.headers.get(CIString("Authorization"))
      .map(h => Full(h.head.value))
      .getOrElse(Empty)
  }
  
  /**
   * Extract DirectLogin header parameters if present.
   * Supports two formats:
   * 1. New format (2021): `DirectLogin: token=xxx`                  (dedicated header)
   * 2. Old format (deprecated): `Authorization: DirectLogin token=xxx`
   *
   * Delegates the actual parsing of header values to [[AuthHeaderParser]] so
   * it stays consistent with other transports (e.g. gRPC).
   */
  private def extractDirectLoginParams(request: Request[IO]): Map[String, String] = {
    // Try new format first: DirectLogin header
    request.headers.get(CIString("DirectLogin"))
      .map(h => AuthHeaderParser.parseDirectLoginHeader(h.head.value))
      .getOrElse {
        // Fall back to old format: Authorization: DirectLogin token=xxx
        request.headers.get(CIString("Authorization"))
          .filter(_.head.value.contains("DirectLogin"))
          .map(h => AuthHeaderParser.parseDirectLoginHeader(h.head.value))
          .getOrElse(Map.empty)
      }
  }

}

/**
 * Matches http4s requests to ResourceDoc entries.
 * 
 * ResourceDoc entries use URL templates with uppercase variable names:
 * - BANK_ID, ACCOUNT_ID, VIEW_ID, COUNTERPARTY_ID
 * 
 * This matcher finds the corresponding ResourceDoc for a given request
 * and extracts path parameters.
 */
object ResourceDocMatcher extends code.util.Helper.MdcLoggable {

  // API prefix pattern: /<urlPrefix>/v<version> — handles both OBP (/obp/vX.X.X) and BG (/berlin-group/v1.3)
  private val apiPrefixPattern = """^/[^/]+/v[\d.]+""".r

  // Pre-built index type: (VERB, apiVersion, segmentCount) -> candidates
  type ResourceDocIndex = Map[(String, String, Int), List[ResourceDoc]]

  /**
   * Build a lookup index from a collection of ResourceDocs.
   * Call this once at middleware startup; pass the result to findResourceDoc.
   */
  def buildIndex(resourceDocs: Iterable[ResourceDoc]): ResourceDocIndex =
    resourceDocs.groupBy { doc =>
      val segCount = doc.requestUrl.split("/").count(_.nonEmpty)
      (doc.requestVerb.toUpperCase, doc.implementedInApiVersion.toString, segCount)
    }.map { case (k, v) => k -> v.toList }

  /**
   * Find ResourceDoc matching the given verb and path using a pre-built index.
   * O(1) map lookup + O(k) scan where k is the number of docs with the same
   * verb/version/segment-count (typically 1–3 in practice).
   *
   * @param verb         HTTP verb (GET, POST, PUT, DELETE, etc.)
   * @param path         Request path
   * @param index        Index built by buildIndex — create once, reuse per request
   * @return Option[ResourceDoc] if a match is found
   */
  def findResourceDoc(
    verb: String,
    path: Uri.Path,
    index: ResourceDocIndex
  ): Option[ResourceDoc] = {
    val pathString   = path.renderString
    val apiVersion   = pathString.split("/").filter(_.nonEmpty).drop(1).headOption.getOrElse("")
    val strippedPath = apiPrefixPattern.replaceFirstIn(pathString, "")
    val segCount     = strippedPath.split("/").count(_.nonEmpty)
    val lookupKey    = (verb.toUpperCase, apiVersion, segCount)
    val candidates   = index.getOrElse(lookupKey, Nil)
    val result       = candidates.find(doc => matchesUrlTemplate(strippedPath, doc.requestUrl))
    if (result.isEmpty) {
      logger.debug(
        s"[ResourceDocMatcher] No match for $verb $pathString. " +
        s"lookupKey=$lookupKey strippedPath='$strippedPath'. " +
        s"Candidates with that key: ${if (candidates.isEmpty) "(none)" else candidates.map(d => s"${d.requestVerb} ${d.requestUrl}(${d.implementedInApiVersion})").mkString(", ")}. " +
        s"Index keys for apiVersion=$apiVersion: ${index.keys.filter(_._2 == apiVersion).mkString(", ")}"
      )
    }
    result
  }

  /**
   * Find ResourceDoc matching the given verb and path.
   * Builds a transient index on every call — use for tests or one-off lookups.
   * For hot paths, prefer buildIndex + findResourceDoc(verb, path, index).
   *
   * @param verb         HTTP verb (GET, POST, PUT, DELETE, etc.)
   * @param path         Request path
   * @param resourceDocs Collection of ResourceDoc entries to search
   * @return Option[ResourceDoc] if a match is found
   */
  def findResourceDoc(
    verb: String,
    path: Uri.Path,
    resourceDocs: ArrayBuffer[ResourceDoc]
  ): Option[ResourceDoc] =
    findResourceDoc(verb, path, buildIndex(resourceDocs))
  
  /**
   * Check if a path matches a URL template
   * Template segments in uppercase are treated as variables
   */
  private def matchesUrlTemplate(path: String, template: String): Boolean = {
    val pathSegments = path.split("/").filter(_.nonEmpty)
    val templateSegments = template.split("/").filter(_.nonEmpty)
    
    if (pathSegments.length != templateSegments.length) {
      false
    } else {
      pathSegments.zip(templateSegments).forall { case (pathSeg, templateSeg) =>
        // Uppercase segments are variables (BANK_ID, ACCOUNT_ID, etc.)
        isTemplateVariable(templateSeg) || pathSeg == templateSeg
      }
    }
  }
  
  /**
   * Check if a template segment is a variable (uppercase)
   */
  /**
   * All-caps URL-segment literals that historically broke the matcher.
   *
   * `isTemplateVariable` originally returned true for every all-caps + underscore +
   * digit segment. That made literals like `SANDBOX_TAN`, `ACCOUNT`, `SEPA` etc.
   * indistinguishable from real placeholders like `BANK_ID`, so a ResourceDoc URL
   * `/banks/BANK_ID/.../transaction-request-types/SANDBOX_TAN/transaction-requests`
   * matched any trans-req-type URL — including v4-only `ACCOUNT` — and the v4
   * request never reached the Lift fallback that knows how to handle it.
   *
   * We special-case the known literal segments. Anything else stays a wildcard so
   * the existing non-standard placeholder convention (NEW_ACCOUNT_ID, GRANT_VIEW_ID,
   * FIREHOSE_BANK_ID, EXPLICIT_COUNTERPARTY_ID, SYS_VIEW_ID, …) keeps working
   * without an explicit allow-list.
   *
   * Add a value here when a new path uses an all-caps literal (e.g. a new
   * transaction-request type or SCA method).
   */
  private val literalAllCapsSegments: Set[String] = Set(
    // transaction-request types
    "SANDBOX_TAN", "COUNTERPARTY", "SEPA", "FREE_FORM",
    "ACCOUNT", "ACCOUNT_OTP", "REFUND", "SIMPLE",
    "AGENT_CASH_WITHDRAWAL", "CARD",
    // SCA methods (POST /banks/BANK_ID/my/consents/{EMAIL|SMS|IMPLICIT})
    "EMAIL", "SMS", "IMPLICIT", "NOT_EMAIL_NEITHER_SMS"
  )

  private def isTemplateVariable(segment: String): Boolean = {
    segment.nonEmpty &&
      segment.forall(c => c.isUpper || c == '_' || c.isDigit) &&
      !literalAllCapsSegments.contains(segment)
  }
  
  /**
   * Extract path parameters from matched ResourceDoc
   * 
   * @param path Request path
   * @param resourceDoc Matched ResourceDoc
   * @return Map with keys: BANK_ID, ACCOUNT_ID, VIEW_ID, COUNTERPARTY_ID (if present)
   */
  def extractPathParams(
    path: Uri.Path,
    resourceDoc: ResourceDoc
  ): Map[String, String] = {
    val pathString = path.renderString
    // Strip the API prefix (/obp/vX.X.X) from the path for matching
    val strippedPath = apiPrefixPattern.replaceFirstIn(pathString, "")
    val pathSegments = strippedPath.split("/").filter(_.nonEmpty)
    val templateSegments = resourceDoc.requestUrl.split("/").filter(_.nonEmpty)
    
    if (pathSegments.length != templateSegments.length) {
      Map.empty
    } else {
      pathSegments.zip(templateSegments).collect {
        case (pathSeg, templateSeg) if isTemplateVariable(templateSeg) =>
          templateSeg -> pathSeg
      }.toMap
    }
  }
  
  /**
   * Update CallContext with matched ResourceDoc
   * MUST be called after successful match for metrics/rate limiting consistency
   * 
   * @param callContext Current CallContext
   * @param resourceDoc Matched ResourceDoc
   * @return Updated CallContext with resourceDocument and operationId set
   */
  def attachToCallContext(
    callContext: CallContext,
    resourceDoc: ResourceDoc
  ): CallContext = {
    callContext.copy(
      resourceDocument = Some(resourceDoc),
      operationId = Some(resourceDoc.operationId)
    )
  }
}

/**
 * Http4s configuration utilities.
 */
object Http4sConfigUtil {
  
  /**
   * Parse hostname from a string that may be either a plain host or a full URI.
   * 
   * Supports both formats:
   * - Plain host: "127.0.0.1" or "localhost"
   * - Full URI: "http://127.0.0.1:8080" or "https://example.com"
   * 
   * @param hostnameValue The hostname or URI string to parse
   * @return The extracted hostname/IP address
   * 
   * Examples:
   * {{{
   * parseHostname("127.0.0.1")                  // Returns: "127.0.0.1"
   * parseHostname("http://127.0.0.1:8080")      // Returns: "127.0.0.1"
   * parseHostname("https://api.example.com")    // Returns: "api.example.com"
   * parseHostname("localhost")                  // Returns: "localhost"
   * }}}
   */
  def parseHostname(hostnameValue: String): String = {
    val trimmed = hostnameValue.trim
    // Try to parse as URI first
    Uri.fromString(trimmed).toOption
      .flatMap(_.host.map(_.renderString))
      .getOrElse(trimmed) // If not a valid URI, use as-is
  }
}
