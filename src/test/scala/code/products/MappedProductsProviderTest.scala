package code.products

import code.products.Products.Product
import code.model.BankId
import code.setup.ServerSetup
import net.liftweb.mapper.By

class MappedProductsProviderTest extends ServerSetup {

  private def delete(): Unit = {
    MappedProduct.bulkDelete_!!()
  }

  override def beforeAll() = {
    super.beforeAll()
    delete()
  }

  override def afterEach() = {
    super.afterEach()
    delete()
  }

  def defaultSetup() =
    new {
      val bankIdX = "some-bank-x"
      val bankIdY = "some-bank-y"

      // 3 products for bank X (one product does not have a license)

      val unlicensedAtm = MappedProduct.create
        .mBankId(bankIdX)
        .mCode("code-unlicensed")
        .mName("Name Unlicensed")
        .mCategory("Cat U")
        .mFamily("Family U")
        .mSuperFamily("Super Fam U")
        .mMoreInfoUrl("www.example.com/moreu")
        .mLicenseId("") // Note: The license is not set
        .mLicenseName("") // Note: The license is not set
        .saveMe()



      val product1 = MappedProduct.create
        .mBankId(bankIdX)
        .mCode("code-1")
        .mName("Product Name 1")
        .mCategory("Cat 1")
        .mFamily("Family 1")
        .mSuperFamily("Super Fam 1")
        .mMoreInfoUrl("www.example.com/more1")
        .mLicenseId("some-license")
        .mLicenseName("Some License")
        .saveMe()

      val product2 = MappedProduct.create
        .mBankId(bankIdX)
        .mCode("code-2")
        .mName("Product Name 2")
        .mCategory("Cat 2")
        .mFamily("Family 2")
        .mSuperFamily("Super Fam 2")
        .mMoreInfoUrl("www.example.com/more2")
        .mLicenseId("some-license")
        .mLicenseName("Some License")
        .saveMe()
    }


  feature("MappedProductsProvider") {

    scenario("We try to get Products") {

      val fixture = defaultSetup()

      // Only these have license set
      val expectedProducts =  List(fixture.product1, fixture.product2)


      Given("the bank in question has Products")
      MappedProduct.find(By(MappedProduct.mBankId, fixture.bankIdX)).isDefined should equal(true)

      When("we try to get the Products for that bank")
      val productsOpt: Option[List[Product]] = MappedProductsProvider.getProducts(BankId(fixture.bankIdX))

      Then("We should get a Products list")
      productsOpt.isDefined should equal (true)
      val products = productsOpt.get

      And("it should contain two Products")
      products.size should equal(2)

      And("they should be the licensed ones")
      products should equal (expectedProducts)
    }

    scenario("We try to get Products for a bank that doesn't have any") {

      val fixture = defaultSetup()

      Given("we don't have any Products")

      MappedProduct.find(By(MappedProduct.mBankId, fixture.bankIdY)).isDefined should equal(false)

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
