package code.search

import code.api.v1_2_1.{AccountJSON, TransactionJSON}
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl._
import dispatch.{Http, url}

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import net.liftweb.http.{JsonResponse, LiftResponse}
import net.liftweb.json.JsonAST._
import net.liftweb.util.Helpers
import net.liftweb.util.Props
import dispatch._
import Defaults._
import kafka.utils.Json
import net.liftweb.json
import java.util.Date

import com.sksamuel.elastic4s.mappings.FieldType.{DateType, ObjectType, StringType}

class elasticsearch {

  case class APIResponse(code: Int, body: JValue)
  case class ErrorMessage(error: String)

  val esHost = ""
  val esPortHTTP = ""
  val esPortTCP = ""
  val esType = ""
  val esIndex = ""

  def searchProxy(queryString: String): LiftResponse = {
    //println("-------------> " + esHost + ":" + esPortHTTP + "/" + esIndex + "/" + queryString)
    if (Props.getBool("allow_elasticsearch", false) ) {
      val request = constructQuery(getParameters(queryString))
      val response = getAPIResponse(request)
      JsonResponse(compactRender(response.body), ("Access-Control-Allow-Origin", "*") :: Nil, Nil, response.code)
    } else {
      JsonResponse(json.JsonParser.parse("""{"error":"elasticsearch disabled"}"""), ("Access-Control-Allow-Origin", "*") :: Nil, Nil, 404)
    }
  }

  private def getAPIResponse(req: Req): APIResponse = {
    Await.result(
      for (response <- Http(req > as.Response(p => p)))
        yield {
          val body = if (response.getResponseBody().isEmpty) "{}" else response.getResponseBody()
          APIResponse(response.getStatusCode, json.parse(body))
        }
      , Duration.Inf)
  }

  private def constructQuery(params: Map[String, String]): Req = {
    val esType = params.getOrElse("esType", "")
    val q = params.getOrElse("q", "")
    val source = params.getOrElse("source","")
    val filteredParams = params -- Set("esIndex", "esType")
    val jsonQuery = Json.encode(filteredParams)
    //TODO: Workaround - HTTP and TCP ports differ. Should there be props entry for both?
    val httpHost = ("http://" +  esHost + ":" + esPortHTTP)
    val esUrl =
      if (q != "")
        Helpers.appendParams( s"${httpHost}/${esIndex}/${esType}${if (esType.nonEmpty) "/" else ""}_search", Seq(("q", q)))
      else if (q == "" && source != "")
        Helpers.appendParams( s"${httpHost}/${esIndex}/${esType}${if (esType.nonEmpty) "/" else ""}_search", Seq(("source", source)))
      else
        ""
    println("[ES.URL]===> " + esUrl)
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

}


class elasticsearchMetrics extends elasticsearch {
  override val esHost     = Props.get("es.metrics.host","localhost")
  override val esPortTCP  = Props.get("es.metrics.port.tcp","9300")
  override val esPortHTTP = Props.get("es.metrics.port.tcp","9200")
  override val esIndex    = Props.get("es.metrics.index", "metrics")

  var client:ElasticClient = null

  if (Props.getBool("allow_elasticsearch", false) && Props.getBool("allow_elasticsearch_metrics", false) ) {
    client = ElasticClient.transport("elasticsearch://" + esHost + ":" + esPortTCP + ",")
    try {
      client.execute {
        create index esIndex mappings (
        "request" as (
          "userId" typed StringType,
          "url" typed StringType,
          "date" typed DateType
          )
        )
      }
    }
    catch {
      case e:Throwable => println("ERROR - "+ e.getMessage )
    }
  }

  def indexMetric(userId: String, url: String, date: Date) {
    if (Props.getBool("allow_elasticsearch", false) && Props.getBool("allow_elasticsearch_metrics", false) ) {
      try {
        client.execute {
          index into esIndex / "request" fields (
            "userId" -> userId,
            "url" -> url,
            "date" -> date
            )
        }
      }
      catch {
        case e:Throwable => println("ERROR - "+ e.getMessage )
      }
    }
  }

}

class elasticsearchWarehouse extends elasticsearch {
  override val esHost     = Props.get("es.warehouse.host","localhost")
  override val esPortTCP  = Props.get("es.warehouse.port.tcp","9300")
  override val esPortHTTP = Props.get("es.warehouse.port.tcp","9200")
  override val esIndex    = Props.get("es.warehouse.index", "warehouse")
  var client:ElasticClient = null
  if (Props.getBool("allow_elasticsearch", false) && Props.getBool("allow_elasticsearch_warehouse", false) ) {
    client = ElasticClient.transport("elasticsearch://" + esHost + ":" + esPortTCP + ",")
  }
}

/*
class elasticsearchOBP extends elasticsearch {
  override val esHost = Props.get("es.obp.host","localhost")
  override val esPortTCP = Props.get("es.obp.port.tcp","9300")
  override val esPortHTTP = Props.get("es.obp.port.tcp","9200")
  override val esIndex = Props.get("es.obp.index", "obp")
  val accountIndex     = "account_v1.2.1"
  val transactionIndex = "transaction_v1.2.1"

  var client:ElasticClient = null

  if (Props.getBool("allow_elasticsearch", false) ) {
    client = ElasticClient.transport("elasticsearch://" + esHost + ":" + esPortTCP + ",")

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
      if (Props.getBool("allow_elasticsearch", false) ) {
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
      if (Props.getBool("allow_elasticsearch", false) ) {
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

