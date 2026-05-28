package code.api.UKOpenBanking.v3_1_0

import code.api.OBPRestHelper
import code.api.util.APIUtil.{OBPEndpoint, ResourceDoc, getAllowedEndpoints}
import code.api.util.ScannedApis
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus}

import scala.collection.mutable.ArrayBuffer

/*
 * All 16 stub-category UK Open Banking v3.1 endpoints have been migrated to their
 * respective Http4sUKOBv310* objects.  The four genuine data-backed categories
 * (AccountAccess, Accounts, Balances, Transactions) still run through Lift until
 * their http4s counterparts are ready.
 *
 * This aggregator is retained for ScannedApis registration (class-path scanning)
 * and so that external callers (APIUtil, SwaggerJSONFactory) that access
 * OBP_UKOpenBanking_310.apiVersion / .allResourceDocs continue to compile.
 */
object OBP_UKOpenBanking_310 extends OBPRestHelper with MdcLoggable with ScannedApis {

  override val apiVersion: ApiVersion = ApiVersion.ukOpenBankingV31
  val versionStatus: String = ApiVersionStatus.DRAFT.toString

  // Genuine endpoints still served by Lift (not yet migrated to http4s).
  private[this] val genuineEndpoints =
    APIMethods_AccountAccessApi.endpoints ++
    APIMethods_AccountsApi.endpoints ++
    APIMethods_BalancesApi.endpoints ++
    APIMethods_TransactionsApi.endpoints

  private[this] val genuineResourceDocs: ArrayBuffer[ResourceDoc] =
    APIMethods_AccountAccessApi.resourceDocs ++
    APIMethods_AccountsApi.resourceDocs ++
    APIMethods_BalancesApi.resourceDocs ++
    APIMethods_TransactionsApi.resourceDocs

  override val allResourceDocs: ArrayBuffer[ResourceDoc] =
    Http4sUKOBv310.resourceDocs ++ genuineResourceDocs

  override val routes: List[OBPEndpoint] = getAllowedEndpoints(genuineEndpoints, genuineResourceDocs)

  // Register only the genuine Lift routes; stub routes are served by http4s in Http4sApp.
  registerRoutes(routes, allResourceDocs, apiPrefix)
}

// ─── Original Lift aggregator (commented out) ────────────────────────────────
// The 16 stub *Api.scala files (Products, Beneficiaries, DirectDebits, Offers,
// Partys, ScheduledPayments, StandingOrders, Statements, DomesticPayments,
// DomesticScheduledPayments, DomesticStandingOrders, FilePayments,
// FundsConfirmations, InternationalPayments, InternationalScheduledPayments,
// InternationalStandingOrders) have been commented out.  Their endpoints are
// now served by the corresponding Http4sUKOBv310* objects.
//
//  private[this] val endpoints =
//    APIMethods_AccountAccessApi.endpoints ++
//    APIMethods_AccountsApi.endpoints ++
//    APIMethods_BalancesApi.endpoints ++
//    APIMethods_BeneficiariesApi.endpoints ++
//    APIMethods_DirectDebitsApi.endpoints ++
//    APIMethods_DomesticPaymentsApi.endpoints ++
//    APIMethods_DomesticScheduledPaymentsApi.endpoints ++
//    APIMethods_DomesticStandingOrdersApi.endpoints ++
//    APIMethods_FilePaymentsApi.endpoints ++
//    APIMethods_FundsConfirmationsApi.endpoints ++
//    APIMethods_InternationalPaymentsApi.endpoints ++
//    APIMethods_InternationalScheduledPaymentsApi.endpoints ++
//    APIMethods_InternationalStandingOrdersApi.endpoints ++
//    APIMethods_OffersApi.endpoints ++
//    APIMethods_PartysApi.endpoints ++
//    APIMethods_ProductsApi.endpoints ++
//    APIMethods_ScheduledPaymentsApi.endpoints ++
//    APIMethods_StandingOrdersApi.endpoints ++
//    APIMethods_StatementsApi.endpoints ++
//    APIMethods_TransactionsApi.endpoints
//
//  override val allResourceDocs: ArrayBuffer[ResourceDoc] =
//    APIMethods_AccountAccessApi.resourceDocs ++
//    APIMethods_AccountsApi.resourceDocs ++
//    APIMethods_BalancesApi.resourceDocs ++
//    APIMethods_BeneficiariesApi.resourceDocs ++
//    APIMethods_DirectDebitsApi.resourceDocs ++
//    APIMethods_DomesticPaymentsApi.resourceDocs ++
//    APIMethods_DomesticScheduledPaymentsApi.resourceDocs ++
//    APIMethods_DomesticStandingOrdersApi.resourceDocs ++
//    APIMethods_FilePaymentsApi.resourceDocs ++
//    APIMethods_FundsConfirmationsApi.resourceDocs ++
//    APIMethods_InternationalPaymentsApi.resourceDocs ++
//    APIMethods_InternationalScheduledPaymentsApi.resourceDocs ++
//    APIMethods_InternationalStandingOrdersApi.resourceDocs ++
//    APIMethods_OffersApi.resourceDocs ++
//    APIMethods_PartysApi.resourceDocs ++
//    APIMethods_ProductsApi.resourceDocs ++
//    APIMethods_ScheduledPaymentsApi.resourceDocs ++
//    APIMethods_StandingOrdersApi.resourceDocs ++
//    APIMethods_StatementsApi.resourceDocs ++
//    APIMethods_TransactionsApi.resourceDocs
//
//  override val routes : List[OBPEndpoint] = getAllowedEndpoints(endpoints, allResourceDocs)
//  registerRoutes(routes, allResourceDocs, apiPrefix)
//  logger.info(s"version $version has been run! There are ${routes.length} routes.")
