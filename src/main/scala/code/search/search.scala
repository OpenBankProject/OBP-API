package code.search

import code.api.v1_2_1.{AccountJSON, TransactionJSON}
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl._
import dispatch.{Http, url}
import net.liftweb.http.S
import net.liftweb.common.Box
import net.liftweb.util.Props
import net.liftweb.util.HttpHelpers
import org.elasticsearch.common.settings.Settings

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import net.liftweb.common.{Box, Full, Loggable}
import net.liftweb.http.js.JsExp
import net.liftweb.http.{JsonResponse, LiftResponse, NoContentResponse, S}
import net.liftweb.http.rest._
import net.liftweb.json.Extraction
import net.liftweb.json.JsonAST._
import net.liftweb.json.Serialization.write
import net.liftweb.util.Helpers
import net.liftweb.util.Props
import dispatch._
import Defaults._
import kafka.utils.Json
import net.liftweb.json
import java.util.Date

import com.sksamuel.elastic4s.analyzers.StopAnalyzer
import com.sksamuel.elastic4s.mappings.FieldType.{DateType, ObjectType, StringType}
import com.sksamuel.elastic4s.source.Indexable

class elasticsearch {

  case class QueryStrings(esType: String, queryStr: String)
  case class APIResponse(code: Int, body: JValue)
  case class ErrorMessage(error: String)

  val esHost = ""

  def getAPIResponse(req: Req): APIResponse = {
    Await.result(
      for (response <- Http(req > as.Response(p => p)))
        yield {
          val body = if (response.getResponseBody().isEmpty) "{}" else response.getResponseBody()
          APIResponse(response.getStatusCode, json.parse(body))
        }
      , Duration.Inf)
  }

  def searchProxy(queryString: String): LiftResponse = {
      val request = constructQuery(getParameters(queryString))
      val response = getAPIResponse(request)
      JsonResponse(response.body, ("Access-Control-Allow-Origin","*") :: Nil, Nil, response.code)
    }

  private def constructQuery(params: Map[String, String]): Req = {
    val esType = params.getOrElse("esType", "")
    val esIndex = params.getOrElse("esIndex", "")
    val filteredParams = params -- Set("esIndex", "esType")
    val jsonQuery = Json.encode(filteredParams)
    val httpHost = ("http://" +  esHost).replaceAll("9300", "9200")
    val esUrl = Helpers.appendParams( s"${httpHost}/${esIndex}/${esType}${if (esType.nonEmpty) "/" else ""}_search", Seq(("source", jsonQuery)))
    url(esUrl).GET
  }

  def getParameters(queryString: String): Map[String, String] = {
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


object elasticsearchWarehouse extends elasticsearch {
  override val esHost = Props.get("es.warehouse.host","localhost:9300")
  val client = ElasticClient.transport("elasticsearch://" + esHost + ",")
}


class elasticsearchMetrics extends elasticsearch {
  override val esHost = Props.get("es.metrics.host","localhost:9300")
  val client = ElasticClient.transport("elasticsearch://" + esHost + ",")
  val metricsIndex = Props.get("es.metrics.index","metrics")
  client.execute { create index metricsIndex }
  client.execute {
    create index metricsIndex mappings (
      "request" as (
        "userId" typed StringType,
        "url" typed StringType,
        "date" typed DateType
        )
      )
  }

  def indexMetric(userId: String, url: String, date: Date) {
      client.execute {
        index into metricsIndex / "request" fields (
          "userId" -> userId,
          "url" -> url,
          "date" -> date
          )
      }
    }
}


class elasticsearchOBP extends elasticsearch {
  override val esHost = Props.get("es.obp.host","localhost:9300")
  val client = ElasticClient.transport("elasticsearch://" + esHost + ",")

  val accountIndex     = "account_v1.2.1"
  val transactionIndex = "transaction_v1.2.1"

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
    /*
  Index objects in Elastic Search.
  Use **the same** representations that we return in the REST API.
  Use the name singular_object_name-version  e.g. transaction-v1.2.1 for the index name / type
   */

    // Index a Transaction
    // Put into a index that has the viewId and version in the name.
    def indexTransaction(viewId: String, transaction: TransactionJSON) {
      client.execute {
        index into transactionIndex / "transaction" fields (
          "viewId" -> viewId,
          "transaction" -> transaction
          )
      }
    }

    // Index an Account
    // Put into a index that has the viewId and version in the name.
    def indexAccount(viewId: String, account: AccountJSON) {
      client.execute {
        index into accountIndex / "account" fields (
          "viewId" -> viewId,
          "account" -> account
          )
      }
    }

  }


