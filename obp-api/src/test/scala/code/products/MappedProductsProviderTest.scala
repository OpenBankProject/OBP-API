package code.products

import com.openbankproject.commons.model.Product
import code.setup.ServerSetup
import com.openbankproject.commons.model.BankId

class MappedProductsProviderTest extends ServerSetup {

  private def delete(): Unit = {
    MappedProduct.deleteAll()
  }

  override def beforeAll() = {
    super.beforeAll()
    delete()
  }

  override def afterEach() = {
    super.afterEach()
    delete()
  }

  def defaultSetup() = new DefaultSetup()
  
  class DefaultSetup {
      val bankIdX = "some-bank-x"
      val bankIdY = "some-bank-y"

      // 3 products for bank X (one product does not have a license)

      // Note: The license is not set
      val unlicensedProduct =
      MappedProduct.createOrUpdate(
        bankId = bankIdX,
        code = "code-unlicensed",
        parentProductCode = None,
        name = "Name Unlicensed",
        category = "Cat U",
        family = "Family U",
        superFamily = "Super Fam U",
        moreInfoUrl = "www.example.com/moreu",
        termsAndConditionsUrl = "",
        details = "",
        description = "",
        licenseId = "",
        licenseName = "")



      val product1 =
      MappedProduct.createOrUpdate(
        bankId = bankIdX,
        code = "code-1",
        parentProductCode = None,
        name = "Product Name 1",
        category = "Cat 1",
        family = "Family 1",
        superFamily = "Super Fam 1",
        moreInfoUrl = "www.example.com/more1",
        termsAndConditionsUrl = "",
        details = "",
        description = "",
        licenseId = "some-license",
        licenseName = "Some License")

      val product2 =
      MappedProduct.createOrUpdate(
        bankId = bankIdX,
        code = "code-2",
        parentProductCode = None,
        name = "Product Name 2",
        category = "Cat 2",
        family = "Family 2",
        superFamily = "Super Fam 2",
        moreInfoUrl = "www.example.com/more2",
        termsAndConditionsUrl = "",
        details = "",
        description = "",
        licenseId = "some-license",
        licenseName = "Some License")
    }


  Feature("MappedProductsProvider") {

    Scenario("We try to get Products") {

      val fixture = defaultSetup()

      // Only these have license set
      val expectedProducts =  List(fixture.product1, fixture.product2, fixture.unlicensedProduct)


      Given("the bank in question has Products")
      MappedProduct.findAllByBankId(fixture.bankIdX).nonEmpty should equal(true)

      When("we try to get the Products for that bank")
      val productsOpt: Option[List[Product]] = MappedProductsProvider.getProducts(BankId(fixture.bankIdX))

      Then("We should get a Products list")
      productsOpt.isDefined should equal (true)
      val products = productsOpt.get

      And("it should contain 3 Products")
      products.size should equal(3)

      And("they should be the licensed ones")
      products.sortBy(_.code.value) should equal (expectedProducts.sortBy(_.code.value))
    }

    Scenario("We try to get Products for a bank that doesn't have any") {

      val fixture = defaultSetup()

      Given("we don't have any Products")

      MappedProduct.findAllByBankId(fixture.bankIdY).nonEmpty should equal(false)

      When("we try to get the Products for that bank")
      val productsOpt = MappedProductsProvider.getProducts(BankId(fixture.bankIdY))

      Then("we should get back an empty list")
      productsOpt.isDefined should equal(true)
      val products = productsOpt.get

      products.size should equal(0)

    }


    // TODO add test for individual items

  }
}
