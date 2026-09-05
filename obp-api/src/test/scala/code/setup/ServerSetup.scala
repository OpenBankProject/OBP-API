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

package code.setup

import org.json4s._
import _root_.org.json4s.JsonAST.JObject
import code.TestServer
import code.api.util.APIUtil._
import code.api.util.{APIUtil, CustomJsonFormats}
import code.model.{Consumer, Nonce, Token}
import code.model.dataAccess.{AuthUser, ResourceUser}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.{AccountId, BankId}
import net.liftweb.common.{Empty, Full}
import org.json4s.JsonDSL._
import org.scalatest._
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers
import code.api.util.DoobieUtil
import doobie.implicits._

trait ServerSetup extends AnyFeatureSpec with SendServerRequests
  with BeforeAndAfterEach with GivenWhenThen
  with BeforeAndAfterAll
  with Matchers with MdcLoggable with CustomJsonFormats with PropsReset{

  // Baseline props pushed twice — once at trait construction, once in beforeAll.
  //
  //   Trait-body pushes are needed because `val server = TestServer` below is
  //   evaluated at suite construction and triggers Lift Boot + http4s server
  //   startup. Migrations and the connector are wired from Props at that
  //   moment, so `migration_scripts.execute_all`, `connector`, etc. MUST be in
  //   place before TestServer.init. In environments where the props file
  //   already carries these values (the source-controlled test.default.props),
  //   omitting trait-body pushes goes unnoticed. In GitHub Actions, where the
  //   workflow writes a minimal props file from scratch, missing trait-body
  //   pushes cause Lift to skip migrations and tests get an empty DB.
  //
  //   beforeAll pushes are needed because PropsReset.beforeAll wipes every
  //   setPropsValues-owned map from Props.lockedProviders before chaining
  //   super — to purge cross-suite contamination from other suites'
  //   construction-time pushes. The same wipe also removes our own trait-body
  //   baselines (TestServer is already past Boot at that point and doesn't
  //   care, but the test scenarios do). Re-pushing in beforeAll restores them
  //   onto the now-clean stack for this suite's tests.
  private def pushBaselineProps(): Unit = {
    setPropsValues("migration_scripts.execute_all" -> "true")
    setPropsValues("migration_scripts.execute" -> "true")
    setPropsValues("allow_dauth" -> "true")
    setPropsValues("dauth.host" -> "127.0.0.1")
    setPropsValues("jwt_token_secret" -> "your-at-least-256-bit-secret-token")
    setPropsValues("jwt.public_key_rsa" -> "src/test/resources/cert/public_dauth.pem")
    setPropsValues("transactionRequests_supported_types" -> "SEPA,SANDBOX_TAN,FREE_FORM,COUNTERPARTY,ACCOUNT,ACCOUNT_OTP,SIMPLE,CARD,AGENT_CASH_WITHDRAWAL,CARDANO")
    setPropsValues("CARD_OTP_INSTRUCTION_TRANSPORT" -> "DUMMY")
    setPropsValues("AGENT_CASH_WITHDRAWAL_OTP_INSTRUCTION_TRANSPORT" -> "DUMMY")
    // Per-shard Redis key namespace: each parallel shard sets a distinct api_instance_id
    // (OBP_API_INSTANCE_ID), which flows into Constant.getGlobalCacheNamespacePrefix and thus
    // every Redis key (rate-limit counters, caches). This isolates shards on a shared Redis so
    // their counters don't collide. Single-instance/CI default stays "1_final".
    setPropsValues("api_instance_id" -> sys.env.getOrElse("OBP_API_INSTANCE_ID", "1_final"))
    setPropsValues("starConnector_supported_types" -> "mapped,internal,cardano_vJun2025")
    setPropsValues("connector" -> "star")
    setPropsValues("berlin_group_mandatory_headers" -> "")
    setPropsValues("berlin_group_mandatory_header_consent" -> "")
  }

  // Trait-body push: needed before `val server = TestServer` triggers Lift Boot.
  pushBaselineProps()

  override def beforeAll(): Unit = {
    super.beforeAll()  // PropsReset.beforeAll wipes owned maps first
    pushBaselineProps()  // re-push for this suite's tests
    resetDatabaseForTestClass()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    // Re-apply Berlin Group props on every scenario — PropsReset.afterEach
    // restores to the post-beforeEach snapshot, which after the first scenario
    // is the same as post-beforeAll. Subsequent scenarios push fresh berlin_group
    // entries; afterEach trims them back to that snapshot.
    setPropsValues(
      "berlin_group_mandatory_headers" -> "",
      "berlin_group_mandatory_header_consent" -> ""
    )
  }

  // Set system properties to force Pekko to use random available ports
  // This prevents conflicts when both RunWebApp and tests are running
  System.setProperty("pekko.remote.artery.canonical.port", "0")
  System.setProperty("pekko.remote.artery.bind.port", "0")

  /**
   * Reset database before each test class to ensure test isolation.
   *
   * This prevents test pollution where state from one test class leaks into another.
   * All tests share a single TestServer/database instance, so we need to clean up
   * before each test class starts.
   *
   * We preserve only the essential OAuth/auth tables (Nonce, Token, Consumer, AuthUser, ResourceUser)
   * as these are needed for test authentication and are managed by DefaultUsers trait. They are
   * preserved by omission: the deletes below name every other table one by one, and these five are
   * simply not among them. Do not add one for them.
   */

  /**
   * Resets database tables to ensure clean state for each test class.
   * Preserves auth-related tables that are managed separately by DefaultUsers.
   */
  protected def resetDatabaseForTestClass(): Unit = {
    logger.info(s"[TEST ISOLATION] Resetting database before test class: ${this.getClass.getSimpleName}")

    // Every table is listed explicitly: no entity is a Lift Mapper any more, so there is no
    // model loop to clear them. AtmTableResetIsolationTest fails if one is forgotten.
    //
    // migrationscriptlog is the one deliberate exception: it must NOT be added here. It is
    // migration bookkeeping, not test data. Wiping it makes isExecuted always false, so every
    // fresh `mvn test` JVM re-runs all historical migrations against a database that already has
    // their effects (e.g. migration-created views) — an in-place column retype on a
    // view-projected column then fails ("cannot alter type of a column used by a view or rule"),
    // aborting boot until the database is manually reset. It used to be excluded from the loop
    // above by identity; now that its entity is gone it is excluded by never appearing in either
    // place.
    DoobieUtil.runUpdate(sql"DELETE FROM mappedatm".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappednarrative".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedcomment".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedtag".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedwheretag".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedtransactionimage".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM producttag".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM connector_trace".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM consent_item".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM jsonschemavalidation".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedtransactiontype".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM etag".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM authenticationtypevalidation".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM userlocks".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM connectormethod".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM apicollectionendpoint".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM featuredapicollection".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM consentauthcontext".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappeduserauthcontext".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM userinitaction".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM accountidmapping".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM transactionidmapping".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedcustomeridmapping".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedbankaccountdata".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM apicollection".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedbadloginattempt".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM bankaccountrouting".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedfxrate".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM transactionrequestreasons".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM apiproductattribute".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedcardattribute".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM atmattribute".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM bankattribute".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM counterpartyattribute".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM regulatedentityattribute".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedproductattribute".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedcustomerattribute".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedaccountattribute".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedtransactionattribute".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM transactionrequestattribute".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedtaxresidence".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM customerlink".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM counterpartylimit".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM customeraccountlink".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedusercustomerlink".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedcrmevent".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappeduserrefreshes".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM payeelookup".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM metricsarchiverun".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM open_corridor_fee_accrual".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM utilitypaymentcallback".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM webuiprops".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM groupofroles".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM organisation".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM attributedefinition".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM jobscheduler".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM bankaccountbalance".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM endpointtag".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM apiproduct".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM amqp_bank_broker".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM productfee".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM message_outbox".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM openidconnecttoken".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM useragreement".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM userinvitation".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM methodrouting".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM AccountAccessRequest".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM BulkPayment".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM BulkBatchReference".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedkycstatus".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedkycmedia".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedkyccheck".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedkycdocument".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedsocialmedia".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM reaction".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM chatmessage".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM participant".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM chatroom".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM chat_email_digest_state".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedproductcollection".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedproductcollectionitem".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM directdebit".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM standingorder".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedaccountwebhook".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM bankaccountnotificationwebhook".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM systemaccountnotificationwebhook".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedscope".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedaccountapplication".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedcustomeraddress".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedentitlementrequest".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedcustomerdependant".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedcounterpartybespoke".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM expectedchallengeanswer".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM userattribute".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM regulatedentity".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM routingscheme".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM banksupportedroutingscheme".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM abacrule".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM endpointmapping".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM dynamicentityindex".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedmeetinginvitee".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedmeeting".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedcustomermessage".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedtransactionrequesttypecharge".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM pinreset".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedphysicalcard".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM doubleentrybooktransaction".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM dynamicendpoint".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedconnectormetric".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedentitlement".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM ratelimiting".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedproduct".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedbranch".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mapperaccountholders".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM dynamicmessagedoc".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM dynamicresourcedoc".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM dynamicdataaccess".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM dynamicentity".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM dynamicdata".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM viewpermission".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM accountaccess".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mandate".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mandateprovision".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM signatorypanel".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM signingbasket".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM signingbasketpayment".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM signingbasketconsent".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM consentrequest".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedcounterparty".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedcounterpartymetadata".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedcounterpartywheretag".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedbank".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedtransaction".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedtransactionrequest".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedcustomer".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM metric".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM metricarchive".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedconsent".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappedbankaccount".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM viewdefinition".update.run)
    DoobieUtil.runUpdate(sql"DELETE FROM mappeduserauthcontextupdate".update.run)
  }

  val server = TestServer
  def baseRequest = host(server.host, server.port)
  val secured = APIUtil.getPropsAsBoolValue("external.https", false)
  def externalBaseRequest = (server.externalHost, server.externalPort) match {
    case (Full(h), Full(p)) if secured  => host(h, p).secure
    case (Full(h), Full(p)) if !secured => host(h, p)
    case (Full(h), Empty) if secured  => host(h).secure
    case (Full(h), Empty) if !secured => host(h)
    case (Full(h), Empty) => host(h)
    case _ => baseRequest
  }
  
  val exampleDate = DateWithSecondsExampleObject
  
  // @code.setup.TestConnectorSetup.createBanks we can know, the bankIds in test database.
  val testBankId1 = BankId(APIUtil.defaultBankId)
  val testBankId2 = BankId("testBank2")
  
 // @code.setup.TestConnectorSetup.createAccounts we can know, the accountIds in test database.
  val testAccountId1 = AccountId("testAccount1")
  val testAccountId0 = AccountId("testAccount0")
  
  val mockCustomerNumber1 = "93934903201"
  val mockCustomerNumber2 = "93934903202"
  
  val mockCustomerNumber = "93934903208565488"
  val mockCustomerId = "cba6c9ef-73fa-4032-9546-c6f6496b354a"
  
  val emptyJSON : JObject = ("error" -> "empty List")
  val errorAPIResponse = new APIResponse(400,emptyJSON, None)
  
}

trait ServerSetupWithTestData extends ServerSetup with DefaultConnectorTestSetup with DefaultUsers{

  // On-demand test data. Creating transactions (10 accounts x 10) and transaction-requests
  // (10 accounts x 20) on every scenario is ~300 DB writes that most suites never read. Only
  // the suites listed below (matched by simple class name) get them; every other suite skips
  // both. The full test suite is the safety net: if a suite silently relied on this data it
  // fails and its name is added here. A suite can also override `needsTransactionData` directly.
  protected val suitesNeedingTransactionData: Set[String] = Set(
    // read beforeEach-created transaction-requests
    "TransactionRequestTest", "TransactionRequestsTest", "MakerCheckerTransactionRequestTest",
    "TransactionRequestAttributesTest", "CardanoTransactionRequestTest", "CounterpartyLimitTest",
    "VRPConsentRequestTest", "ViewPermissionsTest", "Http4s700RoutesTest",
    // read beforeEach-created transactions
    "TransactionsTest", "TransactionTest", "TransactionAttributesTest", "API1_2_1Test",
    "FirehoseTest", "DeleteTransactionCascadeTest", "DoubleEntryTransactionTest",
    "SandboxDataLoadingTest", "UKOpenBankingV310AisTests", "UKOpenBankingV200Tests",
    "Http4sBGv2AISTest", "AccountInformationServiceAISApiTest", "RegulatedEntityTest",
    // reads other-accounts / counterparties, which are derived from transaction metadata
    "CounterpartyTest"
  )
  protected def needsTransactionData: Boolean =
    suitesNeedingTransactionData.contains(this.getClass.getSimpleName)

  override def beforeEach() = {
    super.beforeEach()
    wipeTestData()
    //create fake data for the tests
    //fake banks
    val banks = createBanks()
    //fake bank accounts, views, accountHolders, AccountAccess
    val accounts = createAccountRelevantResources(resourceUser1, banks)
    //fake transactions + transactionRequests — opt-in per suite (see suitesNeedingTransactionData)
    if (needsTransactionData) {
      createTransactions(accounts)
      createTransactionRequests(accounts)
    }

  }

  override def afterEach() = {
    super.afterEach()
    wipeTestData()
  }

}