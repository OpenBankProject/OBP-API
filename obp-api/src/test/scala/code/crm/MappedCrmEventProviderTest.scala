package code.crm

import java.util.Date

import code.setup.{DefaultUsers, ServerSetup}

class MappedCrmEventProviderTest extends ServerSetup with DefaultUsers {

  override def beforeAll() = {
    super.beforeAll()
    DoobieCrmEventProvider.bulkDelete()
  }

  override def afterEach() = {
    super.afterEach()
    DoobieCrmEventProvider.bulkDelete()
  }

  def createCrmEvent1() = DoobieCrmEventProvider.createEvent(
    bankId = testBankId1.value,
    crmEventId = "ASDFIUHUIUYFD444",
    category = "Category X",
    detail = "Call about mortgage",
    channel = "PHONE",
    actualDate = new Date(12340000),
    customerName = "",
    customerNumber = "",
    userIdPrimaryKey = resourceUser1.userPrimaryKey.value,
    scheduledDate = new Date(12340000),
    result = "No answer")

  // Different bank and different user
  def createCrmEvent2() = DoobieCrmEventProvider.createEvent(
    bankId = testBankId2.value,
    crmEventId = "YYASDFYYGYHUIURR",
    category = "Category X",
    detail = "Another Call about mortgage",
    channel = "PHONE",
    actualDate = new Date(12340000),
    customerName = "",
    customerNumber = "",
    userIdPrimaryKey = resourceUser2.userPrimaryKey.value,
    scheduledDate = new Date(12340000),
    result = "No answer again")

  def createCrmEvent3() = DoobieCrmEventProvider.createEvent(
    bankId = testBankId2.value,
    crmEventId = "HY677SRDD",
    category = "Category Y",
    detail = "Want to save some money?",
    channel = "PHONE",
    actualDate = new Date(12340000),
    customerName = "",
    customerNumber = "",
    userIdPrimaryKey = resourceUser2.userPrimaryKey.value,
    scheduledDate = new Date(12340000),
    result = "Yes, is coming into the Branch")

  Feature("Getting crm events") {

    Scenario("No crm events exist for user and we try to get them") {
      Given("No MappedCrmEvent exists for a user (any bank)")
      DoobieCrmEventProvider.getCrmEvent(CrmEvent.CrmEventId("no-such-id")).isDefined should equal(false)

      When("We try to get it by bank and user")
      val foundOpt = DoobieCrmEventProvider.getCrmEvents(testBankId1, resourceUser2)
      val foundList = foundOpt.get

      Then("We don't")
      foundList.size should equal(0)
    }


    Scenario("A CrmEvent exists for user and we try to get it") {
      val createdThing1 = createCrmEvent1()
      Given("MappedCrmEvent exists for a user on a bank")
      DoobieCrmEventProvider.getCrmEvents(testBankId1, resourceUser1).exists(_.nonEmpty) should equal(true)

      When("We try to get it by bank and user")
      val foundOpt = DoobieCrmEventProvider.getCrmEvents(testBankId1, resourceUser1)

      Then("We do")
      foundOpt.isDefined should equal(true)

      And("It is the right thing")
      val foundThing = foundOpt.get
      foundThing(0) should equal(createdThing1)
    }


    Scenario("No crm events exist for a bank and we try to get them") {
      Given("No MappedCrmEvent exists for a bank")
      DoobieCrmEventProvider.getCrmEvents(testBankId1).exists(_.nonEmpty) should equal(false)

      When("We create on another bank")
      createCrmEvent2()

      When("We try to get it by bank")
      val foundOpt = DoobieCrmEventProvider.getCrmEvents(testBankId1)
      val foundList = foundOpt.get

      Then("We don't")
      foundList.size should equal(0)
    }

    Scenario("CrmEvents exist for bank and user and we try to get them") {

      createCrmEvent2()
      createCrmEvent3()

      Given("MappedCrmEvent exists for a user")
      DoobieCrmEventProvider.getCrmEvents(testBankId2, resourceUser2).exists(_.nonEmpty) should equal(true)

      When("We try to get them")
      val foundOpt = DoobieCrmEventProvider.getCrmEvents(testBankId2, resourceUser2)

      Then("We do")
      foundOpt.isDefined should equal(true)

      And("There should be two")
      val foundThings = foundOpt.get
      foundThings.size should equal(2)

      // TODO Check they are the same

    }


  }


}
