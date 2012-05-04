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
import code.model._

class OBPTransactionSnippet {

  val NOOP_SELECTOR = "#i_am_an_id_that_should_never_exist" #> ""
  
  //Show all transactions from every account, for now
  val qry = QueryBuilder.start().get
  val envelopesToDisplay = OBPEnvelope.findAll(qry)
  
  val FORBIDDEN = "---"
  
  val consumer = {
    S.uri match{
      case uri if uri.endsWith("authorities") => "authorities"
      case uri if uri.endsWith("board") => "board"
      case uri if uri.endsWith("our-network") => "our-network"
      case uri if uri.endsWith("team") => "team"
      case uri if uri.endsWith("my-view") => "my-view"
      case _ => "anonymous"
    }
  }
  
  def displayAll = {
    
    val envelopes = groupByDate(envelopesToDisplay.sort(orderByDateDescending))
    
    "* *" #> envelopes.map( envsForDay => {
     daySummary(envsForDay)
    })
  }


  def individualEnvelope(env: OBPEnvelope): CssSel = {
    val envelopeID = env.id

    val transaction = env.obp_transaction.get
    val transactionDetails = transaction.details.get
    val transactionValue = transactionDetails.value.get
    val thisAccount = transaction.this_account.get
    val otherAccount = transaction.other_account.get

    var narrative = env.narrative.get

    val theAccount = thisAccount.theAccount
    val otherUnmediatedHolder = otherAccount.holder.get
    val otherMediatedHolder = otherAccount.mediated_holder(consumer)

    val aliasType = {
      otherMediatedHolder._2 match {
        case Full(APublicAlias) => "public"
        case Full(APrivateAlias) => "private"
        case _ => "None"
      }
    }

    //get some fields from the account
    def getAccountField(getFieldFunction: OtherAccount => String): String = {
      val fieldValue = for {
        a <- theAccount
        oacc <- a.otherAccounts.get.find(o => otherUnmediatedHolder.equals(o.holder.get))
      } yield getFieldFunction(oacc)

      fieldValue getOrElse ""
    }

    val moreInfo = getAccountField((acc: OtherAccount) => acc.moreInfo.get)
    val logoImageSrc = getAccountField((acc: OtherAccount) => acc.imageUrl.get)
    val otherAccWebsiteUrl = getAccountField((acc: OtherAccount) => acc.url.get)
    val openCorporatesUrl = getAccountField((acc: OtherAccount) => acc.openCorporatesUrl.get)

    val amount = transactionValue.mediated_amount(consumer).getOrElse(FORBIDDEN)
    val name = otherMediatedHolder._1.getOrElse(FORBIDDEN)

    //TODO: discuss if we really need public/private disctinction when displayed,
    //it confuses more than it really informs the user of much
    //the view either shows more data or not, only that it is an alias is really informative
    def aliasRelatedInfo: CssSel = {
      aliasType match {
        case "public" =>
          ".alias_indicator [class+]" #> "alias_indicator_public" &
            ".alias_indicator *" #> "(Alias)"
        case "private" =>
          ".alias_indicator [class+]" #> "alias_indicator_private" &
            ".alias_indicator *" #> "(Alias)"
        case _ => NOOP_SELECTOR
      }
    }

    def otherPartyInfo: CssSel = {

      //don't show more info if there is a public alias
      def publicAliasInfo = {
        ".narrative *" #> NodeSeq.Empty &
          ".extra *" #> NodeSeq.Empty
      }

      //The extra information about the other party in the transaction
      def additionalInfo = {

        def moreInfoBlank =
          ".other_account_more_info" #> NodeSeq.Empty &
            ".other_account_more_info_br" #> NodeSeq.Empty

        def moreInfoNotBlank =
          ".other_account_more_info *" #> moreInfo.take(50) //TODO: show ... if info string is actually truncated

        def logoBlank =
          NOOP_SELECTOR

        def logoNotBlank =
          ".other_account_logo_img [src]" #> logoImageSrc

        def websiteBlank =
          ".other_acc_link" #> NodeSeq.Empty & //If there is no link to display, don't render the <a> element
            ".other_acc_link_br" #> NodeSeq.Empty

        def websiteNotBlank =
          ".other_acc_link [href]" #> otherAccWebsiteUrl

        def openCorporatesBlank =
          ".open_corporates_link" #> NodeSeq.Empty

        def openCorporatesNotBlank =
          ".open_corporates_link [href]" #> openCorporatesUrl

        ".narrative *" #> displayNarrative(env) &
          {
            if (moreInfo.equals("")) moreInfoBlank
            else moreInfoNotBlank
          } &
          {
            if (logoImageSrc.equals("")) logoBlank
            else logoNotBlank
          } &
          {
            if (otherAccWebsiteUrl.equals("")) websiteBlank
            else websiteNotBlank
          } &
          {
            if (openCorporatesUrl.equals("")) openCorporatesBlank
            else openCorporatesNotBlank
          }
      }

      if (aliasType.equals("public")) publicAliasInfo
      else additionalInfo

    }

    def commentsInfo = {
      {
        //If we're not allowed to see comments, don't show the comments section
        if (env.mediated_obpComments(consumer).isEmpty) ".comments *" #> ""
        else NOOP_SELECTOR
      } &
        ".comments_ext [href]" #> { consumer + "/transactions/" + envelopeID + "/comments" } &
        ".comment *" #> env.mediated_obpComments(consumer).getOrElse(Nil).size &
        ".symbol *" #> { if (amount.startsWith("-")) "-" else "+" } &
        ".out [class]" #> { if (amount.startsWith("-")) "out" else "in" }
    }

    ".the_name *" #> name &
    ".amount *" #> { "€" + amount.stripPrefix("-") } & //TODO: Format this number according to locale
    aliasRelatedInfo &
    otherPartyInfo &
    commentsInfo
  }
  
  def editableNarrative(envelope: OBPEnvelope) = {
    var narrative = envelope.narrative.get

    CustomEditable.editable(narrative, SHtml.text(narrative, narrative = _), () => {
      //save the narrative
      envelope.narrative(narrative).save
      Noop
    }, "Narrative")
  }

  def displayNarrative(envelope: OBPEnvelope): NodeSeq = {
    consumer match {
      case "my-view" => editableNarrative(envelope)
      case _ => Text(envelope.mediated_narrative(consumer).getOrElse(FORBIDDEN))
    }
  }

  def orderByDateDescending = (e1: OBPEnvelope, e2: OBPEnvelope) => {
    val date1 = e1.obp_transaction.get.details.get.mediated_completed(consumer) getOrElse new Date()
    val date2 = e2.obp_transaction.get.details.get.mediated_completed(consumer) getOrElse new Date()
    date1.after(date2)
  }

  def hasSameDate(e1: OBPEnvelope, e2: OBPEnvelope): Boolean = {
    val t1 = e1.obp_transaction.get
    val t2 = e2.obp_transaction.get

    t1.details.get.completed.get.equals(t2.details.get.completed.get)
  }

  /**
   * Splits a list of envelopes into a list of lists, where each of these new lists
   * is for one day.
   *
   * Example:
   * 	input : List(Jan 5,Jan 6,Jan 7,Jan 7,Jan 8,Jan 9,Jan 9,Jan 9,Jan 10)
   * 	output: List(List(Jan 5), List(Jan 6), List(Jan 7,Jan 7),
   * 				 List(Jan 8), List(Jan 9,Jan 9,Jan 9), List(Jan 10))
   */
  def groupByDate(list: List[OBPEnvelope]): List[List[OBPEnvelope]] = {
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

  def formatDate(date: Box[Date]): String = {
    val dateFormat = new SimpleDateFormat("MMMM dd, yyyy")
    date match {
      case Full(d) => dateFormat.format(d)
      case _ => FORBIDDEN
    }
  }
  
  def daySummary(envsForDay: List[OBPEnvelope]) = {
    val dailyDetails = envsForDay.last.obp_transaction.get.details.get
    val date = formatDate(dailyDetails.mediated_completed(consumer))
    //TODO: This isn't really going to be the right balance, as there's no way of telling which one was the actual
    // last transaction of the day yet
    val balance = dailyDetails.new_balance.get.mediated_amount(consumer) getOrElse FORBIDDEN
    ".date *" #> date &
      ".balance_number *" #> { "€" + balance } & //TODO: support other currencies, format the balance according to locale
      ".transaction_row *" #> envsForDay.map(env => individualEnvelope(env))
  }
   
}

