package code.api.UKOpenBanking.v4_0_1

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import code.api.util.APIUtil.{EmptyBody, ResourceDoc}
import code.api.util.ApiTag
import code.api.util.CustomJsonFormats
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, UnknownError}
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.util.Helper.MdcLoggable
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}
import com.openbankproject.commons.util.JsonAliases
import org.json4s.{Formats, JObject}
import org.http4s._
import org.http4s.dsl.io._
import com.openbankproject.commons.ExecutionContext.Implicits.global

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

// AUTO-GENERATED from UK Open Banking read-write-api-specs v4.0.1 (Events).
// Spec-faithful scaffold: routes return synthesized example JSON from the
// OpenAPI schemas (the specs carry no examples). Deepen to real OBP
// connector logic per endpoint later, mirroring v3_1_0.
object Http4sUKOBv401Events extends MdcLoggable {
  type HttpF[A] = OptionT[IO, A]
  implicit val formats: Formats = CustomJsonFormats.formats
  val implementedInApiVersion: ScannedApiVersion = ApiVersion.ukOpenBankingV401
  val resourceDocs = ArrayBuffer[ResourceDoc]()
  private def parseBody(s: String): JObject = JsonAliases.parse(s).asInstanceOf[JObject]
  val ukV401Prefix = Root / ApiVersion.ukOpenBankingV401.urlPrefix / ApiVersion.ukOpenBankingV401.apiShortVersion

  private val EX_getEventSubscriptions: String = """{
  "Data": {
    "EventSubscription": [
      {
        "EventSubscriptionId": "string",
        "CallbackUrl": "string",
        "Version": "string",
        "EventTypes": [
          "string"
        ]
      }
    ]
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val getEventSubscriptions: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `ukV401Prefix` / "event-subscriptions" =>
      EndpointHelpers.withUser(req) { (u, cc) => Future.successful(parseBody(EX_getEventSubscriptions)) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getEventSubscriptions),
    "GET",
    "/event-subscriptions",
    "Get an Event Subscription",
    """Enables a TPP to retrieve details of its event notifications subscription.""",
    EmptyBody,
    parseBody(EX_getEventSubscriptions),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Event Subscriptions") :: Nil,
    http4sPartialFunction = Some(getEventSubscriptions)
  )

  private val EXREQ_createEventSubscriptions: String = """{
  "Data": {
    "CallbackUrl": "string",
    "Version": "string",
    "EventTypes": [
      "string"
    ]
  }
}"""
  private val EX_createEventSubscriptions: String = """{
  "Data": {
    "EventSubscriptionId": "string",
    "CallbackUrl": "string",
    "Version": "string",
    "EventTypes": [
      "string"
    ]
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val createEventSubscriptions: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV401Prefix` / "event-subscriptions" =>
      EndpointHelpers.executeFutureCreated(req)(Future.successful(parseBody(EX_createEventSubscriptions)))
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createEventSubscriptions),
    "POST",
    "/event-subscriptions",
    "Create an Event Subscription",
    """Enables a TPP to subscribe to events notifications with an ASPSP.""",
    parseBody(EXREQ_createEventSubscriptions),
    parseBody(EX_createEventSubscriptions),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Event Subscriptions") :: Nil,
    http4sPartialFunction = Some(createEventSubscriptions)
  )

  private val EXREQ_changeEventSubscriptionsEventSubscriptionId: String = """{
  "Data": {
    "EventSubscriptionId": "string",
    "CallbackUrl": "string",
    "Version": "string",
    "EventTypes": [
      "string"
    ]
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  private val EX_changeEventSubscriptionsEventSubscriptionId: String = """{
  "Data": {
    "EventSubscriptionId": "string",
    "CallbackUrl": "string",
    "Version": "string",
    "EventTypes": [
      "string"
    ]
  },
  "Links": {
    "Self": "string",
    "First": "string",
    "Prev": "string",
    "Next": "string",
    "Last": "string"
  },
  "Meta": {
    "TotalPages": 0,
    "FirstAvailableDateTime": "2020-01-01T00:00:00+00:00",
    "LastAvailableDateTime": "2020-01-01T00:00:00+00:00"
  }
}"""
  lazy val changeEventSubscriptionsEventSubscriptionId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ PUT -> `ukV401Prefix` / "event-subscriptions" / eventSubscriptionId =>
      EndpointHelpers.executeFutureCreated(req)(Future.successful(parseBody(EX_changeEventSubscriptionsEventSubscriptionId)))
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(changeEventSubscriptionsEventSubscriptionId),
    "PUT",
    "/event-subscriptions/EVENT_SUBSCRIPTION_ID",
    "Update an Event Subscription",
    """Enables a TPP to ask an ASPSP to update its events notifications subscription.""",
    parseBody(EXREQ_changeEventSubscriptionsEventSubscriptionId),
    parseBody(EX_changeEventSubscriptionsEventSubscriptionId),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Event Subscriptions") :: Nil,
    http4sPartialFunction = Some(changeEventSubscriptionsEventSubscriptionId)
  )

  private val EX_deleteEventSubscriptionsEventSubscriptionId: String = """{}"""
  lazy val deleteEventSubscriptionsEventSubscriptionId: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ DELETE -> `ukV401Prefix` / "event-subscriptions" / eventSubscriptionId =>
      EndpointHelpers.executeDelete(req) { cc => Future.successful(()) }
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(deleteEventSubscriptionsEventSubscriptionId),
    "DELETE",
    "/event-subscriptions/EVENT_SUBSCRIPTION_ID",
    "Delete an Event Subscription",
    """Enables a TPP to ask an ASPSP to unsubscribe from events notifications.""",
    EmptyBody,
    parseBody(EX_deleteEventSubscriptionsEventSubscriptionId),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Event Subscriptions") :: Nil,
    http4sPartialFunction = Some(deleteEventSubscriptionsEventSubscriptionId)
  )

  private val EXREQ_createEvents: String = """{
  "maxEvents": 0,
  "returnImmediately": true,
  "ack": [
    "string"
  ],
  "setErrs": {}
}"""
  private val EX_createEvents: String = """{
  "moreAvailable": true,
  "sets": {}
}"""
  lazy val createEvents: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV401Prefix` / "events" =>
      EndpointHelpers.executeFutureCreated(req)(Future.successful(parseBody(EX_createEvents)))
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createEvents),
    "POST",
    "/events",
    "Create Events",
    """Enables a TPP to poll for, acknowledge, and receive event notifications.""",
    parseBody(EXREQ_createEvents),
    parseBody(EX_createEvents),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Events") :: Nil,
    http4sPartialFunction = Some(createEvents)
  )

  val routes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    getEventSubscriptions(req)
      .orElse(createEventSubscriptions(req)
      .orElse(changeEventSubscriptionsEventSubscriptionId(req)
      .orElse(deleteEventSubscriptionsEventSubscriptionId(req)
      .orElse(createEvents(req)))))
  }
}
