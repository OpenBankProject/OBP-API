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
          ".other_account_holder *" #> otherAccount.mediated_holder(accessLevel).getOrElse(FORBIDDEN) &
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
        e.mediated_comments(accessLevel).getOrElse(List()).flatMap(comment =>
          (".comment *" #> comment).apply(xhtml))
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
        e.mediated_comments(accessLevel) match{
          case Full(x) => {
            SHtml.ajaxForm(<p>{
        		SHtml.text("",comment => {
        		  /**
        		   * This was badly hacked together to meet a deadline
        		   */
        		  
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
    
  }
  
}