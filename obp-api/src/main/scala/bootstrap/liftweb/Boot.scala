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

import org.json4s._
import code.CustomerDependants.MappedCustomerDependant
import code.DynamicData.DynamicData
import code.DynamicData.DynamicDataAccess
import code.DynamicEndpoint.DynamicEndpoint
import code.UserRefreshes.MappedUserRefreshes
import code.abacrule.AbacRule
import code.accountaccessrequest.AccountAccessRequest
import code.accountapplication.MappedAccountApplication
import code.accountattribute.MappedAccountAttribute
import code.accountholders.MapperAccountHolders
import code.actorsystem.ObpActorSystem
import code.api.Constant._
//import code.api.ResourceDocs1_4_0.ResourceDocs300.{ResourceDocs310, ResourceDocs400, ResourceDocs500, ResourceDocs510, ResourceDocs600}
import code.api.ResourceDocs1_4_0._
import code.api._
import code.api.attributedefinition.AttributeDefinition
import code.api.berlin.group.ConstantsBG
import code.api.cache.Redis
import code.api.util.APIUtil.{enableVersionIfAllowed, errorJsonResponse, getPropsValue}
import code.api.util.ApiRole._
import code.api.util.ErrorMessages.MandatoryPropertyIsNotSet
import code.api.util._
import code.api.util.migration.Migration
import code.api.util.migration.Migration.DbFunction
import code.apicollection.ApiCollection
import code.apicollectionendpoint.ApiCollectionEndpoint
import code.apiproduct.ApiProduct
import code.apiproductattribute.ApiProductAttribute
import code.apiproductsubscription.{ApiProductSubscription, ApiProductSubscriptionScope}
import code.apiproductsubscriptionattribute.ApiProductSubscriptionAttribute
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
import code.consent.{ConsentItem, ConsentRequest, MappedConsent}
import code.consumer.Consumers
import code.model.Consumer
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
import code.featuredapicollection.FeaturedApiCollection
import code.fx.{MappedCurrency, MappedFXRate}
import code.group.Group
import code.organisation.Organisation
import code.routingscheme.{RoutingScheme, BankSupportedRoutingScheme}
import code.payeelookup.PayeeLookup
import code.utilitypayment.UtilityPaymentCallback
import code.bulkpayment.{BulkPayment, BulkBatchReference}
import code.kycchecks.MappedKycCheck
import code.kycdocuments.MappedKycDocument
import code.kycmedias.MappedKycMedia
import code.kycstatuses.MappedKycStatus
import code.loginattempts.{LoginAttempt, MappedBadLoginAttempt}
import code.meetings.{MappedMeeting, MappedMeetingInvitee}
import code.metadata.comments.MappedComment
import code.metadata.counterparties.{MappedCounterparty, MappedCounterpartyBespoke, MappedCounterpartyMetadata, MappedCounterpartyWhereTag}
import code.metadata.narrative.MappedNarrative
import code.metadata.tags.MappedTag
import code.metadata.transactionimages.MappedTransactionImage
import code.metadata.wheretags.MappedWhereTag
import code.methodrouting.MethodRouting
import code.metrics.{ConnectorTrace, MappedConnectorMetric, MappedMetric, MetricArchive, MetricsArchiveRun}
import code.migration.MigrationScriptLog
import code.model._
import code.model.dataAccess._
import code.model.dataAccess.internalMapping.AccountIdMapping
import code.obp.grpc.ObpGrpcServer
import code.productAttributeattribute.MappedProductAttribute
import code.productcollection.MappedProductCollection
import code.productcollectionitem.MappedProductCollectionItem
import code.productfee.ProductFee
import code.products.{MappedProduct, ProductTag}
import code.ratelimiting.RateLimiting
import code.regulatedentities.MappedRegulatedEntity
import code.regulatedentities.attribute.RegulatedEntityAttribute
import code.counterpartyattribute.{CounterpartyAttribute => CounterpartyAttributeMapper}
import code.scheduler._
import code.scope.{MappedScope, MappedUserScope, Scope}
import code.signingbaskets.{MappedSigningBasket, MappedSigningBasketConsent, MappedSigningBasketPayment}
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
import code.amqpbroker.AmqpBankBroker
import code.messageoutbox.{MessageOutbox, MessageOutboxRelay}
import code.transactionrequests.{MappedTransactionRequest, MappedTransactionRequestTypeCharge, TransactionRequestReasons}
import code.usercustomerlinks.MappedUserCustomerLink
import code.customerlinks.CustomerLink
import code.userlocks.UserLocks
import code.users._
import code.util.Helper.MdcLoggable
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
import org.json4s.Extraction
import net.liftweb.mapper.{DefaultConnectionIdentifier => _, _}
// SiteMap imports removed - API-only mode, no portal pages
import net.liftweb.util.Helpers._
import net.liftweb.util._
import org.apache.commons.io.FileUtils

