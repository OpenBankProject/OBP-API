/**
Open Bank Project - API
Copyright (C) 2011-2016, TESOBE / Music Pictures Ltd

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

import java.io.{File, FileInputStream}
import java.util.Locale
import javax.mail.internet.MimeMessage

import code.api.ResourceDocs1_4_0.ResourceDocs
import code.api._
import code.api.sandbox.SandboxApiCalls
import code.atms.MappedAtm
import code.branches.MappedBranch
import code.crm.MappedCrmEvent
import code.customer.{MappedCustomer, MappedCustomerMessage}
import code.entitlement.MappedEntitlement
import code.kycdocuments.MappedKycDocument
import code.kycmedias.MappedKycMedia
import code.kycchecks.MappedKycCheck
import code.kycstatuses.MappedKycStatus
import code.meetings.MappedMeeting
import code.socialmedia.MappedSocialMedia
import code.management.{AccountsAPI, ImporterAPI}
import code.metadata.comments.MappedComment
import code.metadata.counterparties.{MappedCounterpartyMetadata, MappedCounterpartyWhereTag}
import code.metadata.narrative.MappedNarrative
import code.metadata.tags.MappedTag
import code.metadata.transactionimages.MappedTransactionImage
import code.metadata.wheretags.MappedWhereTag
import code.metrics.MappedMetric
import code.model._
import code.model.dataAccess._
import code.products.MappedProduct
import code.transaction_types.MappedTransactionType
import code.snippet.{OAuthAuthorisation, OAuthWorkedThanks}
import code.tesobe.CashAccountAPI
import code.transactionrequests.MappedTransactionRequest
import code.usercustomerlinks.MappedUserCustomerLink
import net.liftweb.common._
import net.liftweb.http._
import net.liftweb.mapper._
import net.liftweb.sitemap.Loc._
import net.liftweb.sitemap._
import net.liftweb.util.Helpers._
import net.liftweb.util.{Helpers, Schedule, _}
import code.api.Constant._


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

    // set up the way to connect to the relational DB we're using (ok if other connector than relational)
    if (!DB.jndiJdbcConnAvailable_?) {
      val driver =
        Props.mode match {
          case Props.RunModes.Production | Props.RunModes.Staging | Props.RunModes.Development => Props.get("db.driver") openOr "org.h2.Driver"
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

      DB.defineConnectionManager(net.liftweb.util.DefaultConnectionIdentifier, vendor)
    }

    // ensure our relational database's tables are created/fit the schema
    if(Props.get("connector").getOrElse("") == "mapped" ||
       Props.get("connector").getOrElse("") == "kafka" )
      schemifyAll()

    // This sets up MongoDB config (for the mongodb connector)
    if(Props.get("connector").getOrElse("") == "mongodb")
      MongoConfig.init

    val runningMode = Props.mode match {
      case Props.RunModes.Production => "Production mode"
      case Props.RunModes.Staging => "Staging mode"
      case Props.RunModes.Development => "Development mode"
      case Props.RunModes.Test => "test mode"
      case _ => "other mode"
    }

    logger.info("running mode: " + runningMode)
    logger.info(s"ApiPathZero (the bit before version) is $ApiPathZero")

    // where to search snippets
    LiftRules.addToPackages("code")

    //OAuth API call
    LiftRules.statelessDispatch.append(OAuthHandshake)

    // JWT auth endpoints
    if(Props.getBool("allow_direct_login", true)) {
      LiftRules.statelessDispatch.append(DirectLogin)
    }

    // Add the various API versions
    LiftRules.statelessDispatch.append(v1_0.OBPAPI1_0)
    LiftRules.statelessDispatch.append(v1_1.OBPAPI1_1)
    LiftRules.statelessDispatch.append(v1_2.OBPAPI1_2)
    // Can we depreciate the above?
    LiftRules.statelessDispatch.append(v1_2_1.OBPAPI1_2_1)
    LiftRules.statelessDispatch.append(v1_3_0.OBPAPI1_3_0)
    LiftRules.statelessDispatch.append(v1_4_0.OBPAPI1_4_0)
    LiftRules.statelessDispatch.append(v2_0_0.OBPAPI2_0_0)

    //add management apis
    LiftRules.statelessDispatch.append(ImporterAPI)
    LiftRules.statelessDispatch.append(AccountsAPI)

    // add other apis
    LiftRules.statelessDispatch.append(CashAccountAPI)
    LiftRules.statelessDispatch.append(BankMockAPI)



    // Add Resource Docs
    LiftRules.statelessDispatch.append(ResourceDocs)

    // LiftRules.statelessDispatch.append(Metrics) TODO: see metric menu entry below

    //add sandbox api calls only if we're running in sandbox mode
    if(Props.getBool("allow_sandbox_data_import", false)) {
      LiftRules.statelessDispatch.append(SandboxApiCalls)
    } else {
      logger.info("Not adding sandbox api calls")
    }

    //launch the scheduler to clean the database from the expired tokens and nonces
    Schedule.schedule(()=> OAuthAuthorisation.dataBaseCleaner, 2 minutes)

    val accountCreation = {
      if(Props.getBool("allow_sandbox_account_creation", false)){
        //user must be logged in, as a created account needs an owner
        // Not mentioning test and sandbox for App store purposes right now.
        List(Menu("Sandbox Account Creation", "Create Bank Account") / "create-sandbox-account" >> OBPUser.loginFirst)
      } else {
        Nil
      }
    }

    // API Metrics (logs of API calls)
    // If set to true we will write each URL with params to a datastore / log file
    if (Props.getBool("write_metrics", false)) {
      logger.info("writeMetrics is true. We will write API metrics")
    } else {
      logger.info("writeMetrics is false. We will NOT write API metrics")
    }


    // Build SiteMap
    val sitemap = List(
          Menu.i("Home") / "index",
          Menu.i("Consumer Admin") / "admin" / "consumers" >> Admin.loginFirst >> LocGroup("admin")
          	submenus(Consumer.menus : _*),
          Menu("Consumer Registration", "Get API Key") / "consumer-registration" >> OBPUser.loginFirst,
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

    // Template(/Response?) encoding
    LiftRules.early.append(_.setCharacterEncoding("utf-8"))

    // Use HTML5 for rendering
    LiftRules.htmlProperties.default.set((r: Req) =>
      new Html5Properties(r.userAgent))

    LiftRules.explicitlyParsedSuffixes = Helpers.knownSuffixes &~ (Set("com"))

    //set base localization to english (instead of computer default)
    Locale.setDefault(Locale.ENGLISH)

    //override locale calculated from client request with default (until we have translations)
    LiftRules.localeCalculator = {
      case fullReq @ Full(req) => Locale.ENGLISH
      case _ => Locale.ENGLISH
    }

    // Make a transaction span the whole HTTP request
    S.addAround(DB.buildLoanWrapper)

    try {
      val useMessageQueue = Props.getBool("messageQueue.createBankAccounts", false)
      if(useMessageQueue)
        BankAccountCreationListener.startListen
    } catch {
      case e: java.lang.ExceptionInInitializerError => logger.warn(s"BankAccountCreationListener Exception: $e")
    }

    Mailer.devModeSend.default.set( (m : MimeMessage) => {
      logger.info("Would have sent email if not in dev mode: " + m.getContent)
    })

    LiftRules.exceptionHandler.prepend{
      //same as default LiftRules.exceptionHandler
      case(Props.RunModes.Development, r, e) => {
        logger.error("Exception being returned to browser when processing " + r.uri.toString, e)
        XhtmlResponse((<html> <body>Exception occured while processing {r.uri}<pre>{showException(e)}</pre> </body> </html>), S.htmlProperties.docType, List("Content-Type" -> "text/html; charset=utf-8"), Nil, 500, S.legacyIeCompatibilityMode)

      }
      //same as default LiftRules.exceptionHandler, except that it also send an email notification
      case (_, r , e) => {
        sendExceptionEmail(e)
        logger.error("Exception being returned to browser when processing " + r.uri.toString, e)
        XhtmlResponse((<html> <body>Something unexpected happened while serving the page at {r.uri}</body> </html>), S.htmlProperties.docType, List("Content-Type" -> "text/html; charset=utf-8"), Nil, 500, S.legacyIeCompatibilityMode)
      }
    }
  }

  def schemifyAll() = {
    Schemifier.schemify(true, Schemifier.infoF _, ToSchemify.models: _*)
  }

  private def showException(le: Throwable): String = {
    val ret = "Message: " + le.toString + "\n\t" +
      le.getStackTrace.map(_.toString).mkString("\n\t") + "\n"

    val also = le.getCause match {
      case null => ""
      case sub: Throwable => "\nCaught and thrown by:\n" + showException(sub)
    }

    ret + also
  }

  private def sendExceptionEmail(exception: Throwable): Unit = {
    import Mailer.{From, PlainMailBodyType, Subject, To}
    import net.liftweb.util.Helpers.now

    val outputStream = new java.io.ByteArrayOutputStream
    val printStream = new java.io.PrintStream(outputStream)
    exception.printStackTrace(printStream)
    val currentTime = now.toString
    val stackTrace = new String(outputStream.toByteArray)
    val error = currentTime + ": " + stackTrace
    val host = Props.get("hostname", "unknown host")

    val mailSent = for {
      from <- Props.get("mail.exception.sender.address") ?~ "Could not send mail: Missing props param for 'from'"
      // no spaces, comma separated e.g. mail.api.consumer.registered.notification.addresses=notify@example.com,notify2@example.com,notify3@example.com
      toAddressesString <- Props.get("mail.exception.registered.notification.addresses") ?~ "Could not send mail: Missing props param for 'to'"
    } yield {

      //technically doesn't work for all valid email addresses so this will mess up if someone tries to send emails to "foo,bar"@example.com
      val to = toAddressesString.split(",").toList
      val toParams = to.map(To(_))
      val params = PlainMailBodyType(error) :: toParams

      //this is an async call
      Mailer.sendMail(
        From(from),
        Subject(s"you got an exception on $host"),
        params :_*
      )
    }

    //if Mailer.sendMail wasn't called (note: this actually isn't checking if the mail failed to send as that is being done asynchronously)
    if(mailSent.isEmpty)
      logger.warn(s"Exception notification failed: $mailSent")
  }
}

object ToSchemify {
  val models = List(OBPUser,
    Admin,
    Nonce,
    Token,
    Consumer,
    ViewPrivileges,
    ViewImpl,
    APIUser,
    MappedAccountHolder,
    MappedComment,
    MappedNarrative,
    MappedTag,
    MappedWhereTag,
    MappedCounterpartyMetadata,
    MappedCounterpartyWhereTag,
    MappedBank,
    MappedBankAccount,
    MappedTransaction,
    MappedTransactionRequest,
    MappedTransactionImage,
    MappedMetric,
    MappedCustomer,
    MappedCustomerMessage,
    MappedBranch,
    MappedAtm,
    MappedProduct,
    MappedCrmEvent,
    MappedKycDocument,
    MappedKycMedia,
    MappedKycCheck,
    MappedKycStatus,
    MappedSocialMedia,
    MappedTransactionType,
    MappedMeeting,
    MappedUserCustomerLink,
    MappedEntitlement)
}
