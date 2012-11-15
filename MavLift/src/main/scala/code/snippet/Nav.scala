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
import code.model.dataAccess.{OBPUser,Account, LocalStorage}
import net.liftweb.http.SHtml
import net.liftweb.http.js.JsCmd
import net.liftweb.http.js.JsCmds._Noop
import code.model.traits.BankAccount

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
          val bankAccount = BankAccount(url(2), url(4))
          val viewsListBox = for {
            b <- bankAccount
          } yield user.permittedViews(b)
          val viewsList = viewsListBox getOrElse Nil
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
        case _ => LocalStorage.getAccount(url(2), url(4)) match {
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
    val url = S.uri.split("/", 0)

    def getManagement = for {
      user <- OBPUser.currentUser
      bankAccount <- BankAccount(url(2), url(4))
      if (user.hasMangementAccess(bankAccount))
    } yield {
      val managementUrl = "/banks/" + url(2) + "/accounts/" + url(4) + "/management"
      ".navlink [href]" #> { managementUrl } &
        ".navlink *" #> "Management" &
        ".navlink [class+]" #> markIfSelected(managementUrl)
    }

    if (url.size > 4) getManagement getOrElse eraseMenu
    else eraseMenu

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
  
  def privilegeAdmin = {
    val url = S.uri.split("/", 0)

    def hide = ".navitem *" #> ""
    def getPrivilegeAdmin = for {

      bankAccount <- BankAccount(url(2), url(4))
      if (OBPUser.hasOwnerPermission(bankAccount))
      loc <- new SiteMapSingleton().findAndTestLoc("Privilege Admin")
    } yield {
      ".navitem *" #> {
        ".navlink [href]" #> loc.calcDefaultHref &
          ".navlink *" #> loc.linkText &
          ".navlink [class+]" #> markIfSelected(loc.calcDefaultHref)
      }
    }
    
    if(url.size > 4) getPrivilegeAdmin.getOrElse(hide) else hide
  }
  
  def markIfSelected(href : String) : Box[String]= {
    val currentHref = S.uri
    if(href.equals(currentHref)) Full("selected")
    else Empty
  }

  def listAccounts  = {
    var accounts : List[(String, String)] = List()
    OBPUser.currentUser match {
      case Full(user) => Account.findAll.map(account => {
        val bankAccount = Account.toBankAccount(account)
        if(user.permittedViews(bankAccount).size != 0)
          accounts ::= (account.bankPermalink + "," + account.permalink, account.bankName + " - " + account.name)  
      })
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
        LocalStorage.getAccount(bankAndaccount(0), bankAndaccount(1)) match {
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