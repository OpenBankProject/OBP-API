/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

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
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */
package bootstrap.liftweb

import code.CustomerDependants.MappedCustomerDependant
import code.DynamicData.DynamicData
import code.DynamicEndpoint.DynamicEndpoint
import code.UserRefreshes.MappedUserRefreshes
import code.accountapplication.MappedAccountApplication
import code.accountattribute.MappedAccountAttribute
import code.accountholders.MapperAccountHolders
import code.actorsystem.ObpActorSystem
import code.api.Constant._
import code.api.ResourceDocs1_4_0.ResourceDocs300.{ResourceDocs310, ResourceDocs400, ResourceDocs500, ResourceDocs510, ResourceDocs600}
import code.api.ResourceDocs1_4_0._
import code.api._
import code.api.attributedefinition.AttributeDefinition
import code.api.berlin.group.ConstantsBG
import code.api.cache.Redis
import code.api.util.APIUtil.{enableVersionIfAllowed, errorJsonResponse, getPropsValue}
import code.api.util.ApiRole.CanCreateEntitlementAtAnyBank
import code.api.util.ErrorMessages.MandatoryPropertyIsNotSet
import code.api.util._
import code.api.util.migration.Migration
import code.api.util.migration.Migration.DbFunction
import code.apicollection.ApiCollection
import code.apicollectionendpoint.ApiCollectionEndpoint
import code.atmattribute.AtmAttribute
import code.atms.MappedAtm
import code.authtypevalidation.AuthenticationTypeValidation
import code.bankaccountbalance.BankAccountBalance
import code.bankattribute.BankAttribute
import code.bankconnectors.{Connector, ConnectorEndpoints}
import code.branches.MappedBranch
import code.cardattribute.MappedCardAttribute
import code.cards.{MappedPhysicalCard, PinReset}
import code.connectormethod.ConnectorMethod
import code.consent.{ConsentRequest, MappedConsent}
import code.consumer.Consumers
import code.context.{MappedConsentAuthContext, MappedUserAuthContext, MappedUserAuthContextUpdate}
import code.counterpartylimit.CounterpartyLimit
import code.crm.MappedCrmEvent
import code.customer.internalMapping.MappedCustomerIdMapping
import code.customer.{MappedCustomer, MappedCustomerMessage}
import code.customeraccountlinks.CustomerAccountLink
import code.customeraddress.MappedCustomerAddress
import code.customerattribute.MappedCustomerAttribute
import code.directdebit.DirectDebit
import code.dynamicEntity.DynamicEntity
import code.dynamicMessageDoc.DynamicMessageDoc
import code.dynamicResourceDoc.DynamicResourceDoc
import code.endpointMapping.EndpointMapping
import code.endpointTag.EndpointTag
import code.entitlement.{Entitlement, MappedEntitlement}
import code.entitlementrequest.MappedEntitlementRequest
import code.etag.MappedETag
import code.fx.{MappedCurrency, MappedFXRate}
import code.kycchecks.MappedKycCheck
import code.kycdocuments.MappedKycDocument
import code.kycmedias.MappedKycMedia
import code.kycstatuses.MappedKycStatus
import code.loginattempts.{LoginAttempt, MappedBadLoginAttempt}
import code.management.ImporterAPI
import code.meetings.{MappedMeeting, MappedMeetingInvitee}
import code.metadata.comments.MappedComment
import code.metadata.counterparties.{MappedCounterparty, MappedCounterpartyBespoke, MappedCounterpartyMetadata, MappedCounterpartyWhereTag}
import code.metadata.narrative.MappedNarrative
import code.metadata.tags.MappedTag
import code.metadata.transactionimages.MappedTransactionImage
import code.metadata.wheretags.MappedWhereTag
import code.methodrouting.MethodRouting
import code.metrics.{MappedConnectorMetric, MappedMetric, MetricArchive}
import code.migration.MigrationScriptLog
import code.model._
import code.model.dataAccess._
import code.model.dataAccess.internalMapping.AccountIdMapping
import code.obp.grpc.HelloWorldServer
import code.productAttributeattribute.MappedProductAttribute
import code.productcollection.MappedProductCollection
import code.productcollectionitem.MappedProductCollectionItem
import code.productfee.ProductFee
import code.products.MappedProduct
import code.ratelimiting.RateLimiting
import code.regulatedentities.MappedRegulatedEntity
import code.regulatedentities.attribute.RegulatedEntityAttribute
import code.scheduler._
import code.scope.{MappedScope, MappedUserScope}
import code.signingbaskets.{MappedSigningBasket, MappedSigningBasketConsent, MappedSigningBasketPayment}
import code.snippet.OAuthWorkedThanks
import code.socialmedia.MappedSocialMedia
import code.standingorders.StandingOrder
import code.taxresidence.MappedTaxResidence
import code.token.OpenIDConnectToken
import code.transaction.MappedTransaction
import code.transaction.internalMapping.TransactionIdMapping
import code.transactionChallenge.MappedExpectedChallengeAnswer
import code.transactionRequestAttribute.TransactionRequestAttribute
import code.transactionStatusScheduler.TransactionRequestStatusScheduler
import code.transaction_types.MappedTransactionType
import code.transactionattribute.MappedTransactionAttribute
import code.transactionrequests.{MappedTransactionRequest, MappedTransactionRequestTypeCharge, TransactionRequestReasons}
import code.usercustomerlinks.MappedUserCustomerLink
import code.userlocks.UserLocks
import code.users._
import code.util.Helper.{MdcLoggable, ObpS, SILENCE_IS_GOLDEN}
import code.util.{Helper, HydraUtil}
import code.validation.JsonSchemaValidation
import code.views.Views
import code.views.system.{AccountAccess, ViewDefinition, ViewPermission}
import code.webhook.{BankAccountNotificationWebhook, MappedAccountWebhook, SystemAccountNotificationWebhook}
import code.webuiprops.WebUiProps
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.Functions.Implicits._
import com.openbankproject.commons.util.{ApiVersion, Functions}
import net.liftweb.common._
import net.liftweb.db.{DB, DBLogEntry}
import net.liftweb.http.LiftRules.DispatchPF
import net.liftweb.http._
import net.liftweb.json.Extraction
import net.liftweb.mapper.{DefaultConnectionIdentifier => _, _}
import net.liftweb.sitemap.Loc._
import net.liftweb.sitemap._
import net.liftweb.util.Helpers._
import net.liftweb.util._
import org.apache.commons.io.FileUtils

