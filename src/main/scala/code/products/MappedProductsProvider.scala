package code.products

import code.common.{License, Meta}
import code.model.BankId
import code.products.Products.{Product, ProductCode}
import code.util.UUIDString
import net.liftweb.mapper._


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

  object mBankId extends UUIDString(this) // combination of this
  object mCode extends MappedString(this, 50)   // and this is unique

  object mName extends MappedString(this, 125)

  // Note we have an database pk called id but don't expose it

  // Exposed inside address. See below
  object mCategory extends MappedString(this, 50)
  object mFamily extends MappedString(this, 50)
  object mSuperFamily extends MappedString(this, 50)
  object mMoreInfoUrl extends MappedString(this, 2000) // use URL field?
  object mDetails extends MappedString(this, 2000)
  object mDescription extends MappedString(this, 2000)


  // Exposed inside meta.license See below
  object mLicenseId extends UUIDString(this) // This are common open data fields in OBP, add class for them?
  object mLicenseName extends MappedString(this, 255)

  override def bankId: BankId = BankId(mBankId.get)

  override def code: ProductCode = ProductCode(mCode.get)
  override def name: String = mName.get

  override def category: String = mCategory.get
  override def family : String = mFamily.get
  override def superFamily : String = mSuperFamily.get
  override def moreInfoUrl: String = mMoreInfoUrl.get
  override def details: String = mDetails.get
  override def description: String = mDescription.get

  override def meta = Meta (
    license = License (
      id = mLicenseId.get,
      name = mLicenseName.get
    )
  )


}

//
object MappedProduct extends MappedProduct with LongKeyedMetaMapper[MappedProduct] {
  override def dbIndexes = UniqueIndex(mBankId, mCode) :: Index(mBankId) :: super.dbIndexes
}