import java.io.{File, FileInputStream}
import java.util.{Locale, TimeZone}
import scala.util.control.NonFatal



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
          val name = propsPath + f() + "props"
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

    logger.debug("Boot says:Using database driver: " + APIUtil.driver)

    DB.defineConnectionManager(net.liftweb.util.DefaultConnectionIdentifier,
      new code.api.util.http4s.RequestAwareConnectionManager(APIUtil.vendor))

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

    // Pre-Schemifier dedup: drop natural-key duplicate rows in mapperaccountholder /
    // mappedentitlement BEFORE schemifyAll() issues their CREATE UNIQUE INDEX (declared in
    // MapperAccountHolders / MappedEntitlement dbIndexes). On a long-lived DB that still holds
    // duplicates the index DDL would otherwise abort boot.
    //
    // This MUST stay here and must NOT be moved into Migration.database.executeScripts:
    //  - both executeScripts passes below run AFTER schemifyAll() (the index is already created
    //    by then — the "executed before Schemifier" comment on the true-pass is historical), and
    //  - executeScripts is gated by migration_scripts.* props (off in tests), whereas Schemifier —
    //    and therefore this dedup — must run ungated in every environment, incl. the H2 test DB.
    // The method self-guards (skips when the table is absent or has no duplicates), so running it
    // on every boot is a cheap no-op on fresh/clean/test databases.
    Migration.database.deduplicateBeforeUniqueIndexSchemify()
    schemifyAll()

    logger.info("Mapper database info: " + Migration.DbFunction.mapperDatabaseInfo)

    // NOTE: both executeScripts passes below run AFTER schemifyAll() above. The
    // `startedBeforeSchemifier = true` argument does NOT mean this pass runs before Schemifier — it
    // marks the existing-DB pass, in which migrations that require post-Schemifier schema skip
    // themselves (see Migration.executeScripts). The "before Schemifier" wording is historical: the
    // call once sat before schemifyAll() but was moved ahead of it in 2021 (commit ea4537029).
    DbFunction.tableExists(ResourceUser) match {
      case true => // DB already exists
        Migration.database.executeScripts(startedBeforeSchemifier = true)
        logger.info("The Mapper database already exists. Running the existing-DB migration pass (post-Schemifier; migrations needing fresh schema skip themselves).")
      case false => // Fresh DB — its migrations run in the catch-all pass below (after Schemifier)
        logger.info("The Mapper database is still not created. The scripts are going to be executed AFTER Lift Mapper Schemifier.")
    }

    // Migration Scripts are used to update the model of OBP-API DB to a latest version.

    // Please note that migration scripts are executed after Lift Mapper Schemifier
    Migration.database.executeScripts(startedBeforeSchemifier = false)

    // Maker/checker for dynamic code: when first enabled, pre-existing code rows get their current
    // body hash recorded as approved so enabling the feature does not silently disable them.
    code.dynamicchangerequest.MakerChecker.seedApprovedHashesIfEnabled()

    // Idempotent seed of country-qualified routing schemes (TZ.MSISDN, bill, utility, etc.).
    // Toggle off via routing_schemes.seed_defaults_at_boot=false in environments that don't want defaults.
    code.routingscheme.RoutingSchemeSeed.runIfEnabled()

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

      // The UK Open Banking and Berlin Group views whose can_* sets the code defines
      // (Constant.SYSTEM_READ_*_VIEW_PERMISSION). Ensured unconditionally rather than through
      // additional_system_views: the code already depends on them existing. A UK consent naming
      // ReadTransactionsCredits cannot be granted if that view is absent, and
      // validateUKConsentPermissions *requires* a direction permission whenever a
      // transaction-depth one is granted -- so a conforming consent could not be exercised on an
      // instance whose props predated the view.
      //
      // ensureSystemViewUpToDate, not getOrCreateSystemView: the latter returns an existing row
      // untouched, so a view created by an older version keeps that version's permission set for
      // good and a tightening in code reaches new installations only. That is how "Detail granted
      // nothing beyond Basic" and "Balances alone exposed transaction data" survived being fixed.
      val codeDefinedSystemViews = List(
        SYSTEM_READ_ACCOUNTS_BASIC_VIEW_ID,
        SYSTEM_READ_ACCOUNTS_DETAIL_VIEW_ID,
        SYSTEM_READ_BALANCES_VIEW_ID,
        SYSTEM_READ_TRANSACTIONS_BASIC_VIEW_ID,
        SYSTEM_READ_TRANSACTIONS_DEBITS_VIEW_ID,
        SYSTEM_READ_TRANSACTIONS_CREDITS_VIEW_ID,
        SYSTEM_READ_TRANSACTIONS_DETAIL_VIEW_ID,
        SYSTEM_READ_ACCOUNTS_BERLIN_GROUP_VIEW_ID,
        SYSTEM_READ_BALANCES_BERLIN_GROUP_VIEW_ID
      )
      // Default true: a permission set the code tightened must reach the installations that have
      // the problem, not only fresh ones. An operator who has deliberately hand-tuned these rows
      // turns it off and takes responsibility for keeping them current.
      if (APIUtil.getPropsAsBoolValue("system_views.reconcile_permissions_at_boot", true)) {
        codeDefinedSystemViews.foreach(Views.views.vend.ensureSystemViewUpToDate)
      } else {
        logger.warn("system_views.reconcile_permissions_at_boot is false: the UK Open Banking and " +
          "Berlin Group system views keep whatever permissions they already carry, which may be " +
          "an older and wider set than this build defines.")
        codeDefinedSystemViews.foreach(Views.views.vend.getOrCreateSystemView)
      }

      // The remaining two stay opt-in: nothing in the code requires them to exist, so whether an
      // instance has them is still the operator's call. Their permission sets ARE code-defined
      // though, so where they do exist they are kept current the same way -- the prop decides
      // existence, not whether the code is authoritative about what a view grants.
      APIUtil.getPropsValue("additional_system_views") match {
        case Full(value) =>
          val additionalSystemViewsFromProps = value.split(",").map(_.trim).toList
          val additionalSystemViews = List(
            SYSTEM_READ_TRANSACTIONS_BERLIN_GROUP_VIEW_ID,
            SYSTEM_INITIATE_PAYMENTS_BERLIN_GROUP_VIEW_ID
          )
          for {
            systemView <- additionalSystemViewsFromProps
            if additionalSystemViews.exists(_ == systemView)
          } {
            Views.views.vend.ensureSystemViewUpToDate(systemView)
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

    warnAboutSuperAdminUsers()

    warnAboutEmailDeliveryConfiguration()

    OAuth2Login.logConfigWarnings()

    createBootstrapOidcOperatorUser()

    createBootstrapOidcOperatorConsumer()

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

    // NOTE: DB/Redis shutdown is registered together with the gRPC server stop in a
    // single ORDERED shutdown hook at the end of boot (after the gRPC block) — see there.
    // JVM runs every addShutdownHook thread CONCURRENTLY with no ordering guarantee, so a
    // separate DB-close hook could race a still-draining gRPC server that touches the DB.
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
      Option(getClass.getClassLoader.getResource("./")).map { url =>
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

    // DirectLogin (POST /my/logins/direct) and aliveCheck (GET /alive) are now served
    // by their native http4s counterparts wired into Http4sApp.baseServices
    // (DirectLoginRoutes / AliveCheckRoutes). The Lift dispatches were retired in the
    // http4s migration; any prop gates (e.g. `allow_direct_login`) live with those
    // routes. The OBP-as-relying-party OpenID Connect callback was removed: OBP is a
    // pure OAuth2 resource server (Bearer-JWT validation), login is done by the client/BFF.

    //////////////////////////////////////////////////////////////////////////////////////////////////
    // Resource Docs are used in the process of surfacing endpoints so we enable them explicitly
    // to avoid a circular dependency.
    // Make the (currently identical) endpoints available to different versions.
    // ResourceDocs140..600 are now served by code.api.util.http4s.Http4sResourceDocs
    // (wired into Http4sApp.baseServices). The Lift dispatches were retired in the
    // http4s migration. The 10 ResourceDocs* objects remain in the codebase as a
    // source of `ImplementationsResourceDocs.getResourceDocsList` (used by the
    // centralised service) but no longer participate in request dispatch.
    ////////////////////////////////////////////////////


    // Sandbox account creation menu removed - API-only mode, no portal pages


    // API Metrics (logs of API calls)
    // If set to true we will write each URL with params to a datastore / log file
    if (code.metrics.MetricsProps.writeMetrics) {
      logger.info("writeMetrics is true. We will write API metrics")
      code.metrics.MetricBatchWriter.start()
    } else {
      logger.info("writeMetrics is false. We will NOT write API metrics")
    }

    // API Metrics (logs of Connector calls)
    // If set to true we will write each URL with params to a datastore / log file
    if (APIUtil.getPropsAsBoolValue("write_connector_metrics", false)) {
      logger.info("writeConnectorMetrics is true. We will write connector metrics")
      code.metrics.ConnectorMetricBatchWriter.start()
    } else {
      logger.info("writeConnectorMetrics is false. We will NOT write connector metrics")
    }


    logger.info (s"props_identifier is : ${APIUtil.getPropsValue("props_identifier", "NONE-SET")}")

    // SiteMap removed - API-only mode, all routing via statelessDispatch


    try {
      val useMessageQueue = APIUtil.getPropsAsBoolValue("messageQueue.createBankAccounts", false)
      if(useMessageQueue)
        BankAccountCreationListener.startListen
    } catch {
      // ExceptionInInitializerError is a LinkageError, so NonFatal does not cover it.
      // NonFatal covers e.g. java.net.ConnectException when the broker is unreachable;
      // without it the exception escapes boot, the main thread dies and the server never binds.
      case e: ExceptionInInitializerError => logger.warn(s"BankAccountCreationListener Exception: $e")
      case NonFatal(e) => logger.warn(s"BankAccountCreationListener Exception: $e")
    }

    if ( !APIUtil.getPropsAsLongValue("transaction_request_status_scheduler_delay").isEmpty ) {
      val delay = APIUtil.getPropsAsLongValue("transaction_request_status_scheduler_delay").openOrThrowException("Incorrect value for transaction_request_status_scheduler_delay, please provide number of seconds.")
      TransactionRequestStatusScheduler.start(delay)
    }
    // Open Corridor: the transactional-outbox relay publishing Interface C messages
    // (credit notifications + settlement instructions) to the banks' own vhosts.
    if (APIUtil.getPropsAsBoolValue("open_corridor_enabled", false)) {
      MessageOutboxRelay.start(APIUtil.getPropsAsLongValue("open_corridor.outbox_relay_interval", 10L))
    }
    // Chat: emails users an occasional digest of unread messages (computed at
    // send time from read markers — see ChatEmailDigestScheduler for why this
    // is not the transactional message outbox).
    if (APIUtil.getPropsAsBoolValue("chat.email_digest_enabled", false)) {
      code.chat.ChatEmailDigestScheduler.start(APIUtil.getPropsAsLongValue("chat.email_digest_tick_seconds", 300L))
    }
    APIUtil.getPropsAsLongValue("database_messages_scheduler_interval") match {
      case Full(i) => DatabaseDriverScheduler.start(i)
      case _ => // Do not start it
    }
    ConsentScheduler.startAll()
    TransactionScheduler.startAll()


    code.metrics.MetricsProps.enableMetricsScheduler match {
      case true =>
        // Interval default rationale (599s prime) lives at
        // MetricsProps.RetainMetricsSchedulerIntervalInSecondsDefault.
        val interval = code.metrics.MetricsProps.retainMetricsSchedulerIntervalInSeconds
        MetricsArchiveScheduler.start(intervalInSeconds = interval)
      case false => // Do not start it
    }


    // ConnectorEndpoints (connector.name.export.as.endpoints) registered via Lift statelessDispatch
    // which is no longer reachable (Lift bridge removed in Phase B). Disabled until migrated to http4s.
  }

  def schemifyAll() = {
    Schemifier.schemify(true, Schemifier.infoF _, ToSchemify.models: _*)
    // Create default system-level "general" chat room (is_open_room = true)
    code.chat.ChatRoomTrait.chatRoomProvider.vend.getOrCreateDefaultRoom()
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

  /**
   * Warn about Super Admin Users
   * Super admin is intended for bootstrapping only. Users should grant themselves
   * proper roles (e.g. CanCreateEntitlementAtAnyBank) and then remove their user_id
   * from the super_admin_user_ids props setting.
   */
  private def warnAboutSuperAdminUsers(): Unit = {
    APIUtil.getPropsValue("super_admin_user_ids") match {
      case Full(v) if v.trim.nonEmpty =>
        val userIds = v.split(",").map(_.trim).filter(_.nonEmpty).toList
        if (userIds.nonEmpty) {
          logger.warn("========================================================================")
          logger.warn("WARNING: super_admin_user_ids is configured with the following user IDs:")
          userIds.foreach(userId => logger.warn(s"  - $userId"))
          logger.warn("")
          logger.warn("Super admin is intended for BOOTSTRAPPING ONLY.")
          logger.warn("These users bypass normal role checks.")
          logger.warn("Please:")
          logger.warn("  1. Login as a super admin user")
          logger.warn("  2. Grant yourself CanCreateEntitlementAtAnyBank (and other required roles)")
          logger.warn("  3. Remove your user_id from super_admin_user_ids in props")
          logger.warn("========================================================================")
        }
      case _ => // No super admin users configured, nothing to warn about
    }
  }

  /**
   * Warn at startup about email-delivery configuration that would silently break
   * signup-validation and password-reset flows. Both flows embed a link built from
   * `portal_external_url`; if the prop is missing the signup endpoint skips the
   * send with no log line and the user gets a 201. Many SMTP servers reject the
   * default `noreply@example.com` From address, in which case Transport.send
   * succeeds at the boundary but mail is dropped downstream.
   */
  private def warnAboutEmailDeliveryConfiguration(): Unit = {
    val portalUrl = APIUtil.getPropsValue("portal_external_url")
    val senderAddress = APIUtil.getPropsValue("mail.users.userinfo.sender.address", "noreply@example.com")
    val portalMissing = portalUrl.isEmpty || portalUrl.exists(_.trim.isEmpty)
    val senderIsDefault = senderAddress == "noreply@example.com"
    if (portalMissing || senderIsDefault) {
      logger.warn("========================================================================")
      logger.warn("WARNING: Email delivery configuration is incomplete.")
      logger.warn("")
      if (portalMissing) {
        logger.warn("  portal_external_url is not set.")
        logger.warn("    Signup-validation and password-reset emails are silently skipped")
        logger.warn("    when this prop is missing. Users will complete signup with a 201")
        logger.warn("    response but never receive a validation email.")
      }
      if (senderIsDefault) {
        logger.warn("  mail.users.userinfo.sender.address is still the default")
        logger.warn("    'noreply@example.com'. Most SMTP servers reject this From address")
        logger.warn("    (SPF/DKIM/anti-spoof). SMTP send may succeed but mail will be")
        logger.warn("    dropped downstream.")
      }
      logger.warn("")
      logger.warn("Verify end-to-end delivery with POST /obp/v7.0.0/management/self-test-emails.")
      logger.warn("========================================================================")
    }
  }

  /**
   * Bootstrap OIDC Operator User
   * Given the following credentials, OBP will create a user *if it does not exist already*.
   * This user will be granted: CanGetAnyUser, CanVerifyUserCredentials, CanVerifyOidcClient, CanGetOidcClient, CanGetConsumers
   */
  private def createBootstrapOidcOperatorUser() = {

    val oidcOperatorUsername = APIUtil.getPropsValue("oidc_operator_username", "")
    val oidcOperatorInitialPassword = APIUtil.getPropsValue("oidc_operator_initial_password", "")
    val oidcOperatorEmail = APIUtil.getPropsValue("oidc_operator_email", "")

    val isPropsNotSetProperly = oidcOperatorUsername == "" || oidcOperatorInitialPassword == "" || oidcOperatorEmail == ""

    val existingAuthUser = AuthUser.find(By(AuthUser.username, oidcOperatorUsername))

    if (isPropsNotSetProperly) {
      //Nothing happens, props is not set
    } else if (existingAuthUser.isDefined) {
      logger.error(s"createBootstrapOidcOperatorUser- Errors: Existing AuthUser with username ${oidcOperatorUsername} detected in data import where no ResourceUser was found")
    } else {
      val authUser = AuthUser.create
        .email(oidcOperatorEmail)
        .firstName(oidcOperatorUsername)
        .lastName(oidcOperatorUsername)
        .username(oidcOperatorUsername)
        .password(oidcOperatorInitialPassword)
        .passwordShouldBeChanged(false)
        .validated(true)

      val validationErrors = authUser.validate

      if (!validationErrors.isEmpty)
        logger.error(s"createBootstrapOidcOperatorUser- Errors: ${validationErrors.map(_.msg)}")
      else {
        Full(authUser.save())

        val userBox = Users.users.vend.getUserByProviderAndUsername(authUser.getProvider(), authUser.username.get)

        val oidcOperatorRoles = List(
          CanGetAnyUser,
          CanVerifyUserCredentials,
          CanVerifyOidcClient,
          CanGetOidcClient,
          CanGetConsumers
        )

        userBox match {
          case Full(user) =>
            oidcOperatorRoles.foreach { role =>
              val resultBox = Entitlement.entitlement.vend.addEntitlement("", user.userId, role.toString)
              if (resultBox.isEmpty) {
                logger.error(s"createBootstrapOidcOperatorUser- Error granting ${role}: ${resultBox}")
              }
            }
          case _ =>
            logger.error(s"createBootstrapOidcOperatorUser- Error: Could not find user after creation")
        }
      }
    }
  }

  /**
   * Bootstrap OIDC Operator Consumer
   * Given the following key and secret, OBP will create a consumer *if it does not exist already*.
   * This consumer will be granted scopes: CanGetConsumers, CanCreateConsumer, CanVerifyOidcClient, CanGetOidcClient
   * This allows OBP-OIDC to authenticate as an application (without a user) and manage consumers via the API.
   */
  private def createBootstrapOidcOperatorConsumer() = {

    val oidcOperatorConsumerKey = APIUtil.getPropsValue("oidc_operator_consumer_key", "")
    val oidcOperatorConsumerSecret = APIUtil.getPropsValue("oidc_operator_consumer_secret", "")

    val isPropsNotSetProperly = oidcOperatorConsumerKey == "" || oidcOperatorConsumerSecret == ""

    if (isPropsNotSetProperly) {
      logger.info(s"createBootstrapOidcOperatorConsumer says: oidc_operator_consumer_key and/or oidc_operator_consumer_secret props are not set, skipping")
    } else if (oidcOperatorConsumerKey.length < 10) {
      logger.error(s"createBootstrapOidcOperatorConsumer says: oidc_operator_consumer_key is too short (${oidcOperatorConsumerKey.length} chars, minimum 10), skipping")
    } else if (oidcOperatorConsumerKey.length > 250) {
      logger.error(s"createBootstrapOidcOperatorConsumer says: oidc_operator_consumer_key is too long (${oidcOperatorConsumerKey.length} chars, maximum 250), skipping")
    } else if (oidcOperatorConsumerSecret.length < 10) {
      logger.error(s"createBootstrapOidcOperatorConsumer says: oidc_operator_consumer_secret is too short (${oidcOperatorConsumerSecret.length} chars, minimum 10), skipping")
    } else if (oidcOperatorConsumerSecret.length > 250) {
      logger.error(s"createBootstrapOidcOperatorConsumer says: oidc_operator_consumer_secret is too long (${oidcOperatorConsumerSecret.length} chars, maximum 250), skipping")
    } else {
      val existingConsumer = Consumers.consumers.vend.getConsumerByConsumerKey(oidcOperatorConsumerKey)

      if (existingConsumer.isDefined) {
        logger.info(s"createBootstrapOidcOperatorConsumer says: Consumer with key ${oidcOperatorConsumerKey} already exists, skipping creation")
      } else {
        saveOidcOperatorConsumer(oidcOperatorConsumerKey, oidcOperatorConsumerSecret)
      }
    }
  }

  // Separate method to create and save the OIDC operator consumer.
  // Uses Consumer.create directly (not Consumers.consumers.vend.createConsumer)
  // to avoid S.? calls during Boot (Lift's S scope is not initialized at boot time).
  private def saveOidcOperatorConsumer(consumerKey: String, consumerSecret: String): Unit = {
    // Create consumer directly, skipping validate (which calls S.? and fails during Boot)
    val c = Consumer.create
      .key(consumerKey)
      .secret(consumerSecret)
      .name("OIDC Operator Consumer")
    c.isActive(true) // MappedBoolean.apply returns Mapper, must be separate statement
    c.description("Bootstrap consumer for OBP-OIDC to manage consumers via the API") // MappedText.apply returns Mapper, must be separate statement

    val consumerBox = tryo(c.saveMe())

    consumerBox match {
      case Full(consumer) =>
        logger.info(s"createBootstrapOidcOperatorConsumer says: Consumer created successfully with consumer_id: ${consumer.consumerId.get}")
        val scopes = List(CanGetConsumers, CanCreateConsumer, CanVerifyOidcClient, CanGetOidcClient)
        scopes.foreach { role =>
          val resultBox = Scope.scope.vend.addScope("", consumer.id.get.toString, role.toString)
          if (resultBox.isEmpty) {
            logger.error(s"createBootstrapOidcOperatorConsumer says: Error granting scope ${role}: ${resultBox}")
          }
        }
      case net.liftweb.common.Failure(msg, exception, _) =>
        logger.error(s"createBootstrapOidcOperatorConsumer says: Error creating consumer: $msg ${exception.map(_.getMessage).openOr("")}")
      case _ =>
        logger.error("createBootstrapOidcOperatorConsumer says: Error creating consumer (unknown error)")
    }
  }

  // aliveCheck (GET /alive) is served by code.api.AliveCheckRoutes (wired into Http4sApp.baseServices).

}

object ToSchemify extends MdcLoggable {
  val models: List[MetaMapper[_]] = List(
    AuthUser,
    JobScheduler,
    MappedETag,
    MappedSigningBasket,
    MappedSigningBasketPayment,
    MappedSigningBasketConsent,
    MappedRegulatedEntity,
    AtmAttribute,
    AbacRule,
    code.mandate.Mandate,
    code.mandate.MandateProvision,
    code.mandate.SignatoryPanel,
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
    ConsentItem,
    ConsentRequest,
    MigrationScriptLog,
    MethodRouting,
    EndpointMapping,
    WebUiProps,
    DynamicEntity,
    DynamicData,
    DynamicDataAccess,
    code.api.dynamic.entity.projection.DynamicEntityIndex,
    DynamicEndpoint,
    AccountIdMapping,
    DirectDebit,
    StandingOrder,
    MappedUserRefreshes,
    ApiCollection,
    ApiCollectionEndpoint,
    ApiProduct,
    ApiProductAttribute,
    ApiProductSubscription,
    ApiProductSubscriptionScope,
    ApiProductSubscriptionAttribute,
    FeaturedApiCollection,
    JsonSchemaValidation,
    AuthenticationTypeValidation,
    ConnectorMethod,
    DynamicResourceDoc,
    DynamicMessageDoc,
    EndpointTag,
    ProductFee,
    ProductTag,
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
    CustomerLink,
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
    AmqpBankBroker,
    MessageOutbox,
    code.opencorridorfees.OpenCorridorFeeAccrual,
    MappedMetric,
    MetricArchive,
    MetricsArchiveRun,
    MapperAccountHolders,
    MappedEntitlement,
    MappedConnectorMetric,
    ConnectorTrace,
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
    CounterpartyAttributeMapper,
    BankAccountBalance,
    Group,
    Organisation,
    RoutingScheme,
    BankSupportedRoutingScheme,
    PayeeLookup,
    UtilityPaymentCallback,
    BulkPayment,
    BulkBatchReference,
    AccountAccessRequest,
    code.dynamicchangerequest.DynamicChangeRequest,
    code.chat.ChatRoom,
    code.chat.Participant,
    code.chat.ChatMessage,
    code.chat.ChatEmailDigestState,
    code.chat.Reaction
  )

  // start grpc server
  // start grpc server (optional)
  val grpcServerOpt: Option[ObpGrpcServer] =
    if (APIUtil.getPropsAsBoolValue("grpc.server.enabled", false)) {
      val server = new ObpGrpcServer(code.api.util.BlockingIoExecutionContext.ec)
      server.start()
      Some(server)
    } else None

  // Single ORDERED shutdown hook (replaces the former two CONCURRENT hooks: the DB/Redis
  // close that used to sit earlier in boot, and the gRPC stop here). JVM runs every
  // addShutdownHook thread concurrently with no ordering guarantee, so the old pair could
  // race: gRPC might still be serving an in-flight request that touches the DB while the
  // connection pool is being closed. Here we stop gRPC FIRST (drains in-flight requests),
  // THEN close DB + Redis. Each step is guarded so one failure doesn't skip the rest.
  Runtime.getRuntime.addShutdownHook(new Thread(() => {
    grpcServerOpt.foreach { s =>
      try s.stop() catch { case e: Throwable => logger.warn("gRPC server.stop() failed during shutdown", e) }
    }
    try APIUtil.vendor.closeAllConnections_!() catch { case e: Throwable => logger.warn("DB closeAllConnections_!() failed during shutdown", e) }
    try Redis.jedisPoolDestroy catch { case e: Throwable => logger.warn("Redis jedisPoolDestroy failed during shutdown", e) }
  }))

}
