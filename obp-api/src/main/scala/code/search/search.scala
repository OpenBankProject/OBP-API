package code.search

import java.nio.charset.Charset

import dispatch.{Http, url}
import code.util.Helper.MdcLoggable

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import net.liftweb.http.{InMemoryResponse, JsonResponse, LiftResponse}
import net.liftweb.json.JsonAST._
import net.liftweb.util.Helpers
import net.liftweb.util.Props
import dispatch._
import Defaults._
import net.liftweb.json
import java.util.Date

import code.api.util.APIUtil
import code.api.util.ErrorMessages._
import com.sksamuel.elastic4s.ElasticsearchClientUri
import org.elasticsearch.common.settings.Settings
import com.sksamuel.elastic4s.http.HttpClient
import com.sksamuel.elastic4s.mappings.FieldType._
import com.sksamuel.elastic4s.http.ElasticDsl._
import dispatch.as.String.charset
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.http.provider.HTTPCookie
import net.liftweb.json.JsonAST

import scala.util.control.NoStackTrace


class elasticsearch extends MdcLoggable {

  case class APIResponse(code: Int, body: JValue)
  case class ErrorMessage(error: String)

  case class ESJsonResponse(json: JsonAST.JValue, headers: List[(String, String)], cookies: List[HTTPCookie], code: Int) extends LiftResponse
  {
    def toResponse = {
      val bytes = json.toString.getBytes("UTF-8")
      InMemoryResponse(bytes, ("Content-Length", bytes.length.toString) :: ("Content-Type", "application/json; charset=utf-8") :: headers, cookies, code)
    }
  }

  val esHost = ""
  val esPortHTTP = ""
  val esPortTCP = ""
  val esType = ""
  val esIndex = ""


  def isEnabled(): Boolean = {
    APIUtil.getPropsAsBoolValue("allow_elasticsearch", false)
  }

  def searchProxy(userId: String, queryString: String): LiftResponse = {
    //println("-------------> " + esHost + ":" + esPortHTTP + "/" + esIndex + "/" + queryString)
    if (APIUtil.getPropsAsBoolValue("allow_elasticsearch", false) ) {
      val request = constructQuery(userId, getParameters(queryString))
      val response = getAPIResponse(request)
      ESJsonResponse(response.body, ("Access-Control-Allow-Origin", "*") :: Nil, Nil, response.code)
    } else {
      JsonResponse(json.JsonParser.parse("""{"error":"elasticsearch disabled"}"""), ("Access-Control-Allow-Origin", "*") :: Nil, Nil, 404)
    }
  }

  def searchProxyV300(userId: String, uri: String, body: String, statsOnly: Boolean = false): Box[LiftResponse] = {
    if (APIUtil.getPropsAsBoolValue("allow_elasticsearch", false) ) {
      val httpHost = ("http://" +  esHost + ":" +  esPortHTTP)
      val esUrl = s"${httpHost}${uri.replaceAll("\"" , "")}"
      logger.info(s"searchProxyV300 says esUrl is: $esUrl")
      logger.info(s"searchProxyV300 says body is: $body")
      val request: Req = (url(esUrl).<<(body).GET).setContentType("application/json", Charset.forName("UTF-8")) // Note that WE ONLY do GET - Keep it this way!
      val response = getAPIResponse(request)
         if (statsOnly) Full(ESJsonResponse(privacyCheckStatistics(response.body), ("Access-Control-Allow-Origin", "*") :: Nil, Nil, response.code))
         else Full(ESJsonResponse(response.body, ("Access-Control-Allow-Origin", "*") :: Nil, Nil, response.code))
    } else {
      Full(JsonResponse(json.JsonParser.parse("""{"error":"elasticsearch disabled"}"""), ("Access-Control-Allow-Origin", "*") :: Nil, Nil, 404))
    }
  }
  def searchProxyAsyncV300(userId: String, uri: String, body: String, statsOnly: Boolean = false): Future[APIResponse] = {
      val httpHost = ("http://" +  esHost + ":" +  esPortHTTP)
      val esUrl = s"${httpHost}${uri.replaceAll("\"" , "")}"
      logger.info(s"searchProxyAsyncV300 says esUrl is: $esUrl")
      logger.info(s"searchProxyAsyncV300 says body is: $body")
      val request: Req = (url(esUrl).<<(body).GET).setContentType("application/json", Charset.forName("UTF-8")) // Note that WE ONLY do GET - Keep it this way!
      logger.info(s"searchProxyAsyncV300 says request I will send to ES is: ${request.toRequest.toString}")
      val response = getAPIResponseAsync(request)
      logger.info (s"searchProxyAsyncV300 says response follows:")

      // TODO Extract code and hits from response and log that.

    response foreach {
      msg => logger.info(msg.body)
    }
    response
  }

  def parseResponse(response: APIResponse, statsOnly: Boolean = false) = {
    if (APIUtil.getPropsAsBoolValue("allow_elasticsearch", false) ) {
      if (statsOnly) (ESJsonResponse(privacyCheckStatistics(response.body), ("Access-Control-Allow-Origin", "*") :: Nil, Nil, response.code))
      else (ESJsonResponse(response.body, ("Access-Control-Allow-Origin", "*") :: Nil, Nil, response.code))
    } else {
      Full(JsonResponse(json.JsonParser.parse("""{"error":"elasticsearch disabled"}"""), ("Access-Control-Allow-Origin", "*") :: Nil, Nil, 404))
    }
  }


  def searchProxyStatsV300(userId: String, uriPart: String, bodyPart:String, field: String): Box[LiftResponse] = {
    searchProxyV300(userId, uriPart, addAggregation(bodyPart,field), true)
  }
  def searchProxyStatsAsyncV300(userId: String, uriPart: String, bodyPart:String, field: String): Future[APIResponse] = {
    searchProxyAsyncV300(userId, uriPart, addAggregation(bodyPart,field), true)
  }

  private def addAggregation(bodyPart: String, field: String): String = {
    bodyPart.dropRight(1).concat(",\"aggs\":{\"" + field + "\":{\"stats\":{\"field\":\""+ field + "\"}}}}")
  }
  
  private def extractStatistics(body: JValue): JValue = {
    body \  "aggregations" 
  }
  
  private def privacyCheckStatistics(body: JValue): JValue = {
    println("Enter privacyCheckStatistics")
    logger.debug(body)
    val result = extractStatistics(body)
    val count: Int = (result \\ "count" \\ classOf[JInt]).headOption.getOrElse(throw new RuntimeException with NoStackTrace).toInt
    if (count > 9) result
    else json.JsonParser.parse("{\"error\": \"" + NotEnoughtSearchStatisticsResults + "\"}")
  }
  
  private def getAPIResponse(req: Req): APIResponse = {
    Await.result(
      getAPIResponseAsync(req)
      , Duration.Inf)
  }

  private def getAPIResponseAsync(req: Req): Future[APIResponse] = {
    for (response <- Http.default(req > as.Response(p => p)))
      yield {
        val body = if (response.getResponseBody().isEmpty) "{}" else response.getResponseBody()
        APIResponse(response.getStatusCode, json.parse(body))
      }
  }

  private def constructQuery(userId: String, params: Map[String, String]): Req = {
    var esScroll = ""
    val esType = params.getOrElse("esType", "")
    val q = params.getOrElse("q", "")
    val source = params.getOrElse("source","")
    //val jsonQuery = Json.encode(filteredParams)
    //TODO: Workaround - HTTP and TCP ports differ. Should there be props entry for both?
    val httpHost = ("http://" +  esHost + ":" + esPortHTTP)

    var parameters = Seq[(String,String)]()
    if (q != "") {
      parameters = parameters ++ Seq(("q", q))
      val size = params.getOrElse("size", "")
      val sort = params.getOrElse("sort", "")
      val from = params.getOrElse("from", "")
      val df = params.getOrElse("df", "")
      val scroll = params.getOrElse("scroll", "")
      val scroll_id = params.getOrElse("scroll_id", "")
      val search_type = params.getOrElse("search_type", "")
      if (size != "")
        parameters = parameters ++ Seq(("size", size))
      if (sort != "")
        parameters = parameters ++ Seq(("sort", sort))
      if (from != "")
        parameters = parameters ++ Seq(("from", from))
      if (df != "")
        parameters = parameters ++ Seq(("df", df))
      if (scroll != "")
        parameters = parameters ++ Seq(("scroll", scroll))
      if (search_type != "")
        parameters = parameters ++ Seq(("search_type", search_type))
      // scroll needs specific URL
      if (scroll_id != "" && scroll != "") {
        esScroll = "/scroll"
        parameters = Seq(("scroll", scroll)) ++ Seq(("scroll_id", scroll_id))
      }
    }
    else if (q == "" && source != "") {
      parameters = Seq(("source", source))
    }
    val esUrl = Helpers.appendParams( s"${httpHost}/${esIndex}/${esType}${if (esType.nonEmpty) "/" else ""}_search${esScroll}", parameters )
    //println("[ES.URL]===> " + esUrl)

    // Use this incase we cant log to elastic search
    logger.info(s"esUrl is $esUrl parameters are $parameters user_id is $userId")

    url(esUrl).GET
  }

  private def getParameters(queryString: String): Map[String, String] = {
    val res = queryString.split('&') map { str =>
    val pair = str.split('=')
      if (pair.length > 1)
        (pair(0) -> pair(1))
      else
        (pair(0) -> "")
    } toMap

    res
  }

  def createElasticSearchUriPart(index: String, topic: String): String = {
    val validIndices = APIUtil.getPropsValue("es.warehouse.allowed.indices", "").split(",").toSet
    val realIndex =
      if (index == "" || index == "ALL") APIUtil.getPropsValue("es.warehouse.allowed.indices").getOrElse(throw new RuntimeException)
      else index
    if (! realIndex.split(",").toSet.subsetOf(validIndices)) throw new RuntimeException() with NoStackTrace
    val addTopic = if (topic == "ALL") "" else "/" + topic
    "/" + realIndex + addTopic + "/_search"
  }

  def getElasticSearchUri(indexString: String): Box[String] = {
    val validIndices: List[String] = APIUtil.getPropsValue("es.warehouse.allowed.indices").getOrElse(
      throw new RuntimeException(NoValidElasticsearchIndicesConfigured) with NoStackTrace).split(",").toList match
    {
      case List("ALL") => List("")
      case x => x
    }
    checkIndicesValidity(indexString, validIndices) match {
      case x: Failure => Failure(s"Invalid Indices: You used: $indexString . Valid indices are: $validIndices")
      case Full(y) => Full("/" + y + "/_search")
      case Empty => Full("/_search")
    }
  }

  def checkIndicesValidity(indexString: String, validIndices: List[String]): Box[String] ={
    indexString match {
      case "ALL" => Empty
      case x => x match {
        case y if !y.split(",").toSet.subsetOf(validIndices.toSet) => Failure("")
        case y   => Full(y)
      }
    }
  }

}


