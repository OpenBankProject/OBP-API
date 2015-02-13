package code.customerinfo

import java.util.Date

import code.api.DefaultUsers
import code.api.test.ServerSetup
import code.model.BankId
import net.liftweb.mapper.By

class MappedCustomerInfoProviderTest extends ServerSetup with DefaultUsers {

  val testBankId = BankId("bank")

  lazy val customerInfo1 = MappedCustomerInfo.create
    .mBank(testBankId.value).mEmail("bob@example.com").mFaceImageTime(new Date(12340000))
    .mFaceImageUrl("http://example.com/image.jpg").mLegalName("John Johnson")
    .mMobileNumber("12343434").mNumber("343").mUser(obpuser1).saveMe


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
      Given("MappedCustomerInfo exists for a user")
      MappedCustomerInfo.find(By(MappedCustomerInfo.mUser, obpuser1)).isDefined should equal(true)

      When("We try to get it")
      val foundOpt = MappedCustomerInfoProvider.getInfo(testBankId, obpuser1)

      Then("We do")
      foundOpt.isDefined should equal(true)

      And("It is the right info")
      val found = foundOpt.get
      found should equal(customerInfo1)
    }
  }


  override def beforeAll() = {
    super.beforeAll
    MappedCustomerInfo.bulkDelete_!!()
  }

  override def afterAll() = {
    super.afterAll()
    MappedCustomerInfo.bulkDelete_!!()
  }
}
