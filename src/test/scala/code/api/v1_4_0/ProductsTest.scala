package code.api.v1_4_0

import code.api.util.APIUtil.OAuth._
import code.api.v1_4_0.JSONFactory1_4_0.{ProductJson, ProductsJson}
import code.common.{License, LicenseT, Meta, MetaT}
import code.model.BankId
import code.products.Products.ProductCode
import code.products.Products.Product
import code.products.{Products, ProductsProvider}
import code.setup.{DefaultUsers, ServerSetup}
import dispatch._

class ProductsTest extends ServerSetup with DefaultUsers with V140ServerSetup {

  val BankWithLicense = BankId("testBank1")
  val BankWithoutLicense = BankId("testBank2")

  // Have to repeat the constructor parameters from the trait
  case class ProductImpl(bankId: BankId,
                        code : ProductCode,
                        name : String,
                        category: String,
                        family : String,
                        superFamily : String,
                        moreInfoUrl: String,
                        details: String,
                        description: String,
                        meta: Meta) extends Product

  val fakeMeta = Meta(
    License (
      id = "example-data-license",
      name = "Example Data License"
  )
  )

  val fakeMetaNoLicense = Meta(
    License (
      id = "",
      name = ""
    )
  )


  val fakeProduct1 = ProductImpl(BankWithLicense, ProductCode("prod1"), "name 1", "cat 1", "family 1", "super family 1", "http://www.example.com/moreinfo1.html", "", "", fakeMeta)
  val fakeProduct2 = ProductImpl(BankWithLicense, ProductCode("prod2"), "name 2", "cat 1", "family 1", "super family 1", "http://www.example.com/moreinfo2.html", "", "", fakeMeta)

  // Should not be returned (no license)
  val fakeProduct3 = ProductImpl(BankWithoutLicense, ProductCode("prod3"), "name 3", "cat 1", "family 1", "super family 1", "http://www.example.com/moreinfo3.html", "", "", fakeMetaNoLicense)


  // This mock provider is returning same branches for the fake banks
  val mockConnector = new ProductsProvider {
    override protected def getProductsFromProvider(bank: BankId): Option[List[Product]] = {
      bank match {
        // have it return branches even for the bank without a license so we can test the API does not return them
        case BankWithLicense | BankWithoutLicense=> Some(List(fakeProduct1, fakeProduct2, fakeProduct3))
        case _ => None
      }
    }

    // Mock a badly behaving connector that returns data that doesn't have license.
    override protected def getProductFromProvider(bank: BankId, code: ProductCode): Option[Product] = {
      bank match {
         case BankWithLicense => Some(fakeProduct1)
         case BankWithoutLicense=> Some(fakeProduct3) // In case the connector returns, the API should guard
        case _ => None
      }
    }

  }

  def verifySameData(product: Product, productJson : ProductJson) = {
    product.name should equal (productJson.name)
    // Note: We don't currently return the BankId because its part of the URL, so we don't test for that.
    product.code.value should equal (productJson.code)
    product.category should equal (productJson.category)
    product.family should equal (productJson.family)
    product.meta.license.id should equal (productJson.meta.license.id)
    product.meta.license.name should equal (productJson.meta.license.name)
    product.moreInfoUrl should equal (productJson.more_info_url)
    product.name should equal (productJson.name)
    product.superFamily should equal (productJson.super_family)
  }

  /*
  So we can test the API layer, rather than the connector, use a mock connector.
   */
  override def beforeAll() {
    super.beforeAll()
    //use the mock connector
    Products.productsProvider.default.set(mockConnector)
  }

  override def afterAll() {
    super.afterAll()
    //reset the default connector
    Products.productsProvider.default.set(Products.buildOne)
  }

  feature("Getting bank products") {

    scenario("We try to get products for a bank without a data license for product information") {
      When("We make a request")
      val request = (v1_4Request / "banks" / BankWithoutLicense.value / "products").GET <@(user1)
      val response = makeGetRequest(request)

      Then("We should get a 200")
      response.code should equal(200)

    }

    scenario("We try to get products for a bank with a data license for product information") {
      When("We make a request")
      val request = (v1_4Request / "banks" / BankWithLicense.value / "products").GET <@(user1)
      val response = makeGetRequest(request)

      Then("We should get a 200")
      response.code should equal(200)

      And("We should get the right json format containing a list of products")
      val wholeResponseBody = response.body
      val responseBodyOpt = wholeResponseBody.extractOpt[ProductsJson]
      responseBodyOpt.isDefined should equal(true)

      val responseBody = responseBodyOpt.get

      And("We should get the right products")
      val products = responseBody.products

      // Order of Products in the list is arbitrary
      products.size should equal(2)
      val first = products(0)
      if (first.code == fakeProduct1.code.value) {
        verifySameData(fakeProduct1, first)
        verifySameData(fakeProduct2, products(1))
      } else if (first.code == fakeProduct2.code.value) {
        verifySameData(fakeProduct2, first)
        verifySameData(fakeProduct1, products(1))
      } else {
        fail("Incorrect products")
      }
    }
  }
}
