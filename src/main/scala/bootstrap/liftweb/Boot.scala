/**
Open Bank Project - API
Copyright (C) 2011, 2013, TESOBE / Music Pictures Ltd

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
import net.liftweb.util.Helpers._
import net.liftweb.util.Schedule
import net.liftweb.mongodb.BsonDSL._
import net.liftweb.util.Helpers
import javax.mail.PasswordAuthentication
import java.io.FileInputStream
import java.io.File
import javax.mail.internet.MimeMessage

import code.model.{Nonce, Consumer, Token, dataAccess}
import dataAccess._
import code.api._
import code.snippet.{OAuthAuthorisation, OAuthWorkedThanks}
import code.util.MyExceptionLogger
import net.liftweb.mongodb.Skip
import code.metadata.wheretags.OBPWhereTag

/**
 * A class that's instantiated early and run.  It allows the application
 * to modify lift's environment
 */
class Boot extends Loggable{
  def boot {


    val contextPath = LiftRules.context.path
    val propsPath = tryo{Box.legacyNullTest(System.getProperty("props.resource.dir"))}.toIterable.flatten

    logger.info("external props folder: " + propsPath)

    /**
     * Where this application looks for props files:
     *
     * All properties files follow the standard lift naming scheme for order of preference (see https://www.assembla.com/wiki/show/liftweb/Properties)
     * within a directory.
     *
     * The first choice of directory is $props.resource.dir/CONTEXT_PATH where $props.resource.dir is the java option set via -Dprops.resource.dir=...
     * The second choice of directory is $props.resource.dir
     *
     * For example, on a production system:
     *
     * api1.example.com with context path /api1
     *
     * Looks first in (outside of war file): $props.resource.dir/api1, following the normal lift naming rules (e.g. production.default.props)
     * Looks second in (outside of war file): $props.resource.dir, following the normal lift naming rules (e.g. production.default.props)
     * Looks third in the war file
     *
     * and
     *
     * api2.example.com with context path /api2
     *
     * Looks first in (outside of war file): $props.resource.dir/api2 , following the normal lift naming rules (e.g. production.default.props)
     * Looks second in (outside of war file): $props.resource.dir, following the normal lift naming rules (e.g. production.default.props)
     * Looks third in the war file, following the normal lift naming rules
     *
     */

    val firstChoicePropsDir = for {
      propsPath <- propsPath
    } yield {
      Props.toTry.map {
        f => {
          val name = propsPath + contextPath + f() + "props"
          name -> { () => tryo{new FileInputStream(new File(name))} }
        }
      }
    }

    val secondChoicePropsDir = for {
      propsPath <- propsPath
    } yield {
      Props.toTry.map {
        f => {
          val name = propsPath +  f() + "props"
          name -> { () => tryo{new FileInputStream(new File(name))} }
        }
      }
    }

    Props.whereToLook = () => {
      firstChoicePropsDir.flatten.toList ::: secondChoicePropsDir.flatten.toList
    }

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
    Schemifier.schemify(true, Schemifier.infoF _, OBPUser, Admin)

    // where to search snippet
    LiftRules.addToPackages("code")

    // For some restful stuff
    LiftRules.statelessDispatchTable.append(v1_0.OBPAPI1_0)
    LiftRules.statelessDispatchTable.append(v1_1.OBPAPI1_1)
    LiftRules.statelessDispatchTable.append(v1_2.OBPAPI1_2)
    LiftRules.statelessDispatchTable.append(v1_2_1.OBPAPI1_2_1)
    LiftRules.statelessDispatchTable.append(BankMockAPI)
    // LiftRules.statelessDispatchTable.append(Metrics) TODO: see metric menu entry bellow

    //OAuth API call
    LiftRules.statelessDispatchTable.append(OAuthHandshake)

    //OAuth Mapper
    Schemifier.schemify(true, Schemifier.infoF _, Nonce)
    Schemifier.schemify(true, Schemifier.infoF _, Token)
    Schemifier.schemify(true, Schemifier.infoF _, Consumer)
    Schemifier.schemify(true, Schemifier.infoF _, HostedAccount)
    Schemifier.schemify(true, Schemifier.infoF _, ViewPrivileges)
    Schemifier.schemify(true, Schemifier.infoF _, ViewImpl)
    Schemifier.schemify(true, Schemifier.infoF _, APIUser)
    Schemifier.schemify(true, Schemifier.infoF _, MappedAccountHolder)

    //launch the scheduler to clean the database from the expired tokens and nonces
    Schedule.schedule(()=> OAuthAuthorisation.dataBaseCleaner, 2 minutes)

    val accountCreation = {
      if(Props.getBool("allow_sandbox_account_creation", false)){
        //user must be logged in, as a created account needs an owner
        List(Menu("Sandbox Account Creation", "Create Sandbox Test Account") / "create-sandbox-account" >> OBPUser.loginFirst)
      } else {
        Nil
      }
    }

    // Build SiteMap
    val sitemap = List(
          Menu.i("Home") / "index",
          Menu.i("Consumer Admin") / "admin" / "consumers" >> Admin.loginFirst >> LocGroup("admin")
          	submenus(Consumer.menus : _*),
          Menu("Consumer Registration", "Developers") / "consumer-registration",
          // Menu.i("Metrics") / "metrics", //TODO: allow this page once we can make the account number anonymous in the URL
          Menu.i("OAuth") / "oauth" / "authorize", //OAuth authorization page
          OAuthWorkedThanks.menu //OAuth thanks page that will do the redirect
    ) ++ accountCreation ++ Admin.menus

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

    val useMessageQueue = Props.getBool("messageQueue.createBankAccounts", false)
    if(useMessageQueue)
      BankAccountCreationListener.startListen

    Mailer.devModeSend.default.set( (m : MimeMessage) => {
      logger.info("Would have sent email if not in dev mode: " + m.getContent)
    })

    LiftRules.exceptionHandler.prepend{
      case MyExceptionLogger(_, _, t) => throw t // this will never happen
    }
  }
}
