package code.snippet

import code.model.OBPTransaction

import net.liftweb.http.{PaginatorSnippet, StatefulSnippet}
import java.text.SimpleDateFormat

import net.liftweb.http._
//import net.liftweb.http.DispatchSnippet._
//import net.liftweb.http.PaginatorSnippet._
import xml.NodeSeq
import com.mongodb.QueryBuilder
import net.liftweb.mongodb.Limit._
import net.liftweb.mongodb.Skip._
//import net.liftweb.http.Paginator._


import net.liftweb.util.Helpers._
//import net.liftweb.common.{Box,Full,Empty,Failure,ParamFailure}



class OBPTransactionSnippet extends StatefulSnippet with PaginatorSnippet[OBPTransaction] {

  override def count = OBPTransaction.count
  override def itemsPerPage = 5
  //override def page = OBPTransaction.findAll(QueryBuilder.start().get(), Limit(itemsPerPage), Skip(curPage*itemsPerPage))
  override def page = OBPTransaction.findAll(QueryBuilder.start().get())

  var dispatch: DispatchIt = {
    case "showAll" => showAll _
    case "paginate" => paginate _
    //case "top" => top _
  }

  def showAll(xhtml: NodeSeq): NodeSeq = {

    // To get records using mongodb record
    //val qry = QueryBuilder.start("obp_transaction_currency").is("EU").get

    //val obp_transactions = OBPTransaction.findAll(qry)

    def present_obp_transaction_new_balance(value: Double, consumer: String): String = {
      val show: String =
      if(consumer == "team")
        value.toString
      else if(consumer == "board")
        value.toString
      else if(consumer == "tax_office")
        value.toString
      else if(consumer == "anonymous")
        (if (value < 0) "-" else "+")
      else
        "---"
      show
    }

    def present_obp_transaction_other_account(value: String, consumer: String): String = {
      val show: String =
      if(consumer == "team")
        value.toString
      else if(consumer == "board")
        value.toString
      else if(consumer == "tax_office")
        value.toString
      else if(consumer == "anonymous")
        "---"
      else
        "---"
      show
    }


    def other_account_is_a_client(value: String): Boolean = {
      false
    }

    def other_account_is_a_team_member(value: String): Boolean = {
      false
    }

    def other_account_is_a_supplier(value: String): Boolean = {
      false
    }

    def other_account_is_shy(value: String, consumer: String): Boolean = {
      false
    }

    def other_account_has_alias(value: String): Boolean = {
      false
    }


    def other_account_is_known(value: String): Boolean = {
      (other_account_is_a_team_member(value) ||
        other_account_is_a_client(value) ||
        other_account_is_a_supplier(value)
        )
    }

    def other_account_is_private(value: String): Boolean = {
      false
    }



    // xhtml
    val consumer = S.attr("consumer") openOr "no param consumer passed"

    if (consumer == "anonymous") {
    }

    // call anonymous function on every transaction in obp_transactions (i.e. what a map does)
    // the lambda function here replaces some stuff with the data

    import java.text.SimpleDateFormat
    val formatter = new SimpleDateFormat ( "yyyy-MM-dd HH:mm" )






    page.flatMap(obp_transaction => {
      (
        ".obp_transaction_type_en *" #> obp_transaction.obp_transaction_type_en &
        ".obp_transaction_type_de *" #> obp_transaction.obp_transaction_type_de &
        ".obp_transaction_data_blob *" #> obp_transaction.obp_transaction_data_blob &
        ".obp_transaction_new_balance *" #> present_obp_transaction_new_balance(obp_transaction.obp_transaction_new_balance.value, consumer) &
        ".obp_transaction_amount *" #> obp_transaction.obp_transaction_amount &
        ".obp_transaction_currency *" #> obp_transaction.obp_transaction_currency &
        ".obp_transaction_date_start *" #> (formatter format obp_transaction.obp_transaction_date_start.is.getTime()) &
        ".obp_transaction_date_complete *" #> (formatter format obp_transaction.obp_transaction_date_complete.is.getTime()) &
        ".opb_transaction_other_account *" #> obp_transaction.opb_transaction_other_account).apply(xhtml)
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

