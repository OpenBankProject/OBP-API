package code.util


import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.ActorMaterializer
import code.api.util.CustomJsonFormats
import code.util.Helper.MdcLoggable

import scala.concurrent.Future


object AkkaHttpClient extends MdcLoggable with CustomJsonFormats {

  /**
    * This function makes HttpRequest object according to the DB's data related to an account's webhook
    * @param uri In most cases it's a URL
    * @param method  GET/POST/POST/DELETE
    * @param httpProtocol HTTP/1.0 / HTTP/1.1 / HTTP/2.0
    * @param entityJsonString For instance:
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
  def prepareHttpRequest(
    uri: String, 
    method: HttpMethod, 
    httpProtocol: HttpProtocol = HttpProtocol("HTTP/1.1"), 
    entityJsonString: String = ""
  ): HttpRequest = {
    val entity: RequestEntity = HttpEntity(ContentTypes.`application/json`, entityJsonString)
    HttpRequest(uri = uri, method = method, entity = entity, protocol = httpProtocol)
  }
  
  

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext = system.dispatcher

  def makeHttpRequest(httpRequest: HttpRequest): Future[HttpResponse] = {
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

  
}