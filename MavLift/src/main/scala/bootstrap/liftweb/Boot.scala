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
package bootstrap.liftweb

import code.snippet._
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
import net.liftweb.widgets.tablesorter.TableSorter
import net.liftweb.json.JsonDSL._
/**
 * A class that's instantiated early and run.  It allows the application
 * to modify lift's environment
 */
class Boot extends Loggable{
  def boot {

    // This sets up MongoDB config
    MongoConfig.init

    if (!DB.jndiJdbcConnAvailable_?) {
      val driver = Props.get("db.driver") openOr "org.h2.Driver"
      val vendor = 
	      new StandardDBVendor(driver,
			     Props.get("db.url") openOr 
			     "jdbc:h2:lift_proto.db;AUTO_SERVER=TRUE",
			     Props.get("db.user"), Props.get("db.password"))

      logger.debug("Using database driver: " + driver)
      LiftRules.unloadHooks.append(vendor.closeAllConnections_! _)

      DB.defineConnectionManager(DefaultConnectionIdentifier, vendor)
    }

    // Use Lift's Mapper ORM to populate the database
    // you don't need to use Mapper to use Lift... use
    // any ORM you want
    Schemifier.schemify(true, Schemifier.infoF _, User, Privilege)

    // where to search snippet
    LiftRules.addToPackages("code")

    // For some restful stuff
    LiftRules.statelessDispatchTable.append(OBPRest) // stateless -- no session created

    val theOnlyAccount = Account.find(("holder", "Music Pictures Limited"))
    
    def check(bool: Boolean) : Box[LiftResponse] = {
      if(bool){
        Empty
      }else{
        Full(PlainTextResponse("unauthorized"))
      }
    }
    
    // Build SiteMap
    val sitemap = List(
          Menu.i("Home") / "index",
          Menu.i("Privilege Admin") / "admin" / "privilege" >> TestAccess(() => {
            check(theOnlyAccount match{
              case Full(a) => User.hasOwnerPermission(a)
              case _ => false
            })
          }) >> LocGroup("admin") 
          	submenus(Privilege.menus : _*),
          Menu.i("Accounts") / "accounts" submenus(
				Menu.i("TESOBE") / "accounts" / "tesobe" submenus(
		  Menu.i("TESOBE View") / "accounts" / "tesobe" / "my-view" >> LocGroup("owner") >> TestAccess(() => {
		    check(theOnlyAccount match{
		      case Full(a) => User.hasOwnerPermission(a)
		      case _ => false
		    })
		  }),
		  Menu.i("Management") / "accounts" / "tesobe" / "management" >> LocGroup("owner") >> TestAccess(() => {
		    check(theOnlyAccount match{
		      case Full(a) => User.hasOwnerPermission(a)
		      case _ => false
		    })
		  }),
          Menu.i("Anonymous") / "accounts" / "tesobe" / "anonymous" >> LocGroup("views") >> TestAccess(() => {
            check(theOnlyAccount match {
              case Full(a) => a.anonAccess.is
              case _ => false
            })
          }),
          Menu.i("Our Network") / "accounts" / "tesobe" / "our-network" >> LocGroup("views") >> TestAccess(() => {
            check(theOnlyAccount match{
		      case Full(a) => User.hasOurNetworkPermission(a)
		      case _ => false
		    })
          }),
          Menu.i("Team") / "accounts" / "tesobe" / "team" >> LocGroup("views") >> TestAccess(() => {
            check(theOnlyAccount match{
		      case Full(a) => User.hasTeamPermission(a)
		      case _ => false
		    })
          }),
          Menu.i("Board") / "accounts" / "tesobe" / "board" >> LocGroup("views") >> TestAccess(() => {
            check(theOnlyAccount match{
		      case Full(a) => User.hasBoardPermission(a)
		      case _ => false
		    })
          }),
          Menu.i("Authorities") / "accounts" / "tesobe" / "authorities" >> LocGroup("views") >> TestAccess(() => {
            check(theOnlyAccount match{
		      case Full(a) => User.hasAuthoritiesPermission(a)
		      case _ => false
		    })
          }),
          Menu.i("Comments") / "comments" >> TestAccess(() => {
            check(theOnlyAccount match{
		      case Full(a) => User.hasMoreThanAnonAccess(a)
		      case _ => false
		    })
          }) >> Hidden,
          Menu.i("About") / "about"
        )))

    LiftRules.statelessRewrite.append{
        case RewriteRequest(ParsePath("accounts" :: "tesobe" :: accessLevel :: "transactions" :: envelopeID :: "comments" :: Nil, "", true, _), _, therequest) =>
          					RewriteResponse("comments" :: Nil, Map("envelopeID" -> envelopeID, "accessLevel" -> accessLevel))
    }

    def sitemapMutators = User.sitemapMutator

    // set the sitemap.  Note if you don't want access control for
    // each page, just comment this line out.
    LiftRules.setSiteMapFunc(() => sitemapMutators(SiteMap(sitemap : _*)))
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
    
    TableSorter.init
    
    /**
     * A temporary measure to make sure there is an owner for the account, so that someone can set permissions
     */
    theOnlyAccount match{
      case Full(a) => {
        val theOnlyOwnerPriv = Privilege.find(By(Privilege.accountID, a.id.get.toString), By(Privilege.ownerPermission, true))
        theOnlyOwnerPriv match{
          case Empty => {
            //create one
            val randomPassword = StringHelpers.randomString(12)
            print ("The admin passowrd is :"+randomPassword )
            
            logger.debug("Creating tesobe account user and granting it owner permissions")
            val userEmail = "tesobe@tesobe.com"
            val theUserOwner = User.find(By(User.email, userEmail)).getOrElse(User.create.email(userEmail).password(randomPassword).validated(true).saveMe)
        	val newPriv = Privilege.create.accountID(a.id.get.toString).ownerPermission(true).user(theUserOwner)
        	newPriv.saveMe
          }
          case _ => logger.debug("Owner privilege already exists")
        }
      }
      case _ => logger.debug("No account found")
    }
    
  }
}
