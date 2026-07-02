package code.api.util.http4s

import org.json4s._
import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.cache.Redis
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.JsonAliases.{compactRender, parse}
import org.json4s.JsonAST.{JField, JInt, JObject, JString}
import org.json4s.JsonDSL._
import org.http4s._
import org.http4s.headers.`Content-Type`
import org.typelevel.ci.CIString
import redis.clients.jedis.Jedis

import java.security.MessageDigest
import java.util.Base64

/**
 * Idempotency middleware for http4s mutating requests.
 *
 * Clients opt in by sending an `Idempotency-Key: <token>` header on POST, PUT,
 * PATCH or DELETE.  GET, HEAD and OPTIONS are unaffected.
 *
 * Behaviour:
 *   - First request with a given (consumer, key): runs normally; the response
 *     status, content-type, body and request-body hash are cached for 24h.
 *   - Replay with same key + same body hash: cached response is returned with
 *     header `Idempotency-Replay: true` (Stripe convention).
 *   - Replay with same key + different body hash: 409 Conflict.
 *   - Concurrent replay while the original is still in flight: 409 Conflict.
 *   - 5xx responses are NOT cached; clients can retry.
 *
 * Scope: the key is namespaced by SHA-256 of the consumer id, or — when
 * unauthenticated — the Authorization header.  This prevents key reuse across
 * consumers.
 *
 * Validation: 8..255 printable-ASCII characters.  Anything else → 400.
 *
 * Storage: Redis via the existing JedisPool.  Two keys per request:
 *   - idem:lock:<scope>:<key> → "1" (60s TTL, set with NX)
 *   - idem:resp:<scope>:<key> → JSON envelope (24h TTL)
 *
 * Resilience: any Redis error is logged and the request is allowed to proceed
 * unchanged — the middleware never blocks traffic on cache outages.
 *
 * The middleware should be installed INSIDE ResourceDocMiddleware so the
 * CallContext (and therefore the consumer id) is populated before the scope
 * key is computed.
 */
object IdempotencyMiddleware extends MdcLoggable {

  type HttpF[A] = OptionT[IO, A]

  private val IdempotencyKeyHeader    = CIString("Idempotency-Key")
  private val IdempotencyReplayHeader = CIString("Idempotency-Replay")
  private val AuthorizationHeader     = CIString("Authorization")

  private val MutatingMethods: Set[Method] =
    Set(Method.POST, Method.PUT, Method.PATCH, Method.DELETE)

  private val LockTtlSeconds: Int     = 60
  private val ResponseTtlSeconds: Int = 24 * 60 * 60

  private val MinKeyLength = 8
  private val MaxKeyLength = 255

  private val LockKeyPrefix     = "idem:lock:"
  private val ResponseKeyPrefix = "idem:resp:"

  /**
   * Wrap routes so that mutating requests carrying an Idempotency-Key header
   * are deduplicated.
   */
  def apply(routes: HttpRoutes[IO]): HttpRoutes[IO] =
    Kleisli[HttpF, Request[IO], Response[IO]] { req =>
      val keyOpt = req.headers.get(IdempotencyKeyHeader).map(_.head.value)

      if (!MutatingMethods.contains(req.method) || keyOpt.isEmpty) {
        routes.run(req)
      } else {
        val key = keyOpt.get
        if (!isValidKey(key)) {
          OptionT.liftF(invalidKeyResponse(key))
        } else {
          val scope        = scopeFor(req)
          val bodyHash     = sha256Hex(bodyFromCallContextOrEmpty(req))
          val responseKey  = ResponseKeyPrefix + scope + ":" + key
          val lockKey      = LockKeyPrefix + scope + ":" + key

          OptionT.liftF(handle(req, routes, responseKey, lockKey, bodyHash))
        }
      }
    }

