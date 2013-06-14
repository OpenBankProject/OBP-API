/**
Open Bank Project - Transparency / Social Finance Web Application
Copyright (C) 2011, 2012, TESOBE / Music Pictures Ltd

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE / Music Pictures Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)
  by
  Simon Redfern : simon AT tesobe DOT com
  Stefan Bethge : stefan AT tesobe DOT com
  Everett Sochowski : everett AT tesobe DOT com
  Ayoub Benali: ayoub AT tesobe DOT com

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
import code.model.BankAccount

class Nav {

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
                                     val anoymousUrl = "/banks/"+url(2)+"/accounts/"+url(4)+"/public"
                                    ".navlink [href]" #>  {anoymousUrl} &
                                    ".navlink *" #> "Public" &
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
    val loc =
      for{
        sitemap <- LiftRules.siteMap
        l <- new SiteMapSingleton().findAndTestLoc(name)
      } yield l

    ".navitem *" #>{
      loc.map(navItemSelector)
    }
  }

  def navItemSelector(l : Loc[_]) = {
     ".navlink [href]" #> l.calcDefaultHref &
     ".navlink *" #> l.linkText &
     ".navlink [class+]" #> markIfSelected(l.calcDefaultHref)
  }

  def onlyOnSomePages = {
    val pages : List[String]= S.attr("pages").map(_.toString.split(",").toList).getOrElse(Nil)

    val locs = pages.flatMap(page => (for{
      sitemap <- LiftRules.siteMap
      l <- new SiteMapSingleton().findAndTestLoc(page)
    } yield l))

    val isPage = locs.map(l => {
      //hack due to deadline to fix / and /index being the same
      val currentPage = if(S.uri == "/") "/index" else S.uri
      (l.calcDefaultHref == currentPage)
    }).exists(_ == true)

    if(isPage) item
    else "* *" #> ""
  }

  def privilegeAdmin = {
    val url = S.uri.split("/", 0)

    def hide = ".navitem *" #> ""

    def getPrivilegeAdmin = for {
      bankAccount <- BankAccount(url(2), url(4))
      user <- OBPUser.currentUser
      if (user.hasOwnerPermission(bankAccount))
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
        if (LocalStorage.correctBankAndAccount(bankAndaccount(0), bankAndaccount(1)))
          //TODO : the account may not has an public view, so this redirection would retun a 404
          //a better solution has to be found
          S.redirectTo("/banks/" + bankAndaccount(0) + "/accounts/" + bankAndaccount(1) +"/public")
        else
        _Noop
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