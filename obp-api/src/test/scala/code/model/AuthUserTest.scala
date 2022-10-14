package code.model

import code.UserRefreshes.MappedUserRefreshes
import code.accountholders.MapperAccountHolders
import code.api.Constant.{SYSTEM_OWNER_VIEW_ID, SYSTEM_STAGE_ONE_VIEW_ID, SYSTEM_STANDARD_VIEW_ID}
import code.bankconnectors.Connector
import code.connector.MockedCbsConnector
import code.model.dataAccess.AuthUser
import code.setup.{DefaultUsers, PropsReset, ServerSetup}
import code.views.MapperViews
import code.views.system.{AccountAccess, ViewDefinition}
import com.openbankproject.commons.model.InboundAccountCommons
import net.liftweb.mapper.By

import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
  * Created by zhanghongwei on 17/07/2017.
  */
class AuthUserTest extends ServerSetup with DefaultUsers with PropsReset{
  
  
  override def beforeAll() = {
    super.beforeAll()
    Connector.connector.default.set(MockedCbsConnector)
    ViewDefinition.bulkDelete_!!()
    MapperAccountHolders.bulkDelete_!!()
    AccountAccess.bulkDelete_!!()
    MappedUserRefreshes.bulkDelete_!!()
  }
  
  override def afterEach() = {
    super.afterEach()
    Connector.connector.default.set(Connector.buildOne)
    ViewDefinition.bulkDelete_!!()
    MapperAccountHolders.bulkDelete_!!()
    AccountAccess.bulkDelete_!!()
    MappedUserRefreshes.bulkDelete_!!()
  }
  
  val bankIdAccountId1 = MockedCbsConnector.bankIdAccountId
  val bankIdAccountId2 = MockedCbsConnector.bankIdAccountId2

  def account1Access = AccountAccess.findAll(
    By(AccountAccess.user_fk, resourceUser1.userPrimaryKey.value),
    By(AccountAccess.bank_id, bankIdAccountId1.bankId.value),
    By(AccountAccess.account_id, bankIdAccountId1.accountId.value),
  )
  
  def account2Access = AccountAccess.findAll(
    By(AccountAccess.user_fk, resourceUser1.userPrimaryKey.value),
    By(AccountAccess.bank_id, bankIdAccountId2.bankId.value),
    By(AccountAccess.account_id, bankIdAccountId2.accountId.value),
  )
  
  def account1AccessUser2 = AccountAccess.findAll(
    By(AccountAccess.user_fk, resourceUser2.userPrimaryKey.value),
    By(AccountAccess.bank_id, bankIdAccountId1.bankId.value),
    By(AccountAccess.account_id, bankIdAccountId1.accountId.value),
  )
  
  def account2AccessUser2 = AccountAccess.findAll(
    By(AccountAccess.user_fk, resourceUser2.userPrimaryKey.value),
    By(AccountAccess.bank_id, bankIdAccountId2.bankId.value),
    By(AccountAccess.account_id, bankIdAccountId2.accountId.value),
  )

  def accountholder1 = MapperAccountHolders.getAccountHolders(bankIdAccountId1.bankId, bankIdAccountId1.accountId)
  def accountholder2 = MapperAccountHolders.getAccountHolders(bankIdAccountId2.bankId, bankIdAccountId2.accountId)

  def allViewsForAccount1 = MapperViews.availableViewsForAccount(bankIdAccountId1)
  def allViewsForAccount2 = MapperViews.availableViewsForAccount(bankIdAccountId2)
  
  def mappedUserRefreshesLength= MappedUserRefreshes.findAll().length


  val accountsHeldEmpty = List()
  
  val account1Held = List(
    InboundAccountCommons(
      bankId = bankIdAccountId1.bankId.value,
      accountId = bankIdAccountId1.accountId.value,
      viewsToGenerate = SYSTEM_STANDARD_VIEW_ID :: Nil,
      branchId = "",
      accountNumber = "",
      accountType = "",
      balanceAmount = "",
      balanceCurrency = "",
      owners = List(""),
      bankRoutingScheme = "",
      bankRoutingAddress = "",
      branchRoutingScheme = "",
      branchRoutingAddress = "",
      accountRoutingScheme = "",
      accountRoutingAddress = ""
    )
  )

