/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd.

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
TESOBE Ltd.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */
package bootstrap.liftweb

import java.io.{File, FileInputStream}
import java.util.{Locale, TimeZone}

import code.accountapplication.MappedAccountApplication
import code.accountattribute.MappedAccountAttribute
import code.accountholders.MapperAccountHolders
import code.actorsystem.ObpActorSystem
import code.api.Constant._
import code.api.ResourceDocs1_4_0.ResourceDocs300.ResourceDocs310
import code.api.ResourceDocs1_4_0._
import code.api._
import code.api.builder.APIBuilder_Connector
import code.api.sandbox.SandboxApiCalls
import code.api.util.APIUtil.{enableVersionIfAllowed, errorJsonResponse}
import code.api.util._
import code.api.util.migration.Migration
import code.atms.MappedAtm
import code.bankconnectors.ConnectorEndpoints
import code.branches.MappedBranch
import code.cards.{MappedPhysicalCard, PinReset}
import code.consent.MappedConsent
import code.context.{MappedUserAuthContext, MappedUserAuthContextUpdate}
import code.crm.MappedCrmEvent
import code.customer.internalMapping.MappedCustomerIDMapping
import code.customer.{MappedCustomer, MappedCustomerMessage}
import code.customeraddress.MappedCustomerAddress
import code.entitlement.MappedEntitlement
import code.entitlementrequest.MappedEntitlementRequest
import code.fx.{MappedCurrency, MappedFXRate}
import code.kafka.{KafkaHelperActors, OBPKafkaConsumer}
import code.kycchecks.MappedKycCheck
import code.kycdocuments.MappedKycDocument
import code.kycmedias.MappedKycMedia
import code.kycstatuses.MappedKycStatus
import code.loginattempts.MappedBadLoginAttempt
import code.management.ImporterAPI
import code.meetings.{MappedMeeting, MappedMeetingInvitee}
import code.metadata.comments.MappedComment
import code.metadata.counterparties.{MappedCounterparty, MappedCounterpartyBespoke, MappedCounterpartyMetadata, MappedCounterpartyWhereTag}
import code.metadata.narrative.MappedNarrative
import code.metadata.tags.MappedTag
import code.metadata.transactionimages.MappedTransactionImage
import code.metadata.wheretags.MappedWhereTag
import code.metrics.{MappedConnectorMetric, MappedMetric}
import code.migration.MigrationScriptLog
import code.model._
import code.model.dataAccess._
import code.productAttributeattribute.MappedProductAttribute
import code.productcollection.MappedProductCollection
import code.productcollectionitem.MappedProductCollectionItem
import code.products.MappedProduct
import code.remotedata.RemotedataActors
import code.scheduler.DatabaseDriverScheduler
import code.scope.{MappedScope, MappedUserScope}
import code.snippet.{OAuthAuthorisation, OAuthWorkedThanks}
import code.socialmedia.MappedSocialMedia
import code.taxresidence.MappedTaxResidence
import code.transaction.MappedTransaction
import code.transactionChallenge.MappedExpectedChallengeAnswer
import code.transactionStatusScheduler.TransactionStatusScheduler
import code.transaction_types.MappedTransactionType
import code.transactionrequests.{MappedTransactionRequest, MappedTransactionRequestTypeCharge}
import code.usercustomerlinks.MappedUserCustomerLink
import code.util.Helper.MdcLoggable
import code.views.system.{AccountAccess, ViewDefinition}
import code.webhook.{MappedAccountWebhook, WebhookHelperActors}
import javax.mail.internet.MimeMessage
import net.liftweb.common._
import net.liftweb.db.DBLogEntry
import net.liftweb.http._
import net.liftweb.json.Extraction
import net.liftweb.mapper._
import net.liftweb.sitemap.Loc._
import net.liftweb.sitemap._
import net.liftweb.util.Helpers._
import net.liftweb.util.{Helpers, Props, Schedule, _}


/**
 * A class that's instantiated early and run.  It allows the application
 * to modify lift's environment
 */
class Boot extends MdcLoggable {
  
