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
 * Scope: the key is namespaced by SHA-256 of (consumer id, or — when
 * unauthenticated — the Authorization header) AND the resolved operation id. This prevents key
 * reuse across consumers AND across endpoints: a client accidentally or deliberately reusing one
 * Idempotency-Key against two different operations gets two independent dedup slots instead of
 * the second operation being treated as a replay of the first and never executed.
 *
 * Validation: 8..255 printable-ASCII characters.  Anything else → 400, regardless of whether this
 * tier serves the path -- a malformed header is a client error no matter where it resolves.
 *
 * Storage: Redis via the existing JedisPool.  Two keys per request:
 *   - idem:lock:<scope>:<key> → "1" (60s TTL, set with NX)
 *   - idem:resp:<scope>:<key> → JSON envelope (24h TTL)
 *
 * Resilience: any Redis error is logged and the request is allowed to proceed
 * unchanged — the middleware never blocks traffic on cache outages.
 *
 * ── Where it may be installed ──
 *
 * INSIDE ResourceDocMiddleware, on every version's route tree. Both halves matter.
 *
 * Inside, because the scope key and the request-body hash both come from the CallContext, and
 * ResourceDocMiddleware is what populates it. Mounted outside, there is no CallContext at all --
 * no operationId and no httpBody -- so `matchedThisTier` is false for every request and this
 * middleware does nothing at all rather than mishandling anything: no lock, no cached response,
 * no conflict detection. That is a silent loss of protection, not a silent corruption, but it is
 * still a config-only guard against a real risk: on a payment endpoint, dedup quietly not running
 * is the difference between "your retry was deduplicated" and "your retry submitted the payment
 * again".
 *
 * On every tree, because Http4sApp composes the versions with `.orElse` and a tree signals "not
 * mine" by returning OptionT.none. This middleware therefore has to pass a miss through
 * unchanged; an earlier version answered 404 in that case, which terminated the chain -- measured
 * on POST /obp/v3.1.0/management/method_routings, which answered 201 without an Idempotency-Key
 * and 404 with one. A later version preserved the miss but still spent a full lock-acquire/release
 * cycle doing it, using a method+path fallback scope for any tier that had not matched -- which
 * meant two requests racing that SAME miss-tier's fallback lock (two genuinely different
 * endpoints whose method+path fallback happened to differ less often than intended, or two
 * concurrent copies of the request destined for a LATER tier) could have the loser answered a
 * definite 409 by a tier that was never going to serve either of them, before the request ever
 * reached the tier that would have handled it correctly. `matchedThisTier` closes that: only the
 * one tier whose ResourceDocMatcher actually matched (the only tier where operationId is ever
 * set) does any lock/response-key work at all. IdempotencyMiddlewareTest pins all of these.
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
          // A malformed header is a client error whatever the path resolves to, so this
          // one deliberately does NOT fall through.
          OptionT.liftF(invalidKeyResponse(key))
        } else if (!matchedThisTier(req)) {
          // operationId is set on the CallContext ONLY by ResourceDocMiddleware.attachToCallContext,
          // and only for the one version tree whose ResourceDocMatcher actually matched this
          // request (see ResourceDocMiddleware.apply: the `case None` branch never attaches one).
          // Every other tree in the `.orElse` chain is about to answer a miss regardless of what
          // this middleware does, so doing lock/response-key work there is worse than wasted: two
          // requests that legitimately belong to two DIFFERENT trees, or two genuinely concurrent
          // copies of the same request, can race the SAME miss-tier's method+path-fallback lock
          // and the loser gets a definite 409 here -- terminating the `.orElse` chain on behalf of
          // a tree that was never going to serve either of them, before the request ever reaches
          // the tree that would have handled it correctly. Skipping straight through at a
          // miss-tier removes both the wasted Redis round trips and this false-conflict window;
          // only the matching tier ever computes a scope key that means anything durable, so only
          // it needs the protection.
          routes.run(req)
        } else {
          val scope        = scopeFor(req)
          val bodyHash     = sha256Hex(bodyFromCallContextOrEmpty(req))
          val responseKey  = ResponseKeyPrefix + scope + ":" + key
          val lockKey      = LockKeyPrefix + scope + ":" + key

          // OptionT, not OptionT.liftF: a route MISS has to stay a miss.
          //
          // Every version's routes are one link in a fallthrough chain -- Http4sApp composes them
          // with `.orElse`, and `OptionT.none` is how a tree says "not mine, try the next one".
          // Wrapping with liftF made this middleware answer 404 on behalf of a tree that simply
          // did not serve the path, which terminated the chain: measured on
          // `POST /obp/v3.1.0/management/method_routings`, the request answered 201 without an
          // Idempotency-Key and 404 with one, because the first tree it passed through swallowed
          // the miss. So the middleware could only ever be installed on the last link. Preserving
          // the miss is what makes it safe to install on all of them.
          OptionT(handle(req, routes, responseKey, lockKey, bodyHash))
        }
      }
    }

  private def handle(
    req: Request[IO],
    routes: HttpRoutes[IO],
    responseKey: String,
    lockKey: String,
    requestBodyHash: String
  ): IO[Option[Response[IO]]] = {
    IO.blocking(readResponseKey(responseKey)).attempt.flatMap {
      case Right(Some(envelope)) =>
        if (envelope.requestBodyHash == requestBodyHash) {
          IO.pure(Some(rebuildResponse(envelope, replay = true)))
        } else {
          conflictResponse(
            "Idempotency-Key replayed with a different request body. " +
            "Use a fresh key for a different request."
          ).map(Some(_))
        }

      case Right(None) =>
        IO.blocking(tryAcquireLock(lockKey)).attempt.flatMap {
          case Right(true) =>
            runAndCache(req, routes, responseKey, lockKey, requestBodyHash)
          case Right(false) =>
            conflictResponse(
              "Idempotent operation already in flight for this Idempotency-Key."
            ).map(Some(_))
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
  ): IO[Option[Response[IO]]] = {
    runRoutes(req, routes).flatMap {
      // The lock was taken before the routes ran, so a miss has to give it back -- otherwise a
      // path this tree does not serve would hold the key locked for its full 60s TTL and a
      // genuine request carrying that key would be refused with 409.
      case None => IO.blocking(deleteKey(lockKey)).attempt.as(None)
      case Some(resp) =>
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
          storeOrReleaseLock.as(Some(rebuilt))
        }
    }
  }

  // `.value`, not `getOrElseF(404)` -- see the comment on the OptionT in `apply`. Converting a
  // miss into a 404 here is what terminated the version fallthrough chain.
  private def runRoutes(req: Request[IO], routes: HttpRoutes[IO]): IO[Option[Response[IO]]] =
    routes.run(req).value

  // ── Validation ─────────────────────────────────────────────────────────

  private def isValidKey(key: String): Boolean =
    key.length >= MinKeyLength &&
    key.length <= MaxKeyLength &&
    key.forall(c => c >= 0x21 && c <= 0x7E)

  // True only for the one version tree whose ResourceDocMatcher matched this request --
  // ResourceDocMiddleware.attachToCallContext is the sole place operationId is ever set, and it
  // runs only on a match (see ResourceDocMiddleware.apply's `case Some(resourceDoc)` branch; the
  // `case None` branch attaches a CallContext with no operationId). Every other tree in the
  // `.orElse` chain sees operationId absent here and skips idempotency handling entirely, because
  // it is about to answer a miss regardless of what this middleware does.
  private def matchedThisTier(req: Request[IO]): Boolean =
    req.attributes.lookup(Http4sRequestAttributes.callContextKey).exists(_.operationId.isDefined)

  // ── Scope ──────────────────────────────────────────────────────────────

  private def scopeFor(req: Request[IO]): String = {
    val ccOpt = req.attributes.lookup(Http4sRequestAttributes.callContextKey)
    val consumerOrAuth = ccOpt
      .flatMap(_.consumer.map(_.consumerId.get).toOption)
      .filter(_.nonEmpty)
      .orElse(req.headers.get(AuthorizationHeader).map(_.head.value))
      .getOrElse("anonymous")
    // operationId is the canonical identity of "which endpoint" -- set by ResourceDocMiddleware
    // once it has matched a ResourceDoc, and stable across path-template placeholders and
    // bridge-cascade path rewrites (v400->v310->...). scopeFor only ever runs after
    // matchedThisTier has confirmed operationId is present, so the method+path fallback below is
    // purely defensive -- it should never actually be exercised in production, only under a
    // future bug that calls scopeFor without that guard.
    val endpoint = ccOpt
      .flatMap(_.operationId)
      .getOrElse(s"${req.method.name} ${req.uri.path.renderString}")
    sha256Hex(s"$consumerOrAuth|$endpoint").take(16)
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
