package code.webhook

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.util.ByteString
import code.actorsystem.ObpLookupSystem
import code.api.util.ApiTrigger
import code.util.Helper.MdcLoggable
import code.webhook.WebhookActor.{WebhookFailure, WebhookRequest, WebhookResponse}
import net.liftweb
import net.liftweb.json.Extraction
import net.liftweb.mapper.By

import scala.util.{Failure, Success}


object WebhookHttpClient extends MdcLoggable {

  /**
    * This function starts the webhook event for instance ApiTrigger.onBalanceChange.
    * For the whole list of supported webhook events please take a look at a file code.api.util.ApiTrigger
    *
    * @param request is a message which provide all necessary data of the event
    * @return   we do not return anything because we use fire and forget scenario 
    *           but we will still send a result to the Actor in the end.
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
    *         2. requestActor ! WebhookResponse(res.status.toString(), "", request)
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

  private def getEventPayload(request: WebhookRequest): RequestEntity = {
    implicit val formats = net.liftweb.json.DefaultFormats
    val json = liftweb.json.compactRender(Extraction.decompose(request.toEventPayload))
    val entity: RequestEntity = HttpEntity(ContentTypes.`application/json`, json)
    entity
  }

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

  // The Actor which sent the request. 
  // We need to respond to it after we finish an event.
  val requestActor = ObpLookupSystem.getWebhookActor()
  
  private def makeRequest(httpRequest: HttpRequest, request: WebhookRequest): Unit = {
    makeHttpRequest(httpRequest).onComplete {
        case Success(res) =>
          requestActor ! WebhookResponse(res.status.toString(), "", request)
          res.discardEntityBytes()
        case Failure(error)   =>
          requestActor ! WebhookFailure("", error.getMessage, request)
      }
  }

  private def makeHttpRequest(httpRequest: HttpRequest) = {
    Http().singleRequest(httpRequest)
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

    implicit val formats = net.liftweb.json.DefaultFormats
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