import java.io.{File, FileInputStream}
import java.util.stream.Collectors
import java.util.{Locale, TimeZone}
import scala.concurrent.ExecutionContext

// So we can print the version used.
import org.eclipse.jetty.util.Jetty



/**
 * A class that's instantiated early and run.  It allows the application
 * to modify lift's environment
 */
class Boot extends MdcLoggable {

  /**
   * For the project scope, most early initiate logic should in this method.
   */
  override protected def initiate(): Unit = {
    val resourceDir = System.getProperty("props.resource.dir") ?: System.getenv("props.resource.dir")
    val propsPath = tryo{Box.legacyNullTest(resourceDir)}.toList.flatten

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
          val contextPath = LiftRules.context.path
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
      (firstChoicePropsDir ::: secondChoicePropsDir).flatten
    }

    if (Props.mode == Props.RunModes.Development) logger.info("OBP-API Props all fields : \n" + Props.props.mkString("\n"))
    logger.info("external props folder: " + propsPath)
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    logger.info("Current Project TimeZone: " + TimeZone.getDefault)


    // set dynamic_code_sandbox_enable to System.properties, so com.openbankproject.commons.ExecutionContext can read this value
    APIUtil.getPropsValue("dynamic_code_sandbox_enable")
      .foreach(it => System.setProperty("dynamic_code_sandbox_enable", it))
  }



  def boot {
    implicit val formats = CustomJsonFormats.formats

    logger.info("Boot says: Hello from the Open Bank Project API. This is Boot.scala. The gitCommit is : " + APIUtil.gitCommit)

    logger.info(s"Boot says: Jetty Version: ${Jetty.VERSION}")

    logger.debug("Boot says:Using database driver: " + APIUtil.driver)

    DB.defineConnectionManager(net.liftweb.util.DefaultConnectionIdentifier, APIUtil.vendor)

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

    schemifyAll()

    logger.info("Mapper database info: " + Migration.DbFunction.mapperDatabaseInfo)

    DbFunction.tableExists(ResourceUser) match {
      case true => // DB already exist
        // Migration Scripts are used to update the model of OBP-API DB to a latest version.
        // Please note that migration scripts are executed before Lift Mapper Schemifier
        Migration.database.executeScripts(startedBeforeSchemifier = true)
        logger.info("The Mapper database already exits. The scripts are executed BEFORE Lift Mapper Schemifier.")
      case false => // DB is still not created. The scripts will be executed after Lift Mapper Schemifier
        logger.info("The Mapper database is still not created. The scripts are going to be executed AFTER Lift Mapper Schemifier.")
    }

    // Migration Scripts are used to update the model of OBP-API DB to a latest version.

    // Please note that migration scripts are executed after Lift Mapper Schemifier
    Migration.database.executeScripts(startedBeforeSchemifier = false)

    if (APIUtil.getPropsAsBoolValue("create_system_views_at_boot", true)) {
      // Create system views
      val owner = Views.views.vend.getOrCreateSystemView(SYSTEM_OWNER_VIEW_ID).isDefined
      val auditor = Views.views.vend.getOrCreateSystemView(SYSTEM_AUDITOR_VIEW_ID).isDefined
      val accountant = Views.views.vend.getOrCreateSystemView(SYSTEM_ACCOUNTANT_VIEW_ID).isDefined
      val standard = Views.views.vend.getOrCreateSystemView(SYSTEM_STANDARD_VIEW_ID).isDefined
      val stageOne = Views.views.vend.getOrCreateSystemView(SYSTEM_STAGE_ONE_VIEW_ID).isDefined
      val manageCustomViews = Views.views.vend.getOrCreateSystemView(SYSTEM_MANAGE_CUSTOM_VIEWS_VIEW_ID).isDefined
      // Only create Firehose view if they are enabled at instance.
      val accountFirehose = if (ApiPropsWithAlias.allowAccountFirehose)
        Views.views.vend.getOrCreateSystemView(SYSTEM_FIREHOSE_VIEW_ID).isDefined
      else Empty.isDefined

      APIUtil.getPropsValue("additional_system_views") match {
        case Full(value) =>
          val additionalSystemViewsFromProps = value.split(",").map(_.trim).toList
          val additionalSystemViews = List(
            SYSTEM_READ_ACCOUNTS_BASIC_VIEW_ID,
            SYSTEM_READ_ACCOUNTS_DETAIL_VIEW_ID,
            SYSTEM_READ_BALANCES_VIEW_ID,
            SYSTEM_READ_TRANSACTIONS_BASIC_VIEW_ID,
            SYSTEM_READ_TRANSACTIONS_DEBITS_VIEW_ID,
            SYSTEM_READ_TRANSACTIONS_DETAIL_VIEW_ID,
            SYSTEM_READ_ACCOUNTS_BERLIN_GROUP_VIEW_ID,
            SYSTEM_READ_BALANCES_BERLIN_GROUP_VIEW_ID,
            SYSTEM_READ_TRANSACTIONS_BERLIN_GROUP_VIEW_ID,
            SYSTEM_INITIATE_PAYMENTS_BERLIN_GROUP_VIEW_ID
          )
          for {
            systemView <- additionalSystemViewsFromProps
            if additionalSystemViews.exists(_ == systemView)
          } {
            Views.views.vend.getOrCreateSystemView(systemView)
          }
        case _ => // Do nothing
      }

    }

    ApiWarnings.logWarningsRegardingProperties()
    ApiWarnings.customViewNamesCheck()
    ApiWarnings.systemViewNamesCheck()

    //see the notes for this method:
    createDefaultBankAndDefaultAccountsIfNotExisting()

    createBootstrapSuperUser()

    //launch the scheduler to clean the database from the expired tokens and nonces, 1 hour
    DataBaseCleanerScheduler.start(intervalInSeconds = 60*60)

//    if (Props.devMode || Props.testMode) {
//      StoredProceduresMockedData.createOrDropMockedPostgresStoredProcedures()
//    }

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

    // start RabbitMq Adapter(using mapped connector as mockded CBS)
    if (APIUtil.getPropsAsBoolValue("rabbitmq.adapter.enabled", false)) {
      code.bankconnectors.rabbitmq.Adapter.startRabbitMqAdapter.main(Array(""))
    }


    // Database query timeout
//    APIUtil.getPropsValue("database_query_timeout_in_seconds").map { timeoutInSeconds =>
//      tryo(timeoutInSeconds.toInt).isDefined match {
//        case true =>
//          DB.queryTimeout = Full(timeoutInSeconds.toInt)
//          logger.info(s"Query timeout database_query_timeout_in_seconds is set to ${timeoutInSeconds} seconds")
//        case false =>
//          logger.error(
//            s"""
//               |------------------------------------------------------------------------------------
//               |Query timeout database_query_timeout_in_seconds [${timeoutInSeconds}] is not an integer value.
//               |Actual DB.queryTimeout value: ${DB.queryTimeout}
//               |------------------------------------------------------------------------------------""".stripMargin)
//      }
//    }

    LiftRules.unloadHooks.append(APIUtil.vendor.closeAllConnections_! _)
    LiftRules.unloadHooks.append(Redis.jedisPoolDestroy _)
//    LiftRules.statelessDispatch.prepend {
//      case _ if tryo(DB.use(DefaultConnectionIdentifier){ conn => conn}.isClosed).isEmpty =>
//        Props.mode match {
//          case Props.RunModes.Development =>
//            () =>
//              Full(
//                JsonResponse(
//                  Extraction.decompose(ErrorMessage(code = 500, message = s"${ErrorMessages.DatabaseConnectionClosedError}")),
//                  500
//                )
//              )
//        }
//    }

    //If use_custom_webapp=true, this will copy all the files from `OBP-API/obp-api/src/main/webapp` to `OBP-API/obp-api/src/main/resources/custom_webapp`
    if (APIUtil.getPropsAsBoolValue("use_custom_webapp", false)){
      //this `LiftRules.getResource` will get the path of `OBP-API/obp-api/src/main/webapp`:
      LiftRules.getResource("/").map { url =>
        // this following will get the path of `OBP-API/obp-api/src/main/resources/custom_webapp`
        val source = if (getClass().getClassLoader().getResource("custom_webapp") == null)
          throw new RuntimeException("If you set `use_custom_webapp = true`, custom_webapp folder can not be Empty!!")
        else
          getClass().getClassLoader().getResource("custom_webapp").getPath
        val srcDir = new File(source);

        // The destination directory to copy to. This directory
        // doesn't exists and will be created during the copy
        // directory process.
        val destDir = new File(url.getPath)

        // Copy source directory into destination directory
        // including its child directories and files. When
        // the destination directory is not exists it will
        // be created. This copy process also preserve the
        // date information of the file.
        FileUtils.copyDirectory(srcDir, destDir)
      }
    }

    // ensure our relational database's tables are created/fit the schema
    val connector = code.api.Constant.CONNECTOR.openOrThrowException(s"$MandatoryPropertyIsNotSet. The missing prop is `connector` ")

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
      case "star" if (APIUtil.getPropsValue("starConnector_supported_types","").split(",").contains("akka"))  =>
        ObpActorSystem.startNorthSideAkkaConnectorActorSystem()
      case _ => // Do nothing
    }

    // where to search snippets
    LiftRules.addToPackages("code")


    // H2 web console
    // Help accessing H2 from outside Lift, and be able to run any queries against it.
    // It's enabled only in Dev and Test mode
    if (Props.devMode || Props.testMode) {
      LiftRules.liftRequest.append({case r if (r.path.partPath match {
        case "console" :: _ => true
        case _ => false}
        ) => false})
    }





    // here must modify apiPathZero: initiate the value, because obp-commons can't get apiPathZero value.
    // obp-api depends obp-commons, but obp-commons should not depends obp-api
    ApiVersion.setUrlPrefix(ApiPathZero)

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
    enableVersionIfAllowed(ApiVersion.v4_0_0)
    enableVersionIfAllowed(ApiVersion.v5_0_0)
    enableVersionIfAllowed(ApiVersion.v5_1_0)
    enableVersionIfAllowed(ApiVersion.v6_0_0)
    enableVersionIfAllowed(ApiVersion.`dynamic-endpoint`)
    enableVersionIfAllowed(ApiVersion.`dynamic-entity`)

    def enableOpenIdConnectApis = {
      //  OpenIdConnect endpoint and validator
      if (code.api.Constant.openidConnectEnabled) {
        LiftRules.dispatch.append(OpenIdConnect)
      }
    }
    def enableAPIs: LiftRules#RulesSeq[DispatchPF] = {

      //OAuth API call
      LiftRules.statelessDispatch.append(OAuthHandshake)

      // JWT auth endpoints
      if (APIUtil.getPropsAsBoolValue("allow_direct_login", true)) {
        LiftRules.statelessDispatch.append(DirectLogin)
      }

      // TODO Wrap these with enableVersionIfAllowed as well
      //add management apis
      LiftRules.statelessDispatch.append(ImporterAPI)
    }

    code.api.Constant.serverMode match {
      // Instance runs as the portal only
      case mode if mode == "portal" => // Callback url in case of OpenID Connect MUST be enabled at portal side
        enableOpenIdConnectApis
      // Instance runs as the APIs only
      case mode if mode == "apis" =>
        enableAPIs
      // Instance runs as the portal and APIs as well
      // This is default mode
      case mode if mode.contains("apis") && mode.contains("portal") =>
        enableAPIs
        enableOpenIdConnectApis
      // Failure
      case _ =>
        throw new RuntimeException("The props server_mode`is not properly set. Allowed cases: { server_mode=portal, server_mode=apis, server_mode=apis,portal }")
    }


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
    LiftRules.statelessDispatch.append(ResourceDocs400)
    LiftRules.statelessDispatch.append(ResourceDocs500)
    LiftRules.statelessDispatch.append(ResourceDocs510)
    LiftRules.statelessDispatch.append(ResourceDocs600)
    ////////////////////////////////////////////////////


    // LiftRules.statelessDispatch.append(Metrics) TODO: see metric menu entry below
    val accountCreation = {
      if(APIUtil.getPropsAsBoolValue("allow_sandbox_account_creation", false)){
        //user must be logged in, as a created account needs an owner
        // Not mentioning test and sandbox for App store purposes right now.
        List(Menu("Sandbox Account Creation", "Create Bank Account") / "create-sandbox-account" >> AuthUser.loginFirst)
      } else {
        Nil
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

    // This will work for both portal and API modes. This page is used for testing if the API is running properly.
    val awakePage = List( Menu.i("awake") /"debug" / "awake")

    val commonMap = List(Menu.i("Home") / "index") ::: List(
      Menu.i("index-en") / "index-en",
      Menu.i("Plain") / "plain",
      Menu.i("Static") / "static",
      Menu.i("SDKs") / "sdks",
      Menu.i("Consents") / "consents",
      Menu.i("Debug") / "debug",
      Menu.i("debug-basic") / "debug" / "debug-basic",
      Menu.i("debug-default-header") / "debug" / "debug-default-header",
      Menu.i("debug-default-footer") / "debug" / "debug-default-footer",
      Menu.i("debug-localization") / "debug" / "debug-localization",
      Menu.i("debug-plain") / "debug" / "debug-plain",
      Menu.i("debug-webui") / "debug" / "debug-webui",
      Menu.i("Consumer Admin") / "admin" / "consumers" >> Admin.loginFirst >> LocGroup("admin")
        submenus(Consumer.menus : _*),
      Menu("Consumer Registration", Helper.i18n("consumer.registration.nav.name")) / "consumer-registration" >> AuthUser.loginFirst,
      Menu("Consent Screen", Helper.i18n("consent.screen")) / "consent-screen" >> AuthUser.loginFirst,
      Menu("Dummy user tokens", "Get Dummy user tokens") / "dummy-user-tokens" >> AuthUser.loginFirst,

      Menu("Validate OTP", "Validate OTP") / "otp" >> AuthUser.loginFirst,
      Menu("User Information", "User Information") / "user-information",
      Menu("User Invitation", "User Invitation") / "user-invitation",
      Menu("User Invitation Info", "User Invitation Info") / "user-invitation-info",
      Menu("User Invitation Invalid", "User Invitation Invalid") / "user-invitation-invalid",
      Menu("User Invitation Warning", "User Invitation Warning") / "user-invitation-warning",
      Menu("Already Logged In", "Already Logged In") / "already-logged-in",
      Menu("Terms and Conditions", "Terms and Conditions") / "terms-and-conditions",
      Menu("Privacy Policy", "Privacy Policy") / "privacy-policy",
      // Menu.i("Metrics") / "metrics", //TODO: allow this page once we can make the account number anonymous in the URL
      Menu.i("OAuth") / "oauth" / "authorize", //OAuth authorization page
      Menu.i("Consent") / "consent" >> AuthUser.loginFirst,//OAuth consent page
      OAuthWorkedThanks.menu, //OAuth thanks page that will do the redirect
      Menu.i("Introduction") / "introduction",
      Menu.i("add-user-auth-context-update-request") / "add-user-auth-context-update-request",
      Menu.i("confirm-user-auth-context-update-request") / "confirm-user-auth-context-update-request",
      Menu.i("confirm-bg-consent-request") / "confirm-bg-consent-request" >> AuthUser.loginFirst,//OAuth consent page,
      Menu.i("confirm-bg-consent-request-sca") / "confirm-bg-consent-request-sca" >> AuthUser.loginFirst,//OAuth consent page,
      Menu.i("confirm-bg-consent-request-redirect-uri") / "confirm-bg-consent-request-redirect-uri" >> AuthUser.loginFirst,//OAuth consent page,
      Menu.i("confirm-vrp-consent-request") / "confirm-vrp-consent-request" >> AuthUser.loginFirst,//OAuth consent page,
      Menu.i("confirm-vrp-consent") / "confirm-vrp-consent" >> AuthUser.loginFirst //OAuth consent page
    ) ++ accountCreation ++ Admin.menus++ awakePage

    // Build SiteMap
    val sitemap = code.api.Constant.serverMode match {
      case mode if mode == "portal" => commonMap
      case mode if mode == "apis" => awakePage
      case mode if mode.contains("apis") && mode.contains("portal") => commonMap
      case _ => commonMap
    }

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

    val locale = I18NUtil.getDefaultLocale()
    // Locale.setDefault(locale) // TODO Explain why this line of code introduce weird side effects
    logger.info("Default Project Locale is :" + locale)

    // Cookie name
    val localeCookieName = "SELECTED_LOCALE"
    LiftRules.localeCalculator = {
      case fullReq @ Full(req) => {
        // Check against a set cookie, or the locale sent in the request
        def currentLocale : Locale = {
          S.findCookie(localeCookieName).flatMap {
            cookie => cookie.value.map(I18NUtil.computeLocale)
          } openOr locale
        }

        // Check to see if the user explicitly requests a new locale
        // In case it's true we use that value to set up a new cookie value
        ObpS.param(PARAM_LOCALE) match {
          case Full(requestedLocale) if requestedLocale != null && APIUtil.checkShortString(requestedLocale)==SILENCE_IS_GOLDEN => {
            val computedLocale: Locale = I18NUtil.computeLocale(requestedLocale)
            // Simon: if we are not using resource_user.last_used_local we don't need to set it. It is not returned in the Agent User endpoint. Thus, for now, we don't need to set it in the database.
            // val sessionId = S.session.map(_.uniqueId).openOr("")
            // AuthUser.updateComputedLocale(sessionId, computedLocale.toString())
            computedLocale
          }
          case _ => currentLocale
        }
      }
      case _ => locale
    }


    //for XSS vulnerability, set X-Frame-Options header as DENY
    LiftRules.supplementalHeaders.default.set(
      ("X-Frame-Options", "DENY") ::
        Nil
    )

    // Make a transaction span the whole HTTP request
    S.addAround(DB.buildLoanWrapper)
    logger.info("Note: We added S.addAround(DB.buildLoanWrapper) so each HTTP request uses ONE database transaction.")

    try {
      val useMessageQueue = APIUtil.getPropsAsBoolValue("messageQueue.createBankAccounts", false)
      if(useMessageQueue)
        BankAccountCreationListener.startListen
    } catch {
      case e: ExceptionInInitializerError => logger.warn(s"BankAccountCreationListener Exception: $e")
    }

    LiftRules.exceptionHandler.prepend{
      case(_, r, e) if e.isInstanceOf[NullPointerException] && e.getMessage.contains("Looking for Connection Identifier") => {
        logger.error(s"Exception being returned to browser when processing url is ${r.request.uri}, method is ${r.request.method}, exception detail is $e", e)
        JsonResponse(
          Extraction.decompose(ErrorMessage(code = 500, message = s"${ErrorMessages.DatabaseConnectionClosedError}")),
          500
        )
      }
      case(Props.RunModes.Development, r, e) => {
        logger.error(s"Exception being returned to browser when processing url is ${r.request.uri}, method is ${r.request.method}, exception detail is $e", e)
        JsonResponse(
          Extraction.decompose(ErrorMessage(code = 500, message = s"${ErrorMessages.InternalServerError} ${showExceptionAtJson(e)}")),
          500
        )
      }
      case (_, r , e) => {
        sendExceptionEmail(e)
        logger.error(s"Exception being returned to browser when processing url is ${r.request.uri}, method is ${r.request.method}, exception detail is $e", e)
        JsonResponse(
          Extraction.decompose(ErrorMessage(code = 500, message = s"${ErrorMessages.InternalServerError}")),
          500
        )
      }
    }

    LiftRules.uriNotFound.prepend{
      case (r, _) if r.uri.contains(ConstantsBG.berlinGroupVersion1.urlPrefix) => NotFoundAsResponse(errorJsonResponse(
        s"${ErrorMessages.InvalidUri}Current Url is (${r.uri.toString}), Current Content-Type Header is (${r.headers.find(_._1.equals("Content-Type")).map(_._2).getOrElse("")})",
        405,
        Some(CallContextLight(url = r.uri))
      )
      )
      case (r, _) => NotFoundAsResponse(errorJsonResponse(
        s"${ErrorMessages.InvalidUri}Current Url is (${r.uri.toString}), Current Content-Type Header is (${r.headers.find(_._1.equals("Content-Type")).map(_._2).getOrElse("")})",
        404)
      )
    }

    if ( !APIUtil.getPropsAsLongValue("transaction_request_status_scheduler_delay").isEmpty ) {
      val delay = APIUtil.getPropsAsLongValue("transaction_request_status_scheduler_delay").openOrThrowException("Incorrect value for transaction_request_status_scheduler_delay, please provide number of seconds.")
      TransactionRequestStatusScheduler.start(delay)
    }
    APIUtil.getPropsAsLongValue("database_messages_scheduler_interval") match {
      case Full(i) => DatabaseDriverScheduler.start(i)
      case _ => // Do not start it
    }
    ConsentScheduler.startAll()
    TransactionScheduler.startAll()


    APIUtil.getPropsAsBoolValue("enable_metrics_scheduler", true) match {
      case true =>
        val interval =
          APIUtil.getPropsAsIntValue("retain_metrics_scheduler_interval_in_seconds", 3600)
        MetricsArchiveScheduler.start(intervalInSeconds = interval)
      case false => // Do not start it
    }

    object UsernameLockedChecker  {
      def onBeginServicing(session: LiftSession, req: Req): Unit = {
        logger.debug(s"Hello from UsernameLockedChecker.onBeginServicing")
        checkIsLocked()
        logger.debug(s"Bye from UsernameLockedChecker.onBeginServicing")
      }
      def onSessionActivate(session: LiftSession): Unit = {
        logger.debug(s"Hello from UsernameLockedChecker.onSessionActivate")
        checkIsLocked()
        logger.debug(s"Bye from UsernameLockedChecker.onSessionActivate")
      }
      def onSessionPassivate(session: LiftSession): Unit = {
        logger.debug(s"Hello from UsernameLockedChecker.onSessionPassivate")
        checkIsLocked()
        logger.debug(s"Bye from UsernameLockedChecker.onSessionPassivate")
      }
      private def checkIsLocked(): Unit = {
        AuthUser.currentUser match {
          case Full(user) =>
            LoginAttempt.userIsLocked(localIdentityProvider, user.username.get) match {
              case true =>
                AuthUser.logoutCurrentUser
                logger.warn(s"checkIsLocked says: User ${user.username.get} has been logged out because it is locked.")
              case false => // Do nothing
                logger.debug(s"checkIsLocked says: User ${user.username.get} is not locked.")
            }
          case _ => // No user found
            logger.debug(s"checkIsLocked says: No User Found.")
        }
      }
    }
    LiftSession.onBeginServicing = UsernameLockedChecker.onBeginServicing _ :: LiftSession.onBeginServicing
    LiftSession.onSessionActivate = UsernameLockedChecker.onSessionActivate _ :: LiftSession.onSessionActivate
    LiftSession.onSessionPassivate = UsernameLockedChecker.onSessionPassivate _ :: LiftSession.onSessionPassivate

    // Sanity check for incompatible Props values for Scopes.
    sanityCheckOPropertiesRegardingScopes()
    // export one Connector's methods as endpoints, it is just for develop
    APIUtil.getPropsValue("connector.name.export.as.endpoints").foreach { connectorName =>
      // validate whether "connector.name.export.as.endpoints" have set a correct value
      code.api.Constant.CONNECTOR match {
        case Full("star") =>
          val starConnectorTypes = APIUtil.getPropsValue("starConnector_supported_types","mapped")
            .trim
            .split("""\s*,\s*""")

          val allSupportedConnectors: List[String] = Connector.nameToConnector.keys.toList
            .filter(it => starConnectorTypes.exists(it.startsWith(_)))

          assert(allSupportedConnectors.contains(connectorName), s"connector.name.export.as.endpoints=$connectorName, this value should be one of ${allSupportedConnectors.mkString(",")}")

        case _ if connectorName == "mapped" =>
          Functions.doNothing

        case Full(connector) =>
          assert(connector == connectorName, s"When 'connector=$connector', this props must be: connector.name.export.as.endpoints=$connector, but current it is $connectorName")
      }

      ConnectorEndpoints.registerConnectorEndpoints
    }
    if(HydraUtil.integrateWithHydra && HydraUtil.mirrorConsumerInHydra) {
      createHydraClients()
    }

    Props.get("session_inactivity_timeout_in_seconds") match {
      case Full(x) if tryo(x.toLong).isDefined =>
        LiftRules.sessionInactivityTimeout.default.set(Full((x.toLong.minutes): Long))
      case _ =>
      // Do not change default value
    }

  }

  private def sanityCheckOPropertiesRegardingScopes() = {
    if (propertiesRegardingScopesAreValid()) {
      throw new Exception(s"Incompatible Props values for Scopes.")
    }
  }

  def propertiesRegardingScopesAreValid() = {
    (ApiPropsWithAlias.requireScopesForAllRoles || !getPropsValue("require_scopes_for_listed_roles").toList.map(_.split(",")).isEmpty) &&
      APIUtil.getPropsAsBoolValue("allow_entitlements_or_scopes", false)
  }

  // create Hydra client if exists active consumer but missing Hydra client
  def createHydraClients() = {
    try {
      import scala.concurrent.ExecutionContext.Implicits.global
      // exists hydra clients id
      val oAuth2ClientIds = HydraUtil.hydraAdmin.listOAuth2Clients(Long.MaxValue, 0L).stream()
        .map[String](_.getClientId)
        .collect(Collectors.toSet())

      Consumers.consumers.vend.getConsumersFuture(Nil, None).foreach{ consumers =>
        consumers.filter(consumer => consumer.isActive.get && !oAuth2ClientIds.contains(consumer.key.get))
          .foreach(HydraUtil.createHydraClient(_))
      }
    } catch {
      case e: Exception =>
        if(HydraUtil.integrateWithHydra) {
          logger.error("------------------------------ Mirror consumer in hydra issue ------------------------------")
          e.printStackTrace()
        } else {
          logger.warn("------------------------------ Mirror consumer in hydra issue ------------------------------")
          logger.warn(e)
        }
    }
  }

  def schemifyAll() = {
    Schemifier.schemify(true, Schemifier.infoF _, ToSchemify.models: _*)
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

    import net.liftweb.util.Helpers.now

    val outputStream = new java.io.ByteArrayOutputStream
    val printStream = new java.io.PrintStream(outputStream)
    exception.printStackTrace(printStream)
    val currentTime = now.toString
    val stackTrace = new String(outputStream.toByteArray)
    val error = currentTime + ": " + stackTrace
    val host = Constant.HostName

    val mailSent = for {
      from <- APIUtil.getPropsValue("mail.exception.sender.address") ?~ "Could not send mail: Missing props param for 'from'"
      // no spaces, comma separated e.g. mail.api.consumer.registered.notification.addresses=notify@example.com,notify2@example.com,notify3@example.com
      toAddressesString <- APIUtil.getPropsValue("mail.exception.registered.notification.addresses") ?~ "Could not send mail: Missing props param for 'to'"
    } yield {

      //technically doesn't work for all valid email addresses so this will mess up if someone tries to send emails to "foo,bar"@example.com
      val to = toAddressesString.split(",").toList

      val emailContent = CommonsEmailWrapper.EmailContent(
        from = from,
        to = to,
        subject = s"you got an exception on $host",
        textContent = Some(error)
      )

      //this is an async call∆∆
      CommonsEmailWrapper.sendTextEmail(emailContent)
    }

    if(mailSent.isEmpty)
      logger.warn(s"Exception notification failed: $mailSent")
  }

  /**
   *  there will be a default bank and two default accounts in obp mapped mode.
   *  These bank and accounts will be used for the payments.
   *  when we create transaction request over counterparty and if the counterparty do not link to an existing obp account
   *  then we will use the default accounts (incoming and outgoing) to keep the money.
   */
  private def createDefaultBankAndDefaultAccountsIfNotExisting() ={
    val defaultBankId= APIUtil.defaultBankId
    val incomingAccountId= INCOMING_SETTLEMENT_ACCOUNT_ID
    val outgoingAccountId= OUTGOING_SETTLEMENT_ACCOUNT_ID

    MappedBank.find(By(MappedBank.permalink, defaultBankId)) match {
      case Full(b) =>
        logger.debug(s"Bank(${defaultBankId}) is found.")
      case _ =>
        MappedBank.create
          .permalink(defaultBankId)
          .fullBankName("OBP_DEFAULT_BANK")
          .shortBankName("OBP")
          .national_identifier("OBP")
          .mBankRoutingScheme("OBP")
          .mBankRoutingAddress("obp1")
          .logoURL("")
          .websiteURL("")
          .saveMe()
        logger.debug(s"creating Bank(${defaultBankId})")
    }

    MappedBankAccount.find(By(MappedBankAccount.bank, defaultBankId), By(MappedBankAccount.theAccountId, incomingAccountId)) match {
      case Full(b) =>
        logger.debug(s"BankAccount(${defaultBankId}, $incomingAccountId) is found.")
      case _ =>
        MappedBankAccount.create
          .bank(defaultBankId)
          .theAccountId(incomingAccountId)
          .accountCurrency("EUR")
          .saveMe()
        logger.debug(s"creating BankAccount(${defaultBankId}, $incomingAccountId).")
    }

    MappedBankAccount.find(By(MappedBankAccount.bank, defaultBankId), By(MappedBankAccount.theAccountId, outgoingAccountId)) match {
      case Full(b) =>
        logger.debug(s"BankAccount(${defaultBankId}, $outgoingAccountId) is found.")
      case _ =>
        MappedBankAccount.create
          .bank(defaultBankId)
          .theAccountId(outgoingAccountId)
          .accountCurrency("EUR")
          .saveMe()
        logger.debug(s"creating BankAccount(${defaultBankId}, $outgoingAccountId).")
    }
  }


  /**
   * Bootstrap Super User
   * Given the following credentials, OBP will create a user *if it does not exist already*.
   * This user's password will be valid for a limited amount of time.
   * This user will be granted ONLY CanCreateEntitlementAtAnyBank
   * This feature can also be used in a "Break Glass scenario"
   */
  private def createBootstrapSuperUser() ={

    val superAdminUsername = APIUtil.getPropsValue("super_admin_username","")
    val superAdminInitalPassword = APIUtil.getPropsValue("super_admin_inital_password","")
    val superAdminEmail = APIUtil.getPropsValue("super_admin_email","")

    val isPropsNotSetProperly = superAdminUsername==""||superAdminInitalPassword ==""||superAdminEmail==""

    //This is the logic to check if an AuthUser exists for the `create sandbox` endpoint, AfterApiAuth, OpenIdConnect ,,,
    val existingAuthUser = AuthUser.find(By(AuthUser.username, superAdminUsername))

    if(isPropsNotSetProperly) {
      //Nothing happens, props is not set
    }else if(existingAuthUser.isDefined) {
      logger.error(s"createBootstrapSuperUser- Errors:  Existing AuthUser with username ${superAdminUsername} detected in data import where no ResourceUser was found")
    } else {
      val authUser = AuthUser.create
        .email(superAdminEmail)
        .firstName(superAdminUsername)
        .lastName(superAdminUsername)
        .username(superAdminUsername)
        .password(superAdminInitalPassword)
        .passwordShouldBeChanged(true)
        .validated(true)

      val validationErrors = authUser.validate

      if(!validationErrors.isEmpty)
        logger.error(s"createBootstrapSuperUser- Errors: ${validationErrors.map(_.msg)}")
      else {
        Full(authUser.save()) //this will create/update the resourceUser.

        val userBox = Users.users.vend.getUserByProviderAndUsername(authUser.getProvider(), authUser.username.get)

        val resultBox = userBox.map(user => Entitlement.entitlement.vend.addEntitlement("", user.userId, CanCreateEntitlementAtAnyBank.toString))

        if(resultBox.isEmpty){
          logger.error(s"createBootstrapSuperUser- Errors: ${resultBox}")
        }
      }

    }

  }

  LiftRules.statelessDispatch.append(aliveCheck)

}