  val account1HeldWithStageOneView = List(
    InboundAccountCommons(
      bankId = bankIdAccountId1.bankId.value,
      accountId = bankIdAccountId1.accountId.value,
      viewsToGenerate = SYSTEM_STAGE_ONE_VIEW_ID :: Nil,
      branchId = "",
      accountNumber = "",
      accountType = "",
      balanceAmount = "",
      balanceCurrency = "",
      owners = List(""),
      bankRoutingScheme = "",
      bankRoutingAddress = "",
      branchRoutingScheme = "",
      branchRoutingAddress = "",
      accountRoutingScheme = "",
      accountRoutingAddress = ""
    )
  )
  
  val account1HeldWithBothViews = List(
    InboundAccountCommons(
      bankId = bankIdAccountId1.bankId.value,
      accountId = bankIdAccountId1.accountId.value,
      viewsToGenerate = SYSTEM_STAGE_ONE_VIEW_ID :: SYSTEM_STANDARD_VIEW_ID:: Nil,
      branchId = "",
      accountNumber = "",
      accountType = "",
      balanceAmount = "",
      balanceCurrency = "",
      owners = List(""),
      bankRoutingScheme = "",
      bankRoutingAddress = "",
      branchRoutingScheme = "",
      branchRoutingAddress = "",
      accountRoutingScheme = "",
      accountRoutingAddress = ""
    )
  )
  
  val account1HeldWithEmptyView = List(
    InboundAccountCommons(
      bankId = bankIdAccountId1.bankId.value,
      accountId = bankIdAccountId1.accountId.value,
      viewsToGenerate = Nil,
      branchId = "",
      accountNumber = "",
      accountType = "",
      balanceAmount = "",
      balanceCurrency = "",
      owners = List(""),
      bankRoutingScheme = "",
      bankRoutingAddress = "",
      branchRoutingScheme = "",
      branchRoutingAddress = "",
      accountRoutingScheme = "",
      accountRoutingAddress = ""
    )
  )
  
  val account2Held = List(
    InboundAccountCommons(
      bankId = bankIdAccountId2.bankId.value,
      accountId = bankIdAccountId2.accountId.value,
      viewsToGenerate = SYSTEM_STANDARD_VIEW_ID :: Nil,
      branchId = "",
      accountNumber = "",
      accountType = "",
      balanceAmount = "",
      balanceCurrency = "",
      owners = List(""),
      bankRoutingScheme = "",
      bankRoutingAddress = "",
      branchRoutingScheme = "",
      branchRoutingAddress = "",
      accountRoutingScheme = "",
      accountRoutingAddress = ""
    )
  )
  
  val twoAccountsHeld = List(
    InboundAccountCommons(
      bankId = bankIdAccountId1.bankId.value,
      accountId = bankIdAccountId1.accountId.value,
      viewsToGenerate = SYSTEM_STANDARD_VIEW_ID :: Nil,
      branchId = "",
      accountNumber = "",
      accountType = "",
      balanceAmount = "",
      balanceCurrency = "",
      owners = List(""),
      bankRoutingScheme = "",
      bankRoutingAddress = "",
      branchRoutingScheme = "",
      branchRoutingAddress = "",
      accountRoutingScheme = "",
      accountRoutingAddress = ""
    ),
    InboundAccountCommons(
      bankId = bankIdAccountId2.bankId.value,
      accountId = bankIdAccountId2.accountId.value,
      viewsToGenerate = SYSTEM_STANDARD_VIEW_ID :: Nil,
      branchId = "",
      accountNumber = "",
      accountType = "",
      balanceAmount = "",
      balanceCurrency = "",
      owners = List(""),
      bankRoutingScheme = "",
      bankRoutingAddress = "",
      branchRoutingScheme = "",
      branchRoutingAddress = "",
      accountRoutingScheme = "",
      accountRoutingAddress = ""
    )
  )
  
  
  feature("Test the refreshUser method") {
    scenario("we fake the output from getBankAccounts(), and check the functions there") {

      When("We call the method use resourceUser1")
      val result = Await.result(AuthUser.refreshUser(resourceUser1, None), Duration.Inf)

      Then("We check the accountHolders")
      var accountholder1 = MapperAccountHolders.getAccountHolders(bankIdAccountId1.bankId, bankIdAccountId1.accountId)
      var accountholder2 = MapperAccountHolders.getAccountHolders(bankIdAccountId2.bankId, bankIdAccountId2.accountId)
      var accountholders = MapperAccountHolders.findAll()
      accountholder1.head.userPrimaryKey should equal(resourceUser1.userPrimaryKey)
      accountholder2.head.userPrimaryKey should equal(resourceUser1.userPrimaryKey)
      accountholders.length should equal(2)

      Then("We check the views") 
      val allViewsForAccount1 = MapperViews.availableViewsForAccount(bankIdAccountId1)
      val allViewsForAccount2 = MapperViews.availableViewsForAccount(bankIdAccountId2)
      val allViews = ViewDefinition.findAll()
      allViewsForAccount1.toString().contains(SYSTEM_STANDARD_VIEW_ID) should equal(true)
      allViewsForAccount1.toString().contains(SYSTEM_OWNER_VIEW_ID) should equal(true)
      allViewsForAccount1.toString().contains("_public") should equal(true)
      allViewsForAccount1.toString().contains("accountant") should equal(true)
      allViewsForAccount1.toString().contains("auditor") should equal(true)
      allViewsForAccount2.toString().contains(SYSTEM_STANDARD_VIEW_ID) should equal(true)
      allViewsForAccount2.toString().contains("_public") should equal(true)
      allViewsForAccount2.toString().contains("accountant") should equal(true)
      allViewsForAccount2.toString().contains("auditor") should equal(true)
      allViews.length should equal(6) // 3 system views + 2 custom views

      Then("We check the AccountAccess")
      val numberOfAccountAccess = AccountAccess.findAll().length
      numberOfAccountAccess should equal(10) 

    }
  }
  
