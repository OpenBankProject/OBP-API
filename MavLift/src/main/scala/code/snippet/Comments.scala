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
    Benali Ayoub : ayoub AT tesobe DOT com

 */
package code.snippet

import net.liftweb.http.js.JsCmds.Noop
import net.liftweb.http.TemplateFinder
import net.liftweb.util.Helpers._
import net.liftweb.http.S
import code.model.dataAccess.OBPEnvelope
import code.model.dataAccess.OBPAccount.{APublicAlias,APrivateAlias}
import net.liftweb.common.Full
import scala.xml.NodeSeq
import net.liftweb.http.SHtml
import net.liftweb.common.Box
import net.liftweb.http.js.JsCmd
import net.liftweb.http.js.JE.JsRaw
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
import code.model.traits.{ModeratedTransaction,Public,Private,NoAlias,Comment}
import java.util.Currency
import net.liftweb.http.js.jquery.JqJsCmds.AppendHtml

/**
 * This whole class is a rather hastily put together mess
 */
class Comments(transaction : ModeratedTransaction) extends Loggable{

  val commentDateFormat = new SimpleDateFormat("kk:mm:ss EEE MMM dd yyyy")
  
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
  
  def showAll = {
    def noComments = ".container *" #> "No comments"
    transaction.metadata match {
      case Some(metadata)  => 
        metadata.comments match {
          case Some(comments) => 
            if(comments.size==0)
              noComments
            else
            ".container" #>
            { 
              def orderByDateDescending = (comment1 : Comment, comment2 : Comment) =>
                comment1.datePosted.before(comment2.datePosted)
              ".comment" #>
                comments.sort(orderByDateDescending).map(comment => {
                  ".text *" #> {comment.text} &
                  ".commentDate" #> {commentDateFormat.format(comment.datePosted)} &
                  ".userInfo *" #> {
                      comment.postedBy match {
                        case Full(user) => {" -- " + user.theFistName + " "+ user.theLastName}
                        case _ => "-- user not found" 
                      }
                  }
                })
            }
          case _ => noComments 
        }
      case _ => noComments
    }
  }

  def addComment(xhtml: NodeSeq) : NodeSeq = {
    OBPUser.currentUser match {
      case Full(user) =>     
        transaction.metadata match {
          case Some(metadata) =>
            metadata.addComment match {
              case Some(addComment) => {
                var commentText = ""
                var commentDate = new Date
                SHtml.ajaxForm(
                  SHtml.textarea("put a comment here",comment => {
                    commentText = comment
                    commentDate = new Date
                    addComment(user.id,comment,commentDate)},
                    ("rows","4"),("cols","50")) ++
                  SHtml.ajaxSubmit("add a comment",() => {
                    val commentXml = TemplateFinder.findAnyTemplate(List("templates-hidden","_comment")).map( 
                      ".text *" #> {commentText} &
                      ".commentDate" #> {commentDateFormat.format(commentDate)} &
                      ".userInfo *" #> { " -- " + user.theFistName + " "+ user.theLastName}
                    )
                    AppendHtml("comment_list",commentXml.getOrElse(NodeSeq.Empty))
                  },("id","submitComment"))
                )
              }
              case _ => (".add" #> "You cannot comment transactions on this view").apply(xhtml)
            }
          case _ => (".add" #> "You Cannot comment transactions on this view").apply(xhtml)
        }
      case _ => (".add" #> "You need to login before you can submit a comment").apply(xhtml) 
    }
  }
}