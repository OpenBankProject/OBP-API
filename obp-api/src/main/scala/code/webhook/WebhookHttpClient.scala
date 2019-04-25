package code.webhook

import akka.actor.ActorSystem
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.util.ByteString
import code.actorsystem.ObpLookupSystem
import code.api.util.{ApiTrigger, CustomJsonFormats}
import code.api.util.ApiTrigger.{OnBalanceChange, OnCreditTransaction, OnDebitTransaction}
import code.util.Helper.MdcLoggable
import code.webhook.WebhookActor.{WebhookFailure, WebhookRequest, WebhookResponse}
import net.liftweb
import net.liftweb.json.Extraction
import net.liftweb.mapper.By

import scala.concurrent.Future
import scala.util.{Failure, Success}


object WebhookHttpClient extends MdcLoggable {

  /**
    * This function starts the webhook event for instance ApiTrigger.onBalanceChange.
    * For the whole list of supported webhook events please take a look at a file code.api.util.ApiTrigger
    *
    * @param request is a message which provide all necessary data of the event
    * @return we do not return anything because we use fire and forget scenario 
    *         but we will still send a result to the Actor in the end.
    *         I.e. we trigger some webhook's event with:
    *         1. actor ! WebhookActor.WebhookRequest(
    *             ApiTrigger.onBalanceChange,
    *             eventId,
    *             t.theBankId.value,
    *             t.theAccountId.value,
    *             getAmount(t.amount.get),
    *             getAmount(t.newAccountBalance.get)
    *           )
    *           and then send result of event to the Actor:
    *         2. requestActor ! WebhookResponse(res.status.toString(), request)
    */
  def startEvent(request: WebhookRequest): List[Unit] = {
    
    logEvent(request)

    MappedAccountWebhook.findAll(
      By(MappedAccountWebhook.mIsActive, true), 
      By(MappedAccountWebhook.mBankId, request.bankId), 
      By(MappedAccountWebhook.mAccountId, request.accountId),
      By(MappedAccountWebhook.mTriggerName, request.trigger.toString())
    ) map {
      i => makeRequest(getHttpRequest(i.url, i.httpMethod, i.httpProtocol, getEventPayload(request)), request)
    }
  }

  /**
    * This function makes payload for POST/PUT/DELETE HTTP calls. For instance:
    * {
    *   "event_name":"OnCreditTransaction",
    *   "event_id":"fc7e4a71-5ff1-4006-95bb-7fd9e4adaef9", 
    *   "bank_id":"gh.29.uk.x", 
    *   "account_id":"marko_privite_01", 
    *   "amount":"50.00 EUR", 
    *   "balance":"739.00 EUR"
    * }
    * 
    */
  private def getEventPayload(request: WebhookRequest): RequestEntity = {
    request.trigger match {
      case OnBalanceChange() | OnCreditTransaction() | OnDebitTransaction() =>
        implicit val formats = CustomJsonFormats.formats
        val json = liftweb.json.compactRender(Extraction.decompose(request.toEventPayload))
        val entity: RequestEntity = HttpEntity(ContentTypes.`application/json`, json)
        entity
      case _ =>
        HttpEntity.Empty
    }
  }

  /**
    * This function makes HttpRequest object according to the DB's data related to an account's webhook
    * @param uri In most cases it's a URL
    * @param method  GET/POST/POST/DELETE
    * @param httpProtocol HTTP/1.0 / HTTP/1.1 / HTTP/2.0
    * @param entity For instance:
    *                {
    *                  "event_name":"OnCreditTransaction",
    *                  "event_id":"fc7e4a71-5ff1-4006-95bb-7fd9e4adaef9", 
    *                  "bank_id":"gh.29.uk.x", 
    *                  "account_id":"private_01", 
    *                  "amount":"50.00 EUR", 
    *                  "balance":"739.00 EUR"
    *                 }
    * Please note it's empty in case of GET
    * @return HttpRequest object
    */
  private def getHttpRequest(uri: String, method: String, httpProtocol: String, entity: RequestEntity = HttpEntity.Empty): HttpRequest = {
    method match {
      case m: String if m.toUpperCase == "GET" =>
        HttpRequest(uri = uri, method = GET, protocol = getHttpProtocol(httpProtocol))
      case m: String if m.toUpperCase == "POST" =>
        HttpRequest(uri = uri, method = POST, entity = entity, protocol = getHttpProtocol(httpProtocol))
      case m: String if m.toUpperCase == "PUT" =>
        HttpRequest(uri = uri, method = PUT, entity = entity, protocol = getHttpProtocol(httpProtocol))
      case m: String if m.toUpperCase == "DELETE" =>
        HttpRequest(uri = uri, method = DELETE, entity = entity, protocol = getHttpProtocol(httpProtocol))
      case _ =>
        HttpRequest(uri = uri, method = GET, protocol = getHttpProtocol(httpProtocol))
    }
  }
  
  private def getHttpProtocol(httpProtocol: String): HttpProtocol = {
    httpProtocol match {
      case m: String if m.toUpperCase == "HTTP/1.0" => HttpProtocols.`HTTP/1.0`
      case m: String if m.toUpperCase == "HTTP/1.1" => HttpProtocols.`HTTP/1.1`
      case m: String if m.toUpperCase == "HTTP/2.0" => HttpProtocols.`HTTP/2.0`
      case _ => HttpProtocols.`HTTP/1.1`
    }
  }
  
