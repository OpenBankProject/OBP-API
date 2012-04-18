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

import net.liftweb.util.Helpers._
import net.liftweb.http.S
import code.model.OBPEnvelope
import net.liftweb.common.Full
import scala.xml.NodeSeq
import net.liftweb.http.SHtml
import net.liftweb.common.Box
import net.liftweb.http.js.JsCmd
import net.liftweb.http.js.JsCmds.RedirectTo
import net.liftweb.http.SessionVar
import scala.xml.Text
import net.liftweb.json.JsonAST.JObject
import net.liftweb.json.JsonAST.JField
import net.liftweb.json.JsonAST.JString
import net.liftweb.json.JsonAST.JArray
import net.liftweb.http.StringField
import java.util.Date
import java.text.SimpleDateFormat
import code.model.OBPAccount._
import code.model._

/**
 * This whole class is a rather hastily put together mess
 */
class Comments{

  def commentPageTitle(xhtml: NodeSeq): NodeSeq = {
    val accessLevel = S.param("accessLevel") getOrElse "anonymous"
    val envelopeID = S.param("envelopeID") getOrElse ""

    val envelope = OBPEnvelope.find(envelopeID)

    envelope match {
      case Full(e) => {

        val FORBIDDEN = "---"
        val dateFormat = new SimpleDateFormat("EEE MMM dd yyyy")

        val transaction = e.obp_transaction.get
        val transactionDetails = transaction.details.get
        val transactionValue = transactionDetails.value.get
        val thisAccount = transaction.this_account.get
        val otherAccount = transaction.other_account.get

        def formatDate(date: Box[Date]): String = {
          date match {
            case Full(d) => dateFormat.format(d)
            case _ => FORBIDDEN
          }
        }

        (
          ".amount *" #> transactionValue.mediated_amount(accessLevel).getOrElse(FORBIDDEN) &
          ".other_account_holder *" #> {
	        val otherHolder = otherAccount.mediated_holder(accessLevel)
	        val holderName = otherHolder._1 match {
	          case Full(h) => h
	          case _ => FORBIDDEN
	        }
	        val aliasType = otherHolder._2 match{
	          case Full(APublicAlias) => "/media/images/public_alias.png"
	          case Full(APrivateAlias) => "/media/images/private_alias.png"
	          case _ => ""
	        }
	        {aliasType + holderName}
	      } &
          ".currency *" #> transactionValue.mediated_currency(accessLevel).getOrElse(FORBIDDEN) &
          ".date_cleared *" #> formatDate(transactionDetails.mediated_posted(accessLevel)) &
          ".new_balance *" #> {
            transactionDetails.new_balance.get.mediated_amount(accessLevel).getOrElse(FORBIDDEN) + " " +
              transactionDetails.new_balance.get.mediated_currency(accessLevel).getOrElse(FORBIDDEN)
          }).apply(xhtml)

      }
      case _ => Text("")
    }

  }
  
  def showAll(xhtml: NodeSeq) : NodeSeq = {
    val accessLevel = S.param("accessLevel") getOrElse "anonymous"
    val envelopeID = S.param("envelopeID") getOrElse ""
    
    val envelope = OBPEnvelope.find(envelopeID)
    
    envelope match{
      case Full(e) => {
       val comments = e.mediated_obpComments(accessLevel) getOrElse List()
       if(comments.size == 0) (".the_comments *" #> "No comments").apply(xhtml)
       else comments.flatMap(comment => {
          (".comment *" #> comment.text.is &
           ".commenter_email *" #> {"- " + comment.email}).apply(xhtml)
        })
      }
      case _ => (".comment *" #> "No comments").apply(xhtml)
    }
  }
  
  
    def addComment(xhtml: NodeSeq) : NodeSeq = {
    val accessLevel = S.param("accessLevel") getOrElse "anonymous"
    val envelopeID = S.param("envelopeID") getOrElse ""
    
    val envelope = OBPEnvelope.find(envelopeID)
    
    envelope match{
      case Full(e) => {
        e.mediated_obpComments(accessLevel) match{
          case Full(x) => {
            SHtml.ajaxForm(<p>{
        		SHtml.text("",comment => {
        		  /**
        		   * This was badly hacked together to meet a deadline
        		   */
        		  
        		  val comments = e.obp_comments.get
        		  //For now, only logged in users can post comments, so "in theory" there should always be a logged in user here,
        		  // but the other case should probably get handled as well.
        		  val c2 = comments ++ List(OBPComment.createRecord.email(User.currentUser.get.email.get).text(comment))
        		  e.obp_comments(c2)
        		  e.saveTheRecord()
        		  
        		})}</p> ++
        			<input type="submit" onClick="history.go(0)" value="Add Comment"/>
            )
          }
          case _ => Text("Anonymous users may not view or submit comments")
        }
        
      }
      case _ => Text("Cannot add comment to non-existant transaction")
    }
    
  }
  
/*  def addComment(xhtml: NodeSeq) : NodeSeq = {
    val accessLevel = S.param("accessLevel") getOrElse "anonymous"
    val envelopeID = S.param("envelopeID") getOrElse ""
    
    val envelope = OBPEnvelope.find(envelopeID)
    
    envelope match{
      case Full(e) => {
        e.mediated_comments(accessLevel) match{
          case Full(x) => {
            SHtml.ajaxForm(<p>{
        		SHtml.text("",comment => {
        		  *//**
        		   * This was badly hacked together to meet a deadline
        		   *//*
        		  
        		  val comments = e.comments.get
        		  val c2 = comments ++ List(comment)
        		  e.comments(c2)
        		  e.saveTheRecord()
        		  
        		})}</p> ++
        			<input type="submit" onClick="history.go(0)" value="Add Comment"/>
            )
          }
          case _ => Text("Anonymous users may not view or submit comments")
        }
        
      }
      case _ => Text("Cannot add comment to non-existant transaction")
    }
    
  }*/
  
}