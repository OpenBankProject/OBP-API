/** 
Open Bank Project

Copyright 2011,2012 TESOBE / Music Pictures Ltd.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and 
limitations under the License.       
 */
package code.snippet

import net.liftweb.http.{PaginatorSnippet, StatefulSnippet}
import java.text.SimpleDateFormat
import net.liftweb.http._
import java.util.Calendar
import code.model.OBPTransaction
import code.model.OBPEnvelope
import xml.NodeSeq
import com.mongodb.QueryBuilder
import net.liftweb.mongodb.Limit._
import net.liftweb.mongodb.Skip._
import net.liftweb.util.Helpers._
import net.liftweb.util._
import scala.xml.Text
import net.liftweb.common.{Box, Failure, Empty, Full}
import java.util.Date

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

  override def count = OBPEnvelope.count


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
    
    val consumer = S.uri match{
      case uri if uri.endsWith("authorities") => "authorities"
      case uri if uri.endsWith("board") => "board"
      case uri if uri.endsWith("our-network") => "our-network"
      case uri if uri.endsWith("team") => "team"
      case _ => "anonymous"
    }
    
   page.flatMap(obpEnvelope => {
      val FORBIDDEN = "---"
      
      val dateFormat = new SimpleDateFormat("EEE MMM dd yyyy")
      
      val envelopeID = obpEnvelope.id
      
      val transaction = obpEnvelope.obp_transaction.get
      val transactionDetails = transaction.details.get
      val transactionValue = transactionDetails.value.get
      val thisAccount = transaction.this_account.get
      val otherAccount = transaction.other_account.get
      
      def formatDate(date : Box[Date]) : String = {
        date match{
          case Full(d) => dateFormat.format(d)
          case _ => FORBIDDEN
        }
      }
      
      (
      ".amount *" #> transactionValue.mediated_amount(consumer).getOrElse(FORBIDDEN) &
      ".other_account_holder *" #> otherAccount.mediated_holder(consumer).getOrElse(FORBIDDEN) &
      ".currency *" #> transactionValue.mediated_currency(consumer).getOrElse(FORBIDDEN) &
      ".date_cleared *" #> formatDate(transactionDetails.mediated_posted(consumer))&
      ".new_balance *" #> {
        transactionDetails.new_balance.get.mediated_amount(consumer).getOrElse(FORBIDDEN) + " " +
        transactionDetails.new_balance.get.mediated_currency(consumer).getOrElse(FORBIDDEN)
      } &
      ".comments_link *" #> <a href={consumer + "/transactions/" + envelopeID + "/comments"}>Comments ({(obpEnvelope.mediated_comments(consumer) getOrElse List()).size})</a>).apply(xhtml)
      
    })
  }

}

