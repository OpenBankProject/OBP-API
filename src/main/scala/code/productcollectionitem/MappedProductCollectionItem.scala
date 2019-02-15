package code.productcollectionitem

import net.liftweb.common.Box
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object MappedProductCollectionItemProvider extends ProductCollectionItemProvider {
  override def getProductCollectionItems(collectionCode: String) = Future {
    tryo(MappedProductCollectionItem.findAll(By(MappedProductCollectionItem.mCollectionCode, collectionCode)))
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