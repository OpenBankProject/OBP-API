package code.productcollectionitem

import code.productAttributeattribute.MappedProductAttribute
import code.products.MappedProduct
import com.openbankproject.commons.model.{ProductAttribute, ProductCollectionItem}
import net.liftweb.common.Box
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

import com.openbankproject.commons.ExecutionContext.Implicits.global
import scala.concurrent.Future

object MappedProductCollectionItemProvider extends ProductCollectionItemProvider {
  override def getProductCollectionItems(collectionCode: String) = Future {
    tryo(MappedProductCollectionItem.findAll(By(MappedProductCollectionItem.mCollectionCode, collectionCode)))
  }

  override def getProductCollectionItemsTree(collectionCode: String, bankId: String) = Future {
    tryo {
      MappedProductCollectionItem.findAll(By(MappedProductCollectionItem.mCollectionCode, collectionCode)) map {
        productCollectionItem =>
          val product = MappedProduct.find(
            By(MappedProduct.mBankId, bankId), 
            By(MappedProduct.mCode, productCollectionItem.mMemberProductCode.get)
          ).openOrThrowException("There is no product")
          val attributes: List[MappedProductAttribute] = MappedProductAttribute.findAll(
            By(MappedProductAttribute.mBankId, bankId),
            By(MappedProductAttribute.mCode, product.code.value)
          )
          val xxx: (ProductCollectionItem, MappedProduct, List[ProductAttribute]) = (productCollectionItem, product, attributes)
          xxx
      }
    }
  }
  
  
  override def getOrCreateProductCollectionItem(collectionCode: String, memberProductCodes: List[String]): Future[Box[List[ProductCollectionItem]]] = Future {
    tryo {
      val deleted =
        for {
          item <- MappedProductCollectionItem.findAll(By(MappedProductCollectionItem.mCollectionCode, collectionCode))
        } yield item.delete_!

      deleted.forall(_ == true) match {
        case true =>
          for {
            productCode <- memberProductCodes
          } yield {
            MappedProductCollectionItem
              .create
              .mMemberProductCode(productCode)
              .mCollectionCode(collectionCode)
              .saveMe
          }
        case false =>
          Nil
      }
    }
  }
}

class MappedProductCollectionItem extends ProductCollectionItem with LongKeyedMapper[MappedProductCollectionItem] with IdPK with CreatedUpdated {
  
  def getSingleton = MappedProductCollectionItem

  object mCollectionCode extends MappedString(this, 50)
  object mMemberProductCode extends MappedString(this, 50)

  def collectionCode: String = mCollectionCode.get
  def memberProductCode: String = mMemberProductCode.get
  
}


object MappedProductCollectionItem extends MappedProductCollectionItem with LongKeyedMetaMapper[MappedProductCollectionItem] {
  override def dbIndexes: List[BaseIndex[MappedProductCollectionItem]] = UniqueIndex(mCollectionCode, mMemberProductCode) :: super.dbIndexes
}