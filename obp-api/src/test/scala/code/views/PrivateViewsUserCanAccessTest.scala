package code.views

import code.api.Constant
import code.api.Constant.ALL_CONSUMERS
import code.setup.{DefaultUsers, ServerSetup}
import code.views.system.{AccountAccess, ViewDefinition}
import com.openbankproject.commons.model.{AccountId, BankId, BankIdAccountId}
import net.liftweb.common.Full
import net.liftweb.db.DB
import net.liftweb.util.DefaultConnectionIdentifier

class PrivateViewsUserCanAccessTest extends ServerSetup with DefaultUsers {

  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  override def afterEach(): Unit = {
    super.afterEach()
    // Wrap cleanup in DB.use so all deletes share one connection, then commit.
    // Without this, HikariCP rollbacks uncommitted writes (autoCommit=false)
    // and the Doobie pool wouldn't see a clean state for the next test.
    DB.use(DefaultConnectionIdentifier) { conn =>
      AccountAccess.deleteAll()
      ViewDefinition.deleteAll()
      conn.connection.commit()
    }
  }

  val bankId1 = BankId("test-bank-1")
  val bankId2 = BankId("test-bank-2")
  val accountId1 = AccountId("test-account-1")
  val accountId2 = AccountId("test-account-2")
  val accountId3 = AccountId("test-account-3")

  private def createSystemViewAndGrantAccess(bankId: BankId, accountId: AccountId, viewId: String, user: code.model.dataAccess.ResourceUser): Unit = {
    // Wrap in DB.use so all Mapper calls share one connection.
    // Explicit commit ensures Doobie pool (separate connections) can see the writes.
    // In production, Lift's buildLoanWrapper handles this for HTTP requests.
    DB.use(DefaultConnectionIdentifier) { conn =>
      MapperViews.getOrCreateSystemViewFromCbs(viewId)
      val view = ViewDefinition.findSystemView(viewId).openOrThrowException(s"System view $viewId not found")
      MapperViews.grantAccessToSystemView(bankId, accountId, view, user)
      conn.connection.commit()
    }
  }

  Feature("privateViewsUserCanAccess") {

    Scenario("User with no account access returns empty lists") {
      val (views, accountAccess) = MapperViews.privateViewsUserCanAccess(resourceUser1)
      views should be(empty)
      accountAccess should be(empty)
    }

    Scenario("User with one system view access returns that view") {
      createSystemViewAndGrantAccess(bankId1, accountId1, Constant.SYSTEM_OWNER_VIEW_ID, resourceUser1)

      val (views, accountAccess) = MapperViews.privateViewsUserCanAccess(resourceUser1)
      views.size should be(1)
      accountAccess.size should be(1)
      views.head.viewId.value should equal(Constant.SYSTEM_OWNER_VIEW_ID.toLowerCase())
      accountAccess.head.bankId should equal(bankId1.value)
      accountAccess.head.accountId should equal(accountId1.value)
    }

    Scenario("User with access to multiple accounts returns all views") {
      createSystemViewAndGrantAccess(bankId1, accountId1, Constant.SYSTEM_OWNER_VIEW_ID, resourceUser1)
      createSystemViewAndGrantAccess(bankId1, accountId2, Constant.SYSTEM_OWNER_VIEW_ID, resourceUser1)
      createSystemViewAndGrantAccess(bankId2, accountId3, Constant.SYSTEM_OWNER_VIEW_ID, resourceUser1)

      val (views, accountAccess) = MapperViews.privateViewsUserCanAccess(resourceUser1)
      accountAccess.size should be(3)
      // All three account access records should be for the owner view
      accountAccess.map(_.viewId).distinct should equal(List(Constant.SYSTEM_OWNER_VIEW_ID.toLowerCase()))
      // Check all bank/account combinations are present
      val bankAccountPairs = accountAccess.map(a => (a.bankId, a.accountId)).toSet
      bankAccountPairs should contain((bankId1.value, accountId1.value))
      bankAccountPairs should contain((bankId1.value, accountId2.value))
      bankAccountPairs should contain((bankId2.value, accountId3.value))
    }

    Scenario("User with multiple view types on the same account") {
      createSystemViewAndGrantAccess(bankId1, accountId1, Constant.SYSTEM_OWNER_VIEW_ID, resourceUser1)
      createSystemViewAndGrantAccess(bankId1, accountId1, "accountant", resourceUser1)

      val (views, accountAccess) = MapperViews.privateViewsUserCanAccess(resourceUser1)
      accountAccess.size should be(2)
      views.map(_.viewId.value).toSet should contain(Constant.SYSTEM_OWNER_VIEW_ID.toLowerCase())
      views.map(_.viewId.value).toSet should contain("accountant")
    }

    Scenario("Different users have independent access") {
      createSystemViewAndGrantAccess(bankId1, accountId1, Constant.SYSTEM_OWNER_VIEW_ID, resourceUser1)
      createSystemViewAndGrantAccess(bankId1, accountId2, Constant.SYSTEM_OWNER_VIEW_ID, resourceUser2)

      val (views1, access1) = MapperViews.privateViewsUserCanAccess(resourceUser1)
      val (views2, access2) = MapperViews.privateViewsUserCanAccess(resourceUser2)

      access1.size should be(1)
      access1.head.accountId should equal(accountId1.value)

      access2.size should be(1)
      access2.head.accountId should equal(accountId2.value)
    }

    Scenario("Views are distinct even when user has access to same view type across accounts") {
      createSystemViewAndGrantAccess(bankId1, accountId1, Constant.SYSTEM_OWNER_VIEW_ID, resourceUser1)
      createSystemViewAndGrantAccess(bankId1, accountId2, Constant.SYSTEM_OWNER_VIEW_ID, resourceUser1)

      val (views, accountAccess) = MapperViews.privateViewsUserCanAccess(resourceUser1)
      accountAccess.size should be(2)
      // The underlying ViewDefinition for system views is the same, so distinct views should be
      // based on the view definition, but with bank_id/account_id set per account access
      views.size should be >= 1
    }

    Scenario("Returned accountAccess entries match returned views") {
      createSystemViewAndGrantAccess(bankId1, accountId1, Constant.SYSTEM_OWNER_VIEW_ID, resourceUser1)
      createSystemViewAndGrantAccess(bankId1, accountId1, "accountant", resourceUser1)
      createSystemViewAndGrantAccess(bankId2, accountId2, Constant.SYSTEM_OWNER_VIEW_ID, resourceUser1)

      val (views, accountAccess) = MapperViews.privateViewsUserCanAccess(resourceUser1)
      accountAccess.size should be(3)

      // Every accountAccess view_id should correspond to a returned view
      val viewIds = views.map(_.viewId.value).toSet
      accountAccess.foreach { aa =>
        viewIds should contain(aa.viewId)
      }
    }
  }

