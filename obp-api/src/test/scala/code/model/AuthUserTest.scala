package code.model

import code.UserRefreshes.MappedUserRefreshes
import code.accountholders.MapperAccountHolders
import code.bankconnectors.Connector
import code.connector.MockedJune2017Connector
import code.model.dataAccess.{AuthUser, ViewImpl, ViewPrivileges}
import code.setup.{DefaultUsers, PropsReset, ServerSetup}
import code.views.MapperViews
import code.views.system.{AccountAccess, ViewDefinition}
import com.openbankproject.commons.model.{InboundAccount, InboundAccountCommons}
import net.liftweb.mapper.{By, PreCache}

import scala.collection.immutable.List
import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
  * Created by zhanghongwei on 17/07/2017.
  */
class AuthUserTest extends ServerSetup with DefaultUsers {
  
  override def beforeAll() = {
    super.beforeAll()
    Connector.connector.default.set(MockedJune2017Connector)
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
  
  val bankIdAccountId1 = MockedJune2017Connector.bankIdAccountId1
  val bankIdAccountId2 = MockedJune2017Connector.bankIdAccountId2

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
      viewsToGenerate = "Owner" :: Nil,
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
      viewsToGenerate = "Owner" :: Nil,
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
      viewsToGenerate = "Owner" :: Nil,
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
      viewsToGenerate = "Owner" :: Nil,
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
      allViewsForAccount1.toString().contains("owner") should equal(true)
      allViewsForAccount1.toString().contains("_public") should equal(true)
      allViewsForAccount1.toString().contains("accountant") should equal(true)
      allViewsForAccount1.toString().contains("auditor") should equal(true)
      allViewsForAccount2.toString().contains("owner") should equal(true)
      allViewsForAccount2.toString().contains("_public") should equal(true)
      allViewsForAccount2.toString().contains("accountant") should equal(true)
      allViewsForAccount2.toString().contains("auditor") should equal(true)
      allViews.length should equal(5) // 3 system views + 2 custom views

      Then("We check the AccountAccesses")
      val numberOfAccountAccesses = AccountAccess.findAll().length
      numberOfAccountAccesses should equal(8) 

    }
  }
  
  feature("Test the refreshViewsAccountAccessAndHolders method") {
    scenario("Test one account views,account access and account holder") {

      When("1rd Step: no accounts in the List")
      AuthUser.refreshViewsAccountAccessAndHolders(resourceUser1, accountsHeldEmpty)

      Then("We check the accountHolders")
      accountholder1.size should be(0)
      accountholder2.size should be(0)

      Then("We check the views, only support the system views")
      allViewsForAccount1.map(_.viewId.value) should equal(List())
      allViewsForAccount2.map(_.viewId.value) should equal(List())

      Then("We check the AccountAccesses")
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
      allViewsForAccount1.map(_.viewId.value) should equal(List("owner")) //TODO. check this, only one account in the list, why two `owner` here.
      allViewsForAccount2.map(_.viewId.value) should equal(List("owner"))

      Then("We check the AccountAccesses")
      account1Access.length should equal(1)
      account2Access.length should equal(0)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (1)

      //3rd: we remove the accounts 
      Then("we delete the account")
      val accountsHeld = List()
      AuthUser.refreshViewsAccountAccessAndHolders(resourceUser1, accountsHeld)

      Then("We check the accountHolders")
      accountholder1.size should be(0)
      accountholder2.size should be(0)

      Then("We check the views, only support the system view. both accounts should have the `owner` view.")
      allViewsForAccount1.map(_.viewId.value) should equal(List("owner"))
      allViewsForAccount2.map(_.viewId.value) should equal(List("owner"))

      Then("We check the AccountAccesses")
      account1Access.length should equal(0)
      account2Access.length should equal(0)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (1)

    }
    
    
    
    scenario("Test two accounts views,account access and account holder") {

      //1st block, we prepare one account
      When("first we have 1st new account in the accountsHeld")
      AuthUser.refreshViewsAccountAccessAndHolders(resourceUser1, account1Held)

      Then("We check the accountHolders")
      accountholder1.size should be(1)
      accountholder2.size should be(0)

      Then("We check the views, only support the system views")
      allViewsForAccount1.map(_.viewId.value) should equal(List("owner"))
      allViewsForAccount2.map(_.viewId.value) should equal(List("owner"))

      Then("We check the AccountAccesses")
      account1Access.length should equal(1)
      account2Access.length should equal(0)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (1)

      //2rd block, we prepare second account
      Then("first we have two accounts in the accountsHeld")
      AuthUser.refreshViewsAccountAccessAndHolders(resourceUser1, twoAccountsHeld)

      Then("We check the accountHolders")
      accountholder1.size should be(1)
      accountholder2.size should be(1)

      Then("We check the views, only support the system views")
      allViewsForAccount1.map(_.viewId.value) should equal(List("owner"))
      allViewsForAccount2.map(_.viewId.value) should equal(List("owner"))

      Then("We check the AccountAccesses")
      account1Access.length should equal(1)
      account2Access.length should equal(1)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (1)
        

      //3rd block, we removed the 2rd account, only have 1st account there.
      When("we delete 2rd account, only have 1st account in the accountsHeld")
      AuthUser.refreshViewsAccountAccessAndHolders(resourceUser1, account1Held)

      Then("We check the accountHolders")
      accountholder1.size should be(1)
      accountholder2.size should be(0)

      Then("We check the views, only support the system views")
      allViewsForAccount1.map(_.viewId.value) should equal(List("owner"))
      allViewsForAccount2.map(_.viewId.value) should equal(List("owner"))

      Then("We check the AccountAccesses")
      account1Access.length should equal(1)
      account2Access.length should equal(0)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (1)
      

      //4th, we do not have any accounts 
      When("we delete all accounts, no account in the accountsHeld")
      AuthUser.refreshViewsAccountAccessAndHolders(resourceUser1, accountsHeldEmpty)

      Then("We check the accountHolders")
      accountholder1.size should be(0)
      accountholder2.size should be(0)

      Then("We check the views, only support the system views")
      allViewsForAccount1.map(_.viewId.value) should equal(List("owner"))
      allViewsForAccount2.map(_.viewId.value) should equal(List("owner"))

      Then("We check the AccountAccesses")
      account1Access.length should equal(0)
      account2Access.length should equal(0)

      Then("We check the MappedUserRefreshes table")
      MappedUserRefreshes.findAll().length should be (1)

    }
  }
  
}
