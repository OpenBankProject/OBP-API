package code.search

import code.api.v1_2_1.TransactionJSON
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl._



class search {


  /*
  Index objects in Elastic Search.
  Use **the same** representations that we return in the REST API.
  Use the name singular_object_name-version  e.g. transaction-v1.2.1 for the index name / type
   */


  // WIP
  def indexTransaction (transaction: TransactionJSON) {

    val client = ElasticClient.local

    // await is a helper method to make this operation sync instead of async
    // You would normally avoid doing this in a real program as it will block

    // https://github.com/sksamuel/elastic4s/blob/master/guide/source.md

    client.execute { index into "transaction_v1.2.1" doc TransactionJSON }.await

    //client.execute { index into "transactions" JacksonSource (TransactionJSON) }.await

    
    val resp = client.execute { search in "bands/artists" query "coldplay" }.await
    println(resp)

    //logger.debug(s"Search request ${req.show}")

  }



}
