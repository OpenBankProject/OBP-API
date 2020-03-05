package code.model

import code.accountholders.MapperAccountHolders
import code.bankconnectors.Connector
import code.connector.MockedJune2017Connector
import code.model.dataAccess.{AuthUser, ViewImpl, ViewPrivileges}
import code.setup.{DefaultUsers, ServerSetup}
import code.views.MapperViews
import code.views.system.{AccountAccess, ViewDefinition}

/**
  * Created by zhanghongwei on 17/07/2017.
  */
class AuthUserTest extends ServerSetup with DefaultUsers {
  
  override def beforeAll() = {
    super.beforeAll()
    Connector.connector.default.set(MockedJune2017Connector)
    ViewDefinition.bulkDelete_!!()
    MapperAccountHolders.bulkDelete_!!()
  }
  
  override def afterEach() = {
    super.afterEach()
    Connector.connector.default.set(Connector.buildOne)
    ViewDefinition.bulkDelete_!!()
    MapperAccountHolders.bulkDelete_!!()
  }
  
  val bankIdAccountId = MockedJune2017Connector.bankIdAccountId
  val bankIdAccountId2 = MockedJune2017Connector.bankIdAccountId2
  
  
  feature("Test the updateUserAccountViews method") {
    scenario("we fack the output from getBankAccounts(), and check the functions there") {

      When("We call the method use resourceUser1")
      AuthUser.updateUserAccountViews(resourceUser1, None)

      Then("We check the accountHolders")
      var accountholder1 = MapperAccountHolders.getAccountHolders(bankIdAccountId.bankId, bankIdAccountId.accountId)
      var accountholder2 = MapperAccountHolders.getAccountHolders(bankIdAccountId2.bankId, bankIdAccountId2.accountId)
      var accountholders = MapperAccountHolders.findAll()
      accountholder1.head.userPrimaryKey should equal(resourceUser1.userPrimaryKey)
      accountholder2.head.userPrimaryKey should equal(resourceUser1.userPrimaryKey)
      accountholders.length should equal(2)

      Then("We check the views")  //"Owner"::"_Public" :: "Accountant" :: "Auditor"
      val allViewsForAccount1 = MapperViews.availableViewsForAccount(bankIdAccountId)
      val allViewsForAccount2 = MapperViews.availableViewsForAccount(bankIdAccountId)
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
  
}
