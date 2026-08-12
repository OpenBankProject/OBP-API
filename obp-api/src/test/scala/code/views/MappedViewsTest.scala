package code.views

import code.api.Constant
import code.api.util.ErrorMessages.ViewIdNotSupported
import code.setup.{DefaultUsers, ServerSetup}
import code.views.system.{ViewDefinition, ViewPermission}
import com.openbankproject.commons.model.{AccountId, BankId, BankIdAccountId, ViewId}
import net.liftweb.common.{Empty, Failure}

class MappedViewsTest extends ServerSetup with DefaultUsers{
  
  override def beforeAll() = {
    super.beforeAll()
    ViewDefinition.bulkDelete_!!()
  }
  
  override def afterEach() = {
    super.afterEach()
    ViewDefinition.bulkDelete_!!()
  }
  
  val bankIdAccountId = BankIdAccountId(BankId("1"),AccountId("2"))
  
  val viewIdOwner = Constant.SYSTEM_OWNER_VIEW_ID
  val viewIdAccountant = "accountant"
  val viewIdAuditor = "auditor"
  val viewIdNotSupport = "NotSupport"
  
  
  feature("test some important methods in MappedViews ") {
    
    scenario("test - getOrCreateAccountView") {
      
      Given("set up four normal Views")
      var viewOwner = MapperViews.getOrCreateSystemViewFromCbs(viewIdOwner)
      var viewAccountant = MapperViews.getOrCreateSystemViewFromCbs(viewIdAccountant)
      var viewAuditor = MapperViews.getOrCreateSystemViewFromCbs(viewIdAuditor)
      var allExistingViewsForOneAccount = MapperViews.availableViewsForAccount(bankIdAccountId)
      
      Then("Check the result from database. it should have 4 views and with the right viewId")
      viewOwner.head.viewId.value should equal(Constant.SYSTEM_OWNER_VIEW_ID.toLowerCase())
      viewAccountant.head.viewId.value should equal("accountant".toLowerCase())
      viewAuditor.head.viewId.value should equal("auditor".toLowerCase())
      allExistingViewsForOneAccount.length should equal(3)
      
      Then("We set the four normal views again")
      viewOwner = MapperViews.getOrCreateSystemViewFromCbs(viewIdOwner)
      viewAccountant = MapperViews.getOrCreateSystemViewFromCbs(viewIdAccountant)
      viewAuditor = MapperViews.getOrCreateSystemViewFromCbs(viewIdAuditor)
      allExistingViewsForOneAccount = MapperViews.availableViewsForAccount(bankIdAccountId)
  
      Then("Check the result from database again. it should have four views and with the right viewId, there should be not changed.")
      viewOwner.head.viewId.value should equal(Constant.SYSTEM_OWNER_VIEW_ID.toLowerCase())
      viewAccountant.head.viewId.value should equal("accountant".toLowerCase())
      viewAuditor.head.viewId.value should equal("auditor".toLowerCase())
      allExistingViewsForOneAccount.length should equal(3)
  
  
      Then("set up four wrong View name, do not support this viewId")
      val wrongViewId = "WrongViewId"
      val wrongView = MapperViews.getOrCreateSystemViewFromCbs(wrongViewId)
  
      wrongView.toString contains  ViewIdNotSupported shouldBe (true)
      
      wrongView.toString contains  wrongViewId shouldBe(true)

    }

    scenario("factoryResetSystemView restores code-defined defaults") {
      Given("an existing auditor system view created by getOrCreateSystemView")
      val created = MapperViews.getOrCreateSystemView(viewIdAuditor)
      created.isDefined shouldBe true
      val defaultActions = created.openOrThrowException("auditor view should exist").allowed_actions
      defaultActions.contains(Constant.CAN_ADD_TRANSACTION_REQUEST_TO_OWN_ACCOUNT) shouldBe false

      When("we add an extra permission that's not in the default auditor set")
      ViewPermission.createSystemViewPermission(
        ViewId(viewIdAuditor),
        Constant.CAN_ADD_TRANSACTION_REQUEST_TO_OWN_ACCOUNT,
        None
      ).isDefined shouldBe true
      val mutated = ViewDefinition.findSystemView(viewIdAuditor)
        .openOrThrowException("auditor view should still exist after mutation")
      mutated.allowed_actions.contains(Constant.CAN_ADD_TRANSACTION_REQUEST_TO_OWN_ACCOUNT) shouldBe true

      Then("factoryResetSystemView removes the extra permission and restores defaults")
      val reset = MapperViews.factoryResetSystemView(ViewId(viewIdAuditor))
      reset.isDefined shouldBe true
      val resetActions = reset.openOrThrowException("reset should return refreshed view").allowed_actions
      resetActions.contains(Constant.CAN_ADD_TRANSACTION_REQUEST_TO_OWN_ACCOUNT) shouldBe false
      resetActions.toSet should equal(defaultActions.toSet)
    }

    scenario("factoryResetSystemView returns Empty for an unknown system view id") {
      MapperViews.factoryResetSystemView(ViewId("does-not-exist")) shouldBe Empty
    }

    // Regression coverage for the UK Open Banking / Berlin Group views-permissions gap
    // remediation (Gap 1, 2, 5): each of these views previously either shared the generic
    // SYSTEM_VIEW_PERMISSION_COMMON set (so "Detail" granted nothing beyond "Basic") or had no
    // ViewPermission rows at all (the two BG views). Assert each view's allowed_actions match
    // its target set exactly — no more, no less.
    scenario("UK and Berlin Group system views have exact, differentiated can_* permission sets") {
      // UK/BG views are opt-in (created on demand), not unconditionally present like auditor —
      // getOrCreateSystemView creates them fresh with current code defaults; afterEach's
      // ViewDefinition.bulkDelete_!! guarantees no stale permissions leak in between scenarios.
      def actionsOf(viewId: String): Set[String] =
        MapperViews.getOrCreateSystemView(viewId)
          .openOrThrowException(s"$viewId should be a known system view")
          .allowed_actions.toSet

      actionsOf(Constant.SYSTEM_READ_ACCOUNTS_BASIC_VIEW_ID) should equal(Constant.SYSTEM_READ_ACCOUNTS_BASIC_VIEW_PERMISSION.toSet)
      actionsOf(Constant.SYSTEM_READ_ACCOUNTS_DETAIL_VIEW_ID) should equal(Constant.SYSTEM_READ_ACCOUNTS_DETAIL_VIEW_PERMISSION.toSet)
      actionsOf(Constant.SYSTEM_READ_BALANCES_VIEW_ID) should equal(Constant.SYSTEM_READ_BALANCES_VIEW_PERMISSION.toSet)
      actionsOf(Constant.SYSTEM_READ_TRANSACTIONS_BASIC_VIEW_ID) should equal(Constant.SYSTEM_READ_TRANSACTIONS_BASIC_VIEW_PERMISSION.toSet)
      actionsOf(Constant.SYSTEM_READ_TRANSACTIONS_DEBITS_VIEW_ID) should equal(Constant.SYSTEM_READ_TRANSACTIONS_DEBITS_VIEW_PERMISSION.toSet)
      actionsOf(Constant.SYSTEM_READ_TRANSACTIONS_CREDITS_VIEW_ID) should equal(Constant.SYSTEM_READ_TRANSACTIONS_CREDITS_VIEW_PERMISSION.toSet)
      actionsOf(Constant.SYSTEM_READ_TRANSACTIONS_DETAIL_VIEW_ID) should equal(Constant.SYSTEM_READ_TRANSACTIONS_DETAIL_VIEW_PERMISSION.toSet)
      actionsOf(Constant.SYSTEM_READ_ACCOUNTS_BERLIN_GROUP_VIEW_ID) should equal(Constant.SYSTEM_READ_ACCOUNTS_BERLIN_GROUP_VIEW_PERMISSION.toSet)
      actionsOf(Constant.SYSTEM_READ_BALANCES_BERLIN_GROUP_VIEW_ID) should equal(Constant.SYSTEM_READ_BALANCES_BERLIN_GROUP_VIEW_PERMISSION.toSet)

      Then("Detail must be a strict superset of Basic (never narrower), for both Accounts and Transactions")
      Constant.SYSTEM_READ_ACCOUNTS_DETAIL_VIEW_PERMISSION.toSet should contain allElementsOf Constant.SYSTEM_READ_ACCOUNTS_BASIC_VIEW_PERMISSION
      Constant.SYSTEM_READ_ACCOUNTS_DETAIL_VIEW_PERMISSION.toSet.size should be > Constant.SYSTEM_READ_ACCOUNTS_BASIC_VIEW_PERMISSION.toSet.size
      Constant.SYSTEM_READ_TRANSACTIONS_DETAIL_VIEW_PERMISSION.toSet should contain allElementsOf Constant.SYSTEM_READ_TRANSACTIONS_BASIC_VIEW_PERMISSION
      Constant.SYSTEM_READ_TRANSACTIONS_DETAIL_VIEW_PERMISSION.toSet.size should be > Constant.SYSTEM_READ_TRANSACTIONS_BASIC_VIEW_PERMISSION.toSet.size

      Then("Balances must not carry transaction- or counterparty-visibility permissions")
      actionsOf(Constant.SYSTEM_READ_BALANCES_VIEW_ID) should equal(Set(Constant.CAN_SEE_BANK_ACCOUNT_BALANCE, Constant.CAN_QUERY_AVAILABLE_FUNDS))
    }

    /**
     * The scenario above proves the permission sets are right when a view is *created*. That is
     * the only path it can reach: afterEach drops every ViewDefinition, so every scenario starts
     * on an empty table and getOrCreateSystemView always takes its create branch.
     *
     * The upgrade path is the one that matters and was never covered. applyDefaultsForSystemView
     * runs from unsavedSystemView (creation) and factoryResetSystemView (an admin endpoint);
     * getOrCreateSystemView -- what boot calls -- returns an existing row untouched. So on a
     * database where these views already exist carrying the generic set an older version gave
     * them, tightening the sets in code changes nothing at all: "Detail" still grants nothing
     * beyond "Basic", and Balances alone still exposes transaction and counterparty data.
     *
     * These build that database and then do what boot does.
     */
    val upgradedViews = List(
      Constant.SYSTEM_READ_ACCOUNTS_BASIC_VIEW_ID -> Constant.SYSTEM_READ_ACCOUNTS_BASIC_VIEW_PERMISSION,
      Constant.SYSTEM_READ_ACCOUNTS_DETAIL_VIEW_ID -> Constant.SYSTEM_READ_ACCOUNTS_DETAIL_VIEW_PERMISSION,
      Constant.SYSTEM_READ_BALANCES_VIEW_ID -> Constant.SYSTEM_READ_BALANCES_VIEW_PERMISSION,
      Constant.SYSTEM_READ_TRANSACTIONS_BASIC_VIEW_ID -> Constant.SYSTEM_READ_TRANSACTIONS_BASIC_VIEW_PERMISSION,
      Constant.SYSTEM_READ_TRANSACTIONS_DEBITS_VIEW_ID -> Constant.SYSTEM_READ_TRANSACTIONS_DEBITS_VIEW_PERMISSION,
      Constant.SYSTEM_READ_TRANSACTIONS_CREDITS_VIEW_ID -> Constant.SYSTEM_READ_TRANSACTIONS_CREDITS_VIEW_PERMISSION,
      Constant.SYSTEM_READ_TRANSACTIONS_DETAIL_VIEW_ID -> Constant.SYSTEM_READ_TRANSACTIONS_DETAIL_VIEW_PERMISSION,
      Constant.SYSTEM_READ_ACCOUNTS_BERLIN_GROUP_VIEW_ID -> Constant.SYSTEM_READ_ACCOUNTS_BERLIN_GROUP_VIEW_PERMISSION,
      Constant.SYSTEM_READ_BALANCES_BERLIN_GROUP_VIEW_ID -> Constant.SYSTEM_READ_BALANCES_BERLIN_GROUP_VIEW_PERMISSION
    )

    /** A database written by the version that gave all of these one generic permission set. */
    def seedPreUpgradeDatabase(): Unit = upgradedViews.foreach { case (viewId, _) =>
      val view = MapperViews.getOrCreateSystemView(viewId)
        .openOrThrowException(s"$viewId should be a known system view")
      ViewPermission.resetViewPermissions(view, Constant.SYSTEM_VIEW_PERMISSION_COMMON)
    }

    def permissionsOf(viewId: String): Set[String] =
      ViewDefinition.findSystemView(viewId)
        .openOrThrowException(s"$viewId should exist by now").allowed_actions.toSet

    scenario("an upgrade brings existing system views into line with the code") {
      Given("a database whose nine UK/BG views carry the old generic permission set")
      seedPreUpgradeDatabase()
      permissionsOf(Constant.SYSTEM_READ_BALANCES_VIEW_ID) should
        equal(Constant.SYSTEM_VIEW_PERMISSION_COMMON.toSet)

      When("boot sets the system views up")
      upgradedViews.foreach { case (viewId, _) => MapperViews.ensureSystemViewUpToDate(viewId) }

      Then("every one of them matches what the code defines")
      upgradedViews.foreach { case (viewId, expected) =>
        withClue(s"$viewId: ") { permissionsOf(viewId) should equal(expected.toSet) }
      }

      And("Balances in particular no longer carries transaction or counterparty visibility")
      val balances = permissionsOf(Constant.SYSTEM_READ_BALANCES_VIEW_ID)
      balances.filter(_.contains("transaction")) shouldBe empty
      balances.filter(_.contains("other_account")) shouldBe empty
    }

    scenario("reconciling twice is a no-op, not a second write") {
      seedPreUpgradeDatabase()
      upgradedViews.foreach { case (viewId, _) => MapperViews.ensureSystemViewUpToDate(viewId) }
      val afterFirst = upgradedViews.map { case (viewId, _) => viewId -> permissionsOf(viewId) }.toMap

      When("boot runs again, as it does on every restart")
      upgradedViews.foreach { case (viewId, _) => MapperViews.ensureSystemViewUpToDate(viewId) }

      Then("nothing has changed, and no duplicate rows have accumulated")
      upgradedViews.foreach { case (viewId, _) =>
        withClue(s"$viewId: ") {
          permissionsOf(viewId) should equal(afterFirst(viewId))
          val rows = ViewPermission.findSystemViewPermissions(ViewId(viewId))
          rows.map(_.permission.get).distinct.size should equal(rows.size)
        }
      }
    }

    scenario("a view the code does not define is left alone") {
      val owner = MapperViews.getOrCreateSystemView(Constant.SYSTEM_OWNER_VIEW_ID)
        .openOrThrowException("owner should be a known system view")
      val before = owner.allowed_actions.toSet
      MapperViews.ensureSystemViewUpToDate(Constant.SYSTEM_READ_BALANCES_VIEW_ID)
      permissionsOf(Constant.SYSTEM_OWNER_VIEW_ID) should equal(before)
    }

  }
  
  
}
