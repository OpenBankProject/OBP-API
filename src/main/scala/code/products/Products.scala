package code.products

/* For products */

// Need to import these one by one because in same package!

import code.products.Products.{Product, ProductCode}
import code.model.BankId
import code.common.{Address, LocationT, Meta}
import net.liftweb.common.Logger
import net.liftweb.util.SimpleInjector

object Products extends SimpleInjector {

  // Good to have this as a class because when passing as argument, we get compiler error if passing the wrong type.
  case class ProductCode(value : String)

  object ProductCode {
    def unapply(code : String) = Some(ProductCode(code))
  }

  trait Product {
    def code : ProductCode
    def bankId : BankId
    def name : String
    def category: String
    def family : String
    def superFamily : String
    def moreInfoUrl: String
    def details :String
    def description: String
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
  Use adminView = true to get all Products, else only ones with license returned.
   */
  final def getProducts(bankId : BankId, adminView: Boolean = false) : Option[List[Product]] = {
    logger.info(s"Hello from getProducts bankId is: $bankId")

    getProductsFromProvider(bankId) match {
      case Some(products) => {
        val productsWithLicense = for {
          // Only return products that have a license set unless its for an admin view
          product <- products if (adminView || (product.meta.license.name.size > 3 && product.meta.license.name.size > 3))
        } yield product
        Option(productsWithLicense)
      }
      case None => None
    }
  }

  /*
  Return one Product at a bank
   */
  final def getProduct(bankId : BankId, productCode : ProductCode, adminView: Boolean = false) : Option[Product] = {
    // Filter out if no license data
    getProductFromProvider(bankId, productCode).filter(x => (adminView || (x.meta.license.id != "" && x.meta.license.name != "")))
  }

  protected def getProductFromProvider(bankId : BankId, productCode : ProductCode) : Option[Product]
  protected def getProductsFromProvider(bank : BankId) : Option[List[Product]]

// End of Trait
}






