package code.snippet

import code.model.OBPTransaction

import net.liftweb.http.{PaginatorSnippet, StatefulSnippet}
//import net.liftweb.http.DispatchSnippet._
//import net.liftweb.http.PaginatorSnippet._
import xml.NodeSeq
import com.mongodb.QueryBuilder
import net.liftweb.mongodb.Limit._
import net.liftweb.mongodb.Skip._
//import net.liftweb.http.Paginator._


import net.liftweb.util.Helpers._





class OBPTransactionSnippet extends StatefulSnippet with PaginatorSnippet[OBPTransaction] {

  override def count = OBPTransaction.count
  override def itemsPerPage = 5
  //override def page = OBPTransaction.findAll(QueryBuilder.start().get(), Limit(itemsPerPage), Skip(curPage*itemsPerPage))

  override def obp_transactions = OBPTransaction.findAll(QueryBuilder.start().get())



  var dispatch: DispatchIt = {
    case "showAll" => showAll _
    case "paginate" => paginate _
    //case "top" => top _
  }

  def showAll(xhtml: NodeSeq): NodeSeq = {

    // To get records using mongodb record
    //val qry = QueryBuilder.start("obp_transaction_currency").is("EU").get

    //val obp_transactions = OBPTransaction.findAll(qry)


    //bind("peer",xhtml, "status" -> "excited")
    // xhtml


    // call anonymous function on every transaction in obp_transactions (i.e. what a map does)
    // the lambda function here replaces some stuff with the data

    obp_transactions.flatMap(obp_transaction => {
      (".opb_transaction_other_account *" #> obp_transaction.opb_transaction_other_account).apply(xhtml)
      }
    )
  }

    /*

    */

   /*

  def showAll(xhtml: NodeSeq): NodeSeq = {
  page.flatMap(OBPTransaction => {
    (".opb_transaction_other_account *" #> OBPTransaction.opb_transaction_other_account).apply(xhtml)
  })

  */

  /*

  def top(xhtml: NodeSeq): NodeSeq = {
    val auctions = OBPTransaction.findAll(QueryBuilder.start().get())
    //".auction_row *" #> auctions.map { auction =>	".opb_transaction_other_account *" #> auction.opb_transaction_other_account }
    xhtml
    }
  */


}