  private def logEvent(request: WebhookRequest): Unit = {
    logger.debug("TRIGGER: " + request.trigger)
    logger.debug("EVENT_ID: " + request.eventId)
    logger.debug("BANK_ID: " + request.bankId)
    logger.debug("ACCOUNT_ID: " + request.accountId)
    logger.debug("AMOUNT: " + request.amount)
    logger.debug("BALANCE: " + request.balance)
  }
    

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext = system.dispatcher

  // The Actor which has sent the request. 
  // We need to respond to it after we finish an event.
  val requestActor = ObpLookupSystem.getWebhookActor()
  
  private def makeRequest(httpRequest: HttpRequest, request: WebhookRequest): Unit = {
    makeHttpRequest(httpRequest).onComplete {
        case Success(res@HttpResponse(status, headers, entity, protocol)) =>
          requestActor ! WebhookResponse(status.toString(), request)
          res.discardEntityBytes()
        case Failure(error)   =>
          requestActor ! WebhookFailure(error.getMessage, request)
      }
  }

  private def makeHttpRequest(httpRequest: HttpRequest): Future[HttpResponse] = {
    import scala.concurrent.duration.DurationInt
    val poolSettingsWithHttpsProxy  = 
      ConnectionPoolSettings.apply(system)
       /*
        # The minimum duration to backoff new connection attempts after the previous connection attempt failed.
        #
        # The pool uses an exponential randomized backoff scheme. After the first failure, the next attempt will only be
        # tried after a random duration between the base connection backoff and twice the base connection backoff. If that
        # attempt fails as well, the next attempt will be delayed by twice that amount. The total delay is capped using the
        # `max-connection-backoff` setting.
        #
        # The backoff applies for the complete pool. I.e. after one failed connection attempt, further connection attempts
        # to that host will backoff for all connections of the pool. After the service recovered, connections will come out
        # of backoff one by one due to the random extra backoff time. This is to avoid overloading just recently recovered
        # services with new connections ("thundering herd").
        #
        # Example: base-connection-backoff = 100ms, max-connection-backoff = 10 seconds
        #   - After 1st failure, backoff somewhere between 100ms and 200ms
        #   - After 2nd, between  200ms and  400ms
        #   - After 3rd, between  200ms and  400ms
        #   - After 4th, between  400ms and  800ms
        #   - After 5th, between  800ms and 1600ms
        #   - After 6th, between 1600ms and 3200ms
        #   - After 7th, between 3200ms and 6400ms
        #   - After 8th, between 5000ms and 10 seconds (max capped by max-connection-backoff, min by half of that)
        #   - After 9th, etc., stays between 5000ms and 10 seconds
        #
        # This setting only applies to the new pool implementation and is ignored for the legacy one.
       */
      .withBaseConnectionBackoff(1.second)
       /*
        # Maximum backoff duration between failed connection attempts. For more information see the above comment for the
        # `base-connection-backoff` setting.
        #
        # This setting only applies to the new pool implementation and is ignored for the legacy one.
       */
      .withMaxConnectionBackoff(1.minute)
       /*
        # The maximum number of times failed requests are attempted again,
        # (if the request can be safely retried) before giving up and returning an error.
        # Set to zero to completely disable request retries.
       */
      .withMaxRetries(5)
    
    Http().singleRequest(request = httpRequest, settings = poolSettingsWithHttpsProxy)
  }

  def main(args: Array[String]): Unit = {
    val uri = "https://www.openbankproject.com"
    val request = WebhookRequest(
      trigger=ApiTrigger.onBalanceChange , 
      eventId="418044f2-f74e-412f-a4e1-a78cdacdef9c", 
      bankId="gh.29.uk.x", 
      accountId="518044f2-f74e-412f-a4e1-a78cdacdef9c", 
      amount="10000", 
      balance="21000"
    )
    makeRequest(getHttpRequest(uri, "GET", "HTTP/1.1"), request)

    implicit val formats = CustomJsonFormats.formats
    case class User(name: String, job: String)
    val user = User("morpheus", "leader")
    val json = liftweb.json.compactRender(Extraction.decompose(user))
    val entity: RequestEntity = HttpEntity(ContentTypes.`application/json`, json)
    makeHttpRequest(getHttpRequest("https://reqres.in/api/users", "POST", "HTTP/1.1", entity)) map {
      `POST response` =>
        org.scalameta.logger.elem(`POST response`.status)
        `POST response`.entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
            val `Got POST response, body: ` = body.utf8String
            org.scalameta.logger.elem(`Got POST response, body: `)
        }
    }    
    
    val user2 = User("morpheus", "zion resident")
    val json2 = liftweb.json.compactRender(Extraction.decompose(user2))
    val entity2: RequestEntity = HttpEntity(ContentTypes.`application/json`, json2)
    makeHttpRequest(getHttpRequest("https://reqres.in/api/users", "PUT", "HTTP/1.1", entity2)) map {
      `PUT response` =>
        org.scalameta.logger.elem(`PUT response`.status)
        `PUT response`.entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
            val `Got PUT response, body: ` = body.utf8String
            org.scalameta.logger.elem(`Got PUT response, body: `)
        }
    }

  }
  
}