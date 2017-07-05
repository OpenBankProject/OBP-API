package code.products

import code.products.Products._
import code.common.{License, Meta}
import code.model.BankId
import code.products.Products.ProductCode
import code.util.DefaultStringField
import net.liftweb.mapper._

import code.products.Products.Product


object MappedProductsProvider extends ProductsProvider {

  override protected def getProductFromProvider(bankId: BankId, productCode: ProductCode): Option[Product] =
  // Does this implicit cast from MappedProduct to Product?
  MappedProduct.find(
    By(MappedProduct.mBankId, bankId.value),
    By(MappedProduct.mCode, productCode.value)
  )

  override protected def getProductsFromProvider(bankId: BankId): Option[List[Product]] = {
    Some(MappedProduct.findAll(By(MappedProduct.mBankId, bankId.value)))
  }


}

class MappedProduct extends Product with LongKeyedMapper[MappedProduct] with IdPK {

  override def getSingleton = MappedProduct

  object mBankId extends DefaultStringField(this) // combination of this
  object mCode extends DefaultStringField(this)   // and this is unique

  object mName extends DefaultStringField(this)

  // Note we have an database pk called id but don't expose it

  // Exposed inside address. See below
  object mCategory extends DefaultStringField(this)
  object mFamily extends DefaultStringField(this)
  object mSuperFamily extends DefaultStringField(this)
  object mMoreInfoUrl extends DefaultStringField(this) // use URL field?
  object mDetails extends DefaultStringField(this)
  object mDescription extends DefaultStringField(this)


  // Exposed inside meta.license See below
  object mLicenseId extends DefaultStringField(this)
  object mLicenseName extends DefaultStringField(this)

  override def bankId: BankId = BankId(mBankId.get)

  override def code: ProductCode = ProductCode(mCode.get)
  override def name: String = mName.get

  override def category: String = mCategory.get
  override def family : String = mFamily.get
  override def superFamily : String = mSuperFamily.get
  override def moreInfoUrl: String = mMoreInfoUrl.get
  override def details: String = mDetails.get
  override def description: String = mDescription.get

  override def meta: Meta = new Meta {
    override def license: License = new License {
      override def id: String = mLicenseId.get
      override def name: String = mLicenseName.get
    }
  }


}

//
object MappedProduct extends MappedProduct with LongKeyedMetaMapper[MappedProduct] {
  override def dbIndexes = UniqueIndex(mBankId, mCode) :: Index(mBankId) :: super.dbIndexes
}

