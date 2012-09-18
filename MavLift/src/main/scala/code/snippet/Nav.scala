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
}