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

Open Bank Project (http://www.openbankproject.com)
      Copyright 2011,2012 TESOBE / Music Pictures Ltd

      This product includes software developed at
      TESOBE (http://www.tesobe.com/)
		by 
		Simon Redfern : simon AT tesobe DOT com
		Everett Sochowski: everett AT tesobe DOT com

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
import code.model.OBPAccount
import code.model.OBPAccount.{APublicAlias, APrivateAlias}
import net.liftweb.http.js.JsCmds.Noop

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
    
    val consumer = S.uri match{
      case uri if uri.endsWith("authorities") => "authorities"
      case uri if uri.endsWith("board") => "board"
      case uri if uri.endsWith("our-network") => "our-network"
      case uri if uri.endsWith("team") => "team"
      case uri if uri.endsWith("my-view") => "my-view"
      case _ => "anonymous"
    }
    
   def orderByDateDescending = (e1: OBPEnvelope, e2: OBPEnvelope) => {
     val date1 = e1.obp_transaction.get.details.get.mediated_completed(consumer) getOrElse new Date()
     val date2 = e2.obp_transaction.get.details.get.mediated_completed(consumer) getOrElse new Date()
     date1.after(date2)
   } 
    
   val envelopes = page.sort(orderByDateDescending)
    
   envelopes.flatMap(obpEnvelope => {
      val FORBIDDEN = "---"
      
      val dateFormat = new SimpleDateFormat("MMM dd yyyy")
      
      val envelopeID = obpEnvelope.id
      
      val transaction = obpEnvelope.obp_transaction.get
      val transactionDetails = transaction.details.get
      val transactionValue = transactionDetails.value.get
      val thisAccount = transaction.this_account.get
      val otherAccount  = transaction.other_account.get
      
      def formatDate(date : Box[Date]) : String = {
        date match{
          case Full(d) => dateFormat.format(d)
          case _ => FORBIDDEN
        }
      }
      
      var narrative = obpEnvelope.narrative.get
      
      def editableNarrative() = {
    	 SHtml.ajaxEditable(Text(narrative), SHtml.text(narrative, narrative = _), ()=> {
    	   //save the narrative
    	   obpEnvelope.narrative(narrative).save
    	   Noop
    	 })
      }
      
      def displayNarrative() : NodeSeq = {
        consumer match{
          case "my-view" => editableNarrative()
          case _ => Text(obpEnvelope.mediated_narrative(consumer).getOrElse(FORBIDDEN))
        }
      }
      
      val theAccount = thisAccount.theAccount
      val otherUnmediatedHolder = otherAccount.holder.get
      val otherMediatedHolder = otherAccount.mediated_holder(consumer)
      
      val defaultImage = "images/blank.gif"
      
      def useDefaultImageIfBlank(toCheck: String) = {
        if(toCheck.equals("")) defaultImage
        else toCheck
      }
      
      val aliasImageSrc = {
        otherMediatedHolder._2 match{
          case Full(APublicAlias) => "/images/public_alias.png"
          case Full(APrivateAlias) => "/images/private_alias.png"
          case _ => defaultImage
        }
      }
      
      val moreInfo = {
        val moreInfo = for{
          a <- theAccount
          info <- a.getMediatedOtherAccountMoreInfo(consumer, otherUnmediatedHolder)
        } yield info
        
        moreInfo getOrElse ""
      }
      
      val logoImageSrc = {
         val imageUrl = for{
          a <- theAccount
          logo <- a.getMediatedOtherAccountImageURL(consumer, otherUnmediatedHolder)
        } yield logo
        
        useDefaultImageIfBlank(imageUrl getOrElse "")
      }
      
      val otherAccWebsiteUrl = {
        val url = for{
          a <- theAccount
          link <- a.getMediatedOtherAccountURL(consumer, otherUnmediatedHolder)
        } yield link
        
        url getOrElse ""
      }
      
      (
      ".amount *" #> transactionValue.mediated_amount(consumer).getOrElse(FORBIDDEN) &
      ".other_account_holder_name *" #> otherMediatedHolder._1.getOrElse(FORBIDDEN) &
      ".alias_image [src]" #> aliasImageSrc &
      ".other_account_more_info *" #> moreInfo &
      ".other_account_logo_img [src]" #> logoImageSrc &
      ".other_acc_ext [href]" #> otherAccWebsiteUrl &
      ".currency *" #> transactionValue.mediated_currency(consumer).getOrElse(FORBIDDEN) &
      ".date_cleared *" #> formatDate(transactionDetails.mediated_posted(consumer))&
      ".narrative *" #> displayNarrative &
      ".new_balance *" #> {
        transactionDetails.new_balance.get.mediated_amount(consumer).getOrElse(FORBIDDEN) + " " +
        transactionDetails.new_balance.get.mediated_currency(consumer).getOrElse(FORBIDDEN)} &
      ".comments_ext [href]" #> {consumer + "/transactions/" + envelopeID + "/comments"} &
      ".comments_title *" #> {"Comments (" + (obpEnvelope.mediated_comments(consumer) getOrElse List()).size + ")"}
      ).apply(xhtml)
      
    })
  }

}

