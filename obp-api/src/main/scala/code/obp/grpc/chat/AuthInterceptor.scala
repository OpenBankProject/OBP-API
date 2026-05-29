package code.obp.grpc.chat

import code.api.util.{APIUtil, AuthHeaderParser, CallContext}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.User
import io.grpc._
import net.liftweb.common.Full
import code.api.util.APIUtil.HTTPParam

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * gRPC ServerInterceptor that authenticates requests using OBP's existing auth chain.
 *
 * Reads the "authorization" key from gRPC Metadata (same format as HTTP headers:
 * "DirectLogin token=..." or "Bearer ..."), validates it using the same auth logic
 * as REST endpoints, and stores the authenticated User in gRPC Context.
 *
 * Token is validated once at stream open, not per-message.
 */
object AuthInterceptor {
  val USER_CONTEXT_KEY: Context.Key[User] = Context.key("obp-user")
  val CALL_CONTEXT_KEY: Context.Key[CallContext] = Context.key("obp-call-context")

  private val AUTH_METADATA_KEY: Metadata.Key[String] =
    Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)
}

class AuthInterceptor extends ServerInterceptor with MdcLoggable {

  import AuthInterceptor._

  override def interceptCall[ReqT, RespT](
    call: ServerCall[ReqT, RespT],
    headers: Metadata,
    next: ServerCallHandler[ReqT, RespT]
  ): ServerCall.Listener[ReqT] = {

    // Skip auth for gRPC reflection — allow unauthenticated service discovery
    val serviceName = call.getMethodDescriptor.getServiceName
    if (serviceName == "grpc.reflection.v1alpha.ServerReflection" || serviceName == "grpc.reflection.v1.ServerReflection") {
      return Contexts.interceptCall(Context.current(), call, headers, next)
    }

    val authHeader = Option(headers.get(AUTH_METADATA_KEY))

    authHeader match {
      case None =>
        logger.info("AuthInterceptor says: No authorization header in gRPC metadata")
        call.close(Status.UNAUTHENTICATED.withDescription("Missing authorization header"), new Metadata())
        new ServerCall.Listener[ReqT]() {}

      case Some(authValue) =>
        try {
          // Populate the auth-related CallContext fields via the shared parser
          // so the gRPC auth path matches what the REST (http4s) path produces.
          // The downstream auth chain reads authReqHeaderField / directLoginParams
          // — not requestHeaders — to pick a scheme.
          val parsed = AuthHeaderParser.parseAuthorizationHeader(Some(authValue))
          val cc = CallContext(
            requestHeaders = List(HTTPParam("Authorization", List(authValue))),
            authReqHeaderField = parsed.authReqHeaderField,
            directLoginParams = parsed.directLoginParams,
            verb = "GET",
            url = "/grpc/chat",
            implementedInVersion = "v6.0.0",
            correlationId = APIUtil.generateUUID()
          )

          val future = APIUtil.getUserAndSessionContextFuture(cc)
          val (userBox, callContextOption) = Await.result(future, 30.seconds)

          userBox match {
            case Full(user) =>
              val updatedCallContext = callContextOption.getOrElse(cc)
              val ctx = Context.current()
                .withValue(USER_CONTEXT_KEY, user)
                .withValue(CALL_CONTEXT_KEY, updatedCallContext)
              Contexts.interceptCall(ctx, call, headers, next)

            case _ =>
              logger.info("AuthInterceptor says: Auth validation returned no user")
              call.close(Status.UNAUTHENTICATED.withDescription("Invalid or expired token"), new Metadata())
              new ServerCall.Listener[ReqT]() {}
          }

        } catch {
          case e: Throwable =>
            logger.error(s"AuthInterceptor says: Auth validation failed: ${e.getMessage}")
            call.close(Status.UNAUTHENTICATED.withDescription("Authentication failed"), new Metadata())
            new ServerCall.Listener[ReqT]() {}
        }
    }
  }
}
