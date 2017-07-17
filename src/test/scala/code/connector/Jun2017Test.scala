package code.connector

import code.accountholder.MapperAccountHolders
import code.bankconnectors.{Connector, InboundAccountJune2017, KafkaMappedConnector_vJun2017}
import code.model.{AccountId, BankId, BankIdAccountId}
import code.model.dataAccess.{ViewImpl, ViewPrivileges}
import code.setup.{DefaultUsers, ServerSetup}
import code.views.MapperViews
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper.By

class Jun2017Test extends ServerSetup with DefaultUsers {

  
  override def beforeAll() = {
    super.beforeAll()
    Connector.connector.default.set(MockedCardConnector)
    ViewImpl.bulkDelete_!!()
    MapperAccountHolders.bulkDelete_!!()
  }
  
  override def afterEach() = {
    super.afterEach()
    Connector.connector.default.set(Connector.buildOne)
    ViewImpl.bulkDelete_!!()
    MapperAccountHolders.bulkDelete_!!()
  }
  
  val bankIdAccountId = MockedCardConnector.bankIdAccountId
  val bankIdAccountId2 = MockedCardConnector.bankIdAccountId2
  
  feature("Test the updateUserAccountViews method") {
    scenario("we facke the output from getBankAccounts(), and check the functions there") {
      
      When("We call the method use resourceUser1")
      KafkaMappedConnector_vJun2017.updateUserAccountViews(resourceUser1)
  
      Then("We check the accountHolders")
      var accountholder1 = MapperAccountHolders.getAccountHolders(bankIdAccountId.bankId, bankIdAccountId.accountId)
      var accountholder2 = MapperAccountHolders.getAccountHolders(bankIdAccountId2.bankId, bankIdAccountId2.accountId)
      var accountholders = MapperAccountHolders.findAll()
      accountholder1.head.resourceUserId should equal(resourceUser1.resourceUserId)
      accountholder2.head.resourceUserId should equal(resourceUser1.resourceUserId)
      accountholders.length should equal(2)
  
      Then("We check the views")  //"Owner"::"Public" :: "Accountant" :: "Auditor"
      val allViewsForAccount1 = MapperViews.views(bankIdAccountId)
      val allViewsForAccount2 = MapperViews.views(bankIdAccountId)
      val allViews = ViewImpl.findAll()
      allViewsForAccount1.toString().contains("owner") should equal(true)
      allViewsForAccount1.toString().contains("public") should equal(true)
      allViewsForAccount1.toString().contains("accountant") should equal(true)
      allViewsForAccount1.toString().contains("auditor") should equal(true)
      allViewsForAccount2.toString().contains("owner") should equal(true)
      allViewsForAccount2.toString().contains("public") should equal(true)
      allViewsForAccount2.toString().contains("accountant") should equal(true)
      allViewsForAccount2.toString().contains("auditor") should equal(true)
      allViews.length should equal(8)
      
  
      Then("We check the ViewPrivileges")
      val numberOfPrivileges = ViewPrivileges.findAll().length
      numberOfPrivileges should equal(8)
      
    }

  }



}