  feature("Test the refreshViewsAccountAccessAndHolders method") {
    scenario("Test one account views,account access and account holder") {
      
      When("1st Step: no accounts in the List")
      AuthUser.refreshViewsAccountAccessAndHolders(resourceUser1, accountsHeldEmpty)

      Then("We check the accountHolders")
      accountholder1.size should be(0)
      accountholder2.size should be(0)

      Then("There is not system views at all in the ViewDefinition table, so both should be Empty")
      allViewsForAccount1.map(_.viewId.value) should equal(List())
      allViewsForAccount2.map(_.viewId.value) should equal(List())

      Then("We check the AccountAccess")
      account1Access.length should equal(0)
      account2Access.length should equal(0)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (0)

      Then("2rd Step: there is 1st account in the List")
      AuthUser.refreshViewsAccountAccessAndHolders(resourceUser1, account1Held)

      Then("We check the accountHolders")
      accountholder1.size should be(1)
      accountholder2.size should be(0)

      Then("We check the views, only support the system view. both accounts should have the `owner` view.")
      allViewsForAccount1.map(_.viewId.value) should equal(List(SYSTEM_STANDARD_VIEW_ID))
      allViewsForAccount2.map(_.viewId.value) should equal(List(SYSTEM_STANDARD_VIEW_ID))

      Then("We check the AccountAccess")
      account1Access.length should equal(1)
      account2Access.length should equal(0)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (1)

      Then("3rd: we remove the accounts ")
      val accountsHeld = List()
      AuthUser.refreshViewsAccountAccessAndHolders(resourceUser1, accountsHeld)

      Then("We check the accountHolders")
      accountholder1.size should be(0)
      accountholder2.size should be(0)

      Then("We check the views, only support the system view. both accounts should have the `owner` view.")
      allViewsForAccount1.map(_.viewId.value) should equal(List(SYSTEM_STANDARD_VIEW_ID))
      allViewsForAccount2.map(_.viewId.value) should equal(List(SYSTEM_STANDARD_VIEW_ID))

      Then("We check the AccountAccess")
      account1Access.length should equal(0)
      account2Access.length should equal(0)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (1)

    }
    
    scenario("Test two accounts views,account access and account holder") {

      When("1rd Step: no accounts in the List")
      AuthUser.refreshViewsAccountAccessAndHolders(resourceUser1, accountsHeldEmpty)

      Then("We check the accountHolders")
      accountholder1.size should be(0)
      accountholder2.size should be(0)

      Then("There is not system views at all in the ViewDefinition table, so both should be Empty")
      allViewsForAccount1.map(_.viewId.value) should equal(List())
      allViewsForAccount2.map(_.viewId.value) should equal(List())

      Then("We check the AccountAccess")
      account1Access.length should equal(0)
      account2Access.length should equal(0)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (0)
      
      When("2rd block, we prepare one account")
      AuthUser.refreshViewsAccountAccessAndHolders(resourceUser1, account1Held)

      Then("We check the accountHolders")
      accountholder1.size should be(1)
      accountholder2.size should be(0)

      Then("We check the views, only support the system views")
      allViewsForAccount1.map(_.viewId.value) should equal(List(SYSTEM_STANDARD_VIEW_ID))
      allViewsForAccount2.map(_.viewId.value) should equal(List(SYSTEM_STANDARD_VIEW_ID))

      Then("We check the AccountAccess")
      account1Access.length should equal(1)
      account2Access.length should equal(0)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (1)

      Then("3rd:  we have two accounts in the accountsHeld")
      AuthUser.refreshViewsAccountAccessAndHolders(resourceUser1, twoAccountsHeld)

      Then("We check the accountHolders")
      accountholder1.size should be(1)
      accountholder2.size should be(1)

      Then("We check the views, only support the system views")
      allViewsForAccount1.map(_.viewId.value) should equal(List(SYSTEM_STANDARD_VIEW_ID))
      allViewsForAccount2.map(_.viewId.value) should equal(List(SYSTEM_STANDARD_VIEW_ID))

      Then("We check the AccountAccess")
      account1Access.length should equal(1)
      account2Access.length should equal(1)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (1)
        

      When("4th, we removed the 1rd account, only have 2rd account there.")
      AuthUser.refreshViewsAccountAccessAndHolders(resourceUser1, account2Held)

      Then("We check the accountHolders")
      accountholder1.size should be(0)
      accountholder2.size should be(1)

      Then("We check the views, only support the system views")
      allViewsForAccount1.map(_.viewId.value) should equal(List(SYSTEM_STANDARD_VIEW_ID))
      allViewsForAccount2.map(_.viewId.value) should equal(List(SYSTEM_STANDARD_VIEW_ID))

      Then("We check the AccountAccess")
      account1Access.length should equal(0)
      account2Access.length should equal(1)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (1)
      
      When("5th, we do not have any accounts ")
      AuthUser.refreshViewsAccountAccessAndHolders(resourceUser1, accountsHeldEmpty)

      Then("We check the accountHolders")
      accountholder1.size should be(0)
      accountholder2.size should be(0)

      Then("We check the views, only support the system views")
      allViewsForAccount1.map(_.viewId.value) should equal(List(SYSTEM_STANDARD_VIEW_ID))
      allViewsForAccount2.map(_.viewId.value) should equal(List(SYSTEM_STANDARD_VIEW_ID))

      Then("We check the AccountAccess")
      account1Access.length should equal(0)
      account2Access.length should equal(0)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (1)

    }

    scenario("Test two users, account views,account access and account holder") {

      When("1st Step: no accounts in the List")
      AuthUser.refreshViewsAccountAccessAndHolders(resourceUser1, accountsHeldEmpty)

      Then("We check the accountHolders")
      accountholder1.size should be(0)
      accountholder2.size should be(0)

      Then("There is not system views at all in the ViewDefinition table, so both should be Empty")
      allViewsForAccount1.map(_.viewId.value) should equal(List())
      allViewsForAccount2.map(_.viewId.value) should equal(List())

      Then("We check the AccountAccess")
      account1Access.length should equal(0)
      account2Access.length should equal(0)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (0)

      Then("2rd Step: 1st user and  1st account in the List")
      AuthUser.refreshViewsAccountAccessAndHolders(resourceUser1, account1Held)

      Then("We check the accountHolders")
      accountholder1.size should be(1)
      accountholder2.size should be(0)

      Then("We check the views, only support the system view. both accounts should have the `owner` view.")
      allViewsForAccount1.map(_.viewId.value) should equal(List(SYSTEM_STANDARD_VIEW_ID))
      allViewsForAccount2.map(_.viewId.value) should equal(List(SYSTEM_STANDARD_VIEW_ID))

      Then("We check the AccountAccess")
      account1Access.length should equal(1)
      account2Access.length should equal(0)
      account1AccessUser2.length should equal(0)
      account2AccessUser2.length should equal(0)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (1)


      Then("3rd Step: 2rd user and 1st account in the List")
      AuthUser.refreshViewsAccountAccessAndHolders(resourceUser2, account1Held)

      Then("We check the accountHolders")
      accountholder1.size should be(2)
      accountholder2.size should be(0)

      Then("We check the views, only support the system view. both accounts should have the `owner` view.")
      allViewsForAccount1.map(_.viewId.value) should equal(List(SYSTEM_STANDARD_VIEW_ID))
      allViewsForAccount2.map(_.viewId.value) should equal(List(SYSTEM_STANDARD_VIEW_ID))

      Then("We check the AccountAccess")
      account1Access.length should equal(1)
      account2Access.length should equal(0)
      account1AccessUser2.length should equal(1)
      account2AccessUser2.length should equal(0)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (2)

      When("4th, User1 we do not have any accounts ")
      AuthUser.refreshViewsAccountAccessAndHolders(resourceUser1, accountsHeldEmpty)

      Then("We check the accountHolders")
      accountholder1.size should be(1)
      accountholder2.size should be(0)

      Then("We check the views, only support the system views")
      allViewsForAccount1.map(_.viewId.value) should equal(List(SYSTEM_STANDARD_VIEW_ID))
      allViewsForAccount2.map(_.viewId.value) should equal(List(SYSTEM_STANDARD_VIEW_ID))

      Then("We check the AccountAccess")
      account1Access.length should equal(0)
      account2Access.length should equal(0)
      account1AccessUser2.length should equal(1)
      account2AccessUser2.length should equal(0)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (2)

    }
    
    scenario("Test one user, but change the `viewsToGenerate` from `StageOne` to `Owner`, and check all the view accesses. ") {

      When("1st Step: we create the `StageOneView` ")
      AuthUser.refreshViewsAccountAccessAndHolders(resourceUser1, account1HeldWithStageOneView)

      Then("We check the accountHolders")
      accountholder1.size should be(1)
      
      Then("There is system view `StageOne`")
      allViewsForAccount1.map(_.viewId.value) should equal(List(SYSTEM_STAGE_ONE_VIEW_ID))

      Then("We check the AccountAccess")
      account1Access.length should be (1)
      account1Access.map(_.view_id.get).contains(SYSTEM_STAGE_ONE_VIEW_ID) should be (true)
      
      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (1)

      Then("2rd Step: we create the `Owner` and remove the `StageOne` view")
      AuthUser.refreshViewsAccountAccessAndHolders(resourceUser1, account1Held)

      Then("We check the accountHolders")
      accountholder1.size should be(1)

      Then("We check the views, there should be two system views: Stage")
      allViewsForAccount1.length should be(2)
      allViewsForAccount1.map(_.viewId.value) contains (SYSTEM_STANDARD_VIEW_ID) should be (true)
      allViewsForAccount1.map(_.viewId.value) contains (SYSTEM_STAGE_ONE_VIEW_ID) should be (true)

      Then("We check the AccountAccess")
      account1Access.length should equal(1)
      account1Access.map(_.view_id.get).contains(SYSTEM_STANDARD_VIEW_ID) should be (true)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (1)

      Then("3rd Step: we removed the all the views ")
      AuthUser.refreshViewsAccountAccessAndHolders(resourceUser1, account1HeldWithEmptyView)

      Then("We check the AccountAccess, we can only remove the StageOne access, not owner view, if use is the account holder, we can not revoke the access")
      account1Access.length should equal(0)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (1)

      Then("4th Step: we create both the views: owner and StageOne ")
      AuthUser.refreshViewsAccountAccessAndHolders(resourceUser1, account1HeldWithBothViews)

      Then("We check the accountHolders")
      accountholder1.size should be(1)

      Then("We check the views, only support the system view. both accounts should have the `owner` view.")
      allViewsForAccount1.length should be(2)
      allViewsForAccount1.map(_.viewId.value) contains (SYSTEM_STANDARD_VIEW_ID) should be (true)
      allViewsForAccount1.map(_.viewId.value) contains (SYSTEM_STAGE_ONE_VIEW_ID) should be (true)

      Then("We check the AccountAccess")
      account1Access.length should equal(2)
      account1Access.map(_.view_id.get).contains(SYSTEM_STANDARD_VIEW_ID) should be (true)
      account1Access.map(_.view_id.get).contains(SYSTEM_STAGE_ONE_VIEW_ID) should be (true)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (1)

      
      Then("5th Step: we removed all the  views  ")
      AuthUser.refreshViewsAccountAccessAndHolders(resourceUser1, account1HeldWithEmptyView)

      Then("We check the accountHolders")
      accountholder1.size should be(1)

      Then("We check the AccountAccess")
      account1Access.length should equal(0)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (1)

    }
  }
  
}
