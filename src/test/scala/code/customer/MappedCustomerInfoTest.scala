package code.customer

import java.util.Date

import code.api.DefaultUsers
import code.api.ServerSetup
import code.model.BankId
import code.model.dataAccess.ResourceUser

class MappedCustomerProviderTest extends ServerSetup with DefaultUsers {

  val testBankId1 = BankId("bank")
  val testBankId2 = BankId("a-bank")
  val number = "343"

  def createCustomer(bankId: BankId, resourceUser: ResourceUser, nmb: String) = {
    Customer.customerProvider.vend.addCustomer(bankId,
      resourceUser,
      nmb,
      "John Johnson",
      "12343434",
      "bob@example.com",
      MockCustomerFaceImage(new Date(12340000), "http://example.com/image.jpg"),
      new Date(12340000),
      "Single", 2, List(),
      "Bechelor",
      "None",
      true,
      new Date(12340000),
      None,
      None
    )
  }

  feature("Getting customer info") {

    scenario("No customer info exists for user and we try to get it") {
      Given("No MappedCustomer exists for a user")
      When("We try to get it")
      val found = Customer.customerProvider.vend.getCustomerByResourceUserId(testBankId1, authuser2.resourceUserId.value)

      Then("We don't")
      found.isDefined should equal(false)
    }

    scenario("Customer exists and we try to get it") {
      val customer1 = createCustomer(testBankId1, authuser1, number)
      Given("MappedCustomer exists for a user")
      When("We try to get it")
      val foundOpt = Customer.customerProvider.vend.getCustomerByResourceUserId(testBankId1, authuser1.resourceUserId.value)

      Then("We do")
      foundOpt.isDefined should equal(true)

      And("It is the right info")
      val found = foundOpt
      found should equal(customer1)
    }
  }

  feature("Getting a user from a bankId and customer number") {

    scenario("We try to get a user from a customer number that doesn't exist") {
      val customerNumber = "123213213213213"

      When("We try to get the user for a bank with that customer number")
      val found = Customer.customerProvider.vend.getUser(BankId("some-bank"), customerNumber)

      Then("We should not find a user")
      found.isDefined should equal(false)
    }

    scenario("We try to get a user from a customer number that doesn't exist at the bank in question") {
      val customerNumber = "123213213213213"

      Given("Customer info exists for a different bank")
      val customer2 = createCustomer(testBankId2, authuser1, customerNumber)
      When("We try to get the user for the same bank")
      val user = Customer.customerProvider.vend.getUser(BankId(testBankId2.value), customerNumber)

      Then("We should find a user")
      user.isDefined should equal(true)

      When("We try to get the user for a different bank")
      val found = Customer.customerProvider.vend.getUser(BankId(testBankId2.value + "asdsad"), customerNumber)

      Then("We should not find a user")
      found.isDefined should equal(false)
    }

    scenario("We try to get a user from a customer number that does exist at the bank in question") {
      val customerNumber = "123213213213213"

      When("We check is the customer number available")
      val available = Customer.customerProvider.vend.checkCustomerNumberAvailable(testBankId2, customerNumber)
      Then("We should get positive answer")
      available should equal(true)
      createCustomer(testBankId2, authuser1, customerNumber)
      When("We check is the customer number available after creation")
      val notAvailable = Customer.customerProvider.vend.checkCustomerNumberAvailable(testBankId2, customerNumber)
      Then("We should get negative answer")
      notAvailable should equal(false)

      When("We try to get the user for that bank")
      val found = Customer.customerProvider.vend.getUser(testBankId2, customerNumber)

      Then("We should not find a user")
      found.isDefined should equal(true)
    }

  }


  override def beforeAll() = {
    super.beforeAll()
    Customer.customerProvider.vend.bulkDeleteCustomers()
  }

  override def afterEach() = {
    super.afterEach()
    Customer.customerProvider.vend.bulkDeleteCustomers()
  }
}
