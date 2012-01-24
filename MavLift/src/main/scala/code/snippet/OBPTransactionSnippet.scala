package code.snippet

import net.liftweb.http.{PaginatorSnippet, StatefulSnippet}
import java.text.SimpleDateFormat

import net.liftweb.http._
import java.util.Calendar
import code.model.OBPTransaction

import code.model.OBPEnvelope

//import net.liftweb.http.DispatchSnippet._
//import net.liftweb.http.PaginatorSnippet._
import xml.NodeSeq
import com.mongodb.QueryBuilder
import net.liftweb.mongodb.Limit._
import net.liftweb.mongodb.Skip._
//import net.liftweb.http.Paginator._


import net.liftweb.util.Helpers._
//import net.liftweb.common.{Box,Full,Empty,Failure,ParamFailure}


import net.liftweb.util._

/**
 * A default implementation of DateTimeConverter that uses (Time)Helpers

object DefaultDateTimeConverter extends DateTimeConverter {
  def formatDateTime(d: Date) = internetDateFormat.format(d)
  def formatDate(d: Date) = dateFormat.format(d)
  /**  Uses Helpers.hourFormat which includes seconds but not time zone */
  def formatTime(d: Date) = hourFormat.format(d)

  def parseDateTime(s: String) = tryo { internetDateFormat.parse(s) }
  def parseDate(s: String) = tryo { dateFormat.parse(s) }
  /** Tries Helpers.hourFormat and Helpers.timeFormat */
  def parseTime(s: String) = tryo{hourFormat.parse(s)} or
tryo{timeFormat.parse(s)}

}
*/

class OBPTransactionSnippet extends StatefulSnippet with PaginatorSnippet[OBPEnvelope] {

  override def count = OBPTransaction.count


  override def itemsPerPage = 5
  //override def page = OBPTransaction.findAll(QueryBuilder.start().get(), Limit(itemsPerPage), Skip(curPage*itemsPerPage))
  override def page : List[OBPEnvelope]= {
      // TODO we need to get Rogue going otherwise its possible to write queries that don't make sense!
      // val qry = QueryBuilder.start("obp_transaction_data_blob").notEquals("simon-says").get

      val qry = QueryBuilder.start().get
      var obp_envelopes = OBPEnvelope.findAll(qry)


      println("before eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee")
        for (envelope <- obp_envelopes) {
      println("here is an envelope")
      println(envelope.id)
      //println(envelope.obp_transaction.get.obp_transaction_date_complete)
      println("nope")
    }


    obp_envelopes
  }

  var dispatch: DispatchIt = {
    case "showAll" => showAll _
    case "paginate" => paginate _
    //case "top" => top _
  }

