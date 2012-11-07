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

import net.liftweb._
import util._
import common._
import http._
import sitemap._
import Loc._
import mapper._
import code.model.dataAccess.{MongoConfig,OBPUser,Privilege,Account, MongoDBLocalStorage, HostedAccount}
import code.model.{Nonce, Consumer, Token}
import code.model.traits.{Bank, View, ModeratedTransaction}
import code.model.implementedTraits.{BankImpl, Anonymous, View}
import com.tesobe.utils._
import net.liftweb.util.Helpers._
import net.liftweb.widgets.tablesorter.TableSorter
import net.liftweb.json.JsonDSL._
import code.snippet.OAuthHandshake
import net.liftweb.util.Schedule
import net.liftweb.mongodb.BsonDSL._
import code.model.dataAccess.LocalStorage
import code.model.traits.BankAccount

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
    Schemifier.schemify(true, Schemifier.infoF _, OBPUser, Privilege)

    // where to search snippet
    LiftRules.addToPackages("code")

    // For some restful stuff
    LiftRules.statelessDispatchTable.append(OBPRest) // stateless -- no session created
    
    //OAuth API call
    LiftRules.dispatch.append(OAuthHandshake) 
    LiftRules.statelessDispatchTable.append(OAuthHandshake) 

    //OAuth Mapper 
    Schemifier.schemify(true, Schemifier.infoF _, Nonce)
    Schemifier.schemify(true, Schemifier.infoF _, Token)
    Schemifier.schemify(true, Schemifier.infoF _, Consumer) 
    Schemifier.schemify(true, Schemifier.infoF _, HostedAccount)
    //lunch the scheduler to clean the database from the expired tokens and nonces
    Schedule.schedule(()=> OAuthHandshake.dataBaseCleaner, 2 minutes)   


    val theOnlyAccount = Account.find(("holder", "Music Pictures Limited")) //TODO: Remove
    def check(bool: Boolean) : Box[LiftResponse] = {
      if(bool){
        Empty
      }else{
        Full(PlainTextResponse("unauthorized"))
      }
    }
     
    def getTransactionsAndView (URLParameters : List[String]) : Box[(List[ModeratedTransaction], View)] = 
    {
      val bank = URLParameters(0)
      val account = URLParameters(1)
      val viewName = URLParameters(2)
      val bankAccount = BankAccount(bank, account)
      val view = View.fromUrl(viewName)
      
      for {
        b <- bankAccount
        v <- view
        if(b.authorisedAccess(v, OBPUser.currentUser))
      } yield (b.getModeratedTransactions()(v.moderate), v)
      
    }
    def getAccount(URLParameters : List[String]) = 
    {
      val bankUrl = URLParameters(0)
      val accountUrl = URLParameters(1)
      LocalStorage.getAccount(bankUrl,accountUrl) match {
        case Full(account) => 
            OBPUser.currentUserId match {
              case Full(id) =>         
                OBPUser.find(By(OBPUser.id,id.toLong)) match {
                  case Full(user) =>  if(user.hasMangementAccess(bankUrl,accountUrl))
                                        Full(account)
                                      else
                                        Empty    
                  case _ => Empty 
                }
              case _ => Empty
            } 
        case _ => Empty 
      }
    }
    // Build SiteMap
    val sitemap = List(
          Menu.i("Home") / "index",
          Menu.i("Privilege Admin") / "admin" / "privilege"  >> TestAccess(() => {
            check(OBPUser.loggedIn_?)
          }) >> LocGroup("admin") 
          	submenus(Privilege.menus : _*),
          Menu.i("About") / "about",
          Menu.i("OAuth") / "oauth" / "authorize", //OAuth authorization page            
          
          Menu.i("Banks") / "banks", //no test => list of open banks
          //list of open banks (banks with a least a bank account with an open account)
          Menu.param[Bank]("Bank", "bank", LocalStorage.getBank _ ,  bank => bank.id ) / "banks" / * ,
          //list of open accounts in a specific bank
          Menu.param[Bank]("Accounts", "accounts", LocalStorage.getBank _ ,  bank => bank.id ) / "banks" / * / "accounts", 
          
          //test if the bank exists and if the user have access to this view => management page
          Menu.params[Account]("Management", "management", getAccount _ , t => List("")) / "banks" / * / "accounts" / * / "management",
          
          Menu.params[(List[ModeratedTransaction], View)]("Bank Account", "bank accounts", getTransactionsAndView _ ,  t => List("") ) 
          / "banks" / * / "accounts" / * / * , 


          //TODO : delete this URL
          Menu.i("Comments") / "comments" >> TestAccess(() => {
                    check(theOnlyAccount match{
            		      case Full(a) => OBPUser.hasMoreThanAnonAccess(Account.toBankAccount(a))
            		      case _ => false
            		    })
                  }) >> Hidden
    )
    LiftRules.statelessRewrite.append{
        case RewriteRequest(ParsePath("banks" :: bank :: "accounts" :: accountName :: accessLevel :: "transactions" :: envelopeID :: "comments" :: Nil, "", true, _), _, therequest) =>
          					RewriteResponse("comments" :: Nil, Map("envelopeID" -> envelopeID, "accessLevel" -> accessLevel))
    }

    def sitemapMutators = OBPUser.sitemapMutator

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
    LiftRules.loggedInTest = Full(() => OBPUser.loggedIn_?)

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
      case Full(a) => 
        HostedAccount.find(By(HostedAccount.accountID,a.id.toString)) match {
          case Empty => { 
            val hostedAccount = HostedAccount.create.accountID(a.id.toString).saveMe  
            logger.debug("Creating tesobe account user and granting it owner permissions")
            //create one
            // val randomPassword = StringHelpers.randomString(12)
            // println ("The admin password is :"+randomPassword )
            val userEmail = "tesobe@tesobe.com"
            val theUserOwner = OBPUser.find(By(OBPUser.email, userEmail)).getOrElse(OBPUser.create.email(userEmail).password("123tesobe456").validated(true).saveMe)
            Privilege.create.account(hostedAccount).ownerPermission(true).user(theUserOwner).saveMe              
          }  
          case Full(hostedAccount) => 
            Privilege.find(By(Privilege.account,hostedAccount), By(Privilege.ownerPermission, true)) match{
              case Empty => {
                //create one
                // val randomPassword = StringHelpers.randomString(12)
                // println ("The admin password is :"+randomPassword )
                val userEmail = "tesobe@tesobe.com"
                val theUserOwner = OBPUser.find(By(OBPUser.email, userEmail)).getOrElse(OBPUser.create.email(userEmail).password("123tesobe456").validated(true).saveMe)
                Privilege.create.account(hostedAccount).ownerPermission(true)
                  .mangementPermission(true).authoritiesPermission(true).boardPermission(true)
                  .teamPermission(true).ourNetworkPermission(true).user(theUserOwner).saveMe 
              }
              case _ => logger.debug("Owner privilege already exists")
            }
          case _ => None
        }  
      case _ => logger.debug("No account found")
    }
    
  }
}
