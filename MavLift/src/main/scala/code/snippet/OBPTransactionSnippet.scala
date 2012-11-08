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
    Ayoub Benali : ayoub AT tesobe DOT com

 */
package code.snippet

import net.liftweb.http.{PaginatorSnippet, StatefulSnippet}
import java.text.SimpleDateFormat
import net.liftweb.http._
import java.util.Calendar
import code.model.dataAccess.{OBPTransaction,OBPEnvelope,OBPAccount, OtherAccount}
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
import java.util.Currency

class OBPTransactionSnippet (filteredTransactionsAndView : (List[ModeratedTransaction],View)){

  val NOOP_SELECTOR = "#i_am_an_id_that_should_never_exist" #> ""
  val FORBIDDEN = "---"
  val filteredTransactions = filteredTransactionsAndView._1
  val view = filteredTransactionsAndView._2
  val currencySymbol  = filteredTransactions match {
    case Nil => ""
    case x :: xs => x.bankAccount match {
      case Some(bankAccount) => bankAccount.currency match {
                case Some(currencyISOCode) =>{ 
                  tryo{
                    Currency.getInstance(currencyISOCode)
                  } match {
                    case Full(currency) => currency.getSymbol(S.locale)
                    case _ => ""
                  }
                }
                case _ => ""
              }
      case _ => ""
    }
  }  
  def individualTransaction(transaction: ModeratedTransaction): CssSel = {
    
    def otherPartyInfo: CssSel = {
      //The extra information about the other party in the transaction
        def moreInfoBlank =
          ".other_account_more_info" #> NodeSeq.Empty &
            ".other_account_more_info_br" #> NodeSeq.Empty

        def moreInfoNotBlank =
          ".other_account_more_info *" #> transaction.otherBankAccount.get.metadata.get.moreInfo.get

        def logoBlank =
          NOOP_SELECTOR

        def logoNotBlank =
          ".other_account_logo_img [src]" #> transaction.otherBankAccount.get.metadata.get.imageUrl.get

        def websiteBlank =
          ".other_acc_link" #> NodeSeq.Empty & //If there is no link to display, don't render the <a> element
            ".other_acc_link_br" #> NodeSeq.Empty

        def websiteNotBlank =
          ".other_acc_link [href]" #> transaction.otherBankAccount.get.metadata.get.url.get

        def openCorporatesBlank =
          ".open_corporates_link" #> NodeSeq.Empty

        def openCorporatesNotBlank =
          ".open_corporates_link [href]" #> transaction.otherBankAccount.get.metadata.get.openCorporatesUrl.get
        
        transaction.otherBankAccount match {
          case Some(otherAccount) => 
          {
            ".the_name *" #> otherAccount.label.display &
            {otherAccount.label.aliasType match{
                case Public =>
                  ".alias_indicator [class+]" #> "alias_indicator_public" &
                    ".alias_indicator *" #> "(Alias)"
                case Private =>
                  ".alias_indicator [class+]" #> "alias_indicator_private" &
                    ".alias_indicator *" #> "(Alias)"
                case _ => NOOP_SELECTOR
            }}& 
            {otherAccount.metadata match {
                case Some(metadata) => 
                {
                  {metadata.moreInfo match{
                            case Some(m) => if(m.isEmpty) moreInfoBlank else moreInfoNotBlank
                            case _ => moreInfoBlank
                  }}&  
                  {metadata.imageUrl match{
                          case Some(i) => if(i.isEmpty) logoBlank else logoNotBlank
                          case _ => logoBlank
                  }}& 
                  {metadata.url match{
                    case Some(m) => if(m.isEmpty) websiteBlank else websiteNotBlank
                    case _ => websiteBlank
                  }}&
                  {metadata.openCorporatesUrl match{
                    case Some(m) => if(m.isEmpty) openCorporatesBlank else openCorporatesNotBlank
                    case _ => openCorporatesBlank       
                  }}
                }
                case _ => ".extra *" #> NodeSeq.Empty
            }}
          }
          case _ =>  ".the_name *" #> NodeSeq.Empty & ".extra *" #> NodeSeq.Empty
        }
    }
    def transactionInformations = {
      ".amount *" #>  {currencySymbol + { transaction.amount match { 
								      					 case Some(o) => o.toString().stripPrefix("-")
								      					 case _ => ""
								      				   }}} &  
      ".narrative *" #> {transaction.metadata match{
          case Some(metadata) => displayNarrative(transaction,view)
          case _ => NodeSeq.Empty
        }} &     
    	".symbol *" #>  {transaction.amount match {
		        				  	case Some(a) => if (a < 0) "-" else "+"
		        				  	case _ => ""
		        				  }} &
	    ".out [class]" #> { transaction.amount match{
	        				  	case Some(a) => if (a <0) "out" else "in"
	        				  	case _ => ""
	        					} } &
      {transaction.metadata match {
        case Some(metadata) => metadata.comments match{
            case Some(comments) => ".comments_ext [href]" #> { view.permalink + "/transactions/" + transaction.id + "/comments" } &
                                   ".comment *" #> comments.length.toString()
            case _ =>  ".comments *" #> NodeSeq.Empty 
          }
        case _ =>  ".comments *" #> NodeSeq.Empty 
      }}
    }
   transactionInformations & 
   otherPartyInfo 
  }
  
  def displayAll = {
    def orderByDateDescending = (t1: ModeratedTransaction, t2: ModeratedTransaction) => {
      val date1 = t1.finishDate getOrElse new Date()
      val date2 = t2.finishDate getOrElse new Date()
      date1.after(date2)
    }
    
    val sortedTransactions = groupByDate(filteredTransactions.toList.sort(orderByDateDescending))
    
    "* *" #> sortedTransactions.map( transactionsForDay => {daySummary(transactionsForDay)})
  }
  
  def editableNarrative(transaction : ModeratedTransaction) = {
    var narrative = transaction.metadata.get.ownerComment.getOrElse("").toString
    CustomEditable.editable(narrative, SHtml.text(narrative, narrative = _), () => {
      //save the narrative
      transaction.metadata.get.ownerComment(narrative)
      Noop
    }, "Narrative")
  }

  def displayNarrative(transaction : ModeratedTransaction, currentView : View): NodeSeq = {
    if(currentView.canEditOwnerComment)
    	editableNarrative(transaction)	
    else Text(transaction.metadata.get.ownerComment.getOrElse("").toString)
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
      ".balance_number *" #> {currencySymbol + " " + aTransaction.balance } & 
      ".transaction_row *" #> transactionsForDay.map(t => individualTransaction(t))
  }

  def accountDetails = {
    filteredTransactions match {
      case Nil => "#accountName *" #> ""
      case x :: xs => {
        "#accountName *" #> {
          x.bankAccount match {
            case Some(bankAccount) => 
              bankAccount.label match {
                case Some(label) => label
                case _ => ""
              }
            case _ => ""
          }
        }
      }
    }
  }
  def hideSocialWidgets = {
    if(view.name!="Anonymous") ".box *" #> ""
    else ".box *+" #> "" 
  }
  def socialAdress = 
    ".fb-like [data-href] * " #> S.uri &
    ".twitter-share-button [data-url] " #> S.uri &
    "g:plusone [href] * " #> S.uri
}