  def showAll(xhtml: NodeSeq): NodeSeq = {

    // To get records using mongodb record
    //val qry = QueryBuilder.start("obp_transaction_currency").is("EU").get

    //val obp_transactions = OBPTransaction.findAll(qry)

    def present_obp_transaction_new_balance(value: String, consumer: String): String = {
      
      val showOnlyValueSign = if(value.startsWith("-")) "-" else "+"
      
      consumer match {
        case "team" => value
        case "board" => value
        case "our_network" => showOnlyValueSign
        case "authorities" => showOnlyValueSign
        case "anonymous" => showOnlyValueSign
        case _ => "---"
      }
    }

   /* def present_obp_transaction_amount(value: String, consumer: String): String = {
      // How the other account is presented to others
      // Use an alias if shy wins
      val show: String =
      if(consumer == "team")
        value
      else if(consumer == "board")
        value
      else if(consumer == "our_network")
        value
      else if(consumer == "authorities")
        value
      else if(consumer == "anonymous")
        (if (value.startsWith("-") ) "-" else "+")
      else
        "---"
      show
    }*/
/*
    def present_obp_transaction_other_account(value: String, consumer: String): String = {
      // How the other account is presented to others
      // Use an alias if shy wins

      //val result: String

      val outsiders: List[String]	= List("anonymous")

      if (outsiders.contains(value)) {

      }


      if (other_account_is_shy(value, consumer)) other_account_alias(value) else value
    }*/



   /* def other_account_is_a_client(value: String): Boolean = {
      // A list of clients
      val clients: List[String]	= List("TXTR GMBH")
      clients.contains(value)
    }

    def other_account_is_a_team_member(value: String): Boolean = {
      // A list of team members
      val team: List[String]	= List("Simon Redfern", "Stefan Bethge", "Eveline M", "Ismail Chaib", "Tim Kleinschmidt", "Niels Hapke", "Yoav Aner")
      team.contains(value)
    }

    def other_account_is_a_supplier(value: String): Boolean = {
      // A list of suppliers
      val suppliers: List[String]	= List("HETZNER ONLINE AG", "Cyberport GmbH", "S-BAHN BERLIN GMBH")
      suppliers.contains(value)
    }

    def other_account_is_shy(value: String, consumer: String): Boolean = {
       // A list of the financially shy (or just plain private)
       val the_shy: List[String]	= List("Tim Kleinschmidt", "Jan Slabiak")
      // A list of those that can look anyway
       val the_gods: List[String]	= List("team", "board", "authorities")
       // No one can be shy in front of the gods
       (the_shy.contains(value) && !(the_gods.contains(value)))
    }

    def other_account_alias(value: String): String = {
      // A map of aliases (used if shyness wins)
      val aliases = Map("Neils Hapke" -> "The Chicken", "Yoav Aner" -> "Software Developer 1", "Jan Slabiak" -> "Alex")
      aliases.getOrElse(value, "Anon")
    }


    def other_account_is_known(value: String): Boolean = {
      // Do we know this other account?
      (other_account_is_a_team_member(value) ||
        other_account_is_a_client(value) ||
        other_account_is_a_supplier(value)
        )
    }*/


    // xhtml
    val consumer = S.attr("consumer") openOr "no param consumer passed"
/*
    if (consumer == "anonymous") {
    }*/
    
    page.flatMap(obpEnvelope => {
      val FORBIDDEN = "---"
      
      val transaction = obpEnvelope.obp_transaction.get
      val transactionDetails = transaction.details.get
      val transactionValue = transactionDetails.value.get
      val thisAccount = transaction.this_account.get
      val otherAccount = transaction.other_account.get
      
      (
      ".obp_transaction_type_en *" #> transactionDetails.mediated_type_en(consumer).getOrElse(FORBIDDEN) &
      ".obp_transaction_type_de *" #> transactionDetails.mediated_type_de(consumer).getOrElse(FORBIDDEN) &
      ".obp_transaction_amount *" #> transactionValue.mediated_amount(consumer).getOrElse(FORBIDDEN) &
      ".obp_transaction_currency *" #> transactionValue.mediated_currency(consumer).getOrElse(FORBIDDEN) &
      ".obp_transaction_date_start *" #> transactionDetails.mediated_posted(consumer).getOrElse(FORBIDDEN)&
      ".obp_transaction_date_complete *" #> transactionDetails.mediated_completed(consumer).getOrElse(FORBIDDEN) &
      ".opb_transaction_other_account *" #> otherAccount.mediated_holder(consumer).getOrElse(FORBIDDEN)).apply(xhtml)
      
    })
    
    /*page.flatMap(obp_envelope => {
      (
        ".obp_transaction_type_en *" #> obp_envelope.obp_transaction.get.obp_transaction_type_en &
        ".obp_transaction_type_de *" #> obp_envelope.obp_transaction.get.obp_transaction_type_de &
        ".obp_transaction_data_blob *" #> present_obp_transaction_other_account(obp_envelope.obp_transaction.get.obp_transaction_data_blob.value, consumer) &
        ".obp_transaction_new_balance *" #> present_obp_transaction_new_balance(obp_envelope.obp_transaction.get.obp_transaction_new_balance.value, consumer) &
        ".obp_transaction_amount *" #> present_obp_transaction_amount(obp_envelope.obp_transaction.get.obp_transaction_amount.value, consumer) &
        //".obp_transaction_currency *" #> obp_transaction.obp_transaction_currency &
        ".obp_transaction_currency *" #> "EUR" &
        ".obp_transaction_date_start *" #> (short_date_formatter.format(obp_envelope.obp_transaction.get.obp_transaction_date_start.value.getTime())) &
        //".obp_transaction_date_complete *" #> OBPTransaction.formats.dateFormat.format(obp_transaction.obp_transaction_date_complete.value.getTime()) &
          ".obp_transaction_date_complete *" #> short_date_formatter.format(obp_envelope.obp_transaction.get.obp_transaction_date_complete.value.getTime()) &
          ".opb_transaction_other_account *" #> present_obp_transaction_other_account(obp_envelope.obp_transaction.get.opb_transaction_other_account.value, consumer)).apply(xhtml)
      }
    )*/
  }



   /*

  def showAll(xhtml: NodeSeq): NodeSeq = {
  page.flatMap(OBPTransaction => {
    (".opb_transaction_other_account *" #> OBPTransaction.opb_transaction_other_account).apply(xhtml)
  })

  */




}

