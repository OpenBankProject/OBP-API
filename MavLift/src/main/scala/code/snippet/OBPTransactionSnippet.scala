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
import code.model.dataAccess.{OBPTransaction,OBPEnvelope,OBPAccount}
import xml.NodeSeq
import com.mongodb.QueryBuilder
import net.liftweb.mongodb.Limit._
import net.liftweb.mongodb.Skip._
import net.liftweb.util.Helpers._
import net.liftweb.util._
import scala.xml.Text
import net.liftweb.common.{Box, Failure, Empty, Full}
import java.util.Date
import net.liftweb.http.js.JsCmds.Noop
import code.model.implementedTraits._
import code.model.traits._

class OBPTransactionSnippet {

  val NOOP_SELECTOR = "#i_am_an_id_that_should_never_exist" #> ""
  val FORBIDDEN = "---"
  
  
  val view = S.uri match {
    case uri if uri.endsWith("authorities") => Authorities
    case uri if uri.endsWith("board") => Board
    case uri if uri.endsWith("our-network") => OurNetwork
    case uri if uri.endsWith("team") => Team
    case uri if uri.endsWith("my-view") => Owner //a solution has to be found for the editing case
    case _ => Anonymous
  }	
  println("current view name : "+view.name)
  
  val bankAccount = TesobeBankAccount.bankAccount
  val transactions = bankAccount.transactions
  val filteredTransactions = transactions.map(view.moderate(_))
  
  def displayAll = {
    def orderByDateDescending = (t1: ModeratedTransaction, t2: ModeratedTransaction) => {
      val date1 = t1.finishDate getOrElse new Date()
      val date2 = t2.finishDate getOrElse new Date()
      date1.after(date2)
    }
    
    val sortedTransactions = groupByDate(filteredTransactions.toList.sort(orderByDateDescending))
    
    "* *" #> sortedTransactions.map( transactionsForDay => {daySummary(transactionsForDay)})
  }


