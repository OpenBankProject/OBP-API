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

class elasticsearch {

  case class QueryStrings(esType: String, queryStr: String)
  case class APIResponse(code: Int, body: JValue)
  case class ErrorMessage(error: String)

  val esHost = ""
  val esIndex = ""

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
    val filteredParams = params -- Set("esType")
    val jsonQuery = Json.encode(filteredParams)
    val esUrl = Helpers.appendParams( s"${esHost}/${esIndex}/${esType}${if (esType.nonEmpty) "/" else ""}_search", Seq(("source", jsonQuery)))
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



class elasticsearchLocal extends elasticsearch {

  override val esHost = Props.get("es_warehouse.host","http://localhost:9200")
  override val esIndex = Props.get("es_warehouse.data_index","es_warehouse_data_index")

    val settings = Settings.settingsBuilder()
      .put("http.enabled", true)
      .put("path.home", "/tmp/elastic/")

    val client = ElasticClient.local(settings.build)

    /*
  Index objects in Elastic Search.
  Use **the same** representations that we return in the REST API.
  Use the name singular_object_name-version  e.g. transaction-v1.2.1 for the index name / type
   */

    // Index a Transaction
    // Put into a index that has the viewId and version in the name.
    def indexTransaction(viewId: String, transaction: TransactionJSON) {
      client.execute {
        index into "transaction_v1.2.1" fields {
          viewId -> transaction
        }
      }
    }

    def indexAccount(viewId: String, account: AccountJSON) {
      client.execute {
        index into "account_v1.2.1" fields {
          viewId -> account
        }
      }
    }

  }


  object elasticsearchWarehouse extends elasticsearch {
    override val esHost = Props.get("es_warehouse.host","http://localhost:9200")
    override val esIndex = Props.get("es_warehouse.data_index","warehouse")
  }


  class elasticsearchMetrics extends elasticsearch {
    override val esHost = Props.get("es_metrics.host","http://localhost:9200")
    override val esIndex = Props.get("es_metrics.data_index","metrics")
  }