  private def handle(
    req: Request[IO],
    routes: HttpRoutes[IO],
    responseKey: String,
    lockKey: String,
    requestBodyHash: String
  ): IO[Response[IO]] = {
    IO.blocking(readResponseKey(responseKey)).attempt.flatMap {
      case Right(Some(envelope)) =>
        if (envelope.requestBodyHash == requestBodyHash) {
          IO.pure(rebuildResponse(envelope, replay = true))
        } else {
          conflictResponse(
            "Idempotency-Key replayed with a different request body. " +
            "Use a fresh key for a different request."
          )
        }

      case Right(None) =>
        IO.blocking(tryAcquireLock(lockKey)).attempt.flatMap {
          case Right(true) =>
            runAndCache(req, routes, responseKey, lockKey, requestBodyHash)
          case Right(false) =>
            conflictResponse(
              "Idempotent operation already in flight for this Idempotency-Key."
            )
          case Left(t) =>
            logger.warn(s"Idempotency lock unavailable (Redis): ${t.getMessage}")
            runRoutes(req, routes)
        }

      case Left(t) =>
        logger.warn(s"Idempotency cache unavailable (Redis): ${t.getMessage}")
        runRoutes(req, routes)
    }
  }

  private def runAndCache(
    req: Request[IO],
    routes: HttpRoutes[IO],
    responseKey: String,
    lockKey: String,
    requestBodyHash: String
  ): IO[Response[IO]] = {
    runRoutes(req, routes).flatMap { resp =>
      // Drain body so we can both cache and re-emit it.
      resp.body.compile.toVector.flatMap { vec =>
        val bodyBytes = vec.toArray
        val rebuilt   = resp.withBodyStream(fs2.Stream.emits(bodyBytes).covary[IO])

        val storeOrReleaseLock: IO[Unit] =
          if (resp.status.code >= 500) {
            // Don't cache transient failures; release the lock so client can retry.
            IO.blocking(deleteKey(lockKey)).attempt.map(_ => ())
          } else {
            val envelope = Envelope(
              status          = resp.status.code,
              contentType     = resp.headers.get(CIString("Content-Type")).map(_.head.value),
              bodyB64         = Base64.getEncoder.encodeToString(bodyBytes),
              requestBodyHash = requestBodyHash
            )
            IO.blocking {
              writeResponseKey(responseKey, envelope)
              deleteKey(lockKey)
            }.attempt.map { e =>
              e.left.foreach(t =>
                logger.warn(s"Failed to cache idempotent response: ${t.getMessage}")
              )
              ()
            }
          }
        storeOrReleaseLock.as(rebuilt)
      }
    }
  }

  private def runRoutes(req: Request[IO], routes: HttpRoutes[IO]): IO[Response[IO]] =
    routes.run(req).getOrElseF(IO.pure(Response[IO](Status.NotFound)))

  // ── Validation ─────────────────────────────────────────────────────────

  private def isValidKey(key: String): Boolean =
    key.length >= MinKeyLength &&
    key.length <= MaxKeyLength &&
    key.forall(c => c >= 0x21 && c <= 0x7E)

  // ── Scope ──────────────────────────────────────────────────────────────

  private def scopeFor(req: Request[IO]): String = {
    val ccOpt = req.attributes.lookup(Http4sRequestAttributes.callContextKey)
    val raw = ccOpt
      .flatMap(_.consumer.map(_.consumerId.get).toOption)
      .filter(_.nonEmpty)
      .orElse(req.headers.get(AuthorizationHeader).map(_.head.value))
      .getOrElse("anonymous")
    sha256Hex(raw).take(16)
  }

  // ── Body hash ──────────────────────────────────────────────────────────

  private def bodyFromCallContextOrEmpty(req: Request[IO]): String =
    req.attributes
      .lookup(Http4sRequestAttributes.callContextKey)
      .flatMap(_.httpBody)
      .getOrElse("")

  private def sha256Hex(s: String): String = {
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(s.getBytes("UTF-8"))
    bytes.map(b => f"$b%02x").mkString
  }

  // ── Responses ──────────────────────────────────────────────────────────