  def individualTransaction(transaction: ModeratedTransaction): CssSel = {
    def aliasRelatedInfo: CssSel = {
      transaction.aliasType match{
        case Public =>
          ".alias_indicator [class+]" #> "alias_indicator_public" &
            ".alias_indicator *" #> "(Alias)"
        case Private =>
          ".alias_indicator [class+]" #> "alias_indicator_private" &
            ".alias_indicator *" #> "(Alias)"
        case _ => NOOP_SELECTOR

      } 
    }

    def otherPartyInfo: CssSel = {

      //The extra information about the other party in the transaction

        def moreInfoBlank =
          ".other_account_more_info" #> NodeSeq.Empty &
            ".other_account_more_info_br" #> NodeSeq.Empty

        def moreInfoNotBlank =
          ".other_account_more_info *" #> transaction.moreInfo

        def logoBlank =
          NOOP_SELECTOR

        def logoNotBlank =
          ".other_account_logo_img [src]" #> transaction.imageUrl

        def websiteBlank =
          ".other_acc_link" #> NodeSeq.Empty & //If there is no link to display, don't render the <a> element
            ".other_acc_link_br" #> NodeSeq.Empty

        def websiteNotBlank =
          ".other_acc_link [href]" #> transaction.url

        def openCorporatesBlank =
          ".open_corporates_link" #> NodeSeq.Empty

        def openCorporatesNotBlank =
          ".open_corporates_link [href]" #> transaction.openCorporatesUrl

        ".narrative *" #>  displayNarrative(transaction,view) & 
          {
            transaction.moreInfo match{
              case Some(m) => if(m == "") moreInfoBlank else moreInfoNotBlank
              case _ => moreInfoBlank
            }
          } &
          {
            transaction.imageUrl match{
              case Some(i) => if(i == "") logoBlank else logoNotBlank
              case _ => logoBlank
            }
          } &
          {
            transaction.url match{
              case Some(m) => if(m == "") websiteBlank else websiteNotBlank
              case _ => websiteBlank
            }
          } &
          {
            transaction.openCorporatesUrl match{
              case Some(m) => if(m == "") openCorporatesBlank else openCorporatesNotBlank
              case _ => openCorporatesBlank
            }
          }

    }

    def commentsInfo = {
      {
        //If we're not allowed to see comments, don't show the comments section
        transaction.comments match 
        {
          case None => ".comments *" #> ""
          case Some(list) =>{
        	  					".comment *" #> list.length 
        	  					".comments_ext [href]" #>  view.name + "/transactions/" + transaction.id + "/comments"
        	  					 NOOP_SELECTOR
          					}  
        }
      } &
        ".symbol *" #> { transaction.amount match {
        				  	case Some(a) => if (a < 0) "-" else "+"
        				  	case _ => ""
        				}} &
        ".out [class]" #> { transaction.amount match{
        				  	case Some(a) => if (a <0) "out" else "in"
        				  	case _ => ""
        					} }
    }
    
    ".the_name *" #> transaction.accountHolder &
    ".amount *" #> { "€" + {transaction.amount match { 
      					case Some(o) => o.toString().stripPrefix("-")
      					case _ => ""}
                                                     }  } & //TODO: Format this number according to locale
    aliasRelatedInfo &
    otherPartyInfo &
    commentsInfo
  }
  
  def editableNarrative(transaction : ModeratedTransaction) = {
    var narrative = transaction.ownerComment.getOrElse("")
    CustomEditable.editable(narrative, SHtml.text(narrative, narrative = _), () => {
      //save the narrative
      transaction.ownerComment(narrative)
      Noop
    }, "Narrative")
  }

  def displayNarrative(transaction : ModeratedTransaction, currentView : View): NodeSeq = {
    if(currentView.canEditOwnerComment)
    	editableNarrative(transaction)	
    else Text(transaction.ownerComment.getOrElse(""))
  }

  def hasSameDate(t1: ModeratedTransaction, t2: ModeratedTransaction): Boolean = {

    val date1 = t1.finishDate getOrElse new Date()
    val date2 = t2.finishDate getOrElse new Date()
    
    val cal1 = Calendar.getInstance();
    val cal2 = Calendar.getInstance();
    cal1.setTime(date1);
    cal2.setTime(date2);
    
    //True if the two dates fall on the same day of the same year
    cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                  cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
  }

  /**
   * Splits a list of Transactions into a list of lists, where each of these new lists
   * is for one day.
   *
   * Example:
   * 	input : List(Jan 5,Jan 6,Jan 7,Jan 7,Jan 8,Jan 9,Jan 9,Jan 9,Jan 10)
   * 	output: List(List(Jan 5), List(Jan 6), List(Jan 7,Jan 7),
   * 				 List(Jan 8), List(Jan 9,Jan 9,Jan 9), List(Jan 10))
   */
  def groupByDate(list: List[ModeratedTransaction]): List[List[ModeratedTransaction]] = {
    list match {
      case Nil => Nil
      case h :: Nil => List(list)
      case h :: t => {
        //transactions that are identical to the head of the list
        val matches = list.filter(hasSameDate(h, _))
        List(matches) ++ groupByDate(list diff matches)
      }
    }
  }
  def daySummary(transactionsForDay: List[ModeratedTransaction]) = {
    val aTransaction = transactionsForDay.last
    val date = aTransaction.finishDate match{
        case Some(d) => (new SimpleDateFormat("MMMM dd, yyyy")).format(d)
        case _ => ""
      }
    ".date *" #> date &
      ".balance_number *" #> { "€" + {aTransaction.balance }} & 
      ".transaction_row *" #> transactionsForDay.map(t => individualTransaction(t))
  }
  
  //TODO: show bankname then account label
  def accountDetails = {
    "#accountName *" #> bankAccount.label
  }
  def hideSocialWidgets = {
    if(view.name!="anonymous") ".box *" #> ""
    else ".box *+" #> "" 
  }
}

