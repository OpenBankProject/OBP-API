package code.products

/* For products */

// Need to import these one by one because in same package!

import code.products.Products.{Product, ProductId}
import code.model.BankId
import code.common.{Address, Location, Meta}
import net.liftweb.common.Logger
import net.liftweb.util.SimpleInjector

object Products extends SimpleInjector {

  case class ProductId(value : String)

  trait Product {
    def productId : ProductId
    def bankId : BankId
    def code : String
    def name : String
    def category: String
    def family : String
    def superFamily : String
    def moreInfoUrl: String
    def meta : Meta
  }



  val productsProvider = new Inject(buildOne _) {}

  def buildOne: ProductsProvider = MappedProductsProvider

  // Helper to get the count out of an option
  def countOfProducts (listOpt: Option[List[Product]]) : Int = {
    val count = listOpt match {
      case Some(list) => list.size
      case None => 0
    }
    count
  }


}

trait ProductsProvider {

  private val logger = Logger(classOf[ProductsProvider])


  /*
  Common logic for returning products.
   */
  final def getProducts(bankId : BankId) : Option[List[Product]] = {
    // If we get products filter them

    logger.info(s"Hello from getProducts bankId is: $bankId")

    getProductsFromProvider(bankId) match {
      case Some(products) => {

        val productsWithLicense = for {
          product <- products if product.meta.license.name.size > 3 && product.meta.license.name.size > 3
        } yield product
        Option(productsWithLicense)
      }
      case None => None
    }
  }

  /*
  Return one Product
   */
  final def getProduct(productId : ProductId) : Option[Product] = {
    // Filter out if no license data
    getProductFromProvider(productId).filter(x => x.meta.license.id != "" && x.meta.license.name != "")
  }

  protected def getProductFromProvider(productId : ProductId) : Option[Product]
  protected def getProductsFromProvider(bank : BankId) : Option[List[Product]]

// End of Trait
}