  private def invalidKeyResponse(key: String): IO[Response[IO]] = {
    val body = compactRender(
      ("code" -> 400) ~
      ("message" ->
        s"Invalid Idempotency-Key header: must be ${MinKeyLength}..${MaxKeyLength} printable ASCII characters.")
    )
    IO.pure(
      Response[IO](Status.BadRequest)
        .withEntity(body)
        .withContentType(`Content-Type`(MediaType.application.json))
    )
  }

  private def conflictResponse(message: String): IO[Response[IO]] = {
    val body = compactRender(("code" -> 409) ~ ("message" -> message))
    IO.pure(
      Response[IO](Status.Conflict)
        .withEntity(body)
        .withContentType(`Content-Type`(MediaType.application.json))
    )
  }

  private def rebuildResponse(env: Envelope, replay: Boolean): Response[IO] = {
    val bytes = Base64.getDecoder.decode(env.bodyB64)
    val status = Status.fromInt(env.status).getOrElse(Status.Ok)
    val base   = Response[IO](status).withBodyStream(fs2.Stream.emits(bytes).covary[IO])
    val withCt = env.contentType
      .flatMap(v => `Content-Type`.parse(v).toOption)
      .fold(base)(ct => base.withContentType(ct))
    if (replay) withCt.putHeaders(Header.Raw(IdempotencyReplayHeader, "true"))
    else withCt
  }

  // ── Storage envelope ───────────────────────────────────────────────────

  private final case class Envelope(
    status: Int,
    contentType: Option[String],
    bodyB64: String,
    requestBodyHash: String
  )

  private def envelopeToJson(env: Envelope): String = {
    val obj =
      ("status" -> env.status) ~
      ("content_type" -> env.contentType) ~
      ("body_b64" -> env.bodyB64) ~
      ("request_body_hash" -> env.requestBodyHash)
    compactRender(obj)
  }

  private def envelopeFromJson(s: String): Option[Envelope] =
    try {
      parse(s) match {
        case JObject(fields) =>
          val map = fields.collect { case JField(k, v) => k -> v }.toMap
          for {
            status <- map.get("status").collect { case JInt(i) => i.toInt }
            body   <- map.get("body_b64").collect { case JString(v) => v }
            hash   <- map.get("request_body_hash").collect { case JString(v) => v }
          } yield Envelope(
            status          = status,
            contentType     = map.get("content_type").collect { case JString(v) => v },
            bodyB64         = body,
            requestBodyHash = hash
          )
        case _ => None
      }
    } catch {
      case t: Throwable =>
        logger.warn(s"Failed to parse idempotency envelope: ${t.getMessage}")
        None
    }

  // ── Redis primitives (sync; called from IO.blocking) ───────────────────

  private def withJedis[A](f: Jedis => A): A = {
    val jedis = Redis.jedisPool.getResource
    try f(jedis)
    finally jedis.close()
  }

  private def readResponseKey(key: String): Option[Envelope] =
    withJedis { j =>
      Option(j.get(key)).flatMap(envelopeFromJson)
    }

  // First-write-wins via atomic SET NX EX: once a response is cached for an idempotency key it is
  // immutable for its TTL, so a second concurrent response cannot clobber the first (which a replay
  // would then return). Plain setex overwrites unconditionally.
  private def writeResponseKey(key: String, env: Envelope): Unit = {
    Redis.setNxEx(key, envelopeToJson(env), ResponseTtlSeconds)
    ()
  }

  /**
   * Atomic SET NX EX: acquire the lock and set its TTL in one command. Unlike setnx + a separate
   * expire, there is no window in which the key exists without a TTL, so a crash mid-acquire can
   * never orphan the lock and permanently block retries of that idempotency key.
   */
  private def tryAcquireLock(key: String): Boolean =
    Redis.setNxEx(key, "1", LockTtlSeconds)

  private def deleteKey(key: String): Unit =
    withJedis { j =>
      j.del(key)
      ()
    }
}
