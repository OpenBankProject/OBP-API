package bootstrap.liftweb

import net.liftweb._
import util._
import common._
import http._
import sitemap._
import Loc._
import mapper._
import code.model._
import com.tesobe.utils._
import myapp.model.MongoConfig
import net.liftweb.util.Helpers._
/**
 * A class that's instantiated early and run.  It allows the application
 * to modify lift's environment
 */
class Boot {
  def boot {

    // This sets up MongoDB config
    MongoConfig.init

    if (!DB.jndiJdbcConnAvailable_?) {
      val vendor = 
	new StandardDBVendor(Props.get("db.driver") openOr "org.h2.Driver",
			     Props.get("db.url") openOr 
			     "jdbc:h2:lift_proto.db;AUTO_SERVER=TRUE",
			     Props.get("db.user"), Props.get("db.password"))

      LiftRules.unloadHooks.append(vendor.closeAllConnections_! _)

      DB.defineConnectionManager(DefaultConnectionIdentifier, vendor)
    }

    // Use Lift's Mapper ORM to populate the database
    // you don't need to use Mapper to use Lift... use
    // any ORM you want
    Schemifier.schemify(true, Schemifier.infoF _, User)

    // where to search snippet
    LiftRules.addToPackages("code")

    // For some restful stuff
    //LiftRules.dispatch.append(OBPRest) // stateful -- associated with a servlet container session
    LiftRules.statelessDispatchTable.append(OBPRest) // stateless -- no session created

    // Build SiteMap
    def sitemap = SiteMap(
      //Menu.i("Home") / "index" >> User.AddUserMenusAfter //, // the simple way to declare a menu

      // more complex because this menu allows anything in the
      // /static path to be visible
      //Menu(Loc("Static", Link(List("static"), true, "/static/index"), 
	  //     "Static Content"))
      
      // A menu with submenus
		/*, Menu.i("Info") / "info" submenus(
				Menu.i("About") / "about" >> Hidden >> LocGroup("bottom"),
				Menu.i("Contact") / "contact",
				Menu.i("Feedback") / "feedback" >> LocGroup("bottom")
				)*/
            // A Simon menu with submenus
		/*, Menu.i("Simon") / "simon" submenus(
				Menu.i("SAbout") / "sabout" >> Hidden >> LocGroup("bottom"),
				Menu.i("SContact") / "scontact",
				Menu.i("SFeedback") / "sfeedback" >> LocGroup("bottom")
				) */
    //, Menu.i("x") / "x" submenus(
		//		Menu.i("y") / "y"
		//		)
    Menu.i("Accounts") / "accounts" submenus(
				Menu.i("TESOBE") / "accounts" / "tesobe" submenus(
          Menu.i("Anonymous") / "accounts" / "tesobe" / "anonymous",
          Menu.i("Our Network") / "accounts" / "tesobe" / "our-network",
          Menu.i("Team") / "accounts" / "tesobe" / "team",
          Menu.i("Board") / "accounts" / "tesobe" / "board",
          Menu.i("Authorities") / "accounts" / "tesobe" / "authorities",
          Menu.i("Comments") / "comments" >> Hidden
				)
      )
    )

    LiftRules.statelessRewrite.append{
        case RewriteRequest(ParsePath("accounts" :: "tesobe" :: accessLevel :: "transactions" :: envelopeID :: "comments" :: Nil, "", true, _), _, therequest) =>
          					RewriteResponse("comments" :: Nil, Map("envelopeID" -> envelopeID, "accessLevel" -> accessLevel))
    }

    def sitemapMutators = User.sitemapMutator

    // set the sitemap.  Note if you don't want access control for
    // each page, just comment this line out.
    LiftRules.setSiteMapFunc(() => sitemapMutators(sitemap))

    // Use jQuery 1.4
    LiftRules.jsArtifacts = net.liftweb.http.js.jquery.JQuery14Artifacts

    //Show the spinny image when an Ajax call starts
    LiftRules.ajaxStart =
      Full(() => LiftRules.jsArtifacts.show("ajax-loader").cmd)
    
    // Make the spinny image go away when it ends
    LiftRules.ajaxEnd =
      Full(() => LiftRules.jsArtifacts.hide("ajax-loader").cmd)

    // Force the request to be UTF-8
    LiftRules.early.append(_.setCharacterEncoding("UTF-8"))

    // What is the function to test if a user is logged in?
    LiftRules.loggedInTest = Full(() => User.loggedIn_?)

    // Use HTML5 for rendering
    LiftRules.htmlProperties.default.set((r: Req) =>
      new Html5Properties(r.userAgent))    

    // Make a transaction span the whole HTTP request
    S.addAround(DB.buildLoanWrapper)
  }
}