  Feature("privateViewsUserCanAccessAtBank") {

    Scenario("Filters to only the requested bank") {
      createSystemViewAndGrantAccess(bankId1, accountId1, Constant.SYSTEM_OWNER_VIEW_ID, resourceUser1)
      createSystemViewAndGrantAccess(bankId2, accountId2, Constant.SYSTEM_OWNER_VIEW_ID, resourceUser1)

      val (views, accountAccess) = MapperViews.privateViewsUserCanAccessAtBank(resourceUser1, bankId1)
      accountAccess.size should be(1)
      accountAccess.head.bankId should equal(bankId1.value)
    }

    Scenario("Returns empty for bank with no access") {
      createSystemViewAndGrantAccess(bankId1, accountId1, Constant.SYSTEM_OWNER_VIEW_ID, resourceUser1)

      val (views, accountAccess) = MapperViews.privateViewsUserCanAccessAtBank(resourceUser1, bankId2)
      views should be(empty)
      accountAccess should be(empty)
    }
  }

  Feature("privateViewsUserCanAccessForAccount") {

    Scenario("Returns views for the specific account only") {
      createSystemViewAndGrantAccess(bankId1, accountId1, Constant.SYSTEM_OWNER_VIEW_ID, resourceUser1)
      createSystemViewAndGrantAccess(bankId1, accountId1, "accountant", resourceUser1)
      createSystemViewAndGrantAccess(bankId1, accountId2, Constant.SYSTEM_OWNER_VIEW_ID, resourceUser1)

      val views = MapperViews.privateViewsUserCanAccessForAccount(resourceUser1, BankIdAccountId(bankId1, accountId1))
      views.size should be(2)
      views.map(_.viewId.value).toSet should equal(Set(Constant.SYSTEM_OWNER_VIEW_ID.toLowerCase(), "accountant"))
    }

    Scenario("Returns empty for account with no access") {
      createSystemViewAndGrantAccess(bankId1, accountId1, Constant.SYSTEM_OWNER_VIEW_ID, resourceUser1)

      val views = MapperViews.privateViewsUserCanAccessForAccount(resourceUser1, BankIdAccountId(bankId1, accountId2))
      views should be(empty)
    }
  }
}