  def boot {

    val contextPath = LiftRules.context.path
    val propsPath = tryo{Box.legacyNullTest(System.getProperty("props.resource.dir"))}.toIterable.flatten

    if (Props.mode == Props.RunModes.Development) logger.info("OBP-API Props all fields : \n" + Props.props.mkString("\n"))
    logger.info("external props folder: " + propsPath)
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    logger.info("Current Project TimeZone: " + TimeZone.getDefault)

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
     * Looks first in (outside of war file): $props.resource.dir/api2, following the normal lift naming rules (e.g. production.default.props)
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
          case Props.RunModes.Production | Props.RunModes.Staging | Props.RunModes.Development => APIUtil.getPropsValue("db.driver") openOr "org.h2.Driver"
          case _ => "org.h2.Driver"
        }
      val vendor =
        Props.mode match {
          case Props.RunModes.Production | Props.RunModes.Staging | Props.RunModes.Development =>
            new StandardDBVendor(driver,
              APIUtil.getPropsValue("db.url") openOr "jdbc:h2:lift_proto.db;AUTO_SERVER=TRUE",
              APIUtil.getPropsValue("db.user"), APIUtil.getPropsValue("db.password"))
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
    
    if (APIUtil.getPropsAsBoolValue("logging.database.queries.enable", false)) {
      DB.addLogFunc
     {
       case (log, duration) =>
       {
         logger.debug("Total query time : %d ms".format(duration))
         log.allEntries.foreach
         {
           case DBLogEntry(stmt, duration) =>
             logger.debug("The query :  %s in %d ms".format(stmt, duration))
         }
       }
     }
    }
    
    
    // ensure our relational database's tables are created/fit the schema
    val connector = APIUtil.getPropsValue("connector").openOrThrowException("no connector set")
    if(connector != "mongodb")
      schemifyAll()

    // This sets up MongoDB config (for the mongodb connector)
    if( connector == "mongodb")
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

    logger.debug(s"If you can read this, logging level is debug")

    val actorSystem = ObpActorSystem.startLocalActorSystem()
    connector match {
      case "akka_vDec2018" => 
        // Start Actor system of Akka connector
        ObpActorSystem.startNorthSideAkkaConnectorActorSystem()
      case _ => // Do nothing
    }

    // where to search snippets
    LiftRules.addToPackages("code")

    //OAuth API call
    LiftRules.statelessDispatch.append(OAuthHandshake)

    // JWT auth endpoints
    if(APIUtil.getPropsAsBoolValue("allow_direct_login", true)) {
      LiftRules.statelessDispatch.append(DirectLogin)
    }


    /**
      * Function that determines if foreign key constraints are
      * created by Schemifier for the specified connection.
      *
      * Note: The chosen driver must also support foreign keys for
      * creation to happen
      *
      * In case of PostgreSQL it works
      */
    MapperRules.createForeignKeys_? = (_) => APIUtil.getPropsAsBoolValue("mapper_rules.create_foreign_keys", false)



    //  OpenIdConnect endpoint and validator
    if(APIUtil.getPropsAsBoolValue("allow_openidconnect", false)) {
      LiftRules.dispatch.append(OpenIdConnect)
    }

    // Add the various API versions
    ScannedApis.versionMapScannedApis.keys.foreach(enableVersionIfAllowed) // process all scanned apis versions
    enableVersionIfAllowed(ApiVersion.v1_2_1)
    enableVersionIfAllowed(ApiVersion.v1_3_0)
    enableVersionIfAllowed(ApiVersion.v1_4_0)
    enableVersionIfAllowed(ApiVersion.v2_0_0)
    enableVersionIfAllowed(ApiVersion.v2_1_0)
    enableVersionIfAllowed(ApiVersion.v2_2_0)
    enableVersionIfAllowed(ApiVersion.v3_0_0)
    enableVersionIfAllowed(ApiVersion.v3_1_0)
    enableVersionIfAllowed(ApiVersion.apiBuilder)

    // TODO Wrap these with enableVersionIfAllowed as well
    //add management apis
    LiftRules.statelessDispatch.append(ImporterAPI)

    //LiftRules.statelessDispatch.append(AccountsAPI)


    //////////////////////////////////////////////////////////////////////////////////////////////////
    // Resource Docs are used in the process of surfacing endpoints so we enable them explicitly
    // to avoid a circular dependency.
    // Make the (currently identical) endpoints available to different versions.
    LiftRules.statelessDispatch.append(ResourceDocs140)
    LiftRules.statelessDispatch.append(ResourceDocs200)
    LiftRules.statelessDispatch.append(ResourceDocs210)
    LiftRules.statelessDispatch.append(ResourceDocs220)
    LiftRules.statelessDispatch.append(ResourceDocs300)
    LiftRules.statelessDispatch.append(ResourceDocs310)
    ////////////////////////////////////////////////////


    // LiftRules.statelessDispatch.append(Metrics) TODO: see metric menu entry below

    //add sandbox api calls only if we're running in sandbox mode
    if(APIUtil.getPropsAsBoolValue("allow_sandbox_data_import", false)) {
      LiftRules.statelessDispatch.append(SandboxApiCalls)
    } else {
      logger.info("Not adding sandbox api calls")
    }

    //launch the scheduler to clean the database from the expired tokens and nonces
    Schedule.schedule(()=> OAuthAuthorisation.dataBaseCleaner, 2 minutes)

    val accountCreation = {
      if(APIUtil.getPropsAsBoolValue("allow_sandbox_account_creation", false)){
        //user must be logged in, as a created account needs an owner
        // Not mentioning test and sandbox for App store purposes right now.
        List(Menu("Sandbox Account Creation", "Create Bank Account") / "create-sandbox-account" >> AuthUser.loginFirst)
      } else {
        Nil
      }
    }

    WebhookHelperActors.startLocalWebhookHelperWorkers(actorSystem)

    if (connector.startsWith("kafka")) {
      logger.info(s"KafkaHelperActors.startLocalKafkaHelperWorkers( ${actorSystem} ) starting")
      KafkaHelperActors.startLocalKafkaHelperWorkers(actorSystem)
      // Start North Side Consumer if it's not already started
      OBPKafkaConsumer.primaryConsumer.start()
    }

    if (APIUtil.getPropsAsBoolValue("use_akka", false) == true) {
      try {
        logger.info(s"RemotedataActors.startActors( ${actorSystem} ) starting")
        RemotedataActors.startActors(actorSystem)
      } catch {
        case ex: Exception => logger.warn(s"RemotedataActors.startLocalRemotedataWorkers( ${actorSystem} ) could not start: $ex")
      }
    }


    // API Metrics (logs of API calls)
    // If set to true we will write each URL with params to a datastore / log file
    if (APIUtil.getPropsAsBoolValue("write_metrics", false)) {
      logger.info("writeMetrics is true. We will write API metrics")
    } else {
      logger.info("writeMetrics is false. We will NOT write API metrics")
    }

    // API Metrics (logs of Connector calls)
    // If set to true we will write each URL with params to a datastore / log file
    if (APIUtil.getPropsAsBoolValue("write_connector_metrics", false)) {
      logger.info("writeConnectorMetrics is true. We will write connector metrics")
    } else {
      logger.info("writeConnectorMetrics is false. We will NOT write connector metrics")
    }


    logger.info (s"props_identifier is : ${APIUtil.getPropsValue("props_identifier", "NONE-SET")}")


    // Build SiteMap
    val sitemap = List(
          Menu.i("Home") / "index",
          Menu.i("Plain") / "plain",
          Menu.i("Consumer Admin") / "admin" / "consumers" >> Admin.loginFirst >> LocGroup("admin")
          	submenus(Consumer.menus : _*),
          Menu("Consumer Registration", "Get API Key") / "consumer-registration" >> AuthUser.loginFirst,
          // Menu.i("Metrics") / "metrics", //TODO: allow this page once we can make the account number anonymous in the URL
          Menu.i("OAuth") / "oauth" / "authorize", //OAuth authorization page
          OAuthWorkedThanks.menu //OAuth thanks page that will do the redirect
    ) ++ accountCreation ++ Admin.menus

    def sitemapMutators = AuthUser.sitemapMutator

    // set the sitemap.  Note if you don't want access control for
    // each page, just comment this line out.
    LiftRules.setSiteMapFunc(() => sitemapMutators(SiteMap(sitemap : _*)))
    // Use jQuery 1.4
    LiftRules.jsArtifacts = net.liftweb.http.js.jquery.JQueryArtifacts

    //Show the spinny image when an Ajax call starts
    LiftRules.ajaxStart =
      Full(() => LiftRules.jsArtifacts.show("ajax-loader").cmd)

    // Make the spinny image go away when it ends
    LiftRules.ajaxEnd =
      Full(() => LiftRules.jsArtifacts.hide("ajax-loader").cmd)

    // Force the request to be UTF-8
    LiftRules.early.append(_.setCharacterEncoding("UTF-8"))

    // What is the function to test if a user is logged in?
    LiftRules.loggedInTest = Full(() => AuthUser.loggedIn_?)

    // Template(/Response?) encoding
    LiftRules.early.append(_.setCharacterEncoding("utf-8"))

    // Use HTML5 for rendering
    LiftRules.htmlProperties.default.set((r: Req) =>
      new Html5Properties(r.userAgent))

    LiftRules.explicitlyParsedSuffixes = Helpers.knownSuffixes &~ (Set("com"))

    //set base localization to english (instead of computer default)
    Locale.setDefault(Locale.ENGLISH)
    logger.info("Current Project Locale is :" +Locale.getDefault)

    //override locale calculated from client request with default (until we have translations)
    LiftRules.localeCalculator = {
      case fullReq @ Full(req) => Locale.ENGLISH
      case _ => Locale.ENGLISH
    }

    //for XSS vulnerability, set X-Frame-Options header as DENY
    LiftRules.supplementalHeaders.default.set(List(("X-Frame-Options", "DENY")))
    
    // Make a transaction span the whole HTTP request
    S.addAround(DB.buildLoanWrapper)

    try {
      val useMessageQueue = APIUtil.getPropsAsBoolValue("messageQueue.createBankAccounts", false)
      if(useMessageQueue)
        BankAccountCreationListener.startListen
    } catch {
      case e: java.lang.ExceptionInInitializerError => logger.warn(s"BankAccountCreationListener Exception: $e")
    }

    Mailer.devModeSend.default.set( (m : MimeMessage) => {
      logger.info("Would have sent email if not in dev mode: " + m.getContent)
    })

    implicit val formats = CustomJsonFormats.formats
    LiftRules.exceptionHandler.prepend{
      case(Props.RunModes.Development, r, e) => {
        logger.error("Exception being returned to browser when processing " + r.uri.toString, e)
        JsonResponse(
          Extraction.decompose(ErrorMessage(code = 500, message = s"${ErrorMessages.InternalServerError} ${showExceptionAtJson(e)}")),
          500
        )
      }
      case (_, r , e) => {
        sendExceptionEmail(e)
        logger.error("Exception being returned to browser when processing " + r.uri.toString, e)
        JsonResponse(
          Extraction.decompose(ErrorMessage(code = 500, message = s"${ErrorMessages.InternalServerError}")),
          500
        )
      }
    }
    
    LiftRules.uriNotFound.prepend{
      case (r, _) => NotFoundAsResponse(errorJsonResponse(
        s"${ErrorMessages.InvalidUri}Current Url is (${r.uri.toString}), Current Content-Type Header is (${r.headers.find(_._1.equals("Content-Type")).map(_._2).getOrElse("")})", 
        404)
      )
    }

    if ( !APIUtil.getPropsAsLongValue("transaction_status_scheduler_delay").isEmpty ) {
      val delay = APIUtil.getPropsAsLongValue("transaction_status_scheduler_delay").openOrThrowException("Incorrect value for transaction_status_scheduler_delay, please provide number of seconds.")
      TransactionStatusScheduler.start(delay)
    }
    APIUtil.getPropsAsLongValue("database_messages_scheduler_interval") match {
      case Full(i) => DatabaseDriverScheduler.start(i)
      case _ => // Do not start it
    }
    

    APIUtil.akkaSanityCheck() match {
      case Full(c) if c == true => logger.info(s"remotedata.secret matched = $c")
      case Full(c) if c == false => throw new Exception(ErrorMessages.RemoteDataSecretMatchError)
      case Empty =>  APIUtil.getPropsAsBoolValue("use_akka", false) match {
        case true => throw new Exception(ErrorMessages.RemoteDataSecretObtainError)
        case false => logger.info("Akka middleware layer is disabled.")
      }
      case _ => throw new Exception(s"Unexpected error occurs during Akka sanity check!")
    }

    Migration.database.executeScripts()

    Glossary.glossaryItems

    // whether export LocalMappedConnector methods as endpoints, it is just for develop
    if (APIUtil.getPropsAsBoolValue("connector.export.LocalMappedConnector", false)){
      ConnectorEndpoints.registerConnectorEndpoints
    }

  }

