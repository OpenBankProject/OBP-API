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
import code.model.dataAccess.OBPEnvelope
import code.model.dataAccess.OBPAccount.{APublicAlias,APrivateAlias}
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
import code.model.dataAccess.{OBPAccount,OBPUser}
import net.liftweb.common.Loggable
import code.model.dataAccess.Account
import code.model.traits.{ModeratedTransaction,Public,Private,NoAlias}
import java.util.Currency

/**
 * This whole class is a rather hastily put together mess
 */
class Comments(transaction : ModeratedTransaction) extends Loggable{

  def commentPageTitle(xhtml: NodeSeq): NodeSeq = {
    val FORBIDDEN = "---"
    val NOOP_SELECTOR = "#i_am_an_id_that_should_never_exist" #> ""
    val dateFormat = new SimpleDateFormat("EEE MMM dd yyyy")
    var theCurrency = FORBIDDEN
    def formatDate(date: Box[Date]): String = {
      date match {
        case Full(d) => dateFormat.format(d)
        case _ => FORBIDDEN
      }
    }

    (
      ".amount *" #>{ 
        val amount = transaction.amount match {
          case Some(amount) => amount.toString
          case _ => FORBIDDEN
        }
        theCurrency = transaction.currency match {
          case Some(currencyISOCode) => tryo{
                    Currency.getInstance(currencyISOCode)
                  } match {
                    case Full(currency) => currency.getSymbol(S.locale)
                    case _ => FORBIDDEN
                  }
          case _ => FORBIDDEN
        } 
        {amount + " " + theCurrency}
      } &
      ".other_account_holder *" #> {
        transaction.otherBankAccount match {
          case Some(otherBankaccount) =>{
            ".the_name" #> otherBankaccount.label.display &
            {otherBankaccount.label.aliasType match {
                case Public => ".alias_indicator [class+]" #> "alias_indicator_public" &
                    ".alias_indicator *" #> "(Alias)"
                case Private => ".alias_indicator [class+]" #> "alias_indicator_private" &
                    ".alias_indicator *" #> "(Alias)"
                case _ => NOOP_SELECTOR
            }} 
          }
          case _ => "* *" #> FORBIDDEN
        }
      } &
      ".date_cleared *" #> {
        transaction.finishDate match {
          case Some(date) => formatDate(Full(date))
          case _ => FORBIDDEN 
        }
      } &
      ".new_balance *" #> {
            transaction.balance + " " + theCurrency
      }
    ).apply(xhtml)
  }
  
  def showAll(xhtml: NodeSeq) : NodeSeq = {
    def noComments = (".the_comments *" #> "No comments").apply(xhtml)
    transaction.metadata match {
      case Some(metadata)  => 
        metadata.comments match {
          case Some(comments) => 
            if(comments.size==0)
              noComments
            else
              comments.flatMap(comment => {
                (".comment *" #> comment.text &
                ".commenter_email *" #> {"- use email"}).apply(xhtml)
              })
          case _ => noComments 
        }
      case _ => noComments
    }
  }
  
  
  def addComment(xhtml: NodeSeq) : NodeSeq = {
    val accessLevel = S.param("accessLevel") getOrElse "anonymous"
    val envelopeID = S.param("envelopeID") getOrElse ""
    OBPEnvelope.find(envelopeID) match{
      case Full(e) => {
        e.mediated_obpComments(accessLevel) match{
          case Full(x) => {
            SHtml.ajaxForm(<p>{
          		SHtml.text("",comment => {
          		  OBPUser.currentUser match{
          		    case Full(u) => e.addComment(u.email.get, comment)
          		    case _ => logger.warn("No logged in user found when someone tried to post a comment. This shouldn't happen.")
          		  }
          		  
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
  
}