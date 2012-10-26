package code.snippet
import scala.xml.NodeSeq
import net.liftweb.http.S
import net.liftweb.http.LiftRules
import net.liftweb.util.Helpers
import net.liftweb.util.Helpers._
import scala.xml.Group
import net.liftweb.sitemap.Loc
import net.liftweb.common.Box
import net.liftweb.common.Full
import net.liftweb.common.Empty
import net.liftweb.sitemap.SiteMapSingleton
import code.model.dataAccess.{OBPUser,Account, MongoDBLocalStorage}
import net.liftweb.http.SHtml
import net.liftweb.http.js.JsCmd
import net.liftweb.http.js.JsCmds._Noop

class Nav {

  def group = {
    val attrs = S.prefixedAttrsToMetaData("a")
    val group = S.attr("group").getOrElse("")

    val locs = (for{
      sitemap <- LiftRules.siteMap
    } yield sitemap.locForGroup(group)).getOrElse(List())
    
    ".navitem *" #> {
      locs.map(l => {
        ".navlink [href]" #> l.calcDefaultHref &
        ".navlink *" #> l.linkText &
        ".navlink [class+]" #> markIfSelected(l.calcDefaultHref)
    })
    }
  }
  def eraseMenu = 
     "* * " #> ""  
  def views :net.liftweb.util.CssSel = {
    val url = S.uri.split("/",0)
    if(url.size>4)
      OBPUser.currentUser match {
        case Full(user) => {
          val viewsList = user.permittedViews(url(2), url(4))
          if(viewsList.size>0)
            ".navitem *" #> {
            viewsList.toList.map(view => {
              val viewUrl = "/banks/"+url(2)+"/accounts/"+url(4)+"/"+view.permalink
              ".navlink [href]" #>  {viewUrl} &
              ".navlink *" #> view.name &
              ".navlink [class+]" #> markIfSelected(viewUrl)  
            })}
          else
            eraseMenu
        }
        case _ => MongoDBLocalStorage.getAccount(url(2), url(4)) match {
          case Full(account) => if(account.anonAccess.is)
                                  ".navitem *" #> {
                                     val anoymousUrl = "/banks/"+url(2)+"/accounts/"+url(4)+"/anonymous"
                                    ".navlink [href]" #>  {anoymousUrl} &
                                    ".navlink *" #> "Anonymous" &
                                    ".navlink [class+]" #> markIfSelected(anoymousUrl)  
                                  }
                                else
                                  eraseMenu
          case _ => eraseMenu
        }
        
      }
    else
       eraseMenu      
  }
  def management = {
    OBPUser.currentUser match {
      case Full(user) => {
        val url = S.uri.split("/",0)
        if(url.size>4)
          if(user.hasMangementAccess(url(2), url(4)))
          {
            val managementUrl = "/banks/"+url(2)+"/accounts/"+url(4)+"/management"
            ".navlink [href]" #>  {managementUrl} &
            ".navlink *" #> "Management" &
            ".navlink [class+]" #> markIfSelected(managementUrl)  
          }
          else
            eraseMenu
        else
          eraseMenu     
      }
      case _ => eraseMenu
    }
  }
  def item = {
    val attrs = S.prefixedAttrsToMetaData("a")
    val name = S.attr("name").getOrElse("")
    val loc = (for{
      sitemap <- LiftRules.siteMap
      l <- new SiteMapSingleton().findAndTestLoc(name)
    } yield l)
    
    ".navitem *" #>{
      loc.map(l => {
        ".navlink [href]" #> l.calcDefaultHref &
        ".navlink *" #> l.linkText &
        ".navlink [class+]" #> markIfSelected(l.calcDefaultHref)
      })
    }
  }
  
  def markIfSelected(href : String) : Box[String]= {
    val currentHref = S.uri
    if(href.equals(currentHref)) Full("selected")
    else Empty
  }

  def listAccounts  = {
    var accounts : List[(String, String)] = List()
    OBPUser.currentUser match {
      case Full(user) => Account.findAll.map(account =>
        if(user.permittedViews(account.bankPermalink.is, account.permalink.is).size != 0)
          accounts ::= (account.bankPermalink + "," + account.permalink, account.bankName + " - " + account.name)  
        )
      case _ => Account.findAll.map(account => 
        if(account.anonAccess.is)
          accounts ::= (account.bankPermalink + "," + account.permalink, account.bankName + " - " + account.name)   
        )  
    }
    accounts ::= ("0","--> Choose an account")
    def redirect(selectValue : String) : JsCmd = 
    {
      val bankAndaccount = selectValue.split(",",0)      
      if(bankAndaccount.size==2)
        MongoDBLocalStorage.getAccount(bankAndaccount(0), bankAndaccount(1)) match {
          case Full(acc) => S.redirectTo("/banks/" + bankAndaccount(0) + "/accounts/" + bankAndaccount(1) +"/anonymous")
          case _ => _Noop
        }
      else
        _Noop
    } 
    def computeDefaultValue : Box[String] = 
    {
      val url = S.uri.split("/",0)
      var output="0"
      if(url.size>4)
        output = url(2) + "," + url(4)
      Full(output)
    }  
    "#accountList *" #> {
      computeDefaultValue match {
        case Full("postbank,tesobe") =>
          SHtml.ajaxSelect(accounts,computeDefaultValue,redirect _)
        case _ =>
          NodeSeq.Empty
      }
    }
  }
}