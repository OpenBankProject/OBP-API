package code.search

import org.json4s._
import java.util.Date

import code.api.util.APIUtil
import code.api.util.ErrorMessages._
import code.util.Helper.MdcLoggable
import com.sksamuel.elastic4s.http.JavaClient
import com.sksamuel.elastic4s.{ElasticClient, ElasticProperties}
import net.liftweb.common.{Box, Empty, Failure, Full}
import com.openbankproject.commons.util.json
import okhttp3.{MediaType => OkMediaType, OkHttpClient, Request => OkRequest, RequestBody}
import org.json4s.JsonAST
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NoStackTrace


class elasticsearch extends MdcLoggable {

  case class APIResponse(code: Int, body: JValue)
  case class ErrorMessage(error: String)

  case class ESJsonResponse(json: JsonAST.JValue, headers: List[(String, String)], code: Int)

  val esHost = ""
  val esPortHTTP = ""
  val esPortTCP = ""
  val esType = ""
  val esIndex = ""

  private val httpClient = new OkHttpClient()
  private val jsonMediaType = OkMediaType.parse("application/json; charset=UTF-8")

  def isEnabled(): Boolean = {
    APIUtil.getPropsAsBoolValue("allow_elasticsearch", false)
  }

  def searchProxy(userId: String, queryString: String): JValue = {
    if (APIUtil.getPropsAsBoolValue("allow_elasticsearch", false)) {
      val esUrl = constructQuery(userId, getParameters(queryString))
      getAPIResponse(esUrl).body
    } else {
      json.JsonParser.parse("""{"error":"elasticsearch disabled"}""")
    }
  }

  def searchProxyV300(userId: String, uri: String, body: String, statsOnly: Boolean = false): Box[JValue] = {
    if (APIUtil.getPropsAsBoolValue("allow_elasticsearch", false)) {
      val httpHost = "http://" + esHost + ":" + esPortHTTP
      val esUrl = s"${httpHost}${uri.replaceAll("\"", "")}"
      logger.info(s"searchProxyV300 says esUrl is: $esUrl")
      logger.info(s"searchProxyV300 says body is: $body")
      val response = getAPIResponse(esUrl, body)
      if (statsOnly) Full(privacyCheckStatistics(response.body))
      else Full(response.body)
    } else {
      Full(json.JsonParser.parse("""{"error":"elasticsearch disabled"}"""))
    }
  }

  def searchProxyAsyncV300(userId: String, uri: String, body: String, statsOnly: Boolean = false): Future[APIResponse] = {
    val httpHost = "http://" + esHost + ":" + esPortHTTP
    val esUrl = s"${httpHost}${uri.replaceAll("\"", "")}"
    logger.info(s"searchProxyAsyncV300 says esUrl is: $esUrl")
    logger.info(s"searchProxyAsyncV300 says body is: $body")
    val response = getAPIResponseAsync(esUrl, body)
    logger.info(s"searchProxyAsyncV300 says response follows:")
    response foreach { msg => logger.info(msg.body) }
    response
  }

  def parseResponse(response: APIResponse, statsOnly: Boolean = false): JValue = {
    if (statsOnly) privacyCheckStatistics(response.body)
    else response.body
  }

  def searchProxyStatsV300(userId: String, uriPart: String, bodyPart: String, field: String): Box[JValue] =
    searchProxyV300(userId, uriPart, addAggregation(bodyPart, field), statsOnly = true)

  def searchProxyStatsAsyncV300(userId: String, uriPart: String, bodyPart: String, field: String): Future[APIResponse] =
    searchProxyAsyncV300(userId, uriPart, addAggregation(bodyPart, field), true)

  private def addAggregation(bodyPart: String, field: String): String = {
    bodyPart.dropRight(1).concat(",\"aggs\":{\"" + field + "\":{\"stats\":{\"field\":\"" + field + "\"}}}}")
  }

  private def extractStatistics(body: JValue): JValue = {
    body \ "aggregations"
  }

  private def privacyCheckStatistics(body: JValue): JValue = {
    println("Enter privacyCheckStatistics")
    logger.debug(body)
    val result = extractStatistics(body)
    val count: Int = (result \\ "count" \\ classOf[JInt]).headOption.getOrElse(throw new RuntimeException with NoStackTrace).toInt
    if (count > 9) result
    else json.JsonParser.parse("{\"error\": \"" + NotEnoughtSearchStatisticsResults + "\"}")
  }

  private def getAPIResponse(esUrl: String, body: String = ""): APIResponse = {
    val r = httpClient.newCall(buildRequest(esUrl, body)).execute()
    val (statusCode, rawBody) = try {
      (r.code(), Option(r.body()).map(_.string()).filter(_.nonEmpty).getOrElse("{}"))
    } finally r.close()
    APIResponse(statusCode, json.parse(rawBody))
  }

  private def getAPIResponseAsync(esUrl: String, body: String = ""): Future[APIResponse] =
    Future { scala.concurrent.blocking { getAPIResponse(esUrl, body) } }

  private def buildRequest(esUrl: String, body: String): OkRequest =
    if (body.nonEmpty)
      new OkRequest.Builder()
        .url(esUrl)
        .post(RequestBody.create(jsonMediaType, body))
        .build()
    else
      new OkRequest.Builder().url(esUrl).get().build()

  private def appendParams(url: String, params: Seq[(String, String)]): String = {
    def encode(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
    params.toList match {
      case Nil => url
      case xs =>
        val query = xs.map { case (n, v) => encode(n) + "=" + encode(v) }.mkString("&")
        url + (if (url.contains("?")) "&" else "?") + query
    }
  }

  private def constructQuery(userId: String, params: Map[String, String]): String = {
    var esScroll = ""
    val esType = params.getOrElse("esType", "")
    val q = params.getOrElse("q", "")
    val source = params.getOrElse("source", "")
    val httpHost = "http://" + esHost + ":" + esPortHTTP

    var parameters = Seq[(String, String)]()
    if (q != "") {
      parameters = parameters ++ Seq(("q", q))
      val size = params.getOrElse("size", "")
      val sort = params.getOrElse("sort", "")
      val from = params.getOrElse("from", "")
      val df = params.getOrElse("df", "")
      val scroll = params.getOrElse("scroll", "")
      val scroll_id = params.getOrElse("scroll_id", "")
      val search_type = params.getOrElse("search_type", "")
      if (size != "") parameters = parameters ++ Seq(("size", size))
      if (sort != "") parameters = parameters ++ Seq(("sort", sort))
      if (from != "") parameters = parameters ++ Seq(("from", from))
      if (df != "") parameters = parameters ++ Seq(("df", df))
      if (scroll != "") parameters = parameters ++ Seq(("scroll", scroll))
      if (search_type != "") parameters = parameters ++ Seq(("search_type", search_type))
      if (scroll_id != "" && scroll != "") {
        esScroll = "/scroll"
        parameters = Seq(("scroll", scroll)) ++ Seq(("scroll_id", scroll_id))
      }
    } else if (q == "" && source != "") {
      parameters = Seq(("source", source))
    }

    val esUrl = appendParams(
      s"${httpHost}/${esIndex}/${esType}${if (esType.nonEmpty) "/" else ""}_search${esScroll}",
      parameters
    )
    logger.info(s"esUrl is $esUrl parameters are $parameters user_id is $userId")
    esUrl
  }

  private def getParameters(queryString: String): Map[String, String] = {
    queryString.split('&').map { str =>
      val pair = str.split('=')
      if (pair.length > 1) (pair(0) -> pair(1))
      else (pair(0) -> "")
    }.toMap
  }

  def createElasticSearchUriPart(index: String, topic: String): String = {
    val validIndices = APIUtil.getPropsValue("es.warehouse.allowed.indices", "").split(",").toSet
    val realIndex =
      if (index == "" || index == "ALL") APIUtil.getPropsValue("es.warehouse.allowed.indices").getOrElse(throw new RuntimeException)
      else index
    if (!realIndex.split(",").toSet.subsetOf(validIndices)) throw new RuntimeException() with NoStackTrace
    val addTopic = if (topic == "ALL") "" else "/" + topic
    "/" + realIndex + addTopic + "/_search"
  }

  def getElasticSearchUri(indexString: String): Box[String] = {
    val validIndices: List[String] = APIUtil.getPropsValue("es.warehouse.allowed.indices").getOrElse(
      throw new RuntimeException(NoValidElasticsearchIndicesConfigured) with NoStackTrace).split(",").toList match {
      case List("ALL") => List("")
      case x => x
    }
    checkIndicesValidity(indexString, validIndices) match {
      case x: Failure => Failure(s"Invalid Indices: You used: $indexString . Valid indices are: $validIndices")
      case Full(y) => Full("/" + y + "/_search")
      case Empty => Full("/_search")
    }
  }

  def checkIndicesValidity(indexString: String, validIndices: List[String]): Box[String] = {
    indexString match {
      case "ALL" => Empty
      case x => x match {
        case y if !y.split(",").toSet.subsetOf(validIndices.toSet) => Failure("")
        case y => Full(y)
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

  val props = ElasticProperties(s"http://$esHost:${esPortTCP.toInt}")
  lazy val client = ElasticClient(JavaClient(props))
  import com.sksamuel.elastic4s.ElasticDsl._

  if (APIUtil.getPropsAsBoolValue("allow_elasticsearch", false) && APIUtil.getPropsAsBoolValue("allow_elasticsearch_metrics", false) ) {
    try {
      client.execute {
        createIndex(s"$esIndex/request").mapping(
          properties (
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

  def indexMetric(userId: String, url: String, date: Date, duration: Long, userName: String, appName: String, developerEmail: String, correlationId: String, apiInstanceId: String) {
    if (APIUtil.getPropsAsBoolValue("allow_elasticsearch", false) && APIUtil.getPropsAsBoolValue("allow_elasticsearch_metrics", false) ) {
      try {
        import com.sksamuel.elastic4s.ElasticDsl._
        client.execute {
          indexInto(s"$esIndex/request") fields (
            "userId" -> userId,
            "url" -> url,
            "date" -> date,
            "duration" -> duration,
            "userName" -> userName,
            "appName" -> appName,
            "developerEmail" -> developerEmail,
            "correlationId" -> correlationId,
            "apiInstanceId" -> apiInstanceId
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
  val props = ElasticProperties(s"http://$esHost:${esPortTCP.toInt}")
  var client: ElasticClient = null
  if (APIUtil.getPropsAsBoolValue("allow_elasticsearch", false) && APIUtil.getPropsAsBoolValue("allow_elasticsearch_warehouse", false) ) {
    client = ElasticClient(JavaClient(props))
  }
}