object ToSchemify {
  val models: List[MetaMapper[_]] = List(
    AuthUser,
    JobScheduler,
    MappedETag,
    MappedSigningBasket,
    MappedSigningBasketPayment,
    MappedSigningBasketConsent,
    MappedRegulatedEntity,
    AtmAttribute,
    Admin,
    MappedBank,
    MappedBankAccount,
    BankAccountRouting,
    MappedTransaction,
    DoubleEntryBookTransaction,
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
    TransactionRequestReasons,
    MappedMeeting,
    MappedMeetingInvitee,
    MappedBankAccountData,
    MappedPhysicalCard,
    PinReset,
    MappedBadLoginAttempt,
    UserLocks,
    MappedFXRate,
    MappedCurrency,
    MappedTransactionRequestTypeCharge,
    MappedAccountWebhook,
    SystemAccountNotificationWebhook,
    BankAccountNotificationWebhook,
    MappedCustomerIdMapping,
    MappedProductAttribute,
    MappedConsent,
    ConsentRequest,
    MigrationScriptLog,
    MethodRouting,
    EndpointMapping,
    WebUiProps,
    DynamicEntity,
    DynamicData,
    DynamicEndpoint,
    AccountIdMapping,
    DirectDebit,
    StandingOrder,
    MappedUserRefreshes,
    ApiCollection,
    ApiCollectionEndpoint,
    JsonSchemaValidation,
    AuthenticationTypeValidation,
    ConnectorMethod,
    DynamicResourceDoc,
    DynamicMessageDoc,
    EndpointTag,
    ProductFee,
    ViewPermission,
    UserInitAction,
    CounterpartyLimit,
    AccountAccess,
    ViewDefinition,
    ResourceUser,
    UserInvitation,
    UserAgreement,
    UserAttribute,
    MappedComment,
    MappedTag,
    MappedWhereTag,
    MappedTransactionImage,
    MappedNarrative,
    MappedCustomer,
    MappedUserCustomerLink,
    Consumer,
    Token,
    OpenIDConnectToken,
    Nonce,
    MappedCounterparty,
    MappedCounterpartyBespoke,
    MappedCounterpartyMetadata,
    MappedCounterpartyWhereTag,
    MappedTransactionRequest,
    TransactionRequestAttribute,
    MappedMetric,
    MetricArchive,
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
    MappedConsentAuthContext,
    MappedAccountApplication,
    MappedProductCollection,
    MappedProductCollectionItem,
    MappedAccountAttribute,
    MappedCustomerAttribute,
    MappedTransactionAttribute,
    MappedCardAttribute,
    BankAttribute,
    RateLimiting,
    MappedCustomerDependant,
    AttributeDefinition,
    CustomerAccountLink,
    TransactionIdMapping,
    RegulatedEntityAttribute,
    BankAccountBalance
  )

  // start grpc server
  if (APIUtil.getPropsAsBoolValue("grpc.server.enabled", false)) {
    val server = new HelloWorldServer(ExecutionContext.global)
    server.start()
    LiftRules.unloadHooks.append(server.stop)
  }

}
