package code.search

import code.api.v2_0_0.TransactionJSON
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl._



class search {


  /*
  Index objects in Elastic Search.
  Use **the same** representations that we return in the REST API.
  Use the name singular_object_name-version  e.g. transaction-v1.2.1 for the index name / type
   */


  // Index a Transaction
  // Put into a index that has the viewId and version in the name.
  def indexTransaction (viewId: String, transaction: TransactionJSON) {

    val client = ElasticClient.local

    // await is a helper method to make this operation sync instead of async
    // You would normally avoid doing this in a real program as it will block

    // https://github.com/sksamuel/elastic4s/blob/master/guide/source.md


    // TODO
    // client.execute { index into viewId + "transaction_v1.2.1"  doc transaction }.await



    //val resp = client.execute { search in "bands/artists" query "coldplay" }.await
    //println(resp)


  }



}