class elasticsearchMetrics extends elasticsearch {
  override val esHost     = APIUtil.getPropsValue("es.metrics.host","localhost")
  override val esPortTCP  = APIUtil.getPropsValue("es.metrics.port.tcp","9300")
  override val esPortHTTP = APIUtil.getPropsValue("es.metrics.port.http","9200")
  override val esIndex    = APIUtil.getPropsValue("es.metrics.index", "metrics")

  if (esIndex.contains(",")) throw new RuntimeException("Props error: es.metrics.index can not be a list")

  var client:HttpClient = null

  if (APIUtil.getPropsAsBoolValue("allow_elasticsearch", false) && APIUtil.getPropsAsBoolValue("allow_elasticsearch_metrics", false) ) {
    val settings = Settings.builder().put("cluster.name", APIUtil.getPropsValue("es.cluster.name", "elasticsearch")).build()
    client = HttpClient(ElasticsearchClientUri(esHost,  esPortTCP.toInt))
    try {
      client.execute {
        createIndex(esIndex).mappings(
        mapping("request") as (
          textField("userId"),
          textField("url"),
          dateField("date"),
          textField("userName"),
          textField("appName"),
          textField("developerEmail"),
          textField("correlationId")
          )
        )
      }
    }
    catch {
      case e:Throwable => logger.error("ERROR - "+ e.getMessage )
    }
  }

