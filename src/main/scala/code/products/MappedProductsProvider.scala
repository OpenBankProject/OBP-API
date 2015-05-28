package code.products

import code.products.Products._
import code.common.{License, Meta}
import code.model.BankId
import code.products.Products.ProductId
import code.util.DefaultStringField
import net.liftweb.mapper._

import code.products.Products.Product


object MappedProductsProvider extends ProductsProvider {

  override protected def getProductFromProvider(productId: ProductId): Option[Product] =
  MappedProduct.find(By(MappedProduct.mProductId, productId.value))

  override protected def getProductsFromProvider(bankId: BankId): Option[List[Product]] = {
    Some(MappedProduct.findAll(By(MappedProduct.mBankId, bankId.value)))
  }


}

class MappedProduct extends Product with LongKeyedMapper[MappedProduct] with IdPK {

  override def getSingleton = MappedProduct

  object mBankId extends DefaultStringField(this)
  object mCode extends DefaultStringField(this)
  object mName extends DefaultStringField(this)

  object mProductId extends DefaultStringField(this)

  // Exposed inside address. See below
  object mCategory extends DefaultStringField(this)
  object mFamily extends DefaultStringField(this)
  object mSuperFamily extends DefaultStringField(this)
  object mMoreInfoUrl extends DefaultStringField(this) // use URL field?

  // Exposed inside meta.license See below
  object mLicenseId extends DefaultStringField(this)
  object mLicenseName extends DefaultStringField(this)

  override def productId: ProductId = ProductId(mProductId.get)

  override def bankId: BankId = BankId(mBankId.get)

  override def code: String = mCode.get
  override def name: String = mName.get

  override def category: String = mCategory.get
  override def family : String = mFamily.get
  override def superFamily : String = mSuperFamily.get
  override def moreInfoUrl: String = mMoreInfoUrl.get

  override def meta: Meta = new Meta {
    override def license: License = new License {
      override def id: String = mLicenseId.get
      override def name: String = mLicenseName.get
    }
  }



}

//
object MappedProduct extends MappedProduct with LongKeyedMetaMapper[MappedProduct] {
  override def dbIndexes = UniqueIndex(mBankId, mProductId) :: Index(mBankId) :: super.dbIndexes
}