  def schemifyAll() = {
    Schemifier.schemify(true, Schemifier.infoF _, ToSchemify.models: _*)
    if (APIUtil.getPropsAsBoolValue("remotedata.enable", false) == false) {
      Schemifier.schemify(true, Schemifier.infoF _, ToSchemify.modelsRemotedata: _*)
    }
  }

  private def showExceptionAtJson(error: Throwable): String = {
    val formattedError = "Message: " + error.toString  + error.getStackTrace.map(_.toString).mkString(" ")

    val formattedCause = error.getCause match {
      case null => ""
      case cause: Throwable => "Caught and thrown by: " + showExceptionAtJson(cause)
    }

    formattedError + formattedCause
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
    val host = APIUtil.getPropsValue("hostname", "unknown host")

    val mailSent = for {
      from <- APIUtil.getPropsValue("mail.exception.sender.address") ?~ "Could not send mail: Missing props param for 'from'"
      // no spaces, comma separated e.g. mail.api.consumer.registered.notification.addresses=notify@example.com,notify2@example.com,notify3@example.com
      toAddressesString <- APIUtil.getPropsValue("mail.exception.registered.notification.addresses") ?~ "Could not send mail: Missing props param for 'to'"
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
  // The following tables will be accessed via Akka to the OBP Storage instance which in turn uses Mapper / JDBC
  val modelsRemotedata = List(
    ViewImpl,
    ViewPrivileges,
    AccountAccess,
    ViewDefinition,
    ResourceUser,
    MappedComment,
    MappedTag,
    MappedWhereTag,
    MappedTransactionImage,
    MappedNarrative,
    MappedCustomer,
    MappedUserCustomerLink,
    Consumer,
    Token,
    Nonce,
    MappedCounterparty,
    MappedCounterpartyBespoke,
    MappedCounterpartyMetadata,
    MappedCounterpartyWhereTag,
    MappedTransactionRequest,
    MappedMetric,
    MapperAccountHolders,
    MappedEntitlement,
    MappedConnectorMetric,
    MappedExpectedChallengeAnswer,
    MappedEntitlementRequest,
    MappedScope,
    MappedUserScope,
    MappedTaxResidence,
    MappedCustomerAddress,
    MappedUserAuthContext,
    MappedUserAuthContextUpdate,
    MappedAccountApplication,
    MappedProductCollection,
    MappedProductCollectionItem,
    MappedAccountAttribute
  )

  // The following tables are accessed directly via Mapper / JDBC
  val models = List(
    AuthUser,
    Admin,
    MappedBank,
    MappedBankAccount,
    MappedTransaction,
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
    MappedMeetingInvitee,
    MappedBankAccountData,
    MappedPhysicalCard,
    PinReset,
    MappedBadLoginAttempt,
    MappedFXRate,
    MappedCurrency,
    MappedTransactionRequestTypeCharge,
    MappedAccountWebhook,
    MappedCustomerIDMapping,
    MappedProductAttribute,
    MappedConsent,
    MigrationScriptLog,
  )++ APIBuilder_Connector.allAPIBuilderModels
}
