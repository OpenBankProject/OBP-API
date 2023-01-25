package code.webhook

import code.api.util.ApiTrigger.{OnBalanceChange, OnCreateTransaction, OnCreditTransaction, OnDebitTransaction}
import code.api.util.{ApiTrigger, CustomJsonFormats}
import code.util.Helper.MdcLoggable
import code.webhook.WebhookActor.{AccountNotificationWebhookRequest, WebhookRequest, WebhookRequestTrait}
import net.liftweb
import net.liftweb.json.Extraction
import net.liftweb.mapper.By
import okhttp3.{MediaType, Request, RequestBody}
import code.webhook.OkHttpWebhookClient._


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
  def startEvent(request: WebhookRequestTrait): List[Unit] = {
    logger.debug(s"Query table MappedAccountWebhook by mIsActive, mBankId, mAccountId, mTriggerName: true, ${request.bankId}, ${request.accountId}, ${request.trigger.toString()}" )
    logger.debug("WebhookHttpClient.startEvent(WebhookRequestTrait).request.eventId: " + request.eventId)
    MappedAccountWebhook.findAll(
      By(MappedAccountWebhook.mIsActive, true), 
      By(MappedAccountWebhook.mBankId, request.bankId), 
      By(MappedAccountWebhook.mAccountId, request.accountId),
      By(MappedAccountWebhook.mTriggerName, request.trigger.toString())
    ) map {
      i =>
        logEvent(request)
        logger.debug("WebhookHttpClient.startEvent(WebhookRequestTrait) i.url: " + i.url)
        logger.debug("WebhookHttpClient.startEvent(WebhookRequestTrait) i.httpMethod: " + i.httpMethod)
        logger.debug("WebhookHttpClient.startEvent(WebhookRequestTrait) i.httpProtocol: " + i.httpProtocol)
        val payload = getEventPayload(request)
        logger.debug("WebhookHttpClient.startEvent(WebhookRequestTrait) payload: " + payload.toString)
        makeAsynchronousRequest(composeRequest(i.url, i.httpMethod, i.httpProtocol, payload), request)
    }
  }

  def startEvent(request: AccountNotificationWebhookRequest): List[Unit] = {

    val accountWebhooks = {
      logger.debug("Finding BankAccountNotificationWebhook with Triggername = " + request.trigger.toString())
      val bankLevelWebhooks = BankAccountNotificationWebhook.findAll(
        By(BankAccountNotificationWebhook.BankId, request.bankId),
        By(BankAccountNotificationWebhook.TriggerName, request.trigger.toString())
      )
      logger.debug(s"Found ${bankLevelWebhooks.size} BankAccountNotificationWebhook with Triggername = " + request.trigger.toString())
      
      logger.debug("Finding SystemAccountNotificationWebhook with Triggername = " + request.trigger.toString())
      val systemLevelWebhooks = SystemAccountNotificationWebhook.findAll(
        By(SystemAccountNotificationWebhook.TriggerName, request.trigger.toString())
      )
      logger.debug(s"Found ${systemLevelWebhooks.size} SystemAccountNotificationWebhook with Triggername = " + request.trigger.toString())
      
      bankLevelWebhooks ++ systemLevelWebhooks
    }
    
    logger.debug("WebhookHttpClient.startEvent(AccountNotificationWebhookRequest).request.eventId: " + request.eventId)
    logger.debug("WebhookHttpClient.startEvent(AccountNotificationWebhookRequest).accountWebhooks: " + accountWebhooks)
    accountWebhooks map {
      i =>{
        logEvent(request)
        logger.debug("WebhookHttpClient.startEvent(AccountNotificationWebhookRequest) i.url: " + i.url)
        logger.debug("WebhookHttpClient.startEvent(AccountNotificationWebhookRequest) i.httpMethod: " + i.httpMethod)
        logger.debug("WebhookHttpClient.startEvent(AccountNotificationWebhookRequest) i.httpProtocol: " + i.httpProtocol)
        val payload = getEventPayload(request)
        logger.debug("WebhookHttpClient.startEvent(AccountNotificationWebhookRequest) payload: " + payload.toString)
        makeAsynchronousRequest(composeRequest(i.url, i.httpMethod, i.httpProtocol, payload), request)
      }
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
  def getEventPayload(request: WebhookRequestTrait): Option[String] = {
    request.trigger match {
      case OnBalanceChange() | OnCreditTransaction() | OnDebitTransaction() | OnCreateTransaction() =>
        implicit val formats = CustomJsonFormats.formats
        val json = liftweb.json.compactRender(Extraction.decompose(request.toEventPayload))
        Some(json)
      case _ =>
        None
    }
  }

  /**
   * This function makes HttpRequest object according to the DB's data related to an account's webhook
   * @param uri In most cases it's a URL
   * @param method  GET/POST/POST/DELETE
   * @param httpProtocol HTTP/1.0 / HTTP/1.1 / HTTP/2.0
   * @param json For instance:
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
  def composeRequest(uri: String, method: String, httpProtocol: String, json: Option[String]): Request = {
    val jsonType = MediaType.parse("application/json; charset=utf-8");
    val body = RequestBody.create(jsonType, json.getOrElse(""))
    method match {
      case m: String if m.toUpperCase == "GET" =>
        new Request.Builder().url(uri).build
      case m: String if m.toUpperCase == "POST" =>
        new Request.Builder().url(uri).post(body).build
      case m: String if m.toUpperCase == "PUT" =>
        new Request.Builder().url(uri).put(body).build
      case m: String if m.toUpperCase == "DELETE" =>
        new Request.Builder().url(uri).delete(body).build
      case _ =>
        new Request.Builder().url(uri).build
    }
  }
  
  
  private def logEvent(request: WebhookRequestTrait): Unit = {
    logger.debug("TRIGGER: " + request.trigger)
    logger.debug("EVENT_ID: " + request.eventId)
    logger.debug("BANK_ID: " + request.bankId)
    logger.debug("ACCOUNT_ID: " + request.accountId)
    if (request.isInstanceOf[WebhookRequest]){
      logger.debug("AMOUNT: " + request.asInstanceOf[WebhookRequest].amount )
      logger.debug("BALANCE: " + request.asInstanceOf[WebhookRequest].balance)
    }else if (request.isInstanceOf[AccountNotificationWebhookRequest]) {
      logger.debug("TRANSACTION_ID: " + request.asInstanceOf[AccountNotificationWebhookRequest].transactionId)
      logger.debug("RELATED_ENTITIES: " + request.asInstanceOf[AccountNotificationWebhookRequest].relatedEntities)
    }else{
    }
  }
  

  def main(args: Array[String]): Unit = {
    val uri = "https://publicobject.com/helloworld.txt"
    val request = WebhookRequest(
      trigger=ApiTrigger.onBalanceChange , 
      eventId="418044f2-f74e-412f-a4e1-a78cdacdef9c", 
      bankId="gh.29.uk.x", 
      accountId="518044f2-f74e-412f-a4e1-a78cdacdef9c", 
      amount="10000", 
      balance="21000"
    )
    makeAsynchronousRequest(
      composeRequest(uri, "GET", "HTTP/1.1", None), 
      request
    )

    implicit val formats = CustomJsonFormats.formats
    case class User(name: String, job: String)
    val user = User("morpheus", "leader")
    val json = liftweb.json.compactRender(Extraction.decompose(user))
    makeAsynchronousRequest(
      composeRequest("https://reqres.in/api/users", "POST", "HTTP/1.1", Some(json)), 
      request)
    
    val user2 = User("morpheus", "zion resident")
    val json2 = liftweb.json.compactRender(Extraction.decompose(user2))
    makeAsynchronousRequest(
      composeRequest("https://reqres.in/api/users/2", "PUT", "HTTP/1.1", Some(json2)), 
      request
    )
  }
  
}