package code.crm

import java.util.Date

import code.api.DefaultUsers
import code.api.ServerSetup
import code.model.BankId
import net.liftweb.mapper.By

class MappedCrmEventProviderTest extends ServerSetup with DefaultUsers {

  override def beforeAll() = {
    super.beforeAll()
    MappedCrmEvent.bulkDelete_!!()
  }

  override def afterEach() = {
    super.afterEach()
    MappedCrmEvent.bulkDelete_!!()
  }

  val testBankId1 = BankId("bank1")
  val testBankId2 = BankId("bank2")
  val testBankIdNoEvents = BankId("bank-no-events")

  def createCrmEvent1() = MappedCrmEvent.create
    .mCrmEventId("ASDFIUHUIUYFD444")
    .mBankId(testBankId1.value)
    .mUserId(obpuser1)
    .mScheduledDate(new Date(12340000))
    .mActualDate(new Date(12340000))
    .mChannel("PHONE")
    .mDetail("Call about mortgage")
    .mResult("No answer")
    .mCategory("Category X")
    .saveMe()

  // Different bank and different user
  def createCrmEvent2() = MappedCrmEvent.create
    .mCrmEventId("YYASDFYYGYHUIURR")
    .mBankId(testBankId2.value)
    .mUserId(obpuser2)
    .mScheduledDate(new Date(12340000))
    .mActualDate(new Date(12340000))
    .mChannel("PHONE")
    .mDetail("Another Call about mortgage")
    .mResult("No answer again")
    .mCategory("Category X")
    .saveMe()

  def createCrmEvent3() = MappedCrmEvent.create
    .mCrmEventId("HY677SRDD")
    .mBankId(testBankId2.value)
    .mUserId(obpuser2)
    .mScheduledDate(new Date(12340000))
    .mActualDate(new Date(12340000))
    .mChannel("PHONE")
    .mDetail("Want to save some money?")
    .mResult("Yes, is coming into the Branch")
    .mCategory("Category Y")
    .saveMe()

  feature("Getting crm events") {

    scenario("No crm events exist for user and we try to get them") {
      Given("No MappedCrmEvent exists for a user (any bank)")
      MappedCrmEvent.find(By(MappedCrmEvent.mUserId, obpuser2)).isDefined should equal(false) // (Would find on any bank)

      When("We try to get it by bank and user")
      val foundOpt = MappedCrmEventProvider.getCrmEvents(testBankId1, obpuser2)
      val foundList = foundOpt.get

      Then("We don't")
      foundList.size should equal(0)
    }

    scenario("A CrmEvent exists for user and we try to get it") {
      val createdThing1 = createCrmEvent1()
      Given("MappedCrmEvent exists for a user on a bank")
      MappedCrmEvent.find(
        By(MappedCrmEvent.mBankId, testBankId1.toString),
        By(MappedCrmEvent.mUserId, obpuser1.apiId.value)
      ).isDefined should equal(true)

      When("We try to get it by bank and user")
      val foundOpt = MappedCrmEventProvider.getCrmEvents(testBankId1, obpuser1)

      Then("We do")
      foundOpt.isDefined should equal(true)

      And("It is the right thing")
      val foundThing = foundOpt.get
      foundThing(0) should equal(createdThing1)
    }


    scenario("No crm events exist for a bank and we try to get them") {
      Given("No MappedCrmEvent exists for a bank")
      MappedCrmEvent.find(By(MappedCrmEvent.mBankId, testBankId1.toString)).isDefined should equal(false)

      When("We create on another bank")
      val createdThing = createCrmEvent2

      When("We try to get it by bank")
      val foundOpt = MappedCrmEventProvider.getCrmEvents(testBankId1)
      val foundList = foundOpt.get

      Then("We don't")
      foundList.size should equal(0)
    }

    scenario("CrmEvents exist for bank and user and we try to get them") {

      val createdThing2 = createCrmEvent2()
      val createdThing3 = createCrmEvent3()

      Given("MappedCrmEvent exists for a user")
      MappedCrmEvent.find(
        By(MappedCrmEvent.mBankId, testBankId2.toString),
        By(MappedCrmEvent.mUserId, obpuser2.apiId.value)
      ).isDefined should equal(true)

      When("We try to get them")
      val foundOpt = MappedCrmEventProvider.getCrmEvents(testBankId2, obpuser2)

      Then("We do")
      foundOpt.isDefined should equal(true)

      And("There should be two")
      val foundThings = foundOpt.get
      foundThings.size should equal(2)

      // TODO Check they are the same

    }


  }



}
