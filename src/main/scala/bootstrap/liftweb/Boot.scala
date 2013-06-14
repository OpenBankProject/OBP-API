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
package bootstrap.liftweb


import net.liftweb._
import util._
import common._
import http._
import sitemap._
import Loc._
import mapper._
import code.model.dataAccess._
import code.model.{Nonce, Consumer, Token, Bank, ModeratedTransaction, View, BankAccount}
import code.api._
import net.liftweb.util.Helpers._
import net.liftweb.widgets.tablesorter.TableSorter
import net.liftweb.json.JsonDSL._
import code.api.OAuthHandshake
import net.liftweb.util.Schedule
import net.liftweb.mongodb.BsonDSL._
import net.liftweb.http.js.jquery.JqJsCmds
import code.snippet.OAuthAuthorisation
import net.liftweb.util.Helpers
import javax.mail.{ Authenticator, PasswordAuthentication }
/**
 * A class that's instantiated early and run.  It allows the application
 * to modify lift's environment
 */
class Boot extends Loggable{
  def boot {

    // This sets up MongoDB config
    MongoConfig.init

    if (!DB.jndiJdbcConnAvailable_?) {
      val driver =
        Props.mode match {
          case Props.RunModes.Production | Props.RunModes.Staging | Props.RunModes.Development =>  Props.get("db.driver") openOr "org.h2.Driver"
          case _ => "org.h2.Driver"
        }
      val vendor =
        Props.mode match {
          case Props.RunModes.Production | Props.RunModes.Staging | Props.RunModes.Development =>
            new StandardDBVendor(driver,
               Props.get("db.url") openOr "jdbc:h2:lift_proto.db;AUTO_SERVER=TRUE",
               Props.get("db.user"), Props.get("db.password"))
          case _ =>
            new StandardDBVendor(
              driver,
              "jdbc:h2:mem:OBPTest;DB_CLOSE_DELAY=-1",
              Empty, Empty)
        }

      logger.debug("Using database driver: " + driver)
      LiftRules.unloadHooks.append(vendor.closeAllConnections_! _)

      DB.defineConnectionManager(DefaultConnectionIdentifier, vendor)
    }
    Mailer.authenticator = for {
      user <- Props.get("mail.username")
      pass <- Props.get("mail.password")
    } yield new Authenticator {
      override def getPasswordAuthentication =
        new PasswordAuthentication(user,pass)
    }

    val runningMode = Props.mode match {
      case Props.RunModes.Production => "Production mode"
      case Props.RunModes.Staging => "Staging mode"
      case Props.RunModes.Development => "Development mode"
      case Props.RunModes.Test => "test mode"
      case _ => "other mode"
    }

    logger.info("running mode: " + runningMode)

    // Use Lift's Mapper ORM to populate the database
    // you don't need to use Mapper to use Lift... use
    // any ORM you want
    Schemifier.schemify(true, Schemifier.infoF _, OBPUser, Privilege, Admin)

    // where to search snippet
    LiftRules.addToPackages("code")

    // For some restful stuff
    LiftRules.statelessDispatchTable.append(v1_0.OBPAPI1_0)
    LiftRules.statelessDispatchTable.append(v1_1.OBPAPI1_1)
    LiftRules.statelessDispatchTable.append(v1_2.OBPAPI1_2)
    LiftRules.statelessDispatchTable.append(ImporterAPI)
    LiftRules.statelessDispatchTable.append(CashAccountAPI)
    LiftRules.statelessDispatchTable.append(BankMockAPI)

    //OAuth API call
    LiftRules.statelessDispatchTable.append(OAuthHandshake)

    //OAuth Mapper
    Schemifier.schemify(true, Schemifier.infoF _, Nonce)
    Schemifier.schemify(true, Schemifier.infoF _, Token)
    Schemifier.schemify(true, Schemifier.infoF _, Consumer)
    Schemifier.schemify(true, Schemifier.infoF _, HostedAccount)
    //launch the scheduler to clean the database from the expired tokens and nonces
    Schedule.schedule(()=> OAuthAuthorisation.dataBaseCleaner, 2 minutes)

    def check(bool: Boolean) : Box[LiftResponse] = {
      if(bool){
        Empty
      }else{
        Full(PlainTextResponse("unauthorized"))
      }
    }

    def getTransactionsAndView (URLParameters : List[String]) : Box[(List[ModeratedTransaction], View, BankAccount)] =
    {
      val bank = URLParameters(0)
      val account = URLParameters(1)
      val viewName = URLParameters(2)

      val transactionsAndView : Box[(List[ModeratedTransaction], View, BankAccount)] = for {
        b <- BankAccount(bank, account) ?~ {"account " + account + " not found for bank " + bank}
        v <- View.fromUrl(viewName) ?~ {"view " + viewName + " not found for account " + account + " and bank " + bank}
        transactions <- b.getModeratedTransactions(OBPUser.currentUser, v)
      } yield (transactions, v, b)

      transactionsAndView match {
        case Failure(msg, _, _) => logger.warn("Could not get transactions and view: " + msg)
        case _ => //don't log anything
      }
      transactionsAndView
    }

    def getAccount(URLParameters : List[String]) =
    {
      val bankUrl = URLParameters(0)
      val accountUrl = URLParameters(1)
      val account = for {
        bank <- HostedBank.find("permalink",bankUrl)
        account <- bank.getAccount(accountUrl)
        user <- OBPUser.currentUser  ?~ {"user not found when attempting to access account " + account + " of bank " + bankUrl}
        if(user.hasMangementAccess(Account toBankAccount account))
      } yield account

      account match {
        case Failure(msg, _, _) => logger.info("Could not get account: " + msg)
        case _ => //don't log anything
      }
      account
    }
    def getTransaction(URLParameters : List[String]) =
    {
      if(URLParameters.length==4){
        val bank = URLParameters(0)
        val account = URLParameters(1)
        val transactionID = URLParameters(2)
        val viewName = URLParameters(3)
        val transaction = for{
          bankAccount <- BankAccount(bank, account) ?~ {"account " + account + " not found for bank " + bank}
          view <- View.fromUrl(viewName)
          transaction <- bankAccount.moderatedTransaction(transactionID, view, OBPUser.currentUser)
        } yield (transaction,view)

        transaction match {
          case Failure(msg, _, _) => logger.info("Could not get transaction: " + msg)
          case _ => //don't log anything
        }

        transaction
      }
      else
        Empty
    }
    // Build SiteMap
    val sitemap = List(
          Menu.i("Home") / "index",
          Menu.i("Privilege Admin") / "admin" / "privilege"  >> TestAccess(() => {
            check(OBPUser.loggedIn_?)
          }) >> LocGroup("admin")
          	submenus(Privilege.menus : _*),
          Menu.i("Consumer Admin") / "admin" / "consumers" >> LocGroup("admin")
          	submenus(Consumer.menus : _*),
          Menu("Consumer Registration", "Developers") / "consumer-registration",
          Menu.i("Metrics") / "metrics",
          Menu.i("OAuth") / "oauth" / "authorize", //OAuth authorization page
          Menu.i("Connect") / "connect",

          Menu.i("Banks") / "banks", //no test => list of open banks
          //list of open banks (banks with a least a bank account with an open account)
          Menu.param[Bank]("Bank", "bank", LocalStorage.getBank _ ,  bank => bank.id ) / "banks" / * ,
          //list of open accounts in a specific bank
          Menu.param[Bank]("Accounts", "accounts", LocalStorage.getBank _ ,  bank => bank.id ) / "banks" / * / "accounts",

          //test if the bank exists and if the user have access to management page
          Menu.params[Account]("Management", "management", getAccount _ , t => List("")) / "banks" / * / "accounts" / * / "management",

          Menu.params[(List[ModeratedTransaction], View, BankAccount)]("Bank Account", "bank accounts", getTransactionsAndView _ ,  t => List("") )
          / "banks" / * / "accounts" / * / *,

          Menu.params[(ModeratedTransaction,View)]("transaction", "transaction", getTransaction _ ,  t => List("") )
          / "banks" / * / "accounts" / * / "transactions" / * / *
    )

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

    LiftRules.explicitlyParsedSuffixes = Helpers.knownSuffixes &~ (Set("com"))

    // Make a transaction span the whole HTTP request
    S.addAround(DB.buildLoanWrapper)

    TableSorter.init
    /**
     * A temporary measure to make sure there is an owner for the account, so that someone can set permissions
     */
    Account.find(("holder", "MUSIC PICTURES LIMITED")) match{
      case Full(a) =>
        HostedAccount.find(By(HostedAccount.accountID,a.id.toString)) match {
          case Empty => {
            val hostedAccount = HostedAccount.create.accountID(a.id.toString).saveMe
            logger.debug("Creating tesobe account user and granting it owner permissions")
            //create one
            // val randomPassword = StringHelpers.randomString(12)
            // println ("The admin password is :"+randomPassword )
            val userEmail = "tesobe@tesobe.com"
            val firstName = "tesobe first name"
            val lastName = "tesobe last name"
            val theUserOwner = OBPUser.find(By(OBPUser.email, userEmail)).getOrElse(OBPUser.create.email(userEmail).password("123tesobe456").validated(true).firstName(firstName).lastName(lastName).saveMe)
            Privilege.create.account(hostedAccount).ownerPermission(true).user(theUserOwner).saveMe
          }
          case Full(hostedAccount) =>
            Privilege.find(By(Privilege.account,hostedAccount), By(Privilege.ownerPermission, true)) match{
              case Empty => {
                //create one
                // val randomPassword = StringHelpers.randomString(12)
                // println ("The admin password is :"+randomPassword )
                val userEmail = "tesobe@tesobe.com"
                val firstName = "tesobe first name"
                val lastName = "tesobe last name"
                val theUserOwner = OBPUser.find(By(OBPUser.email, userEmail)).getOrElse(OBPUser.create.email(userEmail).password("123tesobe456").validated(true).firstName(firstName).lastName(lastName).saveMe)
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
