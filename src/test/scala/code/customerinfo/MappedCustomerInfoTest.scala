package code.customerinfo

import java.util.Date

import code.api.DefaultUsers
import code.api.test.ServerSetup
import code.model.BankId
import net.liftweb.mapper.By

class MappedCustomerInfoProviderTest extends ServerSetup with DefaultUsers {

  val testBankId = BankId("bank")

  def createCustomerInfo1() = MappedCustomerInfo.create
    .mBank(testBankId.value).mEmail("bob@example.com").mFaceImageTime(new Date(12340000))
    .mFaceImageUrl("http://example.com/image.jpg").mLegalName("John Johnson")
    .mMobileNumber("12343434").mNumber("343").mUser(obpuser1).saveMe()

  feature("Getting customer info") {

    scenario("No customer info exists for user and we try to get it") {
      Given("No MappedCustomerInfo exists for a user")
      MappedCustomerInfo.find(By(MappedCustomerInfo.mUser, obpuser2)).isDefined should equal(false)

      When("We try to get it")
      val found = MappedCustomerInfoProvider.getInfo(testBankId, obpuser2)

      Then("We don't")
      found.isDefined should equal(false)
    }

    scenario("Customer info exists and we try to get it") {
      val customerInfo1 = createCustomerInfo1()
      Given("MappedCustomerInfo exists for a user")
      MappedCustomerInfo.find(By(MappedCustomerInfo.mUser, obpuser1.apiId.value)).isDefined should equal(true)

      When("We try to get it")
      val foundOpt = MappedCustomerInfoProvider.getInfo(testBankId, obpuser1)

      Then("We do")
      foundOpt.isDefined should equal(true)

      And("It is the right info")
      val found = foundOpt.get
      found should equal(customerInfo1)
    }
  }

  feature("Getting a user from a bankId and customer number") {

    scenario("We try to get a user from a customer number that doesn't exist") {
      val customerNumber = "123213213213213"

      Given("No customer info exists for a certain customer number")
      MappedCustomerInfo.find(By(MappedCustomerInfo.mNumber, customerNumber)).isDefined should equal(false)

      When("We try to get the user for a bank with that customer number")
      val found = MappedCustomerInfoProvider.getUser(BankId("some-bank"), customerNumber)

      Then("We should not find a user")
      found.isDefined should equal(false)
    }

    scenario("We try to get a user from a customer number that doesn't exist at the bank in question") {
      val customerNumber = "123213213213213"
      val bankId = BankId("a-bank")

      Given("Customer info exists for a different bank")
      MappedCustomerInfo.create.mNumber(customerNumber).mBank(bankId.value).mUser(obpuser1).saveMe()
      MappedCustomerInfo.count(By(MappedCustomerInfo.mNumber, customerNumber),
        By(MappedCustomerInfo.mBank, bankId.value)) should equal({
        MappedCustomerInfo.count(By(MappedCustomerInfo.mNumber, customerNumber))
      })

      When("We try to get the user for a different bank")
      val found = MappedCustomerInfoProvider.getUser(BankId(bankId.value + "asdsad"), customerNumber)

      Then("We should not find a user")
      found.isDefined should equal(false)
    }

    scenario("We try to get a user from a customer number that does exist at the bank in question") {
      val customerNumber = "123213213213213"
      val bankId = BankId("a-bank")

      Given("Customer info exists for that bank")
      MappedCustomerInfo.create.mNumber(customerNumber).mBank(bankId.value).mUser(obpuser1).saveMe()
      MappedCustomerInfo.count(By(MappedCustomerInfo.mNumber, customerNumber),
        By(MappedCustomerInfo.mBank, bankId.value)) should equal(1)

      When("We try to get the user for that bank")
      val found = MappedCustomerInfoProvider.getUser(bankId, customerNumber)

      Then("We should not find a user")
      found.isDefined should equal(true)
    }

  }


  override def beforeAll() = {
    super.beforeAll()
    MappedCustomerInfo.bulkDelete_!!()
  }

  override def afterEach() = {
    super.afterEach()
    MappedCustomerInfo.bulkDelete_!!()
  }
}