  def indexMetric(userId: String, url: String, date: Date, duration: Long, userName: String, appName: String, developerEmail: String, correlationId: String) {
    if (APIUtil.getPropsAsBoolValue("allow_elasticsearch", false) && APIUtil.getPropsAsBoolValue("allow_elasticsearch_metrics", false) ) {
      try {
        client.execute {
          indexInto(esIndex / "request") fields (
            "userId" -> userId,
            "url" -> url,
            "date" -> date,
            "duration" -> duration,
            "userName" -> userName,
            "appName" -> appName,
            "developerEmail" -> developerEmail,
            "correlationId" -> correlationId
            )
        }
      }
      catch {
        case e:Throwable => logger.error("ERROR - "+ e.getMessage )
      }
    }
  }

}

class elasticsearchWarehouse extends elasticsearch {
  override val esHost     = APIUtil.getPropsValue("es.warehouse.host","localhost")
  override val esPortTCP  = APIUtil.getPropsValue("es.warehouse.port.tcp","9300")
  override val esPortHTTP = APIUtil.getPropsValue("es.warehouse.port.http","9200")
  override val esIndex    = APIUtil.getPropsValue("es.warehouse.index", "warehouse")
  var client:HttpClient = null
  if (APIUtil.getPropsAsBoolValue("allow_elasticsearch", false) && APIUtil.getPropsAsBoolValue("allow_elasticsearch_warehouse", false) ) {
    val settings = Settings.builder().put("cluster.name", APIUtil.getPropsValue("es.cluster.name", "elasticsearch")).build()
    client = HttpClient(ElasticsearchClientUri(esHost,  esPortTCP.toInt))
  }
}

/*
class elasticsearchOBP extends elasticsearch {
  override val esHost = APIUtil.getPropsValue("es.obp.host","localhost")
  override val esPortTCP = APIUtil.getPropsValue("es.obp.port.tcp","9300")
  override val esPortHTTP = APIUtil.getPropsValue("es.obp.port.tcp","9200")
  override val esIndex = APIUtil.getPropsValue("es.obp.index", "obp")
  val accountIndex     = "account_v1.2.1"
  val transactionIndex = "transaction_v1.2.1"

  var client:TcpClient = null

  if (APIUtil.getPropsAsBoolValue("allow_elasticsearch", false) ) {
    client = TcpClient.transport("elasticsearch://" + esHost + ":" + esPortTCP + ",")

    client.execute {
      create index accountIndex mappings (
        "account" as (
          "viewId" typed StringType,
          "account" typed ObjectType
          )
        )
    }

    client.execute {
      create index transactionIndex mappings (
        "transaction" as (
          "viewId" typed StringType,
          "transaction" typed ObjectType
          )
        )
    }
  }
    /*
  Index objects in Elastic Search.
  Use **the same** representations that we return in the REST API.
  Use the name singular_object_name-version  e.g. transaction-v1.2.1 for the index name / type
   */

    // Index a Transaction
    // Put into a index that has the viewId and version in the name.
    def indexTransaction(viewId: String, transaction: TransactionJSON) {
      if (APIUtil.getPropsAsBoolValue("allow_elasticsearch", false) ) {
        client.execute {
          index into transactionIndex / "transaction" fields (
            "viewId" -> viewId,
            "transaction" -> transaction
            )
        }
      }
    }

    // Index an Account
    // Put into a index that has the viewId and version in the name.
    def indexAccount(viewId: String, account: AccountJSON) {
      if (APIUtil.getPropsAsBoolValue("allow_elasticsearch", false) ) {
        client.execute {
          index into accountIndex / "account" fields (
            "viewId" -> viewId,
            "account" -> account
            )
        }
      }
    }

  }
*/

