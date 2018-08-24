/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd

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
TESOBE Ltd
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
import net.liftweb.http.S
import net.liftweb.http.LiftRules
import net.liftweb.util.Helpers._
import net.liftweb.sitemap.Loc
import net.liftweb.common.Box
import net.liftweb.common.Full
import net.liftweb.common.Empty
import net.liftweb.sitemap.SiteMapSingleton

class Nav {

  def eraseMenu =
     "* * " #> ""
  def item = {
    val attrs = S.prefixedAttrsToMetaData("a")
    val name = S.attr("name").getOrElse("")
    //useful to show links that require login, that we still want to show to non-logged in users
    val showEvenIfRestricted : Boolean = {
      tryo {
        val attr = S.attr("showEvenIfRestricted").getOrElse("")
        attr.toBoolean
      }
    }.getOrElse(false)

    val loc =
      for{
        sitemap <- LiftRules.siteMap
        l <- {
          val sitemapSingleton = new SiteMapSingleton()
          if(showEvenIfRestricted) sitemapSingleton.findLoc(name)
          else sitemapSingleton.findAndTestLoc(name)
        }
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

  def markIfSelected(href : String) : Box[String]= {
    val currentHref = S.uri
    if(href.equals(currentHref)) Full("selected")
    else Empty
  }
